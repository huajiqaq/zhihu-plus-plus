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

package com.github.zly2006.zhihu.nlp

import com.github.zly2006.zhihu.i18n.OfflineNlpStrings
import com.github.zly2006.zhihu.util.DisplayFailure
import kotlinx.serialization.Serializable

/** 稳定错误码也用于 Worker 协议；后台状态不保存某种语言的文案。 */
@Serializable
enum class OfflineNlpErrorCode {
    OPERATION_FAILED, LOAD_FAILED, DELETE_FAILED, INFERENCE_FAILED, RANKING_FAILED, CLOSE_FAILED, RULE_FAILED,
    ANDROID_SEPARATE, IOS_LITE, WINDOWS_ARCH, LINUX_ARCH, MACOS_ARCH, MACOS_VERSION, JVM_OS,
    HTTPS_REQUIRED, WEB_RUNTIME_UNSUPPORTED, WEB_STORAGE_UNSUPPORTED, WEB_SYNC_STORAGE_UNSUPPORTED,
    INVALID_MODEL, INVALID_SOURCE, INVALID_TOKENIZER, INVALID_INPUT, INVALID_OUTPUT, ASSETS_MISMATCH,
    DISK_FULL, QUOTA_EXCEEDED, QUOTA_REQUIRED, STORAGE_DENIED, STORAGE_BUSY, STORAGE_FAILED,
    FILE_READ, FILE_WRITE, FILE_CLEANUP, CACHE_SIZE, CACHE_HASH, DOWNLOAD_HTTP, DOWNLOAD_SIZE, DOWNLOAD_HASH,
    CACHE_MISSING, RUNTIME_MISSING, RUNTIME_INTEGRITY, RUNTIME_LOAD, OUT_OF_MEMORY,
    SESSION_CLOSED, SESSION_BUSY, WORKER_FAILED, WORKER_MESSAGE, WORKER_TIMEOUT, CLOSE_TIMEOUT, CANCEL_TIMEOUT, CANCELLED,
}

