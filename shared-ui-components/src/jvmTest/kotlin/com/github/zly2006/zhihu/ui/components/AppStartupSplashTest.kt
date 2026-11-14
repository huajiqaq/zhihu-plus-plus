package com.github.zly2006.zhihu.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.github.zly2006.zhihu.theme.ZhihuTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class AppStartupSplashTest {
    @Test
    fun startupDoesNotExposeApplicationContentBeforeItsFirstSplashFrame() {
        runDesktopComposeUiTest(width = 720, height = 480) {
            mainClock.autoAdvance = false
            val page = mutableStateOf("应用内容")
            var businessCompositions = 0
            setContent {
                AppStartupSplash {
                    SideEffect { businessCompositions++ }
                    ZhihuTheme { Text(page.value) }
                }
            }
            waitForIdle()
            onNodeWithText("应用内容").assertDoesNotExist()
            val first = onNodeWithTag(APP_STARTUP_SPLASH_TAG).captureToImage().toPixelMap()
            assertTrue(first.width == 720 && first.height == 480, "Splash 必须覆盖完整窗口")
            mainClock.advanceTimeBy(200)
            waitForIdle()
            assertEquals(0, businessCompositions, "启动动画结束前不能创建业务页面或原生弹窗")
            onNodeWithText("知乎++").assertExists()
            val moving = onNodeWithTag(APP_STARTUP_SPLASH_TAG).captureToImage().toPixelMap()
            var differences = 0
            for (y in 0 until first.height step 4) {
                for (x in 0 until first.width step 4) {
                    if (first[x, y] != moving[x, y]) differences++
                }
            }
            assertTrue(differences > 30, "真实矢量应在启动期间移动，而不是重复静态帧")
            mainClock.advanceTimeBy(700)
            waitForIdle()
            onNodeWithTag(APP_STARTUP_SPLASH_TAG).assertDoesNotExist()
            onNodeWithText("应用内容").assertExists()
            runOnIdle { page.value = "后续页面" }
            mainClock.advanceTimeByFrame()
            waitForIdle()
            onNodeWithText("后续页面").assertExists()
            onNodeWithTag(APP_STARTUP_SPLASH_TAG).assertDoesNotExist()
        }
    }

    @Test
    fun activitySplashKeepsItsFinalFrameAndFinishesOnlyOnce() {
        runDesktopComposeUiTest(width = 720, height = 480) {
            mainClock.autoAdvance = false
            var completions = 0
            val redraw = mutableStateOf(0)
            setContent {
                redraw.value
                AppSplashScreen(onFinished = { completions++ })
            }
            waitForIdle()
            assertEquals(0, completions)
            mainClock.advanceTimeBy(900)
            waitForIdle()
            assertEquals(1, completions)
            onNodeWithTag(APP_STARTUP_SPLASH_TAG).assertExists()
            onNodeWithText("知乎++").assertExists()
            val finalFrame = onNodeWithTag(APP_STARTUP_SPLASH_TAG).captureToImage().toPixelMap()
            runOnIdle { redraw.value++ }
            mainClock.advanceTimeBy(300)
            waitForIdle()
            assertEquals(1, completions, "宿主重组不能重复打开主 Activity")
            val retainedFrame = onNodeWithTag(APP_STARTUP_SPLASH_TAG).captureToImage().toPixelMap()
            for (y in 0 until finalFrame.height step 4) {
                for (x in 0 until finalFrame.width step 4) {
                    assertEquals(finalFrame[x, y], retainedFrame[x, y], "交接前须保留完成态，不能重播或清空")
                }
            }
        }
    }
}
