package com.github.zly2006.zhihu.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.github.zly2006.zhihu.platform.SettingsStore
import com.github.zly2006.zhihu.platform.desktopSettingsStore
import com.github.zly2006.zhihu.theme.ThemeManager
import com.github.zly2006.zhihu.theme.ThemeMode
import com.github.zly2006.zhihu.theme.ThemeStyle
import com.github.zly2006.zhihu.theme.ZhihuTheme
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class AnswerHorizontalOverscrollUiTest {
    @Test
    fun answerSwipeReturnsWhenUnitCallbackDoesNotNavigate() = withIsolatedSettings(ThemeStyle.MATERIAL_EXPRESSIVE) {
        runDesktopComposeUiTest(width = 400, height = 320) {
            var nextRequests = 0
            setContent {
                ZhihuTheme {
                    AnswerHorizontalOverscroll(
                        canGoPrevious = true,
                        canGoNext = true,
                        onNavigatePrevious = {},
                        onNavigateNext = { nextRequests++ },
                        previousContent = { Text("只读上一个预览") },
                        nextContent = { Text("只读下一个预览") },
                    ) {
                        Box(Modifier.fillMaxSize().testTag("current_answer"))
                    }
                }
            }
            val initial = onNodeWithTag("current_answer").fetchSemanticsNode().boundsInRoot
            onNodeWithText("只读上一个预览").assertDoesNotExist()
            onNodeWithText("只读下一个预览").assertDoesNotExist()
            onNodeWithTag("answer_horizontal_overscroll").performTouchInput {
                swipe(center, center + Offset(-160f, 0f), durationMillis = 400)
            }
            waitForIdle()
            assertEquals(1, nextRequests)
            assertEquals(initial.left, onNodeWithTag("current_answer").fetchSemanticsNode().boundsInRoot.left, 1f)
            onNodeWithTag("answer_horizontal_preview_caption").assertDoesNotExist()
        }
    }

    private fun withIsolatedSettings(style: ThemeStyle, block: (SettingsStore) -> Unit) {
        val originalHome = System.getProperty("user.home")
        val originalTheme = ThemeManager.snapshot()
        val directory = Files.createTempDirectory("zhihu-floating-ui-").toFile()
        try {
            System.setProperty("user.home", directory.absolutePath)
            val settings = desktopSettingsStore()
            settings.putString("uiThemeStyle", style.name)
            settings.putString("themeMode", ThemeMode.LIGHT.name)
            settings.putBoolean("amoledTheme", false)
            ThemeManager.load(originalTheme.copy(themeStyle = style, themeMode = ThemeMode.LIGHT, amoledTheme = false))
            block(settings)
        } finally {
            if (originalHome == null) System.clearProperty("user.home") else System.setProperty("user.home", originalHome)
            ThemeManager.load(originalTheme)
            directory.deleteRecursively()
        }
    }
}
