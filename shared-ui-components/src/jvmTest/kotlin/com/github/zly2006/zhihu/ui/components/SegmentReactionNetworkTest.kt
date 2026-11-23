package com.github.zly2006.zhihu.ui.components

import com.github.zly2006.zhihu.data.SegmentInfoMeta
import com.github.zly2006.zhihu.data.installZhihuCommonClientConfig
import com.github.zly2006.zhihu.util.SegmentHighlightSpan
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.statement.HttpResponsePipeline
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SegmentReactionNetworkTest {
    @Test
    fun createAcceptsOnlyDocumentedSuccessStatusesAndReturnsEachRange() = runTest {
        val second = range.copy(paragraphId = "paragraph-2", text = "另一段选区")
        withFixture(
            responses = listOf(
                Reply(HttpStatusCode.OK, """{"seg_id":"segment-1"}"""),
                Reply(HttpStatusCode.Created, """{"payload":{"segId":"segment-2"}}"""),
            ),
        ) { fixture ->
            assertEquals(
                listOf(range to "segment-1"),
                createSegmentReactions(fixture.environment, target, listOf(range)),
            )
            assertEquals(
                listOf(second to "segment-2"),
                createSegmentReactions(fixture.environment, target, listOf(second)),
            )
            assertEquals(2, fixture.requests.size)
            fixture.requests.forEach { request ->
                assertEquals(HttpMethod.Post, request.method)
                assertEquals("www.zhihu.com", request.url.host)
                assertEquals("/api/v4/reaction/answers/9001/segment_reaction", request.url.encodedPath)
                assertEquals("fetch", request.headers["x-requested-with"])
                assertTrue(request.headers["x-zse-96"].orEmpty().startsWith("2.0_"))
            }
            val body = Json.parseToJsonElement((fixture.requests.first().body as TextContent).text).jsonObject
            assertEquals(range.text, body.getValue("content").jsonPrimitive.content)
            val position = body.getValue("position").jsonObject
            assertEquals(
                range.paragraphId,
                position
                    .getValue("start")
                    .jsonObject
                    .getValue("paragraph_id")
                    .jsonPrimitive.content,
            )
            assertEquals(
                range.startOffset.toString(),
                position
                    .getValue("start")
                    .jsonObject
                    .getValue("offset")
                    .jsonPrimitive.content,
            )
            assertEquals(
                range.endOffset.toString(),
                position
                    .getValue("end")
                    .jsonObject
                    .getValue("offset")
                    .jsonPrimitive.content,
            )
        }
    }

    @Test
    fun createRejectsUndocumentedNoContentStatus() = runTest {
        withFixture(responses = listOf(Reply(HttpStatusCode.NoContent, """{"seg_id":"segment-1"}"""))) { fixture ->
            val error = assertFailsWith<Exception> { createSegmentReactions(fixture.environment, target, listOf(range)) }
            assertTrue(error.message.orEmpty().contains("204"))
        }
    }

    @Test
    fun createRejectsUnauthorizedResponse() = runTest { assertCreateHttpFailure(HttpStatusCode.Unauthorized) }

    @Test
    fun createRejectsServerError() = runTest { assertCreateHttpFailure(HttpStatusCode.InternalServerError) }

    @Test
    fun createRejectsMissingEmptyOrNullSegmentId() = runTest {
        for (body in listOf("{}", """{"seg_id":""}""", """{"seg_id":" , "}""", """{"seg_id":null}""")) {
            withFixture(responses = listOf(Reply(HttpStatusCode.OK, body))) { fixture ->
                val error = assertFailsWith<Exception>("响应体 $body 不能被视为已创建的片段") {
                    createSegmentReactions(fixture.environment, target, listOf(range))
                }
                assertTrue(error.message.orEmpty().contains("seg_id"))
            }
        }
    }

    @Test
    fun createRequiresFiveUtf16UnitsButExistingSpanningReactionsUseOnlyTheirIds() = runTest {
        withFixture { fixture ->
            val error = assertFailsWith<SegmentReactionException> {
                createSegmentReactions(fixture.environment, target, listOf(range.copy(text = "四个文字")))
            }
            assertEquals("请至少选择5个字后再喜欢或评论", error.message)
            assertTrue(fixture.requests.isEmpty())
        }
        val spanning = unliked.copy(meta = unliked.meta.copy(isSpan = true), text = "跨段引文")
        assertEquals(
            """{"seg_id":"${spanning.meta.segIds.joinToString(",")}"}""",
            buildSegmentLikeBody(spanning),
        )
        withFixture(responses = listOf(Reply(HttpStatusCode.Created, """{"seg_id":"five"}"""))) { fixture ->
            val fiveUnits = range.copy(text = "😀三个字")
            assertEquals(5, fiveUnits.text.length)
            assertEquals(listOf(fiveUnits to "five"), createSegmentReactions(fixture.environment, target, listOf(fiveUnits)))
        }
    }

    @Test
    fun createRejectsMultipleRangesBeforeSendingARequest() = runTest {
        val ranges = listOf(range, range.copy(paragraphId = "paragraph-2"))
        withFixture { fixture ->
            assertFailsWith<Exception> { createSegmentReactions(fixture.environment, target, ranges) }
            assertTrue(fixture.requests.isEmpty(), "跨段落选区不能只创建部分句评")
        }
    }

    @Test
    fun createWithoutLoginFailsWithoutSendingARequest() = runTest {
        withFixture(cookies = emptyMap()) { fixture ->
            val error = assertFailsWith<Exception> { createSegmentReactions(fixture.environment, target, listOf(range)) }
            assertTrue(error.message.orEmpty().contains("登录"))
            assertTrue(fixture.requests.isEmpty())
        }
    }

    @Test
    fun toggleWithoutLoginFailsWithoutSendingARequestOrChangingMeta() = runTest {
        withFixture(cookies = emptyMap()) { fixture ->
            for (highlight in listOf(liked, unliked)) {
                var visibleMeta = highlight.meta
                val error = assertFailsWith<Exception> { visibleMeta = toggleSegmentLike(fixture.environment, highlight) }
                assertTrue(error.message.orEmpty().contains("登录"))
                assertEquals(highlight.meta, visibleMeta)
            }
            assertTrue(fixture.requests.isEmpty())
        }
    }

    @Test
    fun toggleWithMissingContentTargetFailsWithoutSendingARequest() = runTest {
        withFixture { fixture ->
            for (highlight in listOf(liked.copy(contentId = null), liked.copy(contentType = null))) {
                val error = assertFailsWith<Exception> { toggleSegmentLike(fixture.environment, highlight) }
                assertTrue(error.message.orEmpty().contains("content"))
            }
            assertTrue(fixture.requests.isEmpty())
        }
    }

    @Test
    fun unlikeSuccessUpdatesMetaAndSendsSignedDelete() = runTest {
        for (status in listOf(HttpStatusCode.OK, HttpStatusCode.NoContent)) {
            for (count in listOf(3, 0)) {
                withFixture(responses = listOf(Reply(status, ""))) { fixture ->
                    val highlight = liked.copy(meta = liked.meta.copy(likeCount = count))
                    assertEquals(
                        highlight.meta.copy(isLike = false, likeCount = (count - 1).coerceAtLeast(0)),
                        toggleSegmentLike(fixture.environment, highlight),
                    )
                    val request = fixture.requests.single()
                    assertEquals(HttpMethod.Delete, request.method)
                    assertEquals("/api/v4/reaction/answers/9001/segment_reaction", request.url.encodedPath)
                    assertEquals("fetch", request.headers["x-requested-with"])
                    assertTrue(request.headers["x-zse-96"].orEmpty().startsWith("2.0_"))
                    val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                    assertEquals("segment-1", body.getValue("seg_ids").jsonPrimitive.content)
                }
            }
        }
    }

    @Test
    fun unlikeRejectsUnauthorizedResponseWithoutChangingMeta() = runTest {
        assertToggleHttpFailure(liked, HttpStatusCode.Unauthorized)
    }

    @Test
    fun unlikeRejectsServerErrorWithoutChangingMeta() = runTest {
        assertToggleHttpFailure(liked, HttpStatusCode.InternalServerError)
    }

    @Test
    fun likeSuccessUpdatesMeta() = runTest {
        withFixture(responses = listOf(Reply(HttpStatusCode.OK, """{"payload":{"segId":"segment-2,segment-3"}}"""))) { fixture ->
            assertEquals(
                unliked.meta.copy(segIds = listOf("segment-2", "segment-3"), isLike = true, likeCount = 4),
                toggleSegmentLike(fixture.environment, unliked),
            )
            val request = fixture.requests.single()
            assertEquals(HttpMethod.Post, request.method)
            val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            assertEquals(unliked.meta.segIds.joinToString(","), body.getValue("seg_id").jsonPrimitive.content)
            assertEquals(unliked.text, body.getValue("content").jsonPrimitive.content)
            val position = body.getValue("position").jsonObject
            for ((end, offset) in listOf("start" to unliked.startOffset, "end" to unliked.endOffset)) {
                val location = position.getValue(end).jsonObject
                assertEquals(unliked.paragraphId, location.getValue("paragraph_id").jsonPrimitive.content)
                assertEquals(offset.toString(), location.getValue("offset").jsonPrimitive.content)
            }
        }
        withFixture(responses = listOf(Reply(HttpStatusCode.NoContent, ""))) { fixture ->
            assertEquals(unliked.meta.copy(isLike = true, likeCount = 4), toggleSegmentLike(fixture.environment, unliked))
        }
    }

    @Test
    fun likeRejectsUnauthorizedResponseWithoutChangingMeta() = runTest {
        assertToggleHttpFailure(unliked, HttpStatusCode.Unauthorized)
    }

    @Test
    fun likeRejectsServerErrorWithoutChangingMeta() = runTest {
        assertToggleHttpFailure(unliked, HttpStatusCode.InternalServerError)
    }

    @Test
    fun finalRedirectsDoNotChangeLikeState() = runTest {
        for (status in listOf(HttpStatusCode.Found, HttpStatusCode.TemporaryRedirect, HttpStatusCode.NotModified)) {
            for (highlight in listOf(liked, unliked)) {
                assertToggleHttpFailure(highlight, status)
            }
        }
    }

    @Test
    fun networkFailurePreservesItsTypeForBothOperations() = runTest {
        for (operation in 0..2) {
            withFixture(requestFailure = IOException("simulated network failure")) { fixture ->
                val error = assertFailsWith<Exception> {
                    when (operation) {
                        0 -> createSegmentReactions(fixture.environment, target, listOf(range))
                        1 -> toggleSegmentLike(fixture.environment, liked)
                        else -> toggleSegmentLike(fixture.environment, unliked)
                    }
                }
                assertTrue(generateSequence<Throwable>(error) { it.cause }.any { it is IOException })
                assertFalse(error.message.orEmpty().contains(testCookie))
                assertEquals(1, fixture.requests.size)
            }
        }
    }

    @Test
    fun cancellationPropagatesFromAllThreeRequestPaths() = runTest {
        for (operation in 0..2) {
            withFixture(requestFailure = CancellationException("cancelled request")) { fixture ->
                assertFailsWith<CancellationException> {
                    when (operation) {
                        0 -> createSegmentReactions(fixture.environment, target, listOf(range))
                        1 -> toggleSegmentLike(fixture.environment, liked)
                        else -> toggleSegmentLike(fixture.environment, unliked)
                    }
                }
                assertEquals(1, fixture.requests.size)
            }
        }
    }

    @Test
    fun cancellationDuringCreatedResponseDecodingIsNotSwallowed() = runTest {
        withFixture(responses = listOf(Reply(HttpStatusCode.OK, """{"seg_id":"segment-1"}"""))) { fixture ->
            var reachedBodyDecode = false
            fixture.client.responsePipeline.intercept(HttpResponsePipeline.Receive) {
                reachedBodyDecode = true
                throw CancellationException("cancelled while decoding response")
            }
            assertFailsWith<CancellationException> { createSegmentReactions(fixture.environment, target, listOf(range)) }
            assertTrue(reachedBodyDecode)
            assertEquals(1, fixture.requests.size)
        }
    }

    private suspend fun assertCreateHttpFailure(status: HttpStatusCode) {
        withFixture(responses = listOf(Reply(status, "{}"))) { fixture ->
            val error = assertFailsWith<Exception> { createSegmentReactions(fixture.environment, target, listOf(range)) }
            assertTrue(error.message.orEmpty().contains(status.value.toString()))
            assertFalse(error.message.orEmpty().contains(testCookie))
            assertEquals(1, fixture.requests.size)
        }
    }

    private suspend fun assertToggleHttpFailure(highlight: SegmentHighlightSpan, status: HttpStatusCode) {
        withFixture(responses = listOf(Reply(status, "{}"))) { fixture ->
            var visibleMeta = highlight.meta
            val error = assertFailsWith<Exception> { visibleMeta = toggleSegmentLike(fixture.environment, highlight) }
            assertTrue(error.message.orEmpty().contains(status.value.toString()))
            assertFalse(error.message.orEmpty().contains(testCookie))
            assertEquals(highlight.meta, visibleMeta)
            assertEquals(1, fixture.requests.size)
        }
    }

    private suspend fun withFixture(
        responses: List<Reply> = listOf(Reply(HttpStatusCode.OK, "{}")),
        cookies: Map<String, String> = mapOf("d_c0" to testCookie),
        requestFailure: Exception? = null,
        block: suspend (Fixture) -> Unit,
    ) {
        val fixture = Fixture(responses, cookies, requestFailure)
        try {
            block(fixture)
        } finally {
            fixture.client.close()
        }
    }

    private class Fixture(
        responses: List<Reply>,
        cookies: Map<String, String>,
        requestFailure: Exception?,
    ) {
        val requests = mutableListOf<HttpRequestData>()
        val client = HttpClient(
            MockEngine { request ->
                requests += request
                if (requestFailure != null) throw requestFailure
                val reply = responses[requests.lastIndex]
                respond(reply.body, reply.status, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        ) {
            installZhihuCommonClientConfig(cookies.toMutableMap(), "segment-test")
        }
        val environment = object : PaginationEnvironment {
            override fun httpClient() = client

            override fun authenticatedCookies() = cookies

            override suspend fun handleFetchFailure(tag: String?, error: Exception) = Unit
        }
    }

    private data class Reply(
        val status: HttpStatusCode,
        val body: String,
    )

    private companion object {
        const val testCookie = "synthetic-test-cookie"
        val target = SegmentSelectionTarget(contentId = "9001", contentType = "answer")
        val range = SegmentCreationRange(paragraphId = "paragraph-1", startOffset = 2, endOffset = 7, text = "本地选区文字")
        val liked = SegmentHighlightSpan(
            text = range.text,
            meta = SegmentInfoMeta(segIds = listOf("segment-1"), isLike = true, likeCount = 3),
            contentId = target.contentId,
            contentType = target.contentType,
            paragraphId = range.paragraphId,
            startOffset = range.startOffset,
            endOffset = range.endOffset,
        )
        val unliked = liked.copy(meta = liked.meta.copy(isLike = false))
    }
}
