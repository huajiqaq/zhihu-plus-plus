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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.github.zly2006.zhihu.account.accountIdentityKey
import com.github.zly2006.zhihu.account.rememberZhihuAccountStore
import com.github.zly2006.zhihu.data.ZhihuPrivateMessage
import com.github.zly2006.zhihu.i18n.AppLocale
import com.github.zly2006.zhihu.i18n.NotificationsStrings
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.Notification
import com.github.zly2006.zhihu.navigation.resolveNavigationUrl
import com.github.zly2006.zhihu.notification.rememberNotificationSettingsStore
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.ui.components.AppCompactTopBar
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppIcon
import com.github.zly2006.zhihu.ui.components.AppIconButton
import com.github.zly2006.zhihu.ui.components.AppPullToRefreshBox
import com.github.zly2006.zhihu.ui.components.AppScaffold
import com.github.zly2006.zhihu.ui.components.AppTextField
import com.github.zly2006.zhihu.ui.components.PaginatedList
import com.github.zly2006.zhihu.ui.components.ProgressIndicatorFooter
import com.github.zly2006.zhihu.ui.components.settingSkinIsMiuix
import com.github.zly2006.zhihu.util.formatFullDateTime
import com.github.zly2006.zhihu.util.formatRelativeTime
import com.github.zly2006.zhihu.viewmodel.PrivateMessageViewModel
import kotlin.time.Clock
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val PRIVATE_MESSAGE_BACK_BUTTON_TAG = "private_message_back_button"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivateMessageScreen(destination: Notification.Message) {
    val navigator = LocalNavigator.current
    val accountStore = rememberZhihuAccountStore()
    val accountClient = accountStore.client
    val accountId = accountStore.session.accountIdentityKey() ?: "guest"
    val environment = rememberNotificationEnvironment(rememberNotificationSettingsStore())
    val viewModel = viewModel(key = "private_message_${destination.peerId}") {
        PrivateMessageViewModel(destination.peerId)
    }
    val peerName = viewModel.peer?.name?.ifBlank { destination.name } ?: destination.name
    val coroutineScope = rememberCoroutineScope()
    val userMessages = rememberUserMessageSink()
    val lifecycleOwner = LocalLifecycleOwner.current
    val selfAvatarUrl = rememberAccountSettingsAccountState().value.avatarUrl.orEmpty()
    val listState = key(accountId, destination.peerId) { rememberLazyListState() }
    val drafts = rememberSaveable(
        destination.peerId,
        saver = mapSaver(
            save = { values: MutableMap<String, TextFieldState> -> values.mapValues { it.value.text.toString() }.filterValues { it.isNotEmpty() } },
            restore = { saved -> saved.mapValues { TextFieldState(it.value as String) }.toMutableMap() },
        ),
    ) { mutableMapOf<String, TextFieldState>() }
    val draft = drafts.getOrPut(accountId) { TextFieldState() }
    var nowEpochSeconds by remember { mutableLongStateOf(Clock.System.now().epochSeconds) }

    LaunchedEffect(viewModel, listState) {
        var previousLatestId = viewModel.allData.firstOrNull()?.stableId
        snapshotFlow { viewModel.allData.firstOrNull()?.stableId }.collect { latestId ->
            if (latestId != null && latestId != previousLatestId && !listState.isScrollInProgress) {
                // 倒序列表插入消息后可能已按 key 锚定旧首项；仍贴着旧最新消息才继续跟随底部。
                val anchoredToPreviousLatest = previousLatestId != null &&
                    listState.layoutInfo.visibleItemsInfo
                        .firstOrNull()
                        ?.key == previousLatestId &&
                    listState.firstVisibleItemScrollOffset == 0
                if (!listState.canScrollBackward || anchoredToPreviousLatest) {
                    listState.requestScrollToItem(0)
                }
            }
            previousLatestId = latestId
        }
    }

    LaunchedEffect(lifecycleOwner, destination.peerId, environment, accountClient) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            nowEpochSeconds = Clock.System.now().epochSeconds
            viewModel.loadFor(environment)
            while (true) {
                delay(PRIVATE_MESSAGE_POLL_INTERVAL_MILLIS)
                nowEpochSeconds = Clock.System.now().epochSeconds
                viewModel.refreshLatest(environment)
            }
        }
    }

    AppScaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            AppCompactTopBar(
                title = peerName.ifBlank { NotificationsStrings.privateTitle },
                onBack = navigator.onNavigateBack,
                backTag = PRIVATE_MESSAGE_BACK_BUTTON_TAG,
                centered = true,
                containerColor = MaterialTheme.colorScheme.surface,
            )
        },
        bottomBar = {
            Surface(
                color = if (settingSkinIsMiuix()) {
                    LocalAppSkinTokens.current.surfaceContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                },
                tonalElevation = AppDimens.ElevationNone,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(
                            start = AppDimens.Space12,
                            end = AppDimens.Space6,
                            top = AppDimens.Space8,
                            bottom = AppDimens.Space8,
                        ),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    AppTextField(
                        state = draft,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("private_message_input"),
                        placeholder = NotificationsStrings.privatePlaceholder,
                        labelText = NotificationsStrings.privateContentLabel,
                        enabled = !viewModel.isSending,
                        lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 4),
                        shape = RoundedCornerShape(AppDimens.InputCornerRadius),
                    )
                    Spacer(Modifier.width(4.dp))
                    AppIconButton(
                        onClick = {
                            val content = draft.text.toString()
                            coroutineScope.launch {
                                if (accountStore.client !== accountClient) return@launch
                                val succeeded = viewModel.sendMessage(content, environment)
                                if (accountStore.client !== accountClient) return@launch
                                if (succeeded) {
                                    if (draft.text.contentEquals(content)) {
                                        draft.clearText()
                                        draft.undoState.clearHistory()
                                    }
                                    listState.requestScrollToItem(0)
                                } else {
                                    userMessages.showMessage(viewModel.errorMessage ?: NotificationsStrings.sendFailed)
                                }
                            }
                        },
                        enabled = draft.text.isNotBlank() && !viewModel.isSending,
                        modifier = Modifier.testTag("private_message_send"),
                    ) {
                        AppIcon(
                            Icons.AutoMirrored.Outlined.Send,
                            contentDescription = NotificationsStrings.send,
                            tint = if (draft.text.isNotBlank()) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                        )
                    }
                }
            }
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
                reverseLayout = true,
                // 保留列表淡入，由新消息高度连续变化带动旧消息让位，避免独立的位置动画造成重叠。
                animateItemPlacement = false,
                listState = listState,
                contentPadding = PaddingValues(vertical = 12.dp),
                modifier = Modifier.fillMaxSize(),
                footer = ProgressIndicatorFooter,
                key = { it.stableId },
            ) { message ->
                var appeared by rememberSaveable(message.stableId) { mutableStateOf(false) }
                val visibility = remember(message.stableId) {
                    MutableTransitionState(appeared).apply { targetState = true }
                }
                LaunchedEffect(message.stableId) { appeared = true }
                AnimatedVisibility(
                    visibleState = visibility,
                    modifier = Modifier.clipToBounds(),
                    enter = slideInVertically(tween(240)) { it } +
                        expandVertically(tween(240), expandFrom = Alignment.Bottom, clip = true),
                ) {
                    PrivateMessageBubble(
                        message = message,
                        incoming = message.sender?.let { sender ->
                            sender.id == destination.peerId || sender.urlToken == destination.peerId
                        } == true,
                        selfAvatarUrl = selfAvatarUrl,
                        nowEpochSeconds = nowEpochSeconds,
                    )
                }
            }
        }
    }
}

