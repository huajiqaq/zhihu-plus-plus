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

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MarkChatRead
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.zly2006.zhihu.account.rememberZhihuAccountStore
import com.github.zly2006.zhihu.i18n.NotificationsStrings
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.Notification
import com.github.zly2006.zhihu.notification.rememberNotificationSettingsStore
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.ui.components.AppFilledTonalIconButton
import com.github.zly2006.zhihu.ui.components.AppIcon
import com.github.zly2006.zhihu.ui.components.AppIconButton
import com.github.zly2006.zhihu.ui.components.AppPageTitleBar
import com.github.zly2006.zhihu.ui.components.AppPullToRefreshBox
import com.github.zly2006.zhihu.ui.components.AppScaffold
import com.github.zly2006.zhihu.ui.components.PaginatedList
import com.github.zly2006.zhihu.ui.components.ProgressIndicatorFooter
import com.github.zly2006.zhihu.ui.components.appPageTitleBarScroll
import com.github.zly2006.zhihu.ui.components.rememberAppPageTitleBarScrollState
import com.github.zly2006.zhihu.viewmodel.NotificationViewModel
import com.github.zly2006.zhihu.viewmodel.shouldShowNotification
import kotlinx.coroutines.launch

const val NOTIFICATION_SCREEN_BACK_BUTTON_TAG = "notification_screen_back_button"

/** 分类显示与自动已读由通知偏好控制，刷新不能改变默认开关。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationScreen() {
    val titleBarScrollState = rememberAppPageTitleBarScrollState()
    val navigator = LocalNavigator.current
    val settingsStore = rememberNotificationSettingsStore()
    val accountStore = rememberZhihuAccountStore()
    val accountClient = accountStore.client
    var markingAllRead by remember(accountClient) { mutableStateOf(false) }
    val viewModel = viewModel { NotificationViewModel() }
    val environment = rememberNotificationEnvironment(settingsStore)
    val coroutineScope = rememberCoroutineScope()
    val userMessages = rememberUserMessageSink()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner, environment, accountClient) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refresh(environment)
        }
    }

    AppScaffold(
        modifier = Modifier.fillMaxSize().appPageTitleBarScroll(titleBarScrollState),
        topBar = {
            AppPageTitleBar(
                title = NotificationsStrings.messages,
                onBack = navigator.onNavigateBack,
                backTag = NOTIFICATION_SCREEN_BACK_BUTTON_TAG,
                scrollState = titleBarScrollState,
                actions = {
                    if (viewModel.unreadCount > 0 || markingAllRead) {
                        AppFilledTonalIconButton(
                            enabled = !markingAllRead,
                            onClick = {
                                if (!markingAllRead && accountStore.client === accountClient) {
                                    markingAllRead = true
                                    coroutineScope.launch {
                                        try {
                                            val succeeded = viewModel.markAllAsRead(environment)
                                            if (accountStore.client === accountClient) {
                                                userMessages.showMessage(if (succeeded) NotificationsStrings.markCategoriesReadSuccess else NotificationsStrings.markCategoriesReadPartial)
                                            }
                                        } finally {
                                            markingAllRead = false
                                        }
                                    }
                                }
                            },
                        ) {
                            AppIcon(Icons.Default.MarkChatRead, contentDescription = NotificationsStrings.markCategoriesRead)
                        }
                    }
                    AppIconButton(onClick = { navigator.onNavigate(Notification.NotificationSettings()) }) {
                        AppIcon(Icons.Default.Settings, contentDescription = NotificationsStrings.settingsTitle)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { paddingValues ->
        AppPullToRefreshBox(
            isRefreshing = viewModel.isLoading,
            onRefresh = { viewModel.refresh(environment) },
            modifier = Modifier.padding(paddingValues),
        ) {
            PaginatedList(
                items = viewModel.allData,
                onLoadMore = { viewModel.loadMore(environment) },
                isEnd = { viewModel.isEnd },
                isLoading = { viewModel.isLoading },
                loadError = { viewModel.errorMessage },
                onRetry = { viewModel.retry(environment) },
                modifier = Modifier.fillMaxSize(),
                footer = ProgressIndicatorFooter,
                key = { it.stableId },
                topContent = {
                    item(key = "notification_categories") {
                        NotificationCategoryRow(
                            unreadCounts = viewModel.categoryUnreadCounts,
                            showUnreadBadges = settingsStore.getUnreadBadgeEnabled(),
                            onCategoryClick = { category ->
                                navigator.onNavigate(
                                    Notification.Entry(category.entryName, title = ""),
                                )
                            },
                        )
                    }
                    viewModel.invitation?.let { invitation ->
                        item(key = "notification_invitation") {
                            NotificationInvitationRow(
                                invitation = invitation,
                                showUnreadBadge = settingsStore.getUnreadBadgeEnabled(),
                                onClick = { navigator.onNavigate(Notification.Invitations) },
                            )
                        }
                    }
                    item(key = "notification_messages_divider") {
                        HorizontalDivider(
                            thickness = 8.dp,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                        )
                    }
                },
            ) { notification ->
                if (settingsStore.shouldShowNotification(notification)) {
                    NotificationConversationRow(notification, showUnreadBadge = settingsStore.getUnreadBadgeEnabled())
                }
            }
        }
    }
}
