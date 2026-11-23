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

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.buildAnnotatedString
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class CommentEmojiResourcesTest {
    @Test
    fun packagedEmojisRenderInTextAndDecodeForTheEditor() = runSkikoComposeUiTest {
        var emojis = emptyList<CommentEmoji>()
        var bitmaps = emptyMap<String, ImageBitmap>()
        setContent {
            emojis = rememberCommentEmojis()
            bitmaps = rememberCommentEmojiBitmaps(emojis.map { it.inlineKey }.toSet())
            val surprise = emojis.single { it.placeholder == "[惊喜]" }
            BasicText(
                text = buildAnnotatedString { appendInlineContent(surprise.inlineKey, surprise.placeholder) },
                inlineContent = rememberCommentEmojiInlineContent(setOf(surprise.inlineKey)),
            )
        }
        runOnIdle {
            assertEquals(emojis.map { it.inlineKey }.toSet(), bitmaps.keys)
            assertEquals(emojis.single { it.placeholder == "[惊喜]" }.inlineKey, commentEmojiInlineKey("[惊喜]"))
        }
        onNodeWithContentDescription(emojis.single { it.placeholder == "[惊喜]" }.inlineKey).assertIsDisplayed()
    }
}
