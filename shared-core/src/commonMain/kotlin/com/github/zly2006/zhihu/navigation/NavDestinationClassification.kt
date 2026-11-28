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

/** 问题可阅读，也承载回答列表；四类规则不互斥。 */
fun NavDestination?.readingDestinationOrNull(): NavDestination? = when (this) {
    is Article, is Pin, is Question -> this
    else -> null
}

fun NavDestination?.isListPaneDestination(): Boolean = when (this) {
    MainTabs, is Search, History, OnlineHistoryRoute, is Collections, is CollectionContent, is Question, is Person, is Topic,
    Notification, is Notification.Entry, Notification.Invitations, Account.RecommendSettings.BlockedFeedHistory,
    -> true
    else -> false
}

/** 问题保留列表身份；评论包装递归沿用其内容的普通详情分类。 */
fun NavDestination.isDetailPaneDestination(): Boolean = when (this) {
    is Article, is Pin -> true
    is CommentHolder -> article.isDetailPaneDestination()
    else -> false
}

/** 账号设置与通知二级页可作附属详情；屏蔽记录仍同时属于列表页。 */
fun NavDestination.isSecondaryDetailDestination(): Boolean = when (this) {
    is WebPage,
    is Account.AppearanceSettings,
    is Account.ReadingSettings,
    is Account.NavigationSettings,
    is Account.PageTurnSettings,
    is Account.ReadingPlaybackSettings,
    is Account.FeedSettings,
    Account.WebDavSync,
    is Account.RecommendSettings,
    Account.RecommendSettings.Blocklist,
    Account.RecommendSettings.BlockedFeedHistory,
    Account.IdentityManagement,
    is Account.SystemAndUpdateSettings,
    Account.SettingsSearch,
    Account.OpenSourceLicenses,
    Account.AccountTools,
    is Notification.NotificationSettings,
    is Notification.Message,
    -> true
    else -> false
}
