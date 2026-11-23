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

import android.content.pm.ActivityInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.room.useReaderConnection
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.zly2006.zhihu.filter.ContentOpenEventSupport
import com.github.zly2006.zhihu.filter.ContentOpenFrom
import com.github.zly2006.zhihu.navigation.Account
import com.github.zly2006.zhihu.navigation.AppNavHost
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.Daily
import com.github.zly2006.zhihu.navigation.EmptyDetail
import com.github.zly2006.zhihu.navigation.Follow
import com.github.zly2006.zhihu.navigation.Home
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.Login
import com.github.zly2006.zhihu.navigation.MainTabs
import com.github.zly2006.zhihu.navigation.MyCollections
import com.github.zly2006.zhihu.navigation.OnlineHistory
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.navigation.Video
import com.github.zly2006.zhihu.navigation.WriteQuestion
import com.github.zly2006.zhihu.navigation.rememberAppNavHost
import com.github.zly2006.zhihu.reading.AndroidReadingPlayerBridge
import com.github.zly2006.zhihu.reading.ReadingContentType
import com.github.zly2006.zhihu.reading.ReadingPlaybackStatus
import com.github.zly2006.zhihu.reading.ReadingPlayerState
import com.github.zly2006.zhihu.reading.ReadingQueueItem
import com.github.zly2006.zhihu.test.MainActivityComposeRule
import com.github.zly2006.zhihu.test.resetAppPreferences
import com.github.zly2006.zhihu.test.setZhihuMainContent
import com.github.zly2006.zhihu.theme.ThemeManager
import com.github.zly2006.zhihu.theme.ZhihuTheme
import com.github.zly2006.zhihu.ui.AndroidZhihuMain
import com.github.zly2006.zhihu.ui.FOLLOW_SCREEN_PAGER_TAG
import com.github.zly2006.zhihu.ui.LANDSCAPE_LIST_PANE_RATIO_KEY
import com.github.zly2006.zhihu.ui.PREFERENCE_NAME
import com.github.zly2006.zhihu.ui.QUESTION_SCREEN_LIST_TAG
import com.github.zly2006.zhihu.ui.WRITE_QUESTION_BACK_BUTTON_TAG
import com.github.zly2006.zhihu.ui.WRITE_QUESTION_TITLE_TAG
import com.github.zly2006.zhihu.ui.ZhihuMain
import com.github.zly2006.zhihu.ui.components.LocalPageTurnDispatcher
import com.github.zly2006.zhihu.ui.components.PageTurnCommand
import com.github.zly2006.zhihu.ui.components.PageTurnDispatcher
import com.github.zly2006.zhihu.ui.components.READING_QUEUE_SHEET_TAG
import com.github.zly2006.zhihu.ui.rememberZhihuMainPreferenceState
import com.github.zly2006.zhihu.ui.subscreens.BOTTOM_BAR_ITEMS_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.subscreens.COLLECTION_DIRECT_BROWSE_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.subscreens.FLOATING_BOTTOM_BAR_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.subscreens.START_DESTINATION_PREFERENCE_KEY
import com.github.zly2006.zhihu.viewmodel.filter.getContentFilterDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.github.zly2006.zhihu.ui.components.AppAlertDialog as AlertDialog

