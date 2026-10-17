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

package com.github.zly2006.zhihu.ui.article

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.runComposeUiTest
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.theme.ZhihuTheme
import com.github.zly2006.zhihu.viewmodel.ArticleViewModel
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Regression for https://github.com/zly2006/zhihu-plus-plus/issues/237：
 * duo3 新 UI 开启时，回答切换预览应使用药丸式操作区，而不是旧蓝色投票按钮。
 */
@OptIn(ExperimentalTestApi::class)
class CachedAnswerPreviewTest {
    private val cached = ArticleViewModel.CachedAnswerContent(
        article = Article(type = ArticleType.Answer, id = 1L),
        title = "预览标题",
        authorName = "预览作者",
        authorBio = "",
        authorAvatarUrl = "",
        content = "",
        voteUpCount = 12,
        commentCount = 3,
    )

    @Test
    fun duo3ActionsUsePillLayout() = runComposeUiTest {
        setContent {
            ZhihuTheme {
                CachedAnswerPreview(cached = cached, useTiqianMarkdown = false, duo3Actions = true)
            }
        }

        assertTrue(onAllNodesWithTag(ANSWER_PREVIEW_DUO3_ACTIONS_TAG).fetchSemanticsNodes().isNotEmpty())
        assertTrue(onAllNodesWithTag(ANSWER_PREVIEW_LEGACY_ACTIONS_TAG).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun legacyActionsRemainWhenDuo3Disabled() = runComposeUiTest {
        setContent {
            ZhihuTheme {
                CachedAnswerPreview(cached = cached, useTiqianMarkdown = false, duo3Actions = false)
            }
        }

        assertTrue(onAllNodesWithTag(ANSWER_PREVIEW_LEGACY_ACTIONS_TAG).fetchSemanticsNodes().isNotEmpty())
        assertTrue(onAllNodesWithTag(ANSWER_PREVIEW_DUO3_ACTIONS_TAG).fetchSemanticsNodes().isEmpty())
    }
}
