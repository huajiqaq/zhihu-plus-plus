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

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.room.useReaderConnection
import coil3.compose.AsyncImagePainter
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import com.github.zly2006.zhihu.account.LocalZhihuAccountStore
import com.github.zly2006.zhihu.account.ZhihuAccountProfileSnapshot
import com.github.zly2006.zhihu.account.ZhihuAccountRepository
import com.github.zly2006.zhihu.account.ZhihuAccountSession
import com.github.zly2006.zhihu.account.ZhihuAccountStore
import com.github.zly2006.zhihu.data.OnlineHistoryItem
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.navigation.Account
import com.github.zly2006.zhihu.navigation.AppNavHost
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.Daily
import com.github.zly2006.zhihu.navigation.Home
import com.github.zly2006.zhihu.navigation.MainTabs
import com.github.zly2006.zhihu.navigation.OnlineHistory
import com.github.zly2006.zhihu.navigation.OnlineHistoryRoute
import com.github.zly2006.zhihu.navigation.TopLevelDestination
import com.github.zly2006.zhihu.navigation.rememberAppNavHost
import com.github.zly2006.zhihu.platform.desktopSettingsStore
import com.github.zly2006.zhihu.theme.ThemeManager
import com.github.zly2006.zhihu.theme.ThemeMode
import com.github.zly2006.zhihu.theme.ThemeStyle
import com.github.zly2006.zhihu.theme.ZhihuTheme
import com.github.zly2006.zhihu.ui.components.HISTORY_SELECT_ALL_TAG
import com.github.zly2006.zhihu.ui.components.HISTORY_SELECT_TOGGLE_TAG
import com.github.zly2006.zhihu.ui.components.HISTORY_TYPE_FILTER_TAG_PREFIX
import com.github.zly2006.zhihu.ui.subscreens.BOTTOM_BAR_ITEMS_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.subscreens.START_DESTINATION_PREFERENCE_KEY
import com.github.zly2006.zhihu.viewmodel.filter.ContentFilterDatabase
import com.github.zly2006.zhihu.viewmodel.filter.getContentFilterDatabase
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertTrue
import kotlinx.io.files.Path as KotlinxPath

