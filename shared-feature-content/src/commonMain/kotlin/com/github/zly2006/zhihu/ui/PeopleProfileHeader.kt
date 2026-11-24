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

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.OfficialBadge
import com.github.zly2006.zhihu.data.officialBadge
import com.github.zly2006.zhihu.data.officialBadgeDetails
import com.github.zly2006.zhihu.platform.rememberImagePreviewOpener
import com.github.zly2006.zhihu.platform.rememberWebViewUrlOpener
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.ui.components.AppAlertDialog
import com.github.zly2006.zhihu.ui.components.AppButton
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppIconButton
import com.github.zly2006.zhihu.ui.components.AppOutlinedButton
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.ui.components.AuthorBadge
import com.github.zly2006.zhihu.ui.components.settingSkinIsMiuix
import com.github.zly2006.zhihu.util.formatCompactCount
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import zhihu.uicomponents.generated.resources.Res
import zhihu.uicomponents.generated.resources.ic_zh_plus_author_badge
import com.github.zly2006.zhihu.ui.components.AppIcon as Icon

@Composable
private fun StatItem(
    label: String,
    value: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    tag: String? = null,
) {
    val skin = LocalAppSkinTokens.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppDimens.Space4),
        modifier = modifier
            .then(tag?.let { Modifier.testTag(it) } ?: Modifier)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "$value $label" }
            .padding(horizontal = AppDimens.Space4, vertical = AppDimens.Space6),
    ) {
        Text(text = formatCompactCount(value), style = skin.title2, color = skin.onSurface, fontWeight = FontWeight.SemiBold)
        Text(text = label, style = skin.footnote2, color = skin.onSurfaceVariantSummary)
    }
}

@Composable
private fun OfficialBadgeDetails(
    badges: List<OfficialBadge>,
    modifier: Modifier = Modifier,
) {
    if (badges.isEmpty()) return
    val primaryBadge = badges.first()
    val skin = LocalAppSkinTokens.current
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (primaryBadge.iconUrl.isNotBlank()) {
                if (primaryBadge.iconUrl == DataHolder.ZH_PLUS_AUTHOR_BADGE_ICON) {
                    Image(
                        painter = painterResource(Res.drawable.ic_zh_plus_author_badge),
                        contentDescription = primaryBadge.description,
                        modifier = Modifier
                            .padding(end = AppDimens.Space6)
                            .size(AppDimens.IconSizeCompact),
                    )
                } else {
                    AsyncImage(
                        model = primaryBadge.iconUrl,
                        contentDescription = primaryBadge.description,
                        modifier = Modifier
                            .padding(end = AppDimens.Space6)
                            .size(AppDimens.IconSizeCompact),
                    )
                }
            }
            Text(
                text = "${primaryBadge.peopleDetailTitle}: ${primaryBadge.description}",
                style = skin.footnote2,
                color = skin.onSurfaceVariantSummary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (badges.size > 1) {
                Spacer(Modifier.width(AppDimens.Space6))
                Text(
                    text = "+${badges.size - 1}",
                    style = skin.footnote2,
                    color = if (settingSkinIsMiuix()) skin.onSurfaceVariantSummary else skin.onSurfaceVariantActions,
                )
            }
        }
    }
}

