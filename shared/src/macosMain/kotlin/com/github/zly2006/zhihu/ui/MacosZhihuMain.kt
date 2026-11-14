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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.zly2006.zhihu.account.rememberZhihuAccountStore
import com.github.zly2006.zhihu.data.fetchHighestQualityZhihuVideoUrl
import com.github.zly2006.zhihu.filter.ContentOpenEventSupport
import com.github.zly2006.zhihu.navigation.Account
import com.github.zly2006.zhihu.navigation.AppNavHost
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.CollectionContent
import com.github.zly2006.zhihu.navigation.CommentHolder
import com.github.zly2006.zhihu.navigation.Daily
import com.github.zly2006.zhihu.navigation.Follow
import com.github.zly2006.zhihu.navigation.History
import com.github.zly2006.zhihu.navigation.Home
import com.github.zly2006.zhihu.navigation.HotList
import com.github.zly2006.zhihu.navigation.MainTabs
import com.github.zly2006.zhihu.navigation.MyCollections
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.Notification
import com.github.zly2006.zhihu.navigation.OnlineHistory
import com.github.zly2006.zhihu.navigation.Pin
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.navigation.Search
import com.github.zly2006.zhihu.navigation.TopLevelDestination
import com.github.zly2006.zhihu.navigation.Video
import com.github.zly2006.zhihu.navigation.rememberAppNavHost
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
fun MacosZhihuMain(windowChrome: MacosWindowChromeHost? = null) {
    val navHost = rememberAppNavHost(MainTabs)
    val accountStore = rememberZhihuAccountStore()
    val accounts by accountStore.accountsState.collectAsState()
    val accountSession = accounts.session
    val httpClient = remember(accountStore, accountSession) { accountStore.client.httpClient() }
    val coroutineScope = rememberCoroutineScope()
    val openExternalUrl = rememberExternalUrlOpener()
    val userMessages = rememberUserMessageSink()
    val preferenceState = rememberZhihuMainPreferenceState()
    var mainTabNavigationTarget by remember { mutableStateOf<TopLevelDestination?>(null) }
    var currentMainTabOpenFrom by remember { mutableStateOf<String?>(null) }
    var currentMainTabDestination by remember { mutableStateOf(preferenceState.startDestination) }

    fun navigateToMainTabs(clearRouteStack: Boolean = false) {
        if (clearRouteStack) navHost.popToRoot()
        navHost.open(MainTabs)
    }

    fun currentContentOpenSource(host: AppNavHost = navHost): NavDestination? = when (val top = host.top) {
        is Article, is Question, is Pin, is CollectionContent, History, Notification -> top
        else -> null
    }

    fun navigate(route: NavDestination, targetHost: AppNavHost = navHost) {
        if (route is CommentHolder) {
            CommentNavigationHandoff.prepareComment(route)
            navigate(route.article, targetHost)
            return
        }
        CommentNavigationHandoff.clearCommentUnless(route)
        when (route) {
            is Video -> {
                val current = when (val top = targetHost.top) {
                    is Article, is Question -> top
                    else -> null
                }
                if (current == null) {
                    userMessages.showMessage("无法打开视频：未知的内容类型")
                    return
                }
                val (contentId, contentType) = when (current) {
                    is Article -> current.id.toString() to when (current.type) {
                        ArticleType.Answer -> "answer"
                        ArticleType.Article -> "article"
                    }
                    is Question -> current.questionId.toString() to "question"
                    else -> return
                }
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
            }
            MainTabs -> {
                mainTabNavigationTarget = Home
                navigateToMainTabs()
            }
            else -> {
                ContentOpenEventSupport.prepareContentOpen(
                    route,
                    (if (navHost.top == MainTabs) currentMainTabOpenFrom else null)
                        ?: ContentOpenEventSupport.inferOpenFrom(currentContentOpenSource(targetHost), route),
                )
                targetHost.open(route)
            }
        }
    }

    val content: @Composable (Modifier) -> Unit = { modifier ->
        ZhihuMain(
            modifier = modifier,
            navHost = navHost,
            mainTabNavigationTarget = mainTabNavigationTarget,
            navigate = ::navigate,
            navigateContent = { destination, targetHost -> navigate(destination, targetHost) },
            enableLandscapeListDetail = true,
            setCurrentMainTabOpenFrom = { currentMainTabOpenFrom = it },
            consumeMainTabNavigationTarget = { destination ->
                if (mainTabNavigationTarget == destination) {
                    mainTabNavigationTarget = null
                }
            },
            preferenceState = preferenceState,
            isDarkTheme = ThemeManager.isDarkTheme(),
            showMainNavigationBar = windowChrome == null,
            showHomeTopActions = windowChrome == null,
            onCurrentMainTabDestinationChange = { currentMainTabDestination = it },
            articleContent = { article: Article ->
                val articleViewModel: ArticleViewModel = viewModel {
                    ArticleViewModel(article, httpClient, userMessages)
                }
                ArticleScreen(article, articleViewModel)
            },
        )
    }

    if (windowChrome == null) {
        content(Modifier)
    } else {
        val navigationItems = preferenceState.selectedBottomBarItemKeys.mapNotNull { destinationName ->
            val destination = navDestinationFromName(destinationName)
            val action = {
                mainTabNavigationTarget = destination
                navigateToMainTabs(clearRouteStack = true)
            }
            when (destination) {
                Home -> MacosWindowNavigationItem(Home.name, "首页", "house", "内容", Home == currentMainTabDestination, action)
                Follow -> MacosWindowNavigationItem(Follow.name, "关注", "person.2", "内容", Follow == currentMainTabDestination, action)
                HotList -> MacosWindowNavigationItem(HotList.name, "热榜", "flame", "内容", HotList == currentMainTabDestination, action)
                Daily -> MacosWindowNavigationItem(Daily.name, "日报", "newspaper", "内容", Daily == currentMainTabDestination, action)
                OnlineHistory -> MacosWindowNavigationItem(
                    OnlineHistory.name,
                    "历史",
                    "clock.arrow.circlepath",
                    "资料",
                    OnlineHistory == currentMainTabDestination,
                    action,
                )
                MyCollections -> MacosWindowNavigationItem(
                    MyCollections.name,
                    "收藏",
                    "bookmark",
                    "资料",
                    MyCollections == currentMainTabDestination,
                    action,
                )
                Account -> MacosWindowNavigationItem(
                    Account.name,
                    "账号",
                    "person.crop.circle",
                    "账号",
                    Account == currentMainTabDestination,
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
                        identifier = "search",
                        label = "搜索",
                        systemSymbolName = "magnifyingglass",
                        action = { navigate(Search()) },
                    ),
                    MacosWindowToolbarItem(
                        identifier = "notifications",
                        label = "通知",
                        systemSymbolName = "bell",
                        action = { navigate(Notification) },
                    ),
                ),
            ),
            content,
        )
    }
}
