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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.unit.em
import com.github.zly2006.zhihu.account.rememberZhihuAccountStore
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.notification.NotificationSettingsStore
import com.github.zly2006.zhihu.platform.LocalNativeFileDelivery
import com.github.zly2006.zhihu.platform.withNativeExportDirectory
import com.github.zly2006.zhihu.viewmodel.ArticleExportResult
import com.github.zly2006.zhihu.i18n.DomainStrings
import kotlinx.coroutines.CancellationException
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.writeString
import com.github.zly2006.zhihu.platform.nativeAppVersionName
import com.github.zly2006.zhihu.platform.nativeBundledResourcePath
import com.github.zly2006.zhihu.platform.nativeIsDesktop
import com.github.zly2006.zhihu.platform.platformName
import com.github.zly2006.zhihu.platform.rememberSystemUrlOpener
import com.github.zly2006.zhihu.viewmodel.NativePaginationEnvironment
import com.github.zly2006.zhihu.viewmodel.NotificationEnvironment
import com.github.zly2006.zhihu.viewmodel.filter.encodeBlocklistBackup
import com.github.zly2006.zhihu.viewmodel.filter.getContentFilterDatabase
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.serialization.json.Json
import platform.Foundation.NSFileManager
import org.jetbrains.skia.Image as SkiaImage

@Composable
actual fun rememberArticleTtsState(): TtsState = rememberContinuousArticleTtsState()

@Composable
actual fun rememberArticleSpeechToggler(): (String, String) -> Unit = rememberContinuousArticleSpeechToggler()

@Composable
actual fun rememberArticleBrowserOpener(): (Article) -> Unit {
    val openSystemUrl = rememberSystemUrlOpener()
    return { article -> openSystemUrl(articleWebUrl(article)) }
}

@Composable
actual fun rememberNotificationEnvironment(
    settingsStore: NotificationSettingsStore,
): NotificationEnvironment {
    val store = rememberZhihuAccountStore()
    return remember(settingsStore, store) { NativePaginationEnvironment(notificationSettingsStore = settingsStore, accountStore = store) }
}

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
actual fun rememberCommentEmojiInlineContent(emojiKeys: Set<String>): Map<String, InlineTextContent> =
    remember(emojiKeys) {
        emojiKeys
            .mapNotNull { emojiKey ->
                val imageBytes = nativeEmojiBytesByInlineKey(emojiKey) ?: return@mapNotNull null
                emojiKey to InlineTextContent(
                    placeholder = Placeholder(
                        width = 1.3.em,
                        height = 1.3.em,
                        placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                    ),
                ) {
                    val image = remember(imageBytes) {
                        runCatching { SkiaImage.makeFromEncoded(imageBytes).toComposeImageBitmap() }.getOrNull()
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
    nativeEmojiMapping.mapNotNull { (placeholder, fileName) ->
        val inlineKey = "emoji_$fileName"
        nativeEmojiBytesByInlineKey(inlineKey)?.let {
            CommentEmoji(placeholder = placeholder, inlineKey = inlineKey)
        }
    }
}

actual fun commentEmojiInlineKey(placeholder: String): String? =
    nativeEmojiMapping[placeholder]?.let { fileName -> "emoji_$fileName" }

@Composable
actual fun rememberCommentEmojiBitmaps(inlineKeys: Set<String>): Map<String, ImageBitmap> =
    remember(inlineKeys) {
        inlineKeys
            .mapNotNull { inlineKey ->
                val imageBytes = nativeEmojiBytesByInlineKey(inlineKey) ?: return@mapNotNull null
                val bitmap = runCatching { SkiaImage.makeFromEncoded(imageBytes).toComposeImageBitmap() }.getOrNull()
                bitmap?.let { inlineKey to it }
            }.toMap()
    }

actual fun Modifier.commentSelectionWorkaround(): Modifier = this

private val nativeEmojiMapping: Map<String, String> by lazy {
    val mappingPath = nativeBundledResourcePath("misc/emoji_mapping.json") ?: return@lazy emptyMap()
    val mappingBytes = readNativeFileBytes(mappingPath) ?: return@lazy emptyMap()
    runCatching {
        Json.decodeFromString<Map<String, String>>(mappingBytes.decodeToString())
    }.getOrDefault(emptyMap())
}

private fun nativeEmojiBytesByInlineKey(emojiKey: String): ByteArray? {
    val fileName = emojiKey.removePrefix("emoji_")
    val imagePath = nativeBundledResourcePath("misc/emojis/$fileName") ?: return null
    return readNativeFileBytes(imagePath)
}

@OptIn(ExperimentalForeignApi::class)
private fun readNativeFileBytes(filePath: String): ByteArray? {
    val data = NSFileManager.defaultManager.contentsAtPath(filePath) ?: return null
    return data.bytes?.reinterpret<ByteVar>()?.readBytes(data.length.toInt())
}

@Composable
actual fun rememberHomeIsDebuggable(): Boolean = nativeIsDesktop

@Composable
actual fun rememberAppVersionInfo(): String = nativeAppVersionName

@Composable
actual fun ZhihuHtmlWebViewContent(html: String): Unit = error("$platformName 暂不支持 HTML WebView 渲染")

actual val isLegacyWebViewSupported: Boolean = false

@Composable
actual fun rememberBlocklistRuleExporter(): suspend () -> String {
    val database = remember { getContentFilterDatabase() }
    val delivery = LocalNativeFileDelivery.current
    return {
        val host = checkNotNull(delivery) { DomainStrings.exportHostUnavailable }
        val text = encodeBlocklistBackup(
            keywordDao = database.blockedKeywordDao(),
            userDao = database.blockedUserDao(),
            questionAuthorDao = database.blockedQuestionAuthorDao(),
            topicDao = database.blockedTopicDao(),
        )
        when (val result = withNativeExportDirectory { directory ->
            val output = "$directory/zhihupp_blocklist.json"
            SystemFileSystem.sink(Path(output)).buffered().use { it.writeString(text) }
            host.saveFile(output, image = false)
        }) {
            is ArticleExportResult.Completed -> result.completionMessage
            ArticleExportResult.Cancelled -> throw CancellationException("Blocklist export cancelled")
        }
    }
}

@Composable
actual fun QuestionDetailWebViewContent(questionId: Long, html: String): Unit =
    error("$platformName 暂不支持问题详情 WebView 渲染")

actual fun Modifier.questionSelectionWorkaround(): Modifier = this

@Composable
actual fun ArticleImmersiveModeEffect(immersive: Boolean) = Unit

@Composable
actual fun LeaveImmersiveModeCleanup() = Unit
