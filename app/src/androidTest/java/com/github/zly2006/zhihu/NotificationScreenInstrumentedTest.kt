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
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.zly2006.zhihu.data.MOBILE_NOTIFICATION_MESSAGE_URL
import com.github.zly2006.zhihu.data.MobileNotificationAuthor
import com.github.zly2006.zhihu.data.MobileNotificationContent
import com.github.zly2006.zhihu.data.MobileNotificationHead
import com.github.zly2006.zhihu.data.MobileNotificationTargetSource
import com.github.zly2006.zhihu.data.MobileNotificationTimelineItem
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.CommentHolder
import com.github.zly2006.zhihu.navigation.Notification
import com.github.zly2006.zhihu.navigation.Person
import com.github.zly2006.zhihu.navigation.Pin
import com.github.zly2006.zhihu.test.InstrumentedTestEnvironment
import com.github.zly2006.zhihu.test.MainActivityComposeRule
import com.github.zly2006.zhihu.test.RecordingNavigator
import com.github.zly2006.zhihu.test.ZhihuMockApi
import com.github.zly2006.zhihu.test.resetAppPreferences
import com.github.zly2006.zhihu.test.setScreenContent
import com.github.zly2006.zhihu.test.setZhihuMainContent
import com.github.zly2006.zhihu.ui.NotificationItemView
import com.github.zly2006.zhihu.ui.NotificationScreen
import com.github.zly2006.zhihu.ui.PEOPLE_SCREEN_BACK_BUTTON_TAG
import com.github.zly2006.zhihu.ui.PRIVATE_MESSAGE_BACK_BUTTON_TAG
import com.github.zly2006.zhihu.viewmodel.MobileNotificationCategory
import com.github.zly2006.zhihu.viewmodel.NotificationViewModel
import io.ktor.http.HttpMethod
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationScreenInstrumentedTest {
    @get:Rule
    val composeRule: MainActivityComposeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        composeRule.resetAppPreferences()
    }

    @After
    fun tearDown() {
        if (!InstrumentedTestEnvironment.isMockMode()) composeRule.resetAppPreferences()
    }

    /**
     * 点赞分类有 210 条未读时，分类按钮自身应显示封顶的未读角标。
     * 后代节点匹配排除页面其他位置的计数；行为依据：https://github.com/zly2006/zhihu-plus-plus/issues/490
     */
    @Test
    fun notificationScreen_showsCategoryUnreadCountBadge() {
        /*
         * 预期行为：
         * 1. 在页面 ViewModel 中预置各分类的未读数量。
         * 2. 顶部分类行应在对应分类上显示可见的未读角标。
         * 3. 角标应属于分类按钮，而不是工具栏中的独立计数。
         */
        composeRule.setScreenContent {
            NotificationScreen()
        }
        composeRule.seedNotificationViewModel(
            unreadCounts = mapOf(MobileNotificationCategory.Like to 210),
        )

        composeRule
            .onNode(
                hasText("99+") and hasAnyAncestor(hasTestTag("notification_category_like")),
                useUnmergedTree = true,
            ).assertIsDisplayed()
    }

    /**
     * 评论、回复和点赞通知须携带评论锚点导航；行为依据：https://github.com/zly2006/zhihu-plus-plus/issues/569
     */
    @Test
    fun notificationScreen_fourObservedCommentActionsNavigateWithCommentAnchors() {
        val notifications = listOf(
            notificationFixture(
                id = "comment-content",
                title = "别人评论我的内容",
                subTitle = "评论了你的回答",
                targetLink = "zhihu://comment/list/answer/2?anchor_comment_id=3&is_child=false",
            ),
            notificationFixture(
                id = "reply-comment",
                title = "别人回复我的评论",
                subTitle = "回复了想法下你的评论",
                targetLink = "zhihu://comment/list/pin/4?anchor_comment_id=5&is_child=true",
            ),
            notificationFixture(
                id = "like-root-comment",
                title = "别人点赞我的根评论",
                subTitle = "喜欢了你的评论",
                targetLink = "zhihu://comment/list/article/6?anchor_comment_id=7&is_child=false",
            ),
            notificationFixture(
                id = "like-child-comment",
                title = "别人点赞我的楼中楼评论",
                subTitle = "喜欢了你的评论",
                targetLink = "zhihu://comment/list/pin/8?anchor_comment_id=9&is_child=false",
            ),
        )
        val scrollGuardNotifications = List(8) { index ->
            notificationFixture(
                id = "scroll-guard-$index",
                title = "占位通知 $index",
                subTitle = "测试占位",
            )
        }
        val recordingNavigator = setNotificationScreenContent(notifications + scrollGuardNotifications)
        val notificationList = composeRule.onNode(hasScrollAction())

        notifications.forEach { notification ->
            val title = notification.content!!.title
            notificationList.performScrollToNode(hasText(title))
            composeRule.onNodeWithText(title).assertIsDisplayed().performClick()
        }

        assertEquals(4, recordingNavigator.destinations.size)
        val commentedAnswerHolder = recordingNavigator.destinations[0] as CommentHolder
        assertEquals("3", commentedAnswerHolder.commentId)
        val commentedAnswer = commentedAnswerHolder.article as Article
        assertEquals(ArticleType.Answer, commentedAnswer.type)
        assertEquals(2L, commentedAnswer.id)
        val repliedPinHolder = recordingNavigator.destinations[1] as CommentHolder
        assertEquals("5", repliedPinHolder.commentId)
        val repliedPin = repliedPinHolder.article as Pin
        assertEquals(4L, repliedPin.id)
        val likedArticleCommentHolder = recordingNavigator.destinations[2] as CommentHolder
        assertEquals("7", likedArticleCommentHolder.commentId)
        val likedArticleComment = likedArticleCommentHolder.article as Article
        assertEquals(ArticleType.Article, likedArticleComment.type)
        assertEquals(6L, likedArticleComment.id)
        val likedChildPinCommentHolder = recordingNavigator.destinations[3] as CommentHolder
        assertEquals("9", likedChildPinCommentHolder.commentId)
        val likedChildPinComment = likedChildPinCommentHolder.article as Pin
        assertEquals(8L, likedChildPinComment.id)
    }

    /** 真实头像子点击不能冒泡为整行导航；摘要与引用评论沿用官方 11.12.0 字段，并保留评论锚点。 */
    @Test
    fun notificationAvatarAndBodyKeepIndependentProfileCommentAndChatNavigation() {
        composeRule.resetAppPreferences(forceMock = true)
        val notification = notificationFixture().copy(
            head = MobileNotificationHead(
                avatarUrl = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGP4z8DwHwAFAAH/iZk9HQAAAABJRU5ErkJggg==",
                author = MobileNotificationAuthor(id = "actor-id", urlToken = "actor-token", name = "通知作者"),
            ),
            content = MobileNotificationContent(
                title = "通知作者",
                subTitle = "Someone replied",
                abstractText = "<p>可见摘要 [微笑]</p>",
                subText = "<p>引用评论</p>",
                targetLink = "zhihu://comment/list/answer/2?anchor_comment_id=3&is_child=false",
                subTargetLink = "zhihu://comment/list/answer/2?anchor_comment_id=8",
            ),
            targetSource = MobileNotificationTargetSource(text = "回答来源", targetLink = "zhihu://answers/2"),
        )
        val displayed = mutableStateOf(notification)
        val navigator = RecordingNavigator()
        composeRule.setScreenContent(navigator) {
            NotificationItemView(displayed.value, entryName = "comment", showUnreadBadge = true)
        }
        composeRule.onNodeWithText("Someone replied").assertIsDisplayed()
        composeRule.onNodeWithText("引用评论").assertIsDisplayed()
        composeRule.onNodeWithTag("notification_avatar_${notification.stableId}").assertIsDisplayed().performClick()
        assertEquals(1, navigator.destinations.size)
        assertEquals("actor-token", (navigator.destinations.single() as Person).urlToken)
        composeRule.onNodeWithText("可见摘要 [微笑]", useUnmergedTree = true).assertIsDisplayed().performClick()
        assertEquals(2, navigator.destinations.size)
        assertEquals("3", (navigator.destinations.last() as CommentHolder).commentId)
        composeRule.onNodeWithText("引用评论").performClick()
        composeRule.onNodeWithText("回答来源").performClick()
        assertEquals("8", (navigator.destinations[2] as CommentHolder).commentId)
        assertEquals(2L, (navigator.destinations[3] as Article).id)
        composeRule.runOnIdle {
            displayed.value = notification.copy(
                content = notification.content!!.copy(isDeleted = true, isSubDeleted = true),
                targetSource = notification.targetSource!!.copy(isDeleted = true),
            )
        }
        val deleted = composeRule.onAllNodesWithText("该内容已被删除", useUnmergedTree = true)
        deleted.assertCountEquals(3)
        val beforeDeletedTaps = navigator.destinations.toList()
        repeat(3) { index ->
            deleted[index].performTouchInput { click() }
            composeRule.runOnIdle {
                assertEquals("点第 ${index + 1} 个删除占位不应触发邻近区域", beforeDeletedTaps, navigator.destinations)
            }
        }

        // 仅覆盖本用例的通知路由，保留其他套件的默认空页与真实数据模式。
        composeRule.resetAppPreferences(forceMock = true)
        ZhihuMockApi.mockJson(
            HttpMethod.Get,
            "$MOBILE_NOTIFICATION_MESSAGE_URL?limit=20",
            """{"data":[{"id":"chat","type":"message","head":{"author":{"id":"actor-id","url_token":"actor-token","name":"通知作者"},"target_link":"https://www.zhihu.com/inbox/peer-id"},"content":{"title":"测试私信","abstract_text":"私信摘要","target_link":"https://www.zhihu.com/inbox/peer-id"}}],"paging":{"is_end":true,"next":""}}""",
        )
        ZhihuMockApi.mockJson(
            HttpMethod.Get,
            "https://api.zhihu.com/messages/user/peer-id",
            """{"id":"peer-id"}""",
        )
        ZhihuMockApi.mockJson(
            HttpMethod.Get,
            "https://api.zhihu.com/messages?limit=20&sender_id=peer-id",
            """{"data":[{"id":"peer-reply","sender":{"id":"peer-id"},"content":"会话专属回复"}],"paging":{"is_end":true,"next":""}}""",
        )
        composeRule.setZhihuMainContent()
        val mainHost = composeRule.activity.navHost
        val notificationEntryId = composeRule.runOnIdle {
            mainHost.open(Notification)
            checkNotNull(mainHost.topEntryId)
        }
        composeRule.waitUntil("通知会话头像未出现", timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasTestTag("notification_avatar_chat")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("notification_avatar_chat").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("个人主页").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals("actor-token", (mainHost.top as Person).urlToken) }
        composeRule.onNodeWithTag(PEOPLE_SCREEN_BACK_BUTTON_TAG).performClick()
        composeRule.onNodeWithText("消息").assertIsDisplayed()
        composeRule.onNodeWithText("私信摘要").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag(PRIVATE_MESSAGE_BACK_BUTTON_TAG).assertIsDisplayed()
        composeRule
            .onNode(
                hasText("测试私信") and hasAnyAncestor(hasAnySibling(hasTestTag(PRIVATE_MESSAGE_BACK_BUTTON_TAG))),
                useUnmergedTree = true,
            ).assertIsDisplayed()
        // 宽屏私信使用详情栈；只有正确的 peer-id 能加载并显示这条会话正文。
        val reply = composeRule.onNode(
            hasText("会话专属回复") and hasAnyAncestor(hasTestTag("private_message_peer-reply")),
            useUnmergedTree = true,
        )
        composeRule.waitUntil("未显示 peer-id 的私信正文", timeoutMillis = 5_000) { reply.isDisplayed() }
        composeRule.onNodeWithTag(PRIVATE_MESSAGE_BACK_BUTTON_TAG).performClick()
        composeRule.waitUntil("返回后私信详情必须关闭", timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasTestTag(PRIVATE_MESSAGE_BACK_BUTTON_TAG)).fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithText("消息").assertIsDisplayed()
        composeRule.onNodeWithTag("notification_avatar_chat").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(notificationEntryId, mainHost.topEntryId) }
    }

    private fun setNotificationScreenContent(
        notifications: List<MobileNotificationTimelineItem> = listOf(notificationFixture()),
    ): RecordingNavigator {
        val recordingNavigator = composeRule.setScreenContent {
            NotificationScreen()
        }
        composeRule.seedNotificationViewModel(notifications = notifications)
        return recordingNavigator
    }

    private fun notificationFixture(
        id: String = "local-notification",
        title: String = "测试用户 回复了回答下你的评论",
        subTitle: String = "评论和回复",
        targetLink: String = "zhihu://comment/list/answer/2?anchor_comment_id=3&is_child=false",
    ) = MobileNotificationTimelineItem(
        id = id,
        type = "aggregate_notification",
        isRead = true,
        created = 1_713_420_000L,
        content = MobileNotificationContent(
            title = title,
            subTitle = subTitle,
            targetLink = targetLink,
        ),
    )

    private fun MainActivityComposeRule.seedNotificationViewModel(
        unreadCounts: Map<MobileNotificationCategory, Int> = emptyMap(),
        notifications: List<MobileNotificationTimelineItem> = listOf(notificationFixture()),
    ) {
        waitUntil(
            "通知页面未完成首次刷新",
            timeoutMillis = 5_000,
        ) {
            ViewModelProvider(activity)[NotificationViewModel::class.java].let { viewModel ->
                !viewModel.isLoading && viewModel.isEnd
            }
        }
        activity.runOnUiThread {
            val viewModel = ViewModelProvider(activity)[NotificationViewModel::class.java]
            viewModel.allData.clear()
            viewModel.allData += notifications
            if (unreadCounts.isNotEmpty()) {
                viewModel.categoryUnreadCounts.putAll(unreadCounts)
            }
        }
        waitForIdle()
    }
}
