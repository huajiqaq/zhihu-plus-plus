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
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.navigation.WriteAnswer
import com.github.zly2006.zhihu.navigation.resolveContent
import com.github.zly2006.zhihu.notification.rememberNotificationSettingsStore
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.ui.components.AppAlertDialog
import com.github.zly2006.zhihu.ui.components.AppButton
import com.github.zly2006.zhihu.ui.components.AppCompactTopBar
import com.github.zly2006.zhihu.ui.components.AppEmptyState
import com.github.zly2006.zhihu.ui.components.AppPullToRefreshBox
import com.github.zly2006.zhihu.ui.components.AppScaffold
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.ui.components.AppTextField
import com.github.zly2006.zhihu.ui.components.PaginatedList
import com.github.zly2006.zhihu.ui.components.ProgressIndicatorFooter
import com.github.zly2006.zhihu.ui.components.pageTurnViewportWithGuide
import com.github.zly2006.zhihu.ui.components.rememberPageTurnTarget
import com.github.zly2006.zhihu.viewmodel.MobileNotificationCategory
import com.github.zly2006.zhihu.viewmodel.NotificationTimelineViewModel
import com.github.zly2006.zhihu.viewmodel.shouldShowNotification
import kotlinx.coroutines.launch
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
    val accountStore = rememberZhihuAccountStore()
    val accountClient = accountStore.client
    val scope = rememberCoroutineScope()
    var replyItem by remember(accountClient, entryName) { mutableStateOf<MobileNotificationTimelineItem?>(null) }
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
                            icon = Icons.Outlined.Notifications,
                            modifier = Modifier.padding(top = 32.dp),
                        )
                    }

                    invitations -> {
                        InvitationAnswerItem(notification)
                    }

                    settingsStore.shouldShowNotification(notification) -> {
                        Column {
                            NotificationItemView(notification, entryName, showUnreadBadge = settingsStore.getUnreadBadgeEnabled())
                            if (entryName == MobileNotificationCategory.Comment.entryName) {
                                notification.commentActionTarget?.let { target ->
                                    val enabled = !viewModel.isCommentActionPending(target.commentId)
                                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.End) {
                                        AppTextButton("回复", { replyItem = notification }, enabled = enabled)
                                        notification.extraAction?.data?.voting?.let { liked ->
                                            AppTextButton(if (liked) "取消喜欢" else "喜欢", {
                                                scope.launch {
                                                    val ok = viewModel.toggleCommentLike(environment, notification)
                                                    if (!ok && accountStore.client === accountClient) userMessages.showMessage("喜欢状态未更新，请重试")
                                                }
                                            }, enabled = enabled)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    replyItem?.let { item ->
        val target = item.commentActionTarget ?: return@let
        val pending = viewModel.isCommentActionPending(target.commentId)
        val draft = rememberSaveable(accountClient, item.stableId, target.commentId, saver = TextFieldState.Saver) {
            TextFieldState()
        }
        var failed by remember(item, draft.text.toString()) { mutableStateOf(false) }
        AppAlertDialog(
            onDismissRequest = { if (!pending) replyItem = null },
            title = {
                Text(
                    item.extraAction
                        ?.authorName
                        ?.takeIf(String::isNotBlank)
                        ?.let { "回复 $it" } ?: "回复评论",
                )
            },
            text = {
                Column {
                    AppTextField(
                        state = draft,
                        placeholder = "写下回复",
                        lineLimits = TextFieldLineLimits.MultiLine(),
                        enabled = !pending,
                    )
                    if (failed) Text("回复未发送，请重试", color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                AppTextButton("发送", {
                    val content = draft.text.toString()
                    scope.launch {
                        if (accountStore.client !== accountClient) return@launch
                        val ok = viewModel.replyToComment(environment, item, content)
                        if (accountStore.client !== accountClient) return@launch
                        if (ok) {
                            replyItem = null
                            userMessages.showMessage("回复已发送")
                        } else {
                            failed = true
                        }
                    }
                }, enabled = !pending && draft.text.isNotBlank())
            },
            dismissButton = { AppTextButton("取消", { replyItem = null }, enabled = !pending) },
        )
    }
}

@Composable
private fun InvitationAnswerItem(notification: MobileNotificationTimelineItem) {
    val navigate = LocalNavigator.current.onNavigate
    val target = notification.target
    val deleted = notification.content?.isDeleted == true || notification.targetSource?.isDeleted == true
    val question = target?.takeIf { !deleted && it.type == "question" }?.let {
        it.id
            .toLongOrNull()
            ?.takeIf { id -> id > 0 }
            ?.let { id -> Question(id, it.title.ifBlank { notification.targetSource?.text.orEmpty() }) }
    }
    val existingAnswer = target?.myAnswerUrl?.takeIf(String::isNotBlank)?.let(::resolveContent) as? Article
    val answer = question?.let {
        when {
            existingAnswer?.type == ArticleType.Answer -> existingAnswer
            target?.hasAnswer == true -> null
            else -> WriteAnswer(it.questionId, it.title)
        }
    }
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
                .then(if (question != null) Modifier.clickable { navigate(question) } else Modifier),
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
                    text = if (deleted) "该内容已被删除" else target?.title.orEmpty().ifBlank { notification.targetSource?.text.orEmpty() },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = if (deleted) "" else notification.targetSource?.subText.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (answer != null) {
                    Spacer(Modifier.height(10.dp))
                    AppButton(
                        onClick = { navigate(answer) },
                        modifier = Modifier.width(IntrinsicSize.Max).align(Alignment.End).testTag("invitation_answer_${notification.stableId}"),
                    ) {
                        Icon(
                            if (answer is Article) Icons.Default.Visibility else Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(if (answer is Article) "查看回答" else "写回答")
                    }
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
