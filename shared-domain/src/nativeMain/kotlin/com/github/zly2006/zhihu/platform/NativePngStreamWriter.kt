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

import com.github.zly2006.zhihu.i18n.DomainStrings
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.free
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.io.Sink
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGContextFillRect
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGContextSetRGBFillColor
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGImageRef
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGImageAlphaInfo.kCGImageAlphaPremultipliedLast
import platform.CoreGraphics.kCGBitmapByteOrder32Big
import platform.posix.memset
import platform.zlib.Z_DEFAULT_COMPRESSION
import platform.zlib.Z_FINISH
import platform.zlib.Z_NO_FLUSH
import platform.zlib.Z_OK
import platform.zlib.Z_STREAM_END
import platform.zlib.crc32
import platform.zlib.deflate
import platform.zlib.deflateEnd
import platform.zlib.deflateInit_
import platform.zlib.z_stream
import platform.zlib.zlibVersion

/**
 * ImageIO 的单图编码入口要求完整 CGImage；这里用系统 zlib 写一个连续 PNG 流，
 * 内存仅含当前截图、当前 RGBA 分段和压缩缓冲，不再分配整页位图。
 */
internal class NativePngStreamWriter(
    filePath: String,
    private val width: Int,
    private val height: Int,
) {
    private val stream = nativeHeap.alloc<z_stream>()
    private var initialized = false
    private var closed = false
    private var rowsWritten = 0
    private var bytesWritten = 0L
    private var sink: Sink? = null
    private val compressed = ByteArray(64 * 1024)
    private val row = ByteArray(width * 3 + 1)

    init {
        try {
            require(width in 1..4096 && height in 1..1_000_000 && width.toLong() * height <= 400_000_000L) {
                DomainStrings.exportTooLarge
            }
            memset(stream.ptr, 0, sizeOf<z_stream>().convert())
            check(deflateInit_(stream.ptr, Z_DEFAULT_COMPRESSION, zlibVersion(), sizeOf<z_stream>().toInt()) == Z_OK) {
                DomainStrings.exportRenderFailed
            }
            initialized = true
            sink = SystemFileSystem.sink(Path(filePath)).buffered()
            writeBytes(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))
            val header = ByteArray(13)
            writeInt(header, 0, width)
            writeInt(header, 4, height)
            header[8] = 8
            header[9] = 2 // RGB，无损输出；透明区域先合成白底。
            chunk("IHDR", header)
        } catch (error: Throwable) {
            runCatching { close() }.exceptionOrNull()?.let(error::addSuppressed)
            throw error
        }
    }

    suspend fun append(image: CGImageRef, expectedHeight: Int) {
        val sourceWidth = CGImageGetWidth(image).toLong()
        val sourceHeight = CGImageGetHeight(image).toLong()
        check(!closed && sourceWidth > 0 && sourceHeight > 0 &&
            kotlin.math.abs(sourceWidth * expectedHeight - sourceHeight * width) <= maxOf(sourceWidth, sourceHeight) * 2
        ) { DomainStrings.exportRenderFailed }
        check(expectedHeight in 1..4096 && rowsWritten.toLong() + expectedHeight <= height) { DomainStrings.exportTooLarge }
        val rgba = ByteArray(width * expectedHeight * 4)
        rgba.usePinned { pixels ->
            val colorSpace = checkNotNull(CGColorSpaceCreateDeviceRGB())
            try {
                val context = checkNotNull(
                    CGBitmapContextCreate(
                        pixels.addressOf(0), width.convert(), expectedHeight.convert(), 8u,
                        (width * 4).convert(), colorSpace,
                        kCGImageAlphaPremultipliedLast.value or kCGBitmapByteOrder32Big,
                    ),
                ) { DomainStrings.exportRenderFailed }
                try {
                    val rect = CGRectMake(0.0, 0.0, width.toDouble(), expectedHeight.toDouble())
                    CGContextSetRGBFillColor(context, 1.0, 1.0, 1.0, 1.0)
                    CGContextFillRect(context, rect)
                    CGContextDrawImage(context, rect, image)
                } finally {
                    CGContextRelease(context)
                }
            } finally {
                CGColorSpaceRelease(colorSpace)
            }
        }
        for (y in 0 until expectedHeight) {
            currentCoroutineContext().ensureActive()
            var source = y * width * 4
            var destination = 1
            repeat(width) {
                row[destination++] = rgba[source++]
                row[destination++] = rgba[source++]
                row[destination++] = rgba[source++]
                source++
            }
            row.usePinned { input ->
                stream.next_in = input.addressOf(0).reinterpret()
                stream.avail_in = row.size.convert()
                do {
                    pump(Z_NO_FLUSH)
                } while (stream.avail_in > 0u)
            }
            rowsWritten++
        }
    }

    fun finish() {
        check(!closed && rowsWritten == height) { DomainStrings.exportRenderFailed }
        stream.next_in = null
        stream.avail_in = 0u
        while (pump(Z_FINISH) != Z_STREAM_END) Unit
        chunk("IEND", byteArrayOf())
        checkNotNull(sink).flush()
    }

    private fun pump(flush: Int): Int = compressed.usePinned { output ->
        stream.next_out = output.addressOf(0).reinterpret()
        stream.avail_out = compressed.size.convert()
        val status = deflate(stream.ptr, flush)
        check(status == Z_OK || status == Z_STREAM_END) { DomainStrings.exportRenderFailed }
        val count = compressed.size - stream.avail_out.toInt()
        if (count > 0) chunk("IDAT", compressed, count)
        status
    }

    private fun chunk(type: String, data: ByteArray, length: Int = data.size) {
        val name = type.encodeToByteArray()
        val size = ByteArray(4).also { writeInt(it, 0, length) }
        writeBytes(size)
        writeBytes(name)
        writeBytes(data, length)
        var checksum = name.usePinned { crc32(0u, it.addressOf(0).reinterpret(), 4u) }
        if (length > 0) checksum = data.usePinned { crc32(checksum, it.addressOf(0).reinterpret(), length.convert()) }
        writeBytes(ByteArray(4).also { writeInt(it, 0, checksum.toInt()) })
    }

    private fun writeBytes(bytes: ByteArray, length: Int = bytes.size) {
        bytesWritten += length
        check(bytesWritten <= 512L * 1024 * 1024) { DomainStrings.exportTooLarge }
        if (length > 0) checkNotNull(sink).write(bytes, 0, length)
    }

    fun close() {
        if (closed) return
        closed = true
        try {
            sink?.close()
        } finally {
            if (initialized) deflateEnd(stream.ptr)
            nativeHeap.free(stream.ptr)
        }
    }
}

private fun writeInt(bytes: ByteArray, offset: Int, value: Int) {
    for (index in 0..3) bytes[offset + index] = (value ushr ((3 - index) * 8)).toByte()
}
