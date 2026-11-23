package com.github.zly2006.zhihu.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import com.github.zly2006.zhihu.theme.ThemeManager
import com.github.zly2006.zhihu.theme.ThemeMode
import com.github.zly2006.zhihu.theme.ThemeStyle
import com.github.zly2006.zhihu.theme.ZhihuTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ExpressiveControlsTest {
    @Test
    fun filledTonalIconButtonKeepsBoundsAndClicksOnlyOnEnabledRelease() {
        val original = ThemeManager.snapshot()
        try {
            ThemeManager.load(original.copy(themeStyle = ThemeStyle.MATERIAL_EXPRESSIVE, themeMode = ThemeMode.LIGHT))
            runSkikoComposeUiTest(size = Size(160f, 160f), density = Density(1f)) {
                var enabled by mutableStateOf(true)
                var clicks = 0
                setContent {
                    ZhihuTheme {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            AppFilledTonalIconButton(
                                onClick = { clicks++ },
                                enabled = enabled,
                                modifier = Modifier.testTag("filled_tonal_button"),
                            ) {
                                AppIcon(Icons.Filled.ExpandMore, "展开")
                            }
                        }
                    }
                }
                val button = onNodeWithTag("filled_tonal_button").assertIsDisplayed()
                val restingBounds = button.fetchSemanticsNode().boundsInRoot
                button.performTouchInput { down(center) }
                try {
                    assertEquals(restingBounds, button.fetchSemanticsNode().boundsInRoot, "按压不能改变按钮布局边界")
                    runOnIdle { assertEquals(0, clicks, "按下不能提前触发点击") }
                } finally {
                    button.performTouchInput { up() }
                }
                assertEquals(restingBounds, button.fetchSemanticsNode().boundsInRoot)
                runOnIdle {
                    assertEquals(1, clicks, "释放应且仅应触发一次点击")
                    enabled = false
                }
                button.assertIsNotEnabled().performTouchInput { click() }
                runOnIdle { assertEquals(1, clicks, "禁用按钮不能响应触摸") }
            }
        } finally {
            ThemeManager.load(original)
        }
    }

    @Test
    fun longDialogSummaryScrollsWithoutHidingItsActions() {
        val original = ThemeManager.snapshot()
        val summary = (1..40).joinToString("\n") { "错误详情第 $it 行：合成异常，保留完整文字供排查。" }
        try {
            for (style in ThemeStyle.entries) {
                ThemeManager.load(original.copy(themeStyle = style, themeMode = ThemeMode.LIGHT))
                runSkikoComposeUiTest(size = Size(720f, 360f), density = Density(1f, 1.5f)) {
                    var confirmed = 0
                    var dismissed = 0
                    setContent {
                        ZhihuTheme {
                            AppScaffold {
                                AppDialog(
                                    title = "错误详情",
                                    summary = summary,
                                    confirmText = "确认错误",
                                    onConfirm = { confirmed++ },
                                    dismissText = "关闭错误",
                                    onDismissRequest = { dismissed++ },
                                )
                            }
                        }
                    }
                    onNodeWithText("确认错误").assertIsDisplayed()
                    onNodeWithText("关闭错误").assertIsDisplayed()
                    val body = onNode(
                        SemanticsMatcher("错误正文滚动区域") { node ->
                            node.config.contains(SemanticsProperties.VerticalScrollAxisRange)
                        },
                    )
                    val range = body.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
                    assertTrue(range.maxValue() > 0f, "$style 长错误正文必须有有效滚动范围")
                    body.performSemanticsAction(SemanticsActions.ScrollBy) { scroll ->
                        assertTrue(scroll(0f, range.maxValue()))
                    }
                    val scrolled = body.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
                    assertTrue(scrolled.value() >= scrolled.maxValue() - 1f, "$style 错误正文末尾必须可达")
                    onNodeWithText(summary, useUnmergedTree = true).assertExists()
                    onNodeWithText("确认错误").assertIsDisplayed().performClick()
                    onNodeWithText("关闭错误").assertIsDisplayed().performClick()
                    assertEquals(1, confirmed)
                    assertEquals(1, dismissed)
                }
            }
        } finally {
            ThemeManager.load(original)
        }
    }

    @Test
    fun alertDialogScrollKeepsDraftAndActionsReachable() {
        val original = ThemeManager.snapshot()
        try {
            for (style in ThemeStyle.entries) {
                ThemeManager.load(original.copy(themeStyle = style, themeMode = ThemeMode.LIGHT))
                runSkikoComposeUiTest(size = Size(720f, 360f), density = Density(1f, 1.5f)) {
                    val draft = TextFieldState("尚未提交的草稿")
                    var confirmed: String? = null
                    setContent {
                        ZhihuTheme {
                            AppScaffold {
                                AppAlertDialog(
                                    onDismissRequest = {},
                                    title = { Text("编辑内容") },
                                    text = {
                                        Column {
                                            AppTextField(draft, modifier = Modifier.testTag("alert_draft"))
                                            repeat(30) { Text("合成说明第 $it 行") }
                                        }
                                    },
                                    confirmButton = { AppTextButton("保存草稿", { confirmed = draft.text.toString() }) },
                                    dismissButton = { AppTextButton("继续编辑", {}) },
                                )
                            }
                        }
                    }
                    onNodeWithTag("alert_draft").performTextInput("补充")
                    val editedDraft = draft.text.toString()
                    assertTrue(editedDraft.contains("补充"))
                    onNodeWithText("合成说明第 29 行", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
                    onNodeWithText("保存草稿").assertIsDisplayed().performClick()
                    onNodeWithText("继续编辑").assertIsDisplayed()
                    assertEquals(editedDraft, confirmed)
                    onNodeWithTag("alert_draft").performScrollTo().assertIsDisplayed()
                    assertEquals(editedDraft, draft.text.toString())
                }
            }
        } finally {
            ThemeManager.load(original)
        }
    }

    @Test
    fun settingSelectorKeepsLastOptionReachable() {
        val original = ThemeManager.snapshot()
        try {
            for (style in ThemeStyle.entries) {
                ThemeManager.load(original.copy(themeStyle = style, themeMode = ThemeMode.LIGHT))
                val selected = mutableStateOf(0)
                var expected = androidx.compose.ui.text.TextStyle.Default
                runDesktopComposeUiTest(width = 360, height = 280) {
                    setContent {
                        ZhihuTheme {
                            expected = MaterialTheme.typography.bodyLarge
                            val owner = LocalNavigationEventDispatcherOwner.current
                                ?: rememberNavigationEventDispatcherOwner(parent = null)
                            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) {
                                AppScaffold {
                                    SettingItemSelect(
                                        title = { Text("选择启动页面", Modifier.testTag("selector_title")) },
                                        options = List(24) { "启动选项 $it" },
                                        selectedIndex = selected.value,
                                        onSelect = { selected.value = it },
                                    )
                                }
                            }
                        }
                    }
                    if (style == ThemeStyle.MATERIAL_EXPRESSIVE) {
                        val layouts = mutableListOf<TextLayoutResult>()
                        onNodeWithTag("selector_title", useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                        assertEquals(
                            expected.fontSize,
                            layouts
                                .single()
                                .layoutInput.style.fontSize,
                        )
                        assertEquals(
                            expected.fontWeight,
                            layouts
                                .single()
                                .layoutInput.style.fontWeight,
                        )
                    }
                    onNodeWithText("选择启动页面").performClick()
                    onNodeWithText("启动选项 23", useUnmergedTree = true)
                        .performScrollTo()
                        .assertIsDisplayed()
                        .performClick()
                    waitUntil(timeoutMillis = 3_000) { selected.value == 23 }
                    onNodeWithText("启动选项 1", useUnmergedTree = true).assertDoesNotExist()
                }
            }
        } finally {
            ThemeManager.load(original)
        }
    }

    @Test
    fun disablingOpenSettingSelectorClosesPopupUntilNextClick() {
        val original = ThemeManager.snapshot()
        try {
            for (style in ThemeStyle.entries) {
                ThemeManager.load(original.copy(themeStyle = style, themeMode = ThemeMode.LIGHT))
                val enabled = mutableStateOf(true)
                var selections = 0
                var rawMenu by mutableStateOf(false)
                var rawSelections = 0
                runDesktopComposeUiTest(width = 360, height = 300) {
                    setContent {
                        ZhihuTheme {
                            val owner = LocalNavigationEventDispatcherOwner.current
                                ?: rememberNavigationEventDispatcherOwner(parent = null)
                            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) {
                                AppScaffold {
                                    Column {
                                        AppTextButton("普通菜单", { rawMenu = true })
                                        AppDropdownMenu(rawMenu, { rawMenu = false }) {
                                            AppDropdownMenuItem(text = { Text("不可用项") }, enabled = false, onClick = { error("禁用项不能执行动作") }, modifier = Modifier.testTag("disabled_menu_item"))
                                            repeat(20) { i ->
                                                AppDropdownMenuItem(text = { Text("菜单 $i") }, onClick = {
                                                    rawSelections++
                                                    rawMenu = false
                                                })
                                            }
                                        }
                                        SettingItemSelect(
                                            title = { Text("可禁用选择行") },
                                            options = listOf("当前值", "可选值"),
                                            selectedIndex = 0,
                                            onSelect = { selections += 1 },
                                            enabled = enabled.value,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    onNodeWithText("可禁用选择行").performClick()
                    onNodeWithText("可选值", useUnmergedTree = true).assertIsDisplayed()
                    runOnIdle { enabled.value = false }
                    onNodeWithText("可选值", useUnmergedTree = true).assertDoesNotExist()
                    assertEquals(0, selections, style.name)
                    runOnIdle { enabled.value = true }
                    onNodeWithText("可选值", useUnmergedTree = true).assertDoesNotExist()
                    onNodeWithText("可禁用选择行").performClick()
                    onNodeWithText("可选值", useUnmergedTree = true).performClick()
                    waitUntil(timeoutMillis = 3_000) { selections == 1 }
                    onNodeWithText("普通菜单").performClick()
                    onNodeWithTag("disabled_menu_item", useUnmergedTree = true).assertIsNotEnabled().performTouchInput { click() }
                    runOnIdle { assertTrue(rawMenu, "$style 禁用项点击不应收起菜单，已执行动作数=$rawSelections") }
                    onNodeWithText("菜单 19", useUnmergedTree = true).performScrollTo().assertIsDisplayed().performClick()
                    waitForIdle()
                    assertEquals(1, rawSelections)
                    onNodeWithText("菜单 19", useUnmergedTree = true).assertDoesNotExist()
                }
            }
        } finally {
            ThemeManager.load(original)
        }
    }

    @Test
    fun settingSlotsCenterOnMultilineTextWithoutCountingBottomControls() {
        val original = ThemeManager.snapshot()
        val rows = listOf("arrow", "switch", "bottom")
        try {
            for (skin in ThemeStyle.entries) {
                ThemeManager.load(original.copy(themeStyle = skin, themeMode = ThemeMode.LIGHT))
                runSkikoComposeUiTest(size = Size(360f, 640f), density = Density(1f)) {
                    setContent {
                        ZhihuTheme {
                            Column {
                                for (row in rows) {
                                    SettingItem(
                                        title = { Text("设置标题", Modifier.testTag("$row.title")) },
                                        description = { Text("第一行说明\n第二行说明\n第三行说明\n第四行说明", Modifier.testTag("$row.summary")) },
                                        icon = { Box(Modifier.size(24.dp).testTag("$row.icon")) },
                                        onClick = if (row == "switch") null else ({}),
                                        checked = if (row == "switch") true else null,
                                        onCheckedChange = {},
                                        endAction = {
                                            val end = Modifier.testTag("$row.end")
                                            when {
                                                row != "switch" -> AppIcon(Icons.Filled.ExpandMore, null, end)
                                                skin == ThemeStyle.MIUIX -> MiuixSettingSwitch(true, null, end, enabled = true)
                                                else -> SwitchWithIcon(true, null, end)
                                            }
                                        },
                                        bottomAction = if (row == "bottom") {
                                            { Box(Modifier.fillMaxWidth().height(72.dp).testTag("bottom.control")) }
                                        } else {
                                            null
                                        },
                                    )
                                }
                            }
                        }
                    }

                    fun bounds(tag: String) = onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                    for (row in rows) {
                        val summary = bounds("$row.summary")
                        val center = (bounds("$row.title").top + summary.bottom) / 2f
                        assertEquals(center, bounds("$row.icon").center.y, 1f, "$skin/$row 前导图标")
                        assertEquals(center, bounds("$row.end").center.y, 1f, "$skin/$row 尾部控件")
                        if (row == "bottom") assertTrue(bounds("bottom.control").top >= summary.bottom)
                    }
                }
            }
        } finally {
            ThemeManager.load(original)
        }
    }

    @Test
    fun miuixRowsPreserveTitleBottomWidthAndDisabledSwitch() {
        val original = ThemeManager.snapshot()
        try {
            ThemeManager.load(original.copy(themeStyle = ThemeStyle.MIUIX, themeMode = ThemeMode.LIGHT))
            var changes = 0
            runDesktopComposeUiTest(width = 320, height = 600) {
                setContent {
                    ZhihuTheme {
                        SettingItemGroup {
                            SettingItemSelect(
                                title = { Text("完整设置标题需要自然换行", Modifier.testTag("budget_title")) },
                                icon = { Box(Modifier.size(24.dp)) },
                                options = listOf("非常长的当前选择值".repeat(12)),
                                selectedIndex = 0,
                                onSelect = {},
                                fieldModifier = Modifier.testTag("budget_value"),
                            )
                            SettingItem(
                                title = { Text("底部配置", Modifier.testTag("bottom_title")) },
                                bottomAction = { Box(Modifier.fillMaxWidth().height(32.dp).testTag("bottom_control")) },
                            )
                            SettingItemWithSwitch(
                                title = { Text("不可用开关") },
                                checked = false,
                                onCheckedChange = { changes += 1 },
                                enabled = false,
                                modifier = Modifier.testTag("disabled_setting_switch"),
                            )
                        }
                    }
                }
                val title = onNodeWithTag("budget_title", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val value = onNodeWithTag("budget_value", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                assertTrue(title.width > 0f, "长尾值不能挤空标题")
                assertTrue(value.width <= (title.width + value.width) * 0.61f, "尾值应遵守原生行的 60% 上限")
                val results = mutableListOf<TextLayoutResult>()
                onNodeWithTag("budget_title", useUnmergedTree = true)
                    .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
                assertTrue(results.single().lineCount > 1, "标题必须保留自然换行")
                assertTrue(!results.single().didOverflowHeight, "标题必须完整显示，不能溢出高度")
                assertTrue((0 until results.single().lineCount).none { results.single().isLineEllipsized(it) }, "标题不能被省略")
                val bottom = onNodeWithTag("bottom_control").fetchSemanticsNode().boundsInRoot
                val bottomTitle = onNodeWithTag("bottom_title", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                assertEquals(bottomTitle.left, bottom.left)
                assertEquals(264f, bottom.width)
                onNodeWithTag("disabled_setting_switch").assertIsNotEnabled().assertIsOff().performClick()
                assertEquals(0, changes)
            }
        } finally {
            ThemeManager.load(original)
        }
    }

    @Test
    fun selectionDropdownUsesNativeSelectionAndStaysInsideItsWindow() {
        val original = ThemeManager.snapshot()
        try {
            for ((style, inDialog) in ThemeStyle.entries.flatMap { style -> listOf(style to false, style to true) }) {
                run {
                    ThemeManager.load(original.copy(themeStyle = style, themeMode = ThemeMode.LIGHT))
                    var selected by mutableStateOf(0)
                    var selections = 0
                    runDesktopComposeUiTest(width = 412, height = 400) {
                        setContent {
                            ZhihuTheme {
                                val owner = LocalNavigationEventDispatcherOwner.current ?: rememberNavigationEventDispatcherOwner(parent = null)
                                CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) {
                                    AppScaffold {
                                        val selection: @Composable () -> Unit = {
                                            Box {
                                                var expanded by remember { mutableStateOf(false) }
                                                AppTextButton("选择排序", { expanded = true })
                                                AppSelectionDropdownMenu(
                                                    expanded = expanded,
                                                    onDismissRequest = { expanded = false },
                                                    options = listOf("按热度", "按时间"),
                                                    selectedIndex = selected,
                                                    onSelect = {
                                                        selected = it
                                                        selections += 1
                                                        expanded = false
                                                    },
                                                    renderInRootScaffold = !inDialog,
                                                ) {
                                                    listOf("按热度", "按时间").forEachIndexed { index, label ->
                                                        AppDropdownMenuItem(text = { Text(label) }, onClick = {
                                                            selected = index
                                                            selections += 1
                                                            expanded = false
                                                        })
                                                    }
                                                }
                                            }
                                        }
                                        if (inDialog) {
                                            Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                                                AppScaffold { selection() }
                                            }
                                        } else {
                                            selection()
                                        }
                                    }
                                }
                            }
                        }
                        onNodeWithText("选择排序").performClick()
                        onNodeWithText("按时间", useUnmergedTree = true).assertIsDisplayed().performClick()
                        waitUntil { selected == 1 }
                        waitForIdle()
                        assertEquals(1, selections)
                        onNodeWithText("选择排序").performClick()
                        if (style == ThemeStyle.MIUIX) onNodeWithText("按时间").assertIsSelected()
                        onNodeWithText("按热度", useUnmergedTree = true).performClick()
                        waitUntil { selected == 0 }
                        waitForIdle()
                        assertEquals(2, selections)
                        onNodeWithText("按时间", useUnmergedTree = true).assertDoesNotExist()
                    }
                }
            }
        } finally {
            ThemeManager.load(original)
        }
    }

    @Test
    fun actionDropdownKeepsTrailingActionIndependentInsideItsWindow() {
        val original = ThemeManager.snapshot()
        try {
            for ((style, inDialog) in ThemeStyle.entries.flatMap { style -> listOf(style to false, style to true) }) {
                ThemeManager.load(original.copy(themeStyle = style, themeMode = ThemeMode.LIGHT))
                var expanded by mutableStateOf(false)
                var selections = 0
                var deletions = 0
                runDesktopComposeUiTest(width = 412, height = 400) {
                    setContent {
                        ZhihuTheme {
                            val owner = LocalNavigationEventDispatcherOwner.current ?: rememberNavigationEventDispatcherOwner(parent = null)
                            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) {
                                AppScaffold {
                                    val actions: @Composable () -> Unit = {
                                        Box(Modifier.fillMaxSize().testTag("actions_window")) {
                                            Box(Modifier.align(Alignment.BottomEnd)) {
                                                AppTextButton("打开操作", { expanded = true }, Modifier.testTag("actions_anchor"))
                                                AppDropdownMenu(
                                                    expanded = expanded,
                                                    onDismissRequest = { expanded = false },
                                                    modifier = Modifier.testTag("actions_menu"),
                                                    renderInRootScaffold = !inDialog,
                                                ) {
                                                    AppDropdownMenuItem(
                                                        text = { Text("选择收藏夹") },
                                                        modifier = Modifier.testTag("folder_action"),
                                                        onClick = {
                                                            selections++
                                                            expanded = false
                                                        },
                                                        trailingIcon = {
                                                            AppTextButton("删除收藏夹", {
                                                                deletions++
                                                                expanded = false
                                                            }, Modifier.testTag("folder_delete"))
                                                        },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    if (inDialog) {
                                        Dialog(onDismissRequest = { error("菜单不能关闭父窗口") }) {
                                            AppScaffold { actions() }
                                        }
                                    } else {
                                        actions()
                                    }
                                }
                            }
                        }
                    }
                    onNodeWithTag("actions_anchor").performTouchInput { click() }
                    val window = onNodeWithTag("actions_window").fetchSemanticsNode()
                    val anchor = onNodeWithTag("actions_anchor").fetchSemanticsNode()
                    val menu = onNodeWithTag("actions_menu").assertIsDisplayed().fetchSemanticsNode()
                    assertTrue(menu.positionOnScreen.x >= window.positionOnScreen.x - 1f, "$style/$inDialog 菜单左边界")
                    assertTrue(menu.positionOnScreen.x + menu.size.width <= window.positionOnScreen.x + window.size.width + 1f, "$style/$inDialog 菜单右边界")
                    assertTrue(menu.positionOnScreen.y >= window.positionOnScreen.y - 1f, "$style/$inDialog 菜单上边界")
                    assertTrue(menu.positionOnScreen.y + menu.size.height <= anchor.positionOnScreen.y + 1f, "$style/$inDialog 底部锚点应向上展开")
                    onNodeWithTag("folder_action").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
                    if (style == ThemeStyle.MIUIX) {
                        onNodeWithTag("folder_action").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
                    }
                    onNodeWithTag("folder_delete", useUnmergedTree = true).performTouchInput { click() }
                    waitForIdle()
                    assertEquals(1, deletions, style.name)
                    assertEquals(0, selections, "独立尾部动作不能触发行选择")
                    onNodeWithTag("actions_menu").assertDoesNotExist()
                    onNodeWithTag("actions_anchor").performTouchInput { click() }
                    onNodeWithText("选择收藏夹", useUnmergedTree = true).assertIsDisplayed().performTouchInput { click() }
                    waitForIdle()
                    assertEquals(1, selections, style.name)
                    assertEquals(1, deletions, style.name)
                    onNodeWithTag("actions_menu").assertDoesNotExist()
                }
            }
        } finally {
            ThemeManager.load(original)
        }
    }

    @Test
    fun confirmationAboveAnotherDialogPreservesTheParentInput() {
        val original = ThemeManager.snapshot()
        try {
            for (style in ThemeStyle.entries) {
                ThemeManager.load(original.copy(themeStyle = style, themeMode = ThemeMode.LIGHT))
                var confirm by mutableStateOf(false)
                var parentDismissals = 0
                runDesktopComposeUiTest(width = 600, height = 800) {
                    setContent {
                        ZhihuTheme {
                            val owner = LocalNavigationEventDispatcherOwner.current
                                ?: rememberNavigationEventDispatcherOwner(parent = null)
                            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) {
                                AppScaffold {
                                    Dialog(onDismissRequest = { parentDismissals++ }) {
                                        Column {
                                            var draft by remember { mutableStateOf("") }
                                            TextField(draft, { draft = it }, modifier = Modifier.testTag("parent_input"))
                                            AppTextButton("打开确认", { confirm = true })
                                        }
                                    }
                                    if (confirm) {
                                        AppAlertDialog(
                                            onDismissRequest = { confirm = false },
                                            title = { Text("二次确认") },
                                            confirmButton = { AppTextButton("取消确认", { confirm = false }) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    onNodeWithTag("parent_input").performTextInput("尚未提交的内容")
                    onNodeWithText("打开确认").performTouchInput { click() }
                    onNodeWithText("二次确认").assertIsDisplayed().assert(hasAnyAncestor(isDialog()))
                    onNodeWithText("取消确认").performTouchInput { click() }
                    waitForIdle()
                    onNodeWithText("二次确认").assertDoesNotExist()
                    onNodeWithTag("parent_input").assertIsDisplayed()
                    onNodeWithText("尚未提交的内容").assertExists()
                    assertEquals(0, parentDismissals, "取消确认不能关闭下层窗口")
                }
            }
        } finally {
            ThemeManager.load(original)
        }
    }

    @Test
    fun switchRowKeepsItsSegmentShapeWhenChecked() {
        val original = ThemeManager.snapshot()
        try {
            ThemeManager.load(original.copy(themeStyle = ThemeStyle.MATERIAL_EXPRESSIVE, themeMode = ThemeMode.LIGHT))
            runDesktopComposeUiTest(width = 412, height = 400) {
                setContent {
                    ZhihuTheme {
                        Column(Modifier.fillMaxWidth().background(Color.Red)) {
                            SettingItemWithSwitch(title = { Text("关闭的开关") }, checked = false, onCheckedChange = {}, modifier = Modifier.testTag("switch_off"))
                            SettingItemWithSwitch(title = { Text("打开的开关") }, checked = true, onCheckedChange = {}, modifier = Modifier.testTag("switch_on"))
                        }
                    }
                }
                waitForIdle()
                val off = onNodeWithTag("switch_off").captureToImage().toPixelMap()[2, 2]
                val on = onNodeWithTag("switch_on").captureToImage().toPixelMap()[2, 2]
                assertEquals(off, on, "打开的开关行左上角与关闭的行不一致：off=$off on=$on")
            }
        } finally {
            ThemeManager.load(original)
        }
    }
}
