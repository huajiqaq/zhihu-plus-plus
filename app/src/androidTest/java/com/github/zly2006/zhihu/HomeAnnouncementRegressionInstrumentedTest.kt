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

import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.FeedDisplayItem
import com.github.zly2006.zhihu.data.RecommendationMode
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.data.toFeedDisplayItemNavDestinationJson
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.Search
import com.github.zly2006.zhihu.notification.HOME_NOTIFICATION_READ_UUIDS_PREFERENCE_KEY
import com.github.zly2006.zhihu.notification.ZHIHU_PLUS_PLUS_HOME_NOTIFICATIONS_URL
import com.github.zly2006.zhihu.test.MainActivityComposeRule
import com.github.zly2006.zhihu.test.ZhihuMockApi
import com.github.zly2006.zhihu.test.resetAppPreferences
import com.github.zly2006.zhihu.test.setScreenContent
import com.github.zly2006.zhihu.ui.DailyScreen
import com.github.zly2006.zhihu.ui.HOME_FEED_LIST_TAG
import com.github.zly2006.zhihu.ui.HomeScreen
import com.github.zly2006.zhihu.ui.PREFERENCE_NAME
import com.github.zly2006.zhihu.ui.ZHIHU_PLUS_AUTHOR_PINS_URL
import com.github.zly2006.zhihu.ui.homeAuthorPollAnnouncementTag
import com.github.zly2006.zhihu.ui.homeOnlineNotificationTag
import com.github.zly2006.zhihu.ui.homePinAnnouncementReadKey
import com.github.zly2006.zhihu.updater.UpdateManager
import com.github.zly2006.zhihu.viewmodel.feed.HomeFeedViewModel
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeAnnouncementRegressionInstrumentedTest {
    @get:Rule
    val composeRule: MainActivityComposeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        composeRule.setScreenContent {}
        composeRule.resetAppPreferences(forceMock = true)
        composeRule.activity.runOnUiThread {
            UpdateManager.updateState.value = UpdateManager.UpdateState.NoUpdate
            ViewModelProvider(composeRule.activity)[HomeFeedViewModel::class.java].apply {
                allData.clear()
                debugData.clear()
                displayItems.clear()
                addDisplayItems(
                    List(8) { index ->
                        FeedDisplayItem(
                            title = "离线条目 ${index.toString().padStart(2, '0')}",
                            summary = "这是第 ${index + 1} 条用于首页公告 instrument test 的离线摘要。",
                            details = "离线验证 · 固定假数据",
                            feed = null,
                            navDestinationJson =
                                Search(query = "fixture-$index").toFeedDisplayItemNavDestinationJson(),
                        )
                    },
                )
            }
        }
        composeRule.waitForIdle()
    }

    /**
     * 展开通知与动态后逐条关闭，验证未读项保留及恢复前台后的刷新，防止整组误标已读。
     */
    @Test
    fun authorAnnouncements_keepUnreadItemsWhenOneIsDismissed() {
        val firstPinId = 2064846692340470567L
        val secondPinId = 2064847476998279874L
        val firstPin = authorTopicPin(firstPinId, "第一条开发动态")
        val secondPin = authorTopicPin(secondPinId, "第二条开发动态")
        mockAuthorAnnouncements(firstPin, secondPin)
        composeRule.activity.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE).edit(commit = true) {
            putBoolean("showRefreshFab", false)
            putBoolean("loginForRecommendation", true)
            putBoolean("survey_feedback_done", true)
            putString("recommendationMode", RecommendationMode.WEB.key)
        }
        composeRule.setScreenContent {
            HomeScreen(
                scrollToTopTrigger = 0,
                innerPadding = PaddingValues(),
            )
        }
        composeRule.waitForIdle()

        composeRule.waitUntilRequestCount(1)
        composeRule.waitUntilHomeFeedTagExists("home_transactions_toggle")
        composeRule.onNodeWithTag("home_create_fab").assertIsDisplayed()
        composeRule.onNodeWithTag("home_transactions_toggle").performClick()
        composeRule.onNodeWithTag("home_create_fab").assertIsDisplayed()
        composeRule.waitUntilHomeFeedTagExists(homeAuthorPollAnnouncementTag(firstPinId))
        composeRule.waitUntilHomeFeedTagExists(homeAuthorPollAnnouncementTag(secondPinId))
        composeRule.waitUntilHomeFeedTagExists(homeAuthorPollAnnouncementTag(firstPinId))
        composeRule
            .onNode(hasText("关闭") and hasAnyAncestor(hasTestTag(homeAuthorPollAnnouncementTag(firstPinId))))
            .performClick()
        composeRule.waitUntil {
            composeRule.activity
                .getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)
                .getBoolean(homePinAnnouncementReadKey(firstPinId), false)
        }
        composeRule.onNodeWithTag(homeAuthorPollAnnouncementTag(firstPinId)).assertDoesNotExist()
        composeRule.onNodeWithTag(homeAuthorPollAnnouncementTag(secondPinId)).assertExists()
        assertEquals(
            false,
            composeRule.activity
                .getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)
                .getBoolean(homePinAnnouncementReadKey(secondPinId), false),
        )

        val thirdPinId = 2064848000000000000L
        mockAuthorAnnouncements(
            authorTopicPin(thirdPinId, "关于新版界面的调整<br><p>详细投票说明</p>"),
            secondPin,
            firstPin,
        )
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)

        composeRule.waitUntilRequestCount(2)
        composeRule.waitUntilHomeFeedTagExists(homeAuthorPollAnnouncementTag(secondPinId))
        composeRule.waitUntilHomeFeedTagExists(homeAuthorPollAnnouncementTag(thirdPinId))
        composeRule.onNodeWithTag(homeAuthorPollAnnouncementTag(firstPinId)).assertDoesNotExist()
        composeRule.onNodeWithText("关于新版界面的调整").assertExists()
        composeRule.onNodeWithText("详细投票说明").assertDoesNotExist()
    }

    @Test
    fun onlineNotifications_keepPerUuidReadStateAndRefreshOnResume() {
        val firstUuid = "da811fe3-858a-4655-afd4-a63024b74dbb"
        val secondUuid = "2c9fe00a-80f3-4b8f-89a1-b96dbe411791"
        val thirdUuid = "18e0690f-ecc8-4b22-afd6-cc5d1578b92d"
        val first = """{"uuid":"$firstUuid","expiresAt":4102444800000,"title":"第一条在线通知","accept":{"text":"查看","key":"open_answer","value":"123"},"dismiss":"关闭"}"""
        val second = """{"uuid":"$secondUuid","expiresAt":4102444800000,"title":"第二条在线通知","accept":{"text":"查看","key":"open_article","value":456},"dismiss":"关闭"}"""
        val third = """{"uuid":"$thirdUuid","expiresAt":4102444800000,"title":"第三条在线通知","dismiss":"关闭"}"""
        val duplicate = """{"uuid":"$firstUuid","expiresAt":4102444800000,"title":"不应重复显示","dismiss":"关闭"}"""
        val invalid = """{"uuid":"fe17a116-717f-4cab-8d63-06e73c0b473e","expiresAt":4102444800000,"title":"无效通知","accept":{"text":"执行","key":"future_action"},"dismiss":"关闭"}"""
        ZhihuMockApi.mockJsonPrefix(
            method = HttpMethod.Get,
            urlPrefix = ZHIHU_PLUS_PLUS_HOME_NOTIFICATIONS_URL,
            body = """{"notifications":[$first,$second,$duplicate,$invalid]}""",
        )
        mockAuthorAnnouncements()
        val preferences = composeRule.activity.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)
        preferences.edit(commit = true) {
            putBoolean("showRefreshFab", false)
            putBoolean("survey_feedback_done", true)
            putString("recommendationMode", RecommendationMode.WEB.key)
        }
        val navigator = composeRule.setScreenContent {
            HomeScreen(scrollToTopTrigger = 0, innerPadding = PaddingValues())
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            ZhihuMockApi.requestCount(HttpMethod.Get, ZHIHU_PLUS_PLUS_HOME_NOTIFICATIONS_URL) == 1
        }
        composeRule.waitUntilHomeFeedTagExists("home_transactions_toggle")
        composeRule.onNodeWithTag("home_transactions_toggle").performClick()
        composeRule.waitUntilHomeFeedTagExists(homeOnlineNotificationTag(firstUuid))
        composeRule.waitUntilHomeFeedTagExists(homeOnlineNotificationTag(secondUuid))
        composeRule.onNodeWithText("不应重复显示").assertDoesNotExist()
        composeRule.onNodeWithText("无效通知").assertDoesNotExist()
        composeRule.waitUntilHomeFeedTagExists(homeOnlineNotificationTag(firstUuid))
        composeRule.onNodeWithTag(homeOnlineNotificationTag(firstUuid) + "_accept").performClick()
        assertEquals(Article(type = ArticleType.Answer, id = 123), navigator.destinations.single())
        assertEquals(setOf(firstUuid), preferences.getStringSet(HOME_NOTIFICATION_READ_UUIDS_PREFERENCE_KEY, emptySet()))
        composeRule.onNodeWithTag(homeOnlineNotificationTag(firstUuid)).assertDoesNotExist()
        composeRule.onNodeWithTag(homeOnlineNotificationTag(secondUuid)).assertExists()

        ZhihuMockApi.mockJsonPrefix(
            method = HttpMethod.Get,
            urlPrefix = ZHIHU_PLUS_PLUS_HOME_NOTIFICATIONS_URL,
            body = """{"notifications":[$third,$second,$first]}""",
        )
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            ZhihuMockApi.requestCount(HttpMethod.Get, ZHIHU_PLUS_PLUS_HOME_NOTIFICATIONS_URL) == 2
        }
        composeRule.waitUntilHomeFeedTagExists(homeOnlineNotificationTag(thirdUuid))
        composeRule.waitUntilHomeFeedTagExists(homeOnlineNotificationTag(secondUuid))
        composeRule.onNodeWithTag(homeOnlineNotificationTag(firstUuid)).assertDoesNotExist()
        composeRule.onNodeWithTag(homeOnlineNotificationTag(secondUuid) + "_accept").performClick()
        assertEquals(Article(type = ArticleType.Article, id = 456), navigator.destinations.last())
        assertEquals(setOf(firstUuid, secondUuid), preferences.getStringSet(HOME_NOTIFICATION_READ_UUIDS_PREFERENCE_KEY, emptySet()))
        composeRule.onNodeWithTag(homeOnlineNotificationTag(secondUuid)).assertDoesNotExist()
        composeRule.onNodeWithTag(homeOnlineNotificationTag(thirdUuid)).assertExists()
    }

    @Test
    fun dailyFailureRetriesThroughTheActualPageAndShowsSuccessfulEmptyState() {
        ZhihuMockApi.mockJsonPrefix(
            method = HttpMethod.Get,
            urlPrefix = "https://news-at.zhihu.com/api/4/stories",
            body = "{}",
            status = HttpStatusCode.InternalServerError,
        )
        composeRule.setScreenContent { DailyScreen() }
        composeRule.onNodeWithTag("daily_screen_error").assertIsDisplayed()
        val failedRequests = ZhihuMockApi.requestCount(urlSubstring = "/api/4/stories")
        ZhihuMockApi.mockJsonPrefix(
            method = HttpMethod.Get,
            urlPrefix = "https://news-at.zhihu.com/api/4/stories",
            body = """{"date":"20260101","stories":[]}""",
        )
        composeRule.onNodeWithText("重试").performClick()
        composeRule.waitForIdle()
        assertEquals(failedRequests + 1, ZhihuMockApi.requestCount(urlSubstring = "/api/4/stories"))
        composeRule.onNodeWithTag("daily_screen_empty").assertIsDisplayed()
        composeRule.onAllNodesWithText("日报加载失败").assertCountEquals(0)
    }

    private fun MainActivityComposeRule.waitUntilHomeFeedTagExists(tag: String) {
        waitUntil("等待首页信息流出现标签 $tag", timeoutMillis = 5_000) {
            runCatching {
                onNodeWithTag(HOME_FEED_LIST_TAG).performScrollToNode(hasTestTag(tag))
                true
            }.getOrDefault(false)
        }
    }

    private fun MainActivityComposeRule.waitUntilRequestCount(count: Int) {
        waitUntil("等待 $count 次作者公告请求", timeoutMillis = 5_000) {
            ZhihuMockApi.requestCount(HttpMethod.Get, ZHIHU_PLUS_AUTHOR_PINS_URL) == count
        }
    }

    private fun authorTopicPin(
        pinId: Long,
        title: String,
    ) = DataHolder.Pin(
        id = pinId.toString(),
        url = "https://www.zhihu.com/pin/$pinId",
        author = DataHolder.Author(
            avatarUrl = "",
            gender = 0,
            headline = "",
            id = "zhihu-plus-author",
            isAdvertiser = false,
            isOrg = false,
            name = "知乎++作者",
            type = "people",
            url = "https://www.zhihu.com/people/scanmenge",
            urlToken = "scanmenge",
            userType = "people",
        ),
        excerptTitle = title,
        created = System.currentTimeMillis() / 1_000,
        topics = listOf(
            DataHolder.Topic(
                id = "2064846813258109867",
                type = "topic",
                url = "zhihu://topic/2064846813258109867/pin20",
                name = "zhihuplusplus",
            ),
        ),
    )

    private fun mockAuthorAnnouncements(vararg pins: DataHolder.Pin) {
        ZhihuMockApi.mockJson(
            method = HttpMethod.Get,
            url = ZHIHU_PLUS_AUTHOR_PINS_URL,
            body = """{"data":${ZhihuJson.json.encodeToString(pins)}}""",
        )
    }
}
