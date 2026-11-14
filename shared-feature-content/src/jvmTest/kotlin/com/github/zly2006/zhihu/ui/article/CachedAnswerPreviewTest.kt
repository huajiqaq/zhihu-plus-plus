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
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.theme.ZhihuTheme
import com.github.zly2006.zhihu.viewmodel.ArticleViewModel.CachedAnswerContent
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class CachedAnswerPreviewTest {
    @Test
    fun cachedPreviewDisplaysRawSourceAndMetadataWithoutBusinessActions() = runDesktopComposeUiTest(width = 720, height = 900) {
        val completeTitle = "预览标题：保留完整缓存标题而不是截短后的版本"
        val author = "预览作者"
        val rawBody = "正文"
        val renderedPlaceholder = "渲染正文占位符"
        val cached = CachedAnswerContent(
            article = Article(type = ArticleType.Answer, id = 1L),
            title = completeTitle,
            authorName = author,
            authorBio = "简介",
            authorAvatarUrl = "",
            content = "<p>$renderedPlaceholder</p>",
            sourceContent = "<p>$rawBody</p>",
            voteUpCount = 12,
            commentCount = 3,
        )

        setContent {
            ZhihuTheme {
                CachedAnswerPreview(cached)
            }
        }

        onNodeWithTag("article_answer_cached_preview_1").assertIsDisplayed()
        onNodeWithText(completeTitle).assertIsDisplayed()
        onNodeWithText(author).assertIsDisplayed()
        onNodeWithText(rawBody).assertIsDisplayed()
        onNodeWithText("12 赞同").assertIsDisplayed()
        onNodeWithText("3 评论").assertIsDisplayed()
        onNodeWithText(renderedPlaceholder, substring = true, useUnmergedTree = true).assertDoesNotExist()
        onAllNodes(hasClickAction(), useUnmergedTree = true).assertCountEquals(0)
    }
}
