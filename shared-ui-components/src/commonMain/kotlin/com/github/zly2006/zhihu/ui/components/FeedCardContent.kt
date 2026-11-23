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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.FeedDisplayItem
import com.github.zly2006.zhihu.data.authorDestination
import com.github.zly2006.zhihu.data.officialBadge
import com.github.zly2006.zhihu.data.plainSearchText
import com.github.zly2006.zhihu.data.sourceLabel
import com.github.zly2006.zhihu.navigation.Account
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.ui.rememberObservedSetting
import com.github.zly2006.zhihu.ui.subscreens.PREF_FONT_SIZE
import com.github.zly2006.zhihu.ui.subscreens.PREF_LINE_HEIGHT
import com.github.zly2006.zhihu.util.cardText
import com.github.zly2006.zhihu.viewmodel.QUALITY_FILTER_MODE_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.components.AppIcon as Icon

/**
 * 信息流卡片右上角的更多菜单。
 *
 * 卡片只负责菜单的展开、收起和通用设置项；页面业务动作由 [menuItems] 直接提供。
 */
@Composable
internal fun FeedCardMenuBox(
    item: FeedDisplayItem,
    showMenu: Boolean,
    onShowMenuChange: (Boolean) -> Unit,
    menuItems: @Composable ColumnScope.(dismissMenu: () -> Unit) -> Unit,
) {
    val navigator = LocalNavigator.current
    Box {
        AppIconButton(
            onClick = { onShowMenuChange(true) },
            modifier = Modifier.size(AppDimens.TouchTarget),
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "更多选项",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(AppDimens.IconSizeCompact),
            )
        }
        AppDropdownMenu(
            expanded = showMenu,
            onDismissRequest = { onShowMenuChange(false) },
        ) {
            menuItems { onShowMenuChange(false) }
            AppDropdownMenuItem(
                text = { Text("外观设置") },
                onClick = {
                    onShowMenuChange(false)
                    navigator.onNavigate(Account.AppearanceSettings())
                },
            )
            if (item.isQualityFiltered) {
                AppDropdownMenuItem(
                    text = { Text("调整质量屏蔽") },
                    onClick = {
                        onShowMenuChange(false)
                        navigator.onNavigate(Account.RecommendSettings(QUALITY_FILTER_MODE_PREFERENCE_KEY))
                    },
                )
            }
        }
    }
}

/**
 * 信息流卡片正文内容。
 *
 * 标题在上，摘要与缩略图并排，作者信息放在底部；两套皮肤只更换字号来源，不改变信息层级。
 */
