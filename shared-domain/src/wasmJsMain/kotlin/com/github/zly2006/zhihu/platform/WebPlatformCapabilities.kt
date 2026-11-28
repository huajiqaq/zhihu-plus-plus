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

@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.github.zly2006.zhihu.platform

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationEventHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.github.zly2006.zhihu.i18n.DomainStrings
import com.github.zly2006.zhihu.nlp.OfflineNlpController
import com.github.zly2006.zhihu.nlp.OfflineNlpModelState
import com.github.zly2006.zhihu.viewmodel.filter.FilterableContent
import com.github.zly2006.zhihu.viewmodel.filter.KeywordSemanticMatcher
import kotlinx.coroutines.flow.StateFlow
import kotlinx.io.files.Path

actual val platformBottomBarItemLimit: Int? = null
actual val platformName = "Web"
actual val isJvm = false
actual val isNative = false
actual val isAigcVoteSupported = true
actual val isLiteVariant = false
actual val isBlocklistNlpSupported = true
actual val isArticleHtmlExportSupported = true
actual val isArticleImageExportSupported = true
actual val isArticlePdfExportSupported = true
actual val isPageTurnSupported = true
actual val isAnswerSwipeSupported = true
actual val defaultAnswerSwitchMode: String = "vertical"
actual val platformKeywordSemanticMatcher: KeywordSemanticMatcher?
    get() = OfflineNlpController.takeIf { it.state.value == OfflineNlpModelState.Ready }

actual val platformSemanticRuntimeGeneration: StateFlow<Long>
    get() = OfflineNlpController.policyGeneration

actual suspend fun showNlpBlockedMessage(blocked: Collection<FilterableContent>) = Unit

actual fun appPrivateDirectory(): Path = throw UnsupportedOperationException("浏览器不提供本机私有目录，请使用应用数据存储")

@Composable
actual fun isSoftwareKeyboardVisible(): Boolean = WindowInsets.ime.getBottom(LocalDensity.current) > 0

/** Web 的语义节点由 Compose accessibility 导出，不使用 Android 的 resource-id 映射。 */
actual fun Modifier.exportTestTagsForUiAutomation(): Modifier = this

@Composable
actual fun rememberUserMessageSink(): UserMessageSink = LocalWindowUserMessages.current

val LocalWebUrlOpener = staticCompositionLocalOf<ExternalUrlOpener> { error("当前窗口未安装外链宿主") }

@Composable
actual fun rememberExternalUrlOpener(): ExternalUrlOpener = LocalWebUrlOpener.current

@Composable
actual fun rememberSystemUrlOpener(): SystemUrlOpener = rememberExternalUrlOpener()

@Composable
actual fun rememberZhihuWebUrlOpener(): ZhihuWebUrlOpener = rememberExternalUrlOpener()

@JsFun("(text, done) => { try { navigator.clipboard.writeText(text).then(() => done(true), () => done(false)); } catch { done(false); } }")
private external fun writeClipboard(text: String, done: (Boolean) -> Unit)

@Composable
actual fun rememberPlainTextClipboard(): PlainTextClipboard {
    val messages = rememberUserMessageSink()
    return remember(messages) {
        object : PlainTextClipboard {
            override fun invoke(label: String, text: String) = writeClipboard(text) { copied ->
                if (!copied) messages.showLongMessage(DomainStrings.clipboardDenied)
            }
        }
    }
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = NavigationEventHandler(
    state = rememberNavigationEventState(NavigationEventInfo.None),
    isForwardEnabled = false,
    isBackEnabled = enabled,
    onBackCompleted = onBack,
)

@Composable
actual fun PlatformPredictiveBackHandler(enabled: Boolean, onProgress: (Float) -> Unit, onCancel: () -> Unit, onBack: () -> Unit) =
    PlatformBackHandler(enabled, onBack)
