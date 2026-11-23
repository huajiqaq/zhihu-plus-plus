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

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.navigation.Person
import com.github.zly2006.zhihu.navigation.Pin
import com.github.zly2006.zhihu.test.InstrumentedTestEnvironment
import com.github.zly2006.zhihu.test.MainActivityComposeRule
import com.github.zly2006.zhihu.test.ZhihuMockApi
import com.github.zly2006.zhihu.test.mockRootComments
import com.github.zly2006.zhihu.test.resetAppPreferences
import com.github.zly2006.zhihu.test.setScreenContent
import com.github.zly2006.zhihu.ui.COMMENT_COMPOSER_ENTRY_TAG
import com.github.zly2006.zhihu.ui.COMMENT_INPUT_TAG
import com.github.zly2006.zhihu.ui.COMMENT_SCREEN_LIST_TAG
import com.github.zly2006.zhihu.ui.PIN_SCREEN_AUTHOR_FOLLOW_ERROR_TAG
import com.github.zly2006.zhihu.ui.PIN_SCREEN_AUTHOR_FOLLOW_TAG
import com.github.zly2006.zhihu.ui.PIN_SCREEN_AUTHOR_TAG
import com.github.zly2006.zhihu.ui.PIN_SCREEN_IP_INFO_TAG
import com.github.zly2006.zhihu.ui.PIN_SCREEN_LINK_CARD_TAG
import com.github.zly2006.zhihu.ui.PIN_SCREEN_TIMESTAMP_TAG
import com.github.zly2006.zhihu.ui.PinScreen
import com.github.zly2006.zhihu.ui.components.CommentScreenComponent
import com.github.zly2006.zhihu.util.formatFullDateTime
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.encodeToString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PinScreenInstrumentedTest {
    @get:Rule
    val composeRule: MainActivityComposeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        composeRule.resetAppPreferences(forceMock = true)
    }

    @After
    fun tearDown() {
        if (!InstrumentedTestEnvironment.isMockMode()) composeRule.resetAppPreferences()
    }

    /**
     * 关闭并重开评论面板须保留未发送草稿；行为依据：https://github.com/zly2006/zhihu-plus-plus/issues/534
     */
    @Test
    fun commentDraftSurvivesSheetDismissAndReopen() {
        /*
         * 预期行为：
         * 1. 评论面板关闭后，输入的长草稿应继续保存。
         * 2. 重新打开同一内容的评论时，必须恢复完整的未发送草稿。
         */
        mockRootComments("https://www.zhihu.com/api/v4/comment_v5/pins/101/root_comment")
        val showComments = mutableStateOf(true)
        composeRule.setScreenContent {
            CommentScreenComponent(
                showComments = showComments.value,
                onDismiss = { showComments.value = false },
                content = Pin(101),
            )
        }
        val draft = "这是一段尚未发送的长评论，用来验证关闭评论区后重新打开仍然保留全部内容。".repeat(8)

        composeRule.waitUntilTagExists(COMMENT_COMPOSER_ENTRY_TAG)
        composeRule.onNodeWithTag(COMMENT_COMPOSER_ENTRY_TAG).performClick()
        composeRule.waitUntilTagExists(COMMENT_INPUT_TAG)
        composeRule.onNodeWithTag(COMMENT_INPUT_TAG).performTextInput(draft)
        composeRule.runOnIdle {
            showComments.value = false
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(COMMENT_SCREEN_LIST_TAG).fetchSemanticsNodes().isEmpty()
        }

        composeRule.runOnIdle {
            showComments.value = true
        }
        composeRule.waitUntilTagExists(COMMENT_COMPOSER_ENTRY_TAG)
        composeRule.onNodeWithTag(COMMENT_COMPOSER_ENTRY_TAG).performClick()
        composeRule.waitUntilTagExists(COMMENT_INPUT_TAG)
        composeRule.onNodeWithTag(COMMENT_INPUT_TAG).assertTextEquals(draft)
    }

    @Test
    fun authorNavigationAndFollowAreSeparateAndTimestampsFollowContent() {
        val pin = seededPinContent().copy(updated = 1_713_460_389L, ipInfo = "IP 属地合成属地")
        mockPinDetailBody(101, ZhihuJson.json.encodeToString(pin))
        val followUrl = "https://www.zhihu.com/api/v4/members/${pin.author.urlToken}/followers"
        ZhihuMockApi.mockJson(HttpMethod.Post, followUrl, "{}")
        ZhihuMockApi.mockJson(HttpMethod.Delete, followUrl, "{}", HttpStatusCode.ServiceUnavailable)
        val navigator = composeRule.setScreenContent { PinScreen(Pin(101)) }
        composeRule.waitUntilTagExists(PIN_SCREEN_AUTHOR_FOLLOW_TAG)
        composeRule.onNodeWithText("想法标题 <原文>").assertIsDisplayed()
        val author = composeRule.onNodeWithTag(PIN_SCREEN_AUTHOR_TAG).assertHasClickAction()
        val follow = composeRule.onNodeWithTag(PIN_SCREEN_AUTHOR_FOLLOW_TAG).assertHasClickAction()
        val ip = composeRule.onNodeWithTag(PIN_SCREEN_IP_INFO_TAG).assertTextEquals("IP 属地合成属地").getUnclippedBoundsInRoot()
        val initialBody = composeRule.onNodeWithText("PinScreen", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("IP 应在作者下、正文前", author.getUnclippedBoundsInRoot().bottom <= ip.top && ip.bottom <= initialBody.top)
        assertEquals("IP 与作者区左缘对齐", author.getUnclippedBoundsInRoot().left, ip.left)
        assertTrue("关注按钮不能并入作者导航区域", author.getUnclippedBoundsInRoot().right <= follow.getUnclippedBoundsInRoot().left)
        author.performClick()
        assertEquals(listOf(Person(pin.author.id, pin.author.urlToken, pin.author.name)), navigator.destinations)
        assertEquals(0, ZhihuMockApi.requestCount(urlSubstring = "/followers"))
        navigator.reset()

        follow.assertTextEquals("关注").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("已关注").fetchSemanticsNodes().isNotEmpty()
        }
        follow.assertTextEquals("已关注")
        assertEquals(1, ZhihuMockApi.requestCount(HttpMethod.Post, followUrl))
        assertTrue(navigator.destinations.isEmpty())
        follow.performClick()
        composeRule.waitUntilTagExists(PIN_SCREEN_AUTHOR_FOLLOW_ERROR_TAG)
        follow.assertTextEquals("已关注")
        composeRule.onNodeWithTag(PIN_SCREEN_AUTHOR_FOLLOW_ERROR_TAG).assertTextEquals("取消关注失败，请重试")
        ZhihuMockApi.mockJson(HttpMethod.Delete, followUrl, "{}")
        follow.performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("关注").fetchSemanticsNodes().isNotEmpty()
        }
        follow.assertTextEquals("关注")
        assertTrue(navigator.destinations.isEmpty())

        composeRule.onNodeWithTag(PIN_SCREEN_TIMESTAMP_TAG).performScrollTo().assertIsDisplayed()
        val body = composeRule.onNodeWithText("PinScreen", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val card = composeRule.onNodeWithTag(PIN_SCREEN_LINK_CARD_TAG).getUnclippedBoundsInRoot()
        val topic = composeRule.onNodeWithText("# 离线话题一").getUnclippedBoundsInRoot()
        val timestamp = composeRule.onNodeWithTag(PIN_SCREEN_TIMESTAMP_TAG).getUnclippedBoundsInRoot()
        assertTrue("正文、关联卡片、话题、时间应依序排列", body.bottom <= card.top && card.bottom <= topic.top && topic.bottom <= timestamp.top)
        assertEquals("时间与内容整体左缘对齐", card.left, timestamp.left)
        composeRule.onNodeWithText("发布于 ${formatFullDateTime(pin.created)}").assertExists()
        composeRule.onNodeWithText("编辑于 ${formatFullDateTime(pin.updated)}").assertExists()
    }

    @Test
    fun missingTimestampsAndSelfAuthorDoNotExposePlaceholderOrFollow() {
        val pin = seededPinContent().copy(created = 0, updated = 0, selfCreate = true)
        mockPinDetailBody(102, ZhihuJson.json.encodeToString(pin.copy(id = "102")))
        composeRule.setScreenContent { PinScreen(Pin(102)) }
        composeRule.waitUntilTagExists(PIN_SCREEN_AUTHOR_TAG)
        composeRule.onNodeWithTag(PIN_SCREEN_TIMESTAMP_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(PIN_SCREEN_IP_INFO_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(PIN_SCREEN_AUTHOR_FOLLOW_TAG).assertDoesNotExist()
        composeRule.onNodeWithText("1970", substring = true).assertDoesNotExist()
        composeRule.onNodeWithTag(PIN_SCREEN_AUTHOR_TAG).assertHasClickAction()
    }

    private fun mockPinDetailBody(
        pinId: Long,
        body: String,
        beforeRespond: suspend () -> Unit = {},
    ) {
        ZhihuMockApi.mockJson(
            method = HttpMethod.Get,
            url = "https://api.zhihu.com/v2/pins/$pinId",
            body = body,
            beforeRespond = beforeRespond,
        )
    }

    private fun MainActivityComposeRule.waitUntilTagExists(tag: String) {
        waitUntil("等待标签为 $tag 的节点", timeoutMillis = 5_000) {
            onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun seededPinContent(): DataHolder.Pin = DataHolder.Pin(
        id = "101",
        url = "https://www.zhihu.com/pin/101",
        author = DataHolder.Author(
            avatarUrl = "",
            gender = 0,
            headline = "离线作者简介",
            id = "pin-author-id",
            isAdvertiser = false,
            isOrg = false,
            name = "离线想法作者",
            type = "people",
            url = "https://www.zhihu.com/people/pin-author-token",
            urlToken = "pin-author-token",
            userType = "people",
            badgeV2 = DataHolder.BadgeV2(
                title = "离线优秀答主",
                icon = DataHolder.ZH_PLUS_AUTHOR_BADGE_ICON,
                detailBadges = listOf(
                    DataHolder.BadgeV2.Badge(
                        type = "best",
                        detailType = "best_answerer",
                        title = "优秀答主",
                        description = "离线优秀答主",
                        icon = DataHolder.ZH_PLUS_AUTHOR_BADGE_ICON,
                        badgeStatus = "passed",
                    ),
                ),
            ),
        ),
        content = listOf(
            DataHolder.Pin.ContentText(
                content = "这是 PinScreen instrumented test 的离线正文。",
                title = "想法标题 <原文>",
            ),
            DataHolder.Pin.ContentLinkCard(
                dataContentId = "987654321",
                dataContentType = "question",
                url = "https://www.zhihu.com/question/987654321",
            ),
        ),
        contentHtml = "<p>这是 <b>PinScreen</b> instrumented test 的离线正文。</p>",
        likeCount = 9,
        commentCount = 3,
        created = 1_713_456_789L,
        likers = listOf(
            DataHolder.Author(
                avatarUrl = "",
                gender = 0,
                headline = "",
                id = "pin-liker-id",
                isAdvertiser = false,
                isOrg = false,
                name = "离线点赞者",
                type = "people",
                url = "https://www.zhihu.com/people/pin-liker-token",
                urlToken = "pin-liker-token",
                userType = "people",
            ),
        ),
        topics = listOf(
            DataHolder.Topic(
                id = "topic-1",
                type = "topic",
                url = "https://www.zhihu.com/topic/topic-1",
                name = "离线话题一",
            ),
        ),
    )
}
