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

package com.github.zly2006.zhihu.markdown

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.TextNode
import com.github.zly2006.zhihu.navigation.Video
import com.github.zly2006.zhihu.navigation.resolveContent
import com.github.zly2006.zhihu.util.extractImageUrl
import com.github.zly2006.zhihu.util.toSegmentSourceText
import com.hrm.markdown.parser.MarkdownParser
import com.hrm.markdown.parser.MarkdownParserConfig
import com.hrm.markdown.parser.ast.BlockQuote
import com.hrm.markdown.parser.ast.ContainerNode
import com.hrm.markdown.parser.ast.DirectiveBlock
import com.hrm.markdown.parser.ast.Document
import com.hrm.markdown.parser.ast.Emphasis
import com.hrm.markdown.parser.ast.FencedCodeBlock
import com.hrm.markdown.parser.ast.Figure
import com.hrm.markdown.parser.ast.FootnoteDefinition
import com.hrm.markdown.parser.ast.FootnoteReference
import com.hrm.markdown.parser.ast.HardLineBreak
import com.hrm.markdown.parser.ast.Heading
import com.hrm.markdown.parser.ast.Highlight
import com.hrm.markdown.parser.ast.Image
import com.hrm.markdown.parser.ast.InlineCode
import com.hrm.markdown.parser.ast.InlineMath
import com.hrm.markdown.parser.ast.KeyboardInput
import com.hrm.markdown.parser.ast.Link
import com.hrm.markdown.parser.ast.ListBlock
import com.hrm.markdown.parser.ast.ListItem
import com.hrm.markdown.parser.ast.MathBlock
import com.hrm.markdown.parser.ast.Paragraph
import com.hrm.markdown.parser.ast.Strikethrough
import com.hrm.markdown.parser.ast.StrongEmphasis
import com.hrm.markdown.parser.ast.StyledText
import com.hrm.markdown.parser.ast.Subscript
import com.hrm.markdown.parser.ast.Superscript
import com.hrm.markdown.parser.ast.Table
import com.hrm.markdown.parser.ast.TableBody
import com.hrm.markdown.parser.ast.TableCell
import com.hrm.markdown.parser.ast.TableHead
import com.hrm.markdown.parser.ast.TableRow
import com.hrm.markdown.parser.ast.Text
import com.hrm.markdown.parser.ast.ThematicBreak
import com.hrm.markdown.parser.block.postprocessors.PostProcessor
import com.hrm.markdown.parser.flavour.CommonMarkFlavour
import io.ktor.http.Url
import com.fleeksoft.ksoup.nodes.Node as HtmlNode
import com.hrm.markdown.parser.ast.Node as MarkdownNode

private var parsingDocument: Document? = null
private const val ZHIHU_EQUATION_URL_PREFIX = "https://www.zhihu.com/equation?tex="

/** 在解析器的树修改阶段展开 HTML，AST 由同一解析器发布。 */
fun htmlToMdAst(
    html: String,
    noNativeBlock: Boolean = false,
): Document {
    val processor = ZhihuHtmlPostProcessor(html, noNativeBlock)
    val parser = MarkdownParser(MarkdownParserConfig(flavour = CommonMarkFlavour.with(processor)))
    return parser.parse(html)
}

class ZhihuHtmlPostProcessor(
    private val html: String,
    private val noNativeBlock: Boolean = false,
) : PostProcessor {
    override val priority: Int = 300

    override fun process(document: Document) {
        parsingDocument = document
        try {
            /** CommonMark 第 6 类 HTML 块会被空行截断，须转换整篇源 HTML 保留闭合标签和代码结构。 */
            val source = Ksoup.parseBodyFragment(html)
            source.select("p[data-pid]").forEach { paragraph ->
                val first = paragraph.childNodes().firstOrNull() as? TextNode
                if (first != null) first.text(first.getWholeText().toSegmentSourceText())
            }
            val expanded = source.body().childNodes().convertNodesToBlocks(noNativeBlock)
            document.clearChildren()
            expanded.forEach(document::appendChild)
            var selectionBlock = 0

            fun identifyParagraphs(node: MarkdownNode) {
                if (node is Paragraph) {
                    node.blockAttributes = node.blockAttributes + (SEGMENT_SELECTION_BLOCK_ATTRIBUTE to (selectionBlock++).toString())
                }
                if (node is ContainerNode) node.children.forEach(::identifyParagraphs)
            }
            identifyParagraphs(document)
            document.footnoteDefinitions.values.toList().forEach { definition ->
                document.appendChild(definition)
            }
        } finally {
            parsingDocument = null
        }
    }
}

