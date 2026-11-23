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

import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowOutward
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import com.github.zly2006.zhihu.account.rememberZhihuAccountStore
import com.github.zly2006.zhihu.navigation.Account
import com.github.zly2006.zhihu.navigation.Collections
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.Notification
import com.github.zly2006.zhihu.navigation.OnlineHistoryRoute
import com.github.zly2006.zhihu.navigation.Person
import com.github.zly2006.zhihu.navigation.WebPage
import com.github.zly2006.zhihu.navigation.requestLoginNavigation
import com.github.zly2006.zhihu.platform.platformBottomBarItemLimit
import com.github.zly2006.zhihu.platform.rememberPlainTextClipboard
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.reading.isReadingPlayerSupported
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppScaffold
import com.github.zly2006.zhihu.ui.components.SettingItem
import com.github.zly2006.zhihu.ui.components.SettingItemGroup
import com.github.zly2006.zhihu.ui.components.SettingsContent
import com.github.zly2006.zhihu.ui.components.appGroupedPageBackground
import com.github.zly2006.zhihu.ui.components.pageTurnViewportWithGuide
import com.github.zly2006.zhihu.ui.components.rememberPageTurnTarget
import com.github.zly2006.zhihu.ui.subscreens.BOTTOM_BAR_ITEMS_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.subscreens.PREF_FONT_SIZE
import com.github.zly2006.zhihu.ui.subscreens.PREF_LINE_HEIGHT
import com.github.zly2006.zhihu.ui.subscreens.SystemUpdateState
import com.github.zly2006.zhihu.ui.subscreens.defaultBottomBarSelectionKeys
import com.github.zly2006.zhihu.ui.subscreens.normalizeBottomBarSelection
import com.github.zly2006.zhihu.ui.subscreens.rememberSystemUpdateState
import com.github.zly2006.zhihu.ui.subscreens.shouldShowAccountHistoryShortcut
import com.github.zly2006.zhihu.updater.UpdateStep
import com.github.zly2006.zhihu.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.jetbrains.compose.resources.painterResource
import zhihu.uicomponents.generated.resources.Res
import zhihu.uicomponents.generated.resources.ic_github_24dp
import zhihu.uicomponents.generated.resources.ic_launcher_foreground
import zhihu.uicomponents.generated.resources.ic_license_24dp
import androidx.compose.material3.Icon as MaterialIcon
import com.github.zly2006.zhihu.ui.components.AppIcon as Icon

const val ACCOUNT_SETTINGS_SCROLL_TAG = "accountSettings.scroll"
const val ACCOUNT_SETTINGS_LOGIN_ITEM_TAG = "accountSettings.loginItem"
const val ACCOUNT_SETTINGS_PROFILE_HEADER_TAG = "accountSettings.profileHeader"
const val ACCOUNT_SETTINGS_PROFILE_NAME_TAG = "accountSettings.profileName"
const val ACCOUNT_SETTINGS_PROFILE_RETRY_TAG = "accountSettings.profileRetry"
const val ACCOUNT_SETTINGS_SCAN_TAG = "accountSettings.scan"
const val ACCOUNT_SETTINGS_SHORTCUT_COLLECTIONS_TAG = "accountSettings.shortcutCollections"
const val ACCOUNT_SETTINGS_SHORTCUT_SUBSCRIPTIONS_TAG = "accountSettings.shortcutSubscriptions"
const val ACCOUNT_SETTINGS_SHORTCUT_NOTIFICATION_TAG = "accountSettings.shortcutNotification"
const val ACCOUNT_SETTINGS_SHORTCUT_HISTORY_TAG = "accountSettings.shortcutHistory"
const val ACCOUNT_SETTINGS_APPEARANCE_TAG = "accountSettings.appearance"
const val ACCOUNT_SETTINGS_READING_TAG = "accountSettings.reading"
const val ACCOUNT_SETTINGS_RECOMMEND_TAG = "accountSettings.recommend"
const val ACCOUNT_SETTINGS_SEARCH_TAG = "accountSettings.search"
const val ACCOUNT_SETTINGS_SYSTEM_TAG = "accountSettings.system"
const val ACCOUNT_SETTINGS_DEVELOPER_TAG = "accountSettings.developer"
const val ACCOUNT_SETTINGS_LICENSES_TAG = "accountSettings.licenses"
const val ACCOUNT_SETTINGS_IDENTITY_MANAGEMENT_TAG = "accountSettings.identityManagement"

