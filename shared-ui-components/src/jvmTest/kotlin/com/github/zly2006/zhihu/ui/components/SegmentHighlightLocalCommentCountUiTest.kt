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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.github.zly2006.zhihu.account.LocalZhihuAccountStore
import com.github.zly2006.zhihu.account.ZhihuAccountRepository
import com.github.zly2006.zhihu.account.ZhihuAccountSession
import com.github.zly2006.zhihu.account.ZhihuAccountStore
import com.github.zly2006.zhihu.account.ZhihuAccounts
import com.github.zly2006.zhihu.account.ZhihuSavedAccount
import com.github.zly2006.zhihu.data.SegmentInfoMeta
import com.github.zly2006.zhihu.markdown.markdownToMdAst
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.Navigator
import com.github.zly2006.zhihu.navigation.SegmentCommentHolder
import com.github.zly2006.zhihu.util.SegmentHighlightSpan
import com.github.zly2006.zhihu.viewmodel.comment.SegmentCommentEvents
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalTestApi::class)
class SegmentHighlightLocalCommentCountUiTest {
    @BeforeTest
    fun resetEvents() {
        requests.set(0)
        unlikeStatus = null
        setUpIsolatedAccount()
    }

    @AfterTest
    fun closeFixture() = tearDownIsolatedAccount()

    @Test
    fun locallySubmittedCommentOpensCommentsWithoutDeletingTheHighlight() = runDesktopComposeUiTest {
        val span = highlight(commentCount = 0, liked = false)
        val document = markdownToMdAst("段评测试")
        val commentSnapshot = SegmentCommentEvents.capture(checkNotNull(fixtureStore).client.httpClient(), "answer", "9001")
        val comments = mutableListOf<SegmentCommentHolder>()
        val shownSheets = mutableListOf<SegmentActionSheetState>()
        val removed = mutableStateMapOf<String, Boolean>()
        var sheet: SegmentActionSheetState? = null
        var refreshes = 0
        var decoration: SegmentDecorationState? = null

        setContent {
            CompositionLocalProvider(
                LocalZhihuAccountStore provides checkNotNull(fixtureStore),
                LocalNavigator provides Navigator({ error("不应触发导航：$it") }, { error("不应触发返回") }),
                LocalSegmentCommentHost provides { comments += it },
                LocalSegmentActionSheetHost provides {
                    sheet = it
                    if (it != null) shownSheets += it
                },
                LocalSegmentRemovedHighlights provides removed,
                LocalOnRequestContentRefresh provides { refreshes++ },
            ) {
                decoration = resolveSegmentDecorationState(
                    meta = span.meta,
                    removed = removed[span.threadKey()] == true,
                    localCommentDelta = commentSnapshot.effectiveDelta(span.meta.segIds),
                    localOwnCommentDelta = commentSnapshot.effectiveDelta(span.meta.segIds, ownComments = true),
                )
                SegmentHighlightInteractionHost(document = document, commentSnapshot = commentSnapshot) {
                    val onHighlightClick = checkNotNull(LocalOnSegmentHighlightClick.current)
                    BasicText("划线", Modifier.testTag("highlight").clickable { onHighlightClick(span) })
                }
            }
        }
        waitForIdle()
        runOnIdle { SegmentCommentEvents.notifySubmitted(checkNotNull(fixtureStore).client.httpClient(), expectedTarget) }
        waitForIdle()
        onNodeWithTag("highlight").performClick()
        waitForIdle()

        runOnIdle { sheet?.onDismiss?.invoke() }
        waitForIdle()
        runOnIdle {
            assertEquals(listOf(expectedTarget), comments, "本地提交后应直接打开该段的评论")
            assertTrue(shownSheets.isEmpty(), "本地提交后不能弹出零评论操作面板")
            assertTrue(removed.isEmpty(), "本地已有评论的划线不能被清理")
            assertEquals(SegmentDecorationState(visible = true, mine = true, hasComments = true), decoration, "发表评论应立即产生本人实线和评论标记")
            assertEquals(0, refreshes, "不能触发误删除后的正文刷新")
            assertEquals(0, requests.get(), "点击和关闭不应发出请求")
        }
    }

