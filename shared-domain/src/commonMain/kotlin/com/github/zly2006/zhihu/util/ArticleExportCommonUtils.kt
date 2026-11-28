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

package com.github.zly2006.zhihu.util

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.safety.Cleaner
import com.fleeksoft.ksoup.safety.Safelist
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.i18n.AppLocale
import com.github.zly2006.zhihu.i18n.DomainStrings
import com.github.zly2006.zhihu.i18n.WidgetsStrings
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.isSuccess
import io.ktor.utils.io.readBuffer
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.io.readByteArray

const val ARTICLE_EXPORT_TEMPLATE_ASSET = "article_export_template.html"
const val ARTICLE_EXPORT_GITHUB_URL = "https://github.com/zly2006/zhihu-plus-plus"
private const val ARTICLE_EXPORT_IMAGE_FETCH_CONCURRENCY = 6
private const val ARTICLE_EXPORT_IMAGE_MAX_BYTES = 32 * 1024 * 1024
private const val ARTICLE_EXPORT_IMAGE_TIMEOUT_MS = 15_000L
private const val ARTICLE_EXPORT_IMAGE_MAX_REDIRECTS = 5
private const val ARTICLE_EXPORT_CONTENT_BASE_URL = "https://www.zhihu.com/"

/** 只用于不可信正文；模板及本地脚注脚本不经过清洗。 */
private fun articleExportSafelist(): Safelist = Safelist.relaxed()
    .addTags("div", "span", "u", "s", "del", "sub", "sup", "kbd", "figure", "figcaption")
    .addAttributes(":all", "id", "class", "title", "lang", "dir", "style", "data-pid")
    .addAttributes("sup", "data-draft-type", "data-numero", "data-text", "data-url")
    .addAttributes("img", "eeimg", "data-rawwidth", "data-rawheight", "loading")
    .addAttributes("pre", "lang", "data-language")
    .addAttributes("code", "data-language")
    .addAttributes("ol", "start")
    .addAttributes("li", "value")
    .addProtocols("a", "href", "#")
    .addProtocols("img", "src", "data")
    .preserveRelativeLinks(true)

data class ArticleExportFooterData(
    val exportEpochMillis: Long = Clock.System.now().toEpochMilliseconds(),
    val createdEpochSeconds: Long = 0L,
    val updatedEpochSeconds: Long = 0L,
    val includeAppAttribution: Boolean = true,
    val githubUrl: String = ARTICLE_EXPORT_GITHUB_URL,
)

data class ArticleExportData(
    val title: String,
    val authorName: String,
    val authorBio: String,
    val authorAvatarSrc: String,
    val voteUpCount: Int,
    val commentCount: Int,
    val content: String,
    val footerData: ArticleExportFooterData = ArticleExportFooterData(),
)

data class ArticleExportComment(
    val authorName: String,
    val contentHtml: String,
    val createdTimeText: String,
    val imageSrc: String = "",
)

fun prepareArticleExportComment(
    authorName: String,
    content: String,
    createdTimeText: String,
): ArticleExportComment {
    val document = Ksoup.parseBodyFragment(content)
    val imageSrc = document
        .selectFirst("a.comment_img, a.comment_gif, a.comment_sticker")
        ?.attr("href")
        ?.takeIf { it.isNotBlank() }
        ?.let { safeArticleExportUrl(it, image = true) }
        .orEmpty()

    document.select("a.comment_img, a.comment_gif, a.comment_sticker").remove()
    document.body().select("p, div").forEach { element ->
        if (element.text().isBlank() && element.select("img").isEmpty()) {
            element.remove()
        }
    }

    return ArticleExportComment(
        authorName = authorName,
        contentHtml = prepareArticleExportContentHtml(document.body().html()).trim(),
        createdTimeText = createdTimeText,
        imageSrc = imageSrc,
    )
}

