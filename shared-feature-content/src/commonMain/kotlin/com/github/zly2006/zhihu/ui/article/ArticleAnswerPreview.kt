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

package com.github.zly2006.zhihu.ui.article

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.fleeksoft.ksoup.Ksoup
import com.github.zly2006.zhihu.i18n.AppLocale
import com.github.zly2006.zhihu.i18n.ContentStrings
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppIcon
import com.github.zly2006.zhihu.util.formatCompactCount
import com.github.zly2006.zhihu.viewmodel.ArticleViewModel.CachedAnswerContent
import org.jetbrains.compose.resources.painterResource
import zhihu.uicomponents.generated.resources.Res
import zhihu.uicomponents.generated.resources.ic_vote_up_24dp

@Composable
internal fun CachedAnswerPreview(cached: CachedAnswerContent, previous: Boolean = false) {
    val tokens = LocalAppSkinTokens.current
    val excerpt = remember(cached.sourceContent, cached.content, AppLocale.current) {
        val document = Ksoup.parse(cached.sourceContent ?: cached.content)
        val blocks = document.select("p, h1, h2, h3, h4, h5, h6, li, pre, img")
        blocks
            .joinToString("\n\n") { block ->
                when (block.tagName()) {
                    "img" -> ContentStrings.imagePlaceholder
                    "pre" -> block.wholeText()
                    else -> block.text()
                }
            }.ifBlank { document.text() }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("article_answer_cached_preview_${cached.article.id}")
            .background(tokens.surfaceContainer),
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = AppDimens.ContentMaxWidth)
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + AppDimens.Space96)
                .padding(horizontal = AppDimens.PageHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(AppDimens.Space16),
        ) {
            Text(
                text = ContentStrings.answerPreviewFrom(cached.sourceLabel),
                modifier = Modifier
                    .background(tokens.secondaryVariant, MaterialTheme.shapes.small)
                    .padding(horizontal = AppDimens.Space12, vertical = AppDimens.Space6),
                color = tokens.onSecondaryVariant,
                style = tokens.footnote2,
            )
            Text(
                text = cached.title,
                color = tokens.onSurface,
                style = tokens.title2,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(AppDimens.Space12),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(AppDimens.Space40)
                        .background(tokens.secondaryVariant, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = cached.authorName.take(1),
                        color = tokens.onSecondaryVariant,
                        style = tokens.title4,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(AppDimens.Space2)) {
                    Text(text = cached.authorName, color = tokens.onSurface, style = tokens.title4)
                    if (cached.authorBio.isNotBlank()) {
                        Text(
                            text = cached.authorBio,
                            color = tokens.onSurfaceVariantSummary,
                            style = tokens.footnote2,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(AppDimens.Space16),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.Space4),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_vote_up_24dp),
                        contentDescription = null,
                        tint = tokens.onSurfaceVariantSummary,
                        modifier = Modifier.size(AppDimens.IconSizeSmall),
                    )
                    Text(
                        text = ContentStrings.compactUpvotes(cached.voteUpCount, formatCompactCount(cached.voteUpCount)),
                        color = tokens.onSurfaceVariantSummary,
                        style = tokens.footnote2,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.Space4),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(
                        Icons.AutoMirrored.Filled.Comment,
                        contentDescription = null,
                        tint = tokens.onSurfaceVariantSummary,
                        modifier = Modifier.size(AppDimens.IconSizeSmall),
                    )
                    Text(
                        text = ContentStrings.compactComments(cached.commentCount, formatCompactCount(cached.commentCount)),
                        color = tokens.onSurfaceVariantSummary,
                        style = tokens.footnote2,
                    )
                }
            }
            Text(
                text = excerpt.ifBlank { if (previous) ContentStrings.preparingPreviousAnswer else ContentStrings.preparingNextAnswer },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(tokens.surface, MaterialTheme.shapes.extraLarge)
                    .padding(AppDimens.Space20),
                color = tokens.onSurface,
                style = tokens.main,
                maxLines = 16,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(AppDimens.Space96)
                .background(Brush.verticalGradient(listOf(Color.Transparent, tokens.surfaceContainer))),
        )
    }
}
