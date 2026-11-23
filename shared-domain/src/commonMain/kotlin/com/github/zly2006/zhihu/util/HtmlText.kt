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

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import com.github.zly2006.zhihu.data.Feed
import com.github.zly2006.zhihu.data.FeedDisplayItem
import com.github.zly2006.zhihu.data.decodeSearchText
import com.github.zly2006.zhihu.data.isSearchResult
import com.github.zly2006.zhihu.data.target

fun parseEmphasizedHtmlText(
    html: String,
    emphasisColor: Color,
): AnnotatedString {
    val spans = mutableListOf<AnnotatedString.Range<SpanStyle>>()
    val text = html.decodeSearchText { start, end ->
        spans += AnnotatedString.Range(SpanStyle(color = emphasisColor), start, end)
    }
    return AnnotatedString(text, spanStyles = spans)
}

/** 想法卡片的内文标题是普通文本，摘要在提取 HTML 时已解码。 */
fun FeedDisplayItem.cardText(text: String, emphasisColor: Color): AnnotatedString =
    if (feed.isSearchResult && feed?.target !is Feed.PinTarget) {
        parseEmphasizedHtmlText(text, emphasisColor)
    } else {
        AnnotatedString(text)
    }

/** 使用当前主题主色显示 [html] 中的强调文本。 */
@Composable
fun parseEmphasizedHtmlTextWithTheme(html: String): AnnotatedString {
    val emphasisColor = MaterialTheme.colorScheme.primary
    return parseEmphasizedHtmlText(html, emphasisColor)
}