fun buildArticleExportCommentsHtml(
    comments: List<ArticleExportComment>,
    requestedCount: Int? = null,
): String {
    if (comments.isEmpty()) return ""
    val heading = requestedCount
        ?.takeIf { it > 0 }
        ?.let { DomainStrings.exportTopCommentsCount(minOf(it, comments.size)) }
        ?: DomainStrings.exportTopComments

    return buildString {
        append("<div class='comments-title'>$heading</div>")
        comments.forEach { comment ->
            val imageHtml = safeArticleExportUrl(comment.imageSrc, image = true)
                ?.let {
                    """
                    <img
                        class="comment-image"
                        src="${escapeArticleExportHtml(it)}"
                        alt="${DomainStrings.exportCommentImage}"
                    />
                    """.trimIndent()
                }.orEmpty()
            append(
                """
                <div class="comment">
                    <div class="comment-author">${escapeArticleExportHtml(comment.authorName)}</div>
                    <div class="comment-content">${prepareArticleExportContentHtml(comment.contentHtml)}</div>
                    $imageHtml
                    <div class="comment-time">${escapeArticleExportHtml(comment.createdTimeText)}</div>
                </div>
                """.trimIndent(),
            )
        }
    }
}

fun buildArticleExportData(
    content: DataHolder.Content,
    includeAppAttribution: Boolean,
    exportEpochMillis: Long = Clock.System.now().toEpochMilliseconds(),
): ArticleExportData = when (content) {
    is DataHolder.Answer -> ArticleExportData(
        title = content.question.title,
        authorName = content.author.name,
        authorBio = content.author.headline,
        authorAvatarSrc = content.author.avatarUrl,
        voteUpCount = content.voteupCount,
        commentCount = content.commentCount,
        content = content.content,
        footerData = ArticleExportFooterData(
            exportEpochMillis = exportEpochMillis,
            createdEpochSeconds = content.createdTime,
            updatedEpochSeconds = content.updatedTime,
            includeAppAttribution = includeAppAttribution,
        ),
    )

    is DataHolder.Article -> ArticleExportData(
        title = content.title,
        authorName = content.author.name,
        authorBio = content.author.headline,
        authorAvatarSrc = content.author.avatarUrl,
        voteUpCount = content.voteupCount,
        commentCount = content.commentCount,
        content = content.content,
        footerData = ArticleExportFooterData(
            exportEpochMillis = exportEpochMillis,
            createdEpochSeconds = content.created,
            updatedEpochSeconds = content.updated,
            includeAppAttribution = includeAppAttribution,
        ),
    )

    else -> throw IllegalArgumentException("Unsupported export content type: ${content::class.simpleName}")
}

fun buildArticleExportHtml(
    loadAssetText: (String) -> String,
    exportData: ArticleExportData,
    extraSectionsHtml: String = "",
): String = renderArticleExportHtml(
    template = loadAssetText(ARTICLE_EXPORT_TEMPLATE_ASSET),
    exportData = exportData,
    extraSectionsHtml = extraSectionsHtml,
)

fun buildArticleExportHtml(
    loadAssetText: (String) -> String,
    content: DataHolder.Content,
    includeAppAttribution: Boolean,
    extraSectionsHtml: String = "",
): String = buildArticleExportHtml(
    loadAssetText = loadAssetText,
    exportData = buildArticleExportData(
        content = content,
        includeAppAttribution = includeAppAttribution,
    ),
    extraSectionsHtml = extraSectionsHtml,
)

suspend fun buildOfflineArticleExportHtml(
    loadAssetText: (String) -> String,
    exportData: ArticleExportData,
    httpClient: HttpClient,
    includeImages: Boolean = true,
    extraSectionsHtml: String = "",
    useOriginalOnImageFetchFailure: Boolean = false,
): String = inlineArticleExportImagesForOfflineHtml(
    html = buildArticleExportHtml(
        loadAssetText = loadAssetText,
        exportData = exportData,
        extraSectionsHtml = extraSectionsHtml,
    ),
    httpClient = httpClient,
    includeImages = includeImages,
    useOriginalOnImageFetchFailure = useOriginalOnImageFetchFailure,
)

