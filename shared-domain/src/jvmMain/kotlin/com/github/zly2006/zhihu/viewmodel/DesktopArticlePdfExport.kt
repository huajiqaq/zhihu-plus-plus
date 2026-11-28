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

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.DataNode
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.i18n.DomainStrings
import com.github.zly2006.zhihu.util.ArticleExportBudgetExceeded
import com.github.zly2006.zhihu.util.buildArticleExportFileName
import com.github.zly2006.zhihu.util.buildArticleExportHtml
import com.github.zly2006.zhihu.util.inlineArticleExportImagesForOfflineHtml
import com.openhtmltopdf.extend.FSSupplier
import com.openhtmltopdf.extend.FSTextBreaker
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FSFontUseCase
import com.openhtmltopdf.outputdevice.helper.ExternalResourceControlPriority
import com.openhtmltopdf.outputdevice.helper.ExternalResourceType
import com.openhtmltopdf.pdfboxout.PDFontSupplier
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import com.openhtmltopdf.render.Box
import com.openhtmltopdf.svgsupport.BatikSVGDrawer
import java.io.ByteArrayInputStream
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.StringReader
import java.io.StringWriter
import java.text.BreakIterator
import java.util.Base64
import java.util.EnumSet
import java.util.Locale
import java.util.logging.Level
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.jsoup.Jsoup
import org.jsoup.helper.W3CDom
import org.xml.sax.InputSource
import org.xml.sax.SAXParseException
import org.w3c.dom.Element as XmlElement

private const val PDF_FONT_FAMILY = "Zhihu PDF SC"
private val desktopPdfExportMutex = Mutex()

/** PDF 使用独立的分页排版器；正文仍是文字和链接，不走长图包裹或系统打印机。 */
internal suspend fun ArticleExportContentEnvironment.exportDesktopPdf(
    content: DataHolder.Content,
    includeAppAttribution: Boolean,
): ArticleExportResult = desktopPdfExportMutex.withLock {
    withContext(Dispatchers.IO) {
        val html = inlineArticleExportImagesForOfflineHtml(
            html = buildArticleExportHtml(::loadExportAssetText, content, includeAppAttribution),
            httpClient = httpClient(),
            replaceFailedImagesWithPlaceholder = true,
            maxTotalImageBytes = 128L * 1024 * 1024,
        )
        val document = Ksoup.parse(html)
        preparePdfDocument(document)
        val imageMessage = prepareDesktopExportImages(document)
        val allowedImages = document.select("img[src]").map { it.attr("src") }.toSet()
        check(allowedImages.all { it.startsWith("data:image/") }) { DomainStrings.exportRenderFailed }
        val fonts = listOf(
            400 to readPdfFont("NotoSansSC-Regular.ttf"),
            700 to readPdfFont("NotoSansSC-Bold.ttf"),
        )
        val context = currentCoroutineContext()
        val file = writeDesktopExport(buildArticleExportFileName(content, "pdf")) { temporary ->
            context.ensureActive()
            PDDocument().use { pdf ->
                val pdfFonts = fonts.map { (weight, bytes) ->
                    weight to ByteArrayInputStream(bytes).use { input -> PDType0Font.load(pdf, input, true) }
                }
                val visibleText = document.body().text()
                validatePdfGlyphs(visibleText, pdfFonts.map { it.second })
                val w3c = W3CDom.convert(Jsoup.parse(document.outerHtml()))
                var renderFailure = false
                val builder = PdfRendererBuilder()
                    .withW3cDocument(w3c, "about:blank")
                    .usePDDocument(pdf)
                    .useStandard14Fonts(false)
                    .useSVGDrawer(BatikSVGDrawer())
                    .useUnicodeLineBreaker(DesktopPdfLineBreaker())
                    .withDiagnosticConsumer { diagnostic ->
                        context.ensureActive()
                        if (diagnostic.hasError() || diagnostic.level.intValue() >= Level.WARNING.intValue()) renderFailure = true
                    }
                for (priority in ExternalResourceControlPriority.values()) {
                    builder.useExternalResourceAccessControl({ uri, type ->
                        context.ensureActive()
                        type == ExternalResourceType.IMAGE_RASTER && uri in allowedImages
                    }, priority)
                }
                pdfFonts.forEach { (weight, font) ->
                    builder.useFont(PDFontSupplier(font), PDF_FONT_FAMILY, weight, FontStyle.NORMAL, true)
                }
                fonts.forEach { (weight, bytes) ->
                    builder.useFont(
                        FSSupplier<InputStream> { ByteArrayInputStream(bytes) }, PDF_FONT_FAMILY, weight, FontStyle.NORMAL, true,
                        EnumSet.of(FSFontUseCase.SVG),
                    )
                }
                temporary.outputStream().buffered().use { output ->
                    val cancellableOutput = object : FilterOutputStream(output) {
                        override fun write(value: Int) {
                            context.ensureActive()
                            out.write(value)
                        }

                        override fun write(bytes: ByteArray, offset: Int, length: Int) {
                            context.ensureActive()
                            out.write(bytes, offset, length)
                        }
                    }
                    builder.toStream(cancellableOutput)
                    builder.buildPdfRenderer().use { renderer ->
                        context.ensureActive()
                        renderer.layout()
                        context.ensureActive()
                        check(!renderFailure) { DomainStrings.exportRenderFailed }
                        val root = renderer.rootBox
                        check(root.layer.pages.isNotEmpty()) { DomainStrings.renderEmpty }
                        val pageWidth = root.layer.pages.first().getContentWidth(renderer.sharedContext.newLayoutContextInstance())
                        checkPdfWidth(root, pageWidth, context::ensureActive)
                        renderer.createPDFWithoutClosing()
                        context.ensureActive()
                        check(!renderFailure && pdf.numberOfPages > 0) { DomainStrings.exportRenderFailed }
                        pdf.documentInformation.title = document.selectFirst(".title")?.text().orEmpty()
                        pdf.documentInformation.author = document.selectFirst(".author-name")?.text().orEmpty()
                        pdf.documentInformation.creator = "Zhihu++"
                        ensureExportAccountActive()
                        pdf.save(cancellableOutput)
                    }
                }
            }
        }
        ArticleExportResult.Completed(
            DomainStrings.pdfSavedTo(file.absolutePath) + imageMessage.takeIf(String::isNotBlank)?.let { "\n$it" }.orEmpty(),
        )
    }
}

