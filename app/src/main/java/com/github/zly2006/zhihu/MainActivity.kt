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
import android.app.AlertDialog
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.lifecycleScope
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.NetworkClient
import coil3.network.NetworkFetcher
import coil3.network.NetworkRequest
import coil3.network.NetworkResponse
import coil3.network.ktor3.asNetworkClient
import coil3.request.crossfade
import com.github.zly2006.zhihu.account.androidZhihuAccountStore
import com.github.zly2006.zhihu.data.AccountData
import com.github.zly2006.zhihu.data.LocalHistoryStorage
import com.github.zly2006.zhihu.data.isZhihuRequestHost
import com.github.zly2006.zhihu.filter.ContentOpenEventSupport
import com.github.zly2006.zhihu.navigation.AppNavHost
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.CollectionContent
import com.github.zly2006.zhihu.navigation.CommentHolder
import com.github.zly2006.zhihu.navigation.History
import com.github.zly2006.zhihu.navigation.Home
import com.github.zly2006.zhihu.navigation.MainTabs
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.Notification
import com.github.zly2006.zhihu.navigation.Pin
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.navigation.TopLevelDestination
import com.github.zly2006.zhihu.navigation.Video
import com.github.zly2006.zhihu.navigation.rememberAppNavHost
import com.github.zly2006.zhihu.navigation.resolveContent
import com.github.zly2006.zhihu.nlp.KeywordWeightExtractor
import com.github.zly2006.zhihu.nlp.NLPService
import com.github.zly2006.zhihu.nlp.NlpServiceKeywordSemanticMatcher
import com.github.zly2006.zhihu.nlp.SentenceEmbeddingManager
import com.github.zly2006.zhihu.platform.androidSettingsStore
import com.github.zly2006.zhihu.platform.androidUserMessageSink
import com.github.zly2006.zhihu.reading.ContentReadingService
import com.github.zly2006.zhihu.theme.AndroidThemeSettings
import com.github.zly2006.zhihu.theme.ZhihuTheme
import com.github.zly2006.zhihu.ui.AndroidArticleNavigationHandoff
import com.github.zly2006.zhihu.ui.AndroidZhihuMain
import com.github.zly2006.zhihu.ui.CommentNavigationHandoff
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

class MainActivity : ComponentActivity() {
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
    private var currentMainTabOpenFrom: String? = null
    var mainTabNavigationTarget by mutableStateOf<TopLevelDestination?>(null)
        private set

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
                if (accountClient.credentialRefresher.refresh()) Log.i(TAG, "Zhihu mobile credentials renewed successfully")
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "Failed to renew Zhihu mobile credentials", error)
                androidUserMessageSink(this@MainActivity).showLongMessage("刷新登录状态失败，请稍后重试")
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
            ZhihuTheme {
                CompositionLocalProvider(LocalPageTurnDispatcher provides pageTurnDispatcher) {
                    Box(Modifier.semantics { testTagsAsResourceId = true }) {
                        AndroidZhihuMain(navHost = navHost)
                        PageTurnFab(dispatcher = pageTurnDispatcher)
                    }
                }
            }
        }
        if (savedInstanceState == null) {
            telemetry(this, "start")
            if (intent.data != null) {
                if (intent.data!!.authority == "zhihu-plus.internal") {
                    if (intent.data!!.path == "/error") {
                        val title = intent.data!!.getQueryParameter("title")

                        val stack = intent.data!!.getQueryParameter("stack")
                        AlertDialog
                            .Builder(this)
                            .apply {
                                setTitle(title)
                                setMessage(stack)
                                setPositiveButton("OK") { _, _ ->
                                }
                                setNeutralButton("Copy") { _, _ ->
                                    val clip = ClipData.newPlainText("error", "$stack")
                                    clipboardManager.setPrimaryClip(clip)
                                }
                            }.create()
                            .show()
                    }
                }
            }
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (::navHost.isInitialized) {
            handleIntentData(intent)
        }
    }

    private fun handleIntentData(incomingIntent: Intent): Boolean {
        val data = incomingIntent.data ?: return false
        if (data.authority == "zhihu-plus.internal") return true
        val forceNavigation = incomingIntent.getBooleanExtra(ContentReadingService.READING_NOTIFICATION_INTENT_EXTRA, false)
        incomingIntent.removeExtra(ContentReadingService.READING_NOTIFICATION_INTENT_EXTRA)

        Log.i(TAG, "Intent data: $data")
        val destination = resolveContent(data.toString())
        if (destination != null) {
            if (forceNavigation || destination != AndroidArticleNavigationHandoff.clipboardDestination) {
                AndroidArticleNavigationHandoff.markClipboardDestination(destination)
                navigate(destination, popup = true)
            }
        } else {
            AlertDialog
                .Builder(this)
                .apply {
                    setTitle("Unsupported URL")
                    setMessage("Unknown URL: $data")
                    setPositiveButton("OK") { _, _ -> }
                }.create()
                .show()
        }
        return true
    }

    /** [targetHost] 区分主栈与详情栏；[popup] 清除外部跳转前的非主壳页面。 */
    fun navigate(
        route: NavDestination,
        targetHost: AppNavHost = navHost,
        popup: Boolean = false,
    ) {
        if (route is CommentHolder) {
            CommentNavigationHandoff.prepareComment(route)
            navigate(route.article, targetHost, popup)
            return
        }
        CommentNavigationHandoff.clearCommentUnless(route)
        preparePendingContentOpen(route, targetHost)
        history.add(route)
        if (route is Video) {
            val current = when (val top = targetHost.top) {
                is Article, is Question -> top
                else -> null
            }
            if (current == null) {
                androidUserMessageSink(this).showShortMessage("无法打开视频：未知的内容类型")
                return
            }
            val (contentId, contentType) = when (current) {
                is Article -> {
                    current.id.toString() to when (current.type) {
                        ArticleType.Answer -> "answer"
                        ArticleType.Article -> "article"
                    }
                }
                is Question -> {
                    current.questionId.toString() to "question"
                }
                else -> error("Unsupported content type for video: $current")
            }
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
            return
        }
        if (route == MainTabs) {
            mainTabNavigationTarget = Home
            navHost.popToRoot()
            return
        }
        if (popup && targetHost.contains(MainTabs)) {
            targetHost.popToRoot()
        }
        targetHost.open(route)
    }

    /** 内容打开来源取触发导航的返回栈，避免误用主栈页面。 */
    private fun preparePendingContentOpen(
        target: NavDestination,
        sourceHost: AppNavHost,
    ) {
        val openFrom = if (navHost.top == MainTabs) {
            currentMainTabOpenFrom
        } else {
            null
        }
            ?: ContentOpenEventSupport.inferOpenFrom(currentContentOpenSource(sourceHost), target)
        ContentOpenEventSupport.prepareContentOpen(target, openFrom)
    }

    fun setCurrentMainTabOpenFrom(openFrom: String?) {
        currentMainTabOpenFrom = openFrom
    }

    fun consumeMainTabNavigationTarget(destination: TopLevelDestination) {
        if (mainTabNavigationTarget == destination) {
            mainTabNavigationTarget = null
        }
    }

    private fun currentContentOpenSource(host: AppNavHost = navHost): NavDestination? = when (val top = host.top) {
        is Article, is Question, is Pin, is CollectionContent, History, Notification -> top
        else -> null
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
