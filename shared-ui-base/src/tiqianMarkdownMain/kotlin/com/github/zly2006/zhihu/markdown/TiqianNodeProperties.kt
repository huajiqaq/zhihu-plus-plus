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

import com.hrm.markdown.parser.ast.Abbreviation
import com.hrm.markdown.parser.ast.AbbreviationDefinition
import com.hrm.markdown.parser.ast.Admonition
import com.hrm.markdown.parser.ast.Autolink
import com.hrm.markdown.parser.ast.BibliographyDefinition
import com.hrm.markdown.parser.ast.BlankLine
import com.hrm.markdown.parser.ast.BlockQuote
import com.hrm.markdown.parser.ast.CitationReference
import com.hrm.markdown.parser.ast.ColumnItem
import com.hrm.markdown.parser.ast.ColumnsLayout
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
import com.hrm.markdown.parser.ast.Link
import com.hrm.markdown.parser.ast.LinkReferenceDefinition
import com.hrm.markdown.parser.ast.ListBlock
import com.hrm.markdown.parser.ast.ListItem
import com.hrm.markdown.parser.ast.MathBlock
import com.hrm.markdown.parser.ast.Node
import com.hrm.markdown.parser.ast.NodeVisitor
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
import org.tiqian.markdown.MarkdownNodeValue

internal data class TiqianNodeFields(
    val type: String,
    val properties: Map<String, MarkdownNodeValue>,
    val attributes: Map<String, String>,
)

/** 实现所有 NodeVisitor 成员，节点字段不得被默认访问器丢弃。 */
internal object TiqianNodeProperties : NodeVisitor<TiqianNodeFields> {
    private fun fields(
        type: String,
        vararg properties: Pair<String, MarkdownNodeValue>,
        attributes: Map<String, String> = emptyMap(),
    ) = TiqianNodeFields(type, properties.toMap(), attributes.toMap())

    private fun definitions(values: Map<String, Node>): MarkdownNodeValue = MarkdownNodeValue.ObjectValue(
        values.mapValues { (_, node) ->
            val fields = node.accept(this)
            MarkdownNodeValue.ObjectValue(node.tiqianCommonProperties() + fields.properties + ("nodeType" to tiqianValue(fields.type)))
        },
    )

    override fun visitDocument(node: Document): TiqianNodeFields {
        val publication = node.publication
        val diagnostics = node.diagnostics
        return fields(
            "Document",
            "linkDefinitions" to definitions(node.linkDefinitions),
            "footnoteDefinitions" to definitions(node.footnoteDefinitions),
            "abbreviationDefinitions" to definitions(node.abbreviationDefinitions),
            "publication" to if (publication == null) {
                MarkdownNodeValue.Null
            } else {
                MarkdownNodeValue.ObjectValue(
                    mapOf(
                        "lineageId" to MarkdownNodeValue.ObjectValue(mapOf("identityKey" to tiqianValue(publication.lineageId.identityKey))),
                        "baseRevision" to (publication.baseRevision?.let(::tiqianValue) ?: MarkdownNodeValue.Null),
                        "revision" to tiqianValue(publication.revision),
                        "startIndex" to tiqianValue(publication.startIndex),
                        "removedCount" to tiqianValue(publication.removedCount),
                        "insertedCount" to tiqianValue(publication.insertedCount),
                        "globalDependenciesChanged" to tiqianValue(publication.globalDependenciesChanged),
                        "isCurrent" to tiqianValue(publication.isCurrent),
                    ),
                )
            },
            "diagnostics" to MarkdownNodeValue.ObjectValue(
                mapOf(
                    "errorCount" to tiqianValue(diagnostics.errorCount),
                    "warningCount" to tiqianValue(diagnostics.warningCount),
                    "hasIssues" to tiqianValue(diagnostics.hasIssues),
                    "hasErrors" to tiqianValue(diagnostics.hasErrors),
                    "diagnostics" to MarkdownNodeValue.ListValue(
                        diagnostics.diagnostics.map { diagnostic ->
                            MarkdownNodeValue.ObjectValue(
                                mapOf(
                                    "severity" to tiqianValue(diagnostic.severity.name),
                                    "code" to tiqianValue(diagnostic.code.name),
                                    "message" to tiqianValue(diagnostic.message),
                                    "line" to tiqianValue(diagnostic.line),
                                    "column" to tiqianValue(diagnostic.column),
                                    "position" to tiqianPosition(diagnostic.position),
                                ),
                            )
                        },
                    ),
                ),
            ),
        )
    }

