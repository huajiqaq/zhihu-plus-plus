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

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationEventHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.github.zly2006.zhihu.account.rememberZhihuAccountStore
import com.github.zly2006.zhihu.i18n.DomainStrings
import com.github.zly2006.zhihu.nlp.OfflineNlpController
import com.github.zly2006.zhihu.nlp.OfflineNlpModelState
import com.github.zly2006.zhihu.viewmodel.filter.KeywordSemanticMatcher
import com.github.zly2006.zhihu.viewmodel.ArticleExportResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.io.files.Path

@Composable
actual fun isSoftwareKeyboardVisible(): Boolean = WindowInsets.ime.getBottom(LocalDensity.current) > 0

@Composable
actual fun rememberSystemUrlOpener(): SystemUrlOpener = rememberExternalUrlOpener()

@Composable
actual fun rememberZhihuWebUrlOpener(): ZhihuWebUrlOpener = rememberExternalUrlOpener()

@Composable
actual fun rememberImageSaver(): ImageSaver {
    val scope = rememberCoroutineScope()
    val userMessages = rememberUserMessageSink()
    val accountStore = rememberZhihuAccountStore()
    val delivery = LocalNativeFileDelivery.current
    return remember(scope, userMessages, accountStore, delivery) {
        object : ImageSaver {
            override fun invoke(url: String) {
                scope.launch {
                    try {
                        val result = deliverNativeImage(accountStore.client.httpClient(), url, requireNativeFileDelivery(delivery), share = false)
                        if (result is ArticleExportResult.Completed) userMessages.showShortMessage(result.completionMessage)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
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
    val accountStore = rememberZhihuAccountStore()
    val delivery = LocalNativeFileDelivery.current
    return remember(scope, userMessages, accountStore, delivery) {
        object : ImageSharer {
            override fun invoke(url: String) {
                scope.launch {
                    try {
                        val result = deliverNativeImage(accountStore.client.httpClient(), url, requireNativeFileDelivery(delivery), share = true)
                        if (result is ArticleExportResult.Completed) userMessages.showShortMessage(result.completionMessage)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        userMessages.showShortMessage(DomainStrings.imageShareFailed(error.message.orEmpty()))
                    }
                }
            }
        }
    }
}

@Composable
actual fun rememberPlainTextClipboard(): PlainTextClipboard = remember {
    object : PlainTextClipboard {
        override fun invoke(label: String, text: String) = copyNativePlainText(text)
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
actual fun PlatformPredictiveBackHandler(
    enabled: Boolean,
    onProgress: (Float) -> Unit,
    onCancel: () -> Unit,
    onBack: () -> Unit,
) = PlatformBackHandler(enabled = enabled, onBack = onBack)

actual fun platformSettingsStore(): SettingsStore = nativeSettingsStore("settings.properties")

actual fun appPrivateDirectory(): Path = Path(nativeAppPrivateDirectoryPath())

actual fun appPrivateTextFile(name: String): AppTextFile = DiskTextFile(Path(appPrivateDirectory(), name))

actual val platformKeywordSemanticMatcher: KeywordSemanticMatcher?
    get() = OfflineNlpController.takeIf { it.state.value == OfflineNlpModelState.Ready }

actual val platformSemanticRuntimeGeneration: StateFlow<Long>
    get() = OfflineNlpController.policyGeneration

@Composable
actual fun rememberSettingsStore(): SettingsStore = remember { nativeSettingsStore("settings.properties") }

actual fun Modifier.exportTestTagsForUiAutomation(): Modifier = this

actual val isJvm: Boolean = false

actual val isNative: Boolean = true

actual val isArticleHtmlExportSupported: Boolean = true

actual val isArticleImageExportSupported: Boolean = true

actual val isArticlePdfExportSupported: Boolean = true
