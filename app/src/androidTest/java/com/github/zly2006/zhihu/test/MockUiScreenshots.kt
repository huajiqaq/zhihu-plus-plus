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

package com.github.zly2006.zhihu.test

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.ActivityManager
import android.app.Instrumentation
import android.app.UiAutomation
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.accessibility.AccessibilityWindowInfo
import androidx.test.platform.app.InstrumentationRegistry

internal enum class MockUiScreenshotCase(
    val suite: String,
    val method: String,
) {
    HIGHLIGHT_SELECTION("ArticleScreenInstrumentedTest", "highlightedParagraphSelectionHandleCanExtendToFollowingParagraph"),
    HANDLE_SELECTION("ArticleScreenInstrumentedTest", "draggingSelectionHandleUsesSameCompleteTextLayer"),
    CHILD_COMMENT_MENU("CommentScreenInstrumentedTest", "childCommentMoreMenuTracksAnchorWithinWindowAndBackLayers"),
    MARKDOWN_SHORTCUTS("WriteAnswerScreenInstrumentedTest", "markdownShortcutsHideOnlyWhileTheSoftKeyboardIsVisible"),
    ;

    fun record(test: MockUiScreenshots.() -> Unit) {
        val screenshots = MockUiScreenshots(this)
        try {
            screenshots.test()
        } catch (error: Throwable) {
            screenshots.capture("failure")
            throw error
        }
    }
}

internal class MockUiScreenshots(
    private val case: MockUiScreenshotCase,
) {
    private var sequence = 0

    fun capture(phase: String, inspect: ((Bitmap) -> String)? = null) {
        runCatching {
            val args = InstrumentationRegistry.getArguments()
            if (
                args.getString("zhpp_mock_screenshots") != "ci" ||
                args.getString(InstrumentedTestEnvironment.DATA_MODE_ARG) != "mock" ||
                InstrumentedTestEnvironment.installedDataMode != InstrumentedTestEnvironment.DataMode.MOCK ||
                Build.VERSION.SDK_INT != Build.VERSION_CODES.BAKLAVA ||
                Build.HARDWARE != "ranchu" ||
                Build.SUPPORTED_ABIS.firstOrNull() != "x86_64"
            ) {
                return
            }
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val context = instrumentation.targetContext
            if (context.packageName != "com.github.zly2006.zhplus.lite") return
            check(phase.matches(Regex("[a-z-]+")))
            val automation = instrumentation.uiAutomation
            val (layer, canCapture) = automation.screenshotScope(context.packageName)
            if (!canCapture) {
                report(phase, "skipped=$layer")
                return
            }

            // 取真实显示合成帧，Compose 节点图不包含另外的 Popup 和系统 IME 窗口。
            val bitmap = checkNotNull(automation.takeScreenshot())
            try {
                val externalFiles = checkNotNull(context.getExternalFilesDir(null))
                val directory = externalFiles.resolve("finishcheck/screenshots/${case.suite}/${case.method}")
                check(directory.isDirectory || directory.mkdirs())
                val index = (++sequence).toString().padStart(2, '0')
                val png = directory.resolve("${case.suite}__${case.method}__${phase}__$index.png")
                val temporary = directory.resolve("${png.name}.tmp")
                try {
                    temporary.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                    check(temporary.renameTo(png))
                    if (inspect != null) {
                        val result = runCatching {
                            val pixels = checkNotNull(bitmap.copy(Bitmap.Config.ARGB_8888, false))
                            try {
                                inspect(pixels)
                            } finally {
                                pixels.recycle()
                            }
                        }.getOrElse { "probe-unavailable" }
                        report(phase, result)
                    }
                } finally {
                    temporary.delete()
                }
            } finally {
                bitmap.recycle()
            }
        }.onFailure {
            report(phase, "capture-failed")
        }
    }

    private fun report(phase: String, status: String) {
        runCatching {
            val result = Bundle().apply {
                putString(Instrumentation.REPORT_KEY_STREAMRESULT, "\nZHPP_SCREENSHOT ${case.suite}#${case.method} $phase $status\n")
            }
            InstrumentationRegistry.getInstrumentation().sendStatus(2, result)
        }
    }
}

