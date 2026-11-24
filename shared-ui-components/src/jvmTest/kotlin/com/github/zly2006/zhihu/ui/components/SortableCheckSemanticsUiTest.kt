/*
 * Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation (version 3 only).
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
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
import kotlin.test.assertTrue

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
            runDesktopComposeUiTest(width = 360, height = 600) {
                setContent {
                    ZhihuTheme {
                        SettingItemGroup {
                            AppSortableCheckList(
                                items = listOf(
                                    SortableCheckItem("locked", "账号设置", lockedReason = "底栏需要账号入口以保留设置出口"),
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

                if (style == ThemeStyle.MATERIAL_EXPRESSIVE) {
                    val rows = listOf("locked" to "账号设置", "second" to "第二项", "third" to "第三项").map { (key, title) ->
                        val tag = "sortable_check:item:$key"
                        val rowMatcher = hasAnyDescendant(hasText(title)) and (hasTestTag(tag) or hasAnyDescendant(hasTestTag(tag)))
                        onAllNodes(rowMatcher and !hasAnyDescendant(rowMatcher), useUnmergedTree = true)
                            .fetchSemanticsNodes()
                            .single()
                    }
                    val geometry = rows.joinToString("; ") { "row=${it.id} size=${it.size} bounds=${it.boundsInRoot} parent=${it.parent?.id}" }
                    assertEquals(1, rows.map { it.parent?.id }.distinct().size, "${style.name} 必须测量同层完整行：$geometry")
                    val selectedHeight = rows[1].size.height.toDouble()
                    assertEquals(selectedHeight, rows[0].size.height.toDouble(), 0.5, "${style.name} 账号长摘要仍须与完整行同高：$geometry")
                    assertEquals(selectedHeight, rows[2].size.height.toDouble(), 0.5, "${style.name} 最大选择限制仍须与完整行同高：$geometry")
                    assertTrue(selectedHeight >= with(density) { AppDimens.TouchTarget.toPx() }, style.name)
                }
                onNodeWithTag("sortable_check:item:second")
                    .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
                    .assertIsOn()
                    .assertIsEnabled()

                onNodeWithTag("sortable_check:item:locked", useUnmergedTree = true)
                    .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
                    .assertIsOn()
                    .assertIsNotEnabled()
                    .assertContentDescriptionEquals("账号设置，底栏需要账号入口以保留设置出口")
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
