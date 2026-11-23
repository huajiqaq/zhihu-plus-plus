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

package com.github.zly2006.zhihu

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.zly2006.zhihu.data.redactDiagnosticText
import com.github.zly2006.zhihu.test.resetAppPreferences
import com.github.zly2006.zhihu.theme.AndroidThemeSettings
import com.github.zly2006.zhihu.theme.ThemeMode
import com.github.zly2006.zhihu.theme.ZhihuTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.compose.ui.graphics.Color as ComposeColor

@RunWith(AndroidJUnit4::class)
class SystemBarsInstrumentedTest {
    @Test
    fun errorIntentKeepsLongDetailsScrollableAndActionsVisible() {
        val title = "合成长错误标题，完整内容必须进入正文。".repeat(24)
        val stack = "Authorization: Bearer fixture-native-secret\n" +
            (1..60).joinToString("\n") { "at fixture.Frame$it.run(Test.kt:$it)" } + "\nSTACK_END_MARKER"
        val uri = Uri
            .Builder()
            .scheme("https")
            .authority("zhihu-plus.internal")
            .appendPath("error")
            .appendQueryParameter("title", title)
            .appendQueryParameter("stack", stack)
            .build()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val originalActivity = composeRule.activity
        launchIntent = originalActivity.intent
        composeRule.runOnIdle {
            originalActivity.startActivity(
                Intent(Intent.ACTION_VIEW, uri)
                    .setClass(originalActivity, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        }
        // 活动展示错误后清空 Intent 的 data，避免系统重放已消费的错误。
        composeRule.waitUntil("错误 Intent 未投递到原活动", timeoutMillis = 5_000) {
            originalActivity.intent.action == Intent.ACTION_VIEW &&
                originalActivity.intent.data == null &&
                originalActivity.intent.getBooleanExtra("internal_error_shown", false)
        }
        org.junit.Assert.assertSame(originalActivity, composeRule.activity)
        instrumentation.waitForIdleSync()
        waitForErrorDialog()
        composeRule.onNodeWithText("关闭").assertIsDisplayed()
        composeRule.onNodeWithText("复制脱敏信息").assertIsDisplayed()
        val isMessage = hasText("STACK_END_MARKER", substring = true)
        val message = composeRule.onNode(isMessage)
        val messageText = message
            .fetchSemanticsNode()
            .config[SemanticsProperties.Text]
            .single()
            .text
        assertTrue("完整标题不能被省略", messageText.contains(title))
        assertTrue("完整堆栈不能被裁掉", messageText.endsWith("STACK_END_MARKER"))
        val scroll = composeRule.onNode(hasScrollAction() and hasAnyDescendant(isMessage))
        val scrollRange = scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue("长错误正文必须能滚动", scrollRange.maxValue() > 0f)
        scroll.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, scrollRange.maxValue() - scrollRange.value()) }
        composeRule.waitForIdle()
        val messageNode = message.fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        composeRule.runOnIdle {
            assertTrue(messageNode.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts) == true)
        }
        val layout = layouts.single()
        val lastLine = layout.getLineForOffset(messageText.length - 1)
        val lineTop = messageNode.positionInRoot.y + layout.getLineTop(lastLine)
        val lineBottom = messageNode.positionInRoot.y + layout.getLineBottom(lastLine)
        val visible = scroll.fetchSemanticsNode().boundsInRoot
        assertTrue(
            "错误正文最后一行必须实际可见：line=$lineTop..$lineBottom, visible=$visible",
            lineTop >= visible.top && lineBottom <= visible.bottom + 1f,
        )
        composeRule.onNodeWithText("复制脱敏信息").assertIsDisplayed()
        composeRule.onNodeWithText("关闭").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("错误详情").assertDoesNotExist()
        composeRule.runOnIdle {
            composeRule.activity.onWindowFocusChanged(true)
        }
        composeRule.onNodeWithText("错误详情").assertDoesNotExist()
        composeRule.runOnIdle {
            originalActivity.startActivity(
                Intent(Intent.ACTION_VIEW, uri)
                    .setClass(originalActivity, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        }
        instrumentation.waitForIdleSync()
        org.junit.Assert.assertSame(originalActivity, composeRule.activity)
        waitForErrorDialog()
        composeRule.onNodeWithText("复制脱敏信息").assertIsDisplayed().performClick()
        val copied = composeRule.runOnIdle {
            val clipboard = composeRule.activity.getSystemService(android.content.ClipboardManager::class.java)
            clipboard.primaryClip
                ?.getItemAt(0)
                ?.text
                ?.toString()
        }
        assertEquals(redactDiagnosticText("$title\n\n$stack"), copied)
        assertTrue("复制结果不能保留凭据", "fixture-native-secret" !in copied.orEmpty())
    }

    private fun waitForErrorDialog() {
        composeRule.waitUntil("错误弹窗未显示", timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("错误详情").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("错误详情").assertIsDisplayed()
    }

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        composeRule.resetAppPreferences()
        AndroidThemeSettings.setThemeMode(composeRule.activity, ThemeMode.DARK)
    }

    private var launchIntent: Intent? = null

    /** ActivityScenario 按启动 Intent 匹配生命周期；用例失败时也要恢复测试身份，否则收尾超时会盖住真正的报错。 */
    @After
    fun restoreLaunchIntent() {
        launchIntent?.let { intent -> composeRule.runOnIdle { composeRule.activity.intent = intent } }
    }

    /**
     * 深色主题状态栏须与页面背景无缝衔接；行为依据：https://github.com/zly2006/zhihu-plus-plus/issues/288
     */
    @Test
    fun darkThemeStatusBarIsEdgeToEdge() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val originalActivity = composeRule.activity
        instrumentation.runOnMainSync {
            originalActivity.setContent {
                ZhihuTheme {
                    SolidThemeSurface()
                }
            }
        }
        composeRule.waitForIdle()
        waitUntilStatusBarColor(originalActivity, "深色主题")
    }

