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

package com.github.zly2006.zhihu.reading

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import com.fleeksoft.ksoup.Ksoup
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.Feed
import com.github.zly2006.zhihu.data.FeedDisplayItem
import com.github.zly2006.zhihu.data.navDestination
import com.github.zly2006.zhihu.data.target
import com.github.zly2006.zhihu.filter.ContentOpenEventSupport
import com.github.zly2006.zhihu.i18n.AppLanguage
import com.github.zly2006.zhihu.i18n.AppLocale
import com.github.zly2006.zhihu.i18n.ReadingStrings
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.Pin
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.platform.SettingsStore
import com.github.zly2006.zhihu.util.twoDigitString
import com.github.zly2006.zhihu.viewmodel.filter.ContentType
import com.github.zly2006.zhihu.viewmodel.filter.getContentFilterDatabase
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

const val READING_PREFERENCES_KEY = "continuousReadingPreferences"
const val READING_PLAYBACK_SPEED_KEY = "continuousReadingPlaybackSpeed"
const val DEFAULT_READING_PLAYBACK_SPEED = 1f
const val MIN_READING_PLAYBACK_SPEED = 0.5f
const val MAX_READING_PLAYBACK_SPEED = 2f

private const val MAX_READING_QUEUE_SIZE = 100
private const val MAX_READING_COMMENT_COUNT = 50
private const val MAX_REGISTERED_READING_SOURCES = 32

@Serializable
enum class ReadingContentType(
    private val label: () -> String,
) {
    Answer(ReadingStrings::typeAnswer),
    Article(ReadingStrings::typeArticle),
    Pin(ReadingStrings::typePin),
    Question(ReadingStrings::typeQuestion),
    ;

    val displayName: String get() = label()
}

@Serializable
enum class ReadingTemplateField(
    private val label: () -> String,
    private val hint: () -> String,
) {
    ContentType(ReadingStrings::fieldContentType, ReadingStrings::hintContentType),
    Title(ReadingStrings::fieldTitle, ReadingStrings::hintTitle),
    Author(ReadingStrings::fieldAuthor, ReadingStrings::hintAuthor),
    Body(ReadingStrings::fieldBody, ReadingStrings::hintBody),
    PublishedAt(ReadingStrings::fieldPublished, ReadingStrings::hintPublished),
    VoteUpCount(ReadingStrings::fieldVotes, ReadingStrings::hintVotes),
    Comments(ReadingStrings::fieldComments, ReadingStrings::hintComments),
    ;

    val displayName: String get() = label()
    val description: String get() = hint()
}

@Serializable
enum class ReadingCommentOrder(
    private val label: () -> String,
    val apiValue: String,
) {
    Score(ReadingStrings::orderPopularity, "score"),
    Time(ReadingStrings::orderTime, "ts"),
    ;

    val displayName: String get() = label()
}

@Serializable
enum class ReadingPublishedTimeMode(
    private val label: () -> String,
) {
    Absolute(ReadingStrings::timeAbsolute),
    Relative(ReadingStrings::timeRelative),
    ;

    val displayName: String get() = label()
}

@Serializable
enum class ReadingRelativeTimePrecision(
    private val label: () -> String,
) {
    Second(ReadingStrings::precisionSecond),
    Hour(ReadingStrings::precisionHour),
    Day(ReadingStrings::precisionDay),
    ;

    val displayName: String get() = label()
}