private fun readPdfFont(name: String): ByteArray = checkNotNull(
    Thread.currentThread().contextClassLoader.getResourceAsStream("pdf-fonts/$name"),
) { DomainStrings.pdfFontUnavailable }.use { input ->
    val bytes = input.readNBytes(24 * 1024 * 1024 + 1)
    check(bytes.size in 12..24 * 1024 * 1024 && bytes[0] == 0.toByte() && bytes[1] == 1.toByte()) {
        DomainStrings.pdfFontUnavailable
    }
    bytes
}

private fun validatePdfGlyphs(text: String, fonts: List<PDType0Font>) {
    text.codePoints().distinct().forEach { codePoint ->
        if (Character.isWhitespace(codePoint) || Character.getType(codePoint) == Character.FORMAT.toInt()) return@forEach
        val character = String(Character.toChars(codePoint))
        val available = fonts.all { font ->
            try {
                font.encode(character)
                true
            } catch (_: IllegalArgumentException) {
                false
            }
        }
        check(available) { DomainStrings.pdfFontMissingGlyph("U+${codePoint.toString(16).uppercase().padStart(4, '0')}") }
    }
}

private fun checkPdfWidth(root: Box, pageWidth: Int, ensureActive: () -> Unit) {
    val pending = ArrayDeque<Box>()
    pending.add(root)
    while (pending.isNotEmpty()) {
        ensureActive()
        val box = pending.removeLast()
        check(box.absX >= -1 && box.absX.toLong() + box.width <= pageWidth + 1L) { DomainStrings.exportRenderFailed }
        pending.addAll(box.children)
    }
}

private class DesktopPdfLineBreaker : FSTextBreaker {
    private val iterator = BreakIterator.getLineInstance(Locale.SIMPLIFIED_CHINESE)
    override fun next(): Int = iterator.next()
    override fun setText(newText: String) = iterator.setText(newText)
}

