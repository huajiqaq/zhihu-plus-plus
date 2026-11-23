/*
 * Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation (version 3 only).
 */

package com.github.zly2006.zhihu.ui

import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.github.zly2006.zhihu.filter.ContentOpenFrom
import com.github.zly2006.zhihu.navigation.AppNavHost
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.CollectionContent
import com.github.zly2006.zhihu.navigation.CommentHolder
import com.github.zly2006.zhihu.navigation.EmptyDetail
import com.github.zly2006.zhihu.navigation.Follow
import com.github.zly2006.zhihu.navigation.History
import com.github.zly2006.zhihu.navigation.Home
import com.github.zly2006.zhihu.navigation.MainTabs
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.Notification
import com.github.zly2006.zhihu.navigation.OnlineHistoryRoute
import com.github.zly2006.zhihu.navigation.Pin
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.navigation.Search
import com.github.zly2006.zhihu.navigation.Video
import com.github.zly2006.zhihu.navigation.rememberAppNavHost
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ZhihuMainNavigationCoordinatorTest {
    private val answer = Article(type = ArticleType.Answer, id = 101)
    private val article = Article(type = ArticleType.Article, id = 102)
    private val question = Question(103)
    private val video = Video(104)

    @AfterTest
    fun clearCommentHandoff() {
        CommentNavigationHandoff.clearCommentUnless(MainTabs)
        CommentNavigationHandoff.consumeCommentId(MainTabs)
    }

    private class Fixture(
        val main: AppNavHost,
        val detail: AppNavHost,
        customMainTabs: Boolean,
    ) {
        val prepared = mutableListOf<Pair<NavDestination, String>>()
        val accepted = mutableListOf<NavDestination>()
        val videos = mutableListOf<Triple<Video, String, String>>()
        val messages = mutableListOf<String>()
        val mainTabsHosts = mutableListOf<AppNavHost>()
        val events = mutableListOf<String>()
        private val prepare: (NavDestination, String) -> Unit = { route, source ->
            prepared += route to source
            events += "prepare"
        }
        private val accept: (NavDestination) -> Unit = {
            accepted += it
            events += "accept"
        }
        private val openVideo: (Video, String, String) -> Unit = { route, id, type ->
            videos += Triple(route, id, type)
            events += "video"
        }
        private val showMessage: (String) -> Unit = {
            messages += it
            events += "message"
        }
        val coordinator = if (customMainTabs) {
            ZhihuMainNavigationCoordinator(
                mainHost = { main },
                showMessage = showMessage,
                openVideo = openVideo,
                onRouteAccepted = accept,
                openMainTabs = {
                    mainTabsHosts += it
                    events += "mainTabs"
                },
                prepareContentOpen = prepare,
            )
        } else {
            ZhihuMainNavigationCoordinator(
                mainHost = { main },
                showMessage = showMessage,
                openVideo = openVideo,
                onRouteAccepted = accept,
                prepareContentOpen = prepare,
            )
        }
    }

    private fun withHosts(
        main: List<NavDestination> = listOf(MainTabs),
        detail: List<NavDestination> = listOf(EmptyDetail),
        customMainTabs: Boolean = false,
        check: Fixture.() -> Unit,
    ) = runDesktopComposeUiTest {
        var fixture: Fixture? = null
        setContent {
            val mainHost = rememberAppNavHost(main.first())
            val detailHost = rememberAppNavHost(detail.first())
            fixture = remember(mainHost, detailHost) {
                main.drop(1).forEach(mainHost::open)
                detail.drop(1).forEach(detailHost::open)
                Fixture(mainHost, detailHost, customMainTabs)
            }
        }
        runOnIdle { checkNotNull(fixture).check() }
    }

    @Test
    fun commentHolderOpensArticleOnRequestedHostAndAcceptsItOnce() = withHosts {
        coordinator.navigate(CommentHolder("synthetic-comment", answer), detail)
        assertEquals(listOf(EmptyDetail, answer), detail.backStack)
        assertEquals(listOf(MainTabs), main.backStack)
        assertEquals("synthetic-comment", CommentNavigationHandoff.consumeCommentId(answer))
        assertNull(CommentNavigationHandoff.consumeCommentId(answer))
        assertEquals(answer, accepted.single())
        assertEquals(listOf(answer), prepared.map { it.first })
    }

    @Test
    fun commentRecursionPreservesPopupAndTargetHost() = withHosts(
        detail = listOf(MainTabs, Search()),
    ) {
        coordinator.navigate(CommentHolder("synthetic-comment", article), detail, popup = true)
        assertEquals(listOf(MainTabs, article), detail.backStack)
        assertEquals(listOf(MainTabs), main.backStack)
        assertEquals("synthetic-comment", CommentNavigationHandoff.consumeCommentId(article))
    }

    @Test
    fun unrelatedNavigationClearsPendingComment() = withHosts {
        CommentNavigationHandoff.prepareComment(CommentHolder("synthetic-comment", answer))
        coordinator.navigate(article)
        assertNull(CommentNavigationHandoff.consumeCommentId(answer))
        assertEquals(listOf(MainTabs, article), main.backStack)
    }

    private fun checkVideo(source: NavDestination, id: String, type: String) = withHosts(
        detail = listOf(EmptyDetail, source),
    ) {
        val before = detail.backStack
        coordinator.navigate(video, detail, popup = true)
        assertEquals(listOf(Triple(video, id, type)), videos)
        assertEquals(before, detail.backStack)
        assertEquals(listOf(MainTabs), main.backStack)
        assertTrue(messages.isEmpty())
        assertEquals(listOf(video), prepared.map { it.first })
        assertEquals(video, accepted.single())
        assertEquals(listOf("prepare", "accept", "video"), events)
    }

    @Test
    fun answerVideoUsesAnswerIdentityWithoutNavigating() = checkVideo(answer, "101", "answer")

    @Test
    fun articleVideoUsesArticleIdentityWithoutNavigating() = checkVideo(article, "102", "article")

    @Test
    fun questionVideoUsesQuestionIdentityWithoutNavigating() = checkVideo(question, "103", "question")

    @Test
    fun videoOnUnsupportedPageOnlyShowsFailureAfterAcceptance() = withHosts(
        main = listOf(MainTabs, Search()),
    ) {
        val before = main.backStack
        coordinator.navigate(video, popup = true)
        assertEquals(listOf("无法打开视频：未知的内容类型"), messages)
        assertTrue(videos.isEmpty())
        assertEquals(before, main.backStack)
        assertEquals(listOf(video), prepared.map { it.first })
        assertEquals(video, accepted.single())
        assertEquals(listOf("prepare", "accept", "message"), events)
    }

    @Test
    fun mainTabsRequestsHomeAndPopsMainHostNotDetailHost() = withHosts(
        main = listOf(MainTabs, Search(), answer),
        detail = listOf(EmptyDetail, article),
    ) {
        coordinator.navigate(MainTabs, detail)
        assertEquals(Home, coordinator.mainTabNavigationTarget)
        assertEquals(listOf(MainTabs), main.backStack)
        assertEquals(listOf(EmptyDetail, article), detail.backStack)
        assertEquals(listOf(MainTabs), prepared.map { it.first })
        assertEquals(MainTabs, accepted.single())
        assertEquals(listOf("prepare", "accept"), events)
    }

    @Test
    fun customMainTabsCallbackReplacesDefaultPopAndReceivesMainHost() = withHosts(
        main = listOf(MainTabs, Search()),
        customMainTabs = true,
    ) {
        coordinator.navigate(MainTabs, detail)
        assertSame(main, mainTabsHosts.single())
        assertEquals(listOf(MainTabs, Search()), main.backStack)
        assertEquals(Home, coordinator.mainTabNavigationTarget)
        assertEquals(listOf("prepare", "accept", "mainTabs"), events)
    }

    @Test
    fun consumeOnlyClearsMatchingMainTabRequest() = withHosts {
        coordinator.navigate(MainTabs)
        coordinator.consumeMainTabNavigationTarget(Follow)
        assertEquals(Home, coordinator.mainTabNavigationTarget)
        coordinator.consumeMainTabNavigationTarget(Home)
        assertNull(coordinator.mainTabNavigationTarget)
        coordinator.consumeMainTabNavigationTarget(Home)
        assertNull(coordinator.mainTabNavigationTarget)
    }

    @Test
    fun requestMainTabSetsAndCancelsWithoutNavigationOrHooks() = withHosts(
        main = listOf(MainTabs, Search()),
    ) {
        CommentNavigationHandoff.prepareComment(CommentHolder("synthetic-comment", answer))
        val before = main.backStack
        coordinator.requestMainTab(Follow)
        assertEquals(Follow, coordinator.mainTabNavigationTarget)
        coordinator.requestMainTab(null)
        assertNull(coordinator.mainTabNavigationTarget)
        assertEquals(before, main.backStack)
        assertTrue(prepared.isEmpty())
        assertTrue(accepted.isEmpty())
        assertTrue(mainTabsHosts.isEmpty())
        assertTrue(events.isEmpty())
        assertEquals("synthetic-comment", CommentNavigationHandoff.consumeCommentId(answer))
    }

    @Test
    fun popupClearsTargetStackBeforeOpening() = withHosts(
        main = listOf(MainTabs, Search(), answer),
    ) {
        val nextAnswer = Article(type = ArticleType.Answer, id = 106)
        coordinator.navigate(nextAnswer, popup = true)
        assertEquals(listOf(MainTabs, nextAnswer), main.backStack)
        assertEquals(nextAnswer to ContentOpenFrom.ANSWER_SWITCH, prepared.single())
    }

    @Test
    fun noPopupKeepsExistingTargetStack() = withHosts(
        main = listOf(MainTabs, Search(), answer),
    ) {
        coordinator.navigate(article, popup = false)
        assertEquals(listOf(MainTabs, Search(), answer, article), main.backStack)
    }

    @Test
    fun popupDoesNotClearStackWithoutMainTabs() = withHosts(
        detail = listOf(EmptyDetail, question),
    ) {
        coordinator.navigate(answer, detail, popup = true)
        assertEquals(listOf(EmptyDetail, question, answer), detail.backStack)
        assertEquals(listOf(MainTabs), main.backStack)
    }

    @Test
    fun mainTabSourceOverridesDetailSourceWhileMainTabsIsTop() = withHosts(
        detail = listOf(EmptyDetail, question),
    ) {
        coordinator.setCurrentMainTabOpenFrom(ContentOpenFrom.HOME_FEED)
        coordinator.navigate(answer, detail)
        assertEquals(answer to ContentOpenFrom.HOME_FEED, prepared.single())
    }

    @Test
    fun clearedMainTabSourceFallsBackToDetailSource() = withHosts(
        detail = listOf(EmptyDetail, question),
    ) {
        coordinator.setCurrentMainTabOpenFrom(ContentOpenFrom.HOME_FEED)
        coordinator.setCurrentMainTabOpenFrom(null)
        coordinator.navigate(answer, detail)
        assertEquals(answer to ContentOpenFrom.QUESTION_FEED, prepared.single())
    }

    private fun checkSource(source: NavDestination, expected: String) = withHosts(
        main = listOf(MainTabs, source),
    ) {
        coordinator.setCurrentMainTabOpenFrom(ContentOpenFrom.HOME_FEED)
        coordinator.navigate(answer)
        assertEquals(answer to expected, prepared.single())
    }

    @Test
    fun questionSourceIsQuestionFeed() = checkSource(question, ContentOpenFrom.QUESTION_FEED)

    @Test
    fun collectionSourceIsCollection() = checkSource(CollectionContent("synthetic-collection"), ContentOpenFrom.COLLECTION)

    @Test
    fun localHistorySourceIsHistory() = checkSource(History, ContentOpenFrom.HISTORY)

    @Test
    fun onlineHistorySourceIsHistory() = checkSource(OnlineHistoryRoute, ContentOpenFrom.HISTORY)

    @Test
    fun notificationSourceIsNotification() = checkSource(Notification, ContentOpenFrom.NOTIFICATION)

    @Test
    fun answerToAnswerSourceIsAnswerSwitch() = checkSource(answer, ContentOpenFrom.ANSWER_SWITCH)

    @Test
    fun articleSourceDoesNotBecomeAnswerSwitch() = checkSource(article, ContentOpenFrom.UNKNOWN)

    @Test
    fun pinSourceRemainsUnknown() = checkSource(Pin(105), ContentOpenFrom.UNKNOWN)

    @Test
    fun onlineHistoryFallsBackForDetailWithoutSource() = withHosts(
        main = listOf(MainTabs, OnlineHistoryRoute),
        detail = listOf(EmptyDetail, Search()),
    ) {
        coordinator.navigate(answer, detail)
        assertEquals(answer to ContentOpenFrom.HISTORY, prepared.single())
    }

    @Test
    fun detailSourceTakesPrecedenceOverMainOnlineHistory() = withHosts(
        main = listOf(MainTabs, OnlineHistoryRoute),
        detail = listOf(EmptyDetail, question),
    ) {
        coordinator.navigate(answer, detail)
        assertEquals(answer to ContentOpenFrom.QUESTION_FEED, prepared.single())
    }

    @Test
    fun pinIsASourceSoItBlocksOnlineHistoryFallback() = withHosts(
        main = listOf(MainTabs, OnlineHistoryRoute),
        detail = listOf(EmptyDetail, Pin(105)),
    ) {
        coordinator.navigate(answer, detail)
        assertEquals(answer to ContentOpenFrom.UNKNOWN, prepared.single())
    }

    @Test
    fun noSourceAndNoMainTabOverrideIsUnknown() = withHosts {
        coordinator.navigate(answer, detail)
        assertEquals(answer to ContentOpenFrom.UNKNOWN, prepared.single())
    }
}
