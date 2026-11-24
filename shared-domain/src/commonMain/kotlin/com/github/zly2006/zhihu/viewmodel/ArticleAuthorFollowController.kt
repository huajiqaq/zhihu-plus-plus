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

package com.github.zly2006.zhihu.viewmodel

import com.github.zly2006.zhihu.platform.UserMessageSink
import com.github.zly2006.zhihu.util.Log
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.reflect.KMutableProperty0

/** 文章页的关注作者开关：提交期间不接受重复提交，请求返回时作者已换则不回写关系。 */
internal class ArticleAuthorFollowController(
    private val httpClient: HttpClient?,
    private val userMessages: UserMessageSink,
    private val scope: () -> CoroutineScope,
    private val canFollowAuthor: () -> Boolean,
    private val authorUrlTokenValue: () -> String,
    authorIsFollowingState: KMutableProperty0<Boolean>,
    authorFollowChangingState: KMutableProperty0<Boolean>,
) {
    private val authorUrlToken: String
        get() = authorUrlTokenValue()
    private var authorIsFollowing by authorIsFollowingState
    private var authorFollowChanging by authorFollowChangingState

    fun toggleAuthorFollow(environment: ZhihuApiEnvironment) {
        val owner = httpClient ?: return
        val token = authorUrlToken
        if (!canFollowAuthor() || authorFollowChanging) return
        val following = !authorIsFollowing
        authorFollowChanging = true
        scope().launch {
            try {
                environment.setAuthorFollowing(owner, token, following)
                if (authorUrlToken == token) authorIsFollowing = following
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e("ArticleViewModel", "Toggle author follow failed", error)
                userMessages.showShortMessage(if (following) "关注失败，请重试" else "取消关注失败，请重试")
            } finally {
                authorFollowChanging = false
            }
        }
    }
}
