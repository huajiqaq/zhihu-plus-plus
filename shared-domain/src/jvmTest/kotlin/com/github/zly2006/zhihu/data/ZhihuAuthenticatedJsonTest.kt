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

package com.github.zly2006.zhihu.data

import com.github.zly2006.zhihu.viewmodel.ZhihuApiEnvironment
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ZhihuAuthenticatedJsonTest {
    @Test
    fun authenticatedJsonFailureDoesNotLogCredentials() = runBlocking {
        val output = ByteArrayOutputStream()
        val originalOutput = System.out
        val engine = MockEngine {
            respond(
                """{"error":{"message":"fixture-body-credential"}}""",
                HttpStatusCode.Forbidden,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine) {
            installZhihuCommonClientConfig(mutableMapOf(), "diagnostic-test")
        }
        val environment = object : ZhihuApiEnvironment {
            override fun httpClient() = client

            override fun authenticatedCookies() = emptyMap<String, String>()

            override suspend fun handleFetchFailure(tag: String?, error: Exception) = Unit
        }
        try {
            PrintStream(output, true, "UTF-8").use { captured ->
                try {
                    System.setOut(captured)
                    assertNull(
                        environment.fetchJson(
                            "https://fixture-user:fixture-password@www.zhihu.com/private?token=fixture-query#fixture-fragment",
                            "",
                        ),
                    )
                } finally {
                    System.setOut(originalOutput)
                }
            }
            val logged = output.toString("UTF-8")
            assertTrue("403" in logged, "保留状态码以诊断失败")
            assertFalse("fixture-" in logged, "地址凭据、查询参数与响应正文不得进入备用日志")
        } finally {
            System.setOut(originalOutput)
            client.close()
            engine.close()
        }
    }
}
