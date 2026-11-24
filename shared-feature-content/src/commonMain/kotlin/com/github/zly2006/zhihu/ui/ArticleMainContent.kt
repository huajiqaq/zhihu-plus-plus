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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.zly2006.zhihu.account.ZhihuAccountStore
import com.github.zly2006.zhihu.markdown.LocalMarkdownWidthManagedByHost
import com.github.zly2006.zhihu.markdown.MarkdownSearchState
import com.github.zly2006.zhihu.markdown.RenderMarkdown
import com.github.zly2006.zhihu.navigation.Account
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.Navigator
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.platform.UserMessageSink
import com.github.zly2006.zhihu.theme.AppSkinTokens
import com.github.zly2006.zhihu.ui.article.ArticleActionsMenu
import com.github.zly2006.zhihu.ui.article.ArticleBottomBarState
import com.github.zly2006.zhihu.ui.article.ArticleContentSearchState
import com.github.zly2006.zhihu.ui.article.ArticleTopBarState
import com.github.zly2006.zhihu.ui.article.ArticleVideoAttachmentContent
import com.github.zly2006.zhihu.ui.components.AppButton
import com.github.zly2006.zhihu.ui.components.AppDialog
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppEmptyState
import com.github.zly2006.zhihu.ui.components.AppIconButton
import com.github.zly2006.zhihu.ui.components.AppPageTitleBar
import com.github.zly2006.zhihu.ui.components.AppPageTitleBarScrollState
import com.github.zly2006.zhihu.ui.components.AppScaffold
import com.github.zly2006.zhihu.ui.components.AppSpinner
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.ui.components.ContentEndMarker
import com.github.zly2006.zhihu.ui.components.LocalOnRequestContentRefresh
import com.github.zly2006.zhihu.ui.components.LocalSegmentSelectionTarget
import com.github.zly2006.zhihu.ui.components.PageTurnTarget
import com.github.zly2006.zhihu.ui.components.SegmentSelectionTarget
import com.github.zly2006.zhihu.ui.components.appPageTitleBarScroll
import com.github.zly2006.zhihu.ui.components.pageTurnViewportWithGuide
import com.github.zly2006.zhihu.util.smoothGradient
import com.github.zly2006.zhihu.viewmodel.ArticleAnswerSwitchData
import com.github.zly2006.zhihu.viewmodel.ArticleViewModel
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment
import com.github.zly2006.zhihu.ui.components.AppIcon as Icon

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)
@Composable
internal fun ArticleMainContent(
    article: Article,
    viewModel: ArticleViewModel,
    navigator: Navigator,
    readingPlayerOverlayPadding: Dp,
    environment: PaginationEnvironment,
    accountStore: ZhihuAccountStore,
    skin: AppSkinTokens,
    displayTitle: String,
    hasBody: Boolean,
    awaitingDetail: Boolean,
    scrollState: ScrollState,
    pinAnswerDate: Boolean,
    legacyWebViewActive: Boolean,
    useTiqianMarkdown: Boolean,
    userMessages: UserMessageSink,
    onShowComments: () -> Unit,
    onShowCollectionDialog: () -> Unit,
    showActionsMenu: Boolean,
    onActionsMenuVisibilityChange: (Boolean) -> Unit,
    showWebViewLimits: Boolean,
    onWebViewLimitsVisibilityChange: (Boolean) -> Unit,
    onShowSummaryDialog: () -> Unit,
    onShowAigcFlagSheet: () -> Unit,
    onShowExportDialog: () -> Unit,
    onShowVoters: () -> Unit,
    markdownSearch: MarkdownSearchState,
    contentSearch: ArticleContentSearchState,
    topBarState: ArticleTopBarState,
    bottomBarState: ArticleBottomBarState,
    sharedData: ArticleAnswerSwitchData?,
    isImmersiveMode: Boolean,
    onToggleImmersiveMode: () -> Unit,
    scrollBehavior: AppPageTitleBarScrollState,
    pageTurnTarget: PageTurnTarget,
    articleBringIntoViewSpec: BringIntoViewSpec,
    onRememberedScrollYSyncChange: (Boolean) -> Unit,
    onAnswerDoubleTap: () -> Unit,
) {
    val showContentTitle = isImmersiveMode && displayTitle.isNotBlank()
    AppScaffold(
        modifier = Modifier
            .fillMaxSize()
            .then(if (!isImmersiveMode) Modifier.appPageTitleBarScroll(scrollBehavior) else Modifier),
        topBar = if (isImmersiveMode) {
            {}
        } else {
            {
                ArticleMainTopBar(
                    article = article,
                    viewModel = viewModel,
                    navigator = navigator,
                    skin = skin,
                    displayTitle = displayTitle,
                    legacyWebViewActive = legacyWebViewActive,
                    contentSearch = contentSearch,
                    topBarState = topBarState,
                    scrollBehavior = scrollBehavior,
                    onWebViewLimitsVisibilityChange = onWebViewLimitsVisibilityChange,
                    onActionsMenuVisibilityChange = onActionsMenuVisibilityChange,
                )
            }
        },
        bottomBar = if (isImmersiveMode) {
            {}
        } else {
            @Composable {
                ArticleMainBottomBar(
                    viewModel = viewModel,
                    environment = environment,
                    userMessages = userMessages,
                    bottomBarState = bottomBarState,
                    readingPlayerOverlayPadding = readingPlayerOverlayPadding,
                    onShowComments = onShowComments,
                    onShowCollectionDialog = onShowCollectionDialog,
                )
            }
        },
    ) { innerPadding ->
        if (showWebViewLimits) {
            ArticleWebViewLimitsDialog(
                navigator = navigator,
                onWebViewLimitsVisibilityChange = onWebViewLimitsVisibilityChange,
            )
        }
        ArticleMainActionsMenu(
            article = article,
            viewModel = viewModel,
            environment = environment,
            sharedData = sharedData,
            showActionsMenu = showActionsMenu,
            userMessages = userMessages,
            onActionsMenuVisibilityChange = onActionsMenuVisibilityChange,
            onShowSummaryDialog = onShowSummaryDialog,
            onShowAigcFlagSheet = onShowAigcFlagSheet,
            onShowExportDialog = onShowExportDialog,
            onToggleImmersiveMode = onToggleImmersiveMode,
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
                    ArticleMainHeader(
                        article = article,
                        viewModel = viewModel,
                        navigator = navigator,
                        environment = environment,
                        skin = skin,
                        displayTitle = displayTitle,
                        showContentTitle = showContentTitle,
                    )

                    ArticleMainLoadState(
                        viewModel = viewModel,
                        environment = environment,
                        skin = skin,
                        hasBody = hasBody,
                        awaitingDetail = awaitingDetail,
                    )

                    if (hasBody) {
                        ArticleMainMetadata(
                            article = article,
                            viewModel = viewModel,
                            navigator = navigator,
                            environment = environment,
                            pinAnswerDate = pinAnswerDate,
                            onShowVoters = onShowVoters,
                        )
                        if (legacyWebViewActive) {
                            ArticleMainWebView(
                                article = article,
                                viewModel = viewModel,
                                skin = skin,
                                scrollState = scrollState,
                                pinAnswerDate = pinAnswerDate,
                                userMessages = userMessages,
                                onWebViewLimitsVisibilityChange = onWebViewLimitsVisibilityChange,
                                onRememberedScrollYSyncChange = onRememberedScrollYSyncChange,
                                onAnswerDoubleTap = onAnswerDoubleTap,
                            )
                        } else {
                            ArticleMainMarkdown(
                                article = article,
                                viewModel = viewModel,
                                environment = environment,
                                accountStore = accountStore,
                                skin = skin,
                                scrollState = scrollState,
                                pinAnswerDate = pinAnswerDate,
                                useTiqianMarkdown = useTiqianMarkdown,
                                markdownSearch = markdownSearch,
                                contentSearch = contentSearch,
                            )
                        }
                    }
                }
                ArticleMainOverlays(
                    innerPadding = innerPadding,
                    pageTurnTarget = pageTurnTarget,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArticleMainTopBar(
    article: Article,
    viewModel: ArticleViewModel,
    navigator: Navigator,
    skin: AppSkinTokens,
    displayTitle: String,
    legacyWebViewActive: Boolean,
    contentSearch: ArticleContentSearchState,
    topBarState: ArticleTopBarState,
    scrollBehavior: AppPageTitleBarScrollState,
    onWebViewLimitsVisibilityChange: (Boolean) -> Unit,
    onActionsMenuVisibilityChange: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged {
                topBarState.heightPx = it.height.toFloat()
            }.graphicsLayer {
                val hidden = topBarState.hiddenFraction.value
                translationY = -size.height * hidden
                alpha = 1f - hidden
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
                        if (legacyWebViewActive) onWebViewLimitsVisibilityChange(true) else contentSearch.show()
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
                AppIconButton(onClick = { onActionsMenuVisibilityChange(true) }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "更多选项",
                        tint = skin.onSurfaceVariantActions,
                    )
                }
            },
        )
        AnimatedVisibility(visible = contentSearch.visible) {
            ArticleContentSearchPanel(state = contentSearch)
        }
    }
}

