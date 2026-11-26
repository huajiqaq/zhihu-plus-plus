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

import com.hrm.markdown.parser.ast.AbbreviationDefinition
import com.hrm.markdown.parser.ast.Admonition
import com.hrm.markdown.parser.ast.BibliographyDefinition
import com.hrm.markdown.parser.ast.BlankLine
import com.hrm.markdown.parser.ast.BlockQuote
import com.hrm.markdown.parser.ast.ColumnItem
import com.hrm.markdown.parser.ast.ColumnsLayout
import com.hrm.markdown.parser.ast.ContainerNode
import com.hrm.markdown.parser.ast.CustomContainer
import com.hrm.markdown.parser.ast.DefinitionDescription
import com.hrm.markdown.parser.ast.DefinitionList
import com.hrm.markdown.parser.ast.DefinitionTerm
import com.hrm.markdown.parser.ast.DiagramBlock
import com.hrm.markdown.parser.ast.DirectiveBlock
import com.hrm.markdown.parser.ast.Document
import com.hrm.markdown.parser.ast.FencedCodeBlock
import com.hrm.markdown.parser.ast.Figure
import com.hrm.markdown.parser.ast.FootnoteDefinition
import com.hrm.markdown.parser.ast.FrontMatter
import com.hrm.markdown.parser.ast.Heading
import com.hrm.markdown.parser.ast.HtmlBlock
import com.hrm.markdown.parser.ast.IndentedCodeBlock
import com.hrm.markdown.parser.ast.LinkReferenceDefinition
import com.hrm.markdown.parser.ast.ListBlock
import com.hrm.markdown.parser.ast.ListItem
import com.hrm.markdown.parser.ast.MathBlock
import com.hrm.markdown.parser.ast.Node
import com.hrm.markdown.parser.ast.PageBreak
import com.hrm.markdown.parser.ast.Paragraph
import com.hrm.markdown.parser.ast.SetextHeading
import com.hrm.markdown.parser.ast.TabBlock
import com.hrm.markdown.parser.ast.TabItem
import com.hrm.markdown.parser.ast.Table
import com.hrm.markdown.parser.ast.TableBody
import com.hrm.markdown.parser.ast.TableCell
import com.hrm.markdown.parser.ast.TableHead
import com.hrm.markdown.parser.ast.TableRow
import com.hrm.markdown.parser.ast.ThematicBreak
import com.hrm.markdown.parser.ast.TocPlaceholder
import org.tiqian.markdown.MarkdownBibliographyBlock
import org.tiqian.markdown.MarkdownBibliographyEntry
import org.tiqian.markdown.MarkdownBlock
import org.tiqian.markdown.MarkdownBlockQuote
import org.tiqian.markdown.MarkdownCapabilityIssue
import org.tiqian.markdown.MarkdownCapabilityIssueKind
import org.tiqian.markdown.MarkdownCodeBlock
import org.tiqian.markdown.MarkdownContainerBlock
import org.tiqian.markdown.MarkdownContainerKind
import org.tiqian.markdown.MarkdownCustomBlock
import org.tiqian.markdown.MarkdownDiagramBlock
import org.tiqian.markdown.MarkdownFootnoteDefinition
import org.tiqian.markdown.MarkdownHeading
import org.tiqian.markdown.MarkdownHtmlBlock
import org.tiqian.markdown.MarkdownImageBlock
import org.tiqian.markdown.MarkdownList
import org.tiqian.markdown.MarkdownListItem
import org.tiqian.markdown.MarkdownMathBlock
import org.tiqian.markdown.MarkdownNodeKey
import org.tiqian.markdown.MarkdownNodeMetadata
import org.tiqian.markdown.MarkdownPageBreakBlock
import org.tiqian.markdown.MarkdownParagraph
import org.tiqian.markdown.MarkdownRenderDocument
import org.tiqian.markdown.MarkdownSourceSpan
import org.tiqian.markdown.MarkdownTable
import org.tiqian.markdown.MarkdownTableAlignment
import org.tiqian.markdown.MarkdownTableCell
import org.tiqian.markdown.MarkdownTableRow
import org.tiqian.markdown.MarkdownTaskState
import org.tiqian.markdown.MarkdownText
import org.tiqian.markdown.MarkdownThematicBreak
import org.tiqian.markdown.MarkdownTocBlock
import org.tiqian.markdown.MarkdownUnsupportedBlock

