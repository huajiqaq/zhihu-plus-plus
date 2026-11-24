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

import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.Feed
import kotlinx.coroutines.test.runTest
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

class FeedContentFilterPipelineTest {
    @Test
    fun filtersByUserKeywordNlpAndTopicInOrder() = runTest {
        val database = getContentFilterDatabase(
            createTempDirectory("feed-content-filter-pipeline").resolve("content-filter.db").toFile(),
        )
        database.blockedUserDao().insertUser(BlockedUser(userId = "blocked-user", userName = "Blocked"))
        database.blockedQuestionAuthorDao().insertUser(
            BlockedQuestionAuthor(userId = "blocked-asker", userName = "Blocked Asker"),
        )
        database.blockedKeywordDao().insertKeyword(BlockedKeyword(keyword = "blocked keyword"))
        database.blockedTopicDao().insertTopic(BlockedTopic(topicId = "blocked-topic", topicName = "Blocked Topic"))

        val keywordService = KeywordSemanticMatcher { text, phrases, _ ->
            phrases.filter { text.contains("nlp hit") }.map { it to 0.95 }
        }
        database.blockedKeywordDao().insertKeyword(
            BlockedKeyword(
                keyword = "nlp phrase",
                keywordType = KeywordType.NLP_SEMANTIC.name,
            ),
        )

        val result = FeedContentFilterPipeline(
            settings = FeedFilterSettings(),
            database = database,
            semanticMatcher = keywordService,
        ).filter(
            listOf(
                filterable("keep", authorId = "ok-user"),
                filterable("by user", authorId = "blocked-user"),
                filterable("by question author", authorId = "ok-user", questionAuthorId = "blocked-asker"),
                filterable("blocked keyword title", authorId = "ok-user"),
                filterable("nlp hit", authorId = "ok-user"),
                filterable("topic", authorId = "ok-user", topicId = "blocked-topic"),
            ),
        )

        assertEquals(listOf("keep"), result.kept.map { it.title })
        assertEquals(
            listOf(
                "屏蔽作者：author",
                "屏蔽提问者：asker",
                "关键词屏蔽",
                "NLP语义屏蔽：nlp phrase",
                "屏蔽主题：Blocked Topic",
            ),
            result.blocked.map { it.second },
        )
        database.close()
    }

    @Test
    fun nlpFilteringUsesPlainTextFromHtmlByDefault() = runTest {
        val database = getContentFilterDatabase(
            createTempDirectory("feed-content-filter-html").resolve("content-filter.db").toFile(),
        )
        val keywordService = KeywordSemanticMatcher { text, phrases, _ ->
            phrases
                .filter { text.contains("blocked phrase") && !text.contains("<strong>") }
                .map { it to 0.95 }
        }
        database.blockedKeywordDao().insertKeyword(
            BlockedKeyword(
                keyword = "nlp phrase",
                keywordType = KeywordType.NLP_SEMANTIC.name,
            ),
        )

        val result = FeedContentFilterPipeline(
            settings = FeedFilterSettings(),
            database = database,
            semanticMatcher = keywordService,
        ).filter(
            listOf(
                filterable(
                    title = "html",
                    content = "<p>blocked <strong>phrase</strong></p>",
                    authorId = "ok-user",
                ),
            ),
        )

        assertEquals(emptyList(), result.kept)
        assertEquals(listOf("NLP语义屏蔽：nlp phrase"), result.blocked.map { it.second })
        database.close()
    }

