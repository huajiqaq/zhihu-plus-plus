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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.officialBadge
import com.github.zly2006.zhihu.data.pinSummaryText
import com.github.zly2006.zhihu.navigation.CollectionContent
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.Person
import com.github.zly2006.zhihu.navigation.Pin
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.platform.rememberWebViewUrlOpener
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.ui.components.AppCard
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AuthorBadge
import com.github.zly2006.zhihu.util.formatCompactCount
import com.github.zly2006.zhihu.ui.components.AppIcon as Icon

@Composable
internal fun CollectionListItem(
    collection: DataHolder.Collection,
    itemTag: String? = null,
) {
    val navigator = LocalNavigator.current
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.Space16, vertical = AppDimens.Space4)
            .then(if (itemTag != null) Modifier.testTag(itemTag) else Modifier),
        onClick = {
            navigator.onNavigate(CollectionContent(collection.id))
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDimens.Space16),
        ) {
            Text(
                text = collection.title,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "${collection.answerCount} 内容 · ${collection.followerCount} 关注",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = AppDimens.Space4),
            )
        }
    }
}

@Composable
internal fun QuestionListItem(
    question: DataHolder.Question,
    itemTag: String? = null,
) {
    val navigator = LocalNavigator.current
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.Space16, vertical = AppDimens.Space4)
            .then(if (itemTag != null) Modifier.testTag(itemTag) else Modifier),
        onClick = {
            navigator.onNavigate(Question(question.id, question.title))
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDimens.Space16),
        ) {
            Text(
                text = question.title,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "${question.answerCount} 回答 · ${question.followerCount} 关注",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = AppDimens.Space4),
            )
        }
    }
}

@Composable
internal fun PinListItem(
    pin: DataHolder.Pin,
    itemTag: String? = null,
    readingQueueSourceId: String? = null,
) {
    val navigator = LocalNavigator.current
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.Space16, vertical = AppDimens.Space4)
            .then(if (itemTag != null) Modifier.testTag(itemTag) else Modifier),
        onClick = {
            navigator.onNavigate(
                Pin(
                    id = pin.id.toLong(),
                    authorName = pin.author.name,
                    readingQueueSourceId = readingQueueSourceId,
                ),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDimens.Space16),
        ) {
            val text = remember(pin.excerptTitle) { pin.excerptTitle.pinSummaryText() }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${pin.likeCount} 赞 · ${pin.commentCount} 评论",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = AppDimens.Space4),
            )
        }
    }
}

@Composable
internal fun ColumnListItem(
    column: DataHolder.Column,
    itemTag: String? = null,
) {
    val openWebUrl = rememberWebViewUrlOpener()
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.Space16, vertical = AppDimens.Space4)
            .then(if (itemTag != null) Modifier.testTag(itemTag) else Modifier),
        onClick = {
            openWebUrl(column.webUrl())
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDimens.Space16),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = column.title,
                    style = MaterialTheme.typography.titleMedium,
                )
                if (column.description.isNotEmpty()) {
                    Text(
                        text = column.description,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = AppDimens.Space4),
                    )
                }
                val counts = listOfNotNull(
                    column.articlesCount?.let { "$it 文章" },
                    listOfNotNull(column.followerCount, column.followers).maxOrNull()?.let { "$it 关注" },
                )
                if (counts.isNotEmpty()) {
                    Text(
                        text = counts.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = AppDimens.Space4),
                    )
                }
            }
        }
    }
}

@Composable
internal fun FollowedQuestionListItem(question: FollowedQuestion) {
    val navigator = LocalNavigator.current
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.Space16, vertical = AppDimens.Space4)
            .testTag("people_screen_followed_question_item_${question.id}"),
        onClick = {
            question.id.toLongOrNull()?.let {
                navigator.onNavigate(Question(it, question.title))
            }
        },
    ) {
        Text(
            text = question.title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(AppDimens.Space16),
        )
    }
}

@Composable
internal fun FollowedTopicListItem(topic: FollowedTopic) {
    val navigator = LocalNavigator.current
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.Space16, vertical = AppDimens.Space4)
            .testTag("people_screen_followed_topic_item_${topic.displayId}"),
        onClick = {
            navigator.onNavigate(
                com.github.zly2006.zhihu.navigation
                    .Topic(topic.displayId, topic.displayName),
            )
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDimens.Space16),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = topic.displayAvatarUrl,
                contentDescription = "话题头像",
                modifier = Modifier
                    .padding(end = AppDimens.Space12)
                    .size(AppDimens.ProfileActionSize)
                    .clip(CircleShape),
            )
            Text(
                text = topic.displayName,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun DataHolder.Column.webUrl(): String = when {
    url.contains("/api/v4/columns/") ->
        url
            .replace("http://", "https://")
            .replace("/api/v4/columns/", "/column/")

    url.startsWith("http") && !url.contains("/api/") -> url.replace("http://", "https://")
    else -> "https://www.zhihu.com/column/$id"
}

@Composable
internal fun PeopleListItem(
    people: DataHolder.People,
    itemTag: String? = null,
    actionTag: String? = null,
) {
    val navigator = LocalNavigator.current
    val skin = LocalAppSkinTokens.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.PageHorizontalPadding, vertical = AppDimens.Space4)
            .then(itemTag?.let { Modifier.testTag(it) } ?: Modifier),
    ) {
        AppCard(
            modifier = Modifier
                .fillMaxWidth()
                .then(actionTag?.let { Modifier.testTag(it) } ?: Modifier),
            onClick = {
                navigator.onNavigate(Person(id = people.id, name = people.name, urlToken = people.urlToken.orEmpty()))
            },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(AppDimens.Space16),
                horizontalArrangement = Arrangement.spacedBy(AppDimens.Space12),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = people.avatarUrl,
                    contentDescription = "用户头像",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(AppDimens.ProfileEditActionSize)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(AppDimens.Space4),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = people.name,
                            style = skin.title4,
                            color = skin.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        val officialBadge = people.badgeV2.officialBadge()
                        if (officialBadge?.isUsefulInList == true) {
                            AuthorBadge(badge = officialBadge, compact = true, modifier = Modifier.padding(start = AppDimens.Space6))
                        }
                    }
                    if (people.headline.isNotBlank()) {
                        Text(
                            text = people.headline,
                            style = skin.footnote2,
                            color = skin.onSurfaceVariantSummary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = "${formatCompactCount(people.answerCount)} 回答 · ${formatCompactCount(people.articlesCount)} 文章 · ${formatCompactCount(people.followerCount)} 粉丝",
                        style = skin.footnote2,
                        color = skin.onSurfaceVariantSummary,
                    )
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = skin.onSurfaceVariantActions)
            }
        }
    }
}
