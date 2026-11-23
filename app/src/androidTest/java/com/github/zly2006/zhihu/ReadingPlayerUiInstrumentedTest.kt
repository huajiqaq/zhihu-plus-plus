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
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
import com.github.zly2006.zhihu.ui.HOME_TOP_ACTIONS_TAG
import com.github.zly2006.zhihu.ui.PREFERENCE_NAME
import com.github.zly2006.zhihu.ui.components.READING_PLAYER_BAR_TAG
import com.github.zly2006.zhihu.ui.components.READING_PLAYER_COMPACT_TAG
import com.github.zly2006.zhihu.ui.components.READING_PLAYER_QUEUE_TAG
import com.github.zly2006.zhihu.ui.components.READING_PLAYER_STOP_TAG
import com.github.zly2006.zhihu.ui.components.READING_QUEUE_SETTINGS_TAG
import com.github.zly2006.zhihu.ui.components.READING_QUEUE_SHEET_TAG
import com.github.zly2006.zhihu.ui.subscreens.BOTTOM_BAR_ITEMS_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.subscreens.FLOATING_BOTTOM_BAR_PREFERENCE_KEY
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
import kotlin.math.abs

/** 浮动朗读播放器的交互与状态契约：[issue #550](https://github.com/zly2006/zhihu-plus-plus/issues/550)。 */
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
        val target = sample.target as Feed.AnswerTarget
        // 曝光记录跨进程保留；每次夹具使用新内容身份，不能靠清数据库绕过真实过滤。
        val contentIdBase = java.util.UUID
            .randomUUID()
            .mostSignificantBits and 0x3fffffffffffffffL
        val feedItemCount = 24
        val middleFeedIndex = feedItemCount / 2
        val feedItems: List<Feed> = List(feedItemCount) { index ->
            sample.copy(
                id = "reading-feed-$contentIdBase-$index",
                target = target.copy(
                    id = contentIdBase + index,
                    url = "https://www.zhihu.com/question/${contentIdBase + feedItemCount + index}/answer/${contentIdBase + index}",
                    question = target.question.copy(
                        id = contentIdBase + feedItemCount + index,
                        url = "https://www.zhihu.com/question/${contentIdBase + feedItemCount + index}",
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
        // 宽视口可能直接显示目标条目；先用真实 LazyColumn 滚到中部，不能仅依赖滚到可见。
        composeRule.onNodeWithTag(HOME_FEED_LIST_TAG).performScrollToIndex(middleFeedIndex)
        composeRule.onNodeWithTag(HOME_FEED_LIST_TAG).performScrollToNode(hasText("朗读背景条目 $middleFeedIndex"))
        composeRule.onNodeWithText("朗读背景条目 $middleFeedIndex").assertIsDisplayed()
        val positionedFeedNode = composeRule.onNodeWithTag(HOME_FEED_LIST_TAG).fetchSemanticsNode()
        val positionedScrollRange = positionedFeedNode.config[SemanticsProperties.VerticalScrollAxisRange]
        val positionedScroll = positionedScrollRange.value()
        val positionedScrollMax = positionedScrollRange.maxValue()
        assertTrue(
            "背景手势前必须离开首屏且下方仍可滚动: middle=$middleFeedIndex " +
                "scroll=$positionedScroll max=$positionedScrollMax feed=${positionedFeedNode.boundsInRoot}",
            positionedScroll > 0f && positionedScroll < positionedScrollMax,
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

        // 展开会改变遮挡范围；在当前主栏的信息流内、播放器和顶栏之外完成真实滑动。
        val gestureRoot = composeRule.onRoot()
        val gestureRootBounds = gestureRoot.fetchSemanticsNode().boundsInRoot
        val currentFeedNode = composeRule.onNodeWithTag(HOME_FEED_LIST_TAG).fetchSemanticsNode()
        val currentFeedBounds = currentFeedNode.boundsInRoot
        val mainPaneBounds = composeRule.onNodeWithTag("list_pane").fetchSemanticsNode().boundsInRoot
        val playerBarBounds = composeRule.onNodeWithTag(READING_PLAYER_BAR_TAG).fetchSemanticsNode().boundsInRoot
        val topActionsBounds = composeRule
            .onAllNodes(hasTestTag(HOME_TOP_ACTIONS_TAG))
            .fetchSemanticsNodes()
            .singleOrNull()
            ?.boundsInRoot
            ?.takeIf { it.width > 0f && it.height > 0f }
        val backgroundBounds = Rect(
            left = maxOf(gestureRootBounds.left, currentFeedBounds.left, mainPaneBounds.left),
            top = maxOf(gestureRootBounds.top, currentFeedBounds.top, mainPaneBounds.top, topActionsBounds?.bottom ?: currentFeedBounds.top),
            right = minOf(gestureRootBounds.right, currentFeedBounds.right, mainPaneBounds.right),
            bottom = minOf(gestureRootBounds.bottom, currentFeedBounds.bottom, mainPaneBounds.bottom, playerBarBounds.top),
        )
        val swipeStart = Offset(backgroundBounds.center.x, backgroundBounds.top + backgroundBounds.height * 0.8f)
        val swipeEnd = Offset(backgroundBounds.center.x, backgroundBounds.top + backgroundBounds.height * 0.2f)
        val gestureDescription = "root=$gestureRootBounds feed=$currentFeedBounds mainPane=$mainPaneBounds " +
            "playerBar=$playerBarBounds topActions=$topActionsBounds viewport=$backgroundBounds start=$swipeStart end=$swipeEnd"
        assertTrue(
            gestureDescription,
            backgroundBounds.width > 0f &&
                backgroundBounds.height > 0f &&
                swipeStart.y > swipeEnd.y &&
                currentFeedBounds.contains(swipeStart) &&
                currentFeedBounds.contains(swipeEnd) &&
                mainPaneBounds.contains(swipeStart) &&
                mainPaneBounds.contains(swipeEnd) &&
                !playerBarBounds.contains(swipeStart) &&
                !playerBarBounds.contains(swipeEnd),
        )
        val backgroundScrollRange = currentFeedNode.config[SemanticsProperties.VerticalScrollAxisRange]
        val scrollBefore = backgroundScrollRange.value()
        val scrollMaxBefore = backgroundScrollRange.maxValue()
        assertTrue(
            "背景滑动前必须仍处于可继续滚动的中部: $gestureDescription scroll=$scrollBefore max=$scrollMaxBefore",
            scrollBefore > 0f && scrollBefore < scrollMaxBefore,
        )
        gestureRoot.performTouchInput {
            swipe(
                start = swipeStart - gestureRootBounds.topLeft,
                end = swipeEnd - gestureRootBounds.topLeft,
                durationMillis = 400,
            )
        }

        try {
            composeRule.waitForTag(READING_PLAYER_COMPACT_TAG)
        } catch (failure: androidx.compose.ui.test.ComposeTimeoutException) {
            val scrollAfter = composeRule
                .onNodeWithTag(HOME_FEED_LIST_TAG)
                .fetchSemanticsNode()
                .config[SemanticsProperties.VerticalScrollAxisRange]
                .value()
            throw AssertionError("背景滑动未收起播放器: $gestureDescription scrollBefore=$scrollBefore scrollAfter=$scrollAfter", failure)
        }
        val scrollAfter = composeRule
            .onNodeWithTag(HOME_FEED_LIST_TAG)
            .fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange]
            .value()
        assertTrue("背景滑动未滚动信息流: $gestureDescription scrollBefore=$scrollBefore scrollAfter=$scrollAfter", scrollAfter > scrollBefore)
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

    @Test
    fun compactPlayerCanBeDraggedAndRestoresItsSavedEdge() {
        val originalOrientation = composeRule.activity.requestedOrientation

        /**
         * 按保存的相对高度推算贴边后的可见边界，[resting] 是默认贴底时的可见边界。
         * DraggableRefreshButton 贴边时默认留 16dp 边距；最小触控区比可见按钮大，可见边界在四周再各内缩一半差值。
         */
        fun snappedBounds(root: Rect, resting: Rect, fraction: Float, onLeft: Boolean): Rect {
            val node = composeRule.onNodeWithTag(READING_PLAYER_COMPACT_TAG).fetchSemanticsNode()
            val insetX = (node.layoutInfo.width - node.size.width) / 2f
            val insetY = (node.layoutInfo.height - node.size.height) / 2f
            val edgeInset = with(composeRule.density) { 16.dp.toPx() } + insetX
            val left = if (onLeft) root.left + edgeInset else root.right - edgeInset - resting.width
            val top = root.top + insetY + fraction * (resting.top - insetY - root.top)
            return Rect(left, top, left + resting.width, top + resting.height)
        }

        fun rotate(orientation: Int, configuration: Int) {
            // 每次向 rule 当前 Activity 发请求，不保留可能过期的宿主引用。
            composeRule.activityRule.scenario.onActivity { it.requestedOrientation = orientation }
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.activity.resources.configuration.orientation == configuration &&
                    composeRule.onRoot().fetchSemanticsNode().boundsInRoot.let {
                        if (configuration == Configuration.ORIENTATION_LANDSCAPE) it.width > it.height else it.height > it.width
                    }
            }
            composeRule.waitForIdle()
        }

        fun settledBounds(expected: Rect): Rect {
            composeRule.waitUntil(timeoutMillis = 5_000) {
                val actual = composeRule.onNodeWithTag(READING_PLAYER_COMPACT_TAG).fetchSemanticsNode().boundsInRoot
                abs(actual.left - expected.left) <= 1f && abs(actual.top - expected.top) <= 1f
            }
            return composeRule.onNodeWithTag(READING_PLAYER_COMPACT_TAG).fetchSemanticsNode().boundsInRoot
        }
        try {
            rotate(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, Configuration.ORIENTATION_LANDSCAPE)
            val preferences = composeRule.activity.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)
            preferences.edit(commit = true) { putBoolean(FLOATING_BOTTOM_BAR_PREFERENCE_KEY, false) }
            composeRule.setZhihuMainContent()
            composeRule.waitForTag(READING_PLAYER_COMPACT_TAG)
            composeRule.onNodeWithTag("nav_rail").assertIsDisplayed()
            val landscapeRoot = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
            assertTrue("实际横屏必须宽于高：$landscapeRoot", landscapeRoot.width > landscapeRoot.height)
            val initial = composeRule.onNodeWithTag(READING_PLAYER_COMPACT_TAG).fetchSemanticsNode().boundsInRoot
            assertTrue("默认 compact 应靠左：$initial root=$landscapeRoot", initial.center.x < landscapeRoot.center.x)
            composeRule.onNodeWithTag(READING_PLAYER_COMPACT_TAG).performTouchInput {
                swipe(center, center + Offset(landscapeRoot.width * 0.75f, -landscapeRoot.height * 0.25f), durationMillis = 700)
            }
            composeRule.waitUntil(timeoutMillis = 5_000) {
                preferences.getString("readingPlayerCompact-landscape-edge", "") == "right"
            }
            val landscapeFraction = preferences.getFloat("readingPlayerCompact-landscape-yFraction", Float.NaN)
            assertTrue("应保存非默认高度：$landscapeFraction", landscapeFraction in 0.1f..0.9f)
            val landscape = settledBounds(snappedBounds(landscapeRoot, initial, landscapeFraction, onLeft = false))
            composeRule.setZhihuMainContent()
            composeRule.waitForTag(READING_PLAYER_COMPACT_TAG)
            val recreated = settledBounds(landscape)
            assertEquals(landscape.left, recreated.left, 1f)
            assertEquals(landscape.top, recreated.top, 1f)

            val beforeRotation = preferences.all.filterKeys { it.startsWith("readingPlayerCompact-") }
            rotate(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, Configuration.ORIENTATION_PORTRAIT)
            composeRule.waitForTag(READING_PLAYER_COMPACT_TAG)
            val portraitRoot = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
            assertTrue("实际竖屏必须高于宽：$portraitRoot", portraitRoot.height > portraitRoot.width)
            composeRule.onNodeWithTag("nav_rail").assertDoesNotExist()
            val homeTab = composeRule
                .onNodeWithTag("nav_tab_home")
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
            assertTrue("竖屏主页导航须在底部且位于实际根边界内：home=$homeTab root=$portraitRoot", homeTab.top > portraitRoot.center.y && homeTab.bottom <= portraitRoot.bottom)
            val portraitInitial = composeRule.onNodeWithTag(READING_PLAYER_COMPACT_TAG).fetchSemanticsNode().boundsInRoot
            assertTrue(portraitInitial.center.x < portraitRoot.center.x)
            val systemBarsBottom = composeRule.runOnIdle {
                checkNotNull(ViewCompat.getRootWindowInsets(composeRule.activity.window.decorView))
                    .getInsets(WindowInsetsCompat.Type.systemBars())
                    .bottom
            }
            val portraitUnclipped = composeRule.onNodeWithTag(READING_PLAYER_COMPACT_TAG).getUnclippedBoundsInRoot()
            val portraitBottomPx = with(composeRule.density) { portraitUnclipped.bottom.toPx() }
            assertTrue(
                "未裁剪播放器须避让实际系统栏：unclipped=$portraitUnclipped root=$portraitRoot inset=$systemBarsBottom",
                portraitBottomPx <= portraitRoot.bottom - systemBarsBottom + 1f,
            )
            assertEquals("旋转不得提前写 portrait 位置", beforeRotation, preferences.all.filterKeys { it.startsWith("readingPlayerCompact-") })
            composeRule.onNodeWithTag(READING_PLAYER_COMPACT_TAG).performTouchInput {
                swipe(center, center + Offset(portraitRoot.width * 0.15f, -portraitRoot.height * 0.5f), durationMillis = 700)
            }
            composeRule.waitUntil(timeoutMillis = 5_000) {
                preferences.getString("readingPlayerCompact-portrait-edge", "") == "left"
            }
            val portraitFraction = preferences.getFloat("readingPlayerCompact-portrait-yFraction", Float.NaN)
            assertTrue(portraitFraction in 0.1f..0.9f && abs(portraitFraction - landscapeFraction) > 0.1f)
            val portrait = settledBounds(snappedBounds(portraitRoot, portraitInitial, portraitFraction, onLeft = true))
            assertEquals("旋转不改变形状宽度", landscape.width, portrait.width, 1f)
            assertEquals("旋转不改变形状高度", landscape.height, portrait.height, 1f)
            val savedBoth = preferences.all.filterKeys { it.startsWith("readingPlayerCompact-") }
            rotate(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, Configuration.ORIENTATION_LANDSCAPE)
            composeRule.waitForTag(READING_PLAYER_COMPACT_TAG)
            composeRule.onNodeWithTag("nav_rail").assertIsDisplayed()
            val returnedRoot = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
            assertTrue("返回实际横屏必须宽于高：$returnedRoot", returnedRoot.width > returnedRoot.height)
            val returned = settledBounds(landscape)
            assertEquals(landscape.left, returned.left, 1f)
            assertEquals(landscape.top, returned.top, 1f)
            assertEquals("返回方向不得重写任一位置", savedBoth, preferences.all.filterKeys { it.startsWith("readingPlayerCompact-") })
            composeRule.onNodeWithTag(READING_PLAYER_COMPACT_TAG).assertIsDisplayed().performTouchInput { click() }
            composeRule.waitForTag(READING_PLAYER_BAR_TAG)
        } finally {
            composeRule.activityRule.scenario.onActivity { it.requestedOrientation = originalOrientation }
            composeRule.waitForIdle()
        }
    }

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
