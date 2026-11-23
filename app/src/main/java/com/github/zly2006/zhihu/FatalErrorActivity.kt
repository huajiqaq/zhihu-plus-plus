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

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.github.zly2006.zhihu.data.redactDiagnosticText
import com.github.zly2006.zhihu.util.clipboardManager
import com.github.zly2006.zhihu.util.enableEdgeToEdgeCompat
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.math.roundToInt

class FatalErrorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdgeCompat()
        val details = intent.getStringExtra(EXTRA_DETAILS) ?: UNKNOWN_ERROR
        onBackPressedDispatcher.addCallback(this) { finishAndRemoveTask() }

        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val padding = (16 * resources.displayMetrics.density).roundToInt()
        ViewCompat.setOnApplyWindowInsetsListener(page) { view, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(safe.left + padding, safe.top + padding, safe.right + padding, safe.bottom + padding)
            insets
        }
        page.addView(
            TextView(this).apply {
                setTextAppearance(android.R.style.TextAppearance_Material_Headline)
                text = "错误详情"
            },
        )
        val scroll = ScrollView(this).apply {
            addView(TextView(context).apply { text = details })
        }
        page.addView(scroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        val actions = LinearLayout(this).apply { gravity = Gravity.END }
        actions.addView(
            Button(this).apply {
                text = "复制脱敏信息"
                setOnClickListener { clipboardManager.setPrimaryClip(ClipData.newPlainText("error", details)) }
            },
        )
        actions.addView(
            Button(this).apply {
                text = "关闭"
                setOnClickListener { finishAndRemoveTask() }
            },
        )
        page.addView(actions)
        setContentView(page)
    }

    companion object {
        private const val EXTRA_DETAILS = "fatal_error_details"
        private const val UNKNOWN_ERROR = "无法获取错误详情"
        private const val TRUNCATED = "\n…（错误详情已截断）"

        /** Intent 只携带一个 UTF-16 字符串，正文最多占约 32 KiB。 */
        private const val MAX_DETAILS_CHARS = 16 * 1024

        internal fun createIntent(context: Context, error: Throwable): Intent {
            val details = runCatching { diagnosticDetails(error) }.getOrDefault(UNKNOWN_ERROR)
            return Intent(context, FatalErrorActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra(EXTRA_DETAILS, details)
        }

        private fun diagnosticDetails(error: Throwable): String {
            val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
            val text = buildString {
                fun appendError(failure: Throwable, prefix: String) {
                    if (length > MAX_DETAILS_CHARS || !seen.add(failure)) return
                    append(prefix).appendLine(failure.javaClass.name)
                    // 异常类型不与 message 拼在同一行，否则行首凭据字段无法脱敏。
                    failure.message?.let { appendLine(redactDiagnosticText(it).take(MAX_DETAILS_CHARS + 1)) }
                    for (frame in failure.stackTrace) {
                        if (length > MAX_DETAILS_CHARS) return
                        append("\tat ").appendLine(frame)
                    }
                    failure.suppressed.forEach { appendError(it, "Suppressed: ") }
                    failure.cause?.let { appendError(it, "Caused by: ") }
                }
                appendError(error, "")
            }
            val safe = redactDiagnosticText(text.trimEnd())
            return if (text.length > MAX_DETAILS_CHARS || safe.length > MAX_DETAILS_CHARS) {
                safe.take(MAX_DETAILS_CHARS - TRUNCATED.length) + TRUNCATED
            } else {
                safe
            }
        }
    }
}
