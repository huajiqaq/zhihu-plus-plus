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

package com.github.zly2006.zhihu.platform

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 设置的逻辑值；备份、平台适配和调用方共享同一类型，不由字符串内容猜测类型。 */
@Serializable
sealed interface SettingValue {
    @Serializable
    @SerialName("boolean")
    data class BooleanValue(val value: Boolean) : SettingValue

    @Serializable
    @SerialName("string")
    data class StringValue(val value: String) : SettingValue

    @Serializable
    @SerialName("int")
    data class IntValue(val value: Int) : SettingValue

    @Serializable
    @SerialName("long")
    data class LongValue(val value: Long) : SettingValue

    /** 按位保存，NaN、无穷与负零也能经过标准 JSON 无损往返。 */
    @Serializable
    @SerialName("float")
    data class FloatValue(val bits: Int) : SettingValue {
        val value: Float get() = Float.fromBits(bits)
    }

    @Serializable
    @SerialName("stringSet")
    data class StringSetValue(val value: Set<String>) : SettingValue
}

/** 平台只负责存取与通知；类型转换、默认值和快照由 common 唯一实现。 */
abstract class SettingsStore {
    protected abstract fun readValue(key: String): SettingValue?

    /** null 删除键；整批写入结束后才通知变化键，原子性与落盘保证由平台存储提供。 */
    protected abstract fun commit(updates: Map<String, SettingValue?>)

    protected abstract fun readEntries(): Map<String, SettingValue>

    fun remove(key: String) = commit(mapOf(key to null))

    abstract fun observeKeyChanges(onChanged: (String) -> Unit): AutoCloseable

    fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        (readValue(key) as? SettingValue.BooleanValue)?.value ?: defaultValue

    fun putBoolean(key: String, value: Boolean) = commit(mapOf(key to SettingValue.BooleanValue(value)))

    fun getString(key: String, defaultValue: String): String = getStringOrNull(key) ?: defaultValue

    fun getStringOrNull(key: String): String? = (readValue(key) as? SettingValue.StringValue)?.value

    fun putString(key: String, value: String) = commit(mapOf(key to SettingValue.StringValue(value)))

    fun getStringSet(key: String, defaultValue: Set<String>): Set<String> =
        (readValue(key) as? SettingValue.StringSetValue)?.value?.toSet() ?: defaultValue

    fun putStringSet(key: String, value: Set<String>) = commit(mapOf(key to SettingValue.StringSetValue(value.toSet())))

    fun getInt(key: String, defaultValue: Int): Int =
        (readValue(key) as? SettingValue.IntValue)?.value ?: defaultValue

    fun putInt(key: String, value: Int) = commit(mapOf(key to SettingValue.IntValue(value)))

    fun getLong(key: String, defaultValue: Long): Long =
        (readValue(key) as? SettingValue.LongValue)?.value ?: defaultValue

    fun putLong(key: String, value: Long) = commit(mapOf(key to SettingValue.LongValue(value)))

    fun getFloat(key: String, defaultValue: Float): Float =
        (readValue(key) as? SettingValue.FloatValue)?.value ?: defaultValue

    fun putFloat(key: String, value: Float) = commit(mapOf(key to SettingValue.FloatValue(value.toRawBits())))

    fun snapshot(): Map<String, SettingValue> = readEntries()

    fun restore(entries: Map<String, SettingValue>) = commit(entries.mapValues { (_, value) ->
        if (value is SettingValue.StringSetValue) value.copy(value = value.value.toSet()) else value
    })

    open fun removeByPrefix(prefix: String) = commit(
        readEntries().keys.filter { it.startsWith(prefix) }.associateWith { null },
    )
}

/** 只供旧 properties / NSUserDefaults 读取：普通未知文本不猜类型，新类型化值不经过此处。 */
internal fun decodeLegacySetting(key: String, text: String): SettingValue =
    legacySettingType(key)?.decode(text) ?: SettingValue.StringValue(text)

private enum class SettingType {
    Boolean,
    Int,
    Long,
    Float,
    StringSet;

    fun decode(text: String): SettingValue? = when (this) {
        Boolean -> text.toBooleanStrictOrNull()?.let { SettingValue.BooleanValue(it) }
        Int -> text.toIntOrNull()?.let { SettingValue.IntValue(it) }
        Long -> text.toLongOrNull()?.let { SettingValue.LongValue(it) }
        Float -> text.toFloatOrNull()?.let { SettingValue.FloatValue(it.toRawBits()) }
        StringSet -> SettingValue.StringSetValue(text.split('\u001F').filter(String::isNotEmpty).toSet())
    }
}

