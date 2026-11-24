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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.intercept.Interceptor
import com.github.zly2006.zhihu.account.LocalZhihuAccountStore
import com.github.zly2006.zhihu.account.ZhihuAccountProfileSnapshot
import com.github.zly2006.zhihu.account.ZhihuAccountRepository
import com.github.zly2006.zhihu.account.ZhihuAccountSession
import com.github.zly2006.zhihu.account.ZhihuAccountStore
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.navigation.AppNavHost
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.Daily
import com.github.zly2006.zhihu.navigation.Home
import com.github.zly2006.zhihu.navigation.MainTabs
import com.github.zly2006.zhihu.navigation.OnlineHistoryRoute
import com.github.zly2006.zhihu.navigation.Search
import com.github.zly2006.zhihu.navigation.rememberAppNavHost
import com.github.zly2006.zhihu.platform.desktopSettingsStore
import com.github.zly2006.zhihu.theme.ThemeManager
import com.github.zly2006.zhihu.theme.ThemeMode
import com.github.zly2006.zhihu.theme.ThemeStyle
import com.github.zly2006.zhihu.theme.ZhihuTheme
import com.github.zly2006.zhihu.ui.subscreens.FEED_CARD_STYLE_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.subscreens.PAGE_TRANSITION_PREFERENCE_KEY
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.serialization.encodeToString
import top.yukonga.miuix.kmp.nav.core.LocalNavTransitionScope
import top.yukonga.miuix.kmp.nav.transition.NavTransitionScope
import java.nio.file.Files
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.io.files.Path as KotlinxPath

