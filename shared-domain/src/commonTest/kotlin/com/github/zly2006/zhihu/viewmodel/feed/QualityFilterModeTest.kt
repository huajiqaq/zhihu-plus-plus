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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.github.zly2006.zhihu.viewmodel.feed

import com.github.zly2006.zhihu.data.CommonFeed
import com.github.zly2006.zhihu.data.Feed
import com.github.zly2006.zhihu.data.Person
import com.github.zly2006.zhihu.data.QualityFilterSettings
import com.github.zly2006.zhihu.data.navDestination
import com.github.zly2006.zhihu.viewmodel.FeedDisplaySettings
import com.github.zly2006.zhihu.viewmodel.QualityFilterMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QualityFilterModeTest {
    private val lowQualityArticle = CommonFeed(
        target = Feed.ArticleTarget(
            id = 637,
            url = "https://zhuanlan.zhihu.com/p/637",
            author = Person(
                id = "author",
                url = "https://api.zhihu.com/people/author",
                userType = "people",
                name = "作者",
                headline = "",
                avatarUrl = "",
                followersCount = 0,
                isFollowing = false,
            ),
            voteupCount = 0,
            title = "低赞文章",
        ),
    )

    @Test
    fun offModeKeepsOriginalCard() {
        val item = HomeFeedViewModel().createDisplayItem(
            FeedDisplaySettings(qualityFilterMode = QualityFilterMode.OFF),
            lowQualityArticle,
        )

        assertEquals("低赞文章", item.title)
        assertFalse(item.isFiltered)
        assertFalse(item.isQualityFiltered)
    }

    @Test
    fun rulesAndHideModesMarkQualityFilteredCards() {
        listOf(QualityFilterMode.RULES, QualityFilterMode.HIDE).forEach { mode ->
            val item = HomeFeedViewModel().createDisplayItem(
                FeedDisplaySettings(qualityFilterMode = mode),
                lowQualityArticle,
            )

            assertEquals("低赞文章", item.title)
            assertEquals(lowQualityArticle.target!!.navDestination, item.navDestination)
            assertEquals("作者", item.authorName)
            assertTrue(item.isFiltered)
            assertTrue(item.isQualityFiltered)
            assertTrue(item.content.orEmpty().isNotBlank())
        }
    }

    @Test
    fun customArticleVoteupThresholdIsApplied() {
        val item = HomeFeedViewModel().createDisplayItem(
            FeedDisplaySettings(qualityFilter = QualityFilterSettings(articleVoteupCount = 1000)),
            lowQualityArticle.copy(target = (lowQualityArticle.target as Feed.ArticleTarget).copy(voteupCount = 500)),
        )

        assertTrue(item.isQualityFiltered)
        assertTrue(item.content.orEmpty().contains("1000"))
    }
}