@Serializable
data class ReadingPreferences(
    val fieldOrder: List<ReadingTemplateField> = ReadingTemplateField.entries,
    val enabledFields: Set<ReadingTemplateField> = setOf(
        ReadingTemplateField.ContentType,
        ReadingTemplateField.Title,
        ReadingTemplateField.Author,
        ReadingTemplateField.Body,
    ),
    val publishedTimeMode: ReadingPublishedTimeMode = ReadingPublishedTimeMode.Absolute,
    val relativeTimePrecision: ReadingRelativeTimePrecision = ReadingRelativeTimePrecision.Second,
    val commentCount: Int = 3,
    val commentOrder: ReadingCommentOrder = ReadingCommentOrder.Score,
    val readCommentAuthor: Boolean = true,
    val queueLimit: Int = 5,
    val transitionText: String? = null,
) {
    val effectiveTransitionText: String get() = transitionText ?: ReadingStrings.defaultTransition

    fun normalized(): ReadingPreferences {
        val normalizedCommentCount = commentCount.coerceIn(0, MAX_READING_COMMENT_COUNT)
        val normalizedOrder = buildList {
            addAll(fieldOrder.distinct())
            addAll(ReadingTemplateField.entries.filterNot(::contains))
        }
        val normalizedEnabledFields = enabledFields
            .intersect(ReadingTemplateField.entries.toSet())
            .ifEmpty { setOf(ReadingTemplateField.Body) }
            .let { fields ->
                if (fields == setOf(ReadingTemplateField.Comments) && normalizedCommentCount == 0) {
                    setOf(ReadingTemplateField.Body)
                } else {
                    fields
                }
            }
        return copy(
            fieldOrder = normalizedOrder,
            enabledFields = normalizedEnabledFields,
            commentCount = normalizedCommentCount,
            queueLimit = queueLimit.coerceIn(1, MAX_READING_QUEUE_SIZE),
        )
    }

    val shouldLoadComments: Boolean
        get() = ReadingTemplateField.Comments in enabledFields && commentCount > 0
}

@Serializable
data class ReadingQueueItem(
    val contentType: ReadingContentType,
    val id: Long,
    val title: String = "",
    val author: String = "",
    val questionId: Long? = null,
    val bodyHtml: String? = null,
    val publishedAt: Long = 0L,
    val updatedAt: Long = 0L,
    val voteUpCount: Int = -1,
    val commentCount: Int = -1,
) {
    val key: String
        get() = "${contentType.name}:$id"

    val displayTitle: String
        get() = title
            .cleanReadingMetadata()
            .takeIf(String::isNotBlank)
            ?: when (contentType) {
                ReadingContentType.Pin -> author.takeIf(String::isNotBlank)?.let(ReadingStrings::pinByAuthor) ?: contentType.displayName
                else -> contentType.displayName
            }

    fun withoutBody(): ReadingQueueItem = if (bodyHtml == null) this else copy(bodyHtml = null)

    fun toDestination(sourceId: String? = null): NavDestination = when (contentType) {
        ReadingContentType.Answer -> Article(
            title = title,
            type = ArticleType.Answer,
            id = id,
            authorName = author,
            readingQueueSourceId = sourceId,
        )
        ReadingContentType.Article -> Article(
            title = title,
            type = ArticleType.Article,
            id = id,
            authorName = author,
            readingQueueSourceId = sourceId,
        )
        ReadingContentType.Pin -> Pin(
            id = id,
            authorName = author,
            readingQueueSourceId = sourceId,
        )
        ReadingContentType.Question -> Question(
            questionId = id,
            title = title,
            readingQueueSourceId = sourceId,
        )
    }
}

@Serializable
data class ReadingComment(
    val author: String,
    val body: String,
)

data class ResolvedReadingContent(
    val contentType: ReadingContentType,
    val title: String,
    val author: String,
    val body: String,
    val publishedAt: Long,
    val voteUpCount: Int,
    val comments: List<ReadingComment>,
    val updatedAt: Long = 0L,
)

enum class ReadingPlaybackStatus {
    Idle,
    Initializing,
    Loading,
    Playing,
    Paused,
    Error,
}

data class ReadingPlayerState(
    val status: ReadingPlaybackStatus = ReadingPlaybackStatus.Idle,
    val queue: List<ReadingQueueItem> = emptyList(),
    val currentIndex: Int = -1,
    val currentChunkIndex: Int = 0,
    val totalChunks: Int = 0,
    val errorMessage: String? = null,
    val engineLabel: String = "",
    val availableEngineLabels: List<String> = emptyList(),
    val sourceId: String? = null,
    val playbackSpeed: Float = DEFAULT_READING_PLAYBACK_SPEED,
) {
    val hasSession: Boolean
        get() = queue.isNotEmpty() && currentIndex in queue.indices

    val currentItem: ReadingQueueItem?
        get() = queue.getOrNull(currentIndex)

    val canPlayPrevious: Boolean
        get() = hasSession && currentIndex > 0

    val canPlayNext: Boolean
        get() = hasSession && currentIndex < queue.lastIndex

    val isActivelyPlaying: Boolean
        get() = status == ReadingPlaybackStatus.Initializing ||
            status == ReadingPlaybackStatus.Loading ||
            status == ReadingPlaybackStatus.Playing
}