internal const val ZHIHU_SEGMENT_HIGHLIGHT_KIND = "zhihu.segment-highlight"

/** 将宿主持有的 AST 投影为渲染模型，保留节点身份与格式。 */
internal class TiqianRenderDocumentCompiler {
    fun compile(document: Document, sourceMarkdown: String? = null): MarkdownRenderDocument {
        val issues = mutableListOf<MarkdownCapabilityIssue>()
        val metadata = document.tiqianSnapshot(emptyList(), sourceMarkdown, sourceMarkdown?.let(::TiqianSourceLocator))

        /** 字段解析会改变脚注编号，须使用私有上下文而非已发布 AST。 */
        val fieldDocument = Document().apply {
            linkDefinitions.putAll(document.linkDefinitions)
            abbreviationDefinitions.putAll(document.abbreviationDefinitions)
            document.footnoteDefinitions.forEach { (key, definition) ->
                footnoteDefinitions[key] = FootnoteDefinition(definition.label, definition.index)
            }
        }
        val detachedFootnotes = document.footnoteDefinitions.values.toList()
        val blocks = buildList {
            addAll(compileBlockChildren(document, metadata, fieldDocument, issues))
            metadata.children.drop(document.children.size).forEach { definitionMetadata ->
                val path = definitionMetadata.key.path
                if (path.size == 3 && path[0] == -2 && path[1] == 1) {
                    detachedFootnotes.getOrNull(path[2])?.let { definition ->
                        compileBlock(definition, definitionMetadata, fieldDocument, issues)?.let(::add)
                    }
                }
            }
        }
        return MarkdownRenderDocument(
            blocks = blocks,
            issues = issues.toList(),
            metadata = metadata,
            revision = document.publication?.revision,
        )
    }

