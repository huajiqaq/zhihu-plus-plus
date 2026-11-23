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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.github.zly2006.zhihu.data.DailyStory
import com.github.zly2006.zhihu.data.fetchDailyStoryDestination
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.platform.isLiteVariant
import com.github.zly2006.zhihu.platform.rememberPlainTextClipboard
import com.github.zly2006.zhihu.platform.rememberWebViewUrlOpener
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.ui.components.AppAlertDialog
import com.github.zly2006.zhihu.ui.components.AppCard
import com.github.zly2006.zhihu.ui.components.AppDialog
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppEmptyState
import com.github.zly2006.zhihu.ui.components.AppIconButton
import com.github.zly2006.zhihu.ui.components.AppPageTitleBar
import com.github.zly2006.zhihu.ui.components.AppPullToRefreshBox
import com.github.zly2006.zhihu.ui.components.AppScaffold
import com.github.zly2006.zhihu.ui.components.AppSpinner
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.ui.components.SettingItem
import com.github.zly2006.zhihu.ui.components.appPageTitleBarScroll
import com.github.zly2006.zhihu.ui.components.pageTurnViewportWithGuide
import com.github.zly2006.zhihu.ui.components.rememberAppPageTitleBarScrollState
import com.github.zly2006.zhihu.ui.components.rememberPageTurnTarget
import com.github.zly2006.zhihu.ui.components.settingSkinIsMiuix
import com.github.zly2006.zhihu.util.formatDailyDate
import com.github.zly2006.zhihu.util.twoDigitString
import com.github.zly2006.zhihu.viewmodel.DailyViewModel
import com.github.zly2006.zhihu.viewmodel.rememberPaginationEnvironment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import com.github.zly2006.zhihu.ui.components.AppIcon as Icon

@OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class)
@Composable
fun DailyScreen(
    scrollToTopTrigger: Int = 0,
    isActive: Boolean = true,
    innerPadding: PaddingValues = PaddingValues(AppDimens.ElevationNone),
) {
    val navigator = LocalNavigator.current
    val httpClient = rememberPaginationEnvironment(allowGuestAccess = false).httpClient()
    val viewModel = viewModel { DailyViewModel() }
    var showDatePicker by remember { mutableStateOf(false) }
    var missingOriginStoryUrl by remember { mutableStateOf<String?>(null) }
    var pendingDateSelection by remember { mutableStateOf<String?>(null) }
    var openingStory by remember { mutableStateOf<DailyStory?>(null) }
    var failedStory by remember { mutableStateOf<DailyStory?>(null) }
    var storyOpenError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val overlayPadding = LocalReadingPlayerOverlayPadding.current
    val listState = rememberLazyListState()
    val titleBarScrollState = rememberAppPageTitleBarScrollState()
    val skin = LocalAppSkinTokens.current
    val pageBackground = if (settingSkinIsMiuix()) skin.surface else skin.background
    val pageTurnTarget = rememberPageTurnTarget(
        listState = listState,
        enabled = isActive && (isLiteVariant || !showDatePicker) && missingOriginStoryUrl == null && openingStory == null,
    )
    var cachedScrollToTopTrigger by remember { mutableIntStateOf(scrollToTopTrigger) }
    val visibleDailyDate = remember {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo
                .asSequence()
                .mapNotNull { info ->
                    val key = info.key as? String ?: return@mapNotNull null
                    when {
                        key.startsWith("group_") -> key.removePrefix("group_")
                        key.startsWith("header_") -> key.removePrefix("header_")
                        else -> null
                    }
                }.firstOrNull()
        }
    }
    val displayedDailyDate = (visibleDailyDate.value ?: viewModel.sections.firstOrNull()?.date)
        ?.let(::formatDailyDate)
        ?: "知乎日报"

    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: 0) to info.totalItemsCount
        }.collect { (last, total) ->
            if (total > 0 && last >= total - 3) {
                viewModel.loadMore(httpClient)
            }
        }
    }

    LaunchedEffect(Unit) {
        if (viewModel.sections.isEmpty()) {
            viewModel.loadLatest(httpClient)
        }
    }

    LaunchedEffect(showDatePicker, pendingDateSelection) {
        if (showDatePicker) return@LaunchedEffect
        val selectedDate = pendingDateSelection ?: return@LaunchedEffect
        /** 平台日期弹窗关闭后等一帧归还焦点与测量，再替换页面。 */
        withFrameNanos { }
        viewModel.loadDate(httpClient, selectedDate)
        if (viewModel.error == null) listState.scrollToItem(0)
        if (pendingDateSelection == selectedDate) {
            pendingDateSelection = null
        }
    }

    val doRefresh: () -> Unit = {
        scope.launch {
            if (viewModel.isLoading) return@launch
            viewModel.loadLatest(httpClient)
            if (viewModel.error == null) listState.scrollToItem(0)
        }
    }

    LaunchedEffect(scrollToTopTrigger, isActive) {
        val action = topLevelReselectAction(
            triggerDelta = scrollToTopTrigger - cachedScrollToTopTrigger,
            isAtTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0,
        )
        if (isActive) {
            when (action) {
                TopLevelReselectAction.Refresh -> doRefresh()
                TopLevelReselectAction.ScrollToTop -> listState.animateScrollToItem(0)
                null -> {}
            }
        }
        cachedScrollToTopTrigger = scrollToTopTrigger
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = Clock.System.now().toEpochMilliseconds(),
        )
        val confirmDateSelection: () -> Unit = {
            showDatePicker = false
            datePickerState.selectedDateMillis?.let { millis ->
                pendingDateSelection = formatDailyDatePickerSelection(millis)
            }
        }
        if (settingSkinIsMiuix()) {
            AppAlertDialog(
                onDismissRequest = { showDatePicker = false },
                title = { Text("选择日期") },
                text = { DatePicker(state = datePickerState) },
                confirmButton = {
                    AppTextButton(text = "确认", onClick = confirmDateSelection)
                },
                dismissButton = {
                    AppTextButton(text = "取消", onClick = { showDatePicker = false })
                },
            )
        } else {
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    AppTextButton(text = "确认", onClick = confirmDateSelection)
                },
                dismissButton = {
                    AppTextButton(
                        text = "取消",
                        onClick = { showDatePicker = false },
                    )
                },
            ) {
                DatePicker(state = datePickerState)
            }
        }
    }

    missingOriginStoryUrl?.let { storyUrl ->
        val openWebPage = rememberWebViewUrlOpener()
        val copyLink = rememberPlainTextClipboard()
        AppDialog(
            onDismissRequest = { missingOriginStoryUrl = null },
            title = "知乎日报的 bug",
            summary = "这是知乎日报的 bug：日报可能未提供文章等其他类型内容的原始链接，导致无法找到原文。建议手动复制链接打开。",
            confirmText = "打开日报网页",
            onConfirm = {
                missingOriginStoryUrl = null
                openWebPage(storyUrl)
            },
            dismissText = "复制日报链接",
            onDismiss = {
                copyLink("日报链接", storyUrl)
                missingOriginStoryUrl = null
            },
        )
    }

    LaunchedEffect(openingStory) {
        val story = openingStory ?: return@LaunchedEffect
        try {
            val destination = withContext(Dispatchers.Default) {
                httpClient.fetchDailyStoryDestination(story)
            }
            if (destination != null) {
                navigator.onNavigate(destination)
            } else {
                missingOriginStoryUrl = story.url
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failedStory = story
            storyOpenError = "原文加载失败：${e.message}"
        } finally {
            if (openingStory?.id == story.id) openingStory = null
        }
    }

    AppScaffold(
        containerColor = pageBackground,
        topBar = {
            AppPageTitleBar(
                title = "知乎日报",
                subtitle = displayedDailyDate.takeUnless { it == "知乎日报" },
                titleTag = DAILY_SCREEN_TITLE_TAG,
                scrollState = titleBarScrollState,
                colors = if (settingSkinIsMiuix()) {
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = pageBackground,
                        scrolledContainerColor = pageBackground,
                    )
                } else {
                    null
                },
                actions = {
                    AppIconButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.testTag(DAILY_SCREEN_DATE_PICKER_BUTTON_TAG),
                    ) {
                        Icon(Icons.Filled.DateRange, contentDescription = "选择日期")
                    }
                },
            )
        },
    ) { scaffoldPadding ->
        /** 底栏与朗读条覆盖列表，留白放进 contentPadding 以便末卡滚出遮挡。 */
        val bottomInset = maxOf(scaffoldPadding.calculateBottomPadding(), innerPadding.calculateBottomPadding()) + overlayPadding
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(pageBackground),
        ) {
            AppPullToRefreshBox(
                isRefreshing = viewModel.isLoading && viewModel.sections.isNotEmpty(),
                onRefresh = doRefresh,
                contentPadding = PaddingValues(top = scaffoldPadding.calculateTopPadding()),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                when {
                    viewModel.isLoading && viewModel.sections.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = scaffoldPadding.calculateTopPadding(), bottom = bottomInset)
                                .testTag(DAILY_SCREEN_LOADING_TAG),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                AppSpinner()
                                Spacer(modifier = Modifier.height(AppDimens.Space16))
                                Text(
                                    "正在加载...",
                                    style = skin.body2,
                                    color = skin.onSurfaceVariantSummary,
                                )
                            }
                        }
                    }

                    viewModel.error != null && viewModel.sections.isEmpty() -> {
                        AppEmptyState(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = scaffoldPadding.calculateTopPadding(), bottom = bottomInset)
                                .testTag(DAILY_SCREEN_ERROR_TAG),
                            icon = Icons.Default.Refresh,
                            title = "日报加载失败",
                            description = viewModel.error,
                            action = {
                                AppTextButton(
                                    text = "重试",
                                    onClick = { scope.launch { viewModel.retry(httpClient) } },
                                )
                            },
                        )
                    }

                    viewModel.sections.isEmpty() -> {
                        AppEmptyState(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = scaffoldPadding.calculateTopPadding(), bottom = bottomInset)
                                .testTag(DAILY_SCREEN_EMPTY_TAG),
                            icon = Icons.Default.DateRange,
                            title = "这天没有日报",
                            description = "点击右上角选择其他日期，或查看今天的日报。",
                            action = {
                                AppTextButton(text = "查看今天", onClick = doRefresh)
                            },
                        )
                    }

                    else -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .appPageTitleBarScroll(titleBarScrollState)
                                .pageTurnViewportWithGuide(pageTurnTarget)
                                .testTag(DAILY_SCREEN_LIST_TAG),
                            contentPadding = PaddingValues(
                                top = scaffoldPadding.calculateTopPadding() + AppDimens.Space8,
                                bottom = bottomInset + AppDimens.Space8,
                            ),
                        ) {
                            viewModel.error?.let { error ->
                                item("daily_refresh_error") {
                                    SettingItem(
                                        title = { Text("刷新失败，已保留当前日报", color = skin.error) },
                                        description = { Text(error, style = skin.footnote2, color = skin.onSurfaceVariantSummary) },
                                        endAction = {
                                            AppTextButton(
                                                text = "重试",
                                                enabled = !viewModel.isLoading,
                                                onClick = { scope.launch { viewModel.retry(httpClient) } },
                                                modifier = Modifier.testTag("daily_refresh_retry"),
                                            )
                                        },
                                    )
                                }
                            }
                            viewModel.sections.forEachIndexed { sectionIndex, section ->
                                if (sectionIndex > 0) {
                                    item(key = "header_${section.date}") {
                                        DateHeader(
                                            date = formatDailyDate(section.date),
                                            modifier = Modifier.testTag("daily_screen_section_${section.date}"),
                                        )
                                    }
                                }
                                /** 分页阈值与可见日期定位依赖每日一个 lazy item。 */
                                item(key = "group_${section.date}") {
                                    Column(
                                        modifier = Modifier.padding(horizontal = AppDimens.PageHorizontalPadding),
                                        verticalArrangement = Arrangement.spacedBy(AppDimens.Space8),
                                    ) {
                                        section.stories.forEach { story ->
                                            DailyStoryCard(
                                                story = story,
                                                modifier = Modifier.testTag("daily_screen_story_${story.id}"),
                                                onClick = {
                                                    if (openingStory == null) {
                                                        failedStory = null
                                                        storyOpenError = null
                                                        openingStory = story
                                                    }
                                                },
                                            )
                                            if (openingStory?.id == story.id || failedStory?.id == story.id) {
                                                SettingItem(
                                                    title = {
                                                        Text(
                                                            if (openingStory?.id == story.id) "正在打开原文…" else "原文加载失败",
                                                            style = skin.footnote1,
                                                        )
                                                    },
                                                    description = storyOpenError?.let { error ->
                                                        { Text(error, color = skin.error, style = skin.footnote2) }
                                                    },
                                                    endAction = {
                                                        if (openingStory?.id == story.id) {
                                                            AppSpinner(size = AppDimens.InlineSpinnerSize)
                                                        } else {
                                                            AppTextButton(
                                                                text = "重试",
                                                                onClick = {
                                                                    failedStory = null
                                                                    storyOpenError = null
                                                                    openingStory = story
                                                                },
                                                                modifier = Modifier.testTag("daily_story_${story.id}_retry"),
                                                            )
                                                        }
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            viewModel.loadMoreError?.let { error ->
                                item("daily_load_more_error") {
                                    SettingItem(
                                        title = { Text("历史日报加载失败", color = skin.error) },
                                        description = { Text(error, style = skin.footnote2, color = skin.onSurfaceVariantSummary) },
                                        endAction = {
                                            AppTextButton(
                                                text = "重试",
                                                enabled = !viewModel.isLoading && !viewModel.isLoadingMore,
                                                onClick = { scope.launch { viewModel.retryLoadMore(httpClient) } },
                                                modifier = Modifier.testTag("daily_load_more_retry"),
                                            )
                                        },
                                    )
                                }
                            }
                            if (viewModel.isLoadingMore) {
                                item(key = "loading_more") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(AppDimens.Space16),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        AppSpinner(size = null)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DateHeader(
    date: String,
    modifier: Modifier = Modifier,
) {
    val skin = LocalAppSkinTokens.current
    Text(
        text = date,
        modifier = modifier
            .semantics { heading() }
            .fillMaxWidth()
            .padding(
                start = AppDimens.PageHorizontalPadding,
                end = AppDimens.PageHorizontalPadding,
                top = AppDimens.SectionLabelTopPadding,
                bottom = AppDimens.SectionLabelBottomPadding,
            ),
        style = skin.footnote1,
        fontWeight = FontWeight.SemiBold,
        color = skin.onSurfaceVariantSummary,
    )
}

@Composable
fun DailyStoryCard(
    story: DailyStory,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val skin = LocalAppSkinTokens.current
    AppCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = AppDimens.FeedCardContentHorizontalPadding,
                    vertical = AppDimens.Space12,
                ),
        ) {
            val imageSize = minOf(AppDimens.Space64, maxWidth * 0.2f)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = story.title,
                        style = skin.title4,
                        fontWeight = FontWeight.SemiBold,
                        color = skin.onSurface,
                    )
                    if (story.hint.isNotBlank()) {
                        Spacer(Modifier.height(AppDimens.FeedCardSourceLabelSpacing))
                        Text(
                            text = story.hint,
                            style = skin.footnote2,
                            color = skin.onSurfaceVariantSummary,
                        )
                    }
                }
                story.images.firstOrNull()?.let { image ->
                    Spacer(Modifier.width(AppDimens.FeedCardThumbnailSpacing))
                    AsyncImage(
                        model = image,
                        contentDescription = null,
                        modifier = Modifier
                            .size(imageSize)
                            .clip(RoundedCornerShape(AppDimens.MediaCornerRadius))
                            .background(skin.surfaceVariant),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }
    }
}

private fun formatDailyDatePickerSelection(millis: Long): String {
    val date = Instant
        .fromEpochMilliseconds(millis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
    return date.year.toString().padStart(4, '0') +
        (date.month.ordinal + 1).twoDigitString() +
        date.day.twoDigitString()
}

private const val DAILY_SCREEN_TITLE_TAG = "daily_screen_title"
private const val DAILY_SCREEN_DATE_PICKER_BUTTON_TAG = "daily_screen_date_picker_button"
private const val DAILY_SCREEN_LOADING_TAG = "daily_screen_loading"
private const val DAILY_SCREEN_ERROR_TAG = "daily_screen_error"
private const val DAILY_SCREEN_EMPTY_TAG = "daily_screen_empty"
private const val DAILY_SCREEN_LIST_TAG = "daily_screen_list"
