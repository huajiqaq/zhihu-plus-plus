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
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.github.zly2006.zhihu.data.FeedDisplayItem
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.ui.components.AppCard
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.settingSkinIsMiuix

@Composable
internal fun QuestionAnswerStrip(
    items: List<FeedDisplayItem>,
    onSelect: (FeedDisplayItem) -> Unit,
) {
    val stripState = rememberLazyListState()
    val snap = rememberSnapFlingBehavior(stripState)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val previewWidth = (maxWidth * 0.82f).coerceIn(AppDimens.Space160, AppDimens.Space240)
        LazyRow(
            state = stripState,
            flingBehavior = snap,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.Space12),
        ) {
            items(items, key = { it.stableKey }) { item ->
                AppCard(
                    modifier = Modifier.width(previewWidth).testTag("question_answer_strip_${item.stableKey}"),
                    onClick = { onSelect(item) },
                    containerColor = if (settingSkinIsMiuix()) {
                        LocalAppSkinTokens.current.surface
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    contentColor = LocalAppSkinTokens.current.onSurface,
                ) {
                    Column(
                        Modifier.padding(AppDimens.Space12),
                        verticalArrangement = Arrangement.spacedBy(AppDimens.Space8),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!item.avatarSrc.isNullOrBlank()) {
                                AsyncImage(
                                    model = item.avatarSrc,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(24.dp).clip(CircleShape),
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(
                                            if (settingSkinIsMiuix()) {
                                                LocalAppSkinTokens.current.surfaceVariant
                                            } else {
                                                MaterialTheme.colorScheme.secondaryContainer
                                            },
                                            CircleShape,
                                        ),
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = item.authorName.orEmpty(),
                                style = if (settingSkinIsMiuix()) LocalAppSkinTokens.current.footnote2 else MaterialTheme.typography.labelMedium,
                                color = if (settingSkinIsMiuix()) {
                                    LocalAppSkinTokens.current.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = item.summary.orEmpty().ifBlank { item.title },
                            style = if (settingSkinIsMiuix()) LocalAppSkinTokens.current.footnote1 else MaterialTheme.typography.bodySmall,
                            color = if (settingSkinIsMiuix()) {
                                LocalAppSkinTokens.current.onSurfaceVariantSummary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = item.details.orEmpty(),
                            style = if (settingSkinIsMiuix()) LocalAppSkinTokens.current.footnote2 else MaterialTheme.typography.labelSmall,
                            color = if (settingSkinIsMiuix()) {
                                LocalAppSkinTokens.current.onSurfaceVariantSummary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
