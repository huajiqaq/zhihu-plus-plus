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

package com.github.zly2006.zhihu.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.unit.em
import com.github.zly2006.zhihu.desktop.openDesktopExternalUrl
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.notification.NotificationSettingsStore
import com.github.zly2006.zhihu.platform.UserMessageSink
import com.github.zly2006.zhihu.platform.platformName
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.ui.subscreens.desktopVersionName
import com.github.zly2006.zhihu.viewmodel.DesktopPaginationEnvironment
import com.github.zly2006.zhihu.viewmodel.filter.desktopContentFilterDatabaseFile
import com.github.zly2006.zhihu.viewmodel.filter.encodeBlocklistBackup
import com.github.zly2006.zhihu.viewmodel.filter.getContentFilterDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URL
import javax.imageio.ImageIO

@Composable
actual fun rememberArticleTtsState(): TtsState = DesktopArticleSpeechController.currentTtsState

@Composable
actual fun rememberArticleSpeechToggler(): (String, String) -> Unit {
    val userMessages = rememberUserMessageSink()
    val coroutineScope = rememberCoroutineScope()
    return { title, content -> DesktopArticleSpeechController.toggleSpeech(title, content, coroutineScope, userMessages) }
}

@Composable
actual fun rememberArticleBrowserOpener(): (Article) -> Unit {
    val userMessages = rememberUserMessageSink()
    return { article ->
        if (openDesktopExternalUrl(articleWebUrl(article))) userMessages.showMessage("已发送到浏览器")
    }
}

private object DesktopArticleSpeechController {
    private var speechProcess: Process? = null
    var currentTtsState by mutableStateOf(
        if (isDesktopSpeechCommandAvailable()) TtsState.Ready else TtsState.Error,
    )
        private set

    fun toggleSpeech(
        title: String,
        content: String,
        coroutineScope: kotlinx.coroutines.CoroutineScope,
        userMessages: UserMessageSink,
    ) {
        if (currentTtsState.isSpeaking) {
            stopSpeaking()
        } else if (currentTtsState !in listOf(TtsState.Error, TtsState.Uninitialized, TtsState.Initializing)) {
            coroutineScope.launch {
                try {
                    val textToRead = withContext(Dispatchers.IO) {
                        articleSpeechText(title, content)
                    }
                    if (textToRead.isNotBlank()) {
                        speakText(textToRead, title, userMessages)
                    }
                } catch (e: Exception) {
                    currentTtsState = TtsState.Error
                    userMessages.showMessage("朗读失败：${e.message}")
                }
            }
        }
    }

    private suspend fun speakText(
        text: String,
        title: String,
        userMessages: UserMessageSink,
    ) {
        currentTtsState = TtsState.LoadingText
        val process = withContext(Dispatchers.IO) {
            ProcessBuilder("say")
                .redirectErrorStream(true)
                .start()
        }
        speechProcess = process
        currentTtsState = TtsState.Speaking
        userMessages.showMessage("开始朗读：$title")
        val exitCode = withContext(Dispatchers.IO) {
            process.outputStream.bufferedWriter().use { writer ->
                writer.write(text)
            }
            process.waitFor()
        }
        if (speechProcess == process) {
            speechProcess = null
            currentTtsState = if (exitCode == 0) TtsState.Ready else TtsState.Error
        }
    }

    private fun stopSpeaking() {
        speechProcess?.destroy()
        speechProcess = null
        currentTtsState = TtsState.Ready
    }
}

private fun isDesktopSpeechCommandAvailable(): Boolean =
    runCatching {
        ProcessBuilder("sh", "-c", "command -v say >/dev/null 2>&1")
            .start()
            .waitFor() == 0
    }.getOrDefault(false)

@Composable
actual fun rememberCommentEmojiInlineContent(emojiKeys: Set<String>): Map<String, InlineTextContent> =
    remember(emojiKeys) {
        emojiKeys
            .mapNotNull { emojiKey ->
                val imageResource = desktopEmojiResourceByInlineKey(emojiKey) ?: return@mapNotNull null
                emojiKey to InlineTextContent(
                    placeholder = Placeholder(
                        width = 1.3.em,
                        height = 1.3.em,
                        placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                    ),
                ) {
                    val image = remember(imageResource) {
                        runCatching {
                            ImageIO.read(imageResource)?.toComposeImageBitmap()
                        }.getOrNull()
                    }
                    image?.let {
                        Image(
                            bitmap = it,
                            contentDescription = emojiKey,
                            modifier = Modifier,
                        )
                    }
                }
            }.toMap()
    }

