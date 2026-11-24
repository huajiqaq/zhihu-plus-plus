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

import com.fleeksoft.ksoup.Ksoup
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.navigation.resolveContent
import com.github.zly2006.zhihu.util.jsonObject
import io.ktor.client.HttpClient
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.util.network.UnresolvedAddressException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.jsonPrimitive

private const val DAILY_PRIMARY_API_BASE = "https://news-at.zhihu.com/api/4/stories"

/**
 * 知乎日报主域名在部分海外网络无法解析；备用域名仅用于域名解析失败。
 * 参见 https://github.com/zly2006/zhihu-plus-plus/issues/417。
 */
private const val DAILY_FALLBACK_API_BASE = "https://daily.zhihu.com/api/4/stories"

suspend fun HttpClient.fetchLatestDailyStories(): DailyStoriesResponse =
    fetchDailyStories("/latest")

suspend fun HttpClient.fetchDailyStoriesBefore(date: String): DailyStoriesResponse =
    fetchDailyStories("/before/$date")

/**
 * 日报条目的 url 是报道网页，不是原文。[日报样本](https://daily.zhihu.com/api/7/story/9791925)
 * 的 originUrl 指向回答，而 view-more 只指向所属问题；正文中的普通引用不算来源。
 */
suspend fun HttpClient.fetchDailyStoryDestination(story: DailyStory): NavDestination? {
    val response = get("https://daily.zhihu.com/api/7/story/${story.id}") { expectSuccess = true }.jsonObject()
    val body = response["body"]?.jsonPrimitive?.content ?: return null
    val doc = Ksoup.parse(body)
    var question: Question? = null
    for (link in doc.select("a.originUrl[href]") + doc.select("div.view-more a[href]")) {
        when (val source = resolveContent(link.attr("href"))) {
            is Article -> if (source.id > 0) return source
            is Question -> if (question == null && source.questionId > 0) question = source
            else -> Unit
        }
    }
    return question
}

private suspend fun HttpClient.fetchDailyStories(path: String): DailyStoriesResponse = ZhihuJson.decodeJson(
    try {
        get("$DAILY_PRIMARY_API_BASE$path").jsonObject()
    } catch (e: Exception) {
        if (e is CancellationException || !e.isHostResolutionFailure()) {
            throw e
        }
        // 仅域名解析失败可切换备用域名，不能扩展为任意网络错误的重试策略。
        get("$DAILY_FALLBACK_API_BASE$path").jsonObject()
    },
)

private fun Throwable.isHostResolutionFailure(): Boolean =
    this is UnresolvedAddressException ||
        this::class.simpleName == "UnknownHostException" ||
        message?.contains("Unable to resolve host", ignoreCase = true) == true ||
        message?.contains("No address associated with hostname", ignoreCase = true) == true ||
        message?.contains("Name or service not known", ignoreCase = true) == true ||
        message?.contains("nodename nor servname provided", ignoreCase = true) == true ||
        generateSequence(cause) { it.cause }.any {
            it is UnresolvedAddressException ||
                it::class.simpleName == "UnknownHostException"
        }
