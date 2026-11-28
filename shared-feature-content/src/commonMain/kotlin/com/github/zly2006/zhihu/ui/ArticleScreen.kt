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

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.github.zly2006.zhihu.account.rememberZhihuAccountStore
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.VoteUpState
import com.github.zly2006.zhihu.i18n.ContentStrings
import com.github.zly2006.zhihu.markdown.rememberMarkdownSearchState
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.platform.PlatformBackHandler
import com.github.zly2006.zhihu.platform.defaultAnswerSwitchMode
import com.github.zly2006.zhihu.platform.isAnswerSwipeSupported
import com.github.zly2006.zhihu.platform.isArticleHtmlExportSupported
import com.github.zly2006.zhihu.platform.isArticleImageExportSupported
import com.github.zly2006.zhihu.platform.isArticlePdfExportSupported
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.ui.AnswerDoubleTapAction
import com.github.zly2006.zhihu.ui.article.AigcFlagSheet
import com.github.zly2006.zhihu.ui.article.ArticleContentSearchState
import com.github.zly2006.zhihu.ui.article.ArticleSummarySheet
import com.github.zly2006.zhihu.ui.article.CachedAnswerPreview
import com.github.zly2006.zhihu.ui.article.rememberArticleAnswerNavigationState
import com.github.zly2006.zhihu.ui.article.rememberArticleBottomBarState
import com.github.zly2006.zhihu.ui.article.rememberArticleTopBarState
import com.github.zly2006.zhihu.ui.article.rememberBottomBarAvoidingBringIntoViewSpec
import com.github.zly2006.zhihu.ui.components.ANSWER_SWITCH_SENSITIVITY_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.components.AnswerHorizontalOverscroll
import com.github.zly2006.zhihu.ui.components.AnswerVerticalOverscroll
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppIcon
import com.github.zly2006.zhihu.ui.components.AppSpinner
import com.github.zly2006.zhihu.ui.components.CollectionDialogComponent
import com.github.zly2006.zhihu.ui.components.CommentScreenComponent
import com.github.zly2006.zhihu.ui.components.DEFAULT_ANSWER_SWITCH_SENSITIVITY
import com.github.zly2006.zhihu.ui.components.DraggableRefreshButton
import com.github.zly2006.zhihu.ui.components.ExportDialogComponent
import com.github.zly2006.zhihu.ui.components.VerticalReadingProgressBar
import com.github.zly2006.zhihu.ui.components.VotersSheet
import com.github.zly2006.zhihu.ui.components.normalizedAnswerSwitchSensitivity
import com.github.zly2006.zhihu.ui.components.rememberAppPageTitleBarScrollState
import com.github.zly2006.zhihu.ui.components.rememberPageTurnTarget
import com.github.zly2006.zhihu.ui.subscreens.DEFAULT_PAGE_TURN_SWITCH_ANSWER
import com.github.zly2006.zhihu.ui.subscreens.PREF_PAGE_TURN_SWITCH_ANSWER
import com.github.zly2006.zhihu.ui.subscreens.TIQIAN_MARKDOWN_PREFERENCE_KEY
import com.github.zly2006.zhihu.util.formatCompactCount
import com.github.zly2006.zhihu.viewmodel.ArticleViewModel
import com.github.zly2006.zhihu.viewmodel.addReadHistory
import com.github.zly2006.zhihu.viewmodel.rememberPaginationEnvironment
import com.github.zly2006.zhihu.viewmodel.sharedArticleAnswerSwitchState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private val ScrollThresholdDp = 10.dp

const val ARTICLE_AUTHOR_FOLLOW_TAG = "article_author_follow"

const val ARTICLE_FULL_TITLE_TAG = "article_full_title"

const val ARTICLE_IP_INFO_TAG = "article_ip_info"

const val ARTICLE_BACK_BUTTON_TAG = "article_back_button"

const val ARTICLE_LOADING_TAG = "article_loading"

const val ARTICLE_LOAD_ERROR_TAG = "article_load_error"

