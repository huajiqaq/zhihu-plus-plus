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

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class VerticalReadingProgressBarTest {
    @Test
    fun viewportShorterThanMinimumThumbStillRenders() = runDesktopComposeUiTest {
        setContent {
            Box(Modifier.width(12.dp).height(12.dp).testTag("reading_progress_host")) {
                VerticalReadingProgressBar(
                    scrollState = rememberScrollState(),
                    scrollValue = 400,
                    maxScrollValue = 2_000,
                    isScrollInProgress = true,
                    trackColor = Color.Transparent,
                    thumbColor = Color.Red,
                )
            }
        }
        waitForIdle()
        mainClock.advanceTimeBy(160)
        waitForIdle()
        val host = onNodeWithTag("reading_progress_host")
        host.assertHeightIsEqualTo(12.dp)
        val pixels = host.captureToImage().toPixelMap()
        val centerX = pixels.width / 2
        assertTrue(
            (0 until pixels.height).all { y ->
                val pixel = pixels[centerX, y]
                pixel.red > 0.8f && pixel.green < 0.2f && pixel.blue < 0.2f
            },
            "短视口中的滚动条拇指必须绘制到完整可用高度",
        )
    }

    @Test
    fun expandedThumbDragsAndPassesTapsThroughWhenHidden() = runDesktopComposeUiTest {
        val scrollState = ScrollState(0)
        var contentClicks = 0
        mainClock.autoAdvance = false
        setContent {
            Box(Modifier.size(120.dp, 200.dp).testTag("reading_progress_host")) {
                Box(
                    Modifier
                        .matchParentSize()
                        .verticalScroll(scrollState)
                        .height(2_000.dp)
                        .clickable { contentClicks++ },
                )
                VerticalReadingProgressBar(scrollState, modifier = Modifier.align(Alignment.CenterEnd))
            }
        }
        waitForIdle()
        mainClock.advanceTimeBy(200)
        val host = onNodeWithTag("reading_progress_host")
        val hit = with(density) { Offset(80.dp.toPx(), 36.dp.toPx()) }
        host.performTouchInput { down(hit) }
        mainClock.advanceTimeByFrame()
        runOnIdle { assertEquals(0, scrollState.value, "按住热区不能跳到手指所在位置") }
        host.performTouchInput {
            moveBy(Offset(0f, 72.dp.toPx()))
            up()
        }
        mainClock.advanceTimeByFrame()
        runOnIdle { assertTrue(scrollState.value > 0, "从细线外拖动应滚动正文") }

        host.performTouchInput { click(hit) }
        runOnIdle { assertEquals(1, contentClicks, "滑块移开后旧热区应透传点击") }
        val movedHit = with(density) { Offset(hit.x, 84.dp.toPx()) }
        host.performTouchInput { click(movedHit) }
        runOnIdle { assertEquals(1, contentClicks, "热区应随可见滑块一起移动") }
        mainClock.advanceTimeBy(1_600)
        host.performTouchInput { click(movedHit) }
        runOnIdle { assertEquals(2, contentClicks, "淡出后原热区应透传点击") }
    }
}
