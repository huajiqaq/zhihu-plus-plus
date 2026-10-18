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

import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.platform.SettingsStore
import com.github.zly2006.zhihu.util.jsonObject
import com.github.zly2006.zhihu.util.raiseForStatus
import com.github.zly2006.zhihu.viewmodel.ZhihuApiEnvironment
import com.github.zly2006.zhihu.viewmodel.postSigned
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.putJsonArray

/**
 * 评论表情/贴纸。接口来自知乎客户端 `EmoticonApi`：
 * - GET  /emoji/user/list        收藏
 * - GET  /emoji/library/list     推荐
 * - POST /emoji/collect          收藏单条
 * - POST /emoji/user/remove      取消收藏
 * - POST /emoji/upload           上传自定义表情
 * - GET  /me/sticker-groups               默认/官方/会员贴纸分组（RN 同款）
 * - GET  /api/v4/sticker-groups/{id}       分组内贴纸
 */
@Serializable
data class CommentSticker(
    val id: String = "",
    val staticImageUrl: String = "",
    val dynamicImageUrl: String = "",
    val title: String = "",
    val groupId: String = "",
    val stickerType: Int = 1,
    val isVip: Boolean = false,
    val collected: Boolean = false,
    val placeholder: String = "",
) {
    /** 列表缩略图优先用静态图，评论里优先用动态图。 */
    val thumbnailUrl: String
        get() = staticImageUrl.ifBlank { dynamicImageUrl }

    val commentUrl: String
        get() = dynamicImageUrl.ifBlank { staticImageUrl }

    val isVipSticker: Boolean
        get() = isVip || stickerType == VIP_STICKER_TYPE
}

@Serializable
data class CommentStickerGroup(
    val id: String = "",
    val title: String = "",
    val iconUrl: String = "",
    val selectedIconUrl: String? = null,
    val type: String = "",
    val version: Int = 0,
) {
    val isVip: Boolean
        get() = type == "vip"
}

data class CommentStickerPage(
    val stickers: List<CommentSticker>,
    val isEnd: Boolean,
)

suspend fun fetchCollectedStickers(
    environment: ZhihuApiEnvironment,
    limit: Int = 40,
    offset: Int = 0,
): CommentStickerPage = fetchStickerPage(
    environment,
    "https://api.zhihu.com/emoji/user/list?limit=$limit&offset=$offset",
)

suspend fun fetchRecommendedStickers(
    environment: ZhihuApiEnvironment,
    limit: Int = 80,
    offset: Int = 0,
): CommentStickerPage = fetchStickerPage(
    environment,
    "https://api.zhihu.com/emoji/library/list?limit=$limit&offset=$offset",
)

private suspend fun fetchStickerPage(
    environment: ZhihuApiEnvironment,
    url: String,
): CommentStickerPage {
    val json = environment.fetchJson(url, include = "") ?: return CommentStickerPage(emptyList(), true)
    val stickers = json["data"]?.jsonArray.orEmpty().mapNotNull { item ->
        runCatching { ZhihuJson.decodeJson<CommentSticker>(item) }.getOrNull()
    }
    val isEnd = json["paging"]
        ?.jsonObject
        ?.get("is_end")
        ?.jsonPrimitive
        ?.booleanOrNull
        ?: true
    return CommentStickerPage(stickers, isEnd)
}

suspend fun fetchStickerGroups(environment: ZhihuApiEnvironment): List<CommentStickerGroup> {
    val json = environment.fetchJson(
        "https://www.zhihu.com/api/v4/me/sticker-groups",
        include = "",
    ) ?: return emptyList()
    return json["data"]?.jsonArray.orEmpty().mapNotNull { item ->
        runCatching { ZhihuJson.decodeJson<CommentStickerGroup>(item) }.getOrNull()
    }
}

suspend fun fetchGroupStickers(
    environment: ZhihuApiEnvironment,
    groupId: String,
): List<CommentSticker> {
    val json = environment.fetchJson(
        "https://www.zhihu.com/api/v4/sticker-groups/$groupId",
        include = "",
    ) ?: return emptyList()
    return json["data"]
        ?.jsonObject
        ?.get("stickers")
        ?.jsonArray
        .orEmpty()
        .mapNotNull { item ->
            runCatching { ZhihuJson.decodeJson<CommentSticker>(item) }.getOrNull()
        }
}

suspend fun collectCommentSticker(environment: ZhihuApiEnvironment, stickerId: String) {
    val body = buildJsonObject {
        putJsonArray("emoji_id") { add(JsonPrimitive(stickerId)) }
    }.toString()
    environment
        .postSigned("https://api.zhihu.com/emoji/collect") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.raiseForStatus(dumpRequest = true)
}

