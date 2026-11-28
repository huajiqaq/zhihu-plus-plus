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

package com.github.zly2006.zhihu.nlp

import com.github.zly2006.zhihu.util.AppLog
import com.github.zly2006.zhihu.viewmodel.filter.KeywordSemanticMatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** 非 Android 平台离线模型的唯一内存所有者；首页匹配绝不触发模型下载。 */
object OfflineNlpController : KeywordSemanticMatcher {
    private const val MAX_EMBEDDING_TEXT_LENGTH = 2048
    private const val MAX_SEGMENT_COUNT = 48
    private const val MAX_SEGMENT_CHAR_LENGTH = 160
    private const val SEGMENT_WINDOW_STEP = 90
    private const val MIN_SEGMENT_CHAR_LENGTH = 4
    private const val MAX_CACHED_EMBEDDINGS = 256
    private val sentenceSplitRegex = "[。！？!?\\n]+".toRegex()
    private val platform = createOfflineNlpPlatform()
    private val mutex = Mutex()
    private val inferenceMutex = Mutex()
    private val mutableState = MutableStateFlow<OfflineNlpModelState>(
        platform.unsupportedReason?.let(OfflineNlpModelState::Unsupported) ?: OfflineNlpModelState.NotDownloaded,
    )
    val state: StateFlow<OfflineNlpModelState> = mutableState.asStateFlow()
    private val mutablePolicyGeneration = MutableStateFlow(0L)

    /** Ready 进入/离开时单调递增，供已加载内容重新执行过滤，不由下载进度或偏好键驱动。 */
    val policyGeneration: StateFlow<Long> = mutablePolicyGeneration.asStateFlow()
    val isSupported: Boolean get() = platform.unsupportedReason == null
    val modelSpec: OfflineNlpModelSpec get() = offlineNlpModelSpec
    private val invalidationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var activeLifetime: SessionLifetime? = null
    private var session: OfflineNlpSession? = null
    private var tokenizer: OfflineBertTokenizer? = null
    private var preparedResources = false

    // 缓存仅在进程内有界复用、不落盘；重新加载/卸载即清空，不混用模型或分词版本。
    private val embeddingCache = linkedMapOf<String, FloatArray>()

    /** 调用者的协程拥有此次下载；取消仅取消自身发起的操作，等待者不会取消当前持有者。 */
    suspend fun load() = withContext(Dispatchers.Default) {
        mutex.withLock {
            checkSupported()
            if (session != null && activeLifetime?.invalidation?.value == null) return@withLock
            if (session != null) {
                closeSession()
                publishState(OfflineNlpModelState.Unloaded)
            }
            val lifetime = SessionLifetime()
            activeLifetime = lifetime
            val cancelledState = if (preparedResources) OfflineNlpModelState.Unloaded else OfflineNlpModelState.NotDownloaded
            var openingSession: OfflineNlpSession? = null
            try {
                publishState(OfflineNlpModelState.Loading)
                val assets = platform.prepareModel(modelSpec, ::publishState)
                preparedResources = true
                publishState(OfflineNlpModelState.Loading)
                val loadedTokenizer = try {
                    OfflineBertTokenizer(assets.tokenizerJson, platform, modelSpec.maxTokens)
                } catch (error: IllegalArgumentException) {
                    throw OfflineNlpException(OfflineNlpFailure(OfflineNlpErrorCode.INVALID_TOKENIZER), error)
                }
                openingSession = platform.openSession(assets) { message -> onSessionInvalidated(lifetime, message) }
                currentCoroutineContext().ensureActive()
                lifetime.checkValid()
                session = openingSession
                tokenizer = loadedTokenizer
                embeddingCache.clear()
                publishState(OfflineNlpModelState.Ready)
            } catch (error: Throwable) {
                withContext(NonCancellable) {
                    try {
                        openingSession?.close()
                    } catch (closeError: Throwable) {
                        error.addSuppressed(closeError)
                    }
                    activeLifetime = null
                    session = null
                    tokenizer = null
                    embeddingCache.clear()
                    publishState(
                        when (error) {
                            is CancellationException -> if (preparedResources) OfflineNlpModelState.Unloaded else cancelledState
                            is SessionInvalidatedException -> error.invalidation.state
                            else -> OfflineNlpModelState.Error(error.toOfflineNlpFailure(OfflineNlpErrorCode.LOAD_FAILED))
                        },
                    )
                }
                throw error
            }
        }
    }

    /** 不删除下载文件；关闭完成后才发布 Unloaded。 */
    suspend fun unload() {
        mutex.withLock {
            checkSupported()
            closeSession()
            publishState(if (preparedResources) OfflineNlpModelState.Unloaded else OfflineNlpModelState.NotDownloaded)
        }
    }

