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

import android.util.Log
import com.hankcs.hanlp.HanLP
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object NLPService {
    private const val TAG = "NLPService"
    private const val MAX_KEYWORD_CANDIDATES = 60
    private const val KEYWORD_CANDIDATE_MULTIPLIER = 5
    private const val MAX_EMBEDDING_TEXT_LENGTH = 2048
    private const val MIN_KEYWORD_LENGTH = 2
    private const val MAX_SEGMENT_COUNT = 48
    private const val MAX_SEGMENT_CHAR_LENGTH = 160
    private const val SEGMENT_WINDOW_STEP = 90
    private const val MIN_SEGMENT_CHAR_LENGTH = 4
    private val SENTENCE_SPLIT_REGEX = "[。！？!?\\n]+".toRegex()

    suspend fun extractKeywords(text: String, topN: Int = 5): List<String> = withContext(Dispatchers.Default) {
        extractKeywordsInternal(text, topN).map { it.keyword }
    }

    suspend fun extractKeywordsWithWeight(text: String, topN: Int = 5): List<KeywordWithWeight> = withContext(Dispatchers.Default) {
        extractKeywordsInternal(text, topN)
    }

    private suspend fun extractKeywordsInternal(text: String, topN: Int): List<KeywordWithWeight> {
        if (text.isBlank() || topN <= 0) return emptyList()
        val candidates = generateKeywordCandidates(text, topN)
        if (candidates.isEmpty()) return emptyList()

        val mmrResult = selectKeywordCandidatesWithMmr(text, candidates, topN, ::encodeTextOrNull)
        if (mmrResult.isNotEmpty()) return mmrResult

        val fallback = candidates.take(topN)
        val denominator = max(1, fallback.size - 1)
        return fallback.mapIndexed { index, keyword ->
            KeywordWithWeight(keyword, 1.0 - index.toDouble() / max(1, denominator))
        }
    }

    private suspend fun calculatePhraseMatchScore(
        phraseKeywords: String,
        segmentEmbeddings: List<FloatArray?>,
    ): Double {
        val normalizedPhrase = phraseKeywords.trim()
        if (normalizedPhrase.isEmpty()) return 0.0
        val phraseEmbedding = encodeTextOrNull(normalizedPhrase) ?: return 0.0
        var bestScore = 0.0
        for (embedding in segmentEmbeddings) {
            if (embedding != null) {
                val score = cosineToScore(cosineSimilarity(phraseEmbedding, embedding))
                if (score > bestScore) bestScore = score
            }
        }
        return bestScore
    }

    private suspend fun buildSegmentEmbeddings(text: String): List<FloatArray?> {
        val normalized = text.trim()
        val segments = splitTextIntoSegments(normalized)
            .ifEmpty {
                val fallback = normalized.take(MAX_SEGMENT_CHAR_LENGTH)
                if (fallback.isNotEmpty()) listOf(fallback) else emptyList()
            }.take(MAX_SEGMENT_COUNT)
        return segments.map { encodeTextOrNull(it) }
    }

    private fun splitTextIntoSegments(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val rawSentences = SENTENCE_SPLIT_REGEX
            .split(text)
            .map { it.trim() }
            .filter { it.length >= MIN_SEGMENT_CHAR_LENGTH }

        val segments = mutableListOf<String>()
        for (sentence in rawSentences) {
            if (sentence.isEmpty()) continue
            if (sentence.length <= MAX_SEGMENT_CHAR_LENGTH) {
                segments.add(sentence)
            } else {
                var start = 0
                while (start < sentence.length) {
                    val end = min(sentence.length, start + MAX_SEGMENT_CHAR_LENGTH)
                    val chunk = sentence.substring(start, end)
                    segments.add(chunk)
                    if (segments.size >= MAX_SEGMENT_COUNT) return segments
                    start += SEGMENT_WINDOW_STEP
                }
            }
            if (segments.size >= MAX_SEGMENT_COUNT) break
        }

        if (segments.isEmpty()) {
            val fallback = text.take(MAX_SEGMENT_CHAR_LENGTH)
            if (fallback.isNotEmpty()) segments.add(fallback)
        }
        return segments
    }

    private fun generateKeywordCandidates(text: String, topN: Int): List<String> {
        if (text.isBlank()) return emptyList()
        val poolSize = max(topN * KEYWORD_CANDIDATE_MULTIPLIER, topN + 3)
        val maxPoolSize = min(MAX_KEYWORD_CANDIDATES * 2, poolSize)
        return HanLP
            .extractKeyword(text, maxPoolSize)
            .mapNotNull { it?.trim() }
            .filter { it.length >= MIN_KEYWORD_LENGTH }
            .distinct()
    }

    private suspend fun encodeTextOrNull(text: String): FloatArray? {
        val normalized = text.trim().take(MAX_EMBEDDING_TEXT_LENGTH)
        if (normalized.isBlank()) return null
        return try {
            SentenceEmbeddingManager.encode(normalized)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "NLP 编码失败", e)
            null
        }
    }

    private fun cosineSimilarity(
        first: FloatArray,
        second: FloatArray,
    ): Double {
        var dot = 0.0
        var magFirst = 0.0
        var magSecond = 0.0
        val size = min(first.size, second.size)
        for (i in 0 until size) {
            val v1 = first[i].toDouble()
            val v2 = second[i].toDouble()
            dot += v1 * v2
            magFirst += v1 * v1
            magSecond += v2 * v2
        }
        val denominator = sqrt(magFirst) * sqrt(magSecond)
        if (denominator == 0.0) return 0.0
        return dot / denominator
    }

    private fun cosineToScore(value: Double): Double = ((value + 1.0) / 2.0).coerceIn(0.0, 1.0)

    /** [blockedPhrases] 每项为用空格分隔的关键词；返回达到阈值的短语，按相似度降序。 */
    suspend fun checkBlockedPhrases(
        text: String,
        blockedPhrases: List<String>,
        threshold: Double = 0.8,
    ): List<Pair<String, Double>> = withContext(Dispatchers.Default) {
        if (text.isBlank() || blockedPhrases.isEmpty()) return@withContext emptyList()
        try {
            val matches = mutableListOf<Pair<String, Double>>()
            val segmentEmbeddings = buildSegmentEmbeddings(text)
            for (phrase in blockedPhrases) {
                val score = calculatePhraseMatchScore(phrase, segmentEmbeddings)
                if (score >= threshold) matches.add(phrase to score)
            }
            matches.sortedByDescending { it.second }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "checkBlockedPhrases failed", e)
            emptyList()
        }
    }
}
