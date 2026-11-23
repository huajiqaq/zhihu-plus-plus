package com.github.zly2006.zhihu.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.Navigator
import com.github.zly2006.zhihu.platform.desktopSettingsStore
import com.github.zly2006.zhihu.theme.ZhihuTheme
import com.github.zly2006.zhihu.ui.subscreens.withIsolatedDesktopHome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 重置已读记录须显示数量并等待确认，不能在打开弹窗时清除。 */
@OptIn(ExperimentalTestApi::class)
class NotificationSettingsResetTest {
    @Test
    fun resetReadRecordsAsksWithTheCountAndClearsOnConfirm() = withIsolatedDesktopHome {
        val settings = desktopSettingsStore()
        settings.putStringSet("onlineHomeNotificationReadUuids", setOf("fixture-announcement"))
        settings.putBoolean("readHomePinAnnouncement_1", true)
        runDesktopComposeUiTest(width = 720, height = 1600) {
            setContent {
                ZhihuTheme {
                    CompositionLocalProvider(LocalNavigator provides Navigator({}, {})) {
                        NotificationSettingsScreen()
                    }
                }
            }
            onNode(
                (hasTestTag(RESET_TRIGGER_TAG) or hasText("重置通知与作者想法推送已读记录")) and hasClickAction(),
            ).performScrollTo().performClick()
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("重置 2 条已读记录？").fetchSemanticsNodes().isNotEmpty() }
            onAllNodesWithText("online notification", substring = true, ignoreCase = true).assertCountEquals(0)

            onNode(hasText("重置") and hasClickAction() and !hasTestTag(RESET_TRIGGER_TAG)).performClick()
            waitForIdle()
            assertEquals(emptySet(), settings.getStringSet("onlineHomeNotificationReadUuids", emptySet()))
            assertNull(settings.getStringOrNull("readHomePinAnnouncement_1"))
            onNodeWithTag(RESET_TRIGGER_TAG).assertIsNotEnabled()
        }
    }

    private companion object {
        const val RESET_TRIGGER_TAG = "notification_settings_reset_read_records"
    }
}
