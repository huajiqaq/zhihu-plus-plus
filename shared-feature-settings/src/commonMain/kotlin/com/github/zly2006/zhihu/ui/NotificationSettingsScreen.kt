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
import com.github.zly2006.zhihu.i18n.NotificationsStrings
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.notification.HOME_NOTIFICATION_READ_UUIDS_PREFERENCE_KEY
import com.github.zly2006.zhihu.notification.NotificationType
import com.github.zly2006.zhihu.notification.rememberNotificationSettingsStore
import com.github.zly2006.zhihu.notification.rememberSystemNotificationControl
import com.github.zly2006.zhihu.platform.SettingsStore
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.ui.components.AppDialog
import com.github.zly2006.zhihu.ui.components.AppIcon
import com.github.zly2006.zhihu.ui.components.AppSettingsPage
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.ui.components.SettingItem
import com.github.zly2006.zhihu.ui.components.SettingItemGroup
import com.github.zly2006.zhihu.ui.components.SettingItemWithSwitch

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
        title = NotificationsStrings.settingsTitle,
        onBack = LocalNavigator.current.onNavigateBack,
        backTag = NOTIFICATION_SETTINGS_BACK_BUTTON_TAG,
        highlightedKey = setting.orEmpty(),
        pageTurnEnabled = !showClearReadRecordsDialog,
    ) {
        SettingItemGroup(title = NotificationsStrings.readingBehavior) {
            SettingItemWithSwitch(
                title = { Text(NotificationsStrings.autoRead) },
                description = { Text(NotificationsStrings.autoReadSummary) },
                checked = autoMarkAsRead,
                onCheckedChange = { checked ->
                    autoMarkAsRead = checked
                    settingsStore.setAutoMarkAsReadEnabled(checked)
                },
                settingKey = "autoMarkAsRead",
            )
            SettingItemWithSwitch(
                title = { Text(NotificationsStrings.unreadBadges) },
                checked = unreadBadgeEnabled,
                onCheckedChange = { checked ->
                    unreadBadgeEnabled = checked
                    settingsStore.setUnreadBadgeEnabled(checked)
                },
                settingKey = "unreadBadge",
            )
        }

        SettingItemGroup(
            title = NotificationsStrings.systemTitle,
            settingKey = "systemNotifications",
            footer = {
                Text(
                    if (!systemStatus.supported) {
                        systemStatus.error ?: systemStatus.availabilityDescription ?: NotificationsStrings.systemUnsupportedSummary
                    } else {
                        val schedule = systemStatus.availabilityDescription ?: if (systemStatus.backgroundPollingAvailable) {
                            NotificationsStrings.backgroundSchedule
                        } else {
                            NotificationsStrings.runningSchedule
                        }
                        NotificationsStrings.subscriptionScheduleSummary(schedule)
                    },
                )
            },
        ) {
            if (systemStatus.supported) {
                SettingItem(
                    title = { Text(NotificationsStrings.systemPermissionTitle) },
                    description = {
                        Text(
                            systemStatus.error ?: when {
                                !systemStatus.signedIn -> NotificationsStrings.systemSignInHint
                                !systemStatus.permissionGranted -> NotificationsStrings.systemPermissionMissing
                                !systemStatus.appNotificationsEnabled -> NotificationsStrings.systemDisabled
                                systemStatus.blockedChannels.any { it in systemStatus.enabledTypes } -> NotificationsStrings.systemSomeChannelsDisabled
                                systemStatus.enabledTypes.isEmpty() -> NotificationsStrings.systemNoSubscriptions
                                else -> NotificationsStrings.systemSubscriptionCount(systemStatus.enabledTypes.size)
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
                                !systemStatus.supported -> NotificationsStrings.unsupported
                                type in systemStatus.blockedChannels -> NotificationsStrings.channelDisabledSummary
                                type in systemStatus.enabledTypes && (!systemStatus.permissionGranted || !systemStatus.appNotificationsEnabled) -> NotificationsStrings.subscribedPermissionMissing
                                type in systemStatus.enabledTypes -> NotificationsStrings.subscribedInitialSilent
                                else -> NotificationsStrings.notSubscribed
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
            title = NotificationsStrings.inAppDisplay,
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

        SettingItemGroup(title = NotificationsStrings.readRecords) {
            SettingItem(
                title = { Text(NotificationsStrings.homeAnnouncements) },
                description = { Text(if (readRecordCount > 0) NotificationsStrings.readRecordCount(readRecordCount) else NotificationsStrings.readRecordsEmpty) },
                icon = { AppIcon(Icons.Default.RestartAlt, null) },
                settingKey = "resetReadRecords",
                endAction = {},
                bottomAction = {
                    AppTextButton(
                        text = NotificationsStrings.reset,
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
            title = NotificationsStrings.resetReadTitle(readRecordCount),
            summary = NotificationsStrings.resetReadSummary,
            confirmText = NotificationsStrings.reset,
            onConfirm = {
                settings.remove(HOME_NOTIFICATION_READ_UUIDS_PREFERENCE_KEY)
                settings.removeByPrefix(HOME_PIN_ANNOUNCEMENT_READ_KEY_PREFIX)
                readRecordCount = countReadRecords(settings)
                userMessages.showShortMessage(NotificationsStrings.resetReadSuccess)
                showClearReadRecordsDialog = false
            },
            dismissText = NotificationsStrings.cancel,
            destructive = true,
        )
    }
}

/** 首页公告的已读记录存成一个集合，作者想法推送每条一个键。 */
private fun countReadRecords(settings: SettingsStore): Int =
    settings.getStringSet(HOME_NOTIFICATION_READ_UUIDS_PREFERENCE_KEY, emptySet()).size +
        settings.snapshot().keys.count { it.startsWith(HOME_PIN_ANNOUNCEMENT_READ_KEY_PREFIX) }
