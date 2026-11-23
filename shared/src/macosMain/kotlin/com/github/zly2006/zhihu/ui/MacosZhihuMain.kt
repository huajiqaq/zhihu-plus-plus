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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.zly2006.zhihu.account.rememberZhihuAccountStore
import com.github.zly2006.zhihu.data.fetchHighestQualityZhihuVideoUrl
import com.github.zly2006.zhihu.navigation.Account
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.Daily
import com.github.zly2006.zhihu.navigation.Follow
import com.github.zly2006.zhihu.navigation.Home
import com.github.zly2006.zhihu.navigation.HotList
import com.github.zly2006.zhihu.navigation.MainTabs
import com.github.zly2006.zhihu.navigation.MyCollections
import com.github.zly2006.zhihu.navigation.Notification
import com.github.zly2006.zhihu.navigation.OnlineHistory
import com.github.zly2006.zhihu.navigation.OnlineHistoryRoute
import com.github.zly2006.zhihu.navigation.Search
import com.github.zly2006.zhihu.navigation.Video
import com.github.zly2006.zhihu.navigation.rememberAppNavHost
import com.github.zly2006.zhihu.notification.AppleSystemNotificationHost
import com.github.zly2006.zhihu.platform.rememberExternalUrlOpener
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.theme.ThemeManager
import com.github.zly2006.zhihu.ui.subscreens.navDestinationFromName
import com.github.zly2006.zhihu.util.signZhihuFetchRequest
import com.github.zly2006.zhihu.viewmodel.ArticleViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MacosZhihuMain(
    onNotificationOpen: () -> Unit = {},
    windowChrome: MacosWindowChromeHost? = null,
) {
    val navHost = rememberAppNavHost(MainTabs)
    val accountStore = rememberZhihuAccountStore()
    val accounts by accountStore.accountsState.collectAsState()
    val accountSession = accounts.session
    val httpClient = remember(accountStore, accountSession) { accountStore.client.httpClient() }
    val coroutineScope = rememberCoroutineScope()
    val openExternalUrl = rememberExternalUrlOpener()
    val userMessages = rememberUserMessageSink()
    val preferenceState = rememberZhihuMainPreferenceState()
    var currentMainTabDestination by remember { mutableStateOf(preferenceState.startDestination) }
    var accountSheetVisible by remember { mutableStateOf(false) }

    fun navigateToMainTabs(clearRouteStack: Boolean = false) {
        accountSheetVisible = false
        if (clearRouteStack) navHost.popToRoot()
        navHost.open(MainTabs)
    }

    val currentOpenVideo by rememberUpdatedState<(Video, String, String) -> Unit>(
        { route, contentId, contentType ->
            coroutineScope.launch {
                val cookies = accountStore.session.cookies
                val videoUrl = withContext(Dispatchers.Default) {
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
                    userMessages.showMessage("获取视频链接失败")
                } else {
                    openExternalUrl(videoUrl)
                }
            }
        },
    )
    val currentShowMessage by rememberUpdatedState<(String) -> Unit>({ userMessages.showMessage(it) })
    val navigationCoordinator = remember(navHost) {
        ZhihuMainNavigationCoordinator(
            mainHost = { navHost },
            showMessage = { currentShowMessage(it) },
            openVideo = { video, contentId, contentType -> currentOpenVideo(video, contentId, contentType) },
            onRouteAccepted = { accountSheetVisible = false },
            openMainTabs = { navigateToMainTabs() },
        )
    }

    val content: @Composable (Modifier) -> Unit = { modifier ->
        AppleSystemNotificationHost(
            accountStore = accountStore,
            onOpen = {
                onNotificationOpen()
                navigationCoordinator.navigate(it)
            },
        ) {
            ZhihuMain(
                modifier = modifier,
                navHost = navHost,
                mainTabNavigationTarget = navigationCoordinator.mainTabNavigationTarget,
                navigate = { navigationCoordinator.navigate(it) },
                navigateContent = { destination, targetHost -> navigationCoordinator.navigate(destination, targetHost) },
                enableLandscapeListDetail = true,
                setCurrentMainTabOpenFrom = navigationCoordinator::setCurrentMainTabOpenFrom,
                consumeMainTabNavigationTarget = navigationCoordinator::consumeMainTabNavigationTarget,
                preferenceState = preferenceState,
                isDarkTheme = ThemeManager.isDarkTheme(),
                showMainNavigationBar = windowChrome == null,
                showHomeTopActions = windowChrome == null,
                onCurrentMainTabDestinationChange = { currentMainTabDestination = it },
                accountSheetVisible = accountSheetVisible,
                onDismissAccountSheet = { accountSheetVisible = false },
                articleContent = { article: Article ->
                    val articleViewModel: ArticleViewModel = viewModel {
                        ArticleViewModel(article, httpClient, userMessages)
                    }
                    ArticleScreen(article, articleViewModel)
                },
            )
        }
    }

    if (windowChrome == null) {
        content(Modifier)
    } else {
        val selectedMainTabDestination = currentMainTabDestination.takeUnless { navHost.top == OnlineHistoryRoute }
        val navigationItems = preferenceState.selectedBottomBarItemKeys.mapNotNull { destinationName ->
            val destination = navDestinationFromName(destinationName)
            val action = {
                accountSheetVisible = false
                if (destination == OnlineHistory) {
                    if (navHost.top != OnlineHistoryRoute) {
                        navigationCoordinator.requestMainTab(null)
                        navigationCoordinator.navigate(OnlineHistoryRoute)
                    }
                } else {
                    navigationCoordinator.requestMainTab(destination)
                    navigateToMainTabs(clearRouteStack = true)
                }
            }
            when (destination) {
                Home -> MacosWindowNavigationItem(Home.name, "首页", "house", "内容", Home == selectedMainTabDestination, action)
                Follow -> MacosWindowNavigationItem(Follow.name, "关注", "person.2", "内容", Follow == selectedMainTabDestination, action)
                HotList -> MacosWindowNavigationItem(HotList.name, "热榜", "flame", "内容", HotList == selectedMainTabDestination, action)
                Daily -> MacosWindowNavigationItem(Daily.name, "日报", "newspaper", "内容", Daily == selectedMainTabDestination, action)
                OnlineHistory -> MacosWindowNavigationItem(
                    OnlineHistory.name,
                    "历史",
                    "clock.arrow.circlepath",
                    "资料",
                    navHost.top == OnlineHistoryRoute,
                    action,
                )
                MyCollections -> MacosWindowNavigationItem(
                    MyCollections.name,
                    "收藏",
                    "bookmark",
                    "资料",
                    MyCollections == selectedMainTabDestination,
                    action,
                )
                Account -> MacosWindowNavigationItem(
                    Account.name,
                    "账号",
                    "person.crop.circle",
                    "账号",
                    Account == selectedMainTabDestination,
                    action,
                )
                else -> null
            }
        }
        windowChrome(
            MacosWindowChrome(
                navigationItems = navigationItems,
                trailingToolbarItems = listOf(
                    MacosWindowToolbarItem(
                        identifier = "account",
                        label = "账号与设置",
                        systemSymbolName = "person.crop.circle",
                        action = { accountSheetVisible = true },
                    ),
                    MacosWindowToolbarItem(
                        identifier = "search",
                        label = "搜索",
                        systemSymbolName = "magnifyingglass",
                        action = { navigationCoordinator.navigate(Search()) },
                    ),
                    MacosWindowToolbarItem(
                        identifier = "notifications",
                        label = "通知",
                        systemSymbolName = "bell",
                        action = { navigationCoordinator.navigate(Notification) },
                    ),
                ),
            ),
            content,
        )
    }
}
