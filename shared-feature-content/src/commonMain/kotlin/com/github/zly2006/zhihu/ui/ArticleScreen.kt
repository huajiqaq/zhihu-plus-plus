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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.github.zly2006.zhihu.account.rememberZhihuAccountStore
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.VoteUpState
import com.github.zly2006.zhihu.markdown.LocalMarkdownWidthManagedByHost
import com.github.zly2006.zhihu.markdown.RenderMarkdown
import com.github.zly2006.zhihu.markdown.rememberMarkdownSearchState
import com.github.zly2006.zhihu.navigation.Account
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.navigation.Topic
import com.github.zly2006.zhihu.navigation.withReadingQueueSource
import com.github.zly2006.zhihu.platform.PlatformBackHandler
import com.github.zly2006.zhihu.platform.isAnswerSwipeSupported
import com.github.zly2006.zhihu.platform.isArticleHtmlExportSupported
import com.github.zly2006.zhihu.platform.isArticleImageExportSupported
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.ui.AnswerDoubleTapAction
import com.github.zly2006.zhihu.ui.article.AigcFlagSheet
import com.github.zly2006.zhihu.ui.article.ArticleActionsMenu
import com.github.zly2006.zhihu.ui.article.ArticleContentSearchState
import com.github.zly2006.zhihu.ui.article.ArticleLoadingPlaceholder
import com.github.zly2006.zhihu.ui.article.ArticleSummarySheet
import com.github.zly2006.zhihu.ui.article.ArticleVideoAttachmentContent
import com.github.zly2006.zhihu.ui.article.CachedAnswerPreview
import com.github.zly2006.zhihu.ui.article.rememberArticleAnswerNavigationState
import com.github.zly2006.zhihu.ui.article.rememberArticleBottomBarState
import com.github.zly2006.zhihu.ui.article.rememberArticleTopBarState
import com.github.zly2006.zhihu.ui.article.rememberBottomBarAvoidingBringIntoViewSpec
import com.github.zly2006.zhihu.ui.components.ANSWER_SWITCH_SENSITIVITY_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.components.AnswerHorizontalOverscroll
import com.github.zly2006.zhihu.ui.components.AnswerVerticalOverscroll
import com.github.zly2006.zhihu.ui.components.AppButton
import com.github.zly2006.zhihu.ui.components.AppChip
import com.github.zly2006.zhihu.ui.components.AppDialog
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppEmptyState
import com.github.zly2006.zhihu.ui.components.AppFloatingToolbar
import com.github.zly2006.zhihu.ui.components.AppIconButton
import com.github.zly2006.zhihu.ui.components.AppIconToggleButton
import com.github.zly2006.zhihu.ui.components.AppPageTitleBar
import com.github.zly2006.zhihu.ui.components.AppScaffold
import com.github.zly2006.zhihu.ui.components.AppSearchField
import com.github.zly2006.zhihu.ui.components.AppSpinner
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.ui.components.AuthorBadge
import com.github.zly2006.zhihu.ui.components.CollectionDialogComponent
import com.github.zly2006.zhihu.ui.components.CommentScreenComponent
import com.github.zly2006.zhihu.ui.components.ContentEndMarker
import com.github.zly2006.zhihu.ui.components.DEFAULT_ANSWER_SWITCH_SENSITIVITY
import com.github.zly2006.zhihu.ui.components.DraggableRefreshButton
import com.github.zly2006.zhihu.ui.components.ExportDialogComponent
import com.github.zly2006.zhihu.ui.components.LocalOnRequestContentRefresh
import com.github.zly2006.zhihu.ui.components.LocalSegmentSelectionTarget
import com.github.zly2006.zhihu.ui.components.MyModalBottomSheet
import com.github.zly2006.zhihu.ui.components.SegmentSelectionTarget
import com.github.zly2006.zhihu.ui.components.VerticalReadingProgressBar
import com.github.zly2006.zhihu.ui.components.VotersSheet
import com.github.zly2006.zhihu.ui.components.appPageTitleBarScroll
import com.github.zly2006.zhihu.ui.components.normalizedAnswerSwitchSensitivity
import com.github.zly2006.zhihu.ui.components.pageTurnViewportWithGuide
import com.github.zly2006.zhihu.ui.components.rememberAppPageTitleBarScrollState
import com.github.zly2006.zhihu.ui.components.rememberPageTurnTarget
import com.github.zly2006.zhihu.ui.components.settingSkinIsMiuix
import com.github.zly2006.zhihu.ui.subscreens.DEFAULT_PAGE_TURN_SWITCH_ANSWER
import com.github.zly2006.zhihu.ui.subscreens.DUO3_TIQIAN_MARKDOWN_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.subscreens.PREF_PAGE_TURN_SWITCH_ANSWER
import com.github.zly2006.zhihu.util.formatCompactCount
import com.github.zly2006.zhihu.util.formatFullDateTime
import com.github.zly2006.zhihu.util.parseEmphasizedHtmlTextWithTheme
import com.github.zly2006.zhihu.util.smoothGradient
import com.github.zly2006.zhihu.viewmodel.ArticleViewModel
import com.github.zly2006.zhihu.viewmodel.addReadHistory
import com.github.zly2006.zhihu.viewmodel.rememberPaginationEnvironment
import com.github.zly2006.zhihu.viewmodel.sharedArticleAnswerSwitchState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import zhihu.uicomponents.generated.resources.Res
import zhihu.uicomponents.generated.resources.ic_vote_down_24dp
import zhihu.uicomponents.generated.resources.ic_vote_up_24dp
import androidx.compose.material3.Icon as MaterialIcon
import com.github.zly2006.zhihu.ui.components.AppIcon as Icon

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
        getBoolean("autoHideArticleBottomBar", false)
    }
    val configuredAnswerSwitchMode by rememberObservedSetting(settings, "answerSwitchMode") {
        getString("answerSwitchMode", "vertical")
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
    val useTiqianMarkdown by rememberObservedSetting(settings, DUO3_TIQIAN_MARKDOWN_PREFERENCE_KEY) {
        getBoolean(DUO3_TIQIAN_MARKDOWN_PREFERENCE_KEY, false)
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
        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
        if (viewModel.voteUpState != VoteUpState.Up) {
            viewModel.toggleVoteUp(environment, VoteUpState.Up)
        }
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
        Modifier.pointerInput(answerDoubleTapAction) {
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
        if (!viewModel.hasLoaded || sharedData?.pendingNavigator != null) answerNavigationState.prepareArticle()
        if (!viewModel.hasLoaded || viewModel.loadError != null) {
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

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainContent() {
        val showContentTitle = isImmersiveMode && displayTitle.isNotBlank()
        AppScaffold(
            modifier = Modifier
                .fillMaxSize()
                .then(if (!isImmersiveMode) Modifier.appPageTitleBarScroll(scrollBehavior) else Modifier),
            topBar = if (isImmersiveMode) {
                {}
            } else {
                {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onSizeChanged {
                                topBarState.heightPx = it.height.toFloat()
                            }.graphicsLayer {
                                translationY = topBarState.offset.value
                                alpha = if (topBarState.heightPx > 0f) 1f + (topBarState.offset.value / topBarState.heightPx) else 1f
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        AppPageTitleBar(
                            title = displayTitle,
                            modifier = Modifier.fillMaxWidth(),
                            titleTag = ARTICLE_FULL_TITLE_TAG,
                            scrollState = scrollBehavior,
                            onBack = navigator.onNavigateBack,
                            backTag = ARTICLE_BACK_BUTTON_TAG,
                            multilineTitle = true,
                            onTitleClick = if (article.type == ArticleType.Answer && viewModel.questionId > 0) {
                                { navigator.onNavigate(Question(viewModel.questionId, displayTitle)) }
                            } else {
                                null
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background,
                                scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            ),
                            actions = {
                                AppIconButton(
                                    onClick = {
                                        if (legacyWebViewActive) showWebViewLimits = true else contentSearch.show()
                                    },
                                    modifier = Modifier.testTag("article_search"),
                                ) {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = "搜索正文",
                                        tint = skin.onSurfaceVariantActions,
                                    )
                                }
                                if (article.type == ArticleType.Answer && viewModel.questionId > 0) {
                                    AppIconButton(
                                        onClick = { navigator.onNavigate(Question(viewModel.questionId, displayTitle)) },
                                        modifier = Modifier.testTag("article_question"),
                                    ) {
                                        Icon(
                                            Icons.Filled.QuestionAnswer,
                                            contentDescription = "查看问题",
                                            tint = skin.onSurfaceVariantActions,
                                        )
                                    }
                                }
                                AppIconButton(onClick = { showActionsMenu = true }) {
                                    Icon(
                                        Icons.Filled.MoreVert,
                                        contentDescription = "更多选项",
                                        tint = skin.onSurfaceVariantActions,
                                    )
                                }
                            },
                        )
                        if (displayTitle.isBlank() && awaitingDetail) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = AppDimens.PageHorizontalPadding, vertical = AppDimens.Space8),
                                verticalArrangement = Arrangement.spacedBy(AppDimens.Space8),
                            ) {
                                ArticleLoadingPlaceholder(Modifier.fillMaxWidth().height(24.dp))
                                ArticleLoadingPlaceholder(Modifier.fillMaxWidth(0.7f).height(24.dp))
                            }
                        }
                        AnimatedVisibility(visible = contentSearch.visible) {
                            ArticleContentSearchPanel(state = contentSearch)
                        }
                    }
                }
            },
            bottomBar = if (isImmersiveMode) {
                {}
            } else {
                @Composable {
                    @Composable
                    fun ActionBarContent() {
                        val tokens = LocalAppSkinTokens.current
                        val actionContentColor = if (settingSkinIsMiuix()) tokens.onSurface else tokens.onSurfaceVariantSummary
                        val ttsState = rememberArticleTtsState()
                        val toggleArticleSpeech = rememberArticleSpeechToggler()
                        Column(
                            modifier = Modifier
                                .padding(bottom = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding() + AppDimens.Space16)
                                .fillMaxWidth()
                                .padding(horizontal = AppDimens.PageHorizontalPadding),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            AnimatedVisibility(
                                visible = ttsState.isSpeaking,
                                modifier = Modifier.align(Alignment.End).padding(bottom = AppDimens.Space8),
                            ) {
                                AppFloatingToolbar {
                                    AppIconButton(
                                        onClick = {
                                            toggleArticleSpeech("", "")
                                            userMessages.showMessage("已停止朗读")
                                        },
                                        enabled = ttsState !in listOf(TtsState.Error, TtsState.Uninitialized, TtsState.Initializing),
                                        modifier = Modifier.size(AppDimens.TouchTarget).testTag("article_stop_tts"),
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.VolumeOff, contentDescription = "停止朗读")
                                    }
                                }
                            }
                            AppFloatingToolbar(modifier = Modifier.testTag("article_action_dock")) {
                                AppButton(
                                    onClick = {
                                        viewModel.toggleVoteUp(
                                            environment,
                                            if (viewModel.voteUpState == VoteUpState.Up) VoteUpState.Neutral else VoteUpState.Up,
                                        )
                                    },
                                    modifier = Modifier
                                        .widthIn(min = AppDimens.TouchTarget)
                                        .height(AppDimens.TouchTarget)
                                        .testTag("article_vote_up")
                                        .semantics {
                                            stateDescription = if (viewModel.voteUpState == VoteUpState.Up) "已赞同" else "未赞同"
                                        },
                                    containerColor = if (viewModel.voteUpState == VoteUpState.Up) tokens.primary else Color.Transparent,
                                    contentColor = if (viewModel.voteUpState == VoteUpState.Up) tokens.onPrimary else actionContentColor,
                                    contentPadding = PaddingValues(horizontal = AppDimens.Space8),
                                ) {
                                    MaterialIcon(
                                        painter = painterResource(Res.drawable.ic_vote_up_24dp),
                                        contentDescription = "赞同",
                                        modifier = Modifier.size(AppDimens.IconSizeMedium),
                                    )
                                    Spacer(Modifier.width(AppDimens.Space4))
                                    Text(formatCompactCount(viewModel.voteUpCount), style = tokens.footnote1, maxLines = 1)
                                }
                                AppTextButton(
                                    onClick = { showComments = true },
                                    modifier = Modifier
                                        .widthIn(min = AppDimens.TouchTarget)
                                        .height(AppDimens.TouchTarget)
                                        .testTag("article_comments"),
                                    contentColor = actionContentColor,
                                    contentPadding = PaddingValues(horizontal = AppDimens.Space8),
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Comment,
                                        contentDescription = "评论",
                                        modifier = Modifier.size(AppDimens.IconSizeMedium),
                                    )
                                    Spacer(Modifier.width(AppDimens.Space4))
                                    Text(formatCompactCount(viewModel.commentCount), style = tokens.footnote1, maxLines = 1)
                                }
                                AppIconButton(
                                    onClick = { showCollectionDialog = true },
                                    modifier = Modifier
                                        .size(AppDimens.TouchTarget)
                                        .testTag("article_collection")
                                        .semantics {
                                            stateDescription = if (viewModel.isFavorited) "已收藏" else "未收藏"
                                        },
                                    colors = IconButtonDefaults.iconButtonColors(
                                        contentColor = if (viewModel.isFavorited) tokens.primary else actionContentColor,
                                    ),
                                ) {
                                    Icon(
                                        if (viewModel.isFavorited) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                        contentDescription = "管理收藏",
                                        modifier = Modifier.size(AppDimens.IconSizeMedium),
                                    )
                                }
                                AppIconToggleButton(
                                    checked = viewModel.voteUpState == VoteUpState.Down,
                                    onCheckedChange = { checked ->
                                        viewModel.toggleVoteUp(environment, if (checked) VoteUpState.Down else VoteUpState.Neutral)
                                    },
                                    modifier = Modifier
                                        .size(AppDimens.TouchTarget)
                                        .testTag("article_vote_down")
                                        .semantics {
                                            stateDescription = if (viewModel.voteUpState == VoteUpState.Down) "已反对" else "未反对"
                                        },
                                    colors = IconButtonDefaults.iconToggleButtonColors(
                                        contentColor = actionContentColor,
                                        checkedContainerColor = tokens.primary,
                                        checkedContentColor = tokens.onPrimary,
                                    ),
                                ) {
                                    MaterialIcon(
                                        painter = painterResource(Res.drawable.ic_vote_down_24dp),
                                        contentDescription = "反对",
                                        modifier = Modifier.size(AppDimens.IconSizeMedium),
                                    )
                                }
                            }
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (bottomBarState.showSlot) {
                            Box(
                                contentAlignment = Alignment.TopCenter,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onSizeChanged { bottomBarState.heightPx = it.height.toFloat() }
                                    .graphicsLayer {
                                        translationY = bottomBarState.offset.value
                                        alpha = if (bottomBarState.heightPx > 0f) {
                                            1f - (bottomBarState.offset.value / bottomBarState.heightPx)
                                        } else {
                                            1f
                                        }
                                    },
                            ) {
                                ActionBarContent()
                            }
                        }
                        Spacer(modifier = Modifier.height(readingPlayerOverlayPadding))
                    }
                }
            },
        ) { innerPadding ->
            if (showWebViewLimits) {
                AppDialog(
                    modifier = Modifier.testTag("article_webview_limitations"),
                    title = "WebView 渲染已弃用",
                    summary = "当前模式不支持正文查找定位与高亮、正文内划线喜欢与句子评论交互，以及电纸书翻页。普通评论、朗读、分享和双击仍可使用。请在“阅读与排版”关闭 WebView 后使用完整功能。",
                    confirmText = "去设置",
                    onConfirm = {
                        showWebViewLimits = false
                        navigator.onNavigate(Account.ReadingSettings(ARTICLE_USE_WEBVIEW_PREFERENCE_KEY))
                    },
                    dismissText = "知道了",
                    onDismissRequest = { showWebViewLimits = false },
                )
            }
            ArticleActionsMenu(
                article = article,
                viewModel = viewModel,
                answerQueueFallbackProvider = sharedData?.navigator?.let { answerNavigator ->
                    { limit -> answerNavigator.remainingAnswersSnapshot(article.id, limit) }
                },
                showMenu = showActionsMenu,
                onDismissRequest = { showActionsMenu = false },
                onSummaryRequest = {
                    showSummaryDialog = true
                    viewModel.requestAiSummary(environment)
                },
                onAigcFlagRequest = {
                    showAigcFlagSheet = true
                    viewModel.loadAigcFlagStatus(environment)
                },
                onExportRequest = { showExportDialog = true },
                onSetImmersiveDoubleTap = {
                    showActionsMenu = false
                    isImmersiveMode = !isImmersiveMode
                    userMessages.showMessage("已进入沉浸式，按返回键即可退出")
                },
            )

            CompositionLocalProvider(
                LocalBringIntoViewSpec provides articleBringIntoViewSpec,
                LocalMarkdownWidthManagedByHost provides true,
            ) {
                Box(contentAlignment = Alignment.TopCenter) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AppDimens.PageHorizontalPadding)
                            .verticalScroll(scrollState)
                            .padding(innerPadding)
                            .padding(top = 4.dp),
                    ) {
                        if (showContentTitle) {
                            Text(
                                text = displayTitle,
                                style = skin.title2,
                                fontWeight = FontWeight.SemiBold,
                                color = skin.onSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = AppDimens.Space12)
                                    .testTag(ARTICLE_FULL_TITLE_TAG)
                                    .semantics { heading() }
                                    .then(
                                        if (article.type == ArticleType.Answer && viewModel.questionId > 0) {
                                            Modifier.clickable {
                                                navigator.onNavigate(Question(viewModel.questionId, displayTitle))
                                            }
                                        } else {
                                            Modifier
                                        },
                                    ),
                            )
                        }
                        if (viewModel.authorName.isNotBlank() || awaitingDetail) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = AppDimens.Space8),
                            ) {
                                ArticleAuthorIdentity(
                                    viewModel = viewModel,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        navigator.onNavigate(
                                            com.github.zly2006.zhihu.navigation.Person(
                                                id = viewModel.authorId,
                                                urlToken = viewModel.authorUrlToken,
                                                name = viewModel.authorName,
                                            ),
                                        )
                                    },
                                )
                                if (viewModel.canFollowAuthor) {
                                    ArticleAuthorFollowButton(
                                        following = viewModel.authorIsFollowing,
                                        changing = viewModel.authorFollowChanging,
                                        onClick = { viewModel.toggleAuthorFollow(environment) },
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(AppDimens.Space8))
                        }
                        if (article.type == ArticleType.Answer) {
                            viewModel.ipInfo?.takeIf { it.isNotBlank() }?.let { ipInfo ->
                                Text(
                                    text = "IP属地：$ipInfo",
                                    modifier = Modifier.testTag(ARTICLE_IP_INFO_TAG),
                                    color = skin.onSurfaceVariantSummary,
                                    style = skin.footnote2,
                                )
                                Spacer(modifier = Modifier.height(AppDimens.Space8))
                            }
                        }

                        if (!hasBody) {
                            if (viewModel.previewContent.isNotBlank() && !legacyWebViewActive) {
                                RenderMarkdown(
                                    html = viewModel.previewContent,
                                    sourceHtml = null,
                                    modifier = Modifier.testTag("article_preview").articleMarkdownSelectionWorkaround(),
                                    scrollState = scrollState,
                                    selectable = true,
                                    enableScroll = false,
                                    useTiqianRenderer = useTiqianMarkdown,
                                )
                                Spacer(Modifier.height(AppDimens.Space16))
                            } else if (viewModel.previewExcerpt.isNotBlank()) {
                                Text(
                                    text = parseEmphasizedHtmlTextWithTheme(viewModel.previewExcerpt),
                                    style = skin.main,
                                    color = skin.onSurface,
                                    modifier = Modifier.fillMaxWidth().testTag("article_preview"),
                                )
                                Spacer(Modifier.height(AppDimens.Space16))
                            }
                            if (awaitingDetail) {
                                Column(Modifier.fillMaxWidth().padding(vertical = AppDimens.Space16).testTag(ARTICLE_LOADING_TAG)) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().testTag("article_body_skeleton").semantics {
                                            contentDescription = "正在加载全文"
                                            progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                                        },
                                        verticalArrangement = Arrangement.spacedBy(AppDimens.Space24),
                                    ) {
                                        repeat(2) {
                                            Column(verticalArrangement = Arrangement.spacedBy(AppDimens.Space12)) {
                                                repeat(3) { ArticleLoadingPlaceholder(Modifier.fillMaxWidth().height(16.dp)) }
                                                ArticleLoadingPlaceholder(Modifier.fillMaxWidth(0.6f).height(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (viewModel.loadError != null) {
                            if (hasBody) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = AppDimens.Space12)
                                        .testTag(ARTICLE_LOAD_ERROR_TAG),
                                ) {
                                    Text(
                                        text = "更新失败，仍可阅读已加载内容",
                                        style = skin.footnote1,
                                        color = skin.onSurfaceVariantSummary,
                                    )
                                    AppTextButton(
                                        text = "重试",
                                        onClick = { viewModel.loadArticle(environment) },
                                        enabled = !viewModel.isLoading,
                                        modifier = Modifier.testTag(ARTICLE_LOAD_RETRY_TAG),
                                    )
                                }
                            } else {
                                AppEmptyState(
                                    title = "内容加载失败",
                                    description = viewModel.loadError,
                                    modifier = Modifier.testTag(ARTICLE_LOAD_ERROR_TAG),
                                    action = {
                                        AppButton(
                                            text = "重试",
                                            onClick = { viewModel.loadArticle(environment) },
                                            enabled = !viewModel.isLoading,
                                            modifier = Modifier.testTag(ARTICLE_LOAD_RETRY_TAG),
                                        )
                                    },
                                )
                            }
                        } else if (!hasBody && !viewModel.isLoading && viewModel.hasLoaded) {
                            AppEmptyState(
                                title = "暂无正文",
                                description = "当前内容没有可显示的正文",
                                modifier = Modifier.testTag(ARTICLE_EMPTY_TAG),
                            )
                        }

                        @Suppress("UnusedReceiverParameter")
                        @Composable
                        fun ColumnScope.DateTexts() {
                            if (viewModel.createdAt > 0) {
                                Text(
                                    "发布于 " + formatFullDateTime(viewModel.createdAt),
                                    color = skin.onSurfaceVariantSummary,
                                    style = skin.footnote2,
                                )
                            }
                            if (viewModel.updatedAt > 0 && viewModel.createdAt != viewModel.updatedAt) {
                                Text(
                                    "编辑于 " + formatFullDateTime(viewModel.updatedAt),
                                    color = skin.onSurfaceVariantSummary,
                                    style = skin.footnote2,
                                )
                            }
                        }

                        @Composable
                        fun ColumnScope.ArticleVotersSocialCredit() {
                            val contentLabel = when (article.type) {
                                ArticleType.Answer -> "回答"
                                ArticleType.Article -> "文章"
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier.fillMaxWidth().height(
                                    with(density) {
                                        MaterialTheme.typography.bodySmall.lineHeight
                                            .toDp()
                                    },
                                ),
                            ) {
                                val text = viewModel.votersSocialText.ifBlank {
                                    "${formatCompactCount(viewModel.votersTotal)} 人赞同了该$contentLabel"
                                }
                                val votersTextModifier = if (article.type == ArticleType.Answer) {
                                    Modifier.clickable {
                                        showVoters = true
                                        if (viewModel.voters.isEmpty()) {
                                            viewModel.loadMoreVoters(environment, reset = true)
                                        }
                                    }
                                } else {
                                    Modifier
                                }
                                Text(
                                    text = text,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = votersTextModifier,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (viewModel.aigcSupportVoterCount > 0) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "有 ${formatCompactCount(viewModel.aigcSupportVoterCount)} 人认为此${contentLabel}包含AIGC内容",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }

                        if (hasBody) {
                            if (article.type == ArticleType.Article && viewModel.topics.isNotEmpty()) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    viewModel.topics.forEach { topic ->
                                        AppChip(
                                            label = "# ${topic.name}",
                                            selected = false,
                                            onClick = { navigator.onNavigate(Topic(topic.id, topic.name)) },
                                        )
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                            }
                            val endorsements = viewModel.endorsements
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalAlignment = Alignment.Start,
                            ) {
                                if (pinAnswerDate) {
                                    DateTexts()
                                }
                                ArticleVotersSocialCredit()
                                if (endorsements.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        endorsements.forEach { endorsement ->
                                            AnswerEndorsementChip(endorsement)
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            if (legacyWebViewActive) {
                                AppTextButton(
                                    text = "WebView 已弃用 · 查看功能限制",
                                    onClick = { showWebViewLimits = true },
                                    modifier = Modifier.testTag("article_webview_notice"),
                                )
                                ArticleWebViewContent(
                                    article = article,
                                    html = viewModel.content,
                                    title = viewModel.title,
                                    scrollState = scrollState,
                                    rememberedScrollY = viewModel.rememberedScrollY,
                                    rememberedScrollYSync = viewModel.rememberedScrollYSync,
                                    onRememberedScrollYSyncChange = { viewModel.rememberedScrollYSync = it },
                                    onImageLoadFailed = { userMessages.showMessage("图片加载失败，请向开发者反馈") },
                                    onDoubleTap = ::handleAnswerDoubleTap,
                                )
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.End,
                                ) {
                                    if (!pinAnswerDate) {
                                        DateTexts()
                                    }
                                    if (article.type == ArticleType.Article && viewModel.ipInfo != null) {
                                        Text(
                                            "IP属地：${viewModel.ipInfo}",
                                            color = skin.onSurfaceVariantSummary,
                                            style = skin.footnote2,
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height((16 + 36).dp))
                            } else {
                                CompositionLocalProvider(
                                    LocalSegmentSelectionTarget provides SegmentSelectionTarget(
                                        contentId = article.id.toString(),
                                        contentType = when (article.type) {
                                            ArticleType.Answer -> "answer"
                                            ArticleType.Article -> "article"
                                        },
                                    ),
                                    LocalOnRequestContentRefresh provides {
                                        viewModel.loadArticle(environment)
                                    },
                                ) {
                                    RenderMarkdown(
                                        html = viewModel.content,
                                        sourceHtml = viewModel.sourceContent,
                                        segmentCommentSnapshot = viewModel.segmentCommentSnapshot,
                                        modifier = Modifier
                                            .testTag("article_content")
                                            .articleMarkdownSelectionWorkaround(),
                                        scrollState = scrollState,
                                        selectable = true,
                                        enableScroll = false,
                                        useTiqianRenderer = useTiqianMarkdown,
                                        searchState = markdownSearch.takeIf { contentSearch.visible },
                                        header = {},
                                        footer = {
                                            ArticleVideoAttachmentContent(viewModel.attachment)
                                            Column(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalAlignment = Alignment.Start,
                                            ) {
                                                if (!pinAnswerDate || (article.type == ArticleType.Article && viewModel.ipInfo != null)) {
                                                    HorizontalDivider(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(vertical = AppDimens.Space12),
                                                        color = MaterialTheme.colorScheme.outlineVariant,
                                                    )
                                                }
                                                if (!pinAnswerDate) {
                                                    DateTexts()
                                                }
                                                if (article.type == ArticleType.Article && viewModel.ipInfo != null) {
                                                    Text(
                                                        "IP属地：${viewModel.ipInfo}",
                                                        color = skin.onSurfaceVariantSummary,
                                                        style = skin.footnote2,
                                                    )
                                                }
                                                if (accountStore.session.login && viewModel.paidContentActionVisible) {
                                                    AppTextButton(
                                                        text = if (viewModel.isLoadingFullContent) "加载中..." else "加载完整内容",
                                                        onClick = { viewModel.loadFullContent(environment) },
                                                        enabled = !viewModel.isLoadingFullContent,
                                                    )
                                                }
                                            }
                                            ContentEndMarker()
                                            Spacer(modifier = Modifier.height((16 + 36).dp))
                                        },
                                    )
                                }
                            }
                        }
                    }
                    Spacer(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .pageTurnViewportWithGuide(pageTurnTarget),
                    )
                    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                    val surfaceColor = MaterialTheme.colorScheme.surfaceContainer
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(statusBarHeight + 16.dp)
                            .background(
                                Brush.verticalGradient(smoothGradient(surfaceColor, 0.8f)),
                            ),
                    ) {}
                }
            }
        }
    }

    val nav = answerNavigationState.answerNavigator
    val progressBarTopPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 64.dp
    val progressBarBottomPadding = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding() + 96.dp

    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter,
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
                val isAtTop by remember(scrollState) {
                    derivedStateOf { scrollState.value == 0 }
                }
                val showSkipButton = !autoHideSkipAnswerButton || bottomBarState.isScrollingUp || isAtTop
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
                            Icon(Icons.Filled.SkipNext, contentDescription = "下一个回答")
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
        title = "${formatCompactCount(viewModel.votersTotal)} 人赞同了该回答",
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
        MyModalBottomSheet(
            onDismissRequest = { showDoubleTapActionDialog = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "设置双击回答动作",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "选择以后双击回答时默认执行的动作。选择后会立即保存到设置，你也可以稍后在设置中修改。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AppButton(
                    text = "设为无操作",
                    onClick = {
                        showDoubleTapActionDialog = false
                        saveAnswerDoubleTapAction(AnswerDoubleTapAction.None)
                        userMessages.showMessage("已将双击回答动作设为：${AnswerDoubleTapAction.None.label}")
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                AppButton(
                    text = "设为点赞",
                    onClick = {
                        showDoubleTapActionDialog = false
                        saveAnswerDoubleTapAction(AnswerDoubleTapAction.VoteUp)
                        upVoteFromDoubleTap()
                        userMessages.showMessage("已将双击回答动作设为：${AnswerDoubleTapAction.VoteUp.label}")
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                AppButton(
                    text = "设为打开评论区",
                    onClick = {
                        showDoubleTapActionDialog = false
                        saveAnswerDoubleTapAction(AnswerDoubleTapAction.OpenComments)
                        showComments = true
                        userMessages.showMessage("已将双击回答动作设为：${AnswerDoubleTapAction.OpenComments.label}")
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                AppButton(
                    text = "设为开关沉浸式",
                    onClick = {
                        showDoubleTapActionDialog = false
                        saveAnswerDoubleTapAction(AnswerDoubleTapAction.ToggleImmersive)
                        isImmersiveMode = !isImmersiveMode
                        userMessages.showMessage("已将双击回答动作设为：${AnswerDoubleTapAction.ToggleImmersive.label}")
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    listOf(AnswerDoubleTapAction.JumpToTop, AnswerDoubleTapAction.JumpToBottom).forEach { action ->
                        AppButton(
                            text = action.label,
                            onClick = {
                                showDoubleTapActionDialog = false
                                saveAnswerDoubleTapAction(action)
                                performAnswerDoubleTapAction(action)
                                userMessages.showMessage("已将双击回答动作设为：${action.label}")
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
    ExportDialogComponent(
        showDialog = showExportDialog,
        isHtmlExportSupported = isArticleHtmlExportSupported,
        isImageExportSupported = isArticleImageExportSupported,
        onDismiss = { showExportDialog = false },
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

@Composable
private fun ArticleAuthorIdentity(
    viewModel: ArticleViewModel,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val skin = LocalAppSkinTokens.current
    val awaitingDetail = viewModel.isLoading || (!viewModel.hasLoaded && viewModel.loadError == null)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.clickable(
            enabled = viewModel.authorId.isNotBlank() || viewModel.authorUrlToken.isNotBlank(),
            onClick = onClick,
        ),
    ) {
        if (viewModel.authorAvatarSrc.isNotEmpty()) {
            AsyncImage(
                model = viewModel.authorAvatarSrc,
                contentDescription = "作者头像",
                modifier = Modifier
                    .size(AppDimens.Space40)
                    .clip(CircleShape),
            )
        } else if (awaitingDetail) {
            ArticleLoadingPlaceholder(Modifier.size(AppDimens.Space40).clip(CircleShape))
        } else {
            Box(
                modifier = Modifier
                    .size(AppDimens.Space40)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            )
        }
        Spacer(modifier = Modifier.width(AppDimens.Space8))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (viewModel.authorName.isBlank() && awaitingDetail) {
                    ArticleLoadingPlaceholder(Modifier.width(96.dp).height(24.dp))
                } else {
                    Text(
                        text = viewModel.authorName,
                        style = skin.title4,
                        color = skin.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                if (viewModel.authorBadge != null) {
                    Spacer(modifier = Modifier.width(4.dp))
                    AuthorBadge(
                        badge = viewModel.authorBadge,
                        compact = false,
                    )
                }
            }
            if (viewModel.authorBio.isBlank() && awaitingDetail) {
                ArticleLoadingPlaceholder(Modifier.fillMaxWidth(0.65f).height(16.dp).padding(vertical = 4.dp).testTag("article_author_bio_skeleton"))
            } else if (viewModel.authorBio.isNotEmpty()) {
                Text(
                    text = viewModel.authorBio,
                    style = skin.main,
                    color = skin.onSurfaceVariantSummary,
                )
            }
        }
    }
}

@Composable
private fun ArticleAuthorFollowButton(
    following: Boolean,
    changing: Boolean,
    onClick: () -> Unit,
) {
    val skin = LocalAppSkinTokens.current
    AppTextButton(
        onClick = onClick,
        enabled = !changing,
        contentPadding = PaddingValues(horizontal = AppDimens.Space8, vertical = AppDimens.ElevationNone),
        modifier = Modifier.testTag(ARTICLE_AUTHOR_FOLLOW_TAG),
    ) {
        Text(
            text = if (following) "已关注" else "关注",
            style = skin.footnote1,
            maxLines = 1,
        )
    }
}

@Composable
private fun ArticleContentSearchPanel(state: ArticleContentSearchState) {
    val skin = LocalAppSkinTokens.current
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(state.visible) {
        if (state.visible) focusRequester.requestFocus()
    }
    Box(
        modifier = Modifier
            .widthIn(max = AppDimens.ArticleReadingMaxWidth)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = AppDimens.PageHorizontalPadding, vertical = AppDimens.Space8)
            .testTag("article_search_panel"),
    ) {
        Column(Modifier.padding(horizontal = AppDimens.Space12, vertical = AppDimens.Space8)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppSearchField(
                    value = state.query,
                    onValueChange = state::update,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .testTag("article_search_field"),
                    placeholder = "搜索正文",
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        state.retry()
                    }),
                )
                Text(
                    text = if (state.matchCount == 0) "0/0" else "${state.current + 1}/${state.matchCount}",
                    style = skin.footnote1,
                    color = skin.onSurfaceVariantSummary,
                    modifier = Modifier
                        .padding(horizontal = AppDimens.Space8)
                        .testTag("article_search_status"),
                )
                AppIconButton(
                    onClick = {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        state.previous()
                    },
                    enabled = state.matchCount > 0,
                    modifier = Modifier.testTag("article_search_previous"),
                ) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "上一处", tint = skin.onSurfaceVariantActions)
                }
                AppIconButton(
                    onClick = {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        state.next()
                    },
                    enabled = state.matchCount > 0,
                    modifier = Modifier.testTag("article_search_next"),
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "下一处", tint = skin.onSurfaceVariantActions)
                }
                AppIconButton(
                    onClick = {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        state.hide()
                    },
                    modifier = Modifier.testTag("article_search_close"),
                ) {
                    Icon(Icons.Default.Close, contentDescription = "关闭搜索", tint = skin.onSurfaceVariantActions)
                }
            }
            val status = when {
                state.locating -> "正在定位…"
                state.failed -> "未能定位此处，请重试"
                state.matchCount == 0 && state.query.isNotBlank() -> "未找到匹配内容"
                else -> null
            }
            Text(
                text = status.orEmpty(),
                style = skin.footnote2,
                color = if (state.failed) skin.error else skin.onSurfaceVariantSummary,
                modifier = Modifier
                    .padding(top = AppDimens.Space4)
                    .clickable(enabled = state.failed, onClick = state::retry)
                    .testTag("article_search_location_status"),
            )
        }
    }
}
