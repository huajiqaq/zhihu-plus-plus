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

import com.hankcs.hanlp.HanLP
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** 沿用 Android Full 的 HanLP 候选、MMR 预算与无模型时的顺序权重，不触发模型下载。 */
val jvmKeywordWeightExtractor = KeywordWeightExtractor { text, topN ->
    if (text.isBlank() || topN <= 0) return@KeywordWeightExtractor emptyList()
    currentCoroutineContext().ensureActive()
    val poolSize = max(topN * 5, topN + 3)
    val candidates = HanLP.extractKeyword(text, min(60 * 2, poolSize))
        .mapNotNull { it?.trim() }
        .filter { it.length >= 2 }
        .distinct()
    currentCoroutineContext().ensureActive()
    if (candidates.isEmpty()) return@KeywordWeightExtractor emptyList()
    val ranked = OfflineNlpController.rankKeywordCandidates(text, candidates, topN)
    if (ranked.isNotEmpty()) {
        ranked
    } else {
        val fallback = candidates.take(topN)
        val denominator = max(1, fallback.size - 1)
        fallback.mapIndexed { index, keyword ->
            KeywordWithWeight(keyword, 1.0 - index.toDouble() / max(1, denominator))
        }
    }
}
