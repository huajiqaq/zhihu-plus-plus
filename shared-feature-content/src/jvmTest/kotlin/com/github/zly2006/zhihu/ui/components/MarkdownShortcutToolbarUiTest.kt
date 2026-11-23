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

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.github.zly2006.zhihu.theme.ThemeManager
import com.github.zly2006.zhihu.theme.ThemeMode
import com.github.zly2006.zhihu.theme.ThemeStyle
import com.github.zly2006.zhihu.theme.ZhihuTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class MarkdownShortcutToolbarUiTest {
    @Test
    fun markdownShortcutsKeepFullLabelsAndActionsAtLargeFont() {
        val original = ThemeManager.snapshot()
        try {
            for (skin in ThemeStyle.entries) {
                ThemeManager.load(original.copy(themeStyle = skin, themeMode = ThemeMode.LIGHT, amoledTheme = false))
                runSkikoComposeUiTest(size = Size(320f, 320f), density = Density(1f, 2f)) {
                    var enabled by mutableStateOf(true)
                    val applied = mutableListOf<MarkdownShortcut>()
                    var expectedLabelWidth = 0
                    setContent {
                        CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                            ZhihuTheme {
                                expectedLabelWidth = rememberTextMeasurer()
                                    .measure(
                                        "Link",
                                        MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                                        softWrap = false,
                                    ).size.width
                                MarkdownShortcutToolbar(
                                    onApplyShortcut = { applied += it },
                                    enabled = enabled,
                                    shortcuts = listOf(MarkdownShortcut.Link, MarkdownShortcut.Math),
                                )
                            }
                        }
                    }
                    val link = onNodeWithContentDescription("插入链接")
                    link.assertIsDisplayed()
                    assertTrue(link.fetchSemanticsNode().boundsInRoot.width >= expectedLabelWidth, "$skin 完整Link标签必须放得下")
                    onNodeWithContentDescription("插入链接").performClick()
                    onNodeWithContentDescription("数学公式").performClick()
                    assertEquals(listOf(MarkdownShortcut.Link, MarkdownShortcut.Math), applied)
                    runOnIdle { enabled = false }
                    link.assertIsNotEnabled().performClick()
                    assertEquals(2, applied.size)
                }
            }
        } finally {
            ThemeManager.load(original)
        }
    }
}