data class ReadingStartRequest(
    val queue: List<ReadingQueueItem>,
    val preferences: ReadingPreferences,
    val startIndex: Int = 0,
    val sourceId: String? = null,
    val playbackSpeed: Float = DEFAULT_READING_PLAYBACK_SPEED,
)

interface ReadingPlayerController {
    val state: State<ReadingPlayerState>

    fun start(request: ReadingStartRequest)

    fun togglePlayPause()

    fun playPrevious()

    fun playNext()

    fun playAt(index: Int)

    fun setPlaybackSpeed(speed: Float)

    fun stop()
}

/** 不支持连续朗读的平台返回 null，调用方应隐藏或禁用对应操作。 */
@Composable
expect fun rememberReadingPlayerController(): ReadingPlayerController?

expect val isReadingPlayerSupported: Boolean

/** 安装宿主唯一播放器；导航不销毁会话，宿主销毁或切账号关闭所属语音资源。 */
@Composable
expect fun ReadingPlayerHost(content: @Composable () -> Unit)

private val readingPreferencesJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun loadReadingPreferences(settings: SettingsStore): ReadingPreferences = settings
    .getStringOrNull(READING_PREFERENCES_KEY)
    ?.let { encoded ->
        runCatching {
            readingPreferencesJson.decodeFromString<ReadingPreferences>(encoded)
        }.getOrNull()
    }?.normalized()
    ?: ReadingPreferences()

fun saveReadingPreferences(
    settings: SettingsStore,
    preferences: ReadingPreferences,
) {
    settings.putString(
        READING_PREFERENCES_KEY,
        readingPreferencesJson.encodeToString(preferences.normalized()),
    )
}

fun normalizeReadingPlaybackSpeed(speed: Float): Float = if (speed.isFinite()) {
    speed.coerceIn(MIN_READING_PLAYBACK_SPEED, MAX_READING_PLAYBACK_SPEED)
} else {
    DEFAULT_READING_PLAYBACK_SPEED
}

fun loadReadingPlaybackSpeed(settings: SettingsStore): Float = normalizeReadingPlaybackSpeed(
    settings.getFloat(READING_PLAYBACK_SPEED_KEY, DEFAULT_READING_PLAYBACK_SPEED),
)

fun saveReadingPlaybackSpeed(
    settings: SettingsStore,
    speed: Float,
) {
    settings.putFloat(READING_PLAYBACK_SPEED_KEY, normalizeReadingPlaybackSpeed(speed))
}

fun buildReadingSpeechText(
    content: ResolvedReadingContent,
    preferences: ReadingPreferences,
    nowEpochSeconds: Long = Clock.System.now().epochSeconds,
): String {
    val normalized = preferences.normalized()
    return normalized.fieldOrder
        .asSequence()
        .filter { it in normalized.enabledFields }
        .mapNotNull { field ->
            when (field) {
                ReadingTemplateField.ContentType -> when (content.contentType) {
                    ReadingContentType.Answer -> ReadingStrings.introduceAnswer
                    ReadingContentType.Article -> ReadingStrings.introduceArticle
                    ReadingContentType.Pin -> ReadingStrings.introducePin
                    ReadingContentType.Question -> ReadingStrings.introduceQuestion
                }
                ReadingTemplateField.Title ->
                    content.title
                        .cleanReadingMetadata()
                        .takeIf(String::isNotBlank)
                        ?.let { ReadingStrings.speakTitle(it) }
                ReadingTemplateField.Author ->
                    content.author
                        .cleanReadingMetadata()
                        .takeIf(String::isNotBlank)
                        ?.let { ReadingStrings.speakAuthor(it) }
                ReadingTemplateField.Body -> content.body.takeIf(String::isNotBlank)
                ReadingTemplateField.PublishedAt ->
                    when (normalized.publishedTimeMode) {
                        ReadingPublishedTimeMode.Absolute ->
                            content.publishedAt
                                .takeIf { it > 0 }
                                ?.let { ReadingStrings.speakPublished(formatReadingDateTime(it)) }
                        ReadingPublishedTimeMode.Relative ->
                            content.updatedAt
                                .takeIf { it > 0 }
                                ?.let {
                                    ReadingStrings.speakLastEdited(formatReadingRelativeTime(it, nowEpochSeconds, normalized.relativeTimePrecision))
                                }
                    }
                ReadingTemplateField.VoteUpCount ->
                    content.voteUpCount
                        .takeIf { it >= 0 }
                        ?.let { ReadingStrings.speakVotes(it) }
                ReadingTemplateField.Comments ->
                    content.comments
                        .takeIf(List<ReadingComment>::isNotEmpty)
                        ?.mapIndexed { index, comment ->
                            val body = if (normalized.readCommentAuthor && comment.author.isNotBlank()) {
                                ReadingStrings.speakCommentBody(ReadingStrings.speakCommentAuthor(comment.author), comment.body)
                            } else {
                                comment.body
                            }
                            ReadingStrings.speakCommentBody(ReadingStrings.speakComment(index + 1), body)
                        }?.joinToString("\n")
            }
        }.joinToString("\n")
        .trim()
}

