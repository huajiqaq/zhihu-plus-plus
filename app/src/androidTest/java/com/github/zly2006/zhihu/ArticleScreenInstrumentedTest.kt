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

package com.github.zly2006.zhihu

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.NativeClipboard
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.zly2006.zhihu.markdown.RenderMarkdownText
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.navigation.Video
import com.github.zly2006.zhihu.reading.AndroidReadingPlayerBridge
import com.github.zly2006.zhihu.reading.ContentReadingService
import com.github.zly2006.zhihu.reading.ReadingPlayerState
import com.github.zly2006.zhihu.test.MainActivityComposeRule
import com.github.zly2006.zhihu.test.RecordingNavigator
import com.github.zly2006.zhihu.test.resetAppPreferences
import com.github.zly2006.zhihu.test.setScreenContent
import com.github.zly2006.zhihu.test.setZhihuMainContent
import com.github.zly2006.zhihu.theme.ThemeManager
import com.github.zly2006.zhihu.ui.ARTICLE_USE_WEBVIEW_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.AnswerDoubleTapAction
import com.github.zly2006.zhihu.ui.ArticleScreen
import com.github.zly2006.zhihu.ui.PREFERENCE_NAME
import com.github.zly2006.zhihu.ui.components.CustomWebView
import com.github.zly2006.zhihu.ui.components.LocalPageTurnDispatcher
import com.github.zly2006.zhihu.ui.components.PageTurnCommand
import com.github.zly2006.zhihu.ui.components.PageTurnDispatcher
import com.github.zly2006.zhihu.ui.components.PageTurnFab
import com.github.zly2006.zhihu.ui.components.WebviewComp
import com.github.zly2006.zhihu.ui.subscreens.PREF_SHOW_PAGE_TURN_FAB
import com.github.zly2006.zhihu.viewmodel.ArticleViewModel
import com.github.zly2006.zhihu.viewmodel.sharedArticleAnswerSwitchState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.ceil

