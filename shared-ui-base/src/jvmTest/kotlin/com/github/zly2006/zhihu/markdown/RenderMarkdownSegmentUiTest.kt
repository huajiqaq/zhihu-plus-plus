/*
 * Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation (version 3 only).
 */

package com.github.zly2006.zhihu.markdown

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.LocalOnRequestContentRefresh
import com.github.zly2006.zhihu.ui.components.LocalSegmentSelectionTarget
import com.github.zly2006.zhihu.ui.components.SegmentCreationRange
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.AfterClass
import org.junit.BeforeClass
import org.tiqian.compose.CjkSelectionContainer
import org.tiqian.compose.CjkSelectionState
import org.tiqian.compose.material3.CjkText
import org.tiqian.compose.rememberCjkSelectionState
import org.tiqian.core.LayoutResult
import org.tiqian.core.getBoundingBoxes
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
        responseGate = CompletableDeferred()
        creationStatus = HttpStatusCode.Created
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
                        TestArticle("""<p data-pid="paragraph-1">重复😀重复</p>""", owner, RecordingClipboard())
                    }
                }
                waitForIdle()
                val start = textPoint("重复😀重复", 4, after = false)
                val end = textPoint("重复😀重复", 5, after = true)
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
                assertEquals("重复", body.getValue("content").jsonPrimitive.content)
                val position = body.getValue("position").jsonObject
                for ((key, offset) in listOf("start" to "4", "end" to "6")) {
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
                        TestArticle("""<p data-pid="paragraph-1">重复😀重复</p>""", owner, RecordingClipboard())
                    }
                }
                waitForIdle()
                val start = textPoint("重复😀重复", 4, after = false)
                val end = textPoint("重复😀重复", 5, after = true)
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
                assertEquals("重复", body.getValue("content").jsonPrimitive.content)
                val position = body.getValue("position").jsonObject
                for ((key, offset) in listOf("start" to "4", "end" to "6")) {
                    val endpoint = position.getValue(key).jsonObject
                    assertEquals("paragraph-1", endpoint.getValue("paragraph_id").jsonPrimitive.content)
                    assertEquals(offset, endpoint.getValue("offset").jsonPrimitive.content)
                }
                assertTrue(request.headers["x-zse-96"].orEmpty().startsWith("2.0_"))
            }
        } finally {
            responseGate.complete(Unit)
            owner.viewModelStore.clear()
        }
    }

    @Test
    fun crossingParagraphsReachesTheRealParentWithoutARequest() {
        val html = """<p data-pid="first">甲段文字</p><p data-pid="second">乙段文字</p>"""
        assertRejectedByRealParent(html, "甲段文字", 1, "乙段文字", 1, "段文字\n乙段")
    }

    @Test
    fun selectingFormattedTextReachesTheRealParentWithoutARequest() {
        val text = "加粗文字选段测试"
        val html = """<p data-pid="paragraph-1"><strong>$text</strong></p>"""
        assertRejectedByRealParent(html, text, 0, text, text.lastIndex, text)
        val partial = "前加粗文字后"
        assertRejectedByRealParent(
            """<p data-pid="paragraph-1">前<strong>加粗文字</strong>后</p>""",
            partial,
            1,
            partial,
            4,
            "加粗文字",
        )
    }

    private fun assertRejectedByRealParent(
        html: String,
        startText: String,
        startIndex: Int,
        endText: String,
        endIndex: Int,
        expectedSelection: String,
    ) {
        val owner = ScreenOwner()
        val clipboard = RecordingClipboard()
        try {
            runDesktopComposeUiTest(width = 720, height = 800) {
                setContent { TestArticle(html, owner, clipboard) }
                waitForIdle()
                val start = textPoint(startText, startIndex, after = false)
                val end = textPoint(endText, endIndex, after = true)

                selectBetween(start, end)
                onNodeWithText("复制").performClick()
                runOnIdle { assertEquals(listOf(expectedSelection), clipboard.copied) }
                assertTrue(requests.isEmpty(), "复制选区不应发送 HTTP")
                selectBetween(start, end)
                onNodeWithText("评论").performClick()
                onNodeWithText(INVALID_SELECTION_MESSAGE, useUnmergedTree = true).assertIsDisplayed()
                onNode(
                    hasAnyAncestor(isDialog()) and hasText(INVALID_SELECTION_MESSAGE),
                    useUnmergedTree = true,
                ).assertIsDisplayed()
                onNodeWithText("喜欢").assertDoesNotExist()
                onNodeWithTag(COMMENT_SCREEN_LIST_TAG).assertDoesNotExist()
                assertTrue(requests.isEmpty(), "无效选区不能发送 HTTP：$requests")
            }
        } finally {
            owner.viewModelStore.clear()
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

    @Test
    fun rightClickMenuFollowsPointerAndCloses() = runDesktopComposeUiTest(width = 1000, height = 600) {
        val source = "Alpha bravo charlie delta echo"
        val selectionEnd = source.indexOf("delta")
        val initialSelection = source.substring(0, selectionEnd)
        lateinit var selectionState: CjkSelectionState
        var textLayout: LayoutResult? = null
        var likedRange: SegmentCreationRange? = null
        var liked by mutableStateOf(false)
        var cancelledRange: SegmentCreationRange? = null
        setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize().testTag("screen")) {
                    Box(Modifier.offset(x = 170.dp, y = 160.dp)) {
                        val state = rememberCjkSelectionState()
                        selectionState = state
                        TiqianSelectionMenuHost(
                            selectionState = state,
                            selectionRange = {
                                state.selectedText?.text?.let { selected ->
                                    val start = source.indexOf(selected)
                                    if (start < 0) null else SegmentCreationRange("paragraph", start, start + selected.length, selected)
                                }
                            },
                            liked = { liked },
                            onLike = {
                                likedRange = it
                                liked = true
                            },
                            onComment = {},
                            onCancelLike = {
                                cancelledRange = it
                                liked = false
                            },
                        ) {
                            CjkSelectionContainer(state = state) {
                                CjkText(
                                    text = source,
                                    modifier = Modifier.width(420.dp).testTag("body"),
                                    fontFamily = FontFamily.Serif,
                                    fontSize = 24.sp,
                                    onTextLayout = { textLayout = it },
                                )
                            }
                        }
                    }
                }
            }
        }

        val body = onNodeWithTag("body")
        val bodyNode = body.fetchSemanticsNode()
        val bodyOrigin = bodyNode.positionInWindow
        assertTrue(bodyOrigin.x > 0f && bodyOrigin.y > 0f, "正文必须偏离窗口原点，以覆盖容器偏移")
        val layout = textLayout ?: error("CjkText 没有产生布局")
        val firstBox = layout.getBoundingBoxes(1, 2).single()
        val secondBox = layout.getBoundingBoxes(selectionEnd - 3, selectionEnd - 2).single()
        assertTrue(
            firstBox.width > 0f &&
                secondBox.width > 0f &&
                firstBox.height > 0f &&
                secondBox.height > 0f &&
                firstBox.left >= 0f &&
                secondBox.right <= bodyNode.size.width &&
                firstBox.top >= 0f &&
                secondBox.bottom <= bodyNode.size.height &&
                firstBox.right < secondBox.left,
            "两个选中字形必须有实际宽高并处于可命中范围：$firstBox, $secondBox",
        )
        val firstClick = Offset((firstBox.left + firstBox.right) / 2f, (firstBox.top + firstBox.bottom) / 2f)
        val secondClick = Offset((secondBox.left + secondBox.right) / 2f, (secondBox.top + secondBox.bottom) / 2f)

        // 桌面测试宿主的默认字形可能没有 advance；通过真实 CjkText 的无障碍动作建立可验证的选区。
        body.performSemanticsAction(SemanticsActions.SetSelection) { setSelection ->
            assertTrue(setSelection(0, selectionEnd, false), "CjkText 必须接受选区语义动作")
        }
        runOnIdle { assertEquals(initialSelection, selectionState.selectedText?.text, "必须先选中真实正文") }
        body.performMouseInput { rightClick(firstClick) }
        runOnIdle { assertEquals(initialSelection, selectionState.selectedText?.text, "右键选区内文字不能清除选区") }
        onNodeWithText("复制").assertExists()
        val popup = onNode(isPopup())
        popup.assertExists()
        val menu = onNodeWithText("复制").onParent()
        menu.assert(hasAnyDescendant(hasText("全选")))
        val popupNode = popup.fetchSemanticsNode()
        val menuNode = menu.fetchSemanticsNode()
        assertTrue(
            menuNode.size.width < popupNode.size.width && menuNode.size.height < popupNode.size.height,
            "必须比较实际菜单而不是铺满窗口的 Popup 语义根",
        )
        var menuPosition = menuNode.positionInWindow
        assertEquals(bodyOrigin.x + firstClick.x, menuPosition.x, 2f, "菜单应定位在第一次右键处")
        assertEquals(bodyOrigin.y + firstClick.y, menuPosition.y, 2f, "菜单应定位在正文所在行")

        onNodeWithTag("screen").performMouseInput { click(Offset(10f, 10f)) }
        popup.assertDoesNotExist()
        onNodeWithText("复制").assertDoesNotExist()

        body.performSemanticsAction(SemanticsActions.SetSelection) { setSelection ->
            assertTrue(setSelection(0, selectionEnd, false), "重新打开菜单前必须选择真实正文")
        }
        runOnIdle { assertEquals(initialSelection, selectionState.selectedText?.text, "重新打开菜单前必须重新选中文字") }
        body.performMouseInput { rightClick(secondClick) }
        runOnIdle { assertEquals(initialSelection, selectionState.selectedText?.text, "重新右键时必须保留选区") }
        popup.assertExists()
        menuPosition = onNodeWithText("复制").onParent().fetchSemanticsNode().positionInWindow
        assertEquals(bodyOrigin.x + secondClick.x, menuPosition.x, 2f, "再次右键时菜单应跟随新的位置")
        assertEquals(bodyOrigin.y + secondClick.y, menuPosition.y, 2f, "再次打开时菜单应仍位于正文所在行")

        onNodeWithText("全选").performClick()
        runOnIdle { assertEquals(source, selectionState.selectedText?.text) }
        onNodeWithText("喜欢").performClick()
        runOnIdle { assertEquals(SegmentCreationRange("paragraph", 0, source.length, source), likedRange) }
        popup.assertDoesNotExist()
        onNodeWithText("喜欢").assertDoesNotExist()
        body.performMouseInput { rightClick(firstClick) }
        onNodeWithText("取消喜欢").assertExists()
        onNodeWithText("喜欢").assertDoesNotExist()
        onNodeWithText("取消喜欢").performClick()
        runOnIdle { assertEquals(SegmentCreationRange("paragraph", 0, source.length, source), cancelledRange) }
        popup.assertDoesNotExist()
    }

    @Test
    fun imageLongPressOpensImageActionsWithoutStartingTextSelection() {
        val image = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGP4z8DwHwAFAAH/iZk9HQAAAABJRU5ErkJggg=="
        val html = """<p>图片前文</p><img src="$image" alt="菜单图片" width="120" height="80"><p>图片后文</p>""" +
            "<p>滚动后的正文</p>".repeat(30)
        for (tiqian in listOf(false)) {
            val owner = ScreenOwner()
            try {
                runDesktopComposeUiTest(width = 720, height = 800) {
                    setContent {
                        TestArticle(html, owner, RecordingClipboard(), useTiqian = tiqian, scrollState = rememberScrollState())
                    }
                    val node = onNodeWithContentDescription("菜单图片", useUnmergedTree = true)
                    waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("图片后文")).fetchSemanticsNodes().isNotEmpty() }
                    val imageNode = node.fetchSemanticsNode()
                    val originalImagePosition = imageNode.positionInWindow
                    val point = originalImagePosition + Offset(imageNode.size.width / 2f, imageNode.size.height / 2f)
                    val gapPx = with(density) { AppDimens.Space8.toPx() }
                    assertTrue(imageNode.positionInWindow.x > 0f && imageNode.positionInWindow.y > 0f, "图片必须偏离窗口原点")
                    node.performTouchInput {
                        down(center)
                        advanceEventTime(1_000L)
                        up()
                    }
                    onNodeWithText("保存图片").assertExists()
                    onNodeWithText("分享图片").assertExists()
                    onNodeWithText("复制").assertDoesNotExist()
                    onNodeWithText("全选").assertDoesNotExist()
                    onNodeWithText("喜欢").assertDoesNotExist()
                    val menu = onNodeWithTag("image_context_menu", useUnmergedTree = true).fetchSemanticsNode()
                    assertTrue(menu.size.width in 1 until 720 && menu.size.height in 1 until 800, "必须断言实际菜单而非 Popup 根")
                    assertEquals(point.x, menu.positionInWindow.x, 2f, "菜单应在图片窗口坐标附近：tiqian=$tiqian")
                    assertEquals(point.y + gapPx, menu.positionInWindow.y, 2f, "菜单不能叠加图片底部或正文容器偏移")

                    onNodeWithText("图片前文").performTouchInput { click(center) }
                    onNodeWithTag("image_context_menu", useUnmergedTree = true).assertDoesNotExist()
                    val scrollPx = with(density) { 20.dp.roundToPx() }
                    onNode(
                        SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollBy) and hasAnyDescendant(hasContentDescription("菜单图片")),
                        useUnmergedTree = true,
                    ).performSemanticsAction(SemanticsActions.ScrollBy) { scrollBy ->
                        assertTrue(scrollBy(0f, scrollPx.toFloat()), "图片所在的真实滚动宿主必须接受滚动")
                    }
                    waitForIdle()
                    val movedImage = node.fetchSemanticsNode()
                    assertEquals(originalImagePosition.y - scrollPx, movedImage.positionInWindow.y, 2f, "夹具必须真正滚动图片")
                    onNode(
                        SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick) and
                            (hasContentDescription("菜单图片") or hasAnyDescendant(hasContentDescription("菜单图片"))),
                        useUnmergedTree = true,
                    ).performSemanticsAction(SemanticsActions.OnLongClick) { assertTrue(it()) }
                    val fallbackMenu = onNodeWithTag("image_context_menu", useUnmergedTree = true).fetchSemanticsNode()
                    assertEquals(movedImage.positionInWindow.x + movedImage.size.width / 2f, fallbackMenu.positionInWindow.x, 2f, "无指针时仍应锚在当前图片")
                    assertEquals(movedImage.positionInWindow.y + movedImage.size.height / 2f + gapPx, fallbackMenu.positionInWindow.y, 2f, "语义长按不能复用滚动前的按压点")
                    assertTrue(requests.isEmpty(), "长按图片不能发送段评请求")
                }
            } finally {
                owner.viewModelStore.clear()
            }
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
                LocalViewModelStoreOwner provides owner,
                dispatcherValue,
                LocalNavigator provides Navigator({ error("Unexpected navigation: $it") }, { error("Unexpected back") }),
                LocalClipboard provides clipboard,
                LocalSegmentSelectionTarget provides SegmentSelectionTarget("9001", "answer"),
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
        private const val INVALID_SELECTION_MESSAGE = "只能选择同一段内的纯文本划线"
        private val requests = CopyOnWriteArrayList<HttpRequestData>()
        private lateinit var responseGate: CompletableDeferred<Unit>

        @Volatile
        private var creationStatus = HttpStatusCode.Created
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
                            responseGate.await()
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
                            request.url.encodedPath == "/api/v4/reaction/answers/9001/segment_reaction" -> respond(
                            "",
                            HttpStatusCode.NoContent,
                        )
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
