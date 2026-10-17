package com.github.zly2006.zhihu.markdown

import com.hrm.latex.parser.LatexParser
import com.hrm.latex.parser.model.LatexNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 验证 #412 修复：`\mathbin`、`\_`、`\{`、`\ ` 等
 * 在 parser 层不应产生 [LatexNode.Command]（会触发渲染端红色原样显示）。
 *
 * 注意：渲染端的红色回退仅发生在 [LatexNode.Command] 节点；
 * `\{`、`\}`、`\_` 在 parser 层已正确解析为 [LatexNode.Symbol]，
 * 真正的 bug 是 `\mathbin` 等一族 math-class 声明命令未注册，
 * 以及 `\ `（反斜杠+空格）产生的 Command(" ") 未被识别为空格。
 */
class Issue412ProbeTest {
    private val parser = LatexParser()

    private fun collectCommands(node: LatexNode): List<String> {
        val result = mutableListOf<String>()
        val pending = ArrayDeque<LatexNode>()
        pending.add(node)
        while (pending.isNotEmpty()) {
            val current = pending.removeFirst()
            if (current is LatexNode.Command) result += current.name
            pending.addAll(current.children())
        }
        return result
    }

    @Test
    fun math_class_commands_should_expand_to_group_not_command() {
        val samples = listOf(
            "x \\mathbin{+} y",
            "\\mathbin{\\lambda}",
            "A \\mathbin{\\diamond} B",
            "\\mathrel{=} x",
            "\\mathopen{\\{}",
            "\\mathclose{\\}}",
            "\\mathpunct{,}",
            "\\mathinner{\\ldots}",
            "a \\mathord{b} c",
            "(s + 1) \\mathbin \\lambda \\_ . return\\ s",
        )
        val failures = mutableListOf<String>()
        for (latex in samples) {
            val result = parser.parseWithDiagnostics(latex)
            val commands = collectCommands(result.document)
            if (commands.isNotEmpty()) {
                failures += "$latex -> unexpected Command nodes: $commands"
            }
            result.errors.forEach { err ->
                failures += "$latex -> error ${err.category}: ${err.message}"
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun escaped_braces_and_underscore_should_parse_as_symbols() {
        val samples = listOf(
            "\\{",
            "\\}",
            "\\_",
            "\\Sigma = \\{get, \\ set\\}",
            "a\\_b",
            "\\{ x \\mid x > 0 \\}",
        )
        val failures = mutableListOf<String>()
        for (latex in samples) {
            val result = parser.parseWithDiagnostics(latex)
            val commands = collectCommands(result.document)
            if (commands.isNotEmpty()) {
                failures += "$latex -> unexpected Command nodes: $commands"
            }
            result.errors.forEach { err ->
                failures += "$latex -> error ${err.category}: ${err.message}"
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun backslash_space_should_produce_space_node_not_command() {
        // `\ ` 在 LaTeX 中是显式 inter-word 空格
        val result = parser.parseWithDiagnostics("a\\ b")
        val commands = collectCommands(result.document)
        assertTrue(commands.isEmpty(), "unexpected Command nodes: $commands")
        // 应包含至少一个 Space 节点
        val hasSpace = result.document.children.any { it is LatexNode.Space }
        assertTrue(hasSpace, "expected a Space node for `\\ `, got: ${result.document.children.map { it::class.simpleName }}")
    }

    @Test
    fun math_class_group_argument_should_be_unwrapped() {
        // \mathbin{+} 应展开为 Group([Text(+)])，而非 Group([Group([Text(+)])])
        val result = parser.parseWithDiagnostics("\\mathbin{+}")
        val group = result.document.children
            .filterIsInstance<LatexNode.Group>()
            .single()
        assertEquals(1, group.children.size)
        // 解包后直接是 Text(+)
        val inner = group.children.single()
        assertTrue(inner is LatexNode.Text, "expected inner Text, got ${inner::class.simpleName}")
        assertEquals("+", inner.content)
    }
}
