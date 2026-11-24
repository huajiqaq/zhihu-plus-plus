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

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.zly2006.zhihu.account.ZhihuRequestProfile
import com.github.zly2006.zhihu.account.rememberZhihuAccountStore
import com.github.zly2006.zhihu.data.MOBILE_NOTIFICATION_MESSAGE_URL
import com.github.zly2006.zhihu.data.MobileNotificationMessageOverview
import com.github.zly2006.zhihu.data.RecommendationMode
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.Search
import com.github.zly2006.zhihu.navigation.requestLoginNavigation
import com.github.zly2006.zhihu.notification.OnlineHomeNotification
import com.github.zly2006.zhihu.notification.OnlineHomeNotificationRepository
import com.github.zly2006.zhihu.notification.rememberNotificationSettingsStore
import com.github.zly2006.zhihu.platform.UserMessageDuration
import com.github.zly2006.zhihu.platform.rememberAppPrivateDirectory
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.reading.RegisterReadingQueueSource
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.theme.NEW_UI_NOTICE_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.components.AppDialog
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.BlockByKeywordsDialog
import com.github.zly2006.zhihu.ui.components.FeedAuthorBlockConfirmDialog
import com.github.zly2006.zhihu.ui.components.FeedAuthorBlockRequest
import com.github.zly2006.zhihu.ui.components.rememberPageTurnTarget
import com.github.zly2006.zhihu.ui.components.settingSkinIsMiuix
import com.github.zly2006.zhihu.ui.subscreens.APP_BLUR_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.subscreens.DEFAULT_FAB_OPACITY
import com.github.zly2006.zhihu.ui.subscreens.PREF_FAB_OPACITY
import com.github.zly2006.zhihu.ui.subscreens.SystemUpdateState
import com.github.zly2006.zhihu.ui.subscreens.rememberSystemUpdateState
import com.github.zly2006.zhihu.util.json
import com.github.zly2006.zhihu.util.raiseForStatus
import com.github.zly2006.zhihu.viewmodel.QUALITY_FILTER_MODE_PREFERENCE_KEY
import com.github.zly2006.zhihu.viewmodel.QualityFilterMode
import com.github.zly2006.zhihu.viewmodel.feed.BaseFeedViewModel
import com.github.zly2006.zhihu.viewmodel.feed.HomeFeedViewModel
import com.github.zly2006.zhihu.viewmodel.local.LocalHomeFeedViewModel
import com.github.zly2006.zhihu.viewmodel.rememberPaginationEnvironment
import com.github.zly2006.zhihu.viewmodel.za.AndroidHomeFeedViewModel
import com.github.zly2006.zhihu.viewmodel.za.MixedHomeFeedViewModel
import io.ktor.client.request.get
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlinx.serialization.json.Json

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

internal const val NEW_UI_NOTICE_TAG = "new_ui_notice"

fun homeAuthorPollAnnouncementTag(pinId: Long): String = "$HOME_AUTHOR_POLL_ANNOUNCEMENT_TAG:$pinId"

fun homeOnlineNotificationTag(uuid: String): String = "$HOME_ONLINE_NOTIFICATION_TAG:$uuid"

fun homePinAnnouncementReadKey(pinId: Long): String = "$HOME_PIN_ANNOUNCEMENT_READ_KEY_PREFIX$pinId"

/** 随主页导航条目保留，避免 Pager 重建后公告移位、登录页返回时再次自动提示。 */
internal class HomeScreenState : ViewModel() {
    val onlineNotifications = mutableStateOf(emptyMap<String, OnlineHomeNotification>())
    val authorPinAnnouncements = mutableStateOf(emptyList<HomePinAnnouncement>())
    val dismissedUpdateVersion = mutableStateOf<String?>(null)
    var transactionsExpanded by mutableStateOf(false)
    private var guestLoginPrompted = false

