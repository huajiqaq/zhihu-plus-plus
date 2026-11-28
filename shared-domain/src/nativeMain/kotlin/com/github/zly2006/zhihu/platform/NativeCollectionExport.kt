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

import com.github.zly2006.zhihu.data.navDestination
import com.github.zly2006.zhihu.i18n.DomainStrings
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.util.AppLog
import com.github.zly2006.zhihu.util.buildArticleExportFileName
import com.github.zly2006.zhihu.util.buildCollectionExportZipFileName
import com.github.zly2006.zhihu.util.buildOfflineArticleExportHtml
import com.github.zly2006.zhihu.viewmodel.ArticleExportResult
import com.github.zly2006.zhihu.viewmodel.CollectionContentEnvironment
import com.github.zly2006.zhihu.viewmodel.CollectionHtmlExportProgress
import com.github.zly2006.zhihu.viewmodel.CollectionHtmlExportResult
import com.github.zly2006.zhihu.viewmodel.CollectionItem
import com.github.zly2006.zhihu.viewmodel.ensureExportAccountActive
import com.github.zly2006.zhihu.viewmodel.fetchContentDetail
import kotlin.time.Clock
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import platform.Foundation.NSError
import platform.Foundation.NSFileCoordinator
import platform.Foundation.NSFileCoordinatorReadingForUploading
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL

internal suspend fun exportNativeCollection(
    environment: CollectionContentEnvironment,
    collectionTitle: String,
    items: List<CollectionItem>,
    includeImages: Boolean,
    onProgress: suspend (CollectionHtmlExportProgress) -> Unit,
    delivery: NativeFileDelivery,
): CollectionHtmlExportResult {
    var processed = 0
    var succeeded = 0
    var skipped = 0
    var failed = 0
    var currentTitle = ""
    suspend fun progress() = onProgress(CollectionHtmlExportProgress(items.size, processed, succeeded, skipped, failed, currentTitle))
    val delivered = withNativeExportDirectory { directory ->
        val contentDirectory = "$directory/articles"
        check(NSFileManager.defaultManager.createDirectoryAtPath(contentDirectory, true, null, null)) { DomainStrings.exportCacheDirectoryFailed }
        progress()
        for ((index, item) in items.withIndex()) {
            ensureExportAccountActive()
            currentTitle = item.content.title
            try {
                val article = item.content.navDestination as? Article
                val content = article?.let { environment.fetchContentDetail(it) }
                if (content == null) {
                    skipped++
                } else {
                    withContext(Dispatchers.Default) {
                        val html = buildOfflineArticleExportHtml(
                            loadAssetText = ::readNativeExportAsset,
                            content = content,
                            includeAppAttribution = true,
                            httpClient = environment.httpClient(),
                            includeImages = includeImages,
                        )
                        ensureExportAccountActive()
                        writeNativeExportText("$contentDirectory/${index + 1}_${buildArticleExportFileName(content, "html")}", html)
                    }
                    succeeded++
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                failed++
                AppLog.e("CollectionExport", "Collection item export failed", error)
            } finally {
                processed++
                progress()
            }
        }
        if (succeeded == 0) return@withNativeExportDirectory ArticleExportResult.Cancelled
        val zipPath = "$directory/${buildCollectionExportZipFileName(collectionTitle, Clock.System.now().toEpochMilliseconds())}"
        createNativeZip(contentDirectory, zipPath)
        ensureExportAccountActive()
        delivery.saveFile(zipPath, image = false)
    }
    return CollectionHtmlExportResult(
        totalCount = items.size, successCount = succeeded, skippedCount = skipped, failedCount = failed,
        zipFilePath = null, delivery = delivered.takeIf { succeeded > 0 },
    )
}

/** Foundation 的上传协调选项将目录生成真正 ZIP；只在 accessor 有效期内消费临时归档。 */
private suspend fun createNativeZip(directory: String, outputFile: String) = coroutineScope {
    val coordinator = NSFileCoordinator(filePresenter = null)
    val operationJob = currentCoroutineContext()[Job]
    val cancellation = launch {
        try {
            kotlinx.coroutines.awaitCancellation()
        } finally {
            coordinator.cancel()
        }
    }
    try {
        withContext(Dispatchers.Default) {
            var copied = false
            var failure: Throwable? = null
            memScoped {
                val error = alloc<ObjCObjectVar<NSError?>>()
                error.value = null
                coordinator.coordinateReadingItemAtURL(
                    NSURL.fileURLWithPath(directory), NSFileCoordinatorReadingForUploading, error.ptr,
                ) { url ->
                    try {
                        operationJob?.ensureActive()
                        val path = checkNotNull(url?.path) { DomainStrings.exportRenderFailed }
                        SystemFileSystem.source(Path(path)).buffered().use { input ->
                            SystemFileSystem.sink(Path(outputFile)).buffered().use { output ->
                                val buffer = ByteArray(64 * 1024)
                                var first = true
                                while (true) {
                                    operationJob?.ensureActive()
                                    val count = input.readAtMostTo(buffer)
                                    if (count < 0) break
                                    if (first) {
                                        check(count >= 4 && buffer[0] == 0x50.toByte() && buffer[1] == 0x4b.toByte()) { DomainStrings.exportRenderFailed }
                                        first = false
                                    }
                                    output.write(buffer, 0, count)
                                }
                                check(!first) { DomainStrings.exportRenderFailed }
                            }
                        }
                        copied = true
                    } catch (cause: Throwable) {
                        failure = cause
                    }
                }
                failure?.let { throw it }
                error.value?.let { throw IllegalStateException(it.localizedDescription) }
                check(copied) { DomainStrings.exportRenderFailed }
            }
        }
    } finally {
        cancellation.cancel()
    }
}
