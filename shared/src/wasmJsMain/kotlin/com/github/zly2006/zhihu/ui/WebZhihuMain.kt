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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.zly2006.zhihu.account.LocalZhihuAccountStore
import com.github.zly2006.zhihu.account.ZhihuAccountClient
import com.github.zly2006.zhihu.account.createWebAccountStore
import com.github.zly2006.zhihu.account.rememberZhihuAccountStore
import com.github.zly2006.zhihu.i18n.AppStrings
import com.github.zly2006.zhihu.i18n.WidgetsStrings
import com.github.zly2006.zhihu.navigation.MainTabs
import com.github.zly2006.zhihu.navigation.rememberAppNavHost
import com.github.zly2006.zhihu.notification.WebSystemNotificationHost
import com.github.zly2006.zhihu.platform.applySavedAppLanguage
import com.github.zly2006.zhihu.platform.rememberExternalUrlOpener
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.reading.ReadingPlayerHost
import com.github.zly2006.zhihu.theme.ZhihuTheme
import com.github.zly2006.zhihu.ui.components.AppDialog
import com.github.zly2006.zhihu.ui.components.AppStartupSplash
import com.github.zly2006.zhihu.ui.components.LocalPageTurnDispatcher
import com.github.zly2006.zhihu.ui.components.PageTurnDispatcher
import com.github.zly2006.zhihu.ui.components.PageTurnFab
import com.github.zly2006.zhihu.ui.components.WebUserMessageHost
import com.github.zly2006.zhihu.viewmodel.ArticleViewModel

@Composable
fun WebZhihuMain() {
    remember { applySavedAppLanguage() }
    val store = remember { createWebAccountStore() }
    DisposableEffect(store) { onDispose(store::close) }
    CompositionLocalProvider(LocalZhihuAccountStore provides store) {
        AppStartupSplash {
            ZhihuTheme {
                WebUserMessageHost(
                    blockedLinkPrompt = { host, retry, dismiss ->
                        AppDialog(
                            onDismissRequest = dismiss,
                            title = WidgetsStrings.externalWebPage,
                            summary = WidgetsStrings.webPopupBlocked(host),
                            confirmText = WidgetsStrings.openInNewTab,
                            onConfirm = retry,
                            dismissText = WidgetsStrings.cancel,
                        )
                    },
                ) {
                    val client = store.client
                    key(client) {
                        val pageTurnDispatcher = remember { PageTurnDispatcher() }
                        CompositionLocalProvider(LocalPageTurnDispatcher provides pageTurnDispatcher) {
                            Box(Modifier.fillMaxSize()) {
                                ReadingPlayerHost {
                                    WebMainContent(client)
                                }
                                PageTurnFab(dispatcher = pageTurnDispatcher)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WebMainContent(client: ZhihuAccountClient) {
    val main = rememberAppNavHost(MainTabs)
    val tabsState = rememberZhihuMainTabsState()
    val messages = rememberUserMessageSink()
    val openUrl = rememberExternalUrlOpener()
    val coordinator = remember(main, tabsState, messages, openUrl) {
        ZhihuMainNavigationCoordinator(
            mainHost = { main },
            tabs = { tabsState },
            showMessage = messages::showShortMessage,
            openVideo = { video, _, _ -> openUrl("https://www.zhihu.com/video/${video.id}") },
        )
    }
    WebSystemNotificationHost(rememberZhihuAccountStore(), { coordinator.navigate(it) }) {
        WebBrowserNavigation(main, tabsState, onUnsupported = { messages.showShortMessage(AppStrings.unsupportedLink) }) {
            ZhihuMain(
                navHost = main,
                tabsState = tabsState,
                navigate = { coordinator.navigate(it) },
                navigateContent = { destination, target -> coordinator.navigate(destination, target) },
                openWebPage = openUrl::invoke,
                enableLandscapeListDetail = true,
                articleContent = { article ->
                    val model: ArticleViewModel = viewModel { ArticleViewModel(article, client.httpClient(), messages) }
                    ArticleScreen(article, model)
                },
            )
        }
    }
}
