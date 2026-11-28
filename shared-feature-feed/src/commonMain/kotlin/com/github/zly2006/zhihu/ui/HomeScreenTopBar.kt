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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.github.zly2006.zhihu.i18n.FeedStrings
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.ui.components.AppBottomSheet
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppIcon
import com.github.zly2006.zhihu.ui.components.AppSearchBar
import com.github.zly2006.zhihu.ui.components.appGlassBlur
import com.github.zly2006.zhihu.ui.components.appGlassContainerColor
import com.github.zly2006.zhihu.ui.components.settingSkinIsMiuix

@Composable
private fun HomeAccountAvatarAction(
    avatarUrl: String?,
    unreadCount: Int,
    showUnreadBadge: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(AppDimens.AccountEntrySize)
            .testTag(HOME_ACCOUNT_BUTTON_TAG)
            /** clip 在 clickable 前，避免方形水波纹。 */
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BadgedBox(
            badge = {
                if (showUnreadBadge && unreadCount > 0) {
                    Badge(modifier = Modifier.testTag(HOME_ACCOUNT_BADGE_TAG))
                }
            },
        ) {
            if (!avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = FeedStrings.account,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(AppDimens.AvatarSizeMedium)
                        .border(
                            AppDimens.Hairline,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                            CircleShape,
                        ).clip(CircleShape),
                )
            } else {
                AppIcon(
                    Icons.Default.AccountCircle,
                    contentDescription = FeedStrings.account,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(AppDimens.AvatarSizeMedium),
                )
            }
        }
    }
}

@Composable
internal fun HomeScreenTopBar(
    avatarUrl: String?,
    unreadCount: Int,
    showUnreadBadge: Boolean,
    onSearchClick: () -> Unit,
    onAccountClick: () -> Unit,
    pageBackground: Color,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            /** 固定顶栏采样下方滚动内容以呈现磨砂效果。 */
            .then(
                Modifier.appGlassBlur(
                    shape = RectangleShape,
                    tint = (
                        if (settingSkinIsMiuix()) {
                            LocalAppSkinTokens.current.surfaceContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        }
                    ).copy(alpha = 0.30f),
                ),
            ).background(appGlassContainerColor(pageBackground))
            .statusBarsPadding()
            .testTag(HOME_TOP_ACTIONS_TAG)
            .padding(top = AppDimens.Space8, bottom = AppDimens.Space4),
    ) {
        AppSearchBar(
            hint = FeedStrings.search,
            onClick = onSearchClick,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .testTag(HOME_SEARCH_BUTTON_TAG),
            trailing = {
                HomeAccountAvatarAction(
                    avatarUrl = avatarUrl,
                    unreadCount = unreadCount,
                    showUnreadBadge = showUnreadBadge,
                    onClick = onAccountClick,
                )
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeScreenAccountSheet(
    visible: Boolean,
    unreadCount: Int,
    showUnreadBadge: Boolean,
    onDismiss: () -> Unit,
) {
    if (visible) {
        AppBottomSheet(
            onDismissRequest = onDismiss,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            AccountSettingScreen(
                innerPadding = PaddingValues(AppDimens.ElevationNone),
                unreadCount = unreadCount,
                showUnreadBadge = showUnreadBadge,
                onDismissRequest = onDismiss,
            )
        }
    }
}
