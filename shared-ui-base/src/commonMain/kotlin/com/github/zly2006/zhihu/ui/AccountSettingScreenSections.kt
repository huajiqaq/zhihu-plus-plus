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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import com.github.zly2006.zhihu.i18n.ReaderStrings
import com.github.zly2006.zhihu.navigation.Account
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.theme.LocalAppTheme
import com.github.zly2006.zhihu.theme.ThemeMode
import com.github.zly2006.zhihu.theme.miuixThemeAvailable
import com.github.zly2006.zhihu.ui.components.AppAlertDialog
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppIcon
import com.github.zly2006.zhihu.ui.components.AppPageHeader
import com.github.zly2006.zhihu.ui.components.AppSearchBar
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.ui.components.SettingItem
import com.github.zly2006.zhihu.ui.components.SettingItemGroup
import com.github.zly2006.zhihu.ui.subscreens.SystemUpdateState

@Composable
internal fun AccountSettingSecuritySection(
    loggedIn: Boolean,
    onNavigate: (NavDestination) -> Unit,
) {
    SettingItemGroup(title = ReaderStrings.accountSecurity) {
        if (loggedIn) {
            SettingItem(
                title = { Text(ReaderStrings.accountsIdentities) },
                description = { Text(ReaderStrings.accountsIdentitiesDescription) },
                icon = { AppIcon(Icons.Default.SwitchAccount, null) },
                modifier = Modifier.testTag(ACCOUNT_SETTINGS_IDENTITY_MANAGEMENT_TAG),
                onClick = { onNavigate(Account.IdentityManagement) },
            )
        }
        SettingItem(
            title = { Text(ReaderStrings.accountMaintenance) },
            description = { Text(ReaderStrings.accountMaintenanceDescription) },
            icon = { AppIcon(Icons.Default.ManageAccounts, null) },
            modifier = Modifier.testTag(ACCOUNT_SETTINGS_DEVELOPER_TAG),
            onClick = { onNavigate(Account.AccountTools) },
        )
    }
}

@Composable
internal fun AccountSettingDisplaySection(
    fontSize: Int,
    lineHeight: Int,
    readingPlayerSupported: Boolean,
    onNavigate: (NavDestination) -> Unit,
) {
    SettingItemGroup(title = ReaderStrings.displayReading) {
        SettingItem(
            title = { Text(ReaderStrings.appearance) },
            description = { Text(appearanceSummary()) },
            icon = { AppIcon(Icons.Default.Palette, null) },
            modifier = Modifier.testTag(ACCOUNT_SETTINGS_APPEARANCE_TAG),
            onClick = { onNavigate(Account.AppearanceSettings()) },
        )
        SettingItem(
            title = { Text(ReaderStrings.navigationInteraction) },
            description = { Text(ReaderStrings.navigationDescription) },
            icon = { AppIcon(Icons.Default.Explore, null) },
            modifier = Modifier.testTag("accountSettings.navigation"),
            onClick = { onNavigate(Account.NavigationSettings()) },
        )
        SettingItem(
            title = { Text(ReaderStrings.readingLayout) },
            description = { Text(ReaderStrings.readingLayoutSummary(fontSize, lineHeight)) },
            icon = { AppIcon(Icons.Default.Book, null) },
            modifier = Modifier.testTag("accountSettings.readingLayout"),
            onClick = { onNavigate(Account.ReadingSettings()) },
        )
        if (readingPlayerSupported) {
            SettingItem(
                title = { Text(ReaderStrings.readingPlayback) },
                description = { Text(ReaderStrings.readingPlaybackDescription) },
                icon = { AppIcon(Icons.AutoMirrored.Filled.VolumeUp, null) },
                modifier = Modifier.testTag(ACCOUNT_SETTINGS_READING_TAG),
                onClick = { onNavigate(Account.ReadingPlaybackSettings()) },
            )
        }
    }
}

/** 外观入口的摘要：当前皮肤（只有能切皮肤的变体才显示）与主题模式。 */
@Composable
private fun appearanceSummary(): String {
    val mode = when (LocalAppTheme.current.snapshot.themeMode) {
        ThemeMode.SYSTEM -> ReaderStrings.themeSystem
        ThemeMode.LIGHT -> ReaderStrings.themeLight
        ThemeMode.DARK -> ReaderStrings.themeDark
    }
    return if (miuixThemeAvailable) "${LocalAppTheme.current.style.displayName} · $mode" else mode
}

@Composable
internal fun AccountSettingHeader() {
    AppPageHeader(
        title = ReaderStrings.accountSettings,
        subtitle = ReaderStrings.accountSettingsDescription,
        modifier = Modifier.testTag(ACCOUNT_SETTINGS_SCROLL_TAG + ":header"),
    )
}

@Composable
internal fun AccountSettingQuickAccessSection(
    data: AccountSettingsAccountState,
    showUnreadBadge: Boolean,
    unreadCount: Int,
    showHistoryShortcut: Boolean,
    onOpenCollections: (String) -> Unit,
    onOpenSubscriptions: () -> Unit,
    onOpenNotification: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    AccountQuickAccessRow(
        actions = buildList {
            data.urlToken?.let { token ->
                add(
                    AccountQuickAccessAction(
                        label = ReaderStrings.collections,
                        icon = Icons.Default.Bookmark,
                        testTag = ACCOUNT_SETTINGS_SHORTCUT_COLLECTIONS_TAG,
                        onClick = { onOpenCollections(token) },
                    ),
                )
            }
            add(
                AccountQuickAccessAction(
                    label = ReaderStrings.subscriptions,
                    icon = Icons.Default.Groups,
                    testTag = ACCOUNT_SETTINGS_SHORTCUT_SUBSCRIPTIONS_TAG,
                    onClick = onOpenSubscriptions,
                ),
            )
            add(
                AccountQuickAccessAction(
                    label = ReaderStrings.notifications,
                    icon = Icons.Default.Notifications,
                    testTag = ACCOUNT_SETTINGS_SHORTCUT_NOTIFICATION_TAG,
                    badgeCount = if (showUnreadBadge) unreadCount else 0,
                    onClick = onOpenNotification,
                ),
            )
            if (showHistoryShortcut) {
                add(
                    AccountQuickAccessAction(
                        label = ReaderStrings.browsingHistory,
                        icon = Icons.Default.History,
                        testTag = ACCOUNT_SETTINGS_SHORTCUT_HISTORY_TAG,
                        /** 历史不在底栏时走普通页面，避免顶层导航回退到主页。 */
                        onClick = onOpenHistory,
                    ),
                )
            }
        },
    )
}

