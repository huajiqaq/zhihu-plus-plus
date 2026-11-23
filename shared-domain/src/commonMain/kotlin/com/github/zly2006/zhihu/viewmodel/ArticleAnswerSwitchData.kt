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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.github.zly2006.zhihu.navigation.AnswerNavigator
import com.github.zly2006.zhihu.ui.ArticleAnswerSwitchState
import com.github.zly2006.zhihu.ui.ArticleAnswerTransitionDirection
import com.github.zly2006.zhihu.viewmodel.ArticleViewModel.CachedAnswerContent

open class ArticleAnswerSwitchData :
    ViewModel(),
    ArticleAnswerSwitchState {
    /** 活跃的导航器：管理来源、历史记录和预取 */
    override var navigator: AnswerNavigator? by mutableStateOf(null)

    /**
     * 导航前由来源界面设置（如 CollectionContentScreen）。
     * [reset] 时会将其应用到 [navigator]。
     */
    override var pendingNavigator: AnswerNavigator? = null

    // 用于消除切换闪动：导航前设置，新页面用它初始化
    override var pendingInitialContent: CachedAnswerContent? = null

    // 标记是否从回答切换导航进入（避免被 LaunchedEffect 重置方向后误判）
    @kotlin.concurrent.Volatile
    override var navigatingFromAnswerSwitch = false

    // 由 DisposableEffect.onDispose 消费，不受 LaunchedEffect 时序影响
    override var answerSwitchDisposeInProgress = false

    // 导航动画方向
    override var answerTransitionDirection = ArticleAnswerTransitionDirection.DEFAULT

    // 沉浸式阅读模式
    override var isImmersiveMode by mutableStateOf(false)

    override fun reset() {
        navigator = pendingNavigator
        pendingNavigator = null
        pendingInitialContent = null
        navigatingFromAnswerSwitch = false
        isImmersiveMode = false
    }

    override fun promoteForNavigation(direction: ArticleAnswerTransitionDirection) = Unit
}

val sharedArticleAnswerSwitchState = ArticleAnswerSwitchData()
