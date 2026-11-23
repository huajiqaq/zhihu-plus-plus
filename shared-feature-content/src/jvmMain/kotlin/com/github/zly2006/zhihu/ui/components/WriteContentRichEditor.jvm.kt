package com.github.zly2006.zhihu.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

actual val isRichEditorSupported: Boolean = false

@Composable
actual fun WriteContentRichEditor(
    state: RichEditorState,
    placeholder: String,
    enabled: Boolean,
    modifier: Modifier,
): Unit = error("当前 JVM 编辑器不支持图文富文本编辑，请使用 Markdown 模式")
