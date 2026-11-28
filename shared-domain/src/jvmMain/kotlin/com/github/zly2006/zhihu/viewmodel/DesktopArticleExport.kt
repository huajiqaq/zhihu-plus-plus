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

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.DataNode
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.desktop.desktopZhihuDownloadsDir
import com.github.zly2006.zhihu.i18n.ContentStrings
import com.github.zly2006.zhihu.i18n.DomainStrings
import com.github.zly2006.zhihu.i18n.WidgetsStrings
import com.github.zly2006.zhihu.util.buildArticleExportFileName
import com.github.zly2006.zhihu.util.buildArticleExportHtml
import com.github.zly2006.zhihu.util.inlineArticleExportImagesForOfflineHtml
import java.awt.image.BufferedImage
import java.io.DataOutputStream
import java.io.File
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import javafx.animation.AnimationTimer
import javafx.application.Platform
import javafx.concurrent.Worker
import javafx.embed.swing.JFXPanel
import javafx.embed.swing.SwingFXUtils
import javafx.geometry.Insets
import javafx.geometry.Rectangle2D
import javafx.scene.Group
import javafx.scene.Scene
import javafx.scene.SnapshotParameters
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.image.WritableImage
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.paint.Color
import javafx.scene.transform.Scale
import javafx.scene.web.WebView
import javafx.stage.Stage
import javafx.util.Callback
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json

private const val EXPORT_WIDTH_CSS = 900.0
private const val EXPORT_DPI = 200.0
private const val EXPORT_SCALE = EXPORT_DPI / 96.0
private const val EXPORT_STRIP_HEIGHT = 1024

private class DesktopExportDismissed : Exception()

private data class DesktopExportHtml(val html: String, val imageMessage: String)

internal suspend fun ArticleExportContentEnvironment.exportDesktopHtml(
    content: DataHolder.Content,
    includeAppAttribution: Boolean,
): ArticleExportResult = withContext(Dispatchers.IO) {
    val html = desktopOfflineExportHtml(content, includeAppAttribution, "", placeholders = false)
    val file = writeDesktopExport(buildArticleExportFileName(content, "html")) { temporary ->
        temporary.writeText(html.html, Charsets.UTF_8)
    }
    ArticleExportResult.Completed(DomainStrings.htmlSavedTo(file.absolutePath))
}

