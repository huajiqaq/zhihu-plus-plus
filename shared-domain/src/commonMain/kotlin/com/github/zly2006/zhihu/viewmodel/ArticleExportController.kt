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

import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.decodeZhihuCommentData
import com.github.zly2006.zhihu.i18n.DomainStrings
import com.github.zly2006.zhihu.markdown.htmlToMdAst
import com.github.zly2006.zhihu.markdown.toMarkdown
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.platform.UserMessageSink
import com.github.zly2006.zhihu.platform.isArticleHtmlExportSupported
import com.github.zly2006.zhihu.platform.isArticleImageExportSupported
import com.github.zly2006.zhihu.platform.isArticlePdfExportSupported
import com.github.zly2006.zhihu.util.AppLog
import com.github.zly2006.zhihu.util.ArticleExportComment
import com.github.zly2006.zhihu.util.buildArticleExportCommentsHtml
import com.github.zly2006.zhihu.util.formatFullDateTime
import com.github.zly2006.zhihu.util.prepareArticleExportComment
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class ArticleExportController(
    private val article: Article,
    private val httpClient: HttpClient?,
    private val userMessages: UserMessageSink,
    private val exportSourceContent: () -> DataHolder.Content?,
    private val onPermissionRequested: () -> Unit,
    private val titleValue: () -> String,
    private val contentValue: () -> String,
    private val authorNameValue: () -> String,
    private val authorBioValue: () -> String,
) {
    private val title: String
        get() = titleValue()
    private val content: String
        get() = contentValue()
    private val authorName: String
        get() = authorNameValue()
    private val authorBio: String
        get() = authorBioValue()

    suspend fun exportToImage(
        environment: ArticleExportContentEnvironment,
        includeAppAttribution: Boolean,
        onComplete: (Boolean) -> Unit,
    ) {
        exportToImageInternal(
            environment = environment,
            includeComments = false,
            commentCount = 0,
            includeAppAttribution = includeAppAttribution,
            onComplete = onComplete,
        )
    }

    suspend fun exportToImageWithComments(
        environment: ArticleExportContentEnvironment,
        commentCount: Int,
        includeAppAttribution: Boolean,
        onComplete: (Boolean) -> Unit,
    ) {
        exportToImageInternal(
            environment = environment,
            includeComments = true,
            commentCount = commentCount,
            includeAppAttribution = includeAppAttribution,
            onComplete = onComplete,
        )
    }

    suspend fun exportToPdf(
        environment: ArticleExportContentEnvironment,
        includeAppAttribution: Boolean,
        onComplete: (Boolean) -> Unit,
    ) {
        if (!isArticlePdfExportSupported) {
            withContext(Dispatchers.Main) { onComplete(false) }
            return
        }
        try {
            withExportAccount(environment, httpClient) {
                val source = requireExportSourceContent()
                // 系统打印/Files 均由系统授权目标，不申请照片库或旧公共目录权限。
                ensureExportAccountActive()
                val result = environment.exportPdf(source, includeAppAttribution)
                completeExport(result, onComplete)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            AppLog.e("ArticleViewModel", "PDF export failed", error)
            withContext(Dispatchers.Main) {
                userMessages.showShortMessage(DomainStrings.pdfExportFailed(error.message.orEmpty()))
                onComplete(false)
            }
        }
    }

    suspend fun exportToHtml(
        environment: ArticleExportContentEnvironment,
        includeAppAttribution: Boolean,
        onComplete: (Boolean) -> Unit,
    ) {
        if (!isArticleHtmlExportSupported) {
            withContext(Dispatchers.Main) { onComplete(false) }
            return
        }
        runCatching {
            check(httpClient == null || environment.httpClient() === httpClient) { DomainStrings.accountChangedReopen }
            requireExportSourceContent()
        }.onFailure { error ->
            withContext(Dispatchers.Main) {
                userMessages.showShortMessage(error.message ?: DomainStrings.contentNotReady)
                onComplete(false)
            }
            return
        }

        try {
            withExportAccount(environment, httpClient) {
                if (environment.requiresHtmlExportPermission() && !ensureExportPermission(environment, DomainStrings.htmlPermission, onComplete)) return@withExportAccount
                ensureExportAccountActive()
                val result = withContext(Dispatchers.Default) {
                    environment.exportHtml(requireExportSourceContent(), includeAppAttribution)
                }
                completeExport(result, onComplete)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.e("ArticleViewModel", "HTML export failed", e)
            withContext(Dispatchers.Main) {
                userMessages.showShortMessage(DomainStrings.htmlExportFailed(e.message.orEmpty()))
                onComplete(false)
            }
        }
    }

    private suspend fun exportToImageInternal(
        environment: ArticleExportContentEnvironment,
        includeComments: Boolean,
        commentCount: Int,
        includeAppAttribution: Boolean,
        onComplete: (Boolean) -> Unit,
    ) {
        if (!isArticleImageExportSupported) {
            withContext(Dispatchers.Main) { onComplete(false) }
            return
        }
        runCatching {
            check(httpClient == null || environment.httpClient() === httpClient) { DomainStrings.accountChangedReopen }
            requireExportSourceContent()
        }.onFailure { error ->
            withContext(Dispatchers.Main) {
                userMessages.showShortMessage(error.message ?: DomainStrings.contentNotReady)
                onComplete(false)
            }
            return
        }

        try {
            withExportAccount(environment, httpClient) {
                if (!ensureExportPermission(environment, DomainStrings.imagePermission, onComplete)) return@withExportAccount
                ensureExportAccountActive()
                val commentsHtml = if (includeComments && commentCount > 0) {
                    buildArticleExportCommentsHtml(
                        comments = fetchExportComments(environment, commentCount),
                        requestedCount = commentCount,
                    )
                } else {
                    ""
                }
                ensureExportAccountActive()
                val result = environment.exportImage(
                    content = requireExportSourceContent(),
                    includeAppAttribution = includeAppAttribution,
                    extraSectionsHtml = commentsHtml,
                    includeComments = includeComments,
                )
                completeExport(result, onComplete)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.e("ArticleViewModel", "Image export failed", e)
            withContext(Dispatchers.Main) {
                userMessages.showShortMessage(
                    if (includeComments) DomainStrings.imageCommentsExportFailed(e.message.orEmpty()) else DomainStrings.imageExportFailed(e.message.orEmpty()),
                )
                onComplete(false)
            }
        }
    }

    private suspend fun ensureExportPermission(
        environment: ArticleExportEnvironment,
        permissionMessage: String,
        onComplete: (Boolean) -> Unit,
    ): Boolean {
        val permission = environment.ensureImageExportPermission()
        if (permission == ArticleExportPermission.Granted) return true
        withContext(Dispatchers.Main) {
            if (permission == ArticleExportPermission.RequestStarted) {
                onPermissionRequested()
                userMessages.showShortMessage(permissionMessage)
            } else {
                userMessages.showShortMessage(DomainStrings.photosPermissionDenied)
            }
            onComplete(false)
        }
        return false
    }

    private suspend fun completeExport(result: ArticleExportResult, onComplete: (Boolean) -> Unit) {
        withContext(Dispatchers.Main) {
            when (result) {
                is ArticleExportResult.Completed -> {
                    userMessages.showLongMessage(result.completionMessage)
                    onComplete(true)
                }
                ArticleExportResult.Cancelled -> onComplete(false)
            }
        }
    }

    private suspend fun fetchExportComments(
        environment: ArticleExportContentEnvironment,
        requestedCount: Int,
    ): List<ArticleExportComment> {
        val safeRequestedCount = requestedCount.coerceAtLeast(0)
        if (safeRequestedCount == 0) return emptyList()

        val url = when (article.type) {
            ArticleType.Answer -> "https://www.zhihu.com/api/v4/comment_v5/answers/${article.id}/root_comment"
            ArticleType.Article -> "https://www.zhihu.com/api/v4/comment_v5/articles/${article.id}/root_comment"
        }
        val json = environment.fetchJson(
            url = "$url?order=score&limit=${safeRequestedCount.coerceAtMost(20)}",
            include = "data[*].content,excerpt,headline",
        ) ?: return emptyList()
        return decodeZhihuCommentData(json, safeRequestedCount)
            .map { comment ->
                prepareArticleExportComment(
                    authorName = comment.author.name,
                    content = comment.content,
                    createdTimeText = formatFullDateTime(comment.createdTime).dropLast(3),
                )
            }
    }

    private fun requireExportSourceContent(): DataHolder.Content = exportSourceContent()
        ?: throw IllegalStateException(DomainStrings.contentNotReady)

    fun convertToMarkdown(): String {
        val sb = StringBuilder()

        sb.append("# $title\n\n")

        sb.append(DomainStrings.markdownAuthor(authorName)).append("\n\n")
        if (authorBio.isNotEmpty()) {
            sb.append(DomainStrings.markdownBio(authorBio)).append("\n\n")
        }

        sb.append("---\n\n")
        sb.append(htmlToMdAst(content, noNativeBlock = true).toMarkdown())

        return sb.toString()
    }

    fun exportToClipboard(environment: ClipboardEnvironment) {
        val markdown = convertToMarkdown()

        environment.setPlainTextClipboard(DomainStrings.articleClipboard, markdown)

        userMessages.showShortMessage(DomainStrings.articleCopied)
    }
}