suspend fun buildOfflineArticleExportHtml(
    loadAssetText: (String) -> String,
    content: DataHolder.Content,
    includeAppAttribution: Boolean,
    httpClient: HttpClient,
    includeImages: Boolean = true,
    extraSectionsHtml: String = "",
    useOriginalOnImageFetchFailure: Boolean = false,
): String = buildOfflineArticleExportHtml(
    loadAssetText = loadAssetText,
    exportData = buildArticleExportData(
        content = content,
        includeAppAttribution = includeAppAttribution,
    ),
    httpClient = httpClient,
    includeImages = includeImages,
    extraSectionsHtml = extraSectionsHtml,
    useOriginalOnImageFetchFailure = useOriginalOnImageFetchFailure,
)

fun renderArticleExportHtml(
    template: String,
    exportData: ArticleExportData,
    extraSectionsHtml: String = "",
): String {
    val bodyHtml = prepareArticleExportContentHtml(exportData.content)
    val extraHtml = prepareArticleExportContentHtml(extraSectionsHtml)
    if (template.isBlank()) {
        return bodyHtml + extraHtml
    }

    val footerPlaceholders = buildArticleExportFooterPlaceholders(exportData.footerData)
    val authorAvatarHtml = safeArticleExportUrl(exportData.authorAvatarSrc, image = true)
        ?.let {
            """
            <img
                class="author-avatar"
                src="${escapeArticleExportHtml(it)}"
                alt="${DomainStrings.exportAuthorAvatar}"
            />
            """.trimIndent()
        } ?: "<div class=\"author-avatar author-avatar-placeholder\"></div>"

    val authorBioHtml = exportData.authorBio
        .takeIf { it.isNotBlank() }
        ?.let { "<div class=\"author-bio\">${escapeArticleExportHtml(it)}</div>" }
        .orEmpty()

    val placeholders = mapOf(
        "{{languageTag}}" to AppLocale.languageTag,
        "{{title}}" to escapeArticleExportHtml(exportData.title),
        "{{authorAvatar}}" to authorAvatarHtml,
        "{{authorName}}" to escapeArticleExportHtml(exportData.authorName),
        "{{authorBio}}" to authorBioHtml,
        "{{voteCount}}" to exportData.voteUpCount.toString(),
        "{{commentCount}}" to exportData.commentCount.toString(),
        "{{bodyHtml}}" to bodyHtml,
        "{{extraSections}}" to extraHtml,
        "{{exportedDate}}" to footerPlaceholders.exportedDate,
        "{{publishedDate}}" to footerPlaceholders.publishedDate,
        "{{editedDate}}" to footerPlaceholders.editedDate,
        "{{editedDateClass}}" to footerPlaceholders.editedDateClass,
        "{{appAttributionClass}}" to footerPlaceholders.appAttributionClass,
        "{{githubUrl}}" to escapeArticleExportHtml(safeArticleExportUrl(footerPlaceholders.githubUrl).orEmpty()),
    )
    // 只替换模板原有占位符，正文里的同名文本不能被再次当成模板执行。
    return Regex("\\{\\{[A-Za-z]+}}").replace(template) { match -> placeholders[match.value] ?: match.value }
}