fun htmlToReadingText(html: String): String = Ksoup
    .parse(html)
    .also { document -> document.select("a.video-box").remove() }
    .text()

fun buildReadingTemplatePreview(preferences: ReadingPreferences): String {
    val normalized = preferences.normalized()
    return normalized.fieldOrder
        .asSequence()
        .filter { it in normalized.enabledFields }
        .mapNotNull { field ->
            when (field) {
                ReadingTemplateField.ContentType -> ReadingStrings.previewContentType
                ReadingTemplateField.Title -> ReadingStrings.previewTitle
                ReadingTemplateField.Author -> ReadingStrings.previewAuthor
                ReadingTemplateField.Body -> ReadingStrings.previewBody
                ReadingTemplateField.PublishedAt -> when (normalized.publishedTimeMode) {
                    ReadingPublishedTimeMode.Absolute -> ReadingStrings.previewPublished
                    ReadingPublishedTimeMode.Relative ->
                        ReadingStrings.previewEdited(normalized.relativeTimePrecision.displayName)
                }
                ReadingTemplateField.VoteUpCount -> ReadingStrings.previewVotes
                ReadingTemplateField.Comments ->
                    normalized.commentCount
                        .takeIf { it > 0 }
                        ?.let { count ->
                            val body = if (normalized.readCommentAuthor) {
                                ReadingStrings.speakCommentBody(ReadingStrings.previewCommentAuthor, ReadingStrings.previewCommentBody)
                            } else {
                                ReadingStrings.previewCommentBody
                            }
                            ReadingStrings.speakCommentBody(ReadingStrings.previewComment(count), body)
                        }
            }
        }.joinToString("\n")
        .trim()
}

fun renderReadingTransition(
    template: String,
    nextItem: ReadingQueueItem,
    nextIndex: Int,
    total: Int,
): String {
    val author = nextItem.author.cleanReadingMetadata()
    val normalizedTemplate = if (author.isBlank()) {
        template.replace(
            Regex("(?:[，,]\\s*)?(?:作者|author)\\s*[：:]?\\s*\\{author}", RegexOption.IGNORE_CASE),
            "",
        )
    } else {
        template
    }
    return normalizedTemplate
        .replace("{index}", (nextIndex + 1).toString())
        .replace("{total}", total.toString())
        .replace("{contentType}", nextItem.contentType.displayName)
        .replace("{title}", nextItem.displayTitle)
        .replace("{author}", author)
        .replace("，。", "。")
        .replace(",。", "。")
        .trim()
}

fun ReadingQueueItem.hasReadableFields(preferences: ReadingPreferences): Boolean {
    val normalized = preferences.normalized()
    return normalized.enabledFields.any { field ->
        when (field) {
            ReadingTemplateField.ContentType -> true
            ReadingTemplateField.Title -> title.cleanReadingMetadata().isNotBlank()
            ReadingTemplateField.Author -> author.cleanReadingMetadata().isNotBlank()
            ReadingTemplateField.Body -> !bodyHtml.isNullOrBlank()
            ReadingTemplateField.PublishedAt -> when (normalized.publishedTimeMode) {
                ReadingPublishedTimeMode.Absolute -> publishedAt > 0
                ReadingPublishedTimeMode.Relative -> updatedAt > 0
            }
            ReadingTemplateField.VoteUpCount -> voteUpCount >= 0
            ReadingTemplateField.Comments -> normalized.shouldLoadComments
        }
    }
}

/**
 * Android TextToSpeech.stop() 无法从语句中途恢复，因此分块也是暂停与恢复的检查点。
 * 按句边界分块可减少重复朗读，[maxLength] 则限制无标点文本的块长。
 *
 * https://developer.android.com/reference/android/speech/tts/TextToSpeech#stop()
 */
