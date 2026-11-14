package com.github.zly2006.zhihu.ui.subscreens

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.github.zly2006.zhihu.theme.ZhihuTheme
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class SystemUpdateStatusCardTest {
    private val update = SystemUpdateState.UpdateAvailable(
        version = "2.0.0",
        isNightly = false,
        releaseNotes = "- 修复了一些问题",
        downloadUrl = "https://example.invalid/zhihu.apk",
        cnDownloadUrl = null,
        opensExternally = false,
    )

    @Test
    fun downloadingKeepsTheVersion() = renderCard(SystemUpdateState.Downloading(update)) {
        onNodeWithText("正式版 2.0.0", substring = true).assertExists()
        onNode(hasText("下载中...") and hasClickAction()).assertIsNotEnabled()
        onNodeWithText("更新说明").assertExists()
    }

    @Test
    fun downloadedKeepsTheVersionAndOffersInstall() = renderCard(SystemUpdateState.Downloaded(update)) {
        onNodeWithText("正式版 2.0.0", substring = true).assertExists()
        onNode(hasText("安装更新") and hasClickAction()).assertIsEnabled()
    }

    private fun renderCard(
        state: SystemUpdateState,
        assertions: ComposeUiTest.() -> Unit,
    ) = withIsolatedDesktopHome {
        runDesktopComposeUiTest(width = 720, height = 900) {
            setContent {
                ZhihuTheme {
                    SystemUpdateStatusCard(
                        state = state,
                        currentVersion = "1.0.0 · Full 版",
                        installSupported = true,
                        onCheck = {},
                        onDownload = {},
                        onInstall = {},
                        onSkip = {},
                        onOpenCnDownload = {},
                        onOpenUrl = {},
                    )
                }
            }
            assertions()
        }
    }
}
