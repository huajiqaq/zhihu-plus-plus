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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.github.zly2006.zhihu.nlp

import com.github.zly2006.zhihu.nlp.ort.ZhihuOrtRun
import com.github.zly2006.zhihu.nlp.ort.ZhihuOrtSession
import com.github.zly2006.zhihu.nlp.ort.zhihu_ort_allocate
import com.github.zly2006.zhihu.nlp.ort.zhihu_ort_cancel_run
import com.github.zly2006.zhihu.nlp.ort.zhihu_ort_close
import com.github.zly2006.zhihu.nlp.ort.zhihu_ort_create_run
import com.github.zly2006.zhihu.nlp.ort.zhihu_ort_encode
import com.github.zly2006.zhihu.nlp.ort.zhihu_ort_open
import com.github.zly2006.zhihu.nlp.ort.zhihu_ort_release_run
import com.github.zly2006.zhihu.nlp.ort.zhihu_ort_release_status
import com.github.zly2006.zhihu.nlp.ort.zhihu_ort_status_message
import com.github.zly2006.zhihu.platform.nativeAppPrivateDirectoryPath
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.utils.io.readAvailable
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import platform.CoreCrypto.CC_SHA256_CTX
import platform.CoreCrypto.CC_SHA256_Final
import platform.CoreCrypto.CC_SHA256_Init
import platform.CoreCrypto.CC_SHA256_Update
import platform.Foundation.NSFileManager
import platform.Foundation.NSMakeRange
import platform.Foundation.NSRegularExpression
import platform.Foundation.NSString
import platform.Foundation.NSUUID
import platform.Foundation.create
import platform.Foundation.decomposedStringWithCanonicalMapping
import platform.Foundation.numberOfMatchesInString
import platform.posix.O_WRONLY
import platform.posix.close
import platform.posix.fsync
import platform.posix.open
import platform.posix.rename

actual fun createOfflineNlpPlatform(): OfflineNlpPlatform = AppleOfflineNlpPlatform()

private class AppleOfflineNlpAssets(
    val modelPath: String,
    override val tokenizerJson: String,
) : OfflineNlpModelAssets

private class AppleOfflineNlpPlatform : OfflineNlpPlatform {
    override val unsupportedReason: OfflineNlpFailure? = null
    private val storageMutex = Mutex()
    private val categoryPatterns = listOf(
        "\\p{C}" to OfflineNlpUnicodeCategory.CONTROL_OR_UNASSIGNED,
        "\\p{Mn}" to OfflineNlpUnicodeCategory.NON_SPACING_MARK,
        "\\p{P}" to OfflineNlpUnicodeCategory.PUNCTUATION,
    ).map { (pattern, category) ->
        checkNotNull(NSRegularExpression.regularExpressionWithPattern(pattern, 0u, null)) to category
    }

    override fun normalizeNfd(text: String): String = NSString.create(string = text).decomposedStringWithCanonicalMapping

    override fun unicodeCategory(codePoint: Int): OfflineNlpUnicodeCategory {
        require(codePoint in 0..0x10ffff)
        val scalar = if (codePoint <= 0xffff) {
            codePoint.toChar().toString()
        } else {
            val supplementary = codePoint - 0x10000
            "${(0xd800 + (supplementary shr 10)).toChar()}${(0xdc00 + (supplementary and 0x3ff)).toChar()}"
        }
        val range = NSMakeRange(0u, scalar.length.convert())
        return categoryPatterns.firstOrNull { (pattern, _) ->
            pattern.numberOfMatchesInString(scalar, 0u, range) != 0uL
        }?.second ?: OfflineNlpUnicodeCategory.OTHER
    }

