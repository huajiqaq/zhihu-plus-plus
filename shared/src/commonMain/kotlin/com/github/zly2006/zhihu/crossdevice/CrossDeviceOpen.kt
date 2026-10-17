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

package com.github.zly2006.zhihu.crossdevice

import com.github.zly2006.zhihu.data.Collection
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.data.navDestination
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.platform.SettingsStore
import com.github.zly2006.zhihu.platform.platformDeviceName
import com.github.zly2006.zhihu.util.signZhihuFetchRequest
import com.github.zly2006.zhihu.viewmodel.CollectionItem
import com.github.zly2006.zhihu.viewmodel.ZhihuApiEnvironment
import com.github.zly2006.zhihu.viewmodel.postSigned
import io.ktor.client.call.body
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.random.Random
import kotlin.time.Clock

/**
 * 跨设备打开基于知乎私密收藏夹做传输：
 * - 每个设备拥有一个 description 为 [CROSS_DEVICE_COLLECTION_PREFIX] + deviceId 的私密收藏夹；
 * - 投放 = 把内容加入目标设备的收藏夹；
 * - 接收 = 目标设备轮询自己的收藏夹，发现新条目后直接打开。
 *
 * 这样不需要自建服务器，也不依赖局域网发现；代价是接收端需要在前台轮询。
 */
const val CROSS_DEVICE_COLLECTION_PREFIX = "com.github.zly2006.zhplus.device."
private const val CROSS_DEVICE_LAST_SEEN_LIMIT = 30
private const val CROSS_DEVICE_DEVICE_ID_KEY = "cross_device_id"
private const val CROSS_DEVICE_DEVICE_NAME_KEY = "cross_device_name"

data class CrossDevice(
    val id: String,
    val name: String,
    val collectionId: String,
)

fun SettingsStore.crossDeviceId(): String {
    val existing = getString(CROSS_DEVICE_DEVICE_ID_KEY, "")
    if (existing.isNotBlank()) return existing
    val generated = buildString {
        append("dev-")
        append(
            Clock.System
                .now()
                .toEpochMilliseconds()
                .toString(16),
        )
        append('-')
        append(Random.nextLong().toULong().toString(16))
    }
    putString(CROSS_DEVICE_DEVICE_ID_KEY, generated)
    return generated
}

fun SettingsStore.crossDeviceName(): String =
    getString(CROSS_DEVICE_DEVICE_NAME_KEY, "").ifBlank { platformDeviceName }

fun SettingsStore.crossDeviceLastSeen(deviceId: String): Set<String> =
    getString(crossDeviceLastSeenKey(deviceId), "")
        .split(',')
        .filter(String::isNotBlank)
        .toSet()

fun SettingsStore.setCrossDeviceLastSeen(deviceId: String, keys: List<String>) {
    putString(
        crossDeviceLastSeenKey(deviceId),
        keys.take(CROSS_DEVICE_LAST_SEEN_LIMIT).joinToString(","),
    )
}

fun SettingsStore.crossDeviceInboxInitialized(deviceId: String): Boolean =
    getBoolean(crossDeviceInboxInitializedKey(deviceId), false)

fun SettingsStore.markCrossDeviceInboxInitialized(deviceId: String) {
    putBoolean(crossDeviceInboxInitializedKey(deviceId), true)
}

private fun crossDeviceLastSeenKey(deviceId: String) = "cross_device_last_seen_$deviceId"

private fun crossDeviceInboxInitializedKey(deviceId: String) = "cross_device_inbox_initialized_$deviceId"

/** 列出当前账号下注册过的所有设备（含本机）。 */
suspend fun ZhihuApiEnvironment.listCrossDevices(selfUrlToken: String): List<CrossDevice> {
    val json = fetchJson(
        "https://www.zhihu.com/api/v4/people/$selfUrlToken/collections?limit=100",
        "",
    ) ?: return emptyList()
    val data = json["data"] as? JsonArray ?: return emptyList()
    return ZhihuJson.decodeJson<List<Collection>>(data).mapNotNull { collection ->
        val deviceId = collection.description
            .takeIf { it.startsWith(CROSS_DEVICE_COLLECTION_PREFIX) }
            ?.removePrefix(CROSS_DEVICE_COLLECTION_PREFIX)
            ?.takeIf(String::isNotBlank)
            ?: return@mapNotNull null
        CrossDevice(
            id = deviceId,
            name = collection.title.ifBlank { deviceId },
            collectionId = collection.id,
        )
    }
}

/** 确保本机在账号下有一个设备收藏夹，不存在时创建。 */
suspend fun ZhihuApiEnvironment.ensureCrossDevice(
    selfUrlToken: String,
    deviceId: String,
    deviceName: String,
): CrossDevice? {
    listCrossDevices(selfUrlToken).firstOrNull { it.id == deviceId }?.let { return it }
    val response = postSigned("https://www.zhihu.com/api/v4/collections") {
        contentType(ContentType.Application.Json)
        setBody(
            buildJsonObject {
                put("title", deviceName)
                put("description", CROSS_DEVICE_COLLECTION_PREFIX + deviceId)
                put("is_public", false)
            },
        )
    }
    if (!response.status.isSuccess()) return null
    val collection = ZhihuJson.decodeJson<Collection>(response.body<JsonObject>()["collection"]!!)
    return CrossDevice(id = deviceId, name = deviceName, collectionId = collection.id)
}

/** 把内容投放到目标设备。 */
suspend fun ZhihuApiEnvironment.castToDevice(
    device: CrossDevice,
    article: Article,
): Boolean {
    val contentType = when (article.type) {
        ArticleType.Answer -> "answer"
        ArticleType.Article -> "article"
    }
    return withAuthenticatedClient { client, cookies ->
        client
            .put("https://api.zhihu.com/collections/contents/$contentType/${article.id}") {
                signZhihuFetchRequest(cookies)
                contentType(ContentType.Application.FormUrlEncoded)
                setBody("add_collections=${device.collectionId}")
            }.status
            .isSuccess()
    }
}

/** 读取目标设备收藏夹里最新的内容（新到旧）。 */
suspend fun ZhihuApiEnvironment.fetchCrossDeviceInbox(device: CrossDevice): List<Article> {
    val json = fetchJson(
        "https://www.zhihu.com/api/v4/collections/${device.collectionId}/items?limit=20&offset=0",
        "data[*].content",
    ) ?: return emptyList()
    val data = json["data"] as? JsonArray ?: return emptyList()
    return ZhihuJson
        .decodeJson<List<CollectionItem>>(data)
        .mapNotNull { it.content.navDestination as? Article }
}
