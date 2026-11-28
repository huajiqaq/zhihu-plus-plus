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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.zly2006.zhihu.account.rememberZhihuAccountStore
import com.github.zly2006.zhihu.account.rememberAccountQrConfirmationOpener
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.MainTabs
import com.github.zly2006.zhihu.navigation.rememberAppNavHost
import com.github.zly2006.zhihu.notification.JvmSystemNotificationHost
import com.github.zly2006.zhihu.notification.LocalJvmNotificationWindow
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.reading.ReadingPlayerHost
import com.github.zly2006.zhihu.ui.components.LocalPageTurnDispatcher
import com.github.zly2006.zhihu.ui.components.PageTurnDispatcher
import com.github.zly2006.zhihu.ui.components.PageTurnFab
import com.github.zly2006.zhihu.ui.components.ReadingPageTurnKeyboardHost
import com.github.zly2006.zhihu.ui.components.ReadingPageTurnKeyboardScope
import com.github.zly2006.zhihu.ui.components.ContinuousUsageReminderHost
import com.github.zly2006.zhihu.ui.subscreens.SystemUpdateAutoCheck
import com.github.zly2006.zhihu.viewmodel.ArticleViewModel

@Composable
fun DesktopZhihuMain() {
    SystemUpdateAutoCheck()
    CompositionLocalProvider(LocalAccountQrConfirmationOpener provides rememberAccountQrConfirmationOpener()) {
        ReadingPlayerHost { DesktopMainContent() }
    }
}

@Composable
private fun DesktopMainContent() {
    val navHost = rememberAppNavHost(MainTabs)
    val pageTurnDispatcher = remember { PageTurnDispatcher() }
    val tabsState = rememberZhihuMainTabsState()
    val accountStore = rememberZhihuAccountStore()
    val accounts by accountStore.accountsState.collectAsState()
    val accountSession = accounts.session
    val httpClient = remember(accountStore, accountSession) { accountStore.client.httpClient() }
    val userMessages = rememberUserMessageSink()

    val currentOpenVideo by rememberUpdatedState(rememberVideoPlaybackOpener())
    val currentShowMessage by rememberUpdatedState<(String) -> Unit>({ userMessages.showMessage(it) })
    val navigationCoordinator = remember(navHost, tabsState) {
        ZhihuMainNavigationCoordinator(
            mainHost = { navHost },
            tabs = { tabsState },
            showMessage = { currentShowMessage(it) },
            openVideo = { video, contentId, contentType -> currentOpenVideo(video, contentId, contentType) },
        )
    }

    PlatformIncomingLinksHost { navigationCoordinator.navigate(it) }
    JvmSystemNotificationHost(
        accounts = accountStore,
        onOpen = { navigationCoordinator.navigate(it) },
        wakeWindow = LocalJvmNotificationWindow.current,
    ) {
        CompositionLocalProvider(LocalPageTurnDispatcher provides pageTurnDispatcher) {
            ContinuousUsageReminderHost()
            ReadingPageTurnKeyboardHost {
                ZhihuMain(
                    navHost = navHost,
                    tabsState = tabsState,
                    navigate = { navigationCoordinator.navigate(it) },
                    navigateContent = { destination, targetHost -> navigationCoordinator.navigate(destination, targetHost) },
                    enableLandscapeListDetail = true,
                    articleContent = { article: Article ->
                        val articleViewModel: ArticleViewModel = viewModel {
                            ArticleViewModel(article, httpClient, userMessages)
                        }
                        ReadingPageTurnKeyboardScope(articleViewModel.content.isNotBlank() || articleViewModel.attachment != null) {
                            ArticleScreen(article, articleViewModel)
                        }
                    },
                )
                PageTurnFab(dispatcher = pageTurnDispatcher)
            }
        }
    }
}
