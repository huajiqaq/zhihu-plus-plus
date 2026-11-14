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
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.rememberLifecycleOwner
import com.github.zly2006.zhihu.account.AccountVerificationDialogs
import com.github.zly2006.zhihu.account.HumanVerificationPhase
import com.github.zly2006.zhihu.account.LoginScreen
import com.github.zly2006.zhihu.account.SessionExpiryPrompt
import com.github.zly2006.zhihu.account.rememberZhihuAccountStore
import com.github.zly2006.zhihu.filter.ContentOpenFrom
import com.github.zly2006.zhihu.navigation.Account
import com.github.zly2006.zhihu.navigation.AppNavEntry
import com.github.zly2006.zhihu.navigation.AppNavHost
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.CollectionContent
import com.github.zly2006.zhihu.navigation.Collections
import com.github.zly2006.zhihu.navigation.Daily
import com.github.zly2006.zhihu.navigation.EmptyDetail
import com.github.zly2006.zhihu.navigation.Follow
import com.github.zly2006.zhihu.navigation.History
import com.github.zly2006.zhihu.navigation.Home
import com.github.zly2006.zhihu.navigation.HotList
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.Login
import com.github.zly2006.zhihu.navigation.MainTabs
import com.github.zly2006.zhihu.navigation.MyCollections
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.Navigator
import com.github.zly2006.zhihu.navigation.Notification
import com.github.zly2006.zhihu.navigation.OnlineHistory
import com.github.zly2006.zhihu.navigation.OnlineHistoryRoute
import com.github.zly2006.zhihu.navigation.Person
import com.github.zly2006.zhihu.navigation.Pin
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.navigation.Search
import com.github.zly2006.zhihu.navigation.SentenceSimilarityTest
import com.github.zly2006.zhihu.navigation.TopLevelDestination
import com.github.zly2006.zhihu.navigation.Topic
import com.github.zly2006.zhihu.navigation.Video
import com.github.zly2006.zhihu.navigation.WriteAnswer
import com.github.zly2006.zhihu.navigation.WriteAnswerChooser
import com.github.zly2006.zhihu.navigation.WriteArticle
import com.github.zly2006.zhihu.navigation.WritePin
import com.github.zly2006.zhihu.navigation.WriteQuestion
import com.github.zly2006.zhihu.navigation.loginNavigationRequestFlow
import com.github.zly2006.zhihu.navigation.rememberAppNavHost
import com.github.zly2006.zhihu.platform.PlatformBackHandler
import com.github.zly2006.zhihu.platform.platformName
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.reading.rememberReadingPlayerController
import com.github.zly2006.zhihu.reading.saveReadingPlaybackSpeed
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.theme.ThemeManager
import com.github.zly2006.zhihu.ui.components.AppBottomBar
import com.github.zly2006.zhihu.ui.components.AppDialog
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppNavigationRail
import com.github.zly2006.zhihu.ui.components.AppOverlayBlurController
import com.github.zly2006.zhihu.ui.components.AppOverlayHost
import com.github.zly2006.zhihu.ui.components.AppScaffold
import com.github.zly2006.zhihu.ui.components.BottomBarItem
import com.github.zly2006.zhihu.ui.components.CompactReadingPlayerButton
import com.github.zly2006.zhihu.ui.components.LocalAppGlassEnabled
import com.github.zly2006.zhihu.ui.components.LocalAppOverlayBlurController
import com.github.zly2006.zhihu.ui.components.LocalNavHostLifecycleOwner
import com.github.zly2006.zhihu.ui.components.NoOpPagerNestedScrollConnection
import com.github.zly2006.zhihu.ui.components.ReadingPlayerBar
import com.github.zly2006.zhihu.ui.components.ReadingQueueSheet
import com.github.zly2006.zhihu.ui.components.appNavSwipeDismiss
import com.github.zly2006.zhihu.ui.components.appNavTransition
import com.github.zly2006.zhihu.ui.components.settingSkinIsMiuix
import com.github.zly2006.zhihu.ui.subscreens.APP_BLUR_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.subscreens.AppearanceSettingsScreen
import com.github.zly2006.zhihu.ui.subscreens.BlockedFeedHistoryScreen
import com.github.zly2006.zhihu.ui.subscreens.ColorSchemeScreen
import com.github.zly2006.zhihu.ui.subscreens.ContentFilterSettingsScreen
import com.github.zly2006.zhihu.ui.subscreens.DeveloperSettingsScreen
import com.github.zly2006.zhihu.ui.subscreens.FEED_SETTINGS_KEYS
import com.github.zly2006.zhihu.ui.subscreens.FLOATING_BOTTOM_BAR_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.subscreens.FeedSettingsScreen
import com.github.zly2006.zhihu.ui.subscreens.IdentityManagementScreen
import com.github.zly2006.zhihu.ui.subscreens.NAVIGATION_SETTINGS_KEYS
import com.github.zly2006.zhihu.ui.subscreens.NavigationSettingsScreen
import com.github.zly2006.zhihu.ui.subscreens.OpenSourceLicensesScreen
import com.github.zly2006.zhihu.ui.subscreens.PAGE_TRANSITION_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.subscreens.PAGE_TURN_SETTINGS_KEYS
import com.github.zly2006.zhihu.ui.subscreens.PageTurnSettingsScreen
import com.github.zly2006.zhihu.ui.subscreens.READING_LAYOUT_SETTINGS_KEYS
import com.github.zly2006.zhihu.ui.subscreens.ReadingPlaybackSettingsScreen
import com.github.zly2006.zhihu.ui.subscreens.ReadingSettingsScreen
import com.github.zly2006.zhihu.ui.subscreens.SettingsSearchScreen
import com.github.zly2006.zhihu.ui.subscreens.SystemAndUpdateSettingsScreen
import com.github.zly2006.zhihu.ui.subscreens.WebDavSyncScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.nav.core.NavCornerClipMode
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import top.yukonga.miuix.kmp.nav.transition.NavTransition
import top.yukonga.miuix.kmp.nav.transition.NavTransitionScope
import com.github.zly2006.zhihu.navigation.AppNavDisplay as NavDisplay
import com.github.zly2006.zhihu.navigation.AppNavEntryBuilder as NavEntryBuilder

private sealed class MainTabPage(
    val bottomDestination: TopLevelDestination,
    val key: String,
) {
    data object HomePage : MainTabPage(Home, "home")

    data object FollowPage : MainTabPage(Follow, "follow")

    data object HotListPage : MainTabPage(HotList, "hotlist")

    data object DailyPage : MainTabPage(Daily, "daily")

    data object OnlineHistoryPage : MainTabPage(OnlineHistory, "online_history")

    data object MyCollectionsPage : MainTabPage(MyCollections, "my_collections")

    data object AccountPage : MainTabPage(Account, "account")
}