    override fun visitHeading(node: Heading) = fields(
        "Heading",
        "level" to tiqianValue(node.level),
        "customId" to tiqianValue(node.customId),
        "autoId" to tiqianValue(node.autoId),
        "id" to tiqianValue(node.id),
        "rawContent" to tiqianValue(node.rawContent),
        "blockAttributes" to tiqianMap(node.blockAttributes),
        attributes = node.blockAttributes,
    )

    override fun visitSetextHeading(node: SetextHeading) = fields(
        "SetextHeading",
        "level" to tiqianValue(node.level),
        "autoId" to tiqianValue(node.autoId),
        "id" to tiqianValue(node.id),
        "rawContent" to tiqianValue(node.rawContent),
        "blockAttributes" to tiqianMap(node.blockAttributes),
        attributes = node.blockAttributes,
    )

    override fun visitParagraph(node: Paragraph) = fields(
        "Paragraph",
        "rawContent" to tiqianValue(node.rawContent),
        "blockAttributes" to tiqianMap(node.blockAttributes),
        attributes = node.blockAttributes,
    )

    override fun visitThematicBreak(node: ThematicBreak) = fields("ThematicBreak", "char" to tiqianValue(node.char.toString()))

    override fun visitFencedCodeBlock(node: FencedCodeBlock) = fields(
        "FencedCodeBlock",
        "info" to tiqianValue(node.info),
        "language" to tiqianValue(node.language),
        "fenceChar" to tiqianValue(node.fenceChar.toString()),
        "fenceLength" to tiqianValue(node.fenceLength),
        "fenceIndent" to tiqianValue(node.fenceIndent),
        "attributes" to tiqianAttributes(node.attributes),
        "highlightLines" to MarkdownNodeValue.ListValue(
            node.highlightLines.map { range ->
                MarkdownNodeValue.ObjectValue(
                    mapOf("first" to tiqianValue(range.first), "last" to tiqianValue(range.last), "step" to tiqianValue(range.step)),
                )
            },
        ),
        "showLineNumbers" to tiqianValue(node.showLineNumbers),
        "startLineNumber" to tiqianValue(node.startLineNumber),
        attributes = node.attributes.toMap(),
    )

    override fun visitIndentedCodeBlock(node: IndentedCodeBlock) = fields("IndentedCodeBlock")

    override fun visitBlockQuote(node: BlockQuote) = fields(
        "BlockQuote",
        "blockAttributes" to tiqianMap(node.blockAttributes),
        attributes = node.blockAttributes,
    )

    override fun visitListBlock(node: ListBlock) = fields(
        "ListBlock",
        "ordered" to tiqianValue(node.ordered),
        "startNumber" to tiqianValue(node.startNumber),
        "bulletChar" to tiqianValue(node.bulletChar.toString()),
        "delimiter" to tiqianValue(node.delimiter.toString()),
        "tight" to tiqianValue(node.tight),
        "blockAttributes" to tiqianMap(node.blockAttributes),
        attributes = node.blockAttributes,
    )

    override fun visitListItem(node: ListItem) = fields(
        "ListItem",
        "markerIndent" to tiqianValue(node.markerIndent),
        "contentIndent" to tiqianValue(node.contentIndent),
        "taskListItem" to tiqianValue(node.taskListItem),
        "checked" to tiqianValue(node.checked),
        "containsBlankLine" to tiqianValue(node.containsBlankLine),
    )

    override fun visitHtmlBlock(node: HtmlBlock) = fields("HtmlBlock", "htmlType" to tiqianValue(node.htmlType))

    override fun visitLinkReferenceDefinition(node: LinkReferenceDefinition) = fields(
        "LinkReferenceDefinition",
        "label" to tiqianValue(node.label),
        "destination" to tiqianValue(node.destination),
        "title" to tiqianValue(node.title),
    )

    override fun visitTable(node: Table) = fields(
        "Table",
        "columnAlignments" to tiqianStrings(node.columnAlignments.map { it.name }),
        "blockAttributes" to tiqianMap(node.blockAttributes),
        attributes = node.blockAttributes,
    )

