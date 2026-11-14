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