fun markdownToMdAst(markdown: String): Document = MarkdownParser().parse(markdown)

fun Document.previewImageUrls(): List<String> =
    collectPreviewImageUrls()
        .filter { it.isNotBlank() && !it.startsWith("data") }
        .distinct()

private fun MarkdownNode.collectPreviewImageUrls(): List<String> = when (this) {
    is Figure -> listOf(imageUrl)
    is Image -> listOf(destination)
    is ContainerNode -> children.flatMap { it.collectPreviewImageUrls() }
    else -> emptyList()
}

private fun List<HtmlNode>.convertNodesToBlocks(noNativeBlock: Boolean, inheritedPid: String? = null): List<MarkdownNode> {
    val blocks = mutableListOf<MarkdownNode>()
    var currentParagraph: Paragraph? = null

    /** 预扫描和主循环共用公式判定；也缓存普通图片的 null 结果。 */
    val equationNodeCache = HashMap<Element, MarkdownNode?>()

    fun Element.cachedEquationNode(): MarkdownNode? =
        if (equationNodeCache.containsKey(this)) {
            equationNodeCache[this]
        } else {
            extractEquationNode().also { equationNodeCache[this] = it }
        }
    val hasNextInlineContent = BooleanArray(size + 1)
    for (index in lastIndex downTo 0) {
        val node = this[index]
        hasNextInlineContent[index] = when {
            node is Element && node.isBlockBoundary { it.cachedEquationNode() } -> false
            node.hasInlineContent() -> true
            else -> hasNextInlineContent[index + 1]
        }
    }

    fun paragraph(): Paragraph = currentParagraph ?: Paragraph().also {
        if (inheritedPid != null) it.blockAttributes = mapOf("data-pid" to inheritedPid)
        blocks.add(it)
        currentParagraph = it
    }

    for ((index, node) in this.withIndex()) {
        when (node) {
            is TextNode -> {
                val text = node.text().trimInlineBoundary(
                    hasPreviousInline = currentParagraph != null,
                    hasNextInline = hasNextInlineContent[index + 1],
                )
                if (text.isNotEmpty()) {
                    paragraph().appendChild(
                        Text(text),
                    )
                }
            }

            is Element -> {
                if (node.tagName().equals("img", ignoreCase = true)) {
                    node.cachedEquationNode()?.let { equation ->
                        if (equation is MathBlock) {
                            blocks.add(equation)
                            currentParagraph = null
                        } else {
                            paragraph().appendChild(equation)
                        }
                        continue
                    }
                }

                val blockNode = convertElementToBlock(node, noNativeBlock, inheritedPid)
                if (blockNode.isNotEmpty()) {
                    blocks.addAll(blockNode)
                    currentParagraph = null
                } else {
                    val inlineNodes = extractInlineNode(node)
                    if (inlineNodes.size == 1 && inlineNodes.first() is HardLineBreak) {
                        currentParagraph = null
                        continue
                    }
                    inlineNodes.forEach {
                        if (it is MathBlock) {
                            blocks.add(it)
                        } else {
                            paragraph().appendChild(it)
                        }
                    }
                }
            }
        }
    }

    return blocks
}

private fun String.trimInlineBoundary(
    hasPreviousInline: Boolean,
    hasNextInline: Boolean,
): String = when {
    isBlank() -> if (hasPreviousInline && hasNextInline) " " else ""
    hasPreviousInline && hasNextInline -> this
    hasPreviousInline -> trimEnd()
    hasNextInline -> trimStart()
    else -> trim()
}

private fun Element.isBlockBoundary(
    equationNode: (Element) -> MarkdownNode? = { it.extractEquationNode() },
): Boolean = when (tagName().lowercase()) {
    "br",
    "h1",
    "h2",
    "h3",
    "h4",
    "h5",
    "h6",
    "p",
    "blockquote",
    "pre",
    "ul",
    "ol",
    "hr",
    "figure",
    "table",
    "div",
    -> true
    "img" -> equationNode(this) is MathBlock
    "a" -> attr("class").contains("video-box")
    else -> false
}