    private fun compileBlock(
        node: Node,
        metadata: MarkdownNodeMetadata,
        fieldDocument: Document,
        issues: MutableList<MarkdownCapabilityIssue>,
    ): MarkdownBlock? {
        if (node is BibliographyDefinition && node.entries.isEmpty()) return null
        return when (node) {
            is Paragraph -> MarkdownParagraph(compileTiqianText(node, metadata, issues), metadata)
            is Heading -> MarkdownHeading(node.level.coerceIn(1, 6), node.id, compileTiqianText(node, metadata, issues), metadata)
            is SetextHeading -> MarkdownHeading(node.level.coerceIn(1, 6), node.id, compileTiqianText(node, metadata, issues), metadata)
            is BlockQuote -> MarkdownBlockQuote(compileBlockChildren(node, metadata, fieldDocument, issues), metadata)
            is ListBlock -> MarkdownList(
                ordered = node.ordered,
                startNumber = node.startNumber,
                tight = node.tight,
                items = node.children.mapIndexedNotNull { index, child ->
                    val item = child as? ListItem ?: return@mapIndexedNotNull null
                    val itemMetadata = metadata.children[index]
                    MarkdownListItem(
                        blocks = compileBlockChildren(item, itemMetadata, fieldDocument, issues),
                        task = if (!item.taskListItem) {
                            null
                        } else if (item.checked) {
                            MarkdownTaskState.Checked
                        } else {
                            MarkdownTaskState.Unchecked
                        },
                        metadata = itemMetadata,
                    )
                },
                metadata = metadata,
            )
            is FencedCodeBlock -> MarkdownCodeBlock(
                code = node.literal,
                language = node.language.ifBlank { null },
                info = node.info.ifBlank { null },
                metadata = metadata,
                fileName = metadata.attributes["title"],
                showLineNumbers = node.showLineNumbers,
                startLineNumber = node.startLineNumber,
                highlightedLines = node.highlightLines.toList(),
            )
            is IndentedCodeBlock -> MarkdownCodeBlock(node.literal, null, null, metadata)
            is ThematicBreak -> MarkdownThematicBreak(metadata)
            is Figure -> MarkdownImageBlock(
                destination = node.imageUrl,
                description = node.caption,
                title = node.caption.ifBlank { null },
                widthPixels = node.imageWidth,
                heightPixels = node.imageHeight,
                metadata = metadata,
                attributes = metadata.attributes,
                caption = node.caption.takeIf(String::isNotBlank)?.let {
                    compileTiqianFieldText(it, metadata, 1, fieldDocument, issues)
                },
            )
            is MathBlock -> MarkdownMathBlock(node.literal, metadata)
            is HtmlBlock -> MarkdownHtmlBlock(node.literal, node.htmlType, metadata)
            is Table -> compileTable(node, metadata, fieldDocument, issues)
            is FootnoteDefinition -> MarkdownFootnoteDefinition(
                node.label,
                node.index,
                compileBlockChildren(node, metadata, fieldDocument, issues),
                metadata,
            )
            is DefinitionList, is DefinitionTerm, is DefinitionDescription,
            is Admonition, is CustomContainer, is ColumnsLayout, is ColumnItem, is TabBlock, is TabItem,
            -> {
                val container = node
                val kind = when (node) {
                    is DefinitionList -> MarkdownContainerKind.DefinitionList
                    is DefinitionTerm -> MarkdownContainerKind.DefinitionTerm
                    is DefinitionDescription -> MarkdownContainerKind.DefinitionDescription
                    is Admonition -> MarkdownContainerKind.Admonition
                    is CustomContainer -> MarkdownContainerKind.Custom
                    is ColumnsLayout -> MarkdownContainerKind.Columns
                    is ColumnItem -> MarkdownContainerKind.Column
                    is TabBlock -> MarkdownContainerKind.Tabs
                    is TabItem -> MarkdownContainerKind.Tab
                }
                val title = when (node) {
                    is DefinitionTerm, is DefinitionDescription ->
                        if (container.children.any(Node::isTiqianInline)) compileTiqianText(container, metadata, issues, inlineOnly = true) else null
                    is Admonition -> node.title.takeIf(String::isNotBlank)?.let { compileTiqianFieldText(it, metadata, 0, fieldDocument, issues) }
                    is CustomContainer -> node.title.takeIf(String::isNotBlank)?.let { compileTiqianFieldText(it, metadata, 0, fieldDocument, issues) }
                    /** 上游标签页标题是普通字符串，不按 Markdown 再解释。 */
                    is TabItem -> MarkdownText(node.title)
                    else -> null
                }
                MarkdownContainerBlock(
                    kind = kind,
                    blocks = compileBlockChildren(container, metadata, fieldDocument, issues),
                    metadata = metadata,
                    title = title,
                    width = (node as? ColumnItem)?.width,
                )
            }
            is BibliographyDefinition -> MarkdownBibliographyBlock(
                entries = node.entries.values.mapIndexed { index, entry ->
                    val entryMetadata = MarkdownNodeMetadata(
                        key = MarkdownNodeKey(metadata.key.parserStableKey, metadata.key.path + listOf(-1, index)),
                        sourceSpan = MarkdownSourceSpan(0, 0, 0, 0, 0, 0),
                        nodeType = "BibEntry",
                        payload = mapOf("key" to entry.key, "content" to entry.content),
                        hasSourceSpan = false,
                        properties = mapOf("key" to tiqianValue(entry.key), "content" to tiqianValue(entry.content)),
                    )
                    MarkdownBibliographyEntry(
                        entry.key,
                        compileTiqianFieldText(entry.content, entryMetadata, 2, fieldDocument, issues),
                        entryMetadata,
                    )
                },
                metadata = metadata,
            )
            is TocPlaceholder -> MarkdownTocBlock(metadata, node.minDepth, node.maxDepth, node.excludeIds.toList(), node.order == "desc")
            is DiagramBlock -> MarkdownDiagramBlock(node.diagramType, node.literal, metadata)
            is PageBreak -> MarkdownPageBreakBlock(metadata)
            is LinkReferenceDefinition, is AbbreviationDefinition, is BlankLine, is FrontMatter -> null
            is DirectiveBlock if (node.tagName == "zhihu-video" && node.args["id"]?.toLongOrNull() != null) -> MarkdownCustomBlock(
                kind = node.tagName,
                attributes = node.args.toMap(),
                blocks = compileBlockChildren(node, metadata, fieldDocument, issues),
                metadata = metadata,
            )
            else -> {
                issues += MarkdownCapabilityIssue(MarkdownCapabilityIssueKind.UnsupportedBlock, metadata.nodeType ?: "UnknownNode", metadata.sourceSpan)
                if (node is ContainerNode) {
                    MarkdownContainerBlock(
                        kind = MarkdownContainerKind.Unknown,
                        blocks = compileBlockChildren(node, metadata, fieldDocument, issues),
                        metadata = metadata,
                        title = if (node.children.any(Node::isTiqianInline)) compileTiqianText(node, metadata, issues, inlineOnly = true) else null,
                    )
                } else {
                    MarkdownUnsupportedBlock(metadata.nodeType ?: "UnknownNode", metadata.sourceMarkdown ?: node.tiqianReadableText(), metadata)
                }
            }
        }
    }

