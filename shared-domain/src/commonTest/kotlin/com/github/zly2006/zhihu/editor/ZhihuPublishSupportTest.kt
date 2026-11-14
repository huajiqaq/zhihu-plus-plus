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

import com.github.zly2006.zhihu.data.ZhihuJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

class ZhihuPublishSupportTest {
    @Test
    fun parsesPublishedContentIdFromNestedPublishResult() {
        assertEquals(
            123456789L,
            parsePublishContentId("""{"publish":{"id":"123456789"}}"""),
        )
    }

    @Test
    fun parsesPublishedContentIdFromPinPublishResult() {
        assertEquals(
            2051493233436386836L,
            parsePublishContentId("""{"id":"2051493233436386836","type":"pin"}"""),
        )
    }

    @Test
    fun publishTracePreservesWireFormat() {
        val before = Clock.System.now().toEpochMilliseconds()
        val traceId = newPublishTraceId()
        val after = Clock.System.now().toEpochMilliseconds()
        assertTrue(Regex("""\d+,[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}""").matches(traceId))
        assertTrue(traceId.substringBefore(',').toLong() in before..after)
        assertEquals(
            """{"traceId":"$traceId"}""",
            ZhihuJson.json.encodeToString(PublishTrace.serializer(), PublishTrace(traceId)),
        )
    }
}
