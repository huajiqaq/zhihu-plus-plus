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

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.github.zly2006.zhihu.account.ZhihuAccountClient
import com.github.zly2006.zhihu.account.ZhihuAccountStore
import com.github.zly2006.zhihu.account.ZhihuRequestProfile
import com.github.zly2006.zhihu.account.androidZhihuAccountStore
import com.github.zly2006.zhihu.account.rememberZhihuAccountStore
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.navDestination
import com.github.zly2006.zhihu.filter.ContentOpenEventSupport
import com.github.zly2006.zhihu.i18n.DomainStrings
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.notification.NotificationSettingsStore
import com.github.zly2006.zhihu.platform.androidUserMessageSink
import com.github.zly2006.zhihu.util.HttpStatusException
import com.github.zly2006.zhihu.util.ResolvedCollectionHtmlExportItem
import com.github.zly2006.zhihu.util.buildArticleExportFileName
import com.github.zly2006.zhihu.util.buildOfflineArticleExportHtml
import com.github.zly2006.zhihu.util.clipboardManager
import com.github.zly2006.zhihu.util.exportCollectionItemsToZip
import com.github.zly2006.zhihu.util.saveBitmapToGallery
import com.github.zly2006.zhihu.viewmodel.filter.BlockedQuestionAuthor
import com.github.zly2006.zhihu.viewmodel.filter.BlockedUser
import com.github.zly2006.zhihu.viewmodel.filter.getContentFilterDatabase
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpMethod
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.put
import com.github.zly2006.zhihu.navigation.Article as ArticleDestination

interface AndroidContextPaginationEnvironment : PaginationEnvironment {
    val context: Context
}