/** 主栈与详情栏按访问 ID 持有状态；换肤和偏好重读不重建返回栈。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ZhihuMain(
    modifier: Modifier = Modifier,
    navHost: AppNavHost,
    mainTabNavigationTarget: TopLevelDestination?,
    navigate: (NavDestination) -> Unit,
    navigateContent: (NavDestination, AppNavHost) -> Unit = { destination, _ -> navigate(destination) },
    enableLandscapeListDetail: Boolean = false,
    isListDetailCapable: Boolean = true,
    setCurrentMainTabOpenFrom: (String?) -> Unit,
    consumeMainTabNavigationTarget: (TopLevelDestination) -> Unit,
    preferenceState: ZhihuMainPreferenceState,
    isDarkTheme: Boolean,
    articleContent: @Composable (Article) -> Unit,
    showMainNavigationBar: Boolean = true,
    showHomeTopActions: Boolean = true,
    onCurrentMainTabDestinationChange: (TopLevelDestination) -> Unit = {},
    sentenceSimilarityContent: @Composable () -> Unit = {
        error("$platformName 暂不支持句子相似度测试")
    },
    blocklistSettingsNlpContent: @Composable (onNavigateBack: () -> Unit) -> Unit = {
        error("$platformName 暂不支持 NLP 智能屏蔽设置")
    },
) = BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val containerWidth = maxWidth
    val layoutDirection = LocalLayoutDirection.current
    val isLargeLandscape = canShowLandscapeListDetail(
        enabled = enableLandscapeListDetail,
        deviceSupportsListDetail = isListDetailCapable,
        preferenceEnabled = preferenceState.landscapeListDetailEnabled,
        width = containerWidth,
        height = maxHeight,
    )
    val bottomPadding = ScaffoldDefaults.contentWindowInsets.asPaddingValues().calculateBottomPadding()
    val tapToScrollToTopEnabled = preferenceState.tapToScrollToTopEnabled
    val autoHideBottomBar = preferenceState.autoHideBottomBar
    val collectionDirectBrowseEnabled = preferenceState.collectionDirectBrowseEnabled
    val selectedBottomBarItemKeys = preferenceState.selectedBottomBarItemKeys
    val startDestination = preferenceState.startDestination
    val reloadBottomBarPreferences = preferenceState::reload
    val readingPlayer = rememberReadingPlayerController()
    val readingPlayerState by readingPlayer.state
    val settings = rememberSettingsStore()
    val floatingBottomBar by rememberObservedSetting(settings, FLOATING_BOTTOM_BAR_PREFERENCE_KEY) {
        getBoolean(FLOATING_BOTTOM_BAR_PREFERENCE_KEY, true)
    }
    val appBlurEnabled by rememberObservedSetting(settings, APP_BLUR_PREFERENCE_KEY) {
        getBoolean(APP_BLUR_PREFERENCE_KEY, true)
    }
    val predictiveBackEnabled by rememberObservedSetting(settings, "enable_predictive_back") {
        getBoolean("enable_predictive_back", true)
    }
    val pageTransition by rememberObservedSetting(settings, PAGE_TRANSITION_PREFERENCE_KEY) {
        getString(PAGE_TRANSITION_PREFERENCE_KEY, "miuix")
    }
    val swipeBack = if (predictiveBackEnabled) {
        appNavSwipeDismiss(layoutDirection == LayoutDirection.Rtl)
    } else {
        NavSwipeDirection.None
    }
    val navTransition = appNavTransition(pageTransition, enabled = predictiveBackEnabled)
    val navEffects = navDisplayEffects(pageTransition == "aosp" && predictiveBackEnabled).let { effects ->
        if (predictiveBackEnabled) effects else effects.copy(enableCornerClip = false, dimAmount = 0f)
    }
    val overlayBlurController = remember { AppOverlayBlurController() }
    val overlayBlurRadius by animateDpAsState(
        targetValue = if (appBlurEnabled && overlayBlurController.openedOverlayCount > 0) AppDimens.OverlayBlurRadius else 0.dp,
        animationSpec = tween(180),
        label = "overlayBlurRadius",
    )
    val accountStore = rememberZhihuAccountStore()
    val accountClient = accountStore.client
    val expiryPrompt by accountClient.expiryPrompt.collectAsState()
    val verification by accountClient.verificationState.collectAsState()
    val http403Feedback by accountStore.http403Log.pendingFeedback.collectAsState()

    val paneStateHolder = rememberSaveableStateHolder()
    val detailHost = rememberAppNavHost(EmptyDetail)
    val detailTop = detailHost.top
    val selectedContentDestination = detailTop.readingDestinationOrNull()
    val hasOpenSecondaryDetail = detailTop != null && detailTop != EmptyDetail
    var showReadingQueue by remember { mutableStateOf(false) }
    var isReadingPlayerExpandedByUser by remember { mutableStateOf(false) }
    var readingPlayerHeightPx by remember { mutableIntStateOf(0) }
    val readingPlayerOverlayOffsetState = remember { ReadingPlayerOverlayOffsetState() }
    val density = LocalDensity.current
    val currentOnMainTabDestinationChange by rememberUpdatedState(onCurrentMainTabDestinationChange)

    val currentDestination = navHost.top
    val currentEntryId = navHost.topEntryId
    val showMainNavigation = currentDestination == MainTabs
    val useSideRail = containerWidth >= 600.dp && showMainNavigationBar && showMainNavigation && !floatingBottomBar
    val isListPaneContext = currentDestination.isListPaneDestination()

    /** 窄屏覆盖详情时保留原条目，返回原来源后恢复。 */
    var detailOwnerEntryId by rememberSaveable { mutableStateOf<String?>(null) }
    val readingEntryIndex = navHost.pageEntries.indexOfFirst { it.destination.isDetailPaneDestination() }
    var renderedMainListEntry by remember { mutableStateOf<AppNavEntry?>(null) }
    var renderedMainReadingEntryId by remember { mutableStateOf<String?>(null) }
    val liveMainListEntry = if (readingEntryIndex > 0) {
        navHost.pageEntries.elementAt(readingEntryIndex - 1).takeIf { it.destination.isListPaneDestination() }
    } else {
        null
    }

    /** 出栈先于退场完成，来源栏位须保留到阅读条目退场。 */
    val mainListEntry = liveMainListEntry ?: renderedMainListEntry?.takeIf { navHost.containsEntry(it.id) }
    val mainReadingStartIndex = mainListEntry?.let { navHost.pageEntries.indexOfFirst { entry -> entry.id == it.id } + 1 } ?: -1

    /** 主栈阅读保持原 entry；它打开的问题使用第二栈，宽屏放左、窄屏暂时覆盖。 */
    val hasMainReadingListContext = hasOpenSecondaryDetail &&
        detailTop is Question &&
        currentEntryId == detailOwnerEntryId &&
        currentDestination?.isDetailPaneDestination() == true
    val showMainReadingListContext = isLargeLandscape && hasMainReadingListContext
    val showMainStackListDetail = isLargeLandscape && mainListEntry != null && !hasMainReadingListContext
    val showListDetail = isLargeLandscape && isListPaneContext && !showMainStackListDetail
    val showDetailPane = hasOpenSecondaryDetail &&
        (showListDetail || currentEntryId == detailOwnerEntryId) &&
        !showMainStackListDetail &&
        !hasMainReadingListContext
    val hasOpenDetail = showDetailPane && selectedContentDestination != null
    val mainPaneLifecycleOwner = rememberLifecycleOwner(
        maxLifecycle = if (hasMainReadingListContext && !isLargeLandscape) Lifecycle.State.CREATED else Lifecycle.State.RESUMED,
    )
    LaunchedEffect(currentEntryId, detailOwnerEntryId) {
        val owner = detailOwnerEntryId
        if (owner != null && !navHost.containsEntry(owner)) {
            detailHost.popToRoot()
            detailOwnerEntryId = null
        }
    }
    /** 竖屏使用主栈，避免预测返回预览到详情栏占位页。 */
    val useSecondaryContentNavigation = enableLandscapeListDetail && showListDetail && !showMainStackListDetail

    fun openListDetail(destination: NavDestination) {
        detailOwnerEntryId = navHost.topEntryId
        navigateContent(destination, detailHost)
    }

    fun openReadingDestination(destination: NavDestination, onlyIfReading: Boolean = false) {
        val current = if (hasOpenDetail) selectedContentDestination else currentDestination.readingDestinationOrNull()
        if (onlyIfReading && current == null) return
        if (destination is Question && current?.isDetailPaneDestination() == true) {
            if (hasOpenDetail) {
                if (currentDestination != destination) navigate(destination)
            } else {
                detailOwnerEntryId = navHost.topEntryId
                navigateContent(destination, detailHost)
            }
            return
        }
        if (current == destination) {
            if (hasMainReadingListContext && !isLargeLandscape) detailHost.popToRoot()
            return
        }
        if (hasMainReadingListContext && destination.isDetailPaneDestination()) {
            navigate(destination)
            detailOwnerEntryId = navHost.topEntryId
            return
        }
        val opensInDetail = useSecondaryContentNavigation && destination.isDetailPaneDestination()
        if (hasOpenDetail) {
            if (opensInDetail) {
                detailHost.back()
            } else {
                detailHost.popToRoot()
            }
        } else if (current != null && !opensInDetail) {
            navHost.back()
        }
        if (opensInDetail) {
            detailOwnerEntryId = navHost.topEntryId
            navigateContent(destination, detailHost)
        } else {
            navigate(destination)
        }
    }
    val isOnReadingDetail = hasOpenDetail ||
        currentDestination is Article ||
        currentDestination is Question ||
        currentDestination is Pin
    val isReadingPlayerExpanded = readingPlayerState.hasSession &&
        (isOnReadingDetail || isReadingPlayerExpandedByUser)
    val shouldCompactPlayerOnBackgroundInteraction by rememberUpdatedState(
        isReadingPlayerExpandedByUser && !isOnReadingDetail,
    )
    val readingPlayerOverlayPadding = when {
        !readingPlayerState.hasSession -> 0.dp
        !isReadingPlayerExpanded -> 0.dp
        readingPlayerHeightPx > 0 -> with(density) { readingPlayerHeightPx.toDp() } + 16.dp
        else -> 16.dp
    }

    LaunchedEffect(readingPlayerState.hasSession) {
        if (!readingPlayerState.hasSession) {
            showReadingQueue = false
            isReadingPlayerExpandedByUser = false
            readingPlayerOverlayOffsetState.resetOffset()
        }
    }
    var previousReadingItemKey by remember { mutableStateOf(readingPlayerState.currentItem?.key) }
    val currentOpenReadingDestination by rememberUpdatedState<(NavDestination) -> Unit> { destination ->
        openReadingDestination(destination, onlyIfReading = true)
    }
    LaunchedEffect(readingPlayerState.currentItem?.key) {
        val currentItem = readingPlayerState.currentItem
        val currentItemKey = currentItem?.key
        if (currentItem != null && previousReadingItemKey != null && previousReadingItemKey != currentItemKey) {
            /** 第一帧回调先于重组和约束测量；跨完该帧后再用最新导航回调，避免改宽与切队列同时发生时按旧窄屏清栈。 */
            withFrameNanos { }
            withFrameNanos { }
        }
        val itemChanged = previousReadingItemKey != null && previousReadingItemKey != currentItemKey
        previousReadingItemKey = currentItemKey
        if (itemChanged && currentItem != null) {
            currentOpenReadingDestination(currentItem.toDestination(readingPlayerState.sourceId))
        }
    }

    val isOnArticle = (hasOpenDetail && detailTop is Article) || currentDestination is Article
    LaunchedEffect(currentDestination) {
        isReadingPlayerExpandedByUser = false
        if (!isOnArticle) readingPlayerOverlayOffsetState.clearRoute()
    }
    var wasOnArticle by remember { mutableStateOf(false) }
    if (!isOnArticle && wasOnArticle) {
        LeaveImmersiveModeCleanup()
    }
    SideEffect {
        wasOnArticle = isOnArticle
    }

    var scrollToTopTrigger by remember { mutableIntStateOf(0) }
    var isBottomBarVisible by remember { mutableStateOf(true) }
    var navigationBarHeightPx by remember { mutableIntStateOf(0) }
    val bottomBarScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                when {
                    available.y < -3f -> isBottomBarVisible = false
                    available.y > 3f -> isBottomBarVisible = true
                }
                return Offset.Zero
            }
        }
    }

    val allBottomBarItems = listOf(
        Triple(Home, "主页", Icons.Filled.Home),
        Triple(Follow, "关注", Icons.Filled.Group),
        Triple(HotList, "热榜", Icons.Filled.Whatshot),
        Triple(Daily, "日报", Icons.Filled.Newspaper),
        Triple(OnlineHistory, "历史", Icons.Filled.History),
        Triple(MyCollections, "收藏夹", Icons.Filled.Bookmarks),
        Triple(Account, "账号", Icons.Filled.ManageAccounts),
    )
    val bottomBarItems = selectedBottomBarItemKeys.mapNotNull { key ->
        allBottomBarItems.firstOrNull { it.first.name == key }
    }

    val mainTabPages = remember(bottomBarItems) {
        bottomBarItems.flatMap { item ->
            when (item.first) {
                Home -> listOf(MainTabPage.HomePage)
                Follow -> listOf(MainTabPage.FollowPage)
                HotList -> listOf(MainTabPage.HotListPage)
                Daily -> listOf(MainTabPage.DailyPage)
                OnlineHistory -> listOf(MainTabPage.OnlineHistoryPage)
                MyCollections -> listOf(MainTabPage.MyCollectionsPage)
                Account -> listOf(MainTabPage.AccountPage)
                else -> emptyList()
            }
        }
    }

    fun pageIndexForDestination(destination: TopLevelDestination): Int = mainTabPages
        .indexOfFirst {
            it.bottomDestination::class == destination::class
        }.takeIf { it >= 0 } ?: mainTabPages
        .indexOfFirst {
            it.bottomDestination::class == startDestination::class
        }.takeIf { it >= 0 } ?: 0

    val mainPagerState = rememberPagerState(
        initialPage = pageIndexForDestination(startDestination),
        pageCount = { mainTabPages.size },
    )
    val coroutineScope = rememberCoroutineScope()

    var currentMainTabDestination by remember { mutableStateOf(startDestination) }
    val isAccountSurface = currentMainTabDestination == Account

    fun navigateTopLevel(destination: TopLevelDestination) {
        if (destination == Account && hasOpenDetail) {
            detailHost.popToRoot()
        }
        val targetPage = pageIndexForDestination(destination)
        coroutineScope.launch {
            mainPagerState.animateScrollToPage(targetPage)
        }
    }

    LaunchedEffect(navHost) {
        loginNavigationRequestFlow.collect {
            navHost.open(Login)
        }
    }

    LaunchedEffect(mainPagerState.currentPage, mainTabPages) {
        mainTabPages.getOrNull(mainPagerState.currentPage)?.bottomDestination?.let { destination ->
            currentMainTabDestination = destination
            setCurrentMainTabOpenFrom(destination.openFrom)
            currentOnMainTabDestinationChange(destination)
        }
    }

    LaunchedEffect(currentMainTabDestination, currentDestination) {
        if (isAccountSurface && hasOpenDetail) {
            /** 阅读详情属于原信息流，不随主 tab 带入账号栏。 */
            detailHost.popToRoot()
        }
    }

    PlatformBackHandler(
        showMainNavigation &&
            mainPagerState.currentPage != 0 &&
            !showDetailPane,
    ) {
        coroutineScope.launch {
            mainPagerState.animateScrollToPage(0)
        }
    }

    LaunchedEffect(mainTabNavigationTarget, mainTabPages) {
        mainTabNavigationTarget?.let { destination ->
            mainPagerState.scrollToPage(pageIndexForDestination(destination))
            consumeMainTabNavigationTarget(destination)
        }
    }

    LaunchedEffect(mainTabPages) {
        if (mainTabPages.isNotEmpty()) {
            val currentDestinationStillVisible = mainTabPages.any {
                it.bottomDestination::class == currentMainTabDestination::class
            }
            val targetDestination = if (currentDestinationStillVisible) {
                currentMainTabDestination
            } else {
                startDestination
            }
            val targetPage = pageIndexForDestination(targetDestination)
            if (mainPagerState.currentPage != targetPage || mainPagerState.currentPage !in mainTabPages.indices) {
                mainPagerState.scrollToPage(targetPage)
            }
        }
    }

    var listPaneRatio by remember {
        mutableFloatStateOf(
            settings.getFloat(LANDSCAPE_LIST_PANE_RATIO_KEY, DEFAULT_LIST_PANE_RATIO),
        )
    }

    @Composable
    fun DetailPane(modifier: Modifier) {
        CompositionLocalProvider(
            LocalArticleNavHost provides detailHost,
            LocalNavigator provides Navigator(
                onNavigate = { destination ->
                    when {
                        hasMainReadingListContext && destination.isDetailPaneDestination() -> {
                            if (destination == currentDestination) {
                                if (!isLargeLandscape) detailHost.popToRoot()
                            } else {
                                navigate(destination)
                                detailOwnerEntryId = navHost.topEntryId
                            }
                        }
                        destination is Question && hasMainReadingListContext -> navigateContent(destination, detailHost)
                        destination is Video && hasMainReadingListContext -> navigateContent(destination, navHost)
                        destination is Video || destination.isDetailPaneDestination() || destination.isAccountDetailDestination() -> {
                            navigateContent(destination, detailHost)
                        }
                        else -> navigate(destination)
                    }
                },
                onNavigateBack = detailHost::back,
                onNavigateTopLevel = ::navigateTopLevel,
            ),
            LocalSelectedContentDestination provides selectedContentDestination,
            LocalReadingPlayerOverlayPadding provides readingPlayerOverlayPadding,
            LocalReadingPlayerOverlayOffsetState provides readingPlayerOverlayOffsetState,
        ) {
            NavDisplay(
                navHost = detailHost,
                modifier = modifier.clipToBounds().testTag("detail_pane"),
                onBack = detailHost::onBack,
                transition = navTransition,
                effects = navEffects,
            ) {
                entry<EmptyDetail>(swipeDismiss = NavSwipeDirection.None) { EmptyDetailPane() }
                entry<Article>(swipeDismiss = swipeBack) { article -> articleContent(article) }
                entry<Pin>(swipeDismiss = swipeBack) { pin -> PinScreen(pin) }
                entry<Question>(swipeDismiss = swipeBack) { question -> QuestionScreen(question) }
                accountSettings(reloadBottomBarPreferences, blocklistSettingsNlpContent, swipeBack)
            }
        }
    }

    CompositionLocalProvider(
        LocalAppOverlayBlurController provides overlayBlurController,
        LocalAppGlassEnabled provides appBlurEnabled,
        LocalNavHostLifecycleOwner provides LocalLifecycleOwner.current,
    ) {
        AppOverlayHost {
            Box(Modifier.fillMaxSize().blur(overlayBlurRadius)) {
                val listPaneWidth = if (isLargeLandscape) {
                    normalizedListPaneWidth(containerWidth, listPaneRatio)
                } else {
                    containerWidth
                }
                val listPaneModifier = Modifier.fillMaxSize()
                if (showListDetail || !showDetailPane) {
                    paneStateHolder.SaveableStateProvider("list") {
                        AppScaffold(
                            modifier = (if (hasMainReadingListContext && !isLargeLandscape) Modifier.size(0.dp) else listPaneModifier)
                                .nestedScroll(bottomBarScrollConnection),
                            bottomBar = {
                                if (showMainNavigationBar && !useSideRail && currentDestination != null) {
                                    LaunchedEffect(currentDestination) { isBottomBarVisible = true }
                                    val currentBottomDestination = mainTabPages
                                        .getOrNull(mainPagerState.targetPage)
                                        ?.bottomDestination
                                    AnimatedVisibility(
                                        modifier = Modifier.width(listPaneWidth).onSizeChanged { navigationBarHeightPx = it.height },
                                        visible = showMainNavigation && (!autoHideBottomBar || isBottomBarVisible),
                                        enter = slideInVertically(tween(200)) { it },
                                        exit = slideOutVertically(tween(200)) { it },
                                    ) {
                                        AppBottomBar(
                                            bottomPadding = bottomPadding,
                                            floating = floatingBottomBar,
                                            modifier = Modifier.width(listPaneWidth),
                                            items = bottomBarItems.map { (destination, label, icon) ->
                                                BottomBarItem(
                                                    label = label,
                                                    icon = icon,
                                                    testTag = "nav_tab_${destination.name.lowercase()}",
                                                    selected = currentBottomDestination?.let { it::class == destination::class } == true,
                                                    onClick = {
                                                        isReadingPlayerExpandedByUser = false
                                                        if (currentBottomDestination?.let { it::class == destination::class } != true) {
                                                            navigateTopLevel(destination)
                                                        } else if (tapToScrollToTopEnabled) {
                                                            scrollToTopTrigger++
                                                        }
                                                    },
                                                )
                                            },
                                        )
                                    }
                                }
                            },
                        ) { innerPadding ->
                            CompositionLocalProvider(
                                LocalArticleNavHost provides navHost,
                                LocalLifecycleOwner provides mainPaneLifecycleOwner,
                                LocalNavigator provides Navigator(
                                    onNavigate = { destination ->
                                        /** 返回和打开可能在同一回调内执行，路由判定须读取当前栈。 */
                                        val currentDestination = navHost.top
                                        val currentEntryId = navHost.topEntryId
                                        val readingEntryIndex = navHost.pageEntries.indexOfFirst { it.destination.isDetailPaneDestination() }
                                        val liveMainListEntry = if (readingEntryIndex > 0) {
                                            navHost.pageEntries.elementAt(readingEntryIndex - 1).takeIf { it.destination.isListPaneDestination() }
                                        } else {
                                            null
                                        }
                                        val mainListEntry = liveMainListEntry ?: renderedMainListEntry?.takeIf { navHost.containsEntry(it.id) }
                                        val hasMainReadingListContext = detailHost.top is Question &&
                                            currentEntryId == detailOwnerEntryId &&
                                            currentDestination?.isDetailPaneDestination() == true
                                        val showMainStackListDetail = isLargeLandscape && mainListEntry != null && !hasMainReadingListContext
                                        val showListDetail = isLargeLandscape && currentDestination.isListPaneDestination() && !showMainStackListDetail
                                        val useSecondaryContentNavigation = enableLandscapeListDetail && showListDetail && !showMainStackListDetail
                                        if (destination is Question && currentDestination?.isDetailPaneDestination() == true) {
                                            detailOwnerEntryId = navHost.topEntryId
                                            navigateContent(destination, detailHost)
                                        } else if (
                                            useSecondaryContentNavigation &&
                                            (
                                                destination.isDetailPaneDestination() ||
                                                    (showListDetail && currentMainTabDestination == Account && destination.isAccountDetailDestination())
                                            )
                                        ) {
                                            openListDetail(destination)
                                        } else {
                                            navigate(destination)
                                        }
                                    },
                                    onNavigateBack = navHost::back,
                                    onNavigateTopLevel = ::navigateTopLevel,
                                ),
                                LocalReadingPlayerOverlayPadding provides readingPlayerOverlayPadding,
                                LocalReadingPlayerOverlayOffsetState provides readingPlayerOverlayOffsetState,
                                LocalSelectedContentDestination provides selectedContentDestination,
                            ) {
                                NavDisplay(
                                    navHost = navHost,
                                    modifier = (if (showListDetail) Modifier.width(listPaneWidth).fillMaxHeight() else Modifier.fillMaxSize()).pointerInput(Unit) {
                                        while (true) {
                                            awaitPointerEventScope {
                                                awaitFirstDown(
                                                    requireUnconsumed = false,
                                                    pass = PointerEventPass.Initial,
                                                )
                                                while (
                                                    awaitPointerEvent(PointerEventPass.Final)
                                                        .changes
                                                        .any { it.pressed }
                                                ) {
                                                    /** 等手势完成后再重组，避免取消同一次背景点击或滚动。 */
                                                }
                                            }
                                            if (shouldCompactPlayerOnBackgroundInteraction) {
                                                delay(100)
                                                isReadingPlayerExpandedByUser = false
                                            }
                                        }
                                    },
                                    onBack = navHost::onBack,
                                    transition = navTransition,
                                    effects = if (isLargeLandscape) navEffects.copy(enableCornerClip = false, dimAmount = 0f) else navEffects,
                                    retainedEntryId = if (showMainStackListDetail) mainListEntry?.id else null,
                                    entryTransition = if (isLargeLandscape) {
                                        { entry, base ->
                                            val entryIndex = navHost.pageEntries.indexOfFirst { it.id == entry.id }
                                            val isLeft = showMainStackListDetail && entry.id == mainListEntry?.id
                                            val isRight = (showMainStackListDetail || showMainReadingListContext) &&
                                                (entryIndex < 0 || entryIndex >= mainReadingStartIndex)
                                            val paneWidth = when {
                                                isRight -> containerWidth - listPaneWidth - LIST_DETAIL_DIVIDER_WIDTH
                                                entry.destination.isListPaneDestination() -> listPaneWidth
                                                else -> containerWidth
                                            }
                                            val radius = navEffects.cornerClipRadius
                                            val allCorners = navEffects.cornerClipMode == NavCornerClipMode.All
                                            val roundLeft = allCorners || layoutDirection == LayoutDirection.Ltr
                                            val roundRight = allCorners || !roundLeft
                                            val paneShape = AbsoluteRoundedCornerShape(
                                                topLeft = if (roundLeft) radius else 0.dp,
                                                bottomLeft = if (roundLeft) radius else 0.dp,
                                                topRight = if (roundRight) radius else 0.dp,
                                                bottomRight = if (roundRight) radius else 0.dp,
                                            )
                                            object : NavTransition by base {
                                                override fun Modifier.transformEntry(scope: NavTransitionScope): Modifier {
                                                    val width = with(scope.density) { paneWidth.roundToPx() }.coerceAtLeast(0)
                                                    val paneScope = object : NavTransitionScope by scope {
                                                        override val layoutSize: IntSize get() = IntSize(width, scope.layoutSize.height)
                                                    }
                                                    val coveredScope = object : NavTransitionScope by paneScope {
                                                        override val relativeDepth: Float get() = scope.relativeDepth + 1f
                                                    }
                                                    val placement = layout { measurable, constraints ->
                                                        val placeable = measurable.measure(Constraints.fixed(width, constraints.maxHeight))
                                                        layout(constraints.maxWidth, constraints.maxHeight) {
                                                            val x = if (isRight) with(scope.density) { (listPaneWidth + LIST_DETAIL_DIVIDER_WIDTH).roundToPx() } else 0
                                                            placeable.placeRelative(x, 0)
                                                        }
                                                    }.clipToBounds()
                                                    if (isLeft) return placement
                                                    /** 共用 NavDisplay 的全屏效果会影响另一栏；在条目自身的栏宽内绘制。 */
                                                    val pane = placement.drawWithContent {
                                                        if (scope.relativeDepth <= 0f) {
                                                            drawRect(Color.Black, alpha = navEffects.dimAmount * base.scrimFraction(coveredScope).coerceIn(0f, 1f))
                                                        }
                                                        drawContent()
                                                    }
                                                    return with(base) { pane.transformEntry(paneScope) }.graphicsLayer {
                                                        shape = paneShape
                                                        clip = navEffects.shouldClipCornersAt(scope.relativeDepth)
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        null
                                    },
                                    entryWrapper = { entry, content ->
                                        val destination = entry.destination
                                        val sourceEntry = remember(entry.id) {
                                            val firstReading = navHost.pageEntries.indexOfFirst { it.destination.isDetailPaneDestination() }
                                            if (navHost.pageEntries.elementAtOrNull(firstReading)?.id == entry.id) {
                                                navHost.pageEntries.elementAtOrNull(firstReading - 1)?.takeIf { it.destination.isListPaneDestination() }
                                            } else {
                                                null
                                            }
                                        }
                                        DisposableEffect(entry.id, sourceEntry?.id) {
                                            if (sourceEntry != null) {
                                                renderedMainListEntry = sourceEntry
                                                renderedMainReadingEntryId = entry.id
                                            }
                                            onDispose {
                                                if (renderedMainReadingEntryId == entry.id) {
                                                    renderedMainListEntry = null
                                                    renderedMainReadingEntryId = null
                                                }
                                            }
                                        }
                                        /** 退场页按自身目的地测量，不能随新栈顶撑满窗口。 */
                                        val entryPaneModifier = if (!showMainStackListDetail && isLargeLandscape && destination.isListPaneDestination()) {
                                            Modifier.width(listPaneWidth).fillMaxHeight()
                                        } else {
                                            Modifier.fillMaxSize()
                                        }
                                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                                            Row(
                                                entryPaneModifier.clipToBounds().then(
                                                    if (hasMainReadingListContext) Modifier else Modifier.testTag("list_pane"),
                                                ),
                                            ) {
                                                if (containerWidth >= 600.dp && showMainNavigationBar && destination == MainTabs && !floatingBottomBar) {
                                                    val currentBottomDestination = mainTabPages.getOrNull(mainPagerState.targetPage)?.bottomDestination
                                                    AppNavigationRail(
                                                        modifier = Modifier.fillMaxHeight().testTag("nav_rail"),
                                                        items = bottomBarItems.map { (destination, label, icon) ->
                                                            BottomBarItem(
                                                                label = label,
                                                                icon = icon,
                                                                testTag = "nav_tab_${destination.name.lowercase()}",
                                                                selected = currentBottomDestination?.let { it::class == destination::class } == true,
                                                                onClick = {
                                                                    isReadingPlayerExpandedByUser = false
                                                                    if (currentBottomDestination?.let { it::class == destination::class } != true) {
                                                                        navigateTopLevel(destination)
                                                                    } else if (tapToScrollToTopEnabled) {
                                                                        scrollToTopTrigger++
                                                                    }
                                                                },
                                                            )
                                                        },
                                                    )
                                                }
                                                Box(Modifier.weight(1f).fillMaxSize()) {
                                                    val parentNavigator = LocalNavigator.current
                                                    CompositionLocalProvider(
                                                        LocalNavigator provides if (showMainStackListDetail && entry.id == mainListEntry?.id) {
                                                            Navigator(
                                                                onNavigate = { navigate(it) },
                                                                onNavigateBack = {
                                                                    while (navHost.topEntryId != entry.id && navHost.back()) { }
                                                                    navHost.back()
                                                                },
                                                                onNavigateTopLevel = ::navigateTopLevel,
                                                            )
                                                        } else {
                                                            parentNavigator
                                                        },
                                                    ) { content() }
                                                }
                                            }
                                        }
                                    },
                                ) {
                                    entry<MainTabs>(swipeDismiss = NavSwipeDirection.None) {
                                        MainTabsPager(
                                            pagerState = mainPagerState,
                                            pages = mainTabPages,
                                            scrollToTopTrigger = scrollToTopTrigger,
                                            innerPadding = innerPadding,
                                            collectionDirectBrowseEnabled = collectionDirectBrowseEnabled,
                                            showHomeTopActions = showHomeTopActions,
                                        )
                                    }
                                    entry<Login>(swipeDismiss = swipeBack) {
                                        LoginScreen(
                                            onLoginComplete = { navHost.back() },
                                            onOpenTelemetrySettings = {
                                                navHost.open(Account.SystemAndUpdateSettings("allowTelemetry"))
                                            },
                                            onBack = navHost::onBack,
                                        )
                                    }
                                    entry<Question>(swipeDismiss = swipeBack) { question ->
                                        QuestionScreen(question)
                                    }
                                    entry<Topic>(swipeDismiss = swipeBack) { topic ->
                                        TopicScreen(topic)
                                    }
                                    entry<WriteAnswerChooser>(swipeDismiss = swipeBack) { args ->
                                        WriteAnswerChooserScreen(args)
                                    }
                                    entry<WriteAnswer>(swipeDismiss = swipeBack) { args ->
                                        WriteAnswerScreen(args)
                                    }
                                    entry<WritePin>(swipeDismiss = swipeBack) { args ->
                                        WritePinScreen(args)
                                    }
                                    entry<WriteQuestion>(swipeDismiss = swipeBack) {
                                        WriteQuestionScreen(WriteQuestion)
                                    }
                                    entry<WriteArticle>(swipeDismiss = swipeBack) {
                                        WriteArticleScreen(WriteArticle)
                                    }
                                    entry<Article>(swipeDismiss = swipeBack) { article -> articleContent(article) }
                                    entry<History>(swipeDismiss = swipeBack) {
                                        LegacyLocalHistoryScreen()
                                    }
                                    entry<OnlineHistoryRoute>(swipeDismiss = swipeBack) {
                                        OnlineHistoryScreen(
                                            onBack = LocalNavigator.current.onNavigateBack,
                                            isActive = currentDestination == OnlineHistoryRoute,
                                        )
                                    }
                                    entry<Search>(swipeDismiss = swipeBack) { search -> SearchScreen(search) }
                                    entry<Collections>(swipeDismiss = swipeBack) { data ->
                                        CollectionScreen(
                                            urlToken = data.userToken,
                                            contentPadding = innerPadding,
                                        )
                                    }
                                    entry<CollectionContent>(swipeDismiss = swipeBack) { content ->
                                        CollectionContentScreen(content.collectionId)
                                    }
                                    entry<Person>(swipeDismiss = swipeBack) { person ->
                                        PeopleScreen(person)
                                    }
                                    entry<Pin>(swipeDismiss = swipeBack) { pin ->
                                        PinScreen(pin)
                                    }
                                    accountSettings(reloadBottomBarPreferences, blocklistSettingsNlpContent, swipeBack)
                                    entry<Notification>(swipeDismiss = swipeBack) {
                                        NotificationScreen()
                                    }
                                    entry<Notification.Entry>(swipeDismiss = swipeBack) { entry ->
                                        NotificationTimelineScreen(entry.entryName, entry.title)
                                    }
                                    entry<Notification.Invitations>(swipeDismiss = swipeBack) {
                                        NotificationTimelineScreen("invite", "邀请回答")
                                    }
                                    entry<Notification.Message>(swipeDismiss = swipeBack) { args ->
                                        PrivateMessageScreen(args)
                                    }
                                    entry<Notification.NotificationSettings>(swipeDismiss = swipeBack) { args ->
                                        NotificationSettingsScreen(
                                            setting = args.setting,
                                        )
                                    }
                                    entry<SentenceSimilarityTest>(swipeDismiss = swipeBack) {
                                        sentenceSimilarityContent()
                                    }
                                }
                            }
                        }
                    }
                }

                if (showListDetail || showDetailPane || showMainStackListDetail || hasMainReadingListContext || detailTop == null) {
                    paneStateHolder.SaveableStateProvider("detail") {
                        /** 隐藏时保存导航子树，独立窗口随所属页面暂停；改宽只调整原宿主布局。 */
                        Box(if (showListDetail || showDetailPane || showMainStackListDetail || hasMainReadingListContext) Modifier.fillMaxSize() else Modifier.size(0.dp)) {
                            Row(Modifier.fillMaxSize()) {
                                if (showListDetail || showMainStackListDetail || showMainReadingListContext) {
                                    Spacer(Modifier.width(listPaneWidth))
                                    ListDetailDivider(
                                        onDrag = { deltaPx ->
                                            val usableWidthPx = with(density) {
                                                (containerWidth - LIST_DETAIL_DIVIDER_WIDTH).toPx()
                                            }
                                            if (usableWidthPx > 0f) {
                                                listPaneRatio = (listPaneRatio + deltaPx / usableWidthPx).coerceIn(0f, 1f)
                                            }
                                        },
                                        onAdjustBy = { ratioDelta ->
                                            listPaneRatio = (listPaneRatio + ratioDelta).coerceIn(0f, 1f)
                                        },
                                        onDragStopped = {
                                            val normalizedWidth = normalizedListPaneWidth(containerWidth, listPaneRatio)
                                            listPaneRatio = normalizedWidth.value /
                                                (containerWidth - LIST_DETAIL_DIVIDER_WIDTH).value
                                            settings.putFloat(LANDSCAPE_LIST_PANE_RATIO_KEY, listPaneRatio)
                                        },
                                    )
                                }
                            }
                            if (!showMainStackListDetail) {
                                DetailPane(
                                    when {
                                        showMainReadingListContext ->
                                            Modifier
                                                .align(Alignment.CenterStart)
                                                .width(listPaneWidth)
                                                .fillMaxHeight()
                                                .testTag("list_pane")
                                        hasMainReadingListContext -> Modifier.fillMaxSize().testTag("list_pane")
                                        showListDetail ->
                                            Modifier
                                                .align(Alignment.CenterEnd)
                                                .width(containerWidth - listPaneWidth - LIST_DETAIL_DIVIDER_WIDTH)
                                                .fillMaxHeight()
                                        else -> Modifier.fillMaxSize()
                                    },
                                )
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(
                            start = if (showListDetail && showDetailPane) listPaneWidth + LIST_DETAIL_DIVIDER_WIDTH else 0.dp,
                            bottom = 16.dp + if (
                                !showDetailPane && showMainNavigation && showMainNavigationBar && !useSideRail && (!autoHideBottomBar || isBottomBarVisible)
                            ) {
                                with(density) { navigationBarHeightPx.toDp() }
                            } else {
                                bottomPadding
                            },
                        ).fillMaxWidth(),
                    visible = isReadingPlayerExpanded,
                    enter = fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.92f),
                    exit = fadeOut(tween(160)) + scaleOut(tween(160), targetScale = 0.92f),
                ) {
                    ReadingPlayerBar(
                        state = readingPlayerState,
                        onPrevious = readingPlayer::playPrevious,
                        onTogglePlayPause = readingPlayer::togglePlayPause,
                        onNext = readingPlayer::playNext,
                        onStop = readingPlayer::stop,
                        onOpenQueue = { showReadingQueue = true },
                        onPlaybackSpeedChange = { speed ->
                            saveReadingPlaybackSpeed(settings, speed)
                            readingPlayer.setPlaybackSpeed(speed)
                        },
                        onBackgroundInteraction = {
                            if (!isOnReadingDetail) isReadingPlayerExpandedByUser = false
                        },
                        modifier = Modifier
                            .onSizeChanged { readingPlayerHeightPx = it.height }
                            .graphicsLayer {
                                translationY = readingPlayerOverlayOffsetState.verticalOffsetPx
                            },
                    )
                }

                AnimatedVisibility(
                    visible = readingPlayerState.hasSession && !isReadingPlayerExpanded,
                    enter = fadeIn(tween(220)),
                    exit = fadeOut(tween(160)),
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        CompactReadingPlayerButton(
                            state = readingPlayerState,
                            onExpand = { isReadingPlayerExpandedByUser = true },
                        )
                    }
                }
            }

            if (showReadingQueue && readingPlayerState.hasSession) {
                ReadingQueueSheet(
                    state = readingPlayerState,
                    onDismissRequest = {
                        showReadingQueue = false
                        if (!isOnReadingDetail) isReadingPlayerExpandedByUser = false
                    },
                    onItemClick = { index, item ->
                        previousReadingItemKey = item.key
                        if (index != readingPlayerState.currentIndex) {
                            readingPlayer.playAt(index)
                        }
                        showReadingQueue = false
                        openReadingDestination(item.toDestination(readingPlayerState.sourceId))
                    },
                    onOpenSettings = {
                        showReadingQueue = false
                        isReadingPlayerExpandedByUser = false
                        if (currentDestination !is Account.ReadingPlaybackSettings) {
                            navigate(Account.ReadingPlaybackSettings())
                        }
                    },
                )
            }
        }
    }
    AppOverlayHost {
        AccountVerificationDialogs(client = accountClient)
        if (currentDestination != Login &&
            expiryPrompt == SessionExpiryPrompt.SHOWING &&
            verification.phase == HumanVerificationPhase.ACTIVE &&
            http403Feedback == null
        ) {
            AppDialog(
                title = "登录已过期",
                summary = "请重新登录以继续使用完整功能。",
                modifier = Modifier.testTag("session_expiry_dialog"),
                confirmText = "重新登录",
                dismissText = "取消",
                onDismissRequest = accountClient::dismissSessionExpiry,
                onConfirm = {
                    accountClient.dismissSessionExpiry()
                    accountStore.clear()
                    navigate(Login)
                },
            )
        }
    }
}

@Composable
private fun navDisplayEffects(aospStyle: Boolean): NavDisplayEffects =
    if (aospStyle || settingSkinIsMiuix()) {
        val cornerRadius = rememberNavSystemCornerRadius()
        NavDisplayEffects(
            cornerClipRadius = if (aospStyle && cornerRadius <= 0.dp) 32.dp else cornerRadius,
            cornerClipMode = if (aospStyle) NavCornerClipMode.All else NavCornerClipMode.Leading,
            dimAmount = if (aospStyle) {
                if (ThemeManager.isDarkTheme()) 0.8f else 0.2f
            } else {
                0.35f
            },
            backdropColor = LocalAppSkinTokens.current.background,
        )
    } else {
        NavDisplayEffects(
            enableCornerClip = false,
            dimAmount = 0f,
            backdropColor = MaterialTheme.colorScheme.background,
        )
    }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MainTabsPager(
    pagerState: PagerState,
    pages: List<MainTabPage>,
    scrollToTopTrigger: Int,
    innerPadding: PaddingValues,
    collectionDirectBrowseEnabled: Boolean,
    showHomeTopActions: Boolean,
) {
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        pageNestedScrollConnection = NoOpPagerNestedScrollConnection,
    ) { pageIndex ->
        val page = pages.getOrNull(pageIndex) ?: return@HorizontalPager
        when (page) {
            MainTabPage.HomePage -> HomeScreen(
                scrollToTopTrigger = scrollToTopTrigger,
                innerPadding = innerPadding,
                showTopActions = showHomeTopActions,
                isActive = pagerState.currentPage == pageIndex,
            )
            MainTabPage.FollowPage -> FollowScreen(
                scrollToTopTrigger = scrollToTopTrigger,
                innerPadding = innerPadding,
                parentPagerState = pagerState,
                isActive = pagerState.currentPage == pageIndex,
            )
            MainTabPage.HotListPage -> HotListScreen(
                innerPadding = innerPadding,
                scrollToTopTrigger = scrollToTopTrigger,
                isActive = pagerState.currentPage == pageIndex,
            )
            MainTabPage.DailyPage -> DailyScreen(
                scrollToTopTrigger = scrollToTopTrigger,
                isActive = pagerState.currentPage == pageIndex,
            )
            MainTabPage.OnlineHistoryPage -> OnlineHistoryScreen(
                scrollToTopTrigger = scrollToTopTrigger,
                isActive = pagerState.currentPage == pageIndex,
            )
            MainTabPage.MyCollectionsPage -> MyCollectionsTopLevelPage(
                scrollToTopTrigger = scrollToTopTrigger,
                innerPadding = innerPadding,
                collectionDirectBrowseEnabled = collectionDirectBrowseEnabled,
                isActive = pagerState.currentPage == pageIndex,
            )
            MainTabPage.AccountPage -> AccountSettingScreen(
                innerPadding = innerPadding,
                isActive = pagerState.currentPage == pageIndex,
            )
        }
    }
}

@Composable
private fun MyCollectionsTopLevelPage(
    scrollToTopTrigger: Int,
    innerPadding: PaddingValues,
    collectionDirectBrowseEnabled: Boolean,
    isActive: Boolean,
) {
    val account = rememberAccountSettingsAccountState().value
    if (collectionDirectBrowseEnabled) {
        CollectionBrowseScreen(
            urlToken = account.urlToken,
            contentPadding = innerPadding,
            showBackButton = false,
            scrollToTopTrigger = scrollToTopTrigger,
            isActive = isActive,
        )
    } else {
        CollectionScreen(
            urlToken = account.urlToken,
            contentPadding = innerPadding,
            showBackButton = false,
            isActive = isActive,
        )
    }
}

private val TopLevelDestination.openFrom: String?
    get() = when (this) {
        Home -> ContentOpenFrom.HOME_FEED
        OnlineHistory -> ContentOpenFrom.HISTORY
        else -> null
    }

private fun NavDestination?.readingDestinationOrNull(): NavDestination? = when (this) {
    is Article, is Pin, is Question -> this
    else -> null
}

private fun NavDestination.isAccountDetailDestination(): Boolean = when (this) {
    is Account.AppearanceSettings,
    is Account.ReadingSettings,
    is Account.NavigationSettings,
    is Account.PageTurnSettings,
    is Account.ReadingPlaybackSettings,
    is Account.FeedSettings,
    Account.WebDavSync,
    is Account.RecommendSettings,
    Account.RecommendSettings.Blocklist,
    Account.RecommendSettings.BlockedFeedHistory,
    Account.IdentityManagement,
    is Account.SystemAndUpdateSettings,
    Account.SettingsSearch,
    Account.OpenSourceLicenses,
    Account.DeveloperSettings,
    Account.DeveloperSettings.ColorScheme,
    -> true
    else -> false
}

private fun NavDestination?.isListPaneDestination(): Boolean = when (this) {
    MainTabs, is Search, History, OnlineHistoryRoute, is Collections, is CollectionContent, is Question, is Person, is Topic,
    Notification, is Notification.Entry, Notification.Invitations, Account.RecommendSettings.BlockedFeedHistory,
    -> true
    else -> false
}

private fun NavEntryBuilder.accountSettings(
    reloadPreferences: () -> Unit,
    nlpContent: @Composable (onNavigateBack: () -> Unit) -> Unit,
    swipeBack: NavSwipeDirection,
) {
    entry<Account.RecommendSettings.Blocklist>(swipeDismiss = swipeBack) {
        BlocklistSettingsScreen(nlpContent)
    }
    entry<Account.RecommendSettings.BlockedFeedHistory>(swipeDismiss = swipeBack) {
        BlockedFeedHistoryScreen()
    }
    entry<Account.AppearanceSettings>(swipeDismiss = swipeBack) { args ->
        when (args.setting) {
            in NAVIGATION_SETTINGS_KEYS -> NavigationSettingsScreen(args.setting, reloadPreferences)
            in PAGE_TURN_SETTINGS_KEYS -> PageTurnSettingsScreen(args.setting)
            in READING_LAYOUT_SETTINGS_KEYS -> ReadingSettingsScreen(args.setting)
            in FEED_SETTINGS_KEYS -> FeedSettingsScreen(args.setting)
            else -> AppearanceSettingsScreen(args.setting, reloadPreferences)
        }
    }
    entry<Account.NavigationSettings>(swipeDismiss = swipeBack) { args ->
        NavigationSettingsScreen(args.setting, reloadPreferences)
    }
    entry<Account.RecommendSettings>(swipeDismiss = swipeBack) { args ->
        if (args.setting in FEED_SETTINGS_KEYS) FeedSettingsScreen(args.setting) else ContentFilterSettingsScreen(args.setting)
    }
    entry<Account.FeedSettings>(swipeDismiss = swipeBack) { args ->
        FeedSettingsScreen(args.setting)
    }
    entry<Account.IdentityManagement>(swipeDismiss = swipeBack) {
        IdentityManagementScreen()
    }
    entry<Account.SystemAndUpdateSettings>(swipeDismiss = swipeBack) { args ->
        SystemAndUpdateSettingsScreen(
            setting = args.setting,
        )
    }
    entry<Account.ReadingSettings>(swipeDismiss = swipeBack) { args ->
        if (args.setting in PAGE_TURN_SETTINGS_KEYS) PageTurnSettingsScreen(args.setting) else ReadingSettingsScreen(args.setting)
    }
    entry<Account.PageTurnSettings>(swipeDismiss = swipeBack) { args ->
        PageTurnSettingsScreen(args.setting)
    }
    entry<Account.ReadingPlaybackSettings>(swipeDismiss = swipeBack) { args ->
        ReadingPlaybackSettingsScreen(args.setting)
    }
    entry<Account.WebDavSync>(swipeDismiss = swipeBack) {
        WebDavSyncScreen()
    }
    entry<Account.SettingsSearch>(swipeDismiss = swipeBack) {
        SettingsSearchScreen()
    }
    entry<Account.OpenSourceLicenses>(swipeDismiss = swipeBack) {
        OpenSourceLicensesScreen()
    }
    entry<Account.DeveloperSettings>(swipeDismiss = swipeBack) {
        DeveloperSettingsScreen()
    }
    entry<Account.DeveloperSettings.ColorScheme>(swipeDismiss = swipeBack) {
        ColorSchemeScreen()
    }
}
