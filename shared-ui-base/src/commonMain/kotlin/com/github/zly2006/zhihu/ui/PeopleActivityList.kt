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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.authorDestination
import com.github.zly2006.zhihu.data.decodeFeedNavDestinationOrNull
import com.github.zly2006.zhihu.data.encodeStoredNavDestination
import com.github.zly2006.zhihu.data.navDestination
import com.github.zly2006.zhihu.data.officialBadge
import com.github.zly2006.zhihu.data.target
import com.github.zly2006.zhihu.i18n.ReaderStrings
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.Person
import com.github.zly2006.zhihu.navigation.Pin
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.navigation.withReadingQueueSource
import com.github.zly2006.zhihu.platform.rememberImagePreviewOpener
import com.github.zly2006.zhihu.reading.RegisterReadingQueueSource
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppDropdownMenuItem
import com.github.zly2006.zhihu.ui.components.AppIcon
import com.github.zly2006.zhihu.ui.components.AuthorBadge
import com.github.zly2006.zhihu.ui.components.CommentScreenComponent
import com.github.zly2006.zhihu.ui.components.FeedCard
import com.github.zly2006.zhihu.ui.components.PaginatedList
import com.github.zly2006.zhihu.util.formatCompactCount
import com.github.zly2006.zhihu.util.formatRelativeTime
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment
import com.github.zly2006.zhihu.viewmodel.feed.PeopleActivitiesViewModel
import com.github.zly2006.zhihu.viewmodel.feed.PeopleActivity

