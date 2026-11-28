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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.uikit.OnFocusBehavior
import androidx.compose.ui.window.ComposeUIViewController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.zly2006.zhihu.account.LocalZhihuAccountStore
import com.github.zly2006.zhihu.account.createNativeAccountStore
import com.github.zly2006.zhihu.account.rememberZhihuAccountStore
import com.github.zly2006.zhihu.data.fetchHighestQualityZhihuVideoUrl
import com.github.zly2006.zhihu.i18n.AppStrings
import com.github.zly2006.zhihu.navigation.MainTabs
import com.github.zly2006.zhihu.navigation.rememberAppNavHost
import com.github.zly2006.zhihu.notification.AppleSystemNotificationHost
import com.github.zly2006.zhihu.notification.installAppleSystemNotificationDelegate
import com.github.zly2006.zhihu.platform.NativeFileDeliveryHost
import com.github.zly2006.zhihu.platform.applySavedAppLanguage
import com.github.zly2006.zhihu.platform.isSoftwareKeyboardVisible
import com.github.zly2006.zhihu.platform.rememberExternalUrlOpener
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.reading.ReadingPlayerHost
import com.github.zly2006.zhihu.theme.ZhihuTheme
import com.github.zly2006.zhihu.ui.components.AppStartupSplash
import com.github.zly2006.zhihu.ui.components.IosPageTurnKeyboardHost
import com.github.zly2006.zhihu.ui.components.IosReadingPageTurnKeyboardScope
import com.github.zly2006.zhihu.ui.components.LocalPageTurnDispatcher
import com.github.zly2006.zhihu.ui.components.PageTurnDispatcher
import com.github.zly2006.zhihu.ui.components.PageTurnFab
import com.github.zly2006.zhihu.ui.components.WindowUserMessageHost
import com.github.zly2006.zhihu.util.signZhihuFetchRequest
import com.github.zly2006.zhihu.viewmodel.ArticleViewModel
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.UIKit.UIViewController

/** 冷启动时先由 Swift 安装代理；内容和路由只在账号对应的共享宿主就绪后处理。 */
fun installIosSystemNotifications(): Boolean {
    applySavedAppLanguage()
    return installAppleSystemNotificationDelegate()
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalForeignApi::class)
fun makeIosViewController(): UIViewController = ComposeUIViewController(
    configure = {
        applySavedAppLanguage()
        onFocusBehavior = OnFocusBehavior.DoNothing
    },
) {
    val accountStore = remember { createNativeAccountStore() }
    val pageTurnDispatcher = remember { PageTurnDispatcher() }
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val keyboardVisible = isSoftwareKeyboardVisible()
    DisposableEffect(accountStore) { onDispose(accountStore::close) }
    CompositionLocalProvider(
        LocalZhihuAccountStore provides accountStore,
        LocalPageTurnDispatcher provides pageTurnDispatcher,
    ) {
        AppStartupSplash {
            ZhihuTheme {
                WindowUserMessageHost {
                    IosPageTurnKeyboardHost {
                        Box(Modifier.fillMaxSize()) {
                            NativeFileDeliveryHost {
                                ReadingPlayerHost {
                                    IosZhihuMain()
                                }
                            }
                            if (lifecycleState == Lifecycle.State.RESUMED && !keyboardVisible) {
                                Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                                    PageTurnFab(dispatcher = pageTurnDispatcher)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IosZhihuMain() {
    val navHost = rememberAppNavHost(MainTabs)
    val tabsState = rememberZhihuMainTabsState()
    val accountStore = rememberZhihuAccountStore()
    val scope = rememberCoroutineScope()
    val messages = rememberUserMessageSink()
    val openUrl = rememberExternalUrlOpener()
    val coordinator = remember(navHost, tabsState, accountStore, scope, messages, openUrl) {
        ZhihuMainNavigationCoordinator(
            mainHost = { navHost },
            tabs = { tabsState },
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
                    if (url == null) messages.showShortMessage(AppStrings.videoLoadFailed) else openUrl(url)
                }
            },
        )
    }
    AppleSystemNotificationHost(accountStore, { coordinator.navigate(it) }) {
        ZhihuMain(
            navHost = navHost,
            tabsState = tabsState,
            navigate = { coordinator.navigate(it) },
            navigateContent = { destination, targetHost -> coordinator.navigate(destination, targetHost) },
            enableLandscapeListDetail = true,
            articleContent = { article ->
                val articleModel: ArticleViewModel = viewModel {
                    ArticleViewModel(article, accountStore.client.httpClient(), messages)
                }
                IosReadingPageTurnKeyboardScope(
                    enabled = articleModel.content.isNotBlank() || articleModel.attachment != null,
                ) {
                    ArticleScreen(article, articleModel)
                }
            },
        )
    }
}