private fun convertElementToBlock(
    element: Element,
    noNativeBlock: Boolean,
    inheritedPid: String?,
): List<MarkdownNode> = when (element.tagName().lowercase()) {
    "h1", "h2", "h3", "h4", "h5", "h6" -> listOf(
        Heading(level = element.tagName()[1].digitToInt()).apply {
            blockAttributes = element.attributes().associate { it.key to it.value }
            appendChildren(extractInlineChildren(element))
        },
    )

    "p" -> {
        fun Element.textWithOnlyWhitespace(): Boolean =
            childNodes().filterIsInstance<TextNode>().all { it.text().isBlank() }

        if (element.childNodeSize() == 0) {
            emptyList()
        } else if (element.childrenSize() == 1 && element.textWithOnlyWhitespace() && element.child(0).tagName() == "br") {
            emptyList()
        } else {
            val list = mutableListOf<MarkdownNode>()

            fun paragraph(): Paragraph = list.lastOrNull() as? Paragraph ?: Paragraph().also { list.add(it) }
            val segmentHighlightsEnabled = element.childNodes().all(HtmlNode::supportsSegmentHighlightFormat)
            extractInlineChildren(element, segmentHighlightsEnabled).forEach {
                if (it is MathBlock) {
                    list.add(it)
                } else {
                    paragraph().appendChild(it)
                }
            }
            /** 原始 data-pid 与格式属性是选区偏移校验的依据。 */
            val paragraphAttributes = element.attributes().associate { it.key to it.value }.toMutableMap()
            if (paragraphAttributes["data-pid"].isNullOrBlank() && inheritedPid != null) paragraphAttributes["data-pid"] = inheritedPid
            list.filterIsInstance<Paragraph>().forEach { it.blockAttributes = paragraphAttributes }
            list
        }
    }

    "blockquote" -> listOf(
        BlockQuote().apply {
            blockAttributes = element.attributes().associate { it.key to it.value }
            element.childNodes().convertNodesToBlocks(noNativeBlock, element.attr("data-pid").ifBlank { inheritedPid }).forEach(::appendChild)
        },
    )

    "pre" -> listOf(createCodeBlock(element))

    "ul" -> listOf(createListBlock(element, ordered = false, noNativeBlock = noNativeBlock))

    "ol" -> listOf(createListBlock(element, ordered = true, noNativeBlock = noNativeBlock))

    "hr" -> listOf(ThematicBreak())

    "img" -> listOfNotNull(createBlockImage(element))

    "figure" -> listOfNotNull(createFigureBlock(element))

    "table" -> listOf(createTableBlock(element))

    "div" -> {
        element.childNodes().convertNodesToBlocks(noNativeBlock, element.attr("data-pid").ifBlank { inheritedPid })
    }

    "a" -> {
        if (element.attr("class").contains("video-box")) {
            if (noNativeBlock) {
                listOfNotNull(createVideoBoxLinkBlock(element))
            } else {
                listOfNotNull(createVideoBoxBlock(element))
            }
        } else {
            emptyList()
        }
    }

    else -> emptyList()
}

private fun createCodeBlock(element: Element): FencedCodeBlock {
    val codeElement = element.selectFirst("code")
    val language =
        sequenceOf(
            parseLanguageFromClassName(codeElement?.classNames().orEmpty()),
            element.attr("lang").ifBlank { null },
        ).firstOrNull { !it.isNullOrBlank() }.orEmpty()

    return FencedCodeBlock(
        info = language,
        language = language,
        literal = codeElement?.text() ?: element.text(),
    )
}

private fun parseLanguageFromClassName(classNames: Set<String>): String? {
    val prefix = "language-"
    return classNames.firstOrNull { it.startsWith(prefix) }?.removePrefix(prefix)
}

private fun createListBlock(
    element: Element,
    ordered: Boolean,
    noNativeBlock: Boolean,
): ListBlock = ListBlock(
    ordered = ordered,
    startNumber = element.attr("start").toIntOrNull() ?: 1,
).apply {
    blockAttributes = element.attributes().associate { it.key to it.value }
    var precedingListItem: ListItem? = null
    element.children().forEach { childElement ->
        when (childElement.tagName().lowercase()) {
            "li" -> {
                val listItem = ListItem().apply {
                    val children = childElement.childNodes().convertNodesToBlocks(noNativeBlock)
                    if (children.isEmpty()) {
                        appendChild(
                            Paragraph().apply {
                                appendChildren(extractInlineChildren(childElement))
                            },
                        )
                    } else {
                        appendChildren(children)
                    }
                }
                appendChild(listItem)
                precedingListItem = listItem
            }

            "ul", "ol" -> {
                val nestedList = createListBlock(
                    childElement,
                    ordered = childElement.tagName().equals("ol", ignoreCase = true),
                    noNativeBlock = noNativeBlock,
                )
                if (precedingListItem != null) {
                    precedingListItem.appendChild(nestedList)
                } else {
                    val nestedItems = nestedList.children.toList()
                    nestedList.clearChildren()
                    appendChildren(nestedItems)
                }
            }
        }
    }
}

