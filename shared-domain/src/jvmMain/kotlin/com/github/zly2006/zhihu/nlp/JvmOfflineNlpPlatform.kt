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

import com.github.zly2006.zhihu.desktop.desktopZhihuDataFile
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.utils.io.readAvailable
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

actual fun createOfflineNlpPlatform(): OfflineNlpPlatform = JvmOfflineNlpPlatform()

internal data class JvmOfflineNlpModelAssets(
    val modelFile: File,
    val spec: OfflineNlpModelSpec,
    override val tokenizerJson: String,
) : OfflineNlpModelAssets

/** 构造和能力判断不引用 ORT，只有用户显式加载模型时才初始化 JNI。 */
private class JvmOfflineNlpPlatform : OfflineNlpPlatform {
    override val unsupportedReason: OfflineNlpFailure? = jvmOfflineNlpUnsupportedReason()

    override fun normalizeNfd(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)

    override fun unicodeCategory(codePoint: Int): OfflineNlpUnicodeCategory = when (Character.getType(codePoint)) {
        Character.CONTROL.toInt(), Character.FORMAT.toInt(), Character.UNASSIGNED.toInt(),
        Character.PRIVATE_USE.toInt(), Character.SURROGATE.toInt(),
        -> OfflineNlpUnicodeCategory.CONTROL_OR_UNASSIGNED
        Character.NON_SPACING_MARK.toInt() -> OfflineNlpUnicodeCategory.NON_SPACING_MARK
        Character.CONNECTOR_PUNCTUATION.toInt(), Character.DASH_PUNCTUATION.toInt(),
        Character.START_PUNCTUATION.toInt(), Character.END_PUNCTUATION.toInt(),
        Character.INITIAL_QUOTE_PUNCTUATION.toInt(), Character.FINAL_QUOTE_PUNCTUATION.toInt(),
        Character.OTHER_PUNCTUATION.toInt(),
        -> OfflineNlpUnicodeCategory.PUNCTUATION
        else -> OfflineNlpUnicodeCategory.OTHER
    }

    override suspend fun prepareModel(
        spec: OfflineNlpModelSpec,
        onProgress: (OfflineNlpModelState) -> Unit,
    ): OfflineNlpModelAssets = withContext(Dispatchers.IO) {
        unsupportedReason?.let { throw OfflineNlpException(it) }
        val directory = modelDirectory(spec)
        Files.createDirectories(directory.toPath())
        val totalBytes = spec.files.sumOf { it.expectedBytes }
        var completedBytes = 0L
        var client: HttpClient? = null
        try {
            for (file in spec.files) {
                currentCoroutineContext().ensureActive()
                val destination = File(directory, file.name)
                onProgress(OfflineNlpModelState.Verifying(file.name))
                if (verifyLocalFile(destination, file)) {
                    completedBytes += file.expectedBytes
                    continue
                }
                val freeBytes = directory.usableSpace
                check(freeBytes <= 0 || freeBytes >= file.expectedBytes + 16L * 1024 * 1024) {
                    offlineNlpError(OfflineNlpErrorCode.DISK_FULL, file.name)
                }
                val downloadClient = client ?: HttpClient(CIO) {
                    expectSuccess = true
                    install(HttpTimeout) {
                        connectTimeoutMillis = 30_000
                        socketTimeoutMillis = 60_000
                        requestTimeoutMillis = 60L * 60 * 1000
                    }
                }.also { client = it }
                downloadFile(downloadClient, file, destination, completedBytes, totalBytes, onProgress)
                completedBytes += file.expectedBytes
            }
            val tokenizerFile = File(directory, "tokenizer.json")
            check(tokenizerFile.length() in 1..2_000_000) { offlineNlpError(OfflineNlpErrorCode.INVALID_TOKENIZER) }
            JvmOfflineNlpModelAssets(
                modelFile = File(directory, "model.onnx"),
                spec = spec,
                tokenizerJson = tokenizerFile.readBytes().decodeToString(throwOnInvalidSequence = true),
            )
        } finally {
            client?.close()
        }
    }

    override suspend fun openSession(
        assets: OfflineNlpModelAssets,
        onInvalidated: (failure: OfflineNlpFailure?) -> Unit,
    ): OfflineNlpSession {
        unsupportedReason?.let { throw OfflineNlpException(it) }
        require(assets is JvmOfflineNlpModelAssets) { offlineNlpError(OfflineNlpErrorCode.ASSETS_MISMATCH) }
        var openingSession: OfflineNlpSession? = null
        return try {
            withContext(Dispatchers.IO) {
                JvmOfflineNlpSession.open(assets).also { openingSession = it }
            }
        } catch (error: Throwable) {
            withContext(NonCancellable) {
                try {
                    openingSession?.close()
                } catch (closeError: Throwable) {
                    error.addSuppressed(closeError)
                }
            }
            // JNI 缺失或静态初始化失败是平台加载失败，保留 cause 交给普通 UI 错误路径。
            if (error is LinkageError) throw OfflineNlpException(OfflineNlpFailure(OfflineNlpErrorCode.RUNTIME_LOAD), error)
            if (error is OutOfMemoryError) throw OfflineNlpException(OfflineNlpFailure(OfflineNlpErrorCode.OUT_OF_MEMORY), error)
            throw error
        }
    }

    override suspend fun deleteModel(spec: OfflineNlpModelSpec) = withContext(Dispatchers.IO) {
        val directory = modelDirectory(spec)
        if (directory.exists()) check(directory.deleteRecursively()) { offlineNlpError(OfflineNlpErrorCode.DELETE_FAILED) }
    }

