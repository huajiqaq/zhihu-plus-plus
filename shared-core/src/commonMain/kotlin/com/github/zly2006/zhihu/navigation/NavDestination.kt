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

package com.github.zly2006.zhihu.navigation

import io.ktor.http.Url
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.nav.core.NavKey

@Serializable
sealed interface NavDestination : NavKey

/** 底栏和侧栏的可配置目标；多数选择 [MainTabs] 内的标签，[OnlineHistory] 打开普通历史页面。 */
interface TopLevelDestination {
    val name: String
}

/** 主栈的主壳根；深链、剪贴板、设置和持久化历史解析出的顶层标签由主壳选择，不作为独立页面压栈。 */
@Serializable
data object MainTabs : NavDestination

/** 详情阅读栏的内部起始目的地。 */
@Serializable
data object EmptyDetail : NavDestination

@Serializable
data object Login : NavDestination

/** 通用网页独立入栈；当前网址和站内浏览历史由该条目的浏览器持有。 */
@Serializable
data class WebPage(
    val url: String,
) : NavDestination

fun isWebPageUrl(url: String): Boolean {
    if (!url.startsWith("https://", ignoreCase = true) && !url.startsWith("http://", ignoreCase = true)) return false
    val authority = url.substringAfter("://").takeWhile { it != '/' && it != '?' && it != '#' }
    if (authority.isEmpty() || authority.any { it.isWhitespace() || it == '\\' } || url.any { it.isISOControl() }) return false
    return runCatching { Url(url).host.isNotBlank() }.getOrDefault(false)
}

/** 首页标签目标，用于深链和设置；通过 [MainTabs] 选择，不注册为独立页面。 */
@Serializable
data object Home : TopLevelDestination {
    override val name: String
        get() = "Home"
}

/** [Follow] 由关注页承载“推荐”“动态”和“想法”三个内层分页，保留上次选择。 */
@Serializable
data object Follow : TopLevelDestination {
    override val name: String
        get() = "Follow"
}

/** 主分页器的顶层标签目标，不作为独立页面压栈。 */
@Serializable
data object HotList : TopLevelDestination {
    override val name: String
        get() = "HotList"
}

/** 本地浏览历史的独立页面；底栏使用 [OnlineHistory]，此路由不加入主分页标签列表。 */
@Serializable
data object History : NavDestination, TopLevelDestination {
    override val name: String
        get() = "History"
}

/** 可配置底栏和侧栏中的在线历史入口，打开 [OnlineHistoryRoute]，不加入主 pager。 */
@Serializable
data object OnlineHistory : TopLevelDestination {
    override val name: String
        get() = "OnlineHistory"
}

/** 在线浏览历史页面；底栏、侧栏、启动页及账号快捷入口共用这条普通 route。 */
@Serializable
data object OnlineHistoryRoute : NavDestination

/** 主分页器的顶层标签目标，不作为独立页面压栈。 */
@Serializable
data object MyCollections : TopLevelDestination {
    override val name: String
        get() = "MyCollections"
}

/** 账号页的主分页标签目标，不作为独立页面压栈。 */
@Serializable
data object Account : TopLevelDestination {
    override val name: String
        get() = "Account"

    @Serializable
    data class AppearanceSettings(
        val setting: String = "",
    ) : NavDestination

    @Serializable
    data class ReadingSettings(
        val setting: String = "",
    ) : NavDestination

    @Serializable
    data class NavigationSettings(
        val setting: String = "",
    ) : NavDestination

    @Serializable
    data class PageTurnSettings(
        val setting: String = "",
    ) : NavDestination

    @Serializable
    data class ReadingPlaybackSettings(
        val setting: String = "",
    ) : NavDestination

    @Serializable
    data class FeedSettings(
        val setting: String = "",
    ) : NavDestination

    @Serializable
    data object WebDavSync : NavDestination

    @Serializable
    data class RecommendSettings(
        val setting: String = "",
    ) : NavDestination {
        @Serializable
        data object Blocklist : NavDestination

        @Serializable
        data object BlockedFeedHistory : NavDestination
    }

    @Serializable
    data object IdentityManagement : NavDestination

    @Serializable
    data class SystemAndUpdateSettings(
        val setting: String = "",
    ) : NavDestination

