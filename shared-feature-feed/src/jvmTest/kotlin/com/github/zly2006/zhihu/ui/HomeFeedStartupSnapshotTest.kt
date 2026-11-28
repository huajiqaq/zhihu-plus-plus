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

package com.github.zly2006.zhihu.ui

import com.github.zly2006.zhihu.data.FeedDisplayItem
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.viewmodel.FeedDisplaySettings
import com.github.zly2006.zhihu.viewmodel.QualityFilterMode
import com.github.zly2006.zhihu.viewmodel.filter.BlockedKeyword
import com.github.zly2006.zhihu.viewmodel.filter.FeedFilterSettings
import com.github.zly2006.zhihu.viewmodel.filter.HomeFeedFilterPolicy
import com.github.zly2006.zhihu.viewmodel.filter.getContentFilterDatabase
import com.github.zly2006.zhihu.viewmodel.filter.readFeedFilterRules
import com.github.zly2006.zhihu.viewmodel.filter.recheckHomeFeedItems
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class HomeFeedStartupSnapshotTest {
    private val display = FeedDisplaySettings(QualityFilterMode.HIDE)
    private val filters = FeedFilterSettings(enableNlpBlocking = false)

    @Test
    fun offlineRecheckAppliesNewKeywordWithoutRecordingExposure() = runTest {
        val directory = createTempDirectory("home-feed-recheck").toFile()
        val database = getContentFilterDatabase(directory.resolve("filter.db"))
        try {
            val items = listOf(item(2), item(3))
            val policy = HomeFeedFilterPolicy(display, filters, readFeedFilterRules(database))
            assertEquals(
                listOf("synthetic-2", "synthetic-3"),
                recheckHomeFeedItems(items, policy, database).map { it.title },
            )
            database.blockedKeywordDao().insertKeyword(BlockedKeyword(keyword = "body-3"))
            assertEquals(
                listOf("synthetic-2"),
                recheckHomeFeedItems(items, policy.copy(rules = readFeedFilterRules(database)), database).map { it.title },
            )
            assertEquals(0, database.contentFilterDao().getRecordCount())
        } finally {
            database.closeDatabase()
            directory.deleteRecursively()
        }
    }

    private fun item(id: Long): FeedDisplayItem = FeedDisplayItem(
        title = "synthetic-$id",
        summary = "body-$id",
        details = "",
        feed = null,
        destination = Article(type = ArticleType.Answer, id = id),
    )
}
