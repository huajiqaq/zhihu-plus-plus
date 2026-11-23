package com.github.zly2006.zhihu.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertAll
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isNotEnabled
import androidx.compose.ui.test.isOff
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.Navigator
import com.github.zly2006.zhihu.notification.NotificationType
import com.github.zly2006.zhihu.theme.ZhihuTheme
import com.github.zly2006.zhihu.ui.subscreens.withIsolatedDesktopHome
import kotlin.test.Test

/** 未接入通知宿主时不申请权限，也不允许在设置页开启任何类型。 */
@OptIn(ExperimentalTestApi::class)
class NotificationSettingsUnsupportedPlatformTest {
    @Test
    fun desktopWithoutNotificationHostShowsDisabledSwitches() = withIsolatedDesktopHome {
        runDesktopComposeUiTest(width = 720, height = 1600) {
            setContent {
                ZhihuTheme {
                    CompositionLocalProvider(LocalNavigator provides Navigator({}, {})) {
                        NotificationSettingsScreen()
                    }
                }
            }
            onAllNodesWithText("此平台暂不支持系统通知，请在应用内通知页查看消息。").assertCountEquals(1)
            onAllNodesWithText("系统授权与通知通道").assertCountEquals(0)

            val systemSwitches = onAllNodes(hasText("暂不支持") and isToggleable())
            systemSwitches.assertCountEquals(NotificationType.entries.size)
            systemSwitches.assertAll(isOff() and isNotEnabled())
        }
    }
}
