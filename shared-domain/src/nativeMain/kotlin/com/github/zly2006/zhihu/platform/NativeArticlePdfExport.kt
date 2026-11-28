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

import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.i18n.DomainStrings
import com.github.zly2006.zhihu.util.buildArticleExportFileName
import com.github.zly2006.zhihu.viewmodel.ArticleExportResult
import com.github.zly2006.zhihu.viewmodel.ensureExportAccountActive
import io.ktor.client.HttpClient
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.Foundation.writeToFile
import platform.PDFKit.PDFDocument
import platform.PDFKit.kPDFDisplayBoxMediaBox
import platform.WebKit.WKPDFConfiguration

/** WebKit 原生 PDF 保留文字和矢量内容；连续单页而非 A4 分页，不经过 PNG 或全页位图。 */
internal suspend fun exportNativeArticlePdf(
    content: DataHolder.Content,
    includeAppAttribution: Boolean,
    httpClient: HttpClient,
    delivery: NativeFileDelivery,
): ArticleExportResult = withNativeExportDirectory { directory ->
    val html = prepareNativeArticleForRendering(content, includeAppAttribution, "", false, httpClient, directory)
    val path = "$directory/${buildArticleExportFileName(content, "pdf")}"
    withNativeArticleExportView(html, delivery) { view, height ->
        val width = withContext(Dispatchers.Main) { view.bounds.useContents { size.width } }
        val data = withContext(Dispatchers.Main) {
            ensureExportAccountActive()
            withTimeoutOrNull(60_000L) {
                suspendCancellableCoroutine<NSData> { continuation ->
                    val configuration = WKPDFConfiguration().apply {
                        rect = CGRectMake(0.0, 0.0, width, height.toDouble())
                    }
                    view.createPDFWithConfiguration(configuration) { pdf, error ->
                        if (continuation.isActive) {
                            if (error != null || pdf == null) {
                                continuation.resumeWithException(IllegalStateException(error?.localizedDescription ?: DomainStrings.exportRenderFailed))
                            } else {
                                continuation.resume(pdf)
                            }
                        }
                    }
                }
            } ?: error(DomainStrings.renderTimedOut)
        }
        withContext(Dispatchers.Default) {
            check(data.length in 1uL..(256uL * 1024uL * 1024uL)) { DomainStrings.exportTooLarge }
            val document = PDFDocument(data)
            check(document.pageCount == 1uL) { DomainStrings.exportRenderFailed }
            val page = checkNotNull(document.pageAtIndex(0u)) { DomainStrings.exportRenderFailed }
            val boundsMatch = page.boundsForBox(kPDFDisplayBoxMediaBox).useContents {
                abs(size.width - width) <= 2.0 && abs(size.height - height) <= 2.0
            }
            check(boundsMatch && !document.string.isNullOrBlank()) { DomainStrings.exportRenderFailed }
            ensureExportAccountActive()
            check(data.writeToFile(path, atomically = true)) { DomainStrings.fileWriteFailed(path) }
        }
    }
    ensureExportAccountActive()
    when (val result = delivery.saveFile(path, image = false)) {
        is ArticleExportResult.Completed -> ArticleExportResult.Completed("${result.completionMessage}\n${DomainStrings.pdfExportContinuousPage}")
        ArticleExportResult.Cancelled -> ArticleExportResult.Cancelled
    }
}
