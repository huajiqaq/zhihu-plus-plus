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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.github.zly2006.zhihu.theme.ThemeManager
import com.github.zly2006.zhihu.theme.ThemeMode
import com.github.zly2006.zhihu.theme.ThemeStyle
import com.github.zly2006.zhihu.theme.ZhihuTheme
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Recent
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import androidx.compose.material.icons.outlined.Home as OutlinedHome
import androidx.compose.material.icons.outlined.ThumbUp as OutlinedThumbUp
import androidx.compose.material.icons.rounded.Home as RoundedHome
import androidx.compose.material.icons.sharp.Home as SharpHome
import androidx.compose.material.icons.twotone.Home as TwoToneHome
import top.yukonga.miuix.kmp.icon.extended.Add as MiuixAdd
import top.yukonga.miuix.kmp.icon.extended.Home as MiuixHome
import top.yukonga.miuix.kmp.icon.extended.Reply as MiuixReply
import top.yukonga.miuix.kmp.icon.extended.Send as MiuixSend
import top.yukonga.miuix.kmp.icon.extended.Settings as MiuixSettings

@OptIn(ExperimentalTestApi::class)
class AppIconContractTest {
    @Test
    fun backButtonKeepsFortyDpSurfaceAndFortyEightDpTouchTarget() {
        val originalTheme = ThemeManager.snapshot()
        val originalHome = System.getProperty("user.home")
        val directory = Files.createTempDirectory("back-button-contract-")
        try {
            System.setProperty("user.home", directory.toString())
            for (style in ThemeStyle.entries) {
                ThemeManager.load(originalTheme.copy(themeStyle = style, themeMode = ThemeMode.LIGHT))
                var clicks = 0
                var background = Color.Unspecified
                var direction by mutableStateOf(LayoutDirection.Ltr)
                runSkikoComposeUiTest(size = Size(100f, 100f), density = Density(1f)) {
                    setContent {
                        ZhihuTheme {
                            background = MaterialTheme.colorScheme.surface
                            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                                Box(Modifier.size(100.dp).background(background).testTag("button_host"), contentAlignment = Alignment.Center) {
                                    AppBackButton({ clicks++ }, Modifier.testTag("back_button"))
                                }
                            }
                        }
                    }
                    waitForIdle()
                    val pixels = onNodeWithTag("button_host").captureToImage().toPixelMap()
                    assertEquals(background, pixels[27, 50], "$style 48dp 触摸区不能全部画成圆底")
                    assertTrue(pixels[31, 50] != background, "$style 40dp 圆底必须仍然可见")
                    assertEquals(background, pixels[31, 31], "$style 圆底必须裁掉角落")
                    val button = onNodeWithTag("back_button").fetchSemanticsNode()
                    assertEquals(48, button.size.width, "$style 保留 48dp 布局槽")
                    assertEquals(48, button.size.height)
                    val leftToRight = onNodeWithTag("button_host").captureToImage().toPixelMap()
                    runOnIdle { direction = LayoutDirection.Rtl }
                    waitForIdle()
                    val rightToLeft = onNodeWithTag("button_host").captureToImage().toPixelMap()
                    for (x in 30 until 70) {
                        for (y in 30 until 70) {
                            val expected = leftToRight[99 - x, y]
                            val actual = rightToLeft[x, y]
                            assertTrue(
                                kotlin.math.abs(expected.red - actual.red) <= 2f / 255f &&
                                    kotlin.math.abs(expected.green - actual.green) <= 2f / 255f &&
                                    kotlin.math.abs(expected.blue - actual.blue) <= 2f / 255f,
                                "$style 默认返回字形须随 RTL 镜像 ($x,$y)：$expected → $actual",
                            )
                        }
                    }
                    onNodeWithTag("back_button").performTouchInput { click(Offset(1f, 24f)) }
                    waitForIdle()
                    assertEquals(1, clicks, "$style 可见圆底外的触摸边缘仍可返回")
                }
            }
        } finally {
            if (originalHome == null) System.clearProperty("user.home") else System.setProperty("user.home", originalHome)
            ThemeManager.load(originalTheme)
            directory.toFile().deleteRecursively()
        }
    }

    /** 相对各自宿主比较原生大小顶栏，不能把双栏或阅读列偏移误当成返回边距。 */
    @Test
    fun topBarsKeepNativeBackAndActionMarginsInBothLayoutDirections() {
        val originalTheme = ThemeManager.snapshot()
        val originalHome = System.getProperty("user.home")
        val directory = Files.createTempDirectory("top-bar-margin-contract-")
        try {
            System.setProperty("user.home", directory.toString())
            for (style in ThemeStyle.entries) {
                ThemeManager.load(originalTheme.copy(themeStyle = style, themeMode = ThemeMode.LIGHT))
                var direction by mutableStateOf(LayoutDirection.Ltr)
                runSkikoComposeUiTest(size = Size(480f, 520f), density = Density(1f)) {
                    setContent {
                        ZhihuTheme {
                            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                                    Column(Modifier.width(320.dp)) {
                                        for (kind in listOf("regular", "compact", "page")) {
                                            val modifier = Modifier.testTag("${kind}_host")
                                            val actions: @Composable RowScope.() -> Unit = {
                                                AppIconButton({}, Modifier.testTag("${kind}_action")) {
                                                    AppIcon(Icons.Filled.Settings, null)
                                                }
                                            }
                                            when (kind) {
                                                "regular" -> AppTopBar(
                                                    title = { Text("标题") },
                                                    modifier = modifier,
                                                    onBack = {},
                                                    backTag = "${kind}_back",
                                                    actions = actions,
                                                )

                                                "compact" -> AppCompactTopBar(
                                                    title = "标题",
                                                    modifier = modifier,
                                                    onBack = {},
                                                    backTag = "${kind}_back",
                                                    actions = actions,
                                                )

                                                "page" -> AppPageTitleBar(
                                                    title = "标题",
                                                    modifier = modifier,
                                                    onBack = {},
                                                    backTag = "${kind}_back",
                                                    actions = actions,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    for (layoutDirection in LayoutDirection.entries) {
                        runOnIdle { direction = layoutDirection }
                        waitForIdle()
                        val margins = listOf("regular", "compact", "page").map { kind ->
                            val host = onNodeWithTag("${kind}_host").fetchSemanticsNode().boundsInRoot
                            val back = onNodeWithTag("${kind}_back").fetchSemanticsNode().boundsInRoot
                            val action = onNodeWithTag("${kind}_action").fetchSemanticsNode().boundsInRoot
                            if (layoutDirection == LayoutDirection.Ltr) {
                                (back.left - host.left) to (host.right - action.right)
                            } else {
                                (host.right - back.right) to (action.left - host.left)
                            }
                        }
                        assertEquals(margins[1], margins[0], "$style $layoutDirection 普通顶栏必须保留原生返回与动作边距")
                        assertEquals(margins[1], margins[2], "$style $layoutDirection 大小顶栏必须遵循同一皮肤的边距")
                    }
                }
            }
        } finally {
            if (originalHome == null) System.clearProperty("user.home") else System.setProperty("user.home", originalHome)
            ThemeManager.load(originalTheme)
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun miuixTopBarHonorsCustomAndZeroInsetsWithoutClippingCompoundTitle() {
        val originalTheme = ThemeManager.snapshot()
        val originalHome = System.getProperty("user.home")
        val directory = Files.createTempDirectory("top-bar-inset-contract-")
        try {
            System.setProperty("user.home", directory.toString())
            ThemeManager.load(originalTheme.copy(themeStyle = ThemeStyle.MIUIX, themeMode = ThemeMode.LIGHT))
            var insets by mutableStateOf(WindowInsets(28, 24, 12, 8))
            runSkikoComposeUiTest(size = Size(480f, 520f), density = Density(1f)) {
                setContent {
                    ZhihuTheme {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                            AppTopBar(
                                title = {
                                    Column {
                                        Text("标题一\n标题二\n标题三\n标题四", Modifier.testTag("compound_title"))
                                        Text("作者信息", Modifier.testTag("compound_author"))
                                    }
                                },
                                modifier = Modifier.width(320.dp).testTag("inset_host"),
                                onBack = {},
                                backTag = "inset_back",
                                actions = {
                                    AppIconButton({}, Modifier.testTag("inset_action")) { AppIcon(Icons.Filled.Settings, null) }
                                },
                                centered = true,
                                windowInsets = insets,
                            )
                        }
                    }
                }
                waitForIdle()
                val insetHost = onNodeWithTag("inset_host").fetchSemanticsNode().boundsInRoot
                val insetBack = onNodeWithTag("inset_back").fetchSemanticsNode().boundsInRoot
                val insetAction = onNodeWithTag("inset_action").fetchSemanticsNode().boundsInRoot
                runOnIdle { insets = WindowInsets(0, 0, 0, 0) }
                waitForIdle()
                val host = onNodeWithTag("inset_host").fetchSemanticsNode().boundsInRoot
                val back = onNodeWithTag("inset_back").fetchSemanticsNode().boundsInRoot
                val action = onNodeWithTag("inset_action").fetchSemanticsNode().boundsInRoot
                assertEquals(28f, insetBack.left - back.left, "自定义左侧边距必须作用于返回入口")
                assertEquals(12f, action.right - insetAction.right, "自定义右侧边距必须作用于动作入口")
                assertEquals(24f, insetBack.top - back.top, "零边距必须移除调用侧的顶部避让")
                assertEquals(32f, insetHost.height - host.height, "零边距必须移除顶部与底部避让")
                val layouts = mutableListOf<TextLayoutResult>()
                val title = onNodeWithTag("compound_title", useUnmergedTree = true)
                title.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertEquals(4, layouts.single().lineCount, "普通顶栏不能把复合标题改成单行字符串")
                assertTrue(!layouts.single().didOverflowHeight, "标题必须按全文测量")
                val titleBounds = title.fetchSemanticsNode().boundsInRoot
                val authorBounds = onNodeWithTag("compound_author", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                assertTrue(titleBounds.height >= layouts.single().size.height - 1f, "完整标题不能被顶栏剪裁")
                assertTrue(authorBounds.top >= titleBounds.bottom, "复合标题中的作者行必须完整保留")
                assertTrue(host.height > AppDimens.MiuixTopBarHeight.value, "顶栏必须随全文内容增长，不能钉死最小高度")
            }
        } finally {
            if (originalHome == null) System.clearProperty("user.home") else System.setProperty("user.home", originalHome)
            ThemeManager.load(originalTheme)
            directory.toFile().deleteRecursively()
        }
    }

    /** 换皮肤不能误替换关注/热度/赞同，丢失 RTL、官方几何或调用侧颜色。 */
    @Test
    fun skinSwitchPreservesFallbacksMirroringAndIconContent() {
        val originalTheme = ThemeManager.snapshot()
        val originalHome = System.getProperty("user.home")
        val directory = Files.createTempDirectory("icon-skin-contract-")
        val inputs = listOf(
            Icons.Filled.Home to MiuixIcons.MiuixHome,
            Icons.Outlined.OutlinedHome to MiuixIcons.MiuixHome,
            Icons.Rounded.RoundedHome to MiuixIcons.MiuixHome,
            Icons.Sharp.SharpHome to MiuixIcons.MiuixHome,
            Icons.TwoTone.TwoToneHome to MiuixIcons.MiuixHome,
            Icons.Filled.Newspaper to MiuixIcons.Notes,
            Icons.Filled.Summarize to MiuixIcons.Notes,
            Icons.Filled.History to MiuixIcons.Recent,
            Icons.Filled.Bookmarks to MiuixIcons.Folder,
            Icons.Filled.AccountCircle to MiuixIcons.Contacts,
            Icons.Filled.Add to MiuixIcons.MiuixAdd,
            Icons.Filled.Settings to MiuixIcons.MiuixSettings,
            Icons.AutoMirrored.Filled.ArrowBack to MiuixIcons.Back,
            Icons.AutoMirrored.Filled.KeyboardArrowRight to MiuixIcons.Basic.ArrowRight,
            Icons.AutoMirrored.Filled.Reply to MiuixIcons.MiuixReply,
            Icons.AutoMirrored.Filled.Send to MiuixIcons.MiuixSend,
            Icons.Filled.Group to null,
            Icons.Filled.ExpandLess to null,
            Icons.Filled.ExpandMore to null,
            Icons.Filled.Whatshot to null,
            Icons.Filled.ThumbUp to null,
            Icons.Outlined.OutlinedThumbUp to null,
            ImageVector.Builder("Brand.Home", 24.dp, 24.dp, 24f, 24f).build() to null,
            ImageVector.Builder("Filled.OutlinedThumbUp", 24.dp, 24.dp, 24f, 24f).build() to null,
        )
        val inheritedTint = Color(0xFF7A315D)
        val explicitTint = Color(0xFF167D63)
        try {
            System.setProperty("user.home", directory.toString())
            ThemeManager.load(originalTheme.copy(themeStyle = ThemeStyle.MATERIAL_EXPRESSIVE))
            runSkikoComposeUiTest(size = Size(180f, 60f), density = Density(1f)) {
                var direction by mutableStateOf(LayoutDirection.Ltr)
                var vectors = emptyList<ImageVector>()
                setContent {
                    CompositionLocalProvider(
                        LocalContentColor provides inheritedTint,
                        LocalLayoutDirection provides direction,
                    ) {
                        vectors = inputs.map { appIconVector(it.first) }
                        val expected = if (ThemeManager.getThemeStyle() == ThemeStyle.MIUIX) MiuixIcons.MiuixHome else Icons.Filled.Home
                        Row {
                            AppIcon(Icons.Filled.Home, "主页", Modifier.size(32.dp).testTag("inherited"))
                            Icon(expected, null, Modifier.size(32.dp).testTag("expected_inherited"), tint = inheritedTint)
                            AppIcon(Icons.Filled.Home, "主页自定义色", Modifier.size(32.dp).testTag("explicit"), tint = explicitTint)
                            Icon(expected, null, Modifier.size(32.dp).testTag("expected_explicit"), tint = explicitTint)
                            AppIcon(Icons.AutoMirrored.Filled.ArrowBack, "返回", Modifier.size(32.dp).testTag("back"))
                        }
                    }
                }
                waitForIdle()
                inputs.forEachIndexed { index, (image, _) -> assertSame(image, vectors[index], image.name) }

                runOnIdle { ThemeManager.load(originalTheme.copy(themeStyle = ThemeStyle.MIUIX)) }
                waitForIdle()
                inputs.forEachIndexed { index, (image, native) ->
                    val actual = vectors[index]
                    when {
                        native == null -> assertSame(image, actual, image.name)
                        !image.autoMirror || native.autoMirror -> assertSame(native, actual, image.name)
                        else -> {
                            assertTrue(actual.autoMirror, image.name)
                            assertEquals(native.name, actual.name)
                            assertEquals(native.defaultWidth, actual.defaultWidth)
                            assertEquals(native.defaultHeight, actual.defaultHeight)
                            assertEquals(native.viewportWidth, actual.viewportWidth)
                            assertEquals(native.viewportHeight, actual.viewportHeight)
                            assertEquals(native.tintColor, actual.tintColor)
                            assertEquals(native.tintBlendMode, actual.tintBlendMode)
                            // VectorGroup 的深相等包含嵌套变换、裁切以及全部路径字段。
                            assertEquals(native.root, actual.root.single(), image.name)
                        }
                    }
                }
                onNodeWithContentDescription("主页").assertExists()
                onNodeWithContentDescription("主页自定义色").assertExists()
                for (tag in listOf("inherited", "explicit")) {
                    val actual = onNodeWithTag(tag).captureToImage().toPixelMap()
                    val expected = onNodeWithTag("expected_$tag").captureToImage().toPixelMap()
                    assertEquals(32, actual.width)
                    assertEquals(32, actual.height)
                    for (x in 0 until actual.width) {
                        for (y in 0 until actual.height) {
                            assertEquals(expected[x, y], actual[x, y], "$tag ($x, $y)")
                        }
                    }
                }

                val leftToRight = onNodeWithTag("back").captureToImage().toPixelMap()
                assertTrue(
                    (0 until leftToRight.width).any { x ->
                        (0 until leftToRight.height).any { y -> leftToRight[x, y] != leftToRight[leftToRight.width - 1 - x, y] }
                    },
                    "返回字形必须具有可见方向，不能用空图通过镜像断言",
                )
                runOnIdle { direction = LayoutDirection.Rtl }
                waitForIdle()
                val rightToLeft = onNodeWithTag("back").captureToImage().toPixelMap()
                for (x in 0 until leftToRight.width) {
                    for (y in 0 until leftToRight.height) {
                        assertEquals(leftToRight[leftToRight.width - 1 - x, y], rightToLeft[x, y], "RTL 返回 ($x, $y)")
                    }
                }
                runOnIdle { ThemeManager.load(originalTheme.copy(themeStyle = ThemeStyle.MATERIAL_EXPRESSIVE)) }
                waitForIdle()
                inputs.forEachIndexed { index, (image, _) -> assertSame(image, vectors[index], "M3E ${image.name}") }
            }
        } finally {
            if (originalHome == null) System.clearProperty("user.home") else System.setProperty("user.home", originalHome)
            ThemeManager.load(originalTheme)
            directory.toFile().deleteRecursively()
        }
    }
}
