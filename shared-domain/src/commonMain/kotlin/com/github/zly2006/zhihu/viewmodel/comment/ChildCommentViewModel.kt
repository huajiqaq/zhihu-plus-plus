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

package com.github.zly2006.zhihu.viewmodel.comment

import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.navigation.CommentHolder
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.viewmodel.CommentItem
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment
import kotlinx.serialization.json.JsonArray

/** 子评论弹层自行持有生命周期，不能交给 viewModel() 保存。 */
class ChildCommentViewModel(
    content: NavDestination,
    private val initialComment: DataHolder.Comment? = null,
) : BaseCommentViewModel(content) {
    private var initialCommentLoaded = false

    override val initialUrl: String = when (content) {
        is CommentHolder -> "https://www.zhihu.com/api/v4/comment_v5/comment/${content.commentId}/child_comment"

        else -> ""
    }

    override suspend fun fetchFeeds(environment: PaginationEnvironment) {
        if (!initialCommentLoaded && initialComment != null) {
            initialCommentLoaded = true
            commitPage(
                environment = environment,
                data = listOf(initialComment),
                rawData = JsonArray(emptyList()),
            )
        }
        super.fetchFeeds(environment)
    }

    override fun createCommentItem(comment: DataHolder.Comment, article: NavDestination): CommentItem {
        val commentItem = CommentItem(comment, null)
        return commentItem
    }
}