private fun preparePdfDocument(document: Document) {
    document.select("script, style, link, meta[http-equiv], base").remove()
    document.select(".is-hidden").remove()
    val chinese = document.selectFirst("html")?.attr("lang").orEmpty().startsWith("zh", ignoreCase = true)
    document.select(if (chinese) ".export-credit [lang=en]" else ".export-credit [lang=zh-CN]").remove()
    document.select("[style]").forEach { element ->
        // 原正文的安全颜色/字阶保留；页宽和分页由 PDF 样式掌管，不能继承手机固定尺寸。
        val retained = element.attr("style").split(';').filter { declaration ->
            val property = declaration.substringBefore(':').trim().lowercase()
            val value = declaration.substringAfter(':', "").trim().lowercase()
            property in setOf("color", "background-color", "font-size", "font-weight", "font-style", "text-decoration", "text-align", "vertical-align", "line-height") &&
                !value.contains("rgba") && !value.contains("var(")
        }.joinToString(";")
        if (retained.isBlank()) element.removeAttr("style") else element.attr("style", retained)
    }
    document.select("table, td, th").forEach { it.removeAttr("width"); it.removeAttr("height") }
    appendPdfFootnotes(document, chinese)
    // 正文清洗已移除内联 SVG，这里只剩可信模板图标；不能吞掉模板自身的绘制错误。
    document.select("svg").toList().forEach { svg -> svg.before(safePdfSvg(svg.outerHtml())); svg.remove() }
    document.select("img[src]").toList().forEach { image ->
        val formula = image.hasAttr("eeimg") || image.hasClass("ee_img")
        val alt = image.attr("alt").trim()
        if (formula && alt.isNotEmpty()) image.after(Element("span").addClass("formula-source").text(alt))
        val src = image.attr("src")
        if (src.startsWith("data:image/svg+xml;base64,")) {
            val encoded = src.substringAfter(',')
            if (encoded.length > 8 * 1024 * 1024) throw ArticleExportBudgetExceeded()
            try {
                val bytes = try {
                    Base64.getDecoder().decode(encoded)
                } catch (_: IllegalArgumentException) {
                    throw UnsupportedPdfSvg()
                }
                image.before(safePdfSvg(bytes.toString(Charsets.UTF_8)))
                image.remove()
            } catch (error: Exception) {
                if (error !is UnsupportedPdfSvg && error !is SAXParseException) throw error
                image.replaceWith(
                    Element("span").addClass("export-image-placeholder").text(
                        "[${DomainStrings.desktopExportImageUnsupported}]" + alt.takeIf(String::isNotBlank)?.let { " $it" }.orEmpty(),
                    ),
                )
            }
        }
    }
    document.head().appendElement("style").appendChild(DataNode(PDF_EXPORT_CSS))
}

private fun appendPdfFootnotes(document: Document, chinese: Boolean) {
    val references = document.select("sup[data-draft-type=reference]")
    if (references.isEmpty()) return
    val section = Element("section").attr("id", "zhihu-plus-footnotes")
    section.appendElement("h2").text(if (chinese) "参考资料" else "References")
    val list = section.appendElement("ol")
    references.forEachIndexed { index, reference ->
        val id = "pdf-reference-${index + 1}"
        val number = reference.attr("data-numero").ifBlank { (index + 1).toString() }
        val text = reference.attr("data-text")
        val url = reference.attr("data-url")
        reference.attr("id", "$id-back").empty().appendElement("a").attr("href", "#$id").text(number)
        val item = list.appendElement("li").attr("id", id)
        item.appendElement("a").attr("href", "#$id-back").text("[$number] ")
        if (url.isNotBlank()) item.appendElement("a").attr("href", url).text(text.ifBlank { url }) else item.appendText(text)
    }
    (document.selectFirst(".content") ?: document.body()).appendChild(section)
}

private class UnsupportedPdfSvg : IllegalArgumentException(DomainStrings.desktopExportImageUnsupported)

/** SVG 只允许无脚本、无外部引用的绘图子集；XML 本身也不能解析实体或 DTD。 */
private fun safePdfSvg(source: String): String {
    if (source.length > 8 * 1024 * 1024) throw ArticleExportBudgetExceeded()
    val factory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        isXIncludeAware = false
        isExpandEntityReferences = false
        setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
        setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
    }
    val xml = factory.newDocumentBuilder().parse(InputSource(StringReader(source)))
    val root = xml.documentElement
    if (root.localName != "svg") throw UnsupportedPdfSvg()
    val elements = root.getElementsByTagName("*")
    if (elements.length > 20_000) throw ArticleExportBudgetExceeded()
    val allowedTags = setOf("svg", "g", "path", "rect", "circle", "ellipse", "line", "polyline", "polygon", "defs", "use", "text", "tspan", "title", "desc", "clipPath")
    val names = HashMap<String, String>()
    for (index in -1 until elements.length) {
        val element = if (index < 0) root else elements.item(index) as XmlElement
        if (element.localName !in allowedTags) throw UnsupportedPdfSvg()
        if (element.hasAttribute("id") && names.put(element.getAttribute("id"), element.localName) != null) throw UnsupportedPdfSvg()
        if (element.hasAttribute("style")) {
            val declarations = element.getAttribute("style").split(';').filter(String::isNotBlank)
            if (!declarations.all {
                    val property = it.substringBefore(':').trim().lowercase()
                    val value = it.substringAfter(':', "").trim()
                    property in setOf("vertical-align", "font-size") && Regex("-?[0-9]+(?:\\.[0-9]+)?(?:px|pt|em|ex|%)?").matches(value)
                }
            ) throw UnsupportedPdfSvg()
            // 数学 SVG 常把基线写在外层 style；保留尺寸字阶，但不把任意 CSS 交给 Batik。
            declarations.forEach {
                if (it.substringBefore(':').trim() == "font-size") element.setAttribute("font-size", it.substringAfter(':').trim())
            }
            element.removeAttribute("style")
        }
        if (element.localName in setOf("text", "tspan")) element.setAttribute("font-family", PDF_FONT_FAMILY)
        for (attributeIndex in 0 until element.attributes.length) {
            val attribute = element.attributes.item(attributeIndex)
            val name = attribute.localName.orEmpty().lowercase()
            val value = attribute.nodeValue.orEmpty()
            if (name.startsWith("on") || name in setOf("style", "filter")) throw UnsupportedPdfSvg()
            if (name == "href" && (!value.startsWith('#') || value.length <= 1)) throw UnsupportedPdfSvg()
            if (value.contains("url(", ignoreCase = true) && !Regex("url\\(#[A-Za-z0-9_.:-]+\\)").matches(value.trim())) {
                throw UnsupportedPdfSvg()
            }
        }
    }
    // 禁止递归 use 引用；公式通常只引用 defs 中的非引用路径。
    for (index in 0 until elements.length) {
        val element = elements.item(index) as XmlElement
        if (element.localName == "use") {
            val target = element.getAttribute("href").ifBlank { element.getAttribute("xlink:href") }.removePrefix("#")
            if (names[target] !in setOf("path", "rect", "circle", "ellipse", "line", "polyline", "polygon")) throw UnsupportedPdfSvg()
        }
    }
    root.setAttribute("xmlns", "http://www.w3.org/2000/svg")
    val transformers = TransformerFactory.newInstance().apply {
        setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
        setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "")
    }
    return StringWriter().also { output ->
        transformers.newTransformer().apply { setOutputProperty("omit-xml-declaration", "yes") }
            .transform(DOMSource(root), StreamResult(output))
    }.toString()
}