/** 只携带有限的技术参数，不接收服务器正文、模型输入或任意本地化错误字符串。 */
@Serializable
data class OfflineNlpFailure(
    val code: OfflineNlpErrorCode,
    val fileName: String = "",
    val httpStatus: Int = 0,
    val requiredMiB: Long = 0,
    val currentVersion: String = "",
) {
    /** 在展示时读取当前语言，语言切换无需重新下载或重跑失败操作。 */
    val displayMessage: String
        get() = when (code) {
            OfflineNlpErrorCode.OPERATION_FAILED -> OfflineNlpStrings.operationFailed
            OfflineNlpErrorCode.LOAD_FAILED -> OfflineNlpStrings.loadFailed
            OfflineNlpErrorCode.DELETE_FAILED -> OfflineNlpStrings.deleteFailed
            OfflineNlpErrorCode.INFERENCE_FAILED -> OfflineNlpStrings.inferenceFailed
            OfflineNlpErrorCode.RANKING_FAILED -> OfflineNlpStrings.rankingFailed
            OfflineNlpErrorCode.CLOSE_FAILED -> OfflineNlpStrings.closeFailed
            OfflineNlpErrorCode.RULE_FAILED -> OfflineNlpStrings.ruleFailed
            OfflineNlpErrorCode.ANDROID_SEPARATE -> OfflineNlpStrings.androidSeparate
            OfflineNlpErrorCode.IOS_LITE -> OfflineNlpStrings.iosLite
            OfflineNlpErrorCode.WINDOWS_ARCH -> OfflineNlpStrings.windowsArch
            OfflineNlpErrorCode.LINUX_ARCH -> OfflineNlpStrings.linuxArch
            OfflineNlpErrorCode.MACOS_ARCH -> OfflineNlpStrings.macosArch
            OfflineNlpErrorCode.MACOS_VERSION -> OfflineNlpStrings.macosVersion(currentVersion)
            OfflineNlpErrorCode.JVM_OS -> OfflineNlpStrings.jvmOs
            OfflineNlpErrorCode.HTTPS_REQUIRED -> OfflineNlpStrings.httpsRequired
            OfflineNlpErrorCode.WEB_RUNTIME_UNSUPPORTED -> OfflineNlpStrings.webRuntimeUnsupported
            OfflineNlpErrorCode.WEB_STORAGE_UNSUPPORTED -> OfflineNlpStrings.webStorageUnsupported
            OfflineNlpErrorCode.WEB_SYNC_STORAGE_UNSUPPORTED -> OfflineNlpStrings.webSyncStorageUnsupported
            OfflineNlpErrorCode.INVALID_MODEL -> OfflineNlpStrings.invalidModel
            OfflineNlpErrorCode.INVALID_SOURCE -> OfflineNlpStrings.invalidSource
            OfflineNlpErrorCode.INVALID_TOKENIZER -> OfflineNlpStrings.invalidTokenizer
            OfflineNlpErrorCode.INVALID_INPUT -> OfflineNlpStrings.invalidInput
            OfflineNlpErrorCode.INVALID_OUTPUT -> OfflineNlpStrings.invalidOutput
            OfflineNlpErrorCode.ASSETS_MISMATCH -> OfflineNlpStrings.assetsMismatch
            OfflineNlpErrorCode.DISK_FULL -> OfflineNlpStrings.diskFull(fileName)
            OfflineNlpErrorCode.QUOTA_EXCEEDED -> OfflineNlpStrings.quotaExceeded
            OfflineNlpErrorCode.QUOTA_REQUIRED -> OfflineNlpStrings.quotaRequired(requiredMiB)
            OfflineNlpErrorCode.STORAGE_DENIED -> OfflineNlpStrings.storageDenied
            OfflineNlpErrorCode.STORAGE_BUSY -> OfflineNlpStrings.storageBusy
            OfflineNlpErrorCode.STORAGE_FAILED -> OfflineNlpStrings.storageFailed
            OfflineNlpErrorCode.FILE_READ -> OfflineNlpStrings.fileRead(fileName)
            OfflineNlpErrorCode.FILE_WRITE -> OfflineNlpStrings.fileWrite(fileName)
            OfflineNlpErrorCode.FILE_CLEANUP -> OfflineNlpStrings.fileCleanup
            OfflineNlpErrorCode.CACHE_SIZE -> OfflineNlpStrings.cacheSize(fileName)
            OfflineNlpErrorCode.CACHE_HASH -> OfflineNlpStrings.cacheHash(fileName)
            OfflineNlpErrorCode.DOWNLOAD_HTTP -> OfflineNlpStrings.downloadHttp(fileName, httpStatus)
            OfflineNlpErrorCode.DOWNLOAD_SIZE -> OfflineNlpStrings.downloadSize(fileName)
            OfflineNlpErrorCode.DOWNLOAD_HASH -> OfflineNlpStrings.downloadHash(fileName)
            OfflineNlpErrorCode.CACHE_MISSING -> OfflineNlpStrings.cacheMissing
            OfflineNlpErrorCode.RUNTIME_MISSING -> OfflineNlpStrings.runtimeMissing
            OfflineNlpErrorCode.RUNTIME_INTEGRITY -> OfflineNlpStrings.runtimeIntegrity(fileName)
            OfflineNlpErrorCode.RUNTIME_LOAD -> OfflineNlpStrings.runtimeLoad
            OfflineNlpErrorCode.OUT_OF_MEMORY -> OfflineNlpStrings.outOfMemory
            OfflineNlpErrorCode.SESSION_CLOSED -> OfflineNlpStrings.sessionClosed
            OfflineNlpErrorCode.SESSION_BUSY -> OfflineNlpStrings.sessionBusy
            OfflineNlpErrorCode.WORKER_FAILED -> OfflineNlpStrings.workerFailed
            OfflineNlpErrorCode.WORKER_MESSAGE -> OfflineNlpStrings.workerMessage
            OfflineNlpErrorCode.WORKER_TIMEOUT -> OfflineNlpStrings.workerTimeout
            OfflineNlpErrorCode.CLOSE_TIMEOUT -> OfflineNlpStrings.closeTimeout
            OfflineNlpErrorCode.CANCEL_TIMEOUT -> OfflineNlpStrings.cancelTimeout
            OfflineNlpErrorCode.CANCELLED -> OfflineNlpStrings.cancelled
        }
}

internal class OfflineNlpException(val failure: OfflineNlpFailure, override val cause: Throwable? = null) :
    DisplayFailure({ failure.displayMessage })

/** 未识别的库错误仅保留为诊断 cause；UI 显示当前操作的已翻译回退文案。 */
fun Throwable.toOfflineNlpFailure(fallback: OfflineNlpErrorCode = OfflineNlpErrorCode.OPERATION_FAILED): OfflineNlpFailure =
    (this as? OfflineNlpException)?.failure ?: OfflineNlpFailure(fallback)

internal fun offlineNlpError(
    code: OfflineNlpErrorCode,
    fileName: String = "",
    httpStatus: Int = 0,
    requiredMiB: Long = 0,
    currentVersion: String = "",
): Nothing = throw OfflineNlpException(OfflineNlpFailure(code, fileName, httpStatus, requiredMiB, currentVersion))
