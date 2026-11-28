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
import androidx.compose.ui.Modifier
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationEventHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.github.zly2006.zhihu.account.rememberZhihuAccountStore
import com.github.zly2006.zhihu.desktop.DesktopFileShareBridge
import com.github.zly2006.zhihu.desktop.copyDesktopPlainText
import com.github.zly2006.zhihu.desktop.desktopZhihuDataDir
import com.github.zly2006.zhihu.desktop.desktopZhihuDataFile
import com.github.zly2006.zhihu.desktop.openDesktopExternalUrl
import com.github.zly2006.zhihu.desktop.saveImageToDownloads
import com.github.zly2006.zhihu.i18n.DomainStrings
import com.github.zly2006.zhihu.nlp.OfflineNlpController
import com.github.zly2006.zhihu.nlp.OfflineNlpModelState
import com.github.zly2006.zhihu.viewmodel.filter.FilterableContent
import com.github.zly2006.zhihu.viewmodel.filter.KeywordSemanticMatcher
import java.awt.KeyboardFocusManager
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.io.files.Path

actual fun platformSettingsStore(): SettingsStore = desktopSettingsStore()

actual fun appPrivateDirectory(): Path = Path(desktopZhihuDataDir().absolutePath)

actual val platformKeywordSemanticMatcher: KeywordSemanticMatcher?
    get() = OfflineNlpController.takeIf { it.state.value == OfflineNlpModelState.Ready }

actual val platformSemanticRuntimeGeneration: StateFlow<Long>
    get() = OfflineNlpController.policyGeneration

actual suspend fun showNlpBlockedMessage(blocked: Collection<FilterableContent>) = Unit

@Composable
actual fun rememberSettingsStore(): SettingsStore = remember { desktopSettingsStore() }

@Composable
actual fun isSoftwareKeyboardVisible(): Boolean = false

actual fun Modifier.exportTestTagsForUiAutomation(): Modifier = this

private val desktopSettingsOwners = mutableMapOf<File, SettingsStore>()

fun desktopSettingsStore(relativePath: String = "settings.properties"): SettingsStore = synchronized(desktopSettingsOwners) {
    val file = desktopZhihuDataFile(relativePath).canonicalFile
    desktopSettingsOwners.getOrPut(file) { DesktopSettingsStore(file) }
}

private class DesktopSettingsStore(
    private val file: File,
) : SettingsStore() {
    private var values: Map<String, SettingValue> = if (!file.exists()) {
        emptyMap()
    } else {
        SettingsFileCodec.decode(file.readText()) ?: Properties().run {
            file.inputStream().use(::load)
            stringPropertyNames().associateWith { decodeLegacySetting(it, getProperty(it)) }
        }
    }
    private val listeners = linkedSetOf<(String) -> Unit>()

    @Synchronized
    override fun readValue(key: String): SettingValue? = values[key]

    @Synchronized
    override fun readEntries(): Map<String, SettingValue> = values.toMap()

    @Synchronized
    override fun removeByPrefix(prefix: String) = commit(
        values.keys.filter { it.startsWith(prefix) }.associateWith { null },
    )

    @Synchronized
    override fun observeKeyChanges(onChanged: (String) -> Unit): AutoCloseable {
        listeners.add(onChanged)
        return AutoCloseable { synchronized(this) { listeners.remove(onChanged) } }
    }

    @Synchronized
    override fun commit(updates: Map<String, SettingValue?>) {
        val changed = updates.filter { (key, value) -> values[key] != value }
        if (changed.isEmpty()) return
        val next = values.toMutableMap().apply {
            changed.forEach { (key, value) -> if (value == null) remove(key) else put(key, value) }
        }
        val target = file.toPath()
        Files.createDirectories(target.parent)
        val temporary = Files.createTempFile(target.parent, ".settings-", ".tmp")
        try {
            Files.writeString(temporary, SettingsFileCodec.encode(next))
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
        values = next
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
actual fun rememberSystemUrlOpener(): SystemUrlOpener {
    val messages = rememberUserMessageSink()
    return remember(messages) {
        object : SystemUrlOpener {
            override fun invoke(url: String) {
                if (!openDesktopExternalUrl(url)) messages.showShortMessage(DomainStrings.systemBrowserOpenFailed)
            }
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
                        userMessages.showShortMessage(DomainStrings.imageSavedTo(file.absolutePath))
                    }.onFailure { error ->
                        if (error is CancellationException) throw error
                        userMessages.showShortMessage(DomainStrings.imageSaveFailed(error.message.orEmpty()))
                    }
                }
            }
        }
    }
}

@Composable
actual fun rememberImageSharer(): ImageSharer {
    val scope = rememberCoroutineScope()
    val userMessages = rememberUserMessageSink()
    val store = rememberZhihuAccountStore()
    return remember(scope, userMessages, store) {
        object : ImageSharer {
            override fun invoke(url: String) {
                val activeWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
                val window = generateSequence(activeWindow) { it.owner }.lastOrNull()
                if (window == null) {
                    userMessages.showShortMessage(DomainStrings.imageShareFailed(DomainStrings.desktopFileShareUnsupported))
                    return
                }
                scope.launch {
                    try {
                        DesktopFileShareBridge.shareImage(store, url, window)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        userMessages.showShortMessage(DomainStrings.imageShareFailed(error.message.orEmpty()))
                    } catch (_: LinkageError) {
                        userMessages.showShortMessage(DomainStrings.imageShareFailed(DomainStrings.desktopFileShareUnavailable))
                    }
                }
            }
        }
    }
}

@Composable
actual fun rememberPlainTextClipboard(): PlainTextClipboard {
    val messages = rememberUserMessageSink()
    return remember(messages) {
        object : PlainTextClipboard {
            override fun invoke(label: String, text: String) {
                runCatching { copyDesktopPlainText(text) }
                    .onFailure { messages.showLongMessage(DomainStrings.clipboardDenied) }
            }
        }
    }
}

@Composable
actual fun rememberUserMessageSink(): UserMessageSink = LocalWindowUserMessages.current

@Composable
actual fun PlatformBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) = NavigationEventHandler(
    state = rememberNavigationEventState(NavigationEventInfo.None),
    isForwardEnabled = false,
    isBackEnabled = enabled,
    onBackCompleted = onBack,
)

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

actual val isAigcVoteSupported: Boolean = true

actual val isBlocklistNlpSupported: Boolean = true

actual val isArticleHtmlExportSupported: Boolean = true

actual val isArticlePdfExportSupported: Boolean = true

actual val isArticleImageExportSupported: Boolean = true

actual val isPageTurnSupported: Boolean = true

actual val isAnswerSwipeSupported: Boolean = true

actual val defaultAnswerSwitchMode: String = "off"