@Composable
private fun ArticleMainBottomBar(
    viewModel: ArticleViewModel,
    environment: PaginationEnvironment,
    userMessages: UserMessageSink,
    bottomBarState: ArticleBottomBarState,
    readingPlayerOverlayPadding: Dp,
    onShowComments: () -> Unit,
    onShowCollectionDialog: () -> Unit,
) {
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
                ArticleActionBar(
                    viewModel = viewModel,
                    environment = environment,
                    userMessages = userMessages,
                    onShowComments = onShowComments,
                    onShowCollectionDialog = onShowCollectionDialog,
                )
            }
        }
        Spacer(modifier = Modifier.height(readingPlayerOverlayPadding))
    }
}

@Composable
private fun ArticleWebViewLimitsDialog(
    navigator: Navigator,
    onWebViewLimitsVisibilityChange: (Boolean) -> Unit,
) {
    AppDialog(
        modifier = Modifier.testTag("article_webview_limitations"),
        title = "WebView 渲染已弃用",
        summary = "当前模式不支持正文查找定位与高亮、正文内划线喜欢与句子评论交互，以及电纸书翻页。普通评论、朗读、分享和双击仍可使用。请在“阅读与排版”关闭 WebView 后使用完整功能。",
        confirmText = "去设置",
        onConfirm = {
            onWebViewLimitsVisibilityChange(false)
            navigator.onNavigate(Account.ReadingSettings(ARTICLE_USE_WEBVIEW_PREFERENCE_KEY))
        },
        dismissText = "知道了",
        onDismissRequest = { onWebViewLimitsVisibilityChange(false) },
    )
}

