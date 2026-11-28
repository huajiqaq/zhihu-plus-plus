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

package com.github.zly2006.zhihu.ui.subscreens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.byValue
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.github.zly2006.zhihu.i18n.SettingsStrings
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.reading.READING_PREFERENCES_KEY
import com.github.zly2006.zhihu.reading.ReadingCommentOrder
import com.github.zly2006.zhihu.reading.ReadingPreferences
import com.github.zly2006.zhihu.reading.ReadingPublishedTimeMode
import com.github.zly2006.zhihu.reading.ReadingRelativeTimePrecision
import com.github.zly2006.zhihu.reading.ReadingTemplateField
import com.github.zly2006.zhihu.reading.buildReadingTemplatePreview
import com.github.zly2006.zhihu.reading.loadReadingPreferences
import com.github.zly2006.zhihu.reading.saveReadingPreferences
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppSegmentedControl
import com.github.zly2006.zhihu.ui.components.AppSettingsPage
import com.github.zly2006.zhihu.ui.components.AppSortableCheckList
import com.github.zly2006.zhihu.ui.components.AppTextField
import com.github.zly2006.zhihu.ui.components.SettingItem
import com.github.zly2006.zhihu.ui.components.SettingItemGroup
import com.github.zly2006.zhihu.ui.components.SettingItemSelect
import com.github.zly2006.zhihu.ui.components.SettingItemWithSwitch
import com.github.zly2006.zhihu.ui.components.SortableCheckItem
import kotlinx.coroutines.launch

const val READING_PLAYBACK_SETTINGS_BACK_BUTTON_TAG = "reading_playback_settings_back_button"

const val READING_SETTINGS_SCROLL_TAG = "readingSettings.scroll"

const val READING_SETTINGS_FIELD_LIST_TAG_PREFIX = "readingSettings.fields"
const val READING_SETTINGS_PUBLISHED_TIME_MODE_TAG_PREFIX = "readingSettings.publishedTimeMode."
const val READING_SETTINGS_RELATIVE_TIME_PRECISION_TAG_PREFIX = "readingSettings.relativeTimePrecision."
const val READING_SETTINGS_COMMENT_COUNT_TAG = "readingSettings.commentCount"
const val READING_SETTINGS_COMMENT_ORDER_TAG_PREFIX = "readingSettings.commentOrder."
const val READING_SETTINGS_COMMENT_AUTHOR_TAG = "readingSettings.commentAuthor"
const val READING_SETTINGS_QUEUE_LIMIT_TAG_PREFIX = "readingSettings.queueLimit."

const val READING_SETTINGS_CUSTOM_QUEUE_LIMIT_TAG = "readingSettings.queueLimit.custom"

const val READING_SETTINGS_CUSTOM_QUEUE_LIMIT_INPUT_TAG = "readingSettings.queueLimit.customInput"
const val READING_SETTINGS_TRANSITION_TEXT_TAG = "readingSettings.transitionText"
const val READING_SETTINGS_TEMPLATE_PREVIEW_TAG = "readingSettings.templatePreview"

/** 偏好统一存于 [READING_PREFERENCES_KEY] 的 JSON；这些行键仅供深链定位，不是存储键。 */
const val READING_PLAYBACK_TEMPLATE_PREVIEW_KEY = "readingTemplatePreview"
const val READING_PLAYBACK_FIELD_ORDER_KEY = "readingFieldOrder"
const val READING_PLAYBACK_PUBLISHED_TIME_MODE_KEY = "readingPublishedTimeMode"
const val READING_PLAYBACK_RELATIVE_TIME_PRECISION_KEY = "readingRelativeTimePrecision"
const val READING_PLAYBACK_COMMENT_COUNT_KEY = "readingCommentCount"
const val READING_PLAYBACK_COMMENT_AUTHOR_KEY = "readingCommentAuthor"
const val READING_PLAYBACK_COMMENT_ORDER_KEY = "readingCommentOrder"
const val READING_PLAYBACK_QUEUE_LIMIT_KEY = "readingQueueLimit"
const val READING_PLAYBACK_TRANSITION_KEY = "readingTransition"

