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
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.core.content.edit
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.zly2006.zhihu.data.FeedDisplayItem
import com.github.zly2006.zhihu.data.RecommendationMode
import com.github.zly2006.zhihu.data.toFeedDisplayItemNavDestinationJson
import com.github.zly2006.zhihu.navigation.Search
import com.github.zly2006.zhihu.notification.ZHIHU_PLUS_PLUS_HOME_NOTIFICATIONS_URL
import com.github.zly2006.zhihu.test.ZhihuMockApi
import com.github.zly2006.zhihu.test.assertListOffsetKept
import com.github.zly2006.zhihu.test.resetAppPreferences
import com.github.zly2006.zhihu.test.setZhihuMainContent
import com.github.zly2006.zhihu.ui.AUTO_REFRESH_HOME_ON_STARTUP_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.HOME_FEED_LIST_TAG
import com.github.zly2006.zhihu.ui.PREFERENCE_NAME
import com.github.zly2006.zhihu.ui.ZHIHU_PLUS_AUTHOR_PINS_URL
import com.github.zly2006.zhihu.viewmodel.feed.HomeFeedViewModel
import io.ktor.http.HttpMethod
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 首页动态头部回归：从搜索页返回后，信息流卡片的像素位置必须保持不变。
 * 与 QuestionScreenScrollRestoreInstrumentedTest 使用同一套断言。
 */
@RunWith(AndroidJUnit4::class)
class HomeScreenScrollRestoreInstrumentedTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        rule.resetAppPreferences()
        ZhihuMockApi.install(enabled = true)
        ZhihuMockApi.reset()
        ZhihuMockApi.mockJson(
            method = HttpMethod.Get,
            url = ZHIHU_PLUS_AUTHOR_PINS_URL,
            body = """{"data":[]}""",
        )
        ZhihuMockApi.mockJsonPrefix(
            method = HttpMethod.Get,
            urlPrefix = ZHIHU_PLUS_PLUS_HOME_NOTIFICATIONS_URL,
            body = """{"notifications":[]}""",
        )
        rule.activity.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE).edit(commit = true) {
            putBoolean("duo3_home_account", false)
            putBoolean("showRefreshFab", false)
            putBoolean(AUTO_REFRESH_HOME_ON_STARTUP_PREFERENCE_KEY, false)
            putBoolean("loginForRecommendation", false)
            putBoolean("survey_feedback_done", true)
            putString("recommendationMode", RecommendationMode.WEB.key)
        }
        rule.setZhihuMainContent()
        rule.waitUntil(10_000) {
            rule.onAllNodesWithTag(HOME_FEED_LIST_TAG).fetchSemanticsNodes().isNotEmpty()
        }
        val entry = requireNotNull(rule.activity.navController.currentBackStackEntry)
        val viewModel = ViewModelProvider(entry)[HomeFeedViewModel::class.java]
        rule.runOnIdle {
            viewModel.apply {
                allData.clear()
                debugData.clear()
                displayItems.clear()
                addDisplayItems(
                    List(8) { index ->
                        FeedDisplayItem(
                            title = "离线条目 ${index.toString().padStart(2, '0')}",
                            summary = "这是第 ${index + 1} 条用于首页滚动回归的离线摘要。",
                            details = "离线验证 · 固定假数据",
                            feed = null,
                            navDestinationJson =
                                Search(query = "fixture-$index").toFeedDisplayItemNavDestinationJson(),
                        )
                    },
                )
            }
        }
    }

    @Test
    fun feedKeepsOffsetAfterReturningFromSearch() {
        rule.waitUntil(10_000) {
            runCatching {
                rule.onNodeWithTag(HOME_FEED_LIST_TAG).performScrollToNode(hasText("离线条目 04"))
                true
            }.getOrDefault(false)
        }
        rule.waitForIdle()
        val before = itemOffset("离线条目 04")
        assertTrue("the feed item must be visible before opening it", before != null)

        rule.onNode(hasText("离线条目 04") and hasClickAction()).performClick()
        rule.waitUntil(10_000) {
            rule.onAllNodesWithTag(HOME_FEED_LIST_TAG).fetchSemanticsNodes().isEmpty()
        }
        rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitUntil(10_000) {
            rule.onAllNodesWithTag(HOME_FEED_LIST_TAG).fetchSemanticsNodes().isNotEmpty()
        }
        rule.waitForIdle()

        val after = itemOffset("离线条目 04")
        assertListOffsetKept("the feed item", before, after)
    }

    private fun listTop(): Float =
        rule
            .onNodeWithTag(HOME_FEED_LIST_TAG)
            .fetchSemanticsNode()
            .boundsInRoot.top

    private fun itemOffset(text: String): Float? =
        rule
            .onAllNodesWithText(text)
            .fetchSemanticsNodes()
            .minOfOrNull { it.boundsInRoot.top }
            ?.minus(listTop())
}