private val PDF_EXPORT_CSS = """
    @page { size: A4; margin: 14mm; }
    html, body { margin: 0; padding: 0; color: #1e293b; background: #ffffff; font-family: '$PDF_FONT_FAMILY'; font-size: 11pt; line-height: 1.65; }
    main, article, section, footer, figure, figcaption { display: block; }
    .page { width: 100%; margin: 0; padding: 0; }
    .title { font-size: 21pt; line-height: 1.3; margin: 0 0 14pt; page-break-after: avoid; }
    .author-card { display: table; width: 100%; margin-bottom: 12pt; padding-bottom: 10pt; border-bottom: 1pt solid #e2e8f0; }
    .author-card > div { display: table-cell; vertical-align: middle; }
    .author-avatar { width: 32pt; height: 32pt; margin-right: 10pt; }
    .author-avatar-placeholder { width: 32pt; }
    .author-name { font-size: 12pt; font-weight: bold; }
    .author-bio { font-size: 9pt; color: #64748b; }
    .stats { margin: 10pt 0 14pt; }
    .stat-chip { display: inline-block; margin-right: 12pt; padding: 5pt 9pt; background: #eff6ff; color: #1d4ed8; }
    .stat-chip-icon { width: 12pt; height: 12pt; vertical-align: middle; }
    .content { word-wrap: break-word; }
    p, blockquote { margin: 0 0 9pt; orphans: 2; widows: 2; }
    h1, h2, h3, h4, h5, h6 { page-break-after: avoid; }
    img { max-width: 100%; max-height: 240mm; }
    .content img { display: block; margin: 8pt auto; }
    .content img[eeimg], .content img.ee_img { display: inline; margin: 0; vertical-align: middle; }
    svg { max-width: 100%; max-height: 240mm; }
    figure { margin: 10pt 0; }
    figcaption, .formula-source { display: block; font-size: 8pt; color: #64748b; word-wrap: break-word; }
    pre, code { white-space: pre-wrap; word-wrap: break-word; font-family: '$PDF_FONT_FAMILY'; }
    table { width: 100%; max-width: 100%; table-layout: fixed; border-collapse: collapse; -fs-table-paginate: paginate; }
    td, th { padding: 4pt; border: 0.5pt solid #cbd5e1; word-wrap: break-word; vertical-align: top; }
    thead { display: table-header-group; }
    a { color: #2563eb; text-decoration: underline; word-wrap: break-word; }
    #zhihu-plus-footnotes { border-top: 0.5pt solid #cbd5e1; padding-top: 10pt; margin-top: 16pt; }
    #zhihu-plus-footnotes h2 { font-size: 13pt; }
    #zhihu-plus-footnotes li { margin-bottom: 5pt; word-wrap: break-word; }
    .export-footer { margin-top: 16pt; padding-top: 10pt; border-top: 0.5pt solid #cbd5e1; font-size: 8pt; color: #64748b; }
    .export-credit { word-wrap: break-word; }
    .export-credit-brand { color: #0284c7; font-weight: bold; }
    .export-image-placeholder { color: #9a3412; }
""".trimIndent()