    override suspend fun prepareModel(
        spec: OfflineNlpModelSpec,
        onProgress: (OfflineNlpModelState) -> Unit,
    ): OfflineNlpModelAssets = withContext(Dispatchers.Default) {
        storageMutex.withLock {
            val directory = modelDirectory(spec)
            check(NSFileManager.defaultManager.createDirectoryAtPath(directory, true, null, null)) {
                offlineNlpError(OfflineNlpErrorCode.STORAGE_FAILED)
            }
            for (file in spec.files) {
                val path = "$directory/${file.name}"
                onProgress(OfflineNlpModelState.Verifying(file.name))
                if (!verifyCachedFile(path, file)) downloadFile(path, file, onProgress)
            }
            currentCoroutineContext().ensureActive()
            AppleOfflineNlpAssets(
                modelPath = "$directory/model.onnx",
                tokenizerJson = SystemFileSystem.source(Path("$directory/tokenizer.json")).buffered().use { it.readString() },
            )
        }
    }

    override suspend fun openSession(assets: OfflineNlpModelAssets, onInvalidated: (OfflineNlpFailure?) -> Unit): OfflineNlpSession {
        require(assets is AppleOfflineNlpAssets) { offlineNlpError(OfflineNlpErrorCode.ASSETS_MISMATCH) }
        var opened: AppleOfflineNlpSession? = null
        try {
            return withContext(Dispatchers.Default) {
                currentCoroutineContext().ensureActive()
                val owner = checkNotNull(zhihu_ort_allocate()) { offlineNlpError(OfflineNlpErrorCode.RUNTIME_LOAD) }
                try {
                    checkOrtStatus(owner, zhihu_ort_open(owner, assets.modelPath))
                    currentCoroutineContext().ensureActive()
                    AppleOfflineNlpSession(owner).also { opened = it }
                } catch (error: Throwable) {
                    zhihu_ort_close(owner)
                    throw error
                }
            }
        } catch (error: Throwable) {
            // withContext 返回时也可能取消，不能泄漏已创建但尚未交给 controller 的会话。
            withContext(NonCancellable) { opened?.close() }
            throw error
        }
    }

    override suspend fun deleteModel(spec: OfflineNlpModelSpec) = withContext(Dispatchers.Default) {
        storageMutex.withLock {
            val directory = modelDirectory(spec)
            if (NSFileManager.defaultManager.fileExistsAtPath(directory)) {
                check(NSFileManager.defaultManager.removeItemAtPath(directory, null)) { offlineNlpError(OfflineNlpErrorCode.DELETE_FAILED) }
            }
        }
    }

    private fun modelDirectory(spec: OfflineNlpModelSpec): String {
        require(safeComponent(spec.id) && safeComponent(spec.revision)) { offlineNlpError(OfflineNlpErrorCode.INVALID_MODEL) }
        require(spec.files.map { it.name }.toSet() == setOf("model.onnx", "tokenizer.json") && spec.files.size == 2) {
            offlineNlpError(OfflineNlpErrorCode.INVALID_MODEL)
        }
        spec.files.forEach { file ->
            require(safeComponent(file.name) && file.expectedBytes > 0 && file.sha256.matches(Regex("[0-9a-f]{64}"))) {
                offlineNlpError(OfflineNlpErrorCode.INVALID_MODEL)
            }
            val url = Url(file.url)
            require(url.protocol == URLProtocol.HTTPS && url.user.isNullOrEmpty() && url.password.isNullOrEmpty()) { offlineNlpError(OfflineNlpErrorCode.INVALID_SOURCE) }
            if (file.name == "tokenizer.json") require(file.expectedBytes <= 8 * 1024 * 1024) { offlineNlpError(OfflineNlpErrorCode.INVALID_TOKENIZER) }
        }
        return "${nativeAppPrivateDirectoryPath()}/offline-nlp/${spec.id}/${spec.revision}"
    }

    private suspend fun verifyCachedFile(path: String, file: OfflineNlpModelFile): Boolean {
        val metadata = SystemFileSystem.metadataOrNull(Path(path)) ?: return false
        if (!metadata.isRegularFile || metadata.size != file.expectedBytes) return false
        return sha256File(path) == file.sha256
    }

