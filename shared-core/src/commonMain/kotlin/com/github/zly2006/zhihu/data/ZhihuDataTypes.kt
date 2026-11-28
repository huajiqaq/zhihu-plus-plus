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

import com.github.zly2006.zhihu.account.DEFAULT_ZHIHU_USER_AGENT
import com.github.zly2006.zhihu.account.ZhihuAccountProfileSnapshot
import com.github.zly2006.zhihu.account.ZhihuAccountSession
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.Person
import com.github.zly2006.zhihu.navigation.Pin
import com.github.zly2006.zhihu.navigation.Question
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

const val ZHIHU_ME_URL = "https://www.zhihu.com/api/v4/me"

@Serializable
data class ZhihuAccountProfile(
    val id: String = "",
    val name: String = "",
    val urlToken: String? = null,
    val userType: String = "",
    val avatarUrl: String? = null,
)

fun <T : HttpClientEngineConfig> HttpClientConfig<T>.installZhihuCommonClientConfig(
    cookies: MutableMap<String, String>,
    userAgent: String,
    onCookieChanged: () -> Unit = {},
    enableHttpCache: Boolean = false,
    enableCookies: Boolean = true,
    loggedIn: () -> Boolean? = { null },
    onFailure: suspend (HttpFailureRecord) -> Unit = {},
    cookieLock: ReentrantLock = reentrantLock(),
) {
    installHttpFailureObserver(loggedIn, onFailure)
    if (enableHttpCache) {
        install(HttpCache)
    }
    if (enableCookies) {
        install(HttpCookies) {
            storage = ZhihuCookieStorage(cookies, onCookieChanged, cookieLock)
        }
    }
    install(ContentNegotiation) {
        json(ZhihuJson.json)
    }
    install(UserAgent) {
        agent = userAgent
    }
}

suspend fun fetchVerifiedZhihuAccount(client: HttpClient): JsonObject? {
    val response = client.get(ZHIHU_ME_URL)
    if (response.status != HttpStatusCode.OK) {
        return null
    }
    return response.body<JsonObject>()
}

suspend fun fetchVerifiedZhihuSession(
    client: HttpClient,
    cookies: Map<String, String>,
    userAgent: String = DEFAULT_ZHIHU_USER_AGENT,
): ZhihuAccountSession? {
    val account = fetchVerifiedZhihuAccount(client) ?: return null
    val profile = ZhihuJson.decodeJson<ZhihuAccountProfile>(account)
    return ZhihuAccountSession(
        login = true,
        username = profile.name,
        cookies = cookies.toMutableMap(),
        userAgent = userAgent,
        profile = ZhihuAccountProfileSnapshot(
            id = profile.id,
            name = profile.name,
            urlToken = profile.urlToken,
            userType = profile.userType,
            avatarUrl = profile.avatarUrl,
        ),
        self = account,
    )
}

private val feedNavigationJson = Json {
    ignoreUnknownKeys = true
}

/** 作者行只使用真实 feed 身份，没有作者信息的历史条目不生成作者页。 */
val FeedDisplayItem.authorDestination: Person?
    get() {
        val author = feed?.target?.author ?: return null
        val urlToken = author.urlToken.orEmpty()
        if (author.id.isBlank() && urlToken.isBlank()) return null
        return Person(
            id = author.id.ifBlank { Person.EMPTY_ID },
            urlToken = urlToken,
            name = feed.plainSearchText(author.name),
        )
    }

val FeedDisplayItem.navDestination: NavDestination?
    get() = destination ?: feed?.navDestination

internal val Feed.navDestination: NavDestination?
    get() = target?.toNavDestination { plainSearchText(it) }

/** 保存导航到历史或可恢复 UI 状态时才编码；运行时卡片直接持有导航值。 */
fun NavDestination.encodeStoredNavDestination(): String = feedNavigationJson.encodeToString<NavDestination>(this)

/** 旧版持久化路由可能已改名或变更字段，解码失败时按不可打开处理。 */
fun decodeFeedNavDestinationOrNull(json: String): NavDestination? =
    runCatching { feedNavigationJson.decodeFromString<NavDestination>(json) }.getOrNull()

