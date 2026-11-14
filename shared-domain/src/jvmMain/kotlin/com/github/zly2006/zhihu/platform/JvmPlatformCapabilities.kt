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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import com.github.zly2006.zhihu.account.rememberZhihuAccountStore
import com.github.zly2006.zhihu.desktop.copyDesktopPlainText
import com.github.zly2006.zhihu.desktop.desktopZhihuDataDir
import com.github.zly2006.zhihu.desktop.desktopZhihuDataFile
import com.github.zly2006.zhihu.desktop.openDesktopExternalUrl
import com.github.zly2006.zhihu.desktop.saveImageToDownloads
import com.github.zly2006.zhihu.viewmodel.filter.FilterableContent
import com.github.zly2006.zhihu.viewmodel.filter.KeywordSemanticMatcher
import kotlinx.coroutines.launch
import kotlinx.io.files.Path
import java.io.File
import java.util.Properties

actual fun platformSettingsStore(): SettingsStore = desktopSettingsStore()

actual fun appPrivateDirectory(): Path = Path(desktopZhihuDataDir().absolutePath)

actual val platformKeywordSemanticMatcher: KeywordSemanticMatcher? = null

actual suspend fun showNlpBlockedMessage(blocked: Collection<FilterableContent>) = Unit

@Composable
actual fun rememberSettingsStore(): SettingsStore = remember { desktopSettingsStore() }

actual fun Modifier.exportTestTagsForUiAutomation(): Modifier = this

@Composable
actual fun rememberAppPrivateDirectory(): Path = remember { Path(desktopZhihuDataDir().absolutePath) }

private val desktopSettingsOwners = mutableMapOf<File, SettingsStore>()

fun desktopSettingsStore(): SettingsStore = synchronized(desktopSettingsOwners) {
    val file = desktopZhihuDataFile("settings.properties").canonicalFile
    desktopSettingsOwners.getOrPut(file) { DesktopSettingsStore(file) }
}

private class DesktopSettingsStore(
    private val file: File,
) : SettingsStore {
    private var properties = Properties().apply {
        if (file.isFile) file.inputStream().use(::load)
    }
    private val listeners = linkedSetOf<(String) -> Unit>()

    @Synchronized
    override fun getBoolean(key: String, defaultValue: Boolean) = properties.getProperty(key)?.toBooleanStrictOrNull() ?: defaultValue

    override fun putBoolean(key: String, value: Boolean) = commit(mapOf(key to value.toString()))

    @Synchronized
    override fun getString(key: String, defaultValue: String) = properties.getProperty(key) ?: defaultValue

    override fun putString(key: String, value: String) = commit(mapOf(key to value))

    @Synchronized
    override fun getStringOrNull(key: String) = properties.getProperty(key)

    override fun putStringSet(key: String, value: Set<String>) = commit(mapOf(key to value.joinToString("\u001F")))

    @Synchronized
    override fun getStringSet(key: String, defaultValue: Set<String>) = properties
        .getProperty(key)
        ?.split("\u001F")
        ?.filter(String::isNotEmpty)
        ?.toSet() ?: defaultValue

    @Synchronized
    override fun getInt(key: String, defaultValue: Int) = properties.getProperty(key)?.toIntOrNull() ?: defaultValue

    override fun putInt(key: String, value: Int) = commit(mapOf(key to value.toString()))

    @Synchronized
    override fun getLong(key: String, defaultValue: Long) = properties.getProperty(key)?.toLongOrNull() ?: defaultValue

    override fun putLong(key: String, value: Long) = commit(mapOf(key to value.toString()))

    @Synchronized
    override fun getFloat(key: String, defaultValue: Float) = properties.getProperty(key)?.toFloatOrNull() ?: defaultValue

    override fun putFloat(key: String, value: Float) = commit(mapOf(key to value.toString()))

    @Synchronized
    override fun allEntries(): Map<String, Any> = properties.stringPropertyNames().associateWith { properties.getProperty(it) }

    override fun remove(key: String) = commit(mapOf(key to null))

    @Synchronized
    override fun removeByPrefix(prefix: String) = commit(
        properties.stringPropertyNames().filter { it.startsWith(prefix) }.associateWith { null },
    )

    @Synchronized
    override fun observeKeyChanges(onChanged: (String) -> Unit): AutoCloseable {
        listeners.add(onChanged)
        return AutoCloseable { synchronized(this) { listeners.remove(onChanged) } }
    }

    @Synchronized
    private fun commit(updates: Map<String, String?>) {
        val changed = updates.filter { (key, value) -> properties.getProperty(key) != value }
        if (changed.isEmpty()) return
        val next = Properties().apply {
            putAll(properties)
            changed.forEach { (key, value) -> if (value == null) remove(key) else setProperty(key, value) }
        }
        file.parentFile?.mkdirs()
        file.outputStream().use { next.store(it, "Zhihu++ desktop settings") }
        properties = next
        changed.keys.forEach { key -> listeners.toList().forEach { it(key) } }
    }
}

