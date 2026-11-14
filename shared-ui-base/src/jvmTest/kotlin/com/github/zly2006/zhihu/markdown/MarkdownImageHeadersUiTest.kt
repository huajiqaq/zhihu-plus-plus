package com.github.zly2006.zhihu.markdown

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import com.github.zly2006.zhihu.account.LocalZhihuAccountStore
import com.github.zly2006.zhihu.account.ZhihuAccountRepository
import com.github.zly2006.zhihu.account.ZhihuAccountSession
import com.github.zly2006.zhihu.account.ZhihuAccountStore
import io.ktor.client.engine.mock.MockEngine
import kotlinx.io.files.Path
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class MarkdownImageHeadersUiTest {
    @Test
    fun realImageModelsOnlyAttachSessionCookiesToZhihuHosts() {
        val directory = Files.createTempDirectory("image-header-store-")
        val store = ZhihuAccountStore(ZhihuAccountRepository(Path(directory.resolve("account.json").toString())), MockEngine { error("图片模型测试不应发起网络请求") })
        store.save(ZhihuAccountSession(login = true, username = "image-header-fixture", cookies = mutableMapOf("z_c0" to "synthetic-image-token"), userAgent = "image-fixture-agent"))
        val allowed = arrayOf("https://zhihu.com/image", "https://www.zhihu.com/image", "https://api.zhihu.com/image")
        val rejected = arrayOf("https://pic1.zhimg.com/image", "https://zhihu.com.evil.invalid/image", "https://evilzhihu.com/image", "data:image/png;base64,AA==", "not a URL")
        val requests = mutableMapOf<String, ImageRequest>()
        try {
            runSkikoComposeUiTest {
                setContent {
                    CompositionLocalProvider(LocalZhihuAccountStore provides store) {
                        for (url in allowed + rejected) requests[url] = rememberMarkdownImageModel(url) as ImageRequest
                    }
                }
                runOnIdle {
                    for (url in allowed) assertEquals("z_c0=synthetic-image-token", requests.getValue(url).httpHeaders["Cookie"])
                    for (url in rejected) assertNull(requests.getValue(url).httpHeaders["Cookie"], "外部图片模型不能携带知乎凭据")
                    requests.values.forEach { assertEquals("image-fixture-agent", it.httpHeaders["User-Agent"]) }
                }
            }
        } finally {
            store.close()
            directory.toFile().deleteRecursively()
        }
    }
}
