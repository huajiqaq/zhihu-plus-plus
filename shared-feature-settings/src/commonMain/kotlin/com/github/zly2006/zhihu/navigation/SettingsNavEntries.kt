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

package com.github.zly2006.zhihu.navigation

import androidx.compose.runtime.Composable
import com.github.zly2006.zhihu.account.LoginScreen
import com.github.zly2006.zhihu.ui.BlocklistSettingsScreen
import com.github.zly2006.zhihu.ui.LegacyLocalHistoryScreen
import com.github.zly2006.zhihu.ui.NotificationScreen
import com.github.zly2006.zhihu.ui.NotificationSettingsScreen
import com.github.zly2006.zhihu.ui.NotificationTimelineScreen
import com.github.zly2006.zhihu.ui.OnlineHistoryScreen
import com.github.zly2006.zhihu.ui.PrivateMessageScreen
import com.github.zly2006.zhihu.ui.subscreens.AccountToolsScreen
import com.github.zly2006.zhihu.ui.subscreens.AppearanceSettingsScreen
import com.github.zly2006.zhihu.ui.subscreens.BlockedFeedHistoryScreen
import com.github.zly2006.zhihu.ui.subscreens.COLLECTION_DIRECT_BROWSE_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.subscreens.ContentFilterSettingsScreen
import com.github.zly2006.zhihu.ui.subscreens.FEED_SETTINGS_KEYS
import com.github.zly2006.zhihu.ui.subscreens.FeedSettingsScreen
import com.github.zly2006.zhihu.ui.subscreens.IdentityManagementScreen
import com.github.zly2006.zhihu.ui.subscreens.NAVIGATION_SETTINGS_KEYS
import com.github.zly2006.zhihu.ui.subscreens.NavigationSettingsScreen
import com.github.zly2006.zhihu.ui.subscreens.OpenSourceLicensesScreen
import com.github.zly2006.zhihu.ui.subscreens.PAGE_TURN_SETTINGS_KEYS
import com.github.zly2006.zhihu.ui.subscreens.PageTurnSettingsScreen
import com.github.zly2006.zhihu.ui.subscreens.READING_LAYOUT_SETTINGS_KEYS
import com.github.zly2006.zhihu.ui.subscreens.ReadingPlaybackSettingsScreen
import com.github.zly2006.zhihu.ui.subscreens.ReadingSettingsScreen
import com.github.zly2006.zhihu.ui.subscreens.SettingsSearchScreen
import com.github.zly2006.zhihu.ui.subscreens.SystemAndUpdateSettingsScreen
import com.github.zly2006.zhihu.ui.subscreens.WebDavSyncScreen
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection

fun AppNavEntryBuilder.loginEntry(
    navHost: AppNavHost,
    swipeBack: NavSwipeDirection,
) {
    entry<Login>(swipeDismiss = swipeBack) {
        LoginScreen(
            onLoginComplete = { navHost.back() },
            onOpenTelemetrySettings = {
                navHost.open(Account.SystemAndUpdateSettings("allowTelemetry"))
            },
            onBack = navHost::onBack,
        )
    }
}

fun AppNavEntryBuilder.historyEntries(
    currentDestination: NavDestination?,
    swipeBack: NavSwipeDirection,
) {
    entry<History>(swipeDismiss = swipeBack) {
        LegacyLocalHistoryScreen()
    }
    entry<OnlineHistoryRoute>(swipeDismiss = swipeBack) {
        OnlineHistoryScreen(
            onBack = LocalNavigator.current.onNavigateBack,
            isActive = currentDestination == OnlineHistoryRoute,
        )
    }
}