@Composable
actual fun rememberCommentEmojis(): List<CommentEmoji> = remember {
    desktopEmojiMapping().mapNotNull { (placeholder, fileName) ->
        val inlineKey = "emoji_$fileName"
        desktopEmojiResourceByInlineKey(inlineKey)?.let {
            CommentEmoji(placeholder = placeholder, inlineKey = inlineKey)
        }
    }
}

actual fun commentEmojiInlineKey(placeholder: String): String? =
    desktopEmojiMapping()[placeholder]?.let { fileName -> "emoji_$fileName" }

@Composable
actual fun rememberCommentEmojiBitmaps(inlineKeys: Set<String>): Map<String, ImageBitmap> =
    remember(inlineKeys) {
        inlineKeys
            .mapNotNull { inlineKey ->
                val imageResource = desktopEmojiResourceByInlineKey(inlineKey) ?: return@mapNotNull null
                val bitmap = runCatching { ImageIO.read(imageResource)?.toComposeImageBitmap() }.getOrNull()
                bitmap?.let { inlineKey to it }
            }.toMap()
    }

actual fun Modifier.commentSelectionWorkaround(): Modifier = this

private fun desktopEmojiResourceByInlineKey(emojiKey: String): URL? =
    CommentEmoji::class.java.getResource("/misc/emojis/${emojiKey.removePrefix("emoji_")}")

private fun desktopEmojiMapping(): Map<String, String> = runCatching {
    CommentEmoji::class.java
        .getResourceAsStream("/misc/emoji_mapping.json")
        ?.bufferedReader(Charsets.UTF_8)
        ?.use { Json.decodeFromString<Map<String, String>>(it.readText()) }
        ?: emptyMap()
}.getOrDefault(emptyMap())

@Composable
actual fun rememberHomeIsDebuggable(): Boolean = true

@Composable
actual fun rememberBlocklistRuleExporter(): suspend () -> String {
    val database = remember { getContentFilterDatabase() }
    return {
        val file = File(desktopContentFilterDatabaseFile().parentFile, "zhihupp_blocklist.json")
        file.writeText(
            encodeBlocklistBackup(
                keywordDao = database.blockedKeywordDao(),
                userDao = database.blockedUserDao(),
                questionAuthorDao = database.blockedQuestionAuthorDao(),
                topicDao = database.blockedTopicDao(),
            ),
        )
        "已导出到 ${file.absolutePath}"
    }
}

@Composable
actual fun rememberAppVersionInfo(): String = desktopVersionName()

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
): Unit = error("$platformName 暂不支持文章 WebView 渲染")

actual fun Modifier.articleMarkdownSelectionWorkaround(): Modifier = this

@Composable
actual fun ZhihuHtmlWebViewContent(html: String): Unit = error("$platformName 暂不支持 HTML WebView 渲染")

actual val isLegacyWebViewSupported: Boolean = false

@Composable
actual fun rememberNotificationEnvironment(
    settingsStore: NotificationSettingsStore,
): com.github.zly2006.zhihu.viewmodel.NotificationEnvironment {
    val store = com.github.zly2006.zhihu.account
        .rememberZhihuAccountStore()
    return remember(settingsStore, store) {
        DesktopPaginationEnvironment(notificationSettingsStore = settingsStore, store = store)
    }
}

@Composable
actual fun QuestionDetailWebViewContent(
    questionId: Long,
    html: String,
) {
    error("$platformName 暂不支持问题详情 WebView 渲染")
}

actual fun Modifier.questionSelectionWorkaround(): Modifier = this

@Composable
actual fun ArticleImmersiveModeEffect(immersive: Boolean) = Unit

@Composable
actual fun LeaveImmersiveModeCleanup() = Unit
