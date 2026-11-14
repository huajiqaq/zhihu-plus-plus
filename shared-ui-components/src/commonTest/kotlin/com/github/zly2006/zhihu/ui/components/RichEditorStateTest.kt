package com.github.zly2006.zhihu.ui.components

import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RichEditorStateTest {
    @Test
    fun restoredHtmlCountsTextAndImagesButNotEmptyMarkup() {
        assertFalse(RichEditorState("<p><br></p>").hasContent)
        assertTrue(RichEditorState("<p><strong>正文</strong></p>").hasContent)
        assertTrue(RichEditorState("<p><img src=\"https://picx.zhimg.com/test.jpg\"></p>").hasContent)
    }

    @Test
    fun commandsRetainImageDimensionsAndSnapshotCompletesWithCurrentHtml() = runTest {
        val state = RichEditorState()
        state.setReady(true)
        val job = launch {
            state.commands.take(2).collect { command ->
                when (command) {
                    is RichEditorCommand.Image -> {
                        assertEquals(420, command.width)
                        assertEquals(280, command.height)
                        state.update("<p><img src=\"${command.url}\"></p>", "")
                    }
                    is RichEditorCommand.Snapshot -> command.result.complete(state.html)
                    else -> error("Unexpected command")
                }
            }
        }
        state.insertImage("https://picx.zhimg.com/test.jpg", 420, 280)
        assertTrue(state.snapshotHtml().contains("<img"))
        job.join()
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun closedEditorFailsPendingSnapshotInsteadOfWaitingForever() = runTest {
        val state = RichEditorState("<p>保留草稿</p>")
        state.setReady(true)
        val snapshot = async { runCatching { state.snapshotHtml() } }
        runCurrent()
        state.setReady(false)
        assertTrue(snapshot.await().exceptionOrNull() is IllegalStateException)
        assertEquals("<p>保留草稿</p>", state.html)
        assertFailsWith<IllegalStateException> { state.snapshotHtml() }
    }

    @Test
    fun contentLoadedBeforePlatformReadyIsTheRestoredStartingPoint() = runTest {
        val state = RichEditorState()
        state.loadHtml("<p><strong>云端草稿</strong></p>")
        assertTrue(state.hasContent)
        assertFalse(state.isReady)
        state.setReady(true)
        state.loadHtml("<p>修改后的草稿</p>")
        assertEquals("<p>修改后的草稿</p>", (state.commands.first() as RichEditorCommand.Load).html)
    }
}
