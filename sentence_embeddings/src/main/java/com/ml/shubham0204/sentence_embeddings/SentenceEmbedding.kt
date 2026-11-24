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

// 与 JNI tokenizer 保留同一原包名，避免改变现有调用方和原生 ABI。
@file:Suppress("ktlint:standard:package-name")

package com.ml.shubham0204.sentence_embeddings

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.providers.NNAPIFlags
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.LongBuffer
import java.util.EnumSet
import kotlin.math.max
import kotlin.math.sqrt

class SentenceEmbedding {
    private lateinit var hfTokenizer: HFTokenizer
    private lateinit var ortEnvironment: OrtEnvironment
    private lateinit var ortSession: OrtSession
    private var useTokenTypeIds: Boolean = false
    private var outputTensorName: String = ""
    private var normalizeEmbedding: Boolean = false
    private var closed = false

    val isModelReady: Boolean
        get() = !closed && this::ortSession.isInitialized && this::hfTokenizer.isInitialized

    suspend fun init(
        modelFilepath: String,
        tokenizerBytes: ByteArray,
        useTokenTypeIds: Boolean,
        outputTensorName: String,
        useFP16: Boolean = false,
        useXNNPack: Boolean = false,
        normalizeEmbeddings: Boolean,
    ) = withContext(Dispatchers.IO) {
        hfTokenizer = HFTokenizer(tokenizerBytes)
        try {
            ortEnvironment = OrtEnvironment.getEnvironment()
            OrtSession.SessionOptions().use { options ->
                if (useFP16) options.addNnapi(EnumSet.of(NNAPIFlags.USE_FP16, NNAPIFlags.CPU_DISABLED))
                if (useXNNPack) options.addXnnpack(mapOf("intra_op_num_threads" to "2"))
                ortSession = ortEnvironment.createSession(modelFilepath, options)
            }
            this@SentenceEmbedding.useTokenTypeIds = useTokenTypeIds
            this@SentenceEmbedding.outputTensorName = outputTensorName
            this@SentenceEmbedding.normalizeEmbedding = normalizeEmbeddings
            Log.d(SentenceEmbedding::class.simpleName, "Input Names: ${ortSession.inputNames}")
            Log.d(SentenceEmbedding::class.simpleName, "Output Names: ${ortSession.outputNames}")
        } catch (error: Throwable) {
            try {
                close()
            } catch (closeError: Throwable) {
                error.addSuppressed(closeError)
            }
            throw error
        }
    }

    suspend fun encode(sentence: String): FloatArray =
        withContext(Dispatchers.IO) {
            check(isModelReady) { "Sentence embedding model is not ready" }
            val result = hfTokenizer.tokenize(sentence)
            val inputTensorMap = mutableMapOf<String, OnnxTensor>()
            try {
                inputTensorMap["input_ids"] = OnnxTensor.createTensor(
                    ortEnvironment,
                    LongBuffer.wrap(result.ids),
                    longArrayOf(1, result.ids.size.toLong()),
                )
                inputTensorMap["attention_mask"] = OnnxTensor.createTensor(
                    ortEnvironment,
                    LongBuffer.wrap(result.attentionMask),
                    longArrayOf(1, result.attentionMask.size.toLong()),
                )
                if (useTokenTypeIds) {
                    inputTensorMap["token_type_ids"] = OnnxTensor.createTensor(
                        ortEnvironment,
                        LongBuffer.wrap(result.tokenTypeIds),
                        longArrayOf(1, result.tokenTypeIds.size.toLong()),
                    )
                }
                ortSession.run(inputTensorMap).use { outputs ->
                    val tokenEmbeddings3D = outputs.get(0).value as Array<Array<FloatArray>>
                    val pooledEmbedding = meanPooling(tokenEmbeddings3D[0], result.attentionMask)
                    if (normalizeEmbedding) normalize(pooledEmbedding) else pooledEmbedding
                }
            } finally {
                inputTensorMap.values.forEach(OnnxTensor::close)
            }
        }

    private fun meanPooling(
        tokenEmbeddings: Array<FloatArray>,
        attentionMask: LongArray,
    ): FloatArray {
        var pooledEmbeddings = FloatArray(tokenEmbeddings[0].size) { 0f }
        var validTokenCount = 0

        tokenEmbeddings
            .filterIndexed { index, _ -> attentionMask[index] == 1L }
            .forEach { token ->
                validTokenCount++
                token.forEachIndexed { j, value ->
                    pooledEmbeddings[j] += value
                }
            }

        val divisor = max(validTokenCount, 1)
        pooledEmbeddings = pooledEmbeddings.map { it / divisor }.toFloatArray()

        return pooledEmbeddings
    }

    private fun normalize(embeddings: FloatArray): FloatArray {
        val norm = sqrt(embeddings.sumOf { it * it.toDouble() }).toFloat()
        return embeddings.map { it / norm }.toFloatArray()
    }

    @Synchronized
    fun close() {
        if (closed) return
        closed = true
        try {
            if (this::ortSession.isInitialized) ortSession.close()
        } finally {
            if (this::hfTokenizer.isInitialized) hfTokenizer.close()
        }
    }
}
