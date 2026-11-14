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

import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import com.fleeksoft.ksoup.Ksoup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CommentTextLinksTest {
    @Test
    fun chinesePunctuationAfterUrlIsNotPartOfTheLink() {
        val matches = findPlainUrls("看这里 https://example.com/a?b=1。后面")

        assertEquals(1, matches.size)
        assertEquals("https://example.com/a?b=1", matches.single().url)
    }

    @Test
    fun multipleUrlsAreFoundInOrder() {
        val matches = findPlainUrls("A https://a.example.com B http://b.example.com/c?d=2")

        assertEquals(listOf("https://a.example.com", "http://b.example.com/c?d=2"), matches.map { it.url })

        val linkColor = Color(0xff245680)
        val openedUrls = mutableListOf<String>()
        val componentsUsed = mutableSetOf<String>()
        val rendered = buildAnnotatedString {
            dfsSimple(
                node = Ksoup
                    .parseBodyFragment(
                        """<p><span><a href="https://a.example.com">标题</a> [微笑] https://b.example.com/plain。</span></p>""",
                    ).body(),
                onNavigate = { error("External links must not navigate internally") },
                openExternalUrl = { openedUrls += it },
                linkColor = linkColor,
                componentUsed = componentsUsed,
            )
        }
        assertEquals("标题 [微笑] https://b.example.com/plain。", rendered.text)
        val annotations = rendered.getLinkAnnotations(0, rendered.length)
        val links = annotations.map { it.item as LinkAnnotation.Clickable }
        assertEquals(listOf("https://a.example.com", "https://b.example.com/plain"), links.map { it.tag })
        assertEquals(listOf("标题", "https://b.example.com/plain"), annotations.map { rendered.text.substring(it.start, it.end) })
        links.forEach { link ->
            assertEquals(
                TextLinkStyles(style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
                link.styles,
            )
            assertNotNull(link.linkInteractionListener).onClick(link)
        }
        assertEquals(listOf("https://a.example.com", "https://b.example.com/plain"), openedUrls)
        val emojiKey = assertNotNull(commentEmojiInlineKey("[微笑]"))
        assertEquals(setOf(emojiKey), componentsUsed)
        val emojiStart = rendered.text.indexOf("[微笑]")
        assertEquals(
            buildAnnotatedString { appendInlineContent(emojiKey, "[微笑]") },
            rendered.subSequence(emojiStart, emojiStart + "[微笑]".length),
        )
    }

    @Test
    fun textWithoutUrlProducesNoMatches() {
        assertEquals(emptyList(), findPlainUrls("没有链接的评论"))
    }
}
