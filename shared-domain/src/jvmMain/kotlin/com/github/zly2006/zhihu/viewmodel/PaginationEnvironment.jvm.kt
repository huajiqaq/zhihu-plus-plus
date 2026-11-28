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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.github.zly2006.zhihu.account.ZhihuAccountClient
import com.github.zly2006.zhihu.account.ZhihuAccountStore
import com.github.zly2006.zhihu.account.rememberZhihuAccountStore
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.navDestination
import com.github.zly2006.zhihu.desktop.copyDesktopPlainText
import com.github.zly2006.zhihu.desktop.desktopZhihuDataFile
import com.github.zly2006.zhihu.desktop.desktopZhihuDownloadsDir
import com.github.zly2006.zhihu.filter.ContentOpenEventSupport
import com.github.zly2006.zhihu.i18n.DomainStrings
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.notification.NotificationSettingsStore
import com.github.zly2006.zhihu.notification.desktopNotificationSettingsStore
import com.github.zly2006.zhihu.util.AppLog
import com.github.zly2006.zhihu.util.buildArticleExportFileName
import com.github.zly2006.zhihu.util.buildCollectionExportZipFileName
import com.github.zly2006.zhihu.util.sanitizeArticleExportFileNamePart
import com.github.zly2006.zhihu.viewmodel.filter.BlockedQuestionAuthor
import com.github.zly2006.zhihu.viewmodel.filter.BlockedUser
import com.github.zly2006.zhihu.viewmodel.filter.getContentFilterDatabase
import io.ktor.client.HttpClient
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.github.zly2006.zhihu.util.buildOfflineArticleExportHtml as buildSharedOfflineArticleExportHtml

private val desktopContentFilterDb by lazy { getContentFilterDatabase() }

class DesktopPaginationEnvironment(
    override val notificationSettingsStore: NotificationSettingsStore = desktopNotificationSettingsStore(),
    private val store: ZhihuAccountStore,
    override val allowGuestAccess: Boolean = false,
) : PaginationEnvironment,
    CollectionContentEnvironment,
    NotificationEnvironment {
    private val contentFilterDb by lazy { desktopContentFilterDb }

    override val accountClient: ZhihuAccountClient
        get() = store.client

    override fun httpClient(): HttpClient = accountClient.let { it.httpClient(useCookies = it.load().login) }

    override fun authenticatedCookies(): Map<String, String> = store.session
        .takeIf { it.login }
        ?.cookies
        .orEmpty()

    override suspend fun <T> withAuthenticatedClient(
        block: suspend (client: HttpClient, cookies: Map<String, String>) -> T,
    ): T = store.client.withAuthenticatedClient(block)

    override suspend fun handleFetchFailure(
        tag: String?,
        error: Exception,
    ) {
        AppLog.e(tag ?: "PaginationViewModel", "Failed to fetch feeds", error)
    }

    override fun setPlainTextClipboard(
        label: String,
        text: String,
    ) {
        copyDesktopPlainText(text)
    }

    override suspend fun isUserBlocked(userId: String): Boolean =
        contentFilterDb.blockedUserDao().isUserBlocked(userId)

    override suspend fun isQuestionAuthorBlocked(userId: String): Boolean =
        contentFilterDb.blockedQuestionAuthorDao().isUserBlocked(userId)

    override suspend fun blockedUserIds(): Set<String> = contentFilterDb
        .blockedUserDao()
        .getAllUsers()
        .map { it.userId }
        .toSet()

    override suspend fun addBlockedUser(
        userId: String,
        userName: String,
        urlToken: String?,
        avatarUrl: String?,
    ) {
        contentFilterDb.blockedUserDao().insertUser(
            BlockedUser(
                userId = userId,
                userName = userName,
                urlToken = urlToken,
                avatarUrl = avatarUrl,
            ),
        )
    }

    override suspend fun addBlockedQuestionAuthor(
        userId: String,
        userName: String,
        urlToken: String?,
        avatarUrl: String?,
    ) {
        contentFilterDb.blockedQuestionAuthorDao().insertUser(
            BlockedQuestionAuthor(
                userId = userId,
                userName = userName,
                urlToken = urlToken,
                avatarUrl = avatarUrl,
            ),
        )
    }

    override suspend fun removeBlockedUser(userId: String) {
        contentFilterDb.blockedUserDao().deleteUserById(userId)
    }

    override suspend fun removeBlockedQuestionAuthor(userId: String) {
        contentFilterDb.blockedQuestionAuthorDao().deleteUserById(userId)
    }

    override suspend fun recordContentOpenEvent(
        destination: NavDestination,
        questionId: Long?,
        openFrom: String,
    ) {
        ContentOpenEventSupport.recordOpenEvent(
            database = contentFilterDb,
            destination = destination,
            questionId = questionId,
            openFrom = openFrom,
        )
    }

    override fun hasImageExportPermission(): Boolean = true

    override fun requiresHtmlExportPermission(): Boolean = false

    override fun requestImageExportPermission() = Unit

    override fun loadExportAssetText(fileName: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(fileName)) { DomainStrings.missingExportAsset(fileName) }
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }

    override suspend fun exportHtml(
        content: DataHolder.Content,
        includeAppAttribution: Boolean,
    ): ArticleExportResult = exportDesktopHtml(content, includeAppAttribution)

    override suspend fun exportPdf(
        content: DataHolder.Content,
        includeAppAttribution: Boolean,
    ): ArticleExportResult = exportDesktopPdf(content, includeAppAttribution)

    override suspend fun exportImage(
        content: DataHolder.Content,
        includeAppAttribution: Boolean,
        extraSectionsHtml: String,
        includeComments: Boolean,
    ): ArticleExportResult = exportDesktopImage(content, includeAppAttribution, extraSectionsHtml, includeComments)

    override suspend fun exportCollectionItemsToHtmlZip(
        collectionTitle: String,
        items: List<CollectionItem>,
        includeImages: Boolean,
        onProgress: suspend (CollectionHtmlExportProgress) -> Unit,
    ): CollectionHtmlExportResult {
        val timestampMillis = System.currentTimeMillis()
        val stagingDir = File(
            desktopZhihuDataFile("collection-html-export-cache").also { directory ->
                if (!directory.exists()) {
                    directory.mkdirs()
                }
            },
            "collection_html_export_${sanitizeArticleExportFileNamePart(collectionTitle).ifBlank { "collection" }}_$timestampMillis",
        )
        if (stagingDir.exists()) {
            stagingDir.deleteRecursively()
        }
        if (!stagingDir.mkdirs()) {
            throw IllegalStateException(DomainStrings.exportCacheDirectoryFailed)
        }

        val outputDir = desktopZhihuDownloadsDir(DomainStrings.exportZipDirectoryFailed)
        val exportHttpClient = httpClient()

        var processedCount = 0
        var successCount = 0
        var skippedCount = 0
        var failedCount = 0
        var currentTitle = ""

        suspend fun emitProgress() {
            onProgress(
                CollectionHtmlExportProgress(
                    totalCount = items.size,
                    processedCount = processedCount,
                    successCount = successCount,
                    skippedCount = skippedCount,
                    failedCount = failedCount,
                    currentTitle = currentTitle,
                ),
            )
        }

        emitProgress()

        items.forEach { item ->
            currentTitle = item.content.title
            try {
                val content = item.resolveDesktopExportContent(this)
                if (content == null) {
                    skippedCount++
                } else {
                    val htmlContent = buildSharedOfflineArticleExportHtml(
                        loadAssetText = ::loadExportAssetText,
                        content = content,
                        includeAppAttribution = true,
                        httpClient = exportHttpClient,
                        includeImages = includeImages,
                        useOriginalOnImageFetchFailure = true,
                    )
                    File(stagingDir, buildArticleExportFileName(content, "html")).writeText(htmlContent)
                    successCount++
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                failedCount++
                AppLog.e("CollectionContentViewModel", "Failed to export collection item: ${item.content.title}", e)
            } finally {
                processedCount++
                emitProgress()
            }
        }

        val zipFile = if (successCount > 0) {
            if (!outputDir.exists() && !outputDir.mkdirs()) {
                throw IllegalStateException(DomainStrings.exportZipDirectoryFailed)
            }
            File(outputDir, buildCollectionExportZipFileName(collectionTitle, timestampMillis)).also { file ->
                if (file.exists()) {
                    file.delete()
                }
                zipDirectoryContents(stagingDir, file)
            }
        } else {
            null
        }

        return CollectionHtmlExportResult(
            totalCount = items.size,
            successCount = successCount,
            skippedCount = skippedCount,
            failedCount = failedCount,
            zipFilePath = zipFile?.absolutePath,
        )
    }

    override suspend fun handleCollectionExportFailure(error: Exception) {
        AppLog.e("CollectionContentViewModel", "Failed to export collection HTML zip", error)
    }
}