internal suspend fun ArticleExportContentEnvironment.exportDesktopImage(
    content: DataHolder.Content,
    includeAppAttribution: Boolean,
    extraSectionsHtml: String,
    includeComments: Boolean,
): ArticleExportResult {
    var stage: Stage? = null
    var view: WebView? = null
    val dismissed = AtomicBoolean(false)
    var primaryError: Throwable? = null
    var imageMessage = ""
    fun ensureOpen() {
        if (dismissed.get()) throw DesktopExportDismissed()
    }
    try {
        val height = withTimeoutOrNull(if (includeComments) 18_000L else 15_000L) {
            val html = withContext(Dispatchers.IO) {
                desktopOfflineExportHtml(content, includeAppAttribution, extraSectionsHtml, placeholders = true)
            }
            imageMessage = html.imageMessage
            withContext(Dispatchers.Main) {
                JFXPanel() // 正式 Swing 入口负责一次性启动工具包，不调用内部 PlatformImpl。
                Platform.setImplicitExit(false)
            }
            onExportFxThread {
                val exportView = WebView()
                view = exportView
                exportView.apply {
                    isContextMenuEnabled = false
                    minWidth = EXPORT_WIDTH_CSS
                    maxWidth = EXPORT_WIDTH_CSS
                    resize(EXPORT_WIDTH_CSS, EXPORT_STRIP_HEIGHT / EXPORT_SCALE)
                    engine.createPopupHandler = Callback { null }
                    engine.locationProperty().addListener { _, _, location ->
                        if (!location.isNullOrBlank() && location != "about:blank") engine.loadWorker.cancel()
                    }
                    isMouseTransparent = true
                    isFocusTraversable = false
                }
                val preview = Stage()
                stage = preview
                val cancel = Button(ContentStrings.cancel).apply { setOnAction { preview.close() } }
                val controls = HBox(16.0, Label(ContentStrings.exporting), cancel).apply { padding = Insets(12.0) }
                val root = BorderPane().apply {
                    center = Group(exportView).apply { isAutoSizeChildren = false }
                    bottom = controls
                }
                preview.title = ContentStrings.exportArticle
                preview.scene = Scene(root)
                preview.isResizable = false
                preview.setOnHidden { dismissed.set(true) }
                // WebView 只有在真实可见的 Window 中才更新绘制帧；隐藏窗口会导出空白。
                preview.show()
                // loadContent 使用不透明 origin；不复用登录 WebView、CookieHandler 或 JavaScript bridge。
                exportView.engine.loadContent(html.html)
            }
            awaitDesktopExportLayout(checkNotNull(view), ::ensureOpen)
        } ?: error(DomainStrings.renderTimedOut)
        val pixelWidth = ceil(EXPORT_WIDTH_CSS * EXPORT_SCALE).toInt()
        val scaledHeight = ceil(height * EXPORT_SCALE)
        check(scaledHeight.isFinite() && scaledHeight in 1.0..Int.MAX_VALUE.toDouble()) { DomainStrings.renderEmpty }
        val pixelHeight = scaledHeight.toInt()
        val exportView = checkNotNull(view)
        val file = withContext(Dispatchers.IO) {
            writeDesktopExport(buildArticleExportFileName(content, "png"), beforePublish = ::ensureOpen) { temporary ->
                temporary.outputStream().buffered().use { output ->
                    DesktopPngWriter(output, pixelWidth, pixelHeight).use { png ->
                        var top = 0
                        while (top < pixelHeight) {
                            currentCoroutineContext().ensureActive()
                            ensureOpen()
                            val stripHeight = minOf(EXPORT_STRIP_HEIGHT, pixelHeight - top)
                            val strip = captureDesktopExportStrip(exportView, top, pixelWidth, stripHeight, ::ensureOpen)
                            try {
                                png.writeStrip(strip, ::ensureOpen)
                            } finally {
                                strip.flush()
                            }
                            top += stripHeight
                        }
                        ensureOpen()
                        png.finish()
                    }
                }
            }
        }
        return ArticleExportResult.Completed(
            DomainStrings.imageSavedTo(file.absolutePath) + imageMessage.takeIf(String::isNotBlank)?.let { "\n$it" }.orEmpty(),
        )
    } catch (error: DesktopExportDismissed) {
        primaryError = error
        return ArticleExportResult.Cancelled
    } catch (error: Throwable) {
        primaryError = error
        throw error
    } finally {
        withContext(NonCancellable) {
            if (view != null || stage != null) {
                val cleanupError = runCatching {
                    onExportFxThread {
                        try {
                            view?.engine?.loadWorker?.cancel()
                            view?.engine?.load(null)
                        } finally {
                            stage?.close()
                            stage?.scene = null
                            view = null
                            stage = null
                        }
                    }
                }.exceptionOrNull()
                if (cleanupError != null) {
                    val original = primaryError
                    if (original == null) throw cleanupError
                    if (original !== cleanupError) original.addSuppressed(cleanupError)
                }
            }
        }
    }
}

private suspend fun ArticleExportContentEnvironment.desktopOfflineExportHtml(
    content: DataHolder.Content,
    includeAppAttribution: Boolean,
    extraSectionsHtml: String,
    placeholders: Boolean,
): DesktopExportHtml {
    val html = inlineArticleExportImagesForOfflineHtml(
        html = buildArticleExportHtml(::loadExportAssetText, content, includeAppAttribution, extraSectionsHtml),
        httpClient = httpClient(),
        replaceFailedImagesWithPlaceholder = placeholders,
        maxTotalImageBytes = 128L * 1024 * 1024,
    )
    val document = Ksoup.parse(html)
    document.head().prependElement("meta")
        .attr("http-equiv", "Content-Security-Policy")
        .attr("content", "default-src 'none'; img-src data:; style-src 'unsafe-inline'; font-src data:; script-src 'unsafe-inline'; base-uri 'none'; form-action 'none'")
    document.body().appendElement("script").appendChild(DataNode(loadExportAssetText("footnotes.js")))
    val imageMessage = if (placeholders) {
        document.head().appendElement("style").appendChild(DataNode(
            """
            .content, .comment-content { overflow-wrap: anywhere; word-break: break-word; }
            .content table, .comment-content table { max-width: 100% !important; table-layout: fixed; }
            .content td, .content th, .comment-content td, .comment-content th { overflow-wrap: anywhere; white-space: normal !important; }
            """.trimIndent(),
        ))
        prepareDesktopExportImages(document)
    } else {
        ""
    }
    return DesktopExportHtml(document.outerHtml(), imageMessage)
}

