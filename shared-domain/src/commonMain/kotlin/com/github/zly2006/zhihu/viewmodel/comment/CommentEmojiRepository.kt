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
import com.github.zly2006.zhihu.i18n.DomainStrings
import com.github.zly2006.zhihu.platform.SettingsStore
import com.github.zly2006.zhihu.util.DisplayFailure
import com.github.zly2006.zhihu.util.raiseForStatus
import com.github.zly2006.zhihu.viewmodel.ZhihuApiEnvironment
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

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

/** 贴纸字段允许 null，按字段缺省值解码。 */
private val stickerJson = Json(ZhihuJson.json) {
    coerceInputValues = true
}

suspend fun fetchCollectedStickers(
    environment: ZhihuApiEnvironment,
    limit: Int = 100,
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
        runCatching {
            stickerJson.decodeFromJsonElement(
                CommentSticker.serializer(),
                ZhihuJson.snakeCaseToCamelCase(item),
            )
        }.getOrNull()?.takeIf { it.id.isNotBlank() && it.commentUrl.isNotBlank() }
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
        runCatching {
            stickerJson.decodeFromJsonElement(
                CommentStickerGroup.serializer(),
                ZhihuJson.snakeCaseToCamelCase(item),
            )
        }.getOrNull()
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
            runCatching {
                stickerJson.decodeFromJsonElement(
                    CommentSticker.serializer(),
                    ZhihuJson.snakeCaseToCamelCase(item),
                )
            }.getOrNull()?.takeIf { it.id.isNotBlank() && it.commentUrl.isNotBlank() }
        }
}

suspend fun collectCommentSticker(environment: ZhihuApiEnvironment, stickerId: String) {
    val body = buildJsonObject {
        putJsonArray("emoji_id") { add(JsonPrimitive(stickerId)) }
        /** collect/remove 缺少 source 时服务端返回 40001。 */
        put("source", JsonPrimitive("comment"))
    }.toString()
    environment
        .post("https://api.zhihu.com/emoji/collect") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.raiseForStatus()
        .ensureStickerSuccess(DomainStrings::stickerSaveFailed)
}

suspend fun removeCollectedCommentSticker(environment: ZhihuApiEnvironment, stickerId: String) {
    val body = buildJsonObject {
        putJsonArray("emoji_id") { add(JsonPrimitive(stickerId)) }
        put("source", JsonPrimitive("comment"))
    }.toString()
    environment
        .post("https://api.zhihu.com/emoji/user/remove") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.raiseForStatus()
        .ensureStickerSuccess(DomainStrings::stickerRemoveFailed)
}

/** HTTP 200 仍可能携带非零业务 code。 */
private suspend fun io.ktor.client.statement.HttpResponse.ensureStickerSuccess(failure: (String) -> String) {
    val text = bodyAsText()
    val json = runCatching { ZhihuJson.json.parseToJsonElement(text).jsonObject }.getOrNull()
    val code = json
        ?.get("code")
        ?.jsonPrimitive
        ?.contentOrNull
        ?.toIntOrNull() ?: 0
    if (code != 0) {
        val message = json?.get("message")?.jsonPrimitive?.contentOrNull ?: text.take(120)
        throw DisplayFailure { failure(message) }
    }
}

class StickerAlreadyCollectedException(
    message: String,
) : IllegalStateException(message)

suspend fun uploadCommentSticker(
    environment: ZhihuApiEnvironment,
    imageToken: String,
): List<CommentSticker> {
    val body = buildJsonObject {
        putJsonArray("image_tokens") { add(JsonPrimitive(imageToken)) }
    }.toString()
    val response = environment.post("https://api.zhihu.com/emoji/upload") {
        contentType(ContentType.Application.Json)
        setBody(body)
    }
    if (!response.status.isSuccess()) {
        val status = response.status
        throw DisplayFailure { DomainStrings.stickerUploadFailed(status) }
    }
    val json = response.bodyAsText().let { text ->
        runCatching { ZhihuJson.json.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: throw DisplayFailure { DomainStrings.stickerInvalidResponse }
    }
    val code = json["code"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
    if (code != 0) {
        val message = json["message"]?.jsonPrimitive?.contentOrNull
        /** 40001「已添加」要求调用方刷新收藏，而非宣告新增成功。 */
        if (message != null && message.contains("已添加")) throw StickerAlreadyCollectedException(message)
        throw DisplayFailure { DomainStrings.stickerUploadFailed(message ?: DomainStrings.unknownError) }
    }
    val stickers = json["data"]
        ?.jsonObject
        ?.get("emojis")
        ?.jsonArray
        .orEmpty()
        .map { item ->
            stickerJson
                .decodeFromJsonElement(
                    CommentSticker.serializer(),
                    ZhihuJson.snakeCaseToCamelCase(item),
                ).copy(collected = true)
        }.filter { it.id.isNotBlank() && it.commentUrl.isNotBlank() }
    if (stickers.isEmpty()) throw DisplayFailure { DomainStrings.stickerNotReturned }
    return stickers
}

fun commentStickerHtml(sticker: CommentSticker): String {
    val url = sticker.commentUrl.replace("\"", "&quot;")
    val id = sticker.id.replace("\"", "&quot;")
    val text = sticker.placeholder
        .ifBlank { "[${sticker.title.ifBlank { "表情" }}]" }
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
    /** 官方贴纸占位宽高为零，折叠由内容视图处理。 */
    return "<a href=\"$url\" class=\"comment_sticker\" " +
        "data-width=\"0\" data-height=\"0\" data-sticker-id=\"$id\">$text</a>"
}

fun loadRecentCommentEmojis(settings: SettingsStore): List<String> {
    val raw = settings.getString(RECENT_COMMENT_EMOJIS_KEY, "")
    if (raw.isBlank()) return emptyList()
    return runCatching {
        ZhihuJson.json.decodeFromString<List<String>>(raw)
    }.getOrDefault(emptyList())
}

fun recordRecentCommentEmoji(settings: SettingsStore, placeholder: String) {
    if (placeholder.isBlank()) return
    val updated =
        (listOf(placeholder) + loadRecentCommentEmojis(settings).filterNot { it == placeholder })
            .take(RECENT_COMMENT_EMOJI_LIMIT)
    settings.putString(
        RECENT_COMMENT_EMOJIS_KEY,
        ZhihuJson.json.encodeToString(updated),
    )
}

private const val RECENT_COMMENT_EMOJIS_KEY = "comment.recentEmojis"
private const val RECENT_COMMENT_EMOJI_LIMIT = 10
private const val VIP_STICKER_TYPE = 2
