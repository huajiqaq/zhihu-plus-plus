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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.github.zly2006.zhihu.platform

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material3.Text
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
import com.github.zly2006.zhihu.account.rememberZhihuAccountStore
import com.github.zly2006.zhihu.i18n.WidgetsStrings
import com.github.zly2006.zhihu.markdown.rememberMarkdownImageModel
import com.github.zly2006.zhihu.navigation.isWebPageUrl
import com.github.zly2006.zhihu.ui.components.AppButton
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppSpinner
import com.github.zly2006.zhihu.ui.components.AppIcon
import com.github.zly2006.zhihu.ui.components.AppIconButton
import com.github.zly2006.zhihu.ui.components.OpenImagePreviewContent
import com.github.zly2006.zhihu.ui.components.rememberNavEntryOnTop

private class GalleryRequest(val urls: List<String>, index: Int) {
    var index = index
    val transforms = mutableMapOf<String, GalleryTransform>()
}

private class GalleryTransform {
    var scale by mutableStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)

    fun zoomTo(value: Float, viewport: IntSize, pan: Offset = Offset.Zero) {
        val nextScale = value.coerceIn(1f, 5f)
        val nextOffset = offset * (nextScale / scale) + pan
        val maxX = viewport.width * (nextScale - 1f) / 2f
        val maxY = viewport.height * (nextScale - 1f) / 2f
        offset = Offset(nextOffset.x.coerceIn(-maxX, maxX), nextOffset.y.coerceIn(-maxY, maxY))
        scale = nextScale
    }
}

/** 查看器属于打开它的页面和账号；导航遮盖只隐藏窗口，不丢失页码与缩放位置。 */
@Composable
internal fun rememberComposeImageGalleryOpener(): ImageGalleryOpener {
    val account = rememberZhihuAccountStore()
    val owner = account.client
    var selection by remember(owner) { mutableStateOf<GalleryRequest?>(null) }
    val messages = rememberUserMessageSink()
    val onTop = rememberNavEntryOnTop()
    if (onTop) selection?.let { request ->
        Dialog(onDismissRequest = { selection = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            OpenImagePreviewContent(
                urls = request.urls,
                initialIndex = request.index,
                onDismiss = { selection = null },
                onOpenInBrowser = rememberSystemUrlOpener()::invoke,
                showControls = true,
                onCurrentPageChange = { request.index = it },
            ) { url, onDismiss, onLongClick, onPageSwipeEnabledChange ->
                val image = remember(request, url) { request.transforms.getOrPut(url) { GalleryTransform() } }
                var viewport by remember(url) { mutableStateOf(IntSize.Zero) }
                var attempt by remember(url) { mutableIntStateOf(0) }
                var loading by remember(url, attempt) { mutableStateOf(true) }
                var failed by remember(url, attempt) { mutableStateOf(false) }
                fun zoomTo(value: Float, pan: Offset = Offset.Zero) = image.zoomTo(value, viewport, pan)
                LaunchedEffect(viewport) { zoomTo(image.scale) }
                LaunchedEffect(image.scale) { onPageSwipeEnabledChange(image.scale == 1f) }
                val transform = rememberTransformableState { _, zoom, pan, _ -> zoomTo(image.scale * zoom, pan) }
                Box(Modifier.fillMaxSize().clipToBounds().onSizeChanged { viewport = it }) {
                    key(url, attempt) {
                        AsyncImage(
                        model = rememberMarkdownImageModel(url),
                        contentDescription = WidgetsStrings.imagePreview,
                        modifier = Modifier.fillMaxSize()
                            .transformable(state = transform, canPan = { image.scale > 1f })
                            .pointerInput(url) {
                                detectTapGestures(
                                    onTap = { if (image.scale == 1f) onDismiss() },
                                    onLongPress = onLongClick,
                                    onDoubleTap = { zoomTo(if (image.scale == 1f) 2f else 1f) },
                                )
                            }.graphicsLayer {
                                scaleX = image.scale
                                scaleY = image.scale
                                translationX = image.offset.x
                                translationY = image.offset.y
                            },
                        onLoading = { loading = true },
                        onSuccess = { loading = false; failed = false },
                        onError = { loading = false; failed = true },
                        )
                    }
                    if (loading) AppSpinner(modifier = Modifier.align(Alignment.Center))
                    if (failed) Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(AppDimens.Space12)) {
                        Text(WidgetsStrings.imageLoadFailed, color = Color.White)
                        AppButton(text = WidgetsStrings.retry, onClick = { attempt++ })
                    }
                    Row(Modifier.align(Alignment.TopCenter).background(Color.Black.copy(alpha = 0.6f))) {
                        AppIconButton(onClick = { zoomTo(image.scale / 1.5f) }, enabled = image.scale > 1f) {
                            AppIcon(Icons.Default.ZoomOut, WidgetsStrings.zoomOut, tint = Color.White)
                        }
                        AppIconButton(onClick = { zoomTo(1f) }, enabled = image.scale > 1f) {
                            AppIcon(Icons.Default.RestartAlt, WidgetsStrings.resetZoom, tint = Color.White)
                        }
                        AppIconButton(onClick = { zoomTo(image.scale * 1.5f) }, enabled = image.scale < 5f) {
                            AppIcon(Icons.Default.ZoomIn, WidgetsStrings.zoomIn, tint = Color.White)
                        }
                    }
                }
            }
        }
    }
    return remember(owner, messages) {
        object : ImageGalleryOpener {
            override fun invoke(urls: List<String>, initialIndex: Int) {
                if (urls.isEmpty() || account.client !== owner) return
                val current = urls[initialIndex.coerceIn(urls.indices)]
                val images = urls.filter(::isWebPageUrl).distinct()
                val page = images.indexOf(current)
                if (page >= 0) selection = GalleryRequest(images, page) else messages.showShortMessage(WidgetsStrings.invalidImageUrl)
            }
        }
    }
}

@Composable
internal fun rememberGalleryImagePreviewOpener(): ImagePreviewOpener {
    val gallery = rememberImageGalleryOpener()
    return remember(gallery) {
        object : ImagePreviewOpener {
            override fun invoke(url: String) = gallery(listOf(url), 0)
        }
    }
}