fun splitReadingSpeechIntoChunks(
    text: String,
    maxLength: Int = 120,
): List<String> {
    if (text.isBlank()) return emptyList()
    require(maxLength > 0)

    val sentenceEnds = setOf('。', '！', '？', '!', '?', '…', '\n')
    val clauseEnds = setOf('，', ',', '；', ';', '：', ':')
    val sentenceClosings = setOf('”', '’', '"', '\'', '）', ')', '】', ']', '》', '〉', '」', '』')
    val chunks = mutableListOf<String>()
    var start = 0
    while (start < text.length) {
        while (start < text.length && text[start].isWhitespace()) start++
        if (start >= text.length) break

        var hardEnd = start
        var length = 0
        while (hardEnd < text.length && length < maxLength) {
            val current = text[hardEnd++]
            if (
                current in '\uD800'..'\uDBFF' &&
                hardEnd < text.length &&
                text[hardEnd] in '\uDC00'..'\uDFFF'
            ) {
                hardEnd++
            }
            length++
        }

        var sentenceEnd: Int? = null
        var cursor = start
        while (cursor < hardEnd) {
            val current = text[cursor]
            val isAsciiPeriod = current == '.' &&
                (
                    cursor + 1 >= text.length ||
                        text[cursor + 1].isWhitespace() ||
                        text[cursor + 1] in sentenceClosings
                )
            if (current in sentenceEnds || isAsciiPeriod) {
                sentenceEnd = cursor + 1
                while (
                    sentenceEnd < hardEnd &&
                    (
                        text[sentenceEnd] in sentenceEnds ||
                            text[sentenceEnd] == '.' ||
                            text[sentenceEnd] in sentenceClosings
                    )
                ) {
                    sentenceEnd++
                }
                break
            }
            cursor++
        }

        val end = sentenceEnd ?: if (hardEnd < text.length) {
            (hardEnd - 1 downTo start)
                .firstOrNull { text[it] in clauseEnds }
                ?.plus(1)
                ?: hardEnd
        } else {
            hardEnd
        }
        text
            .substring(start, end)
            .trim()
            .takeIf(String::isNotEmpty)
            ?.let(chunks::add)
        start = end
    }
    return chunks
}

object ReadingQueueSourceRegistry {
    private val sources = LinkedHashMap<String, List<ReadingQueueItem>>()

    private suspend fun lookupOpenedAnswerIds(answerIds: List<Long>): Set<Long> {
        if (answerIds.isEmpty()) return emptySet()
        return ContentOpenEventSupport
            .getAlreadyOpenedContentIds(
                database = getContentFilterDatabase(),
                content = answerIds.map { answerId -> ContentType.ANSWER to answerId.toString() },
            ).mapNotNull { key -> key.substringAfter(':', "").toLongOrNull() }
            .toSet()
    }

    fun register(
        sourceId: String,
        items: List<ReadingQueueItem>,
    ) {
        val normalizedItems = items
            .distinctBy(ReadingQueueItem::key)
            .map(ReadingQueueItem::withoutBody)
        if (normalizedItems.isEmpty()) {
            sources.remove(sourceId)
            return
        }
        if (sources[sourceId] == normalizedItems) return

        sources.remove(sourceId)
        sources[sourceId] = normalizedItems
        while (sources.size > MAX_REGISTERED_READING_SOURCES) {
            val oldestSourceId = sources.keys.first()
            sources.remove(oldestSourceId)
        }
    }

