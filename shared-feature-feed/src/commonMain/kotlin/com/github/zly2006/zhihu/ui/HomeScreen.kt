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
import androidx.compose.runtime.DisposableEffect
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
import com.github.zly2006.zhihu.i18n.FeedStrings
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.Search
import com.github.zly2006.zhihu.navigation.requestLoginNavigation
import com.github.zly2006.zhihu.notification.OnlineHomeNotification
import com.github.zly2006.zhihu.notification.OnlineHomeNotificationRepository
import com.github.zly2006.zhihu.notification.rememberNotificationSettingsStore
import com.github.zly2006.zhihu.platform.UserMessageDuration
import com.github.zly2006.zhihu.platform.appPrivateTextFile
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
import com.github.zly2006.zhihu.util.AppLog
import com.github.zly2006.zhihu.util.ZhihuFetchSignature
import com.github.zly2006.zhihu.util.json
import com.github.zly2006.zhihu.util.raiseForStatus
import com.github.zly2006.zhihu.viewmodel.feed.HomeFeedStateViewModel
import com.github.zly2006.zhihu.viewmodel.feed.HomeFeedViewModel
import com.github.zly2006.zhihu.viewmodel.filter.KeywordScope
import com.github.zly2006.zhihu.viewmodel.filter.KeywordType
import com.github.zly2006.zhihu.viewmodel.filter.getContentFilterDatabase
import com.github.zly2006.zhihu.viewmodel.filter.readHomeFeedFilterPolicy
import com.github.zly2006.zhihu.viewmodel.local.LocalHomeFeedViewModel
import com.github.zly2006.zhihu.viewmodel.rememberPaginationEnvironment
import io.ktor.client.request.get
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val account = rememberAccountSettingsAccountState().value
    val browserSession = accountStore.usesBrowserSession

    /** 关注豁免属于当前账号；所有平台的内存卡片和启动缓存均按身份分区。 */
    val feedOwner = remember(account.login, account.id) {
        when {
            !account.login -> "guest"
            else -> ZhihuFetchSignature.md5Hex(account.id.encodeToByteArray())
        }
    }
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

    val missingNativeCookie = account.login && !browserSession && !account.hasRequiredCookie
    val currentRecommendationMode = if (!account.login || browserSession) {
        RecommendationMode.WEB
    } else {
        RecommendationMode.entries.find {
            it.key == settings.getString("recommendationMode", RecommendationMode.MIXED.key)
        } ?: RecommendationMode.MIXED
    }
    val startupCacheFile = remember(currentRecommendationMode, feedOwner) {
        appPrivateTextFile(homeFeedStartupCacheFileName(currentRecommendationMode, feedOwner))
    }

    if (missingNativeCookie) {
        AppDialog(
            onDismissRequest = {},
            title = FeedStrings.cookieIncomplete,
            summary = FeedStrings.cookieSignInAgain,
            confirmText = FeedStrings.signInAgain,
            onConfirm = ::requestLoginNavigation,
        )
    }
    val updateState by rememberSystemUpdateState().collectAsState()
    val updateAnnouncement = updateState as? SystemUpdateState.UpdateAvailable
    val versionName = rememberAppVersionInfo().substringBefore(' ').takeIf { it.firstOrNull()?.isDigit() == true }
    val onlineNotificationRepository = remember(settings) { OnlineHomeNotificationRepository(settings) }
    val isDebuggable = rememberHomeIsDebuggable()
    val viewModel: HomeFeedStateViewModel = when (currentRecommendationMode) {
        RecommendationMode.LOCAL -> viewModel(key = "home-local:$feedOwner") { LocalHomeFeedViewModel(accountStore) }
        else -> viewModel(key = "home-${currentRecommendationMode.key}:$feedOwner") { HomeFeedViewModel(currentRecommendationMode) }
    }
    val remoteHistory = viewModel.remoteHistory
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
        if (!account.login || browserSession || !isActive || !showUnreadBadge) return@LaunchedEffect
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

    val completedPageCount = viewModel.completedPageCount
    var continuedPage by remember(viewModel) { mutableStateOf<Pair<Int, Int>?>(null) }
    LaunchedEffect(viewModel, completedPageCount, viewModel.isLoading, viewModel.displayItems.isEmpty(), viewModel.hasNextPage, viewModel.errorText, isActive) {
        val page = viewModel.pageGeneration to completedPageCount
        if (isActive &&
            shouldContinueEmptyHomeFeed(
                currentRecommendationMode,
                completedPageCount,
                viewModel.displayItems.isEmpty(),
                viewModel.hasNextPage,
                viewModel.isLoading,
                viewModel.errorText != null,
            ) &&
            continuedPage != page
        ) {
            continuedPage = page
            viewModel.loadMore(paginationEnvironment)
        }
    }

    val filterDatabase = remember { getContentFilterDatabase() }
    DisposableEffect(settings, viewModel) {
        val subscription = settings.observeKeyChanges { key ->
            if (key in HOME_FEED_FILTER_SETTING_KEYS) {
                viewModel.invalidateFilterPolicy()
            }
        }
        onDispose(subscription::close)
    }
    LaunchedEffect(lifecycleOwner, filterDatabase, viewModel, isActive) {
        if (!isActive) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            /** 分栏设置可能不暂停主页；只订阅规则表，不订阅曝光或屏蔽日志。 */
            val changes = KeywordType.entries.flatMap { type ->
                KeywordScope.entries.map { scope ->
                    filterDatabase.blockedKeywordDao().observeKeywordsByType(type.name, scope.name).map { Unit }
                }
            } + listOf(
                filterDatabase.blockedUserDao().observeAllUsers().map { Unit },
                filterDatabase.blockedQuestionAuthorDao().observeAllUsers().map { Unit },
                filterDatabase.blockedTopicDao().observeAllTopics().map { Unit },
            )
            changes.forEach { changesForTable ->
                launch { changesForTable.collect { viewModel.invalidateFilterPolicy() } }
            }
        }
    }

    LaunchedEffect(lifecycleOwner, viewModel, startupCacheFile, account.login, account.id, autoRefreshOnStartup, isActive) {
        if (!isActive) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (account.login &&
                !browserSession &&
                !missingNativeCookie &&
                settings.getBoolean("enableContentFilter", true) &&
                !settings.getBoolean("reverseBlock", false)
            ) {
                remoteHistory.start(homeState.viewModelScope, paginationEnvironment)
            }
            if (homeState.takeGuestLoginPrompt(account.login, settings.getBoolean("loginForRecommendation", true))) {
                requestLoginNavigation()
            } else if (!viewModel.startupAttempted && viewModel.displayItems.isEmpty() && !viewModel.isLoading && viewModel.completedPageCount == 0) {
                viewModel.startupAttempted = true
                try {
                    run {
                        if (autoRefreshOnStartup) {
                            viewModel.refresh(paginationEnvironment)
                            return@run
                        }
                        val generation = viewModel.pageGeneration
                        val client = paginationEnvironment.httpClient()
                        val serialized = withContext(Dispatchers.Default) {
                            runCatching { startupCacheFile.read() }
                        }.getOrElse {
                            if (browserSession) userMessages.showShortMessage(FeedStrings.cacheReadFailed)
                            null
                        }
                        if (generation != viewModel.pageGeneration || paginationEnvironment.httpClient() !== client || viewModel.isLoading) return@run
                        if (serialized.isNullOrBlank()) {
                            viewModel.refresh(paginationEnvironment)
                            return@run
                        }
                        val policy = readHomeFeedFilterPolicy(settings, filterDatabase)
                        val cachedItems = decodeHomeFeedStartupSnapshot(serialized, policy.fingerprint)
                        if (generation != viewModel.pageGeneration || paginationEnvironment.httpClient() !== client || viewModel.isLoading) return@run
                        if (cachedItems.isEmpty()) {
                            viewModel.refresh(paginationEnvironment)
                            return@run
                        }
                        remoteHistory.awaitRecentPage()
                        val unreadCachedItems = remoteHistory.filterRemoteReads(cachedItems)
                        val currentPolicy = readHomeFeedFilterPolicy(settings, filterDatabase)
                        if (generation != viewModel.pageGeneration || paginationEnvironment.httpClient() !== client || viewModel.isLoading) return@run
                        if (unreadCachedItems.isEmpty() || currentPolicy.fingerprint != policy.fingerprint) {
                            viewModel.refresh(paginationEnvironment)
                        } else {
                            viewModel.restoreStartupItems(unreadCachedItems, policy.fingerprint)
                        }
                    }
                } catch (e: CancellationException) {
                    viewModel.startupAttempted = false
                    throw e
                } catch (e: Exception) {
                    AppLog.e("HomeScreen", "Failed to validate home feed snapshot", e)
                    if (!viewModel.isLoading && viewModel.displayItems.isEmpty()) viewModel.refresh(paginationEnvironment)
                }
            }

        }
    }

    LaunchedEffect(viewModel, viewModel.filterPolicyRevision, isActive) {
        if (!isActive) return@LaunchedEffect
        try {
            viewModel.recheckFilterPolicy(paginationEnvironment)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            AppLog.e("HomeScreen", "Failed to recheck home feed rules", error)
        }
    }
    LaunchedEffect(viewModel, startupCacheFile, isActive) {
        if (!isActive) return@LaunchedEffect
        snapshotFlow { viewModel.snapshotRevision }.collect { revision ->
            if (revision == 0) return@collect
            val (items, fingerprint) = viewModel.startupSnapshot() ?: return@collect
            encodeHomeFeedStartupSnapshot(items, fingerprint)?.let { serialized ->
                withContext(Dispatchers.Default) {
                    runCatching { startupCacheFile.write(serialized) }
                }.onFailure {
                    if (browserSession) userMessages.showShortMessage(FeedStrings.cacheSaveFailed)
                }
            }
        }
    }

    val historyRevision by remoteHistory.importedRevision.collectAsState()
    LaunchedEffect(remoteHistory, viewModel, historyRevision, isActive) {
        if (!isActive) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.isNotEmpty() }.first { it }
        snapshotFlow { viewModel.displayItems.toList() }.collectLatest {
            try {
                viewModel.removeRemoteReads(paginationEnvironment) {
                    listState.layoutInfo.visibleItemsInfo.map { it.key }.toSet()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLog.e("HomeScreen", "Failed to reconcile remote feed history", error)
            }
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

    LaunchedEffect(viewModel.errorText, account.login) {
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
            !missingNativeCookie,
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
                userMessages.showShortMessage(FeedStrings.debugDataCopied)
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
        viewModel = viewModel,
        onDismiss = { feedAuthorBlockRequest = null },
        onConfirm = {
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
                viewModel.invalidateFilterPolicy()
                showBlockByKeywordsDialog = false
                feedToBlockByKeywords = null
            },
        )
    }
}
