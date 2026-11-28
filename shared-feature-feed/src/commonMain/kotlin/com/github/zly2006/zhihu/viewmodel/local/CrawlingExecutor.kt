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

package com.github.zly2006.zhihu.viewmodel.local

import com.github.zly2006.zhihu.account.ZhihuAccountStore
import com.github.zly2006.zhihu.account.accountIdentityKey
import com.github.zly2006.zhihu.navigation.zhihuQuestionFeedsUrl
import io.ktor.client.call.body
import io.ktor.http.URLProtocol
import io.ktor.http.isSuccess
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray

class CrawlingExecutor(
    private val dao: LocalContentDao,
    private val accountStore: ZhihuAccountStore,
) {
    suspend fun recoverAbandonedTasks() = withContext(Dispatchers.Default) {
        /** 恢复只能在无活抓取的 OS 锁内进行，不能按执行时长判断。 */
        withCrawlingLock(dao.databaseFilePath()) { dao.recoverAbandonedTasks() }
    }

    suspend fun executeTask(task: CrawlingTask) {
        withContext(Dispatchers.Default) {
            withCrawlingLock(dao.databaseFilePath()) {
                if (task.status != CrawlingStatus.NotStarted || task.executedAt == Long.MAX_VALUE) return@withCrawlingLock
                /** 代次须严格递增，避免取消后同毫秒重领接受旧快照。 */
                val executedAt = maxOf(Clock.System.now().toEpochMilliseconds(), (task.executedAt ?: 0L) + 1L)
                var claimed = false
                try {
                    /** 领取提交与归属接收不可被取消打断；网络仍可取消。 */
                    withContext(NonCancellable) { claimed = dao.claimTask(task.id, task.executedAt, executedAt) == 1 }
                    if (!claimed) return@withCrawlingLock
                    currentCoroutineContext().ensureActive()

                    val results = when (task.reason) {
                        CrawlingReason.Following -> fetchResults(zhihuFollowingRecommendUrl(), task)
                        CrawlingReason.Trending -> fetchResults(zhihuTopstoryRecommendUrl(limit = 20), task, scoreMultiplier = 1.2)
                        CrawlingReason.UpvotedQuestion -> executeUpvotedQuestionTask(task)
                        CrawlingReason.FollowingUpvote -> fetchResults(zhihuFollowingUpvoteRecommendUrl(limit = 20), task)
                        CrawlingReason.CollaborativeFiltering -> executeCollaborativeFilteringTask(task)
                    }

                    dao.completeTask(task.id, executedAt, results)
                } catch (e: CancellationException) {
                    /** 保留代次并仅释放本次领取，不能复活已完成任务。 */
                    if (claimed) {
                        try {
                            withContext(NonCancellable) { dao.finishTask(task.id, executedAt, CrawlingStatus.NotStarted) }
                        } catch (restoreError: Exception) {
                            if (restoreError !== e) e.addSuppressed(restoreError)
                        }
                    }
                    throw e
                } catch (e: Exception) {
                    if (!claimed) throw e
                    withContext(NonCancellable) { dao.finishTask(task.id, executedAt, CrawlingStatus.Failed, e.message) }
                }
            }
        }
    }

    private suspend fun executeUpvotedQuestionTask(task: CrawlingTask): List<CrawlingResult> {
        val questionId = extractQuestionIdFromUrl(task.url) ?: return emptyList()
        return fetchResults(zhihuQuestionFeedsUrl(questionId, limit = 20), task, scoreMultiplier = 1.1)
    }

    private suspend fun executeCollaborativeFilteringTask(task: CrawlingTask): List<CrawlingResult> {
        val nowMillis = Clock.System.now().toEpochMilliseconds()
        val recentLikes = dao.getBehaviorsByActionSince("like", nowMillis - 7 * 24 * 60 * 60 * 1000)

        if (recentLikes.isEmpty()) {
            return fetchResults(zhihuTopstoryRecommendUrl(limit = 20), task, CrawlingReason.Trending, 1.2)
        }
        return fetchResults(zhihuTopstoryRecommendUrl(limit = 20), task, scoreMultiplier = 0.9)
    }

    private suspend fun fetchResults(
        url: String,
        task: CrawlingTask,
        reason: CrawlingReason = task.reason,
        scoreMultiplier: Double = 1.0,
    ): List<CrawlingResult> {
        val owner = accountStore.client
        val accountId = owner.load().accountIdentityKey()
        val feedArray = crawlingFeedArrayForTesting?.invoke(url)
            ?: owner.withAuthenticatedClient { client, _ ->
                check(owner.httpClient() === client) { "账号已切换" }
                val response = owner.get(url) { url { protocol = URLProtocol.HTTPS } }
                check(accountStore.client === owner) { "账号已切换" }
                if (response.status.isSuccess()) response.body<JsonObject>()["data"]?.jsonArray else null
            }
            ?: JsonArray(emptyList())
        if (accountStore.client !== owner) throw CancellationException("账号已切换")
        return feedArray.mapNotNull { element ->
            try {
                createCrawlingResult(element, task.id, reason, scoreMultiplier, accountId)
            } catch (_: Exception) {
                null
            }
        }
    }
}

var crawlingFeedArrayForTesting: (suspend (String) -> JsonArray)? = null
