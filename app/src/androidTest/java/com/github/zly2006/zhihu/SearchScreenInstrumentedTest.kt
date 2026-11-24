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
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.zly2006.zhihu.navigation.Search
import com.github.zly2006.zhihu.test.InstrumentedTestEnvironment
import com.github.zly2006.zhihu.test.ZhihuMockApi
import com.github.zly2006.zhihu.test.resetAppPreferences
import com.github.zly2006.zhihu.test.setScreenContent
import com.github.zly2006.zhihu.ui.PREFERENCE_NAME
import com.github.zly2006.zhihu.ui.SearchScreen
import com.github.zly2006.zhihu.viewmodel.QUALITY_FILTER_MODE_PREFERENCE_KEY
import com.github.zly2006.zhihu.viewmodel.QualityFilterMode
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchScreenInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        composeRule.resetAppPreferences(forceMock = true)
    }

    @After
    fun tearDown() {
        if (!InstrumentedTestEnvironment.isMockMode()) composeRule.resetAppPreferences(forceMock = true)
    }

    @Test
    fun searchBoxEditingClearImeAndBackAreDeterministic() {
        composeRule.activity
            .getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("showSearchHotSearch", false)
            .commit()

        val recordingNavigator = composeRule.setScreenContent {
            SearchScreen(search = Search())
        }

        val searchInput = composeRule.onNodeWithTag("search_input")
        searchInput.assertIsDisplayed().assertIsFocused()
        composeRule.onNodeWithText("搜索内容").assertIsDisplayed()

        searchInput.performTextInput("compose")
        searchInput.assertTextEquals("compose")
        composeRule.onNodeWithTag("search_clear_button").assertIsDisplayed()

        searchInput.performTextReplacement("jetpack compose")
        searchInput.assertTextEquals("jetpack compose")
        searchInput.performImeAction()
        composeRule.waitForIdle()
        assertEquals(listOf(Search(query = "jetpack compose")), recordingNavigator.destinations)

        assertEquals(
            """["jetpack compose"]""",
            composeRule.activity
                .getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)
                .getString("searchHistoryQueries", null),
        )

        composeRule.onNodeWithTag("search_clear_button").performClick()
        composeRule.onAllNodesWithTag("search_clear_button").assertCountEquals(0)
        composeRule.onNodeWithText("搜索内容").assertIsDisplayed()

        composeRule.onNodeWithTag("search_back_button").performClick()
        composeRule.waitForIdle()
        assertEquals(1, recordingNavigator.backCount)
        assertEquals(listOf(Search(query = "jetpack compose")), recordingNavigator.destinations)
    }

    @Test
    fun memberScopedSearchKeepsRestrictionWhenSubmittingQuery() {
        val preferences = composeRule.activity.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)
        preferences
            .edit()
            .putString("searchHistoryQueries", """["全局历史"]""")
            .commit()

        val recordingNavigator = composeRule.setScreenContent {
            SearchScreen(
                search = Search(
                    restrictedMemberHashId = "member-hash-id",
                    restrictedMemberName = "离线用户",
                ),
            )
        }

        composeRule.onNodeWithText("搜索 离线用户 的创作").assertIsDisplayed()
        composeRule.onNodeWithText("输入关键词搜索 离线用户 的创作").assertIsDisplayed()
        composeRule.onAllNodesWithText("搜索历史").assertCountEquals(0)
        composeRule.onAllNodesWithText("全局历史").assertCountEquals(0)
        composeRule.onAllNodesWithText("全站热搜").assertCountEquals(0)
        composeRule.onAllNodesWithTag("search_hot_list").assertCountEquals(0)

        val searchInput = composeRule.onNodeWithTag("search_input")
        searchInput.assertIsFocused()
        searchInput.performTextInput("限定关键词")
        searchInput.performImeAction()
        composeRule.waitForIdle()

        assertEquals(
            listOf(
                Search(
                    query = "限定关键词",
                    restrictedMemberHashId = "member-hash-id",
                    restrictedMemberName = "离线用户",
                ),
            ),
            recordingNavigator.destinations,
        )
        assertEquals("""["全局历史"]""", preferences.getString("searchHistoryQueries", null))
    }

    @Test
    fun prefilledSearchDoesNotStealFocusFromResultsState() {
        composeRule.activity
            .getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("showSearchHotSearch", false)
            .commit()

        composeRule.setScreenContent {
            SearchScreen(
                search = Search(query = "已有关键词"),
            )
        }

        composeRule.onNodeWithTag("search_input").assertIsDisplayed().assertTextEquals("已有关键词")
        composeRule.onAllNodesWithText("搜索内容").assertCountEquals(0)
    }

    @Test
    fun peopleTabHidesGeneralSearchFilterAndSwitchingBackRestoresIt() {
        ZhihuMockApi.mockJsonPrefix(
            method = HttpMethod.Get,
            urlPrefix = "https://www.zhihu.com/api/v4/search_v3",
            body = """{"data":[],"paging":{"is_end":true,"is_start":true,"totals":0,"next":""}}""",
        )
        composeRule.setScreenContent { SearchScreen(search = Search(query = "用户")) }

        composeRule.waitUntil { ZhihuMockApi.requestCount(urlSubstring = "/search_v3") == 1 }
        composeRule.onNodeWithTag("search_filter_button").performClick()
        composeRule.onNodeWithTag("search_filter_sort_Latest").performClick()
        composeRule.onNodeWithTag("search_filter_type_Answer").performClick()
        composeRule.onNodeWithTag("search_filter_time_Week").performClick()
        composeRule.onNodeWithTag("search_filter_time_Week").assertIsSelected()
        assertEquals(1, ZhihuMockApi.requestCount(urlSubstring = "/search_v3"))
        composeRule.onNodeWithTag("search_filter_cancel").performClick()
        composeRule.onNodeWithTag("search_filter_button").performClick()
        composeRule.onNodeWithTag("search_filter_sort_Default").assertIsSelected()
        composeRule.onNodeWithTag("search_filter_type_All").assertIsSelected()
        composeRule.onNodeWithTag("search_filter_time_All").assertIsSelected()
        composeRule.onNodeWithTag("search_filter_sort_Latest").performClick()
        composeRule.onNodeWithTag("search_filter_type_Answer").performClick()
        composeRule.onNodeWithTag("search_filter_time_Week").performClick()
        composeRule.onNodeWithTag("search_filter_apply").performClick()
        composeRule.waitUntil { ZhihuMockApi.requestCount(urlSubstring = "/search_v3") == 2 }
        val applied = Url(ZhihuMockApi.recordedRequests().last { it.url.contains("/search_v3") }.url)
        assertEquals("created_time", applied.parameters["sort"])
        assertEquals("answer", applied.parameters["vertical"])
        assertEquals("a_week", applied.parameters["time_interval"])
        composeRule.onNodeWithTag("search_filter_button").performClick()
        composeRule.onNodeWithTag("search_filter_apply").performClick()
        assertEquals(2, ZhihuMockApi.requestCount(urlSubstring = "/search_v3"))
        composeRule.onNodeWithTag("search_tab_People").performClick()
        composeRule.onAllNodesWithTag("search_filter_button").assertCountEquals(0)
        composeRule.onNodeWithTag("search_tab_General").performClick()
        composeRule.onNodeWithTag("search_filter_button").performClick()
        composeRule.onNodeWithTag("search_filter_time_Week").assertIsSelected()
        composeRule.onNodeWithTag("search_filter_reset").performClick()
        composeRule.onNodeWithTag("search_filter_sort_Default").assertIsSelected()
        composeRule.onNodeWithTag("search_filter_time_All").assertIsSelected()
        composeRule.onNodeWithTag("search_filter_cancel").performClick()
        composeRule.onAllNodesWithTag("search_filter_button").assertCountEquals(1)
    }

    /**
     * 展示原始返回样本的用户与内容徽章；关闭无关质量过滤，避免低赞文章变成不展示作者的占位卡。
     */
    @Test
    fun peopleAndGeneralResultsRenderReturnedAuthorBadges() {
        composeRule.activity.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE).edit(commit = true) {
            putString(QUALITY_FILTER_MODE_PREFERENCE_KEY, QualityFilterMode.OFF.name)
        }
        ZhihuMockApi.mockJsonPrefix(
            method = HttpMethod.Get,
            urlPrefix = "https://www.zhihu.com/api/v4/search_v3",
            body = readTestAsset("search-badge-general.json"),
        )
        composeRule.setScreenContent { SearchScreen(search = Search(query = "徽章")) }

        composeRule.onNodeWithTag("search_people_result_anon-general-people-001").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("刑法等 2 个话题下的优秀答主").assertIsDisplayed()
        composeRule.onNodeWithTag("search_general_results").performScrollToIndex(2)
        composeRule
            .onNodeWithTag("search_general_content_article:2070450975589110182")
            .assertIsDisplayed()

        ZhihuMockApi.mockJsonPrefix(
            method = HttpMethod.Get,
            urlPrefix = "https://www.zhihu.com/api/v4/search_v3",
            body = readTestAsset("search-badge-people.json"),
        )
        composeRule.onNodeWithTag("search_tab_People").performClick()

        composeRule.onNodeWithContentDescription("已认证机构号").assertIsDisplayed()
    }

    @Test
    fun searchHistoryRendersRecordsSearchesAndSupportsMenuActions() {
        val preferences = composeRule.activity.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)
        preferences
            .edit()
            .putBoolean("showSearchHotSearch", false)
            .putString("searchHistoryQueries", """["old query","older query"]""")
            .commit()

        val recordingNavigator = composeRule.setScreenContent {
            SearchScreen(
                search = Search(),
            )
        }

        composeRule.onNodeWithText("搜索历史").assertIsDisplayed()
        composeRule.onNodeWithText("old query").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertEquals(listOf(Search(query = "old query")), recordingNavigator.destinations)
        assertEquals("""["old query","older query"]""", preferences.getString("searchHistoryQueries", null))

        val searchInput = composeRule.onNodeWithTag("search_input")
        searchInput.performTextInput("new query")
        searchInput.performImeAction()
        composeRule.waitForIdle()
        assertEquals(
            listOf(Search(query = "old query"), Search(query = "new query")),
            recordingNavigator.destinations,
        )
        assertEquals("""["new query","old query","older query"]""", preferences.getString("searchHistoryQueries", null))

        composeRule.onNodeWithTag("search_history_more_button").performClick()
        composeRule.onNodeWithText("清空搜索历史").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertEquals("""["new query","old query","older query"]""", preferences.getString("searchHistoryQueries", null))
        composeRule.onNodeWithText("清空").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("暂无搜索历史").assertIsDisplayed()
        assertEquals("[]", preferences.getString("searchHistoryQueries", null))

        composeRule.onNodeWithTag("search_history_more_button").performClick()
        composeRule.onNodeWithText("关闭搜索历史").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertEquals(false, preferences.getBoolean("showSearchHistory", true))
        composeRule.onAllNodesWithText("搜索历史").assertCountEquals(0)
        assertEquals(
            listOf(Search(query = "old query"), Search(query = "new query")),
            recordingNavigator.destinations,
        )
    }

    @Test
    fun failedPeopleResultsKeepRetryAndRecoverWithoutAutomaticRequests() {
        ZhihuMockApi.mockJsonPrefix(
            method = HttpMethod.Get,
            urlPrefix = "https://www.zhihu.com/api/v4/search_v3",
            body = "{}",
            status = HttpStatusCode.InternalServerError,
        )
        composeRule.setScreenContent { SearchScreen(search = Search(query = "重试用户")) }
        composeRule.onNodeWithTag("search_tab_People").performClick()
        composeRule.onNodeWithText("加载失败").assertIsDisplayed()
        val failedRequests = ZhihuMockApi.requestCount(urlSubstring = "/search_v3")
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        assertEquals(failedRequests, ZhihuMockApi.requestCount(urlSubstring = "/search_v3"))
        ZhihuMockApi.mockJsonPrefix(
            method = HttpMethod.Get,
            urlPrefix = "https://www.zhihu.com/api/v4/search_v3",
            body = """{"data":[],"paging":{"is_end":true,"next":""}}""",
        )
        composeRule.onNodeWithText("重试").performClick()
        composeRule.waitForIdle()
        assertTrue(ZhihuMockApi.requestCount(urlSubstring = "/search_v3") > failedRequests)
        composeRule.onNodeWithTag("search_results_empty").assertIsDisplayed()
        composeRule.onAllNodesWithText("加载失败").assertCountEquals(0)
    }

    private fun readTestAsset(fileName: String): String =
        InstrumentationRegistry
            .getInstrumentation()
            .context.assets
            .open(fileName)
            .bufferedReader()
            .use { it.readText() }
}
