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

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.ui.components.AppCheckbox
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppDropdownMenuItem
import com.github.zly2006.zhihu.ui.components.AppIconButton
import com.github.zly2006.zhihu.ui.components.AppSelectionDropdownMenu
import com.github.zly2006.zhihu.ui.components.AppSpinner
import com.github.zly2006.zhihu.viewmodel.CommentItem
import com.github.zly2006.zhihu.viewmodel.comment.CommentLinkInfo
import com.github.zly2006.zhihu.viewmodel.comment.CommentMention
import kotlin.math.min
import kotlin.math.roundToInt
import com.github.zly2006.zhihu.ui.components.AppIcon as Icon

/** 输入行：表情/键盘切换、能渲染 @ 与链接标题的输入框、发送按钮。 */
@Composable
internal fun CommentScreenInputRow(
    commentFieldState: TextFieldState,
    commentInputFocusRequester: FocusRequester,
    commentInputScrollState: ScrollState,
    mentions: List<CommentMention>,
    commentLinkTitles: Map<String, CommentLinkInfo>,
    requestCommentLinkTitles: (List<String>) -> Unit,
    replyToComment: CommentItem?,
    showEmojiPicker: Boolean,
    onShowEmojiPickerChange: (Boolean) -> Unit,
    onInputFocusChange: (Boolean) -> Unit,
    isSending: Boolean,
    sendEnabled: Boolean,
    onSend: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val commentAccentColor = MaterialTheme.colorScheme.primary
    val mentionNames = remember(mentions) {
        mentions.map { it.name }.filter { it.isNotBlank() }
    }
    val commentLinkTitleMap = commentLinkTitles.mapValues { it.value.title }
    val commentInputGuard = remember(mentionNames, commentLinkTitleMap) {
        CommentInputGuardTransformation(
            mentionNames,
            commentLinkTitleMap.keys,
            { urls -> requestCommentLinkTitles(urls) },
        )
    }
    val commentOutputTransformation = remember(
        mentionNames,
        commentAccentColor,
        commentLinkTitleMap,
    ) {
        CommentInputOutputTransformation(
            mentionNames,
            commentAccentColor,
            commentLinkTitleMap,
        )
    }
    val commentInputEmojiRuns = remember(
        commentFieldState.text.toString(),
        commentLinkTitleMap,
    ) {
        planCommentInput(
            commentFieldState.text.toString(),
            commentLinkTitleMap,
        ).emojiRuns
    }
    val emojiInputBitmaps = rememberCommentEmojiBitmaps(
        remember(commentInputEmojiRuns) {
            commentInputEmojiRuns.mapTo(mutableSetOf(), EmojiInputRun::inlineKey)
        },
    )
    var commentInputTextLayout by remember { mutableStateOf<TextLayoutResult?>(null) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp, max = 140.dp)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconButton(
            onClick = {
                if (showEmojiPicker) {
                    onShowEmojiPickerChange(false)
                    commentInputFocusRequester.requestFocus()
                    keyboardController?.show()
                } else {
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                    onShowEmojiPickerChange(true)
                }
            },
            modifier = Modifier
                .size(40.dp)
                .testTag(COMMENT_EMOJI_BUTTON_TAG),
        ) {
            Icon(
                imageVector = if (showEmojiPicker) {
                    Icons.Outlined.Keyboard
                } else {
                    Icons.Outlined.EmojiEmotions
                },
                contentDescription = if (showEmojiPicker) {
                    "切换到键盘"
                } else {
                    "选择表情"
                },
                tint = if (showEmojiPicker) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Spacer(modifier = Modifier.width(4.dp))

        BasicTextField(
            state = commentFieldState,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            inputTransformation = commentInputGuard,
            outputTransformation = commentOutputTransformation,
            onTextLayout = { getLayout -> commentInputTextLayout = getLayout() },
            scrollState = commentInputScrollState,
            textStyle = TextStyle.Default.copy(
                fontSize = 16.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurface,
            ),
            modifier = Modifier
                .weight(1f)
                .semantics {
                    contentDescription = replyToComment
                        ?.item
                        ?.author
                        ?.name
                        ?.let { "回复 $it 的评论" } ?: "评论内容"
                }.focusRequester(commentInputFocusRequester)
                .onFocusChanged {
                    onInputFocusChange(it.isFocused)
                    if (it.isFocused) onShowEmojiPickerChange(false)
                }.testTag(COMMENT_INPUT_TAG)
                .clipToBounds()
                .drawWithContent {
                    drawContent()
                    val layout = commentInputTextLayout ?: return@drawWithContent
                    val scroll = commentInputScrollState.value
                    emojiInputRunsLocal@ for (run in commentInputEmojiRuns) {
                        if (run.transformedEnd > layout.layoutInput.text.length) {
                            continue@emojiInputRunsLocal
                        }
                        val bitmap = emojiInputBitmaps[run.inlineKey]
                            ?: continue@emojiInputRunsLocal
                        val box = layout.getBoundingBox(run.transformedStart)
                        val side = min(box.width, box.height)
                        if (side <= 0f) continue@emojiInputRunsLocal
                        drawImage(
                            image = bitmap,
                            srcOffset = IntOffset.Zero,
                            srcSize = IntSize(bitmap.width, bitmap.height),
                            dstOffset = IntOffset(
                                (box.left + (box.width - side) / 2f).roundToInt(),
                                (box.top + (box.height - side) / 2f - scroll).roundToInt(),
                            ),
                            dstSize = IntSize(side.roundToInt(), side.roundToInt()),
                            filterQuality = FilterQuality.Medium,
                        )
                    }
                },
            decorator = TextFieldDecorator { inner ->
                Box {
                    if (commentFieldState.text.isEmpty()) {
                        Text(
                            if (replyToComment != null) {
                                "回复 ${replyToComment.item.author.name}..."
                            } else {
                                "写下你的评论..."
                            },
                            fontSize = 16.sp,
                        )
                    }
                    inner()
                }
            },
        )
        AppIconButton(
            onClick = onSend,
            modifier = Modifier
                .size(24.dp)
                .testTag(COMMENT_SEND_BUTTON_TAG),
            enabled = sendEnabled,
        ) {
            if (isSending) {
                AppSpinner(size = 16.dp, color = MaterialTheme.colorScheme.primary)
            } else {
                Icon(
                    Icons.AutoMirrored.Outlined.Send,
                    contentDescription = "发送评论",
                    tint = if (commentFieldState.text.isNotBlank()) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    },
                )
            }
        }
    }
}

