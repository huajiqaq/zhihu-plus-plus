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

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.Feed
import com.github.zly2006.zhihu.data.FeedDisplayItem
import com.github.zly2006.zhihu.data.target
import com.github.zly2006.zhihu.navigation.Pin
import com.github.zly2006.zhihu.platform.UserMessageSink
import com.github.zly2006.zhihu.ui.components.AppDropdownMenuItem
import com.github.zly2006.zhihu.ui.components.FeedAuthorBlockRequest
import com.github.zly2006.zhihu.ui.components.FeedAuthorBlockType
import com.github.zly2006.zhihu.ui.components.feedKeywordExtractionAvailable
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment
import com.github.zly2006.zhihu.viewmodel.feed.BaseFeedViewModel

/** 主页信息流卡片「更多」菜单里的屏蔽项：按关键词、屏蔽用户、屏蔽提问者、屏蔽话题。 */
@Composable
internal fun HomeFeedCardMenuItems(
    item: FeedDisplayItem,
    viewModel: BaseFeedViewModel,
    paginationEnvironment: PaginationEnvironment,
    userMessages: UserMessageSink,
    dismissMenu: () -> Unit,
    onBlockByKeywords: (title: String, excerpt: String?) -> Unit,
    onAuthorBlockRequest: (FeedAuthorBlockRequest) -> Unit,
) {
    if (feedKeywordExtractionAvailable) {
        AppDropdownMenuItem(
            text = { Text("按关键词屏蔽") },
            onClick = {
                dismissMenu()
                viewModel.handleBlockByKeywords(paginationEnvironment, userMessages, item) { (_, contentInfo) ->
                    onBlockByKeywords(contentInfo.first, contentInfo.second)
                }
            },
        )
    }
    AppDropdownMenuItem(
        text = { Text("屏蔽用户") },
        onClick = {
            dismissMenu()
            viewModel.handleBlockUser(paginationEnvironment, userMessages, item) { authorInfo ->
                val request = FeedAuthorBlockRequest(
                    type = FeedAuthorBlockType.CONTENT_AUTHOR,
                    userId = authorInfo.first,
                    userName = authorInfo.second,
                )
                onAuthorBlockRequest(request)
            }
        },
    )
    val canBlockQuestionAuthor = when (item.feed?.target) {
        is Feed.AnswerTarget, is Feed.QuestionTarget -> true
        else -> item.raw is DataHolder.Answer || item.raw is DataHolder.Question
    }
    if (canBlockQuestionAuthor) {
        AppDropdownMenuItem(
            text = { Text("屏蔽提问者") },
            onClick = {
                dismissMenu()
                viewModel.handleBlockQuestionAuthor(paginationEnvironment, userMessages, item) { authorInfo ->
                    val request = FeedAuthorBlockRequest(
                        type = FeedAuthorBlockType.QUESTION_AUTHOR,
                        userId = authorInfo.first,
                        userName = authorInfo.second,
                    )
                    onAuthorBlockRequest(request)
                }
            },
        )
    }
    val topics = when (val raw = item.raw) {
        is DataHolder.Answer -> raw.question.topics
        is DataHolder.Question -> raw.topics
        is DataHolder.Article -> raw.topics ?: emptyList()
        is DataHolder.Pin -> raw.topics ?: emptyList()
        else -> emptyList()
    }
    topics.forEach { topic ->
        AppDropdownMenuItem(
            text = { Text("屏蔽「${topic.name}」") },
            onClick = {
                dismissMenu()
                viewModel.handleBlockTopic(userMessages, topic.id, topic.name)
            },
        )
    }
}
