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

import com.github.zly2006.zhihu.data.installZhihuCommonClientConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/** 日报页面刷新使用真实 VM 和既有 HTTP 请求；失败不能吞掉已成功显示的日期分组。 */
class DailyViewModelTest {
    @Test
    fun failedLatestRefreshKeepsSections() = runTest {
        var fail = false
        val client = HttpClient(
            MockEngine { request ->
                assertEquals("/api/4/stories/latest", request.url.encodedPath)
                if (fail) error("daily refresh unavailable")
                respond(
                    """{"date":"20261029","stories":[{"id":1,"title":"日报正文","url":"https://example.com/1","hint":"作者","images":[],"type":0}]}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        ) {
            installZhihuCommonClientConfig(mutableMapOf(), "test")
        }
        try {
            val model = DailyViewModel()
            model.loadLatest(client)
            val loaded = model.sections
            assertEquals(1, loaded.size)
            fail = true

            model.loadLatest(client)

            assertEquals(loaded, model.sections)
            assertNotNull(model.error)
            assertFalse(model.isLoading)
        } finally {
            client.close()
        }
    }

    @Test
    fun failedDateRefreshKeepsSections() = runTest {
        val requests = mutableListOf<String>()
        val client = HttpClient(
            MockEngine { request ->
                requests += request.url.encodedPath
                if (request.url.encodedPath.contains("/before/")) error("daily date unavailable")
                respond(
                    """{"date":"20261029","stories":[{"id":1,"title":"已加载日报","url":"https://example.com/1","hint":"作者","images":[],"type":0}]}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        ) {
            installZhihuCommonClientConfig(mutableMapOf(), "test")
        }
        try {
            val model = DailyViewModel()
            model.loadLatest(client)
            val loaded = model.sections
            assertEquals(1, loaded.size)

            model.loadDate(client, "20261028")

            assertEquals(loaded, model.sections)
            assertNotNull(model.error)
            assertFalse(model.isLoading)
            assertEquals(
                listOf("/api/4/stories/latest", "/api/4/stories/before/20261029"),
                requests,
            )
        } finally {
            client.close()
        }
    }
}
