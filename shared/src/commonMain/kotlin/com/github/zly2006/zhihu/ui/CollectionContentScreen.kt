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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.util.fastJoinToString
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.zly2006.zhihu.data.FeedDisplayItem
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.CollectionAnswerNavigator
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.platform.PlatformBackHandler
import com.github.zly2006.zhihu.platform.isArticleHtmlExportSupported
import com.github.zly2006.zhihu.reading.RegisterReadingQueueSource
import com.github.zly2006.zhihu.ui.components.AppAlertDialog
import com.github.zly2006.zhihu.ui.components.AppCard
import com.github.zly2006.zhihu.ui.components.AppCheckbox
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppDropdownMenu
import com.github.zly2006.zhihu.ui.components.AppDropdownMenuItem
import com.github.zly2006.zhihu.ui.components.AppIconButton
import com.github.zly2006.zhihu.ui.components.AppLinearProgress
import com.github.zly2006.zhihu.ui.components.AppLinearProgressIndeterminate
import com.github.zly2006.zhihu.ui.components.AppPageTitleBar
import com.github.zly2006.zhihu.ui.components.AppScaffold
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.ui.components.FeedCard
import com.github.zly2006.zhihu.ui.components.PaginatedList
import com.github.zly2006.zhihu.ui.components.ProgressIndicatorFooter
import com.github.zly2006.zhihu.ui.components.appPageTitleBarScroll
import com.github.zly2006.zhihu.ui.components.pageTurnViewportWithGuide
import com.github.zly2006.zhihu.ui.components.rememberAppPageTitleBarScrollState
import com.github.zly2006.zhihu.ui.components.rememberPageTurnTarget
import com.github.zly2006.zhihu.util.formatFullDateTime
import com.github.zly2006.zhihu.viewmodel.CollectionContentEnvironment
import com.github.zly2006.zhihu.viewmodel.CollectionContentViewModel
import com.github.zly2006.zhihu.viewmodel.CollectionHtmlExportDialogState
import com.github.zly2006.zhihu.viewmodel.rememberPaginationEnvironment
import com.github.zly2006.zhihu.viewmodel.sharedArticleAnswerSwitchState
import com.github.zly2006.zhihu.ui.components.AppIcon as Icon

