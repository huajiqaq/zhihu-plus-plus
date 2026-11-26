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

package com.github.zly2006.zhihu

import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.core.view.descendants
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.navigation.WriteArticle
import com.github.zly2006.zhihu.test.resetAppPreferences
import com.github.zly2006.zhihu.test.setScreenContent
import com.github.zly2006.zhihu.ui.WRITE_ARTICLE_CONTENT_TAG
import com.github.zly2006.zhihu.ui.WRITE_ARTICLE_PREVIEW_TAG
import com.github.zly2006.zhihu.ui.WRITE_ARTICLE_TITLE_TAG
import com.github.zly2006.zhihu.ui.WriteArticleScreen
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class WriteAnswerScreenInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        composeRule.resetAppPreferences()
    }

    /** 真实图文输入器也必须交给外部页面滚动，而不是在下方单独滚动。 */
    @Test
    fun articleQuillAndMarkdownSharePageScroll() {
        composeRule.setScreenContent { WriteArticleScreen(WriteArticle) }
        val title = composeRule.onNodeWithTag(WRITE_ARTICLE_TITLE_TAG)
        val markdownMode = hasText("Markdown") and hasClickAction() and isEnabled()
        val richMode = hasText("图文") and hasClickAction() and isEnabled()
        val page = composeRule.onNodeWithTag("write_article_page")
        val preview = composeRule.onNodeWithTag(WRITE_ARTICLE_PREVIEW_TAG)

        fun top() = page.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, -100_000f) }

        composeRule.waitUntil(timeoutMillis = 10_000) { composeRule.onAllNodes(markdownMode).fetchSemanticsNodes().size == 1 }
        title.performTextInput("整页编辑测试")
        val mode = composeRule.onNode(markdownMode).assertIsDisplayed()
        assertTrue(mode.fetchSemanticsNode().boundsInRoot.top > title.fetchSemanticsNode().boundsInRoot.bottom)
        mode.performClick()
        val content = (1..60).joinToString("\n\n") { "图文与 Markdown 正文 $it" }
        val markdown = composeRule.onNode(hasTestTag(WRITE_ARTICLE_CONTENT_TAG) and hasSetTextAction())
        markdown.performTextInput(content)
        top()
        preview.assertIsNotDisplayed()
        composeRule.onNode(richMode).assertIsDisplayed().performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) { composeRule.onAllNodes(markdownMode).fetchSemanticsNodes().size == 1 }
        val webView = richEditorView()

        fun quillText() = webView.evaluate("quill.getText()").jsonPrimitive.content
        assertTrue(quillText().contains("图文与 Markdown 正文 60"))
        title.assertIsDisplayed()
        composeRule.onNodeWithTag(WRITE_ARTICLE_CONTENT_TAG).assertIsDisplayed().performTouchInput {
            swipeUp(startY = height * 0.75f, endY = top, durationMillis = 500)
        }
        title.assertIsNotDisplayed()
        preview.performScrollTo().assertIsDisplayed()
        top()
        title.performTouchInput { click() }
        assertTrue(quillText().contains("图文与 Markdown 正文 60"))
        composeRule.onNode(markdownMode).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasTestTag(WRITE_ARTICLE_CONTENT_TAG) and hasSetTextAction()).fetchSemanticsNodes().size == 1
        }
        markdown.assertTextEquals(content)
    }

    private fun richEditorView() = composeRule.runOnIdle {
        (composeRule.activity.window.decorView as ViewGroup).descendants.filterIsInstance<WebView>().single()
    }

    private fun WebView.evaluate(script: String): JsonElement {
        val result = ArrayBlockingQueue<String>(1)
        composeRule.runOnUiThread { evaluateJavascript(script) { result.offer(it) } }
        return ZhihuJson.json.parseToJsonElement(checkNotNull(result.poll(3, TimeUnit.SECONDS)))
    }
}
