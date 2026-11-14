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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ArrowOutward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.github.zly2006.zhihu.account.rememberZhihuAccountStore
import com.github.zly2006.zhihu.navigation.Account
import com.github.zly2006.zhihu.navigation.Collections
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.Notification
import com.github.zly2006.zhihu.navigation.OnlineHistoryRoute
import com.github.zly2006.zhihu.navigation.Person
import com.github.zly2006.zhihu.navigation.requestLoginNavigation
import com.github.zly2006.zhihu.platform.platformBottomBarItemLimit
import com.github.zly2006.zhihu.platform.rememberPlainTextClipboard
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.platform.rememberSystemUrlOpener
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.reading.isReadingPlayerSupported
import com.github.zly2006.zhihu.ui.components.AppAlertDialog
import com.github.zly2006.zhihu.ui.components.AppFilledTonalIconButton
import com.github.zly2006.zhihu.ui.components.AppScaffold
import com.github.zly2006.zhihu.ui.components.AppSearchBar
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.ui.components.SettingItem
import com.github.zly2006.zhihu.ui.components.SettingItemGroup
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

@OptIn(ExperimentalMaterial3Api::class)
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
    val openSystemUrl = rememberSystemUrlOpener()
    val userMessages = rememberUserMessageSink()
    val systemUpdateState = rememberSystemUpdateState()
    val updateState by systemUpdateState.collectAsState()
    val versionInfo = rememberAppVersionInfo()
    val readingPlayerSupported = isReadingPlayerSupported
    val launchQrScanner = if (isAccountQrScanSupported) rememberAccountQrScanAction() else null

    val useDuo3HomeAccount by rememberObservedSetting(settings, "duo3_home_account") {
        getBoolean("duo3_home_account", false)
    }
    val selectedBottomBarItems by rememberObservedSetting(settings, BOTTOM_BAR_ITEMS_PREFERENCE_KEY) {
        getStringSet(
            BOTTOM_BAR_ITEMS_PREFERENCE_KEY,
            defaultBottomBarSelectionKeys(getBoolean("duo3_home_account", false), platformBottomBarItemLimit),
        )
    }
    val selectedBottomBarItemKeys = normalizeBottomBarSelection(
        selectedBottomBarItems,
        useDuo3HomeAccount,
        enforceMinimumSelection = true,
        maximumSelection = platformBottomBarItemLimit,
    )
    val fontSize by rememberObservedSetting(settings, PREF_FONT_SIZE) { getInt(PREF_FONT_SIZE, 100) }
    val lineHeight by rememberObservedSetting(settings, PREF_LINE_HEIGHT) { getInt(PREF_LINE_HEIGHT, 160) }
    var isDeveloper by remember { mutableStateOf(settings.getBoolean("developer", false)) }
    var clickTimes by remember { mutableIntStateOf(0) }
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
    LaunchedEffect(isDeveloper) {
        settings.putBoolean("developer", isDeveloper)
    }
    val data by accountState

    AppScaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
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
            LaunchedEffect(data.login, refreshAccountProfileOnEnter) {
                if (refreshAccountProfileOnEnter && data.login) {
                    try {
                        accountStore.client.refreshAndSaveProfile()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e("AccountSettingScreen", "Failed to refresh account profile", e)
                        userMessages.showShortMessage("获取用户信息失败")
                    }
                }
            }

            if (data.login) {
                Row(
                    Modifier
                        .testTag(ACCOUNT_SETTINGS_PROFILE_HEADER_TAG)
                        .padding(16.dp, 0.dp, 16.dp, 16.dp)
                        .clickable {
                            navigateAway(
                                Person(
                                    id = data.id,
                                    urlToken = data.urlToken ?: "",
                                    name = data.username,
                                ),
                            )
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = data.avatarUrl,
                        contentDescription = "头像",
                        modifier = Modifier
                            .size(64.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                            .clip(CircleShape),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = data.username,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.testTag(ACCOUNT_SETTINGS_PROFILE_NAME_TAG),
                    )
                    Spacer(Modifier.weight(1f))
                    if (launchQrScanner != null) {
                        AppFilledTonalIconButton(
                            onClick = launchQrScanner,
                            modifier = Modifier.size(40.dp).testTag(ACCOUNT_SETTINGS_SCAN_TAG),
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = "扫码登录")
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    AppFilledTonalIconButton(
                        onClick = {
                            logoutTarget = accountClient
                            logoutError = null
                        },
                        modifier = Modifier.size(40.dp),
                        colors = IconButtonDefaults.iconButtonColors().copy(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "退出登录",
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            } else {
                SettingItemGroup {
                    SettingItem(
                        title = { Text("登录知乎") },
                        icon = { Icon(Icons.AutoMirrored.Filled.Login, null) },
                        modifier = Modifier.testTag(ACCOUNT_SETTINGS_LOGIN_ITEM_TAG),
                        onClick = {
                            onDismissRequest()
                            requestLoginNavigation()
                        },
                    )
                }
            }

            if (useDuo3HomeAccount) {
                Row(
                    Modifier
                        .padding(horizontal = 16.dp)
                        .padding(top = 16.dp, bottom = 32.dp)
                        .clip(RoundedCornerShape(24.dp)),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (data.login) {
                        Column(
                            Modifier
                                .testTag(ACCOUNT_SETTINGS_SHORTCUT_COLLECTIONS_TAG)
                                .weight(1f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .clickable {
                                    data.urlToken?.let { navigateAway(Collections(it)) }
                                }.padding(8.dp, 16.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                Icons.Default.Bookmark,
                                null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "收藏夹",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        Column(
                            Modifier
                                .testTag(ACCOUNT_SETTINGS_SHORTCUT_SUBSCRIPTIONS_TAG)
                                .weight(1f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .clickable {
                                    navigateAway(
                                        Person(
                                            id = data.id,
                                            urlToken = data.urlToken ?: "",
                                            name = data.username,
                                            jumpTo = "关注订阅",
                                        ),
                                    )
                                }.padding(8.dp, 16.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                Icons.Default.Groups,
                                null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "关注订阅",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        Column(
                            Modifier
                                .testTag(ACCOUNT_SETTINGS_SHORTCUT_NOTIFICATION_TAG)
                                .weight(1f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .clickable {
                                    navigateAway(Notification)
                                }.padding(8.dp, 16.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            BadgedBox(
                                badge = {
                                    if (showUnreadBadge && unreadCount > 0) {
                                        Badge { Text(unreadCount.toString()) }
                                    }
                                },
                            ) {
                                Icon(
                                    Icons.Default.Notifications,
                                    null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "通知",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        if (shouldShowAccountHistoryShortcut(useDuo3HomeAccount, selectedBottomBarItemKeys)) {
                            Column(
                                Modifier
                                    .testTag(ACCOUNT_SETTINGS_SHORTCUT_HISTORY_TAG)
                                    .weight(1f)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .clickable {
                                        navigateAway(OnlineHistoryRoute)
                                    }.padding(8.dp, 16.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(
                                    Icons.Default.History,
                                    null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "浏览历史",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(32.dp))
                SettingItemGroup {
                    if (data.login) {
                        SettingItem(
                            title = { Text("查看收藏夹") },
                            icon = { Icon(Icons.Default.BookmarkBorder, null) },
                            onClick = {
                                data.urlToken?.let { navigateAway(Collections(it)) }
                            },
                        )
                        SettingItem(
                            title = { Text("查看关注订阅") },
                            description = { Text("话题、问题、专栏和收藏夹") },
                            icon = { Icon(Icons.Default.Groups, null) },
                            modifier = Modifier.testTag(ACCOUNT_SETTINGS_SHORTCUT_SUBSCRIPTIONS_TAG),
                            onClick = {
                                navigateAway(
                                    Person(
                                        id = data.id,
                                        urlToken = data.urlToken ?: "",
                                        name = data.username,
                                        jumpTo = "关注订阅",
                                    ),
                                )
                            },
                        )
                    }
                }
            }

            AppSearchBar(
                hint = "搜索设置项",
                onClick = { navigateAway(Account.SettingsSearch) },
                modifier = Modifier.padding(horizontal = 16.dp).testTag(ACCOUNT_SETTINGS_SEARCH_TAG),
            )
            Spacer(Modifier.height(16.dp))

            SettingItemGroup {
                if (data.login) {
                    SettingItem(
                        title = { Text("身份管理") },
                        description = { Text("创建马甲号或切换当前账号") },
                        icon = { Icon(Icons.Default.SwitchAccount, null) },
                        modifier = Modifier.testTag(ACCOUNT_SETTINGS_IDENTITY_MANAGEMENT_TAG),
                        onClick = { navigateAway(Account.IdentityManagement) },
                    )
                }

                SettingItem(
                    title = { Text("外观") },
                    description = { Text("皮肤、明暗与配色") },
                    icon = { Icon(Icons.Default.Palette, null) },
                    modifier = Modifier.testTag(ACCOUNT_SETTINGS_APPEARANCE_TAG),
                    onClick = { navigateAway(Account.AppearanceSettings()) },
                )

                SettingItem(
                    title = { Text("导航与交互") },
                    description = { Text("启动页、底栏、返回、分享与搜索") },
                    modifier = Modifier.testTag("accountSettings.navigation"),
                    onClick = { navigateAway(Account.NavigationSettings()) },
                )
                SettingItem(
                    title = { Text("阅读与排版") },
                    description = { Text("字号 $fontSize% · 行高 $lineHeight%") },
                    modifier = Modifier.testTag("accountSettings.readingLayout"),
                    onClick = { navigateAway(Account.ReadingSettings()) },
                )
                SettingItem(
                    title = { Text("推荐与信息流") },
                    description = { Text("推荐来源、缩略图与刷新") },
                    modifier = Modifier.testTag("accountSettings.feed"),
                    onClick = { navigateAway(Account.FeedSettings()) },
                )

                if (readingPlayerSupported) {
                    SettingItem(
                        title = { Text("朗读与播放") },
                        description = { Text("朗读内容、播放队列与条目过渡") },
                        icon = { Icon(Icons.AutoMirrored.Filled.VolumeUp, null) },
                        modifier = Modifier.testTag(ACCOUNT_SETTINGS_READING_TAG),
                        onClick = { navigateAway(Account.ReadingPlaybackSettings()) },
                    )
                }

                SettingItem(
                    title = { Text("内容过滤") },
                    description = { Text("质量过滤、去重与屏蔽规则") },
                    icon = { Icon(Icons.Default.FilterAlt, null) },
                    modifier = Modifier.testTag(ACCOUNT_SETTINGS_RECOMMEND_TAG),
                    onClick = { navigateAway(Account.RecommendSettings()) },
                )

                SettingItem(
                    title = { Text("系统与更新") },
                    description = { SystemUpdateSummary(updateState) },
                    icon = { Icon(Icons.Default.Settings, null) },
                    modifier = Modifier.testTag(ACCOUNT_SETTINGS_SYSTEM_TAG),
                    onClick = { navigateAway(Account.SystemAndUpdateSettings()) },
                )

                AnimatedVisibility(isDeveloper) {
                    SettingItem(
                        title = { Text("开发者选项") },
                        icon = { Icon(Icons.Default.Code, null) },
                        modifier = Modifier.testTag(ACCOUNT_SETTINGS_DEVELOPER_TAG),
                        onClick = { navigateAway(Account.DeveloperSettings) },
                    )
                }
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
                                .size(32.dp),
                        )
                    },
                    modifier = Modifier.combinedClickable(
                        enabled = true,
                        onClick = {
                            clickTimes++
                            if (clickTimes == 5) {
                                clickTimes = 0
                                isDeveloper = true
                                userMessages.showShortMessage("You are now a developer")
                            }
                        },
                        onLongClick = {
                            copyPlainText("version", versionInfo)
                            userMessages.showShortMessage("已复制版本号")
                        },
                    ),
                )
                SettingItem(
                    title = { Text("GitHub 项目地址") },
                    description = { Text("https://github.com/zly2006/zhihu-plus-plus") },
                    icon = { MaterialIcon(painterResource(Res.drawable.ic_github_24dp), null) },
                    onClick = {
                        openSystemUrl("https://github.com/zly2006/zhihu-plus-plus")
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
                        openSystemUrl("https://github.com/zly2006/zhihu-plus-plus/blob/master/LICENSE")
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
                    title = { Text("WebDAV 同步") },
                    description = { Text("备份与恢复屏蔽列表和应用设置") },
                    modifier = Modifier.testTag("accountSettings.webdav"),
                    onClick = { navigateAway(Account.WebDavSync) },
                )
                SettingItem(
                    title = { Text("开源许可") },
                    description = { Text("查看第三方组件许可证") },
                    icon = { MaterialIcon(painterResource(Res.drawable.ic_license_24dp), null) },
                    modifier = Modifier.testTag(ACCOUNT_SETTINGS_LICENSES_TAG),
                    onClick = { navigateAway(Account.OpenSourceLicenses) },
                )
            }
        }
    }

    logoutTarget?.let { owner ->
        AppAlertDialog(
            onDismissRequest = {
                logoutTarget = null
                logoutError = null
            },
            title = { Text("退出登录") },
            text = {
                Column {
                    Text(
                        if (accountStore.accounts.size > 1) {
                            "确定退出并移除当前登录账号吗？退出后会自动切换到另一个已保存账号。"
                        } else {
                            "确定要退出登录吗？"
                        },
                    )
                    logoutError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                AppTextButton(
                    enabled = accountStore.client === owner && !verification.blocksSessionChange,
                    onClick = {
                        if (accountStore.client === owner && !owner.verificationState.value.blocksSessionChange) {
                            try {
                                accountStore.clear()
                                logoutTarget = null
                                logoutError = null
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                if (accountStore.client === owner) {
                                    logoutError = "退出失败：${e.message}"
                                } else {
                                    logoutTarget = null
                                    logoutError = null
                                }
                            }
                        }
                    },
                ) {
                    Text("退出")
                }
            },
            dismissButton = {
                AppTextButton(onClick = {
                    logoutTarget = null
                    logoutError = null
                }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun SystemUpdateSummary(state: SystemUpdateState) {
    when (state) {
        is SystemUpdateState.UpdateAvailable -> Text(
            "发现新版本 ${state.version}",
            color = MaterialTheme.colorScheme.primary,
        )
        is SystemUpdateState.Downloading -> Text("正在下载 ${state.update.version}")
        is SystemUpdateState.Downloaded -> Text(
            "${state.update.version} 已下载，可以安装",
            color = MaterialTheme.colorScheme.primary,
        )
        is SystemUpdateState.Error -> Text(
            "更新失败：${state.message}",
            color = MaterialTheme.colorScheme.error,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        else -> Text("版本更新、更新通道与隐私")
    }
}
