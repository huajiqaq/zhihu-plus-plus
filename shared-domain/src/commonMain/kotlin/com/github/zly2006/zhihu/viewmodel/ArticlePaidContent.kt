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

package com.github.zly2006.zhihu.viewmodel

import com.fleeksoft.ksoup.Ksoup

/** 去掉预览正文末尾提示去 App 查看的那一段；没有这样的段落时原样返回。 */
internal fun stripAppViewPrompt(html: String): String {
    val body = Ksoup.parseBodyFragment(html).body()
    val last = body.children().lastOrNull() ?: return html
    val text = last.text()
    if (!text.contains("App", ignoreCase = true) || !text.contains("查看")) return html
    last.remove()
    return body.html()
}

/** 预览正文最后一个非空段落的文字，用来在全文里定位续接位置。 */
internal fun truncatedContentAnchor(html: String): String? =
    Ksoup
        .parseBodyFragment(html)
        .body()
        .children()
        .lastOrNull { it.text().isNotBlank() }
        ?.text()
        ?.trim()

/**
 * 全文里锚点段落之后的内容，锚点文字多次出现时取最后一次。
 * 没有 `.RichText` 容器、找不到锚点、锚点已是最后一段时返回 null。
 */
internal fun paidContentAfterAnchor(html: String, anchor: String): String? {
    val richText = Ksoup.parse(html).selectFirst(".RichText") ?: return null
    val nodes = richText.children()
    val anchorTexts = nodes.map { it.text().trim() }
    val occurrence = anchorTexts.count { it == anchor }
    if (occurrence == 0) return null
    val anchorIndex = anchorTexts.indices.filter { anchorTexts[it] == anchor }[occurrence - 1]
    if (nodes.lastOrNull()?.text()?.trim() == anchor) return null
    return nodes.drop(anchorIndex + 1).joinToString("") { it.outerHtml() }.ifBlank { null }
}