/** 借用传输引擎，不继承账号的 Cookie、认证、签名或请求插件；重定向逐跳校验。 */
suspend fun inlineArticleExportImagesForOfflineHtml(
    html: String,
    httpClient: HttpClient,
    includeImages: Boolean = true,
    useOriginalOnImageFetchFailure: Boolean = false,
    replaceFailedImagesWithPlaceholder: Boolean = false,
    maxTotalImageBytes: Long = Long.MAX_VALUE,
    validateImage: suspend (bytes: ByteArray, mimeType: String) -> Unit = { _, _ -> },
): String = HttpClient(httpClient.engine) {
    followRedirects = false
    install(HttpTimeout) { requestTimeoutMillis = ARTICLE_EXPORT_IMAGE_TIMEOUT_MS }
}.use { imageClient ->
    require(maxTotalImageBytes > 0)
    val budgetLock = Mutex()
    var totalImageBytes = 0L
    inlineArticleExportImagesInHtml(
        html = html,
        includeImages = includeImages,
        useOriginalOnImageFetchFailure = useOriginalOnImageFetchFailure,
        replaceFailedImagesWithPlaceholder = replaceFailedImagesWithPlaceholder,
    ) { imageUrl ->
        val image = downloadArticleExportImage(imageClient, imageUrl)
        budgetLock.withLock {
            totalImageBytes += image.bytes.size
            if (totalImageBytes > maxTotalImageBytes) throw ArticleExportBudgetExceeded()
        }
        validateImage(image.bytes, image.mimeType)
        "data:${image.mimeType};base64,${Base64.Default.encode(image.bytes)}"
    }
}

internal data class ArticleExportImageDownload(val bytes: ByteArray, val mimeType: String)

/** 资源预算不足不能降级为坏图占位，否则会把不完整导出误报为成功。 */
internal class ArticleExportBudgetExceeded : DisplayFailure({ DomainStrings.exportTooLarge })

/** 调用方提供无凭据且禁用自动重定向的客户端，共用图片导出协议与整链期限。 */
internal suspend fun downloadArticleExportImage(
    httpClient: HttpClient,
    imageUrl: String,
    maxBytes: Int = ARTICLE_EXPORT_IMAGE_MAX_BYTES,
): ArticleExportImageDownload {
    require(maxBytes in 1..ARTICLE_EXPORT_IMAGE_MAX_BYTES)
    var currentUrl = safeArticleExportUrl(imageUrl, image = true) ?: throw DisplayFailure { DomainStrings.exportImageUrlUnsupported }
    if (currentUrl.startsWith("data:", ignoreCase = true)) throw DisplayFailure { DomainStrings.exportImageInlineDownload }
    // 整条重定向链共用期限，不能每跳重新获得完整下载预算。
    return withTimeoutOrNull(ARTICLE_EXPORT_IMAGE_TIMEOUT_MS) {
        val visitedUrls = mutableSetOf<String>()
        var downloaded: ArticleExportImageDownload? = null
        while (downloaded == null) {
            if (!visitedUrls.add(currentUrl.substringBefore('#'))) throw DisplayFailure { DomainStrings.exportImageRedirectLoop }
            downloaded = httpClient.prepareGet(currentUrl).execute { response ->
                if (response.status.value in setOf(301, 302, 303, 307, 308)) {
                    if (visitedUrls.size > ARTICLE_EXPORT_IMAGE_MAX_REDIRECTS) throw DisplayFailure { DomainStrings.exportImageRedirectLimit }
                    currentUrl = safeArticleExportUrl(response.headers[HttpHeaders.Location].orEmpty(), image = true, baseUrl = currentUrl)
                        ?: throw DisplayFailure { DomainStrings.exportImageRedirectUnsupported }
                    if (currentUrl.startsWith("data:", ignoreCase = true)) throw DisplayFailure { DomainStrings.exportImageRedirectUnsupported }
                    null
                } else {
                    if (!response.status.isSuccess()) {
                        throw DisplayFailure { DomainStrings.downloadImageStatus(response.status.value) }
                    }
                    val bytes = response.bodyAsChannel().readBuffer(maxBytes.toLong() + 1).readByteArray()
                    if (bytes.size > maxBytes) throw DisplayFailure { DomainStrings.exportImageTooLarge }
                    if (bytes.isEmpty()) {
                        throw DisplayFailure { DomainStrings.emptyImage }
                    }
                    val mimeType = resolveArticleExportImageMimeType(
                        contentTypeHeader = response.headers[HttpHeaders.ContentType],
                        imageUrl = currentUrl,
                        imageBytes = bytes,
                    )
                    if (mimeType !in setOf("image/jpeg", "image/png", "image/gif", "image/webp", "image/bmp", "image/svg+xml", "image/avif", "image/heic", "image/heif")) {
                        throw DisplayFailure { DomainStrings.imageTypeUnsupported(mimeType) }
                    }
                    // SVG 仍只能作为 img 的 data 资源使用，不插入 DOM，也不以 iframe/object 打开。
                    ArticleExportImageDownload(bytes, mimeType)
                }
            }
        }
        downloaded
    } ?: throw DisplayFailure { DomainStrings.renderTimedOut }
}

