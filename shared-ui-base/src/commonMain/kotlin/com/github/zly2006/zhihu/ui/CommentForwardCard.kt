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
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.github.zly2006.zhihu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.github.zly2006.zhihu.data.VoteUpState
import com.github.zly2006.zhihu.i18n.CommentsStrings
import com.github.zly2006.zhihu.i18n.ContentStrings
import com.github.zly2006.zhihu.i18n.DomainStrings
import com.github.zly2006.zhihu.markdown.htmlToMdAst
import com.github.zly2006.zhihu.navigation.CommentHolder
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.Pin
import com.github.zly2006.zhihu.navigation.isWebPageUrl
import com.github.zly2006.zhihu.navigation.resolveNavigationUrl
import com.github.zly2006.zhihu.platform.ImageGalleryOpener
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppIcon
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.util.formatCompactCount
import com.github.zly2006.zhihu.util.formatRelativeTime
import com.github.zly2006.zhihu.viewmodel.comment.CommentForward
import com.hrm.markdown.renderer.Markdown
import com.hrm.markdown.renderer.MarkdownDefaults
import com.hrm.markdown.renderer.MarkdownInteractions

@Composable
internal fun CommentForwardCard(
    forward: CommentForward,
    readOnly: Boolean,
    gallery: ImageGalleryOpener,
    onComments: (NavDestination) -> Unit,
) {
    val skin = LocalAppSkinTokens.current
    val navigator = LocalNavigator.current
    val pin = forward.commentPin
    val detail = forward.destination
    val replyLabel = if (readOnly) CommentsStrings.normalComments else CommentsStrings.reply
    Column(
        Modifier
            .fillMaxWidth()
            .testTag("comment_forward_${forward.id}")
            .then(if (pin != null) Modifier.clickable(onClickLabel = replyLabel) { onComments(pin) } else Modifier)
            .padding(horizontal = AppDimens.PageHorizontalPadding, vertical = AppDimens.Space12),
        verticalArrangement = Arrangement.spacedBy(AppDimens.Space4),
    ) {
        ForwardAuthor(forward.author)
        forward.title?.takeIf(String::isNotBlank)?.let { title ->
            Text(title, style = skin.title3, color = skin.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        forward.summary?.takeIf(String::isNotBlank)?.let { ForwardHtml(it, skin.body2) }
        ForwardMedia(forward, gallery, detail)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(AppDimens.Space8), itemVerticalAlignment = Alignment.CenterVertically) {
            ForwardAttribution(forward.contentEndInfo?.publishTime, forward.contentEndInfo?.ipInfo)
            if (detail != null) {
                AppTextButton(
                    text = CommentsStrings.forwardFullText,
                    onClick = { navigator.onNavigate(detail) },
                    modifier = Modifier.testTag("comment_forward_detail_${forward.id}"),
                )
            }
            forward.reaction?.statistics?.upVoteCount?.let { count ->
                ForwardVotes(count, VoteUpState.from(forward.reaction?.relation?.vote) == VoteUpState.Up)
            }
            AppTextButton(
                onClick = { if (pin != null) onComments(pin) },
                enabled = pin != null,
                modifier = Modifier.testTag("comment_forward_reply_${forward.id}"),
                contentPadding = PaddingValues(horizontal = AppDimens.Space8),
            ) {
                AppIcon(Icons.AutoMirrored.Outlined.Comment, contentDescription = replyLabel, modifier = Modifier.size(18.dp))
                forward.comments?.count?.let { count ->
                    Spacer(Modifier.width(AppDimens.Space4))
                    Text(formatCompactCount(count), style = skin.footnote1)
                }
            }
        }
        val previews = forward.comments
            ?.list
            .orEmpty()
            .take(3)
        if (previews.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().background(skin.surfaceContainer, RoundedCornerShape(8.dp)).padding(AppDimens.Space12),
                verticalArrangement = Arrangement.spacedBy(AppDimens.Space8),
            ) {
                for (preview in previews) {
                    val id = preview.id?.takeIf { it.isNotEmpty() && it.all { char -> char in '0'..'9' } }
                    val target = if (pin != null && id != null) CommentHolder(id, pin) else null
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .testTag("comment_forward_preview_${forward.id}_${preview.id}")
                            .clickable(enabled = target != null, onClickLabel = replyLabel) { if (target != null) onComments(target) },
                        verticalArrangement = Arrangement.spacedBy(AppDimens.Space4),
                    ) {
                        ForwardAuthor(preview.author, preview.authorLabels, compact = true)
                        preview.content?.takeIf(String::isNotBlank)?.let { ForwardHtml(it, skin.body2) }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(AppDimens.Space8), itemVerticalAlignment = Alignment.CenterVertically) {
                            ForwardAttribution(preview.createdTime, preview.commentTag?.firstOrNull { it.type == "ip_info" }?.text)
                            preview.likeCount?.let { ForwardVotes(it, preview.liked == true) }
                        }
                    }
                }
                val count = forward.comments?.count
                if (pin != null && count != null && count > 3) {
                    AppTextButton(
                        text = CommentsStrings.forwardAllComments(count),
                        onClick = { onComments(pin) },
                        modifier = Modifier.testTag("comment_forward_all_${forward.id}"),
                    )
                }
            }
        }
    }
    HorizontalDivider()
}