    private fun compileTable(
        node: Table,
        metadata: MarkdownNodeMetadata,
        fieldDocument: Document,
        issues: MutableList<MarkdownCapabilityIssue>,
    ): MarkdownTable {
        val rows = buildList {
            node.children.forEachIndexed { sectionIndex, section ->
                val sectionIsHeader = section is TableHead
                if (section !is TableHead && section !is TableBody) return@forEachIndexed
                val sectionMetadata = metadata.children[sectionIndex]
                section.children.forEachIndexed { rowIndex, rowNode ->
                    val row = rowNode as? TableRow ?: return@forEachIndexed
                    val rowMetadata = sectionMetadata.children[rowIndex]
                    add(
                        MarkdownTableRow(
                            cells = row.children.mapIndexedNotNull { cellIndex, cellNode ->
                                val cell = cellNode as? TableCell ?: return@mapIndexedNotNull null
                                val cellMetadata = rowMetadata.children[cellIndex]
                                MarkdownTableCell(
                                    compileTiqianText(cell, cellMetadata, issues),
                                    cell.alignment.toRenderAlignment(),
                                    sectionIsHeader || cell.isHeader,
                                    cellMetadata,
                                )
                            },
                            header = sectionIsHeader,
                            metadata = rowMetadata,
                            sectionMetadata = sectionMetadata,
                        ),
                    )
                }
            }
        }
        return MarkdownTable(
            columnAlignments = node.columnAlignments.map(Table.Alignment::toRenderAlignment),
            rows = rows,
            metadata = metadata,
            caption = node.blockAttributes["caption"]?.let { compileTiqianFieldText(it, metadata, 1, fieldDocument, issues) },
        )
    }

    private fun compileBlockChildren(
        node: ContainerNode,
        metadata: MarkdownNodeMetadata,
        fieldDocument: Document,
        issues: MutableList<MarkdownCapabilityIssue>,
    ): List<MarkdownBlock> = node.children.mapIndexedNotNull { index, child ->
        if (child.isTiqianInline()) return@mapIndexedNotNull null
        compileBlock(child, metadata.children[index], fieldDocument, issues)
    }
}

private fun Table.Alignment.toRenderAlignment(): MarkdownTableAlignment = when (this) {
    Table.Alignment.LEFT -> MarkdownTableAlignment.Start
    Table.Alignment.CENTER -> MarkdownTableAlignment.Center
    Table.Alignment.RIGHT -> MarkdownTableAlignment.End
    Table.Alignment.NONE -> MarkdownTableAlignment.Unspecified
}
