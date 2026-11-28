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

package com.github.zly2006.zhihu.ui

import com.github.zly2006.zhihu.navigation.Account
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.CollectionContent
import com.github.zly2006.zhihu.navigation.Collections
import com.github.zly2006.zhihu.navigation.History
import com.github.zly2006.zhihu.navigation.Login
import com.github.zly2006.zhihu.navigation.MainTabs
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.Notification
import com.github.zly2006.zhihu.navigation.OnlineHistoryRoute
import com.github.zly2006.zhihu.navigation.Person
import com.github.zly2006.zhihu.navigation.Pin
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.navigation.Search
import com.github.zly2006.zhihu.navigation.TopLevelDestination
import com.github.zly2006.zhihu.navigation.Topic
import com.github.zly2006.zhihu.navigation.WriteAnswer
import com.github.zly2006.zhihu.navigation.WriteAnswerChooser
import com.github.zly2006.zhihu.navigation.WriteArticle
import com.github.zly2006.zhihu.navigation.WritePin
import com.github.zly2006.zhihu.navigation.WriteQuestion
import com.github.zly2006.zhihu.navigation.isDetailPaneDestination
import com.github.zly2006.zhihu.navigation.isListPaneDestination
import com.github.zly2006.zhihu.ui.subscreens.linkableSettingsDestinations
import com.github.zly2006.zhihu.viewmodel.MobileNotificationCategory
import io.ktor.http.decodeURLQueryComponent
import io.ktor.http.encodeURLParameter

/** 地址只描述公开的业务入口和可选的双栏来源；访问 ID、API 正文和本机状态不属于地址。 */
internal data class WebRoute(
    val page: NavDestination = MainTabs,
    val tab: TopLevelDestination? = null,
    val source: NavDestination? = null,
) {
    fun hash(): String? {
        val tabKey = tabPages.find { it.bottomDestination == tab }?.key
        if (page == MainTabs) return tabKey?.let { "#/tab/$it" }
        val path = page.path() ?: return null
        return "#/$path".param("tab", tabKey).param("from", source?.takeIf { canPair(it, page) }?.path())
    }

    companion object {
        fun parse(hash: String): WebRoute? = runCatching {
            require(hash.startsWith("#/") && hash.length <= 8192 && '#' !in hash.drop(1))
            val path = RoutePath(hash.drop(2))
            if (path.name.startsWith("tab/")) {
                require(path.params.isEmpty())
                WebRoute(tab = tabPages.first { "tab/${it.key}" == path.name }.bottomDestination)
            } else {
                val tab = path.params.remove("tab")?.let { key -> tabPages.first { it.key == key }.bottomDestination }
                val source = path.params.remove("from")?.let { RoutePath(it).page() }
                val page = path.page()
                require(source == null || canPair(source, page))
                WebRoute(page, tab, source)
            }
        }.getOrNull()
    }
}

private val tabPages = listOf(MainTabPage.HomePage, MainTabPage.FollowPage, MainTabPage.HotListPage, MainTabPage.DailyPage, MainTabPage.MyCollectionsPage, MainTabPage.AccountPage)

private fun canPair(source: NavDestination, page: NavDestination) =
    (source.isListPaneDestination() && page.opensInDetailPane()) || (source.isDetailPaneDestination() && page is Question)

private val fixedPages = mapOf(
    "history" to OnlineHistoryRoute,
    "history/local" to History,
    "login" to Login,
    "notifications" to Notification,
    "notifications/invitations" to Notification.Invitations,
    "settings/search" to Account.SettingsSearch,
    "settings/webdav" to Account.WebDavSync,
    "settings/blocklist" to Account.RecommendSettings.Blocklist,
    "settings/blocked-history" to Account.RecommendSettings.BlockedFeedHistory,
    "settings/identities" to Account.IdentityManagement,
    "settings/licenses" to Account.OpenSourceLicenses,
    "settings/account-tools" to Account.AccountTools,
    "write/question" to WriteQuestion,
    "write/article" to WriteArticle,
    "write/pin" to WritePin(),
)

private val settingsPages: Map<String, (String) -> NavDestination> = mapOf(
    "settings/appearance" to Account::AppearanceSettings,
    "settings/navigation" to Account::NavigationSettings,
    "settings/reading" to Account::ReadingSettings,
    "settings/page-turn" to Account::PageTurnSettings,
    "settings/playback" to Account::ReadingPlaybackSettings,
    "settings/feed" to Account::FeedSettings,
    "settings/filter" to Account::RecommendSettings,
    "settings/system" to Account::SystemAndUpdateSettings,
    "settings/notifications" to Notification::NotificationSettings,
)

private val personToken = Regex("[a-zA-Z0-9_-]{1,128}")
private val memberId = Regex("[0-9a-fA-F]{32}")
private val peopleSections = mapOf("回答" to "answers", "文章" to "articles", "动态" to "activity", "收藏" to "collections", "提问" to "questions", "想法" to "pins", "专栏" to "columns", "粉丝" to "followers", "关注" to "following", "关注订阅" to "subscriptions")
private val topicSections = setOf("unanswered", "top-answers", "newest")

