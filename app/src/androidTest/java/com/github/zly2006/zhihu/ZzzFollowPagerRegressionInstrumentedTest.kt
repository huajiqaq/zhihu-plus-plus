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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.test.MainActivityComposeRule
import com.github.zly2006.zhihu.test.ZhihuMockApi
import com.github.zly2006.zhihu.test.resetAppPreferences
import com.github.zly2006.zhihu.test.setScreenContent
import com.github.zly2006.zhihu.test.setZhihuMainContent
import com.github.zly2006.zhihu.ui.FOLLOWING_USERS_ROW_TAG
import com.github.zly2006.zhihu.ui.FOLLOW_PINS_LIST_TAG
import com.github.zly2006.zhihu.ui.FOLLOW_RECOMMEND_LIST_TAG
import com.github.zly2006.zhihu.ui.FOLLOW_SCREEN_TAB_ROW_TAG
import com.github.zly2006.zhihu.ui.FollowScreen
import com.github.zly2006.zhihu.ui.PIN_SCREEN_BACK_BUTTON_TAG
import com.github.zly2006.zhihu.ui.PIN_SCREEN_SCROLL_TAG
import io.ktor.http.HttpMethod
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

// 这些分页手势之后启动其他 Activity 测试时，Android 模拟器的输入通道可能超时。
// 保持本类在 AndroidJUnitRunner 按名称排序的末尾，让设备测试进程在本类结束后立即退出。
@RunWith(AndroidJUnit4::class)
class ZzzFollowPagerRegressionInstrumentedTest {
    @get:Rule
    val composeRule: MainActivityComposeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        composeRule.resetAppPreferences(forceMock = true)
    }

    /**
     * 边界反向手势后父分页必须落在整页；行为依据：https://github.com/zly2006/zhihu-plus-plus/issues/318
     */
    @Test
    fun boundaryReverseGesture_settlesParentToWholePage() {
        val outerPagerState = AtomicReference<PagerState>()
        composeRule.setScreenContent {
            val state = rememberPagerState(initialPage = 1, pageCount = { 3 })
            SideEffect { outerPagerState.set(state) }
            HorizontalPager(
                state = state,
                modifier = Modifier.fillMaxSize(),
                pageNestedScrollConnection = object : NestedScrollConnection {},
            ) { page ->
                if (page == 1) {
                    FollowScreen(
                        scrollToTopTrigger = 0,
                        innerPadding = PaddingValues(),
                        parentPagerState = state,
                    )
                } else {
                    Box(Modifier.fillMaxSize())
                }
            }
        }

        repeat(10) { iteration ->
            composeRule.onNodeWithTag("follow_screen_tab_2").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("follow_screen_tab_2").assertIsSelected()

            injectReverseBoundaryGesture()
            composeRule.mainClock.advanceTimeBy(1_000)
            composeRule.waitForIdle()

            composeRule.runOnIdle {
                val state = outerPagerState.get()
                val failureContext = "iteration=$iteration isScrollInProgress=${state.isScrollInProgress}"
                assertEquals(failureContext, 1, state.currentPage)
                assertEquals(failureContext, 0f, state.currentPageOffsetFraction, 0.001f)
            }
        }

        composeRule.onRoot().performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            assertEquals(2, outerPagerState.get().currentPage)
            assertEquals(0f, outerPagerState.get().currentPageOffsetFraction, 0.001f)
        }
    }

    @Test
    fun avatarGesturesStayInTheRowAtBothEndsAndInAShortList() {
        fun mockAvatars(count: Int) {
            val users = (1..count).joinToString(",") { id ->
                """{"actor":{"id":"$id","url_token":"actor-$id","name":"关注 $id","avatar_url":""},"unread_count":0}"""
            }
            ZhihuMockApi.mockJsonPrefix(HttpMethod.Get, "https://api.zhihu.com/moments/recent?", """{"data":[$users]}""")
        }
        mockAvatars(18)
        val fixture = InstrumentationRegistry
            .getInstrumentation()
            .context.assets
            .open("home-follow/pin-target.json")
            .bufferedReader()
            .use { it.readText() }
        val pin = ZhihuJson.json.parseToJsonElement(fixture).jsonObject
        val rows = (401L..424L).map { id ->
            buildJsonObject {
                put("id", "recommend-$id")
                put("type", "moments_feed")
                put("target_type", "pin")
                put("target", JsonObject(pin + ("id" to JsonPrimitive(id))))
            }
        }
        ZhihuMockApi.mockJsonPrefix(
            HttpMethod.Get,
            "https://api.zhihu.com/moments_v3?feed_type=recommend",
            buildJsonObject {
                put("data", JsonArray(rows))
                put(
                    "paging",
                    buildJsonObject {
                        put("is_end", true)
                        put("next", "")
                    },
                )
            }.toString(),
        )
        val outer = AtomicReference<PagerState>()
        composeRule.setScreenContent {
            val state = rememberPagerState(initialPage = 1, pageCount = { 3 })
            SideEffect { outer.set(state) }
            HorizontalPager(state, modifier = Modifier.fillMaxSize(), pageNestedScrollConnection = object : NestedScrollConnection {}) { page ->
                if (page == 1) FollowScreen(innerPadding = PaddingValues(), parentPagerState = state)
            }
        }
        val row = composeRule.onNodeWithTag(FOLLOWING_USERS_ROW_TAG).assertIsDisplayed()

        fun rowPosition() = row.fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange].value()

        fun assertPagerStillOnRecommended() {
            composeRule.onNodeWithTag("follow_screen_tab_0").assertIsSelected()
            composeRule.runOnIdle {
                assertEquals(1, outer.get().currentPage)
                assertEquals(0f, outer.get().currentPageOffsetFraction, 0.001f)
            }
        }
        row.performTouchInput { swipeRight(durationMillis = 150) }
        assertPagerStillOnRecommended()
        val beginning = rowPosition()
        row.performTouchInput { swipeLeft(durationMillis = 150) }
        assertTrue(rowPosition() > beginning)
        assertPagerStillOnRecommended()
        row.performScrollToNode(hasTestTag("following_users_item_18"))
        row.performTouchInput { swipeLeft(durationMillis = 150) }
        composeRule.onNodeWithTag("following_users_item_18").assertIsDisplayed()
        assertPagerStillOnRecommended()

        mockAvatars(1)
        composeRule.onNodeWithTag("following_users_retry").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { rowPosition() == 0f }
        row.performTouchInput { swipeLeft(durationMillis = 150) }
        row.performTouchInput { swipeRight(durationMillis = 150) }
        assertEquals(0f, rowPosition(), 0.001f)
        assertPagerStillOnRecommended()

        val feed = composeRule.onNodeWithTag(FOLLOW_RECOMMEND_LIST_TAG)
        val initialPosition = feed.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        row.performTouchInput { swipeUp(startY = centerY, endY = -height.toFloat(), durationMillis = 250) }
        assertTrue(feed.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value() > initialPosition)
        assertPagerStillOnRecommended()
        feed.performTouchInput { swipeLeft() }
        composeRule.onNodeWithTag("follow_screen_tab_1").assertIsSelected()
    }

    /** 想法页首次可见才请求；卡片进入既有详情后返回保留选页、内容和滚动位置。 */
    @Test
    fun pinsLoadOnFirstSelectionAndKeepScrollAcrossTabAndDetailReturn() {
        val fixture = InstrumentationRegistry
            .getInstrumentation()
            .context.assets
            .open("home-follow/pin-target.json")
            .bufferedReader()
            .use { it.readText() }
        val pin = ZhihuJson.json.parseToJsonElement(fixture).jsonObject
        val rows = (301L..324L).map { id ->
            buildJsonObject {
                put("id", "follow-pin-$id")
                put("type", "moments_feed")
                put("target_type", "pin")
                put("target", JsonObject(pin + mapOf("id" to JsonPrimitive(id), "url" to JsonPrimitive("https://www.zhihu.com/pin/$id"))))
            }
        }
        ZhihuMockApi.mockJson(
            HttpMethod.Get,
            "https://api.zhihu.com/moments_v3?feed_type=pin&include=data%5B%2A%5D.content%2Cexcerpt%2Cheadline%2Ctarget.author.badge_v2",
            buildJsonObject {
                put("data", JsonArray(rows))
                put(
                    "paging",
                    buildJsonObject {
                        put("is_end", true)
                        put("next", "")
                    },
                )
            }.toString(),
        )
        // 既有合成 Pin 夹具用于消费者链路，不冒充官方关注接口样本。
        val detailAuthor = pin.getValue("author").jsonObject
        ZhihuMockApi.mockJson(
            HttpMethod.Get,
            "https://api.zhihu.com/v2/pins/318",
            JsonObject(
                pin + mapOf(
                    "id" to JsonPrimitive("318"),
                    "url" to JsonPrimitive("https://www.zhihu.com/pin/318"),
                    "author" to JsonObject(
                        detailAuthor + mapOf(
                            "gender" to JsonPrimitive(0),
                            "type" to JsonPrimitive("people"),
                            "is_advertiser" to JsonPrimitive(false),
                            "is_org" to JsonPrimitive(false),
                        ),
                    ),
                ),
            ).toString(),
        )
        composeRule.setZhihuMainContent()
        assertEquals(0, ZhihuMockApi.requestCount(HttpMethod.Get, "feed_type=pin"))
        composeRule.onNodeWithTag("nav_tab_follow").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("follow_screen_tab_2").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(0, ZhihuMockApi.requestCount(HttpMethod.Get, "feed_type=pin"))
        val tabs = composeRule.onNodeWithTag(FOLLOW_SCREEN_TAB_ROW_TAG)
        val statusBarBottom = composeRule.runOnIdle {
            checkNotNull(ViewCompat.getRootWindowInsets(composeRule.activity.window.decorView))
                .getInsets(WindowInsetsCompat.Type.statusBars())
                .top
        }
        assertEquals("关注标签紧贴状态栏下沿", statusBarBottom.toFloat(), tabs.fetchSemanticsNode().boundsInWindow.top, 1f)
        val tabsBottom = tabs.fetchSemanticsNode().boundsInRoot.bottom
        composeRule.onNodeWithTag("follow_screen_tab_2").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("follow_pins_item_pin:301").fetchSemanticsNodes().isNotEmpty()
        }
        val itemTag = "follow_pins_item_pin:318"
        composeRule.onNodeWithTag(FOLLOW_PINS_LIST_TAG).performScrollToNode(hasTestTag(itemTag))
        composeRule.onNodeWithTag(itemTag).assertIsDisplayed()
        val targetText = composeRule.onNode(
            hasText("合成想法正文", substring = true).and(hasAnyAncestor(hasTestTag(itemTag))),
            useUnmergedTree = true,
        )
        val textTop = targetText.fetchSemanticsNode().boundsInRoot.top
        assertEquals("滚动想法列表时标签栏不移动", tabsBottom, tabs.fetchSemanticsNode().boundsInRoot.bottom, 1f)
        assertEquals(
            "列表视口从标签栏下沿开始",
            tabsBottom,
            composeRule
                .onNodeWithTag(FOLLOW_PINS_LIST_TAG)
                .fetchSemanticsNode()
                .boundsInRoot.top,
            1f,
        )
        composeRule.onNodeWithTag(FOLLOW_PINS_LIST_TAG).performSemanticsAction(SemanticsActions.ScrollBy) { scroll ->
            assertTrue(scroll(0f, -(tabsBottom - textTop + 1f).coerceAtLeast(0f)))
        }
        assertTrue(targetText.fetchSemanticsNode().boundsInRoot.top > tabsBottom)
        val before = composeRule
            .onNodeWithTag(FOLLOW_PINS_LIST_TAG)
            .fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange]
            .value()
        assertTrue(before > 0f)
        composeRule.onNodeWithTag("follow_screen_tab_1").performClick()
        composeRule.onNodeWithTag("follow_screen_tab_2").performClick()
        composeRule.onNodeWithTag("follow_screen_tab_2").assertIsSelected()
        composeRule.onNodeWithTag(FOLLOW_PINS_LIST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(itemTag).assertIsDisplayed()
        assertTrue(targetText.fetchSemanticsNode().boundsInRoot.top > tabsBottom)
        targetText.assertIsDisplayed().performTouchInput { click(center) }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(PIN_SCREEN_SCROLL_TAG).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(PIN_SCREEN_SCROLL_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(PIN_SCREEN_BACK_BUTTON_TAG).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("follow_screen_tab_2").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("follow_screen_tab_2").assertIsSelected()
        composeRule.onNodeWithTag(itemTag).assertIsDisplayed()
        val after = composeRule
            .onNodeWithTag(FOLLOW_PINS_LIST_TAG)
            .fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange]
            .value()
        assertEquals(before, after, 0.001f)
        assertEquals(1, ZhihuMockApi.requestCount(HttpMethod.Get, "feed_type=pin"))
        assertEquals(1, ZhihuMockApi.requestCount(HttpMethod.Get, "https://api.zhihu.com/v2/pins/318"))
    }

    private fun injectReverseBoundaryGesture() {
        composeRule.onRoot().performTouchInput {
            fun point(xFraction: Float) = Offset(width * xFraction, height * 0.55f)

            fun moveToFraction(xFraction: Float) {
                advanceEventTime(55)
                moveTo(point(xFraction))
            }

            down(point(0.72f))
            moveToFraction(0.58f)
            moveToFraction(0.40f)
            moveToFraction(0.22f)
            moveToFraction(0.26f)
            moveToFraction(0.28f)
            advanceEventTime(55)
            up()
        }
    }
}
