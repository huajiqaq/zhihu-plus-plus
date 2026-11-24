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
import com.github.zly2006.zhihu.util.Log
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

const val CONTENT_FILTER_SETTINGS_BACK_BUTTON_TAG = "content_filter_settings_back_button"

private class ThresholdSetting(
    val key: String,
    val title: String,
    val defaultValue: Int,
    val description: String? = null,
    val minimum: Int = 0,
)

private val commonQualityThresholds = listOf(
    ThresholdSetting(ANSWER_VOTEUP_THRESHOLD_PREFERENCE_KEY, "回答最低赞数", 10, "低于此赞同数的未关注作者回答会被过滤"),
    ThresholdSetting(ARTICLE_VOTEUP_THRESHOLD_PREFERENCE_KEY, "文章最低赞数", 20, "低于此赞数或作者粉丝低于对应阈值的文章会被过滤"),
)
private val moreQualityThresholds = listOf(
    ThresholdSetting(ARTICLE_FOLLOWERS_THRESHOLD_PREFERENCE_KEY, "文章最低粉丝数", 50),
    ThresholdSetting(VIDEO_VOTE_THRESHOLD_PREFERENCE_KEY, "视频最低赞数", 20),
    ThresholdSetting(VIDEO_FOLLOWERS_THRESHOLD_PREFERENCE_KEY, "视频最低粉丝数", 50),
    ThresholdSetting(QUESTION_ANSWER_THRESHOLD_PREFERENCE_KEY, "问题最低回答数", 0),
    ThresholdSetting(QUESTION_FOLLOWERS_THRESHOLD_PREFERENCE_KEY, "问题最低关注数", 50),
)
private val topicThreshold = ThresholdSetting(
    key = "topicBlockingThreshold",
    title = "主题屏蔽阈值",
    defaultValue = 1,
    description = "内容命中的被屏蔽主题数达到这个值时屏蔽该内容",
    minimum = 1,
)
private val allThresholds = commonQualityThresholds + moreQualityThresholds + topicThreshold

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
            Log.e("ContentFilterSettingsScreen", "Failed to load filter stats", e)
            LoadState.Failed(e.message ?: e.toString())
        }
    }

    var editingThresholdKey by rememberSaveable { mutableStateOf<String?>(null) }
    var showClearExposureDialog by rememberSaveable { mutableStateOf(false) }
    var clearingExposure by remember { mutableStateOf(false) }
    var clearExposureError by remember { mutableStateOf<String?>(null) }

    AppSettingsPage(
        title = "内容过滤",
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
        SettingItemGroup(title = "过滤概况") {
            FilterOverviewRow(
                filterEnabled = enableContentFilter,
                stats = stats,
                onRetry = { statsReload++ },
            )
            SettingItem(
                modifier = Modifier.testTag("contentFilterSettings:blockedFeedHistory"),
                title = { Text("屏蔽记录") },
                description = { Text("查看首页被过滤的内容和原因") },
                onClick = { navigator.onNavigate(Account.RecommendSettings.BlockedFeedHistory) },
            )
        }

        val deepLink = LocalSettingsDeepLink.current
        var moreThresholdsExpanded by rememberSaveable {
            mutableStateOf(deepLink?.highlightedKey?.let { key -> moreQualityThresholds.any { it.key == key } } == true)
        }
        var qualityModeName by rememberSetting(settings, QUALITY_FILTER_MODE_PREFERENCE_KEY, QualityFilterMode.RULES.name)
        val qualityMode = QualityFilterMode.entries.firstOrNull { it.name == qualityModeName } ?: QualityFilterMode.RULES
        val qualityModeOptions = listOf(
            QualityFilterMode.OFF to "不屏蔽",
            QualityFilterMode.RULES to "屏蔽规则",
            QualityFilterMode.HIDE to "隐藏",
        )
        val thresholdsEnabled = qualityMode != QualityFilterMode.OFF
        val disabledNote = "质量屏蔽已关闭；已保存的阈值在开启后生效"
        SettingItemGroup(title = "质量过滤") {
            SettingItemSelect(
                title = { Text("质量屏蔽") },
                description = { Text("根据赞同数、关注数等指标处理低质量内容") },
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
                title = { Text("更多阈值") },
                description = { Text(if (thresholdsEnabled) "文章粉丝数、视频和问题规则" else disabledNote) },
                endAction = {
                    Text(
                        if (moreThresholdsExpanded) "收起" else "展开",
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

        SettingItemGroup(title = "内容去重") {
            SettingItemWithSwitch(
                modifier = Modifier.testTag("contentFilterSettings:enableContentFilter"),
                title = { Text("启用智能内容过滤") },
                description = { Text("自动过滤首页展示超过2次但用户未点击的内容，减少重复推荐") },
                checked = enableContentFilter,
                onCheckedChange = { enableContentFilter = it },
                settingKey = "enableContentFilter",
            )
            var filterFollowedUserContent by rememberSetting(settings, "filterFollowedUserContent", false)
            SettingItemWithSwitch(
                modifier = Modifier.testTag("contentFilterSettings:filterFollowedUserContent"),
                title = { Text("过滤已关注用户内容") },
                description = {
                    Text(
                        if (enableContentFilter) {
                            "是否对已关注用户的内容也应用过滤规则。关闭此选项可确保关注用户的内容始终显示"
                        } else {
                            "需先启用智能内容过滤"
                        },
                    )
                },
                checked = filterFollowedUserContent,
                onCheckedChange = { filterFollowedUserContent = it },
                enabled = enableContentFilter,
                settingKey = "filterFollowedUserContent",
            )
        }

        SettingItemGroup(title = "屏蔽规则") {
            var enableKeywordBlocking by rememberSetting(settings, "enableKeywordBlocking", true)
            SettingItemWithSwitch(
                title = { Text("启用关键词屏蔽") },
                description = { Text("按全部内容、问题或回答的关键词规则屏蔽内容") },
                checked = enableKeywordBlocking,
                onCheckedChange = { enableKeywordBlocking = it },
                settingKey = "enableKeywordBlocking",
            )
            var enableUserBlocking by rememberSetting(settings, "enableUserBlocking", true)
            SettingItemWithSwitch(
                title = { Text("启用用户屏蔽") },
                description = { Text("屏蔽特定用户发布的内容，或由特定用户提出的问题") },
                checked = enableUserBlocking,
                onCheckedChange = { enableUserBlocking = it },
                settingKey = "enableUserBlocking",
            )
            var enableTopicBlocking by rememberSetting(settings, "enableTopicBlocking", true)
            SettingItemWithSwitch(
                title = { Text("启用主题屏蔽") },
                description = { Text("屏蔽包含特定主题的内容") },
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
                title = { Text("管理屏蔽列表") },
                description = {
                    Text(if (isBlocklistNlpSupported) "关键词与 NLP 短语可分别用于全部内容、问题或回答；另含用户、提问者和主题" else "关键词可分别用于全部内容、问题或回答；另含用户、提问者和主题")
                },
                onClick = { navigator.onNavigate(Account.RecommendSettings.Blocklist) },
            )
        }

        SettingItemGroup(title = "推广内容") {
            var blockZhihuAdPlatform by rememberSetting(settings, "blockZhihuAdPlatform", true)
            SettingItemWithSwitch(
                title = { Text("屏蔽知乎广告平台内容") },
                description = { Text("匹配并屏蔽包含 xg.zhihu.com 的推广内容") },
                checked = blockZhihuAdPlatform,
                onCheckedChange = { blockZhihuAdPlatform = it },
                settingKey = "blockZhihuAdPlatform",
            )
            var blockZhihuSchool by rememberSetting(settings, "blockZhihuSchool", true)
            SettingItemWithSwitch(
                title = { Text("屏蔽知乎学堂内容") },
                description = { Text("匹配并屏蔽包含 d.zhihu.com 或 data-edu-card-id 的内容") },
                checked = blockZhihuSchool,
                onCheckedChange = { blockZhihuSchool = it },
                settingKey = "blockZhihuSchool",
            )
            var blockWeChatOfficialAccount by rememberSetting(settings, "blockWeChatOfficialAccount", true)
            SettingItemWithSwitch(
                title = { Text("屏蔽微信公众号文章") },
                description = { Text("匹配并屏蔽包含 mp.weixin.qq.com 的外链文章") },
                checked = blockWeChatOfficialAccount,
                onCheckedChange = { blockWeChatOfficialAccount = it },
                settingKey = "blockWeChatOfficialAccount",
            )
            var blockPaidContent by rememberSetting(settings, "blockPaidContent", true)
            SettingItemWithSwitch(
                title = { Text("屏蔽知乎盐选付费内容") },
                description = { Text("屏蔽知乎盐选会员专享的付费回答和文章") },
                checked = blockPaidContent,
                onCheckedChange = { blockPaidContent = it },
                settingKey = "blockPaidContent",
            )
            var reverseBlock by rememberSetting(settings, "reverseBlock", false)
            SettingItemWithSwitch(
                title = { Text("反向屏蔽") },
                description = { Text("开启后，首页将只保留广告和付费内容，屏蔽其余所有内容（即“吃\uD83D\uDCA9模式”）") },
                checked = reverseBlock,
                onCheckedChange = { reverseBlock = it },
                settingKey = "reverseBlock",
            )
        }

        SettingItemGroup(title = "维护") {
            SettingItem(
                title = { Text("清除曝光记录") },
                description = {
                    val error = clearExposureError
                    if (error != null) {
                        Text("清除失败：$error", color = MaterialTheme.colorScheme.error)
                    } else {
                        Text("重置智能过滤记下的展示次数，之前被过滤的重复内容可能重新出现")
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
                            text = "清除",
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
            title = if (count != null) "清除 $count 条曝光记录？" else "清除全部曝光记录？",
            summary = "智能过滤会从零开始统计展示次数，之前被过滤的重复内容可能重新出现在首页。清除后无法撤销。",
            confirmText = "清除",
            onConfirm = {
                showClearExposureDialog = false
                coroutineScope.launch {
                    clearingExposure = true
                    clearExposureError = null
                    try {
                        contentFilterDao.clearAllRecords()
                        userMessages.showShortMessage("已清除曝光记录")
                        statsReload++
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e("ContentFilterSettingsScreen", "Failed to clear exposure records", e)
                        clearExposureError = e.message ?: e.toString()
                    } finally {
                        clearingExposure = false
                    }
                }
            },
            dismissText = "取消",
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
            title = { Text("智能过滤已关闭") },
            description = { Text("开启“内容去重”里的智能内容过滤后，这里会统计被过滤的重复内容") },
        )

        stats is LoadState.Failed -> SettingItem(
            title = { Text("过滤统计加载失败") },
            description = { Text(stats.message, color = MaterialTheme.colorScheme.error) },
            endAction = { AppTextButton(text = "重试", onClick = onRetry) },
        )

        stats is LoadState.Ready -> {
            val value = stats.value
            SettingItem(
                title = { Text("已过滤 ${value.filteredCount} 条重复内容") },
                description = {
                    Text("共记录 ${value.totalRecords} 条曝光，过滤率 ${(value.filterRate * 100).roundToInt()}%")
                },
            )
        }

        else -> SettingItem(
            title = { Text("过滤统计") },
            description = { Text("正在读取…") },
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
        title = { Text("设置${threshold.title}") },
        text = {
            Column {
                threshold.description?.let { description ->
                    Text(description, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(AppDimens.Space16))
                }
                AppTextField(
                    state = input,
                    labelText = "阈值",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                if (parsed() == null) {
                    Text(
                        "请输入不小于 ${threshold.minimum} 的整数",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = AppDimens.Space8),
                    )
                }
            }
        },
        confirmButton = {
            AppTextButton(
                text = "确定",
                onClick = {
                    parsed()?.let { value ->
                        settings.putInt(threshold.key, value)
                        onDismiss()
                    }
                },
                enabled = parsed() != null,
            )
        },
        dismissButton = { AppTextButton(text = "取消", onClick = onDismiss) },
    )
}
