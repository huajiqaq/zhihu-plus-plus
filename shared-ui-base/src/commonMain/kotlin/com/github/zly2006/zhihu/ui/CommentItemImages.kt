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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.github.zly2006.zhihu.markdown.rememberMarkdownImageModel
import com.github.zly2006.zhihu.platform.PlatformBackHandler
import com.github.zly2006.zhihu.platform.rememberImagePreviewOpener
import com.github.zly2006.zhihu.platform.rememberImageSaver
import com.github.zly2006.zhihu.platform.rememberImageSharer
import com.github.zly2006.zhihu.platform.rememberSystemUrlOpener
import com.github.zly2006.zhihu.ui.components.AppDropdownMenu
import com.github.zly2006.zhihu.ui.components.AppDropdownMenuItem

internal data class CommentImageItem(
    val url: String,
    val width: Int,
    val height: Int,
)

/** 单图在一列至两列的尺寸预算内按比例缩放，极端长宽比限制到两列。 */
internal fun commentImageDisplaySize(width: Int, height: Int, availableWidth: Dp = 300.dp): Pair<Dp, Dp> {
    val minimum = (availableWidth - 4.dp) / 3
    val maximum = minimum * 2 + 2.dp
    if (width <= 0 || height <= 0) return 120.dp to 120.dp
    val ratio = width.toFloat() / height
    return when {
        ratio < minimum.value / maximum.value -> minimum to maximum
        ratio > maximum.value / minimum.value -> maximum to minimum
        ratio >= 1f -> maximum to maximum / ratio
        else -> maximum * ratio to maximum
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun CommentImageGrid(
    images: List<CommentImageItem>,
    modifier: Modifier = Modifier,
) {
    if (images.isEmpty()) return
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val availableWidth = maxWidth
        val gridSize = (availableWidth - 16.dp) / 3
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = 3,
        ) {
            images.forEach { image ->
                val (width, height) = if (images.size == 1) {
                    commentImageDisplaySize(image.width, image.height, availableWidth)
                } else {
                    gridSize to gridSize
                }
                ClickableImageWithMenu(
                    imageUrl = image.url,
                    modifier = Modifier
                        .width(width)
                        .height(height)
                        .clip(RoundedCornerShape(6.dp)),
                    contentDescription = "评论图片",
                    contentScale = if (images.size == 1) ContentScale.Fit else ContentScale.Crop,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ClickableImageWithMenu(
    imageUrl: String,
    modifier: Modifier = Modifier,
    contentDescription: String = "图片",
    contentScale: ContentScale = ContentScale.Fit,
) {
    var showContextMenu by remember { mutableStateOf(false) }
    val openImagePreview = rememberImagePreviewOpener()
    val openSystemUrl = rememberSystemUrlOpener()
    val saveImage = rememberImageSaver()
    val shareImage = rememberImageSharer()

    PlatformBackHandler(enabled = showContextMenu) {
        showContextMenu = false
    }

    Box(
        modifier = modifier.combinedClickable(
            onClick = { openImagePreview(imageUrl) },
            onLongClick = { showContextMenu = true },
        ),
    ) {
        AsyncImage(
            model = rememberMarkdownImageModel(imageUrl),
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            contentScale = contentScale,
        )

        AppDropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false },
            renderInRootScaffold = false,
        ) {
            AppDropdownMenuItem(
                modifier = Modifier.testTag(COMMENT_IMAGE_MENU_OPEN_TAG),
                text = { Text("查看图片") },
                onClick = {
                    openImagePreview(imageUrl)
                    showContextMenu = false
                },
            )
            AppDropdownMenuItem(
                modifier = Modifier.testTag(COMMENT_IMAGE_MENU_BROWSER_TAG),
                text = { Text("在浏览器中打开") },
                onClick = {
                    openSystemUrl(imageUrl)
                    showContextMenu = false
                },
            )
            AppDropdownMenuItem(
                modifier = Modifier.testTag(COMMENT_IMAGE_MENU_SAVE_TAG),
                text = { Text("保存图片") },
                onClick = {
                    saveImage(imageUrl)
                    showContextMenu = false
                },
            )
            AppDropdownMenuItem(
                modifier = Modifier.testTag(COMMENT_IMAGE_MENU_SHARE_TAG),
                text = { Text("分享图片") },
                onClick = {
                    showContextMenu = false
                    shareImage(imageUrl)
                },
            )
        }
    }
}
