/*
 * Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation (version 3 only).
 */

package com.github.zly2006.zhihu.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.zly2006.zhihu.navigation.Account
import com.github.zly2006.zhihu.navigation.AppNavEntry
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.EmptyDetail
import com.github.zly2006.zhihu.navigation.Login
import com.github.zly2006.zhihu.navigation.MainTabs
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.Pin
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.navigation.Search
import com.github.zly2006.zhihu.navigation.Video
import com.github.zly2006.zhihu.navigation.rememberAppNavHost
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ZhihuMainPaneLayoutTest {
    private val article = Article(type = ArticleType.Article, id = 101)
    private val answer = Article(type = ArticleType.Answer, id = 102)
    private val pin = Pin(103)
    private val question = Question(104)
    private val video = Video(105)
    private val source = AppNavEntry.Root("source", MainTabs)

    private fun layout() = ZhihuMainPaneLayout(
        currentDestination = MainTabs,
        currentEntryId = "current",
        detailTop = EmptyDetail,
        detailOwnerEntryId = "current",
        mainListEntry = null,
        mainReadingStartIndex = -1,
        sideRailEligible = true,
        minimumListWidth = 320.dp,
        minimumDetailWidth = 480.dp,
        isLargeLandscape = true,
        enableLandscapeListDetail = true,
    )

    @Test
    fun selectedContentIncludesQuestionButNotVideoOrSettings() {
        listOf(article, answer, pin, question).forEach {
            assertEquals(it, layout().copy(detailTop = it).selectedContentDestination)
        }
        listOf(null, EmptyDetail, video, Account.SettingsSearch).forEach {
            assertNull(layout().copy(detailTop = it).selectedContentDestination)
        }
    }

    @Test
    fun secondaryDetailIncludesNonReadingPagesButExcludesPlaceholder() {
        listOf(article, question, video, Account.SettingsSearch).forEach {
            assertTrue(layout().copy(detailTop = it).hasOpenSecondaryDetail)
        }
        assertFalse(layout().hasOpenSecondaryDetail)
        assertFalse(layout().copy(detailTop = null).hasOpenSecondaryDetail)
    }

    @Test
    fun mainNavigationAndRailRequireMainTabs() {
        assertTrue(layout().showMainNavigation)
        assertTrue(layout().useSideRail)
        assertFalse(layout().copy(sideRailEligible = false).useSideRail)
        listOf(null, Search(), article, question, Login).forEach {
            val pane = layout().copy(currentDestination = it)
            assertFalse(pane.showMainNavigation)
            assertFalse(pane.useSideRail)
        }
    }

    @Test
    fun questionOpenedFromMainReadingKeepsItsOwnerContextAtBothWidths() {
        listOf(article, answer, pin).forEach { reading ->
            listOf(false, true).forEach { wide ->
                val pane = layout().copy(currentDestination = reading, detailTop = question, isLargeLandscape = wide)
                assertTrue(pane.hasMainReadingListContext)
                assertEquals(wide, pane.showMainReadingListContext)
                assertFalse(pane.showDetailPane)
            }
        }
    }

    @Test
    fun mainReadingContextRequiresQuestionReadingOwnerAndMatchingEntry() {
        val context = layout().copy(currentDestination = article, detailTop = question)
        assertFalse(context.copy(detailTop = EmptyDetail).hasMainReadingListContext)
        assertFalse(context.copy(detailTop = article).hasMainReadingListContext)
        assertFalse(context.copy(detailTop = pin).hasMainReadingListContext)
        assertFalse(context.copy(detailTop = video).hasMainReadingListContext)
        listOf(null, MainTabs, question, video).forEach {
            assertFalse(context.copy(currentDestination = it).hasMainReadingListContext)
        }
        assertFalse(context.copy(detailOwnerEntryId = "another").hasMainReadingListContext)
        assertFalse(context.copy(detailOwnerEntryId = null).hasMainReadingListContext)
    }

    @Test
    fun mainStackSplitRequiresWideWindowAndSourceAndYieldsToQuestionContext() {
        val pane = layout().copy(currentDestination = article, mainListEntry = source)
        assertTrue(pane.showMainStackListDetail)
        assertFalse(pane.copy(isLargeLandscape = false).showMainStackListDetail)
        assertFalse(pane.copy(mainListEntry = null).showMainStackListDetail)
        assertFalse(pane.copy(detailTop = question).showMainStackListDetail)
    }

    @Test
    fun listSplitSupportsListsAndQuestionButNotReadingOrVideo() {
        listOf(MainTabs, Search(), question).forEach {
            val pane = layout().copy(currentDestination = it)
            assertTrue(pane.showListDetail)
            assertFalse(pane.copy(isLargeLandscape = false).showListDetail)
            assertFalse(pane.copy(mainListEntry = source).showListDetail)
        }
        listOf(null, article, pin, video, Login).forEach {
            assertFalse(layout().copy(currentDestination = it).showListDetail)
        }
    }

    @Test
    fun wideListCanShowDetailWithoutMatchingOwner() {
        val pane = layout().copy(detailTop = article, detailOwnerEntryId = "another")
        assertTrue(pane.showDetailPane)
        assertTrue(pane.hasOpenDetail)
        assertFalse(pane.copy(isLargeLandscape = false).showDetailPane)
    }

    @Test
    fun narrowDetailRequiresOwnerAndIsSuppressedByMainStackSplit() {
        val pane = layout().copy(isLargeLandscape = false, detailTop = article)
        assertTrue(pane.showDetailPane)
        assertFalse(pane.copy(detailOwnerEntryId = null).showDetailPane)
        assertFalse(pane.copy(detailOwnerEntryId = "another").showDetailPane)
        assertFalse(pane.copy(detailTop = null).showDetailPane)
        assertFalse(pane.copy(detailTop = EmptyDetail).showDetailPane)
        assertFalse(pane.copy(isLargeLandscape = true, mainListEntry = source).showDetailPane)
    }

    @Test
    fun visibleSecondaryPageCountsAsOpenReadingOnlyForReadingDestinations() {
        listOf(article, pin, question).forEach {
            val pane = layout().copy(detailTop = it)
            assertTrue(pane.showDetailPane)
            assertTrue(pane.hasOpenDetail)
            assertFalse(pane.copy(isLargeLandscape = false, detailOwnerEntryId = "another").hasOpenDetail)
        }
        listOf(video, Account.SettingsSearch).forEach {
            val pane = layout().copy(detailTop = it)
            assertTrue(pane.showDetailPane)
            assertFalse(pane.hasOpenDetail)
        }
    }

    @Test
    fun secondaryContentNavigationRequiresEnabledWideList() {
        assertTrue(layout().useSecondaryContentNavigation)
        assertFalse(layout().copy(enableLandscapeListDetail = false).useSecondaryContentNavigation)
        assertFalse(layout().copy(isLargeLandscape = false).useSecondaryContentNavigation)
        assertFalse(layout().copy(mainListEntry = source).useSecondaryContentNavigation)
        assertFalse(layout().copy(currentDestination = article).useSecondaryContentNavigation)
    }

    @Test
    fun readingStatusRecognizesMainReadingAndVisibleDetailButNotVideo() {
        listOf(article, answer, question, pin).forEach {
            assertTrue(layout().copy(currentDestination = it).isOnReadingDetail)
            assertTrue(layout().copy(detailTop = it).isOnReadingDetail)
        }
        listOf(null, MainTabs, video, Account.SettingsSearch).forEach {
            assertFalse(layout().copy(currentDestination = it).isOnReadingDetail)
        }
        assertFalse(layout().copy(detailTop = article, isLargeLandscape = false, detailOwnerEntryId = "another").isOnReadingDetail)
    }

    @Test
    fun articleStatusIncludesAnswersButNotQuestionPinOrVideo() {
        listOf(article, answer).forEach {
            assertTrue(layout().copy(currentDestination = it).isOnArticle)
            assertTrue(layout().copy(detailTop = it).isOnArticle)
            assertFalse(layout().copy(detailTop = it, isLargeLandscape = false, detailOwnerEntryId = "another").isOnArticle)
        }
        listOf(question, pin, video, MainTabs).forEach {
            assertFalse(layout().copy(currentDestination = it).isOnArticle)
            assertFalse(layout().copy(detailTop = it).isOnArticle)
        }
    }

    @Test
    fun tabsRemainVisibleBesideWideDetailButHideUnderNarrowDetail() {
        assertTrue(layout().isMainTabsPaneVisible)
        val pane = layout().copy(detailTop = article)
        assertTrue(pane.isMainTabsPaneVisible)
        assertFalse(pane.copy(isLargeLandscape = false).isMainTabsPaneVisible)
        assertTrue(pane.copy(isLargeLandscape = false, detailOwnerEntryId = "another").isMainTabsPaneVisible)
    }

    @Test
    fun tabsCanBeRetainedAsMainReadingSourceExceptOnLogin() {
        val pane = layout().copy(currentDestination = article, mainListEntry = source)
        assertTrue(pane.isMainTabsPaneVisible)
        assertFalse(pane.copy(isLargeLandscape = false).isMainTabsPaneVisible)
        assertFalse(pane.copy(currentDestination = Login).isMainTabsPaneVisible)
        assertFalse(pane.copy(mainListEntry = AppNavEntry.Page("search", Search())).isMainTabsPaneVisible)
        assertFalse(pane.copy(mainListEntry = null).isMainTabsPaneVisible)
    }

    // The remaining tests exercise the snapshot assembly with real in-memory navigation hosts.
    // No page, account store, platform settings or database is mounted.
    @Composable
    private fun snapshot(
        width: Dp = 2000.dp,
        height: Dp = 500.dp,
        main: List<NavDestination> = listOf(MainTabs),
        detail: NavDestination = EmptyDetail,
        showNavigation: Boolean = true,
        floating: Boolean = false,
        enabled: Boolean = true,
        capable: Boolean = true,
        preferred: Boolean = true,
        railWidth: Dp = 72.dp,
        retainedSource: Boolean = false,
        popReading: Boolean = false,
        removeSource: Boolean = false,
    ): ZhihuMainPaneLayout {
        val host = rememberAppNavHost(main.first())
        val detailHost = rememberAppNavHost(EmptyDetail)
        val state = remember(host, detailHost) {
            main.drop(1).forEach(host::open)
            if (detail != EmptyDetail) detailHost.open(detail)
            ZhihuMainPaneState(mutableStateOf(host.topEntryId), 1f / 3f).apply {
                navigationRailWidth = railWidth
                if (retainedSource) renderedMainListEntry = host.pageEntries.firstOrNull { it.destination is Search }
                if (popReading) host.back()
                if (removeSource) host.back()
            }
        }
        return zhihuMainPaneLayout(host, detailHost, state, width, height, showNavigation, floating, enabled, capable, preferred)
    }

    private fun checkSnapshots(
        content: @Composable () -> List<ZhihuMainPaneLayout>,
        check: (List<ZhihuMainPaneLayout>) -> Unit,
    ) = runDesktopComposeUiTest {
        var result: List<ZhihuMainPaneLayout>? = null
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                result = content()
            }
        }
        runOnIdle { check(checkNotNull(result)) }
    }

    @Test
    fun railEligibilityIncludes600ButExcludesPortraitSquareAndFloatingNavigation() = checkSnapshots(
        content = {
            listOf(
                snapshot(width = 600.dp, height = 599.dp),
                snapshot(width = 599.dp, height = 500.dp),
                snapshot(width = 600.dp, height = 600.dp),
                snapshot(width = 600.dp, height = 601.dp),
                snapshot(showNavigation = false),
                snapshot(floating = true),
            )
        },
        check = { panes -> assertEquals(listOf(true, false, false, false, false, false), panes.map { it.sideRailEligible }) },
    )

    @Test
    fun landscapeSplitRequiresAllThreeGatesAndEnoughLandscapeSpace() = checkSnapshots(
        content = {
            val baseline = snapshot(showNavigation = false)
            val threshold = baseline.minimumListWidth + LIST_DETAIL_DIVIDER_WIDTH + baseline.minimumDetailWidth
            listOf(
                snapshot(width = threshold, height = 1.dp, showNavigation = false),
                snapshot(width = threshold - 1.dp, height = 1.dp, showNavigation = false),
                snapshot(width = threshold, height = threshold, showNavigation = false),
                snapshot(width = threshold, height = threshold + 1.dp, showNavigation = false),
                snapshot(enabled = false),
                snapshot(capable = false),
                snapshot(preferred = false),
            )
        },
        check = { panes -> assertEquals(listOf(true, false, false, false, false, false, false), panes.map { it.isLargeLandscape }) },
    )

    @Test
    fun snapshotFindsSourceBeforeFirstReadingAndCopiesDetailSelection() = checkSnapshots(
        content = {
            listOf(
                snapshot(main = listOf(MainTabs, Search(), article, pin), detail = question),
                snapshot(main = listOf(MainTabs)),
                snapshot(main = listOf(article)),
                snapshot(main = listOf(MainTabs, Login, article)),
                snapshot(main = listOf(MainTabs, question, pin)),
            )
        },
        check = { panes ->
            assertEquals(Search(), panes[0].mainListEntry?.destination)
            assertEquals(2, panes[0].mainReadingStartIndex)
            assertEquals(pin, panes[0].currentDestination)
            assertEquals(question, panes[0].detailTop)
            assertEquals(question, panes[0].selectedContentDestination)
            assertTrue(panes[0].hasMainReadingListContext)
            panes.slice(1..3).forEach {
                assertNull(it.mainListEntry)
                assertEquals(-1, it.mainReadingStartIndex)
            }
            assertEquals(question, panes[4].mainListEntry?.destination)
            assertEquals(2, panes[4].mainReadingStartIndex)
        },
    )

    @Test
    fun exitingReadingRetainsSourceOnlyWhileSourceEntryIsStillInMainStack() = checkSnapshots(
        content = {
            listOf(
                snapshot(main = listOf(MainTabs, Search(), article), retainedSource = true, popReading = true),
                snapshot(main = listOf(MainTabs, Search(), article), retainedSource = true, popReading = true, removeSource = true),
                snapshot(main = listOf(MainTabs, Search(), article), popReading = true),
            )
        },
        check = { panes ->
            assertEquals(Search(), panes[0].mainListEntry?.destination)
            assertEquals(2, panes[0].mainReadingStartIndex)
            panes.drop(1).forEach {
                assertNull(it.mainListEntry)
                assertEquals(-1, it.mainReadingStartIndex)
            }
        },
    )

    @Test
    fun railWidthIsReservedForTabsOrRetainedTabsSourceOnlyWhenEligible() = checkSnapshots(
        content = {
            listOf(
                snapshot(railWidth = 0.dp),
                snapshot(),
                snapshot(main = listOf(MainTabs, article)),
                snapshot(main = listOf(Search())),
                snapshot(floating = true),
            )
        },
        check = { panes ->
            val base = panes[0].minimumListWidth
            assertEquals(base + 72.dp, panes[1].minimumListWidth)
            assertEquals(base + 72.dp, panes[2].minimumListWidth)
            assertEquals(base, panes[3].minimumListWidth)
            assertEquals(base, panes[4].minimumListWidth)
        },
    )

    @Test
    fun fontScaleBelowOneIsClampedAndLargerFontsIncreasePaneBudgets() = checkSnapshots(
        content = {
            listOf(0.75f, 1f, 2f).map { scale ->
                var pane: ZhihuMainPaneLayout? = null
                CompositionLocalProvider(LocalDensity provides Density(1f, scale)) {
                    pane = snapshot(showNavigation = false)
                }
                checkNotNull(pane)
            }
        },
        check = { panes ->
            assertEquals(panes[0].minimumDetailWidth, panes[1].minimumDetailWidth)
            assertEquals(panes[1].minimumDetailWidth * 2, panes[2].minimumDetailWidth)
            assertTrue(panes[0].minimumListWidth >= MIN_LIST_PANE_WIDTH)
            assertTrue(panes[2].minimumListWidth >= MIN_LIST_PANE_WIDTH * 2)
            assertTrue(panes[2].minimumListWidth > panes[1].minimumListWidth)
        },
    )
}
