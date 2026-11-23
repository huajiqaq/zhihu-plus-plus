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

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import coil3.compose.LocalPlatformContext
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.Feed
import com.github.zly2006.zhihu.data.FeedDisplayItem
import com.github.zly2006.zhihu.data.navDestination
import com.github.zly2006.zhihu.data.target
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.withReadingQueueSource
import com.github.zly2006.zhihu.platform.UserMessageDuration
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.ui.LocalSelectedContentDestination
import com.github.zly2006.zhihu.ui.rememberObservedSetting
import com.github.zly2006.zhihu.ui.subscreens.FEED_CARD_STYLE_PREFERENCE_KEY

const val FEED_CARD_PREVIEW_TAG = "feed_card_preview"
const val FEED_CARD_PREVIEW_OPEN_TAG = "feed_card_preview_open"

/**
 * 两套皮肤共用完整标题、摘要和作者层级；卡片与分割线外观均支持长按预览列表内容。
 * 默认点击保留导航目标与阅读队列来源，页面可以提供独立点击行为和业务菜单。
 */
@Composable
fun FeedCard(
    item: FeedDisplayItem,
    modifier: Modifier = Modifier,
    readingQueueSourceId: String? = null,
    thumbnailUrl: String? = null,
    horizontalPadding: Dp = Dp.Unspecified,
    menuItems: @Composable ColumnScope.(dismissMenu: () -> Unit) -> Unit = { _ -> },
    showSourceLabel: Boolean = false,
    titleTrailingContent: (@Composable () -> Unit)? = null,
    cardBody: (@Composable (showImages: Boolean, moreMenu: @Composable () -> Unit) -> Unit)? = null,
    /**
     * 默认点击行为：优先跳转到信息流条目的详情页；如果只能识别为外链则打开外链，否则提示暂不支持。
     */
    onClick: ((item: FeedDisplayItem, destination: NavDestination?) -> Unit)? = null,
) {
    val navigator = LocalNavigator.current
    val uriHandler = LocalUriHandler.current
    val userMessages = rememberUserMessageSink()
    val settings = rememberSettingsStore()
    val selectedDestination = LocalSelectedContentDestination.current
    var showMenu by remember { mutableStateOf(false) }
    var showPreview by remember { mutableStateOf(false) }
    val imageContext = LocalPlatformContext.current
    val thumbnailModel = remember(imageContext, thumbnailUrl) {
        thumbnailUrl?.takeIf { it.isNotBlank() }?.let {
            ImageRequest
                .Builder(imageContext)
                .data(it)
                // 样本图片在 off 上用默认 UA 返回 403，浏览器 UA 可正常返回原图。
                .httpHeaders(NetworkHeaders.Builder().set("User-Agent", "Mozilla/5.0").build())
                .build()
        }
    }
    val showFeedThumbnail by rememberObservedSetting(settings, "showFeedThumbnail") {
        getBoolean("showFeedThumbnail", true)
    }
    val feedCardStyle by rememberObservedSetting(settings, FEED_CARD_STYLE_PREFERENCE_KEY) {
        getString(FEED_CARD_STYLE_PREFERENCE_KEY, "card")
    }
    val pinImages = (item.feed?.target as? Feed.PinTarget)
        ?.content
        ?.filterIsInstance<DataHolder.Pin.ContentImage>()
        .orEmpty()
    val isSelected = item.navDestination != null && item.navDestination == selectedDestination
    val isMiuix = settingSkinIsMiuix()
    val resolvedHorizontalPadding = if (horizontalPadding == Dp.Unspecified) {
        if (isMiuix) AppDimens.FeedCardHorizontalPaddingMiuix else AppDimens.FeedCardHorizontalPaddingM3e
    } else {
        horizontalPadding
    }
    val performClick: (FeedDisplayItem) -> Unit = { clickedItem ->
        val destination = clickedItem.navDestination?.withReadingQueueSource(readingQueueSourceId)
        if (onClick != null) {
            onClick(clickedItem, destination)
        } else {
            destination?.let(navigator.onNavigate) ?: run {
                val contentUrl = clickedItem.content
                if (contentUrl?.startsWith("http") == true) {
                    uriHandler.openUri(contentUrl)
                } else {
                    userMessages.showMessage("暂不支持打开该内容", UserMessageDuration.Short)
                }
            }
        }
    }
    val cardContent: @Composable () -> Unit = {
        if (cardBody != null && !item.isFiltered) {
            cardBody(showFeedThumbnail) {
                FeedCardMenuBox(item, showMenu, { showMenu = it }, menuItems)
            }
        } else {
            FeedCardContent(
                item = item,
                showFeedThumbnail = showFeedThumbnail,
                thumbnailModel = thumbnailModel,
                pinImages = pinImages,
                showMenu = showMenu,
                onShowMenuChange = { showMenu = it },
                menuItems = menuItems,
                showSourceLabel = showSourceLabel,
                titleTrailingContent = titleTrailingContent,
            )
        }
    }
    if (showPreview) {
        val coverUrl = thumbnailUrl ?: pinImages.firstOrNull()?.let { it.thumbnail.ifBlank { it.url } }
        FeedCardPreviewDialog(
            onDismissRequest = { showPreview = false },
            onOpen = {
                showPreview = false
                performClick(item)
            },
        ) {
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
                FeedCardPreviewBody(item = item, coverUrl = coverUrl)
            }
        }
    }

    if (feedCardStyle == "divider") {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .semantics { selected = isSelected }
                .clip(RectangleShape)
                .combinedClickable(
                    onClick = { performClick(item) },
                    onLongClick = { showPreview = true },
                ),
        ) {
            Column(
                Modifier
                    .padding(horizontal = resolvedHorizontalPadding)
                    .padding(
                        start = AppDimens.FeedCardContentHorizontalPadding,
                        top = AppDimens.FeedCardContentTopPadding,
                        end = AppDimens.FeedCardContentHorizontalPadding,
                        bottom = AppDimens.FeedCardContentBottomPadding,
                    ),
            ) {
                cardContent()
            }
            HorizontalDivider(
                thickness = AppDimens.DividerThickness,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
        return
    }

    if (isMiuix) {
        MiuixFeedCard(
            modifier = modifier
                .fillMaxWidth()
                .semantics { selected = isSelected }
                .padding(
                    horizontal = resolvedHorizontalPadding,
                    vertical = AppDimens.FeedCardVerticalSpacingMiuix,
                ),
            onClick = { performClick(item) },
            onLongClick = { showPreview = true },
            content = cardContent,
        )
        return
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .semantics { selected = isSelected }
            .padding(
                horizontal = resolvedHorizontalPadding,
                vertical = AppDimens.FeedCardVerticalSpacingM3e,
            ),
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            shape = CardDefaults.shape,
            elevation = CardDefaults.cardElevation(defaultElevation = AppDimens.ElevationNone),
            modifier = Modifier
                .fillMaxWidth()
                .clip(CardDefaults.shape)
                .combinedClickable(
                    onClick = { performClick(item) },
                    onLongClick = { showPreview = true },
                ),
        ) {
            Column(
                Modifier.padding(
                    start = AppDimens.FeedCardContentHorizontalPadding,
                    top = AppDimens.FeedCardContentTopPadding,
                    end = AppDimens.FeedCardContentHorizontalPadding,
                    bottom = AppDimens.FeedCardContentBottomPadding,
                ),
            ) {
                cardContent()
            }
        }
    }
}
