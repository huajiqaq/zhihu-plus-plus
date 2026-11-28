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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import com.github.zly2006.zhihu.filter.ContentFilterStats
import com.github.zly2006.zhihu.filter.loadFilterStats
import com.github.zly2006.zhihu.i18n.SettingsStrings
import com.github.zly2006.zhihu.navigation.Account
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.platform.SettingsStore
import com.github.zly2006.zhihu.platform.isBlocklistNlpSupported
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.ui.components.AppAlertDialog
import com.github.zly2006.zhihu.ui.components.AppDialog
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppSettingsPage
import com.github.zly2006.zhihu.ui.components.AppSpinner
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.ui.components.AppTextField
import com.github.zly2006.zhihu.ui.components.LocalSettingsDeepLink
import com.github.zly2006.zhihu.ui.components.SettingItem
import com.github.zly2006.zhihu.ui.components.SettingItemGroup
import com.github.zly2006.zhihu.ui.components.SettingItemSelect
import com.github.zly2006.zhihu.ui.components.SettingItemWithSwitch
import com.github.zly2006.zhihu.ui.rememberObservedSetting
import com.github.zly2006.zhihu.ui.rememberSetting
import com.github.zly2006.zhihu.util.AppLog
import com.github.zly2006.zhihu.viewmodel.ANSWER_VOTEUP_THRESHOLD_PREFERENCE_KEY
import com.github.zly2006.zhihu.viewmodel.ARTICLE_FOLLOWERS_THRESHOLD_PREFERENCE_KEY
import com.github.zly2006.zhihu.viewmodel.ARTICLE_VOTEUP_THRESHOLD_PREFERENCE_KEY
import com.github.zly2006.zhihu.viewmodel.LoadState
import com.github.zly2006.zhihu.viewmodel.QUALITY_FILTER_MODE_PREFERENCE_KEY
import com.github.zly2006.zhihu.viewmodel.QUESTION_ANSWER_THRESHOLD_PREFERENCE_KEY
import com.github.zly2006.zhihu.viewmodel.QUESTION_FOLLOWERS_THRESHOLD_PREFERENCE_KEY
import com.github.zly2006.zhihu.viewmodel.QualityFilterMode
import com.github.zly2006.zhihu.viewmodel.VIDEO_FOLLOWERS_THRESHOLD_PREFERENCE_KEY
import com.github.zly2006.zhihu.viewmodel.VIDEO_VOTE_THRESHOLD_PREFERENCE_KEY
import com.github.zly2006.zhihu.viewmodel.filter.getContentFilterDatabase
import com.github.zly2006.zhihu.viewmodel.readyValue
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

const val CONTENT_FILTER_SETTINGS_BACK_BUTTON_TAG = "content_filter_settings_back_button"

private class ThresholdSetting(
    val key: String,
    val title: String,
    val defaultValue: Int,
    val description: String? = null,
    val minimum: Int = 0,
)

private val commonQualityThresholds
    get() = listOf(
        ThresholdSetting(ANSWER_VOTEUP_THRESHOLD_PREFERENCE_KEY, SettingsStrings.answerMinimumVotes, 10, SettingsStrings.answerMinimumVotesDescription),
        ThresholdSetting(ARTICLE_VOTEUP_THRESHOLD_PREFERENCE_KEY, SettingsStrings.articleMinimumVotes, 20, SettingsStrings.articleMinimumVotesDescription),
    )
private val moreQualityThresholds
    get() = listOf(
        ThresholdSetting(ARTICLE_FOLLOWERS_THRESHOLD_PREFERENCE_KEY, SettingsStrings.articleMinimumFollowers, 50),
        ThresholdSetting(VIDEO_VOTE_THRESHOLD_PREFERENCE_KEY, SettingsStrings.videoMinimumVotes, 20),
        ThresholdSetting(VIDEO_FOLLOWERS_THRESHOLD_PREFERENCE_KEY, SettingsStrings.videoMinimumFollowers, 50),
        ThresholdSetting(QUESTION_ANSWER_THRESHOLD_PREFERENCE_KEY, SettingsStrings.questionMinimumAnswers, 0),
        ThresholdSetting(QUESTION_FOLLOWERS_THRESHOLD_PREFERENCE_KEY, SettingsStrings.questionMinimumFollowers, 50),
    )
