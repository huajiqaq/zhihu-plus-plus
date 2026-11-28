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

import com.github.zly2006.zhihu.data.indexeddb.IdTable
import com.github.zly2006.zhihu.data.indexeddb.IndexedDatabase
import com.github.zly2006.zhihu.data.indexeddb.TextTable
import com.github.zly2006.zhihu.data.indexeddb.sqlLimit
import com.github.zly2006.zhihu.data.indexeddb.sqliteTextOrder

private val views = TextTable(ContentViewRecord.TABLE_NAME, ContentViewRecord.serializer(), ContentViewRecord::id)
private val events = IdTable(
    ContentOpenEvent.TABLE_NAME,
    ContentOpenEvent.serializer(),
    ContentOpenEvent::id,
    { row, id -> row.copy(id = id) },
    { event -> lookupKey(event.contentType, event.contentId) },
)
private val keywords = IdTable(BlockedKeyword.TABLE_NAME, BlockedKeyword.serializer(), BlockedKeyword::id, { row, id -> row.copy(id = id) })
private val users = TextTable(BlockedUser.TABLE_NAME, BlockedUser.serializer(), BlockedUser::userId)
private val authors = TextTable(BlockedQuestionAuthor.TABLE_NAME, BlockedQuestionAuthor.serializer(), BlockedQuestionAuthor::userId)
private val topics = TextTable("blocked_topics", BlockedTopic.serializer(), BlockedTopic::topicId)
private val blockedContent = IdTable(BlockedContentRecord.TABLE_NAME, BlockedContentRecord.serializer(), BlockedContentRecord::id, { row, id -> row.copy(id = id) })
private val blockedFeeds = IdTable(BlockedFeedRecord.TABLE_NAME, BlockedFeedRecord.serializer(), BlockedFeedRecord::id, { row, id -> row.copy(id = id) })

private fun lookupKey(type: String, id: String) = "${type.length}:$type$id"

private val browserFilterDatabase by lazy { IndexedFilterDatabase("zhihu-plus-content-filter") }

actual fun getContentFilterDatabase(): ContentFilterDatabase = browserFilterDatabase