    @Serializable
    data object SettingsSearch : NavDestination

    @Serializable
    data object OpenSourceLicenses : NavDestination

    /** 账号与登录维护入口；保留旧序列化名称以兼容已保存的导航状态。 */
    @Serializable
    data object DeveloperSettings : NavDestination
}

/** 主分页器的顶层标签目标，不作为独立页面压栈。 */
@Serializable
data object Daily : TopLevelDestination {
    override val name: String
        get() = "Daily"
}

@Serializable
data object Notification : NavDestination {
    @Serializable
    data class NotificationSettings(
        val setting: String = "",
    ) : NavDestination

    @Serializable
    data class Entry(
        val entryName: String,
        val title: String,
    ) : NavDestination

    @Serializable
    data object Invitations : NavDestination

    @Serializable
    data class Message(
        val peerId: String,
        val name: String = "",
        val avatarUrl: String = "",
    ) : NavDestination
}

@Serializable
data object WriteQuestion : NavDestination

@Serializable
data object WriteArticle : NavDestination

/** 官方回答入口的页码从 1 开始：邀请、推荐、人气；未指定时默认推荐。 */
@Serializable
data class WriteAnswerChooser(
    val defaultSelectedPage: Int = 2,
) : NavDestination

@Serializable
data class Search(
    val query: String = "",
    val restrictedMemberHashId: String = "",
    val restrictedMemberName: String = "",
) : NavDestination {
    val isRestrictedToMember: Boolean
        get() = restrictedMemberHashId.isNotBlank()
}

@Serializable
data class Collections(
    val userToken: String,
) : NavDestination

@Serializable
data class CollectionContent(
    val collectionId: String,
) : NavDestination

@Serializable
enum class ArticleType {
    @SerialName("article")
    Article,

    @SerialName("answer")
    Answer,
    ;

    override fun toString(): String = name.lowercase()
}

@Serializable
data class Article(
    var title: String = "loading...",
    @SerialName("article_type_1")
    val type: ArticleType,
    val id: Long,
    var authorName: String = "loading...",
    var authorBio: String = "loading...",
    var avatarSrc: String? = null,
    var excerpt: String? = null,
    val readingQueueSourceId: String? = null,
    val authorId: String = "",
    val authorUrlToken: String = "",
    val questionId: Long = 0L,
) : NavDestination {
    override fun hashCode(): Int = id.hashCode()

    override fun equals(other: Any?): Boolean = other is Article && other.id == id && other.type == type
}

@Serializable
data class CommentHolder(
    val commentId: String,
    val article: NavDestination,
) : NavDestination

@Serializable
data class SegmentCommentHolder(
    val contentId: String,
    val contentType: String,
    val segmentId: String,
    val segmentContent: String,
    val paragraphId: String,
    val startOffset: Int,
    val endOffset: Int,
) : NavDestination

@Serializable
data class Question(
    val questionId: Long,
    val title: String = "loading...",
    val readingQueueSourceId: String? = null,
) : NavDestination {
    override fun hashCode(): Int = questionId.hashCode()

    override fun equals(other: Any?): Boolean = other is Question && other.questionId == questionId
}

/** 回答编辑器入口；发布前按登录账号探测已有回答，以决定新建或更新。 */
@Serializable
data class WriteAnswer(
    val questionId: Long,
    val questionTitle: String = "",
    val questionDetail: String = "",
) : NavDestination {
    override fun hashCode(): Int = questionId.hashCode()

    override fun equals(other: Any?): Boolean = other is WriteAnswer && other.questionId == questionId
}

/**
 * 从首页发起“发想法”的编辑器页面。
 *
 * 想法不是问题下的内容，没有问题 ID；标题可选，正文或图片至少存在其一。
 */
@Serializable
data class WritePin(
    val topicName: String = "",
    val publishTopicId: String = "",
) : NavDestination