    suspend fun queueStartingAt(
        current: ReadingQueueItem,
        sourceId: String?,
        limit: Int,
        fallbackAfterCurrent: List<ReadingQueueItem> = emptyList(),
        openedAnswerIdsProvider: suspend (List<Long>) -> Set<Long> = ::lookupOpenedAnswerIds,
    ): List<ReadingQueueItem> {
        val safeLimit = limit.coerceIn(1, MAX_READING_QUEUE_SIZE)
        val source = sourceId?.let(sources::get)
        val currentIndex = source?.indexOfFirst { it.key == current.key } ?: -1
        val sourceQueue = if (source == null || currentIndex < 0) {
            listOf(current)
        } else {
            source
                .drop(currentIndex)
                // 多取一些候选，跳过已读回答后仍能填满请求的队列长度。
                .take(MAX_READING_QUEUE_SIZE)
                .map { item -> if (item.key == current.key) current else item }
        }

        val candidateAnswerIds = buildList {
            addAll(sourceQueue.drop(1))
            addAll(fallbackAfterCurrent)
        }.asSequence()
            .filter { it.contentType == ReadingContentType.Answer }
            .map(ReadingQueueItem::id)
            .distinct()
            .toList()
        val openedAnswerIds = openedAnswerIdsProvider(candidateAnswerIds)

        val filteredSourceQueue = sourceQueue.filter { item ->
            item.key == current.key ||
                item.contentType != ReadingContentType.Answer ||
                item.id !in openedAnswerIds
        }
        if (fallbackAfterCurrent.isEmpty()) {
            return filteredSourceQueue.take(safeLimit)
        }

        val filteredFallbackQueue = fallbackAfterCurrent
            .asSequence()
            .distinctBy(ReadingQueueItem::key)
            .filter { item ->
                item.contentType != ReadingContentType.Answer || item.id !in openedAnswerIds
            }.toList()
        if (source == null || currentIndex < 0) {
            return (filteredSourceQueue + filteredFallbackQueue)
                .distinctBy(ReadingQueueItem::key)
                .take(safeLimit)
        }
        val sourceContinuationKeys = filteredSourceQueue.drop(1).map(ReadingQueueItem::key)
        if (
            sourceContinuationKeys.isNotEmpty() &&
            filteredFallbackQueue.take(sourceContinuationKeys.size).map(ReadingQueueItem::key) != sourceContinuationKeys
        ) {
            return filteredSourceQueue.take(safeLimit)
        }

        return (filteredSourceQueue + filteredFallbackQueue)
            .distinctBy(ReadingQueueItem::key)
            .take(safeLimit)
    }

    internal fun clearForTesting() {
        sources.clear()
    }
}

@Composable
fun RegisterReadingQueueSource(
    sourceId: String,
    items: List<FeedDisplayItem>,
) {
    val queueItems = items
        .asSequence()
        .filterNot { it.isFiltered }
        .mapNotNull(FeedDisplayItem::toReadingQueueItem)
        .toList()
    SideEffect {
        ReadingQueueSourceRegistry.register(sourceId, queueItems)
    }
}

fun FeedDisplayItem.toReadingQueueItem(): ReadingQueueItem? {
    val destination = navDestination ?: return null
    return raw?.toReadingQueueItem(destination)
        ?: feed?.target?.toReadingQueueItem()
        ?: when (destination) {
            is Article -> ReadingQueueItem(
                contentType = when (destination.type) {
                    ArticleType.Answer -> ReadingContentType.Answer
                    ArticleType.Article -> ReadingContentType.Article
                },
                id = destination.id,
                title = title.cleanReadingMetadata().ifBlank { destination.title.cleanReadingMetadata() },
                author = authorName.cleanReadingMetadata().ifBlank { destination.authorName.cleanReadingMetadata() },
            )
            is Pin -> ReadingQueueItem(
                contentType = ReadingContentType.Pin,
                id = destination.id,
                author = authorName.cleanReadingMetadata().ifBlank { destination.authorName.cleanReadingMetadata() },
            )
            is Question -> ReadingQueueItem(
                contentType = ReadingContentType.Question,
                id = destination.questionId,
                title = title.cleanReadingMetadata().ifBlank { destination.title.cleanReadingMetadata() },
            )
            else -> null
        }
}

private fun Feed.Target.toReadingQueueItem(): ReadingQueueItem? = when (this) {
    is Feed.AnswerTarget -> ReadingQueueItem(
        contentType = ReadingContentType.Answer,
        id = id,
        title = question.title,
        author = author?.name.orEmpty(),
        questionId = question.id,
        bodyHtml = content.takeIf(String::isNotBlank),
        publishedAt = createdTime,
        updatedAt = updatedTime,
        voteUpCount = voteupCount,
        commentCount = commentCount,
    )
    is Feed.ArticleTarget -> ReadingQueueItem(
        contentType = ReadingContentType.Article,
        id = id,
        title = title,
        author = author.name,
        bodyHtml = content.takeIf(String::isNotBlank),
        publishedAt = created,
        updatedAt = updated,
        voteUpCount = voteupCount,
        commentCount = commentCount,
    )
    is Feed.PinTarget -> ReadingQueueItem(
        contentType = ReadingContentType.Pin,
        id = id,
        author = author.name,
        bodyHtml = contentHtml.takeIf(String::isNotBlank),
        publishedAt = created,
        updatedAt = updated,
        voteUpCount = likeCount,
        commentCount = commentCount,
    )
    is Feed.QuestionTarget -> ReadingQueueItem(
        contentType = ReadingContentType.Question,
        id = id,
        title = title,
        bodyHtml = detail.takeIf(String::isNotBlank),
        publishedAt = created,
        updatedAt = updatedTime,
        commentCount = commentCount,
    )
    else -> null
}