fun resolveArticleExportImageMimeType(
    contentTypeHeader: String?,
    imageUrl: String,
    imageBytes: ByteArray,
): String {
    contentTypeHeader
        ?.substringBefore(';')
        ?.trim()
        ?.takeIf { it.startsWith("image/") }
        ?.let { return it }

    guessArticleExportImageMimeTypeFromName(imageUrl)?.let { return it }
    guessArticleExportImageMimeTypeFromBytes(imageBytes)?.let { return it }
    return "image/jpeg"
}

private fun guessArticleExportImageMimeTypeFromName(imageUrl: String): String? =
    imageUrl
        .substringBefore('?')
        .substringBefore('#')
        .substringAfterLast('.', missingDelimiterValue = "")
        .lowercase()
        .let { extension ->
            when (extension) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "gif" -> "image/gif"
                "webp" -> "image/webp"
                "bmp" -> "image/bmp"
                "svg", "svgz" -> "image/svg+xml"
                "avif" -> "image/avif"
                "heic" -> "image/heic"
                "heif" -> "image/heif"
                else -> null
            }
        }

private fun guessArticleExportImageMimeTypeFromBytes(imageBytes: ByteArray): String? {
    fun matches(vararg values: Int): Boolean =
        imageBytes.size >= values.size &&
            values.indices.all { index -> imageBytes[index].toInt() and 0xff == values[index] }

    return when {
        matches(0xff, 0xd8, 0xff) -> "image/jpeg"
        matches(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a) -> "image/png"
        matches(0x47, 0x49, 0x46, 0x38) -> "image/gif"
        matches(0x42, 0x4d) -> "image/bmp"
        imageBytes.size >= 12 &&
            matches(0x52, 0x49, 0x46, 0x46) &&
            imageBytes[8].toInt().toChar() == 'W' &&
            imageBytes[9].toInt().toChar() == 'E' &&
            imageBytes[10].toInt().toChar() == 'B' &&
            imageBytes[11].toInt().toChar() == 'P' -> "image/webp"
        else -> null
    }
}

