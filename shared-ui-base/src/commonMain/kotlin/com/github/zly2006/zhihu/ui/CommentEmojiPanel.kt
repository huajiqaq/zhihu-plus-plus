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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.ui.components.AppChip
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppIconButton
import com.github.zly2006.zhihu.ui.components.AppSpinner
import com.github.zly2006.zhihu.ui.components.settingSkinIsMiuix
import com.github.zly2006.zhihu.viewmodel.comment.CommentSticker
import com.github.zly2006.zhihu.viewmodel.comment.CommentStickerGroup
import kotlin.math.min
import com.github.zly2006.zhihu.ui.components.AppIcon as Icon

sealed interface EmojiPanelEntry {
    data object Default : EmojiPanelEntry

    data object Favorites : EmojiPanelEntry

    data class Group(
        val group: CommentStickerGroup,
    ) : EmojiPanelEntry
}

@Composable
internal fun DefaultCommentEmojiGrid(
    emojis: List<CommentEmoji>,
    emojiInlineContent: Map<String, InlineTextContent>,
    onEmojiClick: (CommentEmoji) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (emojis.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                text = "暂无可用表情",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 48.dp),
        modifier = modifier,
        contentPadding = PaddingValues(8.dp),
    ) {
        items(
            items = emojis,
            key = CommentEmoji::placeholder,
        ) { emoji ->
            AppIconButton(
                onClick = { onEmojiClick(emoji) },
                modifier = Modifier
                    .size(48.dp)
                    .testTag(COMMENT_EMOJI_ITEM_TAG_PREFIX + emoji.placeholder),
            ) {
                Text(
                    text = remember(emoji) {
                        buildAnnotatedString {
                            appendInlineContent(emoji.inlineKey, emoji.placeholder)
                        }
                    },
                    inlineContent = emojiInlineContent,
                    fontSize = 28.sp,
                )
            }
        }
    }
}

@Composable
internal fun EmojiPanelChip(
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    label: String? = null,
    icon: ImageVector? = null,
    isVip: Boolean = false,
) {
    AppChip(
        selected = selected,
        onClick = onClick,
        label = {
            if (label != null) {
                Text(label)
            }
        },
        leadingIcon = if (icon != null) {
            {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(AppDimens.IconSizeSmall),
                )
            }
        } else {
            null
        },
        trailingIcon = if (isVip) {
            {
                Text(
                    text = "VIP",
                    style = if (settingSkinIsMiuix()) LocalAppSkinTokens.current.footnote2 else MaterialTheme.typography.labelSmall,
                    color = if (selected && settingSkinIsMiuix()) LocalContentColor.current else LocalAppSkinTokens.current.primary,
                )
            }
        } else {
            null
        },
        modifier = Modifier
            .heightIn(min = AppDimens.TouchTarget)
            .testTag(testTag),
    )
}

@Composable
internal fun FavoritesStickerPanel(
    collected: List<CommentSticker>,
    recommended: List<CommentSticker>,
    isUploading: Boolean,
    onUpload: (() -> Unit)?,
    onCollectedClick: (CommentSticker) -> Unit,
    onCollectedLongClick: (CommentSticker) -> Unit,
    onRecommendedClick: (CommentSticker) -> Unit,
    onRecommendedLongClick: (CommentSticker) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 64.dp),
        modifier = modifier,
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(
            key = "collected_header",
            span = { GridItemSpan(maxLineSpan) },
        ) {
            Text(
                text = "收藏的表情包",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        if (onUpload != null) {
            item(key = "sticker_upload") {
                StickerUploadTile(isUploading = isUploading, onUpload = onUpload)
            }
        }
        items(collected, key = { "collected_${it.id}" }) { sticker ->
            StickerCell(
                sticker = sticker,
                onClick = { onCollectedClick(sticker) },
                onLongClick = { onCollectedLongClick(sticker) },
            )
        }
        item(
            key = "recommended_header",
            span = { GridItemSpan(maxLineSpan) },
        ) {
            Text(
                text = "推荐表情",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        items(recommended, key = { "recommended_${it.id}" }) { sticker ->
            StickerCell(
                sticker = sticker,
                onClick = { onRecommendedClick(sticker) },
                onLongClick = { onRecommendedLongClick(sticker) },
            )
        }
    }
}

@Composable
private fun StickerUploadTile(
    isUploading: Boolean,
    onUpload: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .width(64.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(enabled = !isUploading, onClick = onUpload)
            .testTag(COMMENT_STICKER_UPLOAD_TAG),
    ) {
        if (isUploading) {
            AppSpinner(size = 18.dp)
        } else {
            Icon(
                Icons.Default.Add,
                contentDescription = "上传表情",
                modifier = Modifier.size(18.dp),
            )
            Text("上传", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
internal fun StickerGrid(
    stickers: List<CommentSticker>,
    onClick: (CommentSticker) -> Unit,
    modifier: Modifier = Modifier,
    showTitles: Boolean = false,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 64.dp),
        modifier = modifier,
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (stickers.isEmpty()) {
            item(
                key = "sticker_empty",
                span = { GridItemSpan(maxLineSpan) },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "暂无贴纸",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        items(stickers, key = { it.id }) { sticker ->
            StickerCell(
                sticker = sticker,
                onClick = { onClick(sticker) },
                showTitle = showTitles,
            )
        }
    }
}

@Composable
private fun StickerCell(
    sticker: CommentSticker,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    showTitle: Boolean = true,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(64.dp)
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ).testTag(COMMENT_STICKER_ITEM_TAG_PREFIX + sticker.id)
            .padding(2.dp),
    ) {
        Box(
            modifier = Modifier.size(56.dp),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = sticker.thumbnailUrl,
                contentDescription = sticker.title.ifBlank { "贴纸" },
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
            if (sticker.isVipSticker) {
                Text(
                    text = "VIP",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(4.dp),
                        ).padding(horizontal = 3.dp, vertical = 1.dp),
                )
            }
        }
        if (showTitle && sticker.title.isNotBlank()) {
            Text(
                text = sticker.title,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
