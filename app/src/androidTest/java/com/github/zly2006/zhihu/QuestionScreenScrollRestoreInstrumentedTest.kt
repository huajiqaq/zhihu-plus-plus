package com.github.zly2006.zhihu

import android.content.pm.ActivityInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.zly2006.zhihu.data.CommonFeed
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.Feed
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.test.ZhihuMockApi
import com.github.zly2006.zhihu.test.resetAppPreferences
import com.github.zly2006.zhihu.test.setZhihuMainContent
import io.ktor.http.HttpMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * Regression: https://github.com/zly2006/zhihu-plus-plus/issues/740
 * Fixed by: 本地提交（待审阅，未开 PR）
 *
 * 目标：从回答返回问题页后，第一条回答的像素位置必须保持不变，并且不重复请求问题详情。
 * 覆盖三种姿态：第一个回答贴顶、头部留缝、超长详情展开。
 *
 * 夹具路由必须在 resetAppPreferences() 之后注册，因为 reseed() 会调用 ZhihuMockApi.reset()。
 */
@RunWith(AndroidJUnit4::class)
class QuestionScreenScrollRestoreInstrumentedTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun firstAnswerAtTopKeepsOffsetAfterAnswerRoundTrip() = runRoundTrip(sliverPx = 0f)

    @Test
    fun headerSliverKeepsOffsetAfterAnswerRoundTrip() = runRoundTrip(sliverPx = 40f)

    @Test
    fun expandedLongDetailKeepsOffsetAfterAnswerRoundTrip() = runRoundTrip(sliverPx = 40f, expandDetail = true)

    private fun runRoundTrip(sliverPx: Float, expandDetail: Boolean = false) {
        rule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        rule.resetAppPreferences()
        ZhihuMockApi.install(enabled = true)
        ZhihuMockApi.reset()
        ZhihuMockApi.mockJsonPrefix(
            HttpMethod.Get,
            "https://www.zhihu.com/api/v4/questions/$QUESTION_ID?",
            questionDetailJson(),
        )
        ZhihuMockApi.mockJsonPrefix(
            HttpMethod.Get,
            "https://www.zhihu.com/api/v4/questions/$QUESTION_ID/feeds",
            feedsJson(),
        )
        // 回答详情复用仓库里的真实响应夹具，字段齐全，避免严格解码器报 MissingFieldException。
        ZhihuMockApi.mockJsonPrefix(
            HttpMethod.Get,
            "https://www.zhihu.com/api/v4/answers/",
            assetText("answer-detail.json"),
        )
        rule.setZhihuMainContent()
        rule.runOnIdle { rule.activity.navigate(Question(QUESTION_ID, "滚动位置回归问题")) }
        rule.waitUntil(10_000) {
            rule.onAllNodesWithTag("question_screen_list").fetchSemanticsNodes().isNotEmpty()
        }
        rule.waitUntil(20_000) { scrollToIndex(1) }
        assertTrue("fixture answers must load", firstAnswerOffset() != null)

        if (expandDetail) {
            assertTrue(
                "the long detail must offer an expand control",
                rule.onAllNodesWithTag("question_detail_toggle").fetchSemanticsNodes().isNotEmpty(),
            )
            rule.onNodeWithTag("question_detail_toggle").performClick()
            // 展开后控件文字变为“收起详情”，据此确认展开已生效并等待动画结束。
            rule.waitUntil(10_000) {
                rule.onAllNodesWithText("收起详情").fetchSemanticsNodes().isNotEmpty()
            }
            rule.waitForIdle()
            rule.waitUntil(5_000) { scrollToIndex(1) }
        }

        if (sliverPx > 0f) {
            rule.onNodeWithTag("question_screen_list").performSemanticsAction(SemanticsActions.ScrollBy) {
                it(0f, -sliverPx)
            }
            rule.waitForIdle()
        }
        val before = firstAnswerOffset()
        assertTrue("the first answer must be visible before opening it", before != null)

        rule.onNodeWithText(MARKER_ONE, substring = true).performClick()
        rule.waitUntil(10_000) {
            rule.onAllNodesWithTag("question_screen_list").fetchSemanticsNodes().isEmpty()
        }
        rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitUntil(10_000) {
            rule.onAllNodesWithTag("question_screen_list").fetchSemanticsNodes().isNotEmpty()
        }
        rule.waitForIdle()

        val after = firstAnswerOffset()
        assertTrue("the first answer must stay visible; before=$before after=$after", after != null)
        assertTrue(
            "the first answer must keep its pixel offset; before=$before after=$after",
            abs(after!! - before!!) < 8f,
        )
        assertEquals(
            "returning to the question page must not re-request the question detail",
            1,
            detailRequests(),
        )
    }

    private fun scrollToIndex(index: Int): Boolean =
        runCatching { rule.onNodeWithTag("question_screen_list").performScrollToIndex(index) }.isSuccess

    private fun listTop(): Float =
        rule
            .onNodeWithTag("question_screen_list")
            .fetchSemanticsNode()
            .boundsInRoot.top

    private fun firstAnswerOffset(): Float? =
        rule
            .onAllNodesWithText(MARKER_ONE, substring = true)
            .fetchSemanticsNodes()
            .minOfOrNull { it.boundsInRoot.top }
            ?.minus(listTop())

    private fun detailRequests(): Int = ZhihuMockApi
        .recordedRequests()
        .count { it.url.startsWith("https://www.zhihu.com/api/v4/questions/$QUESTION_ID?") }

    private fun assetText(name: String): String =
        InstrumentationRegistry
            .getInstrumentation()
            .context.assets
            .open(name)
            .bufferedReader()
            .readText()

    private fun questionDetailJson(): String = ZhihuJson.json.encodeToString(
        DataHolder.Question(
            type = "question",
            id = QUESTION_ID,
            title = "滚动位置回归问题",
            questionType = "normal",
            created = 1600000000,
            updatedTime = 1600000000,
            url = "https://www.zhihu.com/api/v4/questions/$QUESTION_ID",
            answerCount = 1,
            visitCount = 12345,
            commentCount = 6,
            followerCount = 789,
            detail = "<p>${DETAIL_SENTENCE.repeat(120)}</p>",
            relationship = DataHolder.QuestionRelationship(),
            topics = emptyList(),
            author = DataHolder.Author(
                avatarUrl = "https://pic.example/avatar.jpg",
                gender = -1,
                headline = "probe",
                id = "probe-author",
                isAdvertiser = false,
                isOrg = false,
                name = "ProbeAuthor",
                type = "people",
                url = "https://www.zhihu.com/people/probe-author",
                urlToken = "probe-author",
                userType = "people",
            ),
            voteupCount = 0,
        ),
    )

    private fun feedsJson(): String {
        val body = ZhihuJson.json.encodeToString<List<Feed>>(
            listOf(
                CommonFeed(
                    id = "probe-feed",
                    verb = "normal",
                    target = Feed.AnswerTarget(
                        id = ANSWER_ID,
                        url = "https://www.zhihu.com/api/v4/answers/$ANSWER_ID",
                        question = Feed.QuestionTarget(
                            id = QUESTION_ID,
                            url = "https://www.zhihu.com/api/v4/questions/$QUESTION_ID",
                            type = "question",
                        ),
                        excerpt = "$MARKER_ONE ${ANSWER_BODY.repeat(3)}",
                        content = "<p>$MARKER_ONE ${ANSWER_BODY.repeat(3)}</p>",
                        voteupCount = 100,
                        commentCount = 10,
                    ),
                ),
            ),
        )
        return """{"data":$body,"paging":{"is_end":true,"is_start":true,"totals":1,"next":""}}"""
    }

    private companion object {
        const val QUESTION_ID = 991000001L
        const val ANSWER_ID = 991000100L
        const val MARKER_ONE = "第一回答摘要标记"
        const val DETAIL_SENTENCE =
            "这是一段用于撑开问题详情区域的问题描述文本，用来让问题页头部达到真实阅读时的布局高度。"
        const val ANSWER_BODY =
            "这是一段用于撑开回答卡片高度的回答摘要正文内容，保证卡片具备真实高度，从而让列表可以滚动并观察返回后的可见位置。"
    }
}
