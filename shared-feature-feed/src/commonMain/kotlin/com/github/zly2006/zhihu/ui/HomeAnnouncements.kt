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
import androidx.compose.material.icons.filled.ArrowCircleUp
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.github.zly2006.zhihu.navigation.Account
import com.github.zly2006.zhihu.navigation.Navigator
import com.github.zly2006.zhihu.navigation.Pin
import com.github.zly2006.zhihu.navigation.WebPage
import com.github.zly2006.zhihu.notification.HOME_NOTIFICATION_REFRESH_INTERVAL_MILLIS
import com.github.zly2006.zhihu.notification.HomeNotificationAction
import com.github.zly2006.zhihu.notification.OnlineHomeNotificationRepository
import com.github.zly2006.zhihu.platform.SettingsStore
import com.github.zly2006.zhihu.platform.isLiteVariant
import com.github.zly2006.zhihu.theme.miuixThemeAvailable
import com.github.zly2006.zhihu.ui.subscreens.SystemUpdateState
import com.github.zly2006.zhihu.util.Log
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.time.Clock

private const val MAX_HOME_PIN_ANNOUNCEMENTS = 3

/** 前台加载事项。 */
@Composable
internal fun HomeAnnouncementLoadEffects(
    homeState: HomeScreenState,
    lifecycleOwner: LifecycleOwner,
    scrollToTopTrigger: Int,
    paginationEnvironment: PaginationEnvironment,
    settings: SettingsStore,
    versionName: String?,
    onlineNotificationRepository: OnlineHomeNotificationRepository,
) {
    var onlineNotifications by homeState.onlineNotifications
    var authorPinAnnouncements by homeState.authorPinAnnouncements
    LaunchedEffect(lifecycleOwner, scrollToTopTrigger) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val loadedAnnouncements = try {
                paginationEnvironment
                    .fetchJson(ZHIHU_PLUS_AUTHOR_PINS_URL, "")
                    ?.let(::decodeHomePinAnnouncements)
                    ?.filter {
                        it.kind == HomePinAnnouncementKind.Poll ||
                            it.createdAtEpochSeconds >= Clock.System.now().epochSeconds - 5 * 24 * 60 * 60
                    }?.filterNot { settings.getBoolean(homePinAnnouncementReadKey(it.pinId), false) }
                    ?.take(MAX_HOME_PIN_ANNOUNCEMENTS)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("HomeScreen", "Failed to load home pin announcements", e)
                null
            }
            if (loadedAnnouncements != null) {
                authorPinAnnouncements = loadedAnnouncements
            }
        }
    }

    LaunchedEffect(lifecycleOwner, paginationEnvironment, versionName) {
        if (versionName != null) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    try {
                        onlineNotifications = onlineNotificationRepository
                            .load(
                                versionName = versionName,
                                httpClient = paginationEnvironment.httpClient(),
                            ).associateBy { it.uuid }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e("HomeScreen", "Failed to load online notifications", e)
                    }
                    delay(HOME_NOTIFICATION_REFRESH_INTERVAL_MILLIS)
                }
            }
        }
    }
}

/** 事项。 */
internal fun buildHomeTransactions(
    homeState: HomeScreenState,
    showNewUiNotice: Boolean,
    dismissNewUiNotice: () -> Unit,
    updateAnnouncement: SystemUpdateState.UpdateAvailable?,
    onlineNotificationRepository: OnlineHomeNotificationRepository,
    settings: SettingsStore,
    navigator: Navigator,
): List<HomeTransaction> {
    var onlineNotifications by homeState.onlineNotifications
    var dismissedUpdateVersion by homeState.dismissedUpdateVersion
    var authorPinAnnouncements by homeState.authorPinAnnouncements
    return buildList {
        if (showNewUiNotice && !isLiteVariant) {
            add(
                HomeTransaction(
                    key = NEW_UI_NOTICE_TAG,
                    title = "可切换 UI 风格",
                    content = null,
                    acceptText = "切换皮肤".takeIf { miuixThemeAvailable },
                    onAccept = if (miuixThemeAvailable) {
                        {
                            dismissNewUiNotice()
                            navigator.onNavigate(Account.AppearanceSettings())
                        }
                    } else {
                        null
                    },
                    dismissText = "知道了",
                    onDismiss = dismissNewUiNotice,
                ),
            )
        }
        updateAnnouncement?.takeIf { dismissedUpdateVersion != it.version }?.let { update ->
            add(
                HomeTransaction(
                    key = "update:${update.version}",
                    title = "发现新版本：${update.version}${if (update.isNightly) " (Nightly)" else ""}",
                    content = "查看更新内容和安装选项。",
                    icon = Icons.Default.ArrowCircleUp,
                    acceptText = "查看更新",
                    onAccept = { navigator.onNavigate(Account.SystemAndUpdateSettings()) },
                    dismissText = "以后",
                    onDismiss = { dismissedUpdateVersion = update.version },
                ),
            )
        }
        onlineNotifications.values.forEach { notification ->
            val markRead = {
                onlineNotificationRepository.markRead(notification)
                onlineNotifications = onlineNotifications - notification.uuid
            }
            add(
                HomeTransaction(
                    key = homeOnlineNotificationTag(notification.uuid),
                    title = notification.title,
                    content = notification.content,
                    icon = Icons.Default.Notifications,
                    acceptText = notification.accept?.let { accept ->
                        if ((accept.action as? HomeNotificationAction.Navigate)?.destination is WebPage) "打开网页" else accept.text
                    },
                    onAccept = notification.accept?.let { accept ->
                        {
                            val action = accept.action
                            if (action != null) {
                                when (action) {
                                    is HomeNotificationAction.Navigate -> navigator.onNavigate(action.destination)
                                    is HomeNotificationAction.SetBoolean -> settings.putBoolean(action.name, action.value)
                                    is HomeNotificationAction.SetString -> settings.putString(action.name, action.value)
                                    is HomeNotificationAction.SetInt -> settings.putInt(action.name, action.value)
                                }
                                markRead()
                            }
                        }
                    },
                    dismissText = notification.dismiss,
                    onDismiss = markRead,
                ),
            )
        }
        authorPinAnnouncements.forEach { announcement ->
            val markRead = {
                settings.putBoolean(homePinAnnouncementReadKey(announcement.pinId), true)
                authorPinAnnouncements = authorPinAnnouncements.filterNot { it.pinId == announcement.pinId }
            }
            add(
                HomeTransaction(
                    key = homeAuthorPollAnnouncementTag(announcement.pinId),
                    title = if (announcement.kind == HomePinAnnouncementKind.Poll) "请给未来的知乎++提出建议" else "知乎++新动态",
                    content = buildString {
                        append(announcement.title)
                        val details = buildList {
                            if (announcement.optionCount > 0) add("${announcement.optionCount} 个选项")
                            if (announcement.memberCount > 0) add("${announcement.memberCount} 人已参与")
                        }
                        if (details.isNotEmpty()) append("\n${details.joinToString(" · ")}")
                    },
                    icon = Icons.Default.Flag,
                    acceptText = if (announcement.kind == HomePinAnnouncementKind.Poll) "去投票" else "查看",
                    onAccept = {
                        markRead()
                        navigator.onNavigate(Pin(announcement.pinId))
                    },
                    dismissText = "关闭",
                    onDismiss = markRead,
                ),
            )
        }
    }
}