private fun createBlockImage(element: Element): MarkdownNode? {
    val src = extractImageUrl(element::attr) ?: return null
    val caption = element.attr("data-caption").ifBlank { element.attr("alt") }
    return Figure(
        imageUrl = src,
        caption = caption,
        imageWidth = element.attr("data-rawwidth").toIntOrNull()
            ?: element.attr("width").toIntOrNull(),
        imageHeight = element.attr("data-rawheight").toIntOrNull()
            ?: element.attr("height").toIntOrNull(),
        attributes = element.attributes().associate { it.key to it.value },
    )
}

private fun createFigureBlock(element: Element): MarkdownNode? {
    element.selectFirst("img")?.let { image ->
        val src = extractImageUrl(image::attr) ?: return@let null
        val caption = element.selectFirst("figcaption")?.text()?.ifBlank { null } ?: ""
        return Figure(
            imageUrl = src,
            caption = caption,
            imageWidth = image.attr("data-rawwidth").toIntOrNull()
                ?: image.attr("width").toIntOrNull(),
            imageHeight = image.attr("data-rawheight").toIntOrNull()
                ?: image.attr("height").toIntOrNull(),
            /** Figure 是叶节点，img. 前缀避免原图属性覆盖 figure 的 class/id。 */
            attributes = element.attributes().associate { it.key to it.value } +
                image.attributes().associate { "img.${it.key}" to it.value },
        )
    }

    if (element.classNames().any { it.contains("highlight") }) {
        return createCodeBlock(element)
    }

    val inlines = extractInlineChildren(element)
    return inlines.takeIf { it.isNotEmpty() }?.let {
        Paragraph().apply { appendChildren(it) }
    }
}

private fun createVideoBoxBlock(element: Element): MarkdownNode? {
    val href = element.attr("href")
    val videoId = href.takeIf { it.isNotBlank() }?.let { destination ->
        val resolved = resolveContent(destination)
        if (resolved is Video) resolved.id else null
    } ?: element.attr("data-lens-id").toLongOrNull() ?: return null
    val thumbnailUrl = element.selectFirst("img")?.let { image ->
        extractImageUrl(image::attr)
    }

    return DirectiveBlock(
        tagName = "zhihu-video",
        args = buildMap {
            put("id", videoId.toString())
            thumbnailUrl?.let { put("thumb", it) }
        },
    )
}

private fun createVideoBoxLinkBlock(element: Element): MarkdownNode? {
    val href = element.attr("href").ifBlank {
        element
            .attr("data-lens-id")
            .toLongOrNull()
            ?.let { "https://www.zhihu.com/video/$it" }
            .orEmpty()
    }
    if (href.isBlank()) return null
    return Paragraph().apply {
        appendChild(
            Link(destination = normalizeLinkDestination(href)).apply {
                appendChild(Text("视频"))
            },
        )
    }
}

private fun createTableBlock(element: Element): Table = Table().apply {
    blockAttributes = element.attributes().associate { it.key to it.value }
    val directRows = element.select("> tr")
    val headRows = element.select("> thead > tr")
    val bodyRows = element.select("> tbody > tr")

    if (headRows.isNotEmpty()) {
        appendChild(
            TableHead().apply {
                headRows.forEach { appendChild(createTableRow(it, isHeader = true)) }
            },
        )
    }

    val rowsForBody = when {
        bodyRows.isNotEmpty() -> bodyRows
        headRows.isEmpty() -> directRows
        else -> emptyList()
    }
    if (rowsForBody.isNotEmpty()) {
        appendChild(
            TableBody().apply {
                rowsForBody.forEach { appendChild(createTableRow(it, isHeader = false)) }
            },
        )
    }

    val referenceRow = headRows.firstOrNull() ?: rowsForBody.firstOrNull()
    columnAlignments = referenceRow
        ?.select("> th, > td")
        ?.map { it.toAlignment() }
        .orEmpty()
}