@Composable
actual fun rememberExternalUrlOpener(): ExternalUrlOpener = remember {
    object : ExternalUrlOpener {
        override fun invoke(url: String) {
            openDesktopExternalUrl(url)
        }
    }
}

@Composable
actual fun rememberWebViewUrlOpener(): WebViewUrlOpener = remember {
    object : WebViewUrlOpener {
        override fun invoke(url: String) {
            openDesktopExternalUrl(url)
        }
    }
}

@Composable
actual fun rememberSystemUrlOpener(): SystemUrlOpener = remember {
    object : SystemUrlOpener {
        override fun invoke(url: String) {
            openDesktopExternalUrl(url)
        }
    }
}

@Composable
actual fun rememberZhihuWebUrlOpener(): ZhihuWebUrlOpener = remember {
    object : ZhihuWebUrlOpener {
        override fun invoke(url: String) {
            openDesktopExternalUrl(url)
        }
    }
}

@Composable
actual fun rememberImageSaver(): ImageSaver {
    val scope = rememberCoroutineScope()
    val userMessages = rememberUserMessageSink()
    val store = rememberZhihuAccountStore()
    return remember(scope, userMessages, store) {
        object : ImageSaver {
            override fun invoke(url: String) {
                scope.launch {
                    runCatching {
                        store.saveImageToDownloads(url, "image")
                    }.onSuccess { file ->
                        userMessages.showShortMessage("已保存图片: ${file.absolutePath}")
                    }.onFailure { error ->
                        userMessages.showShortMessage("保存失败: ${error.message}")
                    }
                }
            }
        }
    }
}

@Composable
actual fun rememberImageSharer(): ImageSharer {
    val userMessages = rememberUserMessageSink()
    return remember(userMessages) {
        object : ImageSharer {
            override fun invoke(url: String) {
                runCatching {
                    copyDesktopPlainText(url)
                    userMessages.showShortMessage("已复制图片链接")
                }.onFailure { error ->
                    userMessages.showShortMessage("分享失败: ${error.message}")
                }
            }
        }
    }
}

@Composable
actual fun rememberPlainTextClipboard(): PlainTextClipboard =
    remember {
        object : PlainTextClipboard {
            override fun invoke(label: String, text: String) {
                runCatching { copyDesktopPlainText(text) }
            }
        }
    }

@Composable
actual fun rememberUserMessageSink(): UserMessageSink = remember {
    object : UserMessageSink {
        override fun showShortMessage(message: String) {
            println(message)
            runCatching {
                ProcessBuilder("terminal-notifier", "-message", message, "-sound", "default")
                    .start()
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
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
) = PlatformBackHandler(enabled = enabled, onBack = onBack)

actual val isLiteVariant: Boolean = false

actual val platformBottomBarItemLimit: Int? = null

actual val platformName: String = "JVM"

actual val isJvm: Boolean = true

actual val isNative: Boolean = false

actual val isAigcVoteSupported: Boolean = false

actual val isBlocklistNlpSupported: Boolean = false

actual val isSentenceSimilaritySupported: Boolean = false

actual val includesFullVariantLicenses: Boolean = false

actual val isArticleHtmlExportSupported: Boolean = true

actual val isArticleImageExportSupported: Boolean = true

actual val isPageTurnSupported: Boolean = false

actual val isAnswerSwipeSupported: Boolean = false
