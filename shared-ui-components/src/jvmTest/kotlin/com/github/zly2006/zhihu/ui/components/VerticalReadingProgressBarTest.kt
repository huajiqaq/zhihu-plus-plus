package com.github.zly2006.zhihu.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
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
}