suspend fun removeCollectedCommentSticker(environment: ZhihuApiEnvironment, stickerId: String) {
    val body = buildJsonObject {
        putJsonArray("emoji_id") { add(JsonPrimitive(stickerId)) }
    }.toString()
    environment
        .postSigned("https://api.zhihu.com/emoji/user/remove") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.raiseForStatus(dumpRequest = true)
}

suspend fun uploadCommentSticker(
    environment: ZhihuApiEnvironment,
    imageToken: String,
): List<CommentSticker> {
    val body = buildJsonObject {
        putJsonArray("image_tokens") { add(JsonPrimitive(imageToken)) }
    }.toString()
    val response = environment.postSigned("https://api.zhihu.com/emoji/upload") {
        contentType(ContentType.Application.Json)
        setBody(body)
    }
    if (!response.status.isSuccess()) {
        throw IllegalStateException("表情上传失败：${response.status}")
    }
    val json = response.jsonObject()
    return json["data"]
        ?.jsonObject
        ?.get("emojis")
        ?.jsonArray
        .orEmpty()
        .mapNotNull { item ->
            runCatching { ZhihuJson.decodeJson<CommentSticker>(item) }.getOrNull()
        }
}

/** 与知乎 RN 评论区一致的贴纸 HTML。 */
fun commentStickerHtml(sticker: CommentSticker): String {
    val url = sticker.commentUrl.replace("\"", "&quot;")
    val id = sticker.id.replace("\"", "&quot;")
    val text = sticker.placeholder
        .ifBlank { "[${sticker.title.ifBlank { "表情" }}]" }
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
    return "<a class=\"comment_sticker\" href=\"$url\" " +
        "data-sticker-id=\"$id\">$text</a>"
}

/** RN 评论输入框的“最近使用表情”，本地保存，按使用次数和最近时间排序。 */
@Serializable
data class RecentCommentSticker(
    val id: String = "",
    val url: String = "",
    val thumbnailUrl: String = "",
    val title: String = "",
    val placeholder: String = "",
    val isVip: Boolean = false,
    val stickerType: Int = 1,
    val count: Int = 0,
    val lastUsedAt: Long = 0,
)

fun loadRecentCommentStickers(settings: SettingsStore): List<CommentSticker> {
    val raw = settings.getString(RECENT_COMMENT_STICKERS_KEY, "")
    if (raw.isBlank()) return emptyList()
    return runCatching {
        ZhihuJson.json.decodeFromString<List<RecentCommentSticker>>(raw)
    }.getOrDefault(emptyList()).map { recent ->
        CommentSticker(
            id = recent.id,
            staticImageUrl = recent.url,
            dynamicImageUrl = recent.url,
            title = recent.title,
            placeholder = recent.placeholder,
            isVip = recent.isVip,
            stickerType = recent.stickerType,
        )
    }
}

fun recordRecentCommentSticker(settings: SettingsStore, sticker: CommentSticker) {
    if (sticker.id.isBlank()) return
    val raw = settings.getString(RECENT_COMMENT_STICKERS_KEY, "")
    val existing = runCatching {
        ZhihuJson.json.decodeFromString<List<RecentCommentSticker>>(raw)
    }.getOrDefault(emptyList())
    val updated = (
        existing.filterNot { it.id == sticker.id } + RecentCommentSticker(
            id = sticker.id,
            url = sticker.commentUrl,
            thumbnailUrl = sticker.thumbnailUrl,
            title = sticker.title,
            placeholder = sticker.placeholder,
            isVip = sticker.isVip,
            stickerType = sticker.stickerType,
            count = (existing.firstOrNull { it.id == sticker.id }?.count ?: 0) + 1,
            lastUsedAt = kotlin.time.Clock.System
                .now()
                .toEpochMilliseconds(),
        )
    ).sortedWith(
        compareByDescending<RecentCommentSticker> { it.count }
            .thenByDescending { it.lastUsedAt },
    ).take(RECENT_COMMENT_STICKER_LIMIT)
    settings.putString(
        RECENT_COMMENT_STICKERS_KEY,
        ZhihuJson.json.encodeToString(updated),
    )
}

private const val RECENT_COMMENT_STICKERS_KEY = "comment.recentStickers"
private const val RECENT_COMMENT_STICKER_LIMIT = 10
private const val VIP_STICKER_TYPE = 2
