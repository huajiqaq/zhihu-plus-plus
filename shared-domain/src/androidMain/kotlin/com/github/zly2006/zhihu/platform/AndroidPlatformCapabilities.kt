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

package com.github.zly2006.zhihu.platform

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.core.content.edit
import androidx.core.net.toUri
import com.github.zly2006.zhihu.account.rememberZhihuAccountStore
import com.github.zly2006.zhihu.data.currentAndroidApplicationContext
import com.github.zly2006.zhihu.navigation.isWebPageUrl
import com.github.zly2006.zhihu.ui.PREFERENCE_NAME
import com.github.zly2006.zhihu.util.clipboardManager
import com.github.zly2006.zhihu.util.saveImageToGallery
import com.github.zly2006.zhihu.util.shareImage
import com.github.zly2006.zhihu.viewmodel.filter.FilterableContent
import com.github.zly2006.zhihu.viewmodel.filter.KeywordSemanticMatcher
import com.github.zly2006.zhihu.viewmodel.filter.androidKeywordSemanticMatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.io.files.Path

private const val WEBVIEW_ACTIVITY_CLASS = "com.github.zly2006.zhihu.WebviewActivity"
const val ACTION_OPEN_ZHIHU_ACCOUNT_WEB = "com.github.zly2006.zhihu.OPEN_ACCOUNT_WEB"

fun openInSystemBrowser(context: Context, url: String) {
    if (!isWebPageUrl(url)) {
        androidUserMessageSink(context).showShortMessage("不是有效的网页地址")
        return
    }
    val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
        /** 按无主机约束的网页协议选择浏览器；实际交付原网址，避免知乎 App Link 再次进入本应用。 */
        selector = Intent(Intent.ACTION_VIEW, "https:".toUri()).addCategory(Intent.CATEGORY_BROWSABLE)
        if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        androidUserMessageSink(context).showShortMessage("未找到系统浏览器")
    } catch (_: SecurityException) {
        androidUserMessageSink(context).showShortMessage("系统拒绝打开浏览器")
    }
}

@Composable
actual fun rememberExternalUrlOpener(): ExternalUrlOpener {
    val context = LocalContext.current
    return remember(context) {
        object : ExternalUrlOpener {
            override fun invoke(url: String) {
                val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                if (isWebPageUrl(url)) intent.setClassName(context, WEBVIEW_ACTIVITY_CLASS)
                if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    context.startActivity(intent)
                } catch (_: ActivityNotFoundException) {
                    androidUserMessageSink(context).showShortMessage("系统无法打开此链接")
                }
            }
        }
    }
}

actual val platformBottomBarItemLimit: Int? = 5

actual val platformName: String = "Android"

actual val isJvm: Boolean = false

actual val isNative: Boolean = false

actual val isAigcVoteSupported: Boolean = true

actual val isArticleHtmlExportSupported: Boolean = true

actual val isArticleImageExportSupported: Boolean = true

actual val isPageTurnSupported: Boolean = true

actual val isAnswerSwipeSupported: Boolean = true

@Composable
actual fun rememberSystemUrlOpener(): SystemUrlOpener {
    val context = LocalContext.current
    return remember(context) {
        object : SystemUrlOpener {
            override fun invoke(url: String) = openInSystemBrowser(context, url)
        }
    }
}

@Composable
actual fun rememberZhihuWebUrlOpener(): ZhihuWebUrlOpener {
    val context = LocalContext.current
    return remember(context) {
        object : ZhihuWebUrlOpener {
            override fun invoke(url: String) = context.startActivity(Intent(ACTION_OPEN_ZHIHU_ACCOUNT_WEB, url.toUri()).setClassName(context, WEBVIEW_ACTIVITY_CLASS))
        }
    }
}

@Composable
actual fun rememberImageSaver(): ImageSaver {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = rememberZhihuAccountStore()
    return remember(context, scope, store) {
        object : ImageSaver {
            override fun invoke(url: String) {
                scope.launch {
                    saveImageToGallery(context, store.client.httpClient(), url)
                }
            }
        }
    }
}

@Composable
actual fun rememberImageSharer(): ImageSharer {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = rememberZhihuAccountStore()
    return remember(context, scope, store) {
        object : ImageSharer {
            override fun invoke(url: String) {
                scope.launch {
                    shareImage(context, store.client.httpClient(), url)
                }
            }
        }
    }
}

@Composable
actual fun rememberPlainTextClipboard(): PlainTextClipboard {
    val context = LocalContext.current
    return remember(context) {
        object : PlainTextClipboard {
            override fun invoke(label: String, text: String) = context.clipboardManager.setPrimaryClip(ClipData.newPlainText(label, text))
        }
    }
}

