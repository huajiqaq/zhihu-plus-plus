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
import androidx.compose.ui.Modifier
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.WebPage
import com.github.zly2006.zhihu.viewmodel.filter.FilterableContent
import com.github.zly2006.zhihu.viewmodel.filter.KeywordSemanticMatcher
import kotlinx.coroutines.flow.StateFlow
import kotlinx.io.files.Path

expect val platformBottomBarItemLimit: Int?

expect val platformName: String

expect val isJvm: Boolean

expect val isNative: Boolean

expect val isAigcVoteSupported: Boolean

expect val isLiteVariant: Boolean

expect val isBlocklistNlpSupported: Boolean

expect val isArticleHtmlExportSupported: Boolean

expect val isArticlePdfExportSupported: Boolean

expect val isArticleImageExportSupported: Boolean

expect val isPageTurnSupported: Boolean

expect val isAnswerSwipeSupported: Boolean

/** 开放桌面手势能力不改变此前默认关闭的阅读行为。 */
expect val defaultAnswerSwitchMode: String

enum class UserMessageDuration {
    Short,
    Long,
}

interface UserMessageSink {
    fun showShortMessage(message: String)

    fun showLongMessage(message: String) = showShortMessage(message)

    fun showMessage(
        message: String,
        duration: UserMessageDuration = UserMessageDuration.Short,
    ) {
        when (duration) {
            UserMessageDuration.Short -> showShortMessage(message)
            UserMessageDuration.Long -> showLongMessage(message)
        }
    }
}

@Composable
expect fun rememberUserMessageSink(): UserMessageSink

interface SystemUrlOpener {
    operator fun invoke(url: String)
}

interface ZhihuWebUrlOpener {
    operator fun invoke(url: String)
}

interface ImagePreviewOpener {
    operator fun invoke(url: String)
}

interface ExternalUrlOpener :
    SystemUrlOpener,
    ZhihuWebUrlOpener,
    ImagePreviewOpener

/** 在应用内嵌 WebView 中打开第三方页面。 */
fun interface WebViewUrlOpener {
    operator fun invoke(url: String)
}

interface ImageGalleryOpener {
    operator fun invoke(urls: List<String>, initialIndex: Int)
}

interface ImageSaver {
    operator fun invoke(url: String)
}

interface ImageSharer {
    operator fun invoke(url: String)
}

interface PlainTextClipboard {
    operator fun invoke(label: String, text: String)
}

expect fun platformSettingsStore(): SettingsStore

expect fun appPrivateDirectory(): Path

expect val platformKeywordSemanticMatcher: KeywordSemanticMatcher?

/** 语义模型进入或离开可推理状态时变化；不随下载进度变化，Android 保持原模型生命周期。 */
expect val platformSemanticRuntimeGeneration: StateFlow<Long>

expect suspend fun showNlpBlockedMessage(blocked: Collection<FilterableContent>)

@Composable
expect fun rememberSettingsStore(): SettingsStore

/** 当前窗口的软件键盘可见性，不按输入焦点推断。 */
@Composable
expect fun isSoftwareKeyboardVisible(): Boolean

expect fun Modifier.exportTestTagsForUiAutomation(): Modifier

@Composable
expect fun rememberExternalUrlOpener(): ExternalUrlOpener

@Composable
fun rememberWebViewUrlOpener(): WebViewUrlOpener {
    val navigate = LocalNavigator.current.onNavigate
    return remember(navigate) { WebViewUrlOpener { navigate(WebPage(it)) } }
}

@Composable
expect fun rememberSystemUrlOpener(): SystemUrlOpener

@Composable
expect fun rememberZhihuWebUrlOpener(): ZhihuWebUrlOpener

@Composable
expect fun rememberImageSaver(): ImageSaver

@Composable
expect fun rememberImageSharer(): ImageSharer

@Composable
expect fun rememberPlainTextClipboard(): PlainTextClipboard

@Composable
expect fun PlatformBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
)

@Composable
expect fun PlatformPredictiveBackHandler(
    enabled: Boolean,
    onProgress: (Float) -> Unit,
    onCancel: () -> Unit,
    onBack: () -> Unit,
)
