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

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.fleeksoft.ksoup.Ksoup
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.Person
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.platform.rememberWebViewUrlOpener
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.ui.components.RecordedVoiceComment
import com.github.zly2006.zhihu.ui.components.VoiceCommentPlayer
import com.github.zly2006.zhihu.ui.subscreens.PREF_FONT_SIZE
import com.github.zly2006.zhihu.ui.subscreens.PREF_LINE_HEIGHT
import com.github.zly2006.zhihu.util.twoDigitString
import com.github.zly2006.zhihu.viewmodel.CommentItem
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CommentItem(
    comment: CommentItem,
    voicePlayer: VoiceCommentPlayer,
    modifier: Modifier = Modifier,
    localVoice: RecordedVoiceComment? = null,
    isLiked: Boolean = false,
    likeCount: Int = 0,
    isLikeLoading: Boolean = false,
    toggleLike: () -> Unit = {},
    onChildCommentClick: (CommentItem) -> Unit,
    onDelete: (() -> Unit)? = null,
    quotedSentence: String? = null,
) {
    val navigator = LocalNavigator.current
    val openWebUrl = rememberWebViewUrlOpener()
    val commentData = comment.item
    val authorPerson =
        Person(
            id = commentData.author.id,
            name = commentData.author.name,
            urlToken = commentData.author.urlToken,
        )
    var showMoreMenu by remember(commentData.id) { mutableStateOf(false) }
    var isContentExpanded by rememberSaveable(commentData.id, commentData.content) { mutableStateOf(false) }
    var collapsedTextOverflows by remember(commentData.content) { mutableStateOf(false) }
    var collapsedTextHeight by remember(commentData.content) { mutableIntStateOf(0) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
        ) {
            AsyncImage(
                model = commentData.author.avatarUrl,
                contentDescription = "头像",
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(onClickLabel = "打开个人主页") { navigator.onNavigate(authorPerson) },
                contentScale = ContentScale.Crop,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(
                verticalArrangement = Arrangement.Top,
                modifier = Modifier,
            ) {
                CommentItemHeader(
                    comment = comment,
                    authorPerson = authorPerson,
                    quotedSentence = quotedSentence,
                    onNavigate = navigator.onNavigate,
                )
                val document = remember(commentData.content) { Ksoup.parseBodyFragment(commentData.content) }
                val commentImages = remember(commentData.content) {
                    document.select("a.comment_img, a.comment_gif").mapNotNull { element ->
                        val url = element.attr("href").takeIf(String::isNotBlank) ?: return@mapNotNull null
                        CommentImageItem(
                            url = url,
                            width = element.attr("data-width").toIntOrNull() ?: 0,
                            height = element.attr("data-height").toIntOrNull() ?: 0,
                        )
                    }
                }
                val commentStickers = remember(commentData.content) {
                    document.select("a.comment_sticker").mapNotNull { it.attr("href").takeIf(String::isNotBlank) }
                }
                val emojisUsed = remember { mutableSetOf<String>() }
                val uriHandler by rememberUpdatedState(LocalUriHandler.current)
                val linkSkin = LocalAppSkinTokens.current
                val linkBackground = MaterialTheme.colorScheme.surfaceContainerLow
                val accentLuminance = linkSkin.primary.luminance()
                val backgroundLuminance = linkBackground.luminance()
                val linkContrast = (maxOf(accentLuminance, backgroundLuminance) + 0.05f) / (minOf(accentLuminance, backgroundLuminance) + 0.05f)
                val linkColor = if (linkContrast >= 4.5f) linkSkin.primary else linkSkin.onSurface
                val string = remember(commentData.content, linkColor) {
                    emojisUsed.clear()
                    buildAnnotatedString {
                        val stripped = document.body().clone()
                        stripped.select("a.comment_img").forEach { it.remove() }
                        stripped.select("a.comment_gif").forEach { it.remove() }
                        stripped.select("a.comment_sticker").forEach { it.remove() }
                        dfsSimple(
                            node = stripped,
                            onOpenLink = { uriHandler.openUri(it) },
                            linkColor = linkColor,
                            componentUsed = emojisUsed,
                        )
                    }
                }

                val inlineContent = rememberCommentEmojiInlineContent(emojisUsed)

                Column(modifier = Modifier.animateContentSize()) {
                    val settings = rememberSettingsStore()
                    val fontSizePercent by rememberObservedSetting(settings, PREF_FONT_SIZE) { getInt(PREF_FONT_SIZE, 100) }
                    val lineHeightPercent by rememberObservedSetting(settings, PREF_LINE_HEIGHT) { getInt(PREF_LINE_HEIGHT, 160) }
                    val density = LocalDensity.current
                    val canExpand = commentData.canUnfold &&
                        (collapsedTextOverflows || commentImages.isNotEmpty() || commentStickers.isNotEmpty() || commentData.voiceInfo != null)
                    val isFolded = canExpand && !isContentExpanded
                    CommentItemText(
                        commentId = commentData.id,
                        string = string,
                        inlineContent = inlineContent,
                        fontSizePercent = fontSizePercent,
                        lineHeightPercent = lineHeightPercent,
                        density = density,
                        canExpand = canExpand,
                        isFolded = isFolded,
                        collapsedTextHeight = collapsedTextHeight,
                        isContentExpanded = isContentExpanded,
                        onTextLayout = { layout ->
                            collapsedTextOverflows = layout.lineCount > 2
                            collapsedTextHeight = layout.getLineBottom(minOf(1, layout.lineCount - 1)).toInt()
                        },
                        onToggleExpanded = { isContentExpanded = !isContentExpanded },
                    )
                    CommentItemMedia(
                        comment = comment,
                        commentImages = commentImages,
                        commentStickers = commentStickers,
                        isFolded = isFolded,
                        localVoice = localVoice,
                        voicePlayer = voicePlayer,
                    )
                }
            }
        }

        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 44.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            val formattedTime = remember(commentData.createdTime) {
                formatCommentTime(commentData.createdTime)
            }

            Text(
                text = formattedTime,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val ipInfo = comment.item.commentTag
                .firstOrNull {
                    it.type == "ip_info"
                }?.text
            if (ipInfo != null) {
                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = ipInfo,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            CommentItemActions(
                comment = comment,
                showMoreMenu = showMoreMenu,
                onShowMoreMenu = { showMoreMenu = true },
                onDismissMoreMenu = { showMoreMenu = false },
                onDelete = onDelete?.let { delete ->
                    {
                        showMoreMenu = false
                        delete()
                    }
                },
                onReport = {
                    showMoreMenu = false
                    openWebUrl(reportUrl(commentData.id, "comment"))
                },
                isLiked = isLiked,
                likeCount = likeCount,
                isLikeLoading = isLikeLoading,
                toggleLike = toggleLike,
                onChildCommentClick = onChildCommentClick,
            )
        }
    }
}

private fun formatCommentTime(createdTimeSeconds: Long): String {
    val zone = TimeZone.currentSystemDefault()
    val dateTime = Instant.fromEpochSeconds(createdTimeSeconds).toLocalDateTime(zone)
    val now = Clock.System.now().toLocalDateTime(zone)
    return when {
        dateTime.date == now.date -> dateTime.formatHms()
        dateTime.year == now.year -> "${dateTime.month.number.twoDigitString()}-${dateTime.day.twoDigitString()} ${dateTime.formatHms()}"
        else -> "${dateTime.year}-${dateTime.month.number.twoDigitString()}-${dateTime.day.twoDigitString()} ${dateTime.formatHms()}"
    }
}

private fun LocalDateTime.formatHms(): String =
    "${hour.twoDigitString()}:${minute.twoDigitString()}:${second.twoDigitString()}"

@Composable
fun AuthorTag(authorTag: String) {
    Box(
        modifier = Modifier
            .border(
                width = 0.5.dp,
                color = Color.Gray,
                shape = RoundedCornerShape(3.dp),
            ).padding(horizontal = 3.dp),
    ) {
        Text(
            text = authorTag,
            fontSize = 12.sp,
            lineHeight = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
