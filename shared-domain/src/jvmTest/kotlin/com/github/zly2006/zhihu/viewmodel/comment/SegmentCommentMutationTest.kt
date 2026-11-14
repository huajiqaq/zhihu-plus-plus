package com.github.zly2006.zhihu.viewmodel.comment

import androidx.lifecycle.viewModelScope
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.data.installZhihuCommonClientConfig
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.CommentHolder
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.SegmentCommentHolder
import com.github.zly2006.zhihu.viewmodel.ZhihuApiEnvironment
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SegmentCommentMutationTest {
    @Test
    fun rootSubmissionAndDeletionRestoreTheOriginalSegmentCount() = withFixture {
        val target = segment("segment-a")
        val model = RootCommentViewModel(target)
        assertTrue(submit(model, target))
        assertEquals(1, delta())
        assertEquals(1, snapshot.effectiveDelta(listOf("segment-a"), ownComments = true))
        assertNull(delete(model, model.allData.single()))
        assertEquals(0, delta(), "Deleting the newly submitted comment must undo its segment increment")
        assertEquals(0, snapshot.effectiveDelta(listOf("segment-a"), ownComments = true))
        assertTrue(model.allData.isEmpty())
        assertEquals(listOf(HttpMethod.Post, HttpMethod.Delete), requests)
    }

    @Test
    fun replySubmissionAndDeletionUpdateItsParentSegment() = withFixture {
        val target = CommentHolder("root-comment", segment("segment-a"))
        val model = ChildCommentViewModel(target)
        assertTrue(submit(model, target))
        assertEquals(1, delta(), "A child reply must update the segment owning its root comment")
        assertNull(delete(model, model.allData.single()))
        assertEquals(0, delta())
    }

    @Test
    fun deletingAServerLoadedRootCommentKeepsANegativeDelta() = withFixture {
        val model = RootCommentViewModel(segment("segment-a"))
        model.allData.add(responseComment)
        assertNull(delete(model, responseComment, ownComment = false))
        assertEquals(-1, delta(), "A loaded comment is already counted in the server metadata")
        assertEquals(0, snapshot.effectiveDelta(listOf("segment-a"), ownComments = true), "删除他人评论不能扣除本人的评论")
    }

    @Test
    fun deletingAServerLoadedReplyUsesTheChildHolderSegment() = withFixture {
        val model = ChildCommentViewModel(CommentHolder("root-comment", segment("segment-a")))
        model.allData.add(responseComment)
        assertNull(delete(model, responseComment))
        assertEquals(-1, delta())
    }

    @Test
    fun deletingAnEmbeddedReplyUpdatesTheSegmentOnce() = withFixture {
        val model = RootCommentViewModel(segment("segment-a"))
        val parent = responseComment.copy(id = "parent", childCommentCount = 1, childComments = listOf(responseComment))
        model.allData.add(parent)
        assertNull(delete(model, responseComment))
        assertEquals(-1, delta())
        assertEquals(0, model.allData.single().childCommentCount)
        assertTrue(
            model.allData
                .single()
                .childComments
                .isEmpty(),
        )
    }

    @Test
    fun articleAndAnswerWithSameIdDoNotShareDeltas() = withFixture {
        val target = SegmentCommentHolder("9001", "article", "segment-a", "划线内容", "paragraph", 0, 4)
        val articleSnapshot = SegmentCommentEvents.capture(client, "article", "9001")
        val model = RootCommentViewModel(target)

        assertTrue(submit(model, target))
        assertEquals(1, articleSnapshot.effectiveDelta(listOf("segment-a")))
        assertEquals(0, snapshot.effectiveDelta(listOf("segment-a")))
    }

    @Test
    fun oneMultiIdSegmentMutationIsCountedOnce() = withFixture {
        val target = segment("segment-a,segment-b")
        val model = RootCommentViewModel(target)
        assertTrue(submit(model, target))
        assertEquals(1, delta())
        assertEquals(1, snapshot.effectiveDelta(listOf("segment-a")))
        assertEquals(1, snapshot.effectiveDelta(listOf("segment-a", " segment-a ", "segment-b")))
        assertEquals(0, snapshot.effectiveDelta(listOf("segment-b")))
        assertNull(delete(model, model.allData.single()))
        assertEquals(0, delta())
    }

    @Test
    fun snapshotMapTracksOnlyChangesAfterItsWatermark() = withFixture {
        val target = segment("segment-a,segment-b")
        val model = RootCommentViewModel(target)
        val first = submit(model, target)
        assertTrue(first)
        val earlierMap = snapshot.asMap()
        val earlierOwnMap = snapshot.asMap(ownComments = true)
        val refreshed = SegmentCommentEvents.capture(client, "answers", "9001")
        assertTrue(submit(model, target))

        assertEquals(1, earlierMap["segment-a"], "已取出的 map 不随新事件改变")
        assertEquals(1, earlierOwnMap["segment-a"])
        assertEquals(2, snapshot.effectiveDelta(listOf("segment-a", "segment-b"), ownComments = true))
        assertEquals(1, refreshed.effectiveDelta(listOf("segment-a", "segment-b"), ownComments = true))
        assertEquals(2, snapshot.effectiveDelta(listOf("segment-a", "segment-b")))
        assertEquals(1, refreshed.asMap()["segment-a"])
        assertEquals(1, refreshed.effectiveDelta(listOf("segment-a", "segment-b")))
        assertNull(refreshed.asMap()["segment-b"])
        val submitted = model.allData.first()
        assertNull(delete(model, submitted))
        assertNull(delete(model, submitted))
        assertEquals(-1, refreshed.asMap()["segment-a"])
        assertEquals(-1, refreshed.effectiveDelta(listOf("segment-a", "segment-b")))
    }

    @Test
    fun failedRootAndReplyMutationsDoNotChangeSegmentCounts() = withFixture {
        status = HttpStatusCode.BadRequest
        for (target in listOf<NavDestination>(segment("segment-a"), CommentHolder("root-comment", segment("segment-a")))) {
            val model: BaseCommentViewModel = if (target is CommentHolder) ChildCommentViewModel(target) else RootCommentViewModel(target)
            assertEquals(false, submit(model, target))
            assertTrue(model.errorMessage.orEmpty().contains("400"))
            assertTrue(delete(model, responseComment).orEmpty().contains("400"))
            assertEquals(0, delta())
        }
    }

    @Test
    fun successfulResponseThatCannotBeDecodedDoesNotEmitIncrement() = withFixture {
        invalidPostBody = true
        val target = segment("segment-a")
        val model = RootCommentViewModel(target)

        assertEquals(false, submit(model, target))
        assertTrue(model.errorMessage.orEmpty().contains("异常"))
        assertEquals(0, delta())
        assertTrue(model.allData.isEmpty())
        val childTarget = CommentHolder("root-comment", target)
        val child = ChildCommentViewModel(childTarget)
        assertEquals(false, submit(child, childTarget))
        assertTrue(child.errorMessage.orEmpty().contains("异常"))
        assertEquals(0, delta())
        assertTrue(child.allData.isEmpty())
    }

    @Test
    fun completedDeletionUsesTheCapturedClientAfterAccountSwitch() = withFixture {
        val alternate = HttpClient(MockEngine { error("账户切换后不应由新客户端发送旧请求") })
        val oldSnapshot = SegmentCommentEvents.capture(client, "answer", "9001")
        val newSnapshot = SegmentCommentEvents.capture(alternate, "answer", "9001")
        val boundRequestStarted = CompletableDeferred<Unit>()
        val releaseBoundRequest = CompletableDeferred<Unit>()
        var currentClient = client
        val switchingEnvironment = object : ZhihuApiEnvironment {
            override fun httpClient() = currentClient

            override fun authenticatedCookies() = mapOf("d_c0" to "test-dc0")

            override suspend fun handleFetchFailure(tag: String?, error: Exception) = Unit

            override suspend fun <T> withAuthenticatedClient(
                block: suspend (HttpClient, Map<String, String>) -> T,
            ): T {
                val requestClient = currentClient
                boundRequestStarted.complete(Unit)
                releaseBoundRequest.await()
                return block(requestClient, authenticatedCookies())
            }
        }
        try {
            val model = RootCommentViewModel(segment("segment-a"))
            val finished = CompletableDeferred<String?>()
            model.deleteComment(
                responseComment,
                switchingEnvironment,
                onSuccess = { finished.complete(null) },
                onFailure = { finished.complete(it) },
                ownComment = true,
            )
            boundRequestStarted.await()
            currentClient = alternate
            releaseBoundRequest.complete(Unit)
            assertNull(finished.await())
            assertEquals(-1, oldSnapshot.effectiveDelta(listOf("segment-a")))
            assertEquals(0, newSnapshot.effectiveDelta(listOf("segment-a")))
            assertEquals(listOf(HttpMethod.Delete), requests)
        } finally {
            releaseBoundRequest.complete(Unit)
            alternate.close()
        }
    }

    @Test
    fun completedSubmissionUsesTheCapturedClientAfterAccountSwitch() = withFixture {
        val alternate = HttpClient(MockEngine { error("账户切换后不应由新客户端发送旧请求") })
        val target = segment("segment-a")
        val oldSnapshot = SegmentCommentEvents.capture(client, "answer", "9001")
        val newSnapshot = SegmentCommentEvents.capture(alternate, "answer", "9001")
        val boundRequestStarted = CompletableDeferred<Unit>()
        val releaseBoundRequest = CompletableDeferred<Unit>()
        var currentClient = client
        val switchingEnvironment = object : ZhihuApiEnvironment {
            override fun httpClient() = currentClient

            override fun authenticatedCookies() = mapOf("d_c0" to "test-dc0")

            override suspend fun handleFetchFailure(tag: String?, error: Exception) = Unit

            override suspend fun <T> withAuthenticatedClient(
                block: suspend (HttpClient, Map<String, String>) -> T,
            ): T {
                val requestClient = currentClient
                boundRequestStarted.complete(Unit)
                releaseBoundRequest.await()
                return block(requestClient, authenticatedCookies())
            }
        }
        try {
            val model = RootCommentViewModel(target)
            val finished = CompletableDeferred<Unit>()
            model.submitComment(
                target,
                "测试评论",
                switchingEnvironment,
                onFinished = { finished.complete(Unit) },
                onSuccess = {},
            )
            boundRequestStarted.await()
            currentClient = alternate
            releaseBoundRequest.complete(Unit)
            finished.await()
            assertNull(model.errorMessage)
            assertEquals(1, oldSnapshot.effectiveDelta(listOf("segment-a")))
            assertEquals(0, newSnapshot.effectiveDelta(listOf("segment-a")))
            assertEquals(listOf(HttpMethod.Post), requests)
        } finally {
            releaseBoundRequest.complete(Unit)
            alternate.close()
        }
    }

    @Test
    fun cancellingSubmissionPropagatesCancellationWithoutEvent() = withFixture {
        val started = CompletableDeferred<Unit>()
        postStarted = started
        postGate = CompletableDeferred()
        val target = segment("segment-a")
        val model = RootCommentViewModel(target)
        var succeeded = false
        var finished = false
        model.submitComment(
            target,
            "测试评论",
            environment,
            onSuccess = { succeeded = true },
            onFinished = { finished = true },
        )
        started.await()
        val requestJob = model.viewModelScope.coroutineContext[Job]!!
            .children
            .single()
        requestJob.cancelAndJoin()

        assertTrue(requestJob.isCancelled)
        assertTrue(finished)
        assertEquals(false, succeeded)
        assertNull(model.errorMessage)
        assertEquals(0, delta())
    }

    @Test
    fun deletingACancelledRequestPropagatesCancellationWithoutEvent() = withFixture {
        val started = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        deleteStarted = started
        deleteGate = gate
        val model = RootCommentViewModel(segment("segment-a"))
        var calledBack = false
        model.deleteComment(
            responseComment,
            environment,
            onSuccess = { calledBack = true },
            onFailure = { calledBack = true },
            ownComment = true,
        )
        started.await()
        val requestJob = model.viewModelScope.coroutineContext[Job]!!
            .children
            .single()
        requestJob.cancelAndJoin()

        assertTrue(requestJob.isCancelled)
        assertEquals(false, calledBack, "协程取消不能转成删除失败回调")
        assertEquals(0, delta())
    }

    @Test
    fun unauthorizedDeletionDoesNotSendOrChangeAnything() = withFixture {
        val model = RootCommentViewModel(segment("segment-a"))
        assertTrue(delete(model, responseComment.copy(canDelete = false)).orEmpty().contains("无权"))
        assertTrue(requests.isEmpty())
        assertEquals(0, delta())
    }

    @Test
    fun ordinaryArticleAndReplyCommentsDoNotEmitSegmentDeltas() = withFixture {
        val article = Article(type = ArticleType.Answer, id = 9001)
        for (target in listOf<NavDestination>(article, CommentHolder("root-comment", article))) {
            val model: BaseCommentViewModel = if (target is CommentHolder) ChildCommentViewModel(target) else RootCommentViewModel(target)
            assertTrue(submit(model, target))
            assertEquals(1, model.allData.size)
            assertNull(delete(model, model.allData.single()))
            assertEquals(0, delta())
        }
    }

    @Test
    fun emptyRootAndReplyPayloadsFinishWithoutSendingOrClearingDraft() = withFixture {
        val segment = segment("segment-a")
        for (target in listOf<NavDestination>(segment, CommentHolder("root-comment", segment))) {
            val model: BaseCommentViewModel = if (target is CommentHolder) ChildCommentViewModel(target) else RootCommentViewModel(target)
            var completed = 0
            var succeeded = false
            model.submitComment(
                target,
                " ",
                environment,
                onFinished = { completed++ },
                onSuccess = { succeeded = true },
            )
            assertEquals(1, completed)
            assertEquals(false, succeeded)
            assertTrue(model.allData.isEmpty())
            assertNull(model.errorMessage)
        }
        assertTrue(requests.isEmpty())
        assertEquals(0, delta())
    }

    private fun segment(id: String) = SegmentCommentHolder("9001", "answer", id, "划线内容", "paragraph", 0, 4)

    private fun withFixture(test: suspend Fixture.() -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture()
        try {
            fixture.test()
        } finally {
            fixture.client.close()
            Dispatchers.resetMain()
        }
    }

    private class Fixture {
        var status = HttpStatusCode.OK
        var invalidPostBody = false
        var postStarted: CompletableDeferred<Unit>? = null
        var postGate: CompletableDeferred<Unit>? = null
        var deleteStarted: CompletableDeferred<Unit>? = null
        var deleteGate: CompletableDeferred<Unit>? = null
        val requests = mutableListOf<HttpMethod>()
        val responseComment = DataHolder.Comment(
            id = "comment-1",
            type = "comment",
            resourceType = "answer",
            url = "https://example.invalid/comment-1",
            content = "<p>本地测试评论</p>",
            createdTime = 1,
            isDelete = false,
            collapsed = false,
            reviewing = false,
            canDelete = true,
            author = DataHolder.Comment.Author(
                id = "author",
                urlToken = "author",
                name = "测试作者",
                avatarUrl = "",
                avatarUrlTemplate = "",
                isOrg = false,
                type = "people",
                url = "https://example.invalid/author",
                userType = "people",
                headline = "",
                gender = 0,
                isAdvertiser = false,
            ),
        )
        val client = HttpClient(
            MockEngine { request ->
                requests += request.method
                if (request.method == HttpMethod.Post) {
                    postStarted?.complete(Unit)
                    postGate?.await()
                }
                if (request.method == HttpMethod.Delete) {
                    deleteStarted?.complete(Unit)
                    deleteGate?.await()
                }
                val body = if (invalidPostBody && request.method == HttpMethod.Post) "{}" else ZhihuJson.json.encodeToString(responseComment)
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        ) { installZhihuCommonClientConfig(mutableMapOf("d_c0" to "test-dc0"), "segment-count-test") }
        val environment = object : ZhihuApiEnvironment {
            override fun httpClient() = client

            override fun authenticatedCookies() = mapOf("d_c0" to "test-dc0")

            override suspend fun handleFetchFailure(tag: String?, error: Exception) = Unit
        }

        suspend fun submit(model: BaseCommentViewModel, target: NavDestination): Boolean {
            val finished = CompletableDeferred<Unit>()
            var accepted = false
            model.submitComment(
                target,
                "测试评论",
                environment,
                onFinished = { finished.complete(Unit) },
                onSuccess = { accepted = true },
            )
            finished.await()
            return accepted
        }

        suspend fun delete(model: BaseCommentViewModel, comment: DataHolder.Comment, ownComment: Boolean = true): String? {
            val finished = CompletableDeferred<String?>()
            model.deleteComment(
                comment,
                environment,
                onSuccess = { finished.complete(null) },
                onFailure = { finished.complete(it) },
                ownComment = ownComment,
            )
            return finished.await()
        }

        val snapshot = SegmentCommentEvents.capture(client, "answer", "9001")

        fun delta(): Int = snapshot.effectiveDelta(listOf("segment-a", "segment-b"))
    }
}
