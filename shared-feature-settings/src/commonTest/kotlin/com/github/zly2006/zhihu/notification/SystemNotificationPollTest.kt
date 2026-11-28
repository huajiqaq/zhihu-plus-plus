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

package com.github.zly2006.zhihu.notification

import com.github.zly2006.zhihu.data.MobileNotificationContent
import com.github.zly2006.zhihu.data.MobileNotificationTimelineItem
import com.github.zly2006.zhihu.data.ZhihuPaging
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.CommentHolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class SystemNotificationPollTest {
    @Test
    fun initialBaselineReadsThroughTheLastPageWithoutDeliveringOldNotifications() = runTest {
        val visited = mutableListOf<String>()
        val secondUrl = systemNotificationPageUrl("comment") + "&offset=20"
        val result = pollSystemNotifications(SystemNotificationCheckpoint(), setOf(NotificationType.REPLY_COMMENT)) { url ->
            visited += url
            if (visited.size == 1) page(listOf(reply("old-2")), secondUrl) else page(listOf(reply("old-1")))
        }
        assertEquals(listOf(systemNotificationPageUrl("comment"), secondUrl), visited)
        assertTrue(result.events.isEmpty())
        assertEquals(setOf("old-1", "old-2"), result.checkpoint.seenByEntry["comment"])
        assertEquals(setOf(NotificationType.REPLY_COMMENT), result.checkpoint.baselinedTypes)
    }

    @Test
    fun catchesUpMultiplePagesToKnownBoundaryAndNeverRepeatsAnEvent() = runTest {
        val previous = checkpoint("comment", setOf("old"), setOf(NotificationType.REPLY_COMMENT))
        val secondUrl = systemNotificationPageUrl("comment") + "&offset=20"
        var calls = 0
        val result = pollSystemNotifications(previous, setOf(NotificationType.REPLY_COMMENT)) {
            if (++calls == 1) page(listOf(reply("new-2"), reply("new-2")), secondUrl) else page(listOf(reply("new-1"), reply("old")), "must-not-be-requested")
        }
        assertEquals(2, calls)
        assertEquals(listOf("comment:new-2", "comment:new-1"), result.events.map { it.key })
        assertEquals(CommentHolder("81", Article(type = ArticleType.Answer, id = 42)), result.events.first().destination)
        val repeated = pollSystemNotifications(result.checkpoint, setOf(NotificationType.REPLY_COMMENT)) {
            page(listOf(reply("new-2"), reply("new-1")), "must-not-be-requested")
        }
        assertTrue(repeated.events.isEmpty())
    }

    @Test
    fun pageFailureDoesNotCommitPartialProgressAndRetryFindsAllNewEvents() = runTest {
        val previous = checkpoint("comment", setOf("old"), setOf(NotificationType.REPLY_COMMENT))
        val secondUrl = systemNotificationPageUrl("comment") + "&offset=20"
        var calls = 0
        assertFailsWith<IllegalStateException> {
            pollSystemNotifications(previous, setOf(NotificationType.REPLY_COMMENT)) {
                if (++calls == 1) page(listOf(reply("new-2")), secondUrl) else error("synthetic failed page")
            }
        }
        assertEquals(setOf("old"), previous.seenByEntry["comment"])
        val retry = pollSystemNotifications(previous, setOf(NotificationType.REPLY_COMMENT)) {
            page(listOf(reply("new-2"), reply("new-1"), reply("old")))
        }
        assertEquals(setOf("comment:new-1", "comment:new-2"), retry.events.map { it.key }.toSet())
    }

    private fun checkpoint(entry: String, ids: Set<String>, types: Set<NotificationType>) = SystemNotificationCheckpoint(
        seenByEntry = mapOf(entry to ids),
        baselinedTypes = types,
    )

    private fun page(items: List<MobileNotificationTimelineItem>, next: String = "") = SystemNotificationPage(
        items,
        ZhihuPaging(isEnd = next.isEmpty(), next = next),
    )

    private fun reply(id: String) = MobileNotificationTimelineItem(
        id = id,
        isRead = false,
        content = MobileNotificationContent(
            subTitle = "回复了你的评论",
            targetLink = "zhihu://comment/list/answer/42?anchor_comment_id=81",
            abstractText = "synthetic body not for notification storage",
        ),
    )
}