@Composable
private fun ForwardAuthor(author: CommentForward.Author?, labels: List<String> = emptyList(), compact: Boolean = false) {
    val name = author?.name?.takeIf(String::isNotBlank)
    val avatar = author?.image
    if (avatar == null && name == null && labels.isEmpty()) return
    val skin = LocalAppSkinTokens.current
    Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.Space8), verticalAlignment = Alignment.CenterVertically) {
        if (avatar != null) {
            AsyncImage(
                model = avatar,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(if (compact) 24.dp else 32.dp).clip(CircleShape),
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(AppDimens.Space4), itemVerticalAlignment = Alignment.CenterVertically) {
            if (name != null) Text(name, style = skin.footnote1, color = skin.onSurface)
            for (label in labels) AuthorTag(label)
        }
    }
}

@Composable
private fun ForwardAttribution(time: Long?, region: String?) {
    val text = listOfNotNull(time?.takeIf { it > 0 }?.let { formatRelativeTime(it) }, region?.takeIf(String::isNotBlank)).joinToString(" · ")
    if (text.isNotEmpty()) {
        val skin = LocalAppSkinTokens.current
        Text(text, style = skin.footnote2, color = skin.onSurfaceVariantSummary)
    }
}

@Composable
private fun ForwardVotes(count: Int, voted: Boolean) {
    val skin = LocalAppSkinTokens.current
    val color = if (voted) skin.primary else skin.onSurfaceVariantActions
    Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.Space4), verticalAlignment = Alignment.CenterVertically) {
        AppIcon(
            if (voted) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
            contentDescription = if (voted) ContentStrings.upvoted else ContentStrings.upvoteCount(count),
            modifier = Modifier.size(16.dp),
            tint = color,
        )
        Text(formatCompactCount(count), style = skin.footnote2, color = color)
    }
}

@Composable
private fun ForwardHtml(html: String, style: TextStyle) {
    val navigator = LocalNavigator.current
    Markdown(
        document = remember(html) { htmlToMdAst(html) },
        colors = MarkdownDefaults.material3Colors(),
        typography = MarkdownDefaults.typography().copy(text = style),
        enableScroll = false,
        enableSelection = false,
        interactions = MarkdownInteractions(onLinkClick = { url ->
            if (isWebPageUrl(url)) resolveNavigationUrl(url)?.let(navigator.onNavigate)
        }),
    )
}

@Composable
private fun ForwardMedia(forward: CommentForward, gallery: ImageGalleryOpener, detail: Pin?) {
    val navigator = LocalNavigator.current
    val video = forward.video
    if (video != null) {
        val cover = video.imageUrl ?: return
        val portrait = (video.height ?: 0) > (video.width ?: 0)
        Box(
            modifier = (if (portrait) Modifier.size(132.dp, 176.dp) else Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                .clip(RoundedCornerShape(8.dp))
                .testTag("comment_forward_video_${forward.id}")
                .then(if (detail != null) Modifier.clickable(onClickLabel = CommentsStrings.viewPin) { navigator.onNavigate(detail) } else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(model = cover, contentDescription = DomainStrings.videoCover, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            AppIcon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(36.dp).background(Color.Black.copy(alpha = 0.54f), CircleShape).padding(AppDimens.Space4),
                tint = Color.White,
            )
        }
        return
    }
    val images = remember(forward.images) {
        forward.images
            .orEmpty()
            .mapNotNull { image -> image.imageUrl?.let { it to image } }
            .take(9)
    }
    if (images.isEmpty()) return
    val urls = remember(images) { images.map { it.first } }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(AppDimens.Space4),
    ) {
        images.forEachIndexed { index, (url, image) ->
            val ratio = (image.width?.takeIf { it > 0 } ?: 1).toFloat() / (image.height?.takeIf { it > 0 } ?: 1)
            AsyncImage(
                model = url,
                contentDescription = CommentsStrings.forwardImage(index + 1),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .height(96.dp)
                    .aspectRatio(ratio.coerceIn(0.75f, 1.8f))
                    .clip(RoundedCornerShape(6.dp))
                    .testTag("comment_forward_image_${forward.id}_$index")
                    .clickable(onClickLabel = CommentsStrings.viewImage) { gallery(urls, index) },
            )
        }
    }
}