internal fun reportCommentMockCiSystemFocus(
    automation: UiAutomation,
    activity: Activity,
    activeWindowId: Int,
) {
    val args = InstrumentationRegistry.getArguments()
    if (
        args.getString("zhpp_mock_screenshots") != "ci" ||
        args.getString(InstrumentedTestEnvironment.DATA_MODE_ARG) != "mock" ||
        InstrumentedTestEnvironment.installedDataMode != InstrumentedTestEnvironment.DataMode.MOCK ||
        Build.VERSION.SDK_INT != Build.VERSION_CODES.BAKLAVA ||
        Build.HARDWARE != "ranchu" ||
        Build.SUPPORTED_ABIS.firstOrNull() != "x86_64" ||
        activity.display?.displayId != Display.DEFAULT_DISPLAY
    ) {
        return
    }
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val targetPackage = "com.github.zly2006.zhplus.lite"
    if (instrumentation.targetContext.packageName != targetPackage || activity.packageName != targetPackage) return
    val manager = activity.getSystemService(ActivityManager::class.java) ?: return
    val taskId = activity.taskId
    val targetVisible = manager.appTasks.any {
        val task = it.taskInfo
        task.taskId == taskId && task.topActivity == activity.componentName && task.isVisible
    }
    if (!targetVisible) return

    val info = automation.serviceInfo ?: return
    val originalFlags = info.flags
    val windowFlag = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
    val needsFlag = originalFlags and windowFlag == 0
    val focused = try {
        if (needsFlag) {
            info.flags = originalFlags or windowFlag
            automation.serviceInfo = info
        }
        automation.windows
            .singleOrNull { it.isFocused }
            ?.takeIf { it.id == activeWindowId && it.getRoot(0)?.packageName?.toString() == "android" }
    } finally {
        if (needsFlag) {
            info.flags = originalFlags
            automation.serviceInfo = info
        }
    }
    // API 36 的无障碍标题可能为空或取自应用标签；只输出精确匹配得到的固定类别。
    val category = when (focused?.title?.toString()) {
        "DeprecatedTargetSdkVersionDialog", "DeprecatedAbiDialog", "UnsupportedDisplaySizeDialog", "UnsupportedCompileSdkDialog" ->
            "framework-app-warning"
        "Quickstep has stopped", "Quickstep keeps stopping", "Application Error: com.android.launcher3" -> "quickstep-crash"
        "Quickstep isn't responding", "Application Not Responding: com.android.launcher3" -> "quickstep-anr"
        "System UI has stopped", "System UI keeps stopping", "Application Error: com.android.systemui" -> "systemui-crash"
        "System UI isn't responding", "Application Not Responding: com.android.systemui" -> "systemui-anr"
        else -> if (focused == null) "unconfirmed" else "framework-unknown"
    }
    val result = Bundle().apply {
        putString(Instrumentation.REPORT_KEY_STREAMRESULT, "\nZHPP_SYSTEM_FOCUS type=${focused?.type ?: "unconfirmed"} system=$category\n")
    }
    instrumentation.sendStatus(2, result)
}

private fun UiAutomation.screenshotScope(targetPackage: String): Pair<String, Boolean> {
    val info = serviceInfo ?: return "other" to false
    val originalFlags = info.flags
    val windowFlag = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
    val needsFlag = originalFlags and windowFlag == 0
    if (needsFlag) {
        info.flags = originalFlags or windowFlag
        serviceInfo = info
    }
    try {
        val visibleWindows = windows
        val active = visibleWindows.firstOrNull { it.isActive } ?: visibleWindows.firstOrNull { it.isFocused }
        val activePackage = active?.root?.packageName?.toString() ?: rootInActiveWindow?.packageName?.toString()
        val layer = when {
            active?.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD -> "IME"
            activePackage == targetPackage -> "target"
            activePackage == "android" || activePackage == "com.android.systemui" -> "system"
            else -> "other"
        }
        val foregroundApp = visibleWindows
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .maxByOrNull { it.layer }
            ?.root
            ?.packageName
            ?.toString()
        val allowed = when (layer) {
            "target" -> foregroundApp == null || foregroundApp == targetPackage
            "system", "IME" -> foregroundApp == targetPackage
            else -> false
        }
        return layer to allowed
    } finally {
        if (needsFlag) {
            runCatching {
                info.flags = originalFlags
                serviceInfo = info
            }
        }
    }
}
