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

import com.github.zly2006.zhihu.data.AdvertisementFeed
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.Feed
import com.github.zly2006.zhihu.data.FeedContentIdentity
import com.github.zly2006.zhihu.data.FeedDisplayItem
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.data.navDestination
import com.github.zly2006.zhihu.data.questionAuthor
import com.github.zly2006.zhihu.data.target
import com.github.zly2006.zhihu.i18n.DomainStrings
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.Pin
import com.github.zly2006.zhihu.platform.SettingsStore
import com.github.zly2006.zhihu.platform.platformKeywordSemanticMatcher
import com.github.zly2006.zhihu.platform.platformSemanticRuntimeGeneration
import com.github.zly2006.zhihu.platform.showNlpBlockedMessage
import com.github.zly2006.zhihu.util.AppLog
import com.github.zly2006.zhihu.util.ZhihuFetchSignature
import com.github.zly2006.zhihu.viewmodel.FeedDisplaySettings
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json

class ForegroundReadFilterPipeline(
    private val settings: FeedFilterSettings,
    database: ContentFilterDatabase = getContentFilterDatabase(),
) {
    private val contentFilterManager = ContentFilterManager(database.contentFilterDao())
    private val contentOpenEventDao = database.contentOpenEventDao()
    private val blockedFeedRecordDao = database.blockedFeedRecordDao()

    suspend fun filter(items: List<FeedDisplayItem>): List<FeedDisplayItem> {
        if (settings.reverseBlock || !settings.enableContentFilter) {
            return items
        }

        val itemIdentityPairs = items.map { item -> item to item.resolveContentIdentity() }
        val viewedContentIds = contentFilterManager.getAlreadyViewedContentIds(
            itemIdentityPairs.map { (_, identity) -> identity.type to identity.id },
        )
        val openedContentKeys = contentOpenEventDao
            .getOpenedContentKeysByKeys(
                itemIdentityPairs.map { (_, identity) -> "${identity.type}:${identity.id}" },
            ).toSet()

        val keptItems = mutableListOf<FeedDisplayItem>()
        val blockedItems = mutableListOf<Pair<FilterableContent, String>>()

        itemIdentityPairs.forEach { (item, identity) ->
            val isViewed = ContentViewRecord.generateId(identity.type, identity.id) in viewedContentIds ||
                "${identity.type}:${identity.id}" in openedContentKeys
            val isFollowing = item.filterFacts?.authorIsFollowing ?: item.feed
                ?.target
                ?.author
                ?.isFollowing ?: item.raw?.author?.isFollowing ?: false
            val isLowQualityAndroidFeed = isLowQualityForegroundFeed(item)

            if (isFollowing || (!isViewed && !isLowQualityAndroidFeed)) {
                keptItems.add(item)
            } else {
                blockedItems.add(
                    item.toFilterableContent(identity, DataHolder.DummyContent) to "已读过且未关注作者",
                )
            }
        }

        if (blockedItems.isNotEmpty()) {
            saveBlockedFeedRecords(blockedFeedRecordDao, blockedItems)
        }

        return keptItems
    }
}

private fun isLowQualityForegroundFeed(item: FeedDisplayItem): Boolean =
    item.details.contains("小时前") ||
        item.details.contains("分钟前") ||
        item.details.contains("浏览")

data class FeedContentFilterResult(
    val kept: List<FilterableContent>,
    val blocked: List<Pair<FilterableContent, String>>,
)

