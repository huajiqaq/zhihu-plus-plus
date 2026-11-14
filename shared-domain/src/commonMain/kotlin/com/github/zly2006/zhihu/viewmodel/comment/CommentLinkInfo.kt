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
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class CommentLinkInfo(
    val url: String,
    val title: String,
    val iconName: String?,
)

/** parse_url 用一个 url 参数逗号拼接本批链接，响应按 normalizedUrl 索引。 */
suspend fun parseCommentLinks(
    environment: ZhihuApiEnvironment,
    urls: List<String>,
    scene: String = "editor",
): Map<String, CommentLinkInfo> {
    val distinct = urls.map(String::trim).filter(String::isNotEmpty).distinct()
    if (distinct.isEmpty()) return emptyMap()
    val joined = distinct.joinToString(",").encodeURLParameter()
    val json = environment.fetchJson(
        "https://api.zhihu.com/content/publish/parse_url?url=$joined&scene=${scene.encodeURLParameter()}",
        "",
    ) ?: return emptyMap()
    val data = json["data"]?.jsonObject ?: return emptyMap()
    return data
        .mapNotNull { (key, value) ->
            val info = value.jsonObject
            val title = info["title"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
                ?: return@mapNotNull null
            key to CommentLinkInfo(
                url = info["url"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank) ?: key,
                title = title,
                iconName = info["icon_name"]?.jsonPrimitive?.contentOrNull,
            )
        }.toMap()
}
