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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
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
import com.github.zly2006.zhihu.i18n.DomainStrings
import com.github.zly2006.zhihu.navigation.isWebPageUrl
import com.github.zly2006.zhihu.ui.PREFERENCE_NAME
import com.github.zly2006.zhihu.util.clipboardManager
import com.github.zly2006.zhihu.util.saveImageToGallery
import com.github.zly2006.zhihu.util.shareImage
import com.github.zly2006.zhihu.viewmodel.filter.FilterableContent
import com.github.zly2006.zhihu.viewmodel.filter.KeywordSemanticMatcher
import com.github.zly2006.zhihu.viewmodel.filter.androidKeywordSemanticMatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.io.files.Path

private const val WEBVIEW_ACTIVITY_CLASS = "com.github.zly2006.zhihu.WebviewActivity"
const val ACTION_OPEN_ZHIHU_ACCOUNT_WEB = "com.github.zly2006.zhihu.OPEN_ACCOUNT_WEB"

fun openInSystemBrowser(context: Context, url: String) {
    if (!isWebPageUrl(url)) {
        androidUserMessageSink(context).showShortMessage(DomainStrings.invalidWebAddress)
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
        androidUserMessageSink(context).showShortMessage(DomainStrings.noSystemBrowser)
    } catch (_: SecurityException) {
        androidUserMessageSink(context).showShortMessage(DomainStrings.systemBrowserDenied)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
actual fun isSoftwareKeyboardVisible(): Boolean = WindowInsets.isImeVisible

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
                    androidUserMessageSink(context).showShortMessage(DomainStrings.systemOpenLinkFailed)
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

actual val isArticlePdfExportSupported: Boolean = true

actual val isPageTurnSupported: Boolean = true

actual val isAnswerSwipeSupported: Boolean = true

actual val defaultAnswerSwitchMode: String = "vertical"

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

actual val platformSemanticRuntimeGeneration: StateFlow<Long> = MutableStateFlow(0L).asStateFlow()

actual suspend fun showNlpBlockedMessage(blocked: Collection<FilterableContent>) {
    if (blocked.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        androidUserMessageSink(currentAndroidApplicationContext()).showShortMessage(DomainStrings.semanticItemsBlocked(blocked.size, blocked.first().title.take(10)))
    }
}

@Composable
actual fun rememberSettingsStore(): SettingsStore {
    val context = LocalContext.current.applicationContext
    return remember(context) { androidSettingsStore(context) }
}

actual fun Modifier.exportTestTagsForUiAutomation(): Modifier = semantics { testTagsAsResourceId = true }

fun androidSettingsStore(context: Context): SettingsStore {
    val preferences = context.applicationContext.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)
    fun android.content.SharedPreferences.Editor.putValue(key: String, value: SettingValue) {
        when (value) {
            is SettingValue.BooleanValue -> putBoolean(key, value.value)
            is SettingValue.IntValue -> putInt(key, value.value)
            is SettingValue.LongValue -> putLong(key, value.value)
            is SettingValue.FloatValue -> putFloat(key, value.value)
            is SettingValue.StringValue -> putString(key, value.value)
            is SettingValue.StringSetValue -> putStringSet(key, value.value)
        }
    }
    return object : SettingsStore() {
        // 平台类型只在适配器解码；类型错配交由 common getter 返回默认值。
        private fun valueOf(value: Any?): SettingValue? = when (value) {
            null -> null
            is Boolean -> SettingValue.BooleanValue(value)
            is Int -> SettingValue.IntValue(value)
            is Long -> SettingValue.LongValue(value)
            is Float -> SettingValue.FloatValue(value.toRawBits())
            is String -> SettingValue.StringValue(value)
            is Set<*> -> SettingValue.StringSetValue(value.map { it as String }.toSet())
            else -> error("Unsupported SharedPreferences value type")
        }

        override fun readValue(key: String): SettingValue? = valueOf(preferences.all[key])

        override fun commit(updates: Map<String, SettingValue?>) = synchronized(preferences) {
            val current = preferences.all
            val changed = updates.filter { (key, value) -> valueOf(current[key]) != value }
            if (changed.isEmpty()) return@synchronized
            // 一个 Editor 原子发布整批内存值；仍沿用 SharedPreferences 的异步落盘语义。
            preferences.edit {
                changed.forEach { (key, value) -> if (value == null) remove(key) else putValue(key, value) }
            }
        }

        override fun readEntries(): Map<String, SettingValue> = preferences.all.mapNotNull { (key, value) ->
            valueOf(value)?.let { key to it }
        }.toMap()

        override fun removeByPrefix(prefix: String) = synchronized(preferences) {
            commit(preferences.all.keys.filter { it.startsWith(prefix) }.associateWith { null })
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
