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

package com.github.zly2006.zhihu

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.zly2006.zhihu.data.CommonFeed
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.Feed
import com.github.zly2006.zhihu.data.FeedDisplayItem
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.data.toFeedDisplayItemNavDestinationJson
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.test.InstrumentedTestEnvironment
import com.github.zly2006.zhihu.test.MainActivityComposeRule
import com.github.zly2006.zhihu.test.RecordingNavigator
import com.github.zly2006.zhihu.test.ZhihuMockApi
import com.github.zly2006.zhihu.test.performVerticalSwipeCycle
import com.github.zly2006.zhihu.test.resetAppPreferences
import com.github.zly2006.zhihu.test.seedViewModel
import com.github.zly2006.zhihu.test.setScreenContent
import com.github.zly2006.zhihu.ui.QUESTION_DETAIL_CONTENT_TAG
import com.github.zly2006.zhihu.ui.QUESTION_DETAIL_TOGGLE_TAG
import com.github.zly2006.zhihu.ui.QUESTION_FOLLOW_BUTTON_TAG
import com.github.zly2006.zhihu.ui.QUESTION_SCREEN_LIST_TAG
import com.github.zly2006.zhihu.ui.QUESTION_SORT_DEFAULT_TAG
import com.github.zly2006.zhihu.ui.QUESTION_SORT_UPDATED_TAG
import com.github.zly2006.zhihu.ui.QUESTION_WRITE_ANSWER_BUTTON_TAG
import com.github.zly2006.zhihu.ui.QuestionScreen
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment
import com.github.zly2006.zhihu.viewmodel.feed.QuestionFeedViewModel
import com.github.zly2006.zhihu.viewmodel.filter.BlockedUser
import com.github.zly2006.zhihu.viewmodel.filter.getContentFilterDatabase
import com.github.zly2006.zhihu.viewmodel.paginationEnvironment
import com.github.zly2006.zhihu.viewmodel.sharedArticleAnswerSwitchState
import io.ktor.http.HttpMethod
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.github.zly2006.zhihu.data.Person as FeedPerson

@RunWith(AndroidJUnit4::class)
class QuestionScreenInstrumentedTest {
    @get:Rule
    val composeRule: MainActivityComposeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() = runBlocking {
        composeRule.resetAppPreferences(forceMock = true)
        val database = getContentFilterDatabase(composeRule.activity)
        database.blockedUserDao().clearAllUsers()
    }

    @After
    fun tearDown() = runBlocking {
        val database = getContentFilterDatabase(composeRule.activity)
        database.blockedUserDao().clearAllUsers()
        if (!InstrumentedTestEnvironment.isMockMode()) composeRule.resetAppPreferences()
    }

