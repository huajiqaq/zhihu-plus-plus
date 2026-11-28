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

package com.github.zly2006.zhihu.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import com.github.zly2006.zhihu.data.FeedDisplayItem
import com.github.zly2006.zhihu.data.plainSearchText
import com.github.zly2006.zhihu.i18n.WidgetsStrings
import com.github.zly2006.zhihu.platform.exportTestTagsForUiAutomation
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.util.cardText

@Composable
internal fun FeedCardPreviewDialog(
    onDismissRequest: () -> Unit,
    onOpen: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        val skin = LocalAppSkinTokens.current
        BoxWithConstraints {
            Card(
                shape = CardDefaults.shape,
                colors = CardDefaults.cardColors(containerColor = skin.surfaceContainerHigh),
                elevation = CardDefaults.cardElevation(defaultElevation = AppDimens.ElevationNone),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight * 0.9f)
                    .exportTestTagsForUiAutomation()
                    .testTag(FEED_CARD_PREVIEW_TAG),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = AppDimens.PageHorizontalPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.HeaderActionSpacing),
                ) {
                    Text(
                        text = WidgetsStrings.contentPreview,
                        modifier = Modifier.weight(1f).semantics { heading() },
                        style = skin.title4.copy(fontWeight = FontWeight.SemiBold),
                        color = skin.onSurface,
                    )
                    AppIconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.size(AppDimens.TouchTarget),
                    ) {
                        AppIcon(
                            imageVector = Icons.Default.Close,
                            contentDescription = WidgetsStrings.closeContentPreview,
                            tint = skin.onSurfaceVariantActions,
                            modifier = Modifier.size(AppDimens.IconSizeMedium),
                        )
                    }
                }
                content()
                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(
                        horizontal = AppDimens.PageHorizontalPadding,
                        vertical = AppDimens.DialogActionSpacing,
                    ),
                ) {
                    TextButton(onClick = onDismissRequest) {
                        Text(WidgetsStrings.close, color = skin.onSurfaceVariantSummary)
                    }
                    TextButton(
                        onClick = onOpen,
                        modifier = Modifier.testTag(FEED_CARD_PREVIEW_OPEN_TAG),
                    ) { Text(WidgetsStrings.open, color = skin.primary) }
                }
            }
        }
    }
}

@Composable
internal fun FeedCardPreviewBody(
    item: FeedDisplayItem,
    coverUrl: String?,
) {
    val skin = LocalAppSkinTokens.current
    coverUrl?.takeIf { it.isNotBlank() }?.let { url ->
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
        )
    }
    Column(
        Modifier.padding(
            horizontal = AppDimens.PageHorizontalPadding,
            vertical = AppDimens.FeedCardContentTopPadding,
        ),
    ) {
        Text(
            text = item.cardText(item.title, MaterialTheme.colorScheme.primary),
            style = skin.title4.copy(fontWeight = FontWeight.SemiBold),
            color = skin.onSurface,
        )
        item.summary?.takeIf { it.isNotBlank() }?.let { summary ->
            Spacer(Modifier.size(AppDimens.FeedCardTitleSpacing))
            Text(
                text = item.cardText(summary, MaterialTheme.colorScheme.primary),
                style = MaterialTheme.typography.bodyMedium,
                color = skin.onSurfaceVariantSummary,
            )
        }
        val details = item.displayDetails
        if (item.authorName != null || details.isNotBlank()) {
            Spacer(Modifier.size(AppDimens.FeedCardContentTopPadding))
            Row(verticalAlignment = Alignment.CenterVertically) {
                item.avatarSrc?.takeIf { it.isNotBlank() }?.let { avatar ->
                    AsyncImage(
                        model = avatar,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(AppDimens.AvatarSizeSmall).clip(CircleShape),
                    )
                    Spacer(Modifier.size(AppDimens.FeedCardTitleSpacing))
                }
                Column {
                    item.authorName?.takeIf { it.isNotBlank() }?.let { author ->
                        Text(
                            text = item.feed.plainSearchText(author),
                            style = MaterialTheme.typography.labelLarge,
                            color = skin.onSurface,
                        )
                    }
                    if (details.isNotBlank()) {
                        Text(
                            text = item.feed.plainSearchText(details),
                            style = MaterialTheme.typography.labelSmall,
                            color = skin.onSurfaceVariantSummary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