    @Test
    fun deletingLastServerCommentOpensActionSheetWithoutDeletingLikedHighlight() = runDesktopComposeUiTest {
        val span = highlight(commentCount = 1, liked = true)
        val document = markdownToMdAst("段评测试")
        val commentSnapshot = SegmentCommentEvents.capture(checkNotNull(fixtureStore).client.httpClient(), "answer", "9001")
        val comments = mutableListOf<SegmentCommentHolder>()
        val removed = mutableStateMapOf<String, Boolean>()
        val metaStates = mutableStateMapOf(span.threadKey() to span.meta)
        var sheet: SegmentActionSheetState? = null
        var refreshes = 0
        var decoration: SegmentDecorationState? = null

        setContent {
            CompositionLocalProvider(
                LocalZhihuAccountStore provides checkNotNull(fixtureStore),
                LocalNavigator provides Navigator({ error("不应触发导航：$it") }, { error("不应触发返回") }),
                LocalSegmentCommentHost provides { comments += it },
                LocalSegmentActionSheetHost provides { sheet = it },
                LocalSegmentRemovedHighlights provides removed,
                LocalOnRequestContentRefresh provides { refreshes++ },
            ) {
                decoration = resolveSegmentDecorationState(
                    meta = metaStates[span.threadKey()] ?: span.meta,
                    removed = removed[span.threadKey()] == true,
                    localCommentDelta = commentSnapshot.effectiveDelta(span.meta.segIds),
                    localOwnCommentDelta = commentSnapshot.effectiveDelta(span.meta.segIds, ownComments = true),
                )
                SegmentHighlightInteractionHost(document = document, metaStates = metaStates, commentSnapshot = commentSnapshot) {
                    val onHighlightClick = checkNotNull(LocalOnSegmentHighlightClick.current)
                    BasicText("划线", Modifier.testTag("highlight").clickable { onHighlightClick(span) })
                }
            }
        }
        waitForIdle()
        onNodeWithTag("highlight").performClick()
        waitForIdle()
        runOnIdle {
            assertEquals(listOf(expectedTarget), comments, "删除前服务端的一条评论应能打开")
            comments.clear()
            SegmentCommentEvents.notifyChanged(checkNotNull(fixtureStore).client.httpClient(), expectedTarget, -1, ownComment = true)
        }
        waitForIdle()
        onNodeWithTag("highlight").performClick()
        waitForIdle()
        runOnIdle {
            assertTrue(comments.isEmpty(), "删除最后一条评论后不能再次直接进入评论")
            val opened = assertNotNull(sheet, "删除最后一条评论后应打开操作面板")
            assertEquals(0, opened.highlight.meta.commentCount, "面板必须显示叠加本地增量后的计数")
            assertEquals(0, opened.highlight.meta.myCommentCount)
            assertEquals(SegmentDecorationState(visible = true, mine = true, hasComments = false), decoration)
            opened.onDismiss()
        }
        waitForIdle()
        runOnIdle {
            assertNull(sheet)
            assertTrue(removed.isEmpty(), "关闭面板不能删除仍被喜欢的划线")
            assertEquals(0, refreshes)
            assertEquals(0, requests.get(), "关闭面板不应取消喜欢或发出其他请求")
        }
        onNodeWithTag("highlight").performClick()
        waitForIdle()
        runOnIdle {
            unlikeStatus = HttpStatusCode.ServiceUnavailable
            assertNotNull(sheet).onLikeClick()
        }
        waitUntil(timeoutMillis = 5_000) { requests.get() == 1 }
        waitForIdle()
        runOnIdle {
            assertTrue(removed.isEmpty(), "取消喜欢失败不能隐藏划线")
            assertEquals(0, refreshes)
            assertTrue(metaStates.getValue(span.threadKey()).isLike)
            unlikeStatus = HttpStatusCode.NoContent
            assertNotNull(sheet).onLikeClick()
        }
        waitUntil(timeoutMillis = 5_000) { refreshes == 1 }
        runOnIdle {
            assertEquals(2, requests.get())
            assertEquals(true, removed[span.threadKey()])
            assertEquals(false, metaStates.getValue(span.threadKey()).isLike)
            assertEquals(SegmentDecorationState(visible = false, mine = false, hasComments = false), decoration)
        }
    }

    private fun highlight(commentCount: Int, liked: Boolean) = SegmentHighlightSpan(
        text = "划线内容",
        meta = SegmentInfoMeta(
            segIds = listOf("segment-count-a", "segment-count-b"),
            isLike = liked,
            likeCount = if (liked) 1 else 0,
            commentCount = commentCount,
            myCommentCount = commentCount,
        ),
        contentId = "9001",
        contentType = "answer",
        paragraphId = "paragraph-1",
        startOffset = 2,
        endOffset = 6,
    )

    companion object {
        private val requests = AtomicInteger()
        private var unlikeStatus: HttpStatusCode? = null
        private val expectedTarget = SegmentCommentHolder(
            contentId = "9001",
            contentType = "answer",
            segmentId = "segment-count-a,segment-count-b",
            segmentContent = "划线内容",
            paragraphId = "paragraph-1",
            startOffset = 2,
            endOffset = 6,
        )
        private var home: Path? = null
        private var fixtureStore: ZhihuAccountStore? = null

        fun setUpIsolatedAccount() {
            try {
                home = Files.createTempDirectory("zhihu-segment-count-ui-")
                val repository = ZhihuAccountRepository(kotlinx.io.files.Path(home!!.resolve("account.json").toString()))
                repository.saveAccounts(
                    ZhihuAccounts(
                        "fixture",
                        listOf(
                            ZhihuSavedAccount(
                                "fixture",
                                ZhihuAccountSession(login = true, username = "segment-count-fixture", cookies = mutableMapOf("d_c0" to "synthetic-device")),
                            ),
                        ),
                    ),
                )
                val rejectingEngine = MockEngine { request ->
                    requests.incrementAndGet()
                    val status = unlikeStatus
                    if (status != null && request.method == HttpMethod.Delete && request.url.encodedPath == "/api/v4/reaction/answers/9001/segment_reaction") {
                        respond("", status)
                    } else {
                        error("不应发起网络请求：${request.method} ${request.url}")
                    }
                }
                val store = ZhihuAccountStore(repository, rejectingEngine)
                fixtureStore = store
                check(store.session.username == "segment-count-fixture")
                check(store.client.httpClient().engine === rejectingEngine)
            } catch (error: Throwable) {
                tearDownIsolatedAccount()
                throw error
            }
        }

        fun tearDownIsolatedAccount() {
            if (home == null) return
            try {
                fixtureStore?.close()
            } finally {
                fixtureStore = null
                home?.toFile()?.deleteRecursively()
                home = null
            }
        }
    }
}