const val ARTICLE_LOAD_RETRY_TAG = "article_load_retry"

const val ARTICLE_EMPTY_TAG = "article_empty"

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class,
)
@Composable
fun ArticleScreen(
    article: Article,
    viewModel: ArticleViewModel,
) {
    val navigator = LocalNavigator.current
    val readingPlayerOverlayPadding = LocalReadingPlayerOverlayPadding.current
    val readingPlayerOverlayOffsetState = LocalReadingPlayerOverlayOffsetState.current
    val environment = rememberPaginationEnvironment(allowGuestAccess = false)
    val accountStore = rememberZhihuAccountStore()
    val onVote: ((VoteUpState) -> Unit)? = if (accountStore.usesBrowserSession) {
        null
    } else {
        { state -> viewModel.toggleVoteUp(environment, state) }
    }
    val articleNavHost = LocalArticleNavHost.current
    val articleEntryId = remember(articleNavHost, viewModel) { articleNavHost?.topEntryId }
    val skin = LocalAppSkinTokens.current
    val displayTitle = viewModel.title.ifBlank { article.title.takeUnless { it == "loading..." }.orEmpty() }
    val hasBody = viewModel.content.isNotBlank() || viewModel.attachment != null
    val awaitingDetail = viewModel.isLoading || (!viewModel.hasLoaded && viewModel.loadError == null)

    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val settings = rememberSettingsStore()
    val isTitleAutoHide by rememberObservedSetting(settings, "titleAutoHide") { getBoolean("titleAutoHide", false) }
    val autoHideArticleBottomBar by rememberObservedSetting(settings, "autoHideArticleBottomBar") {
        getBoolean("autoHideArticleBottomBar", true)
    }
    val configuredAnswerSwitchMode by rememberObservedSetting(settings, "answerSwitchMode") {
        getString("answerSwitchMode", defaultAnswerSwitchMode)
    }
    val answerSwitchMode = configuredAnswerSwitchMode.takeIf { isAnswerSwipeSupported } ?: "off"
    val answerSwitchSensitivity by rememberObservedSetting(settings, ANSWER_SWITCH_SENSITIVITY_PREFERENCE_KEY) {
        normalizedAnswerSwitchSensitivity(getFloat(ANSWER_SWITCH_SENSITIVITY_PREFERENCE_KEY, DEFAULT_ANSWER_SWITCH_SENSITIVITY))
    }
    val pinAnswerDate by rememberObservedSetting(settings, "pinAnswerDate") { getBoolean("pinAnswerDate", false) }
    val buttonSkipAnswer by rememberObservedSetting(settings, "buttonSkipAnswer") { getBoolean("buttonSkipAnswer", true) }
    val autoHideSkipAnswerButton by rememberObservedSetting(settings, "autoHideSkipAnswerButton") {
        getBoolean("autoHideSkipAnswerButton", true)
    }
    var answerDoubleTapAction by rememberObservedSetting(settings, ANSWER_DOUBLE_TAP_ACTION_PREFERENCE_KEY) {
        AnswerDoubleTapAction.fromPreference(
            getString(ANSWER_DOUBLE_TAP_ACTION_PREFERENCE_KEY, AnswerDoubleTapAction.Ask.preferenceValue),
        )
    }
    val useWebView by rememberObservedSetting(settings, ARTICLE_USE_WEBVIEW_PREFERENCE_KEY) {
        getBoolean(ARTICLE_USE_WEBVIEW_PREFERENCE_KEY, false)
    }
    val legacyWebViewActive = useWebView && isLegacyWebViewSupported
    val useTiqianMarkdown by rememberObservedSetting(settings, TIQIAN_MARKDOWN_PREFERENCE_KEY) {
        getBoolean(TIQIAN_MARKDOWN_PREFERENCE_KEY, false)
    }
    val pageTurnSwitchAnswer by rememberObservedSetting(settings, PREF_PAGE_TURN_SWITCH_ANSWER) {
        getBoolean(PREF_PAGE_TURN_SWITCH_ANSWER, DEFAULT_PAGE_TURN_SWITCH_ANSWER)
    }

    fun saveAnswerDoubleTapAction(action: AnswerDoubleTapAction) {
        answerDoubleTapAction = action
        settings.putString(ANSWER_DOUBLE_TAP_ACTION_PREFERENCE_KEY, action.preferenceValue)
    }
    val userMessages = rememberUserMessageSink()
    val density = LocalDensity.current
    val readingPlayerOverlayPaddingPx = with(density) { readingPlayerOverlayPadding.roundToPx() }
    val effectiveScrollMaxValue by remember(readingPlayerOverlayPaddingPx) {
        derivedStateOf {
            if (scrollState.maxValue == Int.MAX_VALUE) {
                Int.MAX_VALUE
            } else {
                (scrollState.maxValue - readingPlayerOverlayPaddingPx).coerceAtLeast(0)
            }
        }
    }
    val latestEffectiveScrollMaxValue by rememberUpdatedState(effectiveScrollMaxValue)
    var showComments by rememberSaveable(article.type, article.id) { mutableStateOf(false) }
    var showCollectionDialog by remember { mutableStateOf(false) }
    var showActionsMenu by remember { mutableStateOf(false) }
    var showWebViewLimits by remember { mutableStateOf(false) }
    var showSummaryDialog by remember { mutableStateOf(false) }
    var showAigcFlagSheet by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showDoubleTapActionDialog by remember { mutableStateOf(false) }
    var showVoters by rememberSaveable(article.type, article.id) { mutableStateOf(false) }
    val markdownSearch = rememberMarkdownSearchState(useTiqianMarkdown, viewModel.content)
    val contentSearch = remember(article.type, article.id, markdownSearch) { ArticleContentSearchState(markdownSearch) }
    LaunchedEffect(contentSearch) { contentSearch.observeQuery() }
    val topBarState = rememberArticleTopBarState(
        scrollState = scrollState,
        autoHide = isTitleAutoHide && !contentSearch.visible,
    )
    LaunchedEffect(legacyWebViewActive, contentSearch.visible, contentSearch.requestId, contentSearch.matchCount) {
        if (legacyWebViewActive) {
            if (contentSearch.visible) contentSearch.hide()
        } else {
            contentSearch.revealCurrent()
        }
    }
    val bottomBarState = rememberArticleBottomBarState(
        scrollState = scrollState,
        autoHide = autoHideArticleBottomBar,
        scrollDeltaThreshold = with(density) { ScrollThresholdDp.toPx() },
        showSlot = articleNavHost == null || articleNavHost.top is Article,
        navigationBarHeightPx = density.run {
            WindowInsets.navigationBars
                .asPaddingValues()
                .calculateBottomPadding()
                .toPx()
                .coerceAtLeast(0f)
        },
    )
    val sharedData = sharedArticleAnswerSwitchState.takeIf { article.type == ArticleType.Answer }
    var isImmersiveMode by remember(sharedData) {
        mutableStateOf(sharedData?.isImmersiveMode ?: false)
    }
    val answerNavigationState = rememberArticleAnswerNavigationState(
        switchState = sharedData,
        viewModel = viewModel,
        navigator = navigator,
        navHost = articleNavHost,
        answerSwitchMode = answerSwitchMode,
        readingQueueSourceId = article.readingQueueSourceId,
    )
    val usesVerticalAnswerSwitch = article.type == ArticleType.Answer && answerSwitchMode == "vertical"
    val pageTurnActive = !legacyWebViewActive &&
        !showComments &&
        !showCollectionDialog &&
        !showActionsMenu &&
        !showSummaryDialog &&
        !showAigcFlagSheet &&
        !showExportDialog &&
        !showDoubleTapActionDialog &&
        !showVoters
    val scrollBehavior = rememberAppPageTitleBarScrollState()
    val pageTurnTarget = rememberPageTurnTarget(
        scrollState = scrollState,
        enabled = pageTurnActive,
        maxScrollValue = effectiveScrollMaxValue,
        onPageUpAtStart = (answerNavigationState::navigateToPrevious)
            .takeIf { article.type == ArticleType.Answer && pageTurnSwitchAnswer },
        onPageDownAtEnd = (answerNavigationState::navigateToNext)
            .takeIf { article.type == ArticleType.Answer && pageTurnSwitchAnswer },
        titleBarScrollState = if (isImmersiveMode) null else scrollBehavior,
    )
    val hapticFeedback = LocalHapticFeedback.current
    val readingPlayerOverlayRouteId = articleEntryId
        ?: article.readingQueueSourceId
        ?: "${article.type}:${article.id}"
    DisposableEffect(readingPlayerOverlayOffsetState, readingPlayerOverlayRouteId, usesVerticalAnswerSwitch) {
        if (usesVerticalAnswerSwitch) {
            readingPlayerOverlayOffsetState?.beginRoute(readingPlayerOverlayRouteId)
        }
        onDispose {
            if (usesVerticalAnswerSwitch) {
                readingPlayerOverlayOffsetState?.endRoute(readingPlayerOverlayRouteId)
            }
        }
    }
    val updateReadingPlayerOverlayOffset = remember(readingPlayerOverlayOffsetState, readingPlayerOverlayRouteId) {
        { offsetPx: Float ->
            readingPlayerOverlayOffsetState?.update(readingPlayerOverlayRouteId, offsetPx)
            Unit
        }
    }

    LaunchedEffect(sharedData, isImmersiveMode) {
        if (sharedData != null) sharedData.isImmersiveMode = isImmersiveMode
    }
    ArticleImmersiveModeEffect(isImmersiveMode)

    LaunchedEffect(Unit) {
        environment.addReadHistory(
            contentToken = article.id.toString(),
            contentTypeName = article.type.name.lowercase(),
        )
    }

    fun upVoteFromDoubleTap() {
        if (onVote == null) {
            userMessages.showShortMessage(ContentStrings.webVotingReadOnly)
            return
        }
        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
        if (viewModel.voteUpState != VoteUpState.Up) onVote(VoteUpState.Up)
    }

    fun performAnswerDoubleTapAction(action: AnswerDoubleTapAction) {
        when (action) {
            AnswerDoubleTapAction.None -> Unit
            AnswerDoubleTapAction.Ask -> showDoubleTapActionDialog = true
            AnswerDoubleTapAction.VoteUp -> upVoteFromDoubleTap()
            AnswerDoubleTapAction.OpenComments -> {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                showComments = true
            }
            AnswerDoubleTapAction.ToggleImmersive -> {
                isImmersiveMode = !isImmersiveMode
            }
            AnswerDoubleTapAction.JumpToTop -> coroutineScope.launch {
                scrollState.scrollTo(0)
            }
            AnswerDoubleTapAction.JumpToBottom -> coroutineScope.launch {
                if (latestEffectiveScrollMaxValue != Int.MAX_VALUE) {
                    scrollState.scrollTo(latestEffectiveScrollMaxValue)
                }
            }
        }
    }

    fun handleAnswerDoubleTap() {
        if (article.type != ArticleType.Answer) return
        performAnswerDoubleTapAction(answerDoubleTapAction)
    }

    val answerDoubleTapModifier = if (
        article.type == ArticleType.Answer &&
        answerDoubleTapAction != AnswerDoubleTapAction.None
    ) {
        Modifier.pointerInput(answerDoubleTapAction, onVote) {
            detectTapGestures(
                onDoubleTap = { handleAnswerDoubleTap() },
            )
        }
    } else {
        Modifier
    }

    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.value }.collectLatest { currentScroll ->
            viewModel.updateAigcReadProgress(currentScroll, effectiveScrollMaxValue)
            viewModel.syncAigcReadEventIfEligible(environment)

            if (viewModel.rememberedScrollYSync) {
                viewModel.rememberedScrollY = currentScroll
            }
            if (currentScroll == viewModel.rememberedScrollY && scrollState.maxValue != Int.MAX_VALUE) {
                viewModel.rememberedScrollYSync = true
            }
        }
    }

    val obscuredBottomPx = bottomBarState.obscuredHeightPx + readingPlayerOverlayPaddingPx
    val obscuredTopPx = if (contentSearch.visible) topBarState.heightPx else null
    val articleBringIntoViewSpec = contentSearch.renderer.rememberBringIntoViewSpec(
        obscuredTopPx = obscuredTopPx ?: 0f,
        obscuredBottomPx = obscuredBottomPx,
        fallback = rememberBottomBarAvoidingBringIntoViewSpec(obscuredBottomPx, obscuredTopPx),
    )
    LaunchedEffect(article.type, article.id, viewModel) {
        val needsLoad = !viewModel.hasLoaded || viewModel.loadError != null
        if (!viewModel.hasLoaded || sharedData?.pendingNavigator != null) answerNavigationState.prepareArticle()
        if (needsLoad) {
            viewModel.loadArticle(environment)
            viewModel.loadCollections(environment)
            viewModel.loadAigcFlagStatus(environment)
        }
    }

    LaunchedEffect(article.type, article.id, viewModel.content) {
        if (viewModel.content.isNotBlank()) {
            viewModel.updateAigcReadProgress(scrollState.value, latestEffectiveScrollMaxValue)
            delay(15_000)
            viewModel.updateAigcReadProgress(scrollState.value, latestEffectiveScrollMaxValue)
            viewModel.syncAigcReadEventIfEligible(environment)
        }
    }
    LaunchedEffect(scrollState, viewModel.content) {
        snapshotFlow { effectiveScrollMaxValue }.collectLatest { maxValue ->
            if (viewModel.content.isNotBlank()) {
                viewModel.updateAigcReadProgress(scrollState.value, maxValue)
            }
        }
    }

    @Composable
    fun MainContent() {
        ArticleMainContent(
            article = article,
            viewModel = viewModel,
            navigator = navigator,
            readingPlayerOverlayPadding = readingPlayerOverlayPadding,
            environment = environment,
            accountStore = accountStore,
            onVote = onVote,
            skin = skin,
            displayTitle = displayTitle,
            hasBody = hasBody,
            awaitingDetail = awaitingDetail,
            scrollState = scrollState,
            pinAnswerDate = pinAnswerDate,
            legacyWebViewActive = legacyWebViewActive,
            useTiqianMarkdown = useTiqianMarkdown,
            userMessages = userMessages,
            onShowComments = { showComments = true },
            onShowCollectionDialog = { showCollectionDialog = true },
            showActionsMenu = showActionsMenu,
            onActionsMenuVisibilityChange = { showActionsMenu = it },
            showWebViewLimits = showWebViewLimits,
            onWebViewLimitsVisibilityChange = { showWebViewLimits = it },
            onShowSummaryDialog = { showSummaryDialog = true },
            onShowAigcFlagSheet = { showAigcFlagSheet = true },
            onShowExportDialog = { showExportDialog = true },
            onShowVoters = { showVoters = true },
            markdownSearch = markdownSearch,
            contentSearch = contentSearch,
            topBarState = topBarState,
            bottomBarState = bottomBarState,
            sharedData = sharedData,
            isImmersiveMode = isImmersiveMode,
            onToggleImmersiveMode = { isImmersiveMode = !isImmersiveMode },
            scrollBehavior = scrollBehavior,
            pageTurnTarget = pageTurnTarget,
            articleBringIntoViewSpec = articleBringIntoViewSpec,
            onRememberedScrollYSyncChange = { viewModel.rememberedScrollYSync = it },
            onAnswerDoubleTap = ::handleAnswerDoubleTap,
        )
    }

    val nav = answerNavigationState.answerNavigator
    val progressBarTopPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 64.dp
    val progressBarBottomPadding = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding() + 96.dp

    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = if (LocalIsDualPaneReading.current) Alignment.TopStart else Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = AppDimens.ArticleReadingMaxWidth)
                .fillMaxSize()
                .testTag("article_screen_root")
                .then(answerDoubleTapModifier),
        ) {
            if (article.type == ArticleType.Answer && answerSwitchMode == "vertical") {
                AnswerVerticalOverscroll(
                    previousAnswer = nav?.previousAnswer?.takeIf { !showActionsMenu },
                    nextAnswer = nav?.nextAnswer?.takeIf { !showActionsMenu },
                    onNavigatePrevious = answerNavigationState::navigateToPrevious,
                    onNavigateNext = answerNavigationState::navigateToNext,
                    isAtTop = { scrollState.value == 0 },
                    isAtBottom = { scrollState.value >= effectiveScrollMaxValue },
                    scrollState = scrollState,
                    answerSwitchSensitivity = answerSwitchSensitivity,
                    onOverscrollOffsetChange = updateReadingPlayerOverlayOffset,
                ) {
                    MainContent()
                }
            } else if (article.type == ArticleType.Answer && answerSwitchMode == "horizontal") {
                AnswerHorizontalOverscroll(
                    canGoPrevious = nav?.previousAnswer != null,
                    canGoNext = nav?.nextAnswer != null,
                    onNavigatePrevious = answerNavigationState::navigateToPrevious,
                    onNavigateNext = answerNavigationState::navigateToNext,
                    previousContent = nav?.previousAnswer?.let { cached ->
                        { CachedAnswerPreview(cached, previous = true) }
                    },
                    nextContent = nav?.nextAnswer?.let { cached ->
                        { CachedAnswerPreview(cached) }
                    },
                    answerSwitchSensitivity = answerSwitchSensitivity,
                ) {
                    MainContent()
                }
            } else {
                MainContent()
            }

            VerticalReadingProgressBar(
                scrollState = scrollState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(
                        top = progressBarTopPadding,
                        bottom = progressBarBottomPadding,
                        end = 2.dp,
                    ),
            )

            if (article.type == ArticleType.Answer && buttonSkipAnswer && !isImmersiveMode) {
                val showSkipButton = !autoHideSkipAnswerButton || !bottomBarState.isScrollingUp
                val skipButtonAlpha by animateFloatAsState(
                    targetValue = if (showSkipButton) 1f else 0f,
                    animationSpec = tween(200),
                    label = "skipButtonAlpha",
                )
                var fabClickCount by remember { mutableIntStateOf(0) }
                LaunchedEffect(fabClickCount) {
                    if (fabClickCount > 0) {
                        delay(350)
                        if (fabClickCount >= 2) {
                            isImmersiveMode = !isImmersiveMode
                        } else {
                            if (showSkipButton) {
                                answerNavigationState.navigateToNext()
                            }
                        }
                        fabClickCount = 0
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxSize(),
                ) {
                    DraggableRefreshButton(
                        modifier = Modifier.graphicsLayer { alpha = skipButtonAlpha },
                        onClick = { fabClickCount++ },
                        preferenceName = "buttonSkipAnswer",
                        bottomAvoidance = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + readingPlayerOverlayPadding,
                    ) {
                        if (answerNavigationState.navigatingToNextAnswer) {
                            AppSpinner(size = 30.dp)
                        } else {
                            AppIcon(Icons.Filled.SkipNext, contentDescription = ContentStrings.nextAnswer)
                        }
                    }
                }
            }
        }
    }

    ArticleSummarySheet(
        showDialog = showSummaryDialog,
        summaryText = viewModel.aiSummaryText,
        loading = viewModel.aiSummaryLoading,
        errorMessage = viewModel.aiSummaryError,
        onDismissRequest = {
            showSummaryDialog = false
            viewModel.cancelAiSummary()
        },
        onRetryRequest = {
            viewModel.requestAiSummary(environment)
        },
    )

    PlatformBackHandler(enabled = isImmersiveMode) {
        isImmersiveMode = false
    }

    PlatformBackHandler(showActionsMenu) {
        showActionsMenu = false
    }

    AigcFlagSheet(
        showDialog = showAigcFlagSheet,
        viewModel = viewModel,
        onDismissRequest = { showAigcFlagSheet = false },
        onSubmitRequest = { viewModel.submitAigcFlag(environment) },
    )

    CollectionDialogComponent(
        showDialog = showCollectionDialog,
        onDismiss = { showCollectionDialog = false },
        collections = viewModel.collections,
        onLoadCollections = { viewModel.loadCollections(environment) },
        onToggleFavorite = { collection ->
            viewModel.toggleFavorite(collection.id, collection.isFavorited, environment)
        },
        onCreateCollection = { title, description, isPublic ->
            viewModel.createNewCollection(environment, title, description, isPublic)
        },
    )

    CommentScreenComponent(
        showComments = showComments,
        onDismiss = { showComments = false },
        content = article,
        isZhPlusAuthorContent = article.type == ArticleType.Answer &&
            viewModel.authorId == DataHolder.ZH_PLUS_AUTHOR_USER_ID,
        commentSegments = viewModel.commentSegments,
    )
    VotersSheet(
        show = showVoters,
        title = ContentStrings.answerUpvotes(viewModel.votersTotal, formatCompactCount(viewModel.votersTotal)),
        voters = viewModel.voters,
        isLoading = viewModel.votersLoading,
        errorMessage = viewModel.votersError,
        canLoadMore = viewModel.votersNextUrl != null,
        onDismissRequest = { showVoters = false },
        onLoadMore = { viewModel.loadMoreVoters(environment) },
        onRetry = { viewModel.loadMoreVoters(environment, reset = viewModel.voters.isEmpty()) },
        onNavigate = { person ->
            showVoters = false
            navigator.onNavigate(person)
        },
    )
    if (showDoubleTapActionDialog) {
        ArticleDoubleTapActionSheet(
            votingEnabled = onVote != null,
            onDismissRequest = { showDoubleTapActionDialog = false },
            onSelect = { action ->
                showDoubleTapActionDialog = false
                saveAnswerDoubleTapAction(action)
                when (action) {
                    AnswerDoubleTapAction.None, AnswerDoubleTapAction.Ask -> Unit
                    AnswerDoubleTapAction.VoteUp -> upVoteFromDoubleTap()
                    AnswerDoubleTapAction.OpenComments -> showComments = true
                    AnswerDoubleTapAction.ToggleImmersive -> isImmersiveMode = !isImmersiveMode
                    AnswerDoubleTapAction.JumpToTop, AnswerDoubleTapAction.JumpToBottom -> performAnswerDoubleTapAction(action)
                }
                userMessages.showMessage(ContentStrings.doubleTapActionSaved(action.label))
            },
        )
    }
    ExportDialogComponent(
        showDialog = showExportDialog,
        isHtmlExportSupported = isArticleHtmlExportSupported,
        isImageExportSupported = isArticleImageExportSupported,
        isPdfExportSupported = isArticlePdfExportSupported,
        onDismiss = { showExportDialog = false },
        onExportPdf = { includeAppAttribution, onComplete ->
            viewModel.exportToPdf(environment, includeAppAttribution, onComplete)
        },
        onExportHtml = { includeAppAttribution, onComplete ->
            viewModel.exportToHtml(environment, includeAppAttribution, onComplete)
        },
        onExportImage = { includeAppAttribution, onComplete ->
            viewModel.exportToImage(environment, includeAppAttribution, onComplete)
        },
        onExportMarkdown = {
            viewModel.exportToClipboard(environment)
        },
        onExportImageWithComments = { commentCount, includeAppAttribution, onComplete ->
            viewModel.exportToImageWithComments(environment, commentCount, includeAppAttribution, onComplete)
        },
    )
}