class FeedContentFilterPipeline(
    private val settings: FeedFilterSettings,
    database: ContentFilterDatabase = getContentFilterDatabase(),
    semanticMatcher: KeywordSemanticMatcher? = platformKeywordSemanticMatcher,
    private val rules: FeedFilterRules? = null,
) {
    private val database = database
    private val blockedKeywordService = BlockedKeywordService(database.blockedContentRecordDao(), semanticMatcher)

    suspend fun filter(contents: List<FilterableContent>): FeedContentFilterResult {
        val blocked = mutableListOf<Pair<FilterableContent, String>>()
        var filteredContents = contents
        val rules = rules ?: readFeedFilterRules(database)

        if (settings.enableUserBlocking) {
            val blockedUserIds = rules.blockedUsers
            val blockedQuestionAuthorIds = rules.blockedQuestionAuthors
            val kept = ArrayList<FilterableContent>(filteredContents.size)
            filteredContents.forEach { content ->
                when {
                    content.authorId in blockedUserIds ->
                        blocked.add(content to "屏蔽作者：${content.authorName ?: content.authorId}")
                    content.questionAuthorId in blockedQuestionAuthorIds ->
                        blocked.add(content to "屏蔽提问者：${content.questionAuthorName ?: content.questionAuthorId}")
                    else -> kept.add(content)
                }
            }
            filteredContents = kept
        }

        if (settings.enableKeywordBlocking) {
            val exactKeywords = rules.keywords.filter { it.getKeywordTypeEnum() == KeywordType.EXACT_MATCH }
            val keywordsByScope = exactKeywords.groupBy { it.getKeywordScopeEnum() }
            val patterns = exactKeywords.filter { it.isRegex }.associateWith { keyword ->
                runCatching {
                    if (keyword.caseSensitive) Regex(keyword.keyword) else Regex(keyword.keyword, RegexOption.IGNORE_CASE)
                }.getOrNull()
            }
            val (kept, removed) = filteredContents.partition { content ->
                keywordsByScope.none { (scope, keywords) ->
                    content.keywordTexts(scope).any { containsBlockedKeyword(it, keywords, patterns) }
                }
            }
            removed.forEach { blocked.add(it to "关键词屏蔽") }
            filteredContents = kept
        }

        if (settings.enableNlpBlocking) {
            val finalFilteredContents = mutableListOf<FilterableContent>()
            val blockedThisRound = mutableListOf<FilterableContent>()

            for (content in filteredContents) {
                val (shouldBlock, matchedKeywords) = blockedKeywordService.checkNLPBlockingWithWeight(
                    content = content,
                    keywords = rules.keywords,
                    threshold = settings.nlpSimilarityThreshold,
                )

                if (!shouldBlock) {
                    finalFilteredContents.add(content)
                } else {
                    blockedKeywordService.recordBlockedContent(
                        contentId = content.contentId,
                        contentType = content.contentType,
                        title = content.title,
                        excerpt = content.summary ?: "",
                        authorName = content.authorName,
                        authorId = content.authorId,
                        matchedKeywords = matchedKeywords,
                    )
                    val keywordNames = matchedKeywords.joinToString("、") { it.keyword }
                    blocked.add(content to "NLP语义屏蔽：$keywordNames")
                    blockedThisRound.add(content)
                }
            }

            if (blockedThisRound.isNotEmpty()) {
                showNlpBlockedMessage(blockedThisRound)
            }

            filteredContents = finalFilteredContents
        }

        if (settings.enableTopicBlocking) {
            filteredContents = filteredContents.filter { content ->
                val topicIds = (
                    extractTopicIds(content.raw).orEmpty() + when (val source = content.source) {
                        is Feed.AnswerTarget -> source.question.boundTopicIds.map(Long::toString)
                        is Feed.QuestionTarget -> source.boundTopicIds.map(Long::toString)
                        else -> emptyList()
                    }
                ).distinct()
                val blockedTopicIds = topicIds.filter { it in rules.blockedTopics }
                val kept = blockedTopicIds.size < settings.topicBlockingThreshold
                if (!kept) {
                    val topicName = blockedTopicIds
                        .firstOrNull()
                        ?.let(rules.blockedTopics::get)
                    blocked.add(content to "屏蔽主题：$topicName")
                }
                kept
            }
        }

        return FeedContentFilterResult(filteredContents, blocked)
    }
}

private fun containsBlockedKeyword(
    text: String?,
    keywords: List<BlockedKeyword>,
    patterns: Map<BlockedKeyword, Regex?>,
): Boolean {
    if (text.isNullOrBlank()) return false

    return keywords.any { blockedKeyword ->
        runCatching {
            when {
                blockedKeyword.isRegex -> patterns[blockedKeyword]?.containsMatchIn(text) == true
                blockedKeyword.caseSensitive -> text.contains(blockedKeyword.keyword)
                else -> text.contains(blockedKeyword.keyword, ignoreCase = true)
            }
        }.getOrDefault(false)
    }
}

fun interface ContentDetailProvider {
    suspend fun get(navDestination: NavDestination): DataHolder.Content?
}

