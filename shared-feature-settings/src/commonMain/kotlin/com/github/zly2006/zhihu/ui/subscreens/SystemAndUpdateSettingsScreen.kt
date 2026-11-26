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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowOutward
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import com.github.zly2006.zhihu.data.AIGC_MARKING_ENABLED_PREFERENCE_KEY
import com.github.zly2006.zhihu.markdown.RenderMarkdownText
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.platform.isJvm
import com.github.zly2006.zhihu.platform.isLiteVariant
import com.github.zly2006.zhihu.platform.isNative
import com.github.zly2006.zhihu.platform.platformName
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.platform.rememberWebViewUrlOpener
import com.github.zly2006.zhihu.ui.components.AppButton
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppIconButton
import com.github.zly2006.zhihu.ui.components.AppLinearProgressIndeterminate
import com.github.zly2006.zhihu.ui.components.AppOutlinedButton
import com.github.zly2006.zhihu.ui.components.AppSecureTextField
import com.github.zly2006.zhihu.ui.components.AppSettingsPage
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.ui.components.SettingItem
import com.github.zly2006.zhihu.ui.components.SettingItemGroup
import com.github.zly2006.zhihu.ui.components.SettingItemSelect
import com.github.zly2006.zhihu.ui.components.SettingItemWithSwitch
import com.github.zly2006.zhihu.ui.rememberAppVersionInfo
import com.github.zly2006.zhihu.ui.rememberSetting
import com.github.zly2006.zhihu.updater.UpdateStep
import com.github.zly2006.zhihu.updater.describeUpdateFailure
import com.github.zly2006.zhihu.util.ContinuousUsageReminderPolicy
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import zhihu.uicomponents.generated.resources.Res
import zhihu.uicomponents.generated.resources.ic_discord_24dp
import zhihu.uicomponents.generated.resources.ic_github_24dp
import zhihu.uicomponents.generated.resources.ic_telegram_24dp
import androidx.compose.material3.Icon as MaterialIcon
import com.github.zly2006.zhihu.ui.components.AppIcon as Icon

const val SYSTEM_AND_UPDATE_SETTINGS_BACK_BUTTON_TAG = "system_and_update_settings_back_button"

internal const val CONTINUOUS_USAGE_REMINDER_INTERVAL_MINUTES_KEY = "continuousUsageReminderIntervalMinutes"
const val SYSTEM_SETTINGS_AIGC_MARKING_TAG = "system_settings_aigc_marking"