    override fun visitTableHead(node: TableHead) = fields("TableHead")

    override fun visitTableBody(node: TableBody) = fields("TableBody")

    override fun visitTableRow(node: TableRow) = fields("TableRow")

    override fun visitTableCell(node: TableCell) = fields(
        "TableCell",
        "alignment" to tiqianValue(node.alignment.name),
        "isHeader" to tiqianValue(node.isHeader),
        "rawContent" to tiqianValue(node.rawContent),
    )

    override fun visitFootnoteDefinition(node: FootnoteDefinition) = fields(
        "FootnoteDefinition",
        "label" to tiqianValue(node.label),
        "index" to tiqianValue(node.index),
    )

    override fun visitMathBlock(node: MathBlock) = fields("MathBlock")

    override fun visitDefinitionList(node: DefinitionList) = fields("DefinitionList")

    override fun visitDefinitionTerm(node: DefinitionTerm) = fields("DefinitionTerm")

    override fun visitDefinitionDescription(node: DefinitionDescription) = fields("DefinitionDescription")

    override fun visitAdmonition(node: Admonition) = fields(
        "Admonition",
        "type" to tiqianValue(node.type),
        "title" to tiqianValue(node.title),
    )

    override fun visitFrontMatter(node: FrontMatter) = fields("FrontMatter", "format" to tiqianValue(node.format))

    override fun visitBlankLine(node: BlankLine) = fields("BlankLine")

    override fun visitTocPlaceholder(node: TocPlaceholder) = fields(
        "TocPlaceholder",
        "minDepth" to tiqianValue(node.minDepth),
        "maxDepth" to tiqianValue(node.maxDepth),
        "excludeIds" to tiqianStrings(node.excludeIds),
        "order" to tiqianValue(node.order),
    )

    override fun visitAbbreviationDefinition(node: AbbreviationDefinition) = fields(
        "AbbreviationDefinition",
        "abbreviation" to tiqianValue(node.abbreviation),
        "fullText" to tiqianValue(node.fullText),
    )

    override fun visitCustomContainer(node: CustomContainer) = fields(
        "CustomContainer",
        "type" to tiqianValue(node.type),
        "title" to tiqianValue(node.title),
        "cssClasses" to tiqianStrings(node.cssClasses),
        "cssId" to tiqianValue(node.cssId),
    )

    override fun visitDiagramBlock(node: DiagramBlock) = fields("DiagramBlock", "diagramType" to tiqianValue(node.diagramType))

    override fun visitColumnsLayout(node: ColumnsLayout) = fields("ColumnsLayout")

    override fun visitColumnItem(node: ColumnItem) = fields("ColumnItem", "width" to tiqianValue(node.width))

    override fun visitPageBreak(node: PageBreak) = fields("PageBreak")

    override fun visitText(node: Text) = fields("Text")

    override fun visitSoftLineBreak(node: SoftLineBreak) = fields("SoftLineBreak")

    override fun visitHardLineBreak(node: HardLineBreak) = fields("HardLineBreak")

    override fun visitEmphasis(node: Emphasis) = fields("Emphasis", "delimiter" to tiqianValue(node.delimiter.toString()))

    override fun visitStrongEmphasis(node: StrongEmphasis) = fields("StrongEmphasis", "delimiter" to tiqianValue(node.delimiter.toString()))

    override fun visitStrikethrough(node: Strikethrough) = fields("Strikethrough")

    override fun visitInlineCode(node: InlineCode) = fields("InlineCode")

    override fun visitLink(node: Link) = fields(
        "Link",
        "destination" to tiqianValue(node.destination),
        "title" to tiqianValue(node.title),
        "attributes" to tiqianMap(node.attributes),
        "cssClasses" to tiqianStrings(node.cssClasses),
        "cssId" to tiqianValue(node.cssId),
        attributes = node.attributes,
    )

    override fun visitImage(node: Image) = fields(
        "Image",
        "destination" to tiqianValue(node.destination),
        "title" to tiqianValue(node.title),
        "imageWidth" to tiqianValue(node.imageWidth),
        "imageHeight" to tiqianValue(node.imageHeight),
        "attributes" to tiqianMap(node.attributes),
        "cssClasses" to tiqianStrings(node.cssClasses),
        "cssId" to tiqianValue(node.cssId),
        attributes = node.attributes,
    )