    fun takeGuestLoginPrompt(loggedIn: Boolean, loginForRecommendation: Boolean): Boolean {
        if (loggedIn || !loginForRecommendation || guestLoginPrompted) return false
        guestLoginPrompted = true
        return true
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

    val account = rememberAccountSettingsAccountState().value
    val currentRecommendationMode = if (!account.login) {
        RecommendationMode.WEB
    } else {
        RecommendationMode.entries.find {
            it.key == settings.getString("recommendationMode", RecommendationMode.MIXED.key)
        } ?: RecommendationMode.MIXED
    }
    val startupCacheFile = remember(appPrivateDirectory, currentRecommendationMode) {
        Path(appPrivateDirectory, homeFeedStartupCacheFileName(currentRecommendationMode))
    }

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

    var showNewUiNotice by remember {
        mutableStateOf(!settings.getBoolean(NEW_UI_NOTICE_PREFERENCE_KEY, false))
    }

    fun dismissNewUiNotice() {
        showNewUiNotice = false
        settings.putBoolean(NEW_UI_NOTICE_PREFERENCE_KEY, true)
    }

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

    val unreadOwner = accountStore.client
    var unreadCount by remember(unreadOwner, account.login) { mutableIntStateOf(0) }
    LaunchedEffect(lifecycleOwner, unreadOwner, account.login, isActive, showUnreadBadge) {
        if (!account.login || !isActive || !showUnreadBadge) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            try {
                val total = paginationEnvironment
                    .get("$MOBILE_NOTIFICATION_MESSAGE_URL?limit=20", ZhihuRequestProfile.Mobile)
                    .raiseForStatus()
                    .json<MobileNotificationMessageOverview>()
                    .totalUnreadCount
                if (accountStore.client === unreadOwner) unreadCount = total
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // 暂时获取失败时保留本账号上一次的总数，不能拿局部分类数代替。
            }
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
            if (homeState.takeGuestLoginPrompt(account.login, settings.getBoolean("loginForRecommendation", true))) {
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

    HomeAnnouncementLoadEffects(
        homeState = homeState,
        lifecycleOwner = lifecycleOwner,
        scrollToTopTrigger = scrollToTopTrigger,
        paginationEnvironment = paginationEnvironment,
        settings = settings,
        versionName = versionName,
        onlineNotificationRepository = onlineNotificationRepository,
    )

    LaunchedEffect(viewModel.errorMessage, account.login) {
        if (account.login) {
            viewModel.errorMessage?.let {
                userMessages.showMessage(it, UserMessageDuration.Long)
            }
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

    val transactions = buildHomeTransactions(
        homeState = homeState,
        showNewUiNotice = showNewUiNotice,
        dismissNewUiNotice = ::dismissNewUiNotice,
        updateAnnouncement = updateAnnouncement,
        onlineNotificationRepository = onlineNotificationRepository,
        settings = settings,
        navigator = navigator,
    )

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
    val utilityBottomAvoidance = homeScreenUtilityBottomAvoidance(
        density = density,
        homeBounds = homeBounds,
        createFabBounds = createFabBounds,
        createFabVisible = createFabVisible,
        innerPadding = innerPadding,
        readingPlayerOverlayPadding = readingPlayerOverlayPadding,
    )

    val feedBottomPadding = homeScreenFeedBottomPadding(
        density = density,
        feedListBounds = feedListBounds,
        utilityFabBounds = utilityFabBounds,
        utilityFabsVisible = utilityFabsVisible,
        innerPadding = innerPadding,
        readingPlayerOverlayPadding = readingPlayerOverlayPadding,
    )
    Box(
        modifier = Modifier.fillMaxSize().onGloballyPositioned {
            val bounds = it.boundsInRoot()
            if (homeBounds != bounds) homeBounds = bounds
        },
    ) {
        HomeScreenFeedContent(
            createMenuBlurRadius = createMenuBlurRadius,
            pageBackground = pageBackground,
            showTopActions = showTopActions,
            avatarUrl = account.avatarUrl,
            unreadCount = unreadCount,
            showUnreadBadge = showUnreadBadge,
            onSearchClick = { navigator.onNavigate(Search(query = "")) },
            onAccountClick = { showAccountBottomSheet = true },
            showAccountBottomSheet = showAccountBottomSheet,
            onAccountDismiss = { showAccountBottomSheet = false },
            viewModel = viewModel,
            paginationEnvironment = paginationEnvironment,
            listState = listState,
            pageTurnTarget = pageTurnTarget,
            onFeedListPositioned = {
                val bounds = it.boundsInRoot()
                if (feedListBounds != bounds) feedListBounds = bounds
            },
            feedBottomPadding = feedBottomPadding,
            transactions = transactions,
            transactionsExpanded = { homeState.transactionsExpanded },
            onTransactionsClick = { homeState.transactionsExpanded = !homeState.transactionsExpanded },
            readingQueueSourceId = readingQueueSourceId,
            userMessages = userMessages,
            onBlockByKeywords = { title, excerpt ->
                feedToBlockByKeywords = title to excerpt
                showBlockByKeywordsDialog = true
            },
            onAuthorBlockRequest = { feedAuthorBlockRequest = it },
            localHomeViewModel = localHomeViewModel,
            navigator = navigator,
        )

        val createFabOpacity by rememberObservedSetting(settings, PREF_FAB_OPACITY) {
            getInt(PREF_FAB_OPACITY, DEFAULT_FAB_OPACITY).coerceIn(10, 100) / 100f
        }
        HomeScreenFloatingActions(
            createFabVisible = createFabVisible,
            utilityFabsVisible = utilityFabsVisible,
            fabGraphicsLayer = { alpha = createFabOpacity },
            innerPadding = innerPadding,
            readingPlayerOverlayPadding = readingPlayerOverlayPadding,
            onCreateClick = { showCreateMenu = true },
            onCreateFabPositioned = {
                val bounds = it.boundsInRoot()
                if (createFabBounds != bounds) createFabBounds = bounds
            },
            onUtilityFabPositioned = {
                val bounds = it.boundsInRoot()
                if (utilityFabBounds != bounds) utilityFabBounds = bounds
            },
            utilityBottomAvoidance = utilityBottomAvoidance,
            isDebuggable = isDebuggable,
            onDebugClick = {
                val data = Json.encodeToString(viewModel.debugData)
                paginationEnvironment.setPlainTextClipboard("data", data)
                userMessages.showShortMessage("已复制调试数据")
            },
            onRefreshClick = {
                refreshingFromFab = true
                viewModel.refresh(paginationEnvironment)
            },
            viewModel = viewModel,
        )
        if (showCreateFab && showCreateMenu) {
            HomeCreateMenuSheet(
                navigator = navigator,
                onDismiss = { showCreateMenu = false },
            )
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
