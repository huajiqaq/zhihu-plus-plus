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

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import coil3.compose.AsyncImage
import com.github.zly2006.zhihu.editor.UploadedZhihuImage
import com.github.zly2006.zhihu.markdown.rememberMarkdownImageModel
import com.github.zly2006.zhihu.ui.components.AppCard
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppTextButton

internal const val PIN_IMAGE_LIMIT = 9

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WritePinImageList(
    images: List<UploadedZhihuImage>,
    imageListBounds: Rect,
    floatingActionBounds: Rect,
    layoutDirection: LayoutDirection,
    density: Density,
    isSubmitting: Boolean,
    onBoundsChanged: (Rect) -> Unit,
    onRemoveImage: (Int) -> Unit,
) {
    val attachmentEndPadding = if (
        imageListBounds.top < floatingActionBounds.bottom && imageListBounds.bottom > floatingActionBounds.top
    ) {
        val overlapWidth = if (layoutDirection == LayoutDirection.Ltr) {
            imageListBounds.right - floatingActionBounds.left
        } else {
            floatingActionBounds.right - imageListBounds.left
        }
        with(density) { overlapWidth.coerceAtLeast(0f).toDp() } + AppDimens.Space8
    } else {
        AppDimens.ElevationNone
    }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(bottom = AppDimens.Space8)
                .onGloballyPositioned { onBoundsChanged(it.boundsInRoot()) }
                .testTag(WRITE_PIN_IMAGE_LIST_TAG),
        verticalArrangement = Arrangement.spacedBy(AppDimens.Space8),
    ) {
        Text(
            text = "图片 ${images.size}/$PIN_IMAGE_LIMIT",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(end = attachmentEndPadding)
                    .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.Space8),
        ) {
            images.forEachIndexed { index, image ->
                AppCard(modifier = Modifier.width(AppDimens.Space120)) {
                    AsyncImage(
                        model = rememberMarkdownImageModel(image.url),
                        contentDescription = "想法图片 ${index + 1}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(AppDimens.Space64),
                    )
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = AppDimens.Space8, vertical = AppDimens.Space4),
                        verticalArrangement = Arrangement.spacedBy(AppDimens.Space4),
                    ) {
                        Text(
                            text = "图片 ${index + 1}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "${image.rawWidth} x ${image.rawHeight}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    AppTextButton(
                        text = "删除图片 ${index + 1}",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onRemoveImage(index) },
                        enabled = !isSubmitting,
                    )
                }
            }
        }
    }
}
