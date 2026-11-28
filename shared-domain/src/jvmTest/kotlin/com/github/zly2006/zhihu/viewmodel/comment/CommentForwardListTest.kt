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

package com.github.zly2006.zhihu.viewmodel.comment

import com.github.zly2006.zhihu.account.ZhihuAccountRepository
import com.github.zly2006.zhihu.account.ZhihuAccountStore
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.io.files.Path

@OptIn(ExperimentalCoroutinesApi::class)
class CommentForwardListTest {
    @Test
    fun reopeningChecksServerVisibilityAndNeverFollowsAForeignPage() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val directory = Files.createTempDirectory("comment-forwards-")
        val count = "https://api.zhihu.com/unify-consumption/forward/count?content_type=answer&content_token=42"
        val first = count.replace("/forward/count", "/forward/list")
        val requests = mutableListOf<String>()
        var tab = """{"data":{"is_show_tab":false,"count":23}}"""
        var page = """{"data":[],"paging":{"is_end":true}}"""
        val engine = MockEngine.create {
            this.dispatcher = dispatcher
            addHandler { request ->
                val url = request.url.toString()
                requests += url
                respond(if (url == count) tab else page, headers = headersOf(HttpHeaders.ContentType to listOf("application/json"), HttpHeaders.CacheControl to listOf("no-store")))
            }
        }
        val store = ZhihuAccountStore(ZhihuAccountRepository(Path(directory.toString(), "account.json")), engine)
        try {
            val list = assertNotNull(CommentForwardList.forContent(Article(type = ArticleType.Answer, id = 42), store.client))
            list.loadTab()
            list.loadFirstPage()
            assertEquals(ForwardTab(false, 23), list.tab)
            assertEquals(listOf(count), requests)

            tab = """{"data":{"is_show_tab":true,"count":0}}"""
            list.loadTab(refresh = true)
            assertEquals(listOf(count, count), requests)
            list.loadFirstPage()
            assertEquals(ForwardTab(true, 0), list.tab)
            assertEquals(listOf(count, count, first), requests)
            assertTrue(list.items.isEmpty() && list.isEnd)

            tab = """{"data":{"is_show_tab":true,"count":1}}"""
            val foreignPage = first.replace("api.zhihu.com", "example.invalid") + "&cursor=next&delivered_count=1"
            page = """{"data":[],"paging":{"is_end":false,"next":"$foreignPage"}}"""
            list.loadTab(refresh = true)
            list.loadFirstPage()
            assertNotNull(list.errorMessage)
            assertEquals(listOf(count, count, first, count, first), requests)
        } finally {
            store.close()
            Dispatchers.resetMain()
            check(directory.toFile().deleteRecursively())
        }
    }
}
