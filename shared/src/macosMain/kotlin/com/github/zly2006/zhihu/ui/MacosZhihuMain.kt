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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.zly2006.zhihu.account.rememberZhihuAccountStore
import com.github.zly2006.zhihu.account.rememberAccountQrConfirmationOpener
import com.github.zly2006.zhihu.i18n.AppStrings
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
import com.github.zly2006.zhihu.navigation.rememberAppNavHost
import com.github.zly2006.zhihu.notification.AppleSystemNotificationHost
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.reading.ReadingPlayerHost
import com.github.zly2006.zhihu.ui.components.LocalPageTurnDispatcher
import com.github.zly2006.zhihu.ui.components.PageTurnDispatcher
import com.github.zly2006.zhihu.ui.components.PageTurnFab
import com.github.zly2006.zhihu.ui.components.ReadingPageTurnKeyboardHost
import com.github.zly2006.zhihu.ui.components.ReadingPageTurnKeyboardScope
import com.github.zly2006.zhihu.ui.components.ContinuousUsageReminderHost
import com.github.zly2006.zhihu.ui.subscreens.SystemUpdateAutoCheck
import com.github.zly2006.zhihu.ui.subscreens.navDestinationFromName
import com.github.zly2006.zhihu.viewmodel.ArticleViewModel

@Composable
fun MacosZhihuMain(
    onNotificationOpen: () -> Unit = {},
    windowChrome: MacosWindowChromeHost? = null,
) {
    SystemUpdateAutoCheck()
    CompositionLocalProvider(
        LocalAccountQrConfirmationOpener provides rememberAccountQrConfirmationOpener(),
        LocalSharedMainNavigationBarAvailable provides (windowChrome == null),
    ) {
        ReadingPlayerHost { MacosMainContent(onNotificationOpen, windowChrome) }
    }
}

@Composable
private fun MacosMainContent(
    onNotificationOpen: () -> Unit,
    windowChrome: MacosWindowChromeHost?,
) {
    val navHost = rememberAppNavHost(MainTabs)
    val pageTurnDispatcher = remember { PageTurnDispatcher() }
    val accountStore = rememberZhihuAccountStore()
    val accounts by accountStore.accountsState.collectAsState()
    val accountSession = accounts.session
    val httpClient = remember(accountStore, accountSession) { accountStore.client.httpClient() }
    val userMessages = rememberUserMessageSink()
    val preferenceState = rememberZhihuMainPreferenceState()
    val tabsState = rememberZhihuMainTabsState(preferenceState)
    var accountSheetVisible by remember { mutableStateOf(false) }

    fun navigateToMainTabs(clearRouteStack: Boolean = false) {
        accountSheetVisible = false
        if (clearRouteStack) navHost.popToRoot()
        navHost.open(MainTabs)
    }

    val currentOpenVideo by rememberUpdatedState(rememberVideoPlaybackOpener())
    val currentShowMessage by rememberUpdatedState<(String) -> Unit>({ userMessages.showMessage(it) })
    val navigationCoordinator = remember(navHost, tabsState) {
        ZhihuMainNavigationCoordinator(
            mainHost = { navHost },
            tabs = { tabsState },
            showMessage = { currentShowMessage(it) },
            openVideo = { video, contentId, contentType -> currentOpenVideo(video, contentId, contentType) },
            onRouteAccepted = { accountSheetVisible = false },
            openMainTabs = { navigateToMainTabs() },
        )
    }

    PlatformIncomingLinksHost {
        onNotificationOpen()
        navigationCoordinator.navigate(it)
    }
    val content: @Composable (Modifier) -> Unit = { modifier ->
        AppleSystemNotificationHost(
            accountStore = accountStore,
            onOpen = {
                onNotificationOpen()
                navigationCoordinator.navigate(it)
            },
        ) {
            CompositionLocalProvider(LocalPageTurnDispatcher provides pageTurnDispatcher) {
                Box(modifier.fillMaxSize()) {
                    ContinuousUsageReminderHost()
                    ReadingPageTurnKeyboardHost {
                        ZhihuMain(
                            navHost = navHost,
                            tabsState = tabsState,
                            navigate = { navigationCoordinator.navigate(it) },
                            navigateContent = { destination, targetHost -> navigationCoordinator.navigate(destination, targetHost) },
                            enableLandscapeListDetail = true,
                            showMainNavigationBar = windowChrome == null,
                            showHomeTopActions = windowChrome == null,
                            accountSheetVisible = accountSheetVisible,
                            onDismissAccountSheet = { accountSheetVisible = false },
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
    }

    if (windowChrome == null) {
        content(Modifier)
    } else {
        val selectedMainTabDestination = tabsState.currentDestination.takeUnless { navHost.top == OnlineHistoryRoute }
        val navigationItems = preferenceState.selectedBottomBarItemKeys.mapNotNull { destinationName ->
            val destination = navDestinationFromName(destinationName)
            val action = {
                accountSheetVisible = false
                if (destination == OnlineHistory) {
                    if (navHost.top != OnlineHistoryRoute) {
                        tabsState.requestTab(null)
                        navigationCoordinator.navigate(OnlineHistoryRoute)
                    }
                } else {
                    tabsState.selectOrReselect(destination, navHost.top == MainTabs && destination == tabsState.currentDestination) {
                        tabsState.requestTab(it)
                        navigateToMainTabs(clearRouteStack = true)
                    }
                }
            }
            when (destination) {
                Home -> MacosWindowNavigationItem(Home.name, AppStrings.home, "house", AppStrings.contentSection, Home == selectedMainTabDestination, action)
                Follow -> MacosWindowNavigationItem(Follow.name, AppStrings.following, "person.2", AppStrings.contentSection, Follow == selectedMainTabDestination, action)
                HotList -> MacosWindowNavigationItem(HotList.name, AppStrings.trending, "flame", AppStrings.contentSection, HotList == selectedMainTabDestination, action)
                Daily -> MacosWindowNavigationItem(Daily.name, AppStrings.daily, "newspaper", AppStrings.contentSection, Daily == selectedMainTabDestination, action)
                OnlineHistory -> MacosWindowNavigationItem(
                    OnlineHistory.name,
                    AppStrings.history,
                    "clock.arrow.circlepath",
                    AppStrings.librarySection,
                    navHost.top == OnlineHistoryRoute,
                    action,
                )
                MyCollections -> MacosWindowNavigationItem(
                    MyCollections.name,
                    AppStrings.collections,
                    "bookmark",
                    AppStrings.librarySection,
                    MyCollections == selectedMainTabDestination,
                    action,
                )
                Account -> MacosWindowNavigationItem(
                    Account.name,
                    AppStrings.account,
                    "person.crop.circle",
                    AppStrings.account,
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
                        label = AppStrings.accountSettings,
                        systemSymbolName = "person.crop.circle",
                        action = { accountSheetVisible = true },
                    ),
                    MacosWindowToolbarItem(
                        identifier = "search",
                        label = AppStrings.search,
                        systemSymbolName = "magnifyingglass",
                        action = { navigationCoordinator.navigate(Search()) },
                    ),
                    MacosWindowToolbarItem(
                        identifier = "notifications",
                        label = AppStrings.notifications,
                        systemSymbolName = "bell",
                        action = { navigationCoordinator.navigate(Notification) },
                    ),
                ),
            ),
            content,
        )
    }
}