@Composable
fun PeopleActivityList(
    model: PeopleActivitiesViewModel,
    environment: PaginationEnvironment,
    listState: LazyListState,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(model, active) {
        if (active && model.allData.isEmpty() && !model.isEnd && model.errorMessage == null) model.loadMore(environment)
    }
    if (active) RegisterReadingQueueSource(model.readingQueueSourceId, model.allData.map { it.item })

    var commentKey by rememberSaveable(model.person.userTokenOrId) { mutableStateOf<String?>(null) }
    var showComments by rememberSaveable(model.person.userTokenOrId) { mutableStateOf(false) }
    var authorCommentPolicy by rememberSaveable(model.person.userTokenOrId) { mutableStateOf(false) }
    val commentTarget = remember(commentKey) { commentKey?.let(::decodeFeedNavDestinationOrNull) }
    if (commentTarget != null) {
        key(commentKey) {
            CommentScreenComponent(
                showComments = showComments && active,
                onDismiss = { showComments = false },
                content = commentTarget,
                isZhPlusAuthorContent = authorCommentPolicy,
            )
        }
    }
    PaginatedList(
        items = model.allData,
        listState = listState,
        onLoadMore = { if (active) model.loadMore(environment) },
        isEnd = { model.isEnd },
        isLoading = { model.isLoading },
        loadError = { model.errorMessage },
        onRetry = { model.retry(environment) },
        key = { it.item.stableKey },
        modifier = modifier,
    ) { activity ->
        PeopleActivityCard(activity, model.person, model.readingQueueSourceId) { destination ->
            commentKey = destination.encodeStoredNavDestination()
            authorCommentPolicy = activity.item.feed
                ?.target
                ?.author
                ?.id == DataHolder.ZH_PLUS_AUTHOR_USER_ID &&
                (destination is Pin || destination is Article && destination.type == ArticleType.Answer)
            showComments = true
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PeopleActivityCard(
    activity: PeopleActivity,
    profile: Person,
    sourceId: String,
    onComments: (NavDestination) -> Unit,
) {
    val item = activity.item
    val skin = LocalAppSkinTokens.current
    val navigator = LocalNavigator.current
    val openImage = rememberImagePreviewOpener()
    val destination = remember(item, sourceId) { item.navDestination?.withReadingQueueSource(sourceId) }
    val author = item.authorDestination
    val actor = activity.actor
    val actorDestination = actor?.destination
    val name = actor?.name?.takeIf(String::isNotBlank)
    val action = activity.action?.let { if (name != null) it.removePrefix(name).trim() else it }?.takeIf(String::isNotBlank)
    FeedCard(
        item = item,
        readingQueueSourceId = sourceId,
        thumbnailUrl = activity.imageUrls.firstOrNull(),
        modifier = Modifier.testTag("people_screen_activity_item_${item.stableKey}"),
        menuItems = { dismiss ->
            if (destination != null) {
                AppDropdownMenuItem(text = { Text(ReaderStrings.viewContent) }, onClick = {
                    dismiss()
                    navigator.onNavigate(destination)
                })
            }
            if (author != null && author != profile) {
                AppDropdownMenuItem(text = { Text(ReaderStrings.viewContentAuthor) }, onClick = {
                    dismiss()
                    navigator.onNavigate(author)
                })
            }
        },
        cardBody = { showImages, moreMenu ->
            Column(verticalArrangement = Arrangement.spacedBy(AppDimens.Space8)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        modifier = Modifier.weight(1f).then(
                            if (actorDestination != null && actorDestination != profile) {
                                Modifier.clickable(role = Role.Button, onClickLabel = ReaderStrings.viewActorProfile) { navigator.onNavigate(actorDestination) }
                            } else {
                                Modifier
                            },
                        ),
                        horizontalArrangement = Arrangement.spacedBy(AppDimens.Space8),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        actor?.avatarUrl?.takeIf(String::isNotBlank)?.let { avatar ->
                            AsyncImage(model = avatar, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(AppDimens.AvatarSizeMedium).clip(CircleShape))
                        }
                        Column {
                            if (name != null) {
                                Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.Space4), verticalAlignment = Alignment.CenterVertically) {
                                    Text(name, style = skin.body2.copy(fontWeight = FontWeight.SemiBold), color = skin.onSurface, modifier = Modifier.weight(1f, fill = false))
                                    AuthorBadge(actor.badgeV2.officialBadge(), compact = true)
                                }
                            }
                            val byline = listOfNotNull(action, activity.time?.let { formatRelativeTime(it) }).joinToString(" · ")
                            if (byline.isNotEmpty()) Text(byline, style = skin.footnote2, color = skin.onSurfaceVariantSummary)
                        }
                    }
                    moreMenu()
                }
                if (item.title.isNotBlank()) Text(item.title, style = skin.title4.copy(fontWeight = FontWeight.SemiBold), color = skin.onSurface)
                item.summary?.let { summary ->
                    Text(summary, style = skin.body2, color = skin.onSurfaceVariantSummary, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
                activity.quote?.let { quote ->
                    Text(
                        quote,
                        style = skin.body2,
                        color = skin.onSurfaceVariantSummary,
                        modifier = Modifier.fillMaxWidth().background(skin.surfaceContainerHigh, RoundedCornerShape(AppDimens.SmallCornerRadius)).padding(AppDimens.Space8),
                    )
                }
                if (showImages && activity.imageUrls.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth(if (activity.imageUrls.size == 1) 0.5f else 1f), horizontalArrangement = Arrangement.spacedBy(AppDimens.FeedCardImageSpacing)) {
                        activity.imageUrls.forEachIndexed { index, url ->
                            AsyncImage(
                                model = url,
                                contentDescription = ReaderStrings.viewAttachedImage(index + 1),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1.4f)
                                    .clip(RoundedCornerShape(AppDimens.SmallCornerRadius))
                                    .clickable(role = Role.Button) { openImage(url) },
                            )
                        }
                    }
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    activity.likes?.let { count ->
                        ActivityStat(Icons.Outlined.ThumbUp, ReaderStrings.likes, count, ReaderStrings.viewLikesDetail) { destination?.let(navigator.onNavigate) }
                    }
                    activity.favorites?.let { count ->
                        ActivityStat(Icons.Outlined.BookmarkBorder, ReaderStrings.saves, count, if (destination is Article) ReaderStrings.manageSavesDetail else null) { destination?.let(navigator.onNavigate) }
                    }
                    activity.comments?.let { count ->
                        val supported = destination is Article || destination is Pin || destination is Question
                        ActivityStat(Icons.Outlined.ChatBubbleOutline, ReaderStrings.comments, count, if (supported) ReaderStrings.viewComments else null) { destination?.let(onComments) }
                    }
                }
            }
        },
    )
}

@Composable
private fun ActivityStat(icon: ImageVector, label: String, count: Int, clickLabel: String?, onClick: () -> Unit) {
    val skin = LocalAppSkinTokens.current
    Row(
        modifier = (if (clickLabel == null) Modifier else Modifier.clickable(role = Role.Button, onClickLabel = clickLabel, onClick = onClick))
            .heightIn(min = AppDimens.TouchTarget)
            .padding(horizontal = AppDimens.Space4),
        horizontalArrangement = Arrangement.spacedBy(AppDimens.Space4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(icon, contentDescription = label, tint = skin.onSurfaceVariantActions, modifier = Modifier.size(AppDimens.IconSizeCompact))
        Text(formatCompactCount(count), style = skin.footnote1, color = skin.onSurfaceVariantActions)
    }
}