    private fun modelDirectory(spec: OfflineNlpModelSpec): File {
        val safeComponent = Regex("[a-zA-Z0-9._-]+")
        require(spec.id.matches(safeComponent) && spec.id !in setOf(".", "..")) { offlineNlpError(OfflineNlpErrorCode.INVALID_MODEL) }
        require(spec.revision.matches(Regex("[0-9a-f]{40}"))) { offlineNlpError(OfflineNlpErrorCode.INVALID_MODEL) }
        require(spec.files.map { it.name }.toSet() == setOf("model.onnx", "tokenizer.json") && spec.files.size == 2) {
            offlineNlpError(OfflineNlpErrorCode.INVALID_MODEL)
        }
        for (file in spec.files) {
            require(file.expectedBytes > 0 && file.sha256.matches(Regex("[0-9a-f]{64}"))) { offlineNlpError(OfflineNlpErrorCode.INVALID_MODEL) }
            require(file.url.startsWith("https://huggingface.co/") && "/resolve/${spec.revision}/" in file.url) {
                offlineNlpError(OfflineNlpErrorCode.INVALID_SOURCE)
            }
        }
        return desktopZhihuDataFile("models/${spec.id}/${spec.revision}")
    }

    private suspend fun verifyLocalFile(file: File, spec: OfflineNlpModelFile): Boolean {
        if (!file.isFile || file.length() != spec.expectedBytes) return false
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().toHex() == spec.sha256
    }

    private suspend fun downloadFile(
        client: HttpClient,
        spec: OfflineNlpModelFile,
        destination: File,
        completedBytes: Long,
        totalBytes: Long,
        onProgress: (OfflineNlpModelState) -> Unit,
    ) {
        val temporary = Files.createTempFile(destination.parentFile.toPath(), ".${spec.name}-", ".part")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var downloadedBytes = 0L
            onProgress(OfflineNlpModelState.Downloading(spec.name, completedBytes, totalBytes))
            client.prepareGet(spec.url).execute { response ->
                val declaredLength = response.contentLength()
                check(declaredLength == null || declaredLength == spec.expectedBytes) { offlineNlpError(OfflineNlpErrorCode.DOWNLOAD_SIZE, spec.name) }
                val channel = response.bodyAsChannel()
                FileOutputStream(temporary.toFile()).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var lastReportedBytes = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = channel.readAvailable(buffer, 0, buffer.size)
                        if (count < 0) break
                        downloadedBytes += count
                        check(downloadedBytes <= spec.expectedBytes) { offlineNlpError(OfflineNlpErrorCode.DOWNLOAD_SIZE, spec.name) }
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        if (downloadedBytes - lastReportedBytes >= 1024 * 1024 || downloadedBytes == spec.expectedBytes) {
                            onProgress(OfflineNlpModelState.Downloading(spec.name, completedBytes + downloadedBytes, totalBytes))
                            lastReportedBytes = downloadedBytes
                        }
                    }
                    output.fd.sync()
                }
            }
            currentCoroutineContext().ensureActive()
            onProgress(OfflineNlpModelState.Verifying(spec.name))
            check(downloadedBytes == spec.expectedBytes) { offlineNlpError(OfflineNlpErrorCode.DOWNLOAD_SIZE, spec.name) }
            check(digest.digest().toHex() == spec.sha256) { offlineNlpError(OfflineNlpErrorCode.DOWNLOAD_HASH, spec.name) }
            try {
                Files.move(temporary, destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (error: ResponseException) {
            throw OfflineNlpException(OfflineNlpFailure(OfflineNlpErrorCode.DOWNLOAD_HTTP, spec.name, error.response.status.value), error)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}

private fun ByteArray.toHex(): String = joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

private fun jvmOfflineNlpUnsupportedReason(): OfflineNlpFailure? {
    val os = System.getProperty("os.name").lowercase(Locale.ROOT)
    val arch = System.getProperty("os.arch").lowercase(Locale.ROOT)
    val x64 = arch == "amd64" || arch == "x86_64"
    val arm64 = arch == "aarch64" || arch == "arm64"
    val osVersion = System.getProperty("os.version")
    val macOsVersion = osVersion.split('.', limit = 3)
    val macOsMajor = macOsVersion[0].toIntOrNull()
    val macOsMinor = if (macOsVersion.size > 1) macOsVersion[1].toIntOrNull() else 0
    val supportsMacOsRuntime = macOsMajor != null && macOsMinor != null && macOsMinor >= 0 &&
        (macOsMajor > 13 || (macOsMajor == 13 && macOsMinor >= 4))
    return when {
        os.contains("win") && !x64 -> OfflineNlpFailure(OfflineNlpErrorCode.WINDOWS_ARCH)
        os.contains("linux") && !x64 && !arm64 -> OfflineNlpFailure(OfflineNlpErrorCode.LINUX_ARCH)
        os.contains("mac") && !x64 && !arm64 -> OfflineNlpFailure(OfflineNlpErrorCode.MACOS_ARCH)
        os.contains("mac") && !supportsMacOsRuntime ->
            OfflineNlpFailure(OfflineNlpErrorCode.MACOS_VERSION, currentVersion = osVersion.take(64))
        !os.contains("win") && !os.contains("linux") && !os.contains("mac") -> OfflineNlpFailure(OfflineNlpErrorCode.JVM_OS)
        else -> null
    }
}
