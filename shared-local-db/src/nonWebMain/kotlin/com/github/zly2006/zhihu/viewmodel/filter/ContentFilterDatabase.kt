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

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabase.Builder
import androidx.room.RoomDatabaseConstructor
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.github.zly2006.zhihu.data.applyPlatformDriver
import kotlinx.coroutines.Dispatchers

@Database(
    entities = [ViewRow::class, KeywordRow::class, UserRow::class, QuestionAuthorRow::class, BlockedContentRow::class, TopicRow::class, BlockedFeedRow::class, OpenEventRow::class],
    version = 8,
    exportSchema = false,
)
@ConstructedBy(ContentFilterDatabaseConstructor::class)
abstract class RoomContentFilterDatabase :
    RoomDatabase(),
    ContentFilterDatabase {
    final override fun closeDatabase() = super.close()

    abstract override fun contentFilterDao(): RoomContentFilterDao

    abstract override fun contentOpenEventDao(): RoomContentOpenEventDao

    abstract override fun blockedKeywordDao(): RoomBlockedKeywordDao

    abstract override fun blockedUserDao(): RoomBlockedUserDao

    abstract override fun blockedQuestionAuthorDao(): RoomBlockedQuestionAuthorDao

    abstract override fun blockedContentRecordDao(): RoomBlockedContentRecordDao

    abstract override fun blockedTopicDao(): RoomBlockedTopicDao

    abstract override fun blockedFeedRecordDao(): RoomBlockedFeedRecordDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object ContentFilterDatabaseConstructor : RoomDatabaseConstructor<RoomContentFilterDatabase> {
    override fun initialize(): RoomContentFilterDatabase
}

private val migration2To3 = object : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `blocked_content_records` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `contentId` TEXT NOT NULL,
                `contentType` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `excerpt` TEXT,
                `authorName` TEXT,
                `authorId` TEXT,
                `blockedTime` INTEGER NOT NULL,
                `blockReason` TEXT NOT NULL,
                `matchedKeywords` TEXT NOT NULL
            )
            """.trimIndent(),
        )

        val defaultKeywordType = KeywordType.EXACT_MATCH.name
        connection.execSQL(
            """
            ALTER TABLE `blocked_keywords`
            ADD COLUMN `keywordType` TEXT NOT NULL DEFAULT '$defaultKeywordType'
            """.trimIndent(),
        )
    }
}

private val migration3To4 = object : Migration(3, 4) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `blocked_topics` (
                `topicId` TEXT NOT NULL PRIMARY KEY,
                `topicName` TEXT NOT NULL,
                `addedTime` INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }
}

private val migration4To5 = object : Migration(4, 5) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `${BlockedFeedRecord.TABLE_NAME}` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `title` TEXT NOT NULL,
                `questionId` INTEGER,
                `authorName` TEXT,
                `authorId` TEXT,
                `url` TEXT,
                `content` TEXT,
                `blockedReason` TEXT NOT NULL,
                `navDestinationJson` TEXT,
                `feedJson` TEXT,
                `blockedTime` INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }
}

private val migration5To6 = object : Migration(5, 6) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `${ContentOpenEvent.TABLE_NAME}` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `contentType` TEXT NOT NULL,
                `contentId` TEXT NOT NULL,
                `questionId` INTEGER,
                `openFrom` TEXT NOT NULL,
                `openedAt` INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        connection.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_${ContentOpenEvent.TABLE_NAME}_contentType_contentId`
            ON `${ContentOpenEvent.TABLE_NAME}` (`contentType`, `contentId`)
            """.trimIndent(),
        )
        connection.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_${ContentOpenEvent.TABLE_NAME}_openedAt`
            ON `${ContentOpenEvent.TABLE_NAME}` (`openedAt`)
            """.trimIndent(),
        )
    }
}

private val migration6To7 = object : Migration(6, 7) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `${BlockedQuestionAuthor.TABLE_NAME}` (
                `userId` TEXT NOT NULL PRIMARY KEY,
                `userName` TEXT NOT NULL,
                `urlToken` TEXT,
                `avatarUrl` TEXT,
                `createdTime` INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }
}

private val migration7To8 = object : Migration(7, 8) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `blocked_keywords` ADD COLUMN `scope` TEXT NOT NULL DEFAULT 'ALL'")
    }
}

fun buildContentFilterDatabase(
    builder: Builder<RoomContentFilterDatabase>,
): RoomContentFilterDatabase = builder
    .addMigrations(migration2To3, migration3To4, migration4To5, migration5To6, migration6To7, migration7To8)
    .applyPlatformDriver()
    .setQueryCoroutineContext(Dispatchers.Default)
    .build()