/** 输入行下方的选项行：同步到想法、最近用过的表情、展开评论工具。 */
@Composable
internal fun CommentScreenOptionsRow(
    syncToPin: Boolean?,
    defaultSyncToPin: Boolean,
    enabled: Boolean,
    onSyncToPinChange: (syncThis: Boolean, syncByDefault: Boolean) -> Unit,
    senderMenu: @Composable () -> Unit,
    recentEmojis: List<String>,
    emojiInlineContent: Map<String, InlineTextContent>,
    onEmojiClick: (String) -> Unit,
    showCommentTools: Boolean,
    /** 有录音或附件时工具必须保持展开，按钮禁用。 */
    commentToolsRequired: Boolean,
    commentToolsVisible: Boolean,
    onToggleCommentTools: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.PageHorizontalPadding),
    ) {
        val syncMaxWidth = (maxWidth - AppDimens.TouchTarget * 2 - AppDimens.Space12).coerceAtLeast(AppDimens.Space32)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (syncToPin != null) {
                var showRange by remember { mutableStateOf(false) }
                Box {
                    Row(
                        modifier = Modifier
                            .widthIn(max = syncMaxWidth)
                            .heightIn(min = AppDimens.TouchTarget)
                            .testTag(COMMENT_SYNC_PIN_TAG)
                            .toggleable(value = syncToPin || showRange, enabled = enabled, role = Role.Checkbox) { checked ->
                                if (checked) showRange = true else onSyncToPinChange(false, false)
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppCheckbox(
                            checked = syncToPin || showRange,
                            onCheckedChange = null,
                            enabled = enabled,
                            modifier = Modifier.size(32.dp).clearAndSetSemantics {},
                        )
                        Text("同时发布到想法", style = LocalAppSkinTokens.current.footnote2, color = LocalAppSkinTokens.current.onSurfaceVariantSummary)
                    }
                    val choices = listOf("只同步一次", "默认同步到想法")

                    fun choose(index: Int) {
                        showRange = false
                        onSyncToPinChange(true, index == 1)
                    }
                    AppSelectionDropdownMenu(showRange, { showRange = false }, choices, if (defaultSyncToPin) 1 else 0, ::choose, renderInRootScaffold = false) {
                        choices.forEachIndexed { index, label ->
                            AppDropdownMenuItem(
                                text = { Text(label) },
                                onClick = { choose(index) },
                                modifier = Modifier.testTag(if (index == 0) "comment_sync_default_off" else "comment_sync_default_on"),
                                trailingIcon = { if (defaultSyncToPin == (index == 1)) Icon(Icons.Default.Check, "已选择") },
                            )
                        }
                    }
                }
            }
            Box(Modifier.padding(start = AppDimens.Space12)) { senderMenu() }
            if (recentEmojis.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(start = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items(recentEmojis.take(6), key = { it }) { placeholder ->
                        Text(
                            text = buildAnnotatedString {
                                val key = commentEmojiInlineKey(placeholder)
                                if (key != null) {
                                    appendInlineContent(key, placeholder)
                                } else {
                                    append(placeholder)
                                }
                            },
                            inlineContent = emojiInlineContent,
                            fontSize = 22.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onEmojiClick(placeholder) },
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            AppIconButton(
                onClick = onToggleCommentTools,
                enabled = !commentToolsRequired,
                modifier = Modifier
                    .size(AppDimens.TouchTarget)
                    .testTag(COMMENT_TOOLS_BUTTON_TAG)
                    .semantics {
                        stateDescription = when {
                            commentToolsRequired -> "有录音或附件，工具保持展开"
                            showCommentTools -> "已展开"
                            else -> "已收起"
                        }
                    },
            ) {
                Icon(
                    imageVector = when {
                        commentToolsRequired -> Icons.Filled.MoreVert
                        showCommentTools -> Icons.Filled.Close
                        else -> Icons.Filled.Add
                    },
                    contentDescription = when {
                        commentToolsRequired -> "评论工具已展开"
                        showCommentTools -> "收起评论工具"
                        else -> "更多评论工具"
                    },
                    tint = when {
                        commentToolsRequired -> LocalAppSkinTokens.current.disabledOnSurface
                        commentToolsVisible -> LocalAppSkinTokens.current.primary
                        else -> LocalAppSkinTokens.current.onSurfaceVariantActions
                    },
                )
            }
        }
    }
}
