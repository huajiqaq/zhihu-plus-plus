package com.github.zly2006.zhihu.ui.components

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.github.zly2006.zhihu.theme.ZhihuTheme
import kotlinx.coroutines.awaitCancellation
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class ExportDialogStateTest {
    @Test
    fun commentCountSliderIsDisabledWhileExporting() = runDesktopComposeUiTest(width = 412, height = 1200) {
        setContent {
            ZhihuTheme {
                ExportDialogComponent(
                    showDialog = true,
                    isHtmlExportSupported = true,
                    isImageExportSupported = true,
                    onDismiss = {},
                    onExportHtml = { _, _ -> awaitCancellation() },
                    onExportImage = { _, _ -> awaitCancellation() },
                    onExportMarkdown = {},
                    onExportImageWithComments = { _, _, _ -> awaitCancellation() },
                )
            }
        }
        /** 排除同样带 ProgressBarRangeInfo 的不确定进度指示器。 */
        val slider = onNode(
            SemanticsMatcher("评论数量滑杆") { node: SemanticsNode ->
                node.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)?.range == 0f..10f
            },
        )
        slider.assertIsEnabled()
        onNodeWithText("导出为图片").performClick()
        waitForIdle()
        slider.assertIsNotEnabled()
    }
}
