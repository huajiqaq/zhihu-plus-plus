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

package com.github.zly2006.zhihu.test

import com.github.zly2006.zhihu.data.CommonFeed
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.Feed
import com.github.zly2006.zhihu.data.FeedDisplayItem
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.data.toFeedDisplayItemNavDestinationJson
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import io.ktor.http.HttpMethod
import kotlinx.serialization.encodeToString
import com.github.zly2006.zhihu.data.Person as FeedPerson

const val QUESTION_FIXTURE_ID = 123456789L

/**
 * Question 页面测试使用的模型夹具。
 *
 * 保持类型安全：测试只覆盖需要变化的字段，Mock 路由内部统一序列化，
 * 避免在测试里手写大段 JSON 导致必填字段漂移。
 */
fun offlineQuestion(
    questionId: Long = QUESTION_FIXTURE_ID,
    detail: String = "<p>离线问题详情用于 QuestionScreen instrumented test。</p>",
    answerCount: Int = 12,
    visitCount: Int = 345,
    commentCount: Int = 7,
    followerCount: Int = 89,
    isFollowing: Boolean = false,
    title: String = "离线问题标题",
): DataHolder.Question = DataHolder.Question(
    type = "question",
    id = questionId,
    title = title,
    questionType = "normal",
    created = 1_713_456_789L,
    updatedTime = 1_713_456_999L,
    url = "https://www.zhihu.com/question/$questionId",
    answerCount = answerCount,
    visitCount = visitCount,
    commentCount = commentCount,
    followerCount = followerCount,
    detail = detail,
    relationship = DataHolder.QuestionRelationship(isFollowing = isFollowing),
    topics = emptyList(),
    author = offlineQuestionAuthor(),
    voteupCount = 0,
)

fun offlineQuestionAuthor(
    id: String = "question-author-id",
    name: String = "离线提问者",
    urlToken: String = "question-author-token",
): DataHolder.Author = DataHolder.Author(
    avatarUrl = "",
    gender = 0,
    headline = "离线提问者简介",
    id = id,
    isAdvertiser = false,
    isOrg = false,
    name = name,
    type = "people",
    url = "https://www.zhihu.com/people/$urlToken",
    urlToken = urlToken,
    userType = "people",
)

fun offlineAnswerFeed(
    id: Long,
    authorId: String = "answer-author-$id",
    authorName: String = "离线作者 $id",
    excerpt: String = "这是第 $id 条离线回答摘要。",
    voteupCount: Int = 10,
    commentCount: Int = 1,
): Feed = CommonFeed(
    id = "answer-feed-$id",
    target = Feed.AnswerTarget(
        id = id,
        url = "https://www.zhihu.com/answer/$id",
        author = FeedPerson(
            id = authorId,
            url = "https://www.zhihu.com/people/$authorId",
            userType = "people",
            urlToken = "$authorId-token",
            name = authorName,
            headline = "$authorName 的离线签名",
            avatarUrl = "https://example.invalid/avatar/$authorId.png",
        ),
        voteupCount = voteupCount,
        commentCount = commentCount,
        question = Feed.QuestionTarget(
            id = QUESTION_FIXTURE_ID,
            _title = "离线问题标题",
            url = "https://www.zhihu.com/question/$QUESTION_FIXTURE_ID",
            type = "question",
        ),
        excerpt = excerpt,
    ),
)

fun offlineQuestionDisplayItems(count: Int): List<FeedDisplayItem> = List(count) { index ->
    val id = index + 1L
    FeedDisplayItem(
        title = "离线回答条目 $id",
        summary = "这是第 $id 条离线回答摘要。",
        details = "离线作者 $id · 赞同 ${id * 3}",
        feed = null,
        navDestinationJson = Article(
            type = ArticleType.Answer,
            id = 7000L + id,
        ).toFeedDisplayItemNavDestinationJson(),
        authorName = "离线作者 $id",
    )
}

fun mockQuestionDetail(
    questionId: Long = QUESTION_FIXTURE_ID,
    detail: String = "<p>离线问题详情用于 QuestionScreen instrumented test。</p>",
    answerCount: Int = 12,
) {
    ZhihuMockApi.mockJsonPrefix(
        method = HttpMethod.Get,
        urlPrefix = "https://www.zhihu.com/api/v4/questions/$questionId?",
        body = ZhihuJson.json.encodeToString(
            offlineQuestion(
                questionId = questionId,
                detail = detail,
                answerCount = answerCount,
            ),
        ),
    )
}

fun mockQuestionFollowActions(questionId: Long = QUESTION_FIXTURE_ID) {
    val url = "https://www.zhihu.com/api/v4/questions/$questionId/followers"
    ZhihuMockApi.mockJson(method = HttpMethod.Post, url = url, body = "{}")
    ZhihuMockApi.mockJson(method = HttpMethod.Delete, url = url, body = "{}")
}

/**
 * 注册问题回答分页响应。使用 List<Feed> 的多态序列化，保留每项的 type 判别字段。
 */
fun mockQuestionFeedPage(
    questionId: Long = QUESTION_FIXTURE_ID,
    feeds: List<Feed>,
    isEnd: Boolean = true,
) {
    val body =
        """
        {
          "data": ${ZhihuJson.json.encodeToString<List<Feed>>(feeds)},
          "paging": {
            "is_end": $isEnd,
            "is_start": true,
            "totals": ${feeds.size},
            "next": ""
          }
        }
        """.trimIndent()
    ZhihuMockApi.mockJsonPrefix(
        method = HttpMethod.Get,
        urlPrefix = "https://www.zhihu.com/api/v4/questions/$questionId/feeds?",
        body = body,
    )
}