const val COLLECTION_CONTENT_TITLE_TAG = "collection_content_title"
const val COLLECTION_CONTENT_BACK_BUTTON_TAG = "collection_content_back_button"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionContentScreen(
    collectionId: String,
) {
    val navigator = LocalNavigator.current
    val screenViewModel = viewModel { CollectionContentViewModel(collectionId) }
    val collectionEnvironment = rememberPaginationEnvironment(allowGuestAccess = false) as CollectionContentEnvironment
    val listState = rememberLazyListState()
    val titleBarScrollState = rememberAppPageTitleBarScrollState()
    var showActionsMenu by remember { mutableStateOf(false) }
    var showExportOptionsDialog by remember { mutableStateOf(false) }
    val pageTurnTarget = rememberPageTurnTarget(
        listState = listState,
        enabled = !showActionsMenu &&
            !showExportOptionsDialog &&
            screenViewModel.exportDialogState == null,
    )

    LaunchedEffect(screenViewModel) {
        if (screenViewModel.allData.isEmpty()) {
            screenViewModel.refresh(collectionEnvironment)
        }
    }

    PlatformBackHandler(enabled = showActionsMenu) {
        showActionsMenu = false
    }

    PlatformBackHandler(enabled = showExportOptionsDialog) {
        showExportOptionsDialog = false
    }

    AppScaffold(
        modifier = Modifier.fillMaxSize().appPageTitleBarScroll(titleBarScrollState),
        topBar = {
            AppPageTitleBar(
                title = screenViewModel.title,
                titleTag = COLLECTION_CONTENT_TITLE_TAG,
                onBack = navigator.onNavigateBack,
                backTag = COLLECTION_CONTENT_BACK_BUTTON_TAG,
                scrollState = titleBarScrollState,
                actions = {
                    if (isArticleHtmlExportSupported) {
                        Box {
                            AppIconButton(
                                onClick = { showActionsMenu = true },
                                enabled = screenViewModel.exportDialogState?.isCompleted != false,
                                modifier = Modifier.testTag("collection_content_more_button"),
                            ) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "更多")
                            }
                            AppDropdownMenu(
                                expanded = showActionsMenu,
                                onDismissRequest = { showActionsMenu = false },
                                modifier = Modifier.testTag("collection_content_more_menu"),
                            ) {
                                AppDropdownMenuItem(
                                    modifier = Modifier.testTag("collection_content_export_action"),
                                    text = { Text("全部导出HTML") },
                                    enabled = screenViewModel.exportDialogState?.isCompleted != false,
                                    onClick = {
                                        showActionsMenu = false
                                        showExportOptionsDialog = true
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        if (showExportOptionsDialog) {
            CollectionHtmlExportOptionsDialog(
                onDismiss = { showExportOptionsDialog = false },
                onConfirm = { includeImages ->
                    showExportOptionsDialog = false
                    screenViewModel.exportAllToHtmlZip(
                        environment = collectionEnvironment,
                        includeImages = includeImages,
                    )
                },
            )
        }
        screenViewModel.exportDialogState?.let { state ->
            CollectionHtmlExportDialog(
                state = state,
                onDismiss = screenViewModel::dismissExportDialog,
            )
        }
        CollectionContentBody(
            viewModel = screenViewModel,
            environment = collectionEnvironment,
            collectionId = collectionId,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .pageTurnViewportWithGuide(pageTurnTarget),
            listState = listState,
            tagPrefix = "collection_content",
        )
    }
}

@Composable
internal fun CollectionContentBody(
    viewModel: CollectionContentViewModel,
    environment: CollectionContentEnvironment,
    collectionId: String,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    tagPrefix: String,
    displayItems: List<FeedDisplayItem> = viewModel.displayItems,
    header: @Composable () -> Unit = {},
) {
    val navigator = LocalNavigator.current
    val sharedData = sharedArticleAnswerSwitchState
    val readingQueueSourceId = "collection:$collectionId:contents"
    RegisterReadingQueueSource(
        sourceId = readingQueueSourceId,
        items = displayItems,
    )

    val visibleCollectionItems = displayItems.mapNotNull { displayItem ->
        val sourceIndex = viewModel.displayItems.indexOf(displayItem)
        viewModel.allData.getOrNull(sourceIndex)
    }

    PaginatedList(
        items = displayItems,
        onLoadMore = { viewModel.loadMore(environment) },
        isEnd = { viewModel.isEnd },
        isLoading = { viewModel.isLoading },
        loadError = { viewModel.errorMessage },
        onRetry = { if (viewModel.collection == null) viewModel.refresh(environment) else viewModel.loadMore(environment) },
        listState = listState,
        modifier = modifier.testTag("${tagPrefix}_list"),
        footer = ProgressIndicatorFooter,
        topContent = {
            item(0) {
                header()
                val summary = listOfNotNull(
                    viewModel.collection?.itemCount?.let { "$it 条收藏" },
                    viewModel.collection?.likeCount?.let { "$it 个赞同" },
                    viewModel.collection?.commentCount?.let { "$it 条评论" },
                    viewModel.collection?.updatedTime?.let { "${formatFullDateTime(it)} 更新" },
                ).fastJoinToString(" · ")
                if (summary.isNotBlank()) {
                    AppCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = AppDimens.Space16,
                                vertical = AppDimens.Space8,
                            ).testTag("${tagPrefix}_stats"),
                    ) {
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(
                                horizontal = AppDimens.Space14,
                                vertical = AppDimens.Space10,
                            ),
                        )
                    }
                }
            }
        },
    ) { item ->
        FeedCard(
            item = item,
            readingQueueSourceId = readingQueueSourceId,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("${tagPrefix}_item_${item.stableKey}"),
        ) { _, destination ->
            if (destination is Article && destination.type == ArticleType.Answer) {
                val index = displayItems.indexOf(item)
                val nextItems = if (index >= 0) visibleCollectionItems.drop(index + 1) else emptyList()
                val previousItems = if (index > 0) visibleCollectionItems.take(index).reversed() else emptyList()
                sharedData.pendingNavigator = CollectionAnswerNavigator(
                    collectionId = collectionId,
                    collectionTitle = viewModel.title,
                    initialNextItems = nextItems,
                    initialPreviousItems = previousItems,
                    initialNextUrl = viewModel.nextPageUrl,
                    environment = environment,
                )
            }
            destination?.let(navigator.onNavigate)
        }
    }
}

@Composable
private fun CollectionHtmlExportOptionsDialog(
    onDismiss: () -> Unit,
    onConfirm: (Boolean) -> Unit,
) {
    var includeImages by remember { mutableStateOf(true) }

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导出收藏夹 HTML") },
        text = {
            Column {
                Text("可以选择是否一并导出图片。导出图片会把图片下载并内嵌到 HTML 中，速度可能更慢。")
                Spacer(modifier = Modifier.height(AppDimens.Space12))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("collection_content_export_include_images")
                        .toggleable(value = includeImages, role = Role.Checkbox, onValueChange = { includeImages = it }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppCheckbox(
                        checked = includeImages,
                        onCheckedChange = null,
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("导出图片（更慢）")
                        Spacer(modifier = Modifier.height(AppDimens.SettingTitleDescriptionSpacing))
                        Text(
                            text = "关闭后保留原始图片链接，不转成 base64",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            AppTextButton(
                text = "开始导出",
                onClick = { onConfirm(includeImages) },
                modifier = Modifier.testTag("collection_content_export_confirm"),
            )
        },
        dismissButton = {
            AppTextButton(
                text = "取消",
                onClick = onDismiss,
                modifier = Modifier.testTag("collection_content_export_cancel"),
            )
        },
    )
}

@Composable
private fun CollectionHtmlExportDialog(
    state: CollectionHtmlExportDialogState,
    onDismiss: () -> Unit,
) {
    AppAlertDialog(
        onDismissRequest = {
            if (state.isCompleted) {
                onDismiss()
            }
        },
        title = {
            Text(if (state.isCompleted) state.phaseText else "正在导出收藏夹")
        },
        text = {
            androidx.compose.foundation.layout.Column {
                Text(state.phaseText)
                if (state.currentTitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(AppDimens.Space8))
                    Text("当前：${state.currentTitle}")
                }
                Spacer(modifier = Modifier.height(AppDimens.Space12))
                if (state.isIndeterminate) {
                    AppLinearProgressIndeterminate(modifier = Modifier.fillMaxWidth())
                } else {
                    AppLinearProgress(
                        progress = state.progress,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(modifier = Modifier.height(AppDimens.Space12))
                Text("成功 ${state.successCount} · 跳过 ${state.skippedCount} · 失败 ${state.failedCount}")
                state.resultMessage?.takeIf { it.isNotBlank() }?.let { message ->
                    Spacer(modifier = Modifier.height(AppDimens.Space12))
                    Text(message)
                }
                state.zipFilePath?.takeIf { it.isNotBlank() }?.let { zipFilePath ->
                    Spacer(modifier = Modifier.height(AppDimens.Space8))
                    Text(zipFilePath)
                }
            }
        },
        confirmButton = {
            if (state.isCompleted) {
                AppTextButton(
                    text = "确定",
                    onClick = onDismiss,
                )
            }
        },
    )
}
