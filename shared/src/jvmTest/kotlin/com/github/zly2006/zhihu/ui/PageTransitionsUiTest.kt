/*
 * Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License (version 3 only).
 */

package com.github.zly2006.zhihu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
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
            check(request.method == HttpMethod.Get) { "UI fixture must not submit data" }
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
                add(Interceptor { error("Isolated UI test forbids image network access") })
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
            check(store.session.profile?.id == profile.id) { "Use a fresh isolated JVM worker" }
            val settings = desktopSettingsStore().apply {
                putBoolean("enableContentFilter", false)
                putBoolean("allowTelemetry", false)
                putBoolean("floatingBottomBar", false)
                putBoolean("showSearchHotSearch", false)
                putBoolean("showSearchHistory", false)
                putBoolean("enable_predictive_back", true)
                putString(PAGE_TRANSITION_PREFERENCE_KEY, "miuix")
            }
            ThemeManager.load(originalTheme.copy(themeStyle = ThemeStyle.MATERIAL_EXPRESSIVE, themeMode = ThemeMode.LIGHT))
            var host: AppNavHost? = null
            var articleInstance: Any? = null
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
                                            duo3HomeAccount = false,
                                            tapToScrollToTopEnabled = false,
                                            autoHideBottomBar = false,
                                            collectionDirectBrowseEnabled = false,
                                            landscapeListDetailEnabled = false,
                                            selectedBottomBarItemKeys = listOf(Home.name, Daily.name),
                                            startDestination = Daily,
                                        )
                                    }
                                },
                                isDarkTheme = false,
                                articleContent = {
                                    articleInstance = remember { Any() }
                                    Box(Modifier.fillMaxSize().testTag("transition_article"))
                                },
                            )
                        }
                    }
                }
                onNodeWithTag("nav_tab_daily").assertIsSelected()
                val rootEntry = host!!.pageEntries.first()
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
                        /** 暂停时钟后仍须推进组合启动，才能采到动画帧。 */
                        repeat(12) {
                            mainClock.advanceTimeByFrame()
                            waitForIdle()
                            onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().singleOrNull()?.let {
                                frames += it.positionInRoot
                            }
                        }
                        mainClock.advanceTimeBy(1_000)
                        waitForIdle()
                        val endpoint = onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
                        endpoints[tag] = endpoint.boundsInRoot
                        val movement = frames.map { it - endpoint.positionInRoot }
                        assertTrue(frames.zipWithNext().any { (a, b) -> (a - b).getDistance() > 1f }, "$tag/$preset never rendered a moving frame")
                        assertTrue(
                            movement.any {
                                if (preset == "miuix") {
                                    it.x > 412 * 0.2f && abs(it.x) > abs(it.y) * 3f
                                } else {
                                    it.x > 0f && abs(it.x) > abs(it.y) * 3f
                                }
                            },
                            "$tag/$preset must enter on the selected axis, not a fixed detail fade or answer transition: $movement",
                        )
                        if (preset == "aosp") {
                            // AOSP programmatic push 从 96dp 有限位移入场并淡入，不是 MiuixDefault 的全宽侧滑。
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
                        assertSame(entry, host!!.pageEntries.last(), "Changing settings must not replace the active page entry")
                        if (route is Article) assertSame(instance, articleInstance, "Changing settings must not recreate answer content")
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
                        "Disabled transition must have its final position and size on the next frame",
                    )
                    runOnIdle { host!!.back() }
                    mainClock.advanceTimeByFrame()
                    waitForIdle()
                    mainClock.advanceTimeByFrame()
                    waitForIdle()
                    assertTrue(onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isEmpty(), "Disabled transition must unload the leaving page")
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
            for (style in ThemeStyle.entries) {
                for (mainStack in listOf(false, true)) {
                    ThemeManager.load(originalTheme.copy(themeStyle = style, themeMode = ThemeMode.LIGHT))
                    var paneHost: AppNavHost? = null
                    var topScope: NavTransitionScope? = null
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
                                ) {
                                    ZhihuTheme {
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
                                                        duo3HomeAccount = false,
                                                        tapToScrollToTopEnabled = false,
                                                        autoHideBottomBar = false,
                                                        collectionDirectBrowseEnabled = false,
                                                        landscapeListDetailEnabled = true,
                                                        selectedBottomBarItemKeys = listOf(Home.name, Daily.name),
                                                        startDestination = Daily,
                                                    )
                                                }
                                            },
                                            isDarkTheme = false,
                                            articleContent = { article ->
                                                paneHost = LocalArticleNavHost.current
                                                if (article.id == 1002L) topScope = LocalNavTransitionScope.current
                                                Box(
                                                    Modifier
                                                        .fillMaxSize()
                                                        .background(if (article.id == 1002L) topColor else Color(0xFF55AA77))
                                                        .testTag("pane_article_${article.id}"),
                                                )
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
                                val leftPaneWidth = onNodeWithTag("list_detail_divider").fetchSemanticsNode().boundsInRoot.left
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
                            val right = onNodeWithTag("pane_article_1002").fetchSemanticsNode().boundsInRoot
                            val left = onNodeWithTag("online_history_list").fetchSemanticsNode().boundsInRoot
                            val before = onRoot().captureToImage().toPixelMap()
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
                            val moving = onNodeWithTag("pane_article_1002").fetchSemanticsNode().boundsInRoot
                            assertTrue(moving.width < right.width && moving.width > right.width * 0.85f, "$style/$mainStack 应按右栏宽缩小：$right → $moving")
                            assertTrue(moving.left >= right.left && moving.right <= right.right, "$style/$mainStack 不能越过栏间边界")
                            assertEquals(left, onNodeWithTag("online_history_list").fetchSemanticsNode().boundsInRoot)
                            val mid = onRoot().captureToImage().toPixelMap()
                            val x = moving.left.toInt() + 2
                            val y = moving.top.toInt() + 2
                            assertTrue(mid[x, y] != topColor, "$style/$mainStack 返回中间帧必须露出圆角")
                            assertEquals(topColor, mid[moving.center.x.toInt(), y], "$style/$mainStack 圆角不能变成整行裁切")
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
                            onRoot().performTouchInput {
                                down(Offset(right.left + 8f, right.center.y))
                                moveTo(Offset(right.left + right.width * 0.8f, right.center.y), delayMillis = 180)
                                up()
                            }
                            waitUntil("完成返回后必须移除顶层", timeoutMillis = 5_000) { (rightHost.top as Article).id == 1001L }
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
