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

package com.github.zly2006.zhihu.viewmodel

import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import com.github.zly2006.zhihu.i18n.DomainStrings
import com.github.zly2006.zhihu.i18n.WidgetsStrings
import com.github.zly2006.zhihu.util.ArticleExportBudgetExceeded
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageReader
import javax.imageio.ImageWriteParam
import javax.imageio.stream.MemoryCacheImageInputStream
import javax.imageio.stream.MemoryCacheImageOutputStream
import kotlin.math.ceil
import kotlin.math.sqrt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

private const val MAX_SOURCE_PIXELS = 64_000_000L
private const val MAX_SOURCE_DIMENSION = 65_535
private const val MAX_IMAGE_PIXELS = 4_000_000L
private const val MAX_IMAGE_BYTES = 32 * 1024 * 1024
private const val MAX_RENDER_WIDTH = 1875
private const val MAX_REENCODED_BYTES = 32L * 1024 * 1024

private data class DesktopExportImage(
    val nodes: List<Element>,
    val source: String,
    val width: Int,
    val height: Int,
    val flattenAnimation: Boolean,
)

/** 小截图不能限制 WebKit 原图解码；在进入 WebView 前逐张检查并限制累计解码像素。 */
internal suspend fun prepareDesktopExportImages(document: Document): String {
    val images = mutableListOf<DesktopExportImage>()
    var adjustedCount = 0
    var replacedCount = 0
    var originalBytes = 0L
    fun placeholder(nodes: List<Element>, reason: String) {
        nodes.forEach { image ->
            image.replaceWith(
                Element("span").addClass("export-image-placeholder").text(
                    "[$reason]" + image.attr("alt").takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty(),
                ),
            )
        }
        replacedCount += nodes.size
    }

    document.select("img").groupBy { it.attr("src") }.forEach { (source, nodes) ->
        currentCoroutineContext().ensureActive()
        try {
            val bytes = decodeDesktopExportImage(source)
            originalBytes += bytes.size
            if (originalBytes > 128L * 1024 * 1024) throw ArticleExportBudgetExceeded()
            withDesktopImageReader(bytes) { reader ->
                val width = reader.getWidth(0)
                val height = reader.getHeight(0)
                requireSafeSourceDimensions(width, height)
                val gif = reader.formatName.equals("gif", ignoreCase = true)
                // GIF 画布可以远大于首帧；不要把小首帧当作整张动画的尺寸证明。
                if (gif && bytes.size >= 10) {
                    requireSafeSourceDimensions(
                        (bytes[6].toInt() and 255) or ((bytes[7].toInt() and 255) shl 8),
                        (bytes[8].toInt() and 255) or ((bytes[9].toInt() and 255) shl 8),
                    )
                }
                images += DesktopExportImage(nodes, source, width, height, gif || pngIsAnimated(bytes))
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (error is ArticleExportBudgetExceeded) throw error
            placeholder(nodes, error.message ?: WidgetsStrings.imageLoadFailed)
        }
    }

    // 最多约 128 MiB 的 WebKit 像素，较小 JVM 堆取四分之一；整篇按比例调整而非截断长文。
    val pixelBudget = minOf(32_000_000L, (Runtime.getRuntime().maxMemory() / 16).coerceAtLeast(1_000_000L))
    fun sampleFor(image: DesktopExportImage): Int = maxOf(
        1,
        ceil(image.width.toDouble() / MAX_RENDER_WIDTH).toInt(),
        ceil(sqrt(image.width.toDouble() * image.height / MAX_IMAGE_PIXELS)).toInt(),
    )
    fun pixelsAt(image: DesktopExportImage, sample: Int): Long =
        ((image.width.toLong() + sample - 1) / sample) * ((image.height.toLong() + sample - 1) / sample)
    val desiredPixels = images.sumOf { pixelsAt(it, sampleFor(it)) * it.nodes.size }
    val documentScale = maxOf(1.0, sqrt(desiredPixels.toDouble() / pixelBudget))
    var remainingPixels = pixelBudget
    var reencodedBytes = 0L
    for (image in images) {
        currentCoroutineContext().ensureActive()
        try {
            var sample = ceil(sampleFor(image) * documentScale).toInt().coerceAtLeast(1)
            val availablePixels = remainingPixels / image.nodes.size
            check(availablePixels > 0) { DomainStrings.desktopExportImageTooLarge }
            while (pixelsAt(image, sample) > availablePixels) sample++
            val pixels = pixelsAt(image, sample)
            if (sample > 1 || image.flattenAnimation) {
                val bytes = decodeDesktopExportImage(image.source)
                val context = currentCoroutineContext()
                val encoded = withDesktopImageReader(bytes) { reader ->
                    val parameters = reader.defaultReadParam.apply { setSourceSubsampling(sample, sample, 0, 0) }
                    val bitmap = reader.read(0, parameters)
                    try {
                        context.ensureActive()
                        check(bitmap.width.toLong() * bitmap.height <= pixels) { DomainStrings.desktopExportImageTooLarge }
                        encodeDesktopExportImage(bitmap)
                    } finally {
                        bitmap.flush()
                    }
                }
                check(encoded.second.size.toLong() <= MAX_REENCODED_BYTES - reencodedBytes) { DomainStrings.desktopExportImageTooLarge }
                reencodedBytes += encoded.second.size
                val url = "data:${encoded.first};base64,${Base64.getEncoder().encodeToString(encoded.second)}"
                image.nodes.forEach { node ->
                    node.attr("src", url)
                    // 保留未指定 CSS 尺寸的原图布局，不让降采样改变正文中的显示宽度。
                    if (!node.hasAttr("width")) node.attr("width", image.width.toString())
                    if (!node.hasAttr("height")) node.attr("height", image.height.toString())
                }
                adjustedCount += image.nodes.size
            }
            remainingPixels -= pixels * image.nodes.size
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            placeholder(image.nodes, error.message ?: WidgetsStrings.imageLoadFailed)
        }
    }
    return if (adjustedCount > 0 || replacedCount > 0) {
        DomainStrings.desktopExportImagesAdjusted(adjustedCount, replacedCount)
    } else {
        ""
    }
}

private fun decodeDesktopExportImage(source: String): ByteArray {
    val separator = source.indexOf(',')
    check(separator > 0 && source.substring(0, separator).startsWith("data:image/") &&
        source.substring(0, separator).endsWith(";base64")
    ) { DomainStrings.desktopExportImageUnsupported }
    check(source.length.toLong() - separator - 1 <= (MAX_IMAGE_BYTES.toLong() + 2) / 3 * 4) {
        DomainStrings.desktopExportImageTooLarge
    }
    return Base64.getDecoder().decode(source.substring(separator + 1)).also {
        check(it.size <= MAX_IMAGE_BYTES) { DomainStrings.desktopExportImageTooLarge }
    }
}

private fun requireSafeSourceDimensions(width: Int, height: Int) {
    check(width in 1..MAX_SOURCE_DIMENSION && height in 1..MAX_SOURCE_DIMENSION && width.toLong() * height <= MAX_SOURCE_PIXELS) {
        DomainStrings.desktopExportImageTooLarge
    }
}

private inline fun <T> withDesktopImageReader(bytes: ByteArray, block: (ImageReader) -> T): T =
    MemoryCacheImageInputStream(ByteArrayInputStream(bytes)).use { input ->
        val readers = ImageIO.getImageReaders(input)
        check(readers.hasNext()) { DomainStrings.desktopExportImageUnsupported }
        val reader = readers.next()
        try {
            reader.setInput(input, true, true)
            block(reader)
        } finally {
            reader.dispose()
        }
    }

/** APNG 必须固化首帧，否则帧数可能绕过静态尺寸预算。 */
private fun pngIsAnimated(bytes: ByteArray): Boolean {
    if (bytes.size < 8 || bytes[0] != 137.toByte() || bytes[1] != 80.toByte() || bytes[2] != 78.toByte() || bytes[3] != 71.toByte()) return false
    var offset = 8
    while (offset <= bytes.size - 12) {
        var length = 0L
        repeat(4) { length = (length shl 8) or (bytes[offset + it].toLong() and 255) }
        if (length > bytes.size - offset - 12) return false
        if (bytes[offset + 4] == 97.toByte() && bytes[offset + 5] == 99.toByte() &&
            bytes[offset + 6] == 84.toByte() && bytes[offset + 7] == 76.toByte()
        ) return true
        offset += length.toInt() + 12
    }
    return false
}

private fun encodeDesktopExportImage(image: BufferedImage): Pair<String, ByteArray> {
    val transparent = image.colorModel.hasAlpha()
    val format = if (transparent) "png" else "jpeg"
    val writers = ImageIO.getImageWritersByFormatName(format)
    check(writers.hasNext()) { DomainStrings.imageEncodeFailed }
    val writer = writers.next()
    val output = ByteArrayOutputStream()
    try {
        MemoryCacheImageOutputStream(output).use { stream ->
            writer.output = stream
            val parameters = writer.defaultWriteParam
            if (!transparent && parameters.canWriteCompressed()) {
                parameters.compressionMode = ImageWriteParam.MODE_EXPLICIT
                parameters.compressionQuality = 0.90f
            }
            writer.write(null, IIOImage(image, null, null), parameters)
        }
    } finally {
        writer.dispose()
    }
    return "image/$format" to output.toByteArray()
}
