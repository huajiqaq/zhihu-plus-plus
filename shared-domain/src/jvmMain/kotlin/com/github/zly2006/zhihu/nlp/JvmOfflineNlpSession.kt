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

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import java.nio.LongBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** JVM CPU 推理只负责张量和 ORT 资源所有权；数值后处理使用 common 实现。 */
internal class JvmOfflineNlpSession private constructor(
    private val environment: OrtEnvironment,
    private val session: OrtSession,
    private val spec: OfflineNlpModelSpec,
) : OfflineNlpSession {
    private var closed = false

    override suspend fun encode(
        inputIds: LongArray,
        attentionMask: LongArray,
        tokenTypeIds: LongArray,
    ): FloatArray = withContext(Dispatchers.IO) {
        check(!closed) { offlineNlpError(OfflineNlpErrorCode.SESSION_CLOSED) }
        require(inputIds.size in 2..spec.maxTokens && attentionMask.size == inputIds.size && tokenTypeIds.size == inputIds.size) {
            offlineNlpError(OfflineNlpErrorCode.INVALID_INPUT)
        }
        require(inputIds.all { it >= 0L } && attentionMask.all { it == 0L || it == 1L } && tokenTypeIds.all { it == 0L }) {
            offlineNlpError(OfflineNlpErrorCode.INVALID_INPUT)
        }
        currentCoroutineContext().ensureActive()
        val shape = longArrayOf(1, inputIds.size.toLong())
        val inputs = mutableMapOf<String, OnnxTensor>()
        try {
            inputs["input_ids"] = OnnxTensor.createTensor(environment, LongBuffer.wrap(inputIds), shape)
            inputs["attention_mask"] = OnnxTensor.createTensor(environment, LongBuffer.wrap(attentionMask), shape)
            inputs["token_type_ids"] = OnnxTensor.createTensor(environment, LongBuffer.wrap(tokenTypeIds), shape)
            session.run(inputs, setOf(spec.outputName)).use { outputs ->
                currentCoroutineContext().ensureActive()
                val output = outputs.get(spec.outputName).orElseThrow { OfflineNlpException(OfflineNlpFailure(OfflineNlpErrorCode.INVALID_OUTPUT)) }
                require(output is OnnxTensor && output.info.type == OnnxJavaType.FLOAT) { offlineNlpError(OfflineNlpErrorCode.INVALID_OUTPUT) }
                require(output.info.shape.contentEquals(longArrayOf(1, inputIds.size.toLong(), spec.embeddingDimension.toLong()))) {
                    offlineNlpError(OfflineNlpErrorCode.INVALID_OUTPUT)
                }
                val flat = FloatArray(inputIds.size * spec.embeddingDimension)
                val values = output.floatBuffer ?: offlineNlpError(OfflineNlpErrorCode.INVALID_OUTPUT)
                require(values.remaining() == flat.size) { offlineNlpError(OfflineNlpErrorCode.INVALID_OUTPUT) }
                values.get(flat)
                poolAndNormalizeTokenEmbeddings(flat, inputIds.size, spec.embeddingDimension, attentionMask)
            }
        } finally {
            inputs.values.forEach(OnnxTensor::close)
        }
    }

    override suspend fun close() = withContext(Dispatchers.IO) {
        if (!closed) {
            closed = true
            session.close()
        }
        // OrtEnvironment.getEnvironment() 是运行库的进程级实例，不随单模型关闭。
    }

    companion object {
        fun open(assets: JvmOfflineNlpModelAssets): JvmOfflineNlpSession {
            val environment = OrtEnvironment.getEnvironment()
            // 本地推理不发送运行库使用遥测；关闭失败时不创建会话。
            environment.setTelemetry(false)
            val session = OrtSession.SessionOptions().use { options ->
                options.setIntraOpNumThreads(2)
                options.setInterOpNumThreads(1)
                environment.createSession(assets.modelFile.absolutePath, options)
            }
            try {
                val expectedInputs = setOf("input_ids", "attention_mask", "token_type_ids")
                require(session.inputNames == expectedInputs) { offlineNlpError(OfflineNlpErrorCode.INVALID_MODEL) }
                for ((_, input) in session.inputInfo) {
                    val info = input.info as? TensorInfo ?: offlineNlpError(OfflineNlpErrorCode.INVALID_INPUT)
                    require(info.type == OnnxJavaType.INT64 && info.shape.size == 2 && (info.shape[0] == 1L || info.shape[0] < 0L)) {
                        offlineNlpError(OfflineNlpErrorCode.INVALID_MODEL)
                    }
                }
                val output = session.outputInfo[assets.spec.outputName]?.info as? TensorInfo
                    ?: offlineNlpError(OfflineNlpErrorCode.INVALID_OUTPUT)
                require(output.type == OnnxJavaType.FLOAT && output.shape.size == 3 && output.shape[2] == assets.spec.embeddingDimension.toLong()) {
                    offlineNlpError(OfflineNlpErrorCode.INVALID_OUTPUT)
                }
                return JvmOfflineNlpSession(environment, session, assets.spec)
            } catch (error: Throwable) {
                try {
                    session.close()
                } catch (closeError: Throwable) {
                    error.addSuppressed(closeError)
                }
                throw error
            }
        }
    }
}
