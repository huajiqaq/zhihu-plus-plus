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

package com.github.zly2006.zhihu.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.theme.ThemeManager
import com.github.zly2006.zhihu.theme.ThemeMode
import com.github.zly2006.zhihu.theme.ThemeStyle
import com.github.zly2006.zhihu.theme.ZhihuTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ButtonContentColorTest {
    @Test
    fun miuixFilledButtonsProvideMaterialContentColor() {
        val original = ThemeManager.snapshot()
        try {
            for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
                ThemeManager.load(original.copy(themeStyle = ThemeStyle.MIUIX, themeMode = mode))
                var expected = Color.Unspecified
                runDesktopComposeUiTest(width = 412, height = 200) {
                    setContent {
                        ZhihuTheme {
                            expected = LocalAppSkinTokens.current.onPrimary
                            AppButton(onClick = {}) { Text("关注") }
                        }
                    }
                    val layouts = mutableListOf<TextLayoutResult>()
                    onNodeWithText("关注").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                    assertEquals(
                        expected,
                        layouts
                            .single()
                            .layoutInput.style.color,
                        "$mode 关注按钮内容色",
                    )
                }
            }
        } finally {
            ThemeManager.load(original)
        }
    }

    @Test
    fun miuixButtonOverloadsKeepTheirNativeActionTiers() {
        val original = ThemeManager.snapshot()
        try {
            ThemeManager.load(original.copy(themeStyle = ThemeStyle.MIUIX, themeMode = ThemeMode.LIGHT, amoledTheme = false))
            var secondary = Color.Unspecified
            var onSecondary = Color.Unspecified
            var primary = Color.Unspecified
            runDesktopComposeUiTest(width = 412, height = 260) {
                setContent {
                    ZhihuTheme {
                        secondary = LocalAppSkinTokens.current.secondaryVariant
                        onSecondary = LocalAppSkinTokens.current.onSecondaryVariant
                        primary = LocalAppSkinTokens.current.primary
                        Column {
                            AppOutlinedButton(onClick = {}, modifier = Modifier.testTag("secondary_action")) {
                                Text("次级动作")
                            }
                            AppButton(onClick = {}, contentColor = Color.Yellow, modifier = Modifier.testTag("primary_action")) {
                                Text("自定义主按钮文字")
                            }
                        }
                    }
                }
                val layouts = mutableListOf<TextLayoutResult>()
                onNodeWithText("次级动作").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertEquals(onSecondary, layouts.single().layoutInput.style.color)
                for ((tag, expected) in listOf("secondary_action" to secondary, "primary_action" to primary)) {
                    val pixels = onNodeWithTag(tag).captureToImage().toPixelMap()
                    assertEquals(expected.toArgb(), pixels[pixels.width / 2, 3].toArgb(), tag)
                }
            }
        } finally {
            ThemeManager.load(original)
        }
    }

    @Test
    fun materialButtonWithOnlyErrorContainerUsesOnError() {
        val original = ThemeManager.snapshot()
        try {
            for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
                ThemeManager.load(original.copy(themeStyle = ThemeStyle.MATERIAL_EXPRESSIVE, themeMode = mode))
                var onError = Color.Unspecified
                runDesktopComposeUiTest(width = 412, height = 180) {
                    setContent {
                        ZhihuTheme {
                            onError = MaterialTheme.colorScheme.onError
                            AppButton(onClick = {}, containerColor = MaterialTheme.colorScheme.error) { Text("清空全部") }
                        }
                    }
                    val layouts = mutableListOf<TextLayoutResult>()
                    onNodeWithText("清空全部").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                    assertEquals(onError, layouts.single().layoutInput.style.color, "$mode 清空按钮文字色")
                }
            }
        } finally {
            ThemeManager.load(original)
        }
    }

    @Test
    fun materialTextButtonCustomColorStillUsesDisabledForeground() {
        val original = ThemeManager.snapshot()
        try {
            for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
                ThemeManager.load(original.copy(themeStyle = ThemeStyle.MATERIAL_EXPRESSIVE, themeMode = mode))
                var disabledColor = Color.Unspecified
                runDesktopComposeUiTest(width = 412, height = 180) {
                    setContent {
                        ZhihuTheme {
                            disabledColor = ButtonDefaults.textButtonColors(contentColor = Color.Red).disabledContentColor
                            AppTextButton("删除", {}, enabled = false, contentColor = Color.Red, modifier = Modifier.testTag("disabled_delete"))
                        }
                    }
                    onNodeWithTag("disabled_delete").assertIsNotEnabled()
                    val layouts = mutableListOf<TextLayoutResult>()
                    onNodeWithText("删除").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                    assertEquals(disabledColor, layouts.single().layoutInput.style.color, mode.name)
                }
            }
        } finally {
            ThemeManager.load(original)
        }
    }

    @Test
    fun miuixIconButtonsPreserveExplicitEnabledDisabledAndCheckedColors() {
        val original = ThemeManager.snapshot()
        try {
            ThemeManager.load(original.copy(themeStyle = ThemeStyle.MIUIX, themeMode = ThemeMode.DARK))
            runDesktopComposeUiTest(width = 412, height = 260) {
                setContent {
                    ZhihuTheme {
                        Column {
                            AppIconButton(
                                onClick = {},
                                colors = IconButtonDefaults.iconButtonColors(contentColor = Color.Red),
                            ) { Text("普通图标") }
                            AppIconButton(
                                onClick = {},
                                enabled = false,
                                colors = IconButtonDefaults.iconButtonColors(disabledContentColor = Color.Green),
                            ) { Text("禁用图标") }
                            AppIconToggleButton(
                                checked = true,
                                onCheckedChange = {},
                                colors = IconButtonDefaults.iconToggleButtonColors(checkedContentColor = Color.Blue),
                            ) { Text("选中图标") }
                        }
                    }
                }
                for ((label, expected) in listOf("普通图标" to Color.Red, "禁用图标" to Color.Green, "选中图标" to Color.Blue)) {
                    val layouts = mutableListOf<TextLayoutResult>()
                    onNodeWithText(label).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                    assertEquals(expected, layouts.single().layoutInput.style.color, label)
                }
            }
        } finally {
            ThemeManager.load(original)
        }
    }

    @Test
    fun miuixFloatingActionButtonPreservesExplicitForeground() {
        val original = ThemeManager.snapshot()
        try {
            for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
                ThemeManager.load(original.copy(themeStyle = ThemeStyle.MIUIX, themeMode = mode))
                runDesktopComposeUiTest(width = 412, height = 180) {
                    setContent {
                        ZhihuTheme {
                            AppFloatingActionButton(onClick = {}, containerColor = Color.Blue, contentColor = Color.White) {
                                Text("悬浮动作")
                            }
                        }
                    }
                    val layouts = mutableListOf<TextLayoutResult>()
                    onNodeWithText("悬浮动作").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                    assertEquals(Color.White, layouts.single().layoutInput.style.color, mode.name)
                }
            }
        } finally {
            ThemeManager.load(original)
        }
    }

    @Test
    fun miuixButtonsApplyExplicitContentPadding() {
        val original = ThemeManager.snapshot()
        try {
            ThemeManager.load(original.copy(themeStyle = ThemeStyle.MIUIX, themeMode = ThemeMode.LIGHT))
            var expectedPaddingPx = 0f
            runDesktopComposeUiTest(width = 412, height = 220) {
                setContent {
                    ZhihuTheme {
                        expectedPaddingPx = with(androidx.compose.ui.platform.LocalDensity.current) { 36.dp.toPx() }
                        Column {
                            AppButton(onClick = {}, contentPadding = PaddingValues(horizontal = 28.dp, vertical = 18.dp), modifier = Modifier.testTag("padded_button")) {
                                Text("按钮内边距", Modifier.testTag("padded_button_text"))
                            }
                            AppTextButton(onClick = {}, contentPadding = PaddingValues(horizontal = 28.dp, vertical = 18.dp), modifier = Modifier.testTag("padded_text_button")) {
                                Text("文字按钮内边距", Modifier.testTag("padded_text_button_text"))
                            }
                        }
                    }
                }
                for (tag in listOf("padded_button", "padded_text_button")) {
                    val button = onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
                    val text = onNodeWithTag("${tag}_text", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                    assertEquals(expectedPaddingPx, button.height - text.height, 1f, tag)
                }
            }
        } finally {
            ThemeManager.load(original)
        }
    }

    @Test
    fun miuixCardPreservesExplicitContainerColorAndItsForeground() {
        val original = ThemeManager.snapshot()
        try {
            ThemeManager.load(original.copy(themeStyle = ThemeStyle.MIUIX, themeMode = ThemeMode.LIGHT))
            var primary = Color.Unspecified
            var onPrimary = Color.Unspecified
            runDesktopComposeUiTest(width = 412, height = 180) {
                setContent {
                    ZhihuTheme {
                        primary = MaterialTheme.colorScheme.primary
                        onPrimary = MaterialTheme.colorScheme.onPrimary
                        AppCard(containerColor = primary, shape = RectangleShape) {
                            Box(Modifier.size(100.dp).testTag("custom_card_content")) { Text("卡片内容") }
                        }
                    }
                }
                val pixels = onNodeWithTag("custom_card_content").captureToImage().toPixelMap()
                assertEquals(primary.toArgb(), pixels[pixels.width / 2, pixels.height - 3].toArgb())
                val layouts = mutableListOf<TextLayoutResult>()
                onNodeWithText("卡片内容").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertEquals(onPrimary, layouts.single().layoutInput.style.color)
            }
        } finally {
            ThemeManager.load(original)
        }
    }

    @Test
    fun miuixProgressPrimitivesHonorExplicitColorsAndSize() {
        val original = ThemeManager.snapshot()
        try {
            ThemeManager.load(original.copy(themeStyle = ThemeStyle.MIUIX, themeMode = ThemeMode.DARK))
            var requestedSpinnerSizePx = 0
            var nativeSpinnerSizePx = 0
            runDesktopComposeUiTest(width = 412, height = 220) {
                setContent {
                    ZhihuTheme {
                        val density = androidx.compose.ui.platform.LocalDensity.current
                        requestedSpinnerSizePx = with(density) { 40.dp.roundToPx() }
                        nativeSpinnerSizePx = with(density) { 20.dp.roundToPx() }
                        Column {
                            AppLinearProgress(0.5f, modifier = Modifier.width(240.dp).testTag("custom_progress"), color = Color.Red, trackColor = Color.Green)
                            AppSpinner(size = 40.dp, modifier = Modifier.testTag("requested_spinner"))
                            AppSpinner(size = null, modifier = Modifier.testTag("native_spinner"))
                        }
                    }
                }
                val progress = onNodeWithTag("custom_progress").captureToImage().toPixelMap()
                val colors = (0 until progress.width).map { x -> progress[x, progress.height / 2].toArgb() }.toSet()
                kotlin.test.assertTrue(Color.Red.toArgb() in colors, "必须绘制显式前景色")
                kotlin.test.assertTrue(Color.Green.toArgb() in colors, "必须绘制显式轨道色")
                val requested = onNodeWithTag("requested_spinner").captureToImage()
                assertEquals(requestedSpinnerSizePx, requested.width, "必须保留请求的 spinner 宽度")
                assertEquals(requestedSpinnerSizePx, requested.height, "必须保留请求的 spinner 高度")
                val native = onNodeWithTag("native_spinner").captureToImage()
                assertEquals(nativeSpinnerSizePx, native.width, "size=null 必须使用 Miuix 原生小圆圈宽度")
                assertEquals(nativeSpinnerSizePx, native.height, "size=null 必须使用 Miuix 原生小圆圈高度")
            }
        } finally {
            ThemeManager.load(original)
        }
    }
}
