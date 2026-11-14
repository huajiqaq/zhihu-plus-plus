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

package com.github.zly2006.zhihu.viewmodel.comment

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import com.github.zly2006.zhihu.navigation.SegmentCommentHolder
import io.ktor.client.HttpClient
import io.ktor.util.AttributeKey
import io.ktor.util.collections.ConcurrentMap

object SegmentCommentEvents {
    private data class ContentKey(
        val type: String,
        val id: String,
    )

    internal class Scope {
        val deltas: SnapshotStateMap<String, Int> = mutableStateMapOf()
        val ownDeltas: SnapshotStateMap<String, Int> = mutableStateMapOf()
    }

    /** 水位须在详情请求前捕获，仅随成功正文发布；快照不持有账号客户端。 */
    class Snapshot internal constructor(
        private val scope: Scope,
        private val watermark: Map<String, Int>,
        private val ownWatermark: Map<String, Int>,
    ) {
        /** 组合期读取可订阅本正文的变化；返回值不随之后的评论操作改变。 */
        fun asMap(ownComments: Boolean = false): Map<String, Int> {
            val current = if (ownComments) scope.ownDeltas.toMap() else scope.deltas.toMap()
            val baseline = if (ownComments) ownWatermark else watermark
            return current.mapValues { (id, value) -> value - (baseline[id] ?: 0) }
        }

        fun effectiveDelta(segmentIds: Iterable<String>, ownComments: Boolean = false): Int {
            val current = asMap(ownComments)
            return segmentIds.map(String::trim).distinct().sumOf { current[it] ?: 0 }
        }
    }

    /** 增量由请求客户端持有，旧快照只保留所属正文 Scope。 */
    private val scopesKey = AttributeKey<ConcurrentMap<ContentKey, Scope>>("ZhihuSegmentCommentScopes")

    /** 与评论 VM 的成功回调一样，在 Main 串行调用，避免捕获与增量写入交错。 */
    fun capture(client: HttpClient, contentType: String, contentId: String): Snapshot {
        val scope = client.attributes
            .computeIfAbsent(scopesKey) { ConcurrentMap() }
            .computeIfAbsent(ContentKey(contentType.trim().lowercase().removeSuffix("s"), contentId.trim())) { Scope() }
        return Snapshot(scope, scope.deltas.toMap(), scope.ownDeltas.toMap())
    }

    /** 须在 Main 串行调用，与正文水位捕获保持一致。 */
    fun notifySubmitted(client: HttpClient, target: SegmentCommentHolder) {
        notifyChanged(client, target, 1, ownComment = true)
    }

    /** Main 串行调用；复合 segmentId 的一次变更只计入首个 ID。 */
    fun notifyChanged(client: HttpClient, target: SegmentCommentHolder, delta: Int, ownComment: Boolean) {
        val id = target.segmentId
            .split(',')
            .firstOrNull { it.isNotBlank() }
            ?.trim() ?: return
        val scope = client.attributes
            .computeIfAbsent(scopesKey) { ConcurrentMap() }
            .computeIfAbsent(
                ContentKey(
                    target.contentType
                        .trim()
                        .lowercase()
                        .removeSuffix("s"),
                    target.contentId.trim(),
                ),
            ) { Scope() }
        scope.deltas[id] = (scope.deltas[id] ?: 0) + delta
        if (ownComment) scope.ownDeltas[id] = (scope.ownDeltas[id] ?: 0) + delta
    }
}