private fun createTableRow(
    row: Element,
    isHeader: Boolean,
): TableRow = TableRow().apply {
    row.select("> th, > td").forEach { cell ->
        appendChild(
            TableCell(
                alignment = cell.toAlignment(),
                isHeader = isHeader || cell.tagName().equals("th", ignoreCase = true),
            ).apply {
                appendChildren(extractInlineChildren(cell))
            },
        )
    }
}

private fun Element.toAlignment(): Table.Alignment = when (attr("align").lowercase()) {
    "left" -> Table.Alignment.LEFT
    "center" -> Table.Alignment.CENTER
    "right" -> Table.Alignment.RIGHT
    else -> Table.Alignment.NONE
}

private fun extractInlineChildren(
    element: Element,
    segmentHighlightsEnabled: Boolean = true,
): List<MarkdownNode> {
    val childNodes = element.childNodes()
    return childNodes.flatMapIndexed { index, child ->
        if (child is TextNode && child.text().isBlank()) {
            if (childNodes.take(index).any { it.hasInlineContent() } && childNodes.drop(index + 1).any { it.hasInlineContent() }) {
                listOf(Text(" "))
            } else {
                emptyList()
            }
        } else {
            extractInlineNode(child, segmentHighlightsEnabled)
        }
    }
}

private fun HtmlNode.hasInlineContent(): Boolean = when (this) {
    is TextNode -> text().isNotBlank()
    is Element -> when (tagName().lowercase()) {
        "br" -> false
        "img" -> extractEquationTex(this) != null || extractImageUrl(this::attr) != null
        else -> childNodes().any { it.hasInlineContent() } || text().isNotBlank()
    }
    else -> false
}

private fun HtmlNode.supportsSegmentHighlightFormat(): Boolean = when (this) {
    is TextNode -> true
    is Element -> {
        val supportedTag = tagName().lowercase() in SEGMENT_HIGHLIGHT_FORMAT_TAGS ||
            (tagName().equals("span", ignoreCase = true) && hasClass("highlight-wrap"))
        supportedTag && childNodes().all(HtmlNode::supportsSegmentHighlightFormat)
    }
    else -> false
}

private fun extractEquationTex(imgElement: Element): String? = extractImageUrl(imgElement::attr)
    ?.takeIf { it.startsWith(ZHIHU_EQUATION_URL_PREFIX) }
    ?.let { Url(it).parameters["tex"].orEmpty() }
    ?.takeIf { it.isNotBlank() }

/** 超长公式超出原图 URL 与行内阅读预算，须作为可滚动的块级公式。 */
private const val OVERLONG_FORMULA_SOURCE_LIMIT = 4096

private fun Element.extractEquationNode(): MarkdownNode? = extractEquationTex(this)?.let { formula ->
    if (
        formula.length > OVERLONG_FORMULA_SOURCE_LIMIT ||
        attr("eeimg") == "2" ||
        formula.zhihuEquationSemantics().isDisplay
    ) {
        MathBlock(formula)
    } else {
        InlineMath(formula)
    }
}

internal data class ZhihuEquationSemantics(
    val isDisplay: Boolean,
    val hasEquationTag: Boolean,
)