/** 先提交到唯一临时文件，编码完成后才发布；取消或磁盘失败不留下伪完整文件。 */
internal suspend fun writeDesktopExport(
    displayName: String,
    beforePublish: () -> Unit = {},
    write: suspend (File) -> Unit,
): File {
    val directory = desktopZhihuDownloadsDir().toPath()
    val temporary = Files.createTempFile(directory, ".export-", ".partial")
    try {
        write(temporary.toFile())
        currentCoroutineContext().ensureActive()
        val destination = directory.resolve(displayName)
        // 不覆盖同一秒的另一次导出或用户已有文件。
        val target = if (Files.exists(destination)) {
            directory.resolve("${displayName.substringBeforeLast('.')}_${temporary.fileName.toString().removePrefix(".export-").removeSuffix(".partial")}.${displayName.substringAfterLast('.')}")
        } else {
            destination
        }
        beforePublish()
        ensureExportAccountActive()
        Files.move(temporary, target)
        return target.toFile()
    } finally {
        Files.deleteIfExists(temporary)
    }
}

private suspend fun awaitDesktopExportLayout(view: WebView, ensureOpen: () -> Unit): Double {
    while (true) {
        ensureOpen()
        when (onExportFxThread { view.engine.loadWorker.state }) {
            Worker.State.SUCCEEDED -> break
            Worker.State.FAILED, Worker.State.CANCELLED -> error(DomainStrings.renderLoadError)
            else -> delay(40)
        }
    }
    onExportFxThread {
        val placeholder = Json.encodeToString("[${WidgetsStrings.imageLoadFailed}]")
        view.engine.executeScript(
            """
            (function() {
                document.documentElement.style.overflow = 'hidden';
                window.__exportFontsReady = !document.fonts;
                if (document.fonts) document.fonts.ready.then(function() { window.__exportFontsReady = true; });
                Array.prototype.forEach.call(document.images, function(image) {
                    function failed() {
                        var text = document.createElement('span');
                        text.textContent = $placeholder + (image.alt ? ' ' + image.alt : '');
                        image.replaceWith(text);
                    }
                    if (image.complete && !image.naturalWidth) failed();
                    else image.addEventListener('error', failed, {once:true});
                });
            })();
            """.trimIndent(),
        )
    }
    var previous = -1.0
    var stable = 0
    while (true) {
        ensureOpen()
        val height = onExportFxThread {
            view.engine.executeScript(
                """
                (function() {
                    if (!window.__exportFontsReady || !Array.prototype.every.call(document.images, function(i) { return i.complete && i.naturalWidth > 0; })) return 0;
                    return Math.max(document.body.scrollHeight, document.body.offsetHeight);
                })()
                """.trimIndent(),
            ) as Number
        }.toDouble()
        stable = if (height > 1 && height == previous) stable + 1 else 0
        if (stable >= 3) {
            val completeWidth = onExportFxThread {
                view.engine.executeScript(
                    """
                    (function() {
                        var width = document.documentElement.clientWidth;
                        if (document.body.scrollWidth > width + 1 || document.documentElement.scrollWidth > width + 1) return false;
                        return Array.prototype.every.call(document.body.querySelectorAll('*'), function(element) {
                            var box = element.getBoundingClientRect();
                            if (!box.width || !box.height || getComputedStyle(element).visibility === 'hidden') return true;
                            return box.left >= -1 && box.right <= width + 1 && (!element.clientWidth || element.scrollWidth <= element.clientWidth + 1);
                        });
                    })()
                    """.trimIndent(),
                ) as Boolean
            }
            check(completeWidth) { DomainStrings.exportRenderFailed }
            return height
        }
        previous = height
        delay(50)
    }
}

private suspend fun captureDesktopExportStrip(
    view: WebView,
    top: Int,
    width: Int,
    height: Int,
    ensureOpen: () -> Unit,
): BufferedImage {
    onExportFxThread {
        ensureOpen()
        view.engine.executeScript("document.body.style.transform = 'translateY(-${top / EXPORT_SCALE}px)'")
        view.applyCss()
        view.layout()
    }
    awaitDesktopExportPaint(view, ensureOpen)
    return suspendCancellableCoroutine { continuation ->
        Platform.runLater {
            if (!continuation.isActive) return@runLater
            try {
                ensureOpen()
                val parameters = SnapshotParameters().apply {
                    fill = Color.WHITE
                    transform = Scale(EXPORT_SCALE, EXPORT_SCALE)
                    viewport = Rectangle2D(0.0, 0.0, width.toDouble(), height.toDouble())
                }
                view.snapshot({ result ->
                    if (continuation.isActive) {
                        try {
                            ensureOpen()
                            val image = SwingFXUtils.fromFXImage(result.image, null)
                            continuation.resume(image, onCancellation = { _, value, _ -> value.flush() })
                        } catch (error: Throwable) {
                            continuation.resumeWithException(error)
                        }
                    }
                    null
                }, parameters, WritableImage(width, height))
            } catch (error: Throwable) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }
        }
    }
}