@Serializable
data class Person(
    /**
     * 32 位十六进制字符。
     */
    var id: String,
    /**
     * 用在 URL 中的可读 token。
     */
    var urlToken: String,
    val name: String = "loading...",
    val jumpTo: String = "",
) : NavDestination {
    override fun hashCode(): Int {
        if (id != EMPTY_ID) {
            // 32 位十六进制字符，通常是用户 ID。
            return id.hashCode()
        }
        return urlToken.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other is Person) {
            if (id != EMPTY_ID && other.id != EMPTY_ID) {
                return other.id == id
            }
            return other.urlToken == urlToken
        }
        return false
    }

    val userTokenOrId get() = urlToken.takeIf { it.isNotEmpty() } ?: id

    companion object {
        const val EMPTY_ID = "00000000000000000000000000000000"
    }
}

@Serializable
data class Video(
    val id: Long,
) : NavDestination

@Serializable
data class Pin(
    val id: Long,
    val authorName: String = "",
    val readingQueueSourceId: String? = null,
) : NavDestination {
    override fun hashCode(): Int = id.hashCode()

    override fun equals(other: Any?): Boolean = other is Pin && other.id == id
}

@Serializable
data class Topic(
    val id: String,
    val name: String = "",
    val section: String = "",
) : NavDestination

fun NavDestination.withReadingQueueSource(sourceId: String?): NavDestination = when (this) {
    is Article -> copy(readingQueueSourceId = sourceId)
    is Pin -> copy(readingQueueSourceId = sourceId)
    is Question -> copy(readingQueueSourceId = sourceId)
    else -> this
}

/** 主动点击才为普通链接建立网页条目；被动探测仍只使用 [resolveContent]。 */
fun resolveNavigationUrl(url: String): NavDestination? {
    resolveContent(url)?.let { return it }
    if (!isWebPageUrl(url)) return null
    val parsed = Url(url)
    val target = if (parsed.host.equals("link.zhihu.com", ignoreCase = true)) parsed.parameters["target"]?.takeIf(::isWebPageUrl) else null
    return WebPage(target ?: url)
}

fun resolveContent(url: String): NavDestination? = runCatching { resolveContent(Url(url)) }.getOrNull()

