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

import android.annotation.SuppressLint
import android.app.Application
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.lifecycleScope
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.NetworkClient
import coil3.network.NetworkFetcher
import coil3.network.NetworkRequest
import coil3.network.NetworkResponse
import coil3.network.ktor3.asNetworkClient
import coil3.request.crossfade
import com.github.zly2006.zhihu.account.InvalidRefreshTokenException
import com.github.zly2006.zhihu.account.androidZhihuAccountStore
import com.github.zly2006.zhihu.data.AccountData
import com.github.zly2006.zhihu.data.LocalHistoryStorage
import com.github.zly2006.zhihu.data.isZhihuRequestHost
import com.github.zly2006.zhihu.data.redactDiagnosticText
import com.github.zly2006.zhihu.navigation.AppNavHost
import com.github.zly2006.zhihu.navigation.MainTabs
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.TopLevelDestination
import com.github.zly2006.zhihu.navigation.WebPage
import com.github.zly2006.zhihu.navigation.rememberAppNavHost
import com.github.zly2006.zhihu.navigation.resolveContent
import com.github.zly2006.zhihu.navigation.resolveNavigationUrl
import com.github.zly2006.zhihu.nlp.KeywordWeightExtractor
import com.github.zly2006.zhihu.nlp.NLPService
import com.github.zly2006.zhihu.nlp.NlpServiceKeywordSemanticMatcher
import com.github.zly2006.zhihu.nlp.SentenceEmbeddingManager
import com.github.zly2006.zhihu.notification.androidSystemNotifications
import com.github.zly2006.zhihu.platform.androidSettingsStore
import com.github.zly2006.zhihu.platform.androidUserMessageSink
import com.github.zly2006.zhihu.reading.ContentReadingService
import com.github.zly2006.zhihu.theme.AndroidThemeSettings
import com.github.zly2006.zhihu.theme.ZhihuTheme
import com.github.zly2006.zhihu.ui.AndroidArticleNavigationHandoff
import com.github.zly2006.zhihu.ui.AndroidZhihuMain
import com.github.zly2006.zhihu.ui.ZhihuMainNavigationCoordinator
import com.github.zly2006.zhihu.ui.components.AppHostlessAlertDialog
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.ui.components.LocalPageTurnDispatcher
import com.github.zly2006.zhihu.ui.components.PageTurnCommand
import com.github.zly2006.zhihu.ui.components.PageTurnDispatcher
import com.github.zly2006.zhihu.ui.components.PageTurnFab
import com.github.zly2006.zhihu.ui.components.getHighestQualityVideoUrl
import com.github.zly2006.zhihu.ui.subscreens.PREF_VOLUME_KEY_PAGE_TURN
import com.github.zly2006.zhihu.updater.UpdateManager
import com.github.zly2006.zhihu.util.ContinuousUsageReminderManager
import com.github.zly2006.zhihu.util.EmojiManager
import com.github.zly2006.zhihu.util.PowerSaveModeCompat
import com.github.zly2006.zhihu.util.ZHIHU_WEB_ZSE93
import com.github.zly2006.zhihu.util.clearShareImageCache
import com.github.zly2006.zhihu.util.clipboardManager
import com.github.zly2006.zhihu.util.enableEdgeToEdgeCompat
import com.github.zly2006.zhihu.util.telemetry
import com.github.zly2006.zhihu.viewmodel.filter.androidKeywordSemanticMatcher
import com.github.zly2006.zhihu.viewmodel.filter.androidKeywordWeightExtractor
import com.github.zly2006.zhihu.viewmodel.filter.getContentFilterDatabase
import io.ktor.client.HttpClient
import io.ktor.http.Url
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/** Coil 从 applicationContext 读取 factory，确保首次图片请求早于 Activity 创建时也使用应用配置。 */
class ZhihuApplication :
    Application(),
    SingletonImageLoader.Factory {
    private val publicImageClient by lazy { HttpClient().asNetworkClient() }

    override fun onCreate() {
        super.onCreate()
        androidSystemNotifications(this)
    }

    @OptIn(ExperimentalCoilApi::class)
    override fun newImageLoader(context: Context): ImageLoader {
        val applicationContext = context.applicationContext
        return ImageLoader
            .Builder(applicationContext)
            .crossfade(true)
            .components {
                add(
                    NetworkFetcher.Factory(networkClient = {
                        object : NetworkClient {
                            override suspend fun <T> executeRequest(request: NetworkRequest, block: suspend (NetworkResponse) -> T): T {
                                val network = if (isZhihuRequestHost(Url(request.url).host)) {
                                    com.github.zly2006.zhihu.account
                                        .androidZhihuAccountStore(applicationContext)
                                        .client
                                        .httpClient(useCookies = false)
                                        .asNetworkClient()
                                } else {
                                    publicImageClient
                                }
                                return network.executeRequest(request, block)
                            }
                        }
                    }),
                )
            }.memoryCache {
                MemoryCache
                    .Builder()
                    .maxSizePercent(applicationContext, 0.25)
                    .build()
            }.diskCache {
                DiskCache
                    .Builder()
                    .directory(applicationContext.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(50L * 1024 * 1024)
                    .build()
            }.build()
    }
}

private const val APP_ERROR_DIALOG_STATE_KEY = "app_error_dialog"
private const val APP_ERROR_INTENT_CONSUMED_STATE_KEY = "app_error_intent_consumed"

/** 仅保存脱敏后的窗口正文，消费标记不代替正在展示的状态。 */
private data class AppErrorDialogContent(
    val title: String,
    val message: String,
    val copyable: Boolean,
)

class MainActivity : ComponentActivity() {
    private val appErrorDialog = mutableStateOf<AppErrorDialogContent?>(null)

    /** 启动错误的消费状态跨重建保留，窗口关闭不清除此标记。 */
    private var internalErrorIntentConsumed = false

    lateinit var history: LocalHistoryStorage
    val httpClient
        get() = com.github.zly2006.zhihu.account
            .androidZhihuAccountStore(this)
            .client
            .httpClient()

    /** 主栈持有页面访问 ID，与详情栏栈独立。 */
    lateinit var navHost: AppNavHost
    private lateinit var continuousUsageReminderManager: ContinuousUsageReminderManager
    private val pageTurnDispatcher = PageTurnDispatcher()
    private val navigationCoordinator = ZhihuMainNavigationCoordinator(
        mainHost = { navHost },
        showMessage = { androidUserMessageSink(this).showShortMessage(it) },
        openVideo = { route, contentId, contentType ->
            CoroutineScope(Dispatchers.Main).launch {
                val videoUrl = getHighestQualityVideoUrl(this@MainActivity, httpClient, route.id.toString(), contentId, contentType)
                if (videoUrl == null) {
                    androidUserMessageSink(this@MainActivity).showShortMessage("获取视频链接失败")
                    return@launch
                }
                startActivity(
                    Intent(this@MainActivity, VideoPlayerActivity::class.java).apply {
                        putExtra("video_url", videoUrl)
                        putExtra("video_id", route.id)
                    },
                )
            }
        },
        onRouteAccepted = { history.add(it) },
    )
    val mainTabNavigationTarget: TopLevelDestination?
        get() = navigationCoordinator.mainTabNavigationTarget

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            Log.e(TAG, "Uncaught exception", e)
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri
                    .Builder()
                    .apply {
                        scheme("https")
                        authority("zhihu-plus.internal")
                        appendPath("error")
                        appendQueryParameter("title", "Uncaught exception: ${e.message}")
                        appendQueryParameter(
                            "message",
                            e.message,
                        )
                        appendQueryParameter("stack", e.stackTraceToString())
                    }.build(),
                this,
                MainActivity::class.java,
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
        }
        enableEdgeToEdgeCompat()
        super.onCreate(savedInstanceState)
        internalErrorIntentConsumed =
            savedInstanceState?.getBoolean(APP_ERROR_INTENT_CONSUMED_STATE_KEY, false) == true ||
            intent.getBooleanExtra("internal_error_shown", false)
        /** 系统可重放原启动 Intent，先清除已消费的错误，不能覆盖恢复的正文。 */
        if (internalErrorIntentConsumed && intent.data?.authority == "zhihu-plus.internal" && intent.data?.path == "/error") {
            intent.putExtra("internal_error_shown", true)
            intent.data = null
        }
        savedInstanceState?.getBundle(APP_ERROR_DIALOG_STATE_KEY)?.let { saved ->
            saved.getString("message")?.let { message ->
                appErrorDialog.value = AppErrorDialogContent(
                    title = redactDiagnosticText(saved.getString("title") ?: "错误详情"),
                    message = redactDiagnosticText(message),
                    copyable = saved.getBoolean("copyable", false),
                )
            }
        }
        clearShareImageCache(this)
        continuousUsageReminderManager = ContinuousUsageReminderManager(this)
        AccountData.loadData(this)
        history = LocalHistoryStorage()
        AndroidThemeSettings.initialize(this)
        androidKeywordSemanticMatcher = NlpServiceKeywordSemanticMatcher
        androidKeywordWeightExtractor = KeywordWeightExtractor { text, topN ->
            NLPService.extractKeywordsWithWeight(text, topN)
        }
        getContentFilterDatabase(this)

        val settings = androidSettingsStore(this)
        val lastLaunchTimestamp = settings.getLong(KEY_LAST_LAUNCH_TIMESTAMP, 0L)
        val now = System.currentTimeMillis()
        val accountClient = androidZhihuAccountStore(this).client
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                accountClient.refreshCredentials()
            } catch (_: InvalidRefreshTokenException) {
                // 原账号的失效弹窗已接收脱敏原因，不再弹出网络重试提示。
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (_: Exception) {
                if (androidZhihuAccountStore(this@MainActivity).client === accountClient) {
                    androidUserMessageSink(this@MainActivity).showLongMessage("刷新登录状态失败，请稍后重试；若多次失败，请重新登录")
                }
            }
        }
        if (now - lastLaunchTimestamp >= TimeUnit.DAYS.toMillis(1)) {
            lifecycleScope.launch(Dispatchers.IO) {
                if (!PowerSaveModeCompat.getPowerSaveMode(this@MainActivity).isPowerSaveMode) {
                    try {
                        SentenceEmbeddingManager.ensureModel(this@MainActivity)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to initialize NLP embedding model", e)
                    }
                }
            }
        }
        settings.putLong(KEY_LAST_LAUNCH_TIMESTAMP, now)

        lifecycleScope.launch {
            try {
                EmojiManager
                    .initialize(this@MainActivity)
                Log.i(TAG, "Emoji manager initialized")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize emoji manager", e)
            }
        }

        setContent {
            navHost = rememberAppNavHost(MainTabs)
            LaunchedEffect(navHost) {
                if (intent.data != null || androidSystemNotifications(this@MainActivity).handles(intent)) handleIntentData(intent)
            }
            ZhihuTheme {
                CompositionLocalProvider(LocalPageTurnDispatcher provides pageTurnDispatcher) {
                    Box(Modifier.semantics { testTagsAsResourceId = true }) {
                        AndroidZhihuMain(navHost = navHost)
                        PageTurnFab(dispatcher = pageTurnDispatcher)
                        appErrorDialog.value?.let { dialog ->
                            AppHostlessAlertDialog(
                                onDismissRequest = { appErrorDialog.value = null },
                                title = { Text(dialog.title) },
                                text = { Text(dialog.message) },
                                confirmButton = {
                                    AppTextButton(onClick = { appErrorDialog.value = null }) { Text("关闭") }
                                },
                                dismissButton = if (dialog.copyable) {
                                    {
                                        AppTextButton(onClick = {
                                            clipboardManager.setPrimaryClip(ClipData.newPlainText("error", dialog.message))
                                        }) { Text("复制脱敏信息") }
                                    }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                }
            }
        }
        if (savedInstanceState == null) {
            telemetry(this, "start")
        }
        if (intent.data?.authority == "zhihu-plus.internal" && intent.data?.path == "/error") {
            showErrorDialog(intent)
        }

        if (savedInstanceState == null) {
            @OptIn(DelicateCoroutinesApi::class)
            GlobalScope.launch {
                try {
                    UpdateManager.autoCheckForUpdate(
                        this@MainActivity,
                        com.github.zly2006.zhihu.account
                            .androidZhihuAccountStore(this@MainActivity)
                            .engine,
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to check for updates", e)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        continuousUsageReminderManager.onAppForeground()
        androidSystemNotifications(this).refresh()
    }

    override fun onStop() {
        continuousUsageReminderManager.onAppBackground()
        super.onStop()
    }

    private var pageTurnLongPressConsumed = false

    private fun pageTurnCommand(keyCode: Int): PageTurnCommand? = when (keyCode) {
        KeyEvent.KEYCODE_PAGE_DOWN -> PageTurnCommand.PageDown
        KeyEvent.KEYCODE_PAGE_UP -> PageTurnCommand.PageUp
        KeyEvent.KEYCODE_VOLUME_DOWN ->
            if (androidSettingsStore(this).getBoolean(PREF_VOLUME_KEY_PAGE_TURN, false)) {
                PageTurnCommand.PageDown
            } else {
                null
            }
        KeyEvent.KEYCODE_VOLUME_UP ->
            if (androidSettingsStore(this).getBoolean(PREF_VOLUME_KEY_PAGE_TURN, false)) {
                PageTurnCommand.PageUp
            } else {
                null
            }
        else -> null
    }

    // androidx.core 把 ComponentActivity.dispatchKeyEvent 标成库内限定，Lint 把正常的重写和 super 调用也算进去。
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val command = pageTurnCommand(event.keyCode) ?: return super.dispatchKeyEvent(event)
        if (!pageTurnDispatcher.hasActiveTarget) return super.dispatchKeyEvent(event)

        return when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                when {
                    event.isLongPress -> {
                        pageTurnLongPressConsumed = true
                        pageTurnDispatcher.dispatch(
                            if (command == PageTurnCommand.PageDown) {
                                PageTurnCommand.JumpToBottom
                            } else {
                                PageTurnCommand.JumpToTop
                            },
                        )
                    }
                    event.repeatCount == 0 -> {
                        pageTurnLongPressConsumed = false
                        true
                    }
                    else -> true
                }
            }
            KeyEvent.ACTION_UP -> {
                val consumed = pageTurnLongPressConsumed || pageTurnDispatcher.dispatch(command)
                pageTurnLongPressConsumed = false
                consumed
            }
            else -> super.dispatchKeyEvent(event)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        if (hasFocus) {
            if (!handleIntentData(intent)) {
                val clip = clipboardManager.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val text = clip.getItemAt(0).text
                    if (text != null) {
                        val regex = Regex("""https?://[-a-zA-Z0-9@:%_+.~#?&/=]*""")
                        val destination = regex.findAll(text).firstNotNullOfOrNull {
                            resolveContent(it.value)
                        }
                        if (destination != null && destination != AndroidArticleNavigationHandoff.clipboardDestination) {
                            AndroidArticleNavigationHandoff.markClipboardDestination(destination)
                            navigate(destination, popup = true)
                        }
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(APP_ERROR_INTENT_CONSUMED_STATE_KEY, internalErrorIntentConsumed)
        outState.remove(APP_ERROR_DIALOG_STATE_KEY)
        appErrorDialog.value?.let { dialog ->
            outState.putBundle(
                APP_ERROR_DIALOG_STATE_KEY,
                Bundle().apply {
                    putString("title", dialog.title)
                    putString("message", dialog.message)
                    putBoolean("copyable", dialog.copyable)
                },
            )
        }
        super.onSaveInstanceState(outState)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.data?.authority == "zhihu-plus.internal" && intent.data?.path == "/error") {
            showErrorDialog(intent)
        } else if (::navHost.isInitialized) {
            handleIntentData(intent)
        }
    }

    private fun showErrorDialog(incomingIntent: Intent) {
        if (incomingIntent.getBooleanExtra("internal_error_shown", false)) {
            internalErrorIntentConsumed = true
            incomingIntent.data = null
            return
        }
        val data = incomingIntent.data ?: return
        val details = redactDiagnosticText(
            listOfNotNull(data.getQueryParameter("title"), data.getQueryParameter("stack"))
                .filter(String::isNotBlank)
                .joinToString("\n\n"),
        )
        internalErrorIntentConsumed = true
        incomingIntent.putExtra("internal_error_shown", true)
        incomingIntent.data = null
        appErrorDialog.value = AppErrorDialogContent(
            title = "错误详情",
            message = details.ifBlank { "未知错误" },
            copyable = true,
        )
    }

    private fun handleIntentData(incomingIntent: Intent): Boolean {
        val notifications = androidSystemNotifications(this)
        if (notifications.handles(incomingIntent)) {
            if (!::navHost.isInitialized) return true
            val hasPayload = incomingIntent.data != null
            val consumed = runCatching {
                notifications.consumeClick(incomingIntent) { navigate(it, popup = true) }
            }.getOrDefault(false)
            if (hasPayload && !consumed) {
                androidUserMessageSink(this).showShortMessage("通知已失效或所属账号已切换，请在消息页查看")
            }
            return true
        }
        if (incomingIntent.getBooleanExtra("internal_error_shown", false)) {
            internalErrorIntentConsumed = true
            incomingIntent.data = null
            return true
        }
        val data = incomingIntent.data ?: return incomingIntent.action == Intent.ACTION_VIEW
        if (data.authority == "zhihu-plus.internal") {
            if (data.path == "/error") showErrorDialog(incomingIntent)
            return true
        }
        if (!::navHost.isInitialized) return true
        val forceNavigation = incomingIntent.getBooleanExtra(ContentReadingService.READING_NOTIFICATION_INTENT_EXTRA, false)
        incomingIntent.removeExtra(ContentReadingService.READING_NOTIFICATION_INTENT_EXTRA)

        val destination = resolveNavigationUrl(data.toString())
        if (destination is WebPage) {
            incomingIntent.data = null
            navigate(destination)
        } else if (destination != null) {
            if (forceNavigation || destination != AndroidArticleNavigationHandoff.clipboardDestination) {
                AndroidArticleNavigationHandoff.markClipboardDestination(destination)
                navigate(destination, popup = true)
            }
        } else {
            appErrorDialog.value = AppErrorDialogContent(
                title = "不支持的链接",
                message = redactDiagnosticText("无法识别链接：$data"),
                copyable = false,
            )
        }
        return true
    }

    /** [targetHost] 区分主栈与详情栏；[popup] 清除外部跳转前的非主壳页面。 */
    fun navigate(
        route: NavDestination,
        targetHost: AppNavHost = navHost,
        popup: Boolean = false,
    ) {
        navigationCoordinator.navigate(route, targetHost, popup)
    }

    fun setCurrentMainTabOpenFrom(openFrom: String?) {
        navigationCoordinator.setCurrentMainTabOpenFrom(openFrom)
    }

    fun consumeMainTabNavigationTarget(destination: TopLevelDestination) {
        navigationCoordinator.consumeMainTabNavigationTarget(destination)
    }

    override fun onDestroy() {
        continuousUsageReminderManager.onDestroy()
        super.onDestroy()
    }

    @Suppress("unused")
    companion object {
        private const val KEY_LAST_LAUNCH_TIMESTAMP = "last_main_launch_timestamp"
        const val IOS = "5_2.0"
        const val ANDROID = "4_2.0"
        const val WEB = "3_2.0"
        const val ZSE93 = ZHIHU_WEB_ZSE93
        const val TAG = "MainActivity"
    }
}
