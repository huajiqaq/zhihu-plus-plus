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
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.NativeClipboard
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
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
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.markdown.RenderImage
import com.github.zly2006.zhihu.markdown.RenderMarkdown
import com.github.zly2006.zhihu.markdown.RenderMarkdownText
import com.github.zly2006.zhihu.navigation.AnswerNavigator
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.navigation.Video
import com.github.zly2006.zhihu.reading.AndroidReadingPlayerBridge
import com.github.zly2006.zhihu.reading.ContentReadingService
import com.github.zly2006.zhihu.reading.ReadingContentType
import com.github.zly2006.zhihu.reading.ReadingPlaybackStatus
import com.github.zly2006.zhihu.reading.ReadingPlayerState
import com.github.zly2006.zhihu.reading.ReadingQueueItem
import com.github.zly2006.zhihu.reading.ReadingQueueSourceRegistry
import com.github.zly2006.zhihu.test.MainActivityComposeRule
import com.github.zly2006.zhihu.test.RecordingNavigator
import com.github.zly2006.zhihu.test.resetAppPreferences
import com.github.zly2006.zhihu.test.setScreenContent
import com.github.zly2006.zhihu.test.setZhihuMainContent
import com.github.zly2006.zhihu.ui.ARTICLE_FULL_TITLE_TAG
import com.github.zly2006.zhihu.ui.ARTICLE_IP_INFO_TAG
import com.github.zly2006.zhihu.ui.ARTICLE_USE_WEBVIEW_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.AnswerDoubleTapAction
import com.github.zly2006.zhihu.ui.ArticleScreen
import com.github.zly2006.zhihu.ui.COMMENT_COMPOSER_ENTRY_TAG
import com.github.zly2006.zhihu.ui.PREFERENCE_NAME
import com.github.zly2006.zhihu.ui.components.CustomWebView
import com.github.zly2006.zhihu.ui.components.LocalPageTurnDispatcher
import com.github.zly2006.zhihu.ui.components.PageTurnCommand
import com.github.zly2006.zhihu.ui.components.PageTurnDispatcher
import com.github.zly2006.zhihu.ui.components.PageTurnFab
import com.github.zly2006.zhihu.ui.components.WebviewComp
import com.github.zly2006.zhihu.ui.subscreens.PREF_SHOW_PAGE_TURN_FAB
import com.github.zly2006.zhihu.viewmodel.ArticleViewModel
import com.github.zly2006.zhihu.viewmodel.ZhihuApiEnvironment
import com.github.zly2006.zhihu.viewmodel.sharedArticleAnswerSwitchState
import com.hrm.markdown.renderer.MarkdownImageData
import io.ktor.client.HttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

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
        ReadingQueueSourceRegistry.register(FULL_ORIGIN_SOURCE_ID, emptyList())
        ReadingQueueSourceRegistry.register(PARTIAL_ORIGIN_SOURCE_ID, emptyList())
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

    @Test
    fun answerHeaderShowsIpBeforeBodyAndTitleNavigatesToItsQuestion() {
        val preferences = composeRule.activity.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)
        preferences
            .edit()
            .putBoolean("titleAutoHide", false)
            .putBoolean("pinAnswerDate", false)
            .commit()
        val viewModel = seededAnswerViewModel(ANSWER)
        composeRule.activity.runOnUiThread {
            viewModel.content = "<p>回答正文开始</p>" + (1..20).joinToString("") { "<p>用于保持真实滚动范围的段落 $it。</p>" }
            viewModel.endorsements = emptyList()
        }
        val navigator = composeRule.setScreenContent {
            ArticleScreen(article = ANSWER, viewModel = viewModel)
        }
        composeRule.onNodeWithTag(ARTICLE_IP_INFO_TAG).assertIsDisplayed()
        val ip = composeRule.onNodeWithTag(ARTICLE_IP_INFO_TAG).getUnclippedBoundsInRoot()
        val body = composeRule.onNodeWithText("回答正文开始").getUnclippedBoundsInRoot()
        assertTrue("IP 属地应位于回答正文之前的头部", ip.bottom <= body.top)
        assertEquals(1, composeRule.onAllNodesWithText("IP属地：上海").fetchSemanticsNodes().size)
        val beforeTitle = navigator.destinations.size
        composeRule.onNodeWithTag(ARTICLE_FULL_TITLE_TAG).performClick()
        composeRule.waitForIdle()
        assertEquals(listOf(Question(123456L, "离线 Answer 标题")), navigator.destinations.drop(beforeTitle))
        composeRule.activity.runOnUiThread { viewModel.questionId = 0L }
        val beforeInvalid = navigator.destinations.size
        composeRule.onNodeWithTag(ARTICLE_FULL_TITLE_TAG).performTouchInput { click(center) }
        composeRule.waitForIdle()
        assertEquals(beforeInvalid, navigator.destinations.size)
    }

    /**
     * 标题隐藏动画中反转滚动方向后，主线程与菜单仍须响应；行为依据：https://github.com/zly2006/zhihu-plus-plus/issues/638
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
        composeRule.onNodeWithText("复制链接").assertIsDisplayed()
    }

    /**
     * 首次向下翻页仅收起文章标题，不滚动正文；第二次才滚动正文。
     * 行为依据：https://github.com/zly2006/zhihu-plus-plus/issues/630
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

        assertTrue(dispatcher.dispatch(PageTurnCommand.PageDown))
        composeRule.waitUntil(5_000) {
            scrollContainer
                .fetchSemanticsNode()
                .config[SemanticsProperties.VerticalScrollAxisRange]
                .value() > initialValue
        }
    }

    /**
     * 长正文首帧耗时须包含真实 HTML 解析、布局与绘制。
     * 行为来源：https://github.com/zly2006/zhihu-plus-plus/issues/495
     */
    @Test
    fun issue495FirstFrameBenchmarkIncludesHtmlParsingLayoutAndDraw() {
        val warmupViewModel = seededAnswerViewModel(ANSWER)
        composeRule.activity.runOnUiThread {
            warmupViewModel.content =
                """<p>预热正文 <img src="https://www.zhihu.com/equation?tex=x%5E2" eeimg="1" /></p>"""
        }
        composeRule.setScreenContent {
            Scaffold(
                modifier = androidx.compose.ui.Modifier
                    .fillMaxSize(),
            ) { _ ->
                ArticleScreen(
                    article = ANSWER,
                    viewModel = warmupViewModel,
                )
            }
        }
        composeRule.onNodeWithText("预热正文", substring = true).assertIsDisplayed()
        composeRule.onRoot().captureToImage()

        val fullFixtureWarmupSamples = mutableListOf<Long>()
        val samples = buildList {
            repeat(7) { iteration ->
                val viewModel = issue495ViewModel()
                composeRule.activity.runOnUiThread {
                    viewModel.content += "<!-- benchmark-run-$iteration -->"
                }
                val startedAt = SystemClock.elapsedRealtime()
                composeRule.setScreenContent {
                    Scaffold(
                        modifier = androidx.compose.ui.Modifier
                            .fillMaxSize(),
                    ) { _ ->
                        ArticleScreen(
                            article = ANSWER,
                            viewModel = viewModel,
                        )
                    }
                }
                composeRule.onNodeWithText("更新：", substring = true).assertIsDisplayed()
                composeRule.onRoot().captureToImage()
                val elapsedMillis = SystemClock.elapsedRealtime() - startedAt
                if (iteration < 2) {
                    fullFixtureWarmupSamples += elapsedMillis
                } else {
                    add(elapsedMillis)
                }
            }
        }
        val medianMillis = samples.sorted()[samples.size / 2]
        Log.i(
            ISSUE_495_BENCHMARK_TAG,
            "fullFixtureWarmupSamplesMs=$fullFixtureWarmupSamples firstFrameSamplesMs=$samples " +
                "medianMs=$medianMillis htmlChars=36460",
        )
        assertTrue(
            "问题 #495 的首帧耗时中位数为 ${medianMillis}ms；测量包含 HTML 解析、Compose 布局与绘制",
            medianMillis < ISSUE_495_FIRST_FRAME_LIMIT_MS,
        )
    }

    /**
     * 长正文选区滚离后返回仍须高亮，并能通过可见工具栏完整复制。
     * 行为来源：https://github.com/zly2006/zhihu-plus-plus/issues/495
     */
    @Test
    fun selectionSurvivesDeferredMarkdownViewDisposal() {
        val clipboard = RecordingClipboard()
        val scheme = lightColorScheme()
        val selectionColor = scheme.primary.copy(alpha = 0.3f).compositeOver(scheme.surface)
        val tag = "deferred-markdown-selection"
        val firstParagraph = "FIRST_TARGET"
        val secondParagraph = "SECOND_TARGET"
        val codeBlock = "CODE_TARGET"
        val quoteBlock = "QUOTE_TARGET"
        val tableCell = "TABLE_TARGET"
        val thirdFromLastParagraph = "TAIL_THIRD_TARGET"
        val secondFromLastParagraph = "TAIL_PENULTIMATE_TARGET"
        val lastParagraph = "TAIL_FINAL_TARGET"
        val fillerParagraphs = (0 until 200).map { "FILLER_$it" }
        val markdown = buildString {
            appendLine(firstParagraph)
            appendLine()
            appendLine(secondParagraph)
            appendLine()
            fillerParagraphs.take(40).forEach {
                appendLine(it)
                appendLine()
            }
            appendLine("```text")
            appendLine(codeBlock)
            appendLine("```")
            appendLine()
            fillerParagraphs.drop(40).take(40).forEach {
                appendLine(it)
                appendLine()
            }
            appendLine("> $quoteBlock")
            appendLine()
            fillerParagraphs.drop(80).take(40).forEach {
                appendLine(it)
                appendLine()
            }
            appendLine("| 测试列 |")
            appendLine("| --- |")
            appendLine("| $tableCell |")
            appendLine()
            fillerParagraphs.drop(120).forEach {
                appendLine(it)
                appendLine()
            }
            appendLine(thirdFromLastParagraph)
            appendLine()
            appendLine(secondFromLastParagraph)
            appendLine()
            appendLine(lastParagraph)
        }
        composeRule.setScreenContent {
            MaterialTheme(colorScheme = scheme) {
                CompositionLocalProvider(LocalClipboard provides clipboard) {
                    RenderMarkdownText(markdown = markdown, modifier = Modifier.background(scheme.surface).testTag(tag))
                }
            }
        }

        val scrollContainer = composeRule.onNode(
            SemanticsMatcher("具有垂直滚动轴") { node ->
                node.config.contains(SemanticsProperties.VerticalScrollAxisRange)
            },
        )

        assertSelectionSurvivesScrolling(
            tag = tag,
            selectionColor = selectionColor,
            target = secondParagraph,
            additionallyOffscreen = listOf(firstParagraph),
            awayToEnd = true,
            scrollContainer = scrollContainer,
            clipboard = clipboard,
        )

        scrollToBoundary(scrollContainer, end = false)
        listOf(codeBlock, quoteBlock, tableCell, lastParagraph).forEach { target ->
            scrollForwardToText(scrollContainer, target)
        }

        scrollToText(scrollContainer, secondParagraph)
        val press = textPoint(tag, secondParagraph, 1)
        composeRule.onRoot().performTouchInput { longClick(press) }
        composeRule.onNodeWithText("全选").performClick()
        waitUntilSelectionHighlight(
            tag = tag,
            text = secondParagraph,
            selectionColor = selectionColor,
            failureMessage = "全选未高亮第二段",
        )
        scrollToBoundary(scrollContainer, end = true)
        listOf(firstParagraph, secondParagraph).forEach { text ->
            assertTrue("滚离后 $text 不应仍在视口内", !textIsVisibleInViewport(scrollContainer, text))
        }

        scrollToText(scrollContainer, secondParagraph)
        waitUntilSelectionHighlight(
            tag = tag,
            text = secondParagraph,
            selectionColor = selectionColor,
            failureMessage = "返回第二段后，全文选择高亮消失",
        )
        val expectedUniqueTexts = listOf(
            firstParagraph,
            secondParagraph,
            codeBlock,
            quoteBlock,
            tableCell,
            thirdFromLastParagraph,
            secondFromLastParagraph,
            lastParagraph,
        )
        val expectedFullDocument = buildList {
            add(firstParagraph)
            add(secondParagraph)
            addAll(fillerParagraphs.take(40))
            add("$codeBlock\n")
            addAll(fillerParagraphs.drop(40).take(40))
            add(quoteBlock)
            addAll(fillerParagraphs.drop(80).take(40))
            add("测试列\n$tableCell")
            addAll(fillerParagraphs.drop(120))
            add(thirdFromLastParagraph)
            add(secondFromLastParagraph)
            add(lastParagraph)
        }.joinToString("\n")
        val fullDocumentCopy = copySelection(clipboard)
        assertCompleteDocumentSelection(fullDocumentCopy, expectedUniqueTexts, fillerParagraphs)
        assertEquals(expectedFullDocument, fullDocumentCopy)

        val restoredPress = textPoint(tag, secondParagraph, 1)
        composeRule.onRoot().performTouchInput { longClick(restoredPress) }
        composeRule.onNodeWithText("全选").performClick()
        waitUntilSelectionHighlight(
            tag = tag,
            text = secondParagraph,
            selectionColor = selectionColor,
            failureMessage = "再次全选未高亮第二段",
        )
        scrollToBoundary(scrollContainer, end = true)
        assertTrue("再次滚离后第二段不应仍在视口内", !textIsVisibleInViewport(scrollContainer, secondParagraph))
        scrollToText(scrollContainer, secondParagraph)
        waitUntilSelectionHighlight(
            tag = tag,
            text = secondParagraph,
            selectionColor = selectionColor,
            failureMessage = "再次返回第二段后，全文选择高亮消失",
        )
        val returnedFullDocumentCopy = copySelection(clipboard)
        assertCompleteDocumentSelection(returnedFullDocumentCopy, expectedUniqueTexts, fillerParagraphs)
        assertEquals(expectedFullDocument, returnedFullDocumentCopy)
        assertEquals(fullDocumentCopy, returnedFullDocumentCopy)
    }

    /**
     * 阅读长正文时，Activity 保存状态与重建不得崩溃。
     * 行为来源：https://github.com/zly2006/zhihu-plus-plus/issues/495
     */
    @Test
    fun deferredMarkdownSaveStateUsesBundleSafeKeys() {
        val markdown = (0 until 80).joinToString("\n\n") { index -> "SAVEABLE_BLOCK_$index" }
        composeRule.setScreenContent {
            RenderMarkdownText(markdown = markdown)
        }
        composeRule.onNodeWithText("SAVEABLE_BLOCK_0").assertIsDisplayed()

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.activityRule.scenario.recreate()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
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

    /** Compose 直接向手柄节点发事件会绕过窗口命中；从 HRM 锚点下方的可见圆柄走系统触摸分派。 */
    private fun dragSelectionHandleTo(description: String, destinationOnScreen: Offset) {
        val handle = composeRule.onNodeWithContentDescription(description).assertIsDisplayed().fetchSemanticsNode()
        val anchor = handle.positionOnScreen + Offset(handle.size.width / 2f, handle.size.height / 2f)
        val grip = Offset(0f, handle.size.height / 4f)
        val input = InstrumentationRegistry.getInstrumentation()
        val downTime = SystemClock.uptimeMillis()

        fun touch(action: Int, point: Offset) {
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, point.x, point.y, 0)
            try {
                event.source = InputDevice.SOURCE_TOUCHSCREEN
                input.sendPointerSync(event)
            } finally {
                event.recycle()
            }
        }
        touch(MotionEvent.ACTION_DOWN, anchor + grip)
        repeat(10) { step ->
            touch(MotionEvent.ACTION_MOVE, anchor + grip + (destinationOnScreen - anchor) * ((step + 1) / 10f))
        }
        touch(MotionEvent.ACTION_UP, destinationOnScreen + grip)
        composeRule.waitForIdle()
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

    private fun waitUntilSelectionHighlight(
        tag: String,
        text: String,
        selectionColor: Color,
        failureMessage: String,
    ) {
        composeRule.waitUntil(failureMessage, timeoutMillis = 5_000) {
            runCatching {
                paragraphFragments(tag, text).any { fragment ->
                    val pixels = composeRule
                        .onNode(SemanticsMatcher("已绘制的文字片段 ${fragment.nodeId}") { it.id == fragment.nodeId }, useUnmergedTree = true)
                        .assertIsDisplayed()
                        .captureToImage()
                        .toPixelMap()
                    (0 until pixels.height).any { y ->
                        (0 until pixels.width).any { x -> pixels[x, y].matchesPixel(selectionColor) }
                    }
                }
            }.getOrDefault(false)
        }
    }

    private fun assertSelectionSurvivesScrolling(
        tag: String,
        selectionColor: Color,
        target: String,
        awayToEnd: Boolean,
        scrollContainer: SemanticsNodeInteraction,
        clipboard: RecordingClipboard,
        additionallyOffscreen: List<String> = emptyList(),
    ) {
        scrollToText(scrollContainer, target)
        val press = textPoint(tag, target, 1)
        composeRule.onRoot().performTouchInput { longClick(press) }
        waitUntilSelectionHighlight(
            tag = tag,
            text = target,
            selectionColor = selectionColor,
            failureMessage = "滚离前选区未高亮 $target",
        )

        scrollToBoundary(scrollContainer, end = awayToEnd)
        (listOf(target) + additionallyOffscreen).forEach { text ->
            assertTrue("滚离后 $text 不应仍在视口内", !textIsVisibleInViewport(scrollContainer, text))
        }

        scrollToText(scrollContainer, target)
        waitUntilSelectionHighlight(
            tag = tag,
            text = target,
            selectionColor = selectionColor,
            failureMessage = "返回 $target 后，选择高亮消失",
        )
        val restoredCopy = copySelection(clipboard)
        assertEquals("返回后的选区必须完整复制 $target", target, restoredCopy)

        val restoredPress = textPoint(tag, target, 1)
        composeRule.onRoot().performTouchInput { longClick(restoredPress) }
        waitUntilSelectionHighlight(
            tag = tag,
            text = target,
            selectionColor = selectionColor,
            failureMessage = "重新选择 $target 未产生可见高亮",
        )

        scrollToBoundary(scrollContainer, end = awayToEnd)
        assertTrue("再次滚离后 $target 不应仍在视口内", !textIsVisibleInViewport(scrollContainer, target))
        scrollToText(scrollContainer, target)
        waitUntilSelectionHighlight(
            tag = tag,
            text = target,
            selectionColor = selectionColor,
            failureMessage = "再次返回 $target 后，选择高亮消失",
        )
        val returnedCopy = copySelection(clipboard)
        assertEquals("再次返回后的选区必须完整复制 $target", target, returnedCopy)
        assertEquals(restoredCopy, returnedCopy)
    }

    private fun scrollToText(
        scrollContainer: SemanticsNodeInteraction,
        text: String,
    ) {
        scrollToBoundary(scrollContainer, end = false)
        scrollForwardToText(scrollContainer, text)
    }

    private fun textIsVisibleInViewport(
        scrollContainer: SemanticsNodeInteraction,
        text: String,
        substring: Boolean = false,
    ): Boolean {
        val viewport = scrollContainer.fetchSemanticsNode().boundsInRoot
        assertTrue("实际滚动视口必须有正尺寸：$viewport", viewport.width > 0f && viewport.height > 0f)
        val nodes = composeRule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes()
        assertTrue("目标文字必须唯一：$text；找到 ${nodes.size} 个节点", nodes.size <= 1)
        return nodes.any { node ->
            val bounds = node.boundsInRoot.intersect(viewport)
            bounds.width > 0f && bounds.height > 0f
        }
    }

    private fun scrollForwardToText(
        scrollContainer: SemanticsNodeInteraction,
        text: String,
        substring: Boolean = false,
    ) {
        repeat(80) {
            if (
                textIsVisibleInViewport(scrollContainer, text, substring) &&
                composeRule.onNodeWithText(text, substring = substring).isDisplayed()
            ) {
                return
            }
            val viewport = scrollContainer.fetchSemanticsNode().boundsInRoot
            scrollContainer.performSemanticsAction(SemanticsActions.ScrollBy) { scrollBy ->
                assertTrue("Markdown 应能继续滚向 $text", scrollBy(0f, viewport.height * 0.8f))
            }
            composeRule.waitForIdle()
        }
        assertTrue("有界滚动后 $text 未进入实际视口", textIsVisibleInViewport(scrollContainer, text, substring))
        composeRule.onNodeWithText(text, substring = substring).assertIsDisplayed()
    }

    private fun scrollToBoundary(
        scrollContainer: SemanticsNodeInteraction,
        end: Boolean,
    ) {
        repeat(80) {
            val range = scrollContainer
                .fetchSemanticsNode()
                .config[SemanticsProperties.VerticalScrollAxisRange]
            val reachedBoundary = if (end) {
                range.maxValue() - range.value() <= 1f
            } else {
                range.value() <= 1f
            }
            if (reachedBoundary) return
            scrollContainer.performSemanticsAction(SemanticsActions.ScrollBy) { scrollBy ->
                scrollBy(0f, if (end) 4_000f else -4_000f)
            }
            composeRule.waitForIdle()
        }
        assertTrue("Markdown 未滚动到${if (end) "末尾" else "开头"}", false)
    }

    private fun assertCompleteDocumentSelection(
        copiedText: String,
        expectedUniqueTexts: List<String>,
        fillerParagraphs: List<String>,
    ) {
        expectedUniqueTexts.forEach { expected ->
            assertEquals(
                "全文复制结果中 $expected 必须恰好出现一次",
                1,
                Regex(Regex.escape(expected)).findAll(copiedText).count(),
            )
        }
        val copiedFillerIndexes = Regex("FILLER_(\\d+)")
            .findAll(copiedText)
            .map { it.groupValues[1].toInt() }
            .toList()
        assertEquals(fillerParagraphs.indices.toList(), copiedFillerIndexes)
    }

    @Test
    fun tiqianLongPressOpensMenuAndSelectsAll() {
        val paragraph = "Select this paragraph"
        composeRule.setScreenContent {
            RenderMarkdown(html = "<p>$paragraph</p>", enableScroll = false, useTiqianRenderer = true)
        }

        val body = composeRule.onNodeWithText(paragraph, useUnmergedTree = true)
        body.performTouchInput { longClick(Offset(8.dp.toPx(), center.y)) }
        composeRule.onNodeWithText("复制").assertIsDisplayed()
        composeRule.onNodeWithText("全选").assertIsDisplayed().performClick()
        val selection = body.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange]
        assertEquals(TextRange(0, paragraph.length), selection)
    }

    /**
     * 划线段落须保留标准文字选择交互；行为依据：https://github.com/zly2006/zhihu-plus-plus/issues/322
     */
    @Test
    fun highlightedParagraphRemainsSelectable() {
        val clipboard = RecordingClipboard()
        val scheme = lightColorScheme()
        val selectionColor = scheme.primary.copy(alpha = 0.3f).compositeOver(scheme.surface)
        val tag = "highlighted-selection-article"
        composeRule.setScreenContent {
            MaterialTheme(colorScheme = scheme) {
                CompositionLocalProvider(LocalClipboard provides clipboard) {
                    RenderMarkdown(
                        html = HIGHLIGHTED_PARAGRAPH_HTML,
                        modifier = Modifier.width(280.dp).background(scheme.surface).testTag(tag),
                        enableScroll = false,
                    )
                }
            }
        }

        val press = textPoint(tag, HIGHLIGHTED_PARAGRAPH, 1)
        composeRule.onRoot().performTouchInput { longClick(press) }
        composeRule.onNodeWithText("划线片段").assertDoesNotExist()

        val selectionImage = composeRule.onNodeWithTag(tag).captureToImage()
        val selectedPixels = selectionImage.toPixelMap().let { pixels ->
            (0 until pixels.height).sumOf { y ->
                (0 until pixels.width).count { x -> pixels[x, y].matchesPixel(selectionColor) }
            }
        }
        assertTrue(
            "长按划线段落必须绘制可见的选择背景；检测到 $selectedPixels 个选中像素",
            selectedPixels >= 100,
        )
        composeRule.onNodeWithText("全选").performClick()

        val copiedText = copySelection(clipboard)
        assertEquals(HIGHLIGHTED_PARAGRAPH, copiedText)
    }

    /**
     * 划线段落须保留标准文字选择交互；行为依据：https://github.com/zly2006/zhihu-plus-plus/issues/322
     */
    @Test
    fun highlightedParagraphSelectionHandleCanExtendToFollowingParagraph() {
        val clipboard = RecordingClipboard()
        val scheme = lightColorScheme()
        val selectionColor = scheme.primary.copy(alpha = 0.3f).compositeOver(scheme.surface)
        val tag = "highlighted-selection-drag-article"
        composeRule.setScreenContent {
            MaterialTheme(colorScheme = scheme) {
                CompositionLocalProvider(LocalClipboard provides clipboard) {
                    RenderMarkdown(
                        html = "$HIGHLIGHTED_PARAGRAPH_HTML<p>$HIGHLIGHT_SELECTION_TARGET</p>",
                        modifier = Modifier.width(280.dp).background(scheme.surface).testTag(tag),
                        enableScroll = false,
                    )
                }
            }
        }

        val press = textPoint(tag, HIGHLIGHTED_PARAGRAPH, 1)
        val root = composeRule.onRoot().fetchSemanticsNode()
        composeRule.onRoot().performTouchInput { longClick(press) }
        val rootOrigin = root.positionOnScreen - root.positionInRoot
        val endHandle = "Markdown selection end handle"
        val prefixEnd = HIGHLIGHTED_PARAGRAPH.indexOf("账号")
        dragSelectionHandleTo(endHandle, rootOrigin + textPoint(tag, HIGHLIGHTED_PARAGRAPH, prefixEnd))
        assertEquals("划线内手动收短的选区不能被工具栏重置", HIGHLIGHTED_PARAGRAPH.take(prefixEnd), copySelection(clipboard))

        composeRule.onRoot().performTouchInput { longClick(press) }
        dragSelectionHandleTo(endHandle, rootOrigin + textPoint(tag, HIGHLIGHT_SELECTION_TARGET, HIGHLIGHT_SELECTION_TARGET.length))
        val selectedPixels = composeRule.onNodeWithTag(tag).captureToImage().toPixelMap().let { pixels ->
            (0 until pixels.height).sumOf { y ->
                (0 until pixels.width).count { x -> pixels[x, y].matchesPixel(selectionColor) }
            }
        }
        assertTrue(
            "从划线段落拖出后，跨块选区必须仍可见；检测到 $selectedPixels 个选中像素",
            selectedPixels >= 1_000,
        )
        waitUntilSelectionHighlight(
            tag = tag,
            text = HIGHLIGHT_SELECTION_TARGET,
            selectionColor = selectionColor,
            failureMessage = "拖动手柄后选区未延伸到后续文字块",
        )
        val copiedText = copySelection(clipboard)
        assertTrue(
            "标准选择手柄必须能从划线段落延伸到后续文字块",
            copiedText.contains(HIGHLIGHT_SELECTION_TARGET),
        )
    }

    /**
     * 划线段落点击仍应打开操作面板；行为依据：https://github.com/zly2006/zhihu-plus-plus/issues/595
     */
    @Test
    fun highlightedParagraphTapStillOpensActions() {
        composeRule.setScreenContent {
            RenderMarkdown(
                html = HIGHLIGHTED_PARAGRAPH_HTML,
                modifier = Modifier.testTag("highlighted-tap-article"),
                enableScroll = false,
            )
        }

        val point = textPoint("highlighted-tap-article", HIGHLIGHTED_PARAGRAPH, 1)
        composeRule.onRoot().performTouchInput { click(point) }
        composeRule.onNodeWithText("划线片段").assertIsDisplayed()
        composeRule.onNodeWithText("“$HIGHLIGHTED_PARAGRAPH”").assertIsDisplayed()
        composeRule.onNodeWithTag(COMMENT_COMPOSER_ENTRY_TAG).assertIsDisplayed()
        composeRule.onNode(hasContentDescription("喜欢"), useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNode(hasContentDescription("评论"), useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("9").assertIsDisplayed()
        composeRule.onNodeWithText("2").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("复制内容").assertIsDisplayed()
        composeRule.onNodeWithTag("segment_action_sheet_top_divider").assertDoesNotExist()
        composeRule.onNodeWithTag("segment_action_sheet_bottom_divider").assertDoesNotExist()
    }

    /**
     * 跨段划线操作面板行为依据：https://github.com/zly2006/zhihu-plus-plus/issues/595
     */
    @Test
    fun spanningHighlightTapShowsTheCompleteSelection() {
        composeRule.setScreenContent {
            RenderMarkdown(
                html = SPANNING_HIGHLIGHT_HTML,
                modifier = Modifier.testTag("spanning-highlight-article"),
                enableScroll = false,
            )
        }

        val point = textPoint("spanning-highlight-article", SPANNING_HIGHLIGHT_SECOND, 1)
        composeRule.onRoot().performTouchInput { click(point) }

        composeRule.onNodeWithText("划线片段").assertIsDisplayed()
        composeRule
            .onNodeWithText("“$SPANNING_HIGHLIGHT_FIRST\n\n$SPANNING_HIGHLIGHT_SECOND”")
            .assertIsDisplayed()
        composeRule.onNodeWithTag(COMMENT_COMPOSER_ENTRY_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("806").assertIsDisplayed()
        composeRule.onNodeWithText("15").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("复制内容").assertIsDisplayed()
    }

    /**
     * 跨段划线操作面板行为依据：https://github.com/zly2006/zhihu-plus-plus/issues/595
     */
    @Test
    fun longSpanningHighlightShowsDirectionalDividersAndKeepsActionsVisible() {
        val repeatedParagraphs = List(24) { SPANNING_HIGHLIGHT_FIRST }
        val longDisplayText = repeatedParagraphs.joinToString("\n\n")
        val longDisplayTextAttribute = repeatedParagraphs.joinToString("&#10;&#10;")
        val html = SPANNING_HIGHLIGHT_HTML.replace(
            "$SPANNING_HIGHLIGHT_FIRST&#10;&#10;$SPANNING_HIGHLIGHT_SECOND",
            longDisplayTextAttribute,
        )
        composeRule.setScreenContent {
            RenderMarkdown(
                html = html,
                modifier = Modifier.testTag("long-spanning-highlight-article"),
                enableScroll = false,
            )
        }

        val point = textPoint("long-spanning-highlight-article", SPANNING_HIGHLIGHT_SECOND, 1)
        composeRule.onRoot().performTouchInput { click(point) }

        val text = composeRule.onNodeWithText("“$longDisplayText”")
        text.fetchSemanticsNode()
        composeRule.onNodeWithText("划线片段").assertIsDisplayed()
        composeRule.onNodeWithTag(COMMENT_COMPOSER_ENTRY_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag("segment_action_sheet_top_divider").assertDoesNotExist()
        composeRule.onNodeWithTag("segment_action_sheet_bottom_divider").assertIsDisplayed()
        composeRule.onNodeWithText("15").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("复制内容").assertIsDisplayed()

        scrollToBoundary(text, end = true)
        val finalScrollRange = text
            .fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue("展开后的面板仍须包含超出可视范围的文字", finalScrollRange.maxValue() > 0f)

        composeRule.onNodeWithTag("segment_action_sheet_top_divider").assertIsDisplayed()
        composeRule.onNodeWithTag("segment_action_sheet_bottom_divider").assertDoesNotExist()
        composeRule.onNodeWithText("15").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("复制内容").assertIsDisplayed()
    }

    /**
     * 划线点击命中与拖动区分行为依据：https://github.com/zly2006/zhihu-plus-plus/issues/595
     */
    @Test
    fun highlightedTextUsesVisibleLayoutForTapTarget() {
        composeRule.setScreenContent {
            RenderMarkdown(
                html = FORMATTED_HIGHLIGHT_PARAGRAPH_HTML,
                modifier = Modifier.width(240.dp).testTag("formatted-highlight-article"),
                enableScroll = false,
            )
        }

        val point = textPoint("formatted-highlight-article", FORMATTED_HIGHLIGHT_PARAGRAPH, FORMATTED_HIGHLIGHT_PREFIX.length + 1)
        composeRule.onRoot().performTouchInput { click(point) }

        composeRule.onNodeWithText("划线片段").assertIsDisplayed()
        composeRule.onNodeWithText("“$FORMATTED_HIGHLIGHT”").assertIsDisplayed()
    }

    /**
     * 划线虚线须覆盖换行后的每个视觉行；行为依据：https://github.com/zly2006/zhihu-plus-plus/issues/271
     */
    @Test
    fun highlightedTextDrawsDashesAcrossEveryWrappedLine() {
        composeRule.setScreenContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    outlineVariant = Color.Magenta,
                    primary = Color.Magenta,
                ),
            ) {
                RenderMarkdown(
                    html = WRAPPED_HIGHLIGHT_PARAGRAPH_HTML,
                    modifier = androidx.compose.ui.Modifier
                        .width(220.dp)
                        .testTag("wrapped-highlight-article"),
                    enableScroll = false,
                )
            }
        }

        val fragments = paragraphFragments("wrapped-highlight-article", WRAPPED_HIGHLIGHT_PARAGRAPH)
        val highlightStart = WRAPPED_HIGHLIGHT_PREFIX.length
        val highlightEnd = highlightStart + WRAPPED_HIGHLIGHT.length
        val container = composeRule.onNodeWithTag("wrapped-highlight-article")
        val bounds = container.fetchSemanticsNode().boundsInRoot
        val pixels = container.captureToImage().toPixelMap()
        val highlightedLines = fragments
            .flatMap { fragment ->
                (0 until fragment.layout.lineCount).mapNotNull { line ->
                    val start = fragment.startOffset + fragment.layout.getLineStart(line)
                    val end = fragment.startOffset + fragment.layout.getLineEnd(line)
                    if (start < highlightEnd && end > highlightStart) {
                        fragment.bounds.top - bounds.top + fragment.layout.getLineBottom(line)
                    } else {
                        null
                    }
                }
            }.distinct()
        assertTrue("测试样本必须使划线文字至少换行至三行", highlightedLines.size >= 3)
        for ((line, lineBottom) in highlightedLines.withIndex()) {
            val top = (lineBottom - 6f).toInt().coerceAtLeast(0)
            val bottom = (lineBottom + 2f).toInt().coerceAtMost(pixels.height - 1)
            val magentaPixels = (top..bottom).sumOf { y ->
                (0 until pixels.width).count { x ->
                    val color = pixels[x, y]
                    color.red > 0.8f && color.green < 0.2f && color.blue > 0.8f
                }
            }
            assertTrue(
                "划线视觉行 $line 必须包含可见的虚线像素；检测到 $magentaPixels 个",
                magentaPixels >= 4,
            )
        }
    }

    /**
     * 划线点击命中与拖动区分行为依据：https://github.com/zly2006/zhihu-plus-plus/issues/595
     */
    @Test
    fun highlightedParagraphTapOpensActionsInsideAnswerScreen() {
        val viewModel = seededAnswerViewModel(ANSWER)
        composeRule.activity.runOnUiThread {
            viewModel.content = HIGHLIGHTED_PARAGRAPH_HTML
        }
        composeRule.setScreenContent {
            Scaffold(
                modifier = androidx.compose.ui.Modifier
                    .fillMaxSize(),
            ) { _ ->
                Box(Modifier.fillMaxSize().testTag("highlighted-answer-screen")) {
                    ArticleScreen(
                        article = ANSWER,
                        viewModel = viewModel,
                    )
                }
            }
        }

        val point = textPoint("highlighted-answer-screen", HIGHLIGHTED_PARAGRAPH, 1)
        composeRule.onRoot().performTouchInput { click(point) }
        composeRule.onNodeWithText("划线片段").assertIsDisplayed()
        composeRule.onNodeWithText("“$HIGHLIGHTED_PARAGRAPH”").assertIsDisplayed()
        composeRule.onNodeWithTag(COMMENT_COMPOSER_ENTRY_TAG).assertIsDisplayed()
        composeRule.onNode(hasContentDescription("喜欢"), useUnmergedTree = true).assertIsDisplayed()
        composeRule
            .onNode(
                hasContentDescription("评论") and hasAnyAncestor(
                    isDialog() and hasAnyDescendant(hasText("划线片段")) and
                        hasAnyDescendant(hasText("2")) and hasAnyDescendant(hasContentDescription("复制内容")),
                ),
                useUnmergedTree = true,
            ).assertIsDisplayed()
        composeRule.onNodeWithText("9").assertIsDisplayed()
        composeRule.onNodeWithText("2").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("复制内容").assertIsDisplayed()
    }

    /**
     * 划线点击命中与拖动区分行为依据：https://github.com/zly2006/zhihu-plus-plus/issues/595
     */
    @Test
    fun highlightedParagraphDragDoesNotOpenActions() {
        val tag = "highlighted-drag-article"
        composeRule.setScreenContent {
            RenderMarkdown(
                html = HIGHLIGHTED_PARAGRAPH_HTML,
                modifier = Modifier.testTag(tag),
                enableScroll = false,
            )
        }

        val point = textPoint(tag, HIGHLIGHTED_PARAGRAPH, 1)
        composeRule.onRoot().performTouchInput {
            down(point)
            moveBy(Offset(0f, -100f))
            up()
        }
        composeRule.onNodeWithText("划线片段").assertDoesNotExist()
    }

    /**
     * 全选须高亮长段落的每个真实视觉行，并完整复制原文。
     * 行为来源：https://github.com/zly2006/zhihu-plus-plus/issues/495
     */
    @Test
    fun selectAllHighlightsEveryVisualLineOfLongParagraph() {
        val clipboard = RecordingClipboard()
        val scheme = lightColorScheme()
        val selectionColor = scheme.primary.copy(alpha = 0.3f).compositeOver(scheme.surface)
        val paragraph = "长段落的选中背景必须跟随真实换行，".repeat(12)
        val tag = "multiline-selection-article"
        composeRule.setScreenContent {
            MaterialTheme(colorScheme = scheme) {
                CompositionLocalProvider(LocalClipboard provides clipboard) {
                    RenderMarkdownText(
                        markdown = paragraph,
                        modifier = Modifier.width(240.dp).background(scheme.surface).testTag(tag),
                        enableScroll = false,
                    )
                }
            }
        }

        val fragments = paragraphFragments(tag, paragraph)
        val press = textPoint(tag, paragraph, 1)
        composeRule.onRoot().performTouchInput { longClick(press) }
        composeRule.onNodeWithText("全选").performClick()

        val container = composeRule.onNodeWithTag(tag)
        val bounds = container.fetchSemanticsNode().boundsInRoot
        val pixels = container.captureToImage().toPixelMap()
        val highlightedRows = (0 until pixels.height).count { y ->
            (0 until pixels.width).count { x -> pixels[x, y].matchesPixel(selectionColor) } >= 100
        }
        Log.i("MarkdownSelection", "multilineSelectionHighlightedRows=$highlightedRows")
        assertTrue(
            "全选高亮仅覆盖 $highlightedRows 行像素；换行段落的每一行都必须高亮。",
            highlightedRows >= 180,
        )
        for (fragment in fragments) {
            val top = (fragment.bounds.top - bounds.top).toInt().coerceAtLeast(0)
            val bottom = (fragment.bounds.bottom - bounds.top).toInt().coerceAtMost(pixels.height)
            val selectedPixels = (top until bottom).sumOf { y ->
                (0 until pixels.width).count { x -> pixels[x, y].matchesPixel(selectionColor) }
            }
            assertTrue("UTF-16 偏移 ${fragment.startOffset} 处的每个已绘制行必须保留选择背景", selectedPixels >= 100)
        }
        assertEquals(paragraph, copySelection(clipboard))
    }

    /**
     * 收起后重新长按可多次拖动同一手柄，跨段高亮和复制都随实际选区变化。
     * 行为来源：https://github.com/zly2006/zhihu-plus-plus/issues/495
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
                            .padding(start = 24.dp, top = 96.dp)
                            .width(280.dp)
                            .background(scheme.surface)
                            .testTag(tag),
                        enableScroll = false,
                    )
                }
            }
        }
        val press = textPoint(tag, paragraphs.first(), paragraphs.first().indexOf("TARGET") + 2)
        val endHandle = "Markdown selection end handle"
        val root = composeRule.onRoot().fetchSemanticsNode()
        val rootOrigin = root.positionOnScreen - root.positionInRoot
        composeRule.onRoot().performTouchInput { longClick(press) }
        waitUntilSelectionHighlight(tag, paragraphs.first(), selectionColor, "第一次长按未选中正文")
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        composeRule.onNodeWithContentDescription(endHandle).assertDoesNotExist()

        composeRule.onRoot().performTouchInput { longClick(press) }
        dragSelectionHandleTo("Markdown selection start handle", rootOrigin + textPoint(tag, paragraphs.first(), 0))
        dragSelectionHandleTo(endHandle, rootOrigin + textPoint(tag, paragraphs[1], paragraphs[1].length))
        waitUntilSelectionHighlight(tag, paragraphs[1], selectionColor, "重新选择后，第一次拖动末手柄未到达中段")
        dragSelectionHandleTo(endHandle, rootOrigin + textPoint(tag, paragraphs.last(), paragraphs.last().length))
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

    /**
     * 长正文须能真实滚到屏外目标与末尾，再返回首部；不约束屏外节点是否保留。
     * 行为来源：https://github.com/zly2006/zhihu-plus-plus/issues/495
     */
    @Test
    fun longArticleScrollsToOffscreenContentAndEndThenBackToStart() {
        val viewModel = issue495ViewModel()

        composeRule.setScreenContent {
            Scaffold(
                modifier = androidx.compose.ui.Modifier
                    .fillMaxSize(),
            ) { _ ->
                ArticleScreen(
                    article = ANSWER,
                    viewModel = viewModel,
                )
            }
        }

        composeRule.onNodeWithText("更新：", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag(ARTICLE_IP_INFO_TAG).assertIsDisplayed()
        val scrollContainer = composeRule.onNode(
            SemanticsMatcher("具有垂直滚动轴") { node ->
                node.config.contains(SemanticsProperties.VerticalScrollAxisRange)
            },
        )
        assertTrue("正文末段初始应位于视口之外", !textIsVisibleInViewport(scrollContainer, "再减去", substring = true))
        assertTrue(
            "长正文必须有真实的正滚动范围",
            scrollContainer.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].maxValue() > 0f,
        )

        scrollForwardToText(scrollContainer, "再减去", substring = true)
        composeRule.onNodeWithText("再减去", substring = true).assertIsDisplayed()
        assertTrue("滚到末段后首段应已离开视口", !textIsVisibleInViewport(scrollContainer, "更新：", substring = true))

        scrollToBoundary(scrollContainer, end = true)
        composeRule.onNodeWithText("即可得到原题目的解。", substring = true).assertIsDisplayed()
        val endRange = scrollContainer.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue("长正文到末尾后仍须保留正滚动范围", endRange.maxValue() > 0f)
        assertTrue("必须实际到达文档末尾", endRange.maxValue() - endRange.value() <= 1f)

        scrollToBoundary(scrollContainer, end = false)
        composeRule.onNodeWithText("更新：", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag(ARTICLE_IP_INFO_TAG).assertIsDisplayed()
        assertTrue("返回首部后末段应离开视口", !textIsVisibleInViewport(scrollContainer, "再减去", substring = true))
    }

    /**
     * 网络图片加载前须按 API 宽高比预留布局空间；行为依据：https://github.com/zly2006/zhihu-plus-plus/issues/320
     */
    @Test
    fun markdownImageReservesItsApiAspectRatioBeforeNetworkLoad() {
        composeRule.setScreenContent {
            RenderImage(
                data = MarkdownImageData(
                    url = "https://invalid.invalid/not-loaded.jpg",
                    altText = "未加载的比例图片",
                    width = 1200,
                    height = 880,
                ),
                modifier = androidx.compose.ui.Modifier,
            )
        }

        val imageBounds = composeRule
            .onNodeWithContentDescription("未加载的比例图片")
            .fetchSemanticsNode()
            .boundsInRoot
        assertTrue("图片加载前必须预留宽度", imageBounds.width > 0f)
        assertTrue("图片加载前必须预留高度", imageBounds.height > 0f)
        assertEquals(
            1200.0 / 880.0,
            imageBounds.width.toDouble() / imageBounds.height.toDouble(),
            0.02,
        )
    }

    /**
     * 外层正文滚动中，脚注跳转与返回须显示各自目标。
     * 行为来源：https://github.com/zly2006/zhihu-plus-plus/issues/495
     */
    @Test
    fun footnoteReferenceAndBackLinkNavigateInsideOuterArticleScroll() {
        val markdown = buildString {
            appendLine("正文开头脚注[^note]")
            appendLine()
            repeat(60) { index ->
                appendLine("填充段落 $index：用于确保脚注定义位于当前屏幕之外。")
                appendLine()
            }
            appendLine("[^note]: 脚注内容")
        }

        composeRule.setScreenContent {
            val scrollState = rememberScrollState()
            Column(
                modifier = androidx.compose.ui.Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState),
            ) {
                RenderMarkdownText(
                    markdown = markdown,
                    scrollState = scrollState,
                    enableScroll = false,
                )
            }
        }

        composeRule
            .onAllNodesWithText("[1]", useUnmergedTree = true)[0]
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithText("脚注内容").assertIsDisplayed()
        composeRule.onNodeWithText("↩").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("正文开头脚注", substring = true).assertIsDisplayed()
    }

    /**
     * 回答背书信息须在真实页面中可见；行为依据：https://github.com/zly2006/zhihu-plus-plus/issues/493
     */
    @Test
    fun answerEndorsementsRenderOffline() {
        val viewModel = seededAnswerViewModel(ANSWER)

        composeRule.setScreenContent {
            Scaffold(
                modifier = androidx.compose.ui.Modifier
                    .fillMaxSize(),
            ) { _ ->
                ArticleScreen(
                    article = ANSWER,
                    viewModel = viewModel,
                )
            }
        }

        listOf(
            "话题收录 我的开源名片",
            "创作声明: 内容包含剧透",
            "收录于话题: 科技",
        ).forEach { endorsement ->
            composeRule.onNodeWithText(endorsement).performScrollTo().assertIsDisplayed()
        }
    }

    /**
     * 朗读暂停在其他队列项时，文章菜单应提供停止操作；行为依据：https://github.com/zly2006/zhihu-plus-plus/issues/550
     */
    @Test
    fun pausedContinuousReadingOnAnotherQueueItemUsesStopActionInArticleMenu() {
        val viewModel = seededAnswerViewModel(ANSWER)
        AndroidReadingPlayerBridge.publish(
            ReadingPlayerState(
                status = ReadingPlaybackStatus.Paused,
                queue = listOf(
                    ReadingQueueItem(
                        contentType = ReadingContentType.Answer,
                        id = ANSWER.id,
                        title = "离线 Answer 标题",
                        author = "离线答主",
                    ),
                    ReadingQueueItem(
                        contentType = ReadingContentType.Answer,
                        id = NEXT_ANSWER.id,
                        title = "下一个离线回答",
                        author = "下一个作者",
                    ),
                ),
                currentIndex = 1,
            ),
        )
        composeRule.setScreenContent {
            Scaffold(
                modifier = androidx.compose.ui.Modifier
                    .fillMaxSize(),
            ) { _ ->
                ArticleScreen(
                    article = ANSWER,
                    viewModel = viewModel,
                )
            }
        }

        composeRule.onNodeWithContentDescription("更多选项").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("停止朗读").assertIsDisplayed()
        composeRule.onNodeWithText("暂停朗读").assertDoesNotExist()
        composeRule.onNodeWithText("继续朗读").assertDoesNotExist()
        composeRule.onNodeWithText("停止朗读").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            !AndroidReadingPlayerBridge.state.value.hasSession
        }
    }

    /**
     * 连续朗读队列应使用共享回答导航器的快照；行为依据：https://github.com/zly2006/zhihu-plus-plus/issues/550
     */
    @Test
    fun articleScreenUsesSharedAnswerNavigatorSnapshotForReadingQueue() {
        val viewModel = seededAnswerViewModel(ANSWER)
        val snapshotCurrentId = AtomicLong(-1L)
        val snapshotLimit = AtomicInteger(0)
        val sharedNavigator = object : AnswerNavigator(
            sourceName = "此问题",
            environment = NO_OP_API_ENVIRONMENT,
        ) {
            override suspend fun loadNext(): Article? = null

            override suspend fun prefetchNext(currentArticleId: Long) = Unit

            override suspend fun remainingAnswersSnapshot(
                currentArticleId: Long,
                limit: Int,
            ): List<Article> {
                snapshotCurrentId.set(currentArticleId)
                snapshotLimit.set(limit)
                return listOf(
                    NEXT_ANSWER,
                    Article(type = ArticleType.Answer, id = 779L),
                ).take(limit)
            }
        }
        composeRule.activity.runOnUiThread {
            sharedArticleAnswerSwitchState.pendingNavigator = sharedNavigator
        }
        composeRule.setScreenContent {
            Scaffold(
                modifier = androidx.compose.ui.Modifier
                    .fillMaxSize(),
            ) { _ ->
                ArticleScreen(
                    article = ANSWER,
                    viewModel = viewModel,
                )
            }
        }

        composeRule.onNodeWithContentDescription("更多选项").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("开始连续朗读").assertIsDisplayed().performClick()

        waitForReadingQueue(listOf(ANSWER.id, NEXT_ANSWER.id, 779L))
        assertEquals(ANSWER.id, snapshotCurrentId.get())
        assertEquals(4, snapshotLimit.get())

        val loadedContent = viewModel.content
        composeRule.setScreenContent {
            Scaffold(Modifier.fillMaxSize()) { _ ->
                ArticleScreen(article = ANSWER, viewModel = viewModel)
            }
        }
        composeRule.runOnIdle {
            assertTrue(sharedArticleAnswerSwitchState.navigator === sharedNavigator)
            assertTrue(sharedArticleAnswerSwitchState.pendingNavigator == null)
            assertEquals(loadedContent, viewModel.content)
            assertEquals(
                listOf(ANSWER.id, NEXT_ANSWER.id, 779L),
                AndroidReadingPlayerBridge.state.value.queue
                    .map(ReadingQueueItem::id),
            )
        }
    }

    /**
     * 切换回答按钮应导航到预取的下一回答；行为依据：https://github.com/zly2006/zhihu-plus-plus/issues/213
     */
    @Test
    fun skipAnswerButtonNavigatesToPrefetchedNextAnswerOffline() {
        val viewModel = seededAnswerViewModel(ANSWER)
        val nextAnswer = ArticleViewModel.CachedAnswerContent(
            article = NEXT_ANSWER,
            title = "下一个离线回答",
            authorName = "下一个作者",
            authorBio = "下一个签名",
            authorAvatarUrl = "",
            content = "下一个离线回答正文",
            voteUpCount = 7,
            commentCount = 3,
        )
        composeRule.activity.runOnUiThread {
            sharedArticleAnswerSwitchState.pendingNavigator = object : AnswerNavigator(
                sourceName = "此问题",
                environment = NO_OP_API_ENVIRONMENT,
            ) {
                init {
                    nextAnswerContent = nextAnswer
                }

                override suspend fun loadNext(): Article? {
                    nextAnswerContent = null
                    return nextAnswer.article
                }

                override suspend fun prefetchNext(currentArticleId: Long) = Unit
            }
        }
        val recordingNavigator = composeRule.setScreenContent {
            Scaffold(
                modifier = androidx.compose.ui.Modifier
                    .fillMaxSize(),
            ) { _ ->
                ArticleScreen(
                    article = ANSWER,
                    viewModel = viewModel,
                )
            }
        }
        composeRule.runOnIdle {
            assertTrue("已有正文也必须消费待交接导航", sharedArticleAnswerSwitchState.pendingNavigator == null)
            assertEquals(NEXT_ANSWER, sharedArticleAnswerSwitchState.navigator?.nextAnswer?.article)
        }

        composeRule
            .onNodeWithContentDescription("下一个回答")
            .assertIsDisplayed()
            .performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            recordingNavigator.destinations.contains(NEXT_ANSWER)
        }
    }

    /**
     * 切换回答按钮须能拖回右侧并保存位置；行为依据：https://github.com/zly2006/zhihu-plus-plus/issues/477
     */
    @Test
    fun skipAnswerButtonCanBeDraggedBackToRightEdge() {
        val viewModel = seededAnswerViewModel(ANSWER)
        var windowSize = IntSize.Zero
        composeRule.setScreenContent {
            windowSize = LocalWindowInfo.current.containerSize
            Scaffold(
                modifier = androidx.compose.ui.Modifier
                    .fillMaxSize(),
            ) { _ ->
                ArticleScreen(
                    article = ANSWER,
                    viewModel = viewModel,
                )
            }
        }

        val orientation = composeRule.runOnIdle {
            assertTrue("实际窗口必须已测量", windowSize.width > 0 && windowSize.height > 0)
            if (windowSize.width > windowSize.height) "landscape" else "portrait"
        }
        val placementKey = "buttonSkipAnswer-$orientation"
        val viewport = composeRule
            .onNodeWithTag("article_screen_root")
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot
        assertTrue("按钮所属视口必须有正尺寸", viewport.width > 0f && viewport.height > 0f)
        val button = composeRule.onNodeWithContentDescription("下一个回答")
        val preferences = composeRule.activity.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)
        val legacyPositions = preferences.all.filterKeys { it == "buttonSkipAnswer-x" || it == "buttonSkipAnswer-y" }
        // DraggableRefreshButton 贴边时默认留 16dp 边距；最小触控区比可见按钮宽，可见边界再内缩一半差值。
        val edgeInset = button.fetchSemanticsNode().let { node ->
            with(composeRule.density) { 16.dp.toPx() } + (node.layoutInfo.width - node.size.width) / 2f
        }
        for (edge in listOf("left", "right")) {
            val snappedEdge = if (edge == "left") viewport.left + edgeInset else viewport.right - edgeInset
            dragSkipAnswerButtonBy(if (edge == "left") -viewport.width else viewport.width)
            composeRule.waitUntil(timeoutMillis = 5_000) {
                preferences.getString("$placementKey-edge", "") == edge &&
                    button.fetchSemanticsNode().boundsInRoot.let { bounds ->
                        abs((if (edge == "left") bounds.left else bounds.right) - snappedEdge) <= 1f
                    }
            }
            val node = button.assertIsDisplayed().fetchSemanticsNode()
            val bounds = Rect(node.positionInRoot, node.size.toSize())
            assertTrue(
                "拖到 $edge 后按钮须完整位于所属视口：$bounds；$viewport",
                bounds.width > 0f &&
                    bounds.height > 0f &&
                    viewport.contains(bounds.topLeft) &&
                    bounds.right <= viewport.right &&
                    bounds.bottom <= viewport.bottom,
            )
            assertEquals(
                "按钮须吸附实际 $edge 边缘并留出边距",
                snappedEdge,
                if (edge == "left") bounds.left else bounds.right,
                1f,
            )
            assertEquals(edge, preferences.getString("$placementKey-edge", ""))
            assertTrue("拖动须保存有效相对高度", preferences.getFloat("$placementKey-yFraction", Float.NaN) in 0f..1f)
            assertEquals(
                "拖动不得新写 legacy x/y",
                legacyPositions,
                preferences.all.filterKeys { it == "buttonSkipAnswer-x" || it == "buttonSkipAnswer-y" },
            )
        }
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
            viewModel.content = (1..20).joinToString("\n\n") { index ->
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

    private fun waitForReadingQueue(expectedIds: List<Long>) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            AndroidReadingPlayerBridge.state.value.queue
                .map(ReadingQueueItem::id) == expectedIds
        }
        assertEquals(
            expectedIds,
            AndroidReadingPlayerBridge.state.value.queue
                .map(ReadingQueueItem::id),
        )
    }

    private fun dragSkipAnswerButtonBy(deltaX: Float) {
        val buttonNode = composeRule.onNodeWithContentDescription("下一个回答").assertIsDisplayed().fetchSemanticsNode()
        val viewport = composeRule
            .onNodeWithTag("article_screen_root")
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot
        val root = composeRule.onRoot().fetchSemanticsNode()
        val buttonCenter = buttonNode.positionInRoot + Offset(buttonNode.size.width / 2f, buttonNode.size.height / 2f)
        val start = buttonCenter - root.positionInRoot
        val endX = (buttonCenter.x + deltaX).coerceIn(viewport.left + 1f, viewport.right - 1f)
        val end = Offset(endX, buttonCenter.y) - root.positionInRoot
        assertTrue("拖动起终点必须在实际视口内", viewport.contains(buttonCenter) && viewport.contains(end + root.positionInRoot))
        composeRule.onRoot().performTouchInput {
            down(start)
            repeat(10) { step -> moveTo(start + (end - start) * ((step + 1) / 10f), delayMillis = 50) }
            up()
        }
        composeRule.waitForIdle()
    }

    private fun issue495ViewModel(): ArticleViewModel {
        val viewModel = seededAnswerViewModel(ANSWER)
        val html = InstrumentationRegistry
            .getInstrumentation()
            .context
            .assets
            .open("issue-495-answer.html")
            .bufferedReader()
            .use { it.readText() }
        composeRule.activity.runOnUiThread {
            viewModel.content = html
        }
        return viewModel
    }

    private fun seededAnswerViewModel(article: Article): ArticleViewModel {
        val viewModel = ArticleViewModel(
            article = article,
            httpClient = null,
        )
        composeRule.activity.runOnUiThread {
            viewModel.title = "离线 Answer 标题"
            viewModel.authorName = "离线答主"
            viewModel.authorId = "offline-answer-author-id"
            viewModel.authorUrlToken = "offline-answer-author"
            viewModel.content = (1..20).joinToString("\n\n") { index ->
                "第 $index 段离线回答正文，用于 ArticleScreen instrumented test。"
            }
            viewModel.voteUpCount = 42
            viewModel.commentCount = 7
            viewModel.questionId = 123456L
            viewModel.createdAt = 1_710_000_000L
            viewModel.updatedAt = 1_710_000_600L
            viewModel.ipInfo = "上海"
            viewModel.endorsements = listOf(
                DataHolder.AnswerEndorsementDisplay(
                    text = "话题收录 我的开源名片",
                    backgroundColor = DataHolder.AnswerEndorsementColor(alpha = 0.1f, group = "GYL02A"),
                    textColor = DataHolder.AnswerEndorsementColor(group = "GYL02A"),
                    leadingIconKey = "zhicon_icon_24_chat_bubble_hash_fill",
                    leadingIconColor = DataHolder.AnswerEndorsementColor(group = "GYL02A"),
                    trailingIconKey = "zhicon_icon_16_arrow_right",
                ),
                DataHolder.AnswerEndorsementDisplay(
                    text = "创作声明: 内容包含剧透",
                    backgroundColor = DataHolder.AnswerEndorsementColor(alpha = 0.1f, group = "GBL01A"),
                    textColor = DataHolder.AnswerEndorsementColor(group = "GBL07A"),
                    trailingIconKey = "zhicon_icon_16_arrow_down",
                ),
                DataHolder.AnswerEndorsementDisplay(
                    text = "收录于话题: 科技",
                ),
            )
        }
        return viewModel
    }

    private companion object {
        const val FULL_ORIGIN_SOURCE_ID = "instrumented:reading-origin"
        const val PARTIAL_ORIGIN_SOURCE_ID = "instrumented:partial-reading-origin"
        const val ISSUE_495_BENCHMARK_TAG = "Issue495Benchmark"
        const val ISSUE_495_FIRST_FRAME_LIMIT_MS = 5_000L
        const val HIGHLIGHTED_PARAGRAPH =
            "目前灰度机制是在OpenCode上，被选中的账号调用deepseek-v4-pro或deepseek-v4-flash有机会拿到GA版。"
        const val HIGHLIGHT_SELECTION_TARGET = "后续普通段落用于验证拖动手柄跨越文字块。"
        const val SPANNING_HIGHLIGHT_FIRST = "第一段跨段划线内容。"
        const val SPANNING_HIGHLIGHT_SECOND = "第二段跨段划线内容。"
        const val FORMATTED_HIGHLIGHT_PREFIX = "WWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWW"
        const val FORMATTED_HIGHLIGHT = "划线命中"
        const val FORMATTED_HIGHLIGHT_PARAGRAPH = "$FORMATTED_HIGHLIGHT_PREFIX$FORMATTED_HIGHLIGHT 后缀"
        const val WRAPPED_HIGHLIGHT_PREFIX = "普通前缀 "
        const val WRAPPED_HIGHLIGHT =
            "这是位于段落中间并且需要跨越多个视觉行的划线内容，用于验证每一行都能完整绘制虚线。"
        const val WRAPPED_HIGHLIGHT_SUFFIX = " 普通后缀"
        const val WRAPPED_HIGHLIGHT_PARAGRAPH =
            "$WRAPPED_HIGHLIGHT_PREFIX$WRAPPED_HIGHLIGHT$WRAPPED_HIGHLIGHT_SUFFIX"
        val HIGHLIGHTED_PARAGRAPH_HTML =
            """
            <p data-pid="WGd4cbq-"><span class="highlight-wrap other has-comments"
                data-highlight-id="2063081895399788604"
                data-highlight-like-count="9"
                data-highlight-comment-count="2"
                data-highlight-my-comment-count="0"
                data-highlight-is-like="false"
                data-highlight-is-span="false"
                data-highlight-content-id="2062174868112676264"
                data-highlight-content-type="answer"
                data-highlight-pid="WGd4cbq-"
                data-highlight-start-offset="0"
                data-highlight-end-offset="68">$HIGHLIGHTED_PARAGRAPH</span></p>
            """.trimIndent()
        val SPANNING_HIGHLIGHT_HTML =
            """
            <p data-pid="first"><span class="highlight-wrap other has-comments"
                data-highlight-id="shared-segment"
                data-highlight-like-count="806"
                data-highlight-comment-count="15"
                data-highlight-is-span="true"
                data-highlight-display-text="$SPANNING_HIGHLIGHT_FIRST&#10;&#10;$SPANNING_HIGHLIGHT_SECOND"
                data-highlight-content-id="1907864533831225689"
                data-highlight-content-type="answer"
                data-highlight-pid="first"
                data-highlight-start-offset="0"
                data-highlight-end-offset="${SPANNING_HIGHLIGHT_FIRST.length}">$SPANNING_HIGHLIGHT_FIRST</span></p>
            <p data-pid="second"><span class="highlight-wrap other has-comments"
                data-highlight-id="shared-segment"
                data-highlight-like-count="806"
                data-highlight-comment-count="15"
                data-highlight-is-span="true"
                data-highlight-content-id="1907864533831225689"
                data-highlight-content-type="answer"
                data-highlight-pid="second"
                data-highlight-start-offset="0"
                data-highlight-end-offset="${SPANNING_HIGHLIGHT_SECOND.length}">$SPANNING_HIGHLIGHT_SECOND</span></p>
            """.trimIndent()
        val FORMATTED_HIGHLIGHT_PARAGRAPH_HTML =
            """
            <p><strong>$FORMATTED_HIGHLIGHT_PREFIX</strong><span class="highlight-wrap other has-comments"
                data-highlight-id="formatted-highlight"
                data-highlight-like-count="1"
                data-highlight-comment-count="1"
                data-highlight-content-id="777"
                data-highlight-content-type="answer">$FORMATTED_HIGHLIGHT</span> 后缀</p>
            """.trimIndent()
        val WRAPPED_HIGHLIGHT_PARAGRAPH_HTML =
            """
            <p>$WRAPPED_HIGHLIGHT_PREFIX<span class="highlight-wrap other has-comments"
                data-highlight-id="wrapped-highlight"
                data-highlight-like-count="1"
                data-highlight-comment-count="1"
                data-highlight-content-id="778"
                data-highlight-content-type="answer">$WRAPPED_HIGHLIGHT</span>$WRAPPED_HIGHLIGHT_SUFFIX</p>
            """.trimIndent()

        val ARTICLE = Article(
            type = ArticleType.Article,
            id = 777L,
            title = "离线 Article 标题",
        )
        val ANSWER = Article(
            type = ArticleType.Answer,
            id = 777L,
            title = "离线 Answer 标题",
        )
        val NEXT_ANSWER = Article(
            type = ArticleType.Answer,
            id = 778L,
            title = "下一个离线回答",
        )

        val NO_OP_API_ENVIRONMENT = object : ZhihuApiEnvironment {
            override fun httpClient(): HttpClient = error("离线导航器测试不提供 HTTP 客户端")

            override fun authenticatedCookies(): Map<String, String> = emptyMap()

            override suspend fun handleFetchFailure(
                tag: String?,
                error: Exception,
            ) = Unit
        }
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
