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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.Navigator
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.navigation.Topic
import com.github.zly2006.zhihu.theme.AppSkinTokens
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.ui.components.AppChip
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.ui.components.AuthorBadge
import com.github.zly2006.zhihu.util.formatCompactCount
import com.github.zly2006.zhihu.util.formatFullDateTime
import com.github.zly2006.zhihu.viewmodel.ArticleViewModel
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment

@Composable
internal fun ArticleMainHeader(
    article: Article,
    viewModel: ArticleViewModel,
    navigator: Navigator,
    environment: PaginationEnvironment,
    skin: AppSkinTokens,
    displayTitle: String,
    showContentTitle: Boolean,
) {
    if (showContentTitle) {
        Text(
            text = displayTitle,
            style = skin.title2,
            fontWeight = FontWeight.SemiBold,
            color = skin.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = AppDimens.Space12)
                .testTag(ARTICLE_FULL_TITLE_TAG)
                .semantics { heading() }
                .then(
                    if (article.type == ArticleType.Answer && viewModel.questionId > 0) {
                        Modifier.clickable {
                            navigator.onNavigate(Question(viewModel.questionId, displayTitle))
                        }
                    } else {
                        Modifier
                    },
                ),
        )
    }
    if (viewModel.authorName.isNotBlank()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = AppDimens.Space8),
        ) {
            ArticleAuthorIdentity(
                viewModel = viewModel,
                modifier = Modifier.weight(1f),
                onClick = {
                    navigator.onNavigate(
                        com.github.zly2006.zhihu.navigation.Person(
                            id = viewModel.authorId,
                            urlToken = viewModel.authorUrlToken,
                            name = viewModel.authorName,
                        ),
                    )
                },
            )
            if (viewModel.canFollowAuthor) {
                ArticleAuthorFollowButton(
                    following = viewModel.authorIsFollowing,
                    changing = viewModel.authorFollowChanging,
                    onClick = { viewModel.toggleAuthorFollow(environment) },
                )
            }
        }
        Spacer(modifier = Modifier.height(AppDimens.Space8))
    }
    if (article.type == ArticleType.Answer) {
        viewModel.ipInfo?.takeIf { it.isNotBlank() }?.let { ipInfo ->
            Text(
                text = "IP属地：$ipInfo",
                modifier = Modifier.testTag(ARTICLE_IP_INFO_TAG),
                color = skin.onSurfaceVariantSummary,
                style = skin.footnote2,
            )
            Spacer(modifier = Modifier.height(AppDimens.Space8))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ArticleMainMetadata(
    article: Article,
    viewModel: ArticleViewModel,
    navigator: Navigator,
    environment: PaginationEnvironment,
    pinAnswerDate: Boolean,
    onShowVoters: () -> Unit,
) {
    if (article.type == ArticleType.Article && viewModel.topics.isNotEmpty()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            viewModel.topics.forEach { topic ->
                AppChip(
                    label = "# ${topic.name}",
                    selected = false,
                    onClick = { navigator.onNavigate(Topic(topic.id, topic.name)) },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
    }
    val endorsements = viewModel.endorsements
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        if (pinAnswerDate) {
            ArticleDateTexts(viewModel)
        }
        ArticleVotersSocialCredit(
            article = article,
            viewModel = viewModel,
            environment = environment,
            onShowVoters = onShowVoters,
        )
        if (endorsements.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                endorsements.forEach { endorsement ->
                    AnswerEndorsementChip(endorsement)
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
internal fun ArticleDateTexts(viewModel: ArticleViewModel) {
    val skin = LocalAppSkinTokens.current
    if (viewModel.createdAt > 0) {
        Text(
            "发布于 " + formatFullDateTime(viewModel.createdAt),
            color = skin.onSurfaceVariantSummary,
            style = skin.footnote2,
        )
    }
    if (viewModel.updatedAt > 0 && viewModel.createdAt != viewModel.updatedAt) {
        Text(
            "编辑于 " + formatFullDateTime(viewModel.updatedAt),
            color = skin.onSurfaceVariantSummary,
            style = skin.footnote2,
        )
    }
}

@Composable
private fun ArticleVotersSocialCredit(
    article: Article,
    viewModel: ArticleViewModel,
    environment: PaginationEnvironment,
    onShowVoters: () -> Unit,
) {
    val density = LocalDensity.current
    val contentLabel = when (article.type) {
        ArticleType.Answer -> "回答"
        ArticleType.Article -> "文章"
    }
    Spacer(modifier = Modifier.height(8.dp))
    Box(
        modifier = Modifier.fillMaxWidth().height(
            with(density) {
                MaterialTheme.typography.bodySmall.lineHeight
                    .toDp()
            },
        ),
    ) {
        val text = viewModel.votersSocialText.ifBlank {
            "${formatCompactCount(viewModel.votersTotal)} 人赞同了该$contentLabel"
        }
        val votersTextModifier = if (article.type == ArticleType.Answer) {
            Modifier.clickable {
                onShowVoters()
                if (viewModel.voters.isEmpty()) {
                    viewModel.loadMoreVoters(environment, reset = true)
                }
            }
        } else {
            Modifier
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = votersTextModifier,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    if (viewModel.aigcSupportVoterCount > 0) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "有 ${formatCompactCount(viewModel.aigcSupportVoterCount)} 人认为此${contentLabel}包含AIGC内容",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun ArticleAuthorIdentity(
    viewModel: ArticleViewModel,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val skin = LocalAppSkinTokens.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.clickable(
            enabled = viewModel.authorId.isNotBlank() || viewModel.authorUrlToken.isNotBlank(),
            onClick = onClick,
        ),
    ) {
        if (viewModel.authorAvatarSrc.isNotEmpty()) {
            AsyncImage(
                model = viewModel.authorAvatarSrc,
                contentDescription = "作者头像",
                modifier = Modifier
                    .size(AppDimens.Space40)
                    .clip(CircleShape),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(AppDimens.Space40)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            )
        }
        Spacer(modifier = Modifier.width(AppDimens.Space8))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = viewModel.authorName,
                    style = skin.title4,
                    color = skin.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (viewModel.authorBadge != null) {
                    Spacer(modifier = Modifier.width(4.dp))
                    AuthorBadge(
                        badge = viewModel.authorBadge,
                        compact = false,
                    )
                }
            }
            if (viewModel.authorBio.isNotEmpty()) {
                Text(
                    text = viewModel.authorBio,
                    style = skin.main,
                    color = skin.onSurfaceVariantSummary,
                )
            }
        }
    }
}

@Composable
private fun ArticleAuthorFollowButton(
    following: Boolean,
    changing: Boolean,
    onClick: () -> Unit,
) {
    val skin = LocalAppSkinTokens.current
    AppTextButton(
        onClick = onClick,
        enabled = !changing,
        contentPadding = PaddingValues(horizontal = AppDimens.Space8, vertical = AppDimens.ElevationNone),
        modifier = Modifier.testTag(ARTICLE_AUTHOR_FOLLOW_TAG),
    ) {
        Text(
            text = if (following) "已关注" else "关注",
            style = skin.footnote1,
            maxLines = 1,
        )
    }
}
