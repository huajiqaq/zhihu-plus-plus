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

import kotlin.math.sqrt

/** 各 ORT 后端只负责取出行优先的 token 向量，池化及数值校验在这里统一执行。 */
fun poolAndNormalizeTokenEmbeddings(
    flat: FloatArray,
    tokenCount: Int,
    dimension: Int,
    mask: LongArray,
): FloatArray {
    require(tokenCount > 0 && dimension == 768) { offlineNlpError(OfflineNlpErrorCode.INVALID_OUTPUT) }
    require(mask.size == tokenCount && flat.size.toLong() == tokenCount.toLong() * dimension) {
        offlineNlpError(OfflineNlpErrorCode.INVALID_OUTPUT)
    }
    val pooled = FloatArray(dimension)
    var validTokens = 0
    for (token in 0 until tokenCount) {
        require(mask[token] == 0L || mask[token] == 1L) { offlineNlpError(OfflineNlpErrorCode.INVALID_INPUT) }
        if (mask[token] == 0L) continue
        validTokens++
        val offset = token * dimension
        for (column in 0 until dimension) {
            val value = flat[offset + column]
            require(value.isFinite()) { offlineNlpError(OfflineNlpErrorCode.INVALID_OUTPUT) }
            pooled[column] += value
        }
    }
    require(validTokens > 0) { offlineNlpError(OfflineNlpErrorCode.INVALID_INPUT) }
    var squaredNorm = 0.0
    for (index in pooled.indices) {
        pooled[index] /= validTokens
        squaredNorm += pooled[index].toDouble() * pooled[index]
    }
    val norm = sqrt(squaredNorm)
    require(norm.isFinite() && norm > 0.0) { offlineNlpError(OfflineNlpErrorCode.INVALID_OUTPUT) }
    val divisor = norm.toFloat()
    require(divisor.isFinite() && divisor > 0f) { offlineNlpError(OfflineNlpErrorCode.INVALID_OUTPUT) }
    for (index in pooled.indices) pooled[index] /= divisor
    return pooled
}

internal fun offlineNlpCosineSimilarity(first: FloatArray, second: FloatArray): Double {
    require(first.size == 768 && second.size == first.size) { offlineNlpError(OfflineNlpErrorCode.INVALID_OUTPUT) }
    var dot = 0.0
    var firstSquaredNorm = 0.0
    var secondSquaredNorm = 0.0
    for (index in first.indices) {
        val firstValue = first[index].toDouble()
        val secondValue = second[index].toDouble()
        require(firstValue.isFinite() && secondValue.isFinite()) { offlineNlpError(OfflineNlpErrorCode.INVALID_OUTPUT) }
        dot += firstValue * secondValue
        firstSquaredNorm += firstValue * firstValue
        secondSquaredNorm += secondValue * secondValue
    }
    val denominator = sqrt(firstSquaredNorm) * sqrt(secondSquaredNorm)
    return if (denominator == 0.0) 0.0 else (dot / denominator).coerceIn(-1.0, 1.0)
}

internal fun offlineNlpCosineToScore(cosine: Double): Double = ((cosine + 1.0) / 2.0).coerceIn(0.0, 1.0)
