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

package com.github.zly2006.zhihu.util

import android.content.Context
import android.util.Base64
import android.util.Log
import com.github.zly2006.zhihu.account.ZhihuAccountClient
import com.github.zly2006.zhihu.data.ZhihuJson
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

@Serializable
private data class EmojiApiResponse(
    val data: EmojiData? = null,
)

@Serializable
private data class EmojiData(
    val version: Int = 0,
    val stickers: List<StickerItem> = emptyList(),
)

@Serializable
private data class StickerItem(
    val id: String,
    val placeholder: String,
    val staticImageUrl: String,
)

object EmojiManager {
    private const val TAG = "EmojiManager"
    private const val EMOJI_API_URL = "https://www.zhihu.com/api/v4/sticker-groups/1114161698310770688"
    private const val EMOJI_GROUPS_URL = "https://www.zhihu.com/api/v4/me/sticker-groups"
    private const val EMOJI_MAPPING_FILE = "emoji_mapping.json"
    private const val EMOJI_CACHE_VERSION_FILE = "emoji_version.txt"

    private val emojiMapping = mutableMapOf<String, String>()
    private val emojiCache = mutableMapOf<String, String>()
    private val mutablePlaceholders = MutableStateFlow<List<String>>(emptyList())
    val placeholders = mutablePlaceholders.asStateFlow()
    private var isInitialized = false

    suspend fun initialize(context: Context, account: ZhihuAccountClient) = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext
        try {
            val emojiDir = File(context.filesDir, "emojis")
            if (!emojiDir.exists()) emojiDir.mkdirs()
            val mappingFile = File(context.filesDir, EMOJI_MAPPING_FILE)
            val versionFile = File(context.filesDir, EMOJI_CACHE_VERSION_FILE)
            val cachedVersion = versionFile
                .takeIf { it.exists() }
                ?.readText()
                ?.trim()
                ?.toIntOrNull()
            val remoteVersion = fetchDefaultEmojiVersion(account)
            val needsDownload = !mappingFile.exists() ||
                !versionFile.exists() ||
                (remoteVersion != null && remoteVersion != cachedVersion)
            if (needsDownload) {
                downloadAndCacheEmojis(account, emojiDir, mappingFile, versionFile)
            } else {
                loadEmojiMapping(mappingFile, emojiDir)
            }
            isInitialized = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize emoji manager", e)
            try {
                val mappingFile = File(context.filesDir, EMOJI_MAPPING_FILE)
                val emojiDir = File(context.filesDir, "emojis")
                if (mappingFile.exists()) {
                    loadEmojiMapping(mappingFile, emojiDir)
                    isInitialized = true
                }
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to load emoji from cache", e2)
            }
        }
    }

    private suspend fun fetchDefaultEmojiVersion(account: ZhihuAccountClient): Int? = try {
        val groups = (fetchEmojiData(account, EMOJI_GROUPS_URL) as? JsonObject)?.get("data") as? JsonArray
        val group = groups?.filterIsInstance<JsonObject>()?.firstOrNull { (it["type"] as? JsonPrimitive)?.content == "emoji" }
        (group?.get("version") as? JsonPrimitive)?.intOrNull
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private suspend fun fetchEmojiData(account: ZhihuAccountClient, url: String): JsonElement {
        val response = account
            .withAuthenticatedClient { client, cookies ->
                client.get(url) {
                    timeout {
                        connectTimeoutMillis = 10_000
                        socketTimeoutMillis = 10_000
                    }
                    signZhihuFetchRequest(cookies)
                }
            }.raiseForStatus()
            .bodyAsText()
        currentCoroutineContext().ensureActive()
        if (!account.isCurrent) throw CancellationException("表情请求期间账号已切换")
        return ZhihuJson.json.parseToJsonElement(response)
    }

    private suspend fun downloadAndCacheEmojis(
        account: ZhihuAccountClient,
        emojiDir: File,
        mappingFile: File,
        versionFile: File,
    ) {
        val data = ZhihuJson.decodeJson<EmojiApiResponse>(fetchEmojiData(account, EMOJI_API_URL)).data
            ?: error("Invalid response format")
        val newMapping = mutableMapOf<String, String>()
        data.stickers.forEach { sticker ->
            val fileName = "emoji_${sticker.id}.png"
            newMapping[sticker.placeholder] = fileName
            if (sticker.staticImageUrl.startsWith("data:image/png;base64,")) {
                val base64Data = sticker.staticImageUrl.substring("data:image/png;base64,".length)
                val imageBytes = Base64.decode(base64Data, Base64.DEFAULT)
                val targetFile = File(emojiDir, fileName)
                targetFile.outputStream().use { it.write(imageBytes) }
                emojiCache[fileName] = targetFile.absolutePath
            }
        }
        mappingFile.writeText(ZhihuJson.json.encodeToString(kotlinx.serialization.serializer<Map<String, String>>(), newMapping))
        versionFile.writeText(data.version.toString())
        emojiMapping.clear()
        emojiMapping.putAll(newMapping)
        mutablePlaceholders.value = newMapping.keys.toList()
    }

    private fun loadEmojiMapping(mappingFile: File, emojiDir: File) {
        val mapping = Json.decodeFromString<Map<String, String>>(mappingFile.readText())
        emojiMapping.clear()
        emojiMapping.putAll(mapping)
        emojiCache.clear()
        mapping.values.forEach { fileName ->
            val file = File(emojiDir, fileName)
            if (file.exists()) emojiCache[fileName] = file.absolutePath
        }
        mutablePlaceholders.value = mapping.filterValues(emojiCache::containsKey).keys.toList()
    }

    fun getEmojiPath(placeholder: String): String? {
        val fileName = emojiMapping[placeholder] ?: return null
        return emojiCache[fileName]
    }

    fun getEmojiPathByFileName(fileName: String): String? = emojiCache[fileName]
}
