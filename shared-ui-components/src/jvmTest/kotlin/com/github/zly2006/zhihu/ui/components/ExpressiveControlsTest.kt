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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.window.Dialog
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import com.github.zly2006.zhihu.theme.ThemeMode
import com.github.zly2006.zhihu.theme.ThemeSnapshot
import com.github.zly2006.zhihu.theme.ThemeStyle
import com.github.zly2006.zhihu.theme.ZhihuTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ExpressiveControlsTest {
    @Test
    fun actionDropdownKeepsTrailingActionIndependentInsideItsWindow() {
        for ((style, inDialog) in ThemeStyle.entries.flatMap { style -> listOf(style to false, style to true) }) {
            var expanded by mutableStateOf(false)
            var selections = 0
            var deletions = 0
            runDesktopComposeUiTest(width = 412, height = 400) {
                setContent {
                    ZhihuTheme(snapshot = ThemeSnapshot(themeStyle = style, themeMode = ThemeMode.LIGHT)) {
                        val owner = LocalNavigationEventDispatcherOwner.current ?: rememberNavigationEventDispatcherOwner(parent = null)
                        CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) {
                            AppScaffold {
                                val actions: @Composable () -> Unit = {
                                    Box(Modifier.fillMaxSize().testTag("actions_window")) {
                                        Box(Modifier.align(Alignment.BottomEnd)) {
                                            AppTextButton("打开操作", { expanded = true }, Modifier.testTag("actions_anchor"))
                                            AppDropdownMenu(
                                                expanded = expanded,
                                                onDismissRequest = { expanded = false },
                                                modifier = Modifier.testTag("actions_menu"),
                                                renderInRootScaffold = !inDialog,
                                            ) {
                                                AppDropdownMenuItem(
                                                    text = { Text("选择收藏夹") },
                                                    modifier = Modifier.testTag("folder_action"),
                                                    onClick = {
                                                        selections++
                                                        expanded = false
                                                    },
                                                    trailingIcon = {
                                                        AppTextButton("删除收藏夹", {
                                                            deletions++
                                                            expanded = false
                                                        }, Modifier.testTag("folder_delete"))
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                                if (inDialog) {
                                    Dialog(onDismissRequest = { error("菜单不能关闭父窗口") }) {
                                        AppScaffold { actions() }
                                    }
                                } else {
                                    actions()
                                }
                            }
                        }
                    }
                }
                onNodeWithTag("actions_anchor").performTouchInput { click() }
                val window = onNodeWithTag("actions_window").fetchSemanticsNode()
                val anchor = onNodeWithTag("actions_anchor").fetchSemanticsNode()
                val menu = onNodeWithTag("actions_menu").assertIsDisplayed().fetchSemanticsNode()
                assertTrue(menu.positionOnScreen.x >= window.positionOnScreen.x - 1f, "$style/$inDialog 菜单左边界")
                assertTrue(menu.positionOnScreen.x + menu.size.width <= window.positionOnScreen.x + window.size.width + 1f, "$style/$inDialog 菜单右边界")
                assertTrue(menu.positionOnScreen.y >= window.positionOnScreen.y - 1f, "$style/$inDialog 菜单上边界")
                assertTrue(menu.positionOnScreen.y + menu.size.height <= anchor.positionOnScreen.y + 1f, "$style/$inDialog 底部锚点应向上展开")
                onNodeWithTag("folder_action").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
                if (style == ThemeStyle.MIUIX) {
                    onNodeWithTag("folder_action").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
                }
                onNodeWithTag("folder_delete", useUnmergedTree = true).performTouchInput { click() }
                waitForIdle()
                assertEquals(1, deletions, style.name)
                assertEquals(0, selections, "独立尾部动作不能触发行选择")
                onNodeWithTag("actions_menu").assertDoesNotExist()
                onNodeWithTag("actions_anchor").performTouchInput { click() }
                onNodeWithText("选择收藏夹", useUnmergedTree = true).assertIsDisplayed().performTouchInput { click() }
                waitForIdle()
                assertEquals(1, selections, style.name)
                assertEquals(1, deletions, style.name)
                onNodeWithTag("actions_menu").assertDoesNotExist()
            }
        }
    }

    @Test
    fun confirmationAboveAnotherDialogPreservesTheParentInput() {
        for (style in ThemeStyle.entries) {
            var confirm by mutableStateOf(false)
            var parentDismissals = 0
            runDesktopComposeUiTest(width = 600, height = 800) {
                setContent {
                    ZhihuTheme(snapshot = ThemeSnapshot(themeStyle = style, themeMode = ThemeMode.LIGHT)) {
                        val owner = LocalNavigationEventDispatcherOwner.current
                            ?: rememberNavigationEventDispatcherOwner(parent = null)
                        CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) {
                            AppScaffold {
                                Dialog(onDismissRequest = { parentDismissals++ }) {
                                    Column {
                                        var draft by remember { mutableStateOf("") }
                                        TextField(draft, { draft = it }, modifier = Modifier.testTag("parent_input"))
                                        AppTextButton("打开确认", { confirm = true })
                                    }
                                }
                                if (confirm) {
                                    AppAlertDialog(
                                        onDismissRequest = { confirm = false },
                                        title = { Text("二次确认") },
                                        confirmButton = { AppTextButton("取消确认", { confirm = false }) },
                                    )
                                }
                            }
                        }
                    }
                }
                onNodeWithTag("parent_input").performTextInput("尚未提交的内容")
                onNodeWithText("打开确认").performTouchInput { click() }
                onNodeWithText("二次确认").assertIsDisplayed().assert(hasAnyAncestor(isDialog()))
                onNodeWithText("取消确认").performTouchInput { click() }
                waitForIdle()
                onNodeWithText("二次确认").assertDoesNotExist()
                onNodeWithTag("parent_input").assertIsDisplayed()
                onNodeWithText("尚未提交的内容").assertExists()
                assertEquals(0, parentDismissals, "取消确认不能关闭下层窗口")
            }
        }
    }
}
