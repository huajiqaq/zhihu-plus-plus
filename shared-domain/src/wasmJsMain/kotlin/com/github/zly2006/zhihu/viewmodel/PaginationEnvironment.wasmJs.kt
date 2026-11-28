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

@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.github.zly2006.zhihu.viewmodel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.github.zly2006.zhihu.account.ZhihuAccountStore
import com.github.zly2006.zhihu.account.rememberZhihuAccountStore
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.i18n.DomainStrings
import com.github.zly2006.zhihu.notification.NotificationSettingsStore
import com.github.zly2006.zhihu.notification.rememberNotificationSettingsStore
import com.github.zly2006.zhihu.platform.PlainTextClipboard
import com.github.zly2006.zhihu.platform.UserMessageSink
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.viewmodel.filter.FilterableContent
import com.github.zly2006.zhihu.platform.readWebExportResource
import com.github.zly2006.zhihu.platform.rememberPlainTextClipboard
import com.github.zly2006.zhihu.util.ARTICLE_EXPORT_TEMPLATE_ASSET
import com.github.zly2006.zhihu.util.buildArticleExportFileName
import com.github.zly2006.zhihu.util.buildArticleExportHtml
import com.github.zly2006.zhihu.util.inlineArticleExportImagesInHtml
import kotlinx.coroutines.CancellationException

@Composable
actual fun rememberPaginationEnvironment(allowGuestAccess: Boolean): PaginationEnvironment {
    val store = rememberZhihuAccountStore()
    val notifications = rememberNotificationSettingsStore()
    val clipboard = rememberPlainTextClipboard()
    val messages = rememberUserMessageSink()
    return remember(store, notifications, clipboard, allowGuestAccess, messages) {
        WebPaginationEnvironment(notifications, store, allowGuestAccess, clipboard, messages)
    }
}

private class WebPaginationEnvironment(
    notifications: NotificationSettingsStore,
    accountStore: ZhihuAccountStore,
    allowGuestAccess: Boolean,
    private val clipboard: PlainTextClipboard,
    private val userMessages: UserMessageSink,
) : StoredPaginationEnvironment(notifications, accountStore, allowGuestAccess) {
    override fun showNlpBlockedMessage(blocked: Collection<FilterableContent>) {
        if (blocked.isNotEmpty()) userMessages.showShortMessage(DomainStrings.semanticItemsBlocked(blocked.size, blocked.first().title.take(10)))
    }

    override fun setPlainTextClipboard(label: String, text: String) = clipboard(label, text)

    override suspend fun ensureImageExportPermission(): ArticleExportPermission = ArticleExportPermission.Granted

    override suspend fun exportPdf(content: DataHolder.Content, includeAppAttribution: Boolean): ArticleExportResult =
        exportWebArticlePdf(content, includeAppAttribution)

    override suspend fun exportImage(
        content: DataHolder.Content,
        includeAppAttribution: Boolean,
        extraSectionsHtml: String,
        includeComments: Boolean,
    ): ArticleExportResult = exportWebArticleImage(content, includeAppAttribution, extraSectionsHtml)

    override suspend fun exportCollectionItemsToHtmlZip(
        collectionTitle: String,
        items: List<CollectionItem>,
        includeImages: Boolean,
        onProgress: suspend (CollectionHtmlExportProgress) -> Unit,
    ): CollectionHtmlExportResult = exportWebCollectionZip(collectionTitle, items, includeImages, onProgress)

    override suspend fun exportHtml(content: DataHolder.Content, includeAppAttribution: Boolean): ArticleExportResult {
        val (html, linkedImages) = buildWebExportHtml(loadWebExportTemplate(), content, includeAppAttribution, includeImages = true)
        val fileName = buildArticleExportFileName(content, "html")
        ensureExportAccountActive()
        check(downloadHtml(fileName, html)) { DomainStrings.cannotCreateDownloadFile }
        return ArticleExportResult.Completed(
            if (linkedImages == 0) {
                DomainStrings.webHtmlDownloadRequested(fileName)
            } else {
                DomainStrings.webHtmlDownloadWithLinkedImages(fileName, linkedImages)
            },
        )
    }
}

internal suspend fun loadWebExportTemplate(): String = readWebExportResource(webExportAssetUrl(ARTICLE_EXPORT_TEMPLATE_ASSET)).also { template ->
    check("{{bodyHtml}}" in template && "{{title}}" in template) { DomainStrings.missingExportAsset(ARTICLE_EXPORT_TEMPLATE_ASSET) }
}

@JsFun("(path) => new URL(path, window.zhihuAppResourceBase || new URL('./', location.href)).href")
private external fun webExportAssetUrl(path: String): String

/** 单篇和收藏夹共用同一安全模板、匿名图片读取及仍需联网的图片计数。 */
internal suspend fun buildWebExportHtml(
    template: String,
    content: DataHolder.Content,
    includeAppAttribution: Boolean,
    includeImages: Boolean,
): Pair<String, Int> {
    var linkedImages = 0
    var inlinedCharacters = 0L
    val html = inlineArticleExportImagesInHtml(
        html = buildArticleExportHtml({ template }, content, includeAppAttribution),
        includeImages = includeImages,
        useOriginalOnImageFetchFailure = true,
    ) { url ->
        try {
            val data = readWebExportResource(url, image = true)
            // 达到单文档内联预算的图片保留链接，并计入交付警告，不裁切正文或伪称可完全离线查看。
            check(inlinedCharacters + data.length <= 64L * 1024 * 1024)
            inlinedCharacters += data.length
            data
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            linkedImages++
            throw error
        }
    }
    return html to linkedImages
}

@JsFun(
    """(name, html) => {
        let url, link;
        try {
            url = URL.createObjectURL(new Blob([html], {type: 'text/html;charset=utf-8'}));
            link = document.createElement('a');
            link.href = url;
            link.download = name;
            link.hidden = true;
            document.body.appendChild(link);
            link.click();
            return true;
        } catch { return false; }
        finally {
            if (link) link.remove();
            if (url) setTimeout(() => URL.revokeObjectURL(url), 60000);
        }
    }""",
)
private external fun downloadHtml(name: String, html: String): Boolean