/** DOM 已就绪后再等待真实绘制脉冲，最小化期间不能把丢弃的 WebKit 帧当成内容。 */
private suspend fun awaitDesktopExportPaint(view: WebView, ensureOpen: () -> Unit) = suspendCancellableCoroutine<Unit> { continuation ->
    Platform.runLater {
        if (!continuation.isActive) return@runLater
        val timer = object : AnimationTimer() {
            private var visiblePulses = 0
            override fun handle(now: Long) {
                try {
                    ensureOpen()
                    val window = view.scene?.window as? Stage
                    visiblePulses = if (window?.isShowing == true && !window.isIconified) visiblePulses + 1 else 0
                    if (visiblePulses >= 3) {
                        stop()
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                } catch (error: Throwable) {
                    stop()
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            }
        }
        continuation.invokeOnCancellation { Platform.runLater { timer.stop() } }
        timer.start()
    }
}

private suspend fun <T> onExportFxThread(block: () -> T): T = suspendCancellableCoroutine { continuation ->
    Platform.runLater {
        if (continuation.isActive) {
            try {
                continuation.resume(block())
            } catch (error: Throwable) {
                continuation.resumeWithException(error)
            }
        }
    }
}

/** PNG 扫描行连续编码为一个文件；内存只保留一个 WebView 分片和 64 KiB 压缩块。 */
private class DesktopPngWriter(output: OutputStream, private val width: Int, private val height: Int) : AutoCloseable {
    private val stream = DataOutputStream(output)
    private val deflater = Deflater()
    private val chunkBuffer = ByteArray(64 * 1024)
    private var chunkSize = 0
    private var rows = 0
    private val compressed = DeflaterOutputStream(object : OutputStream() {
        override fun write(value: Int) = write(byteArrayOf(value.toByte()), 0, 1)

        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            var position = offset
            var remaining = length
            while (remaining > 0) {
                val count = minOf(remaining, chunkBuffer.size - chunkSize)
                bytes.copyInto(chunkBuffer, chunkSize, position, position + count)
                chunkSize += count
                position += count
                remaining -= count
                if (chunkSize == chunkBuffer.size) flushChunk()
            }
        }
    }, deflater, 64 * 1024)

    init {
        try {
            require(width > 0 && height > 0)
            stream.write(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))
            val header = ByteBuffer.allocate(13).putInt(width).putInt(height)
                .put(8.toByte()).put(2.toByte()).put(0.toByte()).put(0.toByte()).put(0.toByte()).array()
            chunk("IHDR", header, header.size)
            val pixelsPerMetre = (EXPORT_DPI / 0.0254).roundToInt()
            val density = ByteBuffer.allocate(9).putInt(pixelsPerMetre).putInt(pixelsPerMetre).put(1.toByte()).array()
            chunk("pHYs", density, density.size)
        } catch (error: Throwable) {
            deflater.end()
            throw error
        }
    }

    suspend fun writeStrip(image: BufferedImage, ensureOpen: () -> Unit) {
        require(image.width == width && image.height <= height - rows)
        val pixels = IntArray(width)
        val row = ByteArray(width * 3 + 1)
        row[0] = 1 // Sub 过滤保持文字区域的压缩效率，无需缓存上一条扫描行。
        repeat(image.height) { y ->
            currentCoroutineContext().ensureActive()
            ensureOpen()
            image.getRGB(0, y, width, 1, pixels, 0, width)
            var previous = 0
            for (x in 0 until width) {
                val pixel = pixels[x]
                row[1 + x * 3] = ((pixel ushr 16 and 255) - (previous ushr 16 and 255)).toByte()
                row[2 + x * 3] = ((pixel ushr 8 and 255) - (previous ushr 8 and 255)).toByte()
                row[3 + x * 3] = ((pixel and 255) - (previous and 255)).toByte()
                previous = pixel
            }
            compressed.write(row)
            rows++
        }
    }

    fun finish() {
        check(rows == height)
        compressed.finish()
        flushChunk()
        chunk("IEND", ByteArray(0), 0)
        stream.flush()
    }

    private fun flushChunk() {
        if (chunkSize > 0) {
            chunk("IDAT", chunkBuffer, chunkSize)
            chunkSize = 0
        }
    }

    private fun chunk(type: String, bytes: ByteArray, length: Int) {
        val name = type.toByteArray(Charsets.US_ASCII)
        val crc = CRC32().apply {
            update(name)
            update(bytes, 0, length)
        }
        stream.writeInt(length)
        stream.write(name)
        stream.write(bytes, 0, length)
        stream.writeInt(crc.value.toInt())
    }

    override fun close() = deflater.end()
}
