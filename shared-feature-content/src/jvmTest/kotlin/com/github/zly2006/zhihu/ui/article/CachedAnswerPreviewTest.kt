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
import com.github.zly2006.zhihu.navigation.Navigator
import com.github.zly2006.zhihu.theme.ZhihuTheme
import com.github.zly2006.zhihu.viewmodel.ArticleAnswerSwitchData
import com.github.zly2006.zhihu.viewmodel.ArticleViewModel
import com.github.zly2006.zhihu.viewmodel.ArticleViewModel.CachedAnswerContent
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class CachedAnswerPreviewTest {
    @Test
    fun cachedHandoffSeedsNewModelsWithoutOverwritingLoadedContent() = runTest {
        val client = HttpClient(MockEngine { error("缓存交接不能请求网络") })
        try {
            val model = ArticleViewModel(Article(type = ArticleType.Answer, id = 1L), client)
            val cached = model.toCachedContent().copy(content = "<p>缓存正文</p>", sourceContent = "<p>缓存原文</p>")
            val switchState = ArticleAnswerSwitchData().apply {
                navigatingFromAnswerSwitch = true
                pendingInitialContent = cached
            }
            val navigationState = ArticleAnswerNavigationState(
                switchState = switchState,
                viewModel = model,
                navigator = Navigator(onNavigate = { error("准备页面不能导航") }, onNavigateBack = { error("准备页面不能返回") }),
                navHost = null,
                coroutineScope = this,
                readingQueueSourceId = null,
                answerSwitchMode = "off",
            )
            assertFalse(model.hasLoaded)
            navigationState.prepareArticle()
            assertTrue(model.hasLoaded)
            assertEquals(cached.content, model.content)
            assertEquals(cached.sourceContent, model.sourceContent)
            assertNull(switchState.pendingInitialContent)

            model.content = "<p>已更新的正文</p>"
            switchState.navigatingFromAnswerSwitch = true
            switchState.pendingInitialContent = cached
            navigationState.prepareArticle()
            assertEquals("<p>已更新的正文</p>", model.content, "已加载正文不能被较旧的交接缓存覆盖")
            assertEquals(cached.sourceContent, model.sourceContent)
            assertNull(switchState.pendingInitialContent)
        } finally {
            client.close()
        }
    }

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
