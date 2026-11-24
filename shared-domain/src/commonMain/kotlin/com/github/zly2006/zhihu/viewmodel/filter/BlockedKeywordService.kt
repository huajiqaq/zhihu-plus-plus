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

import com.fleeksoft.ksoup.Ksoup
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
        content: FilterableContent,
        threshold: Double = 0.8,
    ): Pair<Boolean, List<MatchedKeywordInfo>> {
        val keywordsByScope = keywordDao
            .getAllKeywords()
            .filter { it.getKeywordTypeEnum() == KeywordType.NLP_SEMANTIC }
            .groupBy { it.getKeywordScopeEnum() }
        val matchedInfos = mutableListOf<MatchedKeywordInfo>()
        for ((scope, keywords) in keywordsByScope) {
            // ALL 保留旧的空标题门槛和标题、摘要、纯文本正文合并方式。
            if (scope == KeywordScope.ALL && content.title.isBlank()) continue
            val texts = content.keywordTexts(scope).mapIndexed { index, text ->
                if (scope != KeywordScope.ALL || index == 2) text?.let { Ksoup.parse(it).text() } else text
            }
            if (texts.all { it.isNullOrBlank() }) continue
            val weightedText = buildString {
                texts.forEachIndexed { index, text ->
                    if (!text.isNullOrBlank() || (scope == KeywordScope.ALL && index == 0)) {
                        append(text)
                        append(" ")
                    }
                }
            }
            semanticMatcher
                ?.checkBlockedPhrases(weightedText, keywords.map { it.keyword }, threshold)
                ?.forEach { (phrase, similarity) -> matchedInfos.add(MatchedKeywordInfo(phrase, similarity)) }
        }
        return Pair(matchedInfos.isNotEmpty(), matchedInfos)
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
