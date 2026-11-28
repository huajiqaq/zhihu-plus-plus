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

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.officialBadge
import com.github.zly2006.zhihu.i18n.ContentStrings
import com.github.zly2006.zhihu.markdown.LocalMarkdownWidthManagedByHost
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.Person
import com.github.zly2006.zhihu.navigation.Pin
import com.github.zly2006.zhihu.navigation.Topic
import com.github.zly2006.zhihu.navigation.resolveNavigationUrl
import com.github.zly2006.zhihu.ui.components.AppCard
import com.github.zly2006.zhihu.ui.components.AppChip
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppEmptyState
import com.github.zly2006.zhihu.ui.components.AppPageTitleBarScrollState
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.ui.components.AuthorBadge
import com.github.zly2006.zhihu.ui.components.ContentEndMarker
import com.github.zly2006.zhihu.ui.components.PageTurnTarget
import com.github.zly2006.zhihu.ui.components.appPageTitleBarScroll
import com.github.zly2006.zhihu.ui.components.pageTurnViewportWithGuide
import com.github.zly2006.zhihu.util.formatCompactCount
import com.github.zly2006.zhihu.util.formatFullDateTime
import com.github.zly2006.zhihu.viewmodel.ZhihuApiEnvironment

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PinContent(
    pin: DataHolder.Pin,
    scrollState: ScrollState,
    pageTurnTarget: PageTurnTarget,
    topBarScrollBehavior: AppPageTitleBarScrollState,
    environment: ZhihuApiEnvironment,
    likeCount: Int,
    canFollowAuthor: Boolean,
    authorFollowChanging: Boolean,
    authorFollowError: String?,
    onFollowAuthor: () -> Unit,
    pollVotingOptionId: String?,
    pollErrorMessage: String?,
    loadError: String?,
    operationError: String?,
    onRetry: () -> Unit,
    onRetryLike: () -> Unit,
    onSocialCreditClick: () -> Unit,
    onPollVote: (pollId: String, optionId: String) -> Unit,
) {
    val navigator = LocalNavigator.current
    val readingPlayerOverlayPadding = LocalReadingPlayerOverlayPadding.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .appPageTitleBarScroll(topBarScrollBehavior)
            .pageTurnViewportWithGuide(pageTurnTarget)
            .verticalScroll(scrollState)
            .testTag(PIN_SCREEN_SCROLL_TAG)
            .padding(
                start = AppDimens.PageHorizontalPadding,
                end = AppDimens.PageHorizontalPadding,
                top = 12.dp,
                bottom = 24.dp,
            ),
    ) {
        if (loadError != null) {
            Text(
                text = ContentStrings.pinRefreshError(loadError),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag(PIN_SCREEN_ERROR_TAG),
            )
            AppTextButton(text = ContentStrings.retry, onClick = onRetry, modifier = Modifier.testTag(PIN_SCREEN_RETRY_TAG))
        }
        if (operationError != null) {
            Text(
                text = ContentStrings.operationError(operationError),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag(PIN_SCREEN_OPERATION_ERROR_TAG),
            )
            AppTextButton(text = ContentStrings.retryLike, onClick = onRetryLike)
        }
        val textMeasurer = rememberTextMeasurer()
        val minimumAuthorWidth = with(LocalDensity.current) {
            textMeasurer
                .measure(
                    pin.author.name.take(4),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    softWrap = false,
                ).size.width
                .toDp() + AppDimens.TouchTarget + AppDimens.Space12
        }
        PinContentAuthorHeader(
            pin = pin,
            minimumAuthorWidth = minimumAuthorWidth,
            canFollowAuthor = canFollowAuthor,
            authorFollowChanging = authorFollowChanging,
            onFollowAuthor = onFollowAuthor,
            onAuthorClick = {
                navigator.onNavigate(Person(pin.author.id, pin.author.urlToken, pin.author.name))
            },
        )
        if (authorFollowError != null) {
            Text(
                text = authorFollowError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = AppDimens.Space8).testTag(PIN_SCREEN_AUTHOR_FOLLOW_ERROR_TAG),
            )
        }

        pin.ipInfo?.takeIf(String::isNotBlank)?.let { ipInfo ->
            Text(
                text = ipInfo,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = AppDimens.Space8).testTag(PIN_SCREEN_IP_INFO_TAG),
            )
        }

        if (likeCount > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            val firstLiker = pin.likers.firstOrNull()
            Text(
                text = if (firstLiker != null) {
                    ContentStrings.pinNamedLikes(likeCount, firstLiker.name, formatCompactCount(likeCount))
                } else {
                    ContentStrings.pinLikes(likeCount, formatCompactCount(likeCount))
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable(onClick = onSocialCreditClick),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (pin.contentHtml.isBlank() && pin.content.isEmpty() && pin.bottomPoll == null) {
            AppEmptyState(
                title = ContentStrings.noVisibleBody,
                description = ContentStrings.pinNoBodyHelp,
                modifier = Modifier.testTag(PIN_SCREEN_BODY_EMPTY_TAG),
            )
        } else {
            CompositionLocalProvider(LocalMarkdownWidthManagedByHost provides true) {
                PinHtmlContent(pin.contentHtml)
            }
        }

        val votingPoll = pin.bottomPoll?.voting
        if (votingPoll != null) {
            Spacer(modifier = Modifier.height(16.dp))
            PinPollCard(
                poll = votingPoll,
                votingOptionId = pollVotingOptionId,
                errorMessage = pollErrorMessage,
                onPollVote = onPollVote,
            )
        }

        val linkCard = pin.content.firstNotNullOfOrNull { it as? DataHolder.Pin.ContentLinkCard }
        if (linkCard != null) {
            Spacer(modifier = Modifier.height(AppDimens.Space16))
            var relatedPreview by remember(linkCard.dataContentType, linkCard.dataContentId, linkCard.url) {
                mutableStateOf<PinLinkCardPreview?>(null)
            }
            var isRelatedLoading by remember(linkCard.dataContentType, linkCard.dataContentId, linkCard.url) {
                mutableStateOf(true)
            }

            LaunchedEffect(linkCard.dataContentType, linkCard.dataContentId, linkCard.url) {
                isRelatedLoading = true
                relatedPreview = fetchPinLinkCardPreview(linkCard, environment)
                isRelatedLoading = false
            }

            PinContentRelatedCard(
                linkCard = linkCard,
                relatedTitle = relatedPreview?.displayTitle,
                relatedPreview = relatedPreview?.preview,
                isRelatedLoading = isRelatedLoading,
                onClick = {
                    resolveNavigationUrl(linkCard.url)?.let(navigator.onNavigate)
                },
            )
        }

        val topics = pin.topics
        if (topics?.isNotEmpty() == true) {
            Spacer(modifier = Modifier.height(AppDimens.Space16))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                topics.forEach { topic ->
                    AppChip(
                        label = "# ${topic.name}",
                        selected = false,
                        onClick = {
                            navigator.onNavigate(
                                Topic(topic.id, topic.name),
                            )
                        },
                    )
                }
            }
        }
        if (pin.created > 0 || pin.updated > 0) {
            Column(
                modifier = Modifier.padding(top = AppDimens.Space16).testTag(PIN_SCREEN_TIMESTAMP_TAG),
                verticalArrangement = Arrangement.spacedBy(AppDimens.Space4),
            ) {
                if (pin.created > 0) {
                    Text(
                        text = ContentStrings.publishedAt(formatFullDateTime(pin.created)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (pin.updated > 0 && pin.updated != pin.created) {
                    Text(
                        text = ContentStrings.editedAt(formatFullDateTime(pin.updated)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        ContentEndMarker()
        Spacer(modifier = Modifier.height(readingPlayerOverlayPadding))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PinContentAuthorHeader(
    pin: DataHolder.Pin,
    minimumAuthorWidth: Dp,
    canFollowAuthor: Boolean,
    authorFollowChanging: Boolean,
    onFollowAuthor: () -> Unit,
    onAuthorClick: () -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppDimens.Space8),
        verticalArrangement = Arrangement.spacedBy(AppDimens.Space8),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .widthIn(min = minimumAuthorWidth)
                .weight(1f)
                .testTag(PIN_SCREEN_AUTHOR_TAG)
                .clickable {
                    onAuthorClick()
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = pin.author.avatarUrl,
                contentDescription = ContentStrings.avatar,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        pin.author.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    val authorBadge = pin.author.badgeV2.officialBadge()
                    if (authorBadge != null) {
                        Spacer(modifier = Modifier.width(4.dp))
                        AuthorBadge(authorBadge)
                    }
                }
                if (pin.author.headline.isNotEmpty()) {
                    Text(
                        pin.author.headline,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (canFollowAuthor) {
            AppTextButton(
                text = if (pin.author.isFollowing) ContentStrings.following else ContentStrings.follow,
                onClick = onFollowAuthor,
                enabled = !authorFollowChanging,
                modifier = Modifier.testTag(PIN_SCREEN_AUTHOR_FOLLOW_TAG),
            )
        }
    }
}

@Composable
private fun PinContentRelatedCard(
    linkCard: DataHolder.Pin.ContentLinkCard,
    relatedTitle: String?,
    relatedPreview: String?,
    isRelatedLoading: Boolean,
    onClick: () -> Unit,
) {
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(PIN_SCREEN_LINK_CARD_TAG)
            .clickable {
                onClick()
            },
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                ContentStrings.relatedContent,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (isRelatedLoading) {
                Text(
                    text = ContentStrings.loadingRelatedContent,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (!relatedTitle.isNullOrBlank()) {
                Text(
                    text = relatedTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!relatedPreview.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = relatedPreview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                Text(
                    text = "${linkCardTypeLabel(linkCard.dataContentType)} · ${linkCard.dataContentId}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (relatedTitle.isNullOrBlank() && linkCard.url.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = linkCard.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private fun linkCardTypeLabel(dataContentType: String): String = when (dataContentType.lowercase()) {
    "answer" -> ContentStrings.answer
    "article" -> ContentStrings.article
    "question" -> ContentStrings.question
    "pin" -> ContentStrings.pin
    "people" -> ContentStrings.user
    "video", "zvideo" -> ContentStrings.video
    else -> dataContentType
}