    /** 关闭会话与删除资源都完成才发布 NotDownloaded，不删除任何屏蔽规则。 */
    suspend fun deleteModel() {
        mutex.withLock {
            checkSupported()
            closeSession()
            try {
                publishState(OfflineNlpModelState.Loading)
                platform.deleteModel(modelSpec)
                preparedResources = false
                publishState(OfflineNlpModelState.NotDownloaded)
            } catch (error: CancellationException) {
                publishState(if (preparedResources) OfflineNlpModelState.Unloaded else OfflineNlpModelState.NotDownloaded)
                throw error
            } catch (error: Throwable) {
                publishState(OfflineNlpModelState.Error(error.toOfflineNlpFailure(OfflineNlpErrorCode.DELETE_FAILED)))
                throw error
            }
        }
    }

    override suspend fun checkBlockedPhrases(
        text: String,
        blockedPhrases: List<String>,
        threshold: Double,
    ): List<Pair<String, Double>> {
        if (text.isBlank() || blockedPhrases.isEmpty() || mutableState.value != OfflineNlpModelState.Ready) return emptyList()
        require(threshold.isFinite() && threshold in 0.0..1.0) { offlineNlpError(OfflineNlpErrorCode.INVALID_INPUT) }
        return withContext(Dispatchers.Default) {
            // 只排队等待已有推理。检查状态后若下载/卸载先获得资源锁，首页也不等待它。
            inferenceMutex.withLock {
                if (!mutex.tryLock()) return@withLock emptyList()
                try {
                    val currentSession = session ?: return@withLock emptyList()
                    val currentTokenizer = tokenizer ?: return@withLock emptyList()
                    val lifetime = activeLifetime ?: return@withLock emptyList()
                    if (mutableState.value != OfflineNlpModelState.Ready) return@withLock emptyList()
                    lifetime.checkValid()
                    val segments = splitTextIntoSegments(text.trim())
                    val segmentEmbeddings = segments.map { encode(it, currentSession, currentTokenizer, lifetime) }
                    val matches = mutableListOf<Pair<String, Double>>()
                    for (phrase in blockedPhrases) {
                        currentCoroutineContext().ensureActive()
                        if (phrase.isBlank()) continue
                        val phraseEmbedding = encode(phrase, currentSession, currentTokenizer, lifetime)
                        val score = segmentEmbeddings.maxOfOrNull { embedding ->
                            offlineNlpCosineToScore(offlineNlpCosineSimilarity(phraseEmbedding, embedding))
                        } ?: 0.0
                        if (score >= threshold) matches.add(phrase to score)
                    }
                    lifetime.checkValid()
                    matches.sortedByDescending { it.second }
                } catch (error: SessionInvalidatedException) {
                    publishState(error.invalidation.state)
                    try {
                        closeSession()
                    } catch (closeError: Exception) {
                        AppLog.e("OfflineNlpController", "失效会话关闭失败", closeError)
                    }
                    emptyList()
                } catch (error: CancellationException) {
                    // Web 推理取消会终止 Worker；统一关闭以免下一次错误复用已失效的后端。
                    try {
                        closeSession()
                        publishState(OfflineNlpModelState.Unloaded)
                    } catch (closeError: Throwable) {
                        error.addSuppressed(closeError)
                    }
                    throw error
                } catch (error: Throwable) {
                    AppLog.e("OfflineNlpController", "离线语义匹配失败", error)
                    try {
                        closeSession()
                    } catch (closeError: Throwable) {
                        error.addSuppressed(closeError)
                    }
                    publishState(OfflineNlpModelState.Error(error.toOfflineNlpFailure(OfflineNlpErrorCode.INFERENCE_FAILED)))
                    emptyList()
                } finally {
                    mutex.unlock()
                }
            }
        }
    }

    /** 自动候选仅借用已加载模型；未就绪返回空结果，让 HanLP 按原候选顺序回退。 */
    suspend fun rankKeywordCandidates(
        text: String,
        candidates: List<String>,
        topN: Int,
    ): List<KeywordWithWeight> {
        if (text.isBlank() || candidates.isEmpty() || topN <= 0 || mutableState.value != OfflineNlpModelState.Ready) return emptyList()
        return withContext(Dispatchers.Default) {
            inferenceMutex.withLock {
                if (!mutex.tryLock()) return@withLock emptyList()
                try {
                    val currentSession = session ?: return@withLock emptyList()
                    val currentTokenizer = tokenizer ?: return@withLock emptyList()
                    val lifetime = activeLifetime ?: return@withLock emptyList()
                    if (mutableState.value != OfflineNlpModelState.Ready) return@withLock emptyList()
                    lifetime.checkValid()
                    val ranked = selectKeywordCandidatesWithMmr(text, candidates, topN) { value ->
                        if (value.isBlank()) null else encode(value, currentSession, currentTokenizer, lifetime)
                    }
                    lifetime.checkValid()
                    ranked
                } catch (error: SessionInvalidatedException) {
                    publishState(error.invalidation.state)
                    try {
                        closeSession()
                    } catch (closeError: Exception) {
                        AppLog.e("OfflineNlpController", "失效会话关闭失败", closeError)
                    }
                    emptyList()
                } catch (error: CancellationException) {
                    try {
                        closeSession()
                        publishState(OfflineNlpModelState.Unloaded)
                    } catch (closeError: Throwable) {
                        error.addSuppressed(closeError)
                    }
                    throw error
                } catch (error: Throwable) {
                    AppLog.e("OfflineNlpController", "离线关键词排序失败", error)
                    try {
                        closeSession()
                    } catch (closeError: Throwable) {
                        error.addSuppressed(closeError)
                    }
                    publishState(OfflineNlpModelState.Error(error.toOfflineNlpFailure(OfflineNlpErrorCode.RANKING_FAILED)))
                    emptyList()
                } finally {
                    mutex.unlock()
                }
            }
        }
    }