@Composable
internal fun FeedCardContent(
    item: FeedDisplayItem,
    showFeedThumbnail: Boolean,
    thumbnailModel: ImageRequest?,
    pinImages: List<DataHolder.Pin.ContentImage>,
    showMenu: Boolean,
    onShowMenuChange: (Boolean) -> Unit,
    menuItems: @Composable ColumnScope.(dismissMenu: () -> Unit) -> Unit,
    showSourceLabel: Boolean,
    titleTrailingContent: (@Composable () -> Unit)?,
) {
    val settings = rememberSettingsStore()
    val fontSizePercent by rememberObservedSetting(settings, PREF_FONT_SIZE) { getInt(PREF_FONT_SIZE, 100) }
    val lineHeightPercent by rememberObservedSetting(settings, PREF_LINE_HEIGHT) { getInt(PREF_LINE_HEIGHT, 160) }
    val visiblePinImages = pinImages.takeIf { showFeedThumbnail && !item.isFiltered }.orEmpty()
    val sourceLabel = item.feed?.sourceLabel.takeUnless { item.isFiltered }
    val navigator = LocalNavigator.current
    val isMiuix = settingSkinIsMiuix()
    if (showSourceLabel) {
        FeedCardSourceLabel(sourceLabel)
    }
    if (item.isQualityFiltered) {
        Text(
            text = item.content.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp).testTag("feed_quality_filter_reason"),
        )
    }
    if (item.title.isNotEmpty()) {
        val titleStyle = if (isMiuix) {
            LocalAppSkinTokens.current.title4.copy(fontWeight = FontWeight.SemiBold)
        } else {
            MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
        }
        val scaledTitleLineHeight = if (titleStyle.lineHeight == TextUnit.Unspecified) {
            TextUnit.Unspecified
        } else {
            titleStyle.lineHeight * fontSizePercent / 100
        }
        Row(
            modifier = Modifier.padding(bottom = AppDimens.FeedCardTitleSpacing),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimens.Space6),
        ) {
            Text(
                text = item.cardText(item.title, MaterialTheme.colorScheme.primary),
                style = titleStyle.copy(
                    fontSize = titleStyle.fontSize * fontSizePercent / 100,
                    lineHeight = scaledTitleLineHeight,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = (if (titleTrailingContent == null) Modifier else Modifier.weight(1f, fill = false))
                    .testTag("feed_card_title"),
            )
            titleTrailingContent?.invoke()
        }
    }
    Column {
        Row {
            Text(
                text = item.cardText(item.summary.orEmpty(), MaterialTheme.colorScheme.primary),
                style = (if (isMiuix) LocalAppSkinTokens.current.body2 else MaterialTheme.typography.bodyMedium).copy(
                    fontSize = 14.sp * fontSizePercent / 100,
                    lineHeight = 14.sp * fontSizePercent / 100 * lineHeightPercent / 100,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (thumbnailModel != null && showFeedThumbnail && !item.isFiltered) {
                Spacer(modifier = Modifier.width(AppDimens.FeedCardThumbnailSpacing))
                AsyncImage(
                    model = thumbnailModel,
                    contentDescription = "Thumbnail",
                    modifier = Modifier
                        .padding(top = AppDimens.Space8)
                        .sizeIn(
                            maxHeight = AppDimens.FeedCardThumbnailMaxHeight,
                            maxWidth = AppDimens.FeedCardThumbnailMaxWidth,
                        ).clip(RoundedCornerShape(AppDimens.SmallCornerRadius)),
                    contentScale = ContentScale.FillHeight,
                )
            }
        }
        PinFeedImages(images = visiblePinImages, modifier = Modifier.padding(top = AppDimens.Space8))
        if (item.details.isNotEmpty() || (item.avatarSrc != null && item.authorName != null)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = AppDimens.Space8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(AppDimens.ContentMetadataSpacing),
                ) {
                    val avatarSrc = item.avatarSrc
                    val authorName = item.authorName?.let { item.feed.plainSearchText(it) }
                    if (avatarSrc != null && authorName != null) {
                        val authorDestination = remember(item) { item.authorDestination }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable(enabled = authorDestination != null) {
                                authorDestination?.let(navigator.onNavigate)
                            },
                        ) {
                            AsyncImage(
                                model = avatarSrc,
                                contentDescription = "Avatar",
                                modifier = Modifier.clip(CircleShape).size(AppDimens.AvatarSizeSmall),
                            )
                            Spacer(Modifier.width(AppDimens.Space8))
                            Text(
                                text = authorName,
                                style = if (isMiuix) LocalAppSkinTokens.current.footnote1 else MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            val authorBadge = item.authorBadgeV2.officialBadge()
                            if (authorBadge?.isUsefulInList == true) {
                                Spacer(Modifier.width(AppDimens.ContentMetadataSpacing))
                                AuthorBadge(authorBadge, compact = true)
                            }
                        }
                    }
                    if (item.details.isNotEmpty()) {
                        Text(
                            text = item.feed.plainSearchText(item.details),
                            style = if (isMiuix) LocalAppSkinTokens.current.footnote2 else MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (item.details.isNotEmpty()) {
                    FeedCardMenuBox(item, showMenu, onShowMenuChange, menuItems)
                }
            }
        }
    }
}

@Composable
private fun PinFeedImages(
    images: List<DataHolder.Pin.ContentImage>,
    modifier: Modifier = Modifier,
) {
    when (images.size) {
        0 -> return
        1 -> {
            val image = images.single()
            AsyncImage(
                model = image.feedThumbnailUrl,
                contentDescription = "想法图片 1/1",
                modifier = modifier
                    .fillMaxWidth(1f / 3f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .testTag("pin_feed_image_0"),
                contentScale = ContentScale.Crop,
            )
        }
        in 2..4 -> {
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .testTag("pin_feed_images"),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                images.forEachIndexed { index, image ->
                    PinFeedImage(
                        image = image,
                        index = index,
                        totalCount = images.size,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f),
                    )
                }
                repeat((3 - images.size).coerceAtLeast(0)) {
                    Spacer(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f),
                    )
                }
            }
        }
        else -> {
            val visibleImages = images.take(9)
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .testTag("pin_feed_images"),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                visibleImages.chunked(3).forEachIndexed { rowIndex, rowImages ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        rowImages.forEachIndexed { columnIndex, image ->
                            val index = rowIndex * 3 + columnIndex
                            PinFeedImage(
                                image = image,
                                index = index,
                                totalCount = images.size,
                                remainingCount = (images.size - 9).takeIf { index == 8 && it > 0 },
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f),
                            )
                        }
                        repeat(3 - rowImages.size) {
                            Spacer(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PinFeedImage(
    image: DataHolder.Pin.ContentImage,
    index: Int,
    totalCount: Int,
    remainingCount: Int? = null,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .testTag("pin_feed_image_$index"),
    ) {
        AsyncImage(
            model = image.feedThumbnailUrl,
            contentDescription = "想法图片 ${index + 1}/$totalCount",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        if (remainingCount != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "+$remainingCount",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        }
    }
}

internal val DataHolder.Pin.ContentImage.feedThumbnailUrl: String
    get() = thumbnail.ifBlank { url }

@Composable
private fun FeedCardSourceLabel(sourceLabel: String?) {
    val label = sourceLabel?.takeIf { it.isNotBlank() } ?: return
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}