@Composable
private fun ArticleMainActionsMenu(
    article: Article,
    viewModel: ArticleViewModel,
    environment: PaginationEnvironment,
    sharedData: ArticleAnswerSwitchData?,
    showActionsMenu: Boolean,
    userMessages: UserMessageSink,
    onActionsMenuVisibilityChange: (Boolean) -> Unit,
    onShowSummaryDialog: () -> Unit,
    onShowAigcFlagSheet: () -> Unit,
    onShowExportDialog: () -> Unit,
    onToggleImmersiveMode: () -> Unit,
) {
    ArticleActionsMenu(
        article = article,
        viewModel = viewModel,
        answerQueueFallbackProvider = sharedData?.navigator?.let { answerNavigator ->
            { limit -> answerNavigator.remainingAnswersSnapshot(article.id, limit) }
        },
        showMenu = showActionsMenu,
        onDismissRequest = { onActionsMenuVisibilityChange(false) },
        onSummaryRequest = {
            onShowSummaryDialog()
            viewModel.requestAiSummary(environment)
        },
        onAigcFlagRequest = {
            onShowAigcFlagSheet()
            viewModel.loadAigcFlagStatus(environment)
        },
        onExportRequest = onShowExportDialog,
        onSetImmersiveDoubleTap = {
            onActionsMenuVisibilityChange(false)
            onToggleImmersiveMode()
            userMessages.showMessage("已进入沉浸式，按返回键即可退出")
        },
    )
}

@Composable
private fun ArticleMainLoadState(
    viewModel: ArticleViewModel,
    environment: PaginationEnvironment,
    skin: AppSkinTokens,
    hasBody: Boolean,
    awaitingDetail: Boolean,
) {
    if (!hasBody && awaitingDetail) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = AppDimens.Space16)
                .testTag(ARTICLE_LOADING_TAG),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimens.Space12),
        ) {
            AppSpinner(size = AppDimens.Space24)
            Text(
                text = "正在加载内容…",
                style = skin.footnote1,
                color = skin.onSurfaceVariantSummary,
            )
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
}

@Composable
private fun ArticleMainWebView(
    article: Article,
    viewModel: ArticleViewModel,
    skin: AppSkinTokens,
    scrollState: ScrollState,
    pinAnswerDate: Boolean,
    userMessages: UserMessageSink,
    onWebViewLimitsVisibilityChange: (Boolean) -> Unit,
    onRememberedScrollYSyncChange: (Boolean) -> Unit,
    onAnswerDoubleTap: () -> Unit,
) {
    AppTextButton(
        text = "WebView 已弃用 · 查看功能限制",
        onClick = { onWebViewLimitsVisibilityChange(true) },
        modifier = Modifier.testTag("article_webview_notice"),
    )
    ArticleWebViewContent(
        article = article,
        html = viewModel.content,
        title = viewModel.title,
        scrollState = scrollState,
        rememberedScrollY = viewModel.rememberedScrollY,
        rememberedScrollYSync = viewModel.rememberedScrollYSync,
        onRememberedScrollYSyncChange = onRememberedScrollYSyncChange,
        onImageLoadFailed = { userMessages.showMessage("图片加载失败，请向开发者反馈") },
        onDoubleTap = onAnswerDoubleTap,
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
    ) {
        if (!pinAnswerDate) {
            ArticleDateTexts(viewModel)
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
}

@Composable
private fun ArticleMainMarkdown(
    article: Article,
    viewModel: ArticleViewModel,
    environment: PaginationEnvironment,
    accountStore: ZhihuAccountStore,
    skin: AppSkinTokens,
    scrollState: ScrollState,
    pinAnswerDate: Boolean,
    useTiqianMarkdown: Boolean,
    markdownSearch: MarkdownSearchState,
    contentSearch: ArticleContentSearchState,
) {
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
                        ArticleDateTexts(viewModel)
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

@Composable
private fun ArticleMainOverlays(
    innerPadding: PaddingValues,
    pageTurnTarget: PageTurnTarget,
) {
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
