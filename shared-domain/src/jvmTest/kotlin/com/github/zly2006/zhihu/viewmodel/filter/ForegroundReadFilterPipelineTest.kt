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

package com.github.zly2006.zhihu.viewmodel.filter

import com.github.zly2006.zhihu.data.CommonFeed
import com.github.zly2006.zhihu.data.Feed
import com.github.zly2006.zhihu.data.FeedDisplayItem
import com.github.zly2006.zhihu.data.Person
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class ForegroundReadFilterPipelineTest {
    @Test
    fun blocksRemotelyOpenedItemWithoutPretendingItWasExposedLocally() = runTest {
        val fixture = fixture()
        val item = item("remote", 2)
        fixture.database.contentOpenEventDao().insert(
            ContentOpenEvent(contentType = "article", contentId = "2", openFrom = "remote_sync"),
        )

        assertEquals(
            listOf("article:2"),
            fixture.database.contentOpenEventDao().getRemotelySyncedContentKeysByKeys(listOf("article:2")),
        )
        assertEquals(emptyList(), fixture.pipeline().filter(listOf(item)))
        assertEquals(0, fixture.database.contentFilterDao().getRecordCount())
        fixture.database.closeDatabase()
    }

    private fun fixture(settings: FeedFilterSettings = FeedFilterSettings()): Fixture {
        val database = getContentFilterDatabase(
            createTempDirectory("foreground-read-filter-pipeline").resolve("content-filter.db").toFile(),
        )
        return Fixture(database, settings)
    }

    private class Fixture(
        val database: ContentFilterDatabase,
        val settings: FeedFilterSettings,
    ) {
        fun pipeline(): ForegroundReadFilterPipeline = ForegroundReadFilterPipeline(settings, database)
    }

    private fun item(
        title: String,
        id: Long,
        details: String = "",
        isFollowing: Boolean = false,
    ): FeedDisplayItem = FeedDisplayItem(
        title = title,
        summary = null,
        details = details,
        feed = CommonFeed(
            target = Feed.ArticleTarget(
                id = id,
                url = "",
                author = person(isFollowing),
                title = title,
            ),
        ),
        destination = Article(type = ArticleType.Article, id = id),
    )

    private fun person(isFollowing: Boolean): Person = Person(
        id = "author-id",
        url = "",
        userType = "people",
        name = "author",
        headline = "",
        avatarUrl = "",
        isFollowing = isFollowing,
    )
}
