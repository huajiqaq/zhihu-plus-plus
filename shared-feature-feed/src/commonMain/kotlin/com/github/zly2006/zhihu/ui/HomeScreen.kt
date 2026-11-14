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

import com.github.zly2006.zhihu.account.ZhihuRequestProfile
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowCircleUp
import androidx.compose.material.icons.filled.CopyAll
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MarkUnreadChatAlt
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.github.zly2006.zhihu.account.rememberZhihuAccountStore
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.Feed
import com.github.zly2006.zhihu.data.MOBILE_NOTIFICATION_MESSAGE_URL
import com.github.zly2006.zhihu.data.MobileNotificationMessageOverview
import com.github.zly2006.zhihu.data.RecommendationMode
import com.github.zly2006.zhihu.data.target
import com.github.zly2006.zhihu.navigation.Account
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.Pin
import com.github.zly2006.zhihu.navigation.Search
import com.github.zly2006.zhihu.navigation.WriteAnswerChooser
import com.github.zly2006.zhihu.navigation.WriteArticle
import com.github.zly2006.zhihu.navigation.WritePin
import com.github.zly2006.zhihu.navigation.WriteQuestion
import com.github.zly2006.zhihu.navigation.requestLoginNavigation
import com.github.zly2006.zhihu.notification.HOME_NOTIFICATION_REFRESH_INTERVAL_MILLIS
import com.github.zly2006.zhihu.notification.HomeNotificationAction
import com.github.zly2006.zhihu.notification.OnlineHomeNotification
import com.github.zly2006.zhihu.notification.OnlineHomeNotificationRepository
import com.github.zly2006.zhihu.notification.rememberNotificationSettingsStore
import com.github.zly2006.zhihu.platform.UserMessageDuration
import com.github.zly2006.zhihu.platform.rememberAppPrivateDirectory
import com.github.zly2006.zhihu.platform.rememberExternalUrlOpener
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.platform.rememberWebViewUrlOpener
import com.github.zly2006.zhihu.reading.RegisterReadingQueueSource
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.theme.NEW_UI_NOTICE_PREFERENCE_KEY
import com.github.zly2006.zhihu.theme.ThemeManager
import com.github.zly2006.zhihu.theme.miuixThemeAvailable
import com.github.zly2006.zhihu.ui.components.AppButton
import com.github.zly2006.zhihu.ui.components.AppCard
import com.github.zly2006.zhihu.ui.components.AppDialog
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppDropdownMenuItem
import com.github.zly2006.zhihu.ui.components.AppFloatingActionButton
import com.github.zly2006.zhihu.ui.components.AppScaffold
import com.github.zly2006.zhihu.ui.components.AppSearchBar
import com.github.zly2006.zhihu.ui.components.AppSpinner
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.ui.components.BlockByKeywordsDialog
import com.github.zly2006.zhihu.ui.components.DraggableFabColumn
import com.github.zly2006.zhihu.ui.components.FeedAuthorBlockConfirmDialog
import com.github.zly2006.zhihu.ui.components.FeedAuthorBlockRequest
import com.github.zly2006.zhihu.ui.components.FeedAuthorBlockType
import com.github.zly2006.zhihu.ui.components.FeedCard
import com.github.zly2006.zhihu.ui.components.FeedPullToRefresh
import com.github.zly2006.zhihu.ui.components.MyModalBottomSheet
import com.github.zly2006.zhihu.ui.components.PaginatedList
import com.github.zly2006.zhihu.ui.components.SettingItem
import com.github.zly2006.zhihu.ui.components.appGlassBlur
import com.github.zly2006.zhihu.ui.components.appGlassContainerColor
import com.github.zly2006.zhihu.ui.components.feedKeywordExtractionAvailable
import com.github.zly2006.zhihu.ui.components.pageTurnViewportWithGuide
import com.github.zly2006.zhihu.ui.components.rememberPageTurnTarget
import com.github.zly2006.zhihu.ui.components.settingSkinIsMiuix
import com.github.zly2006.zhihu.ui.subscreens.APP_BLUR_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.subscreens.DEFAULT_FAB_OPACITY
import com.github.zly2006.zhihu.ui.subscreens.PREF_FAB_OPACITY
import com.github.zly2006.zhihu.ui.subscreens.SystemUpdateState
import com.github.zly2006.zhihu.ui.subscreens.rememberSystemUpdateState
import com.github.zly2006.zhihu.util.Log
import com.github.zly2006.zhihu.util.json
import com.github.zly2006.zhihu.viewmodel.QUALITY_FILTER_MODE_PREFERENCE_KEY
import com.github.zly2006.zhihu.viewmodel.QualityFilterMode
import com.github.zly2006.zhihu.viewmodel.feed.BaseFeedViewModel
import com.github.zly2006.zhihu.viewmodel.feed.HomeFeedInteractionViewModel
import com.github.zly2006.zhihu.viewmodel.feed.HomeFeedViewModel
import com.github.zly2006.zhihu.viewmodel.local.LocalHomeFeedViewModel
import com.github.zly2006.zhihu.viewmodel.rememberPaginationEnvironment
import com.github.zly2006.zhihu.viewmodel.za.AndroidHomeFeedViewModel
import com.github.zly2006.zhihu.viewmodel.za.MixedHomeFeedViewModel
import io.ktor.client.request.get
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import com.github.zly2006.zhihu.ui.components.AppIcon as Icon