    private suspend fun downloadFile(
        path: String,
        file: OfflineNlpModelFile,
        onProgress: (OfflineNlpModelState) -> Unit,
    ) {
        val temporary = "$path.${NSUUID().UUIDString}.part"
        var primaryFailure: Throwable? = null
        try {
            val digest = memScoped {
                val sha = alloc<CC_SHA256_CTX>()
                check(CC_SHA256_Init(sha.ptr) == 1)
                var downloaded = 0L
                // 独立匿名 session 不共享账号、Cookie、URL 缓存或系统凭据。
                HttpClient(Darwin) {
                    expectSuccess = true
                    install(HttpTimeout) {
                        connectTimeoutMillis = 30_000
                        socketTimeoutMillis = 60_000
                        requestTimeoutMillis = 30 * 60_000
                    }
                    engine {
                        configureSession {
                            setHTTPCookieStorage(null)
                            setURLCredentialStorage(null)
                            setURLCache(null)
                            setHTTPShouldSetCookies(false)
                        }
                    }
                }.use { client ->
                    client.prepareGet(file.url) { header(HttpHeaders.AcceptEncoding, "identity") }.execute { response ->
                        check(response.status.value == 200) { offlineNlpError(OfflineNlpErrorCode.DOWNLOAD_HTTP, file.name, response.status.value) }
                        response.headers[HttpHeaders.ContentLength]?.toLongOrNull()?.let { length ->
                            check(length == file.expectedBytes) { offlineNlpError(OfflineNlpErrorCode.DOWNLOAD_SIZE, file.name) }
                        }
                        val buffer = ByteArray(64 * 1024)
                        val channel = response.bodyAsChannel()
                        onProgress(OfflineNlpModelState.Downloading(file.name, 0, file.expectedBytes))
                        SystemFileSystem.sink(Path(temporary)).buffered().use { output ->
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val count = channel.readAvailable(buffer, 0, buffer.size)
                                if (count == -1) break
                                if (count == 0) continue
                                check(count.toLong() <= file.expectedBytes - downloaded) { offlineNlpError(OfflineNlpErrorCode.DOWNLOAD_SIZE, file.name) }
                                output.write(buffer, 0, count)
                                buffer.usePinned { check(CC_SHA256_Update(sha.ptr, it.addressOf(0), count.convert()) == 1) }
                                downloaded += count
                                onProgress(OfflineNlpModelState.Downloading(file.name, downloaded, file.expectedBytes))
                            }
                        }
                    }
                }
                check(downloaded == file.expectedBytes) { offlineNlpError(OfflineNlpErrorCode.DOWNLOAD_SIZE, file.name) }
                onProgress(OfflineNlpModelState.Verifying(file.name))
                finishSha256(sha.ptr)
            }
            check(digest == file.sha256) { offlineNlpError(OfflineNlpErrorCode.DOWNLOAD_HASH, file.name) }
            currentCoroutineContext().ensureActive()
            val descriptor = open(temporary, O_WRONLY)
            check(descriptor >= 0) { offlineNlpError(OfflineNlpErrorCode.FILE_WRITE, file.name) }
            try {
                check(fsync(descriptor) == 0) { offlineNlpError(OfflineNlpErrorCode.FILE_WRITE, file.name) }
            } finally {
                close(descriptor)
            }
            currentCoroutineContext().ensureActive()
            check(rename(temporary, path) == 0) { offlineNlpError(OfflineNlpErrorCode.FILE_WRITE, file.name) }
        } catch (error: Throwable) {
            val failure = if (error is ResponseException) {
                OfflineNlpException(OfflineNlpFailure(OfflineNlpErrorCode.DOWNLOAD_HTTP, file.name, error.response.status.value), error)
            } else error
            primaryFailure = failure
            throw failure
        } finally {
            if (NSFileManager.defaultManager.fileExistsAtPath(temporary) && !NSFileManager.defaultManager.removeItemAtPath(temporary, null)) {
                val cleanupFailure = OfflineNlpException(OfflineNlpFailure(OfflineNlpErrorCode.FILE_CLEANUP))
                primaryFailure?.addSuppressed(cleanupFailure) ?: throw cleanupFailure
            }
        }
    }
}

private fun safeComponent(value: String): Boolean =
    value.isNotEmpty() && value != "." && value != ".." && value.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' }