private val queueLimitPresets = listOf(5, 10, 20)
private const val DEFAULT_CUSTOM_QUEUE_LIMIT = 30

/** 设置保存为同一份 [ReadingPreferences]，仅在播放器创建新会话时生效。 */
@Composable
fun ReadingPlaybackSettingsScreen(setting: String = "") {
    val settings = rememberSettingsStore()
    var preferences by remember(settings) { mutableStateOf(loadReadingPreferences(settings)) }
    val effectiveTransitionText = preferences.effectiveTransitionText
    val transitionText = remember(settings) { TextFieldState(effectiveTransitionText) }
    var synchronizedTransitionText by remember(settings) { mutableStateOf(effectiveTransitionText) }
    val scope = rememberCoroutineScope()

    fun persist(next: ReadingPreferences) {
        val normalized = next.normalized()
        preferences = normalized
        saveReadingPreferences(settings, normalized)
    }

    fun saveTransitionText() {
        val text = transitionText.text.toString()
        if (text != synchronizedTransitionText) {
            synchronizedTransitionText = text
            persist(preferences.copy(transitionText = text))
        }
    }

    LaunchedEffect(effectiveTransitionText) {
        if (transitionText.text.contentEquals(synchronizedTransitionText)) {
            synchronizedTransitionText = effectiveTransitionText
            transitionText.setTextAndPlaceCursorAtEnd(effectiveTransitionText)
        }
    }
    LaunchedEffect(transitionText) {
        snapshotFlow { transitionText.text }.collect { saveTransitionText() }
    }
    DisposableEffect(settings) {
        val subscription = settings.observeKeyChanges { key ->
            if (key == READING_PREFERENCES_KEY) {
                scope.launch {
                    val saved = loadReadingPreferences(settings)
                    if (transitionText.text.contentEquals(synchronizedTransitionText)) {
                        synchronizedTransitionText = saved.effectiveTransitionText
                        transitionText.setTextAndPlaceCursorAtEnd(synchronizedTransitionText)
                    }
                    preferences = saved
                }
            }
        }
        onDispose {
            subscription.close()
            saveTransitionText()
        }
    }

    /** 已选字段在前；重新勾选追加到已选末尾，取消勾选移到未选开头。 */
    fun toggleField(field: ReadingTemplateField, checked: Boolean) {
        val checkedFields = preferences.fieldOrder.filter { it in preferences.enabledFields && it != field }
        val uncheckedFields = preferences.fieldOrder.filter { it !in preferences.enabledFields && it != field }
        persist(
            preferences.copy(
                fieldOrder = checkedFields + field + uncheckedFields,
                enabledFields = if (checked) preferences.enabledFields + field else preferences.enabledFields - field,
            ),
        )
    }

    fun moveField(field: ReadingTemplateField, offset: Int) {
        val checkedFields = preferences.fieldOrder.filter { it in preferences.enabledFields }.toMutableList()
        val from = checkedFields.indexOf(field)
        val to = from + offset
        if (from < 0 || to !in checkedFields.indices) return
        checkedFields.add(to, checkedFields.removeAt(from))
        persist(preferences.copy(fieldOrder = checkedFields + preferences.fieldOrder.filter { it !in preferences.enabledFields }))
    }

    AppSettingsPage(
        title = SettingsStrings.readingPlayback,
        onBack = LocalNavigator.current.onNavigateBack,
        backTag = READING_PLAYBACK_SETTINGS_BACK_BUTTON_TAG,
        highlightedKey = when {
            setting in setOf(READING_PLAYBACK_PUBLISHED_TIME_MODE_KEY, READING_PLAYBACK_RELATIVE_TIME_PRECISION_KEY) && ReadingTemplateField.PublishedAt !in preferences.enabledFields -> READING_PLAYBACK_FIELD_ORDER_KEY
            setting == READING_PLAYBACK_RELATIVE_TIME_PRECISION_KEY && preferences.publishedTimeMode != ReadingPublishedTimeMode.Relative -> READING_PLAYBACK_PUBLISHED_TIME_MODE_KEY
            setting in setOf(READING_PLAYBACK_COMMENT_COUNT_KEY, READING_PLAYBACK_COMMENT_AUTHOR_KEY, READING_PLAYBACK_COMMENT_ORDER_KEY) && ReadingTemplateField.Comments !in preferences.enabledFields -> READING_PLAYBACK_FIELD_ORDER_KEY
            setting in setOf(READING_PLAYBACK_COMMENT_AUTHOR_KEY, READING_PLAYBACK_COMMENT_ORDER_KEY) && preferences.commentCount == 0 -> READING_PLAYBACK_COMMENT_COUNT_KEY
            else -> setting
        },
        scrollTag = READING_SETTINGS_SCROLL_TAG,
    ) {
        SettingItemGroup(
            title = SettingsStrings.readAloudPreview,
            footer = { Text(SettingsStrings.readAloudChangesNote) },
        ) {
            SettingItem(
                title = { Text(SettingsStrings.readingTemplate) },
                description = { Text(SettingsStrings.readingTemplateDescription) },
                settingKey = READING_PLAYBACK_TEMPLATE_PREVIEW_KEY,
                bottomAction = {
                    SelectionContainer {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = AppDimens.Space12)
                                .testTag(READING_SETTINGS_TEMPLATE_PREVIEW_TAG),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        ) {
                            Text(
                                text = buildReadingTemplatePreview(preferences),
                                modifier = Modifier.padding(AppDimens.Space16),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                },
            )
        }

        val enabledInOrder = preferences.fieldOrder.filter { it in preferences.enabledFields }

        /** 仅剩零条评论会被归一化为正文，故锁住最后一个其他可读字段。 */
        val lastReadableField = enabledInOrder
            .takeIf { preferences.commentCount == 0 && it.size == 2 && ReadingTemplateField.Comments in it }
            ?.first { it != ReadingTemplateField.Comments }
        /** 列表行不接收 settingKey，故深链定位到组。 */
        SettingItemGroup(
            title = SettingsStrings.readingContent,
            footer = { Text(SettingsStrings.readingContentFooter) },
            settingKey = READING_PLAYBACK_FIELD_ORDER_KEY,
        ) {
            AppSortableCheckList(
                items = (enabledInOrder + preferences.fieldOrder.filter { it !in preferences.enabledFields }).map { field ->
                    SortableCheckItem(
                        key = field.name,
                        title = field.displayName,
                        description = field.description,
                        lockedReason = SettingsStrings.keepReadableField.takeIf { field == lastReadableField },
                    )
                },
                checkedKeys = enabledInOrder.map { it.name },
                onCheckedChange = { key, checked -> toggleField(ReadingTemplateField.valueOf(key), checked) },
                onMove = { key, offset -> moveField(ReadingTemplateField.valueOf(key), offset) },
                testTagPrefix = READING_SETTINGS_FIELD_LIST_TAG_PREFIX,
            )
        }

        val publishedTimeEnabled = ReadingTemplateField.PublishedAt in preferences.enabledFields
        SettingItemGroup(
            title = SettingsStrings.publicationTime,
            footer = if (publishedTimeEnabled) {
                null
            } else {
                { Text(SettingsStrings.publicationTimeDisabled) }
            },
        ) {
            ChoiceSettingItem(
                title = SettingsStrings.timeFormat,
                description = SettingsStrings.timeFormatDescription,
                options = ReadingPublishedTimeMode.entries,
                selected = preferences.publishedTimeMode,
                label = { it.displayName },
                onSelect = { persist(preferences.copy(publishedTimeMode = it)) },
                enabled = publishedTimeEnabled,
                testTagPrefix = READING_SETTINGS_PUBLISHED_TIME_MODE_TAG_PREFIX,
                settingKey = READING_PLAYBACK_PUBLISHED_TIME_MODE_KEY,
            )
            ChoiceSettingItem(
                title = SettingsStrings.relativeTimePrecision,
                description = SettingsStrings.relativeTimePrecisionDescription,
                options = ReadingRelativeTimePrecision.entries,
                selected = preferences.relativeTimePrecision,
                label = { it.displayName },
                onSelect = { persist(preferences.copy(relativeTimePrecision = it)) },
                enabled = publishedTimeEnabled && preferences.publishedTimeMode == ReadingPublishedTimeMode.Relative,
                testTagPrefix = READING_SETTINGS_RELATIVE_TIME_PRECISION_TAG_PREFIX,
                settingKey = READING_PLAYBACK_RELATIVE_TIME_PRECISION_KEY,
            )
        }

        val commentsEnabled = ReadingTemplateField.Comments in preferences.enabledFields
        val commentDetailsEnabled = commentsEnabled && preferences.commentCount > 0
        SettingItemGroup(
            title = SettingsStrings.comments,
            footer = {
                Text(
                    if (commentsEnabled) {
                        SettingsStrings.commentsLoadNote
                    } else {
                        SettingsStrings.commentsDisabled
                    },
                )
            },
        ) {
            SettingItem(
                title = { Text(SettingsStrings.readCommentCount) },
                description = { Text(SettingsStrings.readCommentCountDescription) },
                enabled = commentsEnabled,
                settingKey = READING_PLAYBACK_COMMENT_COUNT_KEY,
                bottomAction = {
                    NumberSettingField(
                        value = preferences.commentCount,
                        onCommit = { count ->
                            persist(preferences.copy(commentCount = count))
                            preferences.commentCount
                        },
                        maxDigits = 2,
                        labelText = SettingsStrings.commentCount,
                        enabled = commentsEnabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = AppDimens.Space12)
                            .testTag(READING_SETTINGS_COMMENT_COUNT_TAG),
                    )
                },
            )
            SettingItemWithSwitch(
                title = { Text(SettingsStrings.readCommentAuthor) },
                description = { Text(SettingsStrings.readCommentAuthorDescription) },
                checked = preferences.readCommentAuthor,
                onCheckedChange = { persist(preferences.copy(readCommentAuthor = it)) },
                enabled = commentDetailsEnabled,
                modifier = Modifier.testTag(READING_SETTINGS_COMMENT_AUTHOR_TAG),
                settingKey = READING_PLAYBACK_COMMENT_AUTHOR_KEY,
            )
            ChoiceSettingItem(
                title = SettingsStrings.commentOrder,
                description = SettingsStrings.commentOrderDescription,
                options = ReadingCommentOrder.entries,
                selected = preferences.commentOrder,
                label = { it.displayName },
                onSelect = { persist(preferences.copy(commentOrder = it)) },
                enabled = commentDetailsEnabled,
                testTagPrefix = READING_SETTINGS_COMMENT_ORDER_TAG_PREFIX,
                settingKey = READING_PLAYBACK_COMMENT_ORDER_KEY,
            )
        }

        SettingItemGroup(title = SettingsStrings.continuousPlayback) {
            val presetIndex = queueLimitPresets.indexOf(preferences.queueLimit)
            val customSelected = presetIndex < 0
            SettingItem(
                title = { Text(SettingsStrings.queueLimit) },
                description = { Text(SettingsStrings.queueLimitDescription) },
                settingKey = READING_PLAYBACK_QUEUE_LIMIT_KEY,
                bottomAction = {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = AppDimens.Space12)) {
                        AppSegmentedControl(
                            options = queueLimitPresets.map { SettingsStrings.queueItemCount(it) } + SettingsStrings.custom,
                            selectedIndex = if (customSelected) queueLimitPresets.size else presetIndex,
                            onSelect = { index ->
                                val preset = queueLimitPresets.getOrNull(index)
                                if (preset != null) {
                                    persist(preferences.copy(queueLimit = preset))
                                } else if (preferences.queueLimit in queueLimitPresets) {
                                    persist(preferences.copy(queueLimit = DEFAULT_CUSTOM_QUEUE_LIMIT))
                                }
                            },
                            testTags = queueLimitPresets.map { READING_SETTINGS_QUEUE_LIMIT_TAG_PREFIX + it } +
                                READING_SETTINGS_CUSTOM_QUEUE_LIMIT_TAG,
                        )
                        AnimatedVisibility(visible = customSelected) {
                            NumberSettingField(
                                value = preferences.queueLimit,
                                onCommit = { limit ->
                                    /** 收起前可能再提交；已选预设时不得被旧输入覆盖。 */
                                    if (preferences.queueLimit !in queueLimitPresets) {
                                        persist(preferences.copy(queueLimit = limit))
                                    }
                                    preferences.queueLimit
                                },
                                maxDigits = 3,
                                labelText = SettingsStrings.customQueueLimit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = AppDimens.Space8)
                                    .testTag(READING_SETTINGS_CUSTOM_QUEUE_LIMIT_INPUT_TAG),
                            )
                        }
                    }
                },
            )
        }

        SettingItemGroup(
            title = SettingsStrings.itemTransition,
            footer = { Text(SettingsStrings.transitionPlaceholders) },
        ) {
            SettingItem(
                title = { Text(SettingsStrings.transitionBetweenItems) },
                description = { Text(SettingsStrings.transitionDescription) },
                settingKey = READING_PLAYBACK_TRANSITION_KEY,
                bottomAction = {
                    AppTextField(
                        state = transitionText,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = AppDimens.Space12)
                            .testTag(READING_SETTINGS_TRANSITION_TEXT_TAG),
                        lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 3),
                        labelText = SettingsStrings.transitionText,
                    )
                },
            )
        }
    }
}