const val HOME_TOP_ACTIONS_TAG = "home_top_actions"
const val HOME_SEARCH_BUTTON_TAG = "home_search_button"
const val HOME_CREATE_FAB_TAG = "home_create_fab"
const val HOME_CREATE_MENU_TAG = "home_create_menu"
const val HOME_WRITE_QUESTION_BUTTON_TAG = "home_write_question_button"
const val HOME_WRITE_ANSWER_BUTTON_TAG = "home_write_answer_button"
const val HOME_WRITE_PIN_BUTTON_TAG = "home_write_pin_button"
const val HOME_ACCOUNT_BUTTON_TAG = "home_account_button"
const val HOME_ACCOUNT_BADGE_TAG = "home_account_badge"
const val HOME_FEED_LIST_TAG = "home_feed_list"
const val HOME_REFRESH_BUTTON_TAG = "home_refresh_button"
const val HOME_AUTHOR_POLL_ANNOUNCEMENT_TAG = "home_author_poll_announcement"
const val HOME_ONLINE_NOTIFICATION_TAG = "home_online_notification"

private const val MAX_HOME_PIN_ANNOUNCEMENTS = 3

fun homeAuthorPollAnnouncementTag(pinId: Long): String = "$HOME_AUTHOR_POLL_ANNOUNCEMENT_TAG:$pinId"

fun homeOnlineNotificationTag(uuid: String): String = "$HOME_ONLINE_NOTIFICATION_TAG:$uuid"

fun homePinAnnouncementReadKey(pinId: Long): String = "$HOME_PIN_ANNOUNCEMENT_READ_KEY_PREFIX$pinId"

private data class HomeTransaction(
    val key: String,
    val title: String,
    val content: String?,
    val icon: ImageVector,
    val acceptText: String?,
    val onAccept: (() -> Unit)?,
    val dismissText: String,
    val onDismiss: () -> Unit,
)

/** 公告与同步 owner 随 feed ViewModel 存活，避免 Pager 重建后恢复列表索引时可见卡片移位。 */
private class HomeScreenState : ViewModel() {
    val onlineNotifications = mutableStateOf(emptyMap<String, OnlineHomeNotification>())
    val authorPinAnnouncements = mutableStateOf(emptyList<HomePinAnnouncement>())
    val dismissedUpdateVersion = mutableStateOf<String?>(null)
    var transactionsExpanded by mutableStateOf(false)
}