private suspend fun sha256File(path: String): String = memScoped {
    val sha = alloc<CC_SHA256_CTX>()
    check(CC_SHA256_Init(sha.ptr) == 1)
    val buffer = ByteArray(64 * 1024)
    SystemFileSystem.source(Path(path)).buffered().use { input ->
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = input.readAtMostTo(buffer)
            if (count == -1) break
            if (count > 0) buffer.usePinned { check(CC_SHA256_Update(sha.ptr, it.addressOf(0), count.convert()) == 1) }
        }
    }
    finishSha256(sha.ptr)
}

private fun finishSha256(sha: CPointer<CC_SHA256_CTX>): String {
    val digest = ByteArray(32)
    digest.usePinned { check(CC_SHA256_Final(it.addressOf(0).reinterpret(), sha) == 1) }
    return digest.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
}

private class AppleOfflineNlpSession(private var owner: CPointer<ZhihuOrtSession>?) : OfflineNlpSession {
    private val mutex = Mutex()

    override suspend fun encode(inputIds: LongArray, attentionMask: LongArray, tokenTypeIds: LongArray): FloatArray =
        withContext(Dispatchers.Default) {
            mutex.withLock {
                require(inputIds.size in 1..512 && attentionMask.size == inputIds.size && tokenTypeIds.size == inputIds.size) {
                    offlineNlpError(OfflineNlpErrorCode.INVALID_INPUT)
                }
                require(attentionMask.all { it == 0L || it == 1L } && attentionMask.any { it == 1L }) { offlineNlpError(OfflineNlpErrorCode.INVALID_INPUT) }
                val session = checkNotNull(owner) { offlineNlpError(OfflineNlpErrorCode.SESSION_CLOSED) }
                currentCoroutineContext().ensureActive()
                memScoped {
                    val runPointer = alloc<CPointerVar<ZhihuOrtRun>>()
                    checkOrtStatus(session, zhihu_ort_create_run(session, runPointer.ptr))
                    val run = checkNotNull(runPointer.value)
                    val watcher = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                        try {
                            awaitCancellation()
                        } finally {
                            zhihu_ort_cancel_run(run)
                        }
                    }
                    try {
                        val flat = FloatArray(inputIds.size * 768)
                        val status = inputIds.usePinned { ids ->
                            attentionMask.usePinned { mask ->
                                tokenTypeIds.usePinned { types ->
                                    flat.usePinned { output ->
                                        zhihu_ort_encode(
                                            session,
                                            run,
                                            ids.addressOf(0),
                                            mask.addressOf(0),
                                            types.addressOf(0),
                                            inputIds.size.convert(),
                                            output.addressOf(0),
                                            flat.size.convert(),
                                        )
                                    }
                                }
                            }
                        }
                        val message = takeOrtError(session, status)
                        currentCoroutineContext().ensureActive()
                        if (message != null) throw OfflineNlpException(OfflineNlpFailure(OfflineNlpErrorCode.INFERENCE_FAILED), IllegalStateException(message))
                        poolAndNormalizeTokenEmbeddings(flat, inputIds.size, 768, attentionMask)
                    } finally {
                        withContext(NonCancellable) {
                            watcher.cancelAndJoin()
                            zhihu_ort_release_run(run)
                        }
                    }
                }
            }
        }

    override suspend fun close() = withContext(NonCancellable + Dispatchers.Default) {
        mutex.withLock {
            owner?.let { zhihu_ort_close(it) }
            owner = null
        }
    }
}

private fun takeOrtError(owner: CPointer<ZhihuOrtSession>, status: COpaquePointer?): String? =
    if (status == null) null else try {
        zhihu_ort_status_message(owner, status)?.toKString() ?: "Unknown ONNX Runtime status"
    } finally {
        zhihu_ort_release_status(owner, status)
    }

private fun checkOrtStatus(owner: CPointer<ZhihuOrtSession>, status: COpaquePointer?) {
    val message = takeOrtError(owner, status)
    if (message != null) throw OfflineNlpException(OfflineNlpFailure(OfflineNlpErrorCode.INFERENCE_FAILED), IllegalStateException(message))
}