/** 生产内容过滤数据库由进程持有，必须在新的独立 JVM 测试进程中执行。 */
@OptIn(ExperimentalTestApi::class, coil3.annotation.ExperimentalCoilApi::class)
class HistoryRouteNavigationTest {
    @Test
    fun historyUsesRoutePreservesPagerAndRestoresWithoutReopening() {
        val originalHome = System.getProperty("user.home")
        val originalTheme = ThemeManager.snapshot()
        val directory = Files.createTempDirectory("zhihu-history-route-")
        val profile = ZhihuAccountProfileSnapshot(
            id = "history-route-fixture",
            name = "历史测试用户",
            urlToken = "history-route-fixture",
            userType = "people",
            avatarUrl = "",
        )
        val session = ZhihuAccountSession(
            login = true,
            username = profile.name,
            cookies = mutableMapOf("d_c0" to "fixture-device", "z_c0" to "fixture-session-a"),
            profile = profile,
        )
        val historyPages = listOf("A", "B").associateWith { label ->
            val answers = (0..20).joinToString(",") { index ->
                """{"card_type":"read_history","data":{"header":{"icon":"","title":"$label 历史测试条目 $index"},"action":{"type":"router","url":"zhihu://answers/${1000 + index}"},"extra":{"content_token":"${1000 + index}","content_type":"answer","read_time":1716120000,"question_token":"456"}}}"""
            }
            val article = """{"card_type":"read_history","data":{"header":{"icon":"","title":"$label 历史测试文章"},"action":{"type":"router","url":"zhihu://articles/9000"},"extra":{"content_token":"9000","content_type":"article","read_time":1716120000,"question_token":""}}}"""
            """{"data":[$answers,$article],"paging":{"is_end":true,"next":""}}"""
        }
        val decodedPages = historyPages.mapValues { (_, page) ->
            ZhihuJson.json
                .parseToJsonElement(page)
                .jsonObject
                .getValue("data")
                .jsonArray
                .map { ZhihuJson.decodeJson<OnlineHistoryItem>(it) }
        }
        assertEquals(22, decodedPages.getValue("A").size)
        assertEquals(
            decodedPages.getValue("A").map { it.data.extra.contentType to it.data.extra.contentToken },
            decodedPages.getValue("B").map { it.data.extra.contentType to it.data.extra.contentToken },
            "客户端替换前后保留完全相同的服务端 key，不能靠 selectedKeys 交集恰好清空",
        )
        val requests = CopyOnWriteArrayList<String>()
        val engine = MockEngine { request ->
            requests += "${request.method.value} ${request.url.host}${request.url.encodedPath}"
            val label = if (request.headers[HttpHeaders.Cookie].orEmpty().contains("fixture-session-b")) "B" else "A"
            val body = when (request.url.encodedPath) {
                "/api/v4/me" -> ZhihuJson.json.encodeToString(profile)
                "/unify-consumption/read_history" -> historyPages.getValue(label)
                "/read_history/total" -> """{"disabled":false}"""
                "/api/4/stories/latest" -> """{"date":"20261102","stories":[]}"""
                else -> """{"data":[],"paging":{"is_end":true,"next":""}}"""
            }
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        var fixtureStore: ZhihuAccountStore? = null
        var ownedDatabase: ContentFilterDatabase? = null
        var fallbackBacks = 0
        val rootBackInput = object : NavigationEventInput() {
            fun back() = dispatchOnBackCompleted()
        }
        val rootBackOwner = object : NavigationEventDispatcherOwner {
            override val navigationEventDispatcher = NavigationEventDispatcher(
                onBackCompletedFallback = { fallbackBacks++ },
            ).apply { addInput(rootBackInput) }
        }
        val pagerModels = object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
        val routeModels = object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
        val noImages = AsyncImagePreviewHandler { _, _ -> AsyncImagePainter.State.Empty }
        try {
            System.setProperty("user.home", directory.toString())
            val database = getContentFilterDatabase()
            val databaseFile = runBlocking {
                database.useReaderConnection { connection ->
                    connection.usePrepared("PRAGMA database_list") { statement ->
                        var mainFile: String? = null
                        while (statement.step()) {
                            if (statement.getText(1) == "main") mainFile = statement.getText(2)
                        }
                        checkNotNull(mainFile)
                    }
                }
            }
            check(
                java.io
                    .File(databaseFile)
                    .canonicalFile
                    .toPath()
                    .startsWith(directory.toRealPath()),
            ) {
                "生产数据库已被其他测试初始化；必须使用独立 JVM worker，禁止写入已有数据库"
            }
            ownedDatabase = database
            val accountFile = directory.resolve("fixture-account.json")
            Files.writeString(accountFile, ZhihuJson.json.encodeToString(session))
            val store = ZhihuAccountStore(ZhihuAccountRepository(KotlinxPath(accountFile.toString())), engine = engine)
            fixtureStore = store
            assertEquals(profile.id, store.session.profile?.id)
            desktopSettingsStore().apply {
                putBoolean("enableContentFilter", false)
                putBoolean("allowTelemetry", false)
                putBoolean("floatingBottomBar", false)
                putStringSet(BOTTOM_BAR_ITEMS_PREFERENCE_KEY, linkedSetOf(Home.name, Daily.name, OnlineHistory.name, Account.name))
                putString(START_DESTINATION_PREFERENCE_KEY, OnlineHistory.name)
            }
            ThemeManager.load(originalTheme.copy(themeStyle = ThemeStyle.MATERIAL_EXPRESSIVE, themeMode = ThemeMode.LIGHT))

            var pagerHost: AppNavHost? = null
            var pagerMounted by mutableStateOf(true)
            runDesktopComposeUiTest(width = 412, height = 892) {
                setContent {
                    CompositionLocalProvider(
                        LocalViewModelStoreOwner provides pagerModels,
                        LocalZhihuAccountStore provides store,
                        LocalNavigationEventDispatcherOwner provides rootBackOwner,
                        LocalInspectionMode provides true,
                        LocalAsyncImagePreviewHandler provides noImages,
                    ) {
                        val holder = rememberSaveableStateHolder()
                        if (pagerMounted) {
                            holder.SaveableStateProvider("startup-history") {
                                ZhihuTheme {
                                    val host = rememberAppNavHost(MainTabs)
                                    pagerHost = host
                                    ZhihuMain(
                                        navHost = host,
                                        mainTabNavigationTarget = null,
                                        navigate = host::open,
                                        setCurrentMainTabOpenFrom = {},
                                        consumeMainTabNavigationTarget = {},
                                        preferenceState = remember { ZhihuMainPreferenceState { snapshot(OnlineHistory) } },
                                        isDarkTheme = false,
                                        articleContent = { Text("测试正文") },
                                    )
                                }
                            }
                        }
                    }
                }
                waitUntil(timeoutMillis = 5_000) {
                    onAllNodesWithText("A 历史测试条目 0", substring = true).fetchSemanticsNodes().isNotEmpty()
                }
                val rootEntryId = pagerHost!!.pageEntries.first().id
                assertEquals(listOf(MainTabs, OnlineHistoryRoute), pagerHost!!.backStack.toList(), "既有启动历史偏好打开普通 route")
                onNodeWithTag(ONLINE_HISTORY_BACK_TAG).assertIsDisplayed()
                runOnIdle { rootBackInput.back() }
                waitForIdle()
                onNodeWithTag("nav_tab_home").assertIsSelected()
                runOnIdle { rootBackInput.back() }
                assertEquals(1, fallbackBacks, "无访问历史的第一个 tab 应放行宿主返回")
                mainClock.autoAdvance = false
                onNodeWithTag("nav_tab_daily").performClick()
                mainClock.advanceTimeBy(96)
                runOnIdle { rootBackInput.back() }
                assertEquals(1, fallbackBacks, "切页尚未停稳时返回不能穿透宿主")
                mainClock.autoAdvance = true
                waitForIdle()
                onNodeWithTag("nav_tab_daily").assertIsSelected()
                onNodeWithTag("nav_tab_account").performClick()
                waitForIdle()
                onNodeWithTag("nav_tab_home").performClick()
                waitForIdle()
                onNodeWithTag("nav_tab_home").performClick()
                runOnIdle {
                    rootBackInput.back()
                    rootBackInput.back()
                }
                waitForIdle()
                onNodeWithTag("nav_tab_account").assertIsSelected()
                assertEquals(1, fallbackBacks, "快速返回只消费一次实际访问，不穿透到宿主")
                runOnIdle { rootBackInput.back() }
                waitForIdle()
                onNodeWithTag("nav_tab_daily").assertIsSelected()
                runOnIdle { rootBackInput.back() }
                waitForIdle()
                onNodeWithTag("nav_tab_home").assertIsSelected()
                runOnIdle { rootBackInput.back() }
                assertEquals(2, fallbackBacks, "重选、动画中间页及返回本身不增加访问历史")
                onNodeWithTag("nav_tab_daily").performClick()
                waitForIdle()
                onNodeWithTag("nav_tab_onlinehistory").performClick()
                waitForIdle()
                assertEquals(listOf(MainTabs, OnlineHistoryRoute), pagerHost!!.backStack.toList(), "底栏历史不加入 pager")
                onNodeWithTag(ONLINE_HISTORY_BACK_TAG).assertIsDisplayed()
                runOnIdle { rootBackInput.back() }
                waitForIdle()
                onNodeWithTag("nav_tab_daily").assertIsSelected()
                assertEquals(rootEntryId, pagerHost!!.topEntryId, "历史返回保留原 tab 与主壳访问")
                val pagerBeforeRemount = pagerHost
                runOnIdle { pagerMounted = false }
                waitForIdle()
                runOnIdle { pagerMounted = true }
                waitForIdle()
                assertNotSame(pagerBeforeRemount, pagerHost)
                assertEquals(listOf(MainTabs), pagerHost!!.backStack.toList(), "重装不能再次应用启动历史偏好")
                assertEquals(rootEntryId, pagerHost!!.topEntryId)
                onNodeWithTag("nav_tab_daily").assertIsSelected()
                runOnIdle { rootBackInput.back() }
                waitForIdle()
                onNodeWithTag("nav_tab_home").assertIsSelected()
                assertEquals(OnlineHistory.name, desktopSettingsStore().getString(START_DESTINATION_PREFERENCE_KEY, ""))
                assertEquals(
                    setOf(Home.name, Daily.name, OnlineHistory.name, Account.name),
                    desktopSettingsStore().getStringSet(BOTTOM_BAR_ITEMS_PREFERENCE_KEY, emptySet()),
                )
            }

            desktopSettingsStore().apply {
                putStringSet(BOTTOM_BAR_ITEMS_PREFERENCE_KEY, linkedSetOf(Daily.name, Account.name))
                putString(START_DESTINATION_PREFERENCE_KEY, Account.name)
            }
            var routeHost: AppNavHost? = null
            var mounted by mutableStateOf(true)
            val accountPreferences = ZhihuMainPreferenceState {
                snapshot(Account).copy(selectedBottomBarItemKeys = listOf(Daily.name, Account.name))
            }
            runDesktopComposeUiTest(width = 412, height = 892) {
                setContent {
                    CompositionLocalProvider(
                        LocalViewModelStoreOwner provides routeModels,
                        LocalZhihuAccountStore provides store,
                        LocalNavigationEventDispatcherOwner provides rootBackOwner,
                        LocalInspectionMode provides true,
                        LocalAsyncImagePreviewHandler provides noImages,
                    ) {
                        val holder = rememberSaveableStateHolder()
                        if (mounted) {
                            holder.SaveableStateProvider("history-start") {
                                ZhihuTheme {
                                    val host = rememberAppNavHost(MainTabs)
                                    routeHost = host
                                    ZhihuMain(
                                        navHost = host,
                                        mainTabNavigationTarget = null,
                                        navigate = host::open,
                                        setCurrentMainTabOpenFrom = {},
                                        consumeMainTabNavigationTarget = {},
                                        preferenceState = accountPreferences,
                                        isDarkTheme = false,
                                        articleContent = { article -> Text("测试正文 ${article.id}") },
                                    )
                                }
                            }
                        }
                    }
                }
                onNodeWithTag("nav_tab_account").assertIsSelected()
                onNodeWithTag("nav_tab_daily").performClick()
                waitForIdle()
                onNodeWithTag("nav_tab_daily").assertIsSelected()
                val hostBeforeTabRestore = routeHost
                runOnIdle { mounted = false }
                waitForIdle()
                runOnIdle { mounted = true }
                waitForIdle()
                assertNotSame(hostBeforeTabRestore, routeHost)
                onNodeWithTag("nav_tab_daily").assertIsSelected()
                onNodeWithTag("nav_tab_account").performClick()
                waitForIdle()
                val rootEntryId = routeHost!!.topEntryId
                onNodeWithTag(ACCOUNT_SETTINGS_SHORTCUT_HISTORY_TAG).performClick()
                waitUntil(timeoutMillis = 5_000) {
                    onAllNodesWithText("A 历史测试条目 0", substring = true).fetchSemanticsNodes().isNotEmpty()
                }
                assertEquals(listOf(MainTabs, OnlineHistoryRoute), routeHost!!.backStack.toList(), "账号快捷入口进入真实普通 route")
                val historyEntryId = routeHost!!.topEntryId
                assertNotEquals(rootEntryId, historyEntryId)
                onNodeWithTag(ONLINE_HISTORY_BACK_TAG).assertIsDisplayed()
                assertTrue(requests.any { it.endsWith("/unify-consumption/read_history") })
                onNodeWithTag(HISTORY_TYPE_FILTER_TAG_PREFIX + "回答").performClick()
                onNodeWithTag(HISTORY_TYPE_FILTER_TAG_PREFIX + "回答").assertIsSelected()
                onNodeWithTag(HISTORY_SELECT_TOGGLE_TAG).performClick()
                onNodeWithTag(HISTORY_SELECT_ALL_TAG).performClick()
                onNodeWithText("共选 21 项").assertIsDisplayed()
                onNodeWithText("删除(21)").assertIsDisplayed()
                onNodeWithTag("online_history_list").performScrollToIndex(11)
                onNodeWithText("A 历史测试条目 11", substring = true).assertIsDisplayed()
                val scrollBeforeCover = onNodeWithTag("online_history_list")
                    .fetchSemanticsNode()
                    .config[SemanticsProperties.VerticalScrollAxisRange]
                    .value()
                assertTrue(scrollBeforeCover > 0f, "恢复验证必须从非零滚动位置开始")
                val titleBeforeCover = onNode(hasTestTag("feed_card_title") and hasText("A 历史测试条目 11"), useUnmergedTree = true)
                    .fetchSemanticsNode()
                    .boundsInRoot.top
                val readsBeforeCover = requests.count { it.endsWith("/unify-consumption/read_history") }
                val coveredAnswer = Article(type = ArticleType.Answer, id = 99111)
                runOnIdle { routeHost!!.open(coveredAnswer) }
                waitForIdle()
                onNodeWithText("测试正文 99111").assertIsDisplayed()
                assertEquals(listOf(MainTabs, OnlineHistoryRoute, coveredAnswer), routeHost!!.backStack.toList())
                runOnIdle { rootBackInput.back() }
                waitForIdle()
                assertEquals(historyEntryId, routeHost!!.topEntryId, "返回覆盖页必须恢复原历史访问")
                onNodeWithTag(HISTORY_TYPE_FILTER_TAG_PREFIX + "回答").assertIsSelected()
                onNodeWithText("删除(21)").assertIsDisplayed()
                assertEquals(scrollBeforeCover, onNodeWithTag("online_history_list").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value())
                assertEquals(titleBeforeCover, onNode(hasTestTag("feed_card_title") and hasText("A 历史测试条目 11"), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top)
                assertEquals(readsBeforeCover, requests.count { it.endsWith("/unify-consumption/read_history") }, "同一客户端覆盖返回不应重新请求历史")

                runOnIdle { routeHost!!.open(coveredAnswer) }
                waitForIdle()
                val entriesBeforeRemount = routeHost!!.pageEntries.map { it.id to it.destination }.toList()
                val hostBeforeRemount = routeHost
                runOnIdle { mounted = false }
                waitForIdle()
                onNodeWithText("测试正文 99111").assertDoesNotExist()
                runOnIdle { mounted = true }
                waitForIdle()
                assertNotSame(hostBeforeRemount, routeHost, "必须真实移出并重新装载 SaveableStateProvider，不能只重组 remember")
                assertEquals(readsBeforeCover, requests.count { it.endsWith("/unify-consumption/read_history") }, "同客户端重装仍复用原 entry 的 ViewModel")
                assertEquals(entriesBeforeRemount, routeHost!!.pageEntries.map { it.id to it.destination }.toList())
                onNodeWithText("测试正文 99111").assertIsDisplayed()
                runOnIdle { rootBackInput.back() }
                waitUntil(timeoutMillis = 5_000) {
                    onAllNodesWithText("A 历史测试条目 11", substring = true).fetchSemanticsNodes().isNotEmpty()
                }
                assertEquals(historyEntryId, routeHost!!.topEntryId)
                onNodeWithTag(HISTORY_TYPE_FILTER_TAG_PREFIX + "回答").assertIsSelected()
                onNodeWithText("共选 21 项").assertIsDisplayed()
                onNodeWithText("删除(21)").assertIsDisplayed()
                assertEquals(scrollBeforeCover, onNodeWithTag("online_history_list").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value())
                assertEquals(titleBeforeCover, onNode(hasTestTag("feed_card_title") and hasText("A 历史测试条目 11"), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top)

                runOnIdle { routeHost!!.open(coveredAnswer) }
                waitForIdle()
                val entriesBeforeSwitch = routeHost!!.pageEntries.map { it.id to it.destination }.toList()
                val clientBeforeSwitch = store.client.httpClient()
                runOnIdle { mounted = false }
                waitForIdle()
                onNodeWithText("测试正文 99111").assertDoesNotExist()
                runOnIdle {
                    store.replaceSession(session.copy(cookies = mutableMapOf("d_c0" to "fixture-device", "z_c0" to "fixture-session-b")))
                }
                assertEquals(session.login, store.session.login)
                assertEquals(profile.id, store.session.profile?.id)
                assertNotSame(clientBeforeSwitch, store.client.httpClient(), "同 login/id 也必须替换客户端身份")
                runOnIdle { mounted = true }
                waitForIdle()
                assertEquals(entriesBeforeSwitch, routeHost!!.pageEntries.map { it.id to it.destination }.toList(), "换客户端不应替换导航访问")
                onNodeWithText("测试正文 99111").assertIsDisplayed()
                runOnIdle { rootBackInput.back() }
                waitUntil(timeoutMillis = 5_000) {
                    onAllNodesWithText("B 历史测试条目 0", substring = true).fetchSemanticsNodes().isNotEmpty()
                }
                assertEquals(historyEntryId, routeHost!!.topEntryId)
                onNodeWithTag(HISTORY_TYPE_FILTER_TAG_PREFIX + "all").assertIsSelected()
                onNodeWithTag(HISTORY_SELECT_ALL_TAG).assertDoesNotExist()
                onNodeWithText("删除(21)").assertDoesNotExist()
                assertEquals(0f, onNodeWithTag("online_history_list").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value(), "覆盖期间换客户端后不能恢复旧保存槽的滚动")
                onNodeWithTag(HISTORY_SELECT_TOGGLE_TAG).performClick()
                onNodeWithText("共选 0 项").assertIsDisplayed()
                onNodeWithText("删除(0)").assertIsDisplayed()
                onNodeWithTag(ONLINE_HISTORY_BACK_TAG).performClick()
                assertEquals(historyEntryId, routeHost!!.topEntryId, "管理模式返回先退出选择，不应关闭 route")
                onNodeWithTag(ONLINE_HISTORY_BACK_TAG).performClick()
                waitForIdle()
                assertEquals(listOf(MainTabs), routeHost!!.backStack.toList())
                assertEquals(rootEntryId, routeHost!!.topEntryId, "普通 route 返回保留主壳 root entry")
                onNodeWithTag("nav_tab_account").assertIsSelected()
                assertEquals(Account.name, desktopSettingsStore().getString(START_DESTINATION_PREFERENCE_KEY, ""))
                assertEquals(setOf(Daily.name, Account.name), desktopSettingsStore().getStringSet(BOTTOM_BAR_ITEMS_PREFERENCE_KEY, emptySet()))
            }
        } finally {
            try {
                try {
                    pagerModels.viewModelStore.clear()
                } finally {
                    routeModels.viewModelStore.clear()
                }
            } finally {
                try {
                    fixtureStore?.close() ?: engine.close()
                } finally {
                    try {
                        ownedDatabase?.close()
                    } finally {
                        try {
                            rootBackOwner.navigationEventDispatcher.dispose()
                        } finally {
                            try {
                                ThemeManager.load(originalTheme)
                            } finally {
                                if (originalHome == null) System.clearProperty("user.home") else System.setProperty("user.home", originalHome)
                                directory.toFile().deleteRecursively()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun snapshot(start: TopLevelDestination) = ZhihuMainPreferenceSnapshot(
        tapToScrollToTopEnabled = false,
        autoHideBottomBar = false,
        landscapeListDetailEnabled = true,
        selectedBottomBarItemKeys = listOf(Home.name, Daily.name, OnlineHistory.name, Account.name),
        startDestination = start,
    )
}
