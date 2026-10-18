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
import com.github.zly2006.zhihu.account.androidZhihuAccountStore
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

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
    @SerialName("static_image_url") val staticImageUrl: String,
)

object EmojiManager {
    private const val TAG = "EmojiManager"
    private const val EMOJI_API_URL = "https://www.zhihu.com/api/v4/sticker-groups/1114161698310770688"
    private const val EMOJI_GROUPS_URL = "https://www.zhihu.com/api/v4/me/sticker-groups"
    private const val EMOJI_MAPPING_FILE = "emoji_mapping.json"
    private const val EMOJI_CACHE_VERSION_FILE = "emoji_version.txt"

    // emoji占位符到文件名的映射
    private val emojiMapping = mutableMapOf<String, String>()

    // emoji文件名到本地缓存路径的映射
    private val emojiCache = mutableMapOf<String, String>()

    private val mutablePlaceholders = MutableStateFlow<List<String>>(emptyList())

    /** 当前已经缓存、可以用于输入和展示的表情占位符。 */
    val placeholders = mutablePlaceholders.asStateFlow()

    private var isInitialized = false

    /**
     * 初始化emoji管理器，从Zhihu API动态下载emoji数据
     */
    suspend fun initialize(context: Context) = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext

        try {
            val emojiDir = File(context.filesDir, "emojis")
            if (!emojiDir.exists()) {
                emojiDir.mkdirs()
            }

            val mappingFile = File(context.filesDir, EMOJI_MAPPING_FILE)
            val versionFile = File(context.filesDir, EMOJI_CACHE_VERSION_FILE)

            // 检查是否需要重新下载：本地缓存缺失，或远端默认分组 version 变了。
            val cachedVersion = versionFile
                .takeIf { it.exists() }
                ?.readText()
                ?.trim()
                ?.toIntOrNull()
            val remoteVersion = fetchDefaultEmojiVersion(context)
            val needsDownload = !mappingFile.exists() ||
                !versionFile.exists() ||
                (remoteVersion != null && remoteVersion != cachedVersion)

            if (needsDownload) {
                Log.i(TAG, "Downloading emoji data from Zhihu API...")
                downloadAndCacheEmojis(context, emojiDir, mappingFile, versionFile)
            } else {
                Log.i(TAG, "Loading emoji data from cache...")
                loadEmojiMapping(mappingFile, emojiDir)
            }

            Log.i(TAG, "Emoji manager initialized with ${emojiMapping.size} emojis")
            isInitialized = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize emoji manager", e)
            // 尝试加载本地缓存
            try {
                val mappingFile = File(context.filesDir, EMOJI_MAPPING_FILE)
                val emojiDir = File(context.filesDir, "emojis")
                if (mappingFile.exists()) {
                    loadEmojiMapping(mappingFile, emojiDir)
                    isInitialized = true
                    Log.i(TAG, "Loaded emoji from cache after download failure")
                }
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to load emoji from cache", e2)
            }
        }
    }

    /** 读取默认表情分组当前的 version；失败时返回 null，回退到本地缓存。 */
    private suspend fun fetchDefaultEmojiVersion(context: Context): Int? = runCatching {
        val accountClient = androidZhihuAccountStore(context).client
        val response = accountClient
            .httpClient()
            .get(EMOJI_GROUPS_URL) {
                signZhihuFetchRequest(accountClient.load().cookies)
            }.raiseForStatus()
            .bodyAsText()
        val data = Json
            .parseToJsonElement(response)
            .jsonObject["data"]
            ?.jsonArray
            ?: return@runCatching null
        data
            .mapNotNull { item -> runCatching { item.jsonObject }.getOrNull() }
            .firstOrNull { group -> group["type"]?.jsonPrimitive?.content == "emoji" }
            ?.get("version")
            ?.jsonPrimitive
            ?.intOrNull
    }.getOrNull()

    /**
     * 从Zhihu API下载emoji数据并缓存到本地
     */
    private suspend fun downloadAndCacheEmojis(
        context: Context,
        emojiDir: File,
        mappingFile: File,
        versionFile: File,
    ) {
        // 走账号客户端，带上 cookie 和 x-zse-96；测试环境的 MockEngine 也能拦截。
        val accountClient = androidZhihuAccountStore(context).client
        val response = accountClient
            .httpClient()
            .get(EMOJI_API_URL) {
                signZhihuFetchRequest(accountClient.load().cookies)
            }.raiseForStatus()
            .bodyAsText()

        val json = Json { ignoreUnknownKeys = true }
        val emojiData = json.decodeFromString<EmojiApiResponse>(response).data
            ?: throw Exception("Invalid response format")
        val stickers = emojiData.stickers

        val newMapping = mutableMapOf<String, String>()
        var downloadCount = 0

        stickers.forEach { sticker ->
            val fileName = "emoji_${sticker.id}.png"
            newMapping[sticker.placeholder] = fileName

            // 解析base64图片数据
            if (sticker.staticImageUrl.startsWith("data:image/png;base64,")) {
                val base64Data = sticker.staticImageUrl.substring("data:image/png;base64,".length)
                val imageBytes = Base64.decode(base64Data, Base64.DEFAULT)

                val targetFile = File(emojiDir, fileName)
                targetFile.outputStream().use { it.write(imageBytes) }

                emojiCache[fileName] = targetFile.absolutePath
                downloadCount++
            }
        }

        // 保存映射文件
        val mappingJson = json.encodeToString(
            kotlinx.serialization.serializer<Map<String, String>>(),
            newMapping,
        )
        mappingFile.writeText(mappingJson)

        // 保存版本文件（使用接口返回的 version，而不是手工常量）
        versionFile.writeText(emojiData.version.toString())

        emojiMapping.clear()
        emojiMapping.putAll(newMapping)
        mutablePlaceholders.value = newMapping.keys.toList()

        Log.i(TAG, "Downloaded and cached $downloadCount emojis")
    }

    /**
     * 从本地缓存加载emoji映射
     */
    private fun loadEmojiMapping(mappingFile: File, emojiDir: File) {
        val json = Json { ignoreUnknownKeys = true }
        val mappingJson = mappingFile.readText()
        val mapping = json.decodeFromString<Map<String, String>>(mappingJson)

        emojiMapping.clear()
        emojiMapping.putAll(mapping)

        emojiCache.clear()
        mapping.values.forEach { fileName ->
            val file = File(emojiDir, fileName)
            if (file.exists()) {
                emojiCache[fileName] = file.absolutePath
            }
        }
        mutablePlaceholders.value = mapping.filterValues(emojiCache::containsKey).keys.toList()
    }

    /**
     * 根据emoji占位符获取本地文件路径
     * @param placeholder emoji占位符
     * @return 本地文件路径，如果emoji不存在返回null
     */
    fun getEmojiPath(placeholder: String): String? {
        val fileName = emojiMapping[placeholder] ?: return null
        return emojiCache[fileName]
    }

    /**
     * 根据emoji文件名获取本地文件路径
     * @param fileName emoji文件名，例如 "emoji_1114211823741685761.png"
     * @return 本地文件路径，如果emoji不存在返回null
     */
    fun getEmojiPathByFileName(fileName: String): String? = emojiCache[fileName]
}
