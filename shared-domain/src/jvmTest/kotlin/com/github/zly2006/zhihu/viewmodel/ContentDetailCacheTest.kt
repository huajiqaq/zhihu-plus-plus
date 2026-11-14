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

package com.github.zly2006.zhihu.viewmodel

import com.github.zly2006.zhihu.account.ZhihuAccountRepository
import com.github.zly2006.zhihu.account.ZhihuAccountSession
import com.github.zly2006.zhihu.account.ZhihuAccountStore
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ContentDetailCacheTest {
    @Test
    fun detailCacheIsBoundToTheAccountClient() = runTest {
        val directory = Files.createTempDirectory("content-detail-cache-").toFile()
        var requests = 0
        val engine = MockEngine { request ->
            requests++
            val cookie = request.headers[HttpHeaders.Cookie].orEmpty()
            val account = when {
                "z_c0=alice" in cookie -> "alice"
                "z_c0=bob" in cookie -> "bob"
                else -> "guest"
            }
            respond(articleResponse("<p>$account</p>"), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val store = ZhihuAccountStore(
            ZhihuAccountRepository(Path(directory.resolve("account.json").path)),
            engine,
        )
        val environment = object : ZhihuApiEnvironment {
            override fun httpClient() = store.client.httpClient()

            override fun authenticatedCookies() = store.session.cookies

            override suspend fun handleFetchFailure(tag: String?, error: Exception) = Unit
        }
        val destination = Article(type = ArticleType.Article, id = 1L)
        try {
            for ((index, account) in listOf("alice", "bob", "guest").withIndex()) {
                if (account == "guest") {
                    store.clear()
                } else {
                    store.replaceSession(ZhihuAccountSession(login = true, username = account, cookies = mutableMapOf("z_c0" to account)))
                }
                val content = environment.getOrFetchContentDetail(destination) as DataHolder.Article
                assertEquals("<p>$account</p>", content.content, "账号切换和退出不能复用前一身份的详情")
                assertSame(content, environment.getOrFetchContentDetail(destination), "同一存活客户端仍复用详情")
                assertEquals(index + 1, requests)
            }
        } finally {
            store.close()
            directory.deleteRecursively()
        }
    }

    private fun articleResponse(content: String): String = ZhihuJson.json.encodeToString(
        DataHolder.Article.serializer(),
        DataHolder.Article(
            id = 1L,
            author = DataHolder.Author(
                avatarUrl = "",
                gender = 0,
                headline = "测试简介",
                id = "test-author",
                isAdvertiser = false,
                isOrg = false,
                name = "测试作者",
                type = "people",
                url = "https://www.zhihu.com/people/test-author",
                urlToken = "test-author",
                userType = "people",
            ),
            canComment = DataHolder.CanComment(status = true, reason = ""),
            title = "完整文章标题",
            content = content,
            excerpt = "测试摘要",
            type = "article",
            created = 1L,
            updated = 2L,
            url = "https://zhuanlan.zhihu.com/p/1",
            voteupCount = 12,
        ),
    )
}
