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
import com.github.zly2006.zhihu.navigation.Account
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.theme.ThemeManager
import com.github.zly2006.zhihu.theme.ThemeMode
import com.github.zly2006.zhihu.theme.miuixThemeAvailable
import com.github.zly2006.zhihu.ui.components.AppAlertDialog
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppPageHeader
import com.github.zly2006.zhihu.ui.components.AppSearchBar
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.ui.components.SettingItem
import com.github.zly2006.zhihu.ui.components.SettingItemGroup
import com.github.zly2006.zhihu.ui.subscreens.SystemUpdateState
import com.github.zly2006.zhihu.ui.components.AppIcon as Icon

@Composable
internal fun AccountSettingSecuritySection(
    loggedIn: Boolean,
    onNavigate: (NavDestination) -> Unit,
) {
    SettingItemGroup(title = "账号与安全") {
        if (loggedIn) {
            SettingItem(
                title = { Text("账号与身份") },
                description = { Text("切换账号、创建身份或移除已保存账号") },
                icon = { Icon(Icons.Default.SwitchAccount, null) },
                modifier = Modifier.testTag(ACCOUNT_SETTINGS_IDENTITY_MANAGEMENT_TAG),
                onClick = { onNavigate(Account.IdentityManagement) },
            )
        }
        SettingItem(
            title = { Text("账号与登录维护") },
            description = { Text("验证登录、刷新 Token 与导入 Cookie") },
            icon = { Icon(Icons.Default.ManageAccounts, null) },
            modifier = Modifier.testTag(ACCOUNT_SETTINGS_DEVELOPER_TAG),
            onClick = { onNavigate(Account.DeveloperSettings) },
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
    SettingItemGroup(title = "显示与阅读") {
        SettingItem(
            title = { Text("外观") },
            description = { Text(appearanceSummary()) },
            icon = { Icon(Icons.Default.Palette, null) },
            modifier = Modifier.testTag(ACCOUNT_SETTINGS_APPEARANCE_TAG),
            onClick = { onNavigate(Account.AppearanceSettings()) },
        )
        SettingItem(
            title = { Text("导航与交互") },
            description = { Text("启动页、底栏、返回手势、分享与搜索") },
            icon = { Icon(Icons.Default.Explore, null) },
            modifier = Modifier.testTag("accountSettings.navigation"),
            onClick = { onNavigate(Account.NavigationSettings()) },
        )
        SettingItem(
            title = { Text("阅读与排版") },
            description = { Text("字号 $fontSize% · 行高 $lineHeight%") },
            icon = { Icon(Icons.Default.Book, null) },
            modifier = Modifier.testTag("accountSettings.readingLayout"),
            onClick = { onNavigate(Account.ReadingSettings()) },
        )
        if (readingPlayerSupported) {
            SettingItem(
                title = { Text("朗读与播放") },
                description = { Text("朗读内容模板、评论、播放队列与条目过渡") },
                icon = { Icon(Icons.AutoMirrored.Filled.VolumeUp, null) },
                modifier = Modifier.testTag(ACCOUNT_SETTINGS_READING_TAG),
                onClick = { onNavigate(Account.ReadingPlaybackSettings()) },
            )
        }
    }
}

/** 外观入口的摘要：当前皮肤（只有能切皮肤的变体才显示）与主题模式。 */
@Composable
private fun appearanceSummary(): String {
    val mode = when (ThemeManager.getThemeMode()) {
        ThemeMode.SYSTEM -> "自动"
        ThemeMode.LIGHT -> "亮色"
        ThemeMode.DARK -> "暗色"
    }
    return if (miuixThemeAvailable) "${ThemeManager.getThemeStyle().displayName} · $mode" else mode
}

@Composable
internal fun AccountSettingHeader() {
    AppPageHeader(
        title = "账号与设置",
        subtitle = "管理账号、内容与界面",
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
                        label = "收藏夹",
                        icon = Icons.Default.Bookmark,
                        testTag = ACCOUNT_SETTINGS_SHORTCUT_COLLECTIONS_TAG,
                        onClick = { onOpenCollections(token) },
                    ),
                )
            }
            add(
                AccountQuickAccessAction(
                    label = "关注订阅",
                    icon = Icons.Default.Groups,
                    testTag = ACCOUNT_SETTINGS_SHORTCUT_SUBSCRIPTIONS_TAG,
                    onClick = onOpenSubscriptions,
                ),
            )
            add(
                AccountQuickAccessAction(
                    label = "通知",
                    icon = Icons.Default.Notifications,
                    testTag = ACCOUNT_SETTINGS_SHORTCUT_NOTIFICATION_TAG,
                    badgeCount = if (showUnreadBadge) unreadCount else 0,
                    onClick = onOpenNotification,
                ),
            )
            if (showHistoryShortcut) {
                add(
                    AccountQuickAccessAction(
                        label = "浏览历史",
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
) {
    SettingItemGroup {
        SettingItem(
            title = { Text("登录知乎") },
            icon = { Icon(Icons.AutoMirrored.Filled.Login, null) },
            modifier = Modifier.testTag(ACCOUNT_SETTINGS_LOGIN_ITEM_TAG),
            onClick = onLogin,
        )
        if (showHistoryShortcut) {
            SettingItem(
                title = { Text("浏览历史") },
                description = { Text("本地历史无需登录，在线历史需要登录") },
                icon = { Icon(Icons.Default.History, null) },
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
        hint = "搜索设置项",
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
    SettingItemGroup(title = "内容与隐私") {
        SettingItem(
            title = { Text("推荐与信息流") },
            description = { Text("推荐算法、首页卡片与刷新") },
            icon = { Icon(Icons.Default.DynamicFeed, null) },
            modifier = Modifier.testTag("accountSettings.feed"),
            onClick = onOpenFeedSettings,
        )
        SettingItem(
            title = { Text("内容过滤") },
            description = { Text("质量过滤、关键词与用户屏蔽、推广内容") },
            icon = { Icon(Icons.Default.FilterAlt, null) },
            modifier = Modifier.testTag(ACCOUNT_SETTINGS_RECOMMEND_TAG),
            onClick = onOpenRecommendSettings,
        )
        SettingItem(
            title = { Text("通知设置") },
            description = { Text("未读红点、系统通知与应用内显示") },
            icon = { Icon(Icons.Default.Notifications, null) },
            onClick = onOpenNotificationSettings,
        )
    }
}

@Composable
internal fun AccountSettingSystemSection(
    updateState: SystemUpdateState,
    onOpenSystemSettings: () -> Unit,
) {
    SettingItemGroup(title = "系统") {
        SettingItem(
            title = { Text("系统与更新") },
            description = { SystemUpdateSummary(updateState) },
            icon = { Icon(Icons.Default.Settings, null) },
            modifier = Modifier.testTag(ACCOUNT_SETTINGS_SYSTEM_TAG),
            onClick = onOpenSystemSettings,
        )
    }
}

@Composable
private fun SystemUpdateSummary(state: SystemUpdateState) {
    when (state) {
        is SystemUpdateState.UpdateAvailable -> Text(
            "发现新版本 ${state.version}",
            color = MaterialTheme.colorScheme.primary,
        )
        is SystemUpdateState.Downloading -> Text("正在下载 ${state.update.version}")
        is SystemUpdateState.Downloaded -> Text(
            "${state.update.version} 已下载，可以安装",
            color = MaterialTheme.colorScheme.primary,
        )
        is SystemUpdateState.Error -> Text(
            "更新失败：${state.message}",
            color = MaterialTheme.colorScheme.error,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        else -> Text("版本更新、更新通道与隐私")
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
        title = { Text("退出登录") },
        text = {
            Column {
                Text(
                    if (accountCount > 1) {
                        "确定退出并移除当前登录账号吗？退出后会自动切换到另一个已保存账号。"
                    } else {
                        "确定要退出登录吗？"
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
                Text("退出")
            }
        },
        dismissButton = {
            AppTextButton(onClick = onCancel) {
                Text("取消")
            }
        },
    )
}
