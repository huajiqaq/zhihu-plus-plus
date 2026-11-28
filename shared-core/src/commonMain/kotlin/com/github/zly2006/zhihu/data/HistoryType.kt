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

package com.github.zly2006.zhihu.data

import com.github.zly2006.zhihu.i18n.CoreStrings
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.Person
import com.github.zly2006.zhihu.navigation.Pin
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.navigation.Topic
import com.github.zly2006.zhihu.navigation.Video

object HistoryType {
    const val ANSWER = "回答"
    const val ARTICLE = "文章"
    const val PIN = "想法"
    const val QUESTION = "问题"
    const val VIDEO = "视频"
    const val PERSON = "用户"
    const val TOPIC = "话题"
    const val COLUMN = "专栏"
    const val BOOK = "电子书"
    const val ROUND_TABLE = "圆桌"
    const val SPECIAL = "专题"
    const val COLLECTION = "收藏夹"
    const val OTHER = "其他"

    val filters = arrayOf(ANSWER, ARTICLE, PIN, QUESTION, VIDEO, PERSON, TOPIC, COLUMN, BOOK, ROUND_TABLE, SPECIAL, COLLECTION, OTHER)

    fun from(destination: NavDestination): String = when (destination) {
        is Article -> when (destination.type) {
            ArticleType.Answer -> ANSWER
            ArticleType.Article -> ARTICLE
        }
        is Pin -> PIN
        is Question -> QUESTION
        is Video -> VIDEO
        is Person -> PERSON
        is Topic -> TOPIC
        else -> OTHER
    }

    fun fromContentType(contentType: String?): String = when (contentType?.lowercase()) {
        "answer" -> ANSWER
        "article" -> ARTICLE
        "pin" -> PIN
        "question" -> QUESTION
        "video", "zvideo" -> VIDEO
        "people", "person", "profile" -> PERSON
        "topic" -> TOPIC
        "column", "paid_column" -> COLUMN
        "ebook" -> BOOK
        "round_table_history", "roundtable" -> ROUND_TABLE
        "special_topic_history", "special" -> SPECIAL
        "collection" -> COLLECTION
        else -> OTHER
    }

    /** 筛选与历史记录使用原类型键，显示名称在绘制时解析。 */
    fun displayName(type: String?): String = when (type) {
        ANSWER -> CoreStrings.typeAnswer
        ARTICLE -> CoreStrings.typeArticle
        PIN -> CoreStrings.typePin
        QUESTION -> CoreStrings.typeQuestion
        VIDEO -> CoreStrings.typeVideo
        PERSON -> CoreStrings.typePerson
        TOPIC -> CoreStrings.typeTopic
        COLUMN -> CoreStrings.typeColumn
        BOOK -> CoreStrings.typeEbook
        ROUND_TABLE -> CoreStrings.typeRoundtable
        SPECIAL -> CoreStrings.typeSpecial
        COLLECTION -> CoreStrings.typeCollection
        else -> CoreStrings.typeOther
    }

    fun badgeType(rawType: String?): String = rawType?.takeIf { it in filters } ?: OTHER
}
