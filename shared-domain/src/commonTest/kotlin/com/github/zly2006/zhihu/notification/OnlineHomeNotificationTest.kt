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
import com.github.zly2006.zhihu.navigation.Account
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.Pin
import com.github.zly2006.zhihu.platform.MapSettingsStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    @Test
    fun repositoryReusesSuccessfulFetchAcrossLoadsAndRecreation() = runTest {
        val values = mutableMapOf<String, Any>()
        val repository = OnlineHomeNotificationRepository(MapSettingsStore(values))
        val notification = OnlineHomeNotification(
            uuid = "da811fe3-858a-4655-afd4-a63024b74dbb",
            expiresAt = Long.MAX_VALUE,
            title = "在线通知",
            accept = OnlineHomeNotificationAccept("打开", "open_url", JsonPrimitive("https://example.com")),
            dismiss = "关闭",
        )
        val requests = Channel<String>(Channel.UNLIMITED)
        val requestStarted = CompletableDeferred<Unit>()
        val responseMayFinish = CompletableDeferred<Unit>()
        val client = HttpClient(
            MockEngine { request ->
                requests.send(request.url.toString())
                requestStarted.complete(Unit)
                responseMayFinish.await()
                respond(
                    content = ZhihuJson.json.encodeToString(OnlineHomeNotificationResponse(listOf(notification))),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        ) {
            install(ContentNegotiation) {
                json(ZhihuJson.json)
            }
        }
        try {
            val firstLoad = async { repository.load("0.26", client) }
            requestStarted.await()
            val overlappingLoad = async(start = CoroutineStart.UNDISPATCHED) { repository.load("0.26", client) }
            responseMayFinish.complete(Unit)
            assertEquals(
                listOf(listOf(notification), listOf(notification)),
                listOf(firstLoad, overlappingLoad).awaitAll(),
            )
            assertEquals(listOf(notification), repository.load("0.26", client))
            assertEquals(listOf(notification), OnlineHomeNotificationRepository(MapSettingsStore(values)).load("0.26", client))
            assertEquals("https://api.redenmc.com/api/zhihu/notifications?version=0.26", requests.tryReceive().getOrNull())
            assertTrue(requests.tryReceive().isFailure)
            assertEquals(listOf(notification), repository.load("0.27", client))
            assertEquals("https://api.redenmc.com/api/zhihu/notifications?version=0.27", requests.tryReceive().getOrNull())
            assertTrue(requests.tryReceive().isFailure)
        } finally {
            responseMayFinish.complete(Unit)
            client.close()
            client.engine.close()
            requests.close()
        }
    }

    @Test
    fun readStateUsesUuidIndependentlyAndIsStoredAsPreference() = runTest {
        val values = mutableMapOf<String, Any>()
        val repository = OnlineHomeNotificationRepository(MapSettingsStore(values))
        val readNotification = OnlineHomeNotification(
            uuid = "da811fe3-858a-4655-afd4-a63024b74dbb",
            expiresAt = Long.MAX_VALUE,
            title = "已读通知",
            dismiss = "关闭",
        )
        val unreadNotification = OnlineHomeNotification(
            uuid = "2c9fe00a-80f3-4b8f-89a1-b96dbe411791",
            expiresAt = Long.MAX_VALUE,
            title = "未读通知",
            dismiss = "关闭",
        )
        val client = notificationClient(listOf(readNotification, unreadNotification))

        repository.markRead(readNotification)

        assertEquals(listOf(unreadNotification), repository.load("0.26", client))
        assertEquals(
            setOf(readNotification.uuid),
            values[HOME_NOTIFICATION_READ_UUIDS_PREFERENCE_KEY],
        )
        client.close()
    }

    @Test
    fun loadedActionsPreserveNavigationAndSettingTypes() = runTest {
        val cases = listOf(
            Triple("open_url", "\"https://example.com\"", HomeNotificationAction.OpenUrl("https://example.com", false)),
            Triple("open_webview", "\"https://example.com\"", HomeNotificationAction.OpenUrl("https://example.com", true)),
            Triple("open_update_settings", null, HomeNotificationAction.Navigate(Account.SystemAndUpdateSettings())),
            Triple("open_pin", "123", HomeNotificationAction.Navigate(Pin(123))),
            Triple("open_answer", "\"123\"", HomeNotificationAction.Navigate(Article(type = ArticleType.Answer, id = 123))),
            Triple("open_article", "456", HomeNotificationAction.Navigate(Article(type = ArticleType.Article, id = 456))),
            Triple("set_setting", """{"setting_name":"option","value_type":"boolean","value":false}""", HomeNotificationAction.SetBoolean("option", false)),
            Triple("set_setting", """{"setting_name":"option","value_type":"string","value":"text"}""", HomeNotificationAction.SetString("option", "text")),
            Triple("set_setting", """{"setting_name":"option","value_type":"int","value":3}""", HomeNotificationAction.SetInt("option", 3)),
        )
        val notifications = cases.mapIndexed { index, (key, value, _) ->
            OnlineHomeNotification(
                uuid = "da811fe3-858a-4655-afd4-${index.toString().padStart(12, '0')}",
                expiresAt = Long.MAX_VALUE,
                title = "通知",
                accept = OnlineHomeNotificationAccept("执行", key, value?.let(ZhihuJson.json::parseToJsonElement)),
                dismiss = "关闭",
            )
        }
        val client = notificationClient(notifications)
        try {
            val loaded = OnlineHomeNotificationRepository(MapSettingsStore(mutableMapOf())).load("0.26", client)
            assertEquals(cases.map { it.third }, loaded.map { it.accept?.action })
        } finally {
            client.close()
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
