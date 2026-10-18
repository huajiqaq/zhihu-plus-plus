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

import com.github.zly2006.zhihu.viewmodel.ZhihuApiEnvironment
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class MentionCandidate(
    val id: String,
    val name: String,
    val urlToken: String,
    val avatarUrl: String?,
)

/**
 * 评论 @ 提及的用户搜索。接口与知乎评论编辑器一致：
 * GET /people/ats?scene=comment_editor&q=...
 */
suspend fun fetchMentionCandidates(
    environment: ZhihuApiEnvironment,
    query: String,
): List<MentionCandidate> {
    val keyword = query.trim()
    val queryPart = if (keyword.isEmpty()) "" else "&q=${keyword.encodeURLParameter()}"
    val json = environment.fetchJson(
        "https://api.zhihu.com/people/ats?offset=0&limit=20&scene=comment_editor$queryPart",
        "",
    ) ?: return emptyList()
    val data = json["data"] as? JsonArray ?: return emptyList()
    return data.mapNotNull { element ->
        val raw = element.jsonObject
        val member = raw["member"]?.jsonObject ?: raw
        val id = member["id"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
            ?: return@mapNotNull null
        val name = member["name"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
            ?: return@mapNotNull null
        val urlToken = member["url_token"]
            ?.jsonPrimitive
            ?.contentOrNull
            ?.takeIf(String::isNotBlank)
            ?: id
        MentionCandidate(
            id = id,
            name = name,
            urlToken = urlToken,
            avatarUrl = member["avatar_url"]?.jsonPrimitive?.contentOrNull,
        )
    }
}
