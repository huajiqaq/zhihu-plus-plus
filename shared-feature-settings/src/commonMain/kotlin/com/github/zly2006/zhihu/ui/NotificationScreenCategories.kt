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
import androidx.compose.material.icons.filled.PersonAddAlt1
import androidx.compose.material.icons.outlined.ContactPage
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.zly2006.zhihu.data.MobileNotificationColumnHead
import com.github.zly2006.zhihu.i18n.NotificationsStrings
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.ui.components.AppBadgedBox
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppIcon
import com.github.zly2006.zhihu.ui.components.settingSkinIsMiuix
import com.github.zly2006.zhihu.viewmodel.MobileNotificationCategory

@Composable
internal fun NotificationCategoryRow(
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
            val unreadCount = unreadCounts[category] ?: 0
            Column(
                modifier = Modifier
                    .weight(1f)
                    .testTag("notification_category_${category.entryName}")
                    .semantics {
                        if (showUnreadBadges) {
                            stateDescription = if (unreadCount > 0) NotificationsStrings.unreadNotifications(unreadCount) else NotificationsStrings.read
                        }
                    }.clickable(role = Role.Button) { onCategoryClick(category) }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppBadgedBox(count = if (showUnreadBadges) unreadCount else 0) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(if (miuix) RoundedCornerShape(AppDimens.SettingIconCornerRadius) else MaterialTheme.shapes.large)
                            .background(if (miuix) LocalAppSkinTokens.current.surfaceContainerHigh else MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppIcon(
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
internal fun NotificationInvitationRow(
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
                if (showUnreadBadge) stateDescription = if (invitation.unreadCount > 0) NotificationsStrings.unreadInvitations(invitation.unreadCount) else NotificationsStrings.read
            }.clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppBadgedBox(count = if (showUnreadBadge) invitation.unreadCount else 0) {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = CircleShape,
                color = LocalAppSkinTokens.current.secondaryVariant,
                contentColor = LocalAppSkinTokens.current.onSecondaryVariant,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    AppIcon(Icons.Outlined.ContactPage, contentDescription = null, modifier = Modifier.size(28.dp))
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = invitation.title.ifBlank { NotificationsStrings.invitationTitle },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = invitation.textPrefix + invitation.text,
                style = LocalAppSkinTokens.current.body2,
                color = LocalAppSkinTokens.current.onSurfaceVariantSummary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun MobileNotificationCategory.homeIcon(): ImageVector = when (this) {
    MobileNotificationCategory.Comment -> Icons.AutoMirrored.Outlined.Comment
    MobileNotificationCategory.Like -> Icons.Filled.Favorite
    MobileNotificationCategory.Favorite -> Icons.Filled.Bookmark
    MobileNotificationCategory.Follow -> Icons.Filled.PersonAddAlt1
}
