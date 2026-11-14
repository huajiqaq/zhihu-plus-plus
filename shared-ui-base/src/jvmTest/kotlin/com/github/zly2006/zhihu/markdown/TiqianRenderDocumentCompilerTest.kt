/*
 * Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation (version 3 only).
 */

package com.github.zly2006.zhihu.markdown

import com.hrm.markdown.parser.LineRange
import com.hrm.markdown.parser.MarkdownParser
import com.hrm.markdown.parser.SourcePosition
import com.hrm.markdown.parser.SourceRange
import com.hrm.markdown.parser.ast.Abbreviation
import com.hrm.markdown.parser.ast.AbbreviationDefinition
import com.hrm.markdown.parser.ast.Admonition
import com.hrm.markdown.parser.ast.Autolink
import com.hrm.markdown.parser.ast.BibEntry
import com.hrm.markdown.parser.ast.BibliographyDefinition
import com.hrm.markdown.parser.ast.BlankLine
import com.hrm.markdown.parser.ast.BlockQuote
import com.hrm.markdown.parser.ast.CitationReference
import com.hrm.markdown.parser.ast.ColumnItem
import com.hrm.markdown.parser.ast.ContainerNode
import com.hrm.markdown.parser.ast.CustomContainer
import com.hrm.markdown.parser.ast.DefinitionTerm
import com.hrm.markdown.parser.ast.DiagramBlock
import com.hrm.markdown.parser.ast.DirectiveBlock
import com.hrm.markdown.parser.ast.DirectiveInline
import com.hrm.markdown.parser.ast.Document
import com.hrm.markdown.parser.ast.Emoji
import com.hrm.markdown.parser.ast.Emphasis
import com.hrm.markdown.parser.ast.EscapedChar
import com.hrm.markdown.parser.ast.FencedCodeBlock
import com.hrm.markdown.parser.ast.Figure
import com.hrm.markdown.parser.ast.FootnoteReference
import com.hrm.markdown.parser.ast.FrontMatter
import com.hrm.markdown.parser.ast.HardLineBreak
import com.hrm.markdown.parser.ast.Heading
import com.hrm.markdown.parser.ast.Highlight
import com.hrm.markdown.parser.ast.HtmlEntity
import com.hrm.markdown.parser.ast.Image
import com.hrm.markdown.parser.ast.InlineCode
import com.hrm.markdown.parser.ast.InlineHtml
import com.hrm.markdown.parser.ast.InlineMath
import com.hrm.markdown.parser.ast.InsertedText
import com.hrm.markdown.parser.ast.KeyboardInput
import com.hrm.markdown.parser.ast.Link
import com.hrm.markdown.parser.ast.LinkReferenceDefinition
import com.hrm.markdown.parser.ast.ListBlock
import com.hrm.markdown.parser.ast.ListItem
import com.hrm.markdown.parser.ast.Node
import com.hrm.markdown.parser.ast.Paragraph
import com.hrm.markdown.parser.ast.RubyText
import com.hrm.markdown.parser.ast.SetextHeading
import com.hrm.markdown.parser.ast.SoftLineBreak
import com.hrm.markdown.parser.ast.Spoiler
import com.hrm.markdown.parser.ast.Strikethrough
import com.hrm.markdown.parser.ast.StrongEmphasis
import com.hrm.markdown.parser.ast.StyledText
import com.hrm.markdown.parser.ast.Subscript
import com.hrm.markdown.parser.ast.Superscript
import com.hrm.markdown.parser.ast.TabItem
import com.hrm.markdown.parser.ast.Table
import com.hrm.markdown.parser.ast.TableBody
import com.hrm.markdown.parser.ast.TableCell
import com.hrm.markdown.parser.ast.TableHead
import com.hrm.markdown.parser.ast.TableRow
import com.hrm.markdown.parser.ast.Text
import com.hrm.markdown.parser.ast.ThematicBreak
import com.hrm.markdown.parser.ast.TocPlaceholder
import com.hrm.markdown.parser.ast.WikiLink
import com.hrm.markdown.parser.core.Attributes
import org.tiqian.markdown.MarkdownBibliographyBlock
import org.tiqian.markdown.MarkdownCodeBlock
import org.tiqian.markdown.MarkdownContainerBlock
import org.tiqian.markdown.MarkdownCustomBlock
import org.tiqian.markdown.MarkdownDiagramBlock
import org.tiqian.markdown.MarkdownList
import org.tiqian.markdown.MarkdownNodeKey
import org.tiqian.markdown.MarkdownNodeValue
import org.tiqian.markdown.MarkdownParagraph
import org.tiqian.markdown.MarkdownSourceSpan
import org.tiqian.markdown.MarkdownTable
import org.tiqian.markdown.MarkdownTextMark
import org.tiqian.markdown.MarkdownTextRange
import org.tiqian.markdown.MarkdownTocBlock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TiqianRenderDocumentCompilerTest {
    @Test
    fun retainsVisibleBibliographyAndBlockOptionsInSemanticModels() {
        val compiler = TiqianRenderDocumentCompiler()
        val source = "[^bibliography]: alpha: 原始源码"
        val bibliography = BibliographyDefinition().apply {
            lineRange = LineRange(0, 1)
            entries["alpha"] = BibEntry("alpha", "相同内容😀")
            entries["beta"] = BibEntry("beta", "相同内容😀")
        }
        val document = Document().apply { appendChild(bibliography) }
        for (sourceInput in listOf(source, null)) {
            val compiled = compiler.compile(document, sourceInput)
            val block = assertIs<MarkdownBibliographyBlock>(compiled.blocks.single())
            assertEquals("References", block.title)
            assertEquals(listOf("alpha", "beta"), block.entries.map { it.key })
            assertTrue(block.entries.all { it.text.value == "相同内容😀" })
            assertEquals(listOf(0), block.metadata.key.path)
            assertEquals("BibliographyDefinition", block.metadata.nodeType)
            assertEquals(sourceInput, block.metadata.sourceMarkdown)
            assertFalse(block.metadata.hasSourceSpan)
            assertEquals(MarkdownSourceSpan(0, 0, 0, 0, 0, 0), block.metadata.sourceSpan)
            val entries = assertIs<MarkdownNodeValue.ObjectValue>(block.metadata.properties["entries"]).values
            for ((index, key) in listOf("alpha", "beta").withIndex()) {
                val entry = assertIs<MarkdownNodeValue.ObjectValue>(entries[key]).values
                assertEquals(MarkdownNodeValue.Text(key), entry["key"])
                assertEquals(MarkdownNodeValue.Text("相同内容😀"), entry["content"])
                assertEquals(
                    listOf(0, -1, index),
                    block.entries[index]
                        .metadata.key.path,
                )
                assertFalse(block.entries[index].metadata.hasSourceSpan)
                assertNull(block.entries[index].metadata.persistentId)
            }
            assertTrue(compiled.issues.isEmpty())
        }
        val invisible = Document().apply {
            appendChild(BibliographyDefinition())
            appendChild(LinkReferenceDefinition())
            appendChild(AbbreviationDefinition())
            appendChild(BlankLine())
            appendChild(FrontMatter(literal = "secret: hidden"))
        }
        val invisibleResult = compiler.compile(invisible, source)
        assertTrue(invisibleResult.blocks.isEmpty())
        assertTrue(invisibleResult.issues.isEmpty())

        for (showLineNumbers in listOf(true, false)) {
            val code = FencedCodeBlock(
                language = "kotlin",
                info = "kotlin title=example.kt",
                literal = "val a = 1\nval b = 2",
                attributes = Attributes(pairs = mapOf("title" to "example.kt")),
            ).apply {
                this.showLineNumbers = showLineNumbers
                startLineNumber = 7
                highlightLines = listOf(1..1, 3..5)
            }
            val compiled = compiler.compile(Document().apply { appendChild(code) })
            val block = assertIs<MarkdownCodeBlock>(compiled.blocks.single())
            assertEquals(code.literal, block.code)
            assertEquals("kotlin", block.language)
            assertEquals(code.info, block.info)
            assertEquals("example.kt", block.fileName)
            assertEquals(showLineNumbers, block.showLineNumbers)
            assertEquals(showLineNumbers.toString(), block.metadata.payload["showLineNumbers"])
            assertEquals("7", block.metadata.payload["startLineNumber"])
            assertEquals(7, block.startLineNumber)
            assertEquals(listOf(1..1, 3..5), block.highlightedLines)
            assertIs<MarkdownNodeValue.ListValue>(block.metadata.properties["highlightLines"])
            assertTrue(block.highlights.isEmpty())
        }
        val defaultCode = assertIs<MarkdownCodeBlock>(
            compiler.compile(Document().apply { appendChild(FencedCodeBlock(literal = "code")) }).blocks.single(),
        )
        assertTrue(defaultCode.showLineNumbers)
        assertNull(defaultCode.fileName)
        assertEquals("1", defaultCode.metadata.payload["startLineNumber"])
        assertEquals(1, defaultCode.startLineNumber)
        assertTrue(defaultCode.highlightedLines.isEmpty())
        assertEquals(MarkdownNodeValue.ListValue(emptyList()), defaultCode.metadata.properties["highlightLines"])

        for (containsBlankLine in listOf(true, false)) {
            val list = ListBlock().apply {
                appendChild(ListItem().apply { this.containsBlankLine = containsBlankLine })
            }
            val block = assertIs<MarkdownList>(compiler.compile(Document().apply { appendChild(list) }).blocks.single())
            assertEquals(
                containsBlankLine.toString(),
                block.items
                    .single()
                    .metadata.payload["containsBlankLine"],
            )
            assertTrue(block.tight)
            assertNull(block.items.single().task)
        }
        for (char in listOf('-', '*', '_')) {
            val block = compiler.compile(Document().apply { appendChild(ThematicBreak(char)) }).blocks.single()
            assertEquals(char.toString(), block.metadata.payload["char"])
        }

        val extensions = listOf(
            Admonition("WARNING", "标题") to mapOf("type" to "WARNING", "title" to "标题"),
            CustomContainer("card", "标题", listOf("wide", "note"), "box") to
                mapOf("type" to "card", "title" to "标题", "cssId" to "box"),
            ColumnItem("50%") to mapOf("width" to "50%"),
            TabItem("第二页") to mapOf("title" to "第二页"),
            DiagramBlock("mermaid", "graph LR; A-->B") to mapOf("diagramType" to "mermaid", "literal" to "graph LR; A-->B"),
            TocPlaceholder().apply {
                minDepth = 2
                maxDepth = 4
                excludeIds = listOf("id,with,comma", "另一项")
                order = "desc"
            } to mapOf("minDepth" to "2", "maxDepth" to "4", "order" to "desc"),
        )
        for ((node, payload) in extensions) {
            if (node is ContainerNode) node.appendChild(Paragraph().apply { appendChild(Text("可读内容😀")) })
            val compiled = compiler.compile(Document().apply { appendChild(node) })
            val block = compiled.blocks.single()
            payload.forEach { (key, value) -> assertEquals(value, block.metadata.payload[key], "${node::class.simpleName}.$key") }
            assertEquals(node::class.simpleName, block.metadata.nodeType)
            assertEquals(listOf(0), block.metadata.key.path)
            when (node) {
                is ContainerNode -> assertEquals("可读内容😀", assertIs<MarkdownParagraph>(assertIs<MarkdownContainerBlock>(block).blocks.single()).text.value)
                is DiagramBlock -> assertEquals(node.literal, assertIs<MarkdownDiagramBlock>(block).source)
                is TocPlaceholder -> {
                    val toc = assertIs<MarkdownTocBlock>(block)
                    assertEquals(node.excludeIds, toc.excludedIds)
                    assertTrue(toc.reversed)
                }
                else -> error("Unexpected extension fixture: ${node::class.simpleName}")
            }
            assertTrue(compiled.issues.isEmpty())
        }
    }

    @Test
    fun retainsAstIdentityAttributesAndUtf16RangesWithoutInventingSourceOrStyles() {
        data class Case(
            val make: () -> Node,
            val text: String,
            val mark: MarkdownTextMark? = null,
            val attributes: Map<String, String> = emptyMap(),
            val payload: Map<String, String> = emptyMap(),
            val unsupported: Boolean = false,
        )

        val text = "重复😀"
        val attributes = mapOf("data-note" to "kept")
        val segmentAttributes = attributes + ("data-highlight-id" to "segment")
        val cases = listOf(
            Case({ Text(text) }, text, payload = mapOf("literal" to text)),
            Case({ Text("") }, ""),
            Case({ SoftLineBreak() }, " ", payload = mapOf("literal" to "\n")),
            Case({ HardLineBreak() }, "\n", payload = mapOf("literal" to "\n")),
            Case({ HtmlEntity("&amp;", "&") }, "&", payload = mapOf("literal" to "&amp;", "resolved" to "&")),
            Case({ EscapedChar("*") }, "*", payload = mapOf("literal" to "*")),
            Case({ Emoji("smile", ":smile:").apply { unicode = "😀" } }, "😀", payload = mapOf("shortcode" to "smile", "unicode" to "😀")),
            Case({ InlineCode(text) }, text, MarkdownTextMark.InlineCode),
            Case({ Emphasis() }, text, MarkdownTextMark.Emphasis),
            Case({ StrongEmphasis() }, text, MarkdownTextMark.Strong),
            Case({ Strikethrough() }, text, MarkdownTextMark.Strikethrough),
            Case({ Highlight(attributes) }, text, MarkdownTextMark.Highlight, attributes),
            Case({ Highlight(segmentAttributes) }, text, MarkdownTextMark.Custom(ZHIHU_SEGMENT_HIGHLIGHT_KIND, segmentAttributes), segmentAttributes),
            Case({ Superscript(attributes) }, text, MarkdownTextMark.Superscript, attributes),
            Case({ Subscript(attributes) }, text, MarkdownTextMark.Subscript, attributes),
            Case({ InsertedText(attributes) }, text, MarkdownTextMark.Inserted, attributes),
            Case({ Link("/link", "title", attributes) }, text, MarkdownTextMark.Link("/link", "title"), attributes, mapOf("destination" to "/link", "title" to "title")),
            Case({ Autolink("mailto:a@example.invalid", true, text) }, text, MarkdownTextMark.Link("mailto:a@example.invalid"), payload = mapOf("isEmail" to "true", "rawText" to text)),
            Case({ WikiLink("page", text) }, text, MarkdownTextMark.Link("page"), payload = mapOf("target" to "page", "label" to text)),
            Case({ Abbreviation(text, "full") }, text, MarkdownTextMark.Abbreviation("full"), payload = mapOf("abbreviation" to text, "fullText" to "full")),
            Case({ FootnoteReference("note", 3) }, "[3]", MarkdownTextMark.Footnote("note", 3), payload = mapOf("label" to "note", "index" to "3")),
            Case({ RubyText(text, "annotation") }, text, MarkdownTextMark.Ruby("annotation"), payload = mapOf("base" to text, "annotation" to "annotation")),
            Case({ KeyboardInput(text) }, text, MarkdownTextMark.KeyboardInput),
            Case({ Image("/image", "title", 24, 16, attributes) }, text, MarkdownTextMark.InlineImage("/image", text, "title", 24, 16, attributes), attributes),
            Case({ InlineMath("x") }, "x", MarkdownTextMark.InlineMath("x")),
            Case({ InlineHtml("<unknown>") }, "<unknown>", unsupported = true),
            Case({ StyledText(attributes + ("class" to "semantic-only")) }, text, attributes = attributes + ("class" to "semantic-only")),
            Case({ StyledText(attributes + ("style" to "font-weight:bold")) }, text, attributes = attributes + ("style" to "font-weight:bold"), unsupported = true),
            Case({ Spoiler() }, text, MarkdownTextMark.Spoiler),
            Case({ CitationReference("cite") }, "[@cite]", MarkdownTextMark.Citation("cite"), payload = mapOf("key" to "cite")),
            Case({ DirectiveInline("tag", attributes) }, "tag", attributes = attributes, payload = mapOf("tagName" to "tag", "args.data-note" to "kept"), unsupported = true),
            Case({ DefinitionTerm() }, text, unsupported = true),
        )
        val source = "前<原😀>\r\n尾"
        val exactRange = SourceRange(SourcePosition(0, 1, 1), SourcePosition(0, 6, 6))
        val compiler = TiqianRenderDocumentCompiler()
        for (case in cases) {
            val first = case.make().apply {
                lineRange = LineRange(11, 12)
                sourceRange = exactRange
                if (this is ContainerNode) appendChild(Text(text))
            }
            val duplicate = case.make().apply {
                lineRange = LineRange(11, 12)
                if (this is ContainerNode) appendChild(Text(text))
            }
            val nested = StrongEmphasis().apply { appendChild(duplicate) }
            val paragraph = Paragraph().apply {
                lineRange = LineRange(0, 1)
                blockAttributes = attributes
                appendChild(first)
                appendChild(nested)
            }
            val document = Document().apply {
                sourceRange = SourceRange(SourcePosition.ZERO, SourcePosition(1, 1, source.length))
                appendChild(paragraph)
            }
            for (sourceInput in listOf(source, null)) {
                val compiled = compiler.compile(document, sourceInput)
                val rendered = assertIs<MarkdownParagraph>(compiled.blocks.single())
                assertEquals(case.text.repeat(2), rendered.text.value, first::class.simpleName)
                assertEquals(attributes, rendered.metadata.attributes)
                assertFalse(rendered.metadata.hasSourceSpan)
                assertEquals(sourceInput?.substring(0, 6), rendered.metadata.sourceMarkdown)
                val spans = rendered.text.spans
                assertEquals(spans.size, spans.map { assertNotNull(it.metadata).key }.distinct().size)
                val firstSpan = spans.single { it.metadata?.key?.path == listOf(0, 0) }
                val duplicateSpan = spans.single { it.metadata?.key?.path == listOf(0, 1, 0) }
                val nestedSpan = spans.single { it.metadata?.key?.path == listOf(0, 1) }
                assertEquals(MarkdownTextRange(0, case.text.length), firstSpan.range)
                assertEquals(MarkdownTextRange(case.text.length, case.text.length * 2), duplicateSpan.range)
                assertEquals(duplicateSpan.range, nestedSpan.range)
                assertEquals(MarkdownTextMark.Strong, nestedSpan.mark)
                assertEquals(case.mark, firstSpan.mark)
                assertEquals(case.mark, duplicateSpan.mark)
                val metadata = assertNotNull(firstSpan.metadata)
                assertEquals(MarkdownNodeKey(11, listOf(0, 0)), metadata.key)
                assertEquals(first::class.simpleName, metadata.nodeType)
                assertEquals(case.attributes, metadata.attributes)
                case.payload.forEach { (key, value) -> assertEquals(value, metadata.payload[key], key) }
                assertEquals(MarkdownSourceSpan(1, 6, 0, 1, 0, 6), metadata.sourceSpan)
                assertTrue(metadata.hasSourceSpan)
                assertEquals(sourceInput?.substring(1, 6), metadata.sourceMarkdown)
                assertNull(metadata.persistentId)
                val unknown = assertNotNull(duplicateSpan.metadata)
                assertEquals(MarkdownSourceSpan(0, 0, 0, 0, 0, 0), unknown.sourceSpan)
                assertFalse(unknown.hasSourceSpan)
                assertNull(unknown.sourceMarkdown)
                if (first is ContainerNode) {
                    val child = spans.single { it.metadata?.key?.path == listOf(0, 0, 0) }
                    assertEquals(firstSpan.range, child.range)
                    assertEquals("Text", child.metadata?.nodeType)
                    assertFalse(assertNotNull(child.metadata).hasSourceSpan)
                    assertNull(child.mark)
                }
                assertEquals(if (case.unsupported) 2 else 0, rendered.text.issues.size)
                assertEquals(rendered.text.issues, compiled.issues)
                assertEquals("Document", compiled.metadata?.nodeType)
                assertEquals(sourceInput, compiled.metadata?.sourceMarkdown)
                assertNull(compiled.revision)
            }
            assertEquals(exactRange, first.sourceRange)
            assertEquals(SourceRange.EMPTY, duplicate.sourceRange)
        }

        val attributeBlocks = listOf(
            Heading(2).apply { blockAttributes = attributes },
            SetextHeading(1).apply { blockAttributes = attributes },
            BlockQuote().apply { blockAttributes = attributes },
            ListBlock().apply { blockAttributes = attributes },
            FencedCodeBlock(literal = "code", attributes = Attributes(pairs = attributes)),
            Figure(imageUrl = "/image", attributes = attributes),
        )
        for (node in attributeBlocks) {
            val compiled = compiler.compile(Document().apply { appendChild(node) })
            assertEquals(
                attributes,
                compiled.blocks
                    .single()
                    .metadata.attributes,
                node::class.simpleName,
            )
        }
        val table = Table().apply {
            blockAttributes = attributes
            columnAlignments = listOf(Table.Alignment.CENTER)
            for (section in listOf(TableHead(), TableBody())) {
                section.appendChild(
                    TableRow().apply {
                        appendChild(TableCell(Table.Alignment.CENTER).apply { appendChild(Text(text)) })
                    },
                )
                appendChild(section)
            }
        }
        val renderedTable = assertIs<MarkdownTable>(compiler.compile(Document().apply { appendChild(table) }).blocks.single())
        assertEquals(attributes, renderedTable.metadata.attributes)
        for ((index, row) in renderedTable.rows.withIndex()) {
            assertEquals(index == 0, row.header)
            assertEquals(if (index == 0) "TableHead" else "TableBody", row.sectionMetadata?.nodeType)
            assertEquals(listOf(0, index), row.sectionMetadata?.key?.path)
            assertEquals(listOf(0, index, 0), row.metadata.key.path)
            val cell = row.cells.single()
            assertEquals(listOf(0, index, 0, 0), cell.metadata.key.path)
            assertEquals(
                listOf(0, index, 0, 0, 0),
                cell.text.spans
                    .single()
                    .metadata
                    ?.key
                    ?.path,
            )
            assertEquals(text, cell.text.value)
            assertFalse(assertNotNull(row.sectionMetadata).hasSourceSpan)
        }

        val directive = DirectiveBlock("unknown", attributes).apply {
            lineRange = LineRange(1, 3)
            appendChild(Paragraph().apply { appendChild(Text("可读 fallback")) })
        }
        val fallbackDocument = Document().apply { appendChild(directive) }
        val fallbackSource = "prefix\r\n{% unknown %}\r\nbody\r\nend"
        val fallback = assertIs<MarkdownContainerBlock>(compiler.compile(fallbackDocument, fallbackSource).blocks.single())
        assertEquals("{% unknown %}\r\nbody", fallback.metadata.sourceMarkdown)
        assertFalse(fallback.metadata.hasSourceSpan)
        assertEquals(MarkdownSourceSpan(0, 0, 0, 0, 0, 0), fallback.metadata.sourceSpan)
        assertEquals(attributes, fallback.metadata.attributes)
        assertEquals("unknown", fallback.metadata.payload["tagName"])
        assertEquals("可读 fallback", assertIs<MarkdownParagraph>(fallback.blocks.single()).text.value)

        val published = MarkdownParser().parse("**重复😀**\n")
        val publishedResult = compiler.compile(published, "**重复😀**\n")
        assertEquals(published.publication?.revision, publishedResult.revision)
        assertEquals(published.persistentId, publishedResult.metadata?.persistentId)
        assertTrue(assertNotNull(publishedResult.metadata?.persistentId) != 0L)
        assertEquals(
            published.children.single().persistentId,
            publishedResult.blocks
                .single()
                .metadata.persistentId,
        )
        assertTrue(assertIs<MarkdownParagraph>(publishedResult.blocks.single()).text.spans.all { it.metadata?.persistentId == null })

        val customDocument = Document().apply { appendChild(DirectiveBlock("zhihu-video", mapOf("id" to "42", "thumb" to "/cover"))) }
        val custom = TiqianRenderDocumentCompiler().compile(customDocument)
        val video = assertIs<MarkdownCustomBlock>(custom.blocks.single())
        assertEquals(mapOf("id" to "42", "thumb" to "/cover"), video.attributes)
        assertEquals("DirectiveBlock", video.metadata.nodeType)
        assertEquals("42", video.metadata.payload["args.id"])
        assertTrue(custom.issues.isEmpty())
    }
}