private suspend fun CollectionItem.resolveDesktopExportContent(
    environment: DesktopPaginationEnvironment,
): DataHolder.Content? {
    val destination = content.navDestination as? Article ?: return null
    return environment.fetchContentDetail(destination)
}

private suspend fun zipDirectoryContents(
    sourceDir: File,
    zipFile: File,
) = withContext(Dispatchers.IO) {
    ZipOutputStream(zipFile.outputStream().buffered()).use { outputStream ->
        sourceDir
            .listFiles()
            ?.sortedBy { it.name }
            ?.forEach { file ->
                addFileToZip(
                    file = file,
                    entryPrefix = "",
                    outputStream = outputStream,
                )
            }
    }
}

private fun addFileToZip(
    file: File,
    entryPrefix: String,
    outputStream: ZipOutputStream,
) {
    if (file.isDirectory) {
        val nextPrefix = if (entryPrefix.isBlank()) file.name else "$entryPrefix/${file.name}"
        file
            .listFiles()
            ?.sortedBy { it.name }
            ?.forEach { child ->
                addFileToZip(
                    file = child,
                    entryPrefix = nextPrefix,
                    outputStream = outputStream,
                )
            }
        return
    }

    val entryName = if (entryPrefix.isBlank()) file.name else "$entryPrefix/${file.name}"
    outputStream.putNextEntry(ZipEntry(entryName))
    file.inputStream().buffered().use { inputStream ->
        inputStream.copyTo(outputStream)
    }
    outputStream.closeEntry()
}

@Composable
actual fun rememberPaginationEnvironment(allowGuestAccess: Boolean): PaginationEnvironment {
    val store = rememberZhihuAccountStore()
    return remember(allowGuestAccess, store) { DesktopPaginationEnvironment(store = store, allowGuestAccess = allowGuestAccess) }
}
