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

package com.github.zly2006.zhihu.data

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.room.useReaderConnection
import coil3.compose.AsyncImagePainter
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import com.github.zly2006.zhihu.account.LocalZhihuAccountStore
import com.github.zly2006.zhihu.account.ZhihuAccountProfileSnapshot
import com.github.zly2006.zhihu.account.ZhihuAccountRepository
import com.github.zly2006.zhihu.account.ZhihuAccountSession
import com.github.zly2006.zhihu.account.ZhihuAccountStore
import com.github.zly2006.zhihu.platform.desktopSettingsStore
import com.github.zly2006.zhihu.theme.ThemeManager
import com.github.zly2006.zhihu.theme.ThemeMode
import com.github.zly2006.zhihu.theme.ThemeStyle
import com.github.zly2006.zhihu.theme.ZhihuTheme
import com.github.zly2006.zhihu.ui.ACCOUNT_SETTINGS_SHORTCUT_NOTIFICATION_TAG
import com.github.zly2006.zhihu.ui.ARTICLE_BACK_BUTTON_TAG
import com.github.zly2006.zhihu.ui.COMMENT_CLOSE_BUTTON_TAG
import com.github.zly2006.zhihu.ui.COMMENT_SCREEN_LIST_TAG
import com.github.zly2006.zhihu.ui.DesktopZhihuMain
import com.github.zly2006.zhihu.ui.subscreens.BOTTOM_BAR_ITEMS_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.subscreens.BOTTOM_BAR_ITEM_ORDER_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.subscreens.LANDSCAPE_LIST_DETAIL_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.subscreens.START_DESTINATION_PREFERENCE_KEY
import com.github.zly2006.zhihu.viewmodel.filter.ContentFilterDatabase
import com.github.zly2006.zhihu.viewmodel.filter.getContentFilterDatabase
import com.github.zly2006.zhihu.viewmodel.za.parseMobileHomeFeedDisplayItem
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.io.files.Path as KotlinxPath

/** 夹具来自 zhurl 的真实生产采集，仅对结构化个人身份字段脱敏；HTML 与私有原始响应逐字节一致。 */
class RealZhihuApiCorpusTest {
    @Test
    fun representativeProductionResponsesDecodeThroughTheirProductionParsers() {
        val answer = assertIs<Feed.AnswerTarget>(decodeTarget("answer-detail.json"))
        assertTrue(answer.content.startsWith("<p"))
        assertTrue(answer.author?.id?.startsWith("redacted-person-") == true)

        for (sample in fixtureJson("question-answer-thumbnails.json").jsonArray) {
            val questionAnswer = assertIs<Feed.AnswerTarget>(ZhihuJson.decodeJson<Feed.Target>(sample))
            val info = assertNotNull(questionAnswer.thumbnailInfo)
            val raw = sample.jsonObject.getValue("thumbnail_info").jsonObject
            assertEquals(raw.getValue("count").jsonPrimitive.int, info.count)
            assertEquals(
                raw.getValue("thumbnails").jsonArray.map {
                    it.jsonObject
                        .getValue("url")
                        .jsonPrimitive.content
                },
                info.thumbnails.filterIsInstance<DataHolder.ThumbnailRich>().map { it.url },
            )
        }

        val comment = ZhihuJson.decodeJson<DataHolder.Comment>(fixtureJson("comment.json"))
        assertEquals("comment", comment.type)
        assertTrue(comment.content.startsWith("<p"))
        assertTrue(comment.author.id.startsWith("redacted-person-"))

        val daily = ZhihuJson.decodeJson<DailyStoriesResponse>(fixtureJson("daily-stories.json"))
        assertEquals("20260816", daily.date)
        assertEquals(9791925L, daily.stories.first().id)
        assertEquals(
            "古代贝壳就是货币，能换百亩良田，为何穷人不去海边多捡点？",
            daily.stories.first().title,
        )
        assertEquals(
            "https://picx.zhimg.com/v2-748b033ca166b5607bf4acd2c748c7fa.jpg?source=8673f162",
            daily.stories
                .first()
                .images
                .single(),
        )

        val mobile = assertNotNull(parseMobileHomeFeedDisplayItem(fixtureJson("mobile-home-card.json").jsonObject))
        assertNotNull(mobile.navDestination)

        val moments = fixtureJson("moments-feed-items.json").jsonArray.map { ZhihuJson.decodeJson<Feed>(it) }
        val voteupAnswer = assertIs<CommonFeed>(moments[0])
        val voteupArticle = assertIs<CommonFeed>(moments[1])
        assertTrue(voteupAnswer.sourceLabel?.endsWith("赞同了回答") == true)
        assertTrue(voteupArticle.sourceLabel?.endsWith("赞同了文章") == true)
        assertNull(GroupFeed(brief = "", groupText = "代表性分组", list = listOf(voteupAnswer)).sourceLabel)

        val notification = ZhihuJson.decodeJson<MobileNotificationTimelineItem>(
            fixtureJson("notification-timeline-item.json"),
        )
        assertEquals("aggregate_notification", notification.type)
        assertTrue(notification.stableId.isNotBlank())

        val pin = assertIs<Feed.PinTarget>(decodeTarget("pin-target.json"))
        assertTrue(pin.content.isNotEmpty())
        assertTrue(pin.author.id.startsWith("redacted-person-"))
    }

