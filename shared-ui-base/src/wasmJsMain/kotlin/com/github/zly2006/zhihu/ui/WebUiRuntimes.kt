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

@file:OptIn(ExperimentalWasmJsInterop::class)

package com.github.zly2006.zhihu.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.github.zly2006.zhihu.i18n.ReaderStrings
import com.github.zly2006.zhihu.markdown.RenderMarkdown
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.notification.NotificationSettingsStore
import com.github.zly2006.zhihu.platform.rememberSystemUrlOpener
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.viewmodel.NotificationEnvironment
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment
import com.github.zly2006.zhihu.viewmodel.filter.encodeBlocklistBackup
import com.github.zly2006.zhihu.viewmodel.filter.getContentFilterDatabase
import com.github.zly2006.zhihu.viewmodel.rememberPaginationEnvironment

@Composable
actual fun rememberNotificationEnvironment(settingsStore: NotificationSettingsStore): NotificationEnvironment {
    val pagination = rememberPaginationEnvironment(allowGuestAccess = false)
    return remember(pagination, settingsStore) {
        object : NotificationEnvironment, PaginationEnvironment by pagination {
            override val notificationSettingsStore = settingsStore
        }
    }
}

@Composable
actual fun rememberArticleBrowserOpener(): (Article) -> Unit {
    val open = rememberSystemUrlOpener()
    return { open(articleWebUrl(it)) }
}

actual val isLegacyWebViewSupported: Boolean = false

@Composable
actual fun ZhihuHtmlWebViewContent(html: String) = RenderMarkdown(html = html, enableScroll = false)

@Composable
actual fun ArticleWebViewContent(
    article: Article,
    html: String,
    title: String,
    scrollState: ScrollState,
    rememberedScrollY: Int,
    rememberedScrollYSync: Boolean,
    onRememberedScrollYSyncChange: (Boolean) -> Unit,
    onImageLoadFailed: () -> Unit,
    onDoubleTap: () -> Unit,
) = RenderMarkdown(html = html, scrollState = scrollState, enableScroll = false)

@Composable
actual fun QuestionDetailWebViewContent(questionId: Long, html: String) = RenderMarkdown(html = html, enableScroll = false)

actual fun Modifier.articleMarkdownSelectionWorkaround(): Modifier = this

actual fun Modifier.commentSelectionWorkaround(): Modifier = this

actual fun Modifier.questionSelectionWorkaround(): Modifier = this

@Composable
actual fun ArticleImmersiveModeEffect(immersive: Boolean) = Unit

@Composable
actual fun LeaveImmersiveModeCleanup() = Unit

@Composable
actual fun rememberAppVersionInfo(): String = webAppVersion

@Composable
actual fun rememberHomeIsDebuggable(): Boolean = false

actual val isAccountQrScanSupported: Boolean = false

@Composable
actual fun rememberAccountQrScanAction(): () -> Unit {
    val messages = rememberUserMessageSink()
    return { messages.showShortMessage(ReaderStrings.webQrUnavailable) }
}

@Composable
actual fun rememberBlocklistRuleExporter(): suspend () -> String = {
    val db = getContentFilterDatabase()
    val json = encodeBlocklistBackup(db.blockedKeywordDao(), db.blockedUserDao(), db.blockedQuestionAuthorDao(), db.blockedTopicDao())
    downloadBlocklist(json)
    ReaderStrings.blocklistDownloadRequested
}

private fun downloadBlocklist(text: String): Unit = js(
    """{
        const url = URL.createObjectURL(new Blob([text], {type: 'application/json;charset=utf-8'}));
        try {
            const link = document.createElement('a');
            link.href = url;
            link.download = 'zhihupp_blocklist.json';
            link.click();
        } finally {
            setTimeout(() => URL.revokeObjectURL(url), 60000);
        }
    }""",
)
