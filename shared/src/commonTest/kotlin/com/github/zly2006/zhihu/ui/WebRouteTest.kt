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

import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.Follow
import com.github.zly2006.zhihu.navigation.Notification
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.navigation.Search
import com.github.zly2006.zhihu.navigation.WebPage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WebRouteTest {
    @Test
    fun publicAddressRestoresThePageAndContextWithoutPrivatePayloads() {
        val answer = Article(title = "private title", type = ArticleType.Answer, id = 23, readingQueueSourceId = "private queue")
        val route = WebRoute(answer, Follow, Question(12, "private source"))
        val hash = "#/answer/23?tab=follow&from=question%2F12"
        assertEquals(hash, route.hash())
        assertEquals(route, WebRoute.parse(hash))
        val query = WebRoute(Search("C++ 知乎/#&%"))
        assertEquals("#/search?q=C%2B%2B%20%E7%9F%A5%E4%B9%8E%2F%23%26%25", query.hash())
        assertEquals(query, WebRoute.parse(checkNotNull(query.hash())))
        assertNull(WebRoute(WebPage("https://example.org/?token=private")).hash())
        assertNull(WebRoute(Notification.Message("private peer")).hash())
        for (invalid in listOf("#/answer/-1", "#/search?q=%GG", "#/search?q=a&q=b", "#/settings/system?token=private")) {
            assertNull(WebRoute.parse(invalid), invalid)
        }
    }
}
