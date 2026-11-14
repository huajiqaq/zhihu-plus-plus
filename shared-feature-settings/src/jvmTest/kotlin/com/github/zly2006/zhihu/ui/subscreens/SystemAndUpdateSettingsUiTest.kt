package com.github.zly2006.zhihu.ui.subscreens

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.github.zly2006.zhihu.account.LocalZhihuAccountStore
import com.github.zly2006.zhihu.account.ZhihuAccountRepository
import com.github.zly2006.zhihu.account.ZhihuAccountStore
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.Navigator
import com.github.zly2006.zhihu.theme.ZhihuTheme
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.io.files.Path
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class SystemAndUpdateSettingsUiTest {
    @Test
    fun tappingTheLatestStateChecksAgain() {
        val requests = AtomicInteger()
        withUpdateServer(
            MockEngine { request ->
                check("/releases/" in request.url.encodedPath) { "检查更新以外不应发起请求：${request.url}" }
                requests.incrementAndGet()
                respond(releaseJson("v0.0.1"), HttpStatusCode.OK, jsonHeaders)
            },
        ) { store ->
            runDesktopComposeUiTest(width = 720, height = 1600) {
                setContent {
                    ZhihuTheme {
                        CompositionLocalProvider(LocalNavigator provides Navigator({}, {}), LocalZhihuAccountStore provides store) {
                            SystemAndUpdateSettingsScreen()
                        }
                    }
                }
                onNode(hasText("检查更新") and hasClickAction()).performScrollTo().performClick()
                waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("最新版本", substring = true).fetchSemanticsNodes().isNotEmpty() }
                assertEquals(1, requests.get())

                onNode((hasText("检查更新") or hasText("已经是最新版本")) and hasClickAction()).performClick()
                waitUntil(timeoutMillis = 5_000) { requests.get() >= 2 }
            }
        }
    }

    @Test
    fun checkFailureShowsTheReason() {
        val recovered = AtomicBoolean(false)
        val requests = AtomicInteger()
        withUpdateServer(
            MockEngine { request ->
                check("/releases/" in request.url.encodedPath) { "检查更新以外不应发起请求：${request.url}" }
                requests.incrementAndGet()
                if (recovered.get()) {
                    respond(releaseJson("v0.0.1"), HttpStatusCode.OK, jsonHeaders)
                } else {
                    respond("""{"message":"Not Found"}""", HttpStatusCode.NotFound, jsonHeaders)
                }
            },
        ) { store ->
            runDesktopComposeUiTest(width = 720, height = 1600) {
                setContent {
                    ZhihuTheme {
                        CompositionLocalProvider(LocalNavigator provides Navigator({}, {}), LocalZhihuAccountStore provides store) {
                            SystemAndUpdateSettingsScreen()
                        }
                    }
                }
                onNode(hasText("检查更新") and hasClickAction()).performScrollTo().performClick()
                waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("HTTP 404", substring = true).fetchSemanticsNodes().isNotEmpty() }
                onNode(hasText("重试") and hasClickAction()).assertIsEnabled()
                val beforeRetry = requests.get()
                recovered.set(true)
                onNode(hasText("重试") and hasClickAction()).performClick()
                waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("最新版本", substring = true).fetchSemanticsNodes().isNotEmpty() }
                assertEquals(beforeRetry + 1, requests.get())
                onAllNodesWithText("HTTP 404", substring = true).assertCountEquals(0)
            }
        }
    }

    @Test
    fun desktopUpdateActionSaysItOpensTheDownloadPage() = withUpdateServer(
        MockEngine { request ->
            check("/releases/" in request.url.encodedPath) { "检查更新以外不应发起请求：${request.url}" }
            respond(releaseJson("v999.0.0"), HttpStatusCode.OK, jsonHeaders)
        },
    ) { store ->
        runDesktopComposeUiTest(width = 720, height = 1600) {
            setContent {
                ZhihuTheme {
                    CompositionLocalProvider(LocalNavigator provides Navigator({}, {}), LocalZhihuAccountStore provides store) {
                        SystemAndUpdateSettingsScreen()
                    }
                }
            }
            onNode(hasText("检查更新") and hasClickAction()).performScrollTo().performClick()
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("999.0.0", substring = true).fetchSemanticsNodes().isNotEmpty() }
            onAllNodesWithText("下载更新").assertCountEquals(0)
            onNode(hasText("前往下载页面") and hasClickAction()).assertIsEnabled()
        }
    }

    private fun withUpdateServer(engine: MockEngine, body: (ZhihuAccountStore) -> Unit) = withIsolatedDesktopHome {
        val originalVersion = System.getProperty("zhihu.version")
        val store = ZhihuAccountStore(ZhihuAccountRepository(Path(Files.createTempDirectory("update-account-").resolve("account.json").toString())), engine)
        try {
            System.setProperty("zhihu.version", "1.0.0")
            body(store)
        } finally {
            resetSystemUpdateState()
            if (originalVersion == null) System.clearProperty("zhihu.version") else System.setProperty("zhihu.version", originalVersion)
            store.close()
        }
    }

    private companion object {
        val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

        fun releaseJson(tag: String) =
            """{"tag_name":"$tag","html_url":"https://github.com/zly2006/zhihu-plus-plus/releases/tag/$tag","assets":[]}"""
    }
}
