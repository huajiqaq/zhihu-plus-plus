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
import androidx.compose.material3.Icon
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
import com.github.zly2006.zhihu.account.rememberZhihuAccountStore
import com.github.zly2006.zhihu.data.AIGC_MARKING_ENABLED_PREFERENCE_KEY
import com.github.zly2006.zhihu.i18n.SettingsStrings
import com.github.zly2006.zhihu.markdown.RenderMarkdownText
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.platform.isJvm
import com.github.zly2006.zhihu.platform.isLiteVariant
import com.github.zly2006.zhihu.platform.isNative
import com.github.zly2006.zhihu.platform.platformName
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.platform.rememberWebViewUrlOpener
import com.github.zly2006.zhihu.ui.components.AppAlertDialog
import com.github.zly2006.zhihu.ui.components.AppButton
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppIcon
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

const val SYSTEM_AND_UPDATE_SETTINGS_BACK_BUTTON_TAG = "system_and_update_settings_back_button"

internal const val CONTINUOUS_USAGE_REMINDER_INTERVAL_MINUTES_KEY = "continuousUsageReminderIntervalMinutes"
const val SYSTEM_SETTINGS_AIGC_MARKING_TAG = "system_settings_aigc_marking"

@Composable
fun SystemAndUpdateSettingsScreen(
    setting: String? = null,
) {
    val settings = rememberSettingsStore()
    val browserSession = rememberZhihuAccountStore().usesBrowserSession
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
        browserSession -> SettingsStrings.webEdition
        isJvm -> SettingsStrings.desktopEdition
        isNative -> SettingsStrings.platformEdition(platformName)
        isLiteVariant -> SettingsStrings.liteEdition
        else -> SettingsStrings.fullEdition
    }

    AppSettingsPage(
        title = SettingsStrings.systemUpdates,
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
                    setSystemUpdateError(SettingsStrings.openMirrorFailed(describeUpdateFailure(error)), UpdateStep.Download)
                }
            },
            onOpenUrl = { openWebUrl(it) },
        )

        var checkNightlyUpdates by rememberSetting(settings, "checkNightlyUpdates", false)
        SettingItemGroup(title = SettingsStrings.updateChannel) {
            SettingItemWithSwitch(
                title = { Text(SettingsStrings.checkNightlyUpdates) },
                description = { Text(if (browserSession) SettingsStrings.webUpdateChannel else SettingsStrings.checkNightlyUpdatesDescription) },
                checked = !browserSession && checkNightlyUpdates,
                enabled = !browserSession,
                onCheckedChange = { checkNightlyUpdates = it },
                settingKey = "checkNightlyUpdates",
            )
            SettingItem(
                title = { Text("GitHub Token") },
                description = { Text(if (browserSession) SettingsStrings.browserNoGithubToken else SettingsStrings.githubTokenDescription) },
                enabled = !browserSession,
                settingKey = "githubToken",
                bottomAction = {
                    if (!browserSession) {
                        AppSecureTextField(
                            state = githubToken,
                            labelText = "GitHub Token",
                            textObfuscationMode = if (showGithubToken) TextObfuscationMode.Visible else TextObfuscationMode.Hidden,
                            trailingIcon = {
                                AppIconButton(onClick = { showGithubToken = !showGithubToken }) {
                                    AppIcon(
                                        imageVector = if (showGithubToken) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = if (showGithubToken) SettingsStrings.hideGithubToken else SettingsStrings.showGithubToken,
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = AppDimens.Space16),
                        )
                    }
                },
            )
        }

        var allowTelemetry by rememberSetting(settings, "allowTelemetry", true)
        var aigcMarkingEnabled by rememberSetting(settings, AIGC_MARKING_ENABLED_PREFERENCE_KEY, false)
        SettingItemGroup(title = SettingsStrings.privacy) {
            SettingItemWithSwitch(
                title = { Text(SettingsStrings.allowTelemetry) },
                description = { Text(SettingsStrings.telemetryDescription) },
                checked = allowTelemetry,
                onCheckedChange = { allowTelemetry = it },
                settingKey = "allowTelemetry",
            )
            SettingItemWithSwitch(
                modifier = Modifier.testTag(SYSTEM_SETTINGS_AIGC_MARKING_TAG),
                title = { Text(SettingsStrings.aigcMarking) },
                description = {
                    Text(SettingsStrings.aigcMarkingDescription)
                },
                checked = aigcMarkingEnabled,
                onCheckedChange = { aigcMarkingEnabled = it },
                settingKey = AIGC_MARKING_ENABLED_PREFERENCE_KEY,
            )
        }

        var reminderIntervalMinutes by rememberSetting(settings, CONTINUOUS_USAGE_REMINDER_INTERVAL_MINUTES_KEY, 0)
        val reminderOptions = listOf(
            0 to SettingsStrings.off,
            15 to SettingsStrings.reminderQuarterHour,
            30 to SettingsStrings.reminderHalfHour,
            60 to SettingsStrings.reminderHourly,
        )
        val normalizedReminderMinutes = ContinuousUsageReminderPolicy.normalizeIntervalMinutes(reminderIntervalMinutes)
        SettingItemGroup(title = SettingsStrings.digitalWellbeing) {
            SettingItemSelect(
                title = { Text(SettingsStrings.usageReminder) },
                description = { Text(SettingsStrings.usageReminderDescription) },
                options = reminderOptions.map { it.second },
                selectedIndex = reminderOptions.indexOfFirst { it.first == normalizedReminderMinutes },
                onSelect = { index -> reminderIntervalMinutes = reminderOptions[index].first },
                settingKey = CONTINUOUS_USAGE_REMINDER_INTERVAL_MINUTES_KEY,
            )
        }

        if (platformName == "macOS") {
            var quitOnWindowClose by rememberSetting(settings, MACOS_QUIT_ON_WINDOW_CLOSE_PREFERENCE_KEY, false)
            SettingItemGroup(title = SettingsStrings.system) {
                SettingItemWithSwitch(
                    title = { Text(SettingsStrings.quitOnWindowClose) },
                    description = { Text(SettingsStrings.quitOnWindowCloseDescription) },
                    checked = quitOnWindowClose,
                    onCheckedChange = { quitOnWindowClose = it },
                    settingKey = MACOS_QUIT_ON_WINDOW_CLOSE_PREFERENCE_KEY,
                )
            }
        }

        SettingItemGroup(
            title = SettingsStrings.community,
            footer = { Text(SettingsStrings.communityFooter) },
        ) {
            CommunityLinkRow(SettingsStrings.discordChannel, SettingsStrings.discordChannelDescription, Res.drawable.ic_discord_24dp) {
                openWebUrl("https://discord.gg/YCPFZV5XSA")
            }
            CommunityLinkRow(SettingsStrings.telegramGroup, SettingsStrings.telegramGroupDescription, Res.drawable.ic_telegram_24dp) {
                openWebUrl("https://t.me/+_A1Yto6EpyIyODA1")
            }
            CommunityLinkRow("GitHub issue", SettingsStrings.githubFeedbackDescription, Res.drawable.ic_github_24dp) {
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
    var showReloadConfirmation by remember(state) { mutableStateOf(false) }
    val pending = state.pendingUpdate
    val pendingName = pending?.let { if (it.isNightly) SettingsStrings.nightlyVersion(it.version) else SettingsStrings.stableVersion(it.version) }
    val downloadLabel = if (pending?.opensExternally == true) SettingsStrings.openDownloadPage else SettingsStrings.downloadUpdate
    val (title, status) = when (state) {
        SystemUpdateState.NoUpdate -> SettingsStrings.appName to null
        SystemUpdateState.Checking -> SettingsStrings.checkingUpdates to null
        SystemUpdateState.Latest -> SettingsStrings.alreadyLatest to null
        SystemUpdateState.Unpublished -> SettingsStrings.webUnpublished to SettingsStrings.webUnpublishedDescription
        is SystemUpdateState.WebUpdateAvailable -> SettingsStrings.updateAvailable to SettingsStrings.webUpdateReady(state.buildId.take(12))
        is SystemUpdateState.UpdateAvailable -> SettingsStrings.updateAvailable to pendingName
        is SystemUpdateState.Downloading -> SettingsStrings.downloading to pendingName
        is SystemUpdateState.Downloaded -> SettingsStrings.downloaded to SettingsStrings.updateReady(pendingName)
        is SystemUpdateState.Error -> {
            val failedStep = when (state.step) {
                UpdateStep.Check -> SettingsStrings.updateCheckFailed
                UpdateStep.Download if pending?.opensExternally == true -> SettingsStrings.openDownloadFailed
                UpdateStep.Download -> SettingsStrings.downloadFailed
                UpdateStep.Install -> SettingsStrings.installFailed
            }
            failedStep to state.message
        }
    }
    val (actionLabel, action) = when (state) {
        SystemUpdateState.NoUpdate, SystemUpdateState.Latest -> SettingsStrings.checkUpdates to onCheck
        SystemUpdateState.Checking -> SettingsStrings.checking to null
        SystemUpdateState.Unpublished -> SettingsStrings.checkUpdates to null
        is SystemUpdateState.WebUpdateAvailable -> SettingsStrings.webReloadUpdate to { showReloadConfirmation = true }
        is SystemUpdateState.UpdateAvailable -> downloadLabel to onDownload
        is SystemUpdateState.Downloading -> SettingsStrings.downloadInProgress to null
        is SystemUpdateState.Downloaded if installSupported -> SettingsStrings.installUpdate to onInstall
        is SystemUpdateState.Downloaded -> SettingsStrings.apkInstallUnsupported to null
        is SystemUpdateState.Error -> {
            val retry = when {
                state.update == null -> onCheck
                state.step == UpdateStep.Install -> onInstall
                else -> onDownload
            }
            SettingsStrings.retry to retry
        }
    }
    if (showReloadConfirmation && state is SystemUpdateState.WebUpdateAvailable) {
        AppAlertDialog(
            onDismissRequest = { showReloadConfirmation = false },
            title = { Text(SettingsStrings.webReloadConfirmation) },
            text = { Text(SettingsStrings.webReloadWarning) },
            confirmButton = {
                AppButton(
                    text = SettingsStrings.webReloadUpdate,
                    onClick = {
                        showReloadConfirmation = false
                        onDownload()
                    },
                )
            },
            dismissButton = {
                AppTextButton(onClick = { showReloadConfirmation = false }) { Text(SettingsStrings.cancel) }
            },
        )
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
                    Text(SettingsStrings.currentVersion(currentVersion))
                }
            },
            icon = { AppIcon(Icons.Default.SystemUpdate, null) },
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
                            AppOutlinedButton(text = SettingsStrings.skipVersion, onClick = { onSkip(skippableVersion) })
                        }
                        if (cnDownloadUrl != null) {
                            AppOutlinedButton(text = SettingsStrings.chinaDownloadMirror, onClick = { onOpenCnDownload(cnDownloadUrl) })
                        }
                    }
                }
            },
        )
        val notes = pending?.releaseNotes
        AnimatedVisibility(visible = notes != null) {
            var expanded by remember(pending?.version) { mutableStateOf(false) }
            SettingItem(
                title = { Text(SettingsStrings.releaseNotes) },
                endAction = {
                    AppTextButton(
                        text = if (expanded) SettingsStrings.collapse else SettingsStrings.expand,
                        onClick = { expanded = !expanded },
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                bottomAction = {
                    AnimatedVisibility(visible = expanded) {
                        Column(modifier = Modifier.fillMaxWidth().padding(top = AppDimens.Space8)) {
                            RenderMarkdownText(markdown = notes.orEmpty(), selectable = true, enableScroll = false)
                            AppTextButton(onClick = { onOpenUrl(RELEASES_PAGE_URL) }, modifier = Modifier.align(Alignment.End)) {
                                Text(SettingsStrings.fullChangelog)
                                AppIcon(Icons.Default.ArrowOutward, null, Modifier.size(AppDimens.Space20))
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
        icon = { Icon(painterResource(icon), null) },
        endAction = { AppIcon(Icons.Default.ArrowOutward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        onClick = onClick,
    )
}
