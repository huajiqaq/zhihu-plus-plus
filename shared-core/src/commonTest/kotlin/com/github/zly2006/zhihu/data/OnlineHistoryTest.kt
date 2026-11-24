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

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class OnlineHistoryTest {
    @Test
    fun decodesOnlineHistoryItem() {
        val item = ZhihuJson.decodeJson<OnlineHistoryItem>(
            ZhihuJson.json.parseToJsonElement(
                """
                {
                  "card_type": "read_history",
                  "data": {
                    "header": {
                      "icon": "https://example.com/icon.png",
                      "title": "标题",
                      "badges": ["https://example.invalid/type-badge.png"]
                    },
                    "content": {
                      "author_name": "作者",
                      "summary": "摘要",
                      "cover_image": "https://example.com/cover.png"
                    },
                    "action": {
                      "type": "open_url",
                      "url": "zhihu://answers/123"
                    },
                    "extra": {
                      "content_token": "123",
                      "content_type": "answer",
                      "read_time": 1716120000,
                      "question_token": "456"
                    },
                    "matrix": [
                      {
                        "type": "text",
                        "data": {
                          "text": "已阅读"
                        }
                      }
                    ]
                  }
                }
                """.trimIndent(),
            ),
        )

        assertEquals("read_history", item.cardType)
        assertEquals("标题", item.data.header.title)
        assertContentEquals(arrayOf("https://example.invalid/type-badge.png"), item.data.header.badges)
        assertEquals("zhihu://answers/123", item.data.action.url)
        assertEquals(
            "已阅读",
            item.data.matrix
                ?.single()
                ?.data
                ?.text,
        )
        assertEquals("456", item.data.extra.questionToken)
        assertEquals(HistoryType.ANSWER, HistoryType.fromContentType(item.data.extra.contentType))
        assertEquals(HistoryType.ANSWER, HistoryType.badgeType(HistoryType.fromContentType(item.data.extra.contentType)))
    }

    /** 官方 APK 11.12.0 的 content_type 词表；未知值统一「其他」，不暴露英文到筛选条。 */
    @Test
    fun officialContentTypesMapToChineseLabels() {
        assertEquals(HistoryType.ANSWER, HistoryType.fromContentType("answer"))
        assertEquals(HistoryType.ARTICLE, HistoryType.fromContentType("article"))
        assertEquals(HistoryType.PIN, HistoryType.fromContentType("pin"))
        assertEquals(HistoryType.QUESTION, HistoryType.fromContentType("question"))
        assertEquals(HistoryType.VIDEO, HistoryType.fromContentType("zvideo"))
        assertEquals(HistoryType.PERSON, HistoryType.fromContentType("people"))
        assertEquals(HistoryType.COLUMN, HistoryType.fromContentType("column"))
        assertEquals(HistoryType.BOOK, HistoryType.fromContentType("ebook"))
        assertEquals(HistoryType.ROUND_TABLE, HistoryType.fromContentType("roundtable"))
        assertEquals(HistoryType.SPECIAL, HistoryType.fromContentType("special"))
        assertEquals(HistoryType.OTHER, HistoryType.fromContentType("kmarket"))
        assertEquals(HistoryType.OTHER, HistoryType.fromContentType(null))
    }
}
