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

import com.github.zly2006.zhihu.i18n.DomainStrings
import kotlin.time.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BlocklistBackup(
    val version: Int = 4,
    val exportTime: Long = Clock.System.now().toEpochMilliseconds(),
    val keywords: List<KeywordBackup> = emptyList(),
    val nlpKeywords: List<NlpKeywordBackup> = emptyList(),
    val users: List<UserBackup> = emptyList(),
    val questionAuthors: List<UserBackup> = emptyList(),
    val topics: List<TopicBackup> = emptyList(),
)

@Serializable
data class KeywordBackup(
    val keyword: String,
    val caseSensitive: Boolean = false,
    val isRegex: Boolean = false,
    val scope: String = KeywordScope.ALL.name,
)

@Serializable
data class NlpKeywordBackup(
    val keyword: String,
    val scope: String = KeywordScope.ALL.name,
)

@Serializable
data class UserBackup(
    val userId: String,
    val userName: String,
    val urlToken: String = "",
    val avatarUrl: String = "",
)

@Serializable
data class TopicBackup(
    val topicId: String,
    val topicName: String,
)

private val blocklistBackupJson = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
}

private fun List<BlockedKeyword>.exactBackups() =
    filter { it.getKeywordTypeEnum() == KeywordType.EXACT_MATCH }.map { KeywordBackup(it.keyword, it.caseSensitive, it.isRegex, it.scope) }

private fun List<BlockedKeyword>.nlpBackups() =
    filter { it.getKeywordTypeEnum() == KeywordType.NLP_SEMANTIC }.map { NlpKeywordBackup(it.keyword, it.scope) }

suspend fun encodeBlocklistBackup(
    keywordDao: BlockedKeywordDao,
    userDao: BlockedUserDao,
    questionAuthorDao: BlockedQuestionAuthorDao,
    topicDao: BlockedTopicDao,
): String {
    val allKeywords = keywordDao.getAllKeywords()
    val users = userDao.getAllUsers()
    val questionAuthors = questionAuthorDao.getAllUsers()
    val topics = topicDao.getAllTopics()

    val backup = BlocklistBackup(
        keywords = allKeywords.exactBackups(),
        nlpKeywords = allKeywords.nlpBackups(),
        users = users.map { UserBackup(it.userId, it.userName, it.urlToken ?: "", it.avatarUrl ?: "") },
        questionAuthors = questionAuthors.map { UserBackup(it.userId, it.userName, it.urlToken ?: "", it.avatarUrl ?: "") },
        topics = topics.map { TopicBackup(it.topicId, it.topicName) },
    )

    return blocklistBackupJson.encodeToString(BlocklistBackup.serializer(), backup)
}

suspend fun importBlocklistBackupFromJsonText(
    keywordDao: BlockedKeywordDao,
    userDao: BlockedUserDao,
    questionAuthorDao: BlockedQuestionAuthorDao,
    topicDao: BlockedTopicDao,
    text: String,
): String {
    val backup = blocklistBackupJson.decodeFromString(BlocklistBackup.serializer(), text)
    // 在写入任何分组前验证范围，未知范围不能被悄悄扩大为 ALL。
    (backup.keywords.map { it.scope } + backup.nlpKeywords.map { it.scope }).forEach { KeywordScope.valueOf(it) }

    val stored = if (backup.keywords.isEmpty() && backup.nlpKeywords.isEmpty()) emptyList() else keywordDao.getAllKeywords()
    val exact = stored.exactBackups().toMutableSet()
    val nlp = stored.nlpBackups().toMutableSet()
    backup.keywords.forEach { kw ->
        if (kw.keyword.isBlank() || !exact.add(kw)) return@forEach
        keywordDao.insertKeyword(
            BlockedKeyword(
                keyword = kw.keyword,
                keywordType = KeywordType.EXACT_MATCH.name,
                caseSensitive = kw.caseSensitive,
                isRegex = kw.isRegex,
                scope = kw.scope,
            ),
        )
    }
    backup.nlpKeywords.forEach { kw ->
        if (kw.keyword.isBlank() || !nlp.add(kw)) return@forEach
        keywordDao.insertKeyword(
            BlockedKeyword(keyword = kw.keyword, keywordType = KeywordType.NLP_SEMANTIC.name, scope = kw.scope),
        )
    }
    backup.users.filter { it.userId.isNotBlank() }.forEach { user ->
        userDao.insertUser(
            BlockedUser(
                userId = user.userId,
                userName = user.userName,
                urlToken = user.urlToken.takeIf { it.isNotBlank() },
                avatarUrl = user.avatarUrl.takeIf { it.isNotBlank() },
            ),
        )
    }
    backup.questionAuthors.filter { it.userId.isNotBlank() }.forEach { user ->
        questionAuthorDao.insertUser(
            BlockedQuestionAuthor(
                userId = user.userId,
                userName = user.userName,
                urlToken = user.urlToken.takeIf { it.isNotBlank() },
                avatarUrl = user.avatarUrl.takeIf { it.isNotBlank() },
            ),
        )
    }
    backup.topics.filter { it.topicId.isNotBlank() }.forEach { topic ->
        topicDao.insertTopic(BlockedTopic(topicId = topic.topicId, topicName = topic.topicName))
    }

    return DomainStrings.blocklistSummary(backup.keywords.size, backup.nlpKeywords.size, backup.users.size, backup.questionAuthors.size, backup.topics.size)
}