@Composable
private fun PrivateMessageBubble(
    message: ZhihuPrivateMessage,
    incoming: Boolean,
    selfAvatarUrl: String,
    nowEpochSeconds: Long,
) {
    val navigator = LocalNavigator.current
    val isMiuix = settingSkinIsMiuix()
    val bubbleColor = if (isMiuix) {
        if (incoming) {
            LocalAppSkinTokens.current.surfaceContainer
        } else {
            LocalAppSkinTokens.current.secondaryVariant
        }
    } else {
        if (incoming) {
            MaterialTheme.colorScheme.surfaceContainerLow
        } else {
            MaterialTheme.colorScheme.primaryContainer
        }
    }
    val bubbleContentColor = if (isMiuix) {
        if (incoming) LocalAppSkinTokens.current.onSurface else LocalAppSkinTokens.current.onSecondaryVariant
    } else {
        if (incoming) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimaryContainer
    }
    val linkColor = bubbleContentColor
    val displayText = remember(message.content, message.plugin, linkColor, navigator, AppLocale.current) {
        message.displayContent(linkColor) { url ->
            resolveNavigationUrl(url)?.let(navigator.onNavigate)
        }
    }
    var showFullTime by rememberSaveable(message.stableId) { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("private_message_${message.stableId}")
            .padding(horizontal = AppDimens.Space14, vertical = AppDimens.Space6),
        horizontalArrangement = if (incoming) Arrangement.Start else Arrangement.End,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (incoming) {
            AsyncImage(
                model = message.sender?.avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(AppDimens.AvatarSizeCompact)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Spacer(Modifier.width(8.dp))
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = if (incoming) Alignment.Start else Alignment.End,
        ) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = if (incoming) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                val bubbleShape = RoundedCornerShape(
                    topStart = AppDimens.BubbleCornerRadius,
                    topEnd = AppDimens.BubbleCornerRadius,
                    bottomStart = if (incoming) {
                        AppDimens.BubbleTailCorner
                    } else {
                        AppDimens.BubbleCornerRadius
                    },
                    bottomEnd = if (incoming) {
                        AppDimens.BubbleCornerRadius
                    } else {
                        AppDimens.BubbleTailCorner
                    },
                )
                Surface(
                    modifier = Modifier.widthIn(max = maxWidth * 0.88f),
                    shape = bubbleShape,
                    color = bubbleColor,
                    contentColor = bubbleContentColor,
                ) {
                    SelectionContainer {
                        Text(
                            text = displayText,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .commentSelectionWorkaround()
                                .semantics { stateDescription = if (incoming) NotificationsStrings.privateIncoming else NotificationsStrings.privateOutgoing }
                                .padding(
                                    horizontal = AppDimens.Space14,
                                    vertical = AppDimens.Space10,
                                ),
                        )
                    }
                }
            }
            Text(
                text = if (showFullTime) {
                    formatFullDateTime(message.createdTime)
                } else {
                    formatRelativeTime(message.createdTime, nowEpochSeconds)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .testTag("private_message_time_${message.stableId}")
                    .clickable { showFullTime = !showFullTime }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
        if (!incoming) {
            Spacer(Modifier.width(8.dp))
            AsyncImage(
                model = selfAvatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(AppDimens.AvatarSizeCompact)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
    }
}

private const val PRIVATE_MESSAGE_POLL_INTERVAL_MILLIS = 5_000L