class FeedDisplayFilterPipeline(
    private val settings: FeedFilterSettings,
    private val contentDetailProvider: ContentDetailProvider,
    database: ContentFilterDatabase = getContentFilterDatabase(),
    semanticMatcher: KeywordSemanticMatcher? = platformKeywordSemanticMatcher,
    private val displaySettings: FeedDisplaySettings? = null,
    rules: FeedFilterRules? = null,
) {
    private val contentFilterPipeline = FeedContentFilterPipeline(settings, database, semanticMatcher, rules)
    private val blockedFeedRecordDao = database.blockedFeedRecordDao()

    suspend fun filter(items: List<FeedDisplayItem>): List<FeedDisplayItem> {
        // feed_advert 没有内容导航，走详情会退回 DummyContent 并漏过滤；在内容规则前分流。
        val contentItems = items.filterNot { it.feed is AdvertisementFeed }.mapNotNull { item ->
            if (displaySettings == null) item else displaySettings.applyQualityFilter(item)
        }

        val itemContentPairs = coroutineScope {
            contentItems
                .map { item ->
                    async {
                        val identity = item.resolveContentIdentity()
                        val follows = item.filterFacts?.authorIsFollowing ?: item.feed
                            ?.target
                            ?.author
                            ?.isFollowing ?: item.raw?.author?.isFollowing
                        val rawContent = if (!settings.filterFollowedUserContent && follows == true) item.raw ?: DataHolder.DummyContent else resolveRawContent(item)
                        val hydrated = item.copy(raw = rawContent.takeUnless { it is DataHolder.DummyContent })
                        val checked = if (displaySettings == null) hydrated else displaySettings.applyQualityFilter(hydrated) ?: return@async null
                        checked to checked.toFilterableContent(identity, rawContent)
                    }
                }.awaitAll()
                .filterNotNull()
        }

        val filterableContents = itemContentPairs.map { it.second }.filterNot { !settings.filterFollowedUserContent && it.isFollowing }

        if (settings.reverseBlock) {
            return items.mapNotNull { item ->
                when {
                    item.feed is AdvertisementFeed -> item
                    else -> {
                        val content = itemContentPairs.firstOrNull { it.first.stableKey == item.stableKey }?.second ?: return@mapNotNull null
                        if (!settings.filterFollowedUserContent && content.isFollowing) return@mapNotNull null
                        item.takeIf { getFeedAdBlockReason(content, FeedAdBlockSettings()) != null }
                    }
                }
            }
        }

        val adBlockedContents = mutableListOf<Pair<FilterableContent, String>>()
        val nonAdContents = filterableContents.filter { content ->
            val blockReason = getFeedAdBlockReason(content, settings.adBlockSettings)
            if (blockReason != null) adBlockedContents.add(content to blockReason)
            blockReason == null
        }

        val contentFilterResult = contentFilterPipeline.filter(nonAdContents)
        val allBlocked = adBlockedContents + contentFilterResult.blocked
        if (allBlocked.isNotEmpty()) {
            saveBlockedFeedRecords(blockedFeedRecordDao, allBlocked)
        }

        return itemContentPairs
            .mapNotNull { (item, content) ->
                item.takeIf { (!settings.filterFollowedUserContent && content.isFollowing) || contentFilterResult.kept.any { it === content } }
            }.filterDetailsKeywords()
    }

    private suspend fun resolveRawContent(item: FeedDisplayItem): DataHolder.Content =
        item.raw?.takeUnless { it is DataHolder.DummyContent } ?: when (val dest = item.navDestination) {
            is Article -> contentDetailProvider.get(dest) ?: DataHolder.DummyContent
            is Pin -> contentDetailProvider.get(dest) ?: DataHolder.DummyContent
            else -> DataHolder.DummyContent
        }

    private fun List<FeedDisplayItem>.filterDetailsKeywords(): List<FeedDisplayItem> = filter { item ->
        detailsPostFilterKeywords.none { keyword ->
            val shouldFilter = item.details.contains(keyword)
            if (shouldFilter) {
                AppLog.e("ContentFilterExtensions", "Filtered item '${item.title}' due to keyword '$keyword' in details: ${item.content}")
            }
            shouldFilter
        }
    }
}

