package com.github.zly2006.zhihu.ui.components

import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.github.zly2006.zhihu.theme.ZhihuTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class PaginationPresentationTest {
    /** 生产列表结束空态不应仍调用加载尾部。 */
    @Test
    fun endedEmptyListShowsEmptyInsteadOfLoadingFooter() = runDesktopComposeUiTest(width = 412, height = 300) {
        var footerCalls = 0
        setContent {
            ZhihuTheme {
                PaginatedList<Int>(
                    items = emptyList(),
                    onLoadMore = {},
                    modifier = Modifier.fillMaxSize(),
                    isEnd = { true },
                    isLoading = { false },
                    loadError = { null },
                    onRetry = {},
                    footer = {
                        footerCalls++
                        Text("正在加载尾部")
                    },
                ) { Text("$it") }
            }
        }
        waitForIdle()
        onNodeWithText("暂无内容").assertExists()
        assertEquals(0, footerCalls)
    }

    /** 未加载的 idle 列表不能以 spinner 冒充加载；页面有明确加载状态后才显示 footer。 */
    @Test
    fun idleListDoesNotRenderLoadingFooter() = runDesktopComposeUiTest(width = 412, height = 300) {
        var footerCalls = 0
        setContent {
            ZhihuTheme {
                PaginatedList<Int>(
                    items = emptyList(),
                    onLoadMore = {},
                    modifier = Modifier.fillMaxSize(),
                    isEnd = { false },
                    isLoading = { false },
                    loadError = { null },
                    onRetry = {},
                    footer = {
                        footerCalls++
                        Text("正在加载尾部")
                    },
                ) { Text("$it") }
            }
        }
        waitForIdle()
        assertEquals(0, footerCalls)
        assertEquals(0, onAllNodesWithText("正在加载尾部").fetchSemanticsNodes().size)
    }

    @Test
    fun appendIsStateDrivenAndFailureRequiresAnExplicitRetry() = runDesktopComposeUiTest(width = 412, height = 300) {
        val items = mutableStateListOf(1)
        val loading = mutableStateOf(false)
        val error = mutableStateOf<String?>(null)
        val ended = mutableStateOf(false)
        var requests = 0
        var retries = 0
        setContent {
            ZhihuTheme {
                PaginatedList(
                    items = items,
                    onLoadMore = { requests++; loading.value = true },
                    isEnd = { ended.value },
                    isLoading = { loading.value },
                    loadError = { error.value },
                    onRetry = { retries++; error.value = null; loading.value = true; requests++ },
                    modifier = Modifier.fillMaxSize(),
                    footer = { Text("真实加载状态") },
                ) { Text("内容 $it", modifier = Modifier.height(48.dp)) }
            }
        }
        waitForIdle()
        assertEquals(1, requests)
        onNodeWithText("真实加载状态").assertExists()
        mainClock.advanceTimeBy(500)
        waitForIdle()
        assertEquals(1, requests, "加载中不能反复追加")

        runOnIdle { error.value = "追加失败"; loading.value = false }
        onNodeWithText("追加失败").assertExists()
        onNodeWithText("内容 1").assertExists()
        mainClock.advanceTimeBy(500)
        waitForIdle()
        assertEquals(1, requests, "失败后不能自动轮询请求")
        onNodeWithText("重试").performClick()
        waitForIdle()
        assertEquals(1, retries)
        assertEquals(2, requests)
        runOnIdle { ended.value = true; loading.value = false }
        waitForIdle()
        mainClock.advanceTimeBy(500)
        assertEquals(2, requests, "结束后停止追加")
    }

    @Test
    fun successfulShortPageMayAppendAgainWithoutPolling() = runDesktopComposeUiTest(width = 412, height = 300) {
        val items = mutableStateListOf(1)
        val loading = mutableStateOf(false)
        val ended = mutableStateOf(false)
        var requests = 0
        setContent {
            ZhihuTheme {
                PaginatedList(
                    items = items,
                    onLoadMore = { requests++; loading.value = true },
                    isEnd = { ended.value },
                    isLoading = { loading.value },
                    loadError = { null },
                    onRetry = {},
                    modifier = Modifier.fillMaxSize(),
                ) { Text("内容 $it", modifier = Modifier.height(48.dp)) }
            }
        }
        waitForIdle()
        assertEquals(1, requests)
        runOnIdle { items += 2; loading.value = false }
        waitForIdle()
        assertEquals(2, requests, "仍接近列表末端的成功页面需要接着追加")
        runOnIdle { ended.value = true; loading.value = false }
        waitForIdle()
        assertEquals(2, requests)
    }
}
