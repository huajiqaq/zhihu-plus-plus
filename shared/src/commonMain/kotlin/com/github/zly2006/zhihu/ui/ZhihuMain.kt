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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.rememberLifecycleOwner
import com.github.zly2006.zhihu.navigation.Account
import com.github.zly2006.zhihu.navigation.AppNavHost
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.EmptyDetail
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.Login
import com.github.zly2006.zhihu.navigation.MainTabs
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.Navigator
import com.github.zly2006.zhihu.navigation.TopLevelDestination
import com.github.zly2006.zhihu.navigation.isListPaneDestination
import com.github.zly2006.zhihu.navigation.rememberAppNavHost
import com.github.zly2006.zhihu.navigation.resolveNavigationUrl
import com.github.zly2006.zhihu.platform.platformName
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.reading.rememberReadingPlayerController
import com.github.zly2006.zhihu.ui.components.AppBottomSheet
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppOverlayBlurController
import com.github.zly2006.zhihu.ui.components.AppOverlayHost
import com.github.zly2006.zhihu.ui.components.AppScaffold
import com.github.zly2006.zhihu.ui.components.LocalAppGlassEnabled
import com.github.zly2006.zhihu.ui.components.LocalAppOverlayBlurController
import com.github.zly2006.zhihu.ui.components.LocalNavHostLifecycleOwner
import com.github.zly2006.zhihu.ui.components.appNavSwipeDismiss
import com.github.zly2006.zhihu.ui.components.appNavTransition
import com.github.zly2006.zhihu.ui.subscreens.APP_BLUR_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.subscreens.FLOATING_BOTTOM_BAR_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.subscreens.PAGE_TRANSITION_PREFERENCE_KEY
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import com.github.zly2006.zhihu.navigation.AppNavDisplay as NavDisplay