    /** 生产内容过滤数据库由进程持有，必须在新的独立 JVM worker 中执行。 */
    @OptIn(ExperimentalTestApi::class, coil3.annotation.ExperimentalCoilApi::class)
    @Test
    fun desktopNotificationCommentLinkOpensItsArticleAndAnchorThenReturns() {
        val originalHome = System.getProperty("user.home")
        val originalTheme = ThemeManager.snapshot()
        val directory = Files.createTempDirectory("zhihu-desktop-comment-navigation-")
        val requests = CopyOnWriteArrayList<String>()
        val anchorId = "51001"
        val notificationId = "comment-navigation"
        val answer = ZhihuJson.decodeJson<DataHolder.Answer>(fixtureJson("answer-detail.json")).let { source ->
            source.copy(
                question = source.question.copy(title = "导航回归回答"),
                author = source.author.copy(avatarUrl = "", badgeV2 = null),
                content = "<p>导航回归回答正文</p>",
                segmentInfos = emptyList(),
                endorsements = emptyList(),
            )
        }
        val comment = ZhihuJson.decodeJson<DataHolder.Comment>(fixtureJson("comment.json")).let { source ->
            source.copy(
                id = anchorId,
                content = "<p>通知指定的锚定评论</p>",
                replyCommentId = null,
                replyRootCommentId = null,
                author = source.author.copy(avatarUrl = ""),
                childComments = emptyList(),
                childCommentCount = 0,
                canDelete = false,
            )
        }
        val profile = ZhihuAccountProfileSnapshot(
            id = "offline-navigation-user",
            name = "离线导航用户",
            urlToken = "offline-navigation-user",
            userType = "people",
            avatarUrl = "",
        )
        val session = ZhihuAccountSession(
            login = true,
            username = profile.name,
            cookies = mutableMapOf("d_c0" to "fixture-device", "z_c0" to "fixture-session"),
            profile = profile,
        )
        val notifications = MobileNotificationMessageOverview(
            data = listOf(
                MobileNotificationTimelineItem(
                    id = notificationId,
                    uniqueId = notificationId,
                    type = "message",
                    content = MobileNotificationContent(
                        title = "查看评论通知",
                        text = "打开回答和通知指定的评论",
                        targetLink = "zhihu://comment/list/answer/${answer.id}?anchor_comment_id=$anchorId&is_child=false",
                    ),
                ),
            ),
            paging = ZhihuPaging(isEnd = true, next = ""),
        )
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            requests += "${request.method.value} $path"
            val body = when (path) {
                "/api/v4/me" -> ZhihuJson.json.encodeToString(profile)
                "/notifications/v3/message/v3" -> ZhihuJson.json.encodeToString(notifications)
                "/api/v4/answers/${answer.id}" -> ZhihuJson.json.encodeToString(answer)
                "/api/v4/answers/${answer.id}/relationship" -> """{"text":""}"""
                "/api/v4/comment_v5/comment/$anchorId" -> ZhihuJson.json.encodeToString(comment)
                "/api/v4/comment_v5/answers/${answer.id}/root_comment" -> """{"data":[],"paging":{"is_end":true,"next":""}}"""
                "/api/4/stories/latest" -> """{"date":"20261030","stories":[]}"""
                else -> """{"data":[],"paging":{"is_end":true,"next":""}}"""
            }
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        var ownedStore: ZhihuAccountStore? = null
        var ownedDatabase: ContentFilterDatabase? = null
        val viewModels = object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
        val rootBackOwner = object : NavigationEventDispatcherOwner {
            override val navigationEventDispatcher = NavigationEventDispatcher()
        }
        val noImages = AsyncImagePreviewHandler { _, _ -> AsyncImagePainter.State.Empty }
        try {
            System.setProperty("user.home", directory.toString())
            val database = getContentFilterDatabase()
            val databaseFile = runBlocking {
                database.useReaderConnection { connection ->
                    connection.usePrepared("PRAGMA database_list") { statement ->
                        var mainFile: String? = null
                        while (statement.step()) {
                            if (statement.getText(1) == "main") mainFile = statement.getText(2)
                        }
                        checkNotNull(mainFile)
                    }
                }
            }
            check(
                java.io
                    .File(databaseFile)
                    .canonicalFile
                    .toPath()
                    .startsWith(directory.toRealPath()),
            ) {
                "生产数据库已被其他测试初始化；必须使用独立 JVM worker，禁止写入已有数据库"
            }
            ownedDatabase = database
            val accountFile = directory.resolve("fixture-account.json")
            Files.writeString(accountFile, ZhihuJson.json.encodeToString(session))
            val store = ZhihuAccountStore(ZhihuAccountRepository(KotlinxPath(accountFile.toString())), engine = engine)
            ownedStore = store
            assertEquals(profile.id, store.session.profile?.id, "未加载隔离导航账户")
            desktopSettingsStore().apply {
                putStringSet(BOTTOM_BAR_ITEMS_PREFERENCE_KEY, linkedSetOf("Account", "Home", "Daily"))
                putString(BOTTOM_BAR_ITEM_ORDER_PREFERENCE_KEY, "Account,Home,Daily")
                putString(START_DESTINATION_PREFERENCE_KEY, "Account")
                putBoolean(LANDSCAPE_LIST_DETAIL_PREFERENCE_KEY, false)
                putBoolean("webviewRender", false)
                putBoolean("tiqianMarkdown", false)
                putBoolean("enableContentFilter", false)
                putBoolean("allowTelemetry", false)
            }
            ThemeManager.load(originalTheme.copy(themeStyle = ThemeStyle.MATERIAL_EXPRESSIVE, themeMode = ThemeMode.LIGHT))
            runDesktopComposeUiTest(width = 412, height = 892) {
                setContent {
                    CompositionLocalProvider(
                        LocalViewModelStoreOwner provides viewModels,
                        LocalZhihuAccountStore provides store,
                        LocalNavigationEventDispatcherOwner provides rootBackOwner,
                        LocalInspectionMode provides true,
                        LocalAsyncImagePreviewHandler provides noImages,
                    ) {
                        ZhihuTheme { DesktopZhihuMain() }
                    }
                }
                waitUntil(timeoutMillis = 5_000) {
                    onAllNodesWithTag(ACCOUNT_SETTINGS_SHORTCUT_NOTIFICATION_TAG).fetchSemanticsNodes().isNotEmpty()
                }
                onNodeWithTag(ACCOUNT_SETTINGS_SHORTCUT_NOTIFICATION_TAG).performScrollTo()
                waitUntil(timeoutMillis = 5_000) {
                    onNodeWithTag(ACCOUNT_SETTINGS_SHORTCUT_NOTIFICATION_TAG).isDisplayed()
                }
                onNodeWithTag(ACCOUNT_SETTINGS_SHORTCUT_NOTIFICATION_TAG).assertIsDisplayed().performClick()
                waitUntil(timeoutMillis = 5_000) {
                    onNodeWithTag("notification_message_$notificationId").isDisplayed()
                }
                onNodeWithTag("notification_message_$notificationId").assertIsDisplayed().performClick()
                waitUntil(timeoutMillis = 5_000) {
                    onNode(hasTestTag("comment_row_$anchorId") and hasAnyAncestor(isDialog())).isDisplayed() &&
                        onNodeWithTag("article_full_title", useUnmergedTree = true).isDisplayed()
                }
                onNodeWithTag("article_full_title", useUnmergedTree = true).assertIsDisplayed()
                onNode(hasTestTag(COMMENT_SCREEN_LIST_TAG) and hasAnyAncestor(isDialog())).assertIsDisplayed()
                onNode(hasTestTag("comment_row_$anchorId") and hasAnyAncestor(isDialog())).assertIsDisplayed()
                assertTrue(requests.contains("GET /notifications/v3/message/v3"))
                assertTrue(requests.contains("GET /api/v4/answers/${answer.id}"))
                assertTrue(requests.contains("GET /api/v4/comment_v5/comment/$anchorId"))

                onNode(hasTestTag(COMMENT_CLOSE_BUTTON_TAG) and hasAnyAncestor(isDialog())).assertIsDisplayed().performClick()
                waitUntil(timeoutMillis = 5_000) {
                    onAllNodes(isDialog()).fetchSemanticsNodes().isEmpty() &&
                        onNodeWithText("导航回归回答正文", substring = true, useUnmergedTree = true).isDisplayed()
                }
                onNode(isDialog()).assertDoesNotExist()
                onNodeWithText("导航回归回答正文", substring = true, useUnmergedTree = true).assertIsDisplayed()
                onNodeWithTag(ARTICLE_BACK_BUTTON_TAG).assertIsDisplayed().performClick()
                waitUntil(timeoutMillis = 5_000) {
                    onNodeWithTag("notification_message_$notificationId").isDisplayed()
                }
                onNodeWithTag("notification_message_$notificationId").assertIsDisplayed()
                onNodeWithTag("article_full_title", useUnmergedTree = true).assertDoesNotExist()
            }
        } finally {
            try {
                viewModels.viewModelStore.clear()
            } finally {
                try {
                    ownedStore?.close()
                } finally {
                    try {
                        engine.close()
                    } finally {
                        try {
                            ownedDatabase?.close()
                        } finally {
                            try {
                                rootBackOwner.navigationEventDispatcher.dispose()
                            } finally {
                                try {
                                    ThemeManager.load(originalTheme)
                                } finally {
                                    if (originalHome == null) System.clearProperty("user.home") else System.setProperty("user.home", originalHome)
                                    directory.toFile().deleteRecursively()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun decodeTarget(name: String) = ZhihuJson.decodeJson<Feed.Target>(fixtureJson(name))

    private fun fixtureJson(name: String) =
        ZhihuJson.json.parseToJsonElement(checkNotNull(javaClass.getResource("/real-api/$name")).readText())
}