    override fun visitAutolink(node: Autolink) = fields(
        "Autolink",
        "destination" to tiqianValue(node.destination),
        "isEmail" to tiqianValue(node.isEmail),
        "rawText" to tiqianValue(node.rawText),
    )

    override fun visitInlineHtml(node: InlineHtml) = fields("InlineHtml")

    override fun visitHtmlEntity(node: HtmlEntity) = fields("HtmlEntity", "resolved" to tiqianValue(node.resolved))

    override fun visitEscapedChar(node: EscapedChar) = fields("EscapedChar")

    override fun visitFootnoteReference(node: FootnoteReference) = fields(
        "FootnoteReference",
        "label" to tiqianValue(node.label),
        "index" to tiqianValue(node.index),
    )

    override fun visitInlineMath(node: InlineMath) = fields("InlineMath")

    override fun visitHighlight(node: Highlight) = fields("Highlight", "attributes" to tiqianMap(node.attributes), attributes = node.attributes)

    override fun visitSuperscript(node: Superscript) = fields("Superscript", "attributes" to tiqianMap(node.attributes), attributes = node.attributes)

    override fun visitSubscript(node: Subscript) = fields("Subscript", "attributes" to tiqianMap(node.attributes), attributes = node.attributes)

    override fun visitInsertedText(node: InsertedText) = fields("InsertedText", "attributes" to tiqianMap(node.attributes), attributes = node.attributes)

    override fun visitEmoji(node: Emoji) = fields(
        "Emoji",
        "shortcode" to tiqianValue(node.shortcode),
        "unicode" to tiqianValue(node.unicode),
    )

    override fun visitStyledText(node: StyledText) = fields(
        "StyledText",
        "attributes" to tiqianMap(node.attributes),
        "cssClasses" to tiqianStrings(node.cssClasses),
        "cssId" to tiqianValue(node.cssId),
        "style" to tiqianValue(node.style),
        attributes = node.attributes,
    )

    override fun visitAbbreviation(node: Abbreviation) = fields(
        "Abbreviation",
        "abbreviation" to tiqianValue(node.abbreviation),
        "fullText" to tiqianValue(node.fullText),
    )

    override fun visitKeyboardInput(node: KeyboardInput) = fields("KeyboardInput")

    override fun visitDirectiveBlock(node: DirectiveBlock) = fields(
        "DirectiveBlock",
        "tagName" to tiqianValue(node.tagName),
        "args" to tiqianMap(node.args),
        attributes = node.args,
    )

    override fun visitDirectiveInline(node: DirectiveInline) = fields(
        "DirectiveInline",
        "tagName" to tiqianValue(node.tagName),
        "args" to tiqianMap(node.args),
        attributes = node.args,
    )

    override fun visitTabBlock(node: TabBlock) = fields("TabBlock")

    override fun visitTabItem(node: TabItem) = fields("TabItem", "title" to tiqianValue(node.title))

    override fun visitBibliographyDefinition(node: BibliographyDefinition) = fields(
        "BibliographyDefinition",
        "entries" to MarkdownNodeValue.ObjectValue(
            node.entries.mapValues { (_, entry) ->
                MarkdownNodeValue.ObjectValue(mapOf("key" to tiqianValue(entry.key), "content" to tiqianValue(entry.content)))
            },
        ),
    )

    override fun visitCitationReference(node: CitationReference) = fields("CitationReference", "key" to tiqianValue(node.key))

    override fun visitSpoiler(node: Spoiler) = fields("Spoiler")

    override fun visitWikiLink(node: WikiLink) = fields("WikiLink", "target" to tiqianValue(node.target), "label" to tiqianValue(node.label))

    override fun visitRubyText(node: RubyText) = fields("RubyText", "base" to tiqianValue(node.base), "annotation" to tiqianValue(node.annotation))

    override fun visitFigure(node: Figure) = fields(
        "Figure",
        "imageUrl" to tiqianValue(node.imageUrl),
        "caption" to tiqianValue(node.caption),
        "imageWidth" to tiqianValue(node.imageWidth),
        "imageHeight" to tiqianValue(node.imageHeight),
        "attributes" to tiqianMap(node.attributes),
        attributes = node.attributes,
    )
}
