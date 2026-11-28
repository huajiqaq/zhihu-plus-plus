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

import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest

class ContentFilterDatabaseTest {
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
            dao.insertKeyword(BlockedKeyword(keyword = "同字词", isRegex = true))
            dao.insertKeyword(BlockedKeyword(keyword = "同字词", caseSensitive = true))
            val before = dao.getAllKeywords()
            val exported = encodeBlocklistBackup(dao, database.blockedUserDao(), database.blockedQuestionAuthorDao(), database.blockedTopicDao())
            for (type in KeywordType.entries) {
                for (scope in KeywordScope.entries) dao.deleteKeywordsByType(type.name, scope.name)
            }
            importBackup(exported)
            assertEquals(before.map { listOf(it.keyword, it.keywordType, it.scope, it.caseSensitive.toString(), it.isRegex.toString()) }.toSet(), dao.getAllKeywords().map { listOf(it.keyword, it.keywordType, it.scope, it.caseSensitive.toString(), it.isRegex.toString()) }.toSet())
            assertEquals(10, dao.getKeywordCount())
            dao.insertKeyword(BlockedKeyword(keyword = "local-only"))
            val mergedRows = dao.getAllKeywords().toSet()
            importBackup(exported)
            assertEquals(mergedRows, dao.getAllKeywords().toSet())
        } finally {
            database.close()
            check(directory.toFile().deleteRecursively())
        }
    }
}
