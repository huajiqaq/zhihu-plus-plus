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

package com.github.zly2006.zhihu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.maxLengthTrim
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.Dp
import com.github.zly2006.zhihu.editor.PinTopicSuggestion
import com.github.zly2006.zhihu.i18n.ContentStrings
import com.github.zly2006.zhihu.markdown.markdownToMdAst
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.ui.components.AppTextField
import com.hrm.markdown.parser.ast.ContainerNode
import com.hrm.markdown.parser.ast.FencedCodeBlock
import com.hrm.markdown.parser.ast.HtmlBlock
import com.hrm.markdown.parser.ast.Image
import com.hrm.markdown.parser.ast.IndentedCodeBlock
import com.hrm.markdown.parser.ast.InlineCode
import com.hrm.markdown.parser.ast.LeafNode
import com.hrm.markdown.parser.ast.Link
import com.hrm.markdown.parser.ast.LinkReferenceDefinition
import com.hrm.markdown.parser.ast.Node as MarkdownNode

internal fun typedPinTopicStart(before: Pair<String, TextRange>, after: Pair<String, TextRange>): Int? {
    val (old, oldSelection) = before
    val (new, selection) = after
    val start = oldSelection.end
    val end = start + 1
    if (!oldSelection.collapsed || selection != TextRange(end) || new.length != old.length + 1) return null
    if (new[start] != '#' || start > 0 && !new[start - 1].isWhitespace()) return null
    if (end < new.length && new[end] !in "\r\n") return null
    if (old != new.removeRange(start, end)) return null

    /** 避免把单独输入的 # 当作 Markdown 标题；只在刚好键入该字符时检查其语境。 */
    var probe = "pin-typed-topic"
    while (probe in old) probe += "x"

    fun blocked(node: MarkdownNode, inLink: Boolean = false): Boolean = when (node) {
        is FencedCodeBlock, is IndentedCodeBlock, is InlineCode, is HtmlBlock -> probe in node.literal
        is LinkReferenceDefinition -> probe in node.label || probe in node.destination || probe in node.title.orEmpty()
        is Link -> probe in node.destination || probe in node.title.orEmpty() || node.children.any { blocked(it, inLink = true) }
        is Image -> probe in node.destination || probe in node.title.orEmpty() || node.children.any { blocked(it, inLink = true) }
        is ContainerNode -> node.children.any { blocked(it, inLink) }
        is LeafNode -> inLink && probe in node.literal
    }
    return start.takeUnless { markdownToMdAst(new.replaceRange(start, end, probe)).children.any { blocked(it) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WritePinTopicSuggestions(
    searchFocus: FocusRequester,
    availableHeight: Dp,
    topicQuery: TextFieldState,
    topicSuggestionError: String?,
    onRetry: () -> Unit,
    topicSuggestions: List<PinTopicSuggestion>,
    isSubmitting: Boolean,
    onCancel: () -> Unit,
    onSelectTopic: (PinTopicSuggestion) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = AppDimens.Space12)
            .heightIn(max = availableHeight * 0.55f)
            .testTag(WRITE_PIN_TOPIC_SUGGESTIONS_TAG),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(AppDimens.Space8),
            verticalArrangement = Arrangement.spacedBy(AppDimens.Space4),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppTextField(
                    state = topicQuery,
                    inputTransformation = InputTransformation.maxLengthTrim(50),
                    placeholder = ContentStrings.searchTopics,
                    modifier = Modifier.weight(1f).focusRequester(searchFocus),
                )
                AppTextButton(text = ContentStrings.cancel, onClick = onCancel)
            }
            topicSuggestionError?.let { message ->
                AppTextButton(
                    text = ContentStrings.topicSuggestionsError(message),
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            topicSuggestions.forEach { topic ->
                AppTextButton(
                    modifier = Modifier.fillMaxWidth().testTag("write_pin_topic_suggestion_${topic.topicId}"),
                    enabled = !isSubmitting,
                    onClick = { onSelectTopic(topic) },
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Text("#${topic.name}")
                        if (topic.discussCount.isNotBlank()) {
                            Text(topic.discussCount, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