private val legacySettingTypes: Map<String, SettingType> = buildMap {
    listOf(
        "allowTelemetry", "amoledTheme", "appBlurEnabled", "autoHideArticleBottomBar", "autoHideBottomBar",
        "autoHideSkipAnswerButton", "autoRefreshHomeOnStartup", "auto_mark_notifications_read", "blockPaidContent",
        "blockWeChatOfficialAccount", "blockZhihuAdPlatform", "blockZhihuSchool", "bottomBarTapScrollToTop",
        "buttonSkipAnswer", "checkNightlyUpdates", "commentSyncToPinDefault", "disableBottomSheetRoundedCorners",
        "enableAigcMarking", "enableContentFilter", "enableKeywordBlocking", "enableNLPBlocking",
        "enableTopicBlocking", "enableUserBlocking", "enable_predictive_back", "filterFollowedUserContent",
        "floatingBottomBar", "historyRecordingEnabled", "landscapeListDetail", "loginForRecommendation",
        "macosQuitOnWindowClose", "newUiNoticeShown", "pageTurnSwitchAnswer", "pinAnswerDate", "reverseBlock", "showContentEndMarker",
        "showCreateFab", "showFeedThumbnail", "showPageTurnFab", "showPageTurnGuide", "showRefreshFab",
        "showSearchHistory", "showSearchHotSearch", "show_unread_badge", "tiqian_markdown", "titleAutoHide",
        "useDynamicColor", "volumeKeyPageTurn", "webviewHardwareAcceleration", "webviewRenderLegacy",
        "zhPlusAuthorCommentPolicyAcknowledged",
    ).forEach { put(it, SettingType.Boolean) }
    listOf(
        "answerVoteupThreshold", "articleFollowersThreshold", "articleVoteupThreshold", "backgroundColorDark",
        "backgroundColorLight", "contentBlockSpacing", "contentFontSize", "contentLineHeight",
        "continuousUsageReminderIntervalMinutes", "customThemeColor", "fabOpacity", "luotianyi_color", "pageTurnPercent",
        "questionAnswerThreshold", "questionFollowersThreshold", "topicBlockingThreshold",
        "videoFollowersThreshold", "videoVoteThreshold",
    ).forEach { put(it, SettingType.Int) }
    listOf("lastRemoteHistorySync", "remoteHistoryReadTime").forEach { put(it, SettingType.Long) }
    listOf("answerSwitchSensitivity", "continuousReadingPlaybackSpeed", "landscape_list_pane_ratio", "nlpSimilarityThreshold").forEach { put(it, SettingType.Float) }
    listOf("bottom_bar_items", "onlineHomeNotificationReadUuids").forEach { put(it, SettingType.StringSet) }
}

private fun legacySettingType(key: String): SettingType? = legacySettingTypes[key] ?: when {
    key.startsWith("system_notification_") || key.startsWith("display_in_app_") || key.startsWith("readHomePinAnnouncement_") -> SettingType.Boolean
    key.startsWith("creation.") && (key.endsWith(".rich") || key.endsWith(".toc")) -> SettingType.Boolean
    listOf("buttonSkipAnswer", "collectionRandomRefresh", "homeUtilityFabs", "hotListRefresh", "searchRefresh", "fabPageTurn", "fabPageTurnComment", "readingPlayerCompact")
        .any { key.startsWith("$it-") } && (key.endsWith("-x") || key.endsWith("-y") || key.endsWith("-yFraction")) -> SettingType.Float
    else -> null
}

/** 版本头只在整个文件起始识别，不解释任何用户字符串的前缀。 */
internal object SettingsFileCodec {
    private const val HEADER_PREFIX = "#Zhihu++ typed settings "
    private const val HEADER = "${HEADER_PREFIX}1\n"

    fun encode(values: Map<String, SettingValue>): String = HEADER + Json.encodeToString(values)

    /** null 表示原有 Properties 文件；已标记但损坏或未知版本必须失败，不能当空设置覆盖。 */
    fun decode(text: String): Map<String, SettingValue>? {
        if (!text.startsWith(HEADER_PREFIX)) return null
        require(text.startsWith(HEADER)) { "Unsupported settings file version" }
        return Json.decodeFromString<Map<String, SettingValue>>(text.removePrefix(HEADER))
    }
}
