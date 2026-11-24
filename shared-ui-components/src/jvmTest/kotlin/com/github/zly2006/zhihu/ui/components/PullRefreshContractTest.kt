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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.github.zly2006.zhihu.theme.ThemeManager
import com.github.zly2006.zhihu.theme.ThemeMode
import com.github.zly2006.zhihu.theme.ThemeStyle
import com.github.zly2006.zhihu.theme.ZhihuTheme
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 刷新指示器须有不透明容器，跨阈值松手只触发一次刷新与确认反馈。 */
@OptIn(ExperimentalTestApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
class PullRefreshContractTest {
    /** 刷新动画必须保留官方容器色，不透明卡片上的正文不能直接透进加载指示器。 */
    @Test
    fun refreshingIndicatorUsesOfficialContainerInLightAndDarkModes() {
        val original = ThemeManager.snapshot()
        val originalHome = System.getProperty("user.home")
        val directory = Files.createTempDirectory("pull-refresh-container-contract-")
        try {
            System.setProperty("user.home", directory.toString())
            for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
                ThemeManager.load(original.copy(themeStyle = ThemeStyle.MATERIAL_EXPRESSIVE, themeMode = mode))
                var expected = Color.Unspecified
                val refreshing = mutableStateOf(false)
                runDesktopComposeUiTest(width = 412, height = 400) {
                    setContent {
                        ZhihuTheme(settings = null) {
                            expected = PullToRefreshDefaults.loadingIndicatorContainerColor
                            Box(Modifier.fillMaxSize().background(Color.Red).testTag("refresh_host")) {
                                AppPullToRefreshBox(refreshing.value, {}, modifier = Modifier.fillMaxSize()) {}
                            }
                        }
                    }
                    waitForIdle()
                    runOnIdle { refreshing.value = true }
                    waitForIdle()
                    mainClock.advanceTimeBy(1000)
                    waitForIdle()
                    val pixels = onNodeWithTag("refresh_host").captureToImage().toPixelMap()
                    var containerPixels = 0
                    for (y in 0 until 160) {
                        for (x in 120 until 292) {
                            val argb = pixels[x, y].toArgb()
                            if (argb == expected.toArgb()) containerPixels++
                        }
                    }
                    assertTrue(containerPixels > 20, "$mode 刷新指示缺少官方容器色，实际像素=$containerPixels")
                }
            }
        } finally {
            if (originalHome == null) System.clearProperty("user.home") else System.setProperty("user.home", originalHome)
            ThemeManager.load(original)
            directory.toFile().deleteRecursively()
        }
    }

    /** 未达到阈值不触发刷新或震动，超过阈值松手后只给一次刷新确认。 */
    @Test
    fun releasedPullTriggersOneRefreshAndOneHaptic() {
        val original = ThemeManager.snapshot()
        val originalHome = System.getProperty("user.home")
        val directory = Files.createTempDirectory("pull-refresh-release-contract-")
        try {
            System.setProperty("user.home", directory.toString())
            ThemeManager.load(original.copy(themeStyle = ThemeStyle.MATERIAL_EXPRESSIVE, themeMode = ThemeMode.LIGHT))
            var refreshes = 0
            val feedback = mutableListOf<HapticFeedbackType>()
            runDesktopComposeUiTest(width = 412, height = 600) {
                setContent {
                    ZhihuTheme(settings = null) {
                        CompositionLocalProvider(
                            LocalHapticFeedback provides object : HapticFeedback {
                                override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                                    feedback += hapticFeedbackType
                                }
                            },
                        ) {
                            AppPullToRefreshBox(false, { refreshes++ }) {
                                LazyColumn(Modifier.fillMaxSize().testTag("pull_list")) {
                                    items(30) { Text("内容 $it") }
                                }
                            }
                        }
                    }
                }
                onNodeWithTag("pull_list").performTouchInput {
                    swipe(Offset(center.x, 120f), Offset(center.x, 155f), 400)
                }
                waitForIdle()
                assertEquals(0, refreshes)
                assertEquals(emptyList(), feedback)
                onNodeWithTag("pull_list").performTouchInput {
                    swipe(Offset(center.x, 120f), Offset(center.x, 550f), 650)
                }
                waitForIdle()
                assertEquals(1, refreshes)
                assertEquals(listOf(HapticFeedbackType.VirtualKey), feedback)
            }
        } finally {
            if (originalHome == null) System.clearProperty("user.home") else System.setProperty("user.home", originalHome)
            ThemeManager.load(original)
            directory.toFile().deleteRecursively()
        }
    }
}
