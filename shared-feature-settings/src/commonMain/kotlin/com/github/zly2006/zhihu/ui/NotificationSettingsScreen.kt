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
import androidx.compose.runtime.collectAsState
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
import com.github.zly2006.zhihu.notification.rememberSystemNotificationControl
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

    val systemNotifications = rememberSystemNotificationControl()
    val systemStatus by systemNotifications.status.collectAsState()
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
            footer = {
                Text(
                    if (!systemStatus.supported) {
                        systemStatus.error ?: systemStatus.availabilityDescription ?: "此平台暂不支持系统通知，请在应用内通知页查看消息。"
                    } else {
                        val schedule = systemStatus.availabilityDescription ?: if (systemStatus.backgroundPollingAvailable) {
                            "后台检查间隔至少 15 分钟，省电模式可能延迟提醒。"
                        } else {
                            "仅在应用运行时检查新消息。"
                        }
                        "$schedule 首次开启不提醒旧消息，退出或切换账号后自动关闭。"
                    },
                )
            },
        ) {
            if (systemStatus.supported) {
                SettingItem(
                    title = { Text("系统授权与通知通道") },
                    description = {
                        Text(
                            systemStatus.error ?: when {
                                !systemStatus.signedIn -> "请先登录，再单独开启需要的通知类型"
                                !systemStatus.permissionGranted -> "尚未允许系统通知"
                                !systemStatus.appNotificationsEnabled -> "系统已关闭本应用的通知"
                                systemStatus.blockedChannels.any { it in systemStatus.enabledTypes } -> "部分已订阅类型的系统通道已关闭"
                                systemStatus.enabledTypes.isEmpty() -> "尚未订阅通知类型"
                                else -> "已订阅 ${systemStatus.enabledTypes.size} 种通知"
                            },
                        )
                    },
                    onClick = systemNotifications::openSystemSettings,
                )
            }
            NotificationType.entries.forEach { type ->
                SettingItemWithSwitch(
                    title = { Text(type.displayName) },
                    description = {
                        Text(
                            when {
                                !systemStatus.supported -> "暂不支持"
                                type in systemStatus.blockedChannels -> "系统通道已关闭，订阅不会产生通知"
                                type in systemStatus.enabledTypes && (!systemStatus.permissionGranted || !systemStatus.appNotificationsEnabled) -> "已订阅，但系统未允许通知"
                                type in systemStatus.enabledTypes -> "已订阅；首次同步不提醒"
                                else -> "未订阅"
                            },
                        )
                    },
                    checked = type in systemStatus.enabledTypes,
                    enabled = systemStatus.supported && systemStatus.signedIn,
                    onCheckedChange = { systemNotifications.setTypeEnabled(type, it) },
                    settingKey = "systemNotification_${type.name}",
                )
            }
        }

        SettingItemGroup(
            title = "应用内显示",
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