    @Test
    fun scopedKeywordsMatchFieldSourcesAndKeepLegacyAllBehavior() = runTest {
        val directory = createTempDirectory("keyword-scope-")
        val database = getContentFilterDatabase(directory.resolve("content-filter.db").toFile())
        try {
            val answer = FilterableContent(
                title = "合成问题标题",
                summary = "合成回答摘要",
                content = "<p>回答包含类比词</p>",
                authorName = null,
                authorId = null,
                contentId = "answer",
                contentType = ContentType.ANSWER,
                raw = DataHolder.DummyContent,
            )
            val question = answer.copy(
                contentId = "question",
                contentType = ContentType.QUESTION,
                content = "合成问题描述",
                summary = null,
                source = Feed.QuestionTarget(id = 1, _title = answer.title, url = "", type = "question", detail = "合成问题描述"),
            )
            val other = filterable("合成文章", "类比词", "author")
            val dao = database.blockedKeywordDao()
            val pipeline = FeedContentFilterPipeline(FeedFilterSettings(enableNlpBlocking = false), database, null)

            suspend fun blockedBy(keyword: BlockedKeyword, contents: List<FilterableContent>): List<String> {
                val id = dao.insertKeyword(keyword)
                val blocked = pipeline.filter(contents).blocked.map { it.first.contentId }
                dao.deleteKeywordById(id, keyword.keywordType, keyword.scope)
                return blocked
            }
            assertEquals(emptyList(), blockedBy(BlockedKeyword(keyword = "类比词", scope = KeywordScope.QUESTION.name), listOf(answer)))
            assertEquals(listOf("answer"), blockedBy(BlockedKeyword(keyword = "合成问题标题", scope = KeywordScope.QUESTION.name), listOf(answer)))
            assertEquals(emptyList(), blockedBy(BlockedKeyword(keyword = "合成问题标题", scope = KeywordScope.ANSWER.name), listOf(answer, question)))
            assertEquals(listOf("answer"), blockedBy(BlockedKeyword(keyword = "合成回答摘要", scope = KeywordScope.ANSWER.name), listOf(answer)))
            assertEquals(listOf("question"), blockedBy(BlockedKeyword(keyword = "合成问题描述", scope = KeywordScope.QUESTION.name), listOf(question)))
            assertEquals(listOf("answer", other.contentId), blockedBy(BlockedKeyword(keyword = "类比词"), listOf(answer, other)))
            val regexAnswer = answer.copy(content = "Synthetic ABC42", title = "ABC42 question")
            assertEquals(listOf("answer"), blockedBy(BlockedKeyword(keyword = "abc\\d+", isRegex = true, scope = KeywordScope.ANSWER.name), listOf(regexAnswer)))
            assertEquals(emptyList(), blockedBy(BlockedKeyword(keyword = "abc\\d+", isRegex = true, caseSensitive = true, scope = KeywordScope.ANSWER.name), listOf(regexAnswer)))
            assertEquals(emptyList(), blockedBy(BlockedKeyword(keyword = "[", isRegex = true, scope = KeywordScope.ANSWER.name), listOf(regexAnswer)))
        } finally {
            database.close()
            check(directory.toFile().deleteRecursively())
        }
    }

    @Test
    fun nlpScopesSeparateQuestionTitleAndAnswerBodyWithoutChangingThreshold() = runTest {
        val directory = createTempDirectory("nlp-keyword-scope-")
        val database = getContentFilterDatabase(directory.resolve("content-filter.db").toFile())
        try {
            val calls = mutableListOf<Pair<String, List<String>>>()
            val matcher = KeywordSemanticMatcher { text, phrases, threshold ->
                assertEquals(0.73, threshold)
                calls.add(text to phrases)
                phrases.filter { it in text }.map { it to 0.95 }
            }
            for (scope in KeywordScope.entries) {
                database.blockedKeywordDao().insertKeyword(BlockedKeyword(keyword = "标题词", keywordType = KeywordType.NLP_SEMANTIC.name, scope = scope.name))
                database.blockedKeywordDao().insertKeyword(BlockedKeyword(keyword = "类比词", keywordType = KeywordType.NLP_SEMANTIC.name, scope = scope.name))
            }
            val answer = FilterableContent(
                title = "标题词",
                summary = "回答摘要",
                content = "<p>回答<strong>类比词</strong></p>",
                authorName = null,
                authorId = null,
                contentId = "answer",
                contentType = ContentType.ANSWER,
                raw = DataHolder.DummyContent,
            )
            val service = BlockedKeywordService(database.blockedKeywordDao(), database.blockedContentRecordDao(), matcher)
            assertEquals(true, service.checkNLPBlockingWithWeight(answer, 0.73).first)
            assertEquals(3, calls.size)
            assertEquals(listOf("标题词 回答摘要 回答类比词 ", "标题词 ", "回答摘要 回答类比词 ").sorted(), calls.map { it.first }.sorted())
            calls.clear()
            service.checkNLPBlockingWithWeight(answer.copy(title = ""), 0.73)
            assertEquals(listOf("回答摘要 回答类比词 "), calls.map { it.first })
        } finally {
            database.close()
            check(directory.toFile().deleteRecursively())
        }
    }

    private fun filterable(
        title: String,
        content: String = title,
        authorId: String,
        questionAuthorId: String? = null,
        topicId: String? = null,
    ): FilterableContent = FilterableContent(
        title = title,
        summary = null,
        content = content,
        authorName = "author",
        authorId = authorId,
        contentId = title,
        contentType = "article",
        raw = article(title, content, topicId),
        questionAuthorName = questionAuthorId?.let { "asker" },
        questionAuthorId = questionAuthorId,
    )

    private fun article(
        title: String,
        body: String,
        topicId: String?,
    ): DataHolder.Article = DataHolder.Article(
        id = title.hashCode().toLong(),
        author = author(),
        canComment = DataHolder.CanComment(status = true, reason = ""),
        title = title,
        content = body,
        excerpt = "",
        type = "article",
        created = 1L,
        updated = 1L,
        url = "https://www.zhihu.com/p/$title",
        voteupCount = 0,
        topics = topicId?.let { listOf(DataHolder.Topic(id = it, type = "topic", url = "", name = it)) },
    )

    private fun author(): DataHolder.Author = DataHolder.Author(
        avatarUrl = "",
        gender = 0,
        headline = "",
        id = "author-id",
        isAdvertiser = false,
        isOrg = false,
        name = "author",
        type = "people",
        url = "",
        urlToken = "author",
        userType = "people",
    )
}
