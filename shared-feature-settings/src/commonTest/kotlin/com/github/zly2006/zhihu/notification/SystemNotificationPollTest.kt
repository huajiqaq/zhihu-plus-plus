package com.github.zly2006.zhihu.notification

import com.github.zly2006.zhihu.data.MobileNotificationContent
import com.github.zly2006.zhihu.data.MobileNotificationTarget
import com.github.zly2006.zhihu.data.MobileNotificationTimelineItem
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.data.ZhihuPaging
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.CommentHolder
import com.github.zly2006.zhihu.navigation.Question
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SystemNotificationPollTest {
    @Test
    fun initialBaselineReadsThroughTheLastPageWithoutDeliveringOldNotifications() = runTest {
        val visited = mutableListOf<String>()
        val secondUrl = systemNotificationPageUrl("comment") + "&offset=20"
        val result = pollSystemNotifications(SystemNotificationCheckpoint(), setOf(NotificationType.REPLY_COMMENT)) { url ->
            visited += url
            if (visited.size == 1) page(listOf(reply("old-2")), secondUrl) else page(listOf(reply("old-1")))
        }
        assertEquals(listOf(systemNotificationPageUrl("comment"), secondUrl), visited)
        assertTrue(result.events.isEmpty())
        assertEquals(setOf("old-1", "old-2"), result.checkpoint.seenByEntry["comment"])
        assertEquals(setOf(NotificationType.REPLY_COMMENT), result.checkpoint.baselinedTypes)
    }

    @Test
    fun catchesUpMultiplePagesToKnownBoundaryAndNeverRepeatsAnEvent() = runTest {
        val previous = checkpoint("comment", setOf("old"), setOf(NotificationType.REPLY_COMMENT))
        val secondUrl = systemNotificationPageUrl("comment") + "&offset=20"
        var calls = 0
        val result = pollSystemNotifications(previous, setOf(NotificationType.REPLY_COMMENT)) {
            if (++calls == 1) page(listOf(reply("new-2"), reply("new-2")), secondUrl) else page(listOf(reply("new-1"), reply("old")), "must-not-be-requested")
        }
        assertEquals(2, calls)
        assertEquals(listOf("comment:new-2", "comment:new-1"), result.events.map { it.key })
        assertEquals(CommentHolder("81", Article(type = ArticleType.Answer, id = 42)), result.events.first().destination)
        val repeated = pollSystemNotifications(result.checkpoint, setOf(NotificationType.REPLY_COMMENT)) {
            page(listOf(reply("new-2"), reply("new-1")), "must-not-be-requested")
        }
        assertTrue(repeated.events.isEmpty())
    }

    @Test
    fun pageFailureDoesNotCommitPartialProgressAndRetryFindsAllNewEvents() = runTest {
        val previous = checkpoint("comment", setOf("old"), setOf(NotificationType.REPLY_COMMENT))
        val secondUrl = systemNotificationPageUrl("comment") + "&offset=20"
        var calls = 0
        assertFailsWith<IllegalStateException> {
            pollSystemNotifications(previous, setOf(NotificationType.REPLY_COMMENT)) {
                if (++calls == 1) page(listOf(reply("new-2")), secondUrl) else error("synthetic failed page")
            }
        }
        assertEquals(setOf("old"), previous.seenByEntry["comment"])
        val retry = pollSystemNotifications(previous, setOf(NotificationType.REPLY_COMMENT)) {
            page(listOf(reply("new-2"), reply("new-1"), reply("old")))
        }
        assertEquals(setOf("comment:new-1", "comment:new-2"), retry.events.map { it.key }.toSet())
    }

    @Test
    fun failedSecondEntryDoesNotCommitTheFirstEntryEither() = runTest {
        val previous = SystemNotificationCheckpoint(
            seenByEntry = mapOf("comment" to setOf("old-comment"), "like" to setOf("old-like")),
            baselinedTypes = setOf(NotificationType.REPLY_COMMENT, NotificationType.LIKE_ANSWER),
        )
        val enabled = linkedSetOf(NotificationType.REPLY_COMMENT, NotificationType.LIKE_ANSWER)
        assertFailsWith<IllegalStateException> {
            pollSystemNotifications(previous, enabled) { url ->
                if ("/comment?" in url) page(listOf(reply("new-comment"), reply("old-comment"))) else error("synthetic failed category")
            }
        }
        assertEquals(setOf("old-comment"), previous.seenByEntry["comment"])
    }

    @Test
    fun newTypeInSharedTimelineGetsItsOwnSilentBaselineAndOnlyEnabledTypesDeliver() = runTest {
        val previous = checkpoint("like", setOf("old"), setOf(NotificationType.LIKE_ANSWER))
        val answer = reply("answer").copy(content = MobileNotificationContent(subTitle = "喜欢了你的回答", targetLink = "zhihu://answers/42"))
        val comment = reply("comment").copy(content = reply("comment").content?.copy(subTitle = "喜欢了你的评论"))
        val enabled = setOf(NotificationType.LIKE_ANSWER, NotificationType.LIKE_COMMENT)
        var calls = 0
        val result = pollSystemNotifications(previous, enabled) {
            calls++
            page(listOf(answer, comment, reply("old")))
        }
        assertEquals(1, calls, "同一时间线不应为两个类型重复加载")
        assertEquals(listOf(NotificationType.LIKE_ANSWER), result.events.map { it.type })
        assertTrue(NotificationType.LIKE_COMMENT in result.checkpoint.baselinedTypes)
        val next = pollSystemNotifications(result.checkpoint, setOf(NotificationType.LIKE_COMMENT)) {
            page(listOf(answer.copy(id = "another-answer"), comment.copy(id = "another-comment"), comment))
        }
        assertEquals(listOf("like:another-comment"), next.events.map { it.key })
    }

    @Test
    fun emptyOptInDoesNotLoadAndUnsupportedPlatformsFailExplicitly() = runTest {
        val result = pollSystemNotifications(SystemNotificationCheckpoint(), emptySet()) { error("未订阅不得请求") }
        assertTrue(result.events.isEmpty())
        assertFalse(UnsupportedSystemNotifications.status.value.supported)
        assertTrue(
            UnsupportedSystemNotifications.status.value.enabledTypes
                .isEmpty(),
        )
        assertFailsWith<UnsupportedOperationException> { UnsupportedSystemNotifications.setTypeEnabled(NotificationType.REPLY_COMMENT, true) }
        assertFailsWith<UnsupportedOperationException> { UnsupportedSystemNotifications.openSystemSettings() }
    }

    @Test
    fun malformedPaginationAndCancellationCannotAdvanceTheCheckpoint() = runTest {
        val previous = checkpoint("comment", setOf("old"), setOf(NotificationType.REPLY_COMMENT))
        assertFailsWith<IllegalStateException> {
            pollSystemNotifications(previous, setOf(NotificationType.REPLY_COMMENT)) { page(listOf(reply("new")), systemNotificationPageUrl("comment")) }
        }
        assertFailsWith<CancellationException> {
            pollSystemNotifications(previous, setOf(NotificationType.REPLY_COMMENT)) { throw CancellationException("synthetic cancelled poll") }
        }
        assertEquals(setOf("old"), previous.seenByEntry["comment"])
        listOf(
            "https://example.invalid/notifications/v3/timeline/entry/comment",
            "https://api.zhihu.com/notifications/v3/timeline/entry/comment/actions/readall",
            "https://api.zhihu.com/notifications/v3/timeline/entry/like",
            "https://api.zhihu.com:8443/notifications/v3/timeline/entry/comment",
            "https://fixture:synthetic@api.zhihu.com/notifications/v3/timeline/entry/comment",
        ).forEach { url -> assertFailsWith<IllegalArgumentException> { systemNotificationPageUrl("comment", url) } }
        assertEquals(
            "https://api.zhihu.com/notifications/v3/timeline/entry/comment?offset=20",
            systemNotificationPageUrl("comment", "http://api.zhihu.com/notifications/v3/timeline/entry/comment?offset=20"),
        )
    }

    @Test
    fun unknownReadDeletedAndPrivateMessagesDoNotBecomeSystemNotifications() = runTest {
        val reply = reply("new")
        assertNull(systemNotificationEvent("comment", reply.copy(type = "message")))
        assertNull(systemNotificationEvent("comment", reply.copy(id = "", uniqueId = "")))
        assertNull(systemNotificationEvent("comment", reply.copy(content = reply.content?.copy(isDeleted = true))))
        assertNull(systemNotificationEvent("comment", reply.copy(content = reply.content?.copy(subTitle = "赞同了你的回答"))))
        assertNull(systemNotificationEvent("comment", reply.copy(content = reply.content?.copy(targetLink = "https://example.invalid/answer/42", subTargetLink = "zhihu://answers/42"))))
        assertNull(systemNotificationEvent("like", reply))
        val result = pollSystemNotifications(checkpoint("comment", setOf("old"), setOf(NotificationType.REPLY_COMMENT)), setOf(NotificationType.REPLY_COMMENT)) {
            page(listOf(reply.copy(isRead = true, unreadCount = 0), reply("old")))
        }
        assertTrue(result.events.isEmpty())
    }

    @Test
    fun invitationOpensTheQuestionAndPersistsNoBodyOrAuthor() {
        val item = MobileNotificationTimelineItem(
            id = "synthetic-invite",
            target = MobileNotificationTarget(id = "51", type = "question", title = "synthetic private title"),
            content = MobileNotificationContent(text = "synthetic private body"),
        )
        val event = systemNotificationEvent("invite", item)
        assertEquals(NotificationType.INVITE_ANSWER, event?.type)
        assertEquals(Question(51), event?.destination)
        assertNull(systemNotificationEvent("invite", item.copy(target = item.target?.copy(id = "0"))))
    }

    @Test
    fun productionLoaderUsesOnlyGetAndRejectsFailedOrMalformedPages() = runTest {
        val requests = mutableListOf<HttpMethod>()
        var response = """{"data":[{"id":"synthetic","is_read":false}],"paging":{"is_end":true,"next":""}}"""
        var status = HttpStatusCode.OK
        val client = HttpClient(
            MockEngine { request ->
                requests += request.method
                respond(response, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
            },
        ) {
            install(ContentNegotiation) { json(ZhihuJson.json) }
        }
        try {
            val page = loadSystemNotificationPage(client, systemNotificationPageUrl("comment"))
            assertTrue(page.data.single().isUnread)
            assertTrue(page.paging.isEnd)
            status = HttpStatusCode.ServiceUnavailable
            assertFailsWith<Exception> { loadSystemNotificationPage(client, systemNotificationPageUrl("comment")) }
            status = HttpStatusCode.OK
            response = """{"data":[]}"""
            assertFailsWith<Exception> { loadSystemNotificationPage(client, systemNotificationPageUrl("comment")) }
            assertEquals(listOf(HttpMethod.Get, HttpMethod.Get, HttpMethod.Get), requests)
        } finally {
            client.close()
        }
    }

    @Test
    fun deviceStateKeepsOptInBaselineAndOneUseAccountScopedReceiptsAcrossStorage() {
        val json = Json { ignoreUnknownKeys = true }
        val legacy = json.decodeFromString<DeviceNotificationState>("""{"accountId":"fixture-a","epoch":"first","enabledTypes":["REPLY_COMMENT"]}""")
        assertTrue(DeviceNotificationState().enabledTypes.isEmpty())
        assertTrue(legacy.checkpoint.baselinedTypes.isEmpty())
        val event = SystemNotificationEvent("comment:one", NotificationType.REPLY_COMMENT, CommentHolder("81", Article(type = ArticleType.Answer, id = 42)))
        val reserved = checkNotNull(legacy.reserve(event, "receipt-one"))
        val restored = json.decodeFromString<DeviceNotificationState>(json.encodeToString(reserved))
        assertEquals(reserved, restored)
        assertNull(restored.reserve(event, "receipt-two"))
        val click = SystemNotificationClick("fixture-a", event.key, "receipt-one")
        assertNull(restored.consumeClick(click, "fixture-b"))
        assertNull(restored.consumeClick(click.copy(accountId = "fixture-b"), "fixture-b"))
        assertNull(restored.consumeClick(click.copy(eventKey = "comment:forged"), "fixture-a"))
        val consumed = checkNotNull(restored.consumeClick(click, "fixture-a"))
        assertEquals(event, consumed.event)
        assertNull(consumed.state.consumeClick(click, "fixture-a"))
        assertEquals(reserved.reserved, consumed.state.reserved)
        val disabled = restored.withTypeEnabled(NotificationType.REPLY_COMMENT, false, "fixture-a", "second")
        assertEquals(DeviceNotificationState(), disabled.state)
        assertEquals(listOf(event), disabled.removed)
        val enabled = disabled.state.withTypeEnabled(NotificationType.REPLY_COMMENT, true, "fixture-a", "third").state
        assertTrue(enabled.checkpoint.baselinedTypes.isEmpty())
        assertNull(enabled.consumeClick(click, "fixture-a"))
    }

    @Test
    fun confirmedReadRemovesOnlyCapturedReceiptsAndKeepsNewOrOtherCategoryNotifications() {
        val answer = SystemNotificationEvent("like:answer", NotificationType.LIKE_ANSWER, Article(type = ArticleType.Answer, id = 42))
        val comment = SystemNotificationEvent("like:comment", NotificationType.LIKE_COMMENT, CommentHolder("81", answer.destination))
        val reply = SystemNotificationEvent("comment:reply", NotificationType.REPLY_COMMENT, comment.destination)
        val invitation = SystemNotificationEvent("invite:question", NotificationType.INVITE_ANSWER, Question(51))
        val before = DeviceNotificationState(
            accountId = "fixture-a",
            epoch = "subscription",
            enabledTypes = NotificationType.entries.toSet(),
            checkpoint = checkpoint("like", setOf("answer", "comment"), NotificationType.entries.toSet()),
            reserved = setOf(answer.key, comment.key),
            clicks = mapOf("old-answer" to answer, "old-comment" to comment, "reply" to reply, "invite" to invitation),
        )
        val snapshot = checkNotNull(before.readSnapshot("like"))
        assertNull(before.readSnapshot("follow"))
        val duringRequest = before.copy(clicks = before.clicks + ("new-answer" to answer))
        assertEquals(duringRequest, duringRequest.markRead(snapshot.copy(accountId = "fixture-b")).state)
        val confirmed = duringRequest.markRead(snapshot)
        assertEquals(setOf("new-answer", "reply", "invite"), confirmed.state.clicks.keys)
        assertEquals(listOf(comment), confirmed.removed, "同 key 的新回执不得随旧回执撤销系统通知")
        assertEquals(before.checkpoint, confirmed.state.checkpoint)
        assertEquals(before.reserved, confirmed.state.reserved, "未完成轮询的投递保留位不能因标读消失")
        assertTrue(
            confirmed.state
                .markRead(snapshot)
                .removed
                .isEmpty(),
        )
        assertNull(confirmed.state.consumeClick(SystemNotificationClick("fixture-a", comment.key, "old-comment"), "fixture-a"))
        assertEquals(answer, confirmed.state.consumeClick(SystemNotificationClick("fixture-a", answer.key, "new-answer"), "fixture-a")?.event)
    }

    private fun checkpoint(entry: String, ids: Set<String>, types: Set<NotificationType>) = SystemNotificationCheckpoint(
        seenByEntry = mapOf(entry to ids),
        baselinedTypes = types,
    )

    private fun page(items: List<MobileNotificationTimelineItem>, next: String = "") = SystemNotificationPage(
        items,
        ZhihuPaging(isEnd = next.isEmpty(), next = next),
    )

    private fun reply(id: String) = MobileNotificationTimelineItem(
        id = id,
        isRead = false,
        content = MobileNotificationContent(
            subTitle = "回复了你的评论",
            targetLink = "zhihu://comment/list/answer/42?anchor_comment_id=81",
            abstractText = "synthetic body not for notification storage",
        ),
    )
}
