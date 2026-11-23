/*
 * Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.github.zly2006.zhihu.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.decodeFromJsonElement

/** 仅保存首次403的脱敏反馈，不累积请求历史。 */
class Http403Log(
    val file: Path,
) {
    private val failed = MutableStateFlow(false)
    private val feedback = MutableStateFlow<HttpFailureRecord?>(null)
    private var recordingStopped = false
    val revision: StateFlow<Long> = changes.asStateFlow()
    val writeFailed: StateFlow<Boolean> = failed.asStateFlow()
    val pendingFeedback: StateFlow<HttpFailureRecord?> = feedback.asStateFlow()

    suspend fun read(): HttpFailureRecord? = withContext(Dispatchers.IO) {
        mutex.withLock { readLocked() }
    }

    suspend fun record(record: HttpFailureRecord) {
        if (record.status != 403) return
        try {
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    if (recordingStopped) return@withLock
                    val existing = try {
                        readLocked()
                    } catch (_: SerializationException) {
                        null
                    }
                    if (existing == null) {
                        val first = record.redacted()
                        writeLocked(first)
                        feedback.value = first
                        changes.update { it + 1 }
                    }
                    failed.value = false
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // 记录失败不改变业务响应，也不把凭据输出到备用日志。
            failed.value = true
        }
    }

    fun dismissFeedback() {
        feedback.value = null
    }

    suspend fun clear(stopRecording: Boolean = false) = withContext(Dispatchers.IO) {
        mutex.withLock {
            SystemFileSystem.delete(file, mustExist = false)
            if (stopRecording) recordingStopped = true
            feedback.value = null
            failed.value = false
            changes.update { it + 1 }
        }
    }

    private fun readLocked(): HttpFailureRecord? {
        if (!SystemFileSystem.exists(file)) return null
        val json = ZhihuJson.json
        val element = SystemFileSystem.source(file).buffered().use { json.parseToJsonElement(it.readString()) }
        val raw = if (element is JsonArray) element.firstOrNull() else element
        val record = raw?.let { json.decodeFromJsonElement<HttpFailureRecord>(it) } ?: return null
        val redacted = record.redacted()
        // 数组格式只保留首条；旧记录重写为脱敏快照。
        if (element is JsonArray || redacted != record) {
            writeLocked(redacted)
            changes.update { it + 1 }
        }
        return redacted
    }

    private fun writeLocked(record: HttpFailureRecord) {
        val directory = requireNotNull(file.parent)
        SystemFileSystem.createDirectories(directory)
        val temporary = Path(directory, file.name + ".tmp")
        try {
            SystemFileSystem.sink(temporary).buffered().use {
                it.writeString(ZhihuJson.json.encodeToString(record))
            }
            SystemFileSystem.atomicMove(temporary, file)
        } finally {
            SystemFileSystem.delete(temporary, mustExist = false)
        }
    }

    private companion object {
        val mutex = Mutex()
        val changes = MutableStateFlow(0L)
    }
}
