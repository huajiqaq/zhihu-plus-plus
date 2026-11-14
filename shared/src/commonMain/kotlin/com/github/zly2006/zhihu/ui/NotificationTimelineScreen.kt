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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.fleeksoft.ksoup.Ksoup
import com.github.zly2006.zhihu.account.rememberZhihuAccountStore
import com.github.zly2006.zhihu.data.MobileNotificationTimelineItem
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.navigation.WriteAnswer
import com.github.zly2006.zhihu.navigation.resolveContent
import com.github.zly2006.zhihu.notification.rememberNotificationSettingsStore
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.ui.components.AppButton
import com.github.zly2006.zhihu.ui.components.AppCompactTopBar
import com.github.zly2006.zhihu.ui.components.AppEmptyState
import com.github.zly2006.zhihu.ui.components.AppPullToRefreshBox
import com.github.zly2006.zhihu.ui.components.AppScaffold
import com.github.zly2006.zhihu.ui.components.PaginatedList
import com.github.zly2006.zhihu.ui.components.ProgressIndicatorFooter
import com.github.zly2006.zhihu.ui.components.pageTurnViewportWithGuide
import com.github.zly2006.zhihu.ui.components.rememberPageTurnTarget
import com.github.zly2006.zhihu.viewmodel.MobileNotificationCategory
import com.github.zly2006.zhihu.viewmodel.NotificationTimelineViewModel
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import com.github.zly2006.zhihu.ui.components.AppIcon as Icon

const val NOTIFICATION_TIMELINE_BACK_BUTTON_TAG = "notification_timeline_back_button"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationTimelineScreen(
    entryName: String,
    title: String,
) {
    val navigator = LocalNavigator.current
    val accountClient = rememberZhihuAccountStore().client
    val settingsStore = rememberNotificationSettingsStore()
    val environment = rememberNotificationEnvironment(settingsStore)
    val userMessages = rememberUserMessageSink()
    val viewModel = viewModel(key = "notification_timeline_$entryName") {
        NotificationTimelineViewModel(entryName)
    }
    val invitations = entryName == INVITATION_ENTRY_NAME
    val listState = key(accountClient) { rememberLazyListState() }
    val pageTurnTarget = rememberPageTurnTarget(
        listState = listState,
        enabled = MobileNotificationCategory.entries.any { it.entryName == entryName },
    )

    LaunchedEffect(entryName, accountClient, environment) {
        viewModel.loadFor(environment)
    }

    AppScaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            AppCompactTopBar(
                title = title,
                onBack = navigator.onNavigateBack,
                backTag = NOTIFICATION_TIMELINE_BACK_BUTTON_TAG,
                centered = true,
                containerColor = MaterialTheme.colorScheme.surface,
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
                listState = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .pageTurnViewportWithGuide(pageTurnTarget),
                footer = ProgressIndicatorFooter,
                key = { it.stableId },
            ) { notification ->
                when {
                    notification.type == "empty" -> {
                        AppEmptyState(
                            title = notification.emptyInfo?.text?.takeIf(String::isNotBlank) ?: "暂无通知",
                            description = "有新的动态时会出现在这里",
                            icon = Icons.Outlined.Notifications,
                            modifier = Modifier.padding(top = 32.dp),
                        )
                    }

                    invitations -> {
                        val invitationTarget = notification.target
                        InvitationAnswerItem(
                            notification = notification,
                            onQuestionClick = {
                                invitationTarget?.id?.toLongOrNull()?.let { questionId ->
                                    navigator.onNavigate(
                                        Question(
                                            questionId = questionId,
                                            title = invitationTarget.title.ifBlank {
                                                notification.targetSource?.text.orEmpty()
                                            },
                                        ),
                                    )
                                } ?: userMessages.showMessage("无法打开这个问题")
                            },
                            onAnswerClick = {
                                val target = notification.target
                                val destination = target
                                    ?.myAnswerUrl
                                    ?.takeIf { it.isNotBlank() }
                                    ?.let(::resolveContent)
                                if (destination != null) {
                                    navigator.onNavigate(destination)
                                } else {
                                    target?.id?.toLongOrNull()?.let { questionId ->
                                        navigator.onNavigate(
                                            WriteAnswer(
                                                questionId = questionId,
                                                questionTitle = target.title.ifBlank {
                                                    notification.targetSource?.text.orEmpty()
                                                },
                                            ),
                                        )
                                    } ?: userMessages.showMessage("无法回答这个问题")
                                }
                            },
                        )
                    }

                    viewModel.shouldShowNotification(settingsStore, notification) -> {
                        NotificationItemView(
                            notification = notification,
                            onClick = {
                                notification.navDestination()?.let(navigator.onNavigate)
                                    ?: userMessages.showMessage("暂不支持打开此通知")
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InvitationAnswerItem(
    notification: MobileNotificationTimelineItem,
    onQuestionClick: () -> Unit,
    onAnswerClick: () -> Unit,
) {
    val author = notification.head?.author
    val authorName = notification.content
        ?.title
        ?.takeIf { it.isNotBlank() }
        ?: author?.name?.takeIf { it.isNotBlank() }
        ?: notification.detailTitle
    val avatarUrl = notification.avatarUrl()
    val rewardText = notification.additionalInfo
        .firstOrNull()
        ?.text
        ?.takeIf { it.isNotBlank() }
        ?.let { Ksoup.parse(it).text() }
        .orEmpty()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("invitation_${notification.stableId}")
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (avatarUrl.isNotBlank()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            } else {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {}
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = authorName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                    notification.head?.labels?.firstOrNull()?.text?.takeIf { it.isNotBlank() }?.let { label ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                notification.content?.subTitle?.takeIf(String::isNotBlank)?.let { event ->
                    Text(
                        text = event,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = notification.invitationTime(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onQuestionClick),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(Modifier.padding(14.dp)) {
                if (rewardText.isNotBlank()) {
                    Text(
                        text = rewardText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    text = notification.target
                        ?.title
                        ?.ifBlank {
                            notification.targetSource?.text.orEmpty()
                        }.orEmpty(),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = notification.targetSource?.subText.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                AppButton(
                    onClick = onAnswerClick,
                    modifier = Modifier.width(IntrinsicSize.Max).align(Alignment.End).testTag("invitation_answer_${notification.stableId}"),
                ) {
                    Icon(
                        if (notification.target?.hasAnswer == true) Icons.Default.Visibility else Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(if (notification.target?.hasAnswer == true) "查看回答" else "写回答")
                }
            }
        }
    }
}

@OptIn(ExperimentalTime::class)
private fun MobileNotificationTimelineItem.invitationTime(): String {
    if (created <= 0) return ""
    val timeZone = TimeZone.currentSystemDefault()
    val dateTime = Instant.fromEpochSeconds(created).toLocalDateTime(timeZone)
    val today = Clock.System
        .now()
        .toLocalDateTime(timeZone)
        .date
    val date = dateTime.date
    return when {
        date == today -> "${dateTime.hour.toString().padStart(2, '0')}:${dateTime.minute.toString().padStart(2, '0')}"
        date.toEpochDays() == today.toEpochDays() - 1 -> "昨天"
        else -> "${date.month.number.toString().padStart(2, '0')}-${date.day.toString().padStart(2, '0')}"
    }
}

private const val INVITATION_ENTRY_NAME = "invite"
