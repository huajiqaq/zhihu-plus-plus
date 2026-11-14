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

import com.github.zly2006.zhihu.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

fun interface KeywordSemanticMatcher {
    suspend fun checkBlockedPhrases(
        text: String,
        blockedPhrases: List<String>,
        threshold: Double,
    ): List<Pair<String, Double>>
}

class BlockedKeywordService(
    private val keywordDao: BlockedKeywordDao,
    private val recordDao: BlockedContentRecordDao,
    private val semanticMatcher: KeywordSemanticMatcher?,
) {
    suspend fun checkNLPBlockingWithWeight(
        title: String,
        excerpt: String?,
        content: String?,
        threshold: Double = 0.8,
    ): Pair<Boolean, List<MatchedKeywordInfo>> {
        if (title.isBlank()) return Pair(false, emptyList())

        val nlpKeywords = keywordDao
            .getAllKeywords()
            .filter { it.getKeywordTypeEnum() == KeywordType.NLP_SEMANTIC }
        if (nlpKeywords.isEmpty()) return Pair(false, emptyList())

        val phrases = nlpKeywords.map { it.keyword }

        val weightedText = buildString {
            append(title)
            append(" ")
            if (!excerpt.isNullOrBlank()) {
                append(excerpt)
                append(" ")
            }
            if (!content.isNullOrBlank()) {
                append(content)
                append(" ")
            }
        }

        val matches = semanticMatcher?.checkBlockedPhrases(weightedText, phrases, threshold)
            ?: emptyList()

        val matchedInfos = matches.map { (phrase, similarity) ->
            MatchedKeywordInfo(phrase, similarity)
        }

        return Pair(matches.isNotEmpty(), matchedInfos)
    }

    suspend fun recordBlockedContent(
        contentId: String,
        contentType: String,
        title: String,
        excerpt: String?,
        authorName: String?,
        authorId: String?,
        matchedKeywords: List<MatchedKeywordInfo>,
    ) {
        try {
            val top3Matches = matchedKeywords.sortedByDescending { it.similarity }.take(3)
            val record = BlockedContentRecord(
                contentId = contentId,
                contentType = contentType,
                title = title,
                excerpt = excerpt ?: "",
                authorName = authorName,
                authorId = authorId,
                blockReason = "NLP语义匹配",
                matchedKeywords = Json.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(MatchedKeywordInfo.serializer()),
                    top3Matches,
                ),
            )
            recordDao.insertRecord(record)
            recordDao.maintainRecordLimit()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("BlockedKeywordService", "Failed to save blocked content record", e)
        }
    }
}