private val topicThreshold
    get() = ThresholdSetting(
        key = "topicBlockingThreshold",
        title = SettingsStrings.topicBlockingThreshold,
        defaultValue = 1,
        description = SettingsStrings.topicBlockingThresholdDescription,
        minimum = 1,
    )
private val allThresholds
    get() = commonQualityThresholds + moreQualityThresholds + topicThreshold

@Composable
fun ContentFilterSettingsScreen(
    setting: String? = null,
) {
    val navigator = LocalNavigator.current
    val coroutineScope = rememberCoroutineScope()
    val settings = rememberSettingsStore()
    val userMessages = rememberUserMessageSink()
    val contentFilterDao = remember { getContentFilterDatabase().contentFilterDao() }
    var enableContentFilter by rememberSetting(settings, "enableContentFilter", true)

    var statsReload by remember { mutableIntStateOf(0) }
    var stats by remember { mutableStateOf<LoadState<ContentFilterStats>>(LoadState.Loading) }
    LaunchedEffect(statsReload) {
        stats = LoadState.Loading
        stats = try {
            LoadState.Ready(contentFilterDao.loadFilterStats())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.e("ContentFilterSettingsScreen", "Failed to load filter stats", e)
            LoadState.Failed { e.message ?: e.toString() }
        }
    }

    var editingThresholdKey by rememberSaveable { mutableStateOf<String?>(null) }
    var showClearExposureDialog by rememberSaveable { mutableStateOf(false) }
    var clearingExposure by remember { mutableStateOf(false) }
    var clearExposureError by remember { mutableStateOf<(() -> String)?>(null) }

    AppSettingsPage(
        title = SettingsStrings.contentFilter,
        onBack = navigator.onNavigateBack,
        backTag = CONTENT_FILTER_SETTINGS_BACK_BUTTON_TAG,
        highlightedKey = when {
            setting == "topicBlockingThreshold" && !settings.getBoolean("enableTopicBlocking", true) -> "enableTopicBlocking"
            setting == "filterFollowedUserContent" && !enableContentFilter -> "enableContentFilter"
            setting in (commonQualityThresholds + moreQualityThresholds).map { it.key } && settings.getString(QUALITY_FILTER_MODE_PREFERENCE_KEY, QualityFilterMode.RULES.name) == QualityFilterMode.OFF.name -> QUALITY_FILTER_MODE_PREFERENCE_KEY
            else -> setting.orEmpty()
        },
        pageTurnEnabled = editingThresholdKey == null && !showClearExposureDialog,
        scrollTag = "contentFilterSettings:scroll",
    ) {
        SettingItemGroup(title = SettingsStrings.filterOverview) {
            FilterOverviewRow(
                filterEnabled = enableContentFilter,
                stats = stats,
                onRetry = { statsReload++ },
            )
            SettingItem(
                modifier = Modifier.testTag("contentFilterSettings:blockedFeedHistory"),
                title = { Text(SettingsStrings.blockedHistory) },
                description = { Text(SettingsStrings.blockedHistoryDescription) },
                onClick = { navigator.onNavigate(Account.RecommendSettings.BlockedFeedHistory) },
            )
        }

        val deepLink = LocalSettingsDeepLink.current
        var moreThresholdsExpanded by rememberSaveable {
            mutableStateOf(deepLink?.highlightedKey?.let { key -> moreQualityThresholds.any { it.key == key } } == true)
        }
        var qualityModeName by rememberSetting(settings, QUALITY_FILTER_MODE_PREFERENCE_KEY, QualityFilterMode.RULES.name)
        val qualityMode = QualityFilterMode.entries.firstOrNull { it.name == qualityModeName } ?: QualityFilterMode.RULES
        val qualityModeOptions = QualityFilterMode.entries.map { it to it.label }
        val thresholdsEnabled = qualityMode != QualityFilterMode.OFF
        val disabledNote = SettingsStrings.qualityFilterDisabled
        SettingItemGroup(title = SettingsStrings.qualityFilter) {
            SettingItemSelect(
                title = { Text(SettingsStrings.qualityBlocking) },
                description = { Text(SettingsStrings.qualityBlockingDescription) },
                options = qualityModeOptions.map { it.second },
                selectedIndex = qualityModeOptions.indexOfFirst { it.first == qualityMode },
                onSelect = { index -> qualityModeName = qualityModeOptions[index].first.name },
                settingKey = QUALITY_FILTER_MODE_PREFERENCE_KEY,
            )
            commonQualityThresholds.forEach { threshold ->
                ThresholdRow(
                    threshold = threshold,
                    settings = settings,
                    enabled = thresholdsEnabled,
                    disabledNote = disabledNote,
                    onClick = { editingThresholdKey = threshold.key },
                )
            }
            SettingItem(
                title = { Text(SettingsStrings.moreThresholds) },
                description = { Text(if (thresholdsEnabled) SettingsStrings.moreThresholdsDescription else disabledNote) },
                endAction = {
                    Text(
                        if (moreThresholdsExpanded) SettingsStrings.collapse else SettingsStrings.expand,
                        modifier = Modifier.padding(horizontal = AppDimens.Space16),
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
                onClick = { moreThresholdsExpanded = !moreThresholdsExpanded },
            )
            moreQualityThresholds.forEach { threshold ->
                AnimatedVisibility(visible = moreThresholdsExpanded) {
                    ThresholdRow(
                        threshold = threshold,
                        settings = settings,
                        enabled = thresholdsEnabled,
                        disabledNote = disabledNote,
                        onClick = { editingThresholdKey = threshold.key },
                    )
                }
            }
        }

        SettingItemGroup(title = SettingsStrings.contentDeduplication) {
            SettingItemWithSwitch(
                modifier = Modifier.testTag("contentFilterSettings:enableContentFilter"),
                title = { Text(SettingsStrings.smartContentFilter) },
                description = { Text(SettingsStrings.smartContentFilterDescription) },
                checked = enableContentFilter,
                onCheckedChange = { enableContentFilter = it },
                settingKey = "enableContentFilter",
            )
            var filterFollowedUserContent by rememberSetting(settings, "filterFollowedUserContent", false)
            SettingItemWithSwitch(
                modifier = Modifier.testTag("contentFilterSettings:filterFollowedUserContent"),
                title = { Text(SettingsStrings.filterFollowedUsers) },
                description = {
                    Text(
                        if (enableContentFilter) {
                            SettingsStrings.filterFollowedUsersDescription
                        } else {
                            SettingsStrings.requiresSmartFilter
                        },
                    )
                },
                checked = filterFollowedUserContent,
                onCheckedChange = { filterFollowedUserContent = it },
                enabled = enableContentFilter,
                settingKey = "filterFollowedUserContent",
            )
        }

        SettingItemGroup(title = SettingsStrings.blockingRules) {
            var enableKeywordBlocking by rememberSetting(settings, "enableKeywordBlocking", true)
            SettingItemWithSwitch(
                title = { Text(SettingsStrings.enableKeywordBlocking) },
                description = { Text(SettingsStrings.keywordBlockingDescription) },
                checked = enableKeywordBlocking,
                onCheckedChange = { enableKeywordBlocking = it },
                settingKey = "enableKeywordBlocking",
            )
            var enableUserBlocking by rememberSetting(settings, "enableUserBlocking", true)
            SettingItemWithSwitch(
                title = { Text(SettingsStrings.enableUserBlocking) },
                description = { Text(SettingsStrings.userBlockingDescription) },
                checked = enableUserBlocking,
                onCheckedChange = { enableUserBlocking = it },
                settingKey = "enableUserBlocking",
            )
            var enableTopicBlocking by rememberSetting(settings, "enableTopicBlocking", true)
            SettingItemWithSwitch(
                title = { Text(SettingsStrings.enableTopicBlocking) },
                description = { Text(SettingsStrings.topicBlockingDescription) },
                checked = enableTopicBlocking,
                onCheckedChange = { enableTopicBlocking = it },
                settingKey = "enableTopicBlocking",
            )
            AnimatedVisibility(visible = enableTopicBlocking) {
                ThresholdRow(
                    threshold = topicThreshold,
                    settings = settings,
                    enabled = true,
                    disabledNote = "",
                    onClick = { editingThresholdKey = topicThreshold.key },
                )
            }
            SettingItem(
                modifier = Modifier.testTag("contentFilterSettings:blocklist"),
                title = { Text(SettingsStrings.manageBlocklist) },
                description = {
                    Text(if (isBlocklistNlpSupported) SettingsStrings.manageBlocklistNlpDescription else SettingsStrings.manageBlocklistDescription)
                },
                onClick = { navigator.onNavigate(Account.RecommendSettings.Blocklist) },
            )
        }

        SettingItemGroup(title = SettingsStrings.promotionalContent) {
            var blockZhihuAdPlatform by rememberSetting(settings, "blockZhihuAdPlatform", true)
            SettingItemWithSwitch(
                title = { Text(SettingsStrings.blockAdPlatform) },
                description = { Text(SettingsStrings.blockAdPlatformDescription) },
                checked = blockZhihuAdPlatform,
                onCheckedChange = { blockZhihuAdPlatform = it },
                settingKey = "blockZhihuAdPlatform",
            )
            var blockZhihuSchool by rememberSetting(settings, "blockZhihuSchool", true)
            SettingItemWithSwitch(
                title = { Text(SettingsStrings.blockZhihuSchool) },
                description = { Text(SettingsStrings.blockZhihuSchoolDescription) },
                checked = blockZhihuSchool,
                onCheckedChange = { blockZhihuSchool = it },
                settingKey = "blockZhihuSchool",
            )
            var blockWeChatOfficialAccount by rememberSetting(settings, "blockWeChatOfficialAccount", true)
            SettingItemWithSwitch(
                title = { Text(SettingsStrings.blockWechatArticles) },
                description = { Text(SettingsStrings.blockWechatArticlesDescription) },
                checked = blockWeChatOfficialAccount,
                onCheckedChange = { blockWeChatOfficialAccount = it },
                settingKey = "blockWeChatOfficialAccount",
            )
            var blockPaidContent by rememberSetting(settings, "blockPaidContent", true)
            SettingItemWithSwitch(
                title = { Text(SettingsStrings.blockPaidContent) },
                description = { Text(SettingsStrings.blockPaidContentDescription) },
                checked = blockPaidContent,
                onCheckedChange = { blockPaidContent = it },
                settingKey = "blockPaidContent",
            )
            var reverseBlock by rememberSetting(settings, "reverseBlock", false)
            SettingItemWithSwitch(
                title = { Text(SettingsStrings.reverseBlocking) },
                description = { Text(SettingsStrings.reverseBlockingDescription) },
                checked = reverseBlock,
                onCheckedChange = { reverseBlock = it },
                settingKey = "reverseBlock",
            )
        }

        SettingItemGroup(title = SettingsStrings.maintenance) {
            SettingItem(
                title = { Text(SettingsStrings.clearExposureRecords) },
                description = {
                    val error = clearExposureError
                    if (error != null) {
                        Text(SettingsStrings.clearFailedDetail(error()), color = MaterialTheme.colorScheme.error)
                    } else {
                        Text(SettingsStrings.clearExposureDescription)
                    }
                },
                endAction = {
                    if (clearingExposure) {
                        AppSpinner(
                            size = AppDimens.InlineSpinnerSize,
                            modifier = Modifier.padding(horizontal = AppDimens.Space16),
                        )
                    } else {
                        AppTextButton(
                            text = SettingsStrings.clear,
                            onClick = { showClearExposureDialog = true },
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                settingKey = "clearExposureRecords",
            )
        }
    }

    allThresholds.firstOrNull { it.key == editingThresholdKey }?.let { threshold ->
        ThresholdDialog(
            threshold = threshold,
            settings = settings,
            onDismiss = { editingThresholdKey = null },
        )
    }

    if (showClearExposureDialog) {
        val count = stats.readyValue?.totalRecords
        AppDialog(
            onDismissRequest = { showClearExposureDialog = false },
            title = if (count != null) SettingsStrings.clearExposureCount(count) else SettingsStrings.clearAllExposureConfirmation,
            summary = SettingsStrings.clearExposureWarning,
            confirmText = SettingsStrings.clear,
            onConfirm = {
                showClearExposureDialog = false
                coroutineScope.launch {
                    clearingExposure = true
                    clearExposureError = null
                    try {
                        contentFilterDao.clearAllRecords()
                        userMessages.showShortMessage(SettingsStrings.exposureRecordsCleared)
                        statsReload++
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        AppLog.e("ContentFilterSettingsScreen", "Failed to clear exposure records", e)
                        clearExposureError = { e.message ?: e.toString() }
                    } finally {
                        clearingExposure = false
                    }
                }
            },
            dismissText = SettingsStrings.cancel,
            destructive = true,
        )
    }
}

@Composable
private fun FilterOverviewRow(
    filterEnabled: Boolean,
    stats: LoadState<ContentFilterStats>,
    onRetry: () -> Unit,
) {
    when {
        !filterEnabled -> SettingItem(
            title = { Text(SettingsStrings.smartFilterOff) },
            description = { Text(SettingsStrings.smartFilterOffDescription) },
        )

        stats is LoadState.Failed -> SettingItem(
            title = { Text(SettingsStrings.filterStatsFailed) },
            description = { Text(stats.message, color = MaterialTheme.colorScheme.error) },
            endAction = { AppTextButton(text = SettingsStrings.retry, onClick = onRetry) },
        )

        stats is LoadState.Ready -> {
            val value = stats.value
            SettingItem(
                title = { Text(SettingsStrings.filteredDuplicateCount(value.filteredCount)) },
                description = {
                    Text(SettingsStrings.exposureStatistics(value.totalRecords, (value.filterRate * 100).roundToInt()))
                },
            )
        }

        else -> SettingItem(
            title = { Text(SettingsStrings.filterStatistics) },
            description = { Text(SettingsStrings.readingData) },
            endAction = {
                AppSpinner(
                    size = AppDimens.InlineSpinnerSize,
                    modifier = Modifier.padding(horizontal = AppDimens.Space16),
                )
            },
        )
    }
}

@Composable
private fun ThresholdRow(
    threshold: ThresholdSetting,
    settings: SettingsStore,
    enabled: Boolean,
    disabledNote: String,
    onClick: () -> Unit,
) {
    val value by rememberObservedSetting(settings, threshold.key) { getInt(threshold.key, threshold.defaultValue) }
    val description = if (enabled) threshold.description else disabledNote
    SettingItem(
        title = { Text(threshold.title) },
        description = description?.let { text -> { Text(text) } },
        endAction = {
            Text(
                value.toString(),
                modifier = Modifier.padding(horizontal = AppDimens.Space16),
                color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        onClick = onClick,
        enabled = enabled,
        settingKey = threshold.key,
    )
}

@Composable
private fun ThresholdDialog(
    threshold: ThresholdSetting,
    settings: SettingsStore,
    onDismiss: () -> Unit,
) {
    val input = rememberSaveable(threshold.key, saver = TextFieldState.Saver) {
        TextFieldState(settings.getInt(threshold.key, threshold.defaultValue).toString())
    }

    fun parsed() = input.text
        .toString()
        .trim()
        .toIntOrNull()
        ?.takeIf { it >= threshold.minimum }
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(SettingsStrings.setThreshold(threshold.title)) },
        text = {
            Column {
                threshold.description?.let { description ->
                    Text(description, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(AppDimens.Space16))
                }
                AppTextField(
                    state = input,
                    labelText = SettingsStrings.threshold,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                if (parsed() == null) {
                    Text(
                        SettingsStrings.minimumInteger(threshold.minimum),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = AppDimens.Space8),
                    )
                }
            }
        },
        confirmButton = {
            AppTextButton(
                text = SettingsStrings.confirm,
                onClick = {
                    parsed()?.let { value ->
                        settings.putInt(threshold.key, value)
                        onDismiss()
                    }
                },
                enabled = parsed() != null,
            )
        },
        dismissButton = { AppTextButton(text = SettingsStrings.cancel, onClick = onDismiss) },
    )
}
