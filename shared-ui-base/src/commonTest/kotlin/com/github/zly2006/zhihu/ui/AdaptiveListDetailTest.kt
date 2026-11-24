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

package com.github.zly2006.zhihu.ui

import androidx.compose.ui.unit.dp
import com.github.zly2006.zhihu.navigation.Account
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.CommentHolder
import com.github.zly2006.zhihu.navigation.Pin
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.navigation.Search
import com.github.zly2006.zhihu.navigation.Video
import com.github.zly2006.zhihu.navigation.isDetailPaneDestination
import com.github.zly2006.zhihu.navigation.isListPaneDestination
import com.github.zly2006.zhihu.navigation.isSecondaryDetailDestination
import com.github.zly2006.zhihu.navigation.readingDestinationOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AdaptiveListDetailTest {
    @Test
    fun defaultRatioMatchesReferenceAndRespectsMinimumWidths() {
        assertEquals((1279f / 3f).dp, normalizedListPaneWidth(1280.dp, DEFAULT_LIST_PANE_RATIO))
        assertEquals(MIN_LIST_PANE_WIDTH, normalizedListPaneWidth(840.dp, 0f))
        assertEquals(359.dp, normalizedListPaneWidth(840.dp, 1f))
    }

    @Test
    fun readablePaneBudgetsControlEligibilityAndClampRequestedRatio() {
        val minimumList = 480.dp
        val minimumDetail = 720.dp
        val boundary = minimumList + LIST_DETAIL_DIVIDER_WIDTH + minimumDetail
        assertFalse(canShowLandscapeListDetail(true, true, true, boundary - 1.dp, 600.dp, minimumList, minimumDetail))
        assertTrue(canShowLandscapeListDetail(true, true, true, boundary, 600.dp, minimumList, minimumDetail))
        assertFalse(canShowLandscapeListDetail(true, true, true, 1000.dp, 600.dp, minimumList, minimumDetail))
        assertEquals(1000.dp, normalizedListPaneWidth(1000.dp, DEFAULT_LIST_PANE_RATIO, minimumList, minimumDetail))
        assertEquals(minimumList, normalizedListPaneWidth(1600.dp, 0f, minimumList, minimumDetail))
        assertEquals(879.dp, normalizedListPaneWidth(1600.dp, 1f, minimumList, minimumDetail))
        assertEquals(0.dp, normalizedListPaneWidth(0.dp, DEFAULT_LIST_PANE_RATIO))
        assertEquals(800.dp, normalizedListPaneWidth(800.dp, DEFAULT_LIST_PANE_RATIO))
        assertEquals(MIN_LIST_PANE_WIDTH, normalizedListPaneWidth(801.dp, DEFAULT_LIST_PANE_RATIO))
    }

    @Test
    fun destinationClassificationPreservesOverlapsAndCommentRecursion() {
        assertTrue(Article(type = ArticleType.Article, id = 1).isDetailPaneDestination())
        assertTrue(Pin(id = 3).isDetailPaneDestination())
        val question = Question(questionId = 2)
        assertFalse(question.isDetailPaneDestination())
        assertSame(question, question.readingDestinationOrNull())
        assertTrue(question.isListPaneDestination())
        assertFalse(Search().isDetailPaneDestination())
        assertTrue(CommentHolder("comment", Article(type = ArticleType.Answer, id = 1)).isDetailPaneDestination())
        assertFalse(CommentHolder("comment", question).isDetailPaneDestination())
        val nestedComments = CommentHolder("outer", CommentHolder("inner", Pin(id = 3)))
        assertTrue(nestedComments.isDetailPaneDestination())
        assertNull(nestedComments.readingDestinationOrNull())
        assertFalse(CommentHolder("outer", CommentHolder("inner", question)).isDetailPaneDestination())

        val blockedHistory = Account.RecommendSettings.BlockedFeedHistory
        assertTrue(blockedHistory.isListPaneDestination())
        assertTrue(blockedHistory.isSecondaryDetailDestination())
        assertFalse(blockedHistory.isDetailPaneDestination())
        assertNull(blockedHistory.readingDestinationOrNull())

        val video = Video(id = 4)
        assertNull(video.readingDestinationOrNull())
        assertFalse(video.isListPaneDestination())
        assertFalse(video.isDetailPaneDestination())
        assertFalse(video.isSecondaryDetailDestination())
    }

    @Test
    fun landscapeListDetailNeedsEnabledPreferenceAndTabletOrDesktopCapability() {
        for ((width, height, expected) in listOf(
            Triple(840.dp, 600.dp, true),
            Triple(839.dp, 600.dp, true),
            Triple(816.dp, 600.dp, true),
            Triple(815.dp, 600.dp, true),
            Triple(801.dp, 600.dp, true),
            Triple(800.dp, 600.dp, false),
            Triple(600.dp, 1000.dp, false),
            Triple(1000.dp, 1200.dp, false),
            Triple(1000.dp, 1000.dp, false),
        )) {
            assertEquals(
                expected,
                canShowLandscapeListDetail(
                    enabled = true,
                    deviceSupportsListDetail = true,
                    preferenceEnabled = true,
                    width = width,
                    height = height,
                ),
            )
        }
        assertTrue(
            canShowLandscapeListDetail(
                enabled = true,
                deviceSupportsListDetail = true,
                preferenceEnabled = true,
                width = 1000.dp,
                height = 600.dp,
            ),
        )
        assertFalse(
            canShowLandscapeListDetail(
                enabled = true,
                deviceSupportsListDetail = false,
                preferenceEnabled = true,
                width = 1000.dp,
                height = 600.dp,
            ),
        )
        assertFalse(
            canShowLandscapeListDetail(
                enabled = true,
                deviceSupportsListDetail = true,
                preferenceEnabled = false,
                width = 1000.dp,
                height = 600.dp,
            ),
        )
    }
}