fun prepareArticleExportContentHtml(content: String): String {
    val document = Ksoup.parseBodyFragment(content, ARTICLE_EXPORT_CONTENT_BASE_URL)
    document.select("script, style, noscript, template, form, input, button, textarea, select, link, meta, base, svg").remove()
    // 交互嵌入不能进入离线文档，保留可读的原链接与静态封面。
    document.select("iframe, video, audio, embed, object").forEach { embedded ->
        val source = embedded.attr("src").ifBlank { embedded.attr("data") }
            .ifBlank { embedded.selectFirst("source[src]")?.attr("src").orEmpty() }
        val link = safeArticleExportUrl(source)
        val poster = safeArticleExportUrl(embedded.attr("poster"), image = true)
        if (poster != null) embedded.before("<img src=\"${escapeArticleExportHtml(poster)}\">")
        if (link != null) {
            val label = embedded.attr("title").ifBlank { link }
            embedded.before("<p><a href=\"${escapeArticleExportHtml(link)}\">${escapeArticleExportHtml(label)}</a></p>")
        } else if (embedded.text().isNotBlank()) {
            embedded.before("<p>${escapeArticleExportHtml(embedded.text())}</p>")
        }
        embedded.remove()
    }
    document.select("img").forEach { image ->
        val src = extractImageUrl(image::attr)?.let { safeArticleExportUrl(it, image = true) }
        if (src == null) {
            image.remove()
        } else {
            image.attr("src", src)
            image.removeClass("lazy")
            image.attr("loading", "eager")
        }
    }
    for (element in document.select("[href], [cite], [data-url]")) {
        for (attribute in listOf("href", "cite", "data-url")) {
            if (!element.hasAttr(attribute)) continue
            val url = safeArticleExportUrl(element.attr(attribute))
            if (url == null) element.removeAttr(attribute) else element.attr(attribute, url)
        }
    }
    document.select("[style]").forEach { element ->
        val style = safeArticleExportStyle(element.attr("style"))
        if (style.isEmpty()) element.removeAttr("style") else element.attr("style", style)
    }
    return Cleaner(articleExportSafelist()).clean(document).apply {
        outputSettings().prettyPrint(false)
    }.body().html()
}

/** 相对地址沿用原来的知乎根基准；离线文档和 WebView 都不再借用知乎 origin。 */
private fun safeArticleExportUrl(raw: String, image: Boolean = false, baseUrl: String = ARTICLE_EXPORT_CONTENT_BASE_URL): String? {
    val value = raw.trim()
    if (value.isEmpty() || value.any { it.isISOControl() || it == '\\' }) return null
    if (!image && value.startsWith('#')) return value
    if (image && Regex("data:image/(?:png|jpeg|gif|webp|avif|bmp);base64,[A-Za-z0-9+/]+={0,2}", RegexOption.IGNORE_CASE).matches(value)) {
        return value
    }
    val absolute = Ksoup.parseBodyFragment("", baseUrl).body()
        .appendElement("a").attr("href", value).absUrl("href")
    if (absolute.isEmpty()) return null
    val url = runCatching { Url(absolute) }.getOrNull() ?: return null
    if (url.host.isBlank() || url.user != null || url.password != null ||
        (url.protocol != URLProtocol.HTTPS && (image || url.protocol != URLProtocol.HTTP))
    ) return null
    return url.toString()
}

/** 仅保留无 URL、无函数求值的排版和颜色，不能用 CSS 绕过资源校验。 */
private fun safeArticleExportStyle(style: String): String = style.split(';').mapNotNull { declaration ->
    val property = declaration.substringBefore(':').trim().lowercase()
    val value = declaration.substringAfter(':', "").trim().lowercase()
    val allowed = when (property) {
        "color", "background-color" -> Regex("(?:#[0-9a-f]{3}|#[0-9a-f]{4}|#[0-9a-f]{6}|#[0-9a-f]{8}|[a-z]+|rgba?\\([0-9.,% ]+\\))").matches(value)
        "text-align" -> value in setOf("left", "right", "center", "justify", "start", "end")
        "font-weight" -> value in setOf("normal", "bold", "bolder", "lighter") || Regex("[1-9]00").matches(value)
        "font-style" -> value in setOf("normal", "italic", "oblique")
        "text-decoration" -> value in setOf("none", "underline", "overline", "line-through")
        "white-space" -> value in setOf("normal", "pre", "pre-wrap", "pre-line", "break-spaces")
        "vertical-align" -> value in setOf("baseline", "sub", "super", "middle", "top", "bottom", "text-top", "text-bottom") ||
            Regex("-?[0-9]+(?:\\.[0-9]+)?(?:px|em|ex|rem|%)?").matches(value)
        "font-size", "line-height", "width", "height", "max-width" -> value in setOf("auto", "normal") ||
            Regex("[0-9]+(?:\\.[0-9]+)?(?:px|em|ex|rem|%)?").matches(value)
        else -> false
    }
    if (allowed) "$property:$value" else null
}.joinToString(";")

