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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.github.zly2006.zhihu.data.MobileNotificationTimelineItem
import com.github.zly2006.zhihu.i18n.NotificationsStrings
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppIcon
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime

@Composable
internal fun NotificationConversationRow(
    notification: MobileNotificationTimelineItem,
    showUnreadBadge: Boolean,
) {
    val unread = showUnreadBadge && notification.isUnread
    val destination = notification.navDestination()
    val navigator = LocalNavigator.current
    val authorDestination = notification.avatarDestination()
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
                    if (showUnreadBadge) {
                        stateDescription = when {
                            notification.unreadCount > 0 -> NotificationsStrings.unreadMessages(notification.unreadCount)
                            unread -> NotificationsStrings.unread
                            else -> NotificationsStrings.read
                        }
                    }
                }.openNotificationTarget(destination, navigator.onNavigate, NotificationsStrings.openMessage)
                .padding(
                    horizontal = notificationHorizontalPadding(),
                    vertical = AppDimens.Space12,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val avatarUrl = notification.avatarUrl()
            val avatarModifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .then(
                    if (authorDestination != null) {
                        Modifier
                            .testTag("notification_avatar_${notification.stableId}")
                            .clickable(role = Role.Button, onClickLabel = NotificationsStrings.openProfile) {
                                navigator.onNavigate(authorDestination)
                            }
                    } else {
                        Modifier
                    },
                )
            if (avatarUrl.isNotBlank()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = authorDestination?.let { NotificationsStrings.avatarDescription(it.name) },
                    modifier = avatarModifier.background(MaterialTheme.colorScheme.surfaceVariant),
                )
            } else {
                Surface(
                    modifier = avatarModifier,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        AppIcon(Icons.Outlined.Notifications, contentDescription = authorDestination?.let { NotificationsStrings.avatarDescription(it.name) })
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
                NotificationPreviewText(
                    html = notification.bodyPreview.ifBlank { notification.quotedPreview },
                    messageId = notification.stableId,
                    maxLines = 1,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (unread) {
                Spacer(Modifier.width(8.dp))
                if (notification.unreadCount > 0) Badge { Text(formatUnreadCount(notification.unreadCount)) } else Badge()
            }
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        )
    }
}

private fun MobileNotificationTimelineItem.notificationListDate(): String {
    val epochSeconds = created.takeIf { it > 0 } ?: createdStr.toLongOrNull()
        ?: return createdStr
    val dateTime = Instant.fromEpochSeconds(epochSeconds).toLocalDateTime(TimeZone.currentSystemDefault())
    return "${dateTime.month.number.toString().padStart(2, '0')}-${dateTime.day.toString().padStart(2, '0')}"
}