    /**
     * 问题详情为空时仍须显示主要操作和回答排序；行为依据：https://github.com/zly2006/zhihu-plus-plus/issues/643
     */
    @Test
    fun emptyQuestionDetailKeepsPrimaryActionsAndAnswerSortVisible() {
        mockQuestionDetail(detail = "")
        seedQuestionViewModel()

        setScreen()

        composeRule.waitUntilTextExists("345 浏览")
        composeRule
            .onNodeWithTag(QUESTION_SCREEN_LIST_TAG)
            .performScrollToNode(hasTestTag(QUESTION_WRITE_ANSWER_BUTTON_TAG))
        composeRule.onNodeWithTag(QUESTION_WRITE_ANSWER_BUTTON_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(QUESTION_FOLLOW_BUTTON_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("12 回答").assertIsDisplayed()
        composeRule.onNodeWithTag(QUESTION_SORT_DEFAULT_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(QUESTION_SORT_UPDATED_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(QUESTION_DETAIL_CONTENT_TAG).assertDoesNotExist()
    }

    /**
     * 回答列表须支持滚动、分页和条目导航；行为依据：https://github.com/zly2006/zhihu-plus-plus/issues/431
     */
    @Test
    fun seededAnswerListSupportsScrollSwipesPaginationAndRowNavigationOffline() {
        val viewModel = seedQuestionViewModel(
            itemCount = 24,
            isEnd = false,
        )
        val tailItemTag = "question_feed_item_${viewModel.displayItems[17].stableKey}"
        val clickedItemTag = "question_feed_item_${viewModel.displayItems[2].stableKey}"
        mockQuestionDetail()
        val navigator = setScreen()

        composeRule.onNodeWithTag(QUESTION_SCREEN_LIST_TAG).assertIsDisplayed()
        composeRule
            .onNodeWithTag(QUESTION_SCREEN_LIST_TAG)
            .performScrollToNode(hasTestTag(tailItemTag))
        composeRule.onNodeWithTag(tailItemTag).assertIsDisplayed()
        composeRule.onNodeWithTag(QUESTION_SCREEN_LIST_TAG).performVerticalSwipeCycle()
        composeRule
            .onNodeWithTag(QUESTION_SCREEN_LIST_TAG)
            .performScrollToNode(hasTestTag(tailItemTag))
        composeRule.onNodeWithTag(tailItemTag).assertIsDisplayed()

        composeRule
            .onNodeWithTag(QUESTION_SCREEN_LIST_TAG)
            .performScrollToNode(hasTestTag(clickedItemTag))
        composeRule.onNodeWithTag(clickedItemTag).performClick()

        assertTrue("滚动接近末尾时应触发预置的加载更多路径", viewModel.loadMoreCount > 0)
        assertEquals(
            listOf(Article(type = ArticleType.Answer, id = 7003L)),
            navigator.destinations,
        )
        val pendingNavigator = sharedArticleAnswerSwitchState.pendingNavigator
        assertEquals(7002L, pendingNavigator?.previousAnswerPreview?.article?.id)
        assertEquals(7004L, runBlocking { pendingNavigator?.loadNext()?.id })
    }

    /**
     * 从回答列表滚回后，长问题详情仍须完整布局并可见；行为依据：https://github.com/zly2006/zhihu-plus-plus/issues/615
     */
    @Test
    fun longQuestionDetailRemainsVisibleAfterReturnFromAnswerList() {
        val lastDetailParagraph =
            "问题详情回归段落 36：这是一段足够长的正文，用于覆盖超过屏幕高度的问题描述滚动场景。"
        val detail = (1..36).joinToString("") { index ->
            "<p>问题详情回归段落 $index：这是一段足够长的正文，用于覆盖超过屏幕高度的问题描述滚动场景。</p>"
        }
        mockQuestionDetail(detail = detail)
        val viewModel = seedQuestionViewModel(itemCount = 24)
        val farAnswerTag = "question_feed_item_${viewModel.displayItems[12].stableKey}"
        setScreen()

        composeRule.waitUntilTextExists("345 浏览")
        composeRule
            .onNodeWithTag(QUESTION_SCREEN_LIST_TAG)
            .performScrollToNode(hasTestTag(QUESTION_DETAIL_TOGGLE_TAG))
        composeRule.onNodeWithTag(QUESTION_DETAIL_TOGGLE_TAG).performClick()
        composeRule.waitUntilTagIsDisplayed(QUESTION_DETAIL_CONTENT_TAG)
        composeRule
            .onNodeWithTag(QUESTION_SCREEN_LIST_TAG)
            .performScrollToNode(hasTestTag(farAnswerTag))
        composeRule.onNodeWithTag(farAnswerTag).assertIsDisplayed()

        val paragraph = SemanticsMatcher("完整第 36 段原文") {
            it.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { text -> text.text } == lastDetailParagraph
        }
        composeRule.onNodeWithTag(QUESTION_SCREEN_LIST_TAG).performScrollToIndex(0)
        try {
            composeRule.waitUntil("等待展开的问题头部完成第 36 段布局", timeoutMillis = 5_000) {
                composeRule.onAllNodesWithTag(QUESTION_DETAIL_CONTENT_TAG).fetchSemanticsNodes().size == 1 &&
                    composeRule.onAllNodes(paragraph).fetchSemanticsNodes().size == 1
            }
        } catch (error: ComposeTimeoutException) {
            val list = composeRule.onNodeWithTag(QUESTION_SCREEN_LIST_TAG).fetchSemanticsNode()
            val scrollRange = list.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)
            val detailBounds = composeRule
                .onAllNodesWithTag(QUESTION_DETAIL_CONTENT_TAG)
                .fetchSemanticsNodes()
                .map { it.boundsInRoot }
            throw AssertionError(
                "返回后缺少第 36 段布局：listBounds=${list.boundsInRoot} " +
                    "scroll=${scrollRange?.value?.invoke()}/${scrollRange?.maxValue?.invoke()} detailBounds=$detailBounds",
                error,
            )
        }
        composeRule.onNode(paragraph).performScrollTo()
        composeRule.onAllNodes(paragraph).assertCountEquals(1)
        val section = composeRule.onNode(paragraph).assertIsDisplayed().fetchSemanticsNode()
        val lines = composeRule
            .onAllNodes(
                SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),
                useUnmergedTree = true,
            ).fetchSemanticsNodes()
            .filter { node ->
                generateSequence(node) { it.parent }.any { it.id == section.id }
            }
        assertTrue("完整段落必须保留实际文字布局", lines.isNotEmpty())
        val lastLine = lines.maxBy { it.positionInRoot.y }
        val viewport = composeRule.onNodeWithTag(QUESTION_SCREEN_LIST_TAG).fetchSemanticsNode().boundsInRoot
        assertTrue("最后一行必须进入实际阅读视口：${lastLine.boundsInRoot} / $viewport", lastLine.boundsInRoot.height > 0 && lastLine.boundsInRoot.top >= viewport.top && lastLine.boundsInRoot.bottom <= viewport.bottom)
        composeRule.onNodeWithTag(QUESTION_DETAIL_CONTENT_TAG).assertIsDisplayed()
    }

    /**
     * 问题信息流不得显示被屏蔽用户的回答；行为依据：https://github.com/zly2006/zhihu-plus-plus/issues/357
     */
    @Test
    fun blockedUserAnswersAreRemovedFromQuestionFeedProcessing() {
        /*
         * 预期行为：
         * 1. 问题回答信息流应遵守内容过滤链路共用的用户屏蔽开关。
         * 2. 创建显示条目前，应移除被屏蔽作者的回答；未屏蔽回答仍通过现有映射显示。
         */
        val viewModel = TestableQuestionFeedViewModel(123456789L)
        runBlocking {
            val database = getContentFilterDatabase(composeRule.activity)
            database.blockedUserDao().insertUser(BlockedUser("blocked-answer-author", "被屏蔽回答作者"))
            viewModel.processForTest(
                composeRule.activity,
                listOf(
                    seedAnswerFeed(
                        id = 1L,
                        authorId = "blocked-answer-author",
                        authorName = "被屏蔽回答作者",
                        excerpt = "这条回答不应展示",
                    ),
                    seedAnswerFeed(
                        id = 2L,
                        authorId = "allowed-answer-author",
                        authorName = "可见回答作者",
                        excerpt = "这条回答应展示",
                    ),
                ),
            )
        }

        assertEquals(listOf("可见回答作者"), viewModel.displayItems.map { it.authorName })
        assertEquals(listOf("这条回答应展示"), viewModel.displayItems.map { it.summary })
    }

    private fun setScreen(): RecordingNavigator = composeRule.setScreenContent {
        QuestionScreen(
            question = Question(questionId = 123456789L, title = "离线问题标题"),
        )
    }

    private fun seedQuestionViewModel(
        itemCount: Int = 8,
        isEnd: Boolean = true,
    ): SeededQuestionFeedViewModel {
        val viewModel = composeRule.seedViewModel<SeededQuestionFeedViewModel>(key = "question_123456789") {
            SeededQuestionFeedViewModel(123456789L, isEnd)
        }
        viewModel.addDisplayItems(seededItems(itemCount))
        return viewModel
    }

    private fun mockQuestionDetail(
        questionId: Long = 123456789L,
        detail: String = "<p>离线问题详情用于 QuestionScreen instrumented test。</p>",
    ) {
        ZhihuMockApi.mockJsonPrefix(
            method = HttpMethod.Get,
            urlPrefix = "https://www.zhihu.com/api/v4/questions/$questionId?",
            body = ZhihuJson.json.encodeToString(seededQuestionDetail(questionId, detail)),
        )
    }

    private class SeededQuestionFeedViewModel(
        questionId: Long,
        private val seededIsEnd: Boolean,
    ) : QuestionFeedViewModel(questionId) {
        var refreshCount = 0
            private set
        var loadMoreCount = 0
            private set

        override val isEnd: Boolean
            get() = seededIsEnd

        override fun refresh(environment: PaginationEnvironment) {
            refreshCount += 1
        }

        override fun loadMore(environment: PaginationEnvironment) {
            loadMoreCount += 1
        }
    }

    private fun MainActivityComposeRule.waitUntilTextExists(text: String) {
        waitUntil("等待文字 $text", timeoutMillis = 5_000) {
            onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun MainActivityComposeRule.waitUntilTagIsDisplayed(tag: String) {
        waitUntil("等待标签为 $tag 的节点显示", timeoutMillis = 5_000) {
            runCatching {
                onNodeWithTag(tag).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
    }

    private fun seededQuestionDetail(
        questionId: Long,
        detail: String,
    ): DataHolder.Question = DataHolder.Question(
        type = "question",
        id = questionId,
        title = "离线问题标题",
        questionType = "normal",
        created = 1_713_456_789L,
        updatedTime = 1_713_456_999L,
        url = "https://www.zhihu.com/question/$questionId",
        answerCount = 12,
        visitCount = 345,
        commentCount = 7,
        followerCount = 89,
        detail = detail,
        relationship = DataHolder.QuestionRelationship(isFollowing = false),
        topics = emptyList(),
        author = DataHolder.Author(
            avatarUrl = "",
            gender = 0,
            headline = "离线提问者简介",
            id = "question-author-id",
            isAdvertiser = false,
            isOrg = false,
            name = "离线提问者",
            type = "people",
            url = "https://www.zhihu.com/people/question-author-token",
            urlToken = "question-author-token",
            userType = "people",
        ),
        voteupCount = 0,
    )

    private fun seededItems(count: Int): List<FeedDisplayItem> = List(count) { index ->
        val id = index + 1L
        FeedDisplayItem(
            title = "离线回答条目 $id",
            summary = "这是第 $id 条离线回答摘要。",
            details = "离线作者 $id · 赞同 ${(id * 3)}",
            feed = null,
            navDestinationJson = Article(
                type = ArticleType.Answer,
                id = 7000L + id,
            ).toFeedDisplayItemNavDestinationJson(),
            authorName = "离线作者 $id",
        )
    }

    private class TestableQuestionFeedViewModel(
        questionId: Long,
    ) : QuestionFeedViewModel(questionId) {
        suspend fun processForTest(context: android.content.Context, data: List<Feed>) {
            processResponse(paginationEnvironment(context), data, JsonArray(emptyList()))
        }
    }

    private fun seedAnswerFeed(
        id: Long,
        authorId: String,
        authorName: String,
        excerpt: String,
    ): Feed = CommonFeed(
        id = "answer-feed-$id",
        target = Feed.AnswerTarget(
            id = id,
            url = "https://www.zhihu.com/answer/$id",
            author = FeedPerson(
                id = authorId,
                url = "https://www.zhihu.com/people/$authorId",
                userType = "people",
                urlToken = "$authorId-token",
                name = authorName,
                headline = "$authorName 的离线签名",
                avatarUrl = "https://example.invalid/avatar/$authorId.png",
            ),
            voteupCount = 10,
            commentCount = 1,
            question = Feed.QuestionTarget(
                id = 123456789L,
                _title = "离线问题标题",
                url = "https://www.zhihu.com/question/123456789",
                type = "question",
            ),
            excerpt = excerpt,
        ),
    )
}