    private fun waitUntilStatusBarColor(
        activity: MainActivity,
        stage: String,
    ) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val deadline = SystemClock.uptimeMillis() + 10_000
        var consecutiveMatchingFrames = 0
        var statusBarColors = emptyList<Int>()
        var bodyColor = SOLID_SURFACE_COLOR
        do {
            instrumentation.waitForIdleSync()
            SystemClock.sleep(16)
            val sampledColors = sampleStatusBarColors(activity)
            statusBarColors = sampledColors.take(2)
            bodyColor = sampledColors.last()
            consecutiveMatchingFrames =
                if (statusBarColors.all { it == SOLID_SURFACE_COLOR }) {
                    consecutiveMatchingFrames + 1
                } else {
                    0
                }
        } while (consecutiveMatchingFrames < REQUIRED_MATCHING_FRAMES && SystemClock.uptimeMillis() < deadline)
        assertTrue(
            "$stage：预期状态栏颜色为 ${SOLID_SURFACE_COLOR.toUInt().toString(16)}，" +
                "实际采样为 ${statusBarColors.joinToString { it.toUInt().toString(16) }}；" +
                "body=${bodyColor.toUInt().toString(16)}, focus=${activity.window.decorView.hasWindowFocus()}, " +
                "flags=${activity.window.attributes.flags}, dim=${activity.window.attributes.dimAmount}",
            consecutiveMatchingFrames >= REQUIRED_MATCHING_FRAMES,
        )
    }

    private fun sampleStatusBarColors(activity: MainActivity): List<Int> {
        val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val statusBarHeight = ViewCompat
            .getRootWindowInsets(activity.window.decorView)
            ?.getInsets(WindowInsetsCompat.Type.statusBars())
            ?.top
            ?: error("无法获取状态栏边距")
        val y = statusBarHeight / 2
        // Pixel 7 CI 设备有居中的摄像头开孔，须在开孔两侧校验精确颜色。
        return listOf(
            screenshot.getPixel(screenshot.width / 3, y),
            screenshot.getPixel(screenshot.width * 2 / 3, y),
            screenshot.getPixel(screenshot.width / 3, screenshot.height / 2),
        ).also { screenshot.recycle() }
    }
}

@Composable
private fun SolidThemeSurface() {
    Box(
        Modifier
            .fillMaxSize()
            .background(ComposeColor(SOLID_SURFACE_COLOR)),
    )
}

private const val SOLID_SURFACE_COLOR = 0xFF121212.toInt()
private const val REQUIRED_MATCHING_FRAMES = 3