val Feed.Target.navDestination: NavDestination?
    get() = toNavDestination { it }

private inline fun Feed.Target.toNavDestination(plainText: (String) -> String): NavDestination? = when (this) {
    is Feed.AnswerTarget -> Article(
        title = plainText(question.title),
        type = ArticleType.Answer,
        id = id,
        authorName = author?.name?.let(plainText) ?: "loading...",
        authorBio = author?.headline ?: "",
        avatarSrc = author?.avatarUrl,
        excerpt = excerpt?.let(plainText),
        authorId = author?.id.orEmpty(),
        authorUrlToken = author?.urlToken.orEmpty(),
        questionId = question.id,
    )

    is Feed.ArticleTarget -> Article(
        title = plainText(title),
        type = ArticleType.Article,
        id = id,
        authorName = plainText(author.name),
        authorBio = author.headline,
        avatarSrc = author.avatarUrl,
        excerpt = plainText(excerpt),
        authorId = author.id,
        authorUrlToken = author.urlToken.orEmpty(),
    )

    is Feed.PinTarget -> Pin(id = id, authorName = plainText(author.name))

    is Feed.QuestionTarget -> Question(questionId = id, title = plainText(title))

    is Feed.VideoTarget -> null
}

suspend fun fetchHighestQualityZhihuVideoUrl(
    httpClient: HttpClient,
    videoId: String,
    contentId: String,
    contentType: String = "answer",
    xsrfToken: String? = null,
    configureRequest: HttpRequestBuilder.() -> Unit = {},
): String? {
    val response = httpClient.post("https://www.zhihu.com/api/v4/video/play_info?r=$videoId") {
        contentType(ContentType.Application.Json)
        xsrfToken?.let { header("x-xsrftoken", it) }
        header("x-app-za", "OS=webplayer")
        header("x-referer", "")
        setBody(
            """{"content_id":"$contentId","content_type_str":"$contentType","video_id":"$videoId","scene_code":"answer_detail_web","is_only_video":true}""",
        )
        configureRequest()
    }

    return selectHighestQualityZhihuVideoUrl(
        ZhihuJson.json
            .parseToJsonElement(response.bodyAsText())
            .jsonObject,
    )
}

fun selectHighestQualityZhihuVideoUrl(jsonResponse: JsonObject): String? {
    val mp4List = jsonResponse["video_play"]
        ?.jsonObject
        ?.get("playlist")
        ?.jsonObject
        ?.get("mp4")
        ?.jsonArray

    var bestVideo: JsonObject? = null
    var maxBitrate = -1

    mp4List?.forEach { videoElement ->
        val video = videoElement.jsonObject
        val bitrate = video["bitrate"]?.jsonPrimitive?.intOrNull ?: 0
        if (bitrate > maxBitrate) {
            maxBitrate = bitrate
            bestVideo = video
        }
    }

    return bestVideo
        ?.get("url")
        ?.jsonArray
        ?.firstOrNull()
        ?.jsonPrimitive
        ?.content
}

object ZhihuJson {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    @Suppress("FunctionName")
    fun snakeCaseToCamelCase(snakeCase: String): String = snakeCase
        .split("_")
        .joinToString("") { it.replaceFirstChar { char -> char.uppercase() } }
        .replaceFirstChar { it.lowercase() }

    fun snakeCaseToCamelCase(json: JsonElement): JsonElement = when (json) {
        is JsonObject -> buildJsonObject {
            for ((key, value) in json) {
                // cookie/cookies 的子键是服务端签发的动态凭据名，不是模型字段，必须逐字保留。
                put(
                    snakeCaseToCamelCase(key),
                    if (key == "cookie" || key == "cookies") value else snakeCaseToCamelCase(value),
                )
            }
        }
        is JsonArray -> buildJsonArray {
            for (item in json) {
                add(snakeCaseToCamelCase(item))
            }
        }
        else -> json
    }

    inline fun <reified T> decodeJson(json: JsonElement): T =
        this.json.decodeFromJsonElement(snakeCaseToCamelCase(json))

    fun <T> decodeJson(serializer: KSerializer<T>, json: JsonElement): T =
        this.json.decodeFromJsonElement(serializer, snakeCaseToCamelCase(json))
}