/** 知乎 MathJax 将顶层行分隔、tag 命令和 align 环境视为块级公式；矩阵内部换行仍为行内，原 TeX 不改写。 */
internal fun String.zhihuEquationSemantics(): ZhihuEquationSemantics {
    var index = 0
    var groupDepth = 0
    var isDisplay = false
    var hasEquationTag = false
    val environments = mutableListOf<String>()

    fun skipIgnorable(start: Int): Int {
        var cursor = start
        while (cursor < length) {
            when {
                this[cursor].isWhitespace() -> cursor++
                this[cursor] == '%' -> {
                    cursor++
                    while (cursor < length && this[cursor] != '\n' && this[cursor] != '\r') cursor++
                }
                else -> return cursor
            }
        }
        return cursor
    }

    fun readEnvironmentName(start: Int): Pair<String, Int>? {
        var cursor = skipIgnorable(start)
        if (cursor >= length || this[cursor] != '{') return null
        val nameStart = ++cursor
        while (cursor < length && this[cursor] != '}') {
            if (this[cursor] == '{') return null
            cursor++
        }
        if (cursor >= length || cursor == nameStart) return null
        return substring(nameStart, cursor) to cursor + 1
    }

    while (index < length) {
        when (this[index]) {
            '%' -> index = skipIgnorable(index)
            '{' -> {
                groupDepth++
                index++
            }
            '}' -> {
                if (groupDepth > 0) groupDepth--
                index++
            }
            '\\' -> {
                if (index + 1 < length && this[index + 1] == '\\') {
                    val afterSeparator = index + 2
                    if (groupDepth == 0 && environments.isEmpty()) {
                        isDisplay = true
                    }
                    index = afterSeparator
                    continue
                }

                var commandEnd = index + 1
                if (commandEnd < length && this[commandEnd].isLetter()) {
                    while (commandEnd < length && this[commandEnd].isLetter()) commandEnd++
                } else if (commandEnd < length) {
                    commandEnd++
                }
                val command = substring(index + 1, commandEnd)
                if (command == "tag") {
                    isDisplay = true
                    hasEquationTag = true
                }
                if (command == "begin" || command == "end") {
                    readEnvironmentName(commandEnd)?.let { (environment, afterEnvironment) ->
                        if (
                            command == "begin" &&
                            groupDepth == 0 &&
                            environments.isEmpty() &&
                            (environment == "align" || environment == "align*")
                        ) {
                            isDisplay = true
                        }
                        if (command == "begin") {
                            environments.add(environment)
                        } else if (environments.lastOrNull() == environment) {
                            environments.removeAt(environments.lastIndex)
                        }
                        index = afterEnvironment
                        continue
                    }
                }
                index = commandEnd
            }
            else -> index++
        }
    }
    return ZhihuEquationSemantics(isDisplay, hasEquationTag)
}

private fun extractInlineNode(
    node: HtmlNode,
    segmentHighlightsEnabled: Boolean = true,
): List<MarkdownNode> = when (node) {
    is TextNode -> {
        val text = node.text()
        if (text.isBlank()) {
            emptyList()
        } else {
            listOf(
                Text(text),
            )
        }
    }

    is Element -> when (node.tagName().lowercase()) {
        "strong", "b" -> listOf(
            StrongEmphasis().apply { appendChildren(extractInlineChildren(node, segmentHighlightsEnabled)) },
        )

        "em", "i" -> listOf(
            Emphasis().apply { appendChildren(extractInlineChildren(node, segmentHighlightsEnabled)) },
        )

        "del", "s", "strike" -> listOf(
            Strikethrough().apply { appendChildren(extractInlineChildren(node, segmentHighlightsEnabled)) },
        )

        "mark" -> listOf(
            Highlight(attributes = node.attributes().associate { it.key to it.value }).apply {
                appendChildren(extractInlineChildren(node, segmentHighlightsEnabled))
            },
        )

        "span" -> {
            if (segmentHighlightsEnabled && node.hasClass("highlight-wrap")) {
                listOf(
                    /** 下划线由渲染器装饰槽绘制，不能再叠加 TextDecoration。 */
                    Highlight(
                        attributes = node.attributes().associate { it.key to it.value },
                    ).apply {
                        appendChildren(extractInlineChildren(node, segmentHighlightsEnabled))
                    },
                )
            } else {
                listOf(
                    StyledText(attributes = node.attributes().associate { it.key to it.value }).apply {
                        appendChildren(extractInlineChildren(node, segmentHighlightsEnabled))
                    },
                )
            }
        }

        "sub" -> listOf(
            Subscript(attributes = node.attributes().associate { it.key to it.value }).apply {
                appendChildren(extractInlineChildren(node, segmentHighlightsEnabled))
            },
        )

        "sup" -> {
            if (node.attr("data-draft-type") == "reference") {
                val index = node.attr("data-numero").toInt()
                parsingDocument!!.footnoteDefinitions[node.attr("data-numero")] = FootnoteDefinition(index.toString(), index).apply {
                    appendChild(
                        Paragraph().apply {
                            appendChild(Text(node.attr("data-text")))
                            val url = node.attr("data-url")
                            if (url.isNotBlank()) {
                                appendChild(
                                    Link(destination = url).apply {
                                        appendChild(Text(url))
                                    },
                                )
                            }
                        },
                    )
                }
                listOf(FootnoteReference(index.toString(), index))
            } else {
                listOf(
                    Superscript(attributes = node.attributes().associate { it.key to it.value }).apply {
                        appendChildren(extractInlineChildren(node, segmentHighlightsEnabled))
                    },
                )
            }
        }

        "kbd" -> listOf(KeyboardInput(node.text()))

        "code" -> listOf(InlineCode(node.text()))

        "a" -> {
            val href = node.attr("href")
            listOf(
                Link(
                    destination = normalizeLinkDestination(href),
                    title = node.attr("title").ifBlank { null },
                    attributes = node.attributes().associate { it.key to it.value },
                ).apply {
                    appendChildren(
                        extractInlineChildren(node, segmentHighlightsEnabled).ifEmpty {
                            listOf(
                                Text(node.text()),
                            )
                        },
                    )
                },
            )
        }

        "br" -> listOf(HardLineBreak())

        "img" -> {
            val equation = node.extractEquationNode()
            if (equation != null) {
                listOf(equation)
            } else {
                extractImageUrl(node::attr)
                    ?.let { url ->
                        listOf(
                            Image(
                                destination = url,
                                title = node.attr("title").ifBlank { null },
                                imageWidth = node.attr("width").toIntOrNull(),
                                imageHeight = node.attr("height").toIntOrNull(),
                                attributes = node.attributes().associate { it.key to it.value },
                            ).apply {
                                node.attr("alt").takeIf { it.isNotBlank() }?.let {
                                    appendChild(
                                        Text(it),
                                    )
                                }
                            },
                        )
                    }.orEmpty()
            }
        }

        else -> {
            val children = extractInlineChildren(node, segmentHighlightsEnabled)
            if (children.isNotEmpty()) {
                children
            } else {
                node
                    .text()
                    .takeIf { it.isNotBlank() }
                    ?.let {
                        listOf(
                            Text(it),
                        )
                    }.orEmpty()
            }
        }
    }

    else -> emptyList()
}

