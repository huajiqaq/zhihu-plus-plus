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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.zly2006.zhihu.data.ZhihuCollection
import com.github.zly2006.zhihu.i18n.ContentStrings
import com.github.zly2006.zhihu.navigation.CollectionContent
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.ui.components.AppAlertDialog
import com.github.zly2006.zhihu.ui.components.AppIcon
import com.github.zly2006.zhihu.ui.components.AppIconButton
import com.github.zly2006.zhihu.ui.components.AppPageTitleBar
import com.github.zly2006.zhihu.ui.components.AppScaffold
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.ui.components.CreateCollectionDialog
import com.github.zly2006.zhihu.ui.components.FeedItemSurface
import com.github.zly2006.zhihu.ui.components.PaginatedList
import com.github.zly2006.zhihu.ui.components.ProgressIndicatorFooter
import com.github.zly2006.zhihu.ui.components.appPageTitleBarScroll
import com.github.zly2006.zhihu.ui.components.pageTurnViewportWithGuide
import com.github.zly2006.zhihu.ui.components.rememberAppPageTitleBarScrollState
import com.github.zly2006.zhihu.ui.components.rememberPageTurnTarget
import com.github.zly2006.zhihu.ui.components.settingSkinIsMiuix
import com.github.zly2006.zhihu.viewmodel.CollectionsViewModel
import com.github.zly2006.zhihu.viewmodel.rememberPaginationEnvironment
import kotlinx.coroutines.launch

