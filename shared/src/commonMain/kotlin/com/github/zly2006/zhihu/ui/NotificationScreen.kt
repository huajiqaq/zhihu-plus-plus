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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MarkChatRead
import androidx.compose.material.icons.filled.PersonAddAlt1
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.ContactPage
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.fleeksoft.ksoup.Ksoup
import com.github.zly2006.zhihu.account.rememberZhihuAccountStore
import com.github.zly2006.zhihu.data.MobileNotificationColumnHead
import com.github.zly2006.zhihu.data.MobileNotificationTimelineItem
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.Notification
import com.github.zly2006.zhihu.navigation.Person
import com.github.zly2006.zhihu.navigation.resolveContent
import com.github.zly2006.zhihu.notification.rememberNotificationSettingsStore
import com.github.zly2006.zhihu.platform.rememberExternalUrlOpener
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppIconButton
import com.github.zly2006.zhihu.ui.components.AppPageTitleBar
import com.github.zly2006.zhihu.ui.components.AppPullToRefreshBox
import com.github.zly2006.zhihu.ui.components.AppScaffold
import com.github.zly2006.zhihu.ui.components.PaginatedList
import com.github.zly2006.zhihu.ui.components.ProgressIndicatorFooter
import com.github.zly2006.zhihu.ui.components.appPageTitleBarScroll
import com.github.zly2006.zhihu.ui.components.rememberAppPageTitleBarScrollState
import com.github.zly2006.zhihu.ui.components.settingSkinIsMiuix
import com.github.zly2006.zhihu.util.formatRelativeTime
import com.github.zly2006.zhihu.viewmodel.MobileNotificationCategory
import com.github.zly2006.zhihu.viewmodel.NotificationViewModel
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import com.github.zly2006.zhihu.ui.components.AppIcon as Icon

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
                title = "消息",
                onBack = navigator.onNavigateBack,
                backTag = NOTIFICATION_SCREEN_BACK_BUTTON_TAG,
                scrollState = titleBarScrollState,
                actions = {
                    if (viewModel.unreadCount > 0) {
                        AppIconButton(onClick = {
                            coroutineScope.launch {
                                val succeeded = viewModel.markAllAsRead(environment)
                                if (accountStore.client !== accountClient) return@launch
                                userMessages.showMessage(if (succeeded) "已全部标记为已读" else "标记已读失败")
                            }
                        }) {
                            Icon(Icons.Default.MarkChatRead, contentDescription = "全部标为已读")
                        }
                    }
                    AppIconButton(onClick = { navigator.onNavigate(Notification.NotificationSettings()) }) {
                        Icon(Icons.Default.Settings, contentDescription = "通知设置")
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
                                    Notification.Entry(category.entryName, category.detailTitle),
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
                NotificationConversationRow(
                    notification = notification,
                    showUnreadBadge = settingsStore.getUnreadBadgeEnabled(),
                    onClick = {
                        notification.navDestination()?.let(navigator.onNavigate)
                            ?: userMessages.showMessage("暂不支持打开此消息")
                    },
                )
            }
        }
    }
}

@Composable
private fun notificationHorizontalPadding() = if (settingSkinIsMiuix()) {
    AppDimens.PageHorizontalPadding
} else {
    AppDimens.PageHeaderM3eHorizontalPadding
}

