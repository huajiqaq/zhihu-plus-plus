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

package com.github.zly2006.zhihu.platform

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.github.zly2006.zhihu.i18n.WidgetsStrings
import com.github.zly2006.zhihu.markdown.rememberMarkdownImageModel
import com.github.zly2006.zhihu.navigation.isWebPageUrl
import com.github.zly2006.zhihu.ui.components.AppIcon
import com.github.zly2006.zhihu.ui.components.AppIconButton
import com.github.zly2006.zhihu.ui.components.OpenImagePreviewContent

@Composable
actual fun rememberImagePreviewOpener(): ImagePreviewOpener {
    val gallery = rememberImageGalleryOpener()
    return remember(gallery) {
        object : ImagePreviewOpener {
            override fun invoke(url: String) = gallery(listOf(url), 0)
        }
    }
}

@Composable
actual fun rememberImageGalleryOpener(): ImageGalleryOpener {
    var selection by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }
    val messages = rememberUserMessageSink()
    selection?.let { (urls, page) ->
        Dialog(onDismissRequest = { selection = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            OpenImagePreviewContent(
                urls = urls,
                initialIndex = page,
                onDismiss = { selection = null },
                onOpenInBrowser = rememberSystemUrlOpener()::invoke,
                showControls = true,
            ) { url, onDismiss, onLongClick, onPageSwipeEnabledChange ->
                var scale by remember(url) { mutableStateOf(1f) }
                var offset by remember(url) { mutableStateOf(Offset.Zero) }
                var viewport by remember(url) { mutableStateOf(IntSize.Zero) }
                fun zoomTo(value: Float, pan: Offset = Offset.Zero) {
                    val nextScale = value.coerceIn(1f, 5f)
                    val nextOffset = offset * (nextScale / scale) + pan
                    val maxX = viewport.width * (nextScale - 1f) / 2f
                    val maxY = viewport.height * (nextScale - 1f) / 2f
                    offset = Offset(nextOffset.x.coerceIn(-maxX, maxX), nextOffset.y.coerceIn(-maxY, maxY))
                    scale = nextScale
                    onPageSwipeEnabledChange(nextScale == 1f)
                }
                LaunchedEffect(viewport) { zoomTo(scale) }
                val transform = rememberTransformableState { _, zoom, pan, _ -> zoomTo(scale * zoom, pan) }
                Box(Modifier.fillMaxSize().clipToBounds().onSizeChanged { viewport = it }) {
                    AsyncImage(
                        model = rememberMarkdownImageModel(url),
                        contentDescription = WidgetsStrings.imagePreview,
                        modifier = Modifier.fillMaxSize()
                            .transformable(state = transform, canPan = { scale > 1f })
                            .pointerInput(url) {
                                detectTapGestures(
                                    onTap = { if (scale == 1f) onDismiss() },
                                    onLongPress = onLongClick,
                                    onDoubleTap = { zoomTo(if (scale == 1f) 2f else 1f) },
                                )
                            }.graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offset.x
                                translationY = offset.y
                            },
                        onError = { messages.showShortMessage(WidgetsStrings.imageLoadFailed) },
                    )
                    Row(Modifier.align(Alignment.TopCenter).background(Color.Black.copy(alpha = 0.6f))) {
                        AppIconButton(onClick = { zoomTo(scale / 1.5f) }, enabled = scale > 1f) {
                            AppIcon(Icons.Default.ZoomOut, WidgetsStrings.zoomOut, tint = Color.White)
                        }
                        AppIconButton(onClick = { zoomTo(1f) }, enabled = scale > 1f) {
                            AppIcon(Icons.Default.RestartAlt, WidgetsStrings.resetZoom, tint = Color.White)
                        }
                        AppIconButton(onClick = { zoomTo(scale * 1.5f) }, enabled = scale < 5f) {
                            AppIcon(Icons.Default.ZoomIn, WidgetsStrings.zoomIn, tint = Color.White)
                        }
                    }
                }
            }
        }
    }
    return remember(messages) {
        object : ImageGalleryOpener {
            override fun invoke(urls: List<String>, initialIndex: Int) {
                if (urls.isEmpty()) return
                val current = urls[initialIndex.coerceIn(urls.indices)]
                val images = urls.filter(::isWebPageUrl).distinct()
                val page = images.indexOf(current)
                if (page >= 0) selection = images to page else messages.showShortMessage(WidgetsStrings.invalidImageUrl)
            }
        }
    }
}
