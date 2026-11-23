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
import com.hrm.markdown.parser.ast.ColumnsLayout
import com.hrm.markdown.parser.ast.ContainerNode
import com.hrm.markdown.parser.ast.CustomContainer
import com.hrm.markdown.parser.ast.DefinitionDescription
import com.hrm.markdown.parser.ast.DefinitionList
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
import com.hrm.markdown.parser.ast.FootnoteDefinition
import com.hrm.markdown.parser.ast.FootnoteReference
import com.hrm.markdown.parser.ast.FrontMatter
import com.hrm.markdown.parser.ast.HardLineBreak
import com.hrm.markdown.parser.ast.Heading
import com.hrm.markdown.parser.ast.Highlight
import com.hrm.markdown.parser.ast.HtmlBlock
import com.hrm.markdown.parser.ast.HtmlEntity
import com.hrm.markdown.parser.ast.Image
import com.hrm.markdown.parser.ast.IndentedCodeBlock
import com.hrm.markdown.parser.ast.InlineCode
import com.hrm.markdown.parser.ast.InlineHtml
import com.hrm.markdown.parser.ast.InlineMath
import com.hrm.markdown.parser.ast.InsertedText
import com.hrm.markdown.parser.ast.KeyboardInput
import com.hrm.markdown.parser.ast.LeafNode
import com.hrm.markdown.parser.ast.Link
import com.hrm.markdown.parser.ast.LinkReferenceDefinition
import com.hrm.markdown.parser.ast.ListBlock
import com.hrm.markdown.parser.ast.ListItem
import com.hrm.markdown.parser.ast.MathBlock
import com.hrm.markdown.parser.ast.Node
import com.hrm.markdown.parser.ast.PageBreak
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
import com.hrm.markdown.parser.ast.TabBlock
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
import com.hrm.markdown.parser.lint.Diagnostic
import com.hrm.markdown.parser.lint.DiagnosticCode
import com.hrm.markdown.parser.lint.DiagnosticSeverity
import org.tiqian.markdown.MarkdownBibliographyBlock
import org.tiqian.markdown.MarkdownContainerBlock
import org.tiqian.markdown.MarkdownContainerKind
import org.tiqian.markdown.MarkdownCustomBlock
import org.tiqian.markdown.MarkdownDiagramBlock
import org.tiqian.markdown.MarkdownNodeKey
import org.tiqian.markdown.MarkdownNodeValue
import org.tiqian.markdown.MarkdownPageBreakBlock
import org.tiqian.markdown.MarkdownParagraph
import org.tiqian.markdown.MarkdownSourceSpan
import org.tiqian.markdown.MarkdownTextMark
import org.tiqian.markdown.MarkdownTocBlock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TiqianAstSnapshotTest {
    @Test
    fun realParserExtensionsKeepRecursiveRenderNodesAndPublishedSourceIdentity() {
        val inputs = listOf(
            "Term\n: Definition with **nested** text" to "DefinitionList",
            "> [!NOTE]\n> A [link](https://example.test)" to "Admonition",
            ":::columns\n:::column\nLeft\n:::column\nRight\n:::" to "ColumnsLayout",
            "=== \"First\"\n    **first body**\n\n=== \"Second\"\n    second body" to "TabBlock",
            "[@book]\n\n[^bibliography]: book: **Reference title**" to "BibliographyDefinition",
            "```mermaid\ngraph LR; A-->B\n```" to "DiagramBlock",
            "[TOC]\n\n## Target" to "TocPlaceholder",
            "Before\n\n***pagebreak***\n\nAfter" to "PageBreak",
        )
        for ((source, expected) in inputs) {
            val original = MarkdownParser(com.hrm.markdown.parser.flavour.ExtendedFlavour).parse(source)
            val rendered = TiqianRenderDocumentCompiler().compile(original, source)

            fun all(blocks: List<org.tiqian.markdown.MarkdownBlock>): List<org.tiqian.markdown.MarkdownBlock> = blocks.flatMap { block ->
                listOf(block) + when (block) {
                    is MarkdownContainerBlock -> all(block.blocks)
                    is org.tiqian.markdown.MarkdownBlockQuote -> all(block.blocks)
                    is org.tiqian.markdown.MarkdownList -> block.items.flatMap { all(it.blocks) }
                    else -> emptyList()
                }
            }
            val nodes = all(rendered.blocks)
            val target = nodes.single { it.metadata.nodeType == expected }
            assertTrue(target !is org.tiqian.markdown.MarkdownUnsupportedBlock, expected)
            assertSame(assertNotNull(rendered.metadata).children[original.children.indexOfFirst { it::class.simpleName == expected }], target.metadata)
            assertEquals(original.publication?.revision, rendered.revision)
            if (target is MarkdownContainerBlock) {
                assertTrue(target.blocks.isNotEmpty(), "$expected 必须保留解析器的子节点")
                assertTrue(target.metadata.children.isNotEmpty())
            }
            assertTrue(rendered.issues.isEmpty(), "$expected: ${rendered.issues}")
        }
    }

    private data class Case(
        val node: Node,
        val type: String,
        val fields: Map<String, MarkdownNodeValue> = emptyMap(),
    )

    @Test
    fun snapshotsEveryOriginalNodeFamilyPublicFieldsAndHiddenChildrenWithoutBorrowedSource() {
        val attributes = mapOf("class" to "one two", "id" to "id,with,comma", "style" to "opaque", "data-node" to "same😀")
        val text = "same😀"
        val t = MarkdownNodeValue.Text(text)
        val nil = MarkdownNodeValue.Null
        val attrs = MarkdownNodeValue.ObjectValue(attributes.mapValues { MarkdownNodeValue.Text(it.value) })
        val css = MarkdownNodeValue.ListValue(listOf(MarkdownNodeValue.Text("one"), MarkdownNodeValue.Text("two")))
        val id = MarkdownNodeValue.Text("id,with,comma")
        val cases = listOf(
            Case(
                Heading(3).apply {
                    rawContent = text
                    blockAttributes = attributes
                },
                "Heading",
                mapOf("level" to MarkdownNodeValue.Integer(3), "customId" to nil, "autoId" to nil, "id" to nil, "rawContent" to t, "blockAttributes" to attrs),
            ),
            Case(
                SetextHeading(2).apply {
                    autoId = text
                    blockAttributes = attributes
                },
                "SetextHeading",
                mapOf("level" to MarkdownNodeValue.Integer(2), "autoId" to t, "id" to t, "rawContent" to nil, "blockAttributes" to attrs),
            ),
            Case(Paragraph().apply { blockAttributes = attributes }, "Paragraph", mapOf("rawContent" to nil, "blockAttributes" to attrs)),
            Case(ThematicBreak('_'), "ThematicBreak", mapOf("char" to MarkdownNodeValue.Text("_"))),
            Case(
                FencedCodeBlock(text, "lang", '~', 4, 2, text, Attributes(null, listOf("one", "two"), attributes)).apply {
                    startLineNumber = 9
                    showLineNumbers = false
                    highlightLines = listOf(2..4)
                },
                "FencedCodeBlock",
                mapOf(
                    "info" to t,
                    "language" to MarkdownNodeValue.Text("lang"),
                    "fenceChar" to MarkdownNodeValue.Text("~"),
                    "fenceLength" to MarkdownNodeValue.Integer(4),
                    "fenceIndent" to MarkdownNodeValue.Integer(2),
                    "attributes" to MarkdownNodeValue.ObjectValue(mapOf("id" to nil, "classes" to css, "pairs" to attrs, "isEmpty" to MarkdownNodeValue.Boolean(false))),
                    "highlightLines" to MarkdownNodeValue.ListValue(listOf(MarkdownNodeValue.ObjectValue(mapOf("first" to MarkdownNodeValue.Integer(2), "last" to MarkdownNodeValue.Integer(4), "step" to MarkdownNodeValue.Integer(1))))),
                    "showLineNumbers" to MarkdownNodeValue.Boolean(false),
                    "startLineNumber" to MarkdownNodeValue.Integer(9),
                ),
            ),
            Case(IndentedCodeBlock(text), "IndentedCodeBlock"),
            Case(BlockQuote().apply { blockAttributes = attributes }, "BlockQuote", mapOf("blockAttributes" to attrs)),
            Case(ListBlock(true, 7, '+', ')', false).apply { blockAttributes = attributes }, "ListBlock", mapOf("ordered" to MarkdownNodeValue.Boolean(true), "startNumber" to MarkdownNodeValue.Integer(7), "bulletChar" to MarkdownNodeValue.Text("+"), "delimiter" to MarkdownNodeValue.Text(")"), "tight" to MarkdownNodeValue.Boolean(false), "blockAttributes" to attrs)),
            Case(
                ListItem(2, 4).apply {
                    taskListItem = true
                    checked = true
                    containsBlankLine = true
                },
                "ListItem",
                mapOf("markerIndent" to MarkdownNodeValue.Integer(2), "contentIndent" to MarkdownNodeValue.Integer(4), "taskListItem" to MarkdownNodeValue.Boolean(true), "checked" to MarkdownNodeValue.Boolean(true), "containsBlankLine" to MarkdownNodeValue.Boolean(true)),
            ),
            Case(HtmlBlock(6, text), "HtmlBlock", mapOf("htmlType" to MarkdownNodeValue.Integer(6))),
            Case(LinkReferenceDefinition(text, "/dest"), "LinkReferenceDefinition", mapOf("label" to t, "destination" to MarkdownNodeValue.Text("/dest"), "title" to nil)),
            Case(
                Table().apply {
                    columnAlignments = Table.Alignment.entries.toList()
                    blockAttributes = attributes
                },
                "Table",
                mapOf("columnAlignments" to MarkdownNodeValue.ListValue(Table.Alignment.entries.map { MarkdownNodeValue.Text(it.name) }), "blockAttributes" to attrs),
            ),
            Case(TableHead(), "TableHead"),
            Case(TableBody(), "TableBody"),
            Case(TableRow(), "TableRow"),
            Case(TableCell(Table.Alignment.RIGHT, true).apply { rawContent = text }, "TableCell", mapOf("alignment" to MarkdownNodeValue.Text("RIGHT"), "isHeader" to MarkdownNodeValue.Boolean(true), "rawContent" to t)),
            Case(FootnoteDefinition(text, 5), "FootnoteDefinition", mapOf("label" to t, "index" to MarkdownNodeValue.Integer(5))),
            Case(MathBlock(text), "MathBlock"),
            Case(DefinitionList(), "DefinitionList"),
            Case(DefinitionTerm(), "DefinitionTerm"),
            Case(DefinitionDescription(), "DefinitionDescription"),
            Case(Admonition("WARNING", text), "Admonition", mapOf("type" to MarkdownNodeValue.Text("WARNING"), "title" to t)),
            Case(FrontMatter("toml", text), "FrontMatter", mapOf("format" to MarkdownNodeValue.Text("toml"))),
            Case(BlankLine(), "BlankLine"),
            Case(
                TocPlaceholder().apply {
                    minDepth = 2
                    maxDepth = 4
                    excludeIds = listOf(text, "id,with,comma")
                    order = "desc"
                },
                "TocPlaceholder",
                mapOf("minDepth" to MarkdownNodeValue.Integer(2), "maxDepth" to MarkdownNodeValue.Integer(4), "excludeIds" to MarkdownNodeValue.ListValue(listOf(t, id)), "order" to MarkdownNodeValue.Text("desc")),
            ),
            Case(AbbreviationDefinition(text, "full"), "AbbreviationDefinition", mapOf("abbreviation" to t, "fullText" to MarkdownNodeValue.Text("full"))),
            Case(CustomContainer("card", text, listOf("one", "two")), "CustomContainer", mapOf("type" to MarkdownNodeValue.Text("card"), "title" to t, "cssClasses" to css, "cssId" to nil)),
            Case(DiagramBlock("mermaid", text), "DiagramBlock", mapOf("diagramType" to MarkdownNodeValue.Text("mermaid"))),
            Case(ColumnsLayout(), "ColumnsLayout"),
            Case(ColumnItem("33%"), "ColumnItem", mapOf("width" to MarkdownNodeValue.Text("33%"))),
            Case(PageBreak(), "PageBreak"),
            Case(Text(text), "Text"),
            Case(SoftLineBreak(), "SoftLineBreak"),
            Case(HardLineBreak(), "HardLineBreak"),
            Case(Emphasis().apply { delimiter = '_' }, "Emphasis", mapOf("delimiter" to MarkdownNodeValue.Text("_"))),
            Case(StrongEmphasis().apply { delimiter = '_' }, "StrongEmphasis", mapOf("delimiter" to MarkdownNodeValue.Text("_"))),
            Case(Strikethrough(), "Strikethrough"),
            Case(InlineCode(text), "InlineCode"),
            Case(Link("/link", null, attributes), "Link", mapOf("destination" to MarkdownNodeValue.Text("/link"), "title" to nil, "attributes" to attrs, "cssClasses" to css, "cssId" to id)),
            Case(Image("/image", null, null, 16, attributes), "Image", mapOf("destination" to MarkdownNodeValue.Text("/image"), "title" to nil, "imageWidth" to nil, "imageHeight" to MarkdownNodeValue.Integer(16), "attributes" to attrs, "cssClasses" to css, "cssId" to id)),
            Case(Autolink("/auto", true, text), "Autolink", mapOf("destination" to MarkdownNodeValue.Text("/auto"), "isEmail" to MarkdownNodeValue.Boolean(true), "rawText" to t)),
            Case(InlineHtml(text), "InlineHtml"),
            Case(HtmlEntity("&amp;", text), "HtmlEntity", mapOf("resolved" to t)),
            Case(EscapedChar(text), "EscapedChar"),
            Case(FootnoteReference(text, 5), "FootnoteReference", mapOf("label" to t, "index" to MarkdownNodeValue.Integer(5))),
            Case(InlineMath(text), "InlineMath"),
            Case(Highlight(attributes), "Highlight", mapOf("attributes" to attrs)),
            Case(Superscript(attributes), "Superscript", mapOf("attributes" to attrs)),
            Case(Subscript(attributes), "Subscript", mapOf("attributes" to attrs)),
            Case(InsertedText(attributes), "InsertedText", mapOf("attributes" to attrs)),
            Case(Emoji("smile", text), "Emoji", mapOf("shortcode" to MarkdownNodeValue.Text("smile"), "unicode" to nil)),
            Case(StyledText(attributes), "StyledText", mapOf("attributes" to attrs, "cssClasses" to css, "cssId" to id, "style" to MarkdownNodeValue.Text("opaque"))),
            Case(Abbreviation(text, "full"), "Abbreviation", mapOf("abbreviation" to t, "fullText" to MarkdownNodeValue.Text("full"))),
            Case(KeyboardInput(text), "KeyboardInput"),
            Case(DirectiveBlock(text, attributes), "DirectiveBlock", mapOf("tagName" to t, "args" to attrs)),
            Case(DirectiveInline(text, attributes), "DirectiveInline", mapOf("tagName" to t, "args" to attrs)),
            Case(TabBlock(), "TabBlock"),
            Case(TabItem(text), "TabItem", mapOf("title" to t)),
            Case(BibliographyDefinition().apply { entries["different-map-key"] = BibEntry(text, text) }, "BibliographyDefinition", mapOf("entries" to MarkdownNodeValue.ObjectValue(mapOf("different-map-key" to MarkdownNodeValue.ObjectValue(mapOf("key" to t, "content" to t)))))),
            Case(CitationReference(text), "CitationReference", mapOf("key" to t)),
            Case(Spoiler(), "Spoiler"),
            Case(WikiLink(text), "WikiLink", mapOf("target" to t, "label" to nil)),
            Case(RubyText(text, "ruby"), "RubyText", mapOf("base" to t, "annotation" to MarkdownNodeValue.Text("ruby"))),
            Case(Figure("/figure", text, 24, null, attributes), "Figure", mapOf("imageUrl" to MarkdownNodeValue.Text("/figure"), "caption" to t, "imageWidth" to MarkdownNodeValue.Integer(24), "imageHeight" to nil, "attributes" to attrs)),
        )
        val source = "前same😀尾\r\nlast"
        val exactRange = SourceRange(SourcePosition(0, 1, 1), SourcePosition(0, 7, 7))
        val document = Document().apply {
            sourceRange = SourceRange(SourcePosition.ZERO, SourcePosition(1, 4, source.length))
            contentHash = 123
            cases.forEach { case ->
                case.node.lineRange = LineRange(7, 9)
                case.node.sourceRange = exactRange
                case.node.contentHash = 99
                if (case.node is ContainerNode) {
                    case.node.appendChild(Text("").apply { lineRange = LineRange(7, 9) })
                    case.node.appendChild(StrongEmphasis().apply { appendChild(Text(text)) })
                }
                appendChild(case.node)
            }
            linkDefinitions["visible"] = cases.single { it.node is LinkReferenceDefinition }.node as LinkReferenceDefinition
            footnoteDefinitions["detached"] = FootnoteDefinition("hidden", 8).apply {
                appendChild(Paragraph().apply { appendChild(Text("hidden child")) })
            }
            abbreviationDefinitions["detached"] = AbbreviationDefinition("H", "Hidden")
            diagnostics.add(Diagnostic(DiagnosticSeverity.WARNING, DiagnosticCode.entries.first(), "typed diagnostic", 4))
        }
        for (sourceInput in listOf(source, null)) {
            val result = TiqianRenderDocumentCompiler().compile(document, sourceInput)
            val root = assertNotNull(result.metadata)
            assertEquals("Document", root.nodeType)
            assertEquals(MarkdownNodeKey(0, emptyList()), root.key)
            assertEquals(123L, root.contentHash)
            assertEquals(MarkdownNodeValue.Null, root.properties["publication"])
            assertEquals(cases.size + 2, root.children.size)
            cases.forEachIndexed { index, case ->
                val metadata = root.children[index]
                assertEquals(case.type, metadata.nodeType)
                assertEquals(MarkdownNodeKey(7, listOf(index)), metadata.key)
                assertEquals(MarkdownSourceSpan(1, 7, 0, 1, 0, 7), metadata.sourceSpan)
                assertTrue(metadata.hasSourceSpan)
                assertEquals(sourceInput?.substring(1, 7), metadata.sourceMarkdown)
                assertNull(metadata.persistentId)
                assertEquals(99L, metadata.contentHash)
                val common = setOf("sourceRange", "lineRange", "stableKey", "persistentId", "contentHash") +
                    if (case.node is ContainerNode) setOf("isInlineParsed") else setOf("literal")
                assertEquals(common + case.fields.keys, metadata.properties.keys, case.type)
                case.fields.forEach { (key, value) -> assertEquals(value, metadata.properties[key], "${case.type}.$key") }
                assertEquals(MarkdownNodeValue.Integer(99), metadata.properties["contentHash"])
                assertEquals(MarkdownNodeValue.Integer(0), metadata.properties["persistentId"])
                val range = assertIs<MarkdownNodeValue.ObjectValue>(metadata.properties["sourceRange"]).values
                assertEquals(MarkdownNodeValue.Integer(6), range["length"])
                assertEquals(MarkdownNodeValue.Integer(1), assertIs<MarkdownNodeValue.ObjectValue>(range["start"]).values["offset"])
                val lines = assertIs<MarkdownNodeValue.ObjectValue>(metadata.properties["lineRange"]).values
                assertEquals(MarkdownNodeValue.Integer(7), lines["startLine"])
                assertEquals(MarkdownNodeValue.Integer(9), lines["endLine"])
                assertEquals(MarkdownNodeValue.Integer(2), lines["lineCount"])
                if (case.node is LeafNode) assertEquals(MarkdownNodeValue.Text(case.node.literal), metadata.properties["literal"])
                if (case.node is ContainerNode) {
                    assertEquals(2, metadata.children.size)
                    assertEquals(listOf(index, 0), metadata.children[0].key.path)
                    assertEquals(
                        listOf(index, 1, 0),
                        metadata.children[1]
                            .children
                            .single()
                            .key.path,
                    )
                    assertEquals(MarkdownNodeValue.Text(""), metadata.children[0].properties["literal"])
                    assertFalse(metadata.children[0].hasSourceSpan)
                    assertEquals(MarkdownSourceSpan(0, 0, 0, 0, 0, 0), metadata.children[0].sourceSpan)
                }
                result.blocks.singleOrNull { it.metadata.key == metadata.key }?.let { assertSame(metadata, it.metadata) }
            }
            val hidden = root.children[cases.size]
            assertEquals(listOf(-2, 1, 0), hidden.key.path)
            assertEquals("FootnoteDefinition", hidden.nodeType)
            val renderedFootnote = result.blocks.filterIsInstance<org.tiqian.markdown.MarkdownFootnoteDefinition>().single { it.label == "hidden" }
            assertSame(hidden, renderedFootnote.metadata)
            val renderedFootnoteBody = assertIs<MarkdownParagraph>(renderedFootnote.blocks.single())
            assertEquals("hidden child", renderedFootnoteBody.text.value)
            assertSame(hidden.children.single(), renderedFootnoteBody.metadata)
            assertEquals(
                listOf(-2, 1, 0, 0, 0),
                hidden.children
                    .single()
                    .children
                    .single()
                    .key.path,
            )
            assertEquals(
                listOf(-2, 2, 0),
                root.children
                    .last()
                    .key.path,
            )
            val dictionary = assertIs<MarkdownNodeValue.ObjectValue>(root.properties["footnoteDefinitions"]).values
            assertEquals(MarkdownNodeValue.Text("hidden"), assertIs<MarkdownNodeValue.ObjectValue>(dictionary["detached"]).values["label"])
            val diagnostic = assertIs<MarkdownNodeValue.ObjectValue>(root.properties["diagnostics"]).values
            assertEquals(MarkdownNodeValue.Boolean(true), diagnostic["hasIssues"])
            val firstDiagnostic = assertIs<MarkdownNodeValue.ObjectValue>(assertIs<MarkdownNodeValue.ListValue>(diagnostic["diagnostics"]).values.single()).values
            assertEquals(nil, firstDiagnostic["position"])
        }
        val old = assertNotNull(TiqianRenderDocumentCompiler().compile(document).metadata)
        (cases.single { it.node is Heading }.node as Heading).customId = "changed"
        (cases.single { it.node is BibliographyDefinition }.node as BibliographyDefinition).entries.clear()
        document.footnoteDefinitions.clear()
        assertEquals(nil, old.children.first().properties["customId"])
        assertEquals(1, assertIs<MarkdownNodeValue.ObjectValue>(old.children[cases.indexOfFirst { it.node is BibliographyDefinition }].properties["entries"]).values.size)
        assertTrue(assertIs<MarkdownNodeValue.ObjectValue>(old.properties["footnoteDefinitions"]).values.isNotEmpty())
    }

    @Test
    fun semanticContainersDerivedTextAndCustomBlocksReuseOriginalIdentityWithoutFlattening() {
        val term = DefinitionTerm().apply { appendChild(StrongEmphasis().apply { appendChild(Text("术语😀")) }) }
        val description = DefinitionDescription().apply {
            appendChild(Text("inline"))
            appendChild(Paragraph().apply { appendChild(Text("block")) })
        }
        val definition = DefinitionList().apply {
            appendChild(term)
            appendChild(description)
        }
        val containers = listOf(
            Admonition("NOTE", "**title**") to MarkdownContainerKind.Admonition,
            CustomContainer("card", "**title**") to MarkdownContainerKind.Custom,
            ColumnsLayout() to MarkdownContainerKind.Columns,
            ColumnItem("50%") to MarkdownContainerKind.Column,
            TabBlock() to MarkdownContainerKind.Tabs,
            TabItem("**literal title**") to MarkdownContainerKind.Tab,
        )
        val document = Document().apply {
            appendChild(definition)
            containers.forEach { (node, _) ->
                node.appendChild(BlockQuote().apply { appendChild(Paragraph().apply { appendChild(Text("nested")) }) })
                appendChild(node)
            }
            appendChild(BibliographyDefinition().apply { entries["key"] = BibEntry("key", "**book😀**") })
            appendChild(Figure("/image", "*caption*"))
            appendChild(DiagramBlock("mermaid", "graph LR; A-->B"))
            appendChild(PageBreak())
            appendChild(
                TocPlaceholder().apply {
                    minDepth = 2
                    maxDepth = 5
                    excludeIds = listOf("same,id")
                    order = "desc"
                },
            )
            appendChild(DirectiveBlock("zhihu-video", mapOf("id" to "42")).apply { appendChild(Paragraph().apply { appendChild(Text("custom child")) }) })
        }
        for (source in listOf("irrelevant source", null)) {
            val result = TiqianRenderDocumentCompiler().compile(document, source)
            val root = assertNotNull(result.metadata)
            val list = assertIs<MarkdownContainerBlock>(result.blocks[0])
            assertSame(root.children[0], list.metadata)
            assertEquals(MarkdownContainerKind.DefinitionList, list.kind)
            val renderedTerm = assertIs<MarkdownContainerBlock>(list.blocks[0])
            assertEquals(MarkdownContainerKind.DefinitionTerm, renderedTerm.kind)
            assertEquals("术语😀", renderedTerm.title?.value)
            assertTrue(renderedTerm.blocks.isEmpty())
            assertSame(
                renderedTerm.metadata.children.single(),
                renderedTerm.title
                    ?.spans
                    ?.single { it.mark == MarkdownTextMark.Strong }
                    ?.metadata,
            )
            val renderedDescription = assertIs<MarkdownContainerBlock>(list.blocks[1])
            assertEquals("inline", renderedDescription.title?.value)
            assertEquals("block", assertIs<MarkdownParagraph>(renderedDescription.blocks.single()).text.value)
            containers.forEachIndexed { index, (node, kind) ->
                val block = assertIs<MarkdownContainerBlock>(result.blocks[index + 1])
                assertEquals(kind, block.kind)
                assertSame(root.children[index + 1], block.metadata)
                assertEquals(
                    "BlockQuote",
                    block.blocks
                        .single()
                        .metadata.nodeType,
                )
                if (node is ColumnItem) assertEquals("50%", block.width)
                if (node is TabItem) {
                    assertEquals("**literal title**", block.title?.value)
                    assertTrue(assertNotNull(block.title).spans.isEmpty())
                }
                if (node is Admonition || node is CustomContainer) {
                    val title = assertNotNull(block.title)
                    assertEquals("title", title.value)
                    assertTrue(title.spans.any { it.mark == MarkdownTextMark.Strong })
                    title.spans.forEach { span ->
                        val metadata = assertNotNull(span.metadata)
                        assertEquals(listOf(index + 1, -3, 0), metadata.key.path.take(3))
                        assertFalse(metadata.hasSourceSpan)
                        assertNull(metadata.sourceMarkdown)
                        assertNull(metadata.persistentId)
                        assertEquals(MarkdownNodeValue.Null, metadata.properties["sourceRange"])
                    }
                }
            }
            val bibliography = assertIs<MarkdownBibliographyBlock>(result.blocks[7])
            val entry = bibliography.entries.single()
            assertEquals(listOf(7, -1, 0), entry.metadata.key.path)
            assertEquals("book😀", entry.text.value)
            assertNull(entry.metadata.persistentId)
            assertFalse(entry.metadata.hasSourceSpan)
            assertSame(root.children[7], bibliography.metadata)
            assertEquals("caption", assertIs<org.tiqian.markdown.MarkdownImageBlock>(result.blocks[8]).caption?.value)
            assertIs<MarkdownDiagramBlock>(result.blocks[9])
            assertIs<MarkdownPageBreakBlock>(result.blocks[10])
            val toc = assertIs<MarkdownTocBlock>(result.blocks[11])
            assertEquals(listOf("same,id"), toc.excludedIds)
            assertEquals(2, toc.minDepth)
            assertEquals(5, toc.maxDepth)
            assertTrue(toc.reversed)
            val custom = assertIs<MarkdownCustomBlock>(result.blocks[12])
            assertEquals("custom child", assertIs<MarkdownParagraph>(custom.blocks.single()).text.value)
            assertSame(root.children[12].children.single(), custom.blocks.single().metadata)
            assertTrue(result.issues.isEmpty())
        }
        val published = MarkdownParser().parse("**published😀**\n")
        val snapshot = assertNotNull(TiqianRenderDocumentCompiler().compile(published).metadata)
        assertEquals(published.contentHash, snapshot.contentHash)
        assertEquals(published.persistentId, snapshot.persistentId)
        val publication = assertIs<MarkdownNodeValue.ObjectValue>(snapshot.properties["publication"]).values
        assertEquals(MarkdownNodeValue.Integer(assertNotNull(published.publication).revision), publication["revision"])
    }
}