@Composable
fun CollectionScreen(
    urlToken: String?,
    testCollections: List<ZhihuCollection>? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    showBackButton: Boolean = true,
    isActive: Boolean = true,
) {
    val navigator = LocalNavigator.current
    val environment = rememberPaginationEnvironment(allowGuestAccess = false)
    val viewModel: CollectionsViewModel = viewModel(key = urlToken) {
        CollectionsViewModel(urlToken.orEmpty())
    }
    val userMessages = rememberUserMessageSink()
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val titleBarScrollState = rememberAppPageTitleBarScrollState()
    val useTestCollections = testCollections != null || urlToken == null
    val collections = testCollections ?: viewModel.allData
    var showCreateCollectionDialog by remember { mutableStateOf(false) }
    var collectionPendingDeletion by remember { mutableStateOf<ZhihuCollection?>(null) }
    val pageTurnTarget = rememberPageTurnTarget(
        listState = listState,
        enabled = isActive && !showCreateCollectionDialog && collectionPendingDeletion == null,
    )

    LaunchedEffect(viewModel, isActive) {
        if (isActive &&
            !useTestCollections &&
            viewModel.allData.isEmpty() &&
            !viewModel.isEnd &&
            viewModel.errorMessage == null
        ) {
            viewModel.loadMore(environment)
        }
    }

    AppScaffold(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .appPageTitleBarScroll(titleBarScrollState),
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            AppPageTitleBar(
                title = ContentStrings.myCollections,
                subtitle = ContentStrings.collectionCount(collections.size),
                titleTag = COLLECTION_SCREEN_TITLE_TAG,
                onBack = navigator.onNavigateBack.takeIf { showBackButton },
                backTag = COLLECTION_SCREEN_BACK_BUTTON_TAG,
                scrollState = titleBarScrollState,
                actions = {
                    AppIconButton(
                        onClick = {
                            if (!viewModel.isCreatingCollection && viewModel.deletingCollectionId == null) {
                                viewModel.clearMutationErrors()
                                showCreateCollectionDialog = true
                            }
                        },
                        enabled = !viewModel.isCreatingCollection && viewModel.deletingCollectionId == null,
                        modifier = Modifier.testTag(COLLECTION_SCREEN_CREATE_BUTTON_TAG),
                    ) {
                        AppIcon(Icons.Filled.Add, contentDescription = ContentStrings.newCollection)
                    }
                },
            )
        },
    ) { innerPadding ->
        PaginatedList(
            items = collections,
            onLoadMore = {
                if (!useTestCollections) {
                    viewModel.loadMore(environment)
                }
            },
            isEnd = { useTestCollections || viewModel.isEnd },
            isLoading = { !useTestCollections && viewModel.isLoading },
            loadError = { viewModel.errorMessage.takeUnless { useTestCollections } },
            onRetry = { if (!useTestCollections) viewModel.loadMore(environment) },
            listState = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .pageTurnViewportWithGuide(pageTurnTarget)
                .testTag(COLLECTION_SCREEN_LIST_TAG),
            footer = ProgressIndicatorFooter,
        ) { collection ->
            FeedItemSurface(
                modifier = Modifier.testTag("collection_screen_item_${collection.id}"),
                onClick = { navigator.onNavigate(CollectionContent(collection.id)) },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (settingSkinIsMiuix()) {
                                    LocalAppSkinTokens.current.primary.copy(alpha = 0.12f)
                                } else {
                                    MaterialTheme.colorScheme.primaryContainer
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppIcon(
                            Icons.Filled.Bookmarks,
                            contentDescription = null,
                            tint = if (settingSkinIsMiuix()) {
                                LocalAppSkinTokens.current.primary
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            },
                        )
                    }
                    Spacer(modifier = Modifier.size(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = collection.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (collection.description.isNotBlank()) {
                            Text(
                                text = collection.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        Text(
                            text = ContentStrings.collectionVisibilityCount(collection.itemCount, if (collection.isPublic) ContentStrings.public else ContentStrings.private),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    if (!collection.isDefault) {
                        AppIconButton(
                            onClick = {
                                viewModel.clearMutationErrors()
                                collectionPendingDeletion = collection
                            },
                            enabled = !viewModel.isCreatingCollection && viewModel.deletingCollectionId == null,
                            modifier = Modifier.testTag("collection_screen_delete_button_${collection.id}"),
                        ) {
                            AppIcon(
                                Icons.Filled.Delete,
                                contentDescription = ContentStrings.deleteCollectionNamed(collection.title),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }

    CreateCollectionDialog(
        showDialog = showCreateCollectionDialog,
        onDismiss = {
            showCreateCollectionDialog = false
        },
        onConfirm = { title, description, isPublic ->
            coroutineScope.launch {
                if (
                    viewModel.createCollection(
                        environment = environment,
                        title = title,
                        description = description,
                        isPublic = isPublic,
                    )
                ) {
                    viewModel.refresh(environment)
                    showCreateCollectionDialog = false
                    userMessages.showShortMessage(ContentStrings.collectionCreated)
                }
            }
        },
        isSubmitting = viewModel.isCreatingCollection,
        errorMessage = viewModel.createCollectionError,
    )

    collectionPendingDeletion?.let { collection ->
        val isDeleting = viewModel.deletingCollectionId != null
        AppAlertDialog(
            modifier = Modifier.testTag("collection_screen_delete_dialog_${collection.id}"),
            onDismissRequest = {
                if (!isDeleting) {
                    collectionPendingDeletion = null
                }
            },
            title = { Text(ContentStrings.deleteCollection) },
            text = {
                Column {
                    Text(ContentStrings.deleteCollectionConfirm(collection.title))
                    viewModel.deleteCollectionError?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            },
            confirmButton = {
                AppTextButton(
                    text = if (isDeleting) ContentStrings.deleting else ContentStrings.delete,
                    onClick = {
                        coroutineScope.launch {
                            if (viewModel.deleteCollection(environment, collection)) {
                                viewModel.refresh(environment)
                                collectionPendingDeletion = null
                                userMessages.showShortMessage(ContentStrings.collectionDeleted)
                            }
                        }
                    },
                    modifier = Modifier.testTag("collection_screen_delete_confirm_${collection.id}"),
                    enabled = !isDeleting,
                )
            },
            dismissButton = {
                AppTextButton(
                    text = ContentStrings.cancel,
                    onClick = {
                        collectionPendingDeletion = null
                    },
                    enabled = !isDeleting,
                )
            },
        )
    }
}

private const val COLLECTION_SCREEN_TITLE_TAG = "collection_screen_title"
private const val COLLECTION_SCREEN_BACK_BUTTON_TAG = "collection_screen_back_button"
private const val COLLECTION_SCREEN_LIST_TAG = "collection_screen_list"
private const val COLLECTION_SCREEN_CREATE_BUTTON_TAG = "collection_screen_create_button"
