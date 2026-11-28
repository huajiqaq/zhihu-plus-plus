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

@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.github.zly2006.zhihu.platform

import androidx.compose.runtime.staticCompositionLocalOf
import com.github.zly2006.zhihu.i18n.DomainStrings
import com.github.zly2006.zhihu.util.AppLog
import com.github.zly2006.zhihu.util.downloadArticleExportImage
import com.github.zly2006.zhihu.viewmodel.ArticleExportPermission
import com.github.zly2006.zhihu.viewmodel.ArticleExportResult
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.cinterop.LongVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.value
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import platform.CoreGraphics.CGImageRef
import platform.CoreGraphics.CGImageRelease
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryGetValue
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFNumberGetValue
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFNumberLongLongType
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.ImageIO.CGImageSourceCopyPropertiesAtIndex
import platform.ImageIO.CGImageSourceCreateThumbnailAtIndex
import platform.ImageIO.CGImageSourceCreateWithURL
import platform.ImageIO.CGImageSourceGetCount
import platform.ImageIO.kCGImagePropertyPixelHeight
import platform.ImageIO.kCGImagePropertyPixelWidth
import platform.ImageIO.kCGImageSourceCreateThumbnailFromImageAlways
import platform.ImageIO.kCGImageSourceThumbnailMaxPixelSize
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.WebKit.WKWebView

/** 每个窗口拥有自己的交付宿主；文件必须保留到系统完成读取。 */
interface NativeFileDelivery {
    suspend fun ensureImagePermission(): ArticleExportPermission
    suspend fun saveFile(filePath: String, image: Boolean): ArticleExportResult
    suspend fun shareFile(filePath: String): ArticleExportResult
    suspend fun shareText(text: String, title: String): ArticleExportResult
    fun attachExportView(webView: WKWebView): () -> Unit
}

val LocalNativeFileDelivery = staticCompositionLocalOf<NativeFileDelivery?> { null }

internal fun requireNativeFileDelivery(delivery: NativeFileDelivery?): NativeFileDelivery =
    checkNotNull(delivery) { DomainStrings.exportHostUnavailable }

internal fun readNativeExportAsset(fileName: String): String {
    require(fileName == "article_export_template.html" || fileName == "footnotes.js")
    val path = checkNotNull(nativeBundledResourcePath("export/$fileName")) { DomainStrings.missingExportAsset(fileName) }
    return SystemFileSystem.source(Path(path)).buffered().use { it.readString() }
}

/** 任务独占目录，取消只清理自己的文件；不扫描账号或其他任务的缓存。 */
suspend fun withNativeExportDirectory(block: suspend (String) -> ArticleExportResult): ArticleExportResult {
    val directory = "${NSTemporaryDirectory().trimEnd('/')}/zhihu-export-${NSUUID().UUIDString}"
    check(NSFileManager.defaultManager.createDirectoryAtPath(directory, true, null, null)) {
        DomainStrings.exportCacheDirectoryFailed
    }
    var primary: Throwable? = null
    var result: ArticleExportResult = ArticleExportResult.Cancelled
    try {
        result = block(directory)
    } catch (error: Throwable) {
        primary = error
        throw error
    } finally {
        withContext(NonCancellable + Dispatchers.Default) {
            val cleanup = runCatching {
                check(NSFileManager.defaultManager.removeItemAtPath(directory, null)) { DomainStrings.fileWriteFailed(directory) }
            }.exceptionOrNull()
            if (cleanup != null) {
                if (primary != null) {
                    primary.addSuppressed(cleanup)
                } else {
                    val delivered = result as? ArticleExportResult.Completed
                    if (delivered == null) throw cleanup
                    AppLog.e("ArticleExport", "File delivered but temporary cleanup failed", cleanup)
                    result = ArticleExportResult.Completed("${delivered.completionMessage}\n${DomainStrings.exportCleanupFailed}")
                }
            }
        }
    }
    return result
}

internal fun writeNativeExportText(filePath: String, text: String) {
    SystemFileSystem.sink(Path(filePath)).buffered().use { it.writeString(text) }
}

