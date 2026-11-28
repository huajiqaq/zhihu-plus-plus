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
import com.github.zly2006.zhihu.account.ZhihuAccountStore
import com.github.zly2006.zhihu.account.rememberZhihuAccountStore
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.platform.LocalNativeFileDelivery
import com.github.zly2006.zhihu.platform.NativeFileDelivery
import com.github.zly2006.zhihu.platform.exportNativeArticleHtml
import com.github.zly2006.zhihu.platform.exportNativeArticleImage
import com.github.zly2006.zhihu.platform.exportNativeArticlePdf
import com.github.zly2006.zhihu.platform.exportNativeCollection
import com.github.zly2006.zhihu.platform.readNativeExportAsset
import com.github.zly2006.zhihu.platform.requireNativeFileDelivery
import com.github.zly2006.zhihu.notification.NotificationSettingsStore
import com.github.zly2006.zhihu.notification.nativeNotificationSettingsStore
import com.github.zly2006.zhihu.platform.copyNativePlainText

@Composable
actual fun rememberPaginationEnvironment(allowGuestAccess: Boolean): PaginationEnvironment {
    val store = rememberZhihuAccountStore()
    val delivery = LocalNativeFileDelivery.current
    return remember(allowGuestAccess, store, delivery) {
        NativePaginationEnvironment(accountStore = store, allowGuestAccess = allowGuestAccess, fileDelivery = delivery)
    }
}

class NativePaginationEnvironment(
    notificationSettingsStore: NotificationSettingsStore = nativeNotificationSettingsStore(),
    accountStore: ZhihuAccountStore,
    allowGuestAccess: Boolean = false,
    private val fileDelivery: NativeFileDelivery? = null,
) : StoredPaginationEnvironment(notificationSettingsStore, accountStore, allowGuestAccess) {
    override fun setPlainTextClipboard(label: String, text: String) = copyNativePlainText(text)

    override fun loadExportAssetText(fileName: String): String = readNativeExportAsset(fileName)

    override suspend fun exportCollectionItemsToHtmlZip(
        collectionTitle: String,
        items: List<CollectionItem>,
        includeImages: Boolean,
        onProgress: suspend (CollectionHtmlExportProgress) -> Unit,
    ): CollectionHtmlExportResult = exportNativeCollection(
        this, collectionTitle, items, includeImages, onProgress, requireNativeFileDelivery(fileDelivery),
    )

    override suspend fun ensureImageExportPermission(): ArticleExportPermission =
        requireNativeFileDelivery(fileDelivery).ensureImagePermission()

    override suspend fun exportPdf(content: DataHolder.Content, includeAppAttribution: Boolean): ArticleExportResult =
        exportNativeArticlePdf(content, includeAppAttribution, httpClient(), requireNativeFileDelivery(fileDelivery))

    override suspend fun exportHtml(content: DataHolder.Content, includeAppAttribution: Boolean): ArticleExportResult =
        exportNativeArticleHtml(content, includeAppAttribution, httpClient(), requireNativeFileDelivery(fileDelivery))

    override suspend fun exportImage(
        content: DataHolder.Content,
        includeAppAttribution: Boolean,
        extraSectionsHtml: String,
        includeComments: Boolean,
    ): ArticleExportResult = exportNativeArticleImage(
        content, includeAppAttribution, extraSectionsHtml, includeComments,
        httpClient(), requireNativeFileDelivery(fileDelivery),
    )
}