fun AppNavEntryBuilder.accountSettings(
    reloadPreferences: () -> Unit,
    nlpContent: @Composable (onNavigateBack: () -> Unit) -> Unit,
    swipeBack: NavSwipeDirection,
) {
    entry<Account.RecommendSettings.Blocklist>(swipeDismiss = swipeBack) {
        BlocklistSettingsScreen(nlpContent)
    }
    entry<Account.RecommendSettings.BlockedFeedHistory>(swipeDismiss = swipeBack) {
        BlockedFeedHistoryScreen()
    }
    entry<Account.AppearanceSettings>(swipeDismiss = swipeBack) { args ->
        when (args.setting) {
            COLLECTION_DIRECT_BROWSE_PREFERENCE_KEY, in NAVIGATION_SETTINGS_KEYS -> NavigationSettingsScreen(args.setting, reloadPreferences)
            in PAGE_TURN_SETTINGS_KEYS -> PageTurnSettingsScreen(args.setting)
            in READING_LAYOUT_SETTINGS_KEYS -> ReadingSettingsScreen(args.setting)
            in FEED_SETTINGS_KEYS -> FeedSettingsScreen(args.setting)
            else -> AppearanceSettingsScreen(args.setting, reloadPreferences)
        }
    }
    entry<Account.NavigationSettings>(swipeDismiss = swipeBack) { args ->
        NavigationSettingsScreen(args.setting, reloadPreferences)
    }
    entry<Account.RecommendSettings>(swipeDismiss = swipeBack) { args ->
        if (args.setting in FEED_SETTINGS_KEYS) FeedSettingsScreen(args.setting) else ContentFilterSettingsScreen(args.setting)
    }
    entry<Account.FeedSettings>(swipeDismiss = swipeBack) { args ->
        FeedSettingsScreen(args.setting)
    }
    entry<Account.IdentityManagement>(swipeDismiss = swipeBack) {
        IdentityManagementScreen()
    }
    entry<Account.SystemAndUpdateSettings>(swipeDismiss = swipeBack) { args ->
        SystemAndUpdateSettingsScreen(
            setting = args.setting,
        )
    }
    entry<Account.ReadingSettings>(swipeDismiss = swipeBack) { args ->
        if (args.setting in PAGE_TURN_SETTINGS_KEYS) PageTurnSettingsScreen(args.setting) else ReadingSettingsScreen(args.setting)
    }
    entry<Account.PageTurnSettings>(swipeDismiss = swipeBack) { args ->
        PageTurnSettingsScreen(args.setting)
    }
    entry<Account.ReadingPlaybackSettings>(swipeDismiss = swipeBack) { args ->
        ReadingPlaybackSettingsScreen(args.setting)
    }
    entry<Account.WebDavSync>(swipeDismiss = swipeBack) {
        WebDavSyncScreen()
    }
    entry<Account.SettingsSearch>(swipeDismiss = swipeBack) {
        SettingsSearchScreen()
    }
    entry<Account.OpenSourceLicenses>(swipeDismiss = swipeBack) {
        OpenSourceLicensesScreen()
    }
    entry<Account.DeveloperSettings>(swipeDismiss = swipeBack) {
        AccountToolsScreen()
    }
}

fun AppNavEntryBuilder.mainNotificationEntries(swipeBack: NavSwipeDirection) {
    entry<Notification>(swipeDismiss = swipeBack) {
        NotificationScreen()
    }
    entry<Notification.Entry>(swipeDismiss = swipeBack) { entry ->
        NotificationTimelineScreen(entry.entryName, entry.title)
    }
    entry<Notification.Invitations>(swipeDismiss = swipeBack) {
        NotificationTimelineScreen("invite", "邀请回答")
    }
    entry<Notification.Message>(swipeDismiss = swipeBack) { args ->
        PrivateMessageScreen(args)
    }
    entry<Notification.NotificationSettings>(swipeDismiss = swipeBack) { args ->
        NotificationSettingsScreen(
            setting = args.setting,
        )
    }
}

fun AppNavEntryBuilder.detailNotificationEntries(swipeBack: NavSwipeDirection) {
    entry<Notification.NotificationSettings>(swipeDismiss = swipeBack) { args ->
        NotificationSettingsScreen(setting = args.setting)
    }
    entry<Notification.Message>(swipeDismiss = swipeBack) { args ->
        PrivateMessageScreen(args)
    }
}