/** 下载到真实附件，限制大小；借用引擎而不把账号认证头发送给图片站点。 */
internal suspend fun deliverNativeImage(
    client: HttpClient,
    imageUrl: String,
    delivery: NativeFileDelivery,
    share: Boolean,
): ArticleExportResult {
    if (!share && delivery.ensureImagePermission() != ArticleExportPermission.Granted) {
        error(DomainStrings.photosPermissionDenied)
    }
    return withNativeExportDirectory { directory ->
        val bytesAndType = withContext(Dispatchers.Default) {
            HttpClient(client.engine) {
                followRedirects = false
                install(HttpTimeout) { requestTimeoutMillis = 15_000L }
            }.use { imageClient ->
                downloadArticleExportImage(imageClient, imageUrl)
            }
        }
        val (bytes, mime) = bytesAndType
        val extension = when (mime) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/gif" -> "gif"
            "image/webp" -> "webp"
            "image/heic" -> "heic"
            "image/heif" -> "heif"
            "image/avif" -> "avif"
            "image/bmp" -> "bmp"
            else -> error(DomainStrings.exportRenderFailed)
        }
        val path = "$directory/image.$extension"
        withContext(Dispatchers.Default) {
            SystemFileSystem.sink(Path(path)).buffered().use { it.write(bytes) }
            validateNativeImageFile(path)
        }
        if (share) delivery.shareFile(path) else delivery.saveFile(path, image = true)
    }
}

/** ImageIO 实际解码小缩略图，避免 MIME 正确但数据损坏，且不解码完整长图。 */
internal fun validateNativeImageFile(filePath: String): Long {
    var pixelCount = 0L
    val url = checkNotNull(CFBridgingRetain(NSURL.fileURLWithPath(filePath)))
    try {
        val source = checkNotNull(CGImageSourceCreateWithURL(url.reinterpret(), null)) { DomainStrings.exportRenderFailed }
        try {
            check(CGImageSourceGetCount(source) > 0uL) { DomainStrings.exportRenderFailed }
            val properties = checkNotNull(CGImageSourceCopyPropertiesAtIndex(source, 0u, null)) { DomainStrings.exportRenderFailed }
            try {
                memScoped {
                    val width = alloc<LongVar>()
                    val height = alloc<LongVar>()
                    check(CFNumberGetValue(CFDictionaryGetValue(properties, kCGImagePropertyPixelWidth)?.reinterpret(), kCFNumberLongLongType, width.ptr) &&
                        CFNumberGetValue(CFDictionaryGetValue(properties, kCGImagePropertyPixelHeight)?.reinterpret(), kCFNumberLongLongType, height.ptr)
                    ) { DomainStrings.exportRenderFailed }
                    if (width.value !in 1..100_000 || height.value !in 1..100_000 || width.value > 64_000_000L / height.value) {
                        error(DomainStrings.exportTooLarge)
                    }
                    pixelCount = width.value * height.value
                }
            } finally {
                CFRelease(properties)
            }
            val options = checkNotNull(CFDictionaryCreateMutable(null, 2, null, null))
            val maxSize = checkNotNull(CFBridgingRetain(NSNumber(int = 32)))
            try {
                CFDictionarySetValue(options, kCGImageSourceCreateThumbnailFromImageAlways, kCFBooleanTrue)
                CFDictionarySetValue(options, kCGImageSourceThumbnailMaxPixelSize, maxSize)
                val thumbnail = checkNotNull(CGImageSourceCreateThumbnailAtIndex(source, 0u, options)) { DomainStrings.exportRenderFailed }
                CGImageRelease(thumbnail)
            } finally {
                CFRelease(maxSize)
                CFRelease(options)
            }
        } finally {
            CFRelease(source)
        }
    } finally {
        CFRelease(url)
    }
    return pixelCount
}

internal class NativeExportSnapshot(val image: CGImageRef) {
    fun close() = CGImageRelease(image)
}

internal expect suspend fun snapshotNativeExportView(webView: WKWebView, width: Int, height: Int, outputWidth: Int): NativeExportSnapshot