suspend fun inlineArticleExportImagesInHtml(
    html: String,
    includeImages: Boolean = true,
    useOriginalOnImageFetchFailure: Boolean = false,
    replaceFailedImagesWithPlaceholder: Boolean = false,
    resolveDataUrl: suspend (String) -> String,
): String {
    if (!includeImages) {
        return html
    }
    val document = Ksoup.parse(html)
    inlineArticleExportImages(
        imageNodes = document.select("img"),
        useOriginalOnImageFetchFailure = useOriginalOnImageFetchFailure,
        replaceFailedImagesWithPlaceholder = replaceFailedImagesWithPlaceholder,
        resolveDataUrl = resolveDataUrl,
    )
    return document.outerHtml()
}

suspend fun inlineArticleExportImages(
    imageNodes: Iterable<Element>,
    useOriginalOnImageFetchFailure: Boolean = false,
    replaceFailedImagesWithPlaceholder: Boolean = false,
    resolveDataUrl: suspend (String) -> String,
) {
    val imagesByUrl = linkedMapOf<String, MutableList<Element>>()
    imageNodes.forEach { image ->
        val source = extractImageUrl(image::attr) ?: return@forEach
        if (source.startsWith("data:", ignoreCase = true)) return@forEach
        val imageUrl = safeArticleExportUrl(source, image = true)
        if (imageUrl == null) {
            image.removeAttr("src")
            image.removeAttr("srcset")
            return@forEach
        }
        imagesByUrl.getOrPut(imageUrl) { mutableListOf() }.add(image)
    }
    if (imagesByUrl.isEmpty()) {
        return
    }

    val dataUrlsByUrl = coroutineScope {
        val semaphore = Semaphore(ARTICLE_EXPORT_IMAGE_FETCH_CONCURRENCY)
        imagesByUrl.keys
            .map { imageUrl ->
                async {
                    imageUrl to try {
                        semaphore.withPermit { resolveDataUrl(imageUrl) }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        if (error is ArticleExportBudgetExceeded) throw error
                        when {
                            replaceFailedImagesWithPlaceholder -> null
                            useOriginalOnImageFetchFailure -> imageUrl
                            else -> throw error
                        }
                    }
                }
            }.awaitAll()
            .toMap()
    }

    imagesByUrl.forEach { (imageUrl, images) ->
        val dataUrl = dataUrlsByUrl.getValue(imageUrl)
        images.forEach { image ->
            if (dataUrl == null) {
                image.replaceWith(
                    Element("span").addClass("export-image-placeholder").text(
                        "[${WidgetsStrings.imageLoadFailed}]" + image.attr("alt").takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty(),
                    ),
                )
            } else {
                image.attr("src", dataUrl)
                image.removeClass("lazy")
                image.removeAttr("srcset")
                image.removeAttr("sizes")
                image.attr("loading", "eager")
            }
        }
    }
}

fun buildArticleExportFileName(
    content: DataHolder.Content,
    extension: String,
): String {
    val timestamp = formatArticleExportFileTimestamp()
    val (title, authorName, typeLabel, typeKey, articleId) = when (content) {
        is DataHolder.Answer -> ExportFileMeta(
            title = content.question.title,
            authorName = content.author.name,
            typeLabel = DomainStrings.exportAnswer,
            typeKey = "answer",
            articleId = content.id,
        )
        is DataHolder.Article -> ExportFileMeta(
            title = content.title,
            authorName = content.author.name,
            typeLabel = DomainStrings.exportArticle,
            typeKey = "article",
            articleId = content.id,
        )
        else -> throw IllegalArgumentException("Unsupported export content type: ${content::class.simpleName}")
    }
    val safeTitle = sanitizeArticleExportFileNamePart(title).ifBlank { DomainStrings.untitled }
    val safeAuthorName = sanitizeArticleExportFileNamePart(authorName)
        .ifBlank { DomainStrings.anonymousAuthor }
    val normalizedExtension = extension.trimStart('.')

    val authorAndType = DomainStrings.exportAuthorType(safeAuthorName, typeLabel)
    return "zhihu++_${safeTitle}_${authorAndType}_${typeKey}_${articleId}_$timestamp.$normalizedExtension"
}