@Composable
private fun <T : Enum<T>> ChoiceSettingItem(
    title: String,
    description: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    enabled: Boolean,
    testTagPrefix: String,
    settingKey: String,
) {
    if (options.size <= 3) {
        SettingItem(
            title = { Text(title) },
            description = { Text(description) },
            enabled = enabled,
            settingKey = settingKey,
            bottomAction = {
                AppSegmentedControl(
                    options = options.map(label),
                    selectedIndex = options.indexOf(selected),
                    onSelect = { onSelect(options[it]) },
                    modifier = Modifier.padding(top = AppDimens.Space12),
                    testTags = options.map { testTagPrefix + it.name },
                    enabled = enabled,
                )
            },
        )
    } else {
        SettingItemSelect(
            title = { Text(title) },
            description = { Text(description) },
            options = options.map(label),
            selectedIndex = options.indexOf(selected),
            onSelect = { onSelect(options[it]) },
            enabled = enabled,
            settingKey = settingKey,
        )
    }
}

/** [onCommit] 返回实际保存值，避免归一化后仍显示越界输入。 */
@Composable
private fun NumberSettingField(
    value: Int,
    onCommit: (Int) -> Int,
    maxDigits: Int,
    labelText: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val input = remember { TextFieldState(value.toString()) }
    var lastCommitted by remember { mutableIntStateOf(value) }
    LaunchedEffect(value) {
        if (value != lastCommitted && input.text.contentEquals(lastCommitted.toString())) {
            input.setTextAndPlaceCursorAtEnd(value.toString())
        }
        lastCommitted = value
    }
    val currentValue by rememberUpdatedState(value)
    val currentEnabled by rememberUpdatedState(enabled)
    val currentOnCommit by rememberUpdatedState(onCommit)

    fun commit() {
        val text = input.text.toString()
        if (text == lastCommitted.toString()) return
        lastCommitted = if (currentEnabled) text.toIntOrNull()?.let(currentOnCommit) ?: currentValue else currentValue
        if (text != lastCommitted.toString()) input.setTextAndPlaceCursorAtEnd(lastCommitted.toString())
    }

    /** 离页不保证先失焦，故在退场时补交输入。 */
    DisposableEffect(Unit) {
        onDispose { commit() }
    }
    val focusManager = LocalFocusManager.current
    AppTextField(
        state = input,
        inputTransformation = InputTransformation.byValue { _, proposed ->
            proposed.filter(Char::isDigit).take(maxDigits)
        },
        modifier = modifier.onFocusChanged { if (!it.isFocused) commit() },
        enabled = enabled,
        labelText = labelText,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        onKeyboardAction = {
            commit()
            focusManager.clearFocus()
        },
    )
}
