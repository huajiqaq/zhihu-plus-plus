/*
 * Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.github.zly2006.zhihu.ui

import com.github.zly2006.zhihu.navigation.CommentHolder
import com.github.zly2006.zhihu.navigation.NavDestination

object CommentNavigationHandoff {
    private var pendingComment: CommentHolder? = null

    fun prepareComment(holder: CommentHolder) {
        pendingComment = holder
    }

    fun clearCommentUnless(destination: NavDestination) {
        if (pendingComment?.article != destination) pendingComment = null
    }

    fun consumeCommentId(destination: NavDestination): String? {
        val holder = pendingComment?.takeIf { it.article == destination } ?: return null
        pendingComment = null
        return holder.commentId
    }
}
