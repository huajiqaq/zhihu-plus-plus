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

package com.github.zly2006.zhihu.desktop

import com.github.zly2006.zhihu.account.ZhihuAccountStore
import com.github.zly2006.zhihu.i18n.DomainStrings
import com.github.zly2006.zhihu.util.DisplayFailure
import com.github.zly2006.zhihu.util.downloadArticleExportImage
import com.github.zly2006.zhihu.viewmodel.ensureExportAccountActive
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

suspend fun ZhihuAccountStore.saveImageToDownloads(
    url: String,
    filePrefix: String,
): File = withContext(Dispatchers.IO) {
    downloadDesktopImage(url, desktopZhihuDownloadsDir(), filePrefix)
}

internal suspend fun ZhihuAccountStore.downloadDesktopImage(
    url: String,
    directory: File,
    filePrefix: String,
): File = withContext(Dispatchers.IO) {
    val image = HttpClient(client.httpClient().engine) {
        followRedirects = false
        install(HttpTimeout) { requestTimeoutMillis = 15_000L }
    }.use { imageClient -> downloadArticleExportImage(imageClient, url) }
    val bytes = image.bytes
    val extension = when (image.mimeType) {
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "image/gif" -> "gif"
        "image/webp" -> "webp"
        "image/bmp" -> "bmp"
        "image/avif" -> "avif"
        "image/heic" -> "heic"
        "image/heif" -> "heif"
        else -> throw DisplayFailure { DomainStrings.imageTypeUnsupported(image.mimeType) }
    }
    // 不把登录页、错误页或任意脚本保存成可分享的图片文件。
    require(desktopImageHasSignature(bytes, extension)) { DomainStrings.imageEncodeFailed }
    Files.createDirectories(directory.toPath())
    val temporary = Files.createTempFile(directory.toPath(), ".image-", ".partial")
    try {
        Files.write(temporary, bytes)
        currentCoroutineContext().ensureActive()
        val name = "${filePrefix}_${temporary.fileName.toString().removePrefix(".image-").removeSuffix(".partial")}.$extension"
        ensureExportAccountActive()
        Files.move(temporary, directory.toPath().resolve(name)).toFile()
    } finally {
        Files.deleteIfExists(temporary)
    }
}

private fun desktopImageHasSignature(bytes: ByteArray, extension: String): Boolean {
    fun starts(vararg signature: Int) = bytes.size >= signature.size && signature.indices.all { (bytes[it].toInt() and 255) == signature[it] }
    return when (extension) {
        "jpg" -> starts(0xff, 0xd8, 0xff)
        "png" -> starts(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
        "gif" -> starts(0x47, 0x49, 0x46, 0x38) && bytes.size >= 6
        "bmp" -> starts(0x42, 0x4d)
        "webp" -> starts(0x52, 0x49, 0x46, 0x46) && bytes.size >= 12 && bytes.copyOfRange(8, 12).toString(Charsets.US_ASCII) == "WEBP"
        "avif", "heic", "heif" -> bytes.size >= 12 && bytes.copyOfRange(4, 8).toString(Charsets.US_ASCII) == "ftyp" &&
            bytes.copyOfRange(8, 12).toString(Charsets.US_ASCII) in setOf("avif", "avis", "heic", "heix", "hevc", "hevx", "mif1", "msf1")
        else -> false
    }
}

fun desktopZhihuDataDir(): File =
    File(System.getProperty("user.home"), ".zhihu-plus")

fun desktopZhihuDataFile(relativePath: String): File =
    File(desktopZhihuDataDir(), relativePath)

fun desktopZhihuDownloadsDir(errorMessage: String = DomainStrings.cannotCreateDownloadDirectory): File =
    File(System.getProperty("user.home"), "Downloads/Zhihu++").also { directory ->
        if (!directory.exists() && !directory.mkdirs()) {
            throw IllegalStateException(errorMessage)
        }
    }

fun desktopZhihuLegacyAccountFile(): Path =
    Path.of(System.getProperty("user.home"), ".zhihu-plus-plus", "account.json")

fun openDesktopExternalUrl(url: String): Boolean = runCatching {
    if (!Desktop.isDesktopSupported()) {
        return@runCatching false
    }
    Desktop.getDesktop().browse(URI(url))
    true
}.getOrDefault(false)

fun copyDesktopPlainText(text: String) =
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