fun resolveContent(url: Url): NavDestination? {
    val segments = url.segments
    val isAnswerChooser = (
        (url.protocol.name == "http" || url.protocol.name == "https") &&
            url.host in setOf("zhihu.com", "www.zhihu.com") &&
            segments == listOf("compose_answer_tab")
    ) ||
        (
            url.protocol.name == "zhihu" &&
                (
                    (url.host == "compose_answer_tab" && url.encodedPath in setOf("", "/")) ||
                        (url.host == "write" && segments == listOf("compose_answer_tab"))
                )
        )
    if (isAnswerChooser) {
        return WriteAnswerChooser(
            defaultSelectedPage = url.parameters["default_selected_page"]
                ?.toIntOrNull()
                ?.takeIf { it in 1..3 }
                ?: 2,
        )
    }
    if (url.protocol.name == "http" || url.protocol.name == "https") {
        if (url.host == "zhihu.com" || url.host == "www.zhihu.com") {
            if (segments.size == 2 && segments[0] == "inbox") {
                return Notification.Message(
                    peerId = segments[1],
                    name = url.parameters["title"].orEmpty(),
                )
            } else if (
                segments.size == 5 &&
                segments[0] == "notifications" &&
                segments[1] == "v3" &&
                segments[2] == "timeline" &&
                segments[3] == "entry"
            ) {
                return Notification.Entry(
                    entryName = segments[4],
                    title = url.parameters["title"].orEmpty().ifBlank { "消息" },
                )
            } else if (segments.size == 4 &&
                segments[0] == "question" &&
                segments[2] == "answer"
            ) {
                val answerId = segments[3].toLong()
                return Article(type = ArticleType.Answer, id = answerId)
            } else if (segments.size == 2 &&
                segments[0] == "answer"
            ) {
                val answerId = segments[1].toLong()
                return Article(type = ArticleType.Answer, id = answerId)
            } else if (segments.size == 2 &&
                segments[0] == "question"
            ) {
                val questionId = segments[1].toLong()
                return Question(questionId)
            } else if (segments.size == 3 &&
                segments[0] == "oia" &&
                segments[1] == "articles"
            ) {
                val articleId = segments[2].toLong()
                return Article(type = ArticleType.Article, id = articleId)
            } else if (segments.size == 2 && segments[0] == "people") {
                val urlToken = segments[1]
                if (urlToken.length == 32 && urlToken.all { it in '0'..'9' || it in 'a'..'f' }) {
                    // 32 位十六进制字符，通常是用户 ID。
                    return Person(id = urlToken, urlToken = urlToken)
                } else {
                    // 可读 token。
                    return Person(id = Person.EMPTY_ID, urlToken = urlToken)
                }
            } else if (segments.size == 2 && segments[0] == "video") {
                val videoId = segments[1].toLongOrNull() ?: return null
                return Video(id = videoId) // TODO: 视频详情页待完善。
            } else if (segments.size == 2 && segments[0] == "pin") {
                val pinId = segments[1].toLongOrNull() ?: return null
                return Pin(id = pinId)
            } else if (segments.size >= 2 && segments[0] == "topic") {
                return Topic(id = segments[1], section = segments.getOrNull(2).orEmpty())
            } else if (segments.size == 3 && segments[0] == "appview") {
                val contentId = segments[2].toLongOrNull() ?: return null
                return when (segments[1]) {
                    "pin" -> Pin(id = contentId)
                    "answer" -> Article(type = ArticleType.Answer, id = contentId)
                    "p" -> Article(type = ArticleType.Article, id = contentId)
                    else -> null
                }
            } else if (segments.size == 1 &&
                segments[0] == "search"
            ) {
                val query = url.parameters["q"] ?: ""
                return Search(query)
            }
            /*
             * 尚未支持的 destination，等待后续补充对应的 NavDestination：
             * - https://www.zhihu.com/appview/roundtable -> https://www.zhihu.com/roundtable
             * - https://www.zhihu.com/appview/special -> https://www.zhihu.com/special/all
             * - https://www.zhihu.com/column/{columnToken}
             * - https://daily.zhihu.com/story/{storyId}
             * - https://www.zhihu.com/special/{specialId}
             */
        } else if (url.host == "zhuanlan.zhihu.com") {
            if (segments.size == 2 &&
                segments[0] == "p"
            ) {
                val articleId = segments[1].toLong()
                return Article(type = ArticleType.Article, id = articleId)
            }
        } else if (url.host == "link.zhihu.com") {
            val target = url.parameters["target"] ?: return null
            return runCatching { Url(target) }.getOrNull()?.let(::resolveContent)
        }
    }
    if (url.protocol.name == "zhihu") {
        if (
            url.host == "comment" &&
            segments.size == 3 &&
            segments[0] == "list"
        ) {
            val contentId = segments[2].toLongOrNull() ?: return null
            val commentId = url.parameters["anchor_comment_id"]
                ?.takeIf { id -> id.isNotEmpty() && id.all { it in '0'..'9' } }
                ?: return null
            val content = when (segments[1]) {
                "answer" -> Article(
                    type = ArticleType.Answer,
                    id = contentId,
                )

                "article" -> Article(
                    type = ArticleType.Article,
                    id = contentId,
                )

                "pin" -> Pin(id = contentId)

                "question" -> Question(questionId = contentId)

                else -> return null
            }
            return CommentHolder(commentId = commentId, article = content)
        } else if (url.host == "answers") {
            val answerId = segments[0].toLong()
            return Article(type = ArticleType.Answer, id = answerId)
        } else if (url.host == "questions") {
            val questionId = segments[0].toLong()
            return Question(questionId)
        } else if (url.host == "feed") {
            return MainTabs
        } else if (url.host == "articles") {
            val articleId = segments[0].toLong()
            return Article(type = ArticleType.Article, id = articleId)
        } else if (url.host == "search") {
            val query = url.parameters["q"] ?: ""
            return Search(query)
        } else if (url.host == "pin") {
            val pinId = segments[0].toLong()
            return Pin(id = pinId)
        } else if (url.host == "topic" || url.host == "topics") {
            val topicId = segments.firstOrNull() ?: return null
            return Topic(id = topicId, section = segments.getOrNull(1).orEmpty())
        } else if (url.host == "pin20") {
            val topicId = url.parameters["topic_id"]
                ?: url.parameters["topicId"]
                ?: segments.lastOrNull()?.takeIf { it.all(Char::isDigit) }
                ?: return null
            return Topic(id = topicId)
        }
    }
    return null
}
