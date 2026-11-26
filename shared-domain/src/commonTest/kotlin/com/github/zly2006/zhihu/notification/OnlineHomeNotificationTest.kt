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

package com.github.zly2006.zhihu.notification

import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.platform.MapSettingsStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class OnlineHomeNotificationTest {
    @Test
    fun malformedSettingDoesNotHideValidNotifications() = runTest {
        val valid = OnlineHomeNotification(
            uuid = "da811fe3-858a-4655-afd4-a63024b74dbb",
            expiresAt = Long.MAX_VALUE,
            title = "有效通知",
            dismiss = "关闭",
        )
        val malformed = listOf(
            """{"setting_name":{},"value_type":"boolean","value":true}""",
            """{"setting_name":"option","value_type":[],"value":true}""",
            """{"setting_name":"option","value_type":"boolean","value":{}}""",
        ).mapIndexed { index, value ->
            valid.copy(
                uuid = "2c9fe00a-80f3-4b8f-89a1-${index.toString().padStart(12, '0')}",
                accept = OnlineHomeNotificationAccept("应用", "set_setting", ZhihuJson.json.parseToJsonElement(value)),
            )
        }
        val invalid = malformed + listOf(
            valid.copy(uuid = "invalid"),
            valid.copy(title = " "),
            valid.copy(dismiss = " "),
            valid.copy(expiresAt = 0),
            valid.copy(accept = OnlineHomeNotificationAccept("执行", "future_action")),
            valid.copy(accept = OnlineHomeNotificationAccept("查看", "open_answer", ZhihuJson.json.parseToJsonElement("\"invalid\""))),
        )
        val client = notificationClient(invalid + valid + valid)
        try {
            assertEquals(listOf(valid), OnlineHomeNotificationRepository(MapSettingsStore(mutableMapOf())).load("0.26", client))
        } finally {
            client.close()
            client.engine.close()
        }
    }

    private fun notificationClient(notifications: List<OnlineHomeNotification>): HttpClient = HttpClient(
        MockEngine {
            respond(
                content = ZhihuJson.json.encodeToString(OnlineHomeNotificationResponse(notifications)),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        },
    ) {
        install(ContentNegotiation) {
            json(ZhihuJson.json)
        }
    }
}