private fun normalizeLinkDestination(href: String): String =
    if (href.contains("link.zhihu.com")) {
        runCatching { Url(href).parameters["target"] }.getOrNull()?.takeIf { it.isNotBlank() } ?: href
    } else {
        href
    }

private fun ContainerNode.appendChildren(children: List<MarkdownNode>) {
    children.forEach(::appendChild)
}

private val SEGMENT_HIGHLIGHT_FORMAT_TAGS = setOf("b", "strong", "i", "em", "br")

fun zhihuHtmlToMarkdown(html: String): String = htmlToMdAst(html).toMarkdown().trim()

fun Document.toMarkdown(): String {
    val out = StringBuilder()
    for (child in children) {
        child.appendMarkdownBlock(out, orderedIndex = null)
    }
    return out.toString().trimEnd()
}

private fun MarkdownNode.appendMarkdownBlock(
    out: StringBuilder,
    orderedIndex: Int?,
) {
    when (this) {
        is Heading -> {
            val level = level.coerceIn(1, 6)
            out.append("#".repeat(level)).append(" ")
            appendMarkdownInline(out)
            out.append("\n\n")
        }

        is Paragraph -> {
            appendMarkdownInline(out)
            out.append("\n\n")
        }

        is BlockQuote -> {
            val content = buildString {
                for (child in children) {
                    child.appendMarkdownBlock(this, orderedIndex = null)
                }
            }.trimEnd()
            val lines = content.lines()
            for (line in lines) {
                if (line.isBlank()) {
                    out.append(">\n")
                } else {
                    out.append("> ").append(line).append("\n")
                }
            }
            out.append("\n")
        }

        is FencedCodeBlock -> {
            val lang = language.takeIf { it.isNotBlank() }
            out.append("```")
            if (lang != null) out.append(lang)
            out.append("\n")
            out.append(literal.trimEnd())
            out.append("\n```\n\n")
        }

        is ListBlock -> {
            for ((i, item) in children.filterIsInstance<ListItem>().withIndex()) {
                val prefix =
                    if (ordered) {
                        "${startNumber + i}. "
                    } else {
                        "- "
                    }
                val itemContent = buildString {
                    item.appendMarkdownListItem(this)
                }.trimEnd()
                val lines = itemContent.lines()
                if (lines.isEmpty() || lines.all { it.isBlank() }) {
                    out.append(prefix.trimEnd()).append("\n")
                    continue
                }
                out.append(prefix).append(lines.first()).append("\n")
                for (line in lines.drop(1)) {
                    if (line.isBlank()) {
                        out.append("\n")
                    } else {
                        out.append("  ").append(line).append("\n")
                    }
                }
            }
            out.append("\n")
        }

        is ThematicBreak -> out.append("---\n\n")

        is MathBlock -> {
            val tex = literal.trim()
            out.append("$$").append(tex).append("$$\n\n")
        }

        is Figure -> {
            val alt = caption.takeIf { it.isNotBlank() }.orEmpty()
            out
                .append("![")
                .append(alt)
                .append("](")
                .append(imageUrl)
                .append(")\n\n")
        }

        is Table -> {
            appendMarkdownTable(out)
            out.append("\n")
        }

        is FootnoteDefinition -> {
            out.append("[^").append(index).append("]: ")
            val content =
                buildString {
                    this@appendMarkdownBlock.appendMarkdownInline(this)
                }.trim()
            out.append(content).append("\n\n")
        }

        else -> {
            if (this is ContainerNode) {
                for (child in children) {
                    child.appendMarkdownBlock(out, orderedIndex = orderedIndex)
                }
            }
        }
    }
}

