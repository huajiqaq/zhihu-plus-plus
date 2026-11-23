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
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import com.github.zly2006.zhihu.theme.ThemeManager
import com.github.zly2006.zhihu.theme.ThemeStyle
import com.github.zly2006.zhihu.theme.ZhihuTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class AppDropdownMenuTypographyTest {
    @Test
    fun menuItemsUseTheSelectedSkinsTypography() {
        val originalTheme = ThemeManager.snapshot()
        val originalHome = System.getProperty("user.home")
        val directory = Files.createTempDirectory("menu-typography-contract-")
        try {
            System.setProperty("user.home", directory.toString())
            for (style in ThemeStyle.entries) {
                ThemeManager.load(originalTheme.copy(themeStyle = style))
                var expectedSize = TextUnit.Unspecified
                var expectedError = Color.Unspecified
                runDesktopComposeUiTest(width = 412, height = 300) {
                    setContent {
                        ZhihuTheme {
                            expectedSize = if (style == ThemeStyle.MIUIX) {
                                MiuixTheme.textStyles.body1.fontSize
                            } else {
                                MaterialTheme.typography.labelLarge.fontSize
                            }
                            expectedError = MaterialTheme.colorScheme.error
                            val owner = LocalNavigationEventDispatcherOwner.current ?: rememberNavigationEventDispatcherOwner(parent = null)
                            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) {
                                AppScaffold {
                                    Box(Modifier.size(40.dp)) {
                                        AppDropdownMenu(expanded = true, onDismissRequest = {}) {
                                            AppDropdownMenuItem(
                                                text = { Text("更多操作", Modifier.testTag("menu_item_text")) },
                                                onClick = {},
                                            )
                                            AppDropdownMenuItem(
                                                text = { Text("删除", Modifier.testTag("menu_error_text"), color = MaterialTheme.colorScheme.error) },
                                                onClick = {},
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    val results = mutableListOf<TextLayoutResult>()
                    onNodeWithTag("menu_item_text", useUnmergedTree = true)
                        .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action -> action(results) }
                    assertEquals(
                        expectedSize,
                        results
                            .single()
                            .layoutInput.style.fontSize,
                        style.name,
                    )
                    val errorResults = mutableListOf<TextLayoutResult>()
                    onNodeWithTag("menu_error_text", useUnmergedTree = true)
                        .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action -> action(errorResults) }
                    assertEquals(
                        expectedError,
                        errorResults
                            .single()
                            .layoutInput.style.color,
                        style.name,
                    )
                }
            }
        } finally {
            if (originalHome == null) System.clearProperty("user.home") else System.setProperty("user.home", originalHome)
            ThemeManager.load(originalTheme)
            directory.toFile().deleteRecursively()
        }
    }
}