open class SharedAndroidPaginationEnvironment(
    override val context: Context,
    override val allowGuestAccess: Boolean,
    private val accountStore: ZhihuAccountStore,
) : AndroidContextPaginationEnvironment,
    CollectionContentEnvironment {
    private val userMessageSink by lazy { androidUserMessageSink(context) }

    override val accountClient: ZhihuAccountClient
        get() = accountStore.client

    override fun httpClient(): HttpClient = httpClient(ZhihuRequestProfile.Web)

    override fun httpClient(profile: ZhihuRequestProfile): HttpClient {
        val owner = accountClient
        val useCookies = owner.load().login
        return owner.httpClient(profile, useCookies)
    }

    override fun mobileHomeFeedHttpClient(): HttpClient = httpClient(ZhihuRequestProfile.Mobile)

    override suspend fun request(url: String, method: HttpMethod, profile: ZhihuRequestProfile, block: suspend HttpRequestBuilder.() -> Unit): HttpResponse {
        val owner = accountClient
        val useCookies = owner.load().login
        if (!useCookies && (!allowGuestAccess || profile == ZhihuRequestProfile.Mobile)) {
            throw com.github.zly2006.zhihu.account
                .ZhihuLoginRequiredException()
        }
        return owner.request(url, method, profile, useCookies, block)
    }

    override suspend fun <T> withAuthenticatedClient(
        block: suspend (client: HttpClient, cookies: Map<String, String>) -> T,
    ): T = accountStore.client.withAuthenticatedClient(block)

    override fun authenticatedCookies(): Map<String, String> {
        val session = accountStore.client.load()
        return session.cookies.takeIf { session.login }.orEmpty()
    }

    override suspend fun handleFetchFailure(
        tag: String?,
        error: Exception,
    ) {
        Log.e(tag, "Failed to fetch feeds")
        if (error is com.github.zly2006.zhihu.account.ZhihuLoginRequiredException || (error is HttpStatusException && error.status.value in setOf(401, 403))) return
        userMessageSink.showShortMessage(DomainStrings.loadFailed(zhihuFetchFailureMessage(error)))
    }

    override suspend fun handleMobileHomeFeedFailure(error: Exception) {
        Log.e("AndroidHomeFeedViewModel", "Failed to fetch feeds", error)
        userMessageSink.showShortMessage(DomainStrings.mobileFeedFailed(error.message.orEmpty()))
    }

    override suspend fun isUserBlocked(userId: String): Boolean =
        getContentFilterDatabase(context).let { database ->
            database.blockedUserDao().isUserBlocked(userId)
        }

    override suspend fun isQuestionAuthorBlocked(userId: String): Boolean =
        getContentFilterDatabase(context).let { database ->
            database.blockedQuestionAuthorDao().isUserBlocked(userId)
        }

    override suspend fun blockedUserIds(): Set<String> = getContentFilterDatabase(context)
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
        val database = getContentFilterDatabase(context)
        database.blockedUserDao().insertUser(
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
        val database = getContentFilterDatabase(context)
        database.blockedQuestionAuthorDao().insertUser(
            BlockedQuestionAuthor(
                userId = userId,
                userName = userName,
                urlToken = urlToken,
                avatarUrl = avatarUrl,
            ),
        )
    }

    override suspend fun removeBlockedUser(userId: String) {
        val database = getContentFilterDatabase(context)
        database.blockedUserDao().deleteUserById(userId)
    }

    override suspend fun removeBlockedQuestionAuthor(userId: String) {
        val database = getContentFilterDatabase(context)
        database.blockedQuestionAuthorDao().deleteUserById(userId)
    }

    override suspend fun recordContentOpenEvent(
        destination: NavDestination,
        questionId: Long?,
        openFrom: String,
    ) {
        ContentOpenEventSupport.recordOpenEvent(
            database = getContentFilterDatabase(context),
            destination = destination,
            questionId = questionId,
            openFrom = openFrom,
        )
    }

    override suspend fun showLocalRecommendationDatabaseError() {
        withContext(Dispatchers.Main) {
            AlertDialog
                .Builder(context)
                .setTitle(DomainStrings.databaseError)
                .setMessage(DomainStrings.localDatabaseUnavailable)
                .setPositiveButton(DomainStrings.confirm) { dialog, _ -> dialog.dismiss() }
                .show()
        }
    }

    override suspend fun exportCollectionItemsToHtmlZip(
        collectionTitle: String,
        items: List<CollectionItem>,
        includeImages: Boolean,
        onProgress: suspend (CollectionHtmlExportProgress) -> Unit,
    ): CollectionHtmlExportResult {
        val outputDir = context.getExternalFilesDir(null)
            ?: throw IllegalStateException(DomainStrings.externalStorageUnavailable)
        val exportHttpClient = httpClient()
        val result = withContext(Dispatchers.IO) {
            exportCollectionItemsToZip(
                collectionTitle = collectionTitle,
                items = items,
                cacheDir = context.cacheDir,
                outputDir = outputDir,
                displayTitle = { item ->
                    item.content.title.ifBlank { item.content.description() }
                },
                resolveItem = { item ->
                    resolveCollectionItemForHtmlExport(
                        item = item,
                        exportHttpClient = exportHttpClient,
                        includeImages = includeImages,
                    )
                },
                onProgress = { progress ->
                    withContext(Dispatchers.Main) {
                        onProgress(
                            CollectionHtmlExportProgress(
                                totalCount = progress.totalCount,
                                processedCount = progress.processedCount,
                                successCount = progress.successCount,
                                skippedCount = progress.skippedCount,
                                failedCount = progress.failedCount,
                                currentTitle = progress.currentTitle,
                            ),
                        )
                    }
                },
            )
        }
        return CollectionHtmlExportResult(
            totalCount = result.totalCount,
            successCount = result.successCount,
            skippedCount = result.skippedCount,
            failedCount = result.failedCount,
            zipFilePath = result.zipFile?.absolutePath,
        )
    }

    override suspend fun handleCollectionExportFailure(error: Exception) {
        Log.e("CollectionContentViewModel", "Failed to export collection HTML zip", error)
        userMessageSink.showShortMessage(DomainStrings.exportErrorDetail(error.message.orEmpty()))
    }

    private suspend fun resolveCollectionItemForHtmlExport(
        item: CollectionItem,
        exportHttpClient: HttpClient,
        includeImages: Boolean,
    ): ResolvedCollectionHtmlExportItem? {
        val navDestination = item.content.navDestination as? ArticleDestination ?: return null
        val content = getOrFetchContentDetail(navDestination)
            ?: throw IllegalStateException(DomainStrings.itemDetailError(item.content.title))
        if (content !is DataHolder.Answer && content !is DataHolder.Article) {
            return null
        }

        return ResolvedCollectionHtmlExportItem(
            htmlFileName = buildArticleExportFileName(content, "html"),
            htmlContent = buildOfflineArticleExportHtml(
                loadAssetText = ::loadExportAssetText,
                content = content,
                includeAppAttribution = true,
                httpClient = exportHttpClient,
                includeImages = includeImages,
            ),
        )
    }

    override fun setPlainTextClipboard(
        label: String,
        text: String,
    ) {
        context.clipboardManager.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    override fun saveImageToMediaStore(
        displayName: String,
        bitmap: Any,
    ) = saveBitmapToGallery(context, displayName, bitmap as android.graphics.Bitmap)

    override fun saveHtmlToDownloads(
        displayName: String,
        htmlContent: String,
    ): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/html")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Zhihu++")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                ?: throw IllegalStateException(DomainStrings.cannotCreateDownloadFile)

            return try {
                resolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
                    writer.write(htmlContent)
                } ?: throw IllegalStateException(DomainStrings.cannotOpenDownloadFile)

                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
                "Zhihu++/$displayName"
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        }

        @Suppress("DEPRECATION")
        val downloadsDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "Zhihu++",
        )
        if (!downloadsDir.exists() && !downloadsDir.mkdirs()) {
            throw IllegalStateException(DomainStrings.cannotCreateDownloadDirectory)
        }
        val file = File(downloadsDir, displayName)
        file.writeText(htmlContent)
        return file.absolutePath
    }

    override suspend fun exportPdf(content: DataHolder.Content, includeAppAttribution: Boolean): ArticleExportResult {
        val activity = generateSequence(context) { (it as? ContextWrapper)?.baseContext?.takeUnless { base -> base === it } }
            .filterIsInstance<Activity>()
            .firstOrNull()
            ?: error(DomainStrings.exportHostUnavailable)
        return exportAndroidArticlePdf(activity, content, includeAppAttribution, httpClient(), AndroidArticleExportRenderer(activity, accountStore))
    }

    override fun articleImageExportRenderer(): ArticleImageExportRenderer =
        AndroidArticleExportRenderer(context, accountStore)

    override fun hasImageExportPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED

    override fun requiresHtmlExportPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    override fun requestImageExportPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            ActivityCompat.requestPermissions(
                context as Activity,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                1001,
            )
        }
    }

    override fun loadExportAssetText(fileName: String): String =
        context.assets.open(fileName).use { inputStream ->
            inputStream.bufferedReader().use { reader ->
                reader.readText()
            }
        }
}

class SharedAndroidNotificationEnvironment(
    context: Context,
    allowGuestAccess: Boolean,
    override val notificationSettingsStore: NotificationSettingsStore,
    accountStore: ZhihuAccountStore = androidZhihuAccountStore(context),
) : SharedAndroidPaginationEnvironment(context, allowGuestAccess, accountStore),
    NotificationEnvironment

fun PaginationViewModel<*>.paginationEnvironment(context: Context): AndroidContextPaginationEnvironment =
    SharedAndroidPaginationEnvironment(context, allowGuestAccess, androidZhihuAccountStore(context))

@Composable
actual fun rememberPaginationEnvironment(allowGuestAccess: Boolean): PaginationEnvironment {
    val context = LocalContext.current
    val store = rememberZhihuAccountStore()
    return remember(context, allowGuestAccess, store) { SharedAndroidPaginationEnvironment(context, allowGuestAccess, store) }
}

fun PaginationViewModel<*>.refresh(context: Context) {
    refresh(paginationEnvironment(context))
}

fun PaginationViewModel<*>.loadMore(context: Context) {
    loadMore(paginationEnvironment(context))
}

fun PaginationViewModel<*>.httpClient(context: Context): HttpClient =
    paginationEnvironment(context).httpClient()
