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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.github.zly2006.zhihu.data.FeedDisplayItem
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.Navigator
import com.github.zly2006.zhihu.platform.desktopSettingsStore
import com.github.zly2006.zhihu.theme.ThemeManager
import com.github.zly2006.zhihu.theme.ThemeMode
import com.github.zly2006.zhihu.theme.ThemeStyle
import com.github.zly2006.zhihu.theme.ZhihuTheme
import com.github.zly2006.zhihu.ui.subscreens.FEED_CARD_STYLE_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.subscreens.PREF_FONT_SIZE
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 在“阅读与排版”里调整正文字号后，已经显示在信息流里的卡片要立即跟随，
 * 不能等卡片滚出屏幕、被重新创建后才生效。
 */
@OptIn(ExperimentalTestApi::class)
class FeedCardSettingsObservationTest {
    @Test
    fun fullTitleAndLongPressPreviewKeepCustomOpenAction() {
        val originalHome = System.getProperty("user.home")
        val originalTheme = ThemeManager.snapshot()
        val directory = Files.createTempDirectory("zhihu-feed-card-preview-").toFile()
        try {
            System.setProperty("user.home", directory.absolutePath)
            val settings = desktopSettingsStore()
            val title = "长标题需要完整显示而不是两行省略，".repeat(6)
            val summary = "预览保留列表已有的完整摘要。".repeat(40)
            for (skin in ThemeStyle.entries) {
                settings.putString("uiThemeStyle", skin.name)
                settings.putString("themeMode", ThemeMode.LIGHT.name)
                ThemeManager.load(originalTheme.copy(themeStyle = skin, themeMode = ThemeMode.LIGHT))
                for (cardStyle in listOf("card", "divider")) {
                    settings.putString(FEED_CARD_STYLE_PREFERENCE_KEY, if (cardStyle == "divider") "card" else "divider")
                    runDesktopComposeUiTest(width = 360, height = 640) {
                        var opens = 0
                        var lineColor = Color.Unspecified
                        setContent {
                            ZhihuTheme {
                                lineColor = MaterialTheme.colorScheme.outlineVariant
                                CompositionLocalProvider(LocalNavigator provides Navigator({}, {})) {
                                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                                        Box(Modifier.width(320.dp).background(Color.Magenta).testTag("feed_pane")) {
                                            FeedCard(
                                                item = FeedDisplayItem(title = title, summary = summary, details = "回答", feed = null),
                                                modifier = Modifier.testTag("feed_item"),
                                                titleTrailingContent = { Text("历史徽章") },
                                                onClick = { _, _ -> opens++ },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        runOnIdle { settings.putString(FEED_CARD_STYLE_PREFERENCE_KEY, cardStyle) }
                        waitForIdle()
                        onNodeWithTag("feed_item").assertIsNotSelected()
                        val titleNode = onNodeWithTag("feed_card_title", useUnmergedTree = true)
                        val layouts = mutableListOf<TextLayoutResult>()
                        titleNode.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                        val layout = layouts.single()
                        assertTrue(layout.lineCount > 2)
                        assertFalse(layout.didOverflowHeight)
                        assertTrue((0 until layout.lineCount).none(layout::isLineEllipsized))
                        onNodeWithText("历史徽章", useUnmergedTree = true).assertExists()
                        val edgeClicks = if (cardStyle == "divider") {
                            val pane = onNodeWithTag("feed_pane")
                            val outerInset = if (skin == ThemeStyle.MIUIX) AppDimens.FeedCardHorizontalPaddingMiuix else AppDimens.FeedCardHorizontalPaddingM3e
                            val inset = with(density) { outerInset.roundToPx() + AppDimens.FeedCardContentHorizontalPadding.roundToPx() }
                            assertEquals(inset.toFloat(), titleNode.fetchSemanticsNode().boundsInRoot.left - pane.fetchSemanticsNode().boundsInRoot.left, 1f)
                            val resting = pane.captureToImage().toPixelMap()
                            val edges = listOf(0, resting.width - 1)
                            for (x in edges) {
                                assertEquals(lineColor.toArgb(), resting[x, resting.height - 1].toArgb(), "$skin 分割线端点 x=$x")
                            }

                            mainClock.autoAdvance = false
                            pane.performTouchInput { down(Offset(1f, centerY)) }
                            try {
                                mainClock.advanceTimeBy(320)
                                val pressed = pane.captureToImage().toPixelMap()
                                for (x in edges) {
                                    assertTrue(pressed[x, pressed.height / 2] != resting[x, resting.height / 2], "$skin 按压反馈应到达 x=$x")
                                }
                            } finally {
                                pane.performTouchInput { up() }
                                mainClock.autoAdvance = true
                            }
                            waitForIdle()
                            assertEquals(1, opens, "$skin 左边缘点击")
                            pane.performTouchInput { click(Offset(width - 1f, centerY)) }
                            waitForIdle()
                            assertEquals(2, opens, "$skin 右边缘点击")
                            pane.performTouchInput { longClick(Offset(width - 1f, centerY)) }
                            2
                        } else {
                            titleNode.performTouchInput { longClick() }
                            0
                        }
                        onNodeWithTag(FEED_CARD_PREVIEW_TAG).assertExists()
                        onNode(hasText(summary) and hasAnyAncestor(hasTestTag(FEED_CARD_PREVIEW_TAG)), useUnmergedTree = true).assertExists()
                        assertEquals(edgeClicks, opens)
                        onNodeWithTag(FEED_CARD_PREVIEW_OPEN_TAG).performClick()
                        assertEquals(edgeClicks + 1, opens)
                        onNodeWithTag(FEED_CARD_PREVIEW_TAG).assertDoesNotExist()
                    }
                }
            }
        } finally {
            if (originalHome == null) System.clearProperty("user.home") else System.setProperty("user.home", originalHome)
            ThemeManager.load(originalTheme)
            directory.deleteRecursively()
        }
    }

    @Test
    fun visibleCardFollowsReadingFontSizeChange() {
        val originalHome = System.getProperty("user.home")
        val directory = Files.createTempDirectory("zhihu-feed-card-settings-").toFile()
        try {
            System.setProperty("user.home", directory.absolutePath)
            val settings = desktopSettingsStore()
            settings.putInt(PREF_FONT_SIZE, 100)
            runDesktopComposeUiTest(width = 412, height = 320) {
                setContent {
                    ZhihuTheme {
                        CompositionLocalProvider(LocalNavigator provides Navigator({}, {})) {
                            FeedCard(FeedDisplayItem(title = "字号跟随标题", summary = "摘要", details = "回答", feed = null))
                        }
                    }
                }

                fun titleFontSize(): Float {
                    val layouts = mutableListOf<TextLayoutResult>()
                    onNodeWithText("字号跟随标题", useUnmergedTree = true)
                        .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                    return layouts
                        .single()
                        .layoutInput.style.fontSize.value
                }
                waitForIdle()
                val initial = titleFontSize()
                runOnIdle { settings.putInt(PREF_FONT_SIZE, 150) }
                waitForIdle()
                assertEquals(initial * 1.5f, titleFontSize(), 0.01f, "已显示卡片的标题字号应跟随阅读字号设置")
            }
        } finally {
            if (originalHome == null) System.clearProperty("user.home") else System.setProperty("user.home", originalHome)
            directory.deleteRecursively()
        }
    }
}
