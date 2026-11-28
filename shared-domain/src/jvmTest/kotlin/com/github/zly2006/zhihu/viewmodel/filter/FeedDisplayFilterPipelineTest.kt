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

package com.github.zly2006.zhihu.viewmodel.filter

import com.github.zly2006.zhihu.data.CommonFeed
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.Feed
import com.github.zly2006.zhihu.data.FeedDisplayItem
import com.github.zly2006.zhihu.data.Person
import com.github.zly2006.zhihu.data.toDisplayItem
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.platform.MapSettingsStore
import com.github.zly2006.zhihu.viewmodel.ANSWER_VOTEUP_THRESHOLD_PREFERENCE_KEY
import com.github.zly2006.zhihu.viewmodel.FeedDisplaySettings
import com.github.zly2006.zhihu.viewmodel.QUALITY_FILTER_MODE_PREFERENCE_KEY
import com.github.zly2006.zhihu.viewmodel.QualityFilterMode
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject

class FeedDisplayFilterPipelineTest {
    @Test
    fun lowVoteRulesPlaceholderStillMatchesBlockedWordsInTheOriginalBody() = runTest {
        val settings = MapSettingsStore().apply {
            putString(QUALITY_FILTER_MODE_PREFERENCE_KEY, QualityFilterMode.RULES.name)
            putInt(ANSWER_VOTEUP_THRESHOLD_PREFERENCE_KEY, 1000)
        }
        val rules = FeedDisplaySettings(settings)
        val target = Feed.AnswerTarget(
            id = 42,
            url = "",
            voteupCount = 999,
            question = Feed.QuestionTarget(id = 8, _title = "合成问题", url = "", type = "question"),
            excerpt = "合成摘要",
            content = "<p>原文合成禁词</p>",
        )
        val normal = CommonFeed(target = target).toDisplayItem(enableQualityFilter = false)
        val placeholder = assertNotNull(rules.applyQualityFilter(normal.copy(content = target.content)))
        assertTrue(placeholder.isQualityFiltered)
        assertEquals(target.content, placeholder.content, "质量规则不能覆盖被自定义词表匹配的原文")
        val fixture = fixture(FeedFilterSettings(enableNlpBlocking = false))
        try {
            fixture.database.blockedKeywordDao().insertKeyword(BlockedKeyword(keyword = "合成禁词"))
            val pipeline = fixture.pipeline(ContentDetailProvider { null }, rules)
            assertTrue(pipeline.filter(listOf(placeholder)).isEmpty(), "RULES 占位也必须经过显式词表")
            val clean = normal.copy(feed = CommonFeed(target = target.copy(content = "纯净原文")), content = "纯净原文")
            assertEquals(1, pipeline.filter(listOf(clean)).size)
        } finally {
            fixture.database.closeDatabase()
        }
    }

    @Test
    fun explicitAuthorAndTopicRulesUseFeedFactsWithoutAnotherDetailRequest() = runTest {
        val fixture = fixture(FeedFilterSettings(enableNlpBlocking = false))
        try {
            val author = Person("blocked-author", "", "people", name = "author", headline = "", avatarUrl = "")
            val target = Feed.AnswerTarget(
                id = 9,
                url = "",
                author = author,
                voteupCount = 1000,
                question = Feed.QuestionTarget(id = 2, _title = "合成问题", url = "", type = "question", boundTopicIds = listOf(12)),
            )
            val card = CommonFeed(target = target).toDisplayItem(enableQualityFilter = false)
            fixture.database.blockedUserDao().insertUser(BlockedUser(userId = author.id, userName = author.name))
            assertTrue(fixture.pipeline(ContentDetailProvider { null }).filter(listOf(card)).isEmpty())
            fixture.database.blockedTopicDao().insertTopic(BlockedTopic(topicId = "12", topicName = "合成主题"))
            val otherAuthor = card.copy(feed = CommonFeed(target = target.copy(author = author.copy(id = "other"))))
            assertTrue(fixture.pipeline(ContentDetailProvider { null }).filter(listOf(otherAuthor)).isEmpty())
            val hydrated = otherAuthor.copy(raw = answer(9, 2, "合成问题"))
            assertTrue(fixture.pipeline(ContentDetailProvider { error("已有详情不可重复请求") }).filter(listOf(hydrated)).isEmpty())
        } finally {
            fixture.database.closeDatabase()
        }
    }

