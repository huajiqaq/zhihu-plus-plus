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
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.unit.em
import org.jetbrains.compose.resources.imageResource
import zhihu.shared_ui_base.generated.resources.Res
import zhihu.shared_ui_base.generated.resources.allDrawableResources

private val emojis = webEmojiFiles.map { (placeholder, file) -> CommentEmoji(placeholder, "emoji_$file") }

actual fun commentEmojiInlineKey(placeholder: String): String? = webEmojiFiles[placeholder]?.let { "emoji_$it" }

@Composable
actual fun rememberCommentEmojis(): List<CommentEmoji> = emojis

private fun emojiResource(key: String) = Res.allDrawableResources[key.removePrefix("emoji_").removeSuffix(".png")]

@Composable
actual fun rememberCommentEmojiInlineContent(emojiKeys: Set<String>): Map<String, InlineTextContent> = remember(emojiKeys) {
    emojiKeys
        .mapNotNull { key ->
            emojiResource(key)?.let { resource ->
                key to InlineTextContent(Placeholder(1.3.em, 1.3.em, PlaceholderVerticalAlign.TextCenter)) {
                    Image(imageResource(resource), contentDescription = it)
                }
            }
        }.toMap()
}

@Composable
actual fun rememberCommentEmojiBitmaps(inlineKeys: Set<String>): Map<String, ImageBitmap> = buildMap {
    for (inlineKey in inlineKeys) {
        val resource = emojiResource(inlineKey) ?: continue
        put(inlineKey, key(inlineKey) { imageResource(resource) })
    }
}