    private suspend fun encode(
        text: String,
        currentSession: OfflineNlpSession,
        currentTokenizer: OfflineBertTokenizer,
        lifetime: SessionLifetime,
    ): FloatArray {
        currentCoroutineContext().ensureActive()
        lifetime.checkValid()
        val normalized = text.trim().take(MAX_EMBEDDING_TEXT_LENGTH)
        embeddingCache.remove(normalized)?.let { cached ->
            embeddingCache[normalized] = cached
            return cached
        }
        val tokens = currentTokenizer.encode(normalized)
        val embedding = currentSession.encode(tokens.inputIds, tokens.attentionMask, tokens.tokenTypeIds)
        lifetime.checkValid()
        require(embedding.size == modelSpec.embeddingDimension && embedding.all { it.isFinite() }) {
            offlineNlpError(OfflineNlpErrorCode.INVALID_OUTPUT)
        }
        embeddingCache[normalized] = embedding
        while (embeddingCache.size > MAX_CACHED_EMBEDDINGS) embeddingCache.remove(embeddingCache.keys.first())
        return embedding
    }

    private fun splitTextIntoSegments(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val sentences = sentenceSplitRegex.split(text).map(String::trim).filter { it.length >= MIN_SEGMENT_CHAR_LENGTH }
        val segments = mutableListOf<String>()
        for (sentence in sentences) {
            if (sentence.length <= MAX_SEGMENT_CHAR_LENGTH) {
                segments.add(sentence)
            } else {
                var start = 0
                while (start < sentence.length) {
                    segments.add(sentence.substring(start, minOf(sentence.length, start + MAX_SEGMENT_CHAR_LENGTH)))
                    if (segments.size >= MAX_SEGMENT_COUNT) return segments
                    start += SEGMENT_WINDOW_STEP
                }
            }
            if (segments.size >= MAX_SEGMENT_COUNT) break
        }
        if (segments.isEmpty()) segments.add(text.take(MAX_SEGMENT_CHAR_LENGTH))
        return segments
    }

    /** 所有状态发布均发生在资源锁内，generation 不受下载进度及重复回调影响。 */
    private fun publishState(next: OfflineNlpModelState) {
        val readinessChanged = (mutableState.value == OfflineNlpModelState.Ready) != (next == OfflineNlpModelState.Ready)
        mutableState.value = next
        if (readinessChanged) mutablePolicyGeneration.value += 1
    }

    private fun onSessionInvalidated(lifetime: SessionLifetime, failure: OfflineNlpFailure?) {
        val invalidation = SessionInvalidation(failure)
        // StateFlow CAS 也覆盖后台线程事件；先标记失效，让尚未取得资源锁的清理不留下缓存命中窗口。
        if (!lifetime.invalidation.compareAndSet(null, invalidation)) return
        invalidationScope.launch {
            mutex.withLock {
                if (activeLifetime !== lifetime) return@withLock
                publishState(invalidation.state)
                try {
                    closeSession()
                } catch (error: Exception) {
                    AppLog.e("OfflineNlpController", "失效会话关闭失败", error)
                }
            }
        }
    }

    private class SessionLifetime {
        val invalidation = MutableStateFlow<SessionInvalidation?>(null)

        fun checkValid() {
            invalidation.value?.let { throw SessionInvalidatedException(it) }
        }
    }

    private class SessionInvalidation(val failure: OfflineNlpFailure?) {
        val state: OfflineNlpModelState
            get() = failure?.let(OfflineNlpModelState::Error) ?: OfflineNlpModelState.Unloaded
    }

    private class SessionInvalidatedException(val invalidation: SessionInvalidation) :
        IllegalStateException(invalidation.failure?.code?.name ?: OfflineNlpErrorCode.SESSION_CLOSED.name)

    private fun checkSupported() {
        platform.unsupportedReason?.let { reason ->
            publishState(OfflineNlpModelState.Unsupported(reason))
            throw OfflineNlpException(reason)
        }
    }

    private suspend fun closeSession() = withContext(NonCancellable) {
        val closing = session
        activeLifetime = null
        session = null
        tokenizer = null
        embeddingCache.clear()
        try {
            closing?.close()
        } catch (error: Throwable) {
            publishState(OfflineNlpModelState.Error(error.toOfflineNlpFailure(OfflineNlpErrorCode.CLOSE_FAILED)))
            throw error
        }
    }
}
