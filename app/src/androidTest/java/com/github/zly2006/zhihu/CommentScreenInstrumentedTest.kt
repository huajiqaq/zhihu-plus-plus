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

import android.graphics.Rect
import android.os.SystemClock
import android.view.KeyEvent
import android.view.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.AnnotatedString
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.github.zly2006.zhihu.data.CommentSortOrder
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.CommentHolder
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.Navigator
import com.github.zly2006.zhihu.navigation.SegmentCommentHolder
import com.github.zly2006.zhihu.test.InstrumentedTestEnvironment
import com.github.zly2006.zhihu.test.MainActivityComposeRule
import com.github.zly2006.zhihu.test.RecordingNavigator
import com.github.zly2006.zhihu.test.ZhihuMockApi
import com.github.zly2006.zhihu.test.mockCommentDetail
import com.github.zly2006.zhihu.test.mockRootComments
import com.github.zly2006.zhihu.test.resetAppPreferences
import com.github.zly2006.zhihu.test.seedViewModel
import com.github.zly2006.zhihu.test.setScreenContent
import com.github.zly2006.zhihu.ui.COMMENT_CLOSE_BUTTON_TAG
import com.github.zly2006.zhihu.ui.COMMENT_COMPOSER_ENTRY_TAG
import com.github.zly2006.zhihu.ui.COMMENT_COMPOSER_SHEET_TAG
import com.github.zly2006.zhihu.ui.COMMENT_DELETE_DIALOG_TAG
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
import com.github.zly2006.zhihu.ui.COMMENT_TOOLS_BUTTON_TAG
import com.github.zly2006.zhihu.ui.CommentNavigationHandoff
import com.github.zly2006.zhihu.ui.CommentScreen
import com.github.zly2006.zhihu.ui.components.CommentScreenComponent
import com.github.zly2006.zhihu.ui.components.LocalNavHostLifecycleOwner
import com.github.zly2006.zhihu.ui.components.rememberVoiceCommentPlayer
import com.github.zly2006.zhihu.viewmodel.CommentItem
import com.github.zly2006.zhihu.viewmodel.comment.RootCommentViewModel
import com.github.zly2006.zhihu.viewmodel.filter.BlockedUser
import com.github.zly2006.zhihu.viewmodel.filter.getContentFilterDatabase
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    /** https://github.com/zly2006/zhihu-plus-plus/issues/569 */
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
        if (!InstrumentedTestEnvironment.isMockMode()) composeRule.resetAppPreferences()
    }

    /** https://github.com/zly2006/zhihu-plus-plus/issues/598 */
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
        val input = composeRule.onNodeWithTag(COMMENT_INPUT_TAG)
        awaitImeVisibility(input, visible = false)
        composeRule.onNodeWithTag(COMMENT_EMOJI_BUTTON_TAG).performClick()
        composeRule.onNodeWithContentDescription("选择表情").assertIsDisplayed()
        awaitImeVisibility(input, visible = true)
        composeRule.onNodeWithTag(COMMENT_EMOJI_BUTTON_TAG).performClick()
        val picker = composeRule.onNodeWithTag(COMMENT_EMOJI_PICKER_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(COMMENT_INPUT_TAG).assertIsNotFocused()
        pressBackAfterImeHidden(picker)
        composeRule.waitUntil("等待返回收起表情面板", 5_000) {
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

    /** 只以当前评论窗口收到的系统 IME 为准；输入焦点、语义输入或非零导航栏均不能代替软键盘。 */
    @Test
    @SdkSuppress(minSdkVersion = 30)
    fun rootAndChildEditorsAvoidRealIme() {
        val originalOrientation = composeRule.activity.requestedOrientation
        try {
            composeRule.activityRule.scenario.onActivity { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            composeRule.waitUntil("等待移动端竖屏", 5_000) {
                composeRule.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
            }
            mockRootComments(
                "https://www.zhihu.com/api/v4/comment_v5/answers/9001/root_comment",
                listOf(seedRootComment(1, childCommentCount = 24)),
            )
            mockRootComments("https://www.zhihu.com/api/v4/comment_v5/comment/root-1/child_comment", seedChildComments(24))
            composeRule.setScreenContent {
                CommentScreenComponent(showComments = true, onDismiss = { error("输入法返回不应关闭根评论") }, content = ROOT_ARTICLE)
            }

            fun node(pane: String, tag: String) = composeRule.onNode(
                hasTestTag(tag) and hasAnyAncestor(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, pane)),
            )

            fun awaitIme(pane: String, visible: Boolean) {
                val view = (node(pane, COMMENT_INPUT_TAG).fetchSemanticsNode().root as ViewRootForTest).view
                val ime = WindowInsets.Type.ime()
                try {
                    composeRule.waitUntil("等待 $pane Dialog 的系统软键盘 visible=$visible（需允许停靠软键盘）", 5_000) {
                        composeRule.runOnUiThread {
                            val insets = view.rootWindowInsets ?: return@runOnUiThread false
                            val bottom = insets.getInsets(ime).bottom
                            view.hasWindowFocus() &&
                                insets.isVisible(ime) == visible &&
                                (if (visible) bottom > insets.getInsets(WindowInsets.Type.navigationBars()).bottom else bottom == 0)
                        }
                    }
                } catch (error: androidx.compose.ui.test.ComposeTimeoutException) {
                    val input = node(pane, COMMENT_INPUT_TAG).fetchSemanticsNode()
                    val tools = node(pane, COMMENT_TOOLS_BUTTON_TAG).fetchSemanticsNode()
                    val currentView = (input.root as ViewRootForTest).view
                    val diagnostic = composeRule.runOnUiThread {
                        val insets = currentView.rootWindowInsets
                        val activityView = composeRule.activity.window.decorView
                        "sameView=${view === currentView} sameToolsRoot=${input.root === tools.root} " +
                            "attached=${currentView.isAttachedToWindow} placed=${input.layoutInfo.isPlaced} " +
                            "focused=${input.config.getOrNull(SemanticsProperties.Focused)} " +
                            "windowFocus=${currentView.hasWindowFocus()} decorFocus=${currentView.rootView.hasWindowFocus()} " +
                            "imeVisible=${insets?.isVisible(ime)} ime=${insets?.getInsets(ime)?.bottom} " +
                            "nav=${insets?.getInsets(WindowInsets.Type.navigationBars())?.bottom} " +
                            "decorIme=${currentView.rootView.rootWindowInsets?.getInsets(ime)?.bottom} " +
                            "decorNav=${currentView.rootView.rootWindowInsets?.getInsets(WindowInsets.Type.navigationBars())?.bottom} " +
                            "activityFocus=${activityView.hasWindowFocus()} activityIme=${activityView.rootWindowInsets?.isVisible(ime)} " +
                            "activityImeBottom=${activityView.rootWindowInsets?.getInsets(ime)?.bottom} " +
                            "windowHeight=${currentView.rootView.height} viewHeight=${currentView.height}"
                    }
                    throw AssertionError("$pane 系统输入窗口未就绪：$diagnostic", error)
                }
                if (visible) {
                    var geometry = ""
                    try {
                        composeRule.waitUntil("等待 $pane 的输入框和工具行贴合实际 IME 顶边，不重叠也不重复留白", 5_000) {
                            // Insets 动画会重新布局，不能一直比较动画前的语义节点坐标。
                            val input = node(pane, COMMENT_INPUT_TAG).fetchSemanticsNode()
                            val tools = node(pane, COMMENT_TOOLS_BUTTON_TAG).fetchSemanticsNode()
                            composeRule.runOnUiThread {
                                val insets = view.rootWindowInsets ?: return@runOnUiThread false
                                val bottom = insets.getInsets(ime).bottom
                                val windowOrigin = IntArray(2).also(view.rootView::getLocationOnScreen)
                                // edge-to-edge 的可见窗口 frame 不随 IME 缩短，键盘顶边须由原窗口和真实 inset 计算。
                                val imeTop = windowOrigin[1] + view.rootView.height - bottom
                                val toolsBottom = tools.positionOnScreen.y + tools.size.height
                                val inputBottom = input.positionOnScreen.y + input.size.height
                                geometry = "windowY=${windowOrigin[1]} rootHeight=${view.rootView.height} ime=$bottom imeTop=$imeTop inputBottom=$inputBottom toolsBottom=$toolsBottom"
                                insets.isVisible(ime) &&
                                    bottom > insets.getInsets(WindowInsets.Type.navigationBars()).bottom &&
                                    input.size.height > 0 &&
                                    inputBottom <= imeTop + 1 &&
                                    tools.size.height > 0 &&
                                    kotlin.math.abs(toolsBottom - imeTop) <= 1f
                            }
                        }
                    } catch (error: Throwable) {
                        throw AssertionError("$pane 输入法几何不正确：$geometry", error)
                    }
                }
            }

            composeRule.onNodeWithTag(COMMENT_INPUT_TAG).assertDoesNotExist()
            node("评论", COMMENT_COMPOSER_ENTRY_TAG).assertIsDisplayed().performClick()
            val rootInput = node("写评论", COMMENT_INPUT_TAG)
            rootInput.assertIsDisplayed().performTouchInput { click() }
            rootInput.assertIsFocused()
            awaitIme("写评论", true)
            rootInput.performTextInput("根草稿")
            pressSystemBack()
            awaitIme("写评论", false)
            composeRule.onAllNodesWithTag(COMMENT_SCREEN_LIST_TAG).assertCountEquals(1)
            rootInput.assertTextEquals("根草稿")
            pressSystemBack(closingTag = COMMENT_COMPOSER_SHEET_TAG)
            composeRule.onNodeWithTag(COMMENT_COMPOSER_SHEET_TAG).assertDoesNotExist()
            composeRule.onNodeWithTag("comment_child_button_root-1").performClick()

            val childList = node("回复", COMMENT_SCREEN_LIST_TAG)
            childList.performScrollToNode(hasTestTag("comment_reply_button_child-15"))
            composeRule.onNodeWithTag("comment_reply_button_child-15").performClick()
            val childInput = node("写回复", COMMENT_INPUT_TAG)
            childInput.assertContentDescriptionEquals("回复 子回复作者 15 的评论").performTouchInput { click() }
            childInput.assertIsFocused()
            awaitIme("写回复", true)
            childInput.performTextInput("子草稿")
            val emoji = node("写回复", COMMENT_EMOJI_BUTTON_TAG)
            emoji.performTouchInput { click() }
            awaitIme("写回复", false)
            childInput.assertIsNotFocused()
            node("写回复", COMMENT_EMOJI_PICKER_TAG).assertIsDisplayed()
            pressSystemBack(closingTag = COMMENT_EMOJI_PICKER_TAG)
            node("写回复", COMMENT_EMOJI_PICKER_TAG).assertDoesNotExist()
            childInput.assertIsDisplayed()
            emoji.performTouchInput { click() }
            node("写回复", COMMENT_EMOJI_PICKER_TAG).assertIsDisplayed()
            emoji.performTouchInput { click() }
            awaitIme("写回复", true)
            childInput.assertIsFocused()
            node("写回复", COMMENT_EMOJI_PICKER_TAG).assertDoesNotExist()
            pressSystemBack()
            awaitIme("写回复", false)
            composeRule.onAllNodesWithTag(COMMENT_SCREEN_LIST_TAG).assertCountEquals(2)
            childInput.assertContentDescriptionEquals("回复 子回复作者 15 的评论").assertTextEquals("子草稿")
            pressSystemBack(closingTag = COMMENT_COMPOSER_SHEET_TAG)
            childInput.assertDoesNotExist()
            childList.assertIsDisplayed()
            composeRule.onNodeWithTag("comment_row_child-15").assertIsDisplayed()
            node("回复", COMMENT_COMPOSER_ENTRY_TAG).performClick()
            childInput.assertContentDescriptionEquals("回复 子回复作者 15 的评论").assertTextEquals("子草稿")
            composeRule.onNodeWithContentDescription("关闭编辑面板").performClick()
            node("回复", COMMENT_CLOSE_BUTTON_TAG).performClick()
            composeRule.onAllNodesWithTag(COMMENT_SCREEN_LIST_TAG).assertCountEquals(1)
            node("评论", COMMENT_COMPOSER_ENTRY_TAG).performClick()
            rootInput.assertTextEquals("根草稿")
        } finally {
            composeRule.activityRule.scenario.onActivity { it.requestedOrientation = originalOrientation }
        }
    }

    /** https://github.com/zly2006/zhihu-plus-plus/issues/595 */
    @Test
    fun spanFragmentsWithTheSameSegmentIdsReuseTheCommentThread() {
        val firstFragment = SegmentCommentHolder(
            contentId = "1907864533831225689",
            contentType = "answer",
            segmentId = "1993195116945487118,1978217501180568395",
            segmentContent = "吃柠檬，怎么吃？谁吃的？吃的哪儿？什么时候吃的？吃的心情怎么样？",
            paragraphId = "nX5RAoeG",
            startOffset = 0,
            endOffset = 32,
        )
        val secondFragment = firstFragment.copy(
            segmentContent = "柠檬，是怎样的檬？这个檬是否从事正当行业？这个檬是活着还是死的？",
            paragraphId = "CANw6uZN",
        )
        val currentFragment = mutableStateOf<NavDestination>(firstFragment)
        val urlPrefix =
            "https://www.zhihu.com/api/v4/comment_v5/answers/${firstFragment.contentId}/segment/root_comment" +
                "?segment_id=${firstFragment.segmentId}"
        mockRootComments(
            urlPrefix = urlPrefix,
            commentId = "root-1",
        )

        composeRule.setScreenContent {
            val commentInput = remember { mutableStateOf("") }
            CommentScreen(
                content = { currentFragment.value },
                onChildCommentClick = {},
                commentInput = commentInput.value,
                onCommentInputChange = { commentInput.value = it },
            )
        }
        composeRule.onNodeWithTag("comment_row_root-1").assertIsDisplayed()

        composeRule.runOnIdle { currentFragment.value = secondFragment }

        composeRule.onNodeWithTag("comment_row_root-1").assertIsDisplayed()
        assertEquals(1, ZhihuMockApi.requestCount(HttpMethod.Get, urlPrefix))
    }

    /** 宿主暂停保留窗口组合；前台覆盖只隐藏窗口，草稿、滚动和关闭次数不受影响。 */
    @Test
    fun commentDialogKeepsCompositionDuringHostPauseAndRestoresCoveredPageState() {
        mockRootComments(
            urlPrefix = "https://www.zhihu.com/api/v4/comment_v5/answers/9001/root_comment",
            comments = seedRootComments(count = 24),
        )
        val hostOwner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
        }
        val entryOwner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
        }
        var bodyEntered = 0
        var bodyDisposed = 0
        var dismissCalls = 0
        composeRule.setScreenContent {
            CompositionLocalProvider(
                LocalNavHostLifecycleOwner provides hostOwner,
                LocalLifecycleOwner provides entryOwner,
            ) {
                CommentScreenComponent(
                    showComments = true,
                    onDismiss = { dismissCalls++ },
                    content = ROOT_ARTICLE,
                    rootHeader = {
                        DisposableEffect(Unit) {
                            bodyEntered++
                            onDispose { bodyDisposed++ }
                        }
                        Text("保留的引文")
                    },
                )
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNodeWithTag(COMMENT_LOADED_COUNT_TAG).assertTextEquals("已加载24条") }.isSuccess
        }
        composeRule.onAllNodes(isDialog()).assertCountEquals(1)
        composeRule.onNodeWithTag(COMMENT_COMPOSER_ENTRY_TAG).performClick()
        composeRule.onNodeWithTag(COMMENT_INPUT_TAG).performTextInput("覆盖前的草稿")
        composeRule.onNodeWithTag(COMMENT_INPUT_TAG).assertTextEquals("覆盖前的草稿")
        composeRule.onNodeWithContentDescription("关闭编辑面板").performClick()
        composeRule.onNodeWithTag(COMMENT_SCREEN_LIST_TAG).performScrollToNode(hasTestTag("comment_row_root-20"))
        composeRule.onNodeWithTag("comment_row_root-20").assertIsDisplayed()
        composeRule.onNodeWithTag(COMMENT_COMPOSER_ENTRY_TAG).performClick()

        composeRule.runOnIdle {
            hostOwner.lifecycle.currentState = Lifecycle.State.STARTED
            entryOwner.lifecycle.currentState = Lifecycle.State.STARTED
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(COMMENT_COMPOSER_SHEET_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(COMMENT_INPUT_TAG).assertTextEquals("覆盖前的草稿")
        composeRule.runOnIdle {
            assertEquals(1, bodyEntered)
            assertEquals(0, bodyDisposed)
            assertEquals(0, dismissCalls)
        }

        composeRule.runOnIdle { hostOwner.lifecycle.currentState = Lifecycle.State.RESUMED }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(isDialog()).fetchSemanticsNodes().isEmpty()
        }
        composeRule.runOnIdle {
            assertEquals(1, bodyDisposed)
            assertEquals(0, dismissCalls)
        }

        composeRule.runOnIdle { entryOwner.lifecycle.currentState = Lifecycle.State.RESUMED }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching { composeRule.onNodeWithTag("comment_row_root-20").assertIsDisplayed() }.isSuccess
        }
        composeRule.onAllNodes(isDialog()).assertCountEquals(1)
        composeRule.onNodeWithText("保留的引文").assertIsDisplayed()
        composeRule.onNodeWithTag("comment_row_root-20").assertIsDisplayed()
        composeRule.onNodeWithTag(COMMENT_COMPOSER_ENTRY_TAG).performClick()
        composeRule.onNodeWithTag(COMMENT_INPUT_TAG).assertTextEquals("覆盖前的草稿")
        composeRule.runOnIdle {
            assertEquals(2, bodyEntered)
            assertEquals(1, bodyDisposed)
            assertEquals(0, dismissCalls)
        }
    }

    /** https://github.com/zly2006/zhihu-plus-plus/issues/572 */
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

        fun probe(label: String) {
            val details = runCatching {
                val nodes = composeRule.onAllNodesWithTag(COMMENT_SCREEN_LIST_TAG).fetchSemanticsNodes()
                val lists = nodes.map { node ->
                    val pane = generateSequence(node.parent) { it.parent }
                        .mapNotNull { it.config.getOrNull(SemanticsProperties.PaneTitle) }.firstOrNull()
                    val view = (node.root as ViewRootForTest).view
                    composeRule.runOnUiThread {
                        val insets = view.rootWindowInsets
                        "$pane:root=${System.identityHashCode(view)}:size=${node.size}" +
                            ":focus=${view.hasWindowFocus()}:attached=${view.isAttachedToWindow}" +
                            ":ime=${insets?.isVisible(WindowInsets.Type.ime())}" +
                            ":imeBottom=${insets?.getInsets(WindowInsets.Type.ime())?.bottom}"
                    }
                }
                "lists=$lists rootDismissCalls=$rootDismissCalls visible=${commentsVisible.value}"
            }.getOrElse { "probeError=${it::class.simpleName}" }
            InstrumentationRegistry.getInstrumentation().sendStatus(
                42, android.os.Bundle().apply { putString("focus_probe", "$label $details") },
            )
        }

        fun closeEditorAndPressBack(target: SemanticsNodeInteraction) {
            closeEditor.performClick()
            composeRule.waitUntil("等待异步关闭的评论编辑窗口离开", timeoutMillis = 5_000) {
                composeRule.onAllNodesWithTag(COMMENT_COMPOSER_SHEET_TAG).fetchSemanticsNodes().isEmpty()
            }
            pressBackAfterImeHidden(target)
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
        closeEditorAndPressBack(childList)
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
        closeEditorAndPressBack(childList)
        try {
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithTag(COMMENT_SCREEN_LIST_TAG).fetchSemanticsNodes().size == 1
            }
        } finally {
            probe("second-back-final")
        }
        composeRule.onAllNodes(isDialog()).assertCountEquals(1)
        pressBackAfterImeHidden(rootList)
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
    fun childCommentMoreMenuTracksAnchorWithinWindowAndBackLayers() {
        mockRootComments(
            "https://www.zhihu.com/api/v4/comment_v5/answers/9001/root_comment",
            listOf(seedRootComment(1, childCommentCount = 24)),
        )
        mockRootComments(
            "https://www.zhihu.com/api/v4/comment_v5/comment/root-1/child_comment",
            seedChildComments(24).map { it.copy(canDelete = it.id == "child-12") },
        )
        val visible = mutableStateOf(true)
        var rootDismissals = 0
        composeRule.setScreenContent {
            CommentScreenComponent(
                showComments = visible.value,
                onDismiss = {
                    visible.value = false
                    rootDismissals++
                },
                content = ROOT_ARTICLE,
            )
        }
        composeRule.onNodeWithTag("comment_child_button_root-1").performClick()
        val childPane = SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "回复")
        val childList = composeRule.onNode(hasTestTag(COMMENT_SCREEN_LIST_TAG) and hasAnyAncestor(childPane))
        childList.performScrollToNode(hasTestTag("comment_more_button_child-12"))
        childList.performSemanticsAction(SemanticsActions.ScrollToIndex) { assertTrue(it(12)) }
        val button = composeRule.onNodeWithTag("comment_more_button_child-12")
        val menuTag = "comment_more_menu_child-12"
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        fun openMenuAndCheckAnchor(): Float {
            val anchor = button.assertIsDisplayed().fetchSemanticsNode()
            val view = (anchor.root as ViewRootForTest).view
            button.performTouchInput { click() }
            val menu = composeRule.onNodeWithTag(menuTag).assertIsDisplayed().fetchSemanticsNode()
            val menuTop = menu.positionOnScreen.y
            composeRule.onNodeWithText("举报").assertIsDisplayed()
            composeRule.onNodeWithTag("comment_delete_menu_item_child-12").assertIsDisplayed()
            composeRule.onAllNodes(isDialog()).assertCountEquals(2)
            composeRule.runOnIdle {
                val frame = Rect().also(view::getWindowVisibleDisplayFrame)
                val left = menu.positionOnScreen.x
                val top = menu.positionOnScreen.y
                val right = left + menu.size.width
                val bottom = top + menu.size.height
                val anchorTop = anchor.positionOnScreen.y
                val anchorBottom = anchorTop + anchor.size.height
                val gap = 8f * view.resources.displayMetrics.density + 1f
                assertTrue("菜单不能越过当前窗口左边界", left >= frame.left - 1f)
                assertTrue("菜单不能越过当前窗口右边界", right <= frame.right + 1f)
                assertTrue("菜单不能越过当前窗口上边界", top >= frame.top - 1f)
                assertTrue("菜单不能越过当前窗口下边界", bottom <= frame.bottom + 1f)
                assertTrue(
                    "菜单必须紧邻所点按钮的上沿或下沿，不能在窗口居中",
                    (top >= anchorBottom - 1f && top <= anchorBottom + gap) ||
                        (bottom <= anchorTop + 1f && bottom >= anchorTop - gap),
                )
                assertTrue(
                    "菜单横向必须覆盖所点按钮",
                    left <= anchor.positionOnScreen.x + anchor.size.width && right >= anchor.positionOnScreen.x,
                )
            }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithTag(menuTag).fetchSemanticsNodes().isEmpty()
            }
            composeRule.onAllNodes(isDialog()).assertCountEquals(2)
            childList.assertIsDisplayed()
            assertEquals(0, rootDismissals)
            return menuTop
        }

        val firstAnchor = button.fetchSemanticsNode()
        val firstAnchorY = firstAnchor.positionOnScreen.y
        val firstMenuY = openMenuAndCheckAnchor()
        val scrollDistance = childList.fetchSemanticsNode().size.height * 0.45f
        childList.performSemanticsAction(SemanticsActions.ScrollBy) { assertTrue(it(0f, -scrollDistance)) }
        assertTrue(
            "必须在实际列表滚动后的不同位置再次打开菜单",
            button.fetchSemanticsNode().positionOnScreen.y > firstAnchorY + firstAnchor.size.height,
        )
        val secondMenuY = openMenuAndCheckAnchor()
        assertTrue("菜单位置必须随锚点改变", kotlin.math.abs(secondMenuY - firstMenuY) > 1f)

        button.performTouchInput { click() }
        composeRule.onNodeWithTag("comment_delete_menu_item_child-12").performTouchInput { click() }
        composeRule.onNodeWithTag(menuTag).assertDoesNotExist()
        composeRule.onNodeWithTag(COMMENT_DELETE_DIALOG_TAG).assertIsDisplayed().assert(hasAnyAncestor(isDialog()))
        composeRule.onAllNodes(isDialog()).assertCountEquals(3)
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        composeRule.onNodeWithTag(COMMENT_DELETE_DIALOG_TAG).assertDoesNotExist()
        assertEquals(0, ZhihuMockApi.requestCount(HttpMethod.Delete, "/comment_v5/comment/child-12"))
        composeRule.onAllNodes(isDialog()).assertCountEquals(2)
        childList.assertIsDisplayed()
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        composeRule.onAllNodes(isDialog()).assertCountEquals(1)
        composeRule.onNodeWithTag("comment_more_button_root-1").performTouchInput { click() }
        composeRule.onNodeWithText("举报").assertIsDisplayed()
        composeRule.onNodeWithTag("comment_delete_menu_item_root-1").assertDoesNotExist()
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        composeRule.onNodeWithTag("comment_more_menu_root-1").assertDoesNotExist()
        composeRule.onAllNodes(isDialog()).assertCountEquals(1)
        assertEquals(0, rootDismissals)
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        composeRule.onAllNodes(isDialog()).assertCountEquals(0)
        assertEquals(1, rootDismissals)
    }

    /**
     * 图片评论仍须显示可点击的回复与点赞操作；行为依据：https://github.com/zly2006/zhihu-plus-plus/issues/369
     */
    @Test
    fun commentWithImageKeepsReplyAndLikeActionsVisible() {
        /*
         * 预期行为：
         * 1. 含内嵌图片的评论应与纯文字评论一样显示底部操作行。
         * 2. 图片布局完成后，回复和点赞按钮必须仍可见、可点击。
         */
        val childEntryCommentIds = mutableListOf<String>()
        val seededComments = seedRootComments(count = 4)
        mockRootComments(
            urlPrefix = "https://www.zhihu.com/api/v4/comment_v5/answers/9001/root_comment",
            comments = seededComments,
        )

        setCommentScreen(
            onChildCommentClick = { childEntryCommentIds += it.item.id },
        )

        composeRule
            .onNodeWithTag(COMMENT_SCREEN_LIST_TAG)
            .performScrollToNode(hasTestTag("comment_image_root-1"))
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag("comment_image_root-1", useUnmergedTree = true).assertIsDisplayed()
            }.isSuccess
        }

        composeRule.onNodeWithTag("comment_reply_button_root-1").assertIsDisplayed()
        composeRule.onNodeWithTag("comment_like_button_root-1").assertIsDisplayed()
        val rowBounds = composeRule
            .onAllNodesWithTag("comment_row_root-1")
            .fetchSemanticsNodes()
            .single()
            .boundsInRoot
        val imageBounds = composeRule
            .onAllNodesWithTag("comment_image_root-1", useUnmergedTree = true)
            .fetchSemanticsNodes()
            .single()
            .boundsInRoot
        val replyBounds = composeRule
            .onAllNodesWithTag("comment_reply_button_root-1")
            .fetchSemanticsNodes()
            .single()
            .boundsInRoot
        val likeBounds = composeRule
            .onAllNodesWithTag("comment_like_button_root-1")
            .fetchSemanticsNodes()
            .single()
            .boundsInRoot
        val expectedBottom = maxOf(imageBounds.bottom, replyBounds.bottom, likeBounds.bottom)
        assertTrue(
            "评论行应包含图片和操作行的完整边界，但行底部为 " +
                "${rowBounds.bottom}，内容底部为 $expectedBottom",
            rowBounds.bottom >= expectedBottom,
        )
        composeRule.onNodeWithTag("comment_more_button_root-1").performClick()
        composeRule.onNodeWithText("举报").assertIsDisplayed()
        composeRule.onNodeWithTag("comment_delete_menu_item_root-1").assertDoesNotExist()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val targetPackage = composeRule.activity.packageName
        var activePackage: String? = null
        composeRule.waitUntil("等待评论菜单拥有活跃的应用窗口", timeoutMillis = 5_000) {
            activePackage = automation.rootInActiveWindow?.packageName?.toString()
            activePackage == targetPackage
        }
        assertEquals(targetPackage, activePackage)
        val downTime = SystemClock.uptimeMillis()
        assertTrue(
            automation.injectInputEvent(KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0), true),
        )
        assertTrue(
            automation.injectInputEvent(KeyEvent(downTime, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK, 0), true),
        )
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("举报").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag("comment_reply_button_root-1").assertIsDisplayed().performClick()
        composeRule
            .onNodeWithTag("comment_like_button_root-1")
            .assertIsDisplayed()
            .assertIsNotSelected()
            .assertContentDescriptionEquals("喜欢")
            .performClick()

        assertEquals(listOf("root-1"), childEntryCommentIds)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            ZhihuMockApi.requestCount(HttpMethod.Post, "/comments/root-1/like") == 1 &&
                runCatching { composeRule.onNodeWithTag("comment_like_button_root-1").assertIsSelected() }.isSuccess
        }
        composeRule
            .onNodeWithTag("comment_like_button_root-1")
            .assertContentDescriptionEquals("取消喜欢")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "已喜欢，6"))
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            ZhihuMockApi.requestCount(HttpMethod.Delete, "/comments/root-1/like") == 1 &&
                runCatching { composeRule.onNodeWithTag("comment_like_button_root-1").assertIsNotSelected() }.isSuccess
        }
        composeRule
            .onNodeWithTag("comment_like_button_root-1")
            .assertContentDescriptionEquals("喜欢")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "5"))
    }

    /**
     * 被屏蔽用户的根评论与内嵌子评论都不得展示；行为依据：https://github.com/zly2006/zhihu-plus-plus/issues/357
     */
    @Test
    fun blockedUsersAreRemovedFromRootAndEmbeddedChildComments() {
        /*
         * 预期行为：
         * 1. 生产响应处理应移除被屏蔽用户发布的根评论。
         * 2. 页面收到保留的根评论前，也应移除其中被屏蔽用户的内嵌子评论。
         */
        runBlocking {
            val database = getContentFilterDatabase(composeRule.activity)
            database.blockedUserDao().insertUser(BlockedUser("blocked-root-author", "被屏蔽根评论作者"))
            database.blockedUserDao().insertUser(BlockedUser("blocked-child-author", "被屏蔽子评论作者"))
            mockRootComments(
                urlPrefix = "https://www.zhihu.com/api/v4/comment_v5/answers/9001/root_comment",
                comments = listOf(
                    seedComment(
                        id = "blocked-root",
                        authorId = "blocked-root-author",
                        authorName = "被屏蔽根评论作者",
                        content = "这条根评论不应展示",
                    ),
                    seedComment(
                        id = "allowed-root",
                        authorId = "allowed-root-author",
                        authorName = "可见根评论作者",
                        content = "这条根评论应展示",
                        childCommentCount = 2,
                        childComments = listOf(
                            seedComment(
                                id = "blocked-child",
                                authorId = "blocked-child-author",
                                authorName = "被屏蔽子评论作者",
                                content = "这条内嵌子评论不应展示",
                            ),
                            seedComment(
                                id = "allowed-child",
                                authorId = "allowed-child-author",
                                authorName = "可见子评论作者",
                                content = "这条内嵌子评论应展示",
                            ),
                        ),
                    ),
                ),
            )
        }

        setCommentScreen()

        composeRule.onNodeWithTag("comment_row_allowed-root").assertIsDisplayed()
        composeRule.onNodeWithText("可见根评论作者").assertIsDisplayed()
        composeRule.onNodeWithText("这条内嵌子评论应展示").assertIsDisplayed()
        composeRule.onAllNodesWithTag("comment_row_blocked-root").assertCountEquals(0)
        composeRule.onAllNodesWithTag("comment_row_blocked-child").assertCountEquals(0)
        composeRule.onAllNodesWithText("被屏蔽根评论作者").assertCountEquals(0)
        composeRule.onAllNodesWithText("被屏蔽子评论作者").assertCountEquals(0)
        composeRule.onNodeWithTag("comment_tab_sentence").assertDoesNotExist()
        composeRule.onNodeWithTag(COMMENT_SORT_BUTTON_TAG).assertIsDisplayed()
    }

    @Test
    fun answerTabsLoadOnDemandAndSortChangeReturnsToTop() {
        val scoreComments = seedRootComments(count = 24)
        val timeComments = scoreComments.reversed()
        val url = "https://www.zhihu.com/api/v4/comment_v5/answers/9001/root_comment"
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
            urlPrefix = "https://www.zhihu.com/api/v4/comment_v5/answers/9001/segment_comment",
            body = """{"data":null,"segment_infos":null,"paging":{"is_end":true,"is_start":true,"totals":0,"next":"","previous":""}}""",
        )
        ZhihuMockApi.mockJsonPrefix(
            method = HttpMethod.Get,
            urlPrefix = "https://www.zhihu.com/api/v4/comment_v5/answers/9001/segment/root_comment?segment_id=seg-9001",
            body =
                """
                {
                  "data": ${ZhihuJson.json.encodeToString(listOf(segmentSentence))},
                  "paging": {"is_end": true, "is_start": true, "totals": 1, "next": ""}
                }
                """.trimIndent(),
        )
        val gate = CompletableDeferred<Unit>()
        val model = composeRule.seedViewModel<RootCommentViewModel>(key = ROOT_ARTICLE_COMMENT_VIEW_MODEL_KEY) {
            RootCommentViewModel(ROOT_ARTICLE)
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
        setCommentScreen(listState = listState, knownSentenceCount = 3)
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
        composeRule.onNodeWithTag("comment_tab_normal").performClick()
        composeRule.onNodeWithTag("comment_row_root-20").assertIsDisplayed()
        assertEquals(1, ZhihuMockApi.requestCount(HttpMethod.Get, "/api/v4/comment_v5/answers/9001/root_comment"))
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
        assertEquals(2, ZhihuMockApi.requestCount(HttpMethod.Get, "/api/v4/comment_v5/answers/9001/root_comment"))
    }

    @Test
    fun totalCommentsKeepKnownSentenceEntryWhenAggregateCountIsMissingOrZero() {
        for (counts in listOf("{}", "{\"segment_comment_counts\":0}")) {
            val base = "https://www.zhihu.com/api/v4/comment_v5/answers/9001"
            ZhihuMockApi.mockJsonPrefix(HttpMethod.Get, "$base/root_comment", """{"counts":$counts,"data":${ZhihuJson.json.encodeToString(seedRootComments(1))},"paging":{"is_end":true,"next":""}}""")
            ZhihuMockApi.mockJsonPrefix(HttpMethod.Get, "$base/segment_comment", """{"counts":{"total_counts":0},"data":null,"paging":{"is_end":true,"next":""}}""")
            val sentence = seedRootComment(30).copy(resourceType = "segment", resourceId = "seg-9001")
            ZhihuMockApi.mockJsonPrefix(HttpMethod.Get, "$base/segment/root_comment", """{"data":${ZhihuJson.json.encodeToString(listOf(sentence))},"paging":{"is_end":true,"next":""}}""")
            composeRule.setScreenContent {
                CommentScreenComponent(
                    showComments = true,
                    onDismiss = {},
                    content = ROOT_ARTICLE,
                    commentSegments = mapOf(
                        "seg-9001" to com.github.zly2006.zhihu.util.SegmentHighlightSpan(
                            text = "正文证实存在的句评",
                            meta = com.github.zly2006.zhihu.data
                                .SegmentInfoMeta(segIds = listOf("seg-9001"), commentCount = 3),
                            contentId = "9001",
                            contentType = "answer",
                            paragraphId = "paragraph-9001",
                            startOffset = 0,
                            endOffset = 6,
                        ),
                    ),
                )
            }
            composeRule.onNodeWithTag("comment_tab_sentence").assertIsDisplayed().performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithTag("comment_row_root-30").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("comment_row_root-30").assertIsDisplayed()
            composeRule.onNodeWithTag(COMMENT_SORT_BUTTON_TAG).assertDoesNotExist()
            composeRule.onNodeWithTag("comment_segment_fallback_notice").assertDoesNotExist()
            composeRule.onNodeWithTag(COMMENT_SORT_SCORE_TAG).assertDoesNotExist()
            composeRule.onNodeWithTag(COMMENT_SORT_TIME_TAG).assertDoesNotExist()
            composeRule.onNodeWithTag("comment_tab_normal").performClick()
            composeRule.onNodeWithTag(COMMENT_SORT_BUTTON_TAG).assertIsDisplayed()
            composeRule.onNodeWithTag(COMMENT_SORT_SCORE_TAG).assertDoesNotExist()
            composeRule.onNodeWithTag(COMMENT_SORT_TIME_TAG).assertDoesNotExist()
            composeRule.setScreenContent { }
            composeRule.runOnIdle { composeRule.activity.viewModelStore.clear() }
        }
    }

    @Test
    fun mediaOnlyAndShortCommentsFoldImagesStickersAndVoiceTogether() {
        val image = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGP4z8DwHwAFAAH/iZk9HQAAAABJRU5ErkJggg=="
        val comments = listOf("", "短评论").mapIndexed { index, text ->
            seedRootComment(index + 1).copy(
                content = """<p>$text</p><a class="comment_img" href="$image" data-width="120" data-height="120"></a><a class="comment_sticker" href="$image"></a>""",
                canUnfold = true,
                voiceInfo = DataHolder.Comment.CommentVoiceInfo(
                    voiceId = "folded-$index",
                    asrText = "合成语音转写 $index",
                    durationMs = 1_000,
                ),
            )
        }
        mockRootComments(
            urlPrefix = "https://www.zhihu.com/api/v4/comment_v5/answers/9001/root_comment",
            comments = comments,
        )
        setCommentScreen()
        comments.forEachIndexed { index, comment ->
            val expandTag = "comment_expand_${comment.id}"
            composeRule.onNodeWithTag(COMMENT_SCREEN_LIST_TAG).performScrollToNode(hasTestTag(expandTag))
            composeRule.onNodeWithTag("comment_image_${comment.id}").assertDoesNotExist()
            composeRule.onNodeWithContentDescription("评论贴纸").assertDoesNotExist()
            composeRule.onNodeWithTag("comment_voice_folded-$index").assertDoesNotExist()
            composeRule.onNodeWithTag("comment_voice_text_folded-$index").assertDoesNotExist()
            composeRule.onNodeWithTag(expandTag).performClick()
            composeRule.onNodeWithTag("comment_image_${comment.id}").assertExists()
            composeRule.onNodeWithContentDescription("评论贴纸").assertExists()
            composeRule.onNodeWithTag("comment_voice_folded-$index").assertExists()
            composeRule.onNodeWithTag("comment_voice_text_folded-$index").assertTextEquals("合成语音转写 $index")
            composeRule.onNodeWithTag(COMMENT_SCREEN_LIST_TAG).performScrollToNode(hasTestTag(expandTag))
            composeRule.onNodeWithTag(expandTag).performClick()
            composeRule.onNodeWithTag("comment_image_${comment.id}").assertDoesNotExist()
            composeRule.onNodeWithContentDescription("评论贴纸").assertDoesNotExist()
            composeRule.onNodeWithTag("comment_voice_folded-$index").assertDoesNotExist()
            composeRule.onNodeWithTag("comment_voice_text_folded-$index").assertDoesNotExist()
        }
    }

    @Test
    fun unreadableVoiceFileDoesNotLeaveThePlayerActive() {
        var player: com.github.zly2006.zhihu.ui.components.VoiceCommentPlayer? = null
        val missingFile = composeRule.activity.cacheDir.resolve("missing-voice-${java.util.UUID.randomUUID()}.m4a")
        assertTrue(!missingFile.exists())
        composeRule.setScreenContent {
            player = rememberVoiceCommentPlayer()
        }
        composeRule.runOnIdle {
            player!!.toggle(missingFile.absolutePath)
            assertEquals(null, player!!.playingUrl)
            player!!.stop()
        }
    }

    private fun pressSystemBack(closingTag: String? = null) {
        assertTrue(
            "系统必须接受返回操作",
            InstrumentationRegistry
                .getInstrumentation()
                .uiAutomation
                .performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK),
        )
        if (closingTag != null) {
            composeRule.waitUntil("等待系统返回关闭 $closingTag", 5_000) {
                composeRule.onAllNodesWithTag(closingTag).fetchSemanticsNodes().isEmpty()
            }
        }
    }

    private fun pressBackAfterImeHidden(target: SemanticsNodeInteraction) {
        awaitImeVisibility(target, visible = false)
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
    }

    /** 切换表情和关闭编辑器需要等目标窗口完成焦点与输入法交接。 */
    private fun awaitImeVisibility(target: SemanticsNodeInteraction, visible: Boolean) {
        val view = (target.fetchSemanticsNode().root as ViewRootForTest).view
        val ime = WindowInsetsCompat.Type.ime()
        val state = if (visible) "显示" else "收起"
        composeRule.waitUntil("等待目标评论窗口获得焦点且系统键盘$state", 5_000) {
            composeRule.runOnUiThread {
                val insets = ViewCompat.getRootWindowInsets(view) ?: return@runOnUiThread false
                view.hasWindowFocus() && insets.isVisible(ime) == visible && (visible || insets.getInsets(ime).bottom == 0)
            }
        }
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
                    contentId = "9001",
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
        val ROOT_ARTICLE_COMMENT_VIEW_MODEL_KEY = "article:${ROOT_ARTICLE.type}:${ROOT_ARTICLE.id}"
    }
}
