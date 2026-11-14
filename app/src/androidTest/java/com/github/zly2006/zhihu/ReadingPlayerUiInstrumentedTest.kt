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
import android.content.Intent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.zly2006.zhihu.data.CommonFeed
import com.github.zly2006.zhihu.data.Feed
import com.github.zly2006.zhihu.data.RecommendationMode
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.navigation.Account
import com.github.zly2006.zhihu.navigation.AppNavHost
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.Home
import com.github.zly2006.zhihu.navigation.Search
import com.github.zly2006.zhihu.reading.AndroidReadingPlayerBridge
import com.github.zly2006.zhihu.reading.ContentReadingService
import com.github.zly2006.zhihu.reading.ReadingContentType
import com.github.zly2006.zhihu.reading.ReadingPlaybackStatus
import com.github.zly2006.zhihu.reading.ReadingPlayerState
import com.github.zly2006.zhihu.reading.ReadingQueueItem
import com.github.zly2006.zhihu.test.MainActivityComposeRule
import com.github.zly2006.zhihu.test.ZhihuMockApi
import com.github.zly2006.zhihu.test.resetAppPreferences
import com.github.zly2006.zhihu.test.setScreenContent
import com.github.zly2006.zhihu.test.setZhihuMainContent
import com.github.zly2006.zhihu.ui.HOME_FEED_LIST_TAG
import com.github.zly2006.zhihu.ui.HOME_REFRESH_BUTTON_TAG
import com.github.zly2006.zhihu.ui.HOME_SEARCH_BUTTON_TAG
import com.github.zly2006.zhihu.ui.PREFERENCE_NAME
import com.github.zly2006.zhihu.ui.components.READING_PLAYER_BAR_TAG
import com.github.zly2006.zhihu.ui.components.READING_PLAYER_COMPACT_TAG
import com.github.zly2006.zhihu.ui.components.READING_PLAYER_QUEUE_TAG
import com.github.zly2006.zhihu.ui.components.READING_PLAYER_STOP_TAG
import com.github.zly2006.zhihu.ui.components.READING_QUEUE_SETTINGS_TAG
import com.github.zly2006.zhihu.ui.components.READING_QUEUE_SHEET_TAG
import com.github.zly2006.zhihu.ui.subscreens.BOTTOM_BAR_ITEMS_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.subscreens.START_DESTINATION_PREFERENCE_KEY
import io.ktor.http.HttpMethod
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReadingPlayerUiInstrumentedTest {
    @get:Rule
    val composeRule: MainActivityComposeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        composeRule.resetAppPreferences()
        composeRule.activity
            .getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)
            .edit(commit = true) {
                putString(START_DESTINATION_PREFERENCE_KEY, Home.name)
                putStringSet(BOTTOM_BAR_ITEMS_PREFERENCE_KEY, linkedSetOf(Home.name, Account.name))
                putBoolean("duo3_nav_style", false)
                putBoolean("autoHideBottomBar", false)
                putBoolean("showRefreshFab", true)
            }
        AndroidReadingPlayerBridge.publish(PLAYER_STATE)
    }

    @After
    fun tearDown() {
        composeRule.activity.stopService(Intent(composeRule.activity, ContentReadingService::class.java))
        AndroidReadingPlayerBridge.publish(ReadingPlayerState())
    }

    /**
     * Contract: https://github.com/zly2006/zhihu-plus-plus/issues/550
     * Introduced by: https://github.com/zly2006/zhihu-plus-plus/pull/552
     */
    @Test
    fun outsideDetailPlayerCompactsForBackgroundGestureWithoutSwallowingNavigation() {
        composeRule.setScreenContent {}
        composeRule.activity.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE).edit(commit = true) {
            putString("recommendationMode", RecommendationMode.WEB.key)
        }
        val sample = ZhihuJson.decodeJson<CommonFeed>(
            ZhihuJson.json
                .parseToJsonElement(
                    InstrumentationRegistry
                        .getInstrumentation()
                        .context.assets
                        .open("home-follow/moments-feed-items.json")
                        .bufferedReader()
                        .use { it.readText() },
                ).jsonArray
                .last(),
        )
        assertTrue("合成主页夹具必须是回答", sample.target is Feed.AnswerTarget)
        val target = sample.target as Feed.AnswerTarget
        // 曝光记录跨进程保留；每次夹具使用新内容身份，不能靠清数据库绕过真实过滤。
        val contentIdBase = java.util.UUID
            .randomUUID()
            .mostSignificantBits and 0x3fffffffffffffffL
        val feedItems: List<Feed> = List(8) { index ->
            sample.copy(
                id = "reading-feed-$contentIdBase-$index",
                target = target.copy(
                    id = contentIdBase + index,
                    url = "https://www.zhihu.com/question/${contentIdBase + 16 + index}/answer/${contentIdBase + index}",
                    question = target.question.copy(
                        id = contentIdBase + 16 + index,
                        url = "https://www.zhihu.com/question/${contentIdBase + 16 + index}",
                        _title = "朗读背景条目 $index",
                    ),
                ),
            )
        }
        ZhihuMockApi.mockJsonPrefix(
            HttpMethod.Get,
            "https://api.zhihu.com/topstory/recommend",
            """{"data":${ZhihuJson.json.encodeToString(feedItems)},"paging":{"is_end":true,"next":""}}""",
        )
        composeRule.setZhihuMainContent()

        composeRule.waitForTag(READING_PLAYER_COMPACT_TAG)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasText("朗读背景条目 0")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(HOME_FEED_LIST_TAG).performScrollToNode(hasText("朗读背景条目 4"))
        composeRule.onNodeWithText("朗读背景条目 4").assertIsDisplayed()
        assertTrue(
            composeRule
                .onNodeWithTag(HOME_FEED_LIST_TAG)
                .fetchSemanticsNode()
                .config[SemanticsProperties.VerticalScrollAxisRange]
                .value() > 0f,
        )
        composeRule.waitForTag(HOME_REFRESH_BUTTON_TAG)
        val compactBounds = composeRule.onNodeWithTag(READING_PLAYER_COMPACT_TAG).fetchSemanticsNode().boundsInRoot
        val refreshBounds = composeRule.onNodeWithTag(HOME_REFRESH_BUTTON_TAG).fetchSemanticsNode().boundsInRoot
        val feedBounds = composeRule.onNodeWithTag(HOME_FEED_LIST_TAG).fetchSemanticsNode().boundsInRoot
        val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("compact=$compactBounds root=$rootBounds", compactBounds.center.x < rootBounds.center.x)
        assertTrue("refresh=$refreshBounds feed=$feedBounds", refreshBounds.center.x > feedBounds.center.x)
        assertFalse(
            "compact=$compactBounds refresh=$refreshBounds",
            compactBounds.left < refreshBounds.right &&
                compactBounds.right > refreshBounds.left &&
                compactBounds.top < refreshBounds.bottom &&
                compactBounds.bottom > refreshBounds.top,
        )
        composeRule.onNodeWithTag(READING_PLAYER_COMPACT_TAG).assertIsDisplayed().performTouchInput { click() }
        composeRule.waitForTag(READING_PLAYER_BAR_TAG)
        composeRule.onNodeWithTag(READING_PLAYER_STOP_TAG).assertIsDisplayed()

        composeRule.onRoot().performTouchInput {
            swipe(
                start = Offset(feedBounds.center.x, feedBounds.top + feedBounds.height * 0.65f),
                end = Offset(feedBounds.center.x, feedBounds.top + feedBounds.height * 0.45f),
                durationMillis = 400,
            )
        }

        composeRule.waitForTag(READING_PLAYER_COMPACT_TAG)
        composeRule.onNodeWithTag(READING_PLAYER_BAR_TAG).assertDoesNotExist()
        composeRule.onNodeWithText("1/2").assertIsDisplayed()

        composeRule.onNodeWithTag(READING_PLAYER_COMPACT_TAG).performClick()
        composeRule.waitForTag(READING_PLAYER_BAR_TAG)
        composeRule.onNodeWithTag(HOME_SEARCH_BUTTON_TAG).performTouchInput { click() }

        composeRule.waitForTag(READING_PLAYER_COMPACT_TAG)
        composeRule.onNodeWithTag(READING_PLAYER_BAR_TAG).assertDoesNotExist()
        composeRule.onNodeWithText("1/2").assertIsDisplayed()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.activity.history.history
                .firstOrNull() == Search()
        }
    }

    /**
     * Contract: https://github.com/zly2006/zhihu-plus-plus/issues/550
     * Introduced by: https://github.com/zly2006/zhihu-plus-plus/pull/552
     */
    @Test
    fun leavingDetailCompactsTheExpandedPlayer() {
        lateinit var navHost: AppNavHost
        composeRule.setZhihuMainContent { navHost = it }
        composeRule.runOnIdle {
            navHost.open(Article(type = ArticleType.Answer, id = 987654L))
        }

        composeRule.waitForTag(READING_PLAYER_BAR_TAG)
        composeRule.onNodeWithTag(READING_PLAYER_COMPACT_TAG).assertDoesNotExist()

        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }

        composeRule.waitForTag(READING_PLAYER_COMPACT_TAG)
        composeRule.onNodeWithTag(READING_PLAYER_BAR_TAG).assertDoesNotExist()
    }

    /**
     * Contract: https://github.com/zly2006/zhihu-plus-plus/issues/550
     * Introduced by: https://github.com/zly2006/zhihu-plus-plus/pull/552
     */
    @Test
    fun changingThePlayingItemReplacesTheVisibleReadingDetail() {
        lateinit var navHost: AppNavHost
        composeRule.setZhihuMainContent { navHost = it }
        composeRule.runOnIdle {
            navHost.open(Article(type = ArticleType.Answer, id = PLAYER_STATE.queue.first().id))
        }
        composeRule.waitForTag(READING_PLAYER_BAR_TAG)

        AndroidReadingPlayerBridge.publish(PLAYER_STATE.copy(currentIndex = 1))

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.activity.history.history
                .firstOrNull() == Article(type = ArticleType.Answer, id = PLAYER_STATE.queue[1].id)
        }
        assertEquals(
            Article(type = ArticleType.Answer, id = PLAYER_STATE.queue[1].id),
            composeRule.activity.history.history
                .first(),
        )
    }

    /**
     * Contract: https://github.com/zly2006/zhihu-plus-plus/issues/550
     * Introduced by: https://github.com/zly2006/zhihu-plus-plus/pull/552
     */
    @Test
    fun expandedPlayerStopButtonEndsTheReadingSession() {
        composeRule.setZhihuMainContent()
        composeRule.waitForTag(READING_PLAYER_COMPACT_TAG)
        composeRule.onNodeWithTag(READING_PLAYER_COMPACT_TAG).performClick()
        composeRule.waitForTag(READING_PLAYER_STOP_TAG)

        composeRule.onNodeWithTag(READING_PLAYER_STOP_TAG).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            !AndroidReadingPlayerBridge.state.value.hasSession
        }
        composeRule.onNodeWithTag(READING_PLAYER_BAR_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(READING_PLAYER_COMPACT_TAG).assertDoesNotExist()
    }

    /**
     * Contract: https://github.com/zly2006/zhihu-plus-plus/issues/550
     * Introduced by: https://github.com/zly2006/zhihu-plus-plus/pull/552
     */
    @Test
    fun compactPlayerCanBeDraggedAndRestoresItsSavedEdge() {
        composeRule.setZhihuMainContent()
        composeRule.waitForTag(READING_PLAYER_COMPACT_TAG)
        val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInRoot

        composeRule.onNodeWithTag(READING_PLAYER_COMPACT_TAG).performTouchInput {
            swipe(
                start = center,
                end = center + Offset(rootBounds.width * 0.75f, -rootBounds.height * 0.2f),
                durationMillis = 700,
            )
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule
                .onNodeWithTag(READING_PLAYER_COMPACT_TAG)
                .fetchSemanticsNode()
                .boundsInRoot.center.x > rootBounds.center.x
        }
        val draggedBounds = composeRule.onNodeWithTag(READING_PLAYER_COMPACT_TAG).fetchSemanticsNode().boundsInRoot

        composeRule.setZhihuMainContent()
        composeRule.waitForTag(READING_PLAYER_COMPACT_TAG)
        val restoredBounds = composeRule.onNodeWithTag(READING_PLAYER_COMPACT_TAG).fetchSemanticsNode().boundsInRoot

        assertEquals(draggedBounds.left, restoredBounds.left, 1f)
        assertEquals(draggedBounds.top, restoredBounds.top, 1f)
    }

    /**
     * Contract: https://github.com/zly2006/zhihu-plus-plus/issues/550
     * Introduced by: https://github.com/zly2006/zhihu-plus-plus/pull/552
     */
    @Test
    fun queueSheetHostsReadingSettingsInsteadOfStop() {
        composeRule.setZhihuMainContent()
        composeRule.waitForTag(READING_PLAYER_COMPACT_TAG)
        composeRule.onNodeWithTag(READING_PLAYER_COMPACT_TAG).performClick()
        composeRule.waitForTag(READING_PLAYER_QUEUE_TAG)
        composeRule.onNodeWithTag(READING_PLAYER_QUEUE_TAG).performClick()

        composeRule.waitForTag(READING_QUEUE_SHEET_TAG)
        composeRule.onNodeWithTag("reading_queue_stop").assertDoesNotExist()
        composeRule.onNodeWithTag(READING_QUEUE_SETTINGS_TAG).assertIsDisplayed().performClick()

        composeRule.onNodeWithTag(READING_QUEUE_SHEET_TAG).assertDoesNotExist()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.activity.history.history
                .firstOrNull() == Account.ReadingPlaybackSettings()
        }
        composeRule.waitForTag(READING_PLAYER_COMPACT_TAG)
    }

    private fun MainActivityComposeRule.waitForTag(tag: String) {
        waitUntil(timeoutMillis = 5_000) {
            onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        val PLAYER_STATE = ReadingPlayerState(
            status = ReadingPlaybackStatus.Playing,
            queue = listOf(
                ReadingQueueItem(
                    contentType = ReadingContentType.Answer,
                    id = 1,
                    title = "第一条离线回答",
                    author = "作者一",
                ),
                ReadingQueueItem(
                    contentType = ReadingContentType.Answer,
                    id = 2,
                    title = "第二条离线回答",
                    author = "作者二",
                ),
            ),
            currentIndex = 0,
        )
    }
}
