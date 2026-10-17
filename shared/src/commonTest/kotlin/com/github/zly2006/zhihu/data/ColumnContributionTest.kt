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

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * #776：`column-contributions` 的条目是 `{ column: {...}, contributions_count }`，
 * 不能直接按 Column 解析，否则 id 缺失会导致整个专栏页为空。
 */
class ColumnContributionTest {
    @Test
    fun decodesNestedColumnContribution() {
        val contribution = ZhihuJson.decodeJson<DataHolder.ColumnContribution>(
            Json.parseToJsonElement(
                """
                {
                  "column": {
                    "accept_submission": true,
                    "column_type": "normal",
                    "title": "Clean Data",
                    "url": "http://www.zhihu.com/api/v4/columns/chenqin",
                    "type": "column",
                    "id": "chenqin",
                    "updated": 1590877462,
                    "image_url": "https://picx.zhimg.com/4b70deef7_720w.jpg"
                  },
                  "contributions_count": 34
                }
                """.trimIndent(),
            ),
        )

        assertEquals("chenqin", contribution.column.id)
        assertEquals("Clean Data", contribution.column.title)
        assertEquals(34, contribution.contributionsCount)
    }
}
