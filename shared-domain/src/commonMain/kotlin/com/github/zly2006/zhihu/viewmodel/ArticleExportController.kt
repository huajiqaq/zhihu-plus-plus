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
import com.github.zly2006.zhihu.markdown.htmlToMdAst
import com.github.zly2006.zhihu.markdown.toMarkdown
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.platform.UserMessageSink
import com.github.zly2006.zhihu.platform.isArticleHtmlExportSupported
import com.github.zly2006.zhihu.platform.isArticleImageExportSupported
import com.github.zly2006.zhihu.platform.isJvm
import com.github.zly2006.zhihu.util.ArticleExportComment
import com.github.zly2006.zhihu.util.Log
import com.github.zly2006.zhihu.util.buildArticleExportCommentsHtml
import com.github.zly2006.zhihu.util.buildArticleExportFileName
import com.github.zly2006.zhihu.util.buildArticleExportHtml
import com.github.zly2006.zhihu.util.buildOfflineArticleExportHtml
import com.github.zly2006.zhihu.util.formatFullDateTime
import com.github.zly2006.zhihu.util.prepareArticleExportComment
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
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
            successMessage = "图片已保存到相册",
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
            successMessage = "带评论图片已保存到相册",
            onComplete = onComplete,
        )
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
            check(httpClient == null || environment.httpClient() === httpClient) { "账户已切换，请重新打开内容" }
            requireExportSourceContent()
        }.onFailure { error ->
            withContext(Dispatchers.Main) {
                userMessages.showShortMessage(error.message ?: "内容未加载完成")
                onComplete(false)
            }
            return
        }

        if (environment.requiresHtmlExportPermission() && !environment.hasImageExportPermission()) {
            withContext(Dispatchers.Main) {
                environment.requestImageExportPermission()
                onPermissionRequested()
                userMessages.showShortMessage("需要存储权限才能导出 HTML，正在请求权限")
                onComplete(false)
            }
            return
        }

        try {
            val htmlContent = withContext(Dispatchers.Default) {
                buildOfflineArticleExportHtml(
                    loadAssetText = environment::loadExportAssetText,
                    content = requireExportSourceContent(),
                    includeAppAttribution = includeAppAttribution,
                    httpClient = environment.httpClient(),
                    useOriginalOnImageFetchFailure = isJvm,
                )
            }
            val savedLocation = withContext(Dispatchers.Default) {
                environment.saveHtmlToDownloads(
                    displayName = buildArticleExportFileName(
                        content = requireExportSourceContent(),
                        extension = "html",
                    ),
                    htmlContent = htmlContent,
                )
            }
            withContext(Dispatchers.Main) {
                userMessages.showLongMessage("HTML 已保存到 $savedLocation")
                onComplete(true)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("ArticleViewModel", "HTML export failed", e)
            withContext(Dispatchers.Main) {
                userMessages.showShortMessage("HTML 导出失败: ${e.message}")
                onComplete(false)
            }
        }
    }

    private suspend fun exportToImageInternal(
        environment: ArticleExportContentEnvironment,
        includeComments: Boolean,
        commentCount: Int,
        includeAppAttribution: Boolean,
        successMessage: String,
        onComplete: (Boolean) -> Unit,
    ) {
        if (!isArticleImageExportSupported) {
            withContext(Dispatchers.Main) { onComplete(false) }
            return
        }
        runCatching {
            check(httpClient == null || environment.httpClient() === httpClient) { "账户已切换，请重新打开内容" }
            requireExportSourceContent()
        }.onFailure { error ->
            withContext(Dispatchers.Main) {
                userMessages.showShortMessage(error.message ?: "内容未加载完成")
                onComplete(false)
            }
            return
        }

        if (!environment.hasImageExportPermission()) {
            withContext(Dispatchers.Main) {
                environment.requestImageExportPermission()
                onPermissionRequested()
                userMessages.showShortMessage("需要存储权限才能导出图片，正在请求权限")
                onComplete(false)
            }
            return
        }

        var preparedWebView: PreparedArticleExportContent? = null
        var bitmap: Any? = null
        val renderer = environment.articleImageExportRenderer()
        var exportError: Throwable? = null
        try {
            try {
                val commentsHtml = if (includeComments && commentCount > 0) {
                    buildArticleExportCommentsHtml(
                        comments = fetchExportComments(environment, commentCount),
                        requestedCount = commentCount,
                    )
                } else {
                    ""
                }
                preparedWebView = renderer.prepareExportWebView(
                    htmlContent = buildArticleExportHtml(
                        loadAssetText = environment::loadExportAssetText,
                        content = requireExportSourceContent(),
                        includeAppAttribution = includeAppAttribution,
                        extraSectionsHtml = commentsHtml,
                    ),
                    timeoutMs = if (includeComments) 18_000L else 15_000L,
                )
                val capturedBitmap = renderer.captureExportBitmap(preparedWebView)
                bitmap = capturedBitmap
                withContext(Dispatchers.Default) {
                    environment.saveImageToMediaStore(
                        displayName = buildArticleExportFileName(
                            content = requireExportSourceContent(),
                            extension = "jpg",
                        ),
                        bitmap = capturedBitmap,
                    )
                }
                withContext(Dispatchers.Main) {
                    userMessages.showLongMessage(successMessage)
                    onComplete(true)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("ArticleViewModel", "Image export failed", e)
                val errorPrefix = if (includeComments) "带评论图片导出失败" else "图片导出失败"
                withContext(Dispatchers.Main) {
                    userMessages.showShortMessage("$errorPrefix: ${e.message}")
                    onComplete(false)
                }
            }
        } catch (error: Throwable) {
            exportError = error
            throw error
        } finally {
            withContext(NonCancellable) {
                val recycleError = runCatching { bitmap?.let { renderer.recycleExportBitmap(it) } }.exceptionOrNull()
                val destroyError = runCatching { preparedWebView?.let { renderer.destroyExportWebView(it) } }.exceptionOrNull()
                val primary = exportError ?: recycleError ?: destroyError
                for (error in arrayOf(recycleError, destroyError)) {
                    if (primary != null && error != null && error !== primary && primary.suppressedExceptions.none { it === error }) {
                        primary.addSuppressed(error)
                    }
                }
                if (exportError == null && primary != null) throw primary
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
        ?: throw IllegalStateException("内容未加载完成")

    fun convertToMarkdown(): String {
        val sb = StringBuilder()

        sb.append("# $title\n\n")

        sb.append("**作者**: $authorName\n\n")
        if (authorBio.isNotEmpty()) {
            sb.append("**简介**: $authorBio\n\n")
        }

        sb.append("---\n\n")
        sb.append(htmlToMdAst(content, noNativeBlock = true).toMarkdown())

        return sb.toString()
    }

    fun exportToClipboard(environment: ClipboardEnvironment) {
        val markdown = convertToMarkdown()

        environment.setPlainTextClipboard("Zhihu Article", markdown)

        userMessages.showShortMessage("文章已复制到剪贴板")
    }
}