// ArticleScreen 自带 AppScaffold 并自己读取系统栏留白，应用里也是不套外层直接放的；
// 用例外层的 Scaffold 只提供全屏容器，再把它的内边距传进去会重复留白。
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@RunWith(AndroidJUnit4::class)
class ArticleScreenInstrumentedTest {
    @get:Rule
    val composeRule: MainActivityComposeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        AndroidReadingPlayerBridge.publish(ReadingPlayerState())
        sharedArticleAnswerSwitchState.reset()
        composeRule.resetAppPreferences()
        composeRule.activity
            .getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("titleAutoHide", true)
            .putBoolean("autoHideArticleBottomBar", true)
            .putBoolean("buttonSkipAnswer", true)
            .putBoolean("autoHideSkipAnswerButton", true)
            .putBoolean("pinAnswerDate", true)
            .putBoolean(ARTICLE_USE_WEBVIEW_PREFERENCE_KEY, false)
            .putString("answerDoubleTapAction", AnswerDoubleTapAction.Ask.preferenceValue)
            .commit()
    }

    @After
    fun tearDown() {
        composeRule.activity.stopService(Intent(composeRule.activity, ContentReadingService::class.java))
        AndroidReadingPlayerBridge.publish(ReadingPlayerState())
        composeRule.runOnIdle {
            sharedArticleAnswerSwitchState.navigator = null
            sharedArticleAnswerSwitchState.pendingNavigator = null
        }
    }

    @Test
    fun webLoginRequirementReturnsToMainLogin() {
        composeRule.resetAppPreferences(forceMock = true)
        composeRule.setZhihuMainContent()
        val main = composeRule.activity
        val client = com.github.zly2006.zhihu.account
            .androidZhihuAccountStore(main)
            .client
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = instrumentation.addMonitor(WebviewActivity::class.java.name, null, false)
        var browser: android.app.Activity? = null
        try {
            composeRule.runOnIdle {
                main.startActivity(Intent(main, WebviewActivity::class.java).setData(android.net.Uri.parse("about:blank")))
            }
            val openedBrowser = checkNotNull(monitor.waitForActivityWithTimeout(5_000))
            browser = openedBrowser
            composeRule.waitUntil(5_000) { openedBrowser.hasWindowFocus() }
            instrumentation.runOnMainSync { client.reportLoginRequired() }

            fun loginButton(node: android.view.accessibility.AccessibilityNodeInfo?): android.view.accessibility.AccessibilityNodeInfo? {
                if (node == null) return null
                if (node.text?.toString() == "去登录") {
                    return generateSequence(node) { it.parent }.firstOrNull { it.isClickable }
                }
                return (0 until node.childCount).firstNotNullOfOrNull { loginButton(node.getChild(it)) }
            }
            composeRule.waitUntil(5_000) { loginButton(instrumentation.uiAutomation.rootInActiveWindow) != null }
            assertTrue(checkNotNull(loginButton(instrumentation.uiAutomation.rootInActiveWindow)).performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
            composeRule.waitUntil(5_000) { openedBrowser.isDestroyed && main.hasWindowFocus() }
            assertTrue("必须回到原主 Activity，不能另开一份主栈", main === composeRule.activity)
            assertEquals(com.github.zly2006.zhihu.navigation.Login, main.navHost.top)
            composeRule.onNodeWithTag("login_notice_step_1").assertExists()
        } finally {
            instrumentation.runOnMainSync { browser?.takeUnless { it.isDestroyed }?.finish() }
            instrumentation.removeMonitor(monitor)
        }
    }

    /** 同一个真实 WebView 重组后，链接和视频 JS 桥必须只交给最新导航宿主。 */
    @Test
    fun webViewLinksAndVideoBridgeUseLatestNavigatorAfterRecomposition() {
        composeRule.resetAppPreferences(forceMock = true)
        val originalNavigator = RecordingNavigator()
        val replacementNavigator = RecordingNavigator()
        val navigator = mutableStateOf(originalNavigator.asNavigator())
        var webView: CustomWebView? = null
        composeRule.setScreenContent {
            CompositionLocalProvider(LocalNavigator provides navigator.value) {
                WebviewComp(modifier = Modifier.fillMaxSize(), onLoad = { webView = it })
            }
        }
        val originalWebView = composeRule.runOnIdle {
            checkNotNull(webView).also {
                it.loadDataWithBaseURL(
                    "https://www.zhihu.com/",
                    """
                    <html><head><title>navigation-callback-regression</title></head><body>
                    <img id="first-image" src="data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///ywAAAAAAQABAAACAUwAOw=="
                         data-original="android.resource://android/${android.R.drawable.ic_menu_gallery}" alt="离线首图">
                    <a id="question" href="https://www.zhihu.com/question/42">问题链接</a>
                    <a id="video" class="video-box" data-lens-id="43"><span>视频</span></a>
                    </body></html>
                    """.trimIndent(),
                    "text/html",
                    "UTF-8",
                    null,
                )
            }
        }
        composeRule.waitUntil("WebView 内存页面必须完成加载", timeoutMillis = 5_000) {
            composeRule.runOnIdle {
                originalWebView.title == "navigation-callback-regression" && originalWebView.progress == 100
            }
        }
        composeRule.runOnIdle {
            originalWebView.evaluateJavascript("document.getElementById('question').click();", null)
        }
        composeRule.waitUntil("链接必须经过真实 WebViewClient 导航", timeoutMillis = 5_000) {
            originalNavigator.destinations.size == 1
        }
        assertEquals(listOf(Question(42)), originalNavigator.destinations)
        composeRule.runOnIdle { navigator.value = replacementNavigator.asNavigator() }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertTrue("更换导航宿主不能重建 WebView", originalWebView === webView)
            originalWebView.evaluateJavascript("document.getElementById('question').click();", null)
        }
        composeRule.waitUntil("重组后的链接必须交给最新导航宿主", timeoutMillis = 5_000) {
            replacementNavigator.destinations.size == 1
        }
        val readingRoots = composeRule.onAllNodes(isRoot()).fetchSemanticsNodes().size
        composeRule.runOnIdle {
            originalWebView.evaluateJavascript("document.body.click(); document.querySelector('#video span').click();", null)
        }
        composeRule.waitUntil("视频 JS 桥必须交给最新导航宿主", timeoutMillis = 5_000) {
            Video(43) in replacementNavigator.destinations
        }
        assertEquals(listOf(Question(42), Video(43)), replacementNavigator.destinations)
        assertEquals("旧导航宿主不能再收到链接或视频事件", listOf(Question(42)), originalNavigator.destinations)
        assertEquals("点正文空白不能打开首图", readingRoots, composeRule.onAllNodes(isRoot()).fetchSemanticsNodes().size)
        composeRule.runOnIdle { originalWebView.evaluateJavascript("document.getElementById('first-image').click();", null) }
        composeRule.waitUntil("点中图片仍可打开查看器", timeoutMillis = 5_000) {
            composeRule.onAllNodes(isRoot()).fetchSemanticsNodes().size == readingRoots + 1
        }
    }

    /**
     * 标题隐藏动画中反转滚动方向后，主线程与菜单仍须响应。
     */
    @Test
    fun autoHideTitleRemainsResponsiveWhenDirectionChangesDuringHideAnimation() {
        setArticleScreen()

        val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        fun injectSwipe(
            startYFraction: Float,
            endYFraction: Float,
            durationMillis: Long,
        ) {
            val downTime = SystemClock.uptimeMillis()
            val x = rootBounds.center.x
            val startY = rootBounds.height * startYFraction
            val endY = rootBounds.height * endYFraction
            val steps = maxOf((durationMillis / 15).toInt(), 1)

            for (step in 0..steps) {
                val eventTime = SystemClock.uptimeMillis()
                val action = when (step) {
                    0 -> MotionEvent.ACTION_DOWN
                    steps -> MotionEvent.ACTION_UP
                    else -> MotionEvent.ACTION_MOVE
                }
                val fraction = step.toFloat() / steps
                val event = MotionEvent.obtain(
                    downTime,
                    eventTime,
                    action,
                    x,
                    startY + (endY - startY) * fraction,
                    0,
                )
                event.source = InputDevice.SOURCE_TOUCHSCREEN
                try {
                    assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true))
                } finally {
                    event.recycle()
                }
                if (step < steps) SystemClock.sleep(durationMillis / steps)
            }
        }

        injectSwipe(0.64f, 0.49f, 600)
        repeat(4) {
            injectSwipe(0.57f, 0.51f, 90)
            injectSwipe(0.51f, 0.58f, 90)
        }

        val mainThreadResponsive = CountDownLatch(1)
        composeRule.activity.runOnUiThread { mainThreadResponsive.countDown() }
        assertTrue(
            "标题隐藏动画中反转滚动方向后，主线程停止响应",
            mainThreadResponsive.await(3, TimeUnit.SECONDS),
        )

        injectSwipe(0.3f, 0.8f, 240)
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("更多选项").assertIsDisplayed().performClick()
        val copyLink = composeRule.onNodeWithText("复制链接")
        composeRule.waitUntil("等待文章操作菜单展开", 5_000) { composeRule.onAllNodesWithText("复制链接").fetchSemanticsNodes().size == 1 }
        copyLink.performScrollTo().assertIsDisplayed()
    }

    /**
     * 首次向下翻页仅收起文章标题，不滚动正文；第二次才滚动正文。
     */
    @Test
    fun pageTurnControlsScrollTheArticle() {
        composeRule.activity
            .getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_SHOW_PAGE_TURN_FAB, true)
            .commit()
        val dispatcher = PageTurnDispatcher()
        setArticleScreen(dispatcher)

        composeRule.onNodeWithContentDescription("下翻页").assertIsDisplayed()
        val scrollContainer = composeRule.onNode(
            SemanticsMatcher("具有垂直滚动轴") { node ->
                node.config.contains(SemanticsProperties.VerticalScrollAxisRange)
            },
        )
        val initialValue = scrollContainer
            .fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange]
            .value()
        val title = composeRule.onNodeWithText("离线 Article 标题")
        val toolbarActionBottom = composeRule
            .onNodeWithContentDescription("更多选项")
            .fetchSemanticsNode()
            .boundsInRoot
            .bottom
        assertTrue(title.fetchSemanticsNode().boundsInRoot.bottom > toolbarActionBottom)

        assertTrue(dispatcher.dispatch(PageTurnCommand.PageDown))
        composeRule.waitUntil(5_000) {
            title.fetchSemanticsNode().boundsInRoot.bottom <= toolbarActionBottom
        }
        composeRule.waitForIdle()
        assertEquals(
            initialValue.toDouble(),
            scrollContainer
                .fetchSemanticsNode()
                .config[SemanticsProperties.VerticalScrollAxisRange]
                .value()
                .toDouble(),
            0.5,
        )

        val rangeBeforeSecondPage = scrollContainer.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue(
            "翻页夹具必须具有正文行程：${rangeBeforeSecondPage.value()}/${rangeBeforeSecondPage.maxValue()}，风格=${ThemeManager.currentStyle}",
            rangeBeforeSecondPage.maxValue() > initialValue,
        )
        assertTrue(dispatcher.dispatch(PageTurnCommand.PageDown))
        try {
            composeRule.waitUntil(5_000) {
                scrollContainer
                    .fetchSemanticsNode()
                    .config[SemanticsProperties.VerticalScrollAxisRange]
                    .value() > initialValue
            }
        } catch (error: androidx.compose.ui.test.ComposeTimeoutException) {
            val range = scrollContainer.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
            throw AssertionError("第二次翻页未滚动：${range.value()}/${range.maxValue()}，风格=${ThemeManager.currentStyle}，标题=${title.fetchSemanticsNode().boundsInRoot}，按钮底=$toolbarActionBottom", error)
        }
    }

    private data class MarkdownTextFragment(
        val nodeId: Int,
        val bounds: Rect,
        val layout: TextLayoutResult,
        val startOffset: Int,
    ) {
        val endOffset: Int get() = startOffset + layout.layoutInput.text.length
    }

    /** HRM 按视觉行/run 绘制；完整原段只用于校验和 UTF-16 偏移，不另测文字布局。 */
    private fun paragraphFragments(tag: String, text: String): List<MarkdownTextFragment> {
        val fragments = composeRule
            .onAllNodes(
                SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult) and
                    (hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag))),
                useUnmergedTree = true,
            ).fetchSemanticsNodes()
            .mapNotNull { node ->
                val results = mutableListOf<TextLayoutResult>()
                val hasLayout = composeRule.runOnIdle {
                    val provided = node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results) == true
                    // 已组合但未放置的节点（如惰性列表预取的条目）没有文字布局，也不会绘制。
                    assertTrue("已放置的文字节点 ${node.id} 须有布局结果", provided || !node.layoutInfo.isPlaced)
                    provided
                }
                if (!hasLayout) return@mapNotNull null
                val layout = results.single()
                if (layout.layoutInput.text.isEmpty()) {
                    null
                } else {
                    MarkdownTextFragment(
                        node.id,
                        Rect(node.positionInRoot, node.size.toSize()),
                        layout,
                        0,
                    )
                }
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
        assertEquals(
            "完整原段必须恰好对应一组已绘制的文字片段：$text；" +
                fragments.joinToString {
                    val runText = it.layout.layoutInput.text.text
                    "${it.nodeId}: text=$runText utf16=${runText.map { char -> char.code.toString(16).padStart(4, '0') }} " +
                        "bounds=${it.bounds} baselineInRoot=${it.bounds.top + it.layout.getLineBaseline(0)} size=${it.layout.size}"
                },
            1,
            matches.size,
        )
        return matches.single()
    }

    private fun textPoint(tag: String, text: String, offset: Int): Offset {
        require(text.isNotEmpty() && offset in 0..text.length)
        require(!(offset in text.indices && text[offset].isLowSurrogate() && offset > 0 && text[offset - 1].isHighSurrogate()))
        val fragments = paragraphFragments(tag, text)
        val fragment = if (offset == text.length) fragments.last() else fragments.single { offset in it.startOffset until it.endOffset }
        val node = composeRule
            .onNode(SemanticsMatcher("已绘制的文字片段 ${fragment.nodeId}") { it.id == fragment.nodeId }, useUnmergedTree = true)
            .assertIsDisplayed()
            .fetchSemanticsNode()
        val localOffset = offset - fragment.startOffset
        val point = if (localOffset == fragment.layout.layoutInput.text.length) {
            val box = fragment.layout.getBoundingBox(localOffset - 1)
            Offset(box.right, box.center.y)
        } else {
            val box = fragment.layout.getBoundingBox(localOffset)
            Offset(box.left, box.center.y)
        }
        val rootPoint = node.positionInRoot + point
        var visibleBounds = node.boundsInRoot
        var parent = node.parent
        while (parent != null) {
            visibleBounds = visibleBounds.intersect(parent.boundsInRoot)
            parent = parent.parent
        }
        assertTrue(
            "UTF-16 偏移 $offset 的实际文字坐标不在可点击视口内：$rootPoint；$visibleBounds",
            visibleBounds.width > 0f &&
                visibleBounds.height > 0f &&
                rootPoint.x in visibleBounds.left..visibleBounds.right &&
                rootPoint.y in visibleBounds.top..visibleBounds.bottom,
        )
        return rootPoint
    }

    /** HRM 的逻辑手柄位于 run 的 caret 横坐标和行底；末端在换行边界取前一个 run。 */
    private fun selectionHandleAnchor(tag: String, text: String, offset: Int, isStart: Boolean = false): Offset {
        val fragment = paragraphFragments(tag, text).single {
            if (isStart) offset in it.startOffset until it.endOffset else offset > it.startOffset && offset <= it.endOffset
        }
        return Offset(
            fragment.bounds.left + fragment.layout.getHorizontalPosition(offset - fragment.startOffset, usePrimaryDirection = true),
            fragment.bounds.bottom,
        )
    }

    /** DOWN 命中原生圆柄；HRM 从正文逻辑锚点累加位移，不能用移动 Popup 的中心代替它。 */
    private fun dragSelectionHandleTo(description: String, anchorInRoot: Offset, destinationInRoot: Offset) {
        val handle = composeRule.onNodeWithContentDescription(description).assertIsDisplayed().fetchSemanticsNode()
        val downPoint = composeRule.runOnUiThread {
            val view = (handle.root as ViewRootForTest).view
            val origin = IntArray(2).also(view::getLocationOnScreen)
            handle.positionInRoot + Offset(origin[0] + handle.size.width / 2f, origin[1] + handle.size.height * 3f / 4f)
        }
        val deltaInRoot = destinationInRoot - anchorInRoot
        val input = InstrumentationRegistry.getInstrumentation()
        val pointer = MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_FINGER
        }
        val downTime = SystemClock.uptimeMillis()

        fun touch(action: Int, point: Offset) {
            val coordinates = MotionEvent.PointerCoords().apply {
                x = point.x
                y = point.y
                pressure = if (action == MotionEvent.ACTION_UP) 0f else 1f
                size = 1f
            }
            val event = MotionEvent.obtain(
                downTime,
                SystemClock.uptimeMillis(),
                action,
                1,
                arrayOf(pointer),
                arrayOf(coordinates),
                0,
                0,
                1f,
                1f,
                0,
                0,
                InputDevice.SOURCE_TOUCHSCREEN,
                0,
            )
            try {
                assertTrue("系统触摸事件必须成功注入", input.uiAutomation.injectInputEvent(event, true))
            } finally {
                event.recycle()
            }
            composeRule.waitForIdle()
        }
        touch(MotionEvent.ACTION_DOWN, downPoint)
        repeat(10) { step ->
            touch(MotionEvent.ACTION_MOVE, downPoint + deltaInRoot * ((step + 1) / 10f))
        }
        touch(MotionEvent.ACTION_UP, downPoint + deltaInRoot)
    }

    /** HRM 使用单一连续区间；检查两端及相邻未选字形，避免把初始高亮误当作完整选词/划线。 */
    private fun waitUntilHandleSelection(tag: String, text: String, range: TextRange, selectionColor: Color) {
        composeRule.waitUntil("正文 $range 的两端及双手柄须就绪", timeoutMillis = 5_000) {
            runCatching {
                val body = composeRule.onNodeWithTag(tag)
                val bounds = body.fetchSemanticsNode().boundsInRoot
                val pixels = body.captureToImage().toPixelMap()
                val fragments = paragraphFragments(tag, text)
                listOf(range.min - 1, range.min, range.max - 1, range.max).filter { it in text.indices }.all { offset ->
                    val fragment = fragments.single { offset in it.startOffset until it.endOffset }
                    val box = fragment.layout.getBoundingBox(offset - fragment.startOffset).translate(fragment.bounds.topLeft - bounds.topLeft)
                    val x = box.center.x
                        .toInt()
                        .coerceIn(0, pixels.width - 1)
                    val selected = (ceil(box.top).toInt().coerceIn(0, pixels.height) until box.bottom.toInt().coerceIn(0, pixels.height)).any { y ->
                        pixels[x, y].matchesPixel(selectionColor)
                    }
                    selected == (offset in range.min until range.max)
                } &&
                    composeRule.onNodeWithText("复制").isDisplayed() &&
                    composeRule.onNodeWithContentDescription("Markdown selection start handle").isDisplayed() &&
                    composeRule.onNodeWithContentDescription("Markdown selection end handle").isDisplayed()
            }.getOrDefault(false)
        }
    }

    private fun copySelection(clipboard: RecordingClipboard): String {
        clipboard.clear()
        composeRule.onNodeWithText("复制").assertIsDisplayed().performTouchInput { click(center) }
        composeRule.waitUntil("选区未将文字写入 Compose 剪贴板", timeoutMillis = 5_000) {
            clipboard.text != null
        }
        return requireNotNull(clipboard.text)
    }

    private fun Color.matchesPixel(expected: Color): Boolean =
        abs(red - expected.red) < 0.04f && abs(green - expected.green) < 0.04f && abs(blue - expected.blue) < 0.04f

    /**
     * 收起后重新长按可多次拖动同一手柄，跨段高亮和复制都随实际选区变化。
     */
    @Test
    fun draggingSelectionHandleUsesSameCompleteTextLayer() {
        val clipboard = RecordingClipboard()
        val scheme = lightColorScheme()
        val selectionColor = scheme.primary.copy(alpha = 0.3f).compositeOver(scheme.surface)
        val paragraphs = listOf(
            "起始 TARGET 从这里开始拖动。",
            "中间段落确保选区跨越多个文字块。",
            "末段拖动必须到达这里。",
        )
        val tag = "selection-handle-article"
        composeRule.setScreenContent {
            MaterialTheme(colorScheme = scheme) {
                CompositionLocalProvider(LocalClipboard provides clipboard) {
                    RenderMarkdownText(
                        markdown = paragraphs.joinToString("\n\n"),
                        modifier = Modifier
                            .safeDrawingPadding()
                            .padding(start = 24.dp, top = 96.dp)
                            .width(280.dp)
                            .background(scheme.surface)
                            .testTag(tag),
                        enableScroll = false,
                    )
                }
            }
        }
        val pressOffset = paragraphs.first().indexOf("TARGET") + 2
        val fragment = paragraphFragments(tag, paragraphs.first()).single { pressOffset in it.startOffset until it.endOffset }
        val wordInRun = fragment.layout.getWordBoundary(pressOffset - fragment.startOffset)
        val word = TextRange(fragment.startOffset + wordInRun.start, fragment.startOffset + wordInRun.end)
        assertEquals("实际绘制的词边界必须选中 TARGET", "TARGET", paragraphs.first().substring(word.start, word.end))
        val press = textPoint(tag, paragraphs.first(), pressOffset)
        val endHandle = "Markdown selection end handle"
        composeRule.onRoot().performTouchInput { longClick(press) }
        waitUntilHandleSelection(tag, paragraphs.first(), word, selectionColor)
        assertTrue(
            "系统必须接受返回操作",
            InstrumentationRegistry
                .getInstrumentation()
                .uiAutomation
                .performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK),
        )
        composeRule.waitUntil("收起选择须移除双手柄和菜单", timeoutMillis = 5_000) {
            composeRule
                .onAllNodes(
                    hasContentDescription("Markdown selection start handle") or hasContentDescription(endHandle) or hasText("复制"),
                ).fetchSemanticsNodes()
                .isEmpty()
        }
        composeRule.onNodeWithContentDescription(endHandle).assertDoesNotExist()

        composeRule.onRoot().performTouchInput { longClick(press) }
        waitUntilHandleSelection(tag, paragraphs.first(), word, selectionColor)
        dragSelectionHandleTo(
            "Markdown selection start handle",
            selectionHandleAnchor(tag, paragraphs.first(), word.start, isStart = true),
            textPoint(tag, paragraphs.first(), 0),
        )
        waitUntilHandleSelection(tag, paragraphs.first(), TextRange(0, word.end), selectionColor)
        dragSelectionHandleTo(
            endHandle,
            selectionHandleAnchor(tag, paragraphs.first(), word.end),
            textPoint(tag, paragraphs[1], paragraphs[1].length),
        )
        waitUntilHandleSelection(tag, paragraphs[1], TextRange(0, paragraphs[1].length), selectionColor)
        dragSelectionHandleTo(
            endHandle,
            selectionHandleAnchor(tag, paragraphs[1], paragraphs[1].length),
            textPoint(tag, paragraphs.last(), paragraphs.last().length),
        )
        val container = composeRule.onNodeWithTag(tag)
        val bounds = container.fetchSemanticsNode().boundsInRoot
        val pixels = container.captureToImage().toPixelMap()
        for (paragraph in paragraphs) {
            for (fragment in paragraphFragments(tag, paragraph)) {
                val top = (fragment.bounds.top - bounds.top).toInt().coerceAtLeast(0)
                val bottom = (fragment.bounds.bottom - bounds.top).toInt().coerceAtMost(pixels.height)
                val selectedPixels = (top until bottom).sumOf { y ->
                    (0 until pixels.width).count { x -> pixels[x, y].matchesPixel(selectionColor) }
                }
                assertTrue("拖动必须高亮经过的每个文字块和视觉行：$paragraph", selectedPixels >= 100)
            }
        }
        assertEquals(paragraphs.joinToString("\n"), copySelection(clipboard))
    }

    private fun setArticleScreen(pageTurnDispatcher: PageTurnDispatcher? = null) {
        val viewModel = ArticleViewModel(
            article = ARTICLE,
            httpClient = null,
        )
        composeRule.activity.runOnUiThread {
            viewModel.title = "离线 Article 标题"
            viewModel.authorName = "离线作者"
            viewModel.authorId = "offline-author-id"
            viewModel.authorUrlToken = "offline-author"
            viewModel.content = (1..80).joinToString("\n\n") { index ->
                "第 $index 段离线正文，用于 ArticleScreen instrumented test。"
            }
            viewModel.voteUpCount = 42
            viewModel.commentCount = 7
            viewModel.questionId = 123456L
            viewModel.createdAt = 1_710_000_000L
            viewModel.updatedAt = 1_710_000_600L
            viewModel.ipInfo = "上海"
        }
        composeRule.setScreenContent {
            Scaffold(
                modifier = androidx.compose.ui.Modifier
                    .fillMaxSize(),
            ) { _ ->
                if (pageTurnDispatcher == null) {
                    ArticleScreen(
                        article = ARTICLE,
                        viewModel = viewModel,
                    )
                } else {
                    CompositionLocalProvider(LocalPageTurnDispatcher provides pageTurnDispatcher) {
                        Box(Modifier.fillMaxSize()) {
                            ArticleScreen(
                                article = ARTICLE,
                                viewModel = viewModel,
                            )
                            PageTurnFab(dispatcher = pageTurnDispatcher)
                        }
                    }
                }
            }
        }
    }

    private companion object {
        val ARTICLE = Article(
            type = ArticleType.Article,
            id = 777L,
            title = "离线 Article 标题",
        )
    }
}

private class RecordingClipboard : Clipboard {
    private var clipEntry: ClipEntry? = null

    var text: String? = null
        private set

    override suspend fun getClipEntry(): ClipEntry? = clipEntry

    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        this.clipEntry = clipEntry
        text = clipEntry
            ?.clipData
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.text
            ?.toString()
    }

    override val nativeClipboard: NativeClipboard
        get() = error("RecordingClipboard 不提供原生剪贴板")

    fun clear() {
        clipEntry = null
        text = null
    }
}
