package com.github.zly2006.zhihu.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.savedstate.compose.LocalSavedStateRegistryOwner
import com.github.zly2006.zhihu.account.LocalZhihuAccountStore
import com.github.zly2006.zhihu.account.ZhihuAccountProfileSnapshot
import com.github.zly2006.zhihu.account.ZhihuAccountRepository
import com.github.zly2006.zhihu.account.ZhihuAccountSession
import com.github.zly2006.zhihu.account.ZhihuAccountStore
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.navigation.Account
import com.github.zly2006.zhihu.navigation.AppNavHost
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.Home
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.MainTabs
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.navigation.rememberAppNavHost
import com.github.zly2006.zhihu.platform.desktopSettingsStore
import com.github.zly2006.zhihu.theme.ThemeManager
import com.github.zly2006.zhihu.theme.ZhihuTheme
import com.github.zly2006.zhihu.ui.components.AppAlertDialog
import com.github.zly2006.zhihu.ui.components.AppScaffold
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.ui.components.LocalNavHostLifecycleOwner
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.merge
import kotlinx.serialization.encodeToString
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import com.github.zly2006.zhihu.navigation.AppNavDisplay as NavDisplay
import kotlinx.io.files.Path as KotlinxPath

/** 被覆盖的条目隐藏窗口，返回后恢复；宿主生命周期在 NavDisplay 外提供。 */
@OptIn(ExperimentalTestApi::class)
class NavEntryDialogCoverTest {
    @Test
    fun dialogInsideANavEntryHidesWhileCoveredAndReturns() {
        val log = CopyOnWriteArrayList<String>()
        var push: (NavDestination) -> Unit = {}
        var pop: () -> Unit = {}
        runDesktopComposeUiTest(width = 412, height = 800) {
            setContent {
                ZhihuTheme {
                    val navHost = rememberAppNavHost(Account.DeveloperSettings)
                    push = { navHost.open(it) }
                    pop = { navHost.onBack() }
                    CompositionLocalProvider(LocalNavHostLifecycleOwner provides LocalLifecycleOwner.current) {
                        NavDisplay(navHost = navHost, onBack = navHost::onBack) {
                            entry<Account.DeveloperSettings> {
                                AppScaffold {
                                    LifecycleLog("A", log)
                                    AppAlertDialog(
                                        onDismissRequest = {},
                                        title = { Text("被盖住的弹窗") },
                                        confirmButton = { AppTextButton("确定", {}) },
                                    )
                                }
                            }
                            entry<Account.OpenSourceLicenses> {
                                AppScaffold { LifecycleLog("B", log) }
                            }
                        }
                    }
                }
            }
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("被盖住的弹窗").fetchSemanticsNodes().isNotEmpty() }
            runOnIdle { push(Account.OpenSourceLicenses) }
            val hidden = runCatching {
                waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("被盖住的弹窗").fetchSemanticsNodes().isEmpty() }
            }.isSuccess
            runOnIdle { pop() }
            val shownAgain = runCatching {
                waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("被盖住的弹窗").fetchSemanticsNodes().isNotEmpty() }
            }.isSuccess
            assertTrue(hidden && shownAgain, "hidden=$hidden shownAgain=$shownAgain\n" + log.joinToString("\n"))
        }
    }

    @Test
    fun repeatedAnswerGetsItsOwnViewModelAndBackRestoresPreviousInstance() = runDesktopComposeUiTest {
        val answer = Article(type = ArticleType.Answer, id = 42)
        setContent {
            ZhihuTheme {
                val host = rememberAppNavHost(answer)
                NavDisplay(navHost = host) {
                    entry<Article> {
                        val model = viewModel { VisitCounter() }
                        Column {
                            Text("阅读进度 ${model.count}")
                            TextButton(onClick = { model.count++ }) { Text("增加进度") }
                            TextButton(onClick = { host.open(answer) }) { Text("再次进入同一回答") }
                            TextButton(onClick = { host.back() }) { Text("返回上次访问 ${model.count}") }
                        }
                    }
                }
            }
        }
        onNodeWithText("增加进度").performClick()
        onNodeWithText("阅读进度 1").assertExists()
        onNodeWithText("再次进入同一回答").performClick()
        waitForIdle()
        onNodeWithText("阅读进度 0").assertExists()
        onNodeWithText("返回上次访问 0").performClick()
        waitForIdle()
        onNodeWithText("阅读进度 1").assertExists()
    }

    @Test
    fun questionContextKeepsTheOriginalReadingEntryAcrossWidths() {
        val oldHome = System.getProperty("user.home")
        val oldTheme = ThemeManager.snapshot()
        val directory = Files.createTempDirectory("zhihu-pane-contract-")
        val profile = ZhihuAccountProfileSnapshot(
            id = "pane-contract-fixture",
            name = "分栏测试用户",
            urlToken = "pane-contract-fixture",
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
            val body = if (request.url.encodedPath == "/api/v4/me") {
                ZhihuJson.json.encodeToString(profile)
            } else {
                """{"data":[],"paging":{"is_end":true,"next":""}}"""
            }
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        var store: ZhihuAccountStore? = null
        try {
            System.setProperty("user.home", directory.toString())
            val accountFile = directory.resolve("fixture-account.json")
            Files.writeString(accountFile, ZhihuJson.json.encodeToString(session))
            val fixtureStore = ZhihuAccountStore(
                ZhihuAccountRepository(KotlinxPath(accountFile.toString())),
                engine = engine,
            )
            store = fixtureStore
            desktopSettingsStore().apply {
                putBoolean("allowTelemetry", false)
                putBoolean("enableContentFilter", false)
            }
            val answer = Article(type = ArticleType.Answer, id = 318L)
            var width by mutableStateOf(1000.dp)
            var mainHost: AppNavHost? = null
            var readingHost: AppNavHost? = null
            val models = mutableSetOf<VisitCounter>()
            runDesktopComposeUiTest(width = 1200, height = 800) {
                setContent {
                    CompositionLocalProvider(
                        LocalZhihuAccountStore provides fixtureStore,
                    ) {
                        ZhihuTheme {
                            val host = rememberAppNavHost(MainTabs)
                            mainHost = host
                            ZhihuMain(
                                modifier = Modifier.requiredSize(width, 600.dp),
                                navHost = host,
                                mainTabNavigationTarget = null,
                                navigate = host::open,
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
                                            selectedBottomBarItemKeys = listOf(Home.name),
                                            startDestination = Home,
                                        )
                                    }
                                },
                                isDarkTheme = ThemeManager.isDarkTheme(),
                                articleContent = {
                                    val navigator = LocalNavigator.current
                                    val model = viewModel { VisitCounter() }
                                    readingHost = LocalArticleNavHost.current
                                    models += model
                                    Column(Modifier.fillMaxSize().testTag("pane_contract_article")) {
                                        Text("进度 ${model.count}")
                                        Button(onClick = { model.count++ }) { Text("增加阅读进度") }
                                        Button(onClick = { navigator.onNavigate(Question(318L, "测试问题")) }) {
                                            Text("阅读中打开问题")
                                        }
                                        TextButton(onClick = { readingHost!!.back() }) { Text("关闭阅读") }
                                    }
                                },
                            )
                        }
                    }
                }
                runOnIdle { mainHost!!.open(answer) }
                waitForIdle()
                val firstEntry = mainHost!!.pageEntries.last()
                val firstModel = models.single()
                onNodeWithText("增加阅读进度").performClick()
                onNodeWithText("阅读中打开问题").performClick()
                waitUntil(timeoutMillis = 5_000) {
                    onAllNodesWithTag(QUESTION_SCREEN_LIST_TAG).fetchSemanticsNodes().isNotEmpty()
                }
                onNodeWithTag("pane_contract_article").assertIsDisplayed()
                onNodeWithTag(QUESTION_SCREEN_LIST_TAG).assertIsDisplayed()
                val left = onNodeWithTag(QUESTION_SCREEN_LIST_TAG).fetchSemanticsNode().boundsInRoot
                val right = onNodeWithTag("pane_contract_article").fetchSemanticsNode().boundsInRoot
                assertTrue(left.width > 0f && right.width > 0f && left.right <= right.left)
                assertSame(firstEntry, mainHost!!.pageEntries.last())
                assertEquals(firstEntry.id, readingHost!!.topEntryId)
                assertSame(firstModel, models.single())
                onNodeWithText("进度 1").assertExists()

                runOnIdle { width = 400.dp }
                waitForIdle()
                onNodeWithTag(QUESTION_SCREEN_LIST_TAG).assertIsDisplayed()
                runOnIdle { width = 1000.dp }
                waitForIdle()
                onNodeWithTag(QUESTION_SCREEN_LIST_TAG).assertIsDisplayed()
                onNodeWithTag("pane_contract_article").assertIsDisplayed()
                assertSame(firstEntry, mainHost!!.pageEntries.last())
                assertSame(firstModel, models.single())
            }
        } finally {
            store?.close()
            engine.close()
            ThemeManager.load(oldTheme)
            if (oldHome == null) System.clearProperty("user.home") else System.setProperty("user.home", oldHome)
            directory.toFile().deleteRecursively()
        }
    }

    class VisitCounter : ViewModel() {
        var count by mutableIntStateOf(0)
    }

    @Composable
    private fun LifecycleLog(name: String, log: MutableList<String>) {
        val entry = LocalLifecycleOwner.current
        val saved = LocalSavedStateRegistryOwner.current
        LaunchedEffect(entry, saved) {
            merge(entry.lifecycle.currentStateFlow, saved.lifecycle.currentStateFlow).collect {
                log += "$name entry=${entry.lifecycle.currentState} savedStateOwner=${saved.lifecycle.currentState}"
            }
        }
    }
}