private fun ListItem.appendMarkdownListItem(out: StringBuilder) {
    val childBlocks = children
    if (childBlocks.size == 1 && childBlocks.single() is Paragraph) {
        (childBlocks.single() as Paragraph).appendMarkdownInline(out)
        return
    }
    for (child in childBlocks) {
        child.appendMarkdownBlock(out, orderedIndex = null)
    }
}

private fun MarkdownNode.appendMarkdownInline(out: StringBuilder) {
    when (this) {
        is Text -> out.append(literal)
        is StrongEmphasis -> out.append("**").also { children.forEach { it.appendMarkdownInline(out) } }.append("**")
        is Emphasis -> out.append("*").also { children.forEach { it.appendMarkdownInline(out) } }.append("*")
        is Strikethrough -> out.append("~~").also { children.forEach { it.appendMarkdownInline(out) } }.append("~~")
        is Highlight -> children.forEach { it.appendMarkdownInline(out) }
        is Subscript -> out.append("<sub>").also { children.forEach { it.appendMarkdownInline(out) } }.append("</sub>")
        is Superscript -> out.append("<sup>").also { children.forEach { it.appendMarkdownInline(out) } }.append("</sup>")
        is InlineCode -> out.append("`").append(literal).append("`")
        is KeyboardInput -> out.append("<kbd>").append(literal).append("</kbd>")
        is HardLineBreak -> out.append("\n")
        is InlineMath -> out.append("$").append(literal.trim()).append("$")
        is FootnoteReference -> out.append("[^").append(index).append("]")
        is Link -> {
            val text = buildString { children.forEach { it.appendMarkdownInline(this) } }.ifBlank { destination }
            out
                .append("[")
                .append(text)
                .append("](")
                .append(destination)
                .append(")")
        }

        is Image -> {
            val alt = children.filterIsInstance<Text>().joinToString(separator = "") { it.literal }.ifBlank { "" }
            out
                .append("![")
                .append(alt)
                .append("](")
                .append(destination)
                .append(")")
        }

        else -> if (this is ContainerNode) children.forEach { it.appendMarkdownInline(out) }
    }
}

private fun Table.appendMarkdownTable(out: StringBuilder) {
    val head = children.filterIsInstance<TableHead>().singleOrNull()
    val body = children.filterIsInstance<TableBody>().singleOrNull()
    val allRows =
        buildList {
            head?.children?.filterIsInstance<TableRow>()?.let(::addAll)
            body?.children?.filterIsInstance<TableRow>()?.let(::addAll)
            if (head == null && body == null) addAll(children.filterIsInstance<TableRow>())
        }
    if (allRows.isEmpty()) return
    val headerRow = allRows.first()
    val headerCells = headerRow.children.filterIsInstance<TableCell>()
    if (headerCells.isEmpty()) return
    out.append("| ")
    headerCells.forEach { cell ->
        out.append(cell.inlineTextForTable()).append(" | ")
    }
    out.append("\n| ")
    headerCells.forEach { _ ->
        out.append("--- | ")
    }
    out.append("\n")
    for (row in allRows.drop(1)) {
        val cells = row.children.filterIsInstance<TableCell>()
        if (cells.isEmpty()) continue
        out.append("| ")
        cells.forEach { cell ->
            out.append(cell.inlineTextForTable()).append(" | ")
        }
        out.append("\n")
    }
}

private fun TableCell.inlineTextForTable(): String =
    buildString { this@inlineTextForTable.appendMarkdownInline(this) }
        .replace(Regex("[\n\r]+"), " ")
        .trim()