@Composable
internal fun AccountSettingLoginSection(
    showHistoryShortcut: Boolean,
    onLogin: () -> Unit,
    onOpenHistory: () -> Unit,
    description: String? = null,
) {
    SettingItemGroup {
        SettingItem(
            title = { Text(ReaderStrings.signInZhihu) },
            description = description?.let { { Text(it) } },
            icon = { AppIcon(Icons.AutoMirrored.Filled.Login, null) },
            modifier = Modifier.testTag(ACCOUNT_SETTINGS_LOGIN_ITEM_TAG),
            onClick = onLogin,
        )
        if (showHistoryShortcut) {
            SettingItem(
                title = { Text(ReaderStrings.browsingHistory) },
                description = { Text(ReaderStrings.historySignInNotice) },
                icon = { AppIcon(Icons.Default.History, null) },
                modifier = Modifier.testTag(ACCOUNT_SETTINGS_SHORTCUT_HISTORY_TAG),
                onClick = onOpenHistory,
            )
        }
    }
}

@Composable
internal fun AccountSettingSearch(
    onClick: () -> Unit,
) {
    AppSearchBar(
        hint = ReaderStrings.searchSettings,
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = AppDimens.PageHorizontalPadding,
                vertical = AppDimens.Space8,
            ).testTag(ACCOUNT_SETTINGS_SEARCH_TAG),
    )
}

@Composable
internal fun AccountSettingContentSection(
    onOpenFeedSettings: () -> Unit,
    onOpenRecommendSettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
) {
    SettingItemGroup(title = ReaderStrings.contentPrivacy) {
        SettingItem(
            title = { Text(ReaderStrings.recommendationsFeed) },
            description = { Text(ReaderStrings.feedDescription) },
            icon = { AppIcon(Icons.Default.DynamicFeed, null) },
            modifier = Modifier.testTag("accountSettings.feed"),
            onClick = onOpenFeedSettings,
        )
        SettingItem(
            title = { Text(ReaderStrings.contentFiltering) },
            description = { Text(ReaderStrings.filteringDescription) },
            icon = { AppIcon(Icons.Default.FilterAlt, null) },
            modifier = Modifier.testTag(ACCOUNT_SETTINGS_RECOMMEND_TAG),
            onClick = onOpenRecommendSettings,
        )
        SettingItem(
            title = { Text(ReaderStrings.notificationSettings) },
            description = { Text(ReaderStrings.notificationsDescription) },
            icon = { AppIcon(Icons.Default.Notifications, null) },
            onClick = onOpenNotificationSettings,
        )
    }
}

@Composable
internal fun AccountSettingSystemSection(
    updateState: SystemUpdateState,
    onOpenSystemSettings: () -> Unit,
) {
    SettingItemGroup(title = ReaderStrings.system) {
        SettingItem(
            title = { Text(ReaderStrings.systemUpdates) },
            description = { SystemUpdateSummary(updateState) },
            icon = { AppIcon(Icons.Default.Settings, null) },
            modifier = Modifier.testTag(ACCOUNT_SETTINGS_SYSTEM_TAG),
            onClick = onOpenSystemSettings,
        )
    }
}

@Composable
private fun SystemUpdateSummary(state: SystemUpdateState) {
    when (state) {
        is SystemUpdateState.UpdateAvailable -> Text(
            ReaderStrings.updateAvailable(state.version),
            color = MaterialTheme.colorScheme.primary,
        )
        is SystemUpdateState.WebUpdateAvailable -> Text(
            ReaderStrings.updateAvailable(state.buildId.take(12)),
            color = MaterialTheme.colorScheme.primary,
        )
        is SystemUpdateState.Downloading -> Text(ReaderStrings.updateDownloading(state.update.version))
        is SystemUpdateState.Downloaded -> Text(
            ReaderStrings.updateReady(state.update.version),
            color = MaterialTheme.colorScheme.primary,
        )
        is SystemUpdateState.Error -> Text(
            ReaderStrings.updateFailed(state.message),
            color = MaterialTheme.colorScheme.error,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        else -> Text(ReaderStrings.systemUpdatesDescription)
    }
}

@Composable
internal fun AccountSettingLogoutDialog(
    accountCount: Int,
    error: String?,
    confirmEnabled: Boolean,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AppAlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(ReaderStrings.signOut) },
        text = {
            Column {
                Text(
                    if (accountCount > 1) {
                        ReaderStrings.signOutRemoveConfirmation
                    } else {
                        ReaderStrings.signOutConfirmation
                    },
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            AppTextButton(
                enabled = confirmEnabled,
                onClick = onConfirm,
            ) {
                Text(ReaderStrings.confirmSignOut)
            }
        },
        dismissButton = {
            AppTextButton(onClick = onCancel) {
                Text(ReaderStrings.cancel)
            }
        },
    )
}
