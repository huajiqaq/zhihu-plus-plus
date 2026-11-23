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