@Composable
fun SystemAndUpdateSettingsScreen(
    setting: String? = null,
) {
    val settings = rememberSettingsStore()
    val updateState by rememberSystemUpdateState().collectAsState()
    val checkForUpdate = rememberSystemUpdateChecker()
    val skipUpdateVersion = rememberSystemUpdateVersionSkipper()
    val downloadUpdate = rememberSystemUpdateDownloader()
    val installDownloadedUpdate = rememberDownloadedSystemUpdateInstaller()
    val openWebUrl = rememberWebViewUrlOpener()
    val coroutineScope = rememberCoroutineScope()
    val githubToken = remember(settings) { TextFieldState(settings.getString("githubToken", "")) }
    var showGithubToken by remember(settings) { mutableStateOf(false) }

    /** 只提交本次编辑；点击或离页早于观察器时补写，不把未编辑的旧凭据写回。 */
    val saveEditedToken = remember(githubToken) {
        var lastWrite = githubToken.text

        fun save() {
            val text = githubToken.text
            if (!text.contentEquals(lastWrite)) {
                settings.putString("githubToken", text.toString())
                lastWrite = text
            }
        }
        ::save
    }
    LaunchedEffect(githubToken) {
        snapshotFlow { githubToken.text }.collect { saveEditedToken() }
    }
    DisposableEffect(githubToken) { onDispose(saveEditedToken) }

    val versionType = when {
        isJvm -> "桌面版"
        isNative -> "$platformName 版"
        isLiteVariant -> "Lite 版"
        else -> "Full 版"
    }

    AppSettingsPage(
        title = "系统与更新",
        onBack = LocalNavigator.current.onNavigateBack,
        backTag = SYSTEM_AND_UPDATE_SETTINGS_BACK_BUTTON_TAG,
        highlightedKey = setting.orEmpty(),
    ) {
        SystemUpdateStatusCard(
            state = updateState,
            currentVersion = "${rememberAppVersionInfo()} · $versionType",
            installSupported = isApkUpdateInstallSupported,
            onCheck = {
                saveEditedToken()
                coroutineScope.launch { checkForUpdate() }
            },
            onDownload = {
                saveEditedToken()
                coroutineScope.launch { downloadUpdate() }
            },
            onInstall = { coroutineScope.launch { installDownloadedUpdate() } },
            onSkip = skipUpdateVersion,
            onOpenCnDownload = { url ->
                try {
                    openWebUrl(url)
                } catch (error: Exception) {
                    setSystemUpdateError("打开国内网盘失败：${describeUpdateFailure(error)}", UpdateStep.Download)
                }
            },
            onOpenUrl = { openWebUrl(it) },
        )

        var checkNightlyUpdates by rememberSetting(settings, "checkNightlyUpdates", false)
        SettingItemGroup(title = "更新通道") {
            SettingItemWithSwitch(
                title = { Text("检查 Nightly 版本更新") },
                description = { Text("检查每日构建版本 (可能不稳定)") },
                checked = checkNightlyUpdates,
                onCheckedChange = { checkNightlyUpdates = it },
                settingKey = "checkNightlyUpdates",
            )
            SettingItem(
                title = { Text("GitHub Token") },
                description = { Text("留空时匿名检查更新；若遇 GitHub 限流，可填写 Token 后重试。") },
                settingKey = "githubToken",
                bottomAction = {
                    AppSecureTextField(
                        state = githubToken,
                        labelText = "GitHub Token",
                        textObfuscationMode = if (showGithubToken) TextObfuscationMode.Visible else TextObfuscationMode.Hidden,
                        trailingIcon = {
                            AppIconButton(onClick = { showGithubToken = !showGithubToken }) {
                                Icon(
                                    imageVector = if (showGithubToken) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (showGithubToken) "隐藏 GitHub Token" else "显示 GitHub Token",
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = AppDimens.Space16),
                    )
                },
            )
        }

        var allowTelemetry by rememberSetting(settings, "allowTelemetry", true)
        var aigcMarkingEnabled by rememberSetting(settings, AIGC_MARKING_ENABLED_PREFERENCE_KEY, false)
        SettingItemGroup(title = "隐私") {
            SettingItemWithSwitch(
                title = { Text("允许发送遥测统计数据") },
                description = { Text("仅用于统计使用人数，不包含个人隐私") },
                checked = allowTelemetry,
                onCheckedChange = { allowTelemetry = it },
                settingKey = "allowTelemetry",
            )
            SettingItemWithSwitch(
                modifier = Modifier.testTag(SYSTEM_SETTINGS_AIGC_MARKING_TAG),
                title = { Text("启用 AIGC 标记") },
                description = {
                    Text("如果启用，会把你正在浏览的内容发送到我们的服务器，这样你可以知道其他用户是否认为其疑似 AIGC。默认关闭，不会发送隐私信息。")
                },
                checked = aigcMarkingEnabled,
                onCheckedChange = { aigcMarkingEnabled = it },
                settingKey = AIGC_MARKING_ENABLED_PREFERENCE_KEY,
            )
        }

        var reminderIntervalMinutes by rememberSetting(settings, CONTINUOUS_USAGE_REMINDER_INTERVAL_MINUTES_KEY, 0)
        val reminderOptions = listOf(
            0 to "关闭",
            15 to "每 15 分钟",
            30 to "每 30 分钟",
            60 to "每 1 小时",
        )
        val normalizedReminderMinutes = ContinuousUsageReminderPolicy.normalizeIntervalMinutes(reminderIntervalMinutes)
        SettingItemGroup(title = "防沉迷") {
            SettingItemSelect(
                title = { Text("防沉迷提醒") },
                description = { Text("退出后 5 分钟内重开仍视为连续使用。") },
                options = reminderOptions.map { it.second },
                selectedIndex = reminderOptions.indexOfFirst { it.first == normalizedReminderMinutes },
                onSelect = { index -> reminderIntervalMinutes = reminderOptions[index].first },
                settingKey = CONTINUOUS_USAGE_REMINDER_INTERVAL_MINUTES_KEY,
            )
        }

        if (platformName == "macOS") {
            var quitOnWindowClose by rememberSetting(settings, MACOS_QUIT_ON_WINDOW_CLOSE_PREFERENCE_KEY, false)
            SettingItemGroup(title = "系统") {
                SettingItemWithSwitch(
                    title = { Text("关闭窗口时退出应用") },
                    description = { Text("关闭最后一个窗口时同时退出 macOS 应用；默认关闭") },
                    checked = quitOnWindowClose,
                    onCheckedChange = { quitOnWindowClose = it },
                    settingKey = MACOS_QUIT_ON_WINDOW_CLOSE_PREFERENCE_KEY,
                )
            }
        }

        SettingItemGroup(
            title = "交流",
            footer = { Text("代码和功能反馈请前往 GitHub。上边的频道用于用户交流和闲聊，开发者不一定会在线回答问题。") },
        ) {
            CommunityLinkRow("Discord 频道", "请在 my-other-apps/zhihu-plus-plus 频道讨论", Res.drawable.ic_discord_24dp) {
                openWebUrl("https://discord.gg/YCPFZV5XSA")
            }
            CommunityLinkRow("Telegram 群组 (Hydrogen)", "另一个知乎客户端 Hydrogen 的群组，也可以在里面讨论知乎++哦", Res.drawable.ic_telegram_24dp) {
                openWebUrl("https://t.me/+_A1Yto6EpyIyODA1")
            }
            CommunityLinkRow("GitHub issue", "欢迎提交 issue 讨论功能和反馈问题", Res.drawable.ic_github_24dp) {
                openWebUrl("https://github.com/zly2006/zhihu-plus-plus/issues")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SystemUpdateStatusCard(
    state: SystemUpdateState,
    currentVersion: String,
    installSupported: Boolean,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onSkip: (version: String) -> Unit,
    onOpenCnDownload: (url: String) -> Unit,
    onOpenUrl: (url: String) -> Unit,
) {
    val pending = state.pendingUpdate
    val pendingName = pending?.let { if (it.isNightly) "Nightly 版 ${it.version}" else "正式版 ${it.version}" }
    val downloadLabel = if (pending?.opensExternally == true) "前往下载页面" else "下载更新"
    val (title, status) = when (state) {
        SystemUpdateState.NoUpdate -> "知乎++" to null
        SystemUpdateState.Checking -> "正在检查更新" to null
        SystemUpdateState.Latest -> "已是最新版本" to null
        is SystemUpdateState.UpdateAvailable -> "发现新版本" to pendingName
        is SystemUpdateState.Downloading -> "正在下载" to pendingName
        is SystemUpdateState.Downloaded -> "已下载" to "$pendingName，可以安装"
        is SystemUpdateState.Error -> {
            val failedStep = when (state.step) {
                UpdateStep.Check -> "检查更新失败"
                UpdateStep.Download if pending?.opensExternally == true -> "打开下载页面失败"
                UpdateStep.Download -> "下载失败"
                UpdateStep.Install -> "安装失败"
            }
            failedStep to state.message
        }
    }
    val (actionLabel, action) = when (state) {
        SystemUpdateState.NoUpdate, SystemUpdateState.Latest -> "检查更新" to onCheck
        SystemUpdateState.Checking -> "检查中..." to null
        is SystemUpdateState.UpdateAvailable -> downloadLabel to onDownload
        is SystemUpdateState.Downloading -> "下载中..." to null
        is SystemUpdateState.Downloaded if installSupported -> "安装更新" to onInstall
        is SystemUpdateState.Downloaded -> "暂不支持安装 APK 更新" to null
        is SystemUpdateState.Error -> {
            val retry = when {
                state.update == null -> onCheck
                state.step == UpdateStep.Install -> onInstall
                else -> onDownload
            }
            "重试" to retry
        }
    }
    val skippableVersion = (state as? SystemUpdateState.UpdateAvailable)?.version
    val cnDownloadUrl = pending?.cnDownloadUrl?.takeIf { it.isNotBlank() && (state is SystemUpdateState.UpdateAvailable || state is SystemUpdateState.Error) }

    SettingItemGroup {
        SettingItem(
            title = { Text(title) },
            description = {
                Column {
                    status?.let {
                        Text(it, color = if (state is SystemUpdateState.Error) MaterialTheme.colorScheme.error else Color.Unspecified)
                    }
                    Text("当前版本 $currentVersion")
                }
            },
            icon = { Icon(Icons.Default.SystemUpdate, null) },
            bottomAction = {
                Column(modifier = Modifier.fillMaxWidth().padding(top = AppDimens.Space12)) {
                    if (state is SystemUpdateState.Checking || state is SystemUpdateState.Downloading) {
                        AppLinearProgressIndeterminate(modifier = Modifier.fillMaxWidth().padding(bottom = AppDimens.Space12))
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(AppDimens.Space12),
                        verticalArrangement = Arrangement.spacedBy(AppDimens.Space8),
                    ) {
                        AppButton(text = actionLabel, onClick = { action?.invoke() }, enabled = action != null)
                        if (skippableVersion != null) {
                            AppOutlinedButton(text = "跳过此版本", onClick = { onSkip(skippableVersion) })
                        }
                        if (cnDownloadUrl != null) {
                            AppOutlinedButton(text = "使用国内网盘加速下载", onClick = { onOpenCnDownload(cnDownloadUrl) })
                        }
                    }
                }
            },
        )
        val notes = pending?.releaseNotes
        AnimatedVisibility(visible = notes != null) {
            var expanded by remember(pending?.version) { mutableStateOf(false) }
            SettingItem(
                title = { Text("更新说明") },
                endAction = {
                    AppTextButton(
                        text = if (expanded) "收起" else "展开",
                        onClick = { expanded = !expanded },
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                bottomAction = {
                    AnimatedVisibility(visible = expanded) {
                        Column(modifier = Modifier.fillMaxWidth().padding(top = AppDimens.Space8)) {
                            RenderMarkdownText(markdown = notes.orEmpty(), selectable = true, enableScroll = false)
                            AppTextButton(onClick = { onOpenUrl(RELEASES_PAGE_URL) }, modifier = Modifier.align(Alignment.End)) {
                                Text("查看完整更新日志")
                                Icon(Icons.Default.ArrowOutward, null, Modifier.size(AppDimens.Space20))
                            }
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun CommunityLinkRow(
    title: String,
    description: String,
    icon: DrawableResource,
    onClick: () -> Unit,
) {
    SettingItem(
        title = { Text(title) },
        description = { Text(description) },
        icon = { MaterialIcon(painterResource(icon), null) },
        endAction = { Icon(Icons.Default.ArrowOutward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        onClick = onClick,
    )
}
