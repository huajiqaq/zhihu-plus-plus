package com.github.zly2006.zhihu.ui.article

import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.runtime.Composable
import com.github.zly2006.zhihu.markdown.MarkdownSearchState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArticleContentSearchTest {
    @Test
    fun queryUsesRendererMatchesAndResetsCurrent() {
        val renderer = TestRenderer()
        val state = ArticleContentSearchState(renderer)
        state.show()
        state.update("needle")
        state.next()
        assertEquals(1, state.current)
        state.update("other")
        assertEquals("other", renderer.receivedQuery)
        assertEquals(0, state.current)
        assertEquals(renderer.matchCount, state.matchCount)
    }

    @Test
    fun nextAndPreviousRevealRealRendererMatchesAndWrap() = runTest {
        val renderer = TestRenderer()
        val state = ArticleContentSearchState(renderer)
        state.show()
        state.update("needle")
        state.revealCurrent()
        state.previous()
        state.revealCurrent()
        state.next()
        state.revealCurrent()
        state.next()
        state.revealCurrent()
        assertEquals(listOf(0, 2, 0, 1), renderer.revealed)
    }

    @Test
    fun singleMatchCanBeRevealedAgainAfterManualScrolling() = runTest {
        val renderer = TestRenderer(count = 1)
        val state = ArticleContentSearchState(renderer)
        state.show()
        state.update("needle")
        state.revealCurrent()
        val request = state.requestId
        state.next()
        assertTrue(state.requestId > request)
        state.revealCurrent()
        assertEquals(listOf(0, 0), renderer.revealed)
    }

    @Test
    fun emptyQueryDoesNotNavigate() = runTest {
        val renderer = TestRenderer()
        val state = ArticleContentSearchState(renderer)
        state.show()
        state.next()
        state.previous()
        state.revealCurrent()
        assertTrue(renderer.revealed.isEmpty())
        assertEquals(0, state.current)
    }

    @Test
    fun closingClearsHighlightAndRetainsQueryForReopen() = runTest {
        val renderer = TestRenderer()
        val state = ArticleContentSearchState(renderer)
        state.show()
        state.update("needle")
        state.hide()
        assertFalse(state.visible)
        assertEquals("needle", state.query)
        assertEquals("", renderer.receivedQuery)
        state.revealCurrent()
        assertTrue(renderer.revealed.isEmpty())
        state.show()
        assertEquals("needle", renderer.receivedQuery)
        assertTrue(state.visible)
    }

    @Test
    fun unsuccessfulRevealIsReportedRatherThanPretendingToNavigate() = runTest {
        val state = ArticleContentSearchState(TestRenderer(reached = false))
        state.show()
        state.update("needle")
        state.revealCurrent()
        assertTrue(state.failed)
        assertFalse(state.locating)
    }

    @Test
    fun retryRevealsTheSameResultWithoutChangingQueryOrIndex() = runTest {
        val renderer = TestRenderer(reached = false)
        val state = ArticleContentSearchState(renderer)
        state.show()
        state.update("needle")
        state.next()
        state.revealCurrent()
        val request = state.requestId
        state.retry()
        assertTrue(state.requestId > request)
        state.revealCurrent()
        assertEquals(listOf(1, 1), renderer.revealed)
        assertEquals("needle", state.query)
        assertEquals(1, state.current)
    }

    @Test
    fun obsoleteRevealCannotOverwriteNewQueryStatus() = runTest {
        val complete = CompletableDeferred<Unit>()
        val renderer = TestRenderer(reached = false, pending = complete)
        val state = ArticleContentSearchState(renderer)
        state.show()
        state.update("needle")
        val job = launch { state.revealCurrent() }
        runCurrent()
        state.update("other")
        complete.complete(Unit)
        job.join()
        assertFalse(state.failed)
        assertFalse(state.locating)
    }

    private class TestRenderer(
        val count: Int = 3,
        val reached: Boolean = true,
        val pending: CompletableDeferred<Unit>? = null,
    ) : MarkdownSearchState {
        var receivedQuery = ""
            private set
        val revealed = mutableListOf<Int>()
        override val matchCount: Int get() = if (receivedQuery.isBlank()) 0 else count

        override fun setQuery(query: String) {
            receivedQuery = query
        }

        override suspend fun reveal(index: Int): Boolean {
            revealed += index
            pending?.await()
            return reached
        }

        override fun clear() {
            receivedQuery = ""
        }

        @Composable
        override fun rememberBringIntoViewSpec(obscuredTopPx: Float, obscuredBottomPx: Float, fallback: BringIntoViewSpec): BringIntoViewSpec = fallback
    }
}