actual fun platformSettingsStore(): SettingsStore = androidSettingsStore(currentAndroidApplicationContext())

actual fun appPrivateDirectory(): Path = Path(currentAndroidApplicationContext().filesDir.absolutePath)

actual val platformKeywordSemanticMatcher: KeywordSemanticMatcher?
    get() = androidKeywordSemanticMatcher

actual suspend fun showNlpBlockedMessage(blocked: Collection<FilterableContent>) {
    if (blocked.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        androidUserMessageSink(currentAndroidApplicationContext()).showShortMessage("NLP 已屏蔽 ${blocked.first().title.take(10)}... 等 ${blocked.size} 条内容")
    }
}

@Composable
actual fun rememberSettingsStore(): SettingsStore {
    val context = LocalContext.current.applicationContext
    return remember(context) { androidSettingsStore(context) }
}

actual fun Modifier.exportTestTagsForUiAutomation(): Modifier = semantics { testTagsAsResourceId = true }

@Composable
actual fun rememberAppPrivateDirectory(): Path {
    val context = LocalContext.current.applicationContext
    return remember(context) { Path(context.filesDir.absolutePath) }
}

fun androidSettingsStore(context: Context): SettingsStore {
    val preferences = context.applicationContext.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)
    return object : SettingsStore {
        override fun getBoolean(key: String, defaultValue: Boolean) = preferences.getBoolean(key, defaultValue)

        override fun putBoolean(key: String, value: Boolean) = preferences.edit { putBoolean(key, value) }

        override fun getString(key: String, defaultValue: String) = preferences.getString(key, defaultValue) ?: defaultValue

        override fun putString(key: String, value: String) = preferences.edit { putString(key, value) }

        override fun getStringOrNull(key: String) = preferences.getString(key, null)

        override fun putStringSet(key: String, value: Set<String>) = preferences.edit { putStringSet(key, value) }

        override fun getStringSet(key: String, defaultValue: Set<String>) = preferences.getStringSet(key, defaultValue)?.toSet() ?: defaultValue

        override fun getInt(key: String, defaultValue: Int) = preferences.getInt(key, defaultValue)

        override fun putInt(key: String, value: Int) = preferences.edit { putInt(key, value) }

        override fun getLong(key: String, defaultValue: Long) = preferences.getLong(key, defaultValue)

        override fun putLong(key: String, value: Long) = preferences.edit { putLong(key, value) }

        override fun getFloat(key: String, defaultValue: Float) = preferences.getFloat(key, defaultValue)

        override fun putFloat(key: String, value: Float) = preferences.edit { putFloat(key, value) }

        override fun remove(key: String) = preferences.edit { remove(key) }

        override fun allEntries(): Map<String, Any> = preferences.all
            .mapNotNull { (key, value) ->
                value?.let { key to it }
            }.toMap()

        override fun removeByPrefix(prefix: String) = preferences.edit {
            preferences.all.keys
                .filter { it.startsWith(prefix) }
                .forEach(::remove)
        }

        override fun observeKeyChanges(onChanged: (String) -> Unit): AutoCloseable {
            val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key != null) {
                    onChanged(key)
                }
            }
            preferences.registerOnSharedPreferenceChangeListener(listener)
            return AutoCloseable {
                preferences.unregisterOnSharedPreferenceChangeListener(listener)
            }
        }
    }
}

fun androidUserMessageSink(context: Context): UserMessageSink {
    val appContext = context.applicationContext
    val mainHandler = Handler(Looper.getMainLooper())

    fun showToast(
        message: String,
        duration: Int,
    ) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Toast.makeText(appContext, message, duration).show()
        } else {
            mainHandler.post {
                Toast.makeText(appContext, message, duration).show()
            }
        }
    }

    return object : UserMessageSink {
        override fun showShortMessage(message: String) = showToast(message, Toast.LENGTH_SHORT)

        override fun showLongMessage(message: String) = showToast(message, Toast.LENGTH_LONG)
    }
}

@Composable
actual fun rememberUserMessageSink(): UserMessageSink {
    val context = LocalContext.current.applicationContext
    return remember(context) { androidUserMessageSink(context) }
}

@Composable
actual fun PlatformBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) = BackHandler(enabled = enabled, onBack = onBack)

@Composable
actual fun PlatformPredictiveBackHandler(
    enabled: Boolean,
    onProgress: (Float) -> Unit,
    onCancel: () -> Unit,
    onBack: () -> Unit,
) = PredictiveBackHandler(enabled = enabled) { progress ->
    try {
        progress.collect { backEvent ->
            onProgress(backEvent.progress)
        }
        onBack()
    } catch (e: CancellationException) {
        onCancel()
        throw e
    }
}