/** 原因字段是已持久化的应用格式。只解释已知前缀，保留名称、关键词和未知格式。 */
val BlockedFeedRecord.displayBlockedReason: String
    get() = when (blockedReason) {
        "已读过且未关注作者" -> DomainStrings.blockedReadUnfollowed
        "关键词屏蔽" -> DomainStrings.blockedByKeyword
        "知乎盐选付费内容" -> DomainStrings.blockedPaidContent
        "知乎广告平台内容" -> DomainStrings.blockedAdPlatform
        "知乎学堂内容" -> DomainStrings.blockedSchool
        "微信公众号文章" -> DomainStrings.blockedWechat
        else -> when {
            blockedReason.startsWith("屏蔽作者：") -> DomainStrings.blockedAuthor(blockedReason.removePrefix("屏蔽作者："))
            blockedReason.startsWith("屏蔽提问者：") -> DomainStrings.blockedQuestionAuthor(blockedReason.removePrefix("屏蔽提问者："))
            blockedReason.startsWith("NLP语义屏蔽：") -> DomainStrings.blockedSemantic(blockedReason.removePrefix("NLP语义屏蔽："))
            blockedReason.startsWith("屏蔽主题：") -> DomainStrings.blockedTopic(blockedReason.removePrefix("屏蔽主题："))
            else -> blockedReason
        }
    }

suspend fun saveBlockedFeedRecords(
    dao: BlockedFeedRecordDao,
    blocked: List<Pair<FilterableContent, String>>,
) {
    blocked.forEach { (content, reason) ->
        dao.insert(
            BlockedFeedRecord(
                title = content.title,
                questionId = content.questionId,
                authorName = content.authorName,
                authorId = content.authorId,
                url = content.url,
                content = content.content,
                blockedReason = reason,
                navDestinationJson = content.destination?.let { feedFilterRecordJson.encodeToString<NavDestination>(it) },
                feedJson = content.feed?.let { feedFilterRecordJson.encodeToString<Feed>(it) },
            ),
        )
    }
    dao.maintainLimit()
}

private val detailsPostFilterKeywords = listOf("感兴趣", "购买")

/**
 * 常见内容身份类型。
 * 用于 feed 过滤、内容打开记录和导航查询，不属于 Android 平台语义。
 */
object ContentType {
    const val ANSWER = "answer"
    const val ARTICLE = "article"
    const val QUESTION = "question"
    const val TOPIC = "topic"
    const val COLUMN = "column"
    const val VIDEO = "video"
    const val PIN = "pin"
}

/**
 * 从 feed item 提炼出的内容快照。
 * 这个结构只在 feed 过滤流水线内部流转，用来承接关键词、NLP、作者、主题等内容级规则。
 */
data class FilterableContent(
    val title: String,
    val summary: String?,
    val content: String?,
    val authorName: String?,
    val authorId: String?,
    val contentId: String,
    val contentType: String,
    val raw: DataHolder.Content,
    val isFollowing: Boolean = false,
    val questionId: Long? = null,
    val url: String? = null,
    val feed: Feed? = null,
    val destination: NavDestination? = null,
    val questionAuthorName: String? = null,
    val questionAuthorId: String? = null,
    val source: Feed.Target? = null,
)

/** 回答卡片的标题属于问题，不能按卡片类型把全部文本分给回答规则。 */
fun FilterableContent.keywordTexts(scope: KeywordScope): List<String?> = when (scope) {
    KeywordScope.ALL -> listOf(title, summary, content)
    KeywordScope.QUESTION -> when {
        raw is DataHolder.Answer -> listOf(title, raw.question.title, (source as? Feed.AnswerTarget)?.question?.detail)
        raw is DataHolder.Question -> listOf(title, raw.title, raw.detail)
        source is Feed.AnswerTarget -> listOf(title, source.question.title, source.question.detail)
        source is Feed.QuestionTarget -> listOf(title, source.title, source.detail)
        contentType == ContentType.ANSWER -> listOf(title)
        contentType == ContentType.QUESTION -> listOf(title)
        else -> emptyList()
    }.distinct()
    KeywordScope.ANSWER -> if (raw is DataHolder.Answer || source is Feed.AnswerTarget || contentType == ContentType.ANSWER) {
        listOf(
            summary,
            content,
            (raw as? DataHolder.Answer)?.excerpt,
            (raw as? DataHolder.Answer)?.content,
            (source as? Feed.AnswerTarget)?.excerpt,
            (source as? Feed.AnswerTarget)?.content,
        ).distinct()
    } else {
        emptyList()
    }
}

fun FeedDisplayItem.resolveContentIdentity(): FeedContentIdentity =
    contentIdentity ?: FeedContentIdentity("unknown", stableKey)