@Composable
private fun HomeAccountAvatarAction(
    avatarUrl: String?,
    unreadCount: Int,
    showUnreadBadge: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(AppDimens.AccountEntrySize)
            .testTag(HOME_ACCOUNT_BUTTON_TAG)
            /** clip 在 clickable 前，避免方形水波纹。 */
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BadgedBox(
            badge = {
                if (showUnreadBadge && unreadCount > 0) {
                    Badge(modifier = Modifier.testTag(HOME_ACCOUNT_BADGE_TAG)) { }
                }
            },
        ) {
            if (!avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = "账号",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(AppDimens.AvatarSizeMedium)
                        .border(
                            AppDimens.Hairline,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                            CircleShape,
                        ).clip(CircleShape),
                )
            } else {
                Icon(
                    Icons.Default.AccountCircle,
                    contentDescription = "账号",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(AppDimens.AvatarSizeMedium),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    scrollToTopTrigger: Int,
    innerPadding: PaddingValues,
    showTopActions: Boolean = true,
    isActive: Boolean = true,
) {
    val readingPlayerOverlayPadding = LocalReadingPlayerOverlayPadding.current
    val navigator = LocalNavigator.current
    val paginationEnvironment = rememberPaginationEnvironment(allowGuestAccess = true)
    val accountStore = rememberZhihuAccountStore()
    val settings = rememberSettingsStore()
    val skin = LocalAppSkinTokens.current
    val pageBackground = if (settingSkinIsMiuix()) skin.surface else MaterialTheme.colorScheme.background
    val homeState: HomeScreenState = viewModel { HomeScreenState() }
    val webViewModel: HomeFeedViewModel = viewModel { HomeFeedViewModel() }
    val remoteHistory = webViewModel.remoteHistory
    val appPrivateDirectory = rememberAppPrivateDirectory()
    val notificationSettings = rememberNotificationSettingsStore()
    val userMessages = rememberUserMessageSink()
    val openExternalUrl = rememberExternalUrlOpener()
    val openWebViewUrl = rememberWebViewUrlOpener()
    val lifecycleOwner = LocalLifecycleOwner.current

    val showRefreshFab = settings.getBoolean("showRefreshFab", true)
    val showCreateFab by rememberObservedSetting(settings, "showCreateFab") {
        getBoolean("showCreateFab", true)
    }
    val autoRefreshOnStartup = settings.getBoolean(AUTO_REFRESH_HOME_ON_STARTUP_PREFERENCE_KEY, true)
    val showUnreadBadge = notificationSettings.getUnreadBadgeEnabled()
    var showAccountBottomSheet by remember { mutableStateOf(false) }
    var showCreateMenu by remember { mutableStateOf(false) }
    LaunchedEffect(showCreateFab) {
        if (!showCreateFab) showCreateMenu = false
    }
    val appBlurEnabled by rememberObservedSetting(settings, APP_BLUR_PREFERENCE_KEY) {
        getBoolean(APP_BLUR_PREFERENCE_KEY, true)
    }
    val createMenuBlurRadius by animateDpAsState(
        targetValue = if (showCreateMenu && appBlurEnabled) {
            AppDimens.Space8
        } else {
            AppDimens.ElevationNone
        },
        animationSpec = tween(durationMillis = 200),
        label = "createMenuBlurRadius",
    )

    val currentRecommendationMode =
        RecommendationMode.entries.find {
            it.key == settings.getString("recommendationMode", RecommendationMode.MIXED.key)
        } ?: RecommendationMode.MIXED
    val startupCacheFile = remember(appPrivateDirectory, currentRecommendationMode) {
        Path(appPrivateDirectory, homeFeedStartupCacheFileName(currentRecommendationMode))
    }

    val account = rememberAccountSettingsAccountState().value
    if (account.login && !account.hasRequiredCookie) {
        AppDialog(
            onDismissRequest = {},
            title = "Cookie 不完整",
            summary = "当前登录信息缺少必要的 Cookie d_c0，请重新登录。",
            confirmText = "重新登录",
            onConfirm = ::requestLoginNavigation,
        )
    }
    val updateState by rememberSystemUpdateState().collectAsState()
    val updateAnnouncement = updateState as? SystemUpdateState.UpdateAvailable
    val versionName = rememberAppVersionInfo().substringBefore(' ').takeIf { it.firstOrNull()?.isDigit() == true }
    val onlineNotificationRepository = remember(settings) { OnlineHomeNotificationRepository(settings) }
    var onlineNotifications by homeState.onlineNotifications
    val isDebuggable = rememberHomeIsDebuggable()
    val viewModel: BaseFeedViewModel = when (currentRecommendationMode) {
        RecommendationMode.WEB -> webViewModel
        RecommendationMode.ANDROID -> viewModel { AndroidHomeFeedViewModel(remoteHistory) }
        RecommendationMode.LOCAL -> viewModel { LocalHomeFeedViewModel(accountStore) }
        RecommendationMode.MIXED -> viewModel { MixedHomeFeedViewModel(remoteHistory) }
    }
    var refreshingFromFab by remember(viewModel) { mutableStateOf(false) }
    LaunchedEffect(viewModel, viewModel.isLoading) {
        if (!viewModel.isLoading) refreshingFromFab = false
    }
    val localHomeViewModel = viewModel as? LocalHomeFeedViewModel
    val readingQueueSourceId = "home:${currentRecommendationMode.name}"
    RegisterReadingQueueSource(
        sourceId = readingQueueSourceId,
        items = viewModel.displayItems,
    )

    var dismissedUpdateVersion by homeState.dismissedUpdateVersion
    var showNewUiNotice by remember {
        mutableStateOf(!settings.getBoolean(NEW_UI_NOTICE_PREFERENCE_KEY, false))
    }

    fun dismissNewUiNotice() {
        showNewUiNotice = false
        settings.putBoolean(NEW_UI_NOTICE_PREFERENCE_KEY, true)
    }
    var authorPinAnnouncements by homeState.authorPinAnnouncements

    val listState = rememberLazyListState()
    /** 顶部未滚动时隐藏悬浮按钮，避免遮挡公告操作。 */
    val shouldShowFeedFabs = viewModel.displayItems.isNotEmpty() &&
        (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0)
    var cachedScrollToTopTrigger by remember { mutableIntStateOf(scrollToTopTrigger) }
    LaunchedEffect(scrollToTopTrigger) {
        when (
            topLevelReselectAction(
                triggerDelta = scrollToTopTrigger - cachedScrollToTopTrigger,
                isAtTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0,
            )
        ) {
            TopLevelReselectAction.Refresh -> viewModel.refresh(paginationEnvironment)
            TopLevelReselectAction.ScrollToTop -> listState.animateScrollToItem(0)
            null -> {}
        }
        cachedScrollToTopTrigger = scrollToTopTrigger
    }

    var unreadCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        try {
            unreadCount = paginationEnvironment
                .get("$MOBILE_NOTIFICATION_MESSAGE_URL?limit=20", ZhihuRequestProfile.Mobile)
                .json<MobileNotificationMessageOverview>()
                .totalUnreadCount
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    val latestLoadedDisplayItems = viewModel.latestLoadedDisplayItems.value
    LaunchedEffect(latestLoadedDisplayItems) {
        if (latestLoadedDisplayItems.isNotEmpty()) {
            encodeHomeFeedStartupSnapshot(latestLoadedDisplayItems)?.let { serialized ->
                withContext(Dispatchers.Default) {
                    runCatching {
                        SystemFileSystem.sink(startupCacheFile).buffered().use { it.writeString(serialized) }
                    }
                }
            }
        }
    }

    val completedPageCount = viewModel.completedPageCount
    LaunchedEffect(completedPageCount) {
        if (completedPageCount > 0 &&
            currentRecommendationMode == RecommendationMode.WEB &&
            settings.getString(QUALITY_FILTER_MODE_PREFERENCE_KEY, QualityFilterMode.RULES.name) == QualityFilterMode.HIDE.name &&
            viewModel.displayItems.isEmpty() &&
            !viewModel.isEnd
        ) {
            viewModel.loadMore(paginationEnvironment)
        }
    }

    LaunchedEffect(lifecycleOwner, currentRecommendationMode, account.login, account.id, autoRefreshOnStartup, isActive) {
        if (!isActive) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (account.login &&
                account.hasRequiredCookie &&
                settings.getBoolean("enableContentFilter", true) &&
                !settings.getBoolean("reverseBlock", false)
            ) {
                remoteHistory.start(homeState.viewModelScope, paginationEnvironment)
            }
            if (!account.login && settings.getBoolean("loginForRecommendation", true)) {
                requestLoginNavigation()
            } else if (viewModel.displayItems.isEmpty()) {
                val cachedItems = if (autoRefreshOnStartup) {
                    emptyList()
                } else {
                    withContext(Dispatchers.Default) {
                        runCatching {
                            if (SystemFileSystem.exists(startupCacheFile)) {
                                SystemFileSystem.source(startupCacheFile).buffered().use { source ->
                                    decodeHomeFeedStartupSnapshot(source.readString())
                                }
                            } else {
                                emptyList()
                            }
                        }.getOrDefault(emptyList())
                    }
                }
                if (cachedItems.isNotEmpty()) {
                    remoteHistory.awaitRecentPage()
                    val unreadCachedItems = remoteHistory.filterRemoteReads(cachedItems)
                    if (unreadCachedItems.isEmpty()) {
                        viewModel.refresh(paginationEnvironment)
                    } else {
                        viewModel.addDisplayItems(unreadCachedItems)
                    }
                } else {
                    viewModel.refresh(paginationEnvironment)
                }
            }
        }
    }

    val historyRevision by remoteHistory.importedRevision.collectAsState()
    LaunchedEffect(remoteHistory, viewModel, historyRevision, isActive) {
        if (!isActive) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.isNotEmpty() }.first { it }
        snapshotFlow { viewModel.displayItems.toList() }.collectLatest { items ->
            /** 仅清理未见尾部，保持已浏览卡片位置稳定。 */
            val visibleKeys = listState.layoutInfo.visibleItemsInfo
                .map { it.key }
                .toSet()
            val lastVisible = items.indexOfLast { it.stableKey in visibleKeys }
            val candidates = items.drop(lastVisible + 1)
            val keptKeys = remoteHistory.filterRemoteReads(candidates).map { it.stableKey }.toSet()
            val removedKeys = candidates.filterNot { it.stableKey in keptKeys }.map { it.stableKey }.toSet()
            /** 数据库查询挂起期间可能已滚动，删除前重取可见边界。 */
            val currentVisibleKeys = listState.layoutInfo.visibleItemsInfo
                .map { it.key }
                .toSet()
            val protectedEnd = viewModel.displayItems.indexOfLast { it.stableKey in currentVisibleKeys }
            for (index in viewModel.displayItems.lastIndex downTo protectedEnd + 1) {
                if (viewModel.displayItems[index].stableKey in removedKeys) viewModel.displayItems.removeAt(index)
            }
            viewModel.latestLoadedDisplayItems.value = remoteHistory.filterRemoteReads(viewModel.latestLoadedDisplayItems.value)
        }
    }

    LaunchedEffect(lifecycleOwner, scrollToTopTrigger) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val loadedAnnouncements = try {
                paginationEnvironment
                    .fetchJson(ZHIHU_PLUS_AUTHOR_PINS_URL, "")
                    ?.let(::decodeHomePinAnnouncements)
                    ?.filter {
                        it.kind == HomePinAnnouncementKind.Poll ||
                            it.createdAtEpochSeconds >= Clock.System.now().epochSeconds - 5 * 24 * 60 * 60
                    }?.filterNot { settings.getBoolean(homePinAnnouncementReadKey(it.pinId), false) }
                    ?.take(MAX_HOME_PIN_ANNOUNCEMENTS)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("HomeScreen", "Failed to load home pin announcements", e)
                null
            }
            if (loadedAnnouncements != null) {
                authorPinAnnouncements = loadedAnnouncements
            }
        }
    }

    LaunchedEffect(lifecycleOwner, paginationEnvironment, versionName) {
        if (versionName != null) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    try {
                        onlineNotifications = onlineNotificationRepository.load(
                            versionName = versionName,
                            httpClient = paginationEnvironment.httpClient(),
                        ).associateBy { it.uuid }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e("HomeScreen", "Failed to load online notifications", e)
                    }
                    delay(HOME_NOTIFICATION_REFRESH_INTERVAL_MILLIS)
                }
            }
        }
    }

    LaunchedEffect(viewModel.errorMessage) {
        viewModel.errorMessage?.let {
            userMessages.showMessage(it, UserMessageDuration.Long)
        }
    }

    var feedAuthorBlockRequest by remember { mutableStateOf<FeedAuthorBlockRequest?>(null) }

    var showBlockByKeywordsDialog by remember { mutableStateOf(false) }
    var feedToBlockByKeywords by remember { mutableStateOf<Pair<String, String?>?>(null) }
    val pageTurnTarget = rememberPageTurnTarget(
        listState = listState,
        enabled = isActive &&
            !showAccountBottomSheet &&
            !showCreateMenu &&
            feedAuthorBlockRequest == null &&
            !showBlockByKeywordsDialog &&
            (!account.login || account.hasRequiredCookie),
    )

    val transactions = buildList {
        if (showNewUiNotice) {
            add(
                HomeTransaction(
                    key = "new_ui_notice",
                    title = "全新 UI 已启用",
                    content = if (miuixThemeAvailable) {
                        "界面采用 Material 3 Expressive 与 Miuix 两套设计语言。可在「外观」中切换皮肤，账号、阅读进度与设置都会保留。"
                    } else {
                        "Lite 版采用 Material 3 Expressive 设计，账号、阅读进度与设置都会保留。"
                    },
                    icon = Icons.Default.Palette,
                    acceptText = "切换皮肤".takeIf { miuixThemeAvailable },
                    onAccept = if (miuixThemeAvailable) {
                        {
                            dismissNewUiNotice()
                            navigator.onNavigate(Account.AppearanceSettings())
                        }
                    } else {
                        null
                    },
                    dismissText = "知道了",
                    onDismiss = ::dismissNewUiNotice,
                ),
            )
        }
        updateAnnouncement?.takeIf { dismissedUpdateVersion != it.version }?.let { update ->
            add(
                HomeTransaction(
                    key = "update:${update.version}",
                    title = "发现新版本：${update.version}${if (update.isNightly) " (Nightly)" else ""}",
                    content = "查看更新内容和安装选项。",
                    icon = Icons.Default.ArrowCircleUp,
                    acceptText = "查看更新",
                    onAccept = { navigator.onNavigate(Account.SystemAndUpdateSettings()) },
                    dismissText = "以后",
                    onDismiss = { dismissedUpdateVersion = update.version },
                ),
            )
        }
        onlineNotifications.values.forEach { notification ->
            val markRead = {
                onlineNotificationRepository.markRead(notification)
                onlineNotifications = onlineNotifications - notification.uuid
            }
            add(
                HomeTransaction(
                    key = homeOnlineNotificationTag(notification.uuid),
                    title = notification.title,
                    content = notification.content,
                    icon = Icons.Default.Notifications,
                    acceptText = notification.accept?.text,
                    onAccept = notification.accept?.let { accept ->
                        {
                            val action = accept.action
                            if (action != null) {
                                when (action) {
                                    is HomeNotificationAction.OpenUrl ->
                                        if (action.inWebView) openWebViewUrl(action.url) else openExternalUrl(action.url)
                                    is HomeNotificationAction.Navigate -> navigator.onNavigate(action.destination)
                                    is HomeNotificationAction.SetBoolean -> settings.putBoolean(action.name, action.value)
                                    is HomeNotificationAction.SetString -> settings.putString(action.name, action.value)
                                    is HomeNotificationAction.SetInt -> settings.putInt(action.name, action.value)
                                }
                                markRead()
                            }
                        }
                    },
                    dismissText = notification.dismiss,
                    onDismiss = markRead,
                ),
            )
        }
        authorPinAnnouncements.forEach { announcement ->
            val markRead = {
                settings.putBoolean(homePinAnnouncementReadKey(announcement.pinId), true)
                authorPinAnnouncements = authorPinAnnouncements.filterNot { it.pinId == announcement.pinId }
            }
            add(
                HomeTransaction(
                    key = homeAuthorPollAnnouncementTag(announcement.pinId),
                    title = if (announcement.kind == HomePinAnnouncementKind.Poll) "请给未来的知乎++提出建议" else "知乎++新动态",
                    content = buildString {
                        append(announcement.title)
                        val details = buildList {
                            if (announcement.optionCount > 0) add("${announcement.optionCount} 个选项")
                            if (announcement.memberCount > 0) add("${announcement.memberCount} 人已参与")
                        }
                        if (details.isNotEmpty()) append("\n${details.joinToString(" · ")}")
                    },
                    icon = Icons.Default.Flag,
                    acceptText = if (announcement.kind == HomePinAnnouncementKind.Poll) "去投票" else "查看",
                    onAccept = {
                        markRead()
                        navigator.onNavigate(Pin(announcement.pinId))
                    },
                    dismissText = "关闭",
                    onDismiss = markRead,
                ),
            )
        }
    }

    val density = LocalDensity.current
    var homeBounds by remember { mutableStateOf<Rect?>(null) }
    var createFabBounds by remember { mutableStateOf<Rect?>(null) }
    var feedListBounds by remember { mutableStateOf<Rect?>(null) }
    val transactionsExpanded = homeState.transactionsExpanded && transactions.isNotEmpty()
    val createFabVisible = showCreateFab
    val utilityFabsVisible = showRefreshFab &&
        !transactionsExpanded &&
        (shouldShowFeedFabs || refreshingFromFab) &&
        !showCreateMenu
    var utilityFabBounds by remember(utilityFabsVisible) { mutableStateOf<Rect?>(null) }
    val utilityBottomAvoidance = with(density) {
        val pane = homeBounds
        val fab = createFabBounds.takeIf { createFabVisible }
        if (pane != null && fab != null) {
            (pane.bottom - fab.top).coerceAtLeast(0f).toDp() + AppDimens.Space8
        } else {
            innerPadding.calculateBottomPadding() + readingPlayerOverlayPadding + AppDimens.Space8
        }
    }
    /** 固定视口高度，避免短列表测量与末端留白互相反馈。 */
    val feedBottomPadding = maxOf(
        innerPadding.calculateBottomPadding() + readingPlayerOverlayPadding,
        with(density) {
            val list = feedListBounds
            val column = utilityFabBounds.takeIf { utilityFabsVisible }
            if (list != null && column != null && column.top < list.bottom && column.bottom > list.top) {
                /** 按同一 root 坐标系预留整列高度，让末卡菜单能滚出按钮遮挡。 */
                (list.bottom - column.top.coerceAtLeast(list.top)).coerceAtLeast(0f).toDp() + AppDimens.Space8
            } else {
                AppDimens.ElevationNone
            }
        },
    )
    Box(
        modifier = Modifier.fillMaxSize().onGloballyPositioned {
            val bounds = it.boundsInRoot()
            if (homeBounds != bounds) homeBounds = bounds
        },
    ) {
        AppScaffold(
            modifier = Modifier
                .fillMaxSize()
                .blur(createMenuBlurRadius),
            containerColor = pageBackground,
            topBar = {
                if (!showTopActions) {
                    return@AppScaffold
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        /** 固定顶栏采样下方滚动内容以呈现磨砂效果。 */
                        .then(
                            Modifier.appGlassBlur(
                                shape = RectangleShape,
                                tint = (
                                    if (settingSkinIsMiuix()) {
                                        LocalAppSkinTokens.current.surfaceContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainer
                                    }
                                ).copy(alpha = 0.30f),
                            ),
                        ).background(appGlassContainerColor(pageBackground))
                        .statusBarsPadding()
                        .testTag(HOME_TOP_ACTIONS_TAG)
                        .padding(top = AppDimens.Space8, bottom = AppDimens.Space4),
                ) {
                    AppSearchBar(
                        hint = "搜索",
                        onClick = { navigator.onNavigate(Search(query = "")) },
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .testTag(HOME_SEARCH_BUTTON_TAG),
                        trailing = {
                            HomeAccountAvatarAction(
                                avatarUrl = account.avatarUrl,
                                unreadCount = unreadCount,
                                showUnreadBadge = showUnreadBadge,
                                onClick = { showAccountBottomSheet = true },
                            )
                        },
                    )
                }
            },
        ) { scaffoldPadding ->
            if (showAccountBottomSheet) {
                MyModalBottomSheet(
                    onDismissRequest = { showAccountBottomSheet = false },
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    AccountSettingScreen(
                        innerPadding = PaddingValues(AppDimens.ElevationNone),
                        unreadCount = unreadCount,
                        showUnreadBadge = showUnreadBadge,
                        onDismissRequest = { showAccountBottomSheet = false },
                    )
                }
            }

            FeedPullToRefresh(viewModel, paginationEnvironment, padding = PaddingValues(top = scaffoldPadding.calculateTopPadding())) {
                PaginatedList(
                    items = viewModel.displayItems,
                    listState = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .pageTurnViewportWithGuide(pageTurnTarget)
                        .onGloballyPositioned {
                            val bounds = it.boundsInRoot()
                            if (feedListBounds != bounds) feedListBounds = bounds
                        }.testTag(HOME_FEED_LIST_TAG),
                    contentPadding = PaddingValues(
                        top = scaffoldPadding.calculateTopPadding() + AppDimens.Space8,
                        bottom = feedBottomPadding,
                    ),
                    onLoadMore = { viewModel.loadMore(paginationEnvironment) },
                    isEnd = { viewModel.isEnd },
                    isLoading = { viewModel.isLoading },
                    loadError = { viewModel.errorMessage },
                    onRetry = { viewModel.retry(paginationEnvironment) },
                    key = { item -> item.stableKey },
                    topContent = {
                        if (transactions.isNotEmpty()) {
                            item("home_transactions") {
                                AppCard(
                                    onClick = { homeState.transactionsExpanded = !homeState.transactionsExpanded },
                                    containerColor = if (ThemeManager.isDarkTheme()) MaterialTheme.colorScheme.surfaceContainerHighest else skin.surfaceContainer,
                                    contentColor = skin.onSurface,
                                    modifier = Modifier
                                        .padding(horizontal = AppDimens.PageHorizontalPadding, vertical = AppDimens.Space4)
                                        .fillMaxWidth()
                                        .testTag("home_transactions_toggle")
                                        .semantics { stateDescription = if (homeState.transactionsExpanded) "已展开" else "已收起" },
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = AppDimens.TouchTarget)
                                            .padding(horizontal = AppDimens.Space16, vertical = AppDimens.Space12),
                                        horizontalArrangement = Arrangement.spacedBy(AppDimens.Space12),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            Icons.Default.Notifications,
                                            contentDescription = null,
                                            tint = skin.primary,
                                            modifier = Modifier.size(AppDimens.IconSizeMedium),
                                        )
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(AppDimens.Space4),
                                        ) {
                                            Text("通知与动态", style = skin.body2, fontWeight = FontWeight.SemiBold)
                                            if (!homeState.transactionsExpanded) {
                                                Text(
                                                    transactions.first().title,
                                                    style = skin.footnote2,
                                                    color = skin.onSurfaceVariantSummary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                        Text("${transactions.size} 条", style = skin.footnote2, color = skin.onSurfaceVariantSummary)
                                        Icon(
                                            if (homeState.transactionsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                            contentDescription = if (homeState.transactionsExpanded) "收起" else "展开",
                                            tint = skin.onSurfaceVariantSummary,
                                            modifier = Modifier.size(AppDimens.IconSizeMedium),
                                        )
                                    }
                                }
                            }
                            if (homeState.transactionsExpanded) {
                                transactions.forEach { transaction ->
                                    item(transaction.key) {
                                        HomeTransactionRow(transaction)
                                    }
                                }
                            }
                        }
                    },
                ) { item ->
                    FeedCard(
                        item,
                        readingQueueSourceId = readingQueueSourceId,
                        thumbnailUrl = when (val target = item.feed?.target) {
                            is Feed.AnswerTarget -> target.thumbnail
                            else -> null
                        },
                        menuItems = { dismissMenu ->
                            if (feedKeywordExtractionAvailable) {
                                AppDropdownMenuItem(
                                    text = { Text("按关键词屏蔽") },
                                    onClick = {
                                        dismissMenu()
                                        viewModel.handleBlockByKeywords(paginationEnvironment, userMessages, item) { (_, contentInfo) ->
                                            feedToBlockByKeywords = contentInfo.first to contentInfo.second
                                            showBlockByKeywordsDialog = true
                                        }
                                    },
                                )
                            }
                            AppDropdownMenuItem(
                                text = { Text("屏蔽用户") },
                                onClick = {
                                    dismissMenu()
                                    viewModel.handleBlockUser(paginationEnvironment, userMessages, item) { authorInfo ->
                                        feedAuthorBlockRequest = FeedAuthorBlockRequest(
                                            type = FeedAuthorBlockType.CONTENT_AUTHOR,
                                            userId = authorInfo.first,
                                            userName = authorInfo.second,
                                        )
                                    }
                                },
                            )
                            val canBlockQuestionAuthor = when (item.feed?.target) {
                                is Feed.AnswerTarget, is Feed.QuestionTarget -> true
                                else -> item.raw is DataHolder.Answer || item.raw is DataHolder.Question
                            }
                            if (canBlockQuestionAuthor) {
                                AppDropdownMenuItem(
                                    text = { Text("屏蔽提问者") },
                                    onClick = {
                                        dismissMenu()
                                        viewModel.handleBlockQuestionAuthor(paginationEnvironment, userMessages, item) { authorInfo ->
                                            feedAuthorBlockRequest = FeedAuthorBlockRequest(
                                                type = FeedAuthorBlockType.QUESTION_AUTHOR,
                                                userId = authorInfo.first,
                                                userName = authorInfo.second,
                                            )
                                        }
                                    },
                                )
                            }
                            val topics = when (val raw = item.raw) {
                                is DataHolder.Answer -> raw.question.topics
                                is DataHolder.Question -> raw.topics
                                is DataHolder.Article -> raw.topics ?: emptyList()
                                is DataHolder.Pin -> raw.topics ?: emptyList()
                                else -> emptyList()
                            }
                            topics.forEach { topic ->
                                AppDropdownMenuItem(
                                    text = { Text("屏蔽「${topic.name}」") },
                                    onClick = {
                                        dismissMenu()
                                        viewModel.handleBlockTopic(userMessages, topic.id, topic.name)
                                    },
                                )
                            }
                        },
                    ) { clickedItem, destination ->
                        val feed = clickedItem.feed
                        if (feed != null) {
                            (viewModel as? HomeFeedInteractionViewModel)
                                ?.onUiContentClick(paginationEnvironment, feed)
                        } else {
                            localHomeViewModel?.onLocalItemOpened(clickedItem)
                        }
                        if (destination != null) {
                            navigator.onNavigate(destination)
                        }
                    }
                }
            }
        }

        val createFabOpacity by rememberObservedSetting(settings, PREF_FAB_OPACITY) {
            getInt(PREF_FAB_OPACITY, DEFAULT_FAB_OPACITY).coerceIn(10, 100) / 100f
        }
        if (createFabVisible) {
            AppFloatingActionButton(
                onClick = { showCreateMenu = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .graphicsLayer { alpha = createFabOpacity }
                    .padding(
                        end = AppDimens.Space16,
                        bottom = innerPadding.calculateBottomPadding() +
                            readingPlayerOverlayPadding +
                            AppDimens.Space8,
                    ).onGloballyPositioned {
                        val bounds = it.boundsInRoot()
                        if (createFabBounds != bounds) createFabBounds = bounds
                    }.testTag(HOME_CREATE_FAB_TAG),
                containerColor = if (settingSkinIsMiuix()) null else FloatingActionButtonDefaults.containerColor,
            ) {
                Icon(Icons.Default.Add, contentDescription = "创作")
            }
        }
        if (utilityFabsVisible) {
            DraggableFabColumn(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = AppDimens.Space16)
                    .graphicsLayer { alpha = createFabOpacity }
                    .onGloballyPositioned {
                        val bounds = it.boundsInRoot()
                        if (utilityFabBounds != bounds) utilityFabBounds = bounds
                    },
                preferenceName = "homeUtilityFabs",
                /** 皮肤 FAB 尺寸不同，避让需使用实测 root bounds（Miuix 为 60dp）。 */
                bottomAvoidance = utilityBottomAvoidance,
            ) {
                if (isDebuggable) {
                    AppFloatingActionButton(
                        onClick = {
                            val data = Json.encodeToString(viewModel.debugData)
                            paginationEnvironment.setPlainTextClipboard("data", data)
                            userMessages.showShortMessage("已复制调试数据")
                        },
                        containerColor = FloatingActionButtonDefaults.containerColor,
                    ) {
                        Icon(Icons.Default.CopyAll, contentDescription = "复制")
                    }
                }
                AppFloatingActionButton(
                    onClick = {
                        refreshingFromFab = true
                        viewModel.refresh(paginationEnvironment)
                    },
                    modifier = Modifier.testTag(HOME_REFRESH_BUTTON_TAG),
                    containerColor = FloatingActionButtonDefaults.containerColor,
                ) {
                    if (viewModel.isLoading && !viewModel.isPullToRefresh) {
                        AppSpinner(modifier = Modifier.testTag("home_refresh_loading"), size = if (settingSkinIsMiuix()) null else AppDimens.IconSizeLarge)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                }
            }
        }
        if (showCreateFab && showCreateMenu) {
            MyModalBottomSheet(
                onDismissRequest = { showCreateMenu = false },
                modifier = Modifier.testTag(HOME_CREATE_MENU_TAG),
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = AppDimens.Space24),
                ) {
                    Text(
                        text = "创作",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(
                            start = AppDimens.Space24,
                            end = AppDimens.Space24,
                            top = AppDimens.Space8,
                            bottom = AppDimens.Space8,
                        ),
                    )
                    HomeCreateAction(
                        title = "提问题",
                        subtitle = "向知友提问，获取回答",
                        icon = Icons.AutoMirrored.Default.HelpOutline,
                        testTag = HOME_WRITE_QUESTION_BUTTON_TAG,
                    ) {
                        showCreateMenu = false
                        navigator.onNavigate(WriteQuestion)
                    }
                    HomeCreateAction(
                        title = "写回答",
                        subtitle = "找一个问题，分享你的见解",
                        icon = Icons.Default.Edit,
                        testTag = HOME_WRITE_ANSWER_BUTTON_TAG,
                    ) {
                        showCreateMenu = false
                        navigator.onNavigate(WriteAnswerChooser(defaultSelectedPage = 1))
                    }
                    HomeCreateAction(
                        title = "写文章",
                        subtitle = "发布一篇长文",
                        icon = Icons.Default.Description,
                        testTag = "home_write_article_button",
                    ) {
                        showCreateMenu = false
                        navigator.onNavigate(WriteArticle)
                    }
                    HomeCreateAction(
                        title = "发想法",
                        subtitle = "发布一条简短的想法",
                        icon = Icons.Default.MarkUnreadChatAlt,
                        testTag = HOME_WRITE_PIN_BUTTON_TAG,
                    ) {
                        showCreateMenu = false
                        navigator.onNavigate(WritePin())
                    }
                }
            }
        }
    }

    FeedAuthorBlockConfirmDialog(
        request = feedAuthorBlockRequest,
        displayItems = viewModel.displayItems,
        onDismiss = { feedAuthorBlockRequest = null },
        onConfirm = {
            viewModel.refresh(paginationEnvironment)
            feedAuthorBlockRequest = null
        },
    )

    feedToBlockByKeywords?.let { (title, excerpt) ->
        BlockByKeywordsDialog(
            showDialog = showBlockByKeywordsDialog,
            feedTitle = title,
            feedExcerpt = excerpt,
            onDismiss = {
                showBlockByKeywordsDialog = false
                feedToBlockByKeywords = null
            },
            onConfirm = {
                viewModel.refresh(paginationEnvironment)
                showBlockByKeywordsDialog = false
                feedToBlockByKeywords = null
            },
        )
    }
}

