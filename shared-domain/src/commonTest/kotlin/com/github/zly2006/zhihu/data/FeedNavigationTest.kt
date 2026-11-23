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

import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.navigation.Search
import com.github.zly2006.zhihu.viewmodel.ArticleViewModel
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class FeedNavigationTest {
    @Test
    fun listNavigationPreservesContentIdentityWithoutSeedingBody() {
        val items = listOf(123L, 124L).map { id ->
            FeedDisplayItem(
                title = "标题 $id",
                summary = "摘要",
                details = "",
                feed = CommonFeed(
                    id = "$id",
                    target = Feed.AnswerTarget(
                        id = id,
                        url = "",
                        question = Feed.QuestionTarget(id = 456, _title = "标题", url = "", type = "question"),
                        content = "<p data-pid=\"$id\">正文 $id</p>",
                    ),
                ),
            )
        }
        items.reversed().forEach { item ->
            val route = assertIs<Article>(item.navDestination)
            assertEquals(item.feed?.target?.navDestination, route)
            val model = ArticleViewModel(route, null)
            assertEquals("", model.content)
            assertNull(model.sourceContent)
        }
        listOf(Article(type = ArticleType.Answer, id = 999), Article(type = ArticleType.Article, id = 123), Article(type = ArticleType.Answer, id = 0), Article(type = ArticleType.Answer, id = -1)).forEach { override ->
            val route = assertIs<Article>(items.first().copy(navDestinationJson = override.toFeedDisplayItemNavDestinationJson()).navDestination)
            assertEquals(override, route)
            assertEquals("", ArticleViewModel(route, null).content)
        }
    }

    @Test
    fun summaryOnlyAndFilteredCardsKeepDisplayFieldsSeparateFromContent() {
        val target = Feed.AnswerTarget(
            id = 123,
            url = "",
            question = Feed.QuestionTarget(id = 456, _title = "标题", url = "", type = "question"),
            excerpt = "摘要",
            content = "<p>不应泄露的正文</p>",
        )
        val item = FeedDisplayItem(title = "已屏蔽", summary = "屏蔽原因", details = "", feed = CommonFeed(id = "123", target = target), isFiltered = true)
        val filtered = assertIs<Article>(item.navDestination)
        assertEquals("标题", filtered.title)
        assertEquals("摘要", filtered.excerpt)
        assertEquals("", ArticleViewModel(filtered, null).content)
        val summaryOnly = item.copy(isFiltered = false, feed = CommonFeed(id = "123", target = target.copy(content = "")))
        val route = assertIs<Article>(summaryOnly.navDestination)
        val model = ArticleViewModel(route, null)
        assertEquals("摘要", route.excerpt)
        assertEquals("", model.content)
        assertNull(model.sourceContent)
    }

    @Test
    fun filteredCardsDoNotTreatFilterReasonsAsContentPreviews() {
        val route = Article(type = ArticleType.Answer, id = 123, title = "实际标题")
        val item = FeedDisplayItem(
            title = "已屏蔽",
            summary = "规则：未关注作者",
            details = "",
            feed = null,
            isFiltered = true,
            navDestinationJson = route.toFeedDisplayItemNavDestinationJson(),
        )
        val model = ArticleViewModel(assertIs<Article>(item.navDestination), null)
        assertEquals("实际标题", model.title)
        assertEquals("", model.content)
        assertNull(model.sourceContent)
    }

    @Test
    fun urlOnlyNavigationDoesNotMergeDisplayMetadata() {
        listOf(ArticleType.Answer, ArticleType.Article).forEach { type ->
            val route = Article(type = type, id = 123)
            val item = FeedDisplayItem(
                title = "列表标题",
                summary = "列表摘要",
                details = "",
                feed = null,
                authorName = "列表作者",
                avatarSrc = "https://example.com/avatar.png",
                navDestinationJson = route.toFeedDisplayItemNavDestinationJson(),
            )
            val model = ArticleViewModel(assertIs<Article>(item.navDestination), null)
            assertEquals("", model.title)
            assertEquals("", model.authorName)
            assertEquals("", model.authorAvatarSrc)
            assertEquals("", model.content)
            assertNull(model.sourceContent)
            assertEquals(route.toFeedDisplayItemNavDestinationJson(), item.stableKey)
        }
        val route = Question(456)
        val question = FeedDisplayItem(
            title = "问题标题",
            summary = "",
            details = "",
            feed = null,
            navDestinationJson = route.toFeedDisplayItemNavDestinationJson(),
        )
        assertEquals(route.title, assertIs<Question>(question.navDestination).title)
    }

    @Test
    fun listMetadataDoesNotOverwriteKnownDestinationMetadata() {
        val route = Article(
            type = ArticleType.Answer,
            id = 123,
            title = "原始标题",
            authorName = "真实作者",
            authorBio = "签名",
            avatarSrc = "avatar",
            excerpt = "原始摘要",
            authorId = "author-id",
            authorUrlToken = "author-token",
            questionId = 456,
            readingQueueSourceId = "source",
        )
        val item = FeedDisplayItem(
            title = "列表展示标题",
            summary = "列表展示摘要",
            details = "",
            feed = null,
            authorName = "展示作者",
            avatarSrc = "展示头像",
            navDestinationJson = route.toFeedDisplayItemNavDestinationJson(),
        )
        val restored = assertIs<Article>(item.navDestination)
        assertEquals(route.toFeedDisplayItemNavDestinationJson(), restored.toFeedDisplayItemNavDestinationJson())
    }

    @Test
    fun persistedNavigationIgnoresExpiredBodyHandoffMetadata() {
        val destination = Article(type = ArticleType.Answer, id = 123, readingQueueSourceId = "source")
        val legacyJson = JsonObject(
            ZhihuJson.json.parseToJsonElement(destination.toFeedDisplayItemNavDestinationJson()).jsonObject +
                ("contentPreviewKey" to JsonPrimitive("expired-handoff")),
        ).toString()
        val item = FeedDisplayItem(title = "标题", summary = "摘要", details = "", feed = null, navDestinationJson = legacyJson)
        val restored = assertIs<Article>(item.navDestination)
        assertEquals(destination.toFeedDisplayItemNavDestinationJson(), restored.toFeedDisplayItemNavDestinationJson())
        assertEquals("source", restored.readingQueueSourceId)
    }

    @Test
    fun feedDisplayItemRestoresExplicitNavigationJson() {
        val destination = Search(query = "fixture")
        val item = FeedDisplayItem(
            title = "标题",
            summary = "摘要",
            details = "详情",
            feed = null,
            navDestinationJson = destination.toFeedDisplayItemNavDestinationJson(),
        )

        assertEquals(destination, item.navDestination)
        assertEquals(destination.toFeedDisplayItemNavDestinationJson(), item.stableKey)
    }
}
