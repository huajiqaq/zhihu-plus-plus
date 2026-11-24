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

package com.github.zly2006.zhihu.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.github.zly2006.zhihu.theme.ThemeManager
import com.github.zly2006.zhihu.theme.ThemeMode
import com.github.zly2006.zhihu.theme.ThemeSnapshot
import com.github.zly2006.zhihu.theme.ThemeStyle
import com.github.zly2006.zhihu.theme.ZhihuTheme
import com.github.zly2006.zhihu.ui.components.AppDimens
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class AccountQuickAccessLayoutTest {
    @Test
    fun cardsCenterContentAndPartialRowsWhileGrowingForLargeTextInBothSkins() {
        val originalTheme = ThemeManager.snapshot()
        val originalHome = System.getProperty("user.home")
        val directory = Files.createTempDirectory("account-quick-access-layout-")
        val actions = listOf("Collections", "Following", "Updates", "History").mapIndexed { index, label ->
            AccountQuickAccessAction(
                label = label,
                icon = Icons.Default.Bookmark,
                testTag = "shortcut-$index",
                badgeCount = if (index == 2) 7 else 0,
                onClick = {},
            )
        }
        try {
            System.setProperty("user.home", directory.toString())
            for (style in ThemeStyle.entries) {
                ThemeManager.load(ThemeSnapshot(themeStyle = style, themeMode = ThemeMode.LIGHT, useDynamicColor = false))
                for ((width, fontScale, columns) in listOf(Triple(420, 1f, 4), Triple(220, 1f, 2), Triple(120, 2f, 1))) {
                    val visibleActions = if (columns == 4) actions else actions.take(3)
                    val context = "$style width=$width fontScale=$fontScale"
                    runSkikoComposeUiTest(size = Size(width.toFloat(), 900f), density = Density(1f)) {
                        setContent {
                            ZhihuTheme(settings = null) {
                                CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                                    AccountQuickAccessRow(visibleActions, Modifier.testTag("shortcuts"))
                                }
                            }
                        }
                        waitForIdle()
                        val host = onNodeWithTag("shortcuts").fetchSemanticsNode().boundsInRoot
                        val cards = visibleActions.map { action ->
                            val card = onNodeWithTag(action.testTag).fetchSemanticsNode().boundsInRoot
                            val icon = onNodeWithTag("${action.testTag}:icon", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                            val textNode = onNodeWithText(action.label, useUnmergedTree = true)
                            val text = textNode.fetchSemanticsNode().boundsInRoot
                            val message = "$context ${action.label}"
                            assertEquals(card.center.y, (icon.top + text.bottom) / 2f, 1f, "$message 图文整体垂直居中")
                            assertEquals(card.center.x, icon.center.x, 1f, "$message 图标横向居中")
                            assertEquals(card.center.x, text.center.x, 1f, "$message 标签横向居中")
                            assertTrue(icon.top >= card.top && text.top >= icon.bottom && text.bottom <= card.bottom, "$message 图文不能越出卡片")
                            val layouts = mutableListOf<TextLayoutResult>()
                            textNode.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                            val layout = layouts.single()
                            assertEquals(fontScale, layout.layoutInput.density.fontScale, 0.01f, "$message 使用指定系统字号")
                            assertFalse(
                                layout.hasVisualOverflow,
                                "$message 标签必须完整显示：size=${layout.size}, lines=${layout.lineCount}, width=${layout.didOverflowWidth}, height=${layout.didOverflowHeight}, card=$card, text=$text",
                            )
                            if (fontScale == 1f) {
                                assertEquals(AppDimens.AccountQuickAccessHeight.value, card.height, 1f, "$message 最小高度包含内边距")
                            }
                            card
                        }
                        val rows = cards.groupBy { it.top }.values
                        assertEquals(visibleActions.chunked(columns).map { it.size }, rows.map { it.size }, "$context 实际换行列数")
                        rows.forEach { row ->
                            assertEquals(host.center.x, (row.first().left + row.last().right) / 2f, 1f, "$context 包括末行在内都应横向居中")
                        }
                        val title = onNodeWithText("快捷访问", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                        assertEquals(if (style == ThemeStyle.MIUIX) 28f else 24f, title.left - host.left, 1f, "$context 标题保留原有左侧起点")
                        if (fontScale > 1f) {
                            assertTrue(cards.any { it.height > AppDimens.AccountQuickAccessHeight.value }, "$context 大字卡片必须允许增高")
                        }
                    }
                }
            }
        } finally {
            if (originalHome == null) System.clearProperty("user.home") else System.setProperty("user.home", originalHome)
            ThemeManager.load(originalTheme)
            directory.toFile().deleteRecursively()
        }
    }
}
