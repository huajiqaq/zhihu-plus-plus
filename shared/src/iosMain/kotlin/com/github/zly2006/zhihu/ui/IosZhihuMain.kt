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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.uikit.OnFocusBehavior
import androidx.compose.ui.window.ComposeUIViewController
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.zly2006.zhihu.account.rememberZhihuAccountStore
import com.github.zly2006.zhihu.data.fetchHighestQualityZhihuVideoUrl
import com.github.zly2006.zhihu.navigation.MainTabs
import com.github.zly2006.zhihu.navigation.rememberAppNavHost
import com.github.zly2006.zhihu.notification.AppleSystemNotificationHost
import com.github.zly2006.zhihu.notification.installAppleSystemNotificationDelegate
import com.github.zly2006.zhihu.platform.IosUserMessageHost
import com.github.zly2006.zhihu.platform.rememberExternalUrlOpener
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.theme.ThemeManager
import com.github.zly2006.zhihu.theme.ZhihuTheme
import com.github.zly2006.zhihu.ui.components.AppStartupSplash
import com.github.zly2006.zhihu.util.signZhihuFetchRequest
import com.github.zly2006.zhihu.viewmodel.ArticleViewModel
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.UIKit.UIViewController

/** 冷启动时先由 Swift 安装代理；内容和路由只在账号对应的共享宿主就绪后处理。 */
fun installIosSystemNotifications(): Boolean = installAppleSystemNotificationDelegate()

@OptIn(ExperimentalComposeUiApi::class, ExperimentalForeignApi::class)
fun makeIosViewController(): UIViewController = ComposeUIViewController(
    configure = { onFocusBehavior = OnFocusBehavior.DoNothing },
) {
    AppStartupSplash {
        ZhihuTheme {
            IosUserMessageHost { IosZhihuMain() }
        }
    }
}

@Composable
private fun IosZhihuMain() {
    val navHost = rememberAppNavHost(MainTabs)
    val accountStore = rememberZhihuAccountStore()
    val scope = rememberCoroutineScope()
    val messages = rememberUserMessageSink()
    val openUrl = rememberExternalUrlOpener()
    val coordinator = remember(navHost, accountStore, scope, messages, openUrl) {
        ZhihuMainNavigationCoordinator(
            mainHost = { navHost },
            showMessage = messages::showShortMessage,
            openVideo = { video, contentId, contentType ->
                val owner = accountStore.client
                scope.launch {
                    val url = try {
                        withContext(Dispatchers.Default) {
                            val cookies = owner.load().cookies.toMap()
                            fetchHighestQualityZhihuVideoUrl(
                                httpClient = owner.httpClient(),
                                videoId = video.id.toString(),
                                contentId = contentId,
                                contentType = contentType,
                                xsrfToken = cookies["_xsrf"],
                            ) { signZhihuFetchRequest(cookies) }
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        null
                    }
                    if (owner !== accountStore.client) return@launch
                    if (url == null) messages.showShortMessage("获取视频链接失败") else openUrl(url)
                }
            },
        )
    }
    AppleSystemNotificationHost(accountStore, { coordinator.navigate(it) }) {
        ZhihuMain(
            navHost = navHost,
            mainTabNavigationTarget = coordinator.mainTabNavigationTarget,
            navigate = { coordinator.navigate(it) },
            navigateContent = { destination, targetHost -> coordinator.navigate(destination, targetHost) },
            enableLandscapeListDetail = true,
            setCurrentMainTabOpenFrom = coordinator::setCurrentMainTabOpenFrom,
            consumeMainTabNavigationTarget = coordinator::consumeMainTabNavigationTarget,
            preferenceState = rememberZhihuMainPreferenceState(),
            isDarkTheme = ThemeManager.isDarkTheme(),
            articleContent = { article ->
                val articleModel: ArticleViewModel = viewModel {
                    ArticleViewModel(article, accountStore.client.httpClient(), messages)
                }
                ArticleScreen(article, articleModel)
            },
        )
    }
}