@Composable
fun AccountSettingScreen(
    innerPadding: PaddingValues,
    unreadCount: Int = 0,
    showUnreadBadge: Boolean = true,
    onDismissRequest: () -> Unit = {},
    refreshAccountProfileOnEnter: Boolean = true,
    isActive: Boolean = true,
) {
    val navigator = LocalNavigator.current
    val navigateAway: (NavDestination) -> Unit = { destination ->
        onDismissRequest()
        navigator.onNavigate(destination)
    }
    val accountStore = rememberZhihuAccountStore()
    val accountClient = accountStore.client
    val verification by accountClient.verificationState.collectAsState()
    val accountState = rememberAccountSettingsAccountState()
    val settings = rememberSettingsStore()
    val copyPlainText = rememberPlainTextClipboard()
    val userMessages = rememberUserMessageSink()
    val systemUpdateState = rememberSystemUpdateState()
    val updateState by systemUpdateState.collectAsState()
    val versionInfo = rememberAppVersionInfo()
    val readingPlayerSupported = isReadingPlayerSupported
    val launchQrScanner = if (isAccountQrScanSupported) rememberAccountQrScanAction() else null

    val bottomBarItemKeys by rememberObservedSetting(settings, BOTTOM_BAR_ITEMS_PREFERENCE_KEY) {
        getStringSet(BOTTOM_BAR_ITEMS_PREFERENCE_KEY, defaultBottomBarSelectionKeys(platformBottomBarItemLimit))
    }
    val showHistoryShortcut = shouldShowAccountHistoryShortcut(
        normalizeBottomBarSelection(
            bottomBarItemKeys,
            enforceMinimumSelection = true,
            maximumSelection = platformBottomBarItemLimit,
        ),
    )
    val fontSize by rememberObservedSetting(settings, PREF_FONT_SIZE) { getInt(PREF_FONT_SIZE, 100) }
    val lineHeight by rememberObservedSetting(settings, PREF_LINE_HEIGHT) { getInt(PREF_LINE_HEIGHT, 160) }
    val data by accountState
    var profileRefresh by remember(accountClient, data.id) { mutableStateOf(ProfileRefresh.Idle) }
    var profileRetryKey by remember(accountClient, data.id) { mutableIntStateOf(0) }
    var logoutTarget by remember { mutableStateOf<com.github.zly2006.zhihu.account.ZhihuAccountClient?>(null) }
    var logoutError by remember { mutableStateOf<String?>(null) }
    val showLogoutDialog = logoutTarget != null
    LaunchedEffect(accountClient) {
        if (logoutTarget != null && logoutTarget !== accountClient) {
            logoutTarget = null
            logoutError = null
        }
    }
    val scrollState = rememberScrollState()
    val pageTurnTarget = rememberPageTurnTarget(
        scrollState = scrollState,
        enabled = isActive && !showLogoutDialog,
    )
    LaunchedEffect(accountClient, data.id, data.login, refreshAccountProfileOnEnter, profileRetryKey) {
        if (!refreshAccountProfileOnEnter || !data.login) {
            profileRefresh = ProfileRefresh.Idle
            return@LaunchedEffect
        }
        profileRefresh = if (profileRefresh == ProfileRefresh.Failed) ProfileRefresh.Retrying else ProfileRefresh.Silent
        val refreshed = try {
            if (accountClient.refreshAndSaveProfile() != null) ProfileRefresh.Idle else ProfileRefresh.Failed
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            if (accountStore.client !== accountClient) return@LaunchedEffect
            Log.e("AccountSettingScreen", "Failed to refresh account profile", e)
            ProfileRefresh.Failed
        }
        currentCoroutineContext().ensureActive()
        if (accountStore.client === accountClient) profileRefresh = refreshed
    }

    LaunchedEffect(updateState) {
        when (val state = updateState) {
            is SystemUpdateState.UpdateAvailable -> {
                val versionType = if (state.isNightly) "Nightly版本" else "正式版本"
                userMessages.showShortMessage("发现新$versionType ${state.version}")
            }
            is SystemUpdateState.Error -> if (state.step == UpdateStep.Check) {
                userMessages.showLongMessage("检查更新失败：${state.message}")
            }
            else -> {}
        }
    }

    AppScaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = appGroupedPageBackground(),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(innerPadding)
                .testTag(ACCOUNT_SETTINGS_SCROLL_TAG)
                .pageTurnViewportWithGuide(pageTurnTarget)
                .verticalScroll(scrollState)
                .padding(padding),
        ) {
            SettingsContent {
                AccountSettingHeader()

                if (data.login) {
                    AccountProfileCard(
                        data = data,
                        refresh = profileRefresh,
                        onRetryRefresh = { profileRetryKey++ },
                        onOpenProfile = {
                            navigateAway(
                                Person(
                                    id = data.id,
                                    urlToken = data.urlToken ?: "",
                                    name = data.username,
                                ),
                            )
                        },
                        onScan = launchQrScanner,
                        onLogout = {
                            logoutTarget = accountClient
                            logoutError = null
                        },
                    )
                    AccountSettingQuickAccessSection(
                        data = data,
                        showUnreadBadge = showUnreadBadge,
                        unreadCount = unreadCount,
                        showHistoryShortcut = showHistoryShortcut,
                        onOpenCollections = { token -> navigateAway(Collections(token)) },
                        onOpenSubscriptions = {
                            navigateAway(
                                Person(
                                    id = data.id,
                                    urlToken = data.urlToken ?: "",
                                    name = data.username,
                                    jumpTo = "关注订阅",
                                ),
                            )
                        },
                        onOpenNotification = { navigateAway(Notification) },
                        onOpenHistory = { navigateAway(OnlineHistoryRoute) },
                    )
                } else {
                    AccountSettingLoginSection(
                        showHistoryShortcut = showHistoryShortcut,
                        onLogin = {
                            onDismissRequest()
                            requestLoginNavigation()
                        },
                        onOpenHistory = { navigateAway(OnlineHistoryRoute) },
                    )
                }

                AccountSettingSearch(onClick = { navigateAway(Account.SettingsSearch) })

                AccountSettingSecuritySection(
                    loggedIn = data.login,
                    onNavigate = navigateAway,
                )

                AccountSettingDisplaySection(
                    fontSize = fontSize,
                    lineHeight = lineHeight,
                    readingPlayerSupported = readingPlayerSupported,
                    onNavigate = navigateAway,
                )

                AccountSettingContentSection(
                    onOpenFeedSettings = { navigateAway(Account.FeedSettings()) },
                    onOpenRecommendSettings = { navigateAway(Account.RecommendSettings()) },
                    onOpenNotificationSettings = { navigateAway(Notification.NotificationSettings()) },
                )

                AccountSettingSystemSection(
                    updateState = updateState,
                    onOpenSystemSettings = { navigateAway(Account.SystemAndUpdateSettings()) },
                )

                AccountSettingAboutSection(
                    versionInfo = versionInfo,
                    onCopyVersion = {
                        copyPlainText("version", versionInfo)
                        userMessages.showShortMessage("已复制版本号")
                    },
                    openWebUrl = { navigateAway(WebPage(it)) },
                    onOpenLicenses = { navigateAway(Account.OpenSourceLicenses) },
                    onOpenWebDav = { navigateAway(Account.WebDavSync) },
                )
            }
        }
    }

    logoutTarget?.let { owner ->
        AccountSettingLogoutDialog(
            accountCount = accountStore.accounts.size,
            error = logoutError,
            confirmEnabled = accountStore.client === owner && !verification.blocksSessionChange,
            onDismissRequest = {
                logoutTarget = null
                logoutError = null
            },
            onConfirm = {
                if (accountStore.client === owner && !owner.verificationState.value.blocksSessionChange) {
                    val accountsBefore = accountStore.accountsState.value
                    try {
                        accountStore.clear()
                        logoutTarget = null
                        logoutError = null
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        if (accountStore.accountsState.value === accountsBefore) {
                            /** 保存失败会重建可用客户端；账号快照未变时，确认和错误归还给恢复的客户端。 */
                            logoutTarget = accountStore.client
                            logoutError = "退出失败：${e.message}"
                        } else {
                            logoutTarget = null
                            logoutError = null
                        }
                    }
                }
            },
            onCancel = {
                logoutTarget = null
                logoutError = null
            },
        )
    }
}