fun FeedDisplayItem.toFilterableContent(
    identity: FeedContentIdentity,
    rawContent: DataHolder.Content,
): FilterableContent = FilterableContent(
    title = title.ifBlank { feed?.target?.title.orEmpty() },
    summary = summary,
    content = when (rawContent) {
        is DataHolder.Answer -> rawContent.content
        is DataHolder.Article -> rawContent.content
        is DataHolder.Pin -> rawContent.contentHtml
        else -> when (val target = feed?.target) {
            is Feed.AnswerTarget -> target.content.takeIf(String::isNotBlank)
            is Feed.ArticleTarget -> target.content.takeIf(String::isNotBlank)
            is Feed.QuestionTarget -> target.detail.takeIf(String::isNotBlank)
            is Feed.PinTarget -> target.contentHtml.takeIf(String::isNotBlank)
            else -> null
        }
    } ?: content.takeUnless { isQualityFiltered && qualityFilterReason == null } ?: summary,
    authorName = authorName ?: rawContent.author?.name ?: feed?.target?.author?.name,
    authorId = filterFacts?.authorId ?: feed?.target?.author?.id ?: rawContent.author?.id,
    contentId = identity.id,
    contentType = identity.type,
    raw = rawContent,
    source = feed?.target,
    isFollowing = filterFacts?.authorIsFollowing ?: feed?.target?.author?.isFollowing ?: rawContent.author?.isFollowing ?: false,
    questionId = (rawContent as? DataHolder.Answer)?.question?.id ?: (feed?.target as? Feed.AnswerTarget)?.question?.id,
    questionAuthorName = feed?.target?.questionAuthor?.name ?: when (rawContent) {
        is DataHolder.Answer -> rawContent.question.author?.name
        is DataHolder.Question -> rawContent.author.name
        else -> null
    },
    questionAuthorId = feed?.target?.questionAuthor?.id ?: when (rawContent) {
        is DataHolder.Answer -> rawContent.question.author?.id
        is DataHolder.Question -> rawContent.author.id
        else -> null
    },
    url = feed?.target?.url,
    feed = feed,
    destination = navDestination,
)

/** 从内容实体中提取主题 ID 列表，供 feed 过滤阶段的主题规则使用。 */
fun extractTopicIds(raw: DataHolder.Content): List<String>? = when (raw) {
    is DataHolder.Answer -> raw.question.topics.map { it.id }
    is DataHolder.Question -> raw.topics.map { it.id }
    is DataHolder.Article -> raw.topics?.map { it.id }
    is DataHolder.Pin -> raw.topics?.map { it.id }
    else -> null
}

private val DataHolder.Content.author: DataHolder.Author?
    get() = when (this) {
        is DataHolder.Answer -> this.author
        is DataHolder.Article -> this.author
        is DataHolder.Pin -> this.author
        is DataHolder.Question -> this.author
        else -> null
    }

private val feedFilterRecordJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
}

data class FeedAdBlockSettings(
    val blockZhihuAdPlatform: Boolean = true,
    val blockZhihuSchool: Boolean = true,
    val blockWeChatOfficialAccount: Boolean = true,
    val blockPaidContent: Boolean = true,
)

fun getFeedAdBlockReason(
    content: FilterableContent,
    settings: FeedAdBlockSettings,
): String? = when (val raw = content.raw) {
    is DataHolder.Answer if settings.blockPaidContent && raw.paidInfo != null -> "知乎盐选付费内容"
    is DataHolder.Answer -> getLinkBasedAdReason(raw.content, settings)
    is DataHolder.Article if settings.blockPaidContent && raw.paidInfo != null -> "知乎盐选付费内容"
    is DataHolder.Article -> getLinkBasedAdReason(raw.content, settings)
    is DataHolder.Pin -> getLinkBasedAdReason(raw.contentHtml, settings)
    else -> null
}

private fun getLinkBasedAdReason(
    content: String,
    settings: FeedAdBlockSettings,
): String? {
    if (settings.blockZhihuAdPlatform && "xg.zhihu.com" in content) return "知乎广告平台内容"
    if (settings.blockZhihuSchool && ("d.zhihu.com" in content || "data-edu-card-id" in content)) return "知乎学堂内容"
    if (settings.blockWeChatOfficialAccount && "mp.weixin.qq.com" in content) return "微信公众号文章"
    return null
}

/** 一轮过滤只读取一次规则；指纹与实际执行使用同一份内容。 */
data class FeedFilterRules(
    val keywords: List<BlockedKeyword>,
    val blockedUsers: Set<String>,
    val blockedQuestionAuthors: Set<String>,
    val blockedTopics: Map<String, String>,
)

