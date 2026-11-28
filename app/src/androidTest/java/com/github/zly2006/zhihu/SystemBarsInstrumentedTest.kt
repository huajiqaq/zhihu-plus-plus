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

import android.app.ActivityManager
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.os.Parcel
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.zly2006.zhihu.test.resetAppPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SystemBarsInstrumentedTest {
    @Test
    fun fatalErrorReplacesOldTaskAndKeepsLongRedactedDetailsCopyable() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val main = composeRule.activity
        val app = main.application
        val manager = checkNotNull(app.getSystemService(ActivityManager::class.java))
        val page = ComponentName(app, FatalErrorActivity::class.java)
        val title = "合成长错误标题，完整内容必须进入正文。".repeat(24)
        val failure = IllegalStateException("Authorization: Bearer fixture-native-secret\n$title").apply {
            stackTrace = Array(60) { index -> StackTraceElement("fixture.Frame$index", "run", "Test.kt", index + 1) }
        }
        val delivered = FatalErrorActivity.createIntent(app, failure)
        val extras = checkNotNull(delivered.extras)
        val expected = checkNotNull(extras.getString(extras.keySet().single()))
        assertEquals(page, delivered.component)
        assertTrue(delivered.data == null && "fixture-native-secret" !in expected)
        assertTrue(title in expected && expected.endsWith("fixture.Frame59.run(Test.kt:60)"))
        val oversized = FatalErrorActivity.createIntent(app, IllegalStateException("x".repeat(80_000)))
        val parcel = Parcel.obtain()
        try {
            oversized.writeToParcel(parcel, 0)
            assertTrue("错误 Intent 须给 Binder 留足余量", parcel.dataSize() < 40 * 1024)
        } finally {
            parcel.recycle()
        }

        fun ownTasks() = manager.appTasks
            .mapNotNull { runCatching { it.taskInfo }.getOrNull() }
            .filter { it.baseIntent.component?.packageName == app.packageName }

        fun node(text: String) = instrumentation.uiAutomation.rootInActiveWindow
            ?.takeIf { it.packageName == app.packageName }
            ?.findAccessibilityNodeInfosByText(text)
            ?.firstOrNull { it.isVisibleToUser && it.text?.toString() == text }

        val forward = AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD
        val backward = AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD
        val oldTask = Intent(app, WebviewActivity::class.java)
            .setAction("fixture.old.task")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        try {
            val thumbnail = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            try {
                assertTrue(manager.addAppTask(main, oldTask, null, thumbnail) >= 0)
            } finally {
                thumbnail.recycle()
            }
            assertTrue(ownTasks().any { it.baseIntent.action == oldTask.action })
            instrumentation.runOnMainSync { replaceActivitiesWithFatalError(app, failure, listOf(main)) }
            composeRule.waitUntil("旧 Activity 应已销毁，错误页应是自有任务中唯一 Activity", 10_000) {
                val task = ownTasks().singleOrNull()
                main.isDestroyed && task?.baseActivity == page && task.topActivity == page && task.numActivities == 1 && node("错误详情") != null
            }
            val info = app.packageManager.getActivityInfo(page, 0)
            assertTrue(!info.exported && info.processName == "${app.packageName}:error")
            assertTrue(node("复制脱敏信息") != null && node("关闭") != null)
            assertTrue(forward in checkNotNull(node(expected)?.parent).actionList)
            composeRule.waitUntil("长错误正文须能滚动到末尾", 10_000) {
                val scroll = node(expected)?.parent ?: return@waitUntil false
                if (forward in scroll.actionList) {
                    scroll.performAction(forward.id)
                    false
                } else {
                    backward in scroll.actionList
                }
            }
            val body = checkNotNull(node(expected))
            val key = AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY
            val range = Bundle().apply {
                putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_START_INDEX, expected.lastIndex)
                putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_LENGTH, 1)
            }
            assertTrue(body.refreshWithExtraData(key, range))
            @Suppress("DEPRECATION")
            val lastCharacter = body.extras.getParcelableArray(key)?.firstOrNull() as? RectF
            val viewport = Rect().also { checkNotNull(body.parent).getBoundsInScreen(it) }
            assertTrue("正文末字须实际位于滚动区内", lastCharacter != null && RectF(viewport).contains(lastCharacter))
            assertTrue(checkNotNull(node("复制脱敏信息")).performAction(AccessibilityNodeInfo.ACTION_CLICK))
            val clipboard = checkNotNull(app.getSystemService(ClipboardManager::class.java))
            composeRule.waitUntil("复制结果须与脱敏正文相同", 5_000) {
                clipboard.primaryClip
                    ?.getItemAt(0)
                    ?.text
                    ?.toString() == expected
            }
            assertTrue(checkNotNull(node("关闭")).performAction(AccessibilityNodeInfo.ACTION_CLICK))
            composeRule.waitUntil("关闭后不得留下旧任务或重启主页", 5_000) { ownTasks().isEmpty() }
        } finally {
            manager.appTasks.forEach { task ->
                runCatching {
                    val info = task.taskInfo ?: return@runCatching
                    if (info.baseActivity == page || info.baseIntent.action == oldTask.action) task.finishAndRemoveTask()
                }
            }
        }
    }

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        composeRule.resetAppPreferences()
    }
}