internal class IndexedFilterDatabase(
    name: String,
) : ContentFilterDatabase,
    AutoCloseable {
    private val db = IndexedDatabase(name, views, events, keywords, users, authors, topics, blockedContent, blockedFeeds)

    private val viewDao = object : ContentFilterDao {
        override suspend fun getViewRecord(id: String) = db.read(views) { get(views, id) }

        override suspend fun insertOrUpdateViewRecord(record: ContentViewRecord) {
            db.write(views) { replace(views, record) }
        }

        override suspend fun incrementViewCount(id: String, currentTime: Long) = db.write(views) {
            change(views, id) { it.copy(viewCount = it.viewCount + 1, lastViewTime = currentTime) }
        }

        override suspend fun markAsInteracted(id: String, currentTime: Long) = db.write(views) {
            change(views, id) { it.copy(hasInteraction = true, lastInteractionTime = currentTime) }
        }

        override suspend fun getFilteredContent(maxCount: Int) = db.read(views) { rows(views).filter { it.viewCount > maxCount } }

        override suspend fun getViewedContentIdsByIds(ids: List<String>) = db.read(views) { ids.distinct().sortedWith(sqliteTextOrder).filter { has(views, it) } }

        override suspend fun clearAllRecords() = db.write(views) { clear(views) }

        override suspend fun getRecordCount() = db.read(views) { count(views) }
    }

    private val eventDao = object : ContentOpenEventDao {
        override suspend fun insert(event: ContentOpenEvent) = db.write(events) { insert(events, event) }

        override suspend fun getOpenedContentKeys(type: String, ids: List<String>) = matching(type, ids, null)

        override suspend fun getRemotelySyncedContentKeys(type: String, ids: List<String>) = matching(type, ids) { it.openFrom == "remote_sync" }

        private suspend fun matching(type: String, ids: List<String>, match: ((ContentOpenEvent) -> Boolean)?) = db.read(events) {
            ids
                .distinct()
                .sortedWith(sqliteTextOrder)
                .filter { hasLookup(events, lookupKey(type, it), match) }
                .map { "$type:$it" }
        }
    }

    private val keywordDao = object : BlockedKeywordDao {
        override suspend fun getAllKeywords() = db.read(keywords) { rows(keywords).sortedByDescending { it.createdTime } }

        override fun observeKeywordsByType(type: String, scope: String) = db.observe(keywords) {
            getAllKeywords().filter { it.keywordType == type && it.scope == scope }
        }

        override suspend fun deleteKeywordsByType(type: String, scope: String) = db.write(keywords) {
            deleteWhere(keywords) { it.keywordType == type && it.scope == scope }
        }

        override suspend fun insertKeyword(keyword: BlockedKeyword) = db.write(keywords) { insert(keywords, keyword, replace = true) }

        override suspend fun deleteKeywordById(id: Long, type: String, scope: String) = db.write(keywords) {
            delete(keywords, id) { it.keywordType == type && it.scope == scope }
        }

        override suspend fun getKeywordCount() = db.read(keywords) { count(keywords) }
    }

    private val userDao = object : BlockedUserDao {
        override suspend fun getAllUsers() = db.read(users) { rows(users).sortedByDescending { it.createdTime } }

        override fun observeAllUsers() = db.observe(users, ::getAllUsers)

        override suspend fun insertUser(user: BlockedUser) {
            db.write(users) { replace(users, user) }
        }

        override suspend fun deleteUserById(userId: String) = db.write(users) { delete(users, userId) }

        override suspend fun clearAllUsers() = db.write(users) { clear(users) }

        override suspend fun getUserCount() = db.read(users) { count(users) }

        override suspend fun isUserBlocked(userId: String) = db.read(users) { has(users, userId) }
    }

    private val authorDao = object : BlockedQuestionAuthorDao {
        override suspend fun getAllUsers() = db.read(authors) { rows(authors).sortedByDescending { it.createdTime } }

        override fun observeAllUsers() = db.observe(authors, ::getAllUsers)

        override suspend fun insertUser(user: BlockedQuestionAuthor) {
            db.write(authors) { replace(authors, user) }
        }

        override suspend fun deleteUserById(userId: String) = db.write(authors) { delete(authors, userId) }

        override suspend fun clearAllUsers() = db.write(authors) { clear(authors) }

        override suspend fun getUserCount() = db.read(authors) { count(authors) }

        override suspend fun isUserBlocked(userId: String) = db.read(authors) { has(authors, userId) }
    }

    private val topicDao = object : BlockedTopicDao {
        override suspend fun insertTopic(topic: BlockedTopic) = db.write(topics) { replace(topics, topic) }

        override suspend fun deleteTopicById(topicId: String) = db.write(topics) { delete(topics, topicId) }

        override suspend fun getAllTopics() = db.read(topics) { rows(topics).sortedByDescending { it.addedTime } }

        override suspend fun isTopicBlocked(topicId: String) = db.read(topics) { has(topics, topicId) }

        override fun observeAllTopics() = db.observe(topics, ::getAllTopics)

        override suspend fun clearAllTopics() = db.write(topics) { clear(topics) }

        override suspend fun getBlockedTopicIds(topicIds: List<String>) = db.read(topics) { topicIds.distinct().sortedWith(sqliteTextOrder).filter { has(topics, it) } }

        override suspend fun getTopicNameById(topicId: String) = db.read(topics) {
            checkNotNull(get(topics, topicId)) { "The query result was empty, but expected a single row to return a NON-NULL object of type 'kotlin.String'." }.topicName
        }
    }

    private val blockedContentDao = object : BlockedContentRecordDao {
        override fun observeRecentRecords() = db.observe(blockedContent) {
            db.read(blockedContent) { rows(blockedContent).sortedByDescending { it.blockedTime }.take(BlockedContentRecord.MAX_RECORDS) }
        }

        override suspend fun insertRecord(record: BlockedContentRecord) = db.write(blockedContent) { insert(blockedContent, record, replace = true) }

        override suspend fun deleteRecord(id: Long) = db.write(blockedContent) { delete(blockedContent, id) }

        override suspend fun clearAllRecords() = db.write(blockedContent) { clear(blockedContent) }

        override suspend fun getRecordCount() = db.read(blockedContent) { count(blockedContent) }

        override suspend fun deleteOldestRecords(deleteCount: Int) = db.write(blockedContent) {
            rows(blockedContent).sortedBy { it.blockedTime }.sqlLimit(deleteCount).forEach { delete(blockedContent, it.id) }
        }
    }

    private val blockedFeedDao = object : BlockedFeedRecordDao {
        override fun observeAll() = db.observe(blockedFeeds) { db.read(blockedFeeds) { rows(blockedFeeds).sortedByDescending { it.blockedTime } } }

        override suspend fun insert(record: BlockedFeedRecord) = db.write(blockedFeeds) { insert(blockedFeeds, record, replace = true) }

        override suspend fun deleteById(id: Long) = db.write(blockedFeeds) { delete(blockedFeeds, id) }

        override suspend fun clearAll() = db.write(blockedFeeds) { clear(blockedFeeds) }

        override suspend fun maintainLimit() = db.write(blockedFeeds) {
            rows(blockedFeeds).sortedByDescending { it.blockedTime }.drop(BlockedFeedRecord.MAX_RECORDS).forEach { delete(blockedFeeds, it.id) }
        }
    }

    override fun contentFilterDao(): ContentFilterDao = viewDao

    override fun contentOpenEventDao(): ContentOpenEventDao = eventDao

    override fun blockedKeywordDao(): BlockedKeywordDao = keywordDao

    override fun blockedUserDao(): BlockedUserDao = userDao

    override fun blockedQuestionAuthorDao(): BlockedQuestionAuthorDao = authorDao

    override fun blockedTopicDao(): BlockedTopicDao = topicDao

    override fun blockedContentRecordDao(): BlockedContentRecordDao = blockedContentDao

    override fun blockedFeedRecordDao(): BlockedFeedRecordDao = blockedFeedDao

    override fun closeDatabase() = db.close()

    override fun close() = closeDatabase()
}