@RunWith(AndroidJUnit4::class)
class ZhihuMainNavigationInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val deterministicBottomBarItems = linkedSetOf(
        Home.name,
        Follow.name,
        Daily.name,
        OnlineHistory.name,
        Account.name,
    )

    @Before
    fun resetPreferences() {
        composeRule.resetAppPreferences()
    }

    /**
     * 主分页须保留启动页、隐藏标签和关注页内部状态；行为依据：https://github.com/zly2006/zhihu-plus-plus/issues/318
     */
    @Test
    fun startDestinationAndHiddenBottomTabsRemainCompatibleWithFlattenedPager() {
        // 关注页是带内部分页的单个主标签。启动应打开配置的可见标签，隐藏标签保持隐藏；
        // 从主分页进入关注页时，内部分页首次显示默认推荐标签。
        composeRule.launchZhihuMain(
            startDestination = Daily.name,
            bottomBarItems = linkedSetOf(Follow.name, Daily.name, OnlineHistory.name, Account.name),
        )

        composeRule.waitUntilTabSelected("nav_tab_daily")
        composeRule.onNodeWithTag("nav_tab_daily").assertIsSelected()
        composeRule.onNodeWithTag("nav_tab_follow").assertIsNotSelected()
        composeRule.onNodeWithTag("nav_tab_account").assertIsNotSelected()
        composeRule.onNodeWithTag("nav_tab_home").assertDoesNotExist()
        composeRule.onNodeWithTag("nav_tab_hotlist").assertDoesNotExist()
        composeRule.onNodeWithTag("nav_tab_onlinehistory").assertDoesNotExist()

        composeRule.onRoot().performTouchInput { swipeRight() }

        composeRule.waitUntilTabSelected("follow_screen_tab_0")
        composeRule.onNodeWithTag("nav_tab_follow").assertIsSelected()
        composeRule.onNodeWithTag("follow_screen_tab_0").assertIsSelected()
        composeRule.onNodeWithTag("follow_screen_tab_1").assertIsNotSelected()

        composeRule.onNodeWithTag(FOLLOW_SCREEN_PAGER_TAG).performTouchInput { swipeLeft() }

        composeRule.waitUntilTabSelected("follow_screen_tab_1")
        composeRule.onNodeWithTag("nav_tab_follow").assertIsSelected()
        composeRule.onNodeWithTag("follow_screen_tab_1").assertIsSelected()

        composeRule.activity.runOnUiThread {
            composeRule.activity.navigate(MainTabs, popup = true)
        }

        composeRule.waitUntilTabSelected("nav_tab_daily")
        composeRule.onNodeWithTag("nav_tab_daily").assertIsSelected()
        composeRule.onNodeWithTag("nav_tab_home").assertDoesNotExist()

        composeRule.onNodeWithTag("nav_tab_follow").performClick()

        composeRule.waitUntilTabSelected("nav_tab_follow")
        composeRule.onNodeWithTag("nav_tab_follow").assertIsSelected()
        composeRule.waitUntilTabSelected("follow_screen_tab_1")
    }

    /**
     * 首页打开内容的记录须归属首页信息流；行为依据：https://github.com/zly2006/zhihu-plus-plus/issues/318
     */
    @Test
    fun homeTabOpenContent_recordsHomeFeedOpenFrom() {
        composeRule.launchZhihuMain(startDestination = Home.name)

        composeRule.waitUntilTabSelected("nav_tab_home")

        val article = Article(type = ArticleType.Answer, id = 318L)
        var openFrom: String? = null
        composeRule.runOnIdle {
            composeRule.activity.navigate(article)
            openFrom = runBlocking {
                val database = getContentFilterDatabase()
                ContentOpenEventSupport.recordOpenEvent(database, article)
                database.useReaderConnection { connection ->
                    connection.usePrepared("SELECT openFrom FROM content_open_events WHERE contentType = 'answer' AND contentId = '318' ORDER BY id DESC LIMIT 1") { statement ->
                        check(statement.step())
                        statement.getText(0)
                    }
                }
            }
        }

        assertEquals(ContentOpenFrom.HOME_FEED, openFrom)
    }

    /**
     * 行为依据：https://github.com/zly2006/zhihu-plus-plus/issues/680
     * 右侧设置进入子页面后返回，必须恢复上级设置；通过真实 Compose 导航验证两个返回栈的归属。
     */
    @Test
    fun secondarySettingsBackRestoresParent() {
        composeRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        composeRule.launchZhihuMain(startDestination = Account.name)
        composeRule.waitUntilTabSelected("nav_tab_account")
        val rootEntryId = composeRule.activity.navHost.topEntryId
        composeRule.onNodeWithTag("accountSettings.search").performScrollTo().performClick()
        composeRule.onNodeWithTag("settingsSearch.input").performTextInput("热搜")
        composeRule.onNodeWithTag("settingsSearch.result.appearance.showSearchHotSearch").performClick()
        // 导航层保留被盖住的搜索页，同名的搜索结果仍在语义树里，按标签排除。
        val searchResult = hasTestTag("settingsSearch.result.appearance.showSearchHotSearch")
        composeRule
            .onNode(hasText("搜索界面显示热搜") and !searchResult and !hasAnyAncestor(searchResult))
            .assertIsDisplayed()
        assertEquals(rootEntryId, composeRule.activity.navHost.topEntryId)
        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.onNodeWithTag("settingsSearch.result.appearance.showSearchHotSearch").assertIsDisplayed()
        composeRule.onNodeWithTag("settingsSearch.input").assertTextEquals("热搜")
        assertEquals(rootEntryId, composeRule.activity.navHost.topEntryId)
    }

    @Test
    fun questionOpenedFromArticleRemainsInLandscapeListPane() {
        composeRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        composeRule.launchZhihuMainWithFakeArticle()

        val article = Article(type = ArticleType.Answer, id = 318L)
        composeRule.runOnIdle {
            composeRule.activity.navigate(article)
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule
                .onAllNodes(hasTestTag("article_question_link"))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeRule.onNodeWithTag("article_question_link").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule
                .onAllNodes(hasTestTag(QUESTION_SCREEN_LIST_TAG))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        val listPaneBounds = composeRule
            .onNodeWithTag("list_pane")
            .fetchSemanticsNode()
            .boundsInRoot
        val questionBounds = composeRule
            .onNodeWithTag(QUESTION_SCREEN_LIST_TAG)
            .fetchSemanticsNode()
            .boundsInRoot
        val articleBounds = composeRule
            .onNodeWithTag("article_content")
            .fetchSemanticsNode()
            .boundsInRoot
        assertTrue(
            "问题回答列表应位于左栏：question=$questionBounds list=$listPaneBounds",
            questionBounds.right <= listPaneBounds.right + 0.5f,
        )
        assertTrue(
            "文章正文应从右栏开始：article=$articleBounds list=$listPaneBounds",
            articleBounds.left >= listPaneBounds.right,
        )
        composeRule.onNodeWithTag("article_content").assertIsDisplayed()
    }

    @Test
    fun questionUsesSinglePaneWhenPortrait() {
        composeRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        composeRule.launchZhihuMainWithFakeArticle()

        composeRule.runOnIdle {
            composeRule.activity.navigate(Article(type = ArticleType.Answer, id = 318L))
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule
                .onAllNodes(hasTestTag("article_question_link"))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithTag("article_question_link").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule
                .onAllNodes(hasTestTag(QUESTION_SCREEN_LIST_TAG))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        val listPaneBounds = composeRule
            .onNodeWithTag("list_pane")
            .fetchSemanticsNode()
            .boundsInRoot
        val questionBounds = composeRule
            .onNodeWithTag(QUESTION_SCREEN_LIST_TAG)
            .fetchSemanticsNode()
            .boundsInRoot
        assertEquals(listPaneBounds.left, questionBounds.left, 0.5f)
        assertEquals(listPaneBounds.right, questionBounds.right, 0.5f)
    }

    /**
     * 双栏导航行为依据：https://github.com/zly2006/zhihu-plus-plus/issues/680
     * 从窄屏开始阅读再展开时，也必须保留原详情的保存状态，不能重新创建导航项。
     */
    @Test
    fun expandingInitiallyNarrowArticlePreservesState() {
        val width = mutableStateOf(400.dp)
        composeRule.launchZhihuMainWithFakeArticle(width = { width.value })
        composeRule.runOnIdle { composeRule.activity.navigate(Article(type = ArticleType.Answer, id = 318L)) }
        composeRule.onNodeWithTag("article_state").performClick()
        composeRule.onNodeWithText("状态已保留").assertIsDisplayed()
        composeRule.runOnIdle { width.value = 1000.dp }
        composeRule.onNodeWithText("状态已保留").assertIsDisplayed()
    }

    /**
     * 双栏导航行为依据：https://github.com/zly2006/zhihu-plus-plus/issues/680
     * 页面隐藏时独立窗口必须同步隐藏，返回后恢复窗口，避免评论窗口遮住跳转目标。
     */
    @Test
    fun hiddenDetailDismissesWindowAndRestoresItOnBack() {
        val width = mutableStateOf(1000.dp)
        composeRule.launchZhihuMainWithFakeArticle(width = { width.value })
        composeRule.runOnIdle { composeRule.activity.navigate(Article(type = ArticleType.Answer, id = 318L)) }
        composeRule.runOnIdle { width.value = 400.dp }
        composeRule.onNodeWithTag("article_dialog").performClick()
        composeRule.onNodeWithText("从弹窗打开问题").performClick()
        composeRule.onNodeWithText("从弹窗打开问题").assertDoesNotExist()
        composeRule.onNodeWithTag(QUESTION_SCREEN_LIST_TAG).assertIsDisplayed()
        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.onNodeWithText("从弹窗打开问题").assertIsDisplayed()

        composeRule.runOnIdle {
            width.value = 1000.dp
            composeRule.activity.navigate(WriteQuestion)
        }
        composeRule.onNodeWithTag(WRITE_QUESTION_TITLE_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("从弹窗打开问题").assertDoesNotExist()
        composeRule.onNodeWithTag(WRITE_QUESTION_BACK_BUTTON_TAG).performClick()
        composeRule.onNodeWithText("从弹窗打开问题").assertIsDisplayed()
    }

    /**
     * 双栏导航行为依据：https://github.com/zly2006/zhihu-plus-plus/issues/680
     * 阅读中收窄窗口后打开问题必须显示问题，返回时恢复原文章，覆盖真实双栈切换。
     */
    @Test
    fun narrowArticleOpensQuestionAndBackRestoresArticle() {
        val width = mutableStateOf(1000.dp)
        composeRule.launchZhihuMainWithFakeArticle(width = { width.value })
        composeRule.runOnIdle { composeRule.activity.navigate(Article(type = ArticleType.Answer, id = 318L)) }
        composeRule.onNodeWithTag("article_question_link").assertIsDisplayed()
        composeRule.runOnIdle { width.value = 400.dp }
        composeRule.onNodeWithTag("article_question_link").performClick()
        composeRule.onNodeWithTag("article_content").assertIsNotDisplayed()
        composeRule.onNodeWithTag(QUESTION_SCREEN_LIST_TAG).assertIsDisplayed()
        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.onNodeWithTag("article_content").assertIsDisplayed()
    }

    /**
     * 双栏导航行为依据：https://github.com/zly2006/zhihu-plus-plus/issues/680
     * 横竖窗口切换保留双栈条目、正文保存状态和分栏比例；宽竖屏使用底栏而非侧栏。
     */
    @Test
    fun resizingPreservesDetailSaveableState() {
        val width = mutableStateOf(1000.dp)
        val height = mutableStateOf(600.dp)
        var questionHost: AppNavHost? = null
        val preferences = composeRule.activity.getSharedPreferences(PREFERENCE_NAME, android.content.Context.MODE_PRIVATE)
        preferences.edit(commit = true) {
            putBoolean(FLOATING_BOTTOM_BAR_PREFERENCE_KEY, false)
            putBoolean("enable_predictive_back", true)
            putFloat(LANDSCAPE_LIST_PANE_RATIO_KEY, 0.4f)
        }
        composeRule.launchZhihuMainWithFakeArticle(
            width = { width.value },
            onQuestionHost = { questionHost = it },
            height = { height.value },
        )
        val landscapeSize = composeRule.onNodeWithTag("zhihu_main_window").fetchSemanticsNode().size
        assertEquals(with(composeRule.density) { 1000.dp.roundToPx() }, landscapeSize.width)
        assertEquals(with(composeRule.density) { 600.dp.roundToPx() }, landscapeSize.height)
        composeRule.onNodeWithTag("nav_rail").assertIsDisplayed()
        composeRule.onNode(hasTestTag("nav_tab_home").and(hasAnyAncestor(hasTestTag("nav_rail")))).assertIsSelected()
        val rootEntries = composeRule.runOnIdle {
            composeRule.activity.navHost.pageEntries
                .toList()
        }

        composeRule.runOnIdle { composeRule.activity.navigate(Article(type = ArticleType.Answer, id = 318L)) }
        composeRule.onNodeWithTag("article_state").performClick()
        composeRule.onNodeWithText("状态已保留").assertIsDisplayed()
        val readingEntries = composeRule.runOnIdle {
            composeRule.activity.navHost.pageEntries
                .toList()
        }
        val dividerBounds = composeRule.onNodeWithTag("list_detail_divider").fetchSemanticsNode().boundsInRoot
        val readingSize = composeRule.onNodeWithTag("article_content").fetchSemanticsNode().size
        val mainHost = composeRule.runOnIdle { composeRule.activity.navHost }
        composeRule.runOnIdle { composeRule.activity.navigate(Login) }
        val loginEntries = composeRule.runOnIdle {
            assertEquals(Login, mainHost.top)
            val entries = mainHost.pageEntries.toList()
            assertEquals(readingEntries, entries.dropLast(1))
            readingEntries.zip(entries).forEach { (before, after) -> assertSame(before, after) }
            entries
        }
        val loginPage = composeRule.onNode(
            hasScrollAction().and(hasAnyDescendant(hasTestTag("login_notice_step_1"))),
            useUnmergedTree = true,
        )
        assertEquals(landscapeSize, loginPage.fetchSemanticsNode().size)
        assertEquals(
            composeRule
                .onNodeWithTag("zhihu_main_window")
                .getUnclippedBoundsInRoot()
                .left.value,
            loginPage.getUnclippedBoundsInRoot().left.value,
            0.5f,
        )
        composeRule.onNodeWithTag("list_detail_divider").assertDoesNotExist()
        composeRule.runOnIdle {
            width.value = 600.dp
            height.value = 1000.dp
        }
        assertEquals(with(composeRule.density) { 600.dp.roundToPx() }, loginPage.fetchSemanticsNode().size.width)
        composeRule.runOnIdle {
            width.value = 1000.dp
            height.value = 600.dp
        }
        assertEquals(landscapeSize, loginPage.fetchSemanticsNode().size)
        composeRule.runOnIdle {
            assertSame(mainHost, composeRule.activity.navHost)
            assertEquals(loginEntries, mainHost.pageEntries.toList())
            loginEntries.zip(mainHost.pageEntries.toList()).forEach { (before, after) -> assertSame(before, after) }
        }
        composeRule.mainClock.autoAdvance = false
        try {
            composeRule.onNodeWithTag("login_back").performClick()
            composeRule.mainClock.advanceTimeByFrame()
            assertEquals(landscapeSize, loginPage.fetchSemanticsNode().size)
            assertEquals(readingSize, composeRule.onNodeWithTag("article_content").fetchSemanticsNode().size)
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("login_back").assertDoesNotExist()
        composeRule.onNodeWithText("状态已保留").assertIsDisplayed()
        assertEquals(
            dividerBounds.left,
            composeRule
                .onNodeWithTag("list_detail_divider")
                .fetchSemanticsNode()
                .boundsInRoot.left,
            0.5f,
        )
        composeRule.runOnIdle {
            assertEquals(readingEntries, mainHost.pageEntries.toList())
            readingEntries.zip(mainHost.pageEntries.toList()).forEach { (before, after) -> assertSame(before, after) }
        }
        composeRule.runOnIdle {
            width.value = 600.dp
            height.value = 1000.dp
        }
        val portraitSize = composeRule.onNodeWithTag("zhihu_main_window").fetchSemanticsNode().size
        assertEquals(with(composeRule.density) { 600.dp.roundToPx() }, portraitSize.width)
        assertEquals(with(composeRule.density) { 1000.dp.roundToPx() }, portraitSize.height)
        composeRule.onNodeWithText("状态已保留").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_rail").assertDoesNotExist()
        composeRule.onNodeWithTag("list_detail_divider").assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(
                readingEntries,
                composeRule.activity.navHost.pageEntries
                    .toList(),
            )
        }
        composeRule.runOnIdle {
            width.value = 1000.dp
            height.value = 600.dp
        }
        composeRule.onNodeWithText("状态已保留").assertIsDisplayed()
        assertEquals(
            dividerBounds.left,
            composeRule
                .onNodeWithTag("list_detail_divider")
                .fetchSemanticsNode()
                .boundsInRoot.left,
            0.5f,
        )

        composeRule.onNodeWithTag("article_question_link").performClick()
        composeRule.onNodeWithTag(QUESTION_SCREEN_LIST_TAG).assertIsDisplayed()
        val detailHost = checkNotNull(questionHost)
        val questionEntries = composeRule.runOnIdle {
            assertTrue(detailHost !== composeRule.activity.navHost)
            assertEquals(
                readingEntries,
                composeRule.activity.navHost.pageEntries
                    .toList(),
            )
            detailHost.pageEntries.toList()
        }
        composeRule.runOnIdle {
            width.value = 600.dp
            height.value = 1000.dp
        }
        composeRule.onNodeWithTag(QUESTION_SCREEN_LIST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag("article_content").assertIsNotDisplayed()
        composeRule.runOnIdle {
            assertEquals(
                readingEntries,
                composeRule.activity.navHost.pageEntries
                    .toList(),
            )
            assertEquals(questionEntries, detailHost.pageEntries.toList())
            width.value = 1000.dp
            height.value = 600.dp
        }
        composeRule.onNodeWithTag(QUESTION_SCREEN_LIST_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("状态已保留").assertIsDisplayed()
        assertEquals(
            dividerBounds.left,
            composeRule
                .onNodeWithTag("list_detail_divider")
                .fetchSemanticsNode()
                .boundsInRoot.left,
            0.5f,
        )
        composeRule.runOnIdle {
            assertEquals(
                readingEntries,
                composeRule.activity.navHost.pageEntries
                    .toList(),
            )
            assertEquals(questionEntries, detailHost.pageEntries.toList())
            assertEquals(0.4f, preferences.getFloat(LANDSCAPE_LIST_PANE_RATIO_KEY, 0f), 0.0001f)
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            detailHost.top == EmptyDetail &&
                composeRule.onAllNodesWithTag(QUESTION_SCREEN_LIST_TAG).fetchSemanticsNodes().isEmpty()
        }
        composeRule.runOnIdle { assertEquals(EmptyDetail, detailHost.top) }
        composeRule.onNodeWithTag("article_content").assertIsDisplayed()
        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitUntilTabSelected("nav_tab_home")
        composeRule.runOnIdle {
            assertEquals(
                rootEntries,
                composeRule.activity.navHost.pageEntries
                    .toList(),
            )
            width.value = 600.dp
            height.value = 1000.dp
        }
        composeRule.onNodeWithTag("nav_rail").assertDoesNotExist()
        composeRule.onNodeWithTag("nav_tab_home").assertIsSelected()
        /** 人工窗口可超出设备根视口，底栏结构按未裁剪几何验证。 */
        val homeBounds = composeRule.onNodeWithTag("nav_tab_home").getUnclippedBoundsInRoot()
        val accountBounds = composeRule.onNodeWithTag("nav_tab_account").getUnclippedBoundsInRoot()
        assertTrue(homeBounds.right - homeBounds.left > 0.dp && homeBounds.bottom - homeBounds.top > 0.dp)
        assertEquals(homeBounds.top.value, accountBounds.top.value, 0.5f)
        assertTrue(accountBounds.left >= homeBounds.right)
    }

    /**
     * 双栏导航行为依据：https://github.com/zly2006/zhihu-plus-plus/issues/680
     * 视频平台入口必须收到发起操作的文章控制器，不能从左侧首页推导内容身份。
     */
    @Test
    fun detailVideoUsesArticleController() {
        var videoSource: Article? = null
        composeRule.launchZhihuMainWithFakeArticle(width = { 1000.dp }, onVideo = { controller ->
            videoSource = controller.top as? Article
        })
        val article = Article(type = ArticleType.Answer, id = 318L)
        composeRule.runOnIdle { composeRule.activity.navigate(article) }
        composeRule.onNodeWithTag("article_video_link").performClick()
        composeRule.runOnIdle { assertEquals(article, videoSource) }
    }

    /**
     * 双栏导航行为依据：https://github.com/zly2006/zhihu-plus-plus/issues/680
     * 混合听读队列从回答切换到问题时，必须打开问题而不是跳入未注册该路由的详情图。
     * 关闭队列弹层使用其语义动作，不依赖系统返回键的窗口分发时序。
     */
    @Test
    fun readingQueueCanSwitchFromDetailToQuestion() {
        val width = mutableStateOf(1000.dp)
        var questionHost: AppNavHost? = null
        val player = ReadingPlayerState(
            status = ReadingPlaybackStatus.Playing,
            queue = listOf(
                ReadingQueueItem(ReadingContentType.Answer, 318L),
                ReadingQueueItem(ReadingContentType.Question, 318L, title = "测试问题"),
            ),
            currentIndex = 0,
        )
        try {
            AndroidReadingPlayerBridge.publish(player)
            composeRule.launchZhihuMainWithFakeArticle(
                width = { width.value },
                onQuestionHost = { questionHost = it },
            )
            composeRule.runOnIdle { composeRule.activity.navigate(Article(type = ArticleType.Answer, id = 318L)) }
            composeRule.onNodeWithTag("article_content").assertIsDisplayed()
            val readingEntryId = composeRule.runOnIdle { checkNotNull(composeRule.activity.navHost.topEntryId) }
            composeRule.onNodeWithTag("article_state").performClick()
            composeRule.onNodeWithText("状态已保留").assertIsDisplayed()
            composeRule.runOnIdle { width.value = 400.dp }
            composeRule.onNodeWithTag("reading_player_queue").performTouchInput { click() }
            composeRule.onNodeWithTag("reading_queue_sheet").assertIsDisplayed()
            composeRule
                .onNode(
                    SemanticsMatcher
                        .keyIsDefined(SemanticsActions.Dismiss)
                        .and(hasAnyAncestor(hasTestTag(READING_QUEUE_SHEET_TAG))),
                    useUnmergedTree = true,
                ).performSemanticsAction(SemanticsActions.Dismiss)
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule
                    .onAllNodesWithTag("reading_queue_sheet")
                    .fetchSemanticsNodes(atLeastOneRootRequired = false)
                    .isEmpty()
            }
            composeRule.runOnIdle { AndroidReadingPlayerBridge.publish(player.copy(currentIndex = 1)) }
            composeRule.waitUntil(timeoutMillis = 5_000) { questionHost?.top is Question }
            composeRule.onNodeWithTag(QUESTION_SCREEN_LIST_TAG).assertIsDisplayed()
            composeRule.onNodeWithTag("article_content").assertIsNotDisplayed()
            val detailHost = checkNotNull(questionHost)
            val questionEntryId = composeRule.runOnIdle {
                assertTrue("问题必须使用次级导航宿主", detailHost !== composeRule.activity.navHost)
                assertEquals(readingEntryId, composeRule.activity.navHost.topEntryId)
                checkNotNull(detailHost.topEntryId)
            }
            val narrowQuestionBounds = composeRule.onNodeWithTag(QUESTION_SCREEN_LIST_TAG).fetchSemanticsNode().boundsInRoot
            val narrowListBounds = composeRule.onNodeWithTag("list_pane").fetchSemanticsNode().boundsInRoot
            assertEquals(narrowListBounds.left, narrowQuestionBounds.left, 0.5f)
            assertEquals(narrowListBounds.right, narrowQuestionBounds.right, 0.5f)
            composeRule.runOnIdle {
                width.value = 1000.dp
                AndroidReadingPlayerBridge.publish(player)
            }
            val expandedWidthPx = with(composeRule.density) { 1000.dp.toPx() }
            var expandedLayoutDiagnostics = "readingEntry=$readingEntryId questionEntry=$questionEntryId"
            try {
                /** 自动队列导航跨测量帧，等待原条目在新宽度完成左右栏布局，而非采样中间帧。 */
                composeRule.waitUntil(timeoutMillis = 5_000) {
                    val questionBounds = composeRule
                        .onAllNodesWithTag(QUESTION_SCREEN_LIST_TAG)
                        .fetchSemanticsNodes()
                        .singleOrNull()
                        ?.boundsInRoot
                    val listBounds = composeRule
                        .onAllNodesWithTag("list_pane")
                        .fetchSemanticsNodes()
                        .singleOrNull()
                        ?.boundsInRoot
                    val articleBounds = composeRule
                        .onAllNodesWithTag("article_content")
                        .fetchSemanticsNodes()
                        .singleOrNull()
                        ?.boundsInRoot
                    composeRule.runOnIdle {
                        expandedLayoutDiagnostics = "readingEntry=$readingEntryId questionEntry=$questionEntryId " +
                            "mainEntries=${composeRule.activity.navHost.pageEntries.map { "${it.id}/${it.destination::class.simpleName}" }.toList()} " +
                            "detailEntries=${detailHost.pageEntries.map { "${it.id}/${it.destination::class.simpleName}" }.toList()} " +
                            "narrowQuestion=$narrowQuestionBounds narrowList=$narrowListBounds expandedWidth=$expandedWidthPx " +
                            "expandedQuestion=$questionBounds expandedList=$listBounds expandedArticle=$articleBounds"
                        assertEquals(expandedLayoutDiagnostics, readingEntryId, composeRule.activity.navHost.topEntryId)
                        assertEquals(expandedLayoutDiagnostics, questionEntryId, detailHost.topEntryId)
                    }
                    questionBounds != null &&
                        listBounds != null &&
                        articleBounds != null &&
                        questionBounds.width > 0f &&
                        questionBounds.height > 0f &&
                        listBounds.width > 0f &&
                        listBounds.height > 0f &&
                        articleBounds.width > 0f &&
                        articleBounds.height > 0f &&
                        questionBounds.left in (listBounds.left - 0.5f)..(listBounds.left + 0.5f) &&
                        questionBounds.right in (listBounds.right - 0.5f)..(listBounds.right + 0.5f) &&
                        articleBounds.left >= listBounds.right &&
                        articleBounds.right - listBounds.left in (expandedWidthPx - 0.5f)..(expandedWidthPx + 0.5f)
                }
            } catch (error: androidx.compose.ui.test.ComposeTimeoutException) {
                throw AssertionError("展开后的朗读队列布局未稳定：$expandedLayoutDiagnostics", error)
            }
            composeRule.onNodeWithTag("article_content").assertIsDisplayed()
            composeRule.onNodeWithTag(QUESTION_SCREEN_LIST_TAG).assertIsDisplayed()
            composeRule.onNodeWithText("状态已保留").assertIsDisplayed()
            val questionNode = composeRule.onNodeWithTag(QUESTION_SCREEN_LIST_TAG).fetchSemanticsNode()
            val listNode = composeRule.onNodeWithTag("list_pane").fetchSemanticsNode()
            val articleNode = composeRule.onNodeWithTag("article_content").fetchSemanticsNode()
            val (questionBounds, listBounds, articleBounds) = composeRule.runOnIdle {
                assertEquals(expandedLayoutDiagnostics, readingEntryId, composeRule.activity.navHost.topEntryId)
                assertEquals(expandedLayoutDiagnostics, questionEntryId, detailHost.topEntryId)
                Triple(questionNode.boundsInRoot, listNode.boundsInRoot, articleNode.boundsInRoot)
            }
            assertTrue(
                "问题必须保留在左栏：question=$questionBounds list=$listBounds",
                questionBounds.right <= listBounds.right + 0.5f,
            )
            assertTrue(
                "文章必须保留在右栏：article=$articleBounds list=$listBounds",
                articleBounds.left >= listBounds.right,
            )
        } finally {
            AndroidReadingPlayerBridge.publish(ReadingPlayerState())
        }
    }

    /**
     * 收藏标签默认仍应显示原列表；行为依据：https://github.com/zly2006/zhihu-plus-plus/issues/609
     */
    @Test
    fun collectionsTabKeepsLegacyListByDefault() {
        composeRule.launchZhihuMain(
            startDestination = MyCollections.name,
            bottomBarItems = collectionBottomBarItems,
        )

        composeRule.waitUntilTabSelected("nav_tab_mycollections")
        composeRule.onNodeWithTag("collection_screen_title").assertIsDisplayed()
        composeRule.onNodeWithTag("collection_browse_title").assertDoesNotExist()
    }

    /**
     * 仅显式启用后收藏标签才使用直达浏览；行为依据：https://github.com/zly2006/zhihu-plus-plus/issues/609
     */
    @Test
    fun collectionsTabUsesDirectBrowseOnlyWhenEnabled() {
        composeRule.launchZhihuMain(
            startDestination = MyCollections.name,
            bottomBarItems = collectionBottomBarItems,
            collectionDirectBrowseEnabled = true,
        )

        composeRule.waitUntilTabSelected("nav_tab_mycollections")
        composeRule.onNodeWithTag("collection_browse_title").assertIsDisplayed()
        composeRule.onNodeWithTag("collection_screen_title").assertDoesNotExist()
    }

    /**
     * 启用自动隐藏时，编程式向下翻页应隐藏底部导航栏，向上翻页应恢复显示。
     */
    @Test
    fun pageTurnHidesAndRevealsBottomBarWhenAutoHideEnabled() {
        composeRule.activity
            .getSharedPreferences(PREFERENCE_NAME, android.content.Context.MODE_PRIVATE)
            .edit(commit = true) {
                putString(START_DESTINATION_PREFERENCE_KEY, Home.name)
                putStringSet(BOTTOM_BAR_ITEMS_PREFERENCE_KEY, deterministicBottomBarItems)
                putBoolean("bottomBarTapScrollToTop", false)
                putBoolean("autoHideBottomBar", true)
                putBoolean(COLLECTION_DIRECT_BROWSE_PREFERENCE_KEY, false)
            }
        val dispatcher = PageTurnDispatcher()
        composeRule.activity.setContent { }
        composeRule.waitForIdle()
        composeRule.activity.setContent {
            ZhihuTheme {
                val navHost = rememberAppNavHost(MainTabs)
                LaunchedEffect(navHost) { composeRule.activity.navHost = navHost }
                CompositionLocalProvider(LocalPageTurnDispatcher provides dispatcher) {
                    AndroidZhihuMain(navHost = navHost)
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.waitUntilTabSelected("nav_tab_home")
        composeRule.onNodeWithTag("nav_tab_home").assertIsDisplayed()

        assertTrue(dispatcher.dispatch(PageTurnCommand.PageDown))
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("nav_tab_home").fetchSemanticsNodes().isEmpty()
        }

        assertTrue(dispatcher.dispatch(PageTurnCommand.PageUp))
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("nav_tab_home").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("nav_tab_home").assertIsDisplayed()

        val rootEntryId = composeRule.runOnIdle { composeRule.activity.navHost.topEntryId }
        composeRule.runOnIdle { composeRule.activity.navigate(WriteQuestion) }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag(WRITE_QUESTION_TITLE_TAG).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(WRITE_QUESTION_TITLE_TAG).assertIsDisplayed()
        composeRule.runOnIdle {
            assertFalse(dispatcher.hasActiveTarget)
            assertFalse(dispatcher.dispatch(PageTurnCommand.PageDown))
        }
        composeRule.onNodeWithTag(WRITE_QUESTION_BACK_BUTTON_TAG).performClick()
        composeRule.waitUntilTabSelected("nav_tab_home")
        composeRule.runOnIdle {
            assertEquals(rootEntryId, composeRule.activity.navHost.topEntryId)
            assertTrue(dispatcher.hasActiveTarget)
        }
    }

    private fun MainActivityComposeRule.launchZhihuMain(
        startDestination: String,
        bottomBarItems: Set<String> = deterministicBottomBarItems,
        collectionDirectBrowseEnabled: Boolean = false,
    ) {
        activity.getSharedPreferences(PREFERENCE_NAME, android.content.Context.MODE_PRIVATE).edit(commit = true) {
            putString(START_DESTINATION_PREFERENCE_KEY, startDestination)
            putStringSet(BOTTOM_BAR_ITEMS_PREFERENCE_KEY, bottomBarItems)
            putBoolean("bottomBarTapScrollToTop", false)
            putBoolean("autoHideBottomBar", false)
            putBoolean(COLLECTION_DIRECT_BROWSE_PREFERENCE_KEY, collectionDirectBrowseEnabled)
        }
        setZhihuMainContent()
    }

    private fun MainActivityComposeRule.launchZhihuMainWithFakeArticle(
        width: (() -> Dp)? = null,
        onVideo: ((AppNavHost) -> Unit)? = null,
        onQuestionHost: ((AppNavHost) -> Unit)? = null,
        height: () -> Dp = { 600.dp },
    ) {
        activity.getSharedPreferences(PREFERENCE_NAME, android.content.Context.MODE_PRIVATE).edit(commit = true) {
            putString(START_DESTINATION_PREFERENCE_KEY, Home.name)
            putStringSet(BOTTOM_BAR_ITEMS_PREFERENCE_KEY, deterministicBottomBarItems)
            putBoolean("bottomBarTapScrollToTop", false)
            putBoolean("autoHideBottomBar", false)
            putBoolean(COLLECTION_DIRECT_BROWSE_PREFERENCE_KEY, false)
        }
        activity.setContent { }
        waitForIdle()
        activity.setContent {
            ZhihuTheme {
                val navHost = rememberAppNavHost(MainTabs)
                LaunchedEffect(navHost) {
                    activity.navHost = navHost
                }
                ZhihuMain(
                    modifier = (width?.let { Modifier.wrapContentSize(Alignment.TopStart, unbounded = true).requiredSize(it(), height()) } ?: Modifier)
                        .testTag("zhihu_main_window"),
                    navHost = navHost,
                    mainTabNavigationTarget = activity.mainTabNavigationTarget,
                    navigate = { destination ->
                        if (destination is Video && onVideo != null) onVideo(navHost) else activity.navigate(destination)
                    },
                    navigateContent = { destination, controller ->
                        if (destination is Question) onQuestionHost?.invoke(controller)
                        if (destination is Video && onVideo != null) onVideo(controller) else activity.navigate(destination, controller)
                    },
                    enableLandscapeListDetail = true,
                    setCurrentMainTabOpenFrom = activity::setCurrentMainTabOpenFrom,
                    consumeMainTabNavigationTarget = activity::consumeMainTabNavigationTarget,
                    preferenceState = rememberZhihuMainPreferenceState(),
                    isDarkTheme = ThemeManager.isDarkTheme,
                    articleContent = { _ ->
                        val navigator = LocalNavigator.current
                        var expanded by rememberSaveable { mutableStateOf(false) }
                        var dialogVisible by rememberSaveable { mutableStateOf(false) }
                        if (dialogVisible) {
                            AlertDialog(
                                onDismissRequest = { dialogVisible = false },
                                text = { Text("详情窗口") },
                                confirmButton = {
                                    Button(onClick = { navigator.onNavigate(Question(318L, "测试问题")) }) {
                                        Text("从弹窗打开问题")
                                    }
                                },
                            )
                        }
                        Column(Modifier.fillMaxSize().testTag("article_content")) {
                            Button(onClick = { dialogVisible = true }, modifier = Modifier.testTag("article_dialog")) {
                                Text("打开窗口")
                            }
                            Button(onClick = { expanded = true }, modifier = Modifier.testTag("article_state")) {
                                Text(if (expanded) "状态已保留" else "更改状态")
                            }
                            Button(onClick = { navigator.onNavigate(Video(1L)) }, modifier = Modifier.testTag("article_video_link")) {
                                Text("播放视频")
                            }
                            Button(
                                onClick = {
                                    navigator.onNavigate(Question(318L, "测试问题"))
                                },
                                modifier = Modifier.testTag("article_question_link"),
                            ) {
                                Text("打开问题")
                            }
                        }
                    },
                )
            }
        }
        waitForIdle()
    }

    private val collectionBottomBarItems = linkedSetOf(
        Home.name,
        Follow.name,
        MyCollections.name,
        Account.name,
    )

    private fun MainActivityComposeRule.waitUntilTabSelected(tag: String) {
        waitUntil(timeoutMillis = 5_000) {
            onAllNodes(hasTestTag(tag).and(isSelectedMatcher()))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun isSelectedMatcher(): SemanticsMatcher = SemanticsMatcher.expectValue(
        SemanticsProperties.Selected,
        true,
    )
}