private fun String.param(key: String, value: String?) = if (value.isNullOrEmpty()) this else this + (if ('?' in this) '&' else '?') + key + '=' + value.encodeURLParameter()

private fun String.publicId() = toLongOrNull()?.takeIf { it > 0 && it.toString() == this }

private fun NavDestination.path(): String? {
    fixedPages.entries.find { it.value == this }?.let { return it.key }
    val focus = when (this) {
        is Account.AppearanceSettings -> setting
        is Account.NavigationSettings -> setting
        is Account.ReadingSettings -> setting
        is Account.PageTurnSettings -> setting
        is Account.ReadingPlaybackSettings -> setting
        is Account.FeedSettings -> setting
        is Account.RecommendSettings -> setting
        is Account.SystemAndUpdateSettings -> setting
        is Notification.NotificationSettings -> setting
        else -> null
    }
    if (focus != null) {
        return settingsPages.entries
            .first { it.value(focus) == this }
            .key
            .param("focus", focus.takeIf { it.isNotEmpty() && this in linkableSettingsDestinations })
    }
    return when (this) {
        is Article -> if (id > 0) "$type/$id" else null
        is Question -> if (questionId > 0) "question/$questionId" else null
        is Pin -> if (id > 0) "pin/$id" else null
        is Person -> userTokenOrId.takeIf { it != Person.EMPTY_ID && personToken.matches(it) }?.let { "people/$it".param("section", peopleSections[jumpTo]) }
        is Topic -> id.publicId()?.let { "topic/$it".param("section", section.takeIf(topicSections::contains)) }
        is CollectionContent -> collectionId.publicId()?.let { "collection/$it" }
        is Collections -> userToken.takeIf(personToken::matches)?.let { "people/$it/collections" }
        is Search -> if (query.length <= 512 && query.none(Char::isISOControl) && (restrictedMemberHashId.isEmpty() || memberId.matches(restrictedMemberHashId))) {
            "search".param("q", query).param("member", restrictedMemberHashId)
        } else {
            null
        }
        is Notification.Entry -> if (MobileNotificationCategory.entries.any { it.entryName == entryName } || entryName == "invite") "notifications/entry/$entryName" else null
        is WriteAnswerChooser -> if (defaultSelectedPage in 1..3) "write/answers".param("page", defaultSelectedPage.toString().takeUnless { it == "2" }) else null
        is WriteAnswer -> if (questionId > 0) "write/answer/$questionId" else null
        is WritePin -> publishTopicId.publicId()?.let { "write/pin/$it" }
        /** 私信、任意网页、内部交接和外部视频均没有可公开重建的页面地址。 */
        else -> null
    }
}

private class RoutePath(
    value: String,
) {
    val name = value.substringBefore('?')
    val params = mutableMapOf<String, String>()

    init {
        if ('?' in value) {
            for (pair in value.substringAfter('?').split('&')) {
                val key = pair.substringBefore('=')
                require('=' in pair && key !in params)
                val decoded = pair.substringAfter('=').decodeURLQueryComponent(plusIsSpace = true)
                require(decoded.none { it.isISOControl() || it == '\uFFFD' })
                params[key] = decoded
            }
        }
    }

    fun page(): NavDestination {
        val segments = name.split('/')
        val id = segments.last()
        val parent = name.substringBeforeLast('/', "")
        val page = fixedPages[name] ?: settingsPages[name]?.let { create ->
            val focus = params.remove("focus")
            create(focus.orEmpty()).also { require(focus == null || it in linkableSettingsDestinations) }
        } ?: when {
            name == "search" -> Search(params.remove("q").orEmpty(), params.remove("member").orEmpty())
            name == "write/answers" -> WriteAnswerChooser(params.remove("page")?.toInt() ?: 2)
            parent == "answer" -> Article(type = ArticleType.Answer, id = requireNotNull(id.publicId()))
            parent == "article" -> Article(type = ArticleType.Article, id = requireNotNull(id.publicId()))
            parent == "question" -> Question(requireNotNull(id.publicId()))
            parent == "pin" -> Pin(requireNotNull(id.publicId()))
            parent == "people" -> Person(
                id.takeIf(memberId::matches) ?: Person.EMPTY_ID,
                id,
                jumpTo = params.remove("section")?.let { key -> peopleSections.entries.first { it.value == key }.key }.orEmpty(),
            )
            segments.size == 3 && segments.first() == "people" && id == "collections" -> Collections(segments[1])
            parent == "collection" -> CollectionContent(id)
            parent == "topic" -> Topic(id, section = params.remove("section")?.also { require(it in topicSections) }.orEmpty())
            parent == "notifications/entry" -> Notification.Entry(id, "")
            parent == "write/answer" -> WriteAnswer(requireNotNull(id.publicId()))
            parent == "write/pin" -> WritePin(publishTopicId = requireNotNull(id.publicId()).toString())
            else -> error("Unknown route")
        }
        require(params.isEmpty() && page.path() != null)
        return page
    }
}
