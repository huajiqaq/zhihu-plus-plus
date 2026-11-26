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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.zly2006.zhihu.account.LocalZhihuAccountStore
import com.github.zly2006.zhihu.account.ZhihuAccountProfileSnapshot
import com.github.zly2006.zhihu.account.ZhihuAccountRepository
import com.github.zly2006.zhihu.account.ZhihuAccountSession
import com.github.zly2006.zhihu.account.ZhihuAccountStore
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.desktop.desktopZhihuDataFile
import com.github.zly2006.zhihu.navigation.AppNavHost
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.Home
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.MainTabs
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.navigation.rememberAppNavHost
import com.github.zly2006.zhihu.platform.desktopSettingsStore
import com.github.zly2006.zhihu.theme.ThemeManager
import com.github.zly2006.zhihu.theme.ZhihuTheme
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.serialization.encodeToString
import java.nio.file.Files
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.io.files.Path as KotlinxPath

/** 被覆盖的条目隐藏窗口，返回后恢复；宿主生命周期在 NavDisplay 外提供。 */
@OptIn(ExperimentalTestApi::class)
class NavEntryDialogCoverTest {
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
            val initialRatio = 0.42f
            val settings = desktopSettingsStore().apply {
                putBoolean("allowTelemetry", false)
                putBoolean("enableContentFilter", false)
                putFloat(LANDSCAPE_LIST_PANE_RATIO_KEY, initialRatio)
            }
            val answer = Article(type = ArticleType.Answer, id = 318L)
            val question = Question(318L, "测试问题")
            var width by mutableStateOf(1000.dp)
            var mainHost: AppNavHost? = null
            var readingHost: AppNavHost? = null
            var questionHost: AppNavHost? = null
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
                                modifier = Modifier
                                    .fillMaxSize()
                                    .wrapContentSize(Alignment.TopStart)
                                    .requiredSize(width, 600.dp),
                                navHost = host,
                                mainTabNavigationTarget = null,
                                navigate = host::open,
                                navigateContent = { destination, target ->
                                    if (destination == question) questionHost = target
                                    target.open(destination)
                                },
                                enableLandscapeListDetail = true,
                                setCurrentMainTabOpenFrom = {},
                                consumeMainTabNavigationTarget = {},
                                preferenceState = remember {
                                    ZhihuMainPreferenceState {
                                        ZhihuMainPreferenceSnapshot(
                                            tapToScrollToTopEnabled = false,
                                            autoHideBottomBar = false,
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
                                        Button(onClick = { navigator.onNavigate(question) }) {
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
                onNodeWithText("增加阅读进度").performSemanticsAction(SemanticsActions.OnClick)
                onNodeWithText("进度 1").assertIsDisplayed()
                onNodeWithText("阅读中打开问题").performSemanticsAction(SemanticsActions.OnClick)
                runOnIdle {
                    assertSame(firstEntry, mainHost!!.pageEntries.last(), "打开问题不能替换主栈阅读条目")
                    assertTrue(questionHost !== mainHost, "问题应进入详情栈")
                    assertEquals(question, questionHost?.top, "实际详情栈没有打开所点的问题")
                }
                waitUntil("问题已进入详情栈，须显示真实问题列表", timeoutMillis = 5_000) {
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

                val listPane = onNodeWithTag("list_pane")
                val divider = onNodeWithTag("list_detail_divider")
                val usableWidth = with(density) { (width - LIST_DETAIL_DIVIDER_WIDTH).toPx() }
                val beforeDrag = listPane.fetchSemanticsNode().boundsInRoot.width
                assertEquals(usableWidth * initialRatio, beforeDrag, 1f)
                val handle = divider.fetchSemanticsNode().boundsInRoot
                val root = onRoot()
                val rootBounds = root.fetchSemanticsNode().boundsInRoot
                val start = handle.center - rootBounds.topLeft - Offset(with(density) { 4.dp.toPx() }, 0f)
                root.performTouchInput {
                    down(start)
                    moveTo(start + Offset(20.dp.toPx(), 0f), delayMillis = 80)
                    moveTo(start + Offset(64.dp.toPx(), 0f), delayMillis = 160)
                }
                mainClock.advanceTimeByFrame()
                waitForIdle()
                val draggedWidth = listPane.fetchSemanticsNode().boundsInRoot.width
                assertTrue(draggedWidth > beforeDrag, "拖动须在松手前改变可见左栏宽度：$beforeDrag → $draggedWidth，分隔条 $handle，根 $rootBounds")
                assertEquals(initialRatio, settings.getFloat(LANDSCAPE_LIST_PANE_RATIO_KEY, -1f))
                root.performTouchInput { up() }
                waitForIdle()
                val disk = Properties()
                desktopZhihuDataFile("settings.properties").inputStream().use(disk::load)
                val savedRatio = disk.getProperty(LANDSCAPE_LIST_PANE_RATIO_KEY).toFloat()
                assertEquals(draggedWidth, usableWidth * savedRatio, 1f)

                runOnIdle { width = 400.dp }
                waitForIdle()
                onNodeWithTag(QUESTION_SCREEN_LIST_TAG).assertIsDisplayed()
                val restoredRatio = savedRatio - 0.05f
                runOnIdle {
                    settings.putFloat(LANDSCAPE_LIST_PANE_RATIO_KEY, restoredRatio)
                    width = 1100.dp
                }
                waitForIdle()
                onNodeWithTag(QUESTION_SCREEN_LIST_TAG).assertIsDisplayed()
                onNodeWithTag("pane_contract_article").assertIsDisplayed()
                assertEquals(
                    with(density) { (width - LIST_DETAIL_DIVIDER_WIDTH).toPx() } * restoredRatio,
                    listPane.fetchSemanticsNode().boundsInRoot.width,
                    1f,
                )
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
}
