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

package com.github.zly2006.zhihu.test

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.WindowSize
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.unit.DpSize
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.github.zly2006.zhihu.MainActivity
import com.github.zly2006.zhihu.navigation.AppNavHost
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.MainTabs
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.Navigator
import com.github.zly2006.zhihu.navigation.TopLevelDestination
import com.github.zly2006.zhihu.navigation.rememberAppNavHost
import com.github.zly2006.zhihu.theme.ZhihuTheme
import com.github.zly2006.zhihu.ui.AndroidZhihuMain
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

typealias MainActivityComposeRule =
    AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>

class RecordingNavigator {
    private val navigateEvents = CopyOnWriteArrayList<NavDestination>()
    private val topLevelNavigateEvents = CopyOnWriteArrayList<TopLevelDestination>()
    private val backEvents = AtomicInteger(0)

    val destinations: List<NavDestination>
        get() = navigateEvents.toList()

    val backCount: Int
        get() = backEvents.get()

    fun asNavigator(): Navigator = Navigator(
        onNavigate = { destination -> navigateEvents += destination },
        onNavigateBack = { backEvents.incrementAndGet() },
        onNavigateTopLevel = { destination -> topLevelNavigateEvents += destination },
    )
}

fun MainActivityComposeRule.resetAppPreferences(forceMock: Boolean = false) {
    val dataMode = if (forceMock || InstrumentedTestEnvironment.isMockMode()) {
        InstrumentedTestEnvironment.DataMode.MOCK
    } else {
        InstrumentedTestEnvironment.DataMode.REAL
    }
    if (InstrumentedTestEnvironment.installedDataMode != dataMode) {
        // 切换数据模式前销毁现有组合与 ViewModel，避免继续持有已关闭的账号存储。
        activity.setContent { }
        waitForIdle()
        runOnIdle { activity.viewModelStore.clear() }
    }
    InstrumentedTestEnvironment.reseed(activity, forceMock = forceMock)
    waitForIdle()
}

fun MainActivityComposeRule.setScreenContent(
    recordingNavigator: RecordingNavigator = RecordingNavigator(),
    content: @Composable () -> Unit,
): RecordingNavigator {
    activity.setContent { }
    waitForIdle()
    activity.setContent {
        ZhihuTheme {
            CompositionLocalProvider(
                LocalNavigator provides recordingNavigator.asNavigator(),
                LocalViewModelStoreOwner provides activity,
            ) {
                content()
            }
        }
    }
    waitForIdle()
    return recordingNavigator
}

@OptIn(ExperimentalTestApi::class)
fun MainActivityComposeRule.setZhihuMainContent(
    windowSize: DpSize? = null,
    onNavHostReady: (AppNavHost) -> Unit = {},
) {
    activity.setContent { }
    waitForIdle()
    activity.setContent {
        ZhihuTheme {
            val navHost = rememberAppNavHost(MainTabs)
            LaunchedEffect(navHost) {
                activity.navHost = navHost
                onNavHostReady(navHost)
            }
            if (windowSize == null) {
                AndroidZhihuMain(navHost = navHost)
            } else {
                DeviceConfigurationOverride(DeviceConfigurationOverride.WindowSize(windowSize)) {
                    CompositionLocalProvider(LocalActivity provides activity) {
                        AndroidZhihuMain(navHost = navHost)
                    }
                }
            }
        }
    }
    waitForIdle()
}