@Composable
private fun AccountSettingAboutSection(
    versionInfo: String,
    onCopyVersion: () -> Unit,
    openWebUrl: (String) -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenWebDav: () -> Unit,
) {
    SettingItemGroup(
        title = "关于",
        footer = { Text("本软件仅供学习交流使用，应用内内容由知乎网站提供，著作权归其对应作者所有。") },
    ) {
        SettingItem(
            title = { Text("知乎++") },
            description = { Text("版本号：$versionInfo") },
            icon = {
                Image(
                    painterResource(Res.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier
                        .clip(CircleShape)
                        .size(AppDimens.IconSizeLarge),
                )
            },
            modifier = Modifier.combinedClickable(
                enabled = true,
                onClick = onCopyVersion,
                onLongClick = onCopyVersion,
            ),
        )
        SettingItem(
            title = { Text("GitHub 项目地址") },
            description = { Text("https://github.com/zly2006/zhihu-plus-plus") },
            icon = { MaterialIcon(painterResource(Res.drawable.ic_github_24dp), null) },
            onClick = {
                openWebUrl("https://github.com/zly2006/zhihu-plus-plus")
            },
            endAction = {
                Icon(
                    Icons.Default.ArrowOutward,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )

        SettingItem(
            title = { Text("项目协议") },
            description = { Text("AGPL-3.0-only") },
            icon = { MaterialIcon(painterResource(Res.drawable.ic_license_24dp), null) },
            onClick = {
                openWebUrl("https://github.com/zly2006/zhihu-plus-plus/blob/master/LICENSE")
            },
            endAction = {
                Icon(
                    Icons.Default.ArrowOutward,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
        SettingItem(
            title = { Text("开源许可") },
            description = { Text("查看第三方组件许可证") },
            icon = { MaterialIcon(painterResource(Res.drawable.ic_license_24dp), null) },
            modifier = Modifier.testTag(ACCOUNT_SETTINGS_LICENSES_TAG),
            onClick = onOpenLicenses,
        )
        SettingItem(
            title = { Text("WebDAV 同步") },
            description = { Text("备份与恢复屏蔽列表和应用设置") },
            icon = { Icon(Icons.Default.Cloud, null) },
            modifier = Modifier.testTag("accountSettings.webdav"),
            onClick = onOpenWebDav,
        )
    }
}
