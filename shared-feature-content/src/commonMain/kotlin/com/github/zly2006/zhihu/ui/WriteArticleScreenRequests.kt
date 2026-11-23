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

package com.github.zly2006.zhihu.ui

import com.fleeksoft.ksoup.Ksoup
import com.github.zly2006.zhihu.editor.UploadedZhihuImage
import io.ktor.http.Parameters
import io.ktor.http.formUrlEncode

internal fun writeArticleScreenHasBody(html: String): Boolean {
    val documentBody = Ksoup.parseBodyFragment(html).body()
    return documentBody.text().isNotBlank() || documentBody.select("img,video,iframe").isNotEmpty()
}

internal fun writeArticleScreenDraftRequestBody(
    title: String,
    html: String,
    currentId: Long?,
): String =
    Parameters
        .build {
            append("title", title)
            append("content", html)
            append("copyright", if (currentId == null) "public" else "need_review")
            if (currentId != null) {
                append("delta_time", "30000")
                append("topic_ids", "")
            }
        }.formUrlEncode()

internal fun writeArticleScreenPublishRequestBody(
    title: String,
    html: String,
): String =
    Parameters
        .build {
            append("title", title)
            append("content", html)
            append("copyright_permission", "need_review")
        }.formUrlEncode()

internal fun writeArticleScreenImageSnippet(image: UploadedZhihuImage): String {
    val imageMetadata = buildString {
        append("zhimg:w=${image.rawWidth};h=${image.rawHeight}")
        image.watermark?.let { append(";wm=${if (it) 1 else 0}") }
        if (image.watermark == true) image.watermarkUrl?.let { append(";wmsrc=$it") }
    }
    return "![](${image.url} \"$imageMetadata\")"
}
