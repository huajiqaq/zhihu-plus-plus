package com.github.zly2006.zhihu.ui.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import com.github.zly2006.zhihu.data.FeedDisplayItem
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.Navigator
import com.github.zly2006.zhihu.platform.desktopSettingsStore
import com.github.zly2006.zhihu.theme.ZhihuTheme
import com.github.zly2006.zhihu.ui.subscreens.PREF_FONT_SIZE
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 在“阅读与排版”里调整正文字号后，已经显示在信息流里的卡片要立即跟随，
 * 不能等卡片滚出屏幕、被重新创建后才生效。
 */
@OptIn(ExperimentalTestApi::class)
class FeedCardSettingsObservationTest {
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
                    return layouts.single().layoutInput.style.fontSize.value
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