@Composable
private fun HomeTransactionRow(transaction: HomeTransaction) {
    val skin = LocalAppSkinTokens.current
    AppCard(
        containerColor = if (ThemeManager.isDarkTheme()) MaterialTheme.colorScheme.surfaceContainerHighest else skin.surfaceContainer,
        contentColor = skin.onSurface,
        modifier = Modifier
            .padding(horizontal = AppDimens.PageHorizontalPadding, vertical = AppDimens.Space4)
            .fillMaxWidth()
            .testTag(transaction.key),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(AppDimens.Space16),
            verticalArrangement = Arrangement.spacedBy(AppDimens.Space12),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(AppDimens.Space12),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    transaction.icon,
                    contentDescription = null,
                    tint = skin.primary,
                    modifier = Modifier.size(AppDimens.IconSizeMedium),
                )
                Text(
                    transaction.title,
                    style = skin.main,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
            }
            transaction.content?.let { text ->
                Text(
                    text,
                    style = skin.body2,
                    color = skin.onSurfaceVariantSummary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppDimens.Space8, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(AppDimens.Space4),
            ) {
                AppTextButton(
                    text = transaction.dismissText,
                    onClick = transaction.onDismiss,
                    contentColor = skin.onSurfaceVariantSummary,
                    modifier = Modifier.testTag("${transaction.key}_dismiss"),
                )
                val onAccept = transaction.onAccept
                transaction.acceptText?.takeIf { onAccept != null }?.let { text ->
                    AppButton(
                        text = text,
                        onClick = { onAccept?.invoke() },
                        modifier = Modifier.testTag("${transaction.key}_accept"),
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeCreateAction(
    title: String,
    subtitle: String,
    icon: ImageVector,
    testTag: String,
    onClick: () -> Unit,
) {
    SettingItem(
        title = { Text(title) },
        description = { Text(subtitle) },
        icon = { Icon(icon, contentDescription = null) },
        modifier = Modifier
            .testTag(testTag),
        onClick = onClick,
        endAction = {},
    )
}
