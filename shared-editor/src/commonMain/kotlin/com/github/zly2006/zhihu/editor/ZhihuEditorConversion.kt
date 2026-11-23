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

package com.github.zly2006.zhihu.editor

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.github.zly2006.zhihu.markdown.zhihuHtmlToMarkdown

/** 编辑模式转换保留 Markdown 无法表示的格式、图片元数据及原始标题层级。 */
fun zhihuHtmlToEditorMarkdown(html: String): String {
    val document = Ksoup.parseBodyFragment(html)
    document.outputSettings().prettyPrint(false)
    return document
        .body()
        .childNodes()
        .joinToString("\n\n") { node ->
            if (node is Element && node.select("u, img, figure, span[style], sub, sup, kbd, table, h1, h2, h3, h4, h5, h6").isNotEmpty()) {
                node.outerHtml()
            } else {
                zhihuHtmlToMarkdown(node.outerHtml())
            }
        }.trim()
}