@Composable
private fun NotificationCategoryRow(
    unreadCounts: Map<MobileNotificationCategory, Int>,
    showUnreadBadges: Boolean,
    onCategoryClick: (MobileNotificationCategory) -> Unit,
) {
    val miuix = settingSkinIsMiuix()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = notificationHorizontalPadding(),
                vertical = AppDimens.Space12,
            ).clip(if (miuix) RoundedCornerShape(AppDimens.MenuCornerRadius) else CardDefaults.shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 8.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        MobileNotificationCategory.entries.forEach { category ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .testTag("notification_category_${category.entryName}")
                    .semantics {
                        val unreadCount = unreadCounts[category] ?: 0
                        stateDescription = if (unreadCount > 0) "$unreadCount 条未读通知" else "已读"
                    }.clickable(role = Role.Button) { onCategoryClick(category) }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BadgedBox(
                    badge = {
                        val unreadCount = unreadCounts[category] ?: 0
                        if (showUnreadBadges && unreadCount > 0) {
                            Badge {
                                Text(formatUnreadCount(unreadCount))
                            }
                        }
                    },
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(if (miuix) RoundedCornerShape(AppDimens.SettingIconCornerRadius) else MaterialTheme.shapes.large)
                            .background(if (miuix) LocalAppSkinTokens.current.surfaceContainerHigh else MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = category.homeIcon(),
                            contentDescription = null,
                            tint = if (miuix) LocalAppSkinTokens.current.primary else MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
                Text(
                    text = category.detailTitle,
                    style = if (miuix) LocalAppSkinTokens.current.footnote1 else MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun NotificationInvitationRow(
    invitation: MobileNotificationColumnHead,
    showUnreadBadge: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = notificationHorizontalPadding(),
                vertical = AppDimens.Space6,
            ).clip(RoundedCornerShape(AppDimens.NoticeCardCorner))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .testTag("notification_invitation")
            .semantics {
                stateDescription = if (invitation.unreadCount > 0) "${invitation.unreadCount} 条未读邀请" else "已读"
            }.clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(52.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.ContactPage, contentDescription = null, modifier = Modifier.size(28.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = invitation.title.ifBlank { "邀请回答" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = invitation.textPrefix + invitation.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (showUnreadBadge && invitation.unreadCount > 0) {
            Badge {
                Text(formatUnreadCount(invitation.unreadCount))
            }
        }
    }
}

@Composable
private fun NotificationConversationRow(
    notification: MobileNotificationTimelineItem,
    showUnreadBadge: Boolean,
    onClick: () -> Unit,
) {
    val unread = showUnreadBadge && notification.unreadCount > 0
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (unread) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.04f)
                } else {
                    Color.Transparent
                },
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("notification_message_${notification.stableId}")
                .semantics {
                    stateDescription = if (notification.unreadCount > 0) "${notification.unreadCount} 条未读私信" else "已读"
                }.clickable(onClick = onClick)
                .padding(
                    horizontal = notificationHorizontalPadding(),
                    vertical = AppDimens.Space12,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val avatarUrl = notification.avatarUrl()
            if (avatarUrl.isNotBlank()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            } else {
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Notifications, contentDescription = null)
                    }
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = notification.displayTitle(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    notification.notificationListDate().takeIf { it.isNotBlank() }?.let { created ->
                        Text(
                            text = created,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = notification.content?.text.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (unread) {
                Spacer(Modifier.width(8.dp))
                Badge {
                    Text(formatUnreadCount(notification.unreadCount))
                }
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 82.dp, end = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        )
    }
}

@Composable
fun NotificationItemView(
    notification: MobileNotificationTimelineItem,
    onClick: () -> Unit,
) {
    val navigator = LocalNavigator.current
    val backgroundColor = if (notification.isRead) {
        Color.Transparent
    } else {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { stateDescription = if (notification.isRead) "已读" else "未读" }
            .clickable(onClick = onClick),
        color = backgroundColor,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = notificationHorizontalPadding(),
                    vertical = AppDimens.Space12,
                ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .width(10.dp)
                        .padding(top = 10.dp, end = 4.dp),
                ) {
                    if (!notification.isRead) {
                        Surface(
                            color = MaterialTheme.colorScheme.error,
                            shape = CircleShape,
                            modifier = Modifier.size(6.dp),
                        ) {}
                    }
                }
                val avatarUrl = notification.avatarUrl()
                if (avatarUrl.isNotBlank()) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = notification.displayTitle(),
                        style = MaterialTheme.typography.titleMedium,
                        // Surface(Transparent) 不提供 contentColor，环境默认值是纯黑，夜间会黑底黑字。
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    notification.displaySubtitle().takeIf { it.isNotBlank() }?.let { subtitle ->
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    val emojisUsed = remember { mutableSetOf<String>() }
                    val content = notification.content
                    val displayText = if (content?.subTitle == "喜欢了你的评论") {
                        buildAnnotatedString {
                            append(Ksoup.parse(content.subText).text())
                        }
                    } else if (content?.subTitle?.contains("评论了") == true) {
                        val document = Ksoup.parseBodyFragment(content.abstractText)
                        val openExternalUrl = rememberExternalUrlOpener()
                        val linkColor = LocalAppSkinTokens.current.onSurface
                        val string = remember(content.abstractText, linkColor) {
                            emojisUsed.clear()
                            buildAnnotatedString {
                                dfsSimple(
                                    node = document.body(),
                                    onNavigate = navigator.onNavigate,
                                    openExternalUrl = openExternalUrl::invoke,
                                    linkColor = linkColor,
                                    componentUsed = emojisUsed,
                                )
                            }
                        }

                        string
                    } else {
                        buildAnnotatedString {
                            append(Ksoup.parse(content?.text.orEmpty()).text())
                        }
                    }

                    val inlineContent = rememberCommentEmojiInlineContent(emojisUsed)

                    if (displayText.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = displayText,
                            inlineContent = inlineContent,
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = formatRelativeTime(notification.created),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = AppDimens.Space4),
                    )
                }
            }

            notification.sourceText().takeIf { it.isNotBlank() }?.let { sourceText ->
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = sourceText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }
    }
}

private fun MobileNotificationCategory.homeIcon(): ImageVector = when (this) {
    MobileNotificationCategory.Comment -> Icons.AutoMirrored.Outlined.Comment
    MobileNotificationCategory.Like -> Icons.Filled.Favorite
    MobileNotificationCategory.Favorite -> Icons.Filled.Bookmark
    MobileNotificationCategory.Follow -> Icons.Filled.PersonAddAlt1
}

internal fun MobileNotificationTimelineItem.displayTitle(): String =
    content
        ?.title
        ?.takeIf { it.isNotBlank() }
        ?: detailTitle.takeIf { it.isNotBlank() }
        ?: target?.name?.takeIf { it.isNotBlank() }
        ?: "通知"

private fun MobileNotificationTimelineItem.displaySubtitle(): String {
    val subtitle = content?.subTitle?.takeIf { it.isNotBlank() }.orEmpty()
    if ((!subtitle.endsWith("：") && subtitle.startsWith("评论了")) ||
        subtitle.startsWith("赞同了") ||
        subtitle.startsWith("喜欢了")
    ) {
        return "$subtitle："
    }
    return subtitle
}

private fun MobileNotificationTimelineItem.sourceText(): String =
    listOfNotNull(
        targetSource?.text?.takeIf { it.isNotBlank() },
        targetSource?.subText?.takeIf { it.isNotBlank() },
    ).joinToString("\n")

internal fun MobileNotificationTimelineItem.avatarUrl(): String =
    head?.avatarUrl?.takeIf { it.isNotBlank() }
        ?: head?.author?.avatarUrl?.takeIf { it.isNotBlank() }
        ?: target?.avatarUrl?.takeIf { it.isNotBlank() }
        ?: content?.subIcon?.takeIf { it.isNotBlank() }
        ?: ""

internal fun MobileNotificationTimelineItem.navDestination(): NavDestination? {
    val destinations = listOf(
        content?.targetLink,
        content?.subTargetLink,
        targetSource?.targetLink,
        head?.targetLink,
    ).mapNotNull { link ->
        link?.takeIf { it.isNotBlank() }?.let(::resolveContent)
    }
    val destination = destinations.firstOrNull { it is Notification.Message }
        ?: destinations.firstOrNull { it !is Person }
        ?: destinations.firstOrNull()
    return if (destination is Notification.Message) {
        destination.copy(
            name = destination.name.ifBlank { displayTitle() },
            avatarUrl = avatarUrl(),
        )
    } else {
        destination ?: target
            ?.takeIf { it.type == "people" && (it.urlToken.isNotBlank() || it.id.isNotBlank()) }
            ?.let {
                Person(
                    id = it.id.ifBlank { Person.EMPTY_ID },
                    urlToken = it.urlToken,
                    name = it.name.ifBlank { "loading..." },
                )
            }
    }
}

@OptIn(ExperimentalTime::class)
private fun MobileNotificationTimelineItem.notificationListDate(): String {
    val epochSeconds = created.takeIf { it > 0 } ?: createdStr.toLongOrNull()
        ?: return createdStr
    val dateTime = Instant.fromEpochSeconds(epochSeconds).toLocalDateTime(TimeZone.currentSystemDefault())
    return "${dateTime.month.number.toString().padStart(2, '0')}-${dateTime.day.toString().padStart(2, '0')}"
}

private fun formatUnreadCount(count: Int): String = if (count > 99) "99+" else count.toString()
