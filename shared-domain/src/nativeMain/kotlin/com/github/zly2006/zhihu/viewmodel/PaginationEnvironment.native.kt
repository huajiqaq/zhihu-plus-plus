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
import com.github.zly2006.zhihu.account.defaultNativeAccountStore
import com.github.zly2006.zhihu.account.rememberZhihuAccountStore
import com.github.zly2006.zhihu.filter.ContentOpenEventSupport
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.notification.NotificationSettingsStore
import com.github.zly2006.zhihu.notification.nativeNotificationSettingsStore
import com.github.zly2006.zhihu.platform.copyNativePlainText
import com.github.zly2006.zhihu.platform.nativeSettingsStore
import com.github.zly2006.zhihu.platform.platformName
import com.github.zly2006.zhihu.util.Log
import com.github.zly2006.zhihu.viewmodel.filter.BlockedQuestionAuthor
import com.github.zly2006.zhihu.viewmodel.filter.BlockedUser
import com.github.zly2006.zhihu.viewmodel.filter.getContentFilterDatabase
import io.ktor.client.HttpClient
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement

@Composable
actual fun rememberPaginationEnvironment(allowGuestAccess: Boolean): PaginationEnvironment {
    val store = rememberZhihuAccountStore()
    return remember(allowGuestAccess, store) { NativePaginationEnvironment(accountStore = store) }
}

class NativePaginationEnvironment(
    override val notificationSettingsStore: NotificationSettingsStore = nativeNotificationSettingsStore(),
    private val accountStore: ZhihuAccountStore = defaultNativeAccountStore,
) : PaginationEnvironment,
    CollectionContentEnvironment,
    NotificationEnvironment {
    private val settingsStore = nativeSettingsStore("settings.properties")
    private val contentFilterDatabase = getContentFilterDatabase()

    override val accountClient: ZhihuAccountClient
        get() = accountStore.client

    override fun httpClient(): HttpClient = accountClient.httpClient()

    override fun authenticatedCookies(): Map<String, String> = accountStore.session.cookies

    override suspend fun <T> withAuthenticatedClient(
        block: suspend (client: HttpClient, cookies: Map<String, String>) -> T,
    ): T = accountStore.client.withAuthenticatedClient(block)

    override fun setPlainTextClipboard(label: String, text: String) = copyNativePlainText(text)

    override suspend fun isUserBlocked(userId: String): Boolean =
        contentFilterDatabase.blockedUserDao().isUserBlocked(userId)

    override suspend fun isQuestionAuthorBlocked(userId: String): Boolean =
        contentFilterDatabase.blockedQuestionAuthorDao().isUserBlocked(userId)

    override fun blockedUserIds(): Set<String> = runBlocking {
        contentFilterDatabase
            .blockedUserDao()
            .getAllUsers()
            .map { it.userId }
            .toSet()
    }

    override suspend fun addBlockedUser(
        userId: String,
        userName: String,
        urlToken: String?,
        avatarUrl: String?,
    ) {
        contentFilterDatabase.blockedUserDao().insertUser(
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
        contentFilterDatabase.blockedQuestionAuthorDao().insertUser(
            BlockedQuestionAuthor(
                userId = userId,
                userName = userName,
                urlToken = urlToken,
                avatarUrl = avatarUrl,
            ),
        )
    }

    override suspend fun removeBlockedUser(userId: String) {
        contentFilterDatabase.blockedUserDao().deleteUserById(userId)
    }

    override suspend fun removeBlockedQuestionAuthor(userId: String) {
        contentFilterDatabase.blockedQuestionAuthorDao().deleteUserById(userId)
    }

    override suspend fun recordContentOpenEvent(
        destination: NavDestination,
        questionId: Long?,
        openFrom: String,
    ) {
        ContentOpenEventSupport.recordOpenEvent(
            database = contentFilterDatabase,
            destination = destination,
            questionId = questionId,
            openFrom = openFrom,
        )
    }

    override suspend fun recordOpenEvent(destination: Article, questionId: Long?) =
        recordContentOpenEvent(destination, questionId)

    override suspend fun exportCollectionItemsToHtmlZip(
        collectionTitle: String,
        items: List<CollectionItem>,
        includeImages: Boolean,
        onProgress: suspend (CollectionHtmlExportProgress) -> Unit,
    ): CollectionHtmlExportResult = error("$platformName 暂不支持收藏夹 HTML 压缩包导出")

    override suspend fun handleCollectionExportFailure(error: Exception) {
        Log.e("CollectionContentViewModel", "Failed to export collection HTML zip", error)
    }

    override fun logDecodeFailure(tag: String?, item: JsonElement, error: Exception) {
        Log.e(tag ?: "PaginationViewModel", "Failed to decode item: $item", error)
    }

    override suspend fun handleFetchFailure(tag: String?, error: Exception) {
        Log.e(tag ?: "PaginationViewModel", "Failed to fetch feeds", error)
    }
}
