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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.zly2006.zhihu.filter.ContentOpenEventSupport
import com.github.zly2006.zhihu.navigation.AppNavHost
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.CollectionContent
import com.github.zly2006.zhihu.navigation.CommentHolder
import com.github.zly2006.zhihu.navigation.History
import com.github.zly2006.zhihu.navigation.Home
import com.github.zly2006.zhihu.navigation.MainTabs
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.Notification
import com.github.zly2006.zhihu.navigation.OnlineHistoryRoute
import com.github.zly2006.zhihu.navigation.Pin
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.navigation.TopLevelDestination
import com.github.zly2006.zhihu.navigation.Video

/** 主壳导航协调：评论交接、内容打开来源、视频入口、回到主页签。平台差异由构造参数注入。 */
class ZhihuMainNavigationCoordinator(
    private val mainHost: () -> AppNavHost,
    private val showMessage: (String) -> Unit,
    private val openVideo: (video: Video, contentId: String, contentType: String) -> Unit,
    private val onRouteAccepted: (NavDestination) -> Unit = {},
    private val openMainTabs: (AppNavHost) -> Unit = { it.popToRoot() },
    private val prepareContentOpen: (NavDestination, String) -> Unit = ContentOpenEventSupport::prepareContentOpen,
) {
    var mainTabNavigationTarget: TopLevelDestination? by mutableStateOf(null)
        private set

    private var currentMainTabOpenFrom: String? = null

    fun navigate(
        route: NavDestination,
        targetHost: AppNavHost = mainHost(),
        popup: Boolean = false,
    ) {
        if (route is CommentHolder) {
            CommentNavigationHandoff.prepareComment(route)
            navigate(route.article, targetHost, popup)
            return
        }
        CommentNavigationHandoff.clearCommentUnless(route)
        val host = mainHost()
        val openFrom = (if (host.top == MainTabs) currentMainTabOpenFrom else null)
            ?: ContentOpenEventSupport.inferOpenFrom(
                if (targetHost != host && host.top == OnlineHistoryRoute && currentContentOpenSource(targetHost) == null) {
                    OnlineHistoryRoute
                } else {
                    currentContentOpenSource(targetHost)
                },
                route,
            )
        prepareContentOpen(route, openFrom)
        onRouteAccepted(route)
        if (route is Video) {
            val (contentId, contentType) = when (val current = targetHost.top) {
                is Article -> current.id.toString() to when (current.type) {
                    ArticleType.Answer -> "answer"
                    ArticleType.Article -> "article"
                }
                is Question -> current.questionId.toString() to "question"
                else -> {
                    showMessage("无法打开视频：未知的内容类型")
                    return
                }
            }
            openVideo(route, contentId, contentType)
            return
        }
        if (route == MainTabs) {
            mainTabNavigationTarget = Home
            openMainTabs(mainHost())
            return
        }
        if (popup && targetHost.contains(MainTabs)) {
            targetHost.popToRoot()
        }
        targetHost.open(route)
    }

    /** 宿主自绘导航（如 macOS 原生侧栏）指定要切到的主页签；传 null 取消尚未消费的请求。 */
    fun requestMainTab(destination: TopLevelDestination?) {
        mainTabNavigationTarget = destination
    }

    fun setCurrentMainTabOpenFrom(openFrom: String?) {
        currentMainTabOpenFrom = openFrom
    }

    fun consumeMainTabNavigationTarget(destination: TopLevelDestination) {
        if (mainTabNavigationTarget == destination) {
            mainTabNavigationTarget = null
        }
    }

    /** 内容打开来源取触发导航的返回栈，避免误用主栈页面。 */
    private fun currentContentOpenSource(host: AppNavHost): NavDestination? = when (val top = host.top) {
        is Article, is Question, is Pin, is CollectionContent, History, OnlineHistoryRoute, Notification -> top
        else -> null
    }
}
