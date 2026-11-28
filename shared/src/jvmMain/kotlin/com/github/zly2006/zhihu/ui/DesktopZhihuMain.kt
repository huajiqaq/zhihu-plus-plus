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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.zly2006.zhihu.account.rememberZhihuAccountStore
import com.github.zly2006.zhihu.data.fetchHighestQualityZhihuVideoUrl
import com.github.zly2006.zhihu.desktop.openDesktopExternalUrl
import com.github.zly2006.zhihu.i18n.AppStrings
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.MainTabs
import com.github.zly2006.zhihu.navigation.Video
import com.github.zly2006.zhihu.navigation.rememberAppNavHost
import com.github.zly2006.zhihu.notification.JvmSystemNotificationHost
import com.github.zly2006.zhihu.notification.LocalJvmNotificationWindow
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.reading.ReadingPlayerHost
import com.github.zly2006.zhihu.ui.components.LocalPageTurnDispatcher
import com.github.zly2006.zhihu.ui.components.PageTurnDispatcher
import com.github.zly2006.zhihu.ui.components.PageTurnFab
import com.github.zly2006.zhihu.util.signZhihuFetchRequest
import com.github.zly2006.zhihu.viewmodel.ArticleViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DesktopZhihuMain() {
    ReadingPlayerHost { DesktopMainContent() }
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
    val coroutineScope = rememberCoroutineScope()
    val userMessages = rememberUserMessageSink()

    val currentOpenVideo by rememberUpdatedState<(Video, String, String) -> Unit>(
        { route, contentId, contentType ->
            coroutineScope.launch {
                val cookies = accountStore.session.cookies
                val videoUrl = withContext(Dispatchers.IO) {
                    runCatching {
                        fetchHighestQualityZhihuVideoUrl(
                            httpClient = httpClient,
                            videoId = route.id.toString(),
                            contentId = contentId,
                            contentType = contentType,
                            xsrfToken = cookies["_xsrf"],
                        ) {
                            signZhihuFetchRequest(cookies)
                        }
                    }.getOrNull()
                }
                if (videoUrl == null) {
                    userMessages.showMessage(AppStrings.videoLoadFailed)
                } else {
                    openDesktopExternalUrl(videoUrl)
                }
            }
        },
    )
    val currentShowMessage by rememberUpdatedState<(String) -> Unit>({ userMessages.showMessage(it) })
    val navigationCoordinator = remember(navHost, tabsState) {
        ZhihuMainNavigationCoordinator(
            mainHost = { navHost },
            tabs = { tabsState },
            showMessage = { currentShowMessage(it) },
            openVideo = { video, contentId, contentType -> currentOpenVideo(video, contentId, contentType) },
        )
    }

    JvmSystemNotificationHost(
        accounts = accountStore,
        onOpen = { navigationCoordinator.navigate(it) },
        wakeWindow = LocalJvmNotificationWindow.current,
    ) {
        CompositionLocalProvider(LocalPageTurnDispatcher provides pageTurnDispatcher) {
            Box(Modifier.fillMaxSize()) {
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
                        ArticleScreen(article, articleViewModel)
                    },
                )
                PageTurnFab(dispatcher = pageTurnDispatcher)
            }
        }
    }
}
