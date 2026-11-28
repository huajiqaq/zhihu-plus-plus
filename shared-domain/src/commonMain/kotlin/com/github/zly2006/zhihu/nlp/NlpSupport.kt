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

import kotlin.math.min
import kotlin.math.sqrt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun interface KeywordWeightExtractor {
    suspend fun extractKeywordsWithWeight(text: String, topN: Int): List<KeywordWithWeight>
}

object KeywordAnalyzerCore {
    suspend fun extractFromFeedWithWeight(
        title: String,
        excerpt: String? = null,
        content: String? = null,
        topN: Int = 10,
        extractor: KeywordWeightExtractor,
    ): List<KeywordWithWeight> = withContext(Dispatchers.Default) {
        if (title.isBlank()) return@withContext emptyList()

        runCatching {
            val weightedText = buildWeightedText(title, excerpt, content)
            val keywordsWithWeight = extractor.extractKeywordsWithWeight(weightedText, topN * 3)
            processKeywordsWithWeight(keywordsWithWeight, topN)
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            emptyList()
        }
    }

    fun buildWeightedText(
        title: String,
        excerpt: String?,
        content: String?,
    ): String = buildString {
        repeat(3) {
            append(title)
            append(" ")
        }

        if (!excerpt.isNullOrBlank()) {
            append(excerpt)
            append(" ")
        }

        if (!content.isNullOrBlank()) {
            append(content.take(500))
        }
    }

    fun processKeywordsWithWeight(
        keywordsWithWeight: List<KeywordWithWeight>,
        topN: Int,
    ): List<KeywordWithWeight> {
        val keywordMap = mutableMapOf<String, Double>()
        for ((keyword, weight) in keywordsWithWeight) {
            val current = keywordMap[keyword] ?: 0.0
            if (weight > current) {
                keywordMap[keyword] = weight
            }
        }

        return keywordMap
            .asSequence()
            .filter { (keyword, _) -> keyword.length >= 2 }
            .filter { (keyword, _) -> !isStopWord(keyword) }
            .filter { (keyword, _) -> !isNumberOnly(keyword) }
            .map { (keyword, weight) -> KeywordWithWeight(keyword, weight) }
            .sortedByDescending { it.weight }
            .take(topN)
            .toList()
    }

    fun isStopWord(word: String): Boolean = word in stopWords

    fun isNumberOnly(word: String): Boolean = word.all { it.isDigit() }

    private val stopWords = setOf(
        "的",
        "了",
        "在",
        "是",
        "我",
        "有",
        "和",
        "就",
        "不",
        "人",
        "都",
        "一",
        "一个",
        "上",
        "也",
        "很",
        "到",
        "说",
        "要",
        "去",
        "你",
        "会",
        "着",
        "没有",
        "看",
        "好",
        "自己",
        "这",
        "个",
        "们",
        "那",
        "来",
        "么",
        "吗",
        "时",
        "大",
        "地",
        "为",
        "子",
        "中",
        "你们",
        "对",
        "生",
        "能",
        "而",
        "得",
        "与",
        "她",
        "他",
        "以",
        "及",
        "于",
        "用",
        "就是",
        "比",
        "啊",
        "呢",
        "吧",
        "呀",
        "嘛",
        "哦",
        "哪",
        "什么",
        "怎么",
    )
}

/** Android Full 与桌面共享原 MMR 选择规则；模型生命周期和候选生成仍归平台所有。 */
suspend fun selectKeywordCandidatesWithMmr(
    text: String,
    candidates: List<String>,
    topN: Int,
    encodeTextOrNull: suspend (String) -> FloatArray?,
): List<KeywordWithWeight> {
    val documentEmbedding = encodeTextOrNull(text) ?: return emptyList()
    val limitedCandidates = candidates.take(min(candidates.size, 60))
    if (limitedCandidates.isEmpty()) return emptyList()

    val candidateEmbeddings = buildMap {
        for (candidate in limitedCandidates) {
            val embedding = encodeTextOrNull(candidate)
            if (embedding != null) put(candidate, embedding)
        }
    }
    if (candidateEmbeddings.isEmpty()) return emptyList()

    val remaining = candidateEmbeddings.toMutableMap()
    val selected = mutableListOf<KeywordWithWeight>()
    while (selected.size < topN && remaining.isNotEmpty()) {
        var bestKey: String? = null
        var bestRelevance = 0.0
        var bestScore = Double.NEGATIVE_INFINITY
        remaining.forEach { (word, embedding) ->
            val relevance = keywordCandidateCosineSimilarity(documentEmbedding, embedding)
            val redundancy = selected.maxOfOrNull { keyword ->
                val otherEmbedding = candidateEmbeddings[keyword.keyword]
                if (otherEmbedding != null) keywordCandidateCosineSimilarity(embedding, otherEmbedding) else 0.0
            } ?: 0.0
            val score = 0.65 * relevance - (1 - 0.65) * redundancy
            if (score > bestScore) {
                bestScore = score
                bestKey = word
                bestRelevance = relevance
            }
        }
        val keyword = bestKey ?: break
        selected.add(KeywordWithWeight(keyword, ((bestRelevance + 1.0) / 2.0).coerceIn(0.0, 1.0)))
        remaining.remove(keyword)
    }
    return selected
}

/** 保持 Android 原余弦的计算顺序及短向量边界，不借用语义过滤额外校验改变排序。 */
private fun keywordCandidateCosineSimilarity(first: FloatArray, second: FloatArray): Double {
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

data class KeywordWithWeight(
    val keyword: String,
    val weight: Double,
)
