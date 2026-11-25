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

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.github.zly2006.zhihu.filter.asListLoadState
import com.github.zly2006.zhihu.viewmodel.LoadState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ContentFilterDatabaseTest {
    @Test
    fun migratesSyntheticVersionSevenKeywordsWithoutLosingRules() = runTest {
        val directory = createTempDirectory("keyword-scope-migration-")
        val file = directory.resolve("content-filter.db").toFile()
        val original = BlockedKeyword(id = 41, keyword = "合成旧词", caseSensitive = true, isRegex = true, createdTime = 123)
        val semantic = BlockedKeyword(id = 42, keyword = "合成短语", keywordType = KeywordType.NLP_SEMANTIC.name, createdTime = 456)
        val initial = getContentFilterDatabase(file)
        try {
            initial.blockedKeywordDao().insertKeyword(original)
            initial.blockedKeywordDao().insertKeyword(semantic)
            initial.blockedUserDao().insertUser(BlockedUser(userId = "synthetic", userName = "合成用户"))
        } finally {
            initial.close()
        }
        // 只在新建临时库重建 v7 关键词表，其余表沿用 Room 生成的 schema。
        BundledSQLiteDriver().open(file.absolutePath).use { connection ->
            connection.execSQL("ALTER TABLE `blocked_keywords` DROP COLUMN `scope`")
            connection.execSQL("DROP TABLE `room_master_table`")
            connection.execSQL("PRAGMA user_version = 7")
        }
        val migrated = getContentFilterDatabase(file)
        try {
            assertEquals(listOf(semantic, original), migrated.blockedKeywordDao().getAllKeywords())
            assertEquals(2, migrated.blockedKeywordDao().getKeywordCount())
            assertEquals(listOf("synthetic"), migrated.blockedUserDao().getAllUsers().map { it.userId })
            val inserted = migrated.blockedKeywordDao().insertKeyword(BlockedKeyword(keyword = "合成新词", scope = KeywordScope.ANSWER.name))
            assertTrue(inserted > 42)
            assertEquals(
                KeywordScope.ANSWER.name,
                migrated
                    .blockedKeywordDao()
                    .getAllKeywords()
                    .first()
                    .scope,
            )
        } finally {
            migrated.close()
            check(directory.toFile().deleteRecursively())
        }
    }

    @Test
    fun scopedQueriesAndClearingPreserveOtherScopesAndKeywordTypes() = runTest {
        val directory = createTempDirectory("keyword-scope-dao-")
        val database = getContentFilterDatabase(directory.resolve("content-filter.db").toFile())
        try {
            val dao = database.blockedKeywordDao()
            for (type in KeywordType.entries) {
                for (scope in KeywordScope.entries) dao.insertKeyword(BlockedKeyword(keyword = "同字词", keywordType = type.name, scope = scope.name))
            }
            assertEquals(6, dao.getKeywordCount())
            for (type in KeywordType.entries) {
                for (scope in KeywordScope.entries) {
                    val keyword = dao.observeKeywordsByType(type.name, scope.name).first().single()
                    assertEquals(scope.name, keyword.scope)
                    assertEquals(type.name, keyword.keywordType)
                }
            }
            val question = dao.observeKeywordsByType(KeywordType.EXACT_MATCH.name, KeywordScope.QUESTION.name).first().single()
            dao.deleteKeywordById(question.id, KeywordType.EXACT_MATCH.name, KeywordScope.ANSWER.name)
            assertEquals(6, dao.getKeywordCount())
            dao.deleteKeywordsByType(KeywordType.EXACT_MATCH.name, KeywordScope.QUESTION.name)
            assertEquals(5, dao.getKeywordCount())
            assertEquals(emptyList(), dao.observeKeywordsByType(KeywordType.EXACT_MATCH.name, KeywordScope.QUESTION.name).first())
            assertEquals(1, dao.observeKeywordsByType(KeywordType.NLP_SEMANTIC.name, KeywordScope.QUESTION.name).first().size)
            assertEquals(1, dao.observeKeywordsByType(KeywordType.EXACT_MATCH.name, KeywordScope.ALL.name).first().size)
            dao.deleteKeywordsByType(KeywordType.NLP_SEMANTIC.name, KeywordScope.ANSWER.name)
            assertEquals(4, dao.getKeywordCount())
        } finally {
            database.close()
            check(directory.toFile().deleteRecursively())
        }
    }

    @Test
    fun oldBackupImportsAllAndScopedBackupKeepsSameWordsIndependent() = runTest {
        val directory = createTempDirectory("keyword-scope-backup-")
        val database = getContentFilterDatabase(directory.resolve("content-filter.db").toFile())
        try {
            val dao = database.blockedKeywordDao()

            suspend fun importBackup(text: String) = importBlocklistBackupFromJsonText(dao, database.blockedUserDao(), database.blockedQuestionAuthorDao(), database.blockedTopicDao(), text)
            assertFailsWith<IllegalArgumentException> {
                importBackup("""{"version":3,"keywords":[{"keyword":"不应写入的前项"}],"nlpKeywords":[{"keyword":"非法范围的后项","scope":"INVALID"}]}""")
            }
            assertEquals(0, dao.getKeywordCount())
            importBackup("""{"version":3,"keywords":[{"keyword":"旧词","caseSensitive":true,"isRegex":true}],"nlpKeywords":[{"keyword":"旧短语"}]}""")
            assertEquals(setOf(KeywordScope.ALL.name), dao.getAllKeywords().map { it.scope }.toSet())
            for (type in KeywordType.entries) {
                for (scope in KeywordScope.entries) dao.insertKeyword(BlockedKeyword(keyword = "同字词", keywordType = type.name, scope = scope.name))
            }
            val before = dao.getAllKeywords()
            val exported = encodeBlocklistBackup(dao, database.blockedUserDao(), database.blockedQuestionAuthorDao(), database.blockedTopicDao())
            for (type in KeywordType.entries) {
                for (scope in KeywordScope.entries) dao.deleteKeywordsByType(type.name, scope.name)
            }
            importBackup(exported)
            assertEquals(before.map { listOf(it.keyword, it.keywordType, it.scope, it.caseSensitive.toString(), it.isRegex.toString()) }.toSet(), dao.getAllKeywords().map { listOf(it.keyword, it.keywordType, it.scope, it.caseSensitive.toString(), it.isRegex.toString()) }.toSet())
            assertEquals(8, dao.getKeywordCount())
        } finally {
            database.close()
            check(directory.toFile().deleteRecursively())
        }
    }

    @Test
    fun storesBlockedQuestionAuthor() = runTest {
        val database = getContentFilterDatabase(
            createTempDirectory("content-filter-room-question-author").resolve("content-filter.db").toFile(),
        )

        database.blockedQuestionAuthorDao().insertUser(
            BlockedQuestionAuthor(
                userId = "asker-id",
                userName = "提问者",
                urlToken = "asker-token",
            ),
        )

        val authors = database.blockedQuestionAuthorDao().getAllUsers()
        assertEquals(listOf("asker-id"), authors.map { it.userId })
        assertEquals(listOf("提问者"), authors.map { it.userName })
        database.close()
    }

    @Test
    fun categoryClearingAndObservedQueriesKeepOtherRules() = runTest {
        val database = getContentFilterDatabase(
            createTempDirectory("content-filter-room-categories").resolve("content-filter.db").toFile(),
        )
        try {
            val keywords = database.blockedKeywordDao()
            keywords.insertKeyword(BlockedKeyword(keyword = "exact", keywordType = KeywordType.EXACT_MATCH.name))
            keywords.insertKeyword(BlockedKeyword(keyword = "semantic", keywordType = KeywordType.NLP_SEMANTIC.name))
            assertEquals(listOf("exact"), keywords.observeKeywordsByType(KeywordType.EXACT_MATCH.name).first().map { it.keyword })
            assertEquals(listOf("semantic"), keywords.observeKeywordsByType(KeywordType.NLP_SEMANTIC.name).first().map { it.keyword })
            keywords.deleteKeywordsByType(KeywordType.EXACT_MATCH.name)
            assertEquals(emptyList(), keywords.observeKeywordsByType(KeywordType.EXACT_MATCH.name).first())
            assertEquals(listOf("semantic"), keywords.getAllKeywords().map { it.keyword })
            keywords.insertKeyword(BlockedKeyword(keyword = "exact-again", keywordType = KeywordType.EXACT_MATCH.name))
            keywords.deleteKeywordsByType(KeywordType.NLP_SEMANTIC.name)
            assertEquals(listOf("exact-again"), keywords.getAllKeywords().map { it.keyword })

            database.blockedUserDao().insertUser(BlockedUser(userId = "user", userName = "User"))
            database.blockedQuestionAuthorDao().insertUser(BlockedQuestionAuthor(userId = "asker", userName = "Asker"))
            database.blockedTopicDao().insertTopic(BlockedTopic(topicId = "topic", topicName = "Topic"))
            assertEquals(
                listOf("user"),
                database
                    .blockedUserDao()
                    .observeAllUsers()
                    .first()
                    .map { it.userId },
            )
            assertEquals(
                listOf("asker"),
                database
                    .blockedQuestionAuthorDao()
                    .observeAllUsers()
                    .first()
                    .map { it.userId },
            )
            assertEquals(
                listOf("topic"),
                database
                    .blockedTopicDao()
                    .observeAllTopics()
                    .first()
                    .map { it.topicId },
            )
            database.blockedQuestionAuthorDao().clearAllUsers()
            assertEquals(emptyList(), database.blockedQuestionAuthorDao().observeAllUsers().first())
            assertEquals(listOf("user"), database.blockedUserDao().getAllUsers().map { it.userId })
            assertEquals(listOf("topic"), database.blockedTopicDao().getAllTopics().map { it.topicId })
        } finally {
            database.close()
        }
    }

    @Test
    fun observedListDistinguishesLoadingEmptyDataFailureAndCancellation() = runTest {
        assertEquals(listOf(LoadState.Loading, LoadState.Ready(emptyList<Int>())), emptyFlow<List<Int>>().asListLoadState().toList())
        assertEquals(listOf(LoadState.Loading, LoadState.Ready(listOf(7))), flowOf(listOf(7)).asListLoadState().toList())
        val failure = flow<List<Int>> { error("query failed") }.asListLoadState().toList()
        assertEquals(listOf(LoadState.Loading, LoadState.Failed("query failed")), failure)
        val states = mutableListOf<LoadState<List<Int>>>()
        var cancelled = false
        try {
            flow<List<Int>> { throw CancellationException("old observer") }.asListLoadState().collect { states.add(it) }
        } catch (error: CancellationException) {
            cancelled = true
            assertEquals("old observer", error.message)
        }
        assertEquals(true, cancelled)
        assertEquals(listOf<LoadState<List<Int>>>(LoadState.Loading), states)
    }

    @Test
    fun cancelledBlockedRecordWriteStopsBeforePublishingSuccess() = runTest {
        for (phase in listOf("insert", "limit")) {
            val directory = createTempDirectory("blocked-record-cancellation-")
            val database = getContentFilterDatabase(directory.resolve("content-filter.db").toFile())
            try {
                val records = database.blockedContentRecordDao()
                val calls = mutableListOf<String>()
                val cancellation = CancellationException("cancel $phase")
                val recordDao = object : BlockedContentRecordDao by records {
                    override suspend fun insertRecord(record: BlockedContentRecord): Long {
                        calls += "insert"
                        if (phase == "insert") throw cancellation
                        return records.insertRecord(record)
                    }

                    override suspend fun maintainRecordLimit() {
                        calls += "limit"
                        throw cancellation
                    }
                }
                val service = BlockedKeywordService(database.blockedKeywordDao(), recordDao, null)
                val thrown = assertFailsWith<CancellationException> {
                    service.recordBlockedContent(
                        contentId = "synthetic-answer",
                        contentType = "answer",
                        title = "合成内容",
                        excerpt = "合成摘要",
                        authorName = null,
                        authorId = null,
                        matchedKeywords = listOf(MatchedKeywordInfo("合成关键词", 0.9)),
                    )
                }
                assertTrue(generateSequence<Throwable>(thrown) { it.cause }.any { it === cancellation })
                assertEquals(if (phase == "insert") listOf("insert") else listOf("insert", "limit"), calls)
                assertEquals(if (phase == "insert") 0 else 1, records.getRecordCount())
            } finally {
                database.close()
                check(directory.toFile().deleteRecursively())
            }
        }
    }
}
