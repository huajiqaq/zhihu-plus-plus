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

import com.hrm.latex.parser.LatexParser
import com.hrm.latex.parser.ParseDiagnostic
import com.hrm.latex.parser.model.LatexNode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest
import java.text.Normalizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ZhihuFormulaCorpusTest {
    private val json = Json { ignoreUnknownKeys = true }

    private data class AstProfile(
        var fractions: Int = 0,
        var rulelessFractions: Int = 0,
        var binomials: Int = 0,
        var roots: Int = 0,
        var indexedRoots: Int = 0,
        var superscripts: Int = 0,
        var subscripts: Int = 0,
        val delimiters: MutableList<String> = mutableListOf(),
        val tables: MutableList<String> = mutableListOf(),
        val rowGapPatterns: MutableList<String> = mutableListOf(),
        val textSegments: MutableList<String> = mutableListOf(),
        var accents: Int = 0,
        var extensibleArrows: Int = 0,
        var operators: Int = 0,
        var colors: Int = 0,
        var boxes: Int = 0,
    ) {
        fun sorted(): AstProfile =
            copy(
                delimiters = delimiters.sorted().toMutableList(),
                tables = tables.sorted().toMutableList(),
                rowGapPatterns = rowGapPatterns.sorted().toMutableList(),
                textSegments = textSegments.sorted().toMutableList(),
            )
    }

    @Test
    fun formula_index_should_be_self_consistent() {
        val manifest = resourceText("manifest.json").let(json::parseToJsonElement).jsonObject
        val contents = manifest.getValue("contents").jsonArray.map { it.jsonObject }
        val formulas = resourceText("formulas.json").let(json::parseToJsonElement).jsonArray.map { it.jsonObject }
        val contentKeys = contents.map {
            "${it.getValue("type").jsonPrimitive.content}-${it.getValue("id").jsonPrimitive.content}"
        }
        val rawPaths = contents.map { it.getValue("raw_path").jsonPrimitive.content }
        val latex = formulas.map { it.getValue("latex").jsonPrimitive.content }
        val sourceKeys = formulas.flatMap { formula ->
            formula.getValue("sources").jsonArray.map { sourceElement ->
                val source = sourceElement.jsonObject
                "${source.getValue("content_type").jsonPrimitive.content}-" +
                    source.getValue("content_id").jsonPrimitive.content
            }
        }

        assertEquals(contentKeys.size, contentKeys.toSet().size, "内容键重复")
        assertEquals(rawPaths.size, rawPaths.toSet().size, "原始样本路径重复")
        assertEquals(latex.size, latex.toSet().size, "公式重复")
        assertTrue(sourceKeys.all(contentKeys.toSet()::contains), "清单中缺少公式来源")
        assertEquals(
            manifest
                .getValue("formula_occurrences")
                .jsonPrimitive.content
                .toInt(),
            sourceKeys.size,
        )
        assertEquals(
            sourceKeys.size,
            contents.sumOf {
                it
                    .getValue("formula_count")
                    .jsonPrimitive.content
                    .toInt()
            },
        )
    }

    @Test
    fun extracted_formulas_should_parse_with_the_current_latex_library() {
        val parser = LatexParser()
        val formulas = resourceText("formulas.json")
            .let(json::parseToJsonElement)
            .jsonArray
            .map {
                it.jsonObject
                    .getValue("latex")
                    .jsonPrimitive.content
            }
        val failures = mutableListOf<String>()
        val unknownCommands = mutableMapOf<String, MutableList<String>>()
        var acceptedTrailingBackslashes = 0

        formulas.forEachIndexed { index, formula ->
            try {
                val result = parser.parseWithDiagnostics(formula)
                if (result.document.children.isEmpty()) {
                    failures += "formula[$index] 生成了空 AST：$formula"
                }
                val pending = ArrayDeque<LatexNode>()
                pending.addAll(result.document.children)
                while (pending.isNotEmpty()) {
                    val node = pending.removeFirst()
                    if (node is LatexNode.Command) {
                        unknownCommands.getOrPut(node.name, ::mutableListOf) += formula
                    }
                    pending.addAll(node.children())
                }
                val trailingBackslashCount = formula.length - formula.trimEnd('\\').length
                result.errors.forEach { error ->
                    if (
                        trailingBackslashCount % 2 == 1 &&
                        error.category == ParseDiagnostic.Category.UNKNOWN_COMMAND &&
                        error.message == "Trailing backslash does not form a control sequence"
                    ) {
                        acceptedTrailingBackslashes++
                    } else {
                        failures +=
                            "formula[$index] 出现非预期的 ${error.category} 错误：" +
                            "${error.message}: $formula"
                    }
                }
            } catch (error: Exception) {
                failures += "formula[$index] ${error::class.simpleName}: $formula"
            }
        }

        assertTrue(failures.isEmpty(), failures.take(20).joinToString(separator = "\n"))
        assertEquals(211, acceptedTrailingBackslashes, "已知格式错误的尾部反斜杠数量已改变")
        assertTrue(
            unknownCommands.isEmpty(),
            unknownCommands.entries
                .sortedByDescending { it.value.size }
                .take(30)
                .joinToString(separator = "\n") { (command, samples) ->
                    "\\$command count=${samples.size} sample=${samples.first()}"
                },
        )
    }

    @Test
    fun current_ast_should_match_katex_semantic_profiles() {
        val formulas =
            resourceText("formulas.json")
                .let(json::parseToJsonElement)
                .jsonArray
                .map {
                    it.jsonObject
                        .getValue("latex")
                        .jsonPrimitive.content
                }
        val oracle = resourceText("katex-ast-oracle.json").let(json::parseToJsonElement).jsonObject
        val entries = oracle.getValue("entries").jsonArray

        assertEquals("0.18.1", oracle.getValue("katexVersion").jsonPrimitive.content)
        assertEquals(
            3699,
            oracle
                .getValue("parsedCount")
                .jsonPrimitive.content
                .toInt(),
        )
        assertEquals(
            4,
            oracle
                .getValue("rejectedCount")
                .jsonPrimitive.content
                .toInt(),
        )
        assertEquals(formulas.size, entries.size)

        val parser = LatexParser()
        val mismatches = mutableListOf<String>()
        val rejectionCounts = mutableMapOf<String, Int>()
        formulas.zip(entries).forEachIndexed { index, (formula, entryElement) ->
            val entry = entryElement.jsonObject
            assertEquals(
                entry.getValue("sha256").jsonPrimitive.content,
                formula.encodeToByteArray().sha256(),
                "formula[$index] 的 KaTeX 对照顺序已改变",
            )
            if (entry.getValue("status").jsonPrimitive.content == "rejected") {
                val category = entry.getValue("errorCategory").jsonPrimitive.content
                rejectionCounts[category] = rejectionCounts.getOrDefault(category, 0) + 1
                return@forEachIndexed
            }

            val expected = entry.getValue("profile").jsonObject.toAstProfile()
            val actual = parser.parse(formula).toAstProfile(formula)
            if (actual != expected) {
                mismatches +=
                    "formula[$index] 预期=$expected 实际=$actual latex=$formula"
            }
        }

        assertEquals(mapOf("invalid-array" to 2, "malformed-dollar" to 2), rejectionCounts)
        assertTrue(mismatches.isEmpty(), mismatches.take(30).joinToString(separator = "\n"))
    }

    private fun resourceText(path: String): String = resourceBytes(path).decodeToString()

    private fun resourceBytes(path: String): ByteArray =
        checkNotNull(javaClass.getResourceAsStream("/zhihu-formula-corpus/$path")) {
            "缺少语料资源：$path"
        }.use { it.readBytes() }

    private fun ByteArray.sha256(): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(this)
            .joinToString("") { byte -> "%02x".format(byte) }

    private fun JsonObject.toAstProfile(): AstProfile =
        AstProfile(
            fractions = getValue("fractions").jsonPrimitive.content.toInt(),
            rulelessFractions = getValue("rulelessFractions").jsonPrimitive.content.toInt(),
            binomials = getValue("binomials").jsonPrimitive.content.toInt(),
            roots = getValue("roots").jsonPrimitive.content.toInt(),
            indexedRoots = getValue("indexedRoots").jsonPrimitive.content.toInt(),
            superscripts = getValue("superscripts").jsonPrimitive.content.toInt(),
            subscripts = getValue("subscripts").jsonPrimitive.content.toInt(),
            delimiters =
                getValue("delimiters")
                    .jsonArray
                    .map { it.jsonPrimitive.content }
                    .toMutableList(),
            tables =
                getValue("tables")
                    .jsonArray
                    .map { it.jsonPrimitive.content }
                    .toMutableList(),
            rowGapPatterns =
                getValue("rowGapPatterns")
                    .jsonArray
                    .map { it.jsonPrimitive.content }
                    .toMutableList(),
            textSegments =
                getValue("textSegments")
                    .jsonArray
                    .map { it.jsonPrimitive.content }
                    .toMutableList(),
            accents = getValue("accents").jsonPrimitive.content.toInt(),
            extensibleArrows = getValue("extensibleArrows").jsonPrimitive.content.toInt(),
            operators = getValue("operators").jsonPrimitive.content.toInt(),
            colors = getValue("colors").jsonPrimitive.content.toInt(),
            boxes = getValue("boxes").jsonPrimitive.content.toInt(),
        )

    private fun LatexNode.Document.toAstProfile(source: String): AstProfile {
        val profile = AstProfile()
        val pending = ArrayDeque<LatexNode>()
        pending.addAll(children)
        while (pending.isNotEmpty()) {
            val node = pending.removeFirst()
            when (node) {
                is LatexNode.Fraction -> {
                    profile.fractions++
                    if (node.style == LatexNode.Fraction.FractionStyle.RULELESS) {
                        profile.rulelessFractions++
                    }
                }
                is LatexNode.Binomial -> profile.binomials++
                is LatexNode.Root -> {
                    profile.roots++
                    if (node.index != null) {
                        profile.indexedRoots++
                    }
                }
                is LatexNode.Superscript -> profile.superscripts++
                is LatexNode.Subscript -> profile.subscripts++
                is LatexNode.BigOperator -> {
                    profile.operators++
                    if (node.superscript != null) {
                        profile.superscripts++
                    }
                    if (node.subscript != null) {
                        profile.subscripts++
                    }
                }
                is LatexNode.Prescript -> {
                    if (node.preSuperscript != null) {
                        profile.superscripts++
                    }
                    if (node.preSubscript != null) {
                        profile.subscripts++
                    }
                }
                is LatexNode.Stack -> {
                    if (!node.base.isSemanticOperator()) {
                        profile.operators++
                    }
                    if (node.above != null) {
                        profile.superscripts++
                    }
                    if (node.below != null) {
                        profile.subscripts++
                    }
                }
                is LatexNode.Delimited ->
                    profile.delimiters +=
                        "${node.left.comparableDelimiter()}|${node.right.comparableDelimiter()}"
                is LatexNode.Matrix -> {
                    profile.tables += node.rows.tableShape()
                    profile.rowGapPatterns += node.rowGaps.comparablePattern()
                    when (node.type) {
                        LatexNode.Matrix.MatrixType.PLAIN -> Unit
                        LatexNode.Matrix.MatrixType.PAREN -> profile.delimiters += "(|)"
                        LatexNode.Matrix.MatrixType.BRACKET -> profile.delimiters += "[|]"
                        LatexNode.Matrix.MatrixType.BRACE -> profile.delimiters += "{|}"
                        LatexNode.Matrix.MatrixType.VBAR -> profile.delimiters += "|||"
                        LatexNode.Matrix.MatrixType.DOUBLE_VBAR -> profile.delimiters += "‖|‖"
                    }
                }
                is LatexNode.Array -> {
                    profile.tables += node.rows.tableShape()
                    profile.rowGapPatterns += node.rowGaps.comparablePattern()
                }
                is LatexNode.Cases -> {
                    val cases =
                        node.cases.dropLastWhile { (expression, condition) ->
                            expression.isSemanticallyEmpty() && condition.isSemanticallyEmpty()
                        }
                    profile.delimiters += "{|"
                    profile.tables += "cases:${cases.size}"
                    profile.rowGapPatterns += node.rowGaps.comparablePattern()
                }
                is LatexNode.Aligned -> {
                    profile.tables += node.rows.tableShape()
                    profile.rowGapPatterns += node.rowGaps.comparablePattern()
                }
                is LatexNode.Split -> {
                    profile.tables += node.rows.tableShape()
                    profile.rowGapPatterns += node.rowGaps.comparablePattern()
                }
                is LatexNode.Eqnarray -> {
                    profile.tables += node.rows.tableShape()
                    profile.rowGapPatterns += node.rowGaps.comparablePattern()
                }
                is LatexNode.Substack ->
                    profile.tables += node.rows.tableShape()
                is LatexNode.Tabular -> {
                    profile.tables += node.rows.tableShape()
                    profile.rowGapPatterns += node.rowGaps.comparablePattern()
                }
                is LatexNode.Multline -> {
                    profile.tables += "table:${node.lines.size}x1"
                    profile.rowGapPatterns += node.rowGaps.comparablePattern()
                }
                is LatexNode.TextMode ->
                    node.text
                        .comparableText()
                        .takeIf(String::isNotEmpty)
                        ?.let(profile.textSegments::add)
                is LatexNode.Style -> {
                    val range = node.sourceRange
                    if (
                        range != null &&
                        TEXT_STYLE_COMMANDS.any { command ->
                            source.regionMatches(range.start, "\\$command", 0, command.length + 1)
                        }
                    ) {
                        node.content
                            .profileText()
                            .comparableText()
                            .takeIf(String::isNotEmpty)
                            ?.let(profile.textSegments::add)
                    }
                }
                is LatexNode.Accent ->
                    if (
                        node.accentType == LatexNode.Accent.AccentType.CANCEL ||
                        node.accentType == LatexNode.Accent.AccentType.BCANCEL ||
                        node.accentType == LatexNode.Accent.AccentType.XCANCEL
                    ) {
                        profile.boxes++
                    } else {
                        profile.accents++
                    }
                is LatexNode.ExtensibleArrow -> profile.extensibleArrows++
                is LatexNode.Operator,
                is LatexNode.OperatorName,
                is LatexNode.ModOperator,
                -> profile.operators++
                is LatexNode.Color -> profile.colors++
                is LatexNode.Boxed,
                is LatexNode.Enclose,
                is LatexNode.ColorBox,
                -> profile.boxes++
                else -> Unit
            }
            pending.addAll(node.children())
        }
        return profile.sorted()
    }

    private fun List<LatexNode.RowGap?>.comparablePattern(): String =
        joinToString(",") { gap ->
            gap?.let { "${it.number}${it.unit}" } ?: "null"
        }

    private fun LatexNode.isSemanticOperator(): Boolean =
        when (this) {
            is LatexNode.BigOperator,
            is LatexNode.Operator,
            is LatexNode.OperatorName,
            is LatexNode.ModOperator,
            -> true
            is LatexNode.Group -> children.singleSemanticNode()?.isSemanticOperator() == true
            is LatexNode.Style -> content.singleSemanticNode()?.isSemanticOperator() == true
            is LatexNode.MathStyle -> content.singleSemanticNode()?.isSemanticOperator() == true
            else -> false
        }

    private fun List<LatexNode>.singleSemanticNode(): LatexNode? =
        filterNot { node -> node.isSemanticallyEmpty() }.singleOrNull()

    private fun List<List<LatexNode>>.tableShape(): String {
        val rows = filterNot { row ->
            row.any { node -> node.containsRule() } &&
                row.all { node -> node.containsOnlyRuleOrSpace() }
        }
        return "table:${rows.size}x${rows.maxOfOrNull { it.size } ?: 0}"
    }

    private fun LatexNode.containsRule(): Boolean =
        when (this) {
            is LatexNode.HLine,
            is LatexNode.CLine,
            -> true
            is LatexNode.Group -> children.any { node -> node.containsRule() }
            else -> false
        }

    private fun LatexNode.containsOnlyRuleOrSpace(): Boolean =
        when (this) {
            is LatexNode.HLine,
            is LatexNode.CLine,
            is LatexNode.Space,
            -> true
            is LatexNode.Group -> children.all { node -> node.containsOnlyRuleOrSpace() }
            else -> false
        }

    private fun List<LatexNode>.profileText(): String =
        joinToString(separator = "") { node ->
            when (node) {
                is LatexNode.Text -> node.content
                is LatexNode.TextMode -> node.text
                is LatexNode.Symbol -> node.unicode
                else -> node.children().profileText()
            }
        }

    private fun LatexNode.isSemanticallyEmpty(): Boolean =
        when (this) {
            is LatexNode.Group -> children.all { node -> node.isSemanticallyEmpty() }
            is LatexNode.Text -> content.isBlank()
            is LatexNode.TextMode -> text.isBlank()
            is LatexNode.Style -> content.all { node -> node.isSemanticallyEmpty() }
            is LatexNode.MathStyle -> content.all { node -> node.isSemanticallyEmpty() }
            else -> false
        }

    private fun String.comparableDelimiter(): String =
        when (this) {
            "." -> ""
            "\\{" -> "{"
            "\\}" -> "}"
            "\\langle" -> "⟨"
            "\\rangle" -> "⟩"
            "lbrack" -> "\\lbrack"
            "rbrack" -> "\\rbrack"
            "\\lvert", "\\rvert" -> "|"
            "\\lVert", "\\rVert" -> "‖"
            else -> this
        }

    private fun String.comparableText(): String =
        Normalizer
            .normalize(this, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("\\s+"), "")

    private companion object {
        val TEXT_STYLE_COMMANDS = setOf("textbf", "textit", "textrm", "textsf", "texttt")
    }
}
