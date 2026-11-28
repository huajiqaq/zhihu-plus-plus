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
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.i18n.ReadingStrings
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.Pin
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.navigation.resolveContent

fun resolveLinkCardDestination(linkCard: DataHolder.Pin.ContentLinkCard): NavDestination? {
    val byUrl = linkCard.url
        .takeIf { it.isNotBlank() }
        ?.let(::resolveContent)
    if (byUrl != null) return byUrl

    val contentId = linkCard.dataContentId
    return when (linkCard.dataContentType.lowercase()) {
        "answer" -> contentId.toLongOrNull()?.let { Article(type = ArticleType.Answer, id = it) }
        "article" -> contentId.toLongOrNull()?.let { Article(type = ArticleType.Article, id = it) }
        "question" -> contentId.toLongOrNull()?.let { Question(questionId = it) }
        "pin" -> contentId.toLongOrNull()?.let { Pin(id = it) }
        else -> null
    }
}

fun compactPreview(raw: String, maxLength: Int = 120): String {
    val plainText = Ksoup
        .parse(raw)
        .text()
        .replace(Regex("\\s+"), " ")
        .trim()
    if (plainText.length <= maxLength) return plainText
    return plainText.take(maxLength).trimEnd() + "..."
}

fun compactTitle(raw: String, maxLength: Int = 56): String = compactPreview(raw, maxLength)

data class PinLinkCardPreview(
    val title: String,
    val preview: String,
    val pinAuthorName: String? = null,
) {
    val displayTitle: String get() = pinAuthorName?.let(ReadingStrings::pinByAuthor) ?: title
}
