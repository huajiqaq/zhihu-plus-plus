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

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = ContentViewRecord.TABLE_NAME, primaryKeys = ["id"])
internal class ViewRow(
    @Embedded val value: ContentViewRecord,
)

@Entity(tableName = BlockedUser.TABLE_NAME, primaryKeys = ["userId"])
internal class UserRow(
    @Embedded val value: BlockedUser,
)

@Entity(tableName = BlockedQuestionAuthor.TABLE_NAME, primaryKeys = ["userId"])
internal class QuestionAuthorRow(
    @Embedded val value: BlockedQuestionAuthor,
)

@Entity(tableName = "blocked_topics", primaryKeys = ["topicId"])
internal class TopicRow(
    @Embedded val value: BlockedTopic,
)

/** 自增键和列默认值由 Room 实体持有；DAO 直接读写同列名的公共模型。 */
@Entity(tableName = BlockedKeyword.TABLE_NAME)
internal class KeywordRow(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val keyword: String,
    val keywordType: String,
    val caseSensitive: Boolean,
    val isRegex: Boolean,
    val createdTime: Long,
    @ColumnInfo(defaultValue = "'ALL'") val scope: String,
)

@Entity(tableName = BlockedContentRecord.TABLE_NAME)
internal class BlockedContentRow(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val contentId: String,
    val contentType: String,
    val title: String,
    val excerpt: String?,
    val authorName: String?,
    val authorId: String?,
    val blockedTime: Long,
    val blockReason: String,
    val matchedKeywords: String,
)

@Entity(tableName = BlockedFeedRecord.TABLE_NAME)
internal class BlockedFeedRow(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val title: String,
    val questionId: Long?,
    val authorName: String?,
    val authorId: String?,
    val url: String?,
    val content: String?,
    val blockedReason: String,
    val navDestinationJson: String?,
    val feedJson: String?,
    val blockedTime: Long,
)

@Entity(tableName = ContentOpenEvent.TABLE_NAME, indices = [Index(value = ["contentType", "contentId"]), Index(value = ["openedAt"])])
internal class OpenEventRow(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val contentType: String,
    val contentId: String,
    val questionId: Long?,
    val openFrom: String,
    val openedAt: Long,
)
