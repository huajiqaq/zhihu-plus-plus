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

package com.github.zly2006.zhihu.data

import com.fleeksoft.ksoup.Ksoup
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** 真实动态页的完整 envelope 保持正文和链接标题原样；合成变体仅用于摘要优先级与空字段边界。 */
class PinLinkCardDisplayRegressionTest {
    private val page = ZhihuJson.json
        .parseToJsonElement(
            checkNotNull(javaClass.getResource("/home-follow/follow-dynamic-page-0-sanitized.json")).readText(),
        ).jsonObject
    private val linkOnlyFeed = page.getValue("data").jsonArray[4].jsonObject
    private val linkOnlyTarget = linkOnlyFeed.getValue("target").jsonObject
    private val realLink = linkOnlyTarget
        .getValue("content")
        .jsonArray
        .single()
        .jsonObject
    private val realLinkTitle = "如何评价亚马逊前华人女工程师疑似按照ChatGPT的建议自行减少抗癫痫药物后猝死这一事件？"

    @Test
    fun realLinkOnlyPinUsesFullDraftTitleAsSummaryWithoutInventingPinTitle() {
        assertEquals(14, page.getValue("data").jsonArray.size)
        assertTrue("paging" in page)
        assertEquals("2099187254094837687", linkOnlyTarget.getValue("id").jsonPrimitive.content)
        assertEquals("", linkOnlyTarget.getValue("content_html").jsonPrimitive.content)
        assertEquals("", linkOnlyTarget.getValue("excerpt_title").jsonPrimitive.content)
        assertEquals("link_card", realLink.getValue("type").jsonPrimitive.content)
        assertEquals("", realLink.getValue("data_draft_cover").jsonPrimitive.content)
        assertEquals(realLinkTitle, realLink.getValue("data_draft_title").jsonPrimitive.content)

        val feed = ZhihuJson.decodeJson<Feed>(linkOnlyFeed)
        val pin = assertIs<Feed.PinTarget>(feed.target)
        assertIs<DataHolder.Pin.ContentLinkCard>(pin.content.single())
        val item = feed.toDisplayItem()

        assertEquals("", item.title)
        assertEquals(realLinkTitle, item.summary)
        assertFalse(item.isFiltered)
        assertFalse(item.isQualityFiltered)
        assertSame(feed, item.feed)
        assertEquals("pin:2099187254094837687", item.stableKey)
    }

    @Test
    fun realTextAndLinkPinKeepsBodySummaryAheadOfDraftTitle() {
        val feed = ZhihuJson.decodeJson<Feed>(page.getValue("data").jsonArray[6])
        val pin = assertIs<Feed.PinTarget>(feed.target)
        val text = pin.content.filterIsInstance<DataHolder.Pin.ContentText>().single()
        assertEquals(1, pin.content.filterIsInstance<DataHolder.Pin.ContentLinkCard>().size)
        val body = Ksoup.parse(text.content).text()
        assertTrue(body.isNotBlank())
        assertEquals("", text.title)

        val item = feed.toDisplayItem()

        assertEquals("", item.title)
        assertEquals(body, item.summary)
        assertFalse(item.isFiltered)
    }

    @Test
    fun nonblankTextBodyKeepsPriorityOverLinkDraftTitle() {
        val textTarget = page
            .getValue("data")
            .jsonArray[6]
            .jsonObject
            .getValue("target")
            .jsonObject
        val text = textTarget.getValue("content").jsonArray.first()
        val target = JsonObject(linkOnlyTarget + ("content" to JsonArray(listOf(text, realLink))))
        val feed = ZhihuJson.decodeJson<Feed>(JsonObject(linkOnlyFeed + ("target" to target)))
        val body = Ksoup
            .parse(
                text.jsonObject
                    .getValue("content")
                    .jsonPrimitive.content,
            ).text()
        assertTrue(body.isNotBlank())

        val item = feed.toDisplayItem()

        assertEquals("", item.title)
        assertEquals(body, item.summary)
        assertFalse(item.isFiltered)
    }

    @Test
    fun nonblankExcerptKeepsPriorityOverLinkDraftTitle() {
        val target = JsonObject(linkOnlyTarget + ("excerpt_title" to JsonPrimitive("<p>已有摘要优先</p>")))
        val feed = ZhihuJson.decodeJson<Feed>(JsonObject(linkOnlyFeed + ("target" to target)))

        val item = feed.toDisplayItem()

        assertEquals("", item.title)
        assertEquals("已有摘要优先", item.summary)
        assertFalse(item.isFiltered)
    }

    @Test
    fun missingAndBlankDraftTitlesAreSkippedInContentOrder() {
        val links = JsonArray(
            listOf(
                JsonObject(realLink - "data_draft_title"),
                JsonObject(realLink + ("data_draft_title" to JsonPrimitive(" \t "))),
                realLink,
                JsonObject(realLink + ("data_draft_title" to JsonPrimitive("后一个链接标题"))),
            ),
        )
        val target = JsonObject(linkOnlyTarget + ("content" to links))
        val feed = ZhihuJson.decodeJson<Feed>(JsonObject(linkOnlyFeed + ("target" to target)))

        val item = feed.toDisplayItem()

        assertEquals("", item.title)
        assertEquals(realLinkTitle, item.summary)
        assertFalse(item.isFiltered)
    }

    @Test
    fun emptyLinkTitlesRemainUnfilteredWithoutInventingSummary() {
        val links = listOf(
            JsonObject(realLink - "data_draft_title"),
            JsonObject(realLink + ("data_draft_title" to JsonPrimitive(""))),
            JsonObject(realLink + ("data_draft_title" to JsonPrimitive(" \t "))),
        )
        for (link in links) {
            val target = JsonObject(linkOnlyTarget + ("content" to JsonArray(listOf(link))))
            val feed = ZhihuJson.decodeJson<Feed>(JsonObject(linkOnlyFeed + ("target" to target)))
            assertIs<DataHolder.Pin.ContentLinkCard>(assertIs<Feed.PinTarget>(feed.target).content.single())

            val item = feed.toDisplayItem()

            assertEquals("", item.title)
            assertNull(item.summary)
            assertFalse(item.isFiltered)
            assertFalse(item.isQualityFiltered)
            assertSame(feed, item.feed)
            assertEquals("pin:2099187254094837687", item.stableKey)
        }
    }
}