    @Test
    fun filteringKeepsContentTypesSeparateWhenNumericIdsMatch() = runTest {
        for (reverse in arrayOf(false, true)) {
            val fixture = fixture(FeedFilterSettings(reverseBlock = reverse))
            try {
                val answer = answer(7, 20, "question").copy(paidInfo = buildJsonObject { })
                val article = article("allowed article").copy(id = 7)
                val answerCard = item("paid answer", 7).copy(
                    destination = Article(type = ArticleType.Answer, id = 7),
                )
                val articleCard = item("allowed article", 7)
                val result = fixture
                    .pipeline(
                        ContentDetailProvider { destination ->
                            when ((destination as Article).type) {
                                ArticleType.Answer -> answer
                                ArticleType.Article -> article
                            }
                        },
                    ).filter(arrayOf(answerCard, articleCard, articleCard).asList())
                assertContentEquals(
                    if (reverse) arrayOf("paid answer") else arrayOf("allowed article", "allowed article"),
                    result.map { it.title }.toTypedArray(),
                )
                if (!reverse) {
                    result.forEach { assertEquals(article, it.raw) }
                    assertEquals(
                        1,
                        fixture.database
                            .blockedFeedRecordDao()
                            .observeAll()
                            .first()
                            .size,
                    )
                }
            } finally {
                fixture.database.closeDatabase()
            }
        }
    }

    private fun fixture(settings: FeedFilterSettings = FeedFilterSettings()): Fixture {
        val database = getContentFilterDatabase(
            createTempDirectory("feed-display-filter-pipeline").resolve("content-filter.db").toFile(),
        )
        return Fixture(database, settings)
    }

    private class Fixture(
        val database: ContentFilterDatabase,
        val settings: FeedFilterSettings,
    ) {
        fun pipeline(
            detailProvider: ContentDetailProvider,
            displaySettings: FeedDisplaySettings? = null,
        ): FeedDisplayFilterPipeline = FeedDisplayFilterPipeline(
            settings = settings,
            contentDetailProvider = detailProvider,
            database = database,
            semanticMatcher = KeywordSemanticMatcher { _, _, _ -> emptyList() },
            displaySettings = displaySettings,
        )
    }

    private fun item(
        title: String,
        id: Long,
        details: String = "",
        feed: Feed? = null,
    ): FeedDisplayItem = FeedDisplayItem(
        title = title,
        summary = null,
        details = details,
        feed = feed,
        destination = Article(type = ArticleType.Article, id = id),
    )

    private fun article(
        title: String,
        content: String = title,
        paid: Boolean = false,
    ): DataHolder.Article = DataHolder.Article(
        id = title.hashCode().toLong(),
        author = author(),
        canComment = DataHolder.CanComment(status = true, reason = ""),
        title = title,
        content = content,
        excerpt = "",
        type = "article",
        created = 1L,
        updated = 1L,
        url = "https://www.zhihu.com/p/$title",
        voteupCount = 0,
        paidInfo = if (paid) buildJsonObject { } else null,
    )

    private fun answer(
        id: Long,
        questionId: Long,
        questionTitle: String,
        questionAuthor: DataHolder.Author? = null,
    ): DataHolder.Answer = DataHolder.Answer(
        answerType = "answer",
        author = author(),
        canComment = DataHolder.CanComment(status = true, reason = ""),
        content = "answer",
        createdTime = 1L,
        excerpt = "answer",
        id = id,
        question = DataHolder.AnswerModelQuestion(
            created = 1L,
            id = questionId,
            questionType = "normal",
            title = questionTitle,
            type = "question",
            updatedTime = 1L,
            url = "https://www.zhihu.com/question/$questionId",
            author = questionAuthor,
        ),
        thanksCount = 0,
        type = "answer",
        updatedTime = 1L,
        url = "https://www.zhihu.com/question/$questionId/answer/$id",
        voteupCount = 0,
    )

    private fun author(
        id: String = "author-id",
        name: String = "author",
    ): DataHolder.Author = DataHolder.Author(
        avatarUrl = "",
        gender = 0,
        headline = "",
        id = id,
        isAdvertiser = false,
        isOrg = false,
        name = name,
        type = "people",
        url = "",
        urlToken = name,
        userType = "people",
    )
}
