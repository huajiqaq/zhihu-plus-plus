package com.github.zly2006.zhihu.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.TextUnit
import com.github.zly2006.zhihu.theme.ThemeManager
import com.github.zly2006.zhihu.theme.ThemeStyle
import com.github.zly2006.zhihu.theme.ZhihuTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class AppDropdownMenuTypographyTest {
    @Test
    fun menuItemsUseTheSelectedSkinsTypography() {
        val originalTheme = ThemeManager.snapshot()
        val originalHome = System.getProperty("user.home")
        val directory = Files.createTempDirectory("menu-typography-contract-")
        try {
            System.setProperty("user.home", directory.toString())
            for (style in ThemeStyle.entries) {
                ThemeManager.load(originalTheme.copy(themeStyle = style))
                var expectedSize = TextUnit.Unspecified
                runDesktopComposeUiTest(width = 412, height = 300) {
                    setContent {
                        ZhihuTheme {
                            expectedSize = if (style == ThemeStyle.MIUIX) {
                                MiuixTheme.textStyles.main.fontSize
                            } else {
                                MaterialTheme.typography.labelLarge.fontSize
                            }
                            Box {
                                AppDropdownMenu(expanded = true, onDismissRequest = {}) {
                                    AppDropdownMenuItem(
                                        text = { Text("更多操作", Modifier.testTag("menu_item_text")) },
                                        onClick = {},
                                    )
                                }
                            }
                        }
                    }
                    val results = mutableListOf<TextLayoutResult>()
                    onNodeWithTag("menu_item_text", useUnmergedTree = true)
                        .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action -> action(results) }
                    assertEquals(
                        expectedSize,
                        results
                            .single()
                            .layoutInput.style.fontSize,
                        style.name,
                    )
                }
            }
        } finally {
            if (originalHome == null) System.clearProperty("user.home") else System.setProperty("user.home", originalHome)
            ThemeManager.load(originalTheme)
            directory.toFile().deleteRecursively()
        }
    }
}
