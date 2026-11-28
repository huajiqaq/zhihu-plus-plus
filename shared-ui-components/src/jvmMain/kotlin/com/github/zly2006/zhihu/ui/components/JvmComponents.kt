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

package com.github.zly2006.zhihu.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.window.PopupProperties
import com.github.zly2006.zhihu.desktop.DesktopFileShareBridge
import com.github.zly2006.zhihu.desktop.copyDesktopPlainText
import com.github.zly2006.zhihu.i18n.DomainStrings
import com.github.zly2006.zhihu.i18n.WidgetsStrings
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.nlp.KeywordAnalyzerCore
import com.github.zly2006.zhihu.nlp.KeywordWithWeight
import com.github.zly2006.zhihu.nlp.jvmKeywordWeightExtractor
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import java.awt.KeyboardFocusManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal actual val hrmSelectionToolbarPopupProperties = PopupProperties(focusable = true)

actual suspend fun extractFeedKeywords(
    title: String,
    excerpt: String?,
): List<KeywordWithWeight> = KeywordAnalyzerCore.extractFromFeedWithWeight(
    title = title,
    excerpt = excerpt,
    content = null,
    topN = 10,
    extractor = jvmKeywordWeightExtractor,
)

actual val feedKeywordExtractionAvailable: Boolean = true

@Composable
actual fun rememberShareActionExecutor(): ShareActionExecutor {
    val scope = rememberCoroutineScope()
    val userMessages = rememberUserMessageSink()
    return remember(scope, userMessages) {
        object : ShareActionExecutor {
            override fun invoke(action: ShareAction, content: NavDestination, shareText: String) {
                if (action == ShareAction.CopyLink) {
                    runCatching { copyDesktopPlainText(shareText) }
                        .onSuccess { userMessages.showMessage(WidgetsStrings.linkCopied) }
                        .onFailure { userMessages.showLongMessage(DomainStrings.clipboardDenied) }
                    return
                }
                val activeWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
                val window = generateSequence(activeWindow) { it.owner }.lastOrNull()
                if (window == null) {
                    userMessages.showLongMessage(WidgetsStrings.systemShareUnavailable)
                    return
                }
                scope.launch {
                    try {
                        DesktopFileShareBridge.shareText(window, shareText, getShareTitle(content))
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        userMessages.showLongMessage(error.message ?: WidgetsStrings.systemShareFailed)
                    } catch (_: LinkageError) {
                        userMessages.showLongMessage(DomainStrings.desktopFileShareUnavailable)
                    }
                }
            }
        }
    }
}
