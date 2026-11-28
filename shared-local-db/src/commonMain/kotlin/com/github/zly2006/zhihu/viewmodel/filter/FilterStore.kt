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

import com.github.zly2006.zhihu.viewmodel.currentEpochMillis
import kotlinx.coroutines.flow.Flow

expect fun getContentFilterDatabase(): ContentFilterDatabase

interface ContentFilterDatabase {
    fun closeDatabase()

    fun contentFilterDao(): ContentFilterDao

    fun contentOpenEventDao(): ContentOpenEventDao

    fun blockedKeywordDao(): BlockedKeywordDao

    fun blockedUserDao(): BlockedUserDao

    fun blockedQuestionAuthorDao(): BlockedQuestionAuthorDao

    fun blockedContentRecordDao(): BlockedContentRecordDao

    fun blockedTopicDao(): BlockedTopicDao

    fun blockedFeedRecordDao(): BlockedFeedRecordDao
}

interface ContentFilterDao {
    suspend fun getViewRecord(id: String): ContentViewRecord?

    suspend fun insertOrUpdateViewRecord(record: ContentViewRecord)

    suspend fun incrementViewCount(id: String, currentTime: Long = currentEpochMillis())

    suspend fun markAsInteracted(id: String, currentTime: Long = currentEpochMillis())

    suspend fun getFilteredContent(maxCount: Int = ContentViewRecord.MAX_VIEW_COUNT_WITHOUT_INTERACTION): List<ContentViewRecord>

    suspend fun getViewedContentIdsByIds(ids: List<String>): List<String>

    suspend fun clearAllRecords()

    suspend fun getRecordCount(): Int
}

interface ContentOpenEventDao {
    suspend fun insert(event: ContentOpenEvent): Long

    /** 按类型和 ID 查询，复用联合索引，避免拼接表达式扫描全部历史。 */
    suspend fun getOpenedContentKeysByKeys(keys: List<String>): List<String> = queryContentKeys(keys, ::getOpenedContentKeys)

    suspend fun getRemotelySyncedContentKeysByKeys(keys: List<String>): List<String> = queryContentKeys(keys, ::getRemotelySyncedContentKeys)

    suspend fun getOpenedContentKeys(type: String, ids: List<String>): List<String>

    suspend fun getRemotelySyncedContentKeys(type: String, ids: List<String>): List<String>
}

private suspend fun queryContentKeys(keys: List<String>, query: suspend (String, List<String>) -> List<String>): List<String> =
    keys.distinct().groupBy { it.substringBefore(':') }.flatMap { (type, typedKeys) ->
        // 部分 SQLite 版本最多接受 999 个变量，分批时需为类型参数预留位置。
        typedKeys.map { it.substringAfter(':') }.chunked(500).flatMap { ids -> query(type, ids) }
    }

interface BlockedKeywordDao {
    suspend fun getAllKeywords(): List<BlockedKeyword>

    fun observeKeywordsByType(type: String, scope: String = KeywordScope.ALL.name): Flow<List<BlockedKeyword>>

    /** 关键词与 NLP 短语共用这张表，清空只能删除当前类型和作用范围。 */
    suspend fun deleteKeywordsByType(type: String, scope: String = KeywordScope.ALL.name)

    suspend fun insertKeyword(keyword: BlockedKeyword): Long

    suspend fun deleteKeywordById(id: Long, type: String = KeywordType.EXACT_MATCH.name, scope: String = KeywordScope.ALL.name)

    suspend fun getKeywordCount(): Int
}

interface BlockedUserDao {
    suspend fun getAllUsers(): List<BlockedUser>

    fun observeAllUsers(): Flow<List<BlockedUser>>

    suspend fun insertUser(user: BlockedUser)

    suspend fun deleteUserById(userId: String)

    suspend fun clearAllUsers()

    suspend fun getUserCount(): Int

    suspend fun isUserBlocked(userId: String): Boolean
}

interface BlockedQuestionAuthorDao {
    suspend fun getAllUsers(): List<BlockedQuestionAuthor>

    fun observeAllUsers(): Flow<List<BlockedQuestionAuthor>>

    suspend fun insertUser(user: BlockedQuestionAuthor)

    suspend fun deleteUserById(userId: String)

    suspend fun clearAllUsers()

    suspend fun getUserCount(): Int

    suspend fun isUserBlocked(userId: String): Boolean
}

interface BlockedContentRecordDao {
    fun observeRecentRecords(): Flow<List<BlockedContentRecord>>

    suspend fun insertRecord(record: BlockedContentRecord): Long

    suspend fun deleteRecord(id: Long)

    suspend fun clearAllRecords()

    suspend fun getRecordCount(): Int

    suspend fun deleteOldestRecords(deleteCount: Int)

    suspend fun maintainRecordLimit() {
        val count = getRecordCount()
        if (count > BlockedContentRecord.MAX_RECORDS) deleteOldestRecords(count - BlockedContentRecord.MAX_RECORDS)
    }
}

interface BlockedTopicDao {
    suspend fun insertTopic(topic: BlockedTopic): Long

    suspend fun deleteTopicById(topicId: String)

    suspend fun getAllTopics(): List<BlockedTopic>

    suspend fun isTopicBlocked(topicId: String): Boolean

    fun observeAllTopics(): Flow<List<BlockedTopic>>

    suspend fun clearAllTopics()

    suspend fun getBlockedTopicIds(topicIds: List<String>): List<String>

    suspend fun getTopicNameById(topicId: String): String
}

interface BlockedFeedRecordDao {
    fun observeAll(): Flow<List<BlockedFeedRecord>>

    suspend fun insert(record: BlockedFeedRecord): Long

    suspend fun deleteById(id: Long)

    suspend fun clearAll()

    suspend fun maintainLimit()
}