private val OfficialBadge.peopleDetailTitle: String
    get() = when {
        title == "认证" || title == "已认证的个人" -> "认证信息"
        else -> title
    }

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun UserInfoHeader(
    viewModel: PersonViewModel,
    pagerState: PagerState,
    onSearch: (() -> Unit)?,
    showSearch: Boolean,
    modifier: Modifier = Modifier,
    onFollowToggle: () -> Unit,
) {
    val skin = LocalAppSkinTokens.current
    val coroutineScope = rememberCoroutineScope()
    val openImagePreview = rememberImagePreviewOpener()
    val openWebUrl = rememberWebViewUrlOpener()
    var showFullProfile by rememberSaveable(viewModel.person.userTokenOrId) { mutableStateOf(false) }
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val minimumNameWidth = with(density) {
        textMeasurer
            .measure(
                viewModel.name.take(4),
                style = skin.headline2.copy(fontWeight = FontWeight.SemiBold),
                softWrap = false,
            ).size.width
            .toDp()
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = AppDimens.Space8, bottom = AppDimens.Space16),
        verticalArrangement = Arrangement.spacedBy(AppDimens.Space16),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.Space16),
            verticalAlignment = Alignment.Top,
        ) {
            FlowRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(AppDimens.Space16),
                verticalArrangement = Arrangement.spacedBy(AppDimens.Space12),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = viewModel.avatar,
                    contentDescription = "用户头像",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .testTag(PEOPLE_SCREEN_AVATAR_TAG)
                        .size(AppDimens.ProfileAvatarLarge)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .clickable(enabled = viewModel.avatar.isNotBlank()) {
                            openImagePreview(viewModel.avatar.substringBefore("_") + ".jpg")
                        },
                )
                Column(
                    modifier = Modifier.widthIn(min = minimumNameWidth).weight(1f),
                    verticalArrangement = Arrangement.spacedBy(AppDimens.Space8),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = viewModel.name,
                            style = skin.headline2,
                            color = skin.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f, fill = false).semantics { heading() },
                        )
                        viewModel.officialBadge?.let { badge ->
                            AuthorBadge(
                                badge = badge,
                                modifier = Modifier
                                    .padding(start = AppDimens.Space6)
                                    .testTag(PEOPLE_SCREEN_OFFICIAL_BADGE_TAG),
                            )
                        }
                    }
                    val followModifier = Modifier
                        .heightIn(min = AppDimens.TouchTarget)
                        .testTag(PEOPLE_SCREEN_FOLLOW_BUTTON_TAG)
                        .semantics {
                            selected = viewModel.isFollowing
                            role = Role.Button
                        }
                    if (viewModel.isFollowing) {
                        AppOutlinedButton(onClick = onFollowToggle, modifier = followModifier) {
                            Text("已关注")
                        }
                    } else {
                        AppButton(onClick = onFollowToggle, modifier = followModifier) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(AppDimens.IconSizeSmall))
                            Spacer(Modifier.width(AppDimens.Space4))
                            Text("关注")
                        }
                    }
                }
            }
            if (onSearch != null) {
                Box(Modifier.size(AppDimens.TouchTarget), contentAlignment = Alignment.Center) {
                    if (showSearch) {
                        AppIconButton(onClick = onSearch, modifier = Modifier.testTag(PEOPLE_SCREEN_SEARCH_BUTTON_TAG)) {
                            Icon(Icons.Default.Search, contentDescription = "搜索 TA 的创作", tint = skin.onSurfaceVariantActions)
                        }
                    }
                }
            }
        }
        if (viewModel.headline.isNotBlank() || viewModel.officialBadgeDetails.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = AppDimens.TouchTarget)
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { showFullProfile = true }
                    .testTag(PEOPLE_SCREEN_PROFILE_DETAILS_TAG)
                    .semantics { contentDescription = "查看完整资料" },
                horizontalArrangement = Arrangement.spacedBy(AppDimens.Space8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(AppDimens.Space6),
                ) {
                    if (viewModel.headline.isNotBlank()) {
                        Text(
                            text = viewModel.headline,
                            style = skin.body2,
                            color = skin.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    OfficialBadgeDetails(badges = viewModel.officialBadgeDetails)
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = skin.onSurfaceVariantActions,
                    modifier = Modifier.size(AppDimens.IconSizeCompact),
                )
            }
        }
        viewModel.githubSocial?.let { githubSocial ->
            Row(
                modifier = Modifier
                    .testTag(PEOPLE_SCREEN_GITHUB_STARS_TAG)
                    .clickable { openWebUrl(githubSocial.profileUrl) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppDimens.Space6),
            ) {
                githubSocial.iconUrl?.let { iconUrl ->
                    AsyncImage(model = iconUrl, contentDescription = null, modifier = Modifier.size(AppDimens.IconSizeSmall))
                }
                Text(
                    text = githubSocial.title,
                    modifier = Modifier.weight(1f, fill = false),
                    style = skin.body2,
                    color = skin.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "· ${githubSocial.starCount} stars",
                    style = skin.footnote1,
                    color = skin.onSurfaceVariantSummary,
                    maxLines = 1,
                )
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val statSpacingPx = with(density) { AppDimens.Space6.roundToPx() }
            val minimumStatWidthPx = with(density) {
                listOf(
                    "回答" to viewModel.answerCount,
                    "文章" to viewModel.articleCount,
                    "粉丝" to viewModel.followerCount,
                    "关注" to viewModel.followingCount,
                ).maxOf { (label, value) ->
                    maxOf(
                        textMeasurer.measure(formatCompactCount(value), style = skin.title2.copy(fontWeight = FontWeight.SemiBold), softWrap = false).size.width,
                        textMeasurer.measure(label, style = skin.footnote2, softWrap = false).size.width,
                    )
                } + AppDimens.Space4.roundToPx() * 2
            }
            val columns = when {
                constraints.maxWidth >= minimumStatWidthPx * 4 + statSpacingPx * 3 -> 4
                constraints.maxWidth >= minimumStatWidthPx * 2 + statSpacingPx -> 2
                else -> 1
            }
            // 像素预算整除，避免各项独立舍入后挤出额外一行。
            val statWidth = with(density) {
                ((constraints.maxWidth - statSpacingPx * (columns - 1)) / columns).toDp()
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppDimens.Space6),
                verticalArrangement = Arrangement.spacedBy(AppDimens.Space8),
                maxItemsInEachRow = columns,
            ) {
                StatItem("回答", viewModel.answerCount, modifier = Modifier.width(statWidth), onClick = {
                    coroutineScope.launch { pagerState.animateScrollToPage(0) }
                }, tag = PEOPLE_SCREEN_ANSWER_COUNT_TAG)
                StatItem("文章", viewModel.articleCount, modifier = Modifier.width(statWidth), onClick = {
                    coroutineScope.launch { pagerState.animateScrollToPage(1) }
                }, tag = PEOPLE_SCREEN_ARTICLE_COUNT_TAG)
                StatItem("粉丝", viewModel.followerCount, modifier = Modifier.width(statWidth), onClick = {
                    coroutineScope.launch { pagerState.animateScrollToPage(7) }
                }, tag = PEOPLE_SCREEN_FOLLOWER_COUNT_TAG)
                StatItem("关注", viewModel.followingCount, modifier = Modifier.width(statWidth), onClick = {
                    coroutineScope.launch { pagerState.animateScrollToPage(8) }
                }, tag = PEOPLE_SCREEN_FOLLOWING_COUNT_TAG)
            }
        }
    }
    if (showFullProfile) {
        AppAlertDialog(
            title = { Text(viewModel.name) },
            text = {
                Text(
                    buildString {
                        append(viewModel.headline)
                        viewModel.officialBadgeDetails.forEach { badge ->
                            if (isNotEmpty()) append("\n\n")
                            append(badge.peopleDetailTitle).append(": ").append(badge.description)
                        }
                    },
                )
            },
            confirmButton = {
                AppTextButton("关闭", { showFullProfile = false })
            },
            onDismissRequest = { showFullProfile = false },
        )
    }
}
