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
import android.view.WindowInsets
import android.webkit.WebView
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.core.view.descendants
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.navigation.WriteAnswer
import com.github.zly2006.zhihu.navigation.WriteArticle
import com.github.zly2006.zhihu.test.resetAppPreferences
import com.github.zly2006.zhihu.test.setScreenContent
import com.github.zly2006.zhihu.ui.WRITE_ANSWER_CONTENT_TAG
import com.github.zly2006.zhihu.ui.WRITE_ANSWER_FAB_PREVIEW_TAG
import com.github.zly2006.zhihu.ui.WRITE_ANSWER_FAB_SAVE_TAG
import com.github.zly2006.zhihu.ui.WRITE_ARTICLE_CONTENT_TAG
import com.github.zly2006.zhihu.ui.WRITE_ARTICLE_PREVIEW_TAG
import com.github.zly2006.zhihu.ui.WRITE_ARTICLE_TITLE_TAG
import com.github.zly2006.zhihu.ui.WRITE_PIN_CONTENT_TAG
import com.github.zly2006.zhihu.ui.WriteAnswerScreen
import com.github.zly2006.zhihu.ui.WriteArticleScreen
import com.github.zly2006.zhihu.ui.WritePinScreen
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
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

    /** 正文很长时，题头和操作区仍与正文一起滚动，末尾操作可达。 */
    @Test
    fun editorHeaderAndActionsScrollWithTheBody() {
        composeRule.setScreenContent {
            WriteAnswerScreen(
                WriteAnswer(
                    questionId = 648,
                    questionTitle = "离线编辑器滚动测试",
                ),
            )
        }

        val editor = switchToMarkdownEditor()
        val content = (1..80).joinToString("\n") { "回答正文第 $it 行" }
        editor.performTextInput(content)
        editor.assertTextEquals(content)

        val page = composeRule.onNodeWithTag("write_answer_page")
        val title = composeRule.onNodeWithText("离线编辑器滚动测试")
        val preview = composeRule.onNodeWithTag(WRITE_ANSWER_FAB_PREVIEW_TAG)
        page.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, -100_000f) }
        title.assertIsDisplayed()
        preview.assertIsNotDisplayed()

        page.performTouchInput { swipeUp(durationMillis = 600) }
        title.assertIsNotDisplayed()
        preview.performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(WRITE_ANSWER_FAB_SAVE_TAG).assertIsDisplayed()
        page.performTouchInput { swipeDown(durationMillis = 600) }
        preview.assertIsNotDisplayed()
        page.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, -100_000f) }
        title.assertIsDisplayed()
        editor.assertTextEquals(content)
    }

    /**
     * 回答预览须正确渲染带引用定义的 Markdown；行为依据：https://github.com/zly2006/zhihu-plus-plus/issues/271
     */
    @Test
    fun previewRendersMarkdownWithReferenceDefinitions() {
        composeRule.setScreenContent {
            WriteAnswerScreen(
                WriteAnswer(
                    questionId = 648,
                    questionTitle = "回答预览重复状态键测试",
                ),
            )
        }

        val editor = switchToMarkdownEditor()
        val content = buildString {
            appendLine("[回答预览正文][docs]")
            appendLine()
            repeat(12) { index ->
                appendLine("[引用 $index][ref-$index]")
                appendLine("[ref-$index]: https://example.com/preview/$index")
                appendLine("${'$'}${'$'}x_$index${'$'}${'$'} 同行尾随正文 $index")
                appendLine()
            }
            appendLine("[docs]: https://example.com/preview")
        }
        editor.performTextInput(content)
        editor.assertTextEquals(content)
        composeRule
            .onNodeWithTag(WRITE_ANSWER_FAB_PREVIEW_TAG)
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        composeRule
            .onNode(hasText("Markdown") and hasAnyAncestor(hasTestTag("write_content_preview_header")))
            .assertIsDisplayed()
        composeRule.onAllNodesWithText("回答预览正文", substring = true).assertCountEquals(2)
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

    @Test
    fun shortRichDocumentCanSelectAHeadingAndReturnToBody() {
        composeRule.setScreenContent { WriteArticleScreen(WriteArticle) }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodes(hasText("Markdown") and hasClickAction() and isEnabled()).fetchSemanticsNodes().size == 1
        }
        val view = richEditorView()
        val editor = composeRule.onNodeWithTag(WRITE_ARTICLE_CONTENT_TAG)
        view.evaluate("editorLoad('<p>短正文</p>')")

        fun tap(selector: String) {
            val point = view
                .evaluate(
                    """(() => {const e = document.querySelector("$selector"), r = e.getBoundingClientRect();
                   const x = r.left + 12, y = r.top + 12;
                   return e.contains(document.elementFromPoint(x, y)) ? [x * devicePixelRatio, y * devicePixelRatio] : [];})()""",
                ).jsonArray
            assertEquals("$selector 应在下拉可见区内", 2, point.size)
            editor.performTouchInput { click(Offset(point[0].jsonPrimitive.float, point[1].jsonPrimitive.float)) }
        }
        for ((item, tag) in listOf("[data-value='2']" to "H2", ":not([data-value])" to "P")) {
            tap(".ql-header .ql-picker-label")
            composeRule.waitUntil(timeoutMillis = 3_000) {
                view.evaluate("document.querySelector('.ql-header .ql-picker-label').getAttribute('aria-expanded')").jsonPrimitive.content == "true"
            }
            tap(".ql-header .ql-picker-item$item")
            composeRule.waitUntil(timeoutMillis = 3_000) { view.evaluate("quill.root.firstElementChild.tagName").jsonPrimitive.content == tag }
            assertEquals("短正文", view.evaluate("quill.getText().trim()").jsonPrimitive.content)
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = 30)
    fun markdownShortcutsHideOnlyWhileTheSoftKeyboardIsVisible() {
        composeRule.setScreenContent { WritePinScreen() }
        val editor = composeRule.onNodeWithTag(WRITE_PIN_CONTENT_TAG)
        val shortcuts = composeRule.onNodeWithTag("markdown_shortcut_toolbar")
        val view = composeRule.activity.window.decorView
        val keyboard = WindowInsets.Type.ime()
        for (draft in listOf("保留正文", "以及再次输入")) {
            shortcuts.assertIsDisplayed()
            editor.performTouchInput { click() }
            composeRule.waitUntil("等待真实输入法打开", timeoutMillis = 5_000) {
                composeRule.runOnUiThread { view.rootWindowInsets?.isVisible(keyboard) == true }
            }
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithTag("markdown_shortcut_toolbar").fetchSemanticsNodes().isEmpty()
            }
            editor.performTextInput(draft)
            composeRule.onNodeWithTag("write_pin_action_toolbar").assertIsDisplayed()
            Espresso.closeSoftKeyboard()
            composeRule.waitUntil("等待输入法收起", timeoutMillis = 5_000) {
                composeRule.runOnUiThread { view.rootWindowInsets?.isVisible(keyboard) == false }
            }
            shortcuts.assertIsDisplayed()
        }
        editor.assertTextEquals("保留正文以及再次输入")
    }

    private fun richEditorView() = composeRule.runOnIdle {
        (composeRule.activity.window.decorView as ViewGroup).descendants.filterIsInstance<WebView>().single()
    }

    private fun WebView.evaluate(script: String): JsonElement {
        val result = ArrayBlockingQueue<String>(1)
        composeRule.runOnUiThread { evaluateJavascript(script) { result.offer(it) } }
        return ZhihuJson.json.parseToJsonElement(checkNotNull(result.poll(3, TimeUnit.SECONDS)))
    }

    private fun switchToMarkdownEditor(): SemanticsNodeInteraction {
        val markdownMode = hasText("Markdown") and hasClickAction() and isEnabled()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodes(markdownMode).fetchSemanticsNodes().size == 1
        }
        composeRule.onNode(markdownMode).assertIsDisplayed().performClick()

        val editorMatcher = hasTestTag(WRITE_ANSWER_CONTENT_TAG) and
            hasSetTextAction() and
            SemanticsMatcher.keyIsDefined(SemanticsActions.RequestFocus)
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodes(editorMatcher).fetchSemanticsNodes().size == 1
        }
        return composeRule.onNode(editorMatcher).assertIsDisplayed()
    }
}
