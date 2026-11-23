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

package com.github.zly2006.zhihu.markdown

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.github.zly2006.zhihu.platform.rememberImageGalleryOpener
import com.github.zly2006.zhihu.platform.rememberImageSaver
import com.github.zly2006.zhihu.platform.rememberImageSharer
import com.github.zly2006.zhihu.platform.rememberSystemUrlOpener
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppDropdownMenuItem
import com.github.zly2006.zhihu.ui.components.AppImageContextMenu
import com.hrm.markdown.renderer.MarkdownImageData

/** 两套图片引擎共享平板预览预算；外部滚动正文用窗口可视高度，不用整篇正文高度。 */
@Composable
internal fun tabletMarkdownImageBounds(): DpSize? {
    val window = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val width = with(density) { window.width.toDp() }
    val height = with(density) { window.height.toDp() }
    return if (minOf(width, height) >= 600.dp) {
        DpSize(AppDimens.ContentMaxWidth, height * 0.6f)
    } else {
        null
    }
}

@Composable
fun RenderImage(
    data: MarkdownImageData,
    modifier: Modifier,
    imageUrls: List<String> = listOf(data.url),
) {
    val openImageGallery = rememberImageGalleryOpener()
    var expanded by remember(data.url) { mutableStateOf(false) }
    var windowPressPoint by remember(data.url) { mutableStateOf<Offset?>(null) }
    var windowImageBounds by remember(data.url) { mutableStateOf<Rect?>(null) }
    var imageCoordinates by remember(data.url) { mutableStateOf<LayoutCoordinates?>(null) }
    val hapticFeedback = LocalHapticFeedback.current
    val previewUrls = remember(imageUrls, data.url) {
        imageUrls.ifEmpty { listOf(data.url) }
    }
    val imageBounds = tabletMarkdownImageBounds()
    var decodedSize by remember(data.url) { mutableStateOf<IntSize?>(null) }
    val imageWidth = data.width?.takeIf { it > 0 } ?: decodedSize?.width?.takeIf { imageBounds != null }
    val imageHeight = data.height?.takeIf { it > 0 } ?: decodedSize?.height?.takeIf { imageBounds != null }
    val imageAspectRatio =
        if (imageWidth != null && imageHeight != null && imageWidth > 0 && imageHeight > 0) {
            imageWidth.toFloat() / imageHeight
        } else {
            null
        }

    fun openGallery() {
        val initialIndex = previewUrls.indexOf(data.url).takeIf { it >= 0 } ?: 0
        openImageGallery(previewUrls, initialIndex)
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        val imageSizeModifier = if (imageBounds == null) {
            Modifier.fillMaxWidth(0.8f)
        } else {
            val widthLimit = minOf(maxWidth, imageBounds.width, imageWidth?.dp ?: maxWidth)
            val displayWidth = if (imageAspectRatio != null) {
                widthLimit.coerceAtMost(imageBounds.height * imageAspectRatio)
            } else {
                widthLimit
            }
            Modifier.width(displayWidth).heightIn(max = imageBounds.height)
        }
        AsyncImage(
            model = rememberMarkdownImageModel(data.url),
            contentDescription = data.altText,
            contentScale = ContentScale.Fit,
            onSuccess = { state ->
                val image = state.result.image
                if (image.width > 0 && image.height > 0) {
                    decodedSize = IntSize(image.width, image.height)
                }
            },
            modifier = modifier
                .then(imageSizeModifier)
                .then(
                    if (imageAspectRatio != null) {
                        Modifier.aspectRatio(imageAspectRatio)
                    } else {
                        Modifier
                    },
                ).onGloballyPositioned { coordinates ->
                    imageCoordinates = coordinates
                    windowImageBounds = coordinates.boundsInWindow()
                }.pointerInput(data.url, hapticFeedback) {
                    detectTapGestures(
                        onTap = {
                            openGallery()
                        },
                        onLongPress = { offset ->
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            imageCoordinates?.takeIf { it.isAttached }?.let { coordinates ->
                                windowImageBounds = coordinates.boundsInWindow()
                                windowPressPoint = coordinates.localToWindow(offset)
                            } ?: run { windowPressPoint = null }
                            expanded = true
                        },
                    )
                }.semantics {
                    onClick(label = "查看原图") {
                        openGallery()
                        true
                    }
                    onLongClick(label = "图片操作") {
                        windowPressPoint = null
                        windowImageBounds = imageCoordinates?.takeIf { it.isAttached }?.boundsInWindow()
                        expanded = true
                        true
                    }
                },
        )

        MarkdownImageMenu(
            url = data.url,
            expanded = expanded,
            onDismiss = { expanded = false },
            onViewImage = ::openGallery,
            windowImageBounds = windowImageBounds,
            windowPressPoint = windowPressPoint,
        )
    }
}

@Composable
internal fun MarkdownImageMenu(
    url: String,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onViewImage: () -> Unit,
    windowImageBounds: Rect?,
    windowPressPoint: Offset? = null,
) {
    val openSystemUrl = rememberSystemUrlOpener()
    val saveImage = rememberImageSaver()
    val shareImage = rememberImageSharer()
    /** 弹窗文字不能注册到父级选区，否则跨窗口坐标排序会失败。 */
    DisableSelection {
        AppImageContextMenu(
            expanded = expanded,
            onDismissRequest = onDismiss,
            windowImageBounds = windowImageBounds,
            windowPressPoint = windowPressPoint,
        ) {
            AppDropdownMenuItem(
                text = { Text("查看图片") },
                onClick = {
                    onDismiss()
                    onViewImage()
                },
            )
            AppDropdownMenuItem(
                text = { Text("在浏览器中打开") },
                onClick = {
                    onDismiss()
                    openSystemUrl(url)
                },
            )
            AppDropdownMenuItem(
                text = { Text("保存图片") },
                onClick = {
                    onDismiss()
                    saveImage(url)
                },
            )
            AppDropdownMenuItem(
                text = { Text("分享图片") },
                onClick = {
                    onDismiss()
                    shareImage(url)
                },
            )
        }
    }
}
