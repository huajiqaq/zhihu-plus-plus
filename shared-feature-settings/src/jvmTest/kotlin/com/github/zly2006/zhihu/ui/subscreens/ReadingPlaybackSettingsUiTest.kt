package com.github.zly2006.zhihu.ui.subscreens

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.Navigator
import com.github.zly2006.zhihu.platform.desktopSettingsStore
import com.github.zly2006.zhihu.reading.ReadingPreferences
import com.github.zly2006.zhihu.reading.ReadingTemplateField
import com.github.zly2006.zhihu.reading.loadReadingPreferences
import com.github.zly2006.zhihu.reading.saveReadingPreferences
import com.github.zly2006.zhihu.theme.ThemeManager
import com.github.zly2006.zhihu.theme.ThemeStyle
import com.github.zly2006.zhihu.theme.ZhihuTheme
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ReadingPlaybackSettingsUiTest {
    @Test
    fun disabledReadingOptionsCannotChangeSavedPreferences() = withReadingPlaybackScreen(ReadingPreferences()) { initial ->
        listOf(
            READING_SETTINGS_PUBLISHED_TIME_MODE_TAG_PREFIX + "Relative",
            READING_SETTINGS_RELATIVE_TIME_PRECISION_TAG_PREFIX + "Day",
            READING_SETTINGS_COMMENT_ORDER_TAG_PREFIX + "Time",
        ).forEach { tag ->
            onNodeWithTag(tag).performScrollTo().assertIsNotEnabled().performClick()
            waitForIdle()
            assertEquals(initial, savedPreferences())
        }
    }

    @Test
    fun lastEnabledFieldIsLockedWithReason() = withReadingPlaybackScreen(
        ReadingPreferences(enabledFields = setOf(ReadingTemplateField.Body)),
    ) { initial ->
        onAllNodesWithText("至少保留一项").assertCountEquals(1)
        onAllNodes(hasText("正文") and hasClickAction()).assertCountEquals(0)
        assertEquals(initial, savedPreferences())
    }

    /** 仅剩零条评论会被归一化为正文，故锁住另一可读字段。 */
    @Test
    fun lastReadableFieldIsLockedWhenCommentCountIsZero() = withReadingPlaybackScreen(
        ReadingPreferences(
            enabledFields = setOf(ReadingTemplateField.Body, ReadingTemplateField.Comments),
            commentCount = 0,
        ),
    ) { initial ->
        onAllNodesWithText("评论条数为 0，至少保留一项其他内容").assertCountEquals(1)
        onAllNodes(hasText("正文") and hasClickAction()).assertCountEquals(0)
        assertEquals(initial, savedPreferences())
    }

    @Test
    fun customQueueLimitInputHasItsOwnTag() = withReadingPlaybackScreen(ReadingPreferences()) {
        onAllNodesWithTag(READING_SETTINGS_CUSTOM_QUEUE_LIMIT_TAG).assertCountEquals(1)
        onAllNodesWithTag(CUSTOM_QUEUE_LIMIT_INPUT_TAG).assertCountEquals(0)

        onNodeWithTag(READING_SETTINGS_CUSTOM_QUEUE_LIMIT_TAG).performScrollTo().performClick()
        waitForIdle()

        onAllNodesWithTag(READING_SETTINGS_CUSTOM_QUEUE_LIMIT_TAG).assertCountEquals(1)
        onAllNodesWithTag(CUSTOM_QUEUE_LIMIT_INPUT_TAG).assertCountEquals(1)
    }

    @Test
    fun commentCountIsSavedOnImeDoneOrFocusLossOnly() = withReadingPlaybackScreen(
        ReadingPreferences(
            enabledFields = setOf(ReadingTemplateField.Body, ReadingTemplateField.Comments),
            commentCount = 3,
        ),
    ) { initial ->
        val commentCount = onNodeWithTag(READING_SETTINGS_COMMENT_COUNT_TAG).performScrollTo()
        commentCount.performTextReplacement("12")
        waitForIdle()
        assertEquals(initial, savedPreferences(), "输入过程中不应写入设置")
        commentCount.performImeAction()
        waitForIdle()
        assertEquals(12, savedPreferences().commentCount)

        commentCount.performScrollTo().performTextReplacement("7")
        waitForIdle()
        assertEquals(12, savedPreferences().commentCount, "输入过程中不应写入设置")
        onNodeWithTag(READING_SETTINGS_TRANSITION_TEXT_TAG).performScrollTo().requestFocus()
        waitForIdle()
        assertEquals(7, savedPreferences().commentCount)
    }

    private fun withReadingPlaybackScreen(
        preferences: ReadingPreferences,
        body: ComposeUiTest.(saved: ReadingPreferences) -> Unit,
    ) {
        val originalUserHome = System.getProperty("user.home")
        val originalTheme = ThemeManager.snapshot()
        val testDirectory = Files.createTempDirectory("zhihu-reading-ui-").toFile()
        try {
            System.setProperty("user.home", testDirectory.absolutePath)
            ThemeManager.setThemeStyle(ThemeStyle.MATERIAL_EXPRESSIVE)
            val settings = desktopSettingsStore()
            settings.putString("uiThemeStyle", "MATERIAL_EXPRESSIVE")
            saveReadingPreferences(settings, preferences)
            val saved = loadReadingPreferences(settings)
            runDesktopComposeUiTest(width = 412, height = 892) {
                setContent {
                    ZhihuTheme {
                        CompositionLocalProvider(LocalNavigator provides Navigator({}, {})) {
                            ReadingPlaybackSettingsScreen()
                        }
                    }
                }
                body(saved)
            }
        } finally {
            System.setProperty("user.home", originalUserHome)
            ThemeManager.load(originalTheme)
            testDirectory.deleteRecursively()
        }
    }

    private fun savedPreferences(): ReadingPreferences = loadReadingPreferences(desktopSettingsStore())

    private companion object {
        const val CUSTOM_QUEUE_LIMIT_INPUT_TAG = "readingSettings.queueLimit.customInput"
    }
}
