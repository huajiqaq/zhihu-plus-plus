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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.AnnotatedString
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.zly2006.zhihu.data.CommentSortOrder
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.CommentHolder
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.Navigator
import com.github.zly2006.zhihu.navigation.Pin
import com.github.zly2006.zhihu.test.MainActivityComposeRule
import com.github.zly2006.zhihu.test.RecordingNavigator
import com.github.zly2006.zhihu.test.ZhihuMockApi
import com.github.zly2006.zhihu.test.instrumentedTestEnvironment
import com.github.zly2006.zhihu.test.mockCommentDetail
import com.github.zly2006.zhihu.test.mockRootComments
import com.github.zly2006.zhihu.test.resetAppPreferences
import com.github.zly2006.zhihu.test.seedViewModel
import com.github.zly2006.zhihu.test.setScreenContent
import com.github.zly2006.zhihu.ui.COMMENT_CLOSE_BUTTON_TAG
import com.github.zly2006.zhihu.ui.COMMENT_COMPOSER_ENTRY_TAG
import com.github.zly2006.zhihu.ui.COMMENT_COMPOSER_SHEET_TAG
import com.github.zly2006.zhihu.ui.COMMENT_EMOJI_BUTTON_TAG
import com.github.zly2006.zhihu.ui.COMMENT_EMOJI_ITEM_TAG_PREFIX
import com.github.zly2006.zhihu.ui.COMMENT_EMOJI_PICKER_TAG
import com.github.zly2006.zhihu.ui.COMMENT_HEADER_TAG
import com.github.zly2006.zhihu.ui.COMMENT_INPUT_TAG
import com.github.zly2006.zhihu.ui.COMMENT_LOADED_COUNT_TAG
import com.github.zly2006.zhihu.ui.COMMENT_SCREEN_LIST_TAG
import com.github.zly2006.zhihu.ui.COMMENT_SEND_BUTTON_TAG
import com.github.zly2006.zhihu.ui.COMMENT_SORT_BUTTON_TAG
import com.github.zly2006.zhihu.ui.COMMENT_SORT_SCORE_TAG
import com.github.zly2006.zhihu.ui.COMMENT_SORT_TIME_TAG
import com.github.zly2006.zhihu.ui.CommentNavigationHandoff
import com.github.zly2006.zhihu.ui.CommentScreen
import com.github.zly2006.zhihu.ui.components.CommentScreenComponent
import com.github.zly2006.zhihu.viewmodel.CommentItem
import com.github.zly2006.zhihu.viewmodel.comment.RootCommentViewModel
import com.github.zly2006.zhihu.viewmodel.filter.getContentFilterDatabase
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CommentScreenInstrumentedTest {
    @get:Rule
    val composeRule: MainActivityComposeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() = runBlocking {
        composeRule.resetAppPreferences(forceMock = true)
        val database = getContentFilterDatabase(composeRule.activity)
        database.blockedUserDao().clearAllUsers()
        ZhihuMockApi.mockJsonPrefix(
            method = HttpMethod.Post,
            urlPrefix = "https://www.zhihu.com/api/v4/comments/",
            body = "{}",
        )
        ZhihuMockApi.mockJsonPrefix(
            method = HttpMethod.Delete,
            urlPrefix = "https://www.zhihu.com/api/v4/comments/",
            body = "{}",
        )
    }

    @Test
    fun articleHostPendingCommentOpensChildListAndTargetsNestedComment() {
        mockCommentDetail(
            commentId = "liked-child-comment",
            resourceType = "answer",
            replyRootCommentId = "liked-root-comment",
        )
        mockCommentDetail(
            commentId = "liked-root-comment",
            resourceType = "answer",
        )
        mockRootComments(
            urlPrefix = "https://www.zhihu.com/api/v4/comment_v5/answers/9001/root_comment",
            commentId = "liked-root-comment",
        )
        mockRootComments(
            urlPrefix = "https://www.zhihu.com/api/v4/comment_v5/comment/liked-root-comment/child_comment",
            commentId = "other-child-comment",
        )
        CommentNavigationHandoff.prepareComment(CommentHolder("liked-child-comment", ROOT_ARTICLE))

        composeRule.setScreenContent {
            CommentScreenComponent(
                showComments = false,
                onDismiss = {},
                content = ROOT_ARTICLE,
            )
        }

        composeRule.waitUntil("等待待处理的子评论目标打开两层评论面板", timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(COMMENT_SCREEN_LIST_TAG).fetchSemanticsNodes().size == 2
        }
        composeRule.onAllNodesWithTag(COMMENT_SCREEN_LIST_TAG).assertCountEquals(2)
        composeRule.onNodeWithTag("comment_row_liked-child-comment").assertIsDisplayed()
        composeRule.waitUntil("等待目标评论、根评论详情及根评论子列表请求", timeoutMillis = 5_000) {
            ZhihuMockApi.requestCount(HttpMethod.Get, "comment/liked-child-comment") == 1 &&
                ZhihuMockApi.requestCount(HttpMethod.Get, "comment/liked-root-comment") == 2 &&
                ZhihuMockApi.requestCount(HttpMethod.Get, "comment/liked-root-comment/child_comment") == 1
        }
        assertEquals(0, ZhihuMockApi.requestCount(HttpMethod.Get, "comment/liked-child-comment/child_comment"))
    }

    @After
    fun tearDown() = runBlocking {
        val database = getContentFilterDatabase(composeRule.activity)
        database.blockedUserDao().clearAllUsers()
        if (!composeRule.activity.instrumentedTestEnvironment.isMockMode()) composeRule.resetAppPreferences()
    }

    @Test
    fun emojiPickerInsertsPlaceholderAtCursor() {
        mockRootComments(
            urlPrefix = "https://www.zhihu.com/api/v4/comment_v5/answers/9001/root_comment",
            comments = seedRootComments(count = 1),
        )
        val commentsVisible = mutableStateOf(true)
        var dismissCalls = 0
        composeRule.setScreenContent {
            CommentScreenComponent(
                showComments = commentsVisible.value,
                onDismiss = {
                    commentsVisible.value = false
                    dismissCalls++
                },
                content = ROOT_ARTICLE,
            )
        }
        composeRule.onAllNodes(isDialog()).assertCountEquals(1)

        composeRule.onNodeWithTag(COMMENT_INPUT_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(COMMENT_COMPOSER_ENTRY_TAG).assertIsDisplayed().performClick()
        composeRule.onNodeWithTag(COMMENT_COMPOSER_SHEET_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(COMMENT_INPUT_TAG).performTextInput("已有草稿")
        composeRule
            .onNodeWithTag(COMMENT_INPUT_TAG)
            .performSemanticsAction(SemanticsActions.SetSelection) { setSelection ->
                setSelection(0, 0, false)
            }
        composeRule.onNodeWithTag(COMMENT_EMOJI_BUTTON_TAG).performClick()
        composeRule.onNodeWithTag(COMMENT_EMOJI_PICKER_TAG).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("切换到键盘").assertIsDisplayed()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(COMMENT_EMOJI_ITEM_TAG_PREFIX + "[惊喜]").fetchSemanticsNodes().size == 1
        }
        composeRule.onNodeWithTag(COMMENT_EMOJI_ITEM_TAG_PREFIX + "[惊喜]").performClick()
        composeRule.onNodeWithTag(COMMENT_INPUT_TAG).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.InputText, AnnotatedString("[惊喜]已有草稿")),
        )
        composeRule.onNodeWithTag(COMMENT_INPUT_TAG).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("\u3000已有草稿")),
        )
        val picker = composeRule.onNodeWithTag(COMMENT_EMOJI_PICKER_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(COMMENT_INPUT_TAG).assertIsNotFocused()
        composeRule.onNodeWithTag(COMMENT_EMOJI_BUTTON_TAG).performClick()
        composeRule.waitUntil("等待切换按钮收起表情面板", 5_000) {
            composeRule.onAllNodesWithTag(COMMENT_EMOJI_PICKER_TAG).fetchSemanticsNodes().isEmpty()
        }
        picker.assertDoesNotExist()
        composeRule.onAllNodesWithTag(COMMENT_SCREEN_LIST_TAG).assertCountEquals(1)
        composeRule.onNodeWithTag(COMMENT_COMPOSER_SHEET_TAG).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("选择表情").assertIsDisplayed()
        composeRule.onNodeWithTag(COMMENT_INPUT_TAG).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.InputText, AnnotatedString("[惊喜]已有草稿")),
        )
        assertEquals(0, dismissCalls)

        val submitUrl = "https://www.zhihu.com/api/v4/comment_v5/answers/9001/comment"
        ZhihuMockApi.mockJson(
            method = HttpMethod.Post,
            url = submitUrl,
            body = "{}",
            status = HttpStatusCode.BadRequest,
        )
        composeRule.onNodeWithTag(COMMENT_SEND_BUTTON_TAG).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            ZhihuMockApi.requestCount(HttpMethod.Post, submitUrl) == 1 &&
                runCatching { composeRule.onNodeWithTag(COMMENT_SEND_BUTTON_TAG).assertIsEnabled() }.isSuccess
        }
        composeRule.onNodeWithTag(COMMENT_SEND_BUTTON_TAG).assertIsEnabled()
        composeRule.onNodeWithTag(COMMENT_INPUT_TAG).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.InputText, AnnotatedString("[惊喜]已有草稿")),
        )
        composeRule.onNodeWithTag(COMMENT_INPUT_TAG).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("\u3000已有草稿")),
        )
        val submittedComment = seedRootComment(index = 51).copy(content = "<p>[惊喜]已有草稿</p>")
        ZhihuMockApi.mockJson(
            method = HttpMethod.Post,
            url = submitUrl,
            body = ZhihuJson.json.encodeToString(submittedComment),
        )
        composeRule.onNodeWithTag(COMMENT_SEND_BUTTON_TAG).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            ZhihuMockApi.requestCount(HttpMethod.Post, submitUrl) == 2 &&
                composeRule.onAllNodesWithTag(COMMENT_COMPOSER_SHEET_TAG).fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag("comment_row_root-51").assertIsDisplayed()
        composeRule.onNodeWithTag(COMMENT_COMPOSER_ENTRY_TAG).performClick()
        composeRule.onNodeWithTag(COMMENT_INPUT_TAG).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.InputText, AnnotatedString("")),
        )
    }

    @Test
    fun childCommentTargetAndScrollSurviveExternalNavigation() {
        val rootComment = seedRootComment(index = 99, childCommentCount = 24)
        val childComments = seedChildComments(count = 24)
        ZhihuMockApi.mockJsonPrefix(
            method = HttpMethod.Get,
            urlPrefix = "https://www.zhihu.com/api/v4/comment_v5/answers/9001/root_comment",
            body =
                """
                {
                  "counts": {"segment_comment_counts": 0},
                  "data": ${ZhihuJson.json.encodeToString(listOf(rootComment))},
                  "paging": {"is_end": true, "is_start": true, "totals": 1, "next": ""}
                }
                """.trimIndent(),
        )
        ZhihuMockApi.mockJsonPrefix(
            method = HttpMethod.Get,
            urlPrefix = "https://www.zhihu.com/api/v4/comment_v5/comment/root-99/child_comment",
            body =
                """
                {
                  "data": ${ZhihuJson.json.encodeToString(childComments)},
                  "paging": {"is_end": true, "is_start": true, "totals": 24, "next": ""}
                }
                """.trimIndent(),
        )

        val showCommentHost = mutableStateOf(true)
        val commentsVisible = mutableStateOf(true)
        var rootDismissCalls = 0
        composeRule.setScreenContent {
            val stateHolder = rememberSaveableStateHolder()
            val navigator = remember {
                Navigator(
                    onNavigate = { showCommentHost.value = false },
                    onNavigateBack = {},
                )
            }
            Box(Modifier.fillMaxSize()) {
                if (showCommentHost.value) {
                    stateHolder.SaveableStateProvider("comment-host") {
                        CompositionLocalProvider(LocalNavigator provides navigator) {
                            CommentScreenComponent(
                                showComments = commentsVisible.value,
                                onDismiss = {
                                    commentsVisible.value = false
                                    rootDismissCalls++
                                },
                                content = ROOT_ARTICLE,
                            )
                        }
                    }
                } else {
                    Text(
                        text = "外部页面",
                        modifier = Modifier.testTag("external_page"),
                    )
                }
            }
        }

        val rootPane = SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "评论")
        val childPane = SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "回复")
        val rootList = composeRule.onNode(hasTestTag(COMMENT_SCREEN_LIST_TAG) and hasAnyAncestor(rootPane))
        val childList = composeRule.onNode(hasTestTag(COMMENT_SCREEN_LIST_TAG) and hasAnyAncestor(childPane))
        val rootEntry = composeRule.onNode(hasTestTag(COMMENT_COMPOSER_ENTRY_TAG) and hasAnyAncestor(rootPane))
        val childEntry = composeRule.onNode(hasTestTag(COMMENT_COMPOSER_ENTRY_TAG) and hasAnyAncestor(childPane))
        val input = composeRule.onNodeWithTag(COMMENT_INPUT_TAG)
        val closeEditor = composeRule.onNodeWithContentDescription("关闭编辑面板")

        fun closeEditorAndChildComments() {
            closeEditor.performClick()
            composeRule.waitUntil("等待异步关闭的评论编辑窗口离开", timeoutMillis = 5_000) {
                composeRule.onAllNodesWithTag(COMMENT_COMPOSER_SHEET_TAG).fetchSemanticsNodes().isEmpty()
            }
            composeRule.onNode(hasTestTag(COMMENT_CLOSE_BUTTON_TAG) and hasAnyAncestor(childPane)).assertIsDisplayed().performClick()
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("comment_child_button_root-99").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodes(isDialog()).assertCountEquals(1)
        composeRule.onNodeWithTag("comment_tab_sentence").assertDoesNotExist()
        composeRule.onNodeWithTag(COMMENT_SORT_BUTTON_TAG).assertIsDisplayed()
        rootList.assert(hasAnyAncestor(isDialog())).assertIsDisplayed()
        rootEntry.performClick()
        input.performTextInput("根评论草稿")
        input.assertTextEquals("根评论草稿")
        closeEditor.performClick()
        composeRule.onNodeWithTag("comment_child_button_root-99").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(COMMENT_SCREEN_LIST_TAG).fetchSemanticsNodes().size == 2
        }
        composeRule.onAllNodes(isDialog()).assertCountEquals(2)
        rootList.assert(hasAnyAncestor(isDialog()))
        childList.assert(hasAnyAncestor(isDialog())).assertIsDisplayed()
        composeRule.onNode(hasTestTag(COMMENT_HEADER_TAG) and hasAnyAncestor(childPane)).assertTextEquals("回复")
        childEntry.performClick()
        input.performTextInput("回复草稿")
        input.assertTextEquals("回复草稿")
        closeEditor.performClick()
        childList.performScrollToNode(hasTestTag("comment_row_child-20"))
        composeRule.onNodeWithTag("comment_row_child-20").assertIsDisplayed()
        composeRule.onNodeWithTag("comment_author_child-20").performClick()
        composeRule.onNodeWithTag("external_page").assertIsDisplayed()
        composeRule.onAllNodes(isDialog()).assertCountEquals(0)
        assertEquals(0, rootDismissCalls)

        composeRule.runOnIdle { showCommentHost.value = true }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag(COMMENT_SCREEN_LIST_TAG).fetchSemanticsNodes().size == 2
        }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithTag("comment_row_child-20").assertIsDisplayed()
            }.isSuccess
        }
        composeRule.onAllNodes(isDialog()).assertCountEquals(2)
        composeRule.onNodeWithTag("comment_row_child-20").assertIsDisplayed()
        rootList.assert(hasAnyAncestor(isDialog()))
        childList.assert(hasAnyAncestor(isDialog())).assertIsDisplayed()
        childEntry.performClick()
        input.assertTextEquals("回复草稿")
        closeEditorAndChildComments()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(COMMENT_SCREEN_LIST_TAG).fetchSemanticsNodes().size == 1
        }
        composeRule.onAllNodes(isDialog()).assertCountEquals(1)
        assertEquals(0, rootDismissCalls)
        rootList.assertIsDisplayed()
        rootEntry.performClick()
        input.assertTextEquals("根评论草稿")
        closeEditor.performClick()
        composeRule.onNodeWithTag("comment_child_button_root-99").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(COMMENT_SCREEN_LIST_TAG).fetchSemanticsNodes().size == 2
        }
        composeRule.onAllNodes(isDialog()).assertCountEquals(2)
        childEntry.performClick()
        input.assertIsDisplayed().assertTextEquals("回复草稿")
        closeEditorAndChildComments()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(COMMENT_SCREEN_LIST_TAG).fetchSemanticsNodes().size == 1
        }
        composeRule.onAllNodes(isDialog()).assertCountEquals(1)
        composeRule.onNode(hasTestTag(COMMENT_CLOSE_BUTTON_TAG) and hasAnyAncestor(rootPane)).assertIsDisplayed().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(COMMENT_SCREEN_LIST_TAG).fetchSemanticsNodes().isEmpty()
        }
        composeRule.onAllNodes(isDialog()).assertCountEquals(0)
        assertEquals(1, rootDismissCalls)

        composeRule.runOnIdle { commentsVisible.value = true }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("comment_child_button_root-99").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodes(isDialog()).assertCountEquals(1)
        composeRule.onNodeWithTag(COMMENT_HEADER_TAG).assertTextEquals("评论")
        composeRule.onNodeWithTag(COMMENT_LOADED_COUNT_TAG).assertTextEquals("已加载1条")
        rootEntry.performClick()
        input.assertTextEquals("根评论草稿")
        closeEditor.performClick()
        composeRule.onNodeWithTag("comment_child_button_root-99").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(COMMENT_CLOSE_BUTTON_TAG).fetchSemanticsNodes().size == 2
        }
        composeRule.onAllNodes(isDialog()).assertCountEquals(2)
        composeRule.onNode(hasTestTag(COMMENT_HEADER_TAG) and hasAnyAncestor(childPane)).assertTextEquals("回复")
        childEntry.performClick()
        input.assertTextEquals("回复草稿")
        closeEditor.performClick()
        composeRule
            .onNode(hasTestTag(COMMENT_CLOSE_BUTTON_TAG) and hasAnyAncestor(childPane))
            .assertContentDescriptionEquals("关闭回复")
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(COMMENT_SCREEN_LIST_TAG).fetchSemanticsNodes().size == 1
        }
        composeRule.onAllNodes(isDialog()).assertCountEquals(1)
        assertEquals(1, rootDismissCalls)
        composeRule.onNodeWithTag(COMMENT_CLOSE_BUTTON_TAG).assertContentDescriptionEquals("关闭评论").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(COMMENT_SCREEN_LIST_TAG).fetchSemanticsNodes().isEmpty()
        }
        composeRule.onAllNodes(isDialog()).assertCountEquals(0)
        assertEquals(2, rootDismissCalls)
    }

    @Test
    fun answerTabsLoadOnDemandAndSortChangeReturnsToTop() {
        val target = ROOT_ARTICLE.copy(id = 9042)
        val scoreComments = seedRootComments(count = 24)
        val timeComments = scoreComments.reversed()
        val threadUrl = "https://www.zhihu.com/api/v4/comment_v5/answers/${target.id}"
        val url = "$threadUrl/root_comment"
        ZhihuMockApi.mockJsonPrefix(
            method = HttpMethod.Get,
            urlPrefix = url,
            body =
                """
                {
                  "counts": {"total_counts": 24, "collapsed_counts": 0, "reviewing_counts": 0, "segment_comment_counts": 3},
                  "data": ${ZhihuJson.json.encodeToString(scoreComments)},
                  "paging": {"is_end": true, "is_start": true, "totals": 24, "next": ""}
                }
                """.trimIndent(),
        )
        val segmentSentence = seedRootComment(index = 30).copy(resourceType = "segment", resourceId = "seg-9001")
        ZhihuMockApi.mockJsonPrefix(
            method = HttpMethod.Get,
            urlPrefix = "$threadUrl/segment_comment",
            body = """{"data":null,"segment_infos":null,"paging":{"is_end":true,"is_start":true,"totals":0,"next":"","previous":""}}""",
        )
        ZhihuMockApi.mockJsonPrefix(
            method = HttpMethod.Get,
            urlPrefix = "$threadUrl/segment/root_comment?segment_id=seg-9001",
            body =
                """
                {
                  "data": ${ZhihuJson.json.encodeToString(listOf(segmentSentence))},
                  "paging": {"is_end": true, "is_start": true, "totals": 1, "next": ""}
                }
                """.trimIndent(),
        )
        val forwardsUrl = "https://api.zhihu.com/unify-consumption/forward/list?content_type=answer&content_token=${target.id}"
        val forwardCountUrl = forwardsUrl.replace("/forward/list", "/forward/count")
        ZhihuMockApi.mockJson(
            method = HttpMethod.Get,
            url = forwardCountUrl,
            body = """{"data":{"is_show_tab":true,"count":23}}""",
        )
        ZhihuMockApi.mockJson(
            method = HttpMethod.Get,
            url = forwardsUrl,
            body = """{"data":[{"id":"61","router":"https://www.zhihu.com/pin/62","title":"转发标题","summary":"<b>测试转发</b>","author":{"name":"测试作者"},"content_end_info":{"publish_time":12}}],"paging":{"is_end":true}}""",
        )
        val pinComments = "https://www.zhihu.com/api/v4/comment_v5/pins/61/root_comment"
        ZhihuMockApi.mockJsonPrefix(
            method = HttpMethod.Get,
            urlPrefix = pinComments,
            body = """{"data":[],"paging":{"is_end":true,"next":""}}""",
        )
        ZhihuMockApi.mockJson(
            method = HttpMethod.Get,
            url = "https://api.zhihu.com/unify-consumption/forward/count?content_type=pin&content_token=61",
            body = """{"data":{"is_show_tab":false,"count":0}}""",
        )
        val gate = CompletableDeferred<Unit>()
        val model = composeRule.seedViewModel<RootCommentViewModel>(key = "article:${target.type}:${target.id}") {
            RootCommentViewModel(target)
        }
        ZhihuMockApi.mockJson(
            method = HttpMethod.Get,
            url = "$url?order_by=ts&include=${model.include.encodeURLParameter()}",
            body =
                """
                {
                  "data": ${ZhihuJson.json.encodeToString(timeComments)},
                  "paging": {"is_end": true, "is_start": true, "totals": 24, "next": ""}
                }
                """.trimIndent(),
            beforeRespond = { gate.await() },
        )
        val listState = LazyListState()
        val navigator = setCommentScreen(content = target, listState = listState, knownSentenceCount = 3)
        composeRule.onNodeWithTag("comment_tab_normal").assertIsDisplayed().assertIsSelected()
        composeRule.onNodeWithTag("comment_tab_sentence").assertIsDisplayed()
        assertEquals(0, ZhihuMockApi.requestCount(HttpMethod.Get, "/segment_comment"))
        composeRule.onNodeWithTag(COMMENT_SCREEN_LIST_TAG).performScrollToNode(hasTestTag("comment_row_root-20"))
        composeRule.onNodeWithTag("comment_tab_sentence").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("comment_row_root-30").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("comment_row_root-30").assertIsDisplayed()
        composeRule.onNodeWithTag("comment_sentence_quote_root-30").assertIsDisplayed().assertTextEquals("“被评论的句子”")
        composeRule.onNodeWithTag("comment_segment_fallback_notice").assertDoesNotExist()
        composeRule.onNodeWithTag(COMMENT_SORT_BUTTON_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(COMMENT_SORT_SCORE_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(COMMENT_SORT_TIME_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(COMMENT_INPUT_TAG).assertDoesNotExist()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("comment_tab_forward").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(1, ZhihuMockApi.requestCount(HttpMethod.Get, forwardCountUrl))
        assertEquals(0, ZhihuMockApi.requestCount(HttpMethod.Get, forwardsUrl))
        composeRule
            .onNodeWithTag("comment_tab_forward")
            .performClick()
            .assertIsSelected()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("comment_forward_61").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("comment_forward_detail_61").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(listOf(Pin(62)), navigator.destinations) }
        composeRule.onNodeWithTag("comment_forward_61").assertIsDisplayed().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { ZhihuMockApi.requestCount(HttpMethod.Get, pinComments) == 1 }
        composeRule.onNodeWithTag(COMMENT_CLOSE_BUTTON_TAG).performClick()
        composeRule.onNodeWithTag("comment_tab_sentence").performClick()
        composeRule.onNodeWithTag("comment_row_root-30").assertIsDisplayed()
        assertEquals(1, ZhihuMockApi.requestCount(HttpMethod.Get, forwardsUrl))
        composeRule.onNodeWithTag("comment_tab_normal").performClick()
        composeRule.onNodeWithTag("comment_row_root-20").assertIsDisplayed()
        assertEquals(1, ZhihuMockApi.requestCount(HttpMethod.Get, url))
        assertEquals(1, ZhihuMockApi.requestCount(HttpMethod.Get, "/segment_comment"))
        assertEquals(1, ZhihuMockApi.requestCount(HttpMethod.Get, "/segment/root_comment"))
        composeRule.onNodeWithTag("comment_tab_normal").assertIsSelected()
        composeRule.onNodeWithTag("comment_row_root-20").assertIsDisplayed()
        composeRule.onNodeWithTag(COMMENT_SORT_SCORE_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(COMMENT_SORT_TIME_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(COMMENT_SORT_BUTTON_TAG).assertIsDisplayed().performClick()
        composeRule.onNodeWithTag(COMMENT_SORT_SCORE_TAG).assertIsDisplayed().assertIsSelected()
        composeRule.onNodeWithTag(COMMENT_SORT_TIME_TAG).assertIsDisplayed().performClick()
        composeRule.onNodeWithTag(COMMENT_SORT_TIME_TAG).assertDoesNotExist()
        composeRule.waitUntil(timeoutMillis = 5_000) { model.isLoading && model.sortOrder == CommentSortOrder.TIME }
        gate.complete(Unit)
        composeRule.waitUntil(timeoutMillis = 5_000) { !model.isLoading && model.allData.map { it.id } == timeComments.map { it.id } }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            val layout = listState.layoutInfo
            val visibleComments = layout.visibleItemsInfo.filter {
                it.offset < layout.viewportEndOffset &&
                    it.offset + it.size > layout.viewportStartOffset
            }
            visibleComments.firstOrNull()?.key == "root-24" &&
                visibleComments.none { it.key == "root-1" } &&
                visibleComments.map { it.key } == timeComments.take(visibleComments.size).map { it.id }
        }
        composeRule.runOnIdle {
            assertEquals(0, listState.firstVisibleItemIndex)
            assertEquals(0, listState.firstVisibleItemScrollOffset)
            assertEquals(timeComments.map { it.id }, model.allData.map { it.id })
        }
        val viewport = composeRule.onNodeWithTag(COMMENT_SCREEN_LIST_TAG).fetchSemanticsNode().boundsInRoot
        composeRule.waitUntil(timeoutMillis = 5_000) {
            val layout = listState.layoutInfo
            val visibleTags = layout.visibleItemsInfo
                .filter {
                    it.offset < layout.viewportEndOffset && it.offset + it.size > layout.viewportStartOffset
                }.mapTo(mutableSetOf()) { "comment_row_${it.key}" }
            val drawnTags = composeRule
                .onAllNodes(
                    SemanticsMatcher("评论行") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("comment_row_") == true },
                ).fetchSemanticsNodes()
                .filter {
                    it.boundsInRoot.width > 0f && it.boundsInRoot.height > 0f && it.boundsInRoot.overlaps(viewport)
                }.mapTo(mutableSetOf()) { it.config[SemanticsProperties.TestTag] }
            drawnTags == visibleTags
        }
        composeRule.onNodeWithTag("comment_row_root-24").assertIsDisplayed()
        composeRule.onNodeWithTag("comment_row_root-1").assertIsNotDisplayed()
        composeRule.onNodeWithTag(COMMENT_SCREEN_LIST_TAG).performScrollToNode(hasTestTag("comment_row_root-5"))
        val scrollPosition = composeRule.runOnIdle {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }
        val visibleKeys = composeRule.runOnIdle { listState.layoutInfo.visibleItemsInfo.map { it.key } }
        composeRule.onNodeWithTag(COMMENT_SORT_BUTTON_TAG).performClick()
        composeRule.onNodeWithTag(COMMENT_SORT_TIME_TAG).assertIsSelected().performClick()
        composeRule.onNodeWithTag("comment_row_root-5").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(scrollPosition, listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset)
            assertEquals(visibleKeys, listState.layoutInfo.visibleItemsInfo.map { it.key })
            assertEquals(timeComments.map { it.id }, model.allData.map { it.id })
        }
        assertEquals(2, ZhihuMockApi.requestCount(HttpMethod.Get, url))
    }

    private fun setCommentScreen(
        content: NavDestination = ROOT_ARTICLE,
        activeCommentItem: CommentItem? = null,
        onChildCommentClick: (CommentItem) -> Unit = {},
        listState: LazyListState? = null,
        knownSentenceCount: Int = 0,
    ): RecordingNavigator = composeRule.setScreenContent {
        val commentInput = remember { mutableStateOf("") }
        CommentScreen(
            content = { content },
            activeCommentItem = activeCommentItem,
            onChildCommentClick = onChildCommentClick,
            commentInput = commentInput.value,
            onCommentInputChange = { commentInput.value = it },
            listState = listState ?: rememberLazyListState(),
            commentSegments = mapOf(
                "seg-9001" to com.github.zly2006.zhihu.util.SegmentHighlightSpan(
                    text = "被评论的句子",
                    meta = com.github.zly2006.zhihu.data
                        .SegmentInfoMeta(segIds = listOf("seg-9001"), commentCount = knownSentenceCount),
                    contentId = (content as? Article)?.id?.toString(),
                    contentType = "answer",
                    paragraphId = "paragraph-9001",
                    startOffset = 0,
                    endOffset = 6,
                ),
            ),
        )
    }

    private fun seedRootComments(count: Int): List<DataHolder.Comment> = List(count) { index ->
        when (index) {
            0 -> seedRootComment(index = 1, childCommentCount = 2, withImage = true)
            1 -> seedRootComment(index = 2, replyToAuthor = seedAuthor("reply-to-root-2", "reply-to-root-2-token", "被回复作者 2"))
            else -> seedRootComment(index = index + 1)
        }
    }

    private fun seedChildComments(count: Int): List<DataHolder.Comment> = List(count) { index ->
        seedComment(
            id = "child-${index + 1}",
            authorId = "child-author-${index + 1}",
            authorName = "子回复作者 ${index + 1}",
            content = "子回复内容 ${index + 1}",
            likeCount = index + 1,
        )
    }

    private fun seedRootComment(
        index: Int,
        childCommentCount: Int = 0,
        withImage: Boolean = false,
        replyToAuthor: DataHolder.Comment.Author? = null,
    ): DataHolder.Comment = seedComment(
        id = "root-$index",
        authorId = "author-root-$index",
        authorName = "离线作者 $index",
        content = if (withImage) {
            "<p>根评论内容 $index</p><a class=\"comment_img\" href=\"https://example.invalid/comment-$index.jpg\">image</a>"
        } else {
            "根评论内容 $index"
        },
        likeCount = if (index == 1) 5 else index,
        childCommentCount = childCommentCount,
        childComments = if (childCommentCount > 0) {
            listOf(
                seedComment(
                    id = "root-$index-child-1",
                    authorId = "root-$index-child-author-1",
                    authorName = "子评论作者 1",
                    content = "内嵌子评论 1",
                    likeCount = 1,
                ),
            )
        } else {
            emptyList()
        },
        replyToAuthor = replyToAuthor,
    )

    private fun seedComment(
        id: String,
        authorId: String,
        authorName: String,
        content: String,
        likeCount: Int = 0,
        childCommentCount: Int = 0,
        childComments: List<DataHolder.Comment> = emptyList(),
        replyToAuthor: DataHolder.Comment.Author? = null,
        canDelete: Boolean = false,
    ): DataHolder.Comment = DataHolder.Comment(
        id = id,
        type = "comment",
        resourceType = "answer",
        url = "https://www.zhihu.com/comment/$id",
        content = if (content.trimStart().startsWith("<")) content else "<p>$content</p>",
        createdTime = 1_713_500_000L,
        isDelete = false,
        collapsed = false,
        reviewing = false,
        liked = false,
        likeCount = likeCount,
        isAuthor = false,
        canDelete = canDelete,
        author = seedAuthor(authorId, "$authorId-token", authorName),
        replyToAuthor = replyToAuthor,
        childCommentCount = childCommentCount,
        childComments = childComments,
    )

    private fun seedAuthor(id: String, urlToken: String, name: String): DataHolder.Comment.Author = DataHolder.Comment.Author(
        id = id,
        urlToken = urlToken,
        name = name,
        avatarUrl = "https://example.invalid/avatar/$id.png",
        avatarUrlTemplate = "",
        isOrg = false,
        type = "people",
        url = "https://www.zhihu.com/people/$urlToken",
        userType = "people",
        headline = "$name 的离线签名",
        gender = 0,
        isAdvertiser = false,
    )

    private companion object {
        val ROOT_ARTICLE = Article(
            type = ArticleType.Answer,
            id = 9001L,
            title = "离线评论宿主回答",
        )
    }
}