/** 主栈与详情栏按访问 ID 持有状态；换肤和偏好重读不重建返回栈。 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
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
    blocklistSettingsNlpContent: @Composable (onNavigateBack: () -> Unit) -> Unit = {
        error("$platformName 暂不支持 NLP 智能屏蔽设置")
    },
    accountSheetVisible: Boolean = false,
    onDismissAccountSheet: () -> Unit = {},
) = BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val containerWidth = maxWidth
    val layoutDirection = LocalLayoutDirection.current
    val bottomPadding = ScaffoldDefaults.contentWindowInsets.asPaddingValues().calculateBottomPadding()
    val autoHideBottomBar = preferenceState.autoHideBottomBar
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

    val paneStateHolder = rememberSaveableStateHolder()
    val detailHost = rememberAppNavHost(EmptyDetail)
    val paneState = rememberZhihuMainPaneState(settings)
    val readingUi = rememberZhihuMainReadingPlayerUiState(readingPlayerState)
    val density = LocalDensity.current

    val panes = zhihuMainPaneLayout(
        navHost = navHost,
        detailHost = detailHost,
        paneState = paneState,
        containerWidth = containerWidth,
        containerHeight = maxHeight,
        showMainNavigationBar = showMainNavigationBar,
        floatingBottomBar = floatingBottomBar,
        enableLandscapeListDetail = enableLandscapeListDetail,
        isListDetailCapable = isListDetailCapable,
        landscapeListDetailEnabled = preferenceState.landscapeListDetailEnabled,
    )
    val currentDestination = panes.currentDestination
    val mainPaneLifecycleOwner = rememberLifecycleOwner(
        maxLifecycle = if (panes.hasMainReadingListContext && !panes.isLargeLandscape) Lifecycle.State.CREATED else Lifecycle.State.RESUMED,
    )
    LaunchedEffect(panes.currentEntryId, paneState.detailOwnerEntryId) {
        val owner = paneState.detailOwnerEntryId
        if (owner != null && !navHost.containsEntry(owner)) {
            detailHost.popToRoot()
            paneState.detailOwnerEntryId = null
        }
    }
    val router = remember(navHost, detailHost, paneState, panes, navigate, navigateContent) {
        ZhihuMainRouter(navHost, detailHost, paneState, panes, navigate, navigateContent)
    }

    val isReadingPlayerExpanded = readingPlayerState.hasSession &&
        (panes.isOnReadingDetail || readingUi.isExpandedByUser)
    val shouldCompactPlayerOnBackgroundInteraction by rememberUpdatedState(
        readingUi.isExpandedByUser && !panes.isOnReadingDetail,
    )
    val readingPlayerOverlayPadding = when {
        !readingPlayerState.hasSession -> 0.dp
        !isReadingPlayerExpanded -> 0.dp
        readingUi.heightPx > 0 -> with(density) { readingUi.heightPx.toDp() } + 16.dp
        else -> 16.dp
    }
    ZhihuMainReadingPlayerEffects(
        ui = readingUi,
        readingPlayer = readingPlayer,
        currentDestination = currentDestination,
        isOnArticle = panes.isOnArticle,
        onOpenReadingDestination = { router.openReadingDestination(it, onlyIfReading = true) },
    )

    val tabs = rememberZhihuMainTabs(
        navHost = navHost,
        detailHost = detailHost,
        layout = panes,
        preferenceState = preferenceState,
        mainTabNavigationTarget = mainTabNavigationTarget,
        animatePageScroll = predictiveBackEnabled,
        navigate = navigate,
        consumeMainTabNavigationTarget = consumeMainTabNavigationTarget,
        setCurrentMainTabOpenFrom = setCurrentMainTabOpenFrom,
        onCurrentMainTabDestinationChange = onCurrentMainTabDestinationChange,
        onTabClicked = { readingUi.isExpandedByUser = false },
    )

    val mainPaneNavigator = Navigator(
        onNavigate = router::navigateFromMainPane,
        onNavigateBack = navHost::back,
        onNavigateTopLevel = tabs.navigateTopLevel,
    )

    val platformUriHandler = LocalUriHandler.current
    val messages = rememberUserMessageSink()
    CompositionLocalProvider(
        LocalNavigator provides mainPaneNavigator,
        // 由读取链接的页面决定导航栏位，包括组件库自动处理的 URL 注解。
        LocalUriHandler providesComputed {
            val onNavigate = LocalNavigator.currentValue.onNavigate
            object : UriHandler {
                override fun openUri(uri: String) {
                    val destination = resolveNavigationUrl(uri)
                    when {
                        destination != null -> onNavigate(destination)
                        uri.substringBefore(':').lowercase() in setOf("mailto", "tel", "sms") -> {
                            runCatching { platformUriHandler.openUri(uri) }
                                .onFailure { messages.showShortMessage("没有可打开此链接的应用") }
                        }
                        else -> messages.showShortMessage("不支持的链接")
                    }
                }
            }
        },
        LocalAppOverlayBlurController provides overlayBlurController,
        LocalAppGlassEnabled provides appBlurEnabled,
        LocalNavHostLifecycleOwner provides LocalLifecycleOwner.current,
    ) {
        AppOverlayHost {
            Box(Modifier.fillMaxSize().blur(overlayBlurRadius)) {
                val listPaneWidth = if (panes.isLargeLandscape) {
                    normalizedListPaneWidth(containerWidth, paneState.listPaneRatio, panes.minimumListWidth, panes.minimumDetailWidth)
                } else {
                    containerWidth
                }

                /** 两栏共用取整后的像素预算，避免独立舍入在细线旁留下缝隙。 */
                val detailPaneWidth = with(density) {
                    (containerWidth.roundToPx() - listPaneWidth.roundToPx() - LIST_DETAIL_DIVIDER_WIDTH.roundToPx())
                        .coerceAtLeast(0)
                        .toDp()
                }
                val detailPaneStart = containerWidth - detailPaneWidth
                if (panes.showListDetail || !panes.showDetailPane) {
                    paneStateHolder.SaveableStateProvider("list") {
                        AppScaffold(
                            modifier = if (panes.hasMainReadingListContext && !panes.isLargeLandscape) Modifier.size(0.dp) else Modifier.fillMaxSize(),
                        ) { innerPadding ->
                            CompositionLocalProvider(
                                LocalArticleNavHost provides navHost,
                                LocalLifecycleOwner provides mainPaneLifecycleOwner,
                                LocalNavigator provides mainPaneNavigator,
                                LocalReadingPlayerOverlayPadding provides readingPlayerOverlayPadding,
                                LocalReadingPlayerOverlayOffsetState provides readingUi.overlayOffsetState,
                                LocalSelectedContentDestination provides panes.selectedContentDestination,
                            ) {
                                NavDisplay(
                                    navHost = navHost,
                                    modifier = (if (panes.showListDetail) Modifier.width(listPaneWidth).fillMaxHeight() else Modifier.fillMaxSize()).afterEachGesture {
                                        if (shouldCompactPlayerOnBackgroundInteraction) {
                                            delay(100)
                                            readingUi.isExpandedByUser = false
                                        }
                                    },
                                    onBack = navHost::onBack,
                                    transition = navTransition,
                                    effects = if (panes.isLargeLandscape) navEffects.copy(enableCornerClip = false, dimAmount = 0f) else navEffects,
                                    retainedEntryId = if (panes.showMainStackListDetail && currentDestination != Login) panes.mainListEntry?.id else null,
                                    entryTransition = if (panes.isLargeLandscape) {
                                        { entry, base ->
                                            val entryIndex = navHost.pageEntries.indexOfFirst { it.id == entry.id }
                                            val isRight = entry.destination != Login &&
                                                (panes.showMainStackListDetail || panes.showMainReadingListContext) &&
                                                (entryIndex < 0 || entryIndex >= panes.mainReadingStartIndex)
                                            listDetailEntryTransition(
                                                base = base,
                                                isLeft = panes.showMainStackListDetail && entry.id == panes.mainListEntry?.id,
                                                isRight = isRight,
                                                paneWidth = when {
                                                    isRight -> detailPaneWidth
                                                    entry.destination.isListPaneDestination() -> listPaneWidth
                                                    else -> containerWidth
                                                },
                                                detailPaneStart = detailPaneStart,
                                                effects = navEffects,
                                                layoutDirection = layoutDirection,
                                            )
                                        }
                                    } else {
                                        null
                                    },
                                    entryWrapper = { entry, content ->
                                        ZhihuMainListEntryFrame(
                                            entry = entry,
                                            navHost = navHost,
                                            paneState = paneState,
                                            layout = panes,
                                            tabs = tabs,
                                            listPaneWidth = listPaneWidth,
                                            navigate = navigate,
                                            content = content,
                                        )
                                    },
                                ) {
                                    entry<MainTabs>(swipeDismiss = NavSwipeDirection.None) {
                                        ZhihuMainTabsSurface(
                                            tabs = tabs,
                                            isActive = panes.isMainTabsPaneVisible,
                                            showBottomBar = showMainNavigationBar && !panes.sideRailEligible,
                                            autoHideBottomBar = autoHideBottomBar,
                                            floatingBottomBar = floatingBottomBar,
                                            bottomPadding = bottomPadding,
                                            showHomeTopActions = showHomeTopActions,
                                        )
                                    }
                                    mainStackPages(
                                        navHost = navHost,
                                        currentDestination = currentDestination,
                                        innerPadding = innerPadding,
                                        swipeBack = swipeBack,
                                        articleContent = articleContent,
                                        reloadBottomBarPreferences = reloadBottomBarPreferences,
                                        blocklistSettingsNlpContent = blocklistSettingsNlpContent,
                                    )
                                }
                            }
                        }
                    }
                }

                if (panes.showListDetail || panes.showDetailPane || panes.showMainStackListDetail || panes.hasMainReadingListContext || panes.hasOpenSecondaryDetail || panes.detailTop == null) {
                    paneStateHolder.SaveableStateProvider("detail") {
                        /** 隐藏时保存导航子树，独立窗口随所属页面暂停；改宽只调整原宿主布局。 */
                        Box(if (panes.showListDetail || panes.showDetailPane || panes.showMainStackListDetail || panes.hasMainReadingListContext) Modifier.fillMaxSize() else Modifier.size(0.dp)) {
                            if (!panes.showMainStackListDetail || panes.hasOpenSecondaryDetail) {
                                ZhihuMainDetailPane(
                                    visible = !panes.showMainStackListDetail && (panes.showListDetail || panes.showDetailPane || panes.hasMainReadingListContext),
                                    isDualPaneReading = panes.showListDetail,
                                    modifier = when {
                                        panes.showMainReadingListContext ->
                                            Modifier
                                                .align(Alignment.CenterStart)
                                                .width(listPaneWidth)
                                                .fillMaxHeight()
                                                .testTag("list_pane")
                                        panes.hasMainReadingListContext -> Modifier.fillMaxSize().testTag("list_pane")
                                        panes.showListDetail ->
                                            Modifier
                                                .align(Alignment.CenterEnd)
                                                .width(detailPaneWidth)
                                                .fillMaxHeight()
                                        else -> Modifier.fillMaxSize()
                                    },
                                    detailHost = detailHost,
                                    navigator = Navigator(
                                        onNavigate = router::navigateFromDetailPane,
                                        onNavigateBack = detailHost::back,
                                        onNavigateTopLevel = tabs.navigateTopLevel,
                                    ),
                                    selectedContentDestination = panes.selectedContentDestination,
                                    readingPlayerOverlayPadding = readingPlayerOverlayPadding,
                                    readingPlayerOverlayOffsetState = readingUi.overlayOffsetState,
                                    navTransition = navTransition,
                                    navEffects = navEffects,
                                    swipeBack = swipeBack,
                                    articleContent = articleContent,
                                    reloadBottomBarPreferences = reloadBottomBarPreferences,
                                    blocklistSettingsNlpContent = blocklistSettingsNlpContent,
                                )
                            }
                        }
                    }
                }

                /** 与两栏同级且后绘制，使拖拽层先命中并向下共享输入；浮动栏仍在它之上。 */
                if (currentDestination != Login && (panes.showListDetail || panes.showMainStackListDetail || panes.showMainReadingListContext)) {
                    ZhihuMainPaneDivider(
                        modifier = Modifier.align(Alignment.CenterStart).offset(x = listPaneWidth),
                        paneState = paneState,
                        settings = settings,
                        containerWidth = containerWidth,
                        minimumListWidth = panes.minimumListWidth,
                        minimumDetailWidth = panes.minimumDetailWidth,
                    )
                }

                ZhihuMainReadingPlayerBar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(
                            start = if (panes.showListDetail && panes.showDetailPane) detailPaneStart else 0.dp,
                            bottom = 16.dp + if (
                                !panes.showDetailPane && panes.showMainNavigation && showMainNavigationBar && !panes.useSideRail && (!autoHideBottomBar || tabs.state.isBottomBarVisible)
                            ) {
                                with(density) { tabs.state.navigationBarHeightPx.toDp() }
                            } else {
                                bottomPadding
                            },
                        ).fillMaxWidth(),
                    ui = readingUi,
                    readingPlayer = readingPlayer,
                    readingPlayerState = readingPlayerState,
                    settings = settings,
                    isExpanded = isReadingPlayerExpanded,
                    isOnReadingDetail = panes.isOnReadingDetail,
                )
            }

            if (accountSheetVisible) {
                CompositionLocalProvider(LocalNavigator provides mainPaneNavigator) {
                    AppBottomSheet(
                        onDismissRequest = onDismissAccountSheet,
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        AccountSettingScreen(
                            innerPadding = PaddingValues(0.dp),
                            onDismissRequest = onDismissAccountSheet,
                        )
                    }
                }
            }

            ZhihuMainReadingQueueSheet(
                ui = readingUi,
                readingPlayer = readingPlayer,
                readingPlayerState = readingPlayerState,
                isOnReadingDetail = panes.isOnReadingDetail,
                onOpenDestination = { router.openReadingDestination(it) },
                onOpenSettings = {
                    if (currentDestination !is Account.ReadingPlaybackSettings) {
                        navigate(Account.ReadingPlaybackSettings())
                    }
                },
            )
            ZhihuMainAccountDialogs(
                showExpiryPrompt = currentDestination != Login,
                onRelogin = { navigate(Login) },
            )
        }
    }
}
