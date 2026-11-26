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

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.NativeClipboard
import androidx.compose.ui.platform.asAwtTransferable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import com.github.zly2006.zhihu.account.LocalZhihuAccountStore
import com.github.zly2006.zhihu.account.ZhihuAccountRepository
import com.github.zly2006.zhihu.account.ZhihuAccountStore
import com.github.zly2006.zhihu.data.SegmentInfoMark
import com.github.zly2006.zhihu.data.SegmentInfoMeta
import com.github.zly2006.zhihu.data.SegmentInfoParagraph
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.Navigator
import com.github.zly2006.zhihu.theme.ThemeManager
import com.github.zly2006.zhihu.theme.ThemeSnapshot
import com.github.zly2006.zhihu.theme.ZhihuTheme
import com.github.zly2006.zhihu.ui.COMMENT_INPUT_TAG
import com.github.zly2006.zhihu.ui.COMMENT_SCREEN_LIST_TAG
import com.github.zly2006.zhihu.ui.COMMENT_SEND_BUTTON_TAG
import com.github.zly2006.zhihu.ui.components.LocalNavHostLifecycleOwner
import com.github.zly2006.zhihu.ui.components.LocalOnRequestContentRefresh
import com.github.zly2006.zhihu.ui.components.LocalSegmentSelectionTarget
import com.github.zly2006.zhihu.ui.components.SegmentSelectionTarget
import com.github.zly2006.zhihu.util.ZHIHU_WEB_ZSE93
import com.github.zly2006.zhihu.util.ZhihuFetchSignature
import com.github.zly2006.zhihu.util.applySegmentInfosToHtml
import com.github.zly2006.zhihu.viewmodel.comment.SegmentCommentEvents
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.AfterClass
import org.junit.BeforeClass
import java.awt.datatransfer.DataFlavor
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class RenderMarkdownSegmentUiTest {
    @BeforeTest
    fun resetFixture() {
        requests.clear()
        cancelledRequests.set(0)
        responseGate = CompletableDeferred()
        creationStatus = HttpStatusCode.Created
        cancellationStatus = HttpStatusCode.NoContent
    }

    @AfterTest
    fun releaseFixture() {
        responseGate.complete(Unit)
    }

    @Test
    fun secondOccurrenceCreatesSignedSegmentAndOpensCommentsAfterToolbarDisappears() {
        val owner = ScreenOwner()
        val refreshes = AtomicInteger()
        try {
            runDesktopComposeUiTest(width = 720, height = 800) {
                setContent {
                    CompositionLocalProvider(
                        LocalOnRequestContentRefresh provides {
                            refreshes.incrementAndGet()
                            Unit
                        },
                    ) {
                        TestArticle("""<p data-pid="paragraph-1">重复文字段😀重复文字段</p>""", owner, RecordingClipboard())
                    }
                }
                waitForIdle()
                val start = textPoint("重复文字段😀重复文字段", 7, after = false)
                val end = textPoint("重复文字段😀重复文字段", 11, after = true)
                selectBetween(start, end)
                onNodeWithText("评论").performClick()

                waitUntil(timeoutMillis = 10_000) { requests.any { it.method == HttpMethod.Post } }
                onNodeWithText("喜欢").assertDoesNotExist()
                onNodeWithTag(COMMENT_SCREEN_LIST_TAG).assertDoesNotExist()
                assertEquals(1, requests.size, "响应放行前不应打开评论或发送其他请求")
                val request = requests.single()
                assertEquals(HttpMethod.Post, request.method)
                assertEquals("www.zhihu.com", request.url.host)
                assertEquals("/api/v4/reaction/answers/9001/segment_reaction", request.url.encodedPath)
                val requestBody = (request.body as TextContent).text
                val body = Json.parseToJsonElement(requestBody).jsonObject
                assertEquals("重复文字段", body.getValue("content").jsonPrimitive.content)
                val position = body.getValue("position").jsonObject
                for ((key, offset) in listOf("start" to "7", "end" to "12")) {
                    val endpoint = position.getValue(key).jsonObject
                    assertEquals("paragraph-1", endpoint.getValue("paragraph_id").jsonPrimitive.content)
                    assertEquals(offset, endpoint.getValue("offset").jsonPrimitive.content)
                }
                assertEquals("fetch", request.headers["x-requested-with"])
                assertEquals(ZHIHU_WEB_ZSE93, request.headers["x-zse-93"])
                assertTrue(
                    (0..126).any { seed ->
                        request.headers["x-zse-96"] == ZhihuFetchSignature.createZse96Header(
                            ZHIHU_WEB_ZSE93,
                            request.url.toString(),
                            DEVICE_COOKIE,
                            requestBody,
                            seed = seed,
                        )
                    },
                    "随机 seed 下仍须签名实际正文与设备 Cookie",
                )

                responseGate.complete(Unit)
                waitUntil(timeoutMillis = 10_000) {
                    onAllNodes(hasTestTag(COMMENT_INPUT_TAG)).fetchSemanticsNodes().isNotEmpty()
                }
                waitUntil(timeoutMillis = 10_000) {
                    requests.any { it.method == HttpMethod.Get && it.url.encodedPath == COMMENT_LIST_PATH }
                }
                onNodeWithTag(COMMENT_INPUT_TAG).assertExists()
                onNodeWithText("暂无评论").assertIsDisplayed()
                onNodeWithTag(COMMENT_SEND_BUTTON_TAG).assertIsNotEnabled()
                val listRequest = requests.single { it.method == HttpMethod.Get && it.url.encodedPath == COMMENT_LIST_PATH }
                assertEquals("segment-fixture", listRequest.url.parameters["segment_id"])
                assertEquals(1, refreshes.get(), "从评论动作新建划线也必须刷新正文获取划线")
                assertEquals(listOf(request), requests.filter { it.method == HttpMethod.Post }, "打开评论不能自动发表")
                assertTrue(SegmentCommentEvents.capture(checkNotNull(fixtureStore).client.httpClient(), "answer", "9001").asMap().isEmpty())
            }
        } finally {
            responseGate.complete(Unit)
            owner.viewModelStore.clear()
        }
    }

    @Test
    fun containingLikedRangeCopiesWholeSourceAndDoesNotReviveFromStaleRefresh() {
        val text = "重复😀重复"
        val highlighted = applySegmentInfosToHtml(
            """<p data-pid="paragraph-1">$text</p>""",
            listOf(
                SegmentInfoParagraph(
                    "paragraph-1",
                    text,
                    listOf(
                        SegmentInfoMark(0, text.length, SegmentInfoMeta(segIds = listOf("segment-fixture"), isLike = true, likeCount = 1, myCommentCount = 1)),
                    ),
                ),
            ),
            contentId = "9001",
            contentType = "answer",
        )
        val owner = ScreenOwner()
        val clipboard = RecordingClipboard()
        val refreshes = AtomicInteger()
        try {
            runDesktopComposeUiTest(width = 720, height = 800) {
                var activeHtml by mutableStateOf(highlighted)
                setContent {
                    CompositionLocalProvider(
                        LocalOnRequestContentRefresh provides {
                            refreshes.incrementAndGet()
                            activeHtml = highlighted + "<p>旧响应仍带已取消的划线</p>"
                        },
                    ) {
                        TestArticle(activeHtml, owner, clipboard, sourceHtml = """<p data-pid="paragraph-1">$text</p>""")
                    }
                }
                waitForIdle()
                val start = textPoint(text, 4, after = false)
                val end = textPoint(text, 5, after = true)
                selectBetween(start, end)
                onNodeWithText("取消喜欢").assertExists()
                onNodeWithText("复制").performClick()
                runOnIdle { assertEquals(listOf(text), clipboard.copied, "扩大选区必须同步真实复制内容") }
                selectBetween(start, end)
                onNodeWithText("取消喜欢").performClick()
                waitUntil(timeoutMillis = 10_000) { requests.any { it.method == HttpMethod.Delete } }
                onNodeWithText("取消喜欢").assertDoesNotExist()
                responseGate.complete(Unit)
                waitUntil(timeoutMillis = 10_000) { refreshes.get() == 1 }
                val cancelledPoint = textPoint(text, 0, after = false)
                onRoot().performTouchInput { click(cancelledPoint) }
                waitForIdle()
                onNodeWithText("划线片段").assertDoesNotExist()
                onNodeWithContentDescription("复制内容").assertDoesNotExist()
                onNodeWithTag(COMMENT_SCREEN_LIST_TAG).assertDoesNotExist()
                val request = requests.single()
                assertEquals(HttpMethod.Delete, request.method)
                assertEquals(
                    "segment-fixture",
                    Json
                        .parseToJsonElement((request.body as TextContent).text)
                        .jsonObject
                        .getValue("seg_ids")
                        .jsonPrimitive.content,
                )
                selectBetween(textPoint(text, 4, false), textPoint(text, 5, true))
                onNodeWithText("取消喜欢").assertDoesNotExist()
                onNodeWithText("复制").performClick()
                runOnIdle { assertEquals(listOf(text, "重复"), clipboard.copied, "旧响应不能让已取消的划线重新扩大选区") }
            }
        } finally {
            owner.viewModelStore.clear()
        }
    }

    @Test
    fun failedSegmentCreationShowsFeedbackWithoutOpeningCommentsOrRefreshing() {
        val owner = ScreenOwner()
        val host = ScreenOwner()
        val refreshes = AtomicInteger()
        creationStatus = HttpStatusCode.InternalServerError
        try {
            runDesktopComposeUiTest(width = 720, height = 800) {
                setContent {
                    CompositionLocalProvider(
                        LocalOnRequestContentRefresh provides {
                            refreshes.incrementAndGet()
                            Unit
                        },
                    ) {
                        TestArticle("""<p data-pid="paragraph-1">重复文字段😀重复文字段</p>""", owner, RecordingClipboard(), hostOwner = host)
                    }
                }
                waitForIdle()
                val start = textPoint("重复文字段😀重复文字段", 7, after = false)
                val end = textPoint("重复文字段😀重复文字段", 11, after = true)
                selectBetween(start, end)
                onNodeWithText("评论").performClick()
                waitUntil(timeoutMillis = 10_000) { requests.any { it.method == HttpMethod.Post } }
                onNodeWithText("喜欢").assertDoesNotExist()
                onNodeWithTag(COMMENT_SCREEN_LIST_TAG).assertDoesNotExist()
                assertEquals(0, refreshes.get(), "请求未完成时不能刷新正文")

                responseGate.complete(Unit)
                waitUntil(timeoutMillis = 10_000) {
                    onAllNodes(hasText("HTTP 500", substring = true)).fetchSemanticsNodes().isNotEmpty()
                }
                onNodeWithText("段评操作失败", substring = true, useUnmergedTree = true).assertIsDisplayed()
                onNodeWithText("HTTP 500", substring = true, useUnmergedTree = true).assertIsDisplayed()
                onNode(
                    hasAnyAncestor(isDialog()) and hasText("HTTP 500", substring = true),
                    useUnmergedTree = true,
                ).assertIsDisplayed()
                onNodeWithTag(COMMENT_SCREEN_LIST_TAG).assertDoesNotExist()
                onNodeWithTag(COMMENT_INPUT_TAG).assertDoesNotExist()
                assertEquals(0, refreshes.get(), "创建失败后不能刷新正文")
                assertTrue(SegmentCommentEvents.capture(checkNotNull(fixtureStore).client.httpClient(), "answer", "9001").asMap().isEmpty())
                assertEquals(1, requests.size, "创建失败不能请求评论列表或提交评论")
                val request = requests.single()
                assertEquals(HttpMethod.Post, request.method)
                assertEquals("www.zhihu.com", request.url.host)
                assertEquals("/api/v4/reaction/answers/9001/segment_reaction", request.url.encodedPath)
                val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                assertEquals("重复文字段", body.getValue("content").jsonPrimitive.content)
                val position = body.getValue("position").jsonObject
                for ((key, offset) in listOf("start" to "7", "end" to "12")) {
                    val endpoint = position.getValue(key).jsonObject
                    assertEquals("paragraph-1", endpoint.getValue("paragraph_id").jsonPrimitive.content)
                    assertEquals(offset, endpoint.getValue("offset").jsonPrimitive.content)
                }
                assertTrue(request.headers["x-zse-96"].orEmpty().startsWith("2.0_"))

                runOnIdle { owner.lifecycle.currentState = Lifecycle.State.STARTED }
                waitUntil(timeoutMillis = 10_000) {
                    onAllNodes(hasText("段评提示"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
                }
                runOnIdle { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
                waitUntil(timeoutMillis = 10_000) {
                    onAllNodes(hasText("HTTP 500", substring = true)).fetchSemanticsNodes().isNotEmpty()
                }
                onNode(hasAnyAncestor(isDialog()) and hasText("HTTP 500", substring = true), useUnmergedTree = true).assertIsDisplayed()
                onNodeWithText("知道了").performClick()
                onNode(isDialog()).assertDoesNotExist()
                assertEquals(1, requests.size, "关闭提示和页面恢复不能重试写请求")
            }
        } finally {
            responseGate.complete(Unit)
            owner.viewModelStore.clear()
            host.viewModelStore.clear()
        }
    }

    @Test
    fun changingRequestOwnerCancelsCreationWithoutFeedbackOnTheNewPage() {
        val store = checkNotNull(fixtureStore)
        val originalSession = store.session
        for (change in listOf("account", "article", "source")) {
            resetFixture()
            creationStatus = HttpStatusCode.InternalServerError
            val owner = ScreenOwner()
            val refreshes = AtomicInteger()
            try {
                runDesktopComposeUiTest(width = 720, height = 800) {
                    var html by mutableStateOf("""<p data-pid="paragraph-1">重复文字段😀重复文字段</p>""")
                    var target by mutableStateOf(SegmentSelectionTarget("9001", "answer"))
                    setContent {
                        CompositionLocalProvider(
                            LocalOnRequestContentRefresh provides {
                                refreshes.incrementAndGet()
                                Unit
                            },
                        ) {
                            TestArticle(html, owner, RecordingClipboard(), target = target)
                        }
                    }
                    waitForIdle()
                    selectBetween(textPoint("重复文字段😀重复文字段", 7, false), textPoint("重复文字段😀重复文字段", 11, true))
                    onNodeWithText("评论").performClick()
                    waitUntil(timeoutMillis = 10_000) { requests.any { it.method == HttpMethod.Post } }
                    runOnIdle {
                        when (change) {
                            "account" -> store.replaceSession(originalSession.copy(username = "replacement-fixture"))
                            "article" -> target = SegmentSelectionTarget("9002", "answer")
                            else -> html = """<p data-pid="paragraph-1">正文已更新</p>"""
                        }
                    }
                    waitUntil(timeoutMillis = 10_000) { cancelledRequests.get() == 1 }
                    responseGate.complete(Unit)
                    waitForIdle()
                    onNodeWithText("段评提示", useUnmergedTree = true).assertDoesNotExist()
                    onNodeWithTag(COMMENT_SCREEN_LIST_TAG).assertDoesNotExist()
                    onNodeWithTag(COMMENT_INPUT_TAG).assertDoesNotExist()
                    assertEquals(0, refreshes.get(), "$change 变化后旧请求不能刷新新正文")
                    assertEquals(1, requests.size, "$change 变化后不能产生评论或重试请求")
                }
            } finally {
                responseGate.complete(Unit)
                owner.viewModelStore.clear()
                if (change == "account") store.replaceSession(originalSession)
            }
        }
    }

    @Suppress("DEPRECATION")
    @Test
    fun inlineImageWithDifferentAltAndTitleKeepsSelectionSourceFaithful() {
        val owner = ScreenOwner()
        val clipboard = object : ClipboardManager {
            var copied: AnnotatedString? = null

            override fun setText(annotatedString: AnnotatedString) {
                copied = annotatedString
            }

            override fun getText(): AnnotatedString? = copied
        }
        val image = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGP4z8DwHwAFAAH/iZk9HQAAAABJRU5ErkJggg=="
        val html = """<p data-pid="p">前文<img src="$image" alt="X" title="TITLE" width="24" height="24">后文</p>"""
        val controlHtml = html.replace("TITLE", "X")
        try {
            runDesktopComposeUiTest(width = 720, height = 600) {
                var activeHtml by mutableStateOf(controlHtml)
                var recreate by mutableStateOf(false)
                setContent {
                    CompositionLocalProvider(LocalClipboardManager provides clipboard) {
                        key(recreate) { TestArticle(activeHtml, owner, RecordingClipboard(), useTiqian = true) }
                    }
                }
                waitForIdle()
                val body = onNode(hasText("前文", substring = true) and SemanticsMatcher.keyIsDefined(SemanticsActions.SetSelection), useUnmergedTree = true)
                body.performSemanticsAction(SemanticsActions.SetSelection) { assertTrue(it(0, 2, false)) }
                body.performSemanticsAction(SemanticsActions.CopyText) { assertTrue(it()) }
                runOnIdle {
                    assertEquals("前文", clipboard.copied?.text)
                    activeHtml = html
                }
                waitForIdle()
                onNodeWithTag("tiqian_inline_image", useUnmergedTree = true).assertExists()
                val rendered = body
                    .fetchSemanticsNode()
                    .config[SemanticsProperties.Text]
                    .single()
                    .text
                assertTrue(rendered.startsWith("前文") && rendered.endsWith("后文"))
                for ((start, end, expected) in listOf(
                    Triple(0, 2, "前文"),
                    Triple(rendered.lastIndexOf("后文"), rendered.length, "后文"),
                    Triple(0, rendered.length, "前文X后文"),
                )) {
                    body.performSemanticsAction(SemanticsActions.SetSelection) { assertTrue(it(start, end, false)) }
                    assertEquals(TextRange(start, end), body.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
                    body.performSemanticsAction(SemanticsActions.CopyText) { assertTrue(it()) }
                    runOnIdle { assertEquals(expected, clipboard.copied?.text) }
                }
                runOnIdle { recreate = true }
                waitForIdle()
                onNodeWithTag("tiqian_inline_image", useUnmergedTree = true).assertExists()
                val fresh = body
                    .fetchSemanticsNode()
                    .config[SemanticsProperties.Text]
                    .single()
                    .text
                val start = fresh.lastIndexOf("后文")
                assertTrue(start >= 0)
                body.performSemanticsAction(SemanticsActions.SetSelection) { assertTrue(it(start, fresh.length, false)) }
                assertEquals(TextRange(start, fresh.length), body.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
                body.performSemanticsAction(SemanticsActions.CopyText) { assertTrue(it()) }
                runOnIdle { assertEquals("后文", clipboard.copied?.text) }
                assertTrue(requests.isEmpty())
            }
        } finally {
            owner.viewModelStore.clear()
        }
    }

    private data class MarkdownTextFragment(
        val nodeId: Int,
        val bounds: Rect,
        val layout: TextLayoutResult,
        val startOffset: Int = 0,
    ) {
        val endOffset: Int get() = startOffset + layout.layoutInput.text.length
    }

    /** HRM 按视觉行/run 绘制；用真实布局映射原段的 UTF-16 偏移，不能假定全文属于单个节点。 */
    private fun ComposeUiTest.paragraphFragments(text: String): List<MarkdownTextFragment> {
        val fragments = onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),
            useUnmergedTree = true,
        ).fetchSemanticsNodes()
            .mapNotNull { node ->
                if (node.boundsInRoot.width <= 0f || node.boundsInRoot.height <= 0f) return@mapNotNull null
                val layouts = mutableListOf<TextLayoutResult>()
                runOnIdle {
                    assertTrue(node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts) == true)
                }
                val layout = layouts.single()
                if (layout.layoutInput.text.isEmpty()) null else MarkdownTextFragment(node.id, node.boundsInRoot, layout)
            }.sortedWith(compareBy({ it.bounds.top + it.layout.getLineBaseline(0) }, { it.bounds.left }))
        val matches = fragments.indices.mapNotNull { first ->
            var offset = 0
            val matched = mutableListOf<MarkdownTextFragment>()
            for (fragment in fragments.drop(first)) {
                val visibleText = fragment.layout.layoutInput.text.text
                if (!text.startsWith(visibleText, offset)) {
                    while (offset < text.length && (text[offset] == ' ' || text[offset] == '\t')) offset++
                }
                if (!text.startsWith(visibleText, offset)) break
                matched += fragment.copy(startOffset = offset)
                offset += visibleText.length
                if (offset == text.length) return@mapNotNull matched
            }
            null
        }
        assertEquals(1, matches.size, "原段必须唯一映射到完整的可见文字 runs：$text")
        return matches.single()
    }

    private fun ComposeUiTest.textPoint(text: String, index: Int, after: Boolean): Offset {
        require(index in text.indices)
        require(!(text[index].isLowSurrogate() && index > 0 && text[index - 1].isHighSurrogate()))
        val fragment = paragraphFragments(text).single { index in it.startOffset until it.endOffset }
        onNode(SemanticsMatcher("painted text run ${fragment.nodeId}") { it.id == fragment.nodeId }, useUnmergedTree = true)
            .assertIsDisplayed()
        val box = fragment.layout.getBoundingBox(index - fragment.startOffset)
        assertTrue(box.width > 0f && box.height > 0f, "选区端点必须命中实际字形：$text index=$index box=$box")
        val x = if (after) box.right - box.width * 0.1f else box.left + box.width * 0.1f
        return fragment.bounds.topLeft + Offset(x, box.center.y)
    }

    private fun ComposeUiTest.selectBetween(start: Offset, end: Offset) {
        onRoot().performTouchInput {
            down(start)
            advanceEventTime(1_000L)
            moveTo(end, delayMillis = 100L)
            up()
        }
        onNodeWithText("评论").assertExists()
    }

    @Composable
    private fun TestArticle(
        html: String,
        owner: ScreenOwner,
        clipboard: RecordingClipboard,
        useTiqian: Boolean = false,
        scrollState: ScrollState? = null,
        sourceHtml: String? = html,
        target: SegmentSelectionTarget? = SegmentSelectionTarget("9001", "answer"),
        hostOwner: ScreenOwner = owner,
    ) {
        val dispatcher = remember { NavigationEventDispatcher() }
        val dispatcherValue = remember(dispatcher) {
            val dispatcherOwner = object : NavigationEventDispatcherOwner {
                override val navigationEventDispatcher = dispatcher
            }

            /** navigationevent-compose 仅在运行时可用，须反射注册公开 Local。 */
            val localClass = Class.forName("androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner")
            val local = localClass.getField("INSTANCE").get(null)
            localClass
                .getMethod("provides", NavigationEventDispatcherOwner::class.java)
                .invoke(local, dispatcherOwner) as ProvidedValue<*>
        }
        DisposableEffect(dispatcher) { onDispose { dispatcher.dispose() } }
        ZhihuTheme {
            CompositionLocalProvider(
                LocalZhihuAccountStore provides checkNotNull(fixtureStore),
                LocalLifecycleOwner provides owner,
                LocalNavHostLifecycleOwner provides hostOwner,
                LocalViewModelStoreOwner provides owner,
                dispatcherValue,
                LocalNavigator provides Navigator({ error("不应触发导航：$it") }, { error("不应触发返回") }),
                LocalClipboard provides clipboard,
                LocalSegmentSelectionTarget provides target,
            ) {
                Box(Modifier.fillMaxSize().padding(40.dp)) {
                    RenderMarkdown(
                        html = html,
                        sourceHtml = sourceHtml,
                        scrollState = scrollState ?: rememberScrollState(),
                        enableScroll = scrollState != null,
                        useTiqianRenderer = useTiqian,
                    )
                }
            }
        }
    }

    private class ScreenOwner :
        LifecycleOwner,
        ViewModelStoreOwner {
        override val lifecycle: LifecycleRegistry = LifecycleRegistry.createUnsafe(this).apply {
            currentState = Lifecycle.State.RESUMED
        }
        override val viewModelStore = ViewModelStore()
    }

    private class RecordingClipboard : Clipboard {
        val copied = mutableListOf<String>()
        override val nativeClipboard: NativeClipboard = Any()

        override suspend fun getClipEntry(): ClipEntry? = null

        override suspend fun setClipEntry(clipEntry: ClipEntry?) {
            copied += requireNotNull(clipEntry?.asAwtTransferable).getTransferData(DataFlavor.stringFlavor) as String
        }
    }

    companion object {
        private const val DEVICE_COOKIE = "synthetic-device"
        private const val COMMENT_LIST_PATH = "/api/v4/comment_v5/answers/9001/segment/root_comment"
        private val requests = CopyOnWriteArrayList<HttpRequestData>()
        private val cancelledRequests = AtomicInteger()
        private lateinit var responseGate: CompletableDeferred<Unit>

        @Volatile
        private var creationStatus = HttpStatusCode.Created

        @Volatile
        private var cancellationStatus = HttpStatusCode.NoContent
        private var originalHome: String? = null
        private var originalTheme: ThemeSnapshot? = null
        private var fixtureStarted = false
        private var home: Path? = null
        private var engine: MockEngine? = null
        private var fixtureStore: ZhihuAccountStore? = null

        @BeforeClass
        @JvmStatic
        fun setUpIsolatedAccount() {
            originalHome = System.getProperty("user.home")
            originalTheme = ThemeManager.snapshot()
            fixtureStarted = true
            try {
                val directory = Files.createTempDirectory("zhihu-segment-selection-ui-")
                home = directory
                System.setProperty("user.home", directory.toString())
                val accountFile = directory.resolve(".zhihu-plus-plus/account.json")
                Files.createDirectories(accountFile.parent)
                Files.writeString(
                    accountFile,
                    """{"login":true,"username":"segment-ui-fixture","cookies":{"d_c0":"$DEVICE_COOKIE","z_c0":"synthetic-session"}}""",
                )
                val mock = MockEngine { request ->
                    requests += request
                    when {
                        request.method == HttpMethod.Post &&
                            request.url.encodedPath == "/api/v4/reaction/answers/9001/segment_reaction" -> {
                            try {
                                responseGate.await()
                            } catch (error: CancellationException) {
                                cancelledRequests.incrementAndGet()
                                throw error
                            }
                            respond(
                                if (creationStatus == HttpStatusCode.Created) {
                                    """{"seg_id":"segment-fixture"}"""
                                } else {
                                    """{"error":{"message":"synthetic failure"}}"""
                                },
                                creationStatus,
                                headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        }
                        request.method == HttpMethod.Delete &&
                            request.url.encodedPath == "/api/v4/reaction/answers/9001/segment_reaction" -> {
                            responseGate.await()
                            respond("", cancellationStatus)
                        }
                        request.method == HttpMethod.Get && request.url.encodedPath == COMMENT_LIST_PATH -> respond(
                            """{"data":[],"paging":{"is_end":true,"next":""}}""",
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                        else -> error("测试不允许其他 HTTP：${request.method} ${request.url}")
                    }
                }
                engine = mock
                fixtureStore = ZhihuAccountStore(ZhihuAccountRepository(kotlinx.io.files.Path(accountFile.toString())), mock)
                check(fixtureStore?.session?.username == "segment-ui-fixture") { "未加载隔离测试账户" }
                assertSame(mock, fixtureStore?.client?.httpClient()?.engine, "账户客户端必须使用 MockEngine")
            } catch (error: Throwable) {
                tearDownIsolatedAccount()
                throw error
            }
        }

        @AfterClass
        @JvmStatic
        fun tearDownIsolatedAccount() {
            if (!fixtureStarted) return
            fixtureStarted = false
            fixtureStore?.close()
            fixtureStore = null
            engine?.close()
            engine = null
            originalTheme?.let(ThemeManager::load)
            originalTheme = null
            if (originalHome == null) System.clearProperty("user.home") else System.setProperty("user.home", originalHome)
            originalHome = null
            home?.toFile()?.deleteRecursively()
            home = null
        }
    }
}
