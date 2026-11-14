package com.github.zly2006.zhihu.ui.components

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.github.zly2006.zhihu.theme.ThemeManager
import com.github.zly2006.zhihu.theme.ThemeMode
import com.github.zly2006.zhihu.theme.ThemeStyle
import com.github.zly2006.zhihu.theme.ZhihuTheme
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class SortableCheckSemanticsUiTest {
    private val originalTheme = ThemeManager.snapshot()

    @AfterTest
    fun restoreTheme() {
        ThemeManager.load(originalTheme)
    }

    @Test
    fun enabledRowsExposeCheckboxStateAndClickOnlyOnce() {
        for (style in ThemeStyle.entries) {
            ThemeManager.load(originalTheme.copy(themeStyle = style, themeMode = ThemeMode.LIGHT))
            val checkedKeys = mutableStateOf(listOf("first", "second"))
            val changes = mutableListOf<Pair<String, Boolean>>()
            val moves = mutableListOf<Pair<String, Int>>()
            runDesktopComposeUiTest(width = 480, height = 600) {
                setContent {
                    ZhihuTheme {
                        SettingItemGroup {
                            AppSortableCheckList(
                                items = listOf(
                                    SortableCheckItem("first", "第一项"),
                                    SortableCheckItem("second", "第二项"),
                                    SortableCheckItem("third", "第三项"),
                                ),
                                checkedKeys = checkedKeys.value,
                                onCheckedChange = { key, checked ->
                                    changes += key to checked
                                    checkedKeys.value = if (checked) checkedKeys.value + key else checkedKeys.value - key
                                },
                                onMove = { key, offset -> moves += key to offset },
                            )
                        }
                    }
                }

                onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState), useUnmergedTree = true)
                    .assertCountEquals(3)
                onNodeWithTag("sortable_check:item:first")
                    .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
                    .assertIsOn()
                    .assertIsEnabled()
                    .performSemanticsAction(SemanticsActions.OnClick) { it() }
                onNodeWithTag("sortable_check:item:first").assertIsOff()
                runOnIdle { assertEquals(listOf("first" to false), changes, style.name) }

                onNodeWithTag("sortable_check:item:first").performTouchInput { click() }
                onNodeWithTag("sortable_check:item:first").assertIsOn()
                onNodeWithTag("sortable_check:item:third")
                    .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
                    .assertIsOff()
                runOnIdle {
                    assertEquals(listOf("first" to false, "first" to true), changes, style.name)
                    assertEquals(emptyList(), moves, style.name)
                }
            }
        }
    }

    @Test
    fun minimumSelectionDisablesCheckboxesButNotIndependentMoveButtons() {
        for (style in ThemeStyle.entries) {
            ThemeManager.load(originalTheme.copy(themeStyle = style, themeMode = ThemeMode.LIGHT))
            var changes = 0
            val moves = mutableListOf<Pair<String, Int>>()
            runDesktopComposeUiTest(width = 480, height = 600) {
                setContent {
                    ZhihuTheme {
                        SettingItemGroup {
                            AppSortableCheckList(
                                items = listOf(
                                    SortableCheckItem("first", "第一项"),
                                    SortableCheckItem("second", "第二项"),
                                ),
                                checkedKeys = listOf("first", "second"),
                                onCheckedChange = { _, _ -> changes++ },
                                onMove = { key, offset -> moves += key to offset },
                                minChecked = 2,
                            )
                        }
                    }
                }

                onNodeWithTag("sortable_check:item:first", useUnmergedTree = true)
                    .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
                    .assertIsOn()
                    .assertIsNotEnabled()
                    .assertContentDescriptionEquals("第一项，至少保留 2 项")
                    .performTouchInput { click() }
                onNodeWithTag("sortable_check:item:second", useUnmergedTree = true)
                    .assertIsOn()
                    .assertIsNotEnabled()
                onNodeWithTag("sortable_check:moveUp:first", useUnmergedTree = true)
                    .assertIsNotEnabled()
                    .performTouchInput { click() }
                onNodeWithTag("sortable_check:moveDown:second", useUnmergedTree = true).assertIsNotEnabled()
                runOnIdle {
                    assertEquals(0, changes, style.name)
                    assertEquals(emptyList(), moves, style.name)
                }

                onNodeWithTag("sortable_check:moveDown:first", useUnmergedTree = true)
                    .assertIsEnabled()
                    .performSemanticsAction(SemanticsActions.OnClick) { it() }
                onNodeWithTag("sortable_check:moveUp:second", useUnmergedTree = true)
                    .assertIsEnabled()
                    .performTouchInput { click() }
                runOnIdle {
                    assertEquals(0, changes, style.name)
                    assertEquals(listOf("first" to 1, "second" to -1), moves, style.name)
                }
            }
        }
    }

    @Test
    fun lockedAndMaximumSelectionKeepCheckboxAndSortingActionsSeparate() {
        for (style in ThemeStyle.entries) {
            ThemeManager.load(originalTheme.copy(themeStyle = style, themeMode = ThemeMode.LIGHT))
            val checkedKeys = mutableStateOf(listOf("locked", "second"))
            val changes = mutableListOf<Pair<String, Boolean>>()
            val moves = mutableListOf<Pair<String, Int>>()
            runDesktopComposeUiTest(width = 480, height = 600) {
                setContent {
                    ZhihuTheme {
                        SettingItemGroup {
                            AppSortableCheckList(
                                items = listOf(
                                    SortableCheckItem("locked", "必选项", lockedReason = "必须保留"),
                                    SortableCheckItem("second", "第二项"),
                                    SortableCheckItem("third", "第三项"),
                                ),
                                checkedKeys = checkedKeys.value,
                                onCheckedChange = { key, checked ->
                                    changes += key to checked
                                    checkedKeys.value = if (checked) checkedKeys.value + key else checkedKeys.value - key
                                },
                                onMove = { key, offset -> moves += key to offset },
                                maxChecked = 2,
                            )
                        }
                    }
                }

                onNodeWithTag("sortable_check:item:locked", useUnmergedTree = true)
                    .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
                    .assertIsOn()
                    .assertIsNotEnabled()
                    .assertContentDescriptionEquals("必选项，必须保留")
                    .performTouchInput { click() }
                onNodeWithTag("sortable_check:item:third", useUnmergedTree = true)
                    .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
                    .assertIsOff()
                    .assertIsNotEnabled()
                    .assertContentDescriptionEquals("第三项，最多选择 2 项")
                    .performTouchInput { click() }
                onNodeWithTag("sortable_check:moveDown:locked", useUnmergedTree = true)
                    .assertIsEnabled()
                    .performTouchInput { click() }
                runOnIdle {
                    assertEquals(emptyList(), changes, style.name)
                    assertEquals(listOf("locked" to 1), moves, style.name)
                }

                onNodeWithTag("sortable_check:item:second")
                    .assertIsEnabled()
                    .performSemanticsAction(SemanticsActions.OnClick) { it() }
                onNodeWithTag("sortable_check:item:third")
                    .assertIsOff()
                    .assertIsEnabled()
                    .performSemanticsAction(SemanticsActions.OnClick) { it() }
                onNodeWithTag("sortable_check:item:third").assertIsOn()
                runOnIdle {
                    assertEquals(listOf("second" to false, "third" to true), changes, style.name)
                    assertEquals(listOf("locked" to 1), moves, style.name)
                }
            }
        }
    }
}