suspend fun readFeedFilterRules(database: ContentFilterDatabase): FeedFilterRules = FeedFilterRules(
    database.blockedKeywordDao().getAllKeywords(),
    database.blockedUserDao().getAllUsers().mapTo(hashSetOf()) { it.userId },
    database.blockedQuestionAuthorDao().getAllUsers().mapTo(hashSetOf()) { it.userId },
    database.blockedTopicDao().getAllTopics().associate { it.topicId to it.topicName },
)

data class HomeFeedFilterPolicy(
    val displaySettings: FeedDisplaySettings,
    val filterSettings: FeedFilterSettings,
    val rules: FeedFilterRules,
    /** 0 保留原指纹，Android 不因非 Android 模型接线失效既有快照。 */
    val semanticRuntimeGeneration: Long = 0,
) {
    val fingerprint: String = ZhihuFetchSignature.md5Hex(
        ZhihuJson.json.encodeToString(
            listOf(
                displaySettings.toString(),
                filterSettings.toString(),
                ZhihuJson.json.encodeToString(rules.keywords.map { ZhihuJson.json.encodeToString(it.copy(id = 0, createdTime = 0)) }.sorted()),
                ZhihuJson.json.encodeToString(rules.blockedUsers.sorted()),
                ZhihuJson.json.encodeToString(rules.blockedQuestionAuthors.sorted()),
                ZhihuJson.json.encodeToString(rules.blockedTopics.keys.sorted()),
            ) + if (semanticRuntimeGeneration == 0L) emptyList() else listOf("semanticRuntime:$semanticRuntimeGeneration"),
        ).encodeToByteArray(),
    )
}

suspend fun readHomeFeedFilterPolicy(settings: SettingsStore, database: ContentFilterDatabase): HomeFeedFilterPolicy = HomeFeedFilterPolicy(
    FeedDisplaySettings(settings), settings.toFeedFilterSettings(), readFeedFilterRules(database), platformSemanticRuntimeGeneration.value,
)

/** 复检只使用现有内容，不请求详情或重新登记曝光。 */
suspend fun recheckHomeFeedItems(items: List<FeedDisplayItem>, policy: HomeFeedFilterPolicy, database: ContentFilterDatabase): List<FeedDisplayItem> {
    val contentItems = items.filter { it.feed != null || it.navDestination != null || it.filterFacts != null }
    val filtered = FeedDisplayFilterPipeline(
        settings = policy.filterSettings,
        contentDetailProvider = ContentDetailProvider { null },
        database = database,
        displaySettings = policy.displaySettings,
        rules = policy.rules,
    ).filter(contentItems).associateBy { it.stableKey }
    val contentKeys = contentItems.mapTo(hashSetOf()) { it.stableKey }
    return items.mapNotNull { if (it.stableKey in contentKeys) filtered[it.stableKey] else it }
}

data class FeedFilterSettings(
    val enableContentFilter: Boolean = true,
    val reverseBlock: Boolean = false,
    val filterFollowedUserContent: Boolean = false,
    val enableKeywordBlocking: Boolean = true,
    val enableNlpBlocking: Boolean = true,
    val nlpSimilarityThreshold: Double = 0.8,
    val enableUserBlocking: Boolean = true,
    val enableTopicBlocking: Boolean = true,
    val topicBlockingThreshold: Int = 1,
    val adBlockSettings: FeedAdBlockSettings = FeedAdBlockSettings(),
)

fun SettingsStore.toFeedFilterSettings(): FeedFilterSettings = FeedFilterSettings(
    enableContentFilter = getBoolean("enableContentFilter", true),
    reverseBlock = getBoolean("reverseBlock", false),
    filterFollowedUserContent = getBoolean("filterFollowedUserContent", false),
    enableKeywordBlocking = getBoolean("enableKeywordBlocking", true),
    enableNlpBlocking = getBoolean("enableNLPBlocking", true),
    nlpSimilarityThreshold = getFloat("nlpSimilarityThreshold", 0.8f).toDouble(),
    enableUserBlocking = getBoolean("enableUserBlocking", true),
    enableTopicBlocking = getBoolean("enableTopicBlocking", true),
    topicBlockingThreshold = getInt("topicBlockingThreshold", 1),
    adBlockSettings = FeedAdBlockSettings(
        blockZhihuAdPlatform = getBoolean("blockZhihuAdPlatform", true),
        blockZhihuSchool = getBoolean("blockZhihuSchool", true),
        blockWeChatOfficialAccount = getBoolean("blockWeChatOfficialAccount", true),
        blockPaidContent = getBoolean("blockPaidContent", true),
    ),
)