fun DataHolder.Content.toReadingQueueItem(destination: NavDestination): ReadingQueueItem? = when (this) {
    is DataHolder.Answer -> ReadingQueueItem(
        contentType = ReadingContentType.Answer,
        id = id,
        title = question.title,
        author = author.name,
        questionId = question.id,
        bodyHtml = content,
        publishedAt = createdTime,
        updatedAt = updatedTime,
        voteUpCount = voteupCount,
        commentCount = commentCount,
    )
    is DataHolder.Article -> ReadingQueueItem(
        contentType = ReadingContentType.Article,
        id = id,
        title = title,
        author = author.name,
        bodyHtml = content,
        publishedAt = created,
        updatedAt = updated,
        voteUpCount = voteupCount,
        commentCount = commentCount,
    )
    is DataHolder.Pin -> ReadingQueueItem(
        contentType = ReadingContentType.Pin,
        id = id.toLongOrNull() ?: (destination as? Pin)?.id ?: return null,
        author = author.name,
        bodyHtml = contentHtml.ifBlank {
            content
                .filterIsInstance<DataHolder.Pin.ContentText>()
                .joinToString("\n") { item -> item.content }
        },
        publishedAt = created,
        updatedAt = updated,
        voteUpCount = likeCount,
        commentCount = commentCount,
    )
    is DataHolder.Question -> ReadingQueueItem(
        contentType = ReadingContentType.Question,
        id = id,
        title = title,
        author = author.name,
        bodyHtml = detail,
        publishedAt = created,
        updatedAt = updatedTime,
        voteUpCount = voteupCount,
        commentCount = commentCount,
    )
    else -> null
}

private fun formatReadingDateTime(seconds: Long): String {
    val dateTime = Instant.fromEpochSeconds(seconds).toLocalDateTime(TimeZone.currentSystemDefault())
    val english = AppLocale.current == AppLanguage.English
    val month = if (english) {
        dateTime.month.name
            .lowercase()
            .replaceFirstChar(Char::uppercaseChar)
    } else {
        dateTime.month.number.toString()
    }
    val minute = if (english) dateTime.minute.twoDigitString() else dateTime.minute.toString()
    return ReadingStrings.dateTime(dateTime.year, month, dateTime.day, dateTime.hour, minute)
}

private fun formatReadingRelativeTime(
    updatedAt: Long,
    nowEpochSeconds: Long,
    precision: ReadingRelativeTimePrecision,
): String {
    val difference = (nowEpochSeconds - updatedAt).coerceAtLeast(0L)
    if (difference == 0L) return ReadingStrings.justNow

    var remaining = difference
    val days = remaining / 86_400
    remaining %= 86_400
    val hours = remaining / 3_600
    remaining %= 3_600
    val minutes = remaining / 60
    val seconds = remaining % 60
    val parts = buildList {
        if (days > 0) add(ReadingStrings.days(days))
        if (precision != ReadingRelativeTimePrecision.Day && hours > 0) add(ReadingStrings.hours(hours))
        if (precision == ReadingRelativeTimePrecision.Second) {
            if (minutes > 0) add(ReadingStrings.minutes(minutes))
            if (seconds > 0) add(ReadingStrings.seconds(seconds))
        }
    }
    if (parts.isEmpty()) {
        return when (precision) {
            ReadingRelativeTimePrecision.Second -> ReadingStrings.justNow
            ReadingRelativeTimePrecision.Hour -> ReadingStrings.underOneHour
            ReadingRelativeTimePrecision.Day -> ReadingStrings.underOneDay
        }
    }
    return ReadingStrings.elapsed(parts.reduce(ReadingStrings::durationPartJoin))
}

private fun String?.cleanReadingMetadata(): String {
    val value = orEmpty()
        .takeUnless { it.equals("loading...", ignoreCase = true) }
        .orEmpty()
        .trim()
    return if ('<' in value || '&' in value) Ksoup.parse(value).text().trim() else value
}