fun buildCollectionExportZipFileName(
    collectionTitle: String,
    timestampMillis: Long = Clock.System.now().toEpochMilliseconds(),
): String {
    val safeTitle = sanitizeArticleExportFileNamePart(collectionTitle).ifBlank { DomainStrings.collectionTitle }
    return "zhihu++_${safeTitle}_${formatArticleExportFileTimestamp(timestampMillis)}.zip"
}

fun sanitizeArticleExportFileNamePart(text: String): String = text
    .trim()
    .replace(Regex("\\s+"), "_")
    .replace(Regex("[\\\\/:*?\"<>|]"), "_")
    .replace(Regex("_+"), "_")
    .trim('_')

fun escapeArticleExportHtml(text: String): String = buildString(text.length) {
    text.forEach { char ->
        when (char) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&#x27;")
            else -> append(char)
        }
    }
}

private data class ArticleExportFooterPlaceholders(
    val exportedDate: String,
    val publishedDate: String,
    val editedDate: String,
    val editedDateClass: String,
    val appAttributionClass: String,
    val githubUrl: String,
)

private fun buildArticleExportFooterPlaceholders(footerData: ArticleExportFooterData): ArticleExportFooterPlaceholders {
    val publishedDate = footerData.createdEpochSeconds
        .takeIf { it > 0L }
        ?.let { DomainStrings.exportPublishedDate(formatArticleExportDate(it * 1000L)) }
        .orEmpty()
    val editedDate = footerData.updatedEpochSeconds
        .takeIf { it > 0L && it != footerData.createdEpochSeconds }
        ?.let { DomainStrings.exportEditedDate(formatArticleExportDate(it * 1000L)) }
        .orEmpty()

    return ArticleExportFooterPlaceholders(
        exportedDate = DomainStrings.exportExportedDate(formatArticleExportDate(footerData.exportEpochMillis)),
        publishedDate = publishedDate,
        editedDate = editedDate,
        editedDateClass = if (editedDate.isBlank()) "export-footer-line is-hidden" else "export-footer-line",
        appAttributionClass = if (footerData.includeAppAttribution) "export-credit" else "export-credit is-hidden",
        githubUrl = footerData.githubUrl,
    )
}

private fun formatArticleExportFileTimestamp(timestampMillis: Long = Clock.System.now().toEpochMilliseconds()): String {
    val dateTime = Instant
        .fromEpochMilliseconds(timestampMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    return buildString {
        append(dateTime.year.toString().padStart(4, '0'))
        append((dateTime.month.ordinal + 1).twoDigitString())
        append(dateTime.day.twoDigitString())
        append('_')
        append(dateTime.hour.twoDigitString())
        append(dateTime.minute.twoDigitString())
        append(dateTime.second.twoDigitString())
    }
}

fun formatArticleExportDate(epochMillis: Long): String {
    val dateTime = Instant
        .fromEpochMilliseconds(epochMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    return buildString {
        append(dateTime.year.toString().padStart(4, '0'))
        append('-')
        append((dateTime.month.ordinal + 1).twoDigitString())
        append('-')
        append(dateTime.day.twoDigitString())
        append(' ')
        append(dateTime.hour.twoDigitString())
        append(':')
        append(dateTime.minute.twoDigitString())
    }
}

private data class ExportFileMeta(
    val title: String,
    val authorName: String,
    val typeLabel: String,
    val typeKey: String,
    val articleId: Long,
)
