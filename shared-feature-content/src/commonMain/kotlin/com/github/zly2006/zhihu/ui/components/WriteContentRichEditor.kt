package com.github.zly2006.zhihu.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.fleeksoft.ksoup.Ksoup
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withTimeoutOrNull

class RichEditorState(
    initialHtml: String = "",
) {
    var html by mutableStateOf(initialHtml)
        private set
    var text by mutableStateOf(Ksoup.parseBodyFragment(initialHtml).text())
        private set
    var isReady by mutableStateOf(false)
        private set
    val hasContent: Boolean
        get() = text.isNotBlank() || "<img" in html || "<video" in html

    private val channel = Channel<RichEditorCommand>(Channel.UNLIMITED)
    private val snapshots = mutableSetOf<CompletableDeferred<String>>()
    internal val commands = channel.receiveAsFlow()

    fun loadHtml(value: String) {
        html = value
        text = Ksoup.parseBodyFragment(value).text()
        if (isReady) channel.trySend(RichEditorCommand.Load(value))
    }

    fun insertImage(url: String, width: Int, height: Int) {
        check(isReady) { "图文编辑器尚未就绪" }
        channel.trySend(RichEditorCommand.Image(url, width, height))
    }

    suspend fun snapshotHtml(): String = withTimeoutOrNull(10_000) {
        check(isReady) { "图文编辑器尚未就绪" }
        val result = CompletableDeferred<String>()
        snapshots += result
        try {
            channel.send(RichEditorCommand.Snapshot(result))
            result.await()
        } finally {
            snapshots -= result
        }
    } ?: error("读取图文编辑器内容超时，请重试")

    internal fun setReady(ready: Boolean) {
        isReady = ready
        /** 卸载后无人消费命令，需终止快照并清空旧命令；通道保留供同一草稿重新挂载。 */
        if (!ready) {
            snapshots.forEach { it.completeExceptionally(IllegalStateException("图文编辑器已关闭")) }
            while (true) {
                val command = channel.tryReceive().getOrNull() ?: break
                (command as? RichEditorCommand.Snapshot)?.result?.completeExceptionally(IllegalStateException("图文编辑器已关闭"))
            }
        }
    }

    internal fun update(value: String, plainText: String) {
        html = value
        text = plainText
    }
}

internal sealed interface RichEditorCommand {
    data class Load(
        val html: String,
    ) : RichEditorCommand

    data class Image(
        val url: String,
        val width: Int,
        val height: Int,
    ) : RichEditorCommand

    data class Snapshot(
        val result: CompletableDeferred<String>,
    ) : RichEditorCommand
}

expect val isRichEditorSupported: Boolean

@Composable
expect fun WriteContentRichEditor(
    state: RichEditorState,
    placeholder: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
)