/** 独立 JVM 隔离 user.home、ThemeManager 和 Coil 全局状态。 */
@OptIn(ExperimentalTestApi::class, coil3.annotation.DelicateCoilApi::class)
class PageTransitionsUiTest {
    @Test
    fun liveSettingsMoveSearchAndDefaultAnswerOnTheSelectedAxisAndDisableWithoutAnExitOverlay() {
        val originalHome = System.getProperty("user.home")
        val originalTheme = ThemeManager.snapshot()
        val directory = Files.createTempDirectory("zhihu-page-transitions-")
        val profile = ZhihuAccountProfileSnapshot(
            id = "page-transitions-fixture",
            name = "转场测试用户",
            urlToken = "page-transitions-fixture",
            userType = "people",
            avatarUrl = "",
        )
        val session = ZhihuAccountSession(
            login = true,
            username = profile.name,
            cookies = mutableMapOf("d_c0" to "fixture-device", "z_c0" to "fixture-session"),
            profile = profile,
        )
        val engine = MockEngine { request ->
            check(request.method == HttpMethod.Get) { "UI 夹具不能提交数据" }
            val body = when (request.url.encodedPath) {
                "/api/v4/me" -> ZhihuJson.json.encodeToString(profile)
                "/api/4/stories/latest" -> """{"date":"20261110","stories":[]}"""
                "/unify-consumption/read_history" -> """{"data":[{"card_type":"read_history","data":{"header":{"icon":"","title":"转场测试回答"},"action":{"type":"router","url":"zhihu://answers/1001"},"extra":{"content_token":"1001","content_type":"answer","read_time":1716120000,"question_token":"456"}}}],"paging":{"is_end":true,"next":""}}"""
                else -> """{"data":[],"paging":{"is_end":true,"next":""}}"""
            }
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val imageLoader = ImageLoader
            .Builder(PlatformContext.INSTANCE)
            .diskCache(null)
            .components {
                add(Interceptor { error("隔离的 UI 测试禁止图片网络访问") })
            }.build()
        var fixtureStore: ZhihuAccountStore? = null
        val backOwner = object : NavigationEventDispatcherOwner {
            override val navigationEventDispatcher = NavigationEventDispatcher(onBackCompletedFallback = {})
        }
        try {
            System.setProperty("user.home", directory.toString())
            SingletonImageLoader.setUnsafe(imageLoader)
            val accountFile = directory.resolve("fixture-account.json")
            Files.writeString(accountFile, ZhihuJson.json.encodeToString(session))
            val store = ZhihuAccountStore(ZhihuAccountRepository(KotlinxPath(accountFile.toString())), engine = engine)
            fixtureStore = store
            check(store.session.profile?.id == profile.id) { "请使用新的独立 JVM 测试进程" }
            val settings = desktopSettingsStore().apply {
                putBoolean("enableContentFilter", false)
                putBoolean("allowTelemetry", false)
                putBoolean("floatingBottomBar", false)
                putBoolean("showSearchHotSearch", false)
                putBoolean("showSearchHistory", false)
                putBoolean("enable_predictive_back", true)
                putString(PAGE_TRANSITION_PREFERENCE_KEY, "miuix")
            }
            settings.putString("themeMode", ThemeMode.LIGHT.name)
            var host: AppNavHost? = null
            var articleInstance: Any? = null
            var articleScope: NavTransitionScope? = null
            val articleColor = Color(0xFF245BCC)
            runDesktopComposeUiTest(width = 412, height = 892) {
                setContent {
                    CompositionLocalProvider(
                        LocalZhihuAccountStore provides store,
                        LocalNavigationEventDispatcherOwner provides backOwner,
                    ) {
                        ZhihuTheme {
                            val navHost = rememberAppNavHost(MainTabs)
                            host = navHost
                            ZhihuMain(
                                navHost = navHost,
                                mainTabNavigationTarget = null,
                                navigate = navHost::open,
                                setCurrentMainTabOpenFrom = {},
                                consumeMainTabNavigationTarget = {},
                                preferenceState = remember {
                                    ZhihuMainPreferenceState {
                                        ZhihuMainPreferenceSnapshot(
                                            tapToScrollToTopEnabled = false,
                                            autoHideBottomBar = false,
                                            landscapeListDetailEnabled = false,
                                            selectedBottomBarItemKeys = listOf(Home.name, Daily.name),
                                            startDestination = Daily,
                                        )
                                    }
                                },
                                isDarkTheme = false,
                                articleContent = {
                                    articleInstance = remember { Any() }
                                    articleScope = LocalNavTransitionScope.current
                                    Box(Modifier.fillMaxSize().background(articleColor).testTag("transition_article"))
                                },
                            )
                        }
                    }
                }
                onNodeWithTag("nav_tab_daily").assertIsSelected()
                val rootEntry = host!!.pageEntries.first()
                val rootBounds = onNodeWithTag("main_tabs_surface").fetchSemanticsNode().boundsInRoot
                val rootTabBounds = onNodeWithTag("nav_tab_daily").fetchSemanticsNode().boundsInRoot
                val routes = listOf(Search(query = "") to "search_input", Article(type = ArticleType.Answer, id = 99111) to "transition_article")
                val endpoints = mutableMapOf<String, Rect>()
                mainClock.autoAdvance = false
                for (preset in listOf("miuix", "aosp")) {
                    runOnIdle { settings.putString(PAGE_TRANSITION_PREFERENCE_KEY, preset) }
                    mainClock.advanceTimeByFrame()
                    waitForIdle()
                    for ((route, tag) in routes) {
                        runOnIdle { host!!.open(route) }
                        val frames = mutableListOf<Offset>()
                        var rootFrames = 0
                        /** 暂停时钟后仍须推进组合启动，才能采到动画帧。 */
                        repeat(12) {
                            mainClock.advanceTimeByFrame()
                            waitForIdle()
                            onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().singleOrNull()?.let {
                                frames += it.positionInRoot
                            }
                            if (preset == "aosp") {
                                onAllNodesWithTag("main_tabs_surface").fetchSemanticsNodes().singleOrNull()?.let {
                                    rootFrames++
                                    assertEquals(rootBounds, it.boundsInRoot, "覆盖来源页时不能改变其完整高度")
                                    assertEquals(rootTabBounds, onNodeWithTag("nav_tab_daily").fetchSemanticsNode().boundsInRoot, "底栏必须随原条目保留")
                                    onNodeWithTag("nav_tab_daily").assertIsSelected()
                                }
                            }
                        }
                        if (preset == "aosp") assertTrue(rootFrames > 0, "入场期间未绘制带底栏的来源页")
                        mainClock.advanceTimeBy(1_000)
                        waitForIdle()
                        val endpoint = onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
                        endpoints[tag] = endpoint.boundsInRoot
                        val movement = frames.map { it - endpoint.positionInRoot }
                        assertTrue(frames.zipWithNext().any { (a, b) -> (a - b).getDistance() > 1f }, "$tag/$preset 未渲染出任何移动帧")
                        assertTrue(
                            movement.any {
                                if (preset == "miuix") {
                                    it.x > 412 * 0.2f && abs(it.x) > abs(it.y) * 3f
                                } else {
                                    it.x > 0f && abs(it.x) > abs(it.y) * 3f
                                }
                            },
                            "$tag/$preset 必须沿所选轴入场，不能固定使用详情淡入或回答转场：$movement",
                        )
                        if (preset == "aosp") {
                            // 程序触发的前进只做有限位移与淡入，不能退化为全宽侧滑。
                            assertTrue(
                                movement.maxOf { abs(it.x) } < 412f * 0.6f,
                                "AOSP push 必须是有限位移，不能是全宽侧滑：$movement",
                            )
                        }
                        val entry = host!!.pageEntries.last()
                        val instance = articleInstance
                        val otherPreset = if (preset == "miuix") "aosp" else "miuix"
                        runOnIdle { settings.putString(PAGE_TRANSITION_PREFERENCE_KEY, otherPreset) }
                        mainClock.advanceTimeByFrame()
                        waitForIdle()
                        assertEquals(entry.id, host!!.topEntryId)
                        assertSame(entry, host!!.pageEntries.last(), "切换设置不能替换当前页面条目")
                        if (route is Article) assertSame(instance, articleInstance, "切换设置不能重新创建回答内容")
                        runOnIdle { host!!.back() }
                        mainClock.advanceTimeBy(1_000)
                        waitForIdle()
                        assertEquals(rootEntry.id, host!!.topEntryId)
                        assertSame(rootEntry, host!!.pageEntries.first())
                        runOnIdle { settings.putString(PAGE_TRANSITION_PREFERENCE_KEY, preset) }
                        mainClock.advanceTimeByFrame()
                        waitForIdle()
                    }
                }
                runOnIdle { host!!.open(Search(query = "多级返回")) }
                mainClock.advanceTimeBy(1_000)
                waitForIdle()
                val searchEntry = host!!.pageEntries.last()
                val searchBounds = onNodeWithTag("search_input", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                runOnIdle { host!!.open(Article(type = ArticleType.Answer, id = 99112)) }
                mainClock.advanceTimeBy(1_000)
                waitForIdle()
                val fullArticle = onNodeWithTag("transition_article").fetchSemanticsNode().boundsInRoot
                var solidFrames = 0
                var fadingFrames = 0
                runOnIdle { host!!.back() }
                repeat(18) {
                    mainClock.advanceTimeByFrame()
                    waitForIdle()
                    assertSame(searchEntry, host!!.pageEntries.last(), "多级返回必须露出上一层原条目")
                    assertEquals(searchBounds, onNodeWithTag("search_input", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot)
                    val progress = -(articleScope?.relativeDepth ?: 0f)
                    val outgoing = onAllNodesWithTag("transition_article").fetchSemanticsNodes().singleOrNull()
                    if (outgoing != null && progress in 0.05f..0.85f) {
                        assertEquals(fullArticle.width.toInt(), outgoing.size.width, "普通返回不缩放正文")
                        assertTrue(outgoing.positionInRoot.x > fullArticle.left && outgoing.positionInRoot.x <= fullArticle.left + 48f)
                        val pixels = onRoot().captureToImage().toPixelMap()
                        val pixel = pixels[fullArticle.center.x.toInt(), fullArticle.center.y.toInt()]
                        when {
                            progress <= 0.4f -> {
                                solidFrames++
                                assertEquals(articleColor, pixel, "普通返回前段仍须绘出完整、不透明的退场正文")
                            }
                            progress >= 0.65f -> {
                                fadingFrames++
                                assertTrue(pixel != articleColor, "普通返回后段才淡出，不能一直不透明再直切")
                            }
                        }
                    }
                }
                assertTrue(solidFrames >= 2, "普通返回至少应绘出两个前段位移帧")
                assertTrue(fadingFrames >= 2, "普通返回至少应绘出两个后段淡出帧")
                mainClock.advanceTimeBy(1_000)
                waitForIdle()
                runOnIdle { host!!.back() }
                repeat(6) {
                    mainClock.advanceTimeByFrame()
                    waitForIdle()
                    onNodeWithTag("nav_tab_daily").assertIsSelected()
                    assertEquals(rootTabBounds, onNodeWithTag("nav_tab_daily").fetchSemanticsNode().boundsInRoot)
                }
                mainClock.advanceTimeBy(1_000)
                waitForIdle()
                assertSame(rootEntry, host!!.pageEntries.first())
                runOnIdle { settings.putBoolean("enable_predictive_back", false) }
                mainClock.advanceTimeByFrame()
                waitForIdle()
                for ((index, taggedRoute) in routes.withIndex()) {
                    val (route, tag) = taggedRoute
                    runOnIdle { host!!.open(route) }
                    mainClock.advanceTimeByFrame()
                    waitForIdle()
                    mainClock.advanceTimeByFrame()
                    waitForIdle()
                    val bounds = onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                    val endpoint = endpoints.getValue(tag)
                    assertTrue(
                        (bounds.topLeft - endpoint.topLeft).getDistance() <= 1f && (bounds.bottomRight - endpoint.bottomRight).getDistance() <= 1f,
                        "禁用转场后下一帧必须处于最终位置和尺寸",
                    )
                    runOnIdle { host!!.back() }
                    mainClock.advanceTimeByFrame()
                    waitForIdle()
                    mainClock.advanceTimeByFrame()
                    waitForIdle()
                    assertTrue(onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isEmpty(), "禁用转场后必须卸载退场页面")
                    val tab = if (index == 0) "nav_tab_home" else "nav_tab_daily"
                    /** 语义点击绕过触摸分发，无法发现退场页拦截根页面输入。 */
                    onNodeWithTag(tab).performTouchInput { click() }
                    mainClock.advanceTimeByFrame()
                    waitForIdle()
                    onNodeWithTag(tab).assertIsSelected()
                    assertEquals(rootEntry.id, host!!.topEntryId)
                    assertSame(rootEntry, host!!.pageEntries.first())
                }
            }
            settings.putBoolean("enable_predictive_back", true)
            settings.putString(PAGE_TRANSITION_PREFERENCE_KEY, "aosp")
            settings.putString(FEED_CARD_STYLE_PREFERENCE_KEY, "divider")
            for (style in ThemeStyle.entries) {
                for (mainStack in listOf(false, true)) {
                    settings.putString("uiThemeStyle", style.name)
                    var paneHost: AppNavHost? = null
                    var topScope: NavTransitionScope? = null
                    val paneDirection = mutableStateOf(LayoutDirection.Ltr)
                    var articleScroll: ScrollState? = null
                    var articleClicks = 0
                    var dividerColor = Color.Unspecified
                    settings.putFloat(LANDSCAPE_LIST_PANE_RATIO_KEY, DEFAULT_LIST_PANE_RATIO)
                    val paneBackOwner = object : NavigationEventDispatcherOwner {
                        override val navigationEventDispatcher = NavigationEventDispatcher(onBackCompletedFallback = {})
                    }
                    val topColor = Color(0xFF245BCC)
                    try {
                        runDesktopComposeUiTest(width = 1200, height = 800) {
                            setContent {
                                CompositionLocalProvider(
                                    LocalZhihuAccountStore provides store,
                                    LocalNavigationEventDispatcherOwner provides paneBackOwner,
                                    LocalLayoutDirection provides paneDirection.value,
                                ) {
                                    ZhihuTheme {
                                        dividerColor = MaterialTheme.colorScheme.outlineVariant
                                        val navHost = rememberAppNavHost(MainTabs)
                                        host = navHost
                                        ZhihuMain(
                                            navHost = navHost,
                                            mainTabNavigationTarget = null,
                                            navigate = navHost::open,
                                            navigateContent = { destination, target -> target.open(destination) },
                                            enableLandscapeListDetail = true,
                                            setCurrentMainTabOpenFrom = {},
                                            consumeMainTabNavigationTarget = {},
                                            preferenceState = remember {
                                                ZhihuMainPreferenceState {
                                                    ZhihuMainPreferenceSnapshot(
                                                        tapToScrollToTopEnabled = false,
                                                        autoHideBottomBar = false,
                                                        landscapeListDetailEnabled = true,
                                                        selectedBottomBarItemKeys = listOf(Home.name, Daily.name),
                                                        startDestination = Daily,
                                                    )
                                                }
                                            },
                                            isDarkTheme = false,
                                            articleContent = { article ->
                                                paneHost = LocalArticleNavHost.current
                                                val scroll = rememberScrollState()
                                                if (article.id == 1002L) {
                                                    topScope = LocalNavTransitionScope.current
                                                    articleScroll = scroll
                                                }
                                                Box(
                                                    Modifier
                                                        .fillMaxSize()
                                                        .background(if (article.id == 1002L) topColor else Color(0xFF55AA77))
                                                        .testTag("pane_article_${article.id}")
                                                        .verticalScroll(scroll)
                                                        .clickable(interactionSource = null, indication = null) { articleClicks++ },
                                                ) {
                                                    Box(Modifier.fillMaxWidth().height(1600.dp))
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                            runOnIdle { host!!.open(OnlineHistoryRoute) }
                            waitUntil(timeoutMillis = 5_000) {
                                onAllNodesWithTag("online_history_list").fetchSemanticsNodes().isNotEmpty()
                            }
                            if (!mainStack) {
                                val leftPaneWidth = onNodeWithTag("list_detail_divider_line", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.left
                                onRoot().performTouchInput {
                                    down(Offset(8f, 450f))
                                    moveTo(Offset(leftPaneWidth * 0.65f, 450f), delayMillis = 400)
                                    repeat(3) { moveTo(Offset(leftPaneWidth * 0.65f, 450f), delayMillis = 100) }
                                    up()
                                }
                                waitForIdle()
                                assertEquals(MainTabs, host!!.top, "左栏超过半栏宽的无速度手势必须返回，不能按全窗口宽判取消")
                                runOnIdle { host!!.open(OnlineHistoryRoute) }
                                waitUntil(timeoutMillis = 5_000) {
                                    onAllNodesWithTag("online_history_list").fetchSemanticsNodes().isNotEmpty()
                                }
                            }
                            if (mainStack) {
                                runOnIdle { host!!.open(Article(type = ArticleType.Answer, id = 1001)) }
                            } else {
                                onNodeWithText("转场测试回答", substring = true).performClick()
                            }
                            waitForIdle()
                            val rightHost = checkNotNull(paneHost)
                            assertEquals(mainStack, rightHost === host, "$style 应命中指定的双栏分支")
                            runOnIdle { rightHost.open(Article(type = ArticleType.Answer, id = 1002)) }
                            waitForIdle()
                            val entryId = rightHost.topEntryId
                            for (direction in LayoutDirection.entries) {
                                runOnIdle { paneDirection.value = direction }
                                val rtl = direction == LayoutDirection.Rtl
                                val sign = if (rtl) -1f else 1f
                                val list = onNodeWithTag("online_history_list").fetchSemanticsNode().boundsInRoot
                                val article = onNodeWithTag("pane_article_1002").fetchSemanticsNode().boundsInRoot
                                val line = onNodeWithTag("list_detail_divider_line", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                                val hit = onNodeWithTag("list_detail_divider").fetchSemanticsNode().boundsInRoot
                                assertEquals(with(density) { 1.dp.roundToPx().toFloat() }, line.width, 0.01f)
                                assertEquals(with(density) { 16.dp.roundToPx().toFloat() }, hit.width, 0.01f)
                                assertEquals(line.center.x, hit.center.x, 0.5f, "宽命中区必须对称覆盖细线两侧")
                                assertEquals(if (rtl) list.left else list.right, if (rtl) line.right else line.left, 0.01f, "列表横线必须直接接上竖线")
                                assertEquals(if (rtl) article.right else article.left, if (rtl) line.left else line.right, 0.01f, "不能将空带推向正文")
                                for (side in listOf(-1f, 1f)) {
                                    val beforeWidth = onNodeWithTag("online_history_list").fetchSemanticsNode().boundsInRoot.width
                                    val handle = onNodeWithTag("list_detail_divider").fetchSemanticsNode().boundsInRoot
                                    val start = Offset(handle.center.x + side * 4f, handle.center.y)
                                    onRoot().performTouchInput {
                                        down(start)
                                        moveTo(start + Offset(-side * sign * 60f, 0f), delayMillis = 300)
                                        up()
                                    }
                                    waitForIdle()
                                    val afterWidth = onNodeWithTag("online_history_list").fetchSemanticsNode().boundsInRoot.width
                                    assertTrue((afterWidth - beforeWidth) * -side > 20f, "$style/$mainStack/$direction 两侧命中区均须能拖拽")
                                    val usableWidth = onRoot().fetchSemanticsNode().boundsInRoot.width - line.width
                                    assertEquals(afterWidth / usableWidth, settings.getFloat(LANDSCAPE_LIST_PANE_RATIO_KEY, 0f), 0.002f, "拖动结束必须保存可见比例")
                                    assertEquals(entryId, rightHost.topEntryId, "分栏拖动不能触发正文返回")
                                }
                                val handle = onNodeWithTag("list_detail_divider").fetchSemanticsNode().boundsInRoot
                                val contentPoint = Offset(handle.center.x + sign * 4f, handle.center.y)
                                val clicksBefore = articleClicks
                                onRoot().performTouchInput { click(contentPoint) }
                                runOnIdle { assertEquals(clicksBefore + 1, articleClicks, "透明命中区不能吞正文点击") }
                                val scrollBefore = articleScroll!!.value
                                onRoot().performTouchInput {
                                    down(contentPoint)
                                    moveTo(contentPoint - Offset(0f, 120f), delayMillis = 400)
                                    up()
                                }
                                runOnIdle { assertTrue(articleScroll!!.value > scrollBefore, "透明命中区不能吞竖向滚动") }
                                val savedRatio = settings.getFloat(LANDSCAPE_LIST_PANE_RATIO_KEY, 0f)
                                onNodeWithTag("list_detail_divider").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
                                onNodeWithTag("list_detail_divider").performKeyInput { pressKey(if (rtl) Key.DirectionLeft else Key.DirectionRight) }
                                assertEquals(savedRatio + 0.05f, settings.getFloat(LANDSCAPE_LIST_PANE_RATIO_KEY, 0f), 0.002f)
                                val decrease = onNodeWithTag("list_detail_divider")
                                    .fetchSemanticsNode()
                                    .config[SemanticsActions.CustomActions]
                                    .single { it.label == "减小列表宽度" }
                                runOnIdle { assertTrue(decrease.action()) }
                                assertEquals(savedRatio, settings.getFloat(LANDSCAPE_LIST_PANE_RATIO_KEY, 0f), 0.002f)
                                assertEquals(entryId, rightHost.topEntryId)
                            }
                            runOnIdle { paneDirection.value = LayoutDirection.Ltr }
                            val right = onNodeWithTag("pane_article_1002").fetchSemanticsNode().boundsInRoot
                            val left = onNodeWithTag("online_history_list").fetchSemanticsNode().boundsInRoot
                            val before = onRoot().captureToImage().toPixelMap()
                            val line = onNodeWithTag("list_detail_divider_line", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                            val dividerX = line.left.toInt()
                            assertEquals(dividerColor, before[dividerX, left.center.y.toInt()], "$style 竖线须使用该皮肤的分隔色")
                            val card = onNode(
                                hasClickAction() and hasAnyAncestor(hasTestTag("online_history_list")) and
                                    hasAnyDescendant(hasText("转场测试回答", substring = true)),
                                useUnmergedTree = true,
                            ).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                            assertEquals(line.left, card.right, 0.01f, "卡片必须与竖线接合")
                            assertTrue(card.bottom <= left.bottom, "横线须实际位于列表视口内")
                            val lineY = card.bottom.toInt() - 1
                            val horizontalColor = before[dividerX - 12, lineY].toArgb()
                            assertTrue(horizontalColor != before[dividerX - 12, lineY - 2].toArgb(), "取样行必须是横线而非卡片背景")
                            assertTrue(
                                (dividerX - 12 until dividerX).all { x -> before[x, lineY].toArgb() == horizontalColor },
                                "$style/$mainStack 信息流横线与竖线之间不能存在空带",
                            )
                            assertEquals(topColor, before[line.right.toInt(), right.center.y.toInt()], "竖线后应立即绘制正文，不得用背景覆盖触摸带")
                            mainClock.autoAdvance = false
                            onRoot().performTouchInput {
                                down(Offset(right.left + 8f, right.center.y))
                                moveTo(Offset(right.left + right.width * 0.5f, right.center.y), delayMillis = 180)
                            }
                            mainClock.advanceTimeBy(120)
                            waitForIdle()
                            waitUntil("右栏必须接收侧滑返回进度", timeoutMillis = 5_000) {
                                topScope?.gesture != null && (topScope?.relativeDepth ?: 0f) < -0.1f
                            }
                            val movingNode = onNodeWithTag("pane_article_1002")
                            val moving = movingNode.fetchSemanticsNode().boundsInRoot
                            val uncut = movingNode.getUnclippedBoundsInRoot()
                            assertEquals(right.width, with(density) { (uncut.right - uncut.left).toPx() }, 1f, "手势不改变页面测量宽度")
                            val scale = moving.height / right.height
                            assertTrue(scale in 0.939f..0.99f, "$style/$mainStack 手势只轻缩当前页，不能缩到94%以下：$right → $moving")
                            assertEquals(right.right, moving.right, 1f, "移出部分应按原栏位裁剪，不能越过视口")
                            assertEquals((1f - scale) * right.height / 2f, moving.top - right.top, 1f, "缩放中心须在当前栏内")
                            assertEquals(moving.top - right.top, right.bottom - moving.bottom, 1f, "上下露出同一份原页面")
                            assertTrue(moving.left > right.left && moving.left - right.left <= 56f + right.width * 0.03f, "$style/$mainStack 按右栏短距离跟手：$right → $moving")
                            assertEquals(left, onNodeWithTag("online_history_list").fetchSemanticsNode().boundsInRoot)
                            val mid = onRoot().captureToImage().toPixelMap()
                            val x = moving.left.toInt() + 2
                            val y = moving.top.toInt() + 2
                            assertTrue(mid[x, y] != topColor, "$style/$mainStack 轻卡片必须有真实裁剪圆角")
                            assertEquals(topColor, mid[moving.center.x.toInt(), y], "$style/$mainStack 卡片顶边内仍须绘出正文")
                            assertEquals(topColor, mid[moving.center.x.toInt(), moving.center.y.toInt()], "手势拖动期间当前页不淡出")
                            for (py in left.top.toInt() + 10 until left.bottom.toInt() - 10 step 13) {
                                for (px in left.left.toInt() + 10 until left.right.toInt() - 10 step 13) {
                                    assertEquals(before[px, py], mid[px, py], "$style/$mainStack 右栏返回不能改变左栏像素 ($px,$py)")
                                }
                            }
                            onRoot().performTouchInput {
                                moveTo(Offset(right.left + 8f, right.center.y), delayMillis = 200)
                                repeat(3) { moveTo(Offset(right.left + 8f, right.center.y), delayMillis = 100) }
                                up()
                            }
                            mainClock.autoAdvance = true
                            waitUntil("取消返回后必须停稳", timeoutMillis = 5_000) {
                                topScope?.gesture == null && abs(topScope?.relativeDepth ?: 1f) < 0.01f
                            }
                            waitForIdle()
                            assertEquals(entryId, rightHost.topEntryId, "取消返回不能移除条目")
                            assertEquals(right, onNodeWithTag("pane_article_1002").fetchSemanticsNode().boundsInRoot)
                            val scrollAtRelease = articleScroll!!.value
                            val previousEntry = rightHost.pageEntries
                                .toList()
                                .dropLast(1)
                                .last()
                            mainClock.autoAdvance = false
                            onRoot().performTouchInput {
                                down(Offset(right.left + 8f, right.center.y))
                                moveTo(Offset(right.left + right.width * 0.8f, right.center.y), delayMillis = 180)
                            }
                            mainClock.advanceTimeByFrame()
                            waitForIdle()
                            val releaseBounds = onNodeWithTag("pane_article_1002").fetchSemanticsNode().boundsInRoot
                            onRoot().performTouchInput { up() }
                            mainClock.advanceTimeByFrame()
                            waitForIdle()
                            val firstCommit = onNodeWithTag("pane_article_1002").fetchSemanticsNode().boundsInRoot
                            assertEquals(releaseBounds.top, firstCommit.top, 1f, "提交首帧保持释放时的缩放和圆角位置")
                            assertEquals(releaseBounds.bottom, firstCommit.bottom, 1f)
                            assertTrue(firstCommit.left >= releaseBounds.left - 1f && firstCommit.left < releaseBounds.left + right.width * 0.25f, "松手不能跳到栏外")
                            assertEquals(scrollAtRelease, articleScroll!!.value, "提交过程中不能重置正文滚动")
                            mainClock.autoAdvance = true
                            waitUntil("完成返回后必须移除顶层", timeoutMillis = 5_000) { (rightHost.top as Article).id == 1001L }
                            assertSame(previousEntry, rightHost.pageEntries.last(), "完成手势只退一层并保留原页面")
                            waitForIdle()
                            assertEquals(1001L, (rightHost.top as Article).id)
                            onNodeWithTag("pane_article_1002").assertDoesNotExist()
                            assertEquals(left, onNodeWithTag("online_history_list").fetchSemanticsNode().boundsInRoot)
                            if (mainStack) {
                                val first = onNodeWithTag("pane_article_1001").fetchSemanticsNode().boundsInRoot
                                val lastBefore = onRoot().captureToImage().toPixelMap()
                                mainClock.autoAdvance = false
                                runOnIdle { rightHost.back() }
                                repeat(6) {
                                    mainClock.advanceTimeByFrame()
                                    waitForIdle()
                                    val outgoing = onAllNodesWithTag("pane_article_1001").fetchSemanticsNodes().singleOrNull()
                                    if (outgoing != null) {
                                        assertEquals(first.width.toInt(), outgoing.size.width, "最后回答退场的测量宽度不能变成全宽")
                                        assertTrue(outgoing.boundsInRoot.left >= first.left, "最后回答退场不能移入左栏")
                                    }
                                    assertEquals(left, onNodeWithTag("online_history_list").fetchSemanticsNode().boundsInRoot)
                                    val frame = onRoot().captureToImage().toPixelMap()
                                    for (py in left.top.toInt() + 10 until left.bottom.toInt() - 10 step 23) {
                                        for (px in left.left.toInt() + 10 until left.right.toInt() - 10 step 23) {
                                            assertEquals(lastBefore[px, py], frame[px, py], "最后回答退场不能改变来源列表 ($px,$py)")
                                        }
                                    }
                                }
                                mainClock.autoAdvance = true
                                waitForIdle()
                                onNodeWithTag("pane_article_1001").assertDoesNotExist()
                                assertEquals(OnlineHistoryRoute, rightHost.top)
                            }
                            mainClock.autoAdvance = true
                        }
                    } finally {
                        paneBackOwner.navigationEventDispatcher.dispose()
                    }
                }
            }
        } finally {
            backOwner.navigationEventDispatcher.dispose()
            fixtureStore?.close()
            imageLoader.shutdown()
            SingletonImageLoader.reset()
            if (originalHome == null) System.clearProperty("user.home") else System.setProperty("user.home", originalHome)
            ThemeManager.load(originalTheme)
            directory.toFile().deleteRecursively()
        }
    }
}
