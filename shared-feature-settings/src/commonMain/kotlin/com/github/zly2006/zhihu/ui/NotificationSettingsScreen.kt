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

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.notification.HOME_NOTIFICATION_READ_UUIDS_PREFERENCE_KEY
import com.github.zly2006.zhihu.notification.NotificationType
import com.github.zly2006.zhihu.notification.rememberNotificationSettingsStore
import com.github.zly2006.zhihu.platform.SettingsStore
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.ui.components.AppDialog
import com.github.zly2006.zhihu.ui.components.AppSettingsPage
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.ui.components.SettingItem
import com.github.zly2006.zhihu.ui.components.SettingItemGroup
import com.github.zly2006.zhihu.ui.components.SettingItemWithSwitch
import com.github.zly2006.zhihu.ui.components.AppIcon as Icon

const val NOTIFICATION_SETTINGS_BACK_BUTTON_TAG = "notification_settings_back_button"
const val NOTIFICATION_SETTINGS_RESET_READ_RECORDS_TAG = "notification_settings_reset_read_records"

@Composable
fun NotificationSettingsScreen(
    setting: String? = null,
) {
    val settingsStore = rememberNotificationSettingsStore()
    val settings = rememberSettingsStore()
    val userMessages = rememberUserMessageSink()
    var showClearReadRecordsDialog by remember { mutableStateOf(false) }
    var readRecordCount by remember(settings) { mutableIntStateOf(countReadRecords(settings)) }

    var systemNotificationSettings by remember {
        mutableStateOf(NotificationType.entries.associateWith { settingsStore.getSystemNotificationEnabled(it) })
    }
    var displayInAppSettings by remember {
        mutableStateOf(NotificationType.entries.associateWith { settingsStore.getDisplayInAppEnabled(it) })
    }
    var autoMarkAsRead by remember { mutableStateOf(settingsStore.getAutoMarkAsReadEnabled()) }
    var unreadBadgeEnabled by remember { mutableStateOf(settingsStore.getUnreadBadgeEnabled()) }

    AppSettingsPage(
        title = "通知设置",
        onBack = LocalNavigator.current.onNavigateBack,
        backTag = NOTIFICATION_SETTINGS_BACK_BUTTON_TAG,
        highlightedKey = setting.orEmpty(),
        pageTurnEnabled = !showClearReadRecordsDialog,
    ) {
        SettingItemGroup(title = "阅读行为") {
            SettingItemWithSwitch(
                title = { Text("打开通知自动已读") },
                description = { Text("进入通知板块后，自动把当前查看的板块标记为已读") },
                checked = autoMarkAsRead,
                onCheckedChange = { checked ->
                    autoMarkAsRead = checked
                    settingsStore.setAutoMarkAsReadEnabled(checked)
                },
                settingKey = "autoMarkAsRead",
            )
            SettingItemWithSwitch(
                title = { Text("显示未读红点") },
                checked = unreadBadgeEnabled,
                onCheckedChange = { checked ->
                    unreadBadgeEnabled = checked
                    settingsStore.setUnreadBadgeEnabled(checked)
                },
                settingKey = "unreadBadge",
            )
        }

        SettingItemGroup(
            title = "系统消息通知",
            settingKey = "systemNotifications",
            footer = { Text("暂不支持系统消息通知。请在应用内通知页查看消息；已有开关配置仍会保留。") },
        ) {
            NotificationType.entries.forEach { type ->
                SettingItemWithSwitch(
                    title = { Text(type.displayName) },
                    checked = systemNotificationSettings[type] ?: false,
                    enabled = false,
                    onCheckedChange = { checked ->
                        systemNotificationSettings = systemNotificationSettings + (type to checked)
                        settingsStore.setSystemNotificationEnabled(type, checked)
                    },
                )
            }
        }

        SettingItemGroup(
            title = "应用内显示",
            footer = { Text("选择在通知页面显示哪些通知") },
            settingKey = "displayInAppNotifications",
        ) {
            NotificationType.entries.forEach { type ->
                SettingItemWithSwitch(
                    title = { Text(type.displayName) },
                    checked = displayInAppSettings[type] ?: true,
                    onCheckedChange = { checked ->
                        displayInAppSettings = displayInAppSettings + (type to checked)
                        settingsStore.setDisplayInAppEnabled(type, checked)
                    },
                )
            }
        }

        SettingItemGroup(title = "已读记录") {
            SettingItem(
                title = { Text("首页公告与作者想法推送") },
                description = { Text(if (readRecordCount > 0) "已读 $readRecordCount 条，重置后重新显示为未读" else "暂无已读记录") },
                icon = { Icon(Icons.Default.RestartAlt, null) },
                settingKey = "resetReadRecords",
                endAction = {},
                bottomAction = {
                    AppTextButton(
                        text = "重置",
                        onClick = { showClearReadRecordsDialog = true },
                        modifier = Modifier.testTag(NOTIFICATION_SETTINGS_RESET_READ_RECORDS_TAG),
                        enabled = readRecordCount > 0,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
        }
    }

    if (showClearReadRecordsDialog) {
        AppDialog(
            onDismissRequest = { showClearReadRecordsDialog = false },
            title = "重置 $readRecordCount 条已读记录？",
            summary = "首页公告和作者想法推送会重新显示为未读。此操作无法撤销。",
            confirmText = "重置",
            onConfirm = {
                settings.remove(HOME_NOTIFICATION_READ_UUIDS_PREFERENCE_KEY)
                settings.removeByPrefix(HOME_PIN_ANNOUNCEMENT_READ_KEY_PREFIX)
                readRecordCount = countReadRecords(settings)
                userMessages.showShortMessage("已重置首页公告和作者想法推送的已读记录")
                showClearReadRecordsDialog = false
            },
            dismissText = "取消",
            destructive = true,
        )
    }
}

/** 首页公告的已读记录存成一个集合，作者想法推送每条一个键。 */
private fun countReadRecords(settings: SettingsStore): Int =
    settings.getStringSet(HOME_NOTIFICATION_READ_UUIDS_PREFERENCE_KEY, emptySet()).size +
        settings.allEntries().keys.count { it.startsWith(HOME_PIN_ANNOUNCEMENT_READ_KEY_PREFIX) }
