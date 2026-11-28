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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import com.github.zly2006.zhihu.i18n.AccountStrings
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.ui.components.AppDialog
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppEmptyState
import com.github.zly2006.zhihu.ui.components.AppIcon
import com.github.zly2006.zhihu.ui.components.AppIconButton
import com.github.zly2006.zhihu.ui.components.AppLoadStateContent
import com.github.zly2006.zhihu.ui.components.AppSegmentedControl
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.ui.components.SettingItem
import com.github.zly2006.zhihu.ui.components.SettingsContent
import com.github.zly2006.zhihu.viewmodel.LoadState
import com.github.zly2006.zhihu.viewmodel.filter.BlockedKeyword
import com.github.zly2006.zhihu.viewmodel.filter.BlockedQuestionAuthor
import com.github.zly2006.zhihu.viewmodel.filter.BlockedTopic
import com.github.zly2006.zhihu.viewmodel.filter.BlockedUser
import com.github.zly2006.zhihu.viewmodel.filter.KeywordScope
import com.github.zly2006.zhihu.viewmodel.readyValue

@Composable
internal fun BlocklistSettingsLists(
    selectedTab: BlocklistTab,
    keywords: LoadState<List<BlockedKeyword>>,
    users: LoadState<List<BlockedUser>>,
    questionAuthors: LoadState<List<BlockedQuestionAuthor>>,
    topics: LoadState<List<BlockedTopic>>,
    keywordScope: KeywordScope,
    submitting: Boolean,
    retry: () -> Unit,
    listState: LazyListState,
    listPadding: PaddingValues,
    bottomPadding: Dp,
    nlpContent: @Composable (onNavigateBack: () -> Unit) -> Unit,
    onBack: () -> Unit,
    onScopeSelect: (KeywordScope) -> Unit,
    onClearKeywords: () -> Unit,
    onDeleteKeyword: (BlockedKeyword) -> Unit,
    onClearUsers: () -> Unit,
    onDeleteUser: (BlockedUser) -> Unit,
    onClickUser: (BlockedUser) -> Unit,
    onClearQuestionAuthors: () -> Unit,
    onDeleteQuestionAuthor: (BlockedQuestionAuthor) -> Unit,
    onClickQuestionAuthor: (BlockedQuestionAuthor) -> Unit,
    onClearTopics: () -> Unit,
    onDeleteTopic: (BlockedTopic) -> Unit,
) {
    when (selectedTab) {
        BlocklistTab.Keywords -> BlocklistSettingsKeywordList(
            state = keywords,
            onRetry = retry,
            keywordScope = keywordScope,
            onScopeSelect = onScopeSelect,
            submitting = submitting,
            onClearAll = onClearKeywords,
            onDelete = onDeleteKeyword,
            listState = listState,
            contentPadding = listPadding,
        )

        BlocklistTab.Nlp -> Box(Modifier.fillMaxSize().padding(bottom = bottomPadding)) {
            nlpContent(onBack)
        }

        BlocklistTab.Users -> BlocklistSettingsUserList(
            state = users,
            onRetry = retry,
            onClearAll = onClearUsers,
            onDelete = onDeleteUser,
            onClick = onClickUser,
            listState = listState,
            contentPadding = listPadding,
        )

        BlocklistTab.QuestionAuthors -> BlocklistSettingsQuestionAuthorList(
            state = questionAuthors,
            onRetry = retry,
            onClearAll = onClearQuestionAuthors,
            onDelete = onDeleteQuestionAuthor,
            onClick = onClickQuestionAuthor,
            listState = listState,
            contentPadding = listPadding,
        )

        BlocklistTab.Topics -> BlocklistSettingsTopicList(
            state = topics,
            onRetry = retry,
            onClearAll = onClearTopics,
            onDelete = onDeleteTopic,
            listState = listState,
            contentPadding = listPadding,
        )
    }
}

@Composable
private fun BlocklistSettingsKeywordList(
    state: LoadState<List<BlockedKeyword>>,
    onRetry: () -> Unit,
    keywordScope: KeywordScope,
    onScopeSelect: (KeywordScope) -> Unit,
    submitting: Boolean,
    onClearAll: () -> Unit,
    onDelete: (BlockedKeyword) -> Unit,
    listState: LazyListState,
    contentPadding: PaddingValues,
) {
    BlocklistItemList(
        state = state,
        onRetry = onRetry,
        description = keywordScopeDescription(keywordScope),
        emptyTitle = AccountStrings.keywordsEmpty,
        emptyDescription = AccountStrings.keywordsEmptyHint,
        clearTitle = { count -> AccountStrings.keywordsClearTitle(count) },
        clearSummary = AccountStrings.keywordsClearSummary,
        onClearAll = onClearAll,
        itemKey = { it.id },
        listState = listState,
        contentPadding = contentPadding,
        listTag = BlocklistSettingsTestTags.KEYWORD_LIST,
        clearTag = BlocklistSettingsTestTags.KEYWORD_CLEAR_BUTTON,
        clearDialogTag = BlocklistSettingsTestTags.CLEAR_DIALOG,
        leadingItems = {
            item {
                SettingsContent {
                    KeywordScopeSettingItem(
                        scope = keywordScope,
                        onSelect = onScopeSelect,
                        enabled = !submitting,
                        testTagPrefix = "blocklistSettings:keywords:scope",
                    )
                }
            }
        },
    ) { keyword ->
        BlocklistItemRow(
            title = keyword.keyword,
            description = listOfNotNull(
                AccountStrings.caseSensitive.takeIf { keyword.caseSensitive },
                AccountStrings.regularExpression.takeIf { keyword.isRegex },
            ).joinToString(" · ").ifEmpty { AccountStrings.exactMatch },
            deleteDescription = AccountStrings.keywordDeleteDescription(keyword.keyword),
            onDelete = { onDelete(keyword) },
            modifier = Modifier.testTag("blocklistSettings:keywords:item:${keyword.id}"),
            deleteTag = "blocklistSettings:keywords:delete:${keyword.id}",
        )
    }
}

@Composable
private fun BlocklistSettingsUserList(
    state: LoadState<List<BlockedUser>>,
    onRetry: () -> Unit,
    onClearAll: () -> Unit,
    onDelete: (BlockedUser) -> Unit,
    onClick: (BlockedUser) -> Unit,
    listState: LazyListState,
    contentPadding: PaddingValues,
) {
    BlocklistItemList(
        state = state,
        onRetry = onRetry,
        description = AccountStrings.usersBlockSummary,
        emptyTitle = AccountStrings.usersEmpty,
        emptyDescription = AccountStrings.usersEmptyHint,
        clearTitle = { count -> AccountStrings.usersClearTitle(count) },
        clearSummary = AccountStrings.usersClearSummary,
        onClearAll = onClearAll,
        itemKey = { it.userId },
        listState = listState,
        contentPadding = contentPadding,
        listTag = BlocklistSettingsTestTags.USER_LIST,
        clearTag = BlocklistSettingsTestTags.USER_CLEAR_BUTTON,
        clearDialogTag = BlocklistSettingsTestTags.CLEAR_DIALOG,
    ) { user ->
        BlockedPersonRow(
            category = "users",
            noun = AccountStrings.blockedUsers,
            userId = user.userId,
            userName = user.userName,
            avatarUrl = user.avatarUrl,
            onDelete = { onDelete(user) },
            onClick = { onClick(user) },
        )
    }
}

@Composable
private fun BlocklistSettingsQuestionAuthorList(
    state: LoadState<List<BlockedQuestionAuthor>>,
    onRetry: () -> Unit,
    onClearAll: () -> Unit,
    onDelete: (BlockedQuestionAuthor) -> Unit,
    onClick: (BlockedQuestionAuthor) -> Unit,
    listState: LazyListState,
    contentPadding: PaddingValues,
) {
    BlocklistItemList(
        state = state,
        onRetry = onRetry,
        description = AccountStrings.questionAuthorsSummary,
        emptyTitle = AccountStrings.questionAuthorsEmpty,
        emptyDescription = AccountStrings.addBottomRightHint,
        clearTitle = { count -> AccountStrings.questionAuthorsClearTitle(count) },
        clearSummary = AccountStrings.questionAuthorsClearSummary,
        onClearAll = onClearAll,
        itemKey = { it.userId },
        listState = listState,
        contentPadding = contentPadding,
        listTag = BlocklistSettingsTestTags.QUESTION_AUTHOR_LIST,
        clearTag = BlocklistSettingsTestTags.QUESTION_AUTHOR_CLEAR_BUTTON,
        clearDialogTag = BlocklistSettingsTestTags.CLEAR_DIALOG,
    ) { author ->
        BlockedPersonRow(
            category = "questionAuthors",
            noun = AccountStrings.blockedQuestionAuthors,
            userId = author.userId,
            userName = author.userName,
            avatarUrl = author.avatarUrl,
            onDelete = { onDelete(author) },
            onClick = { onClick(author) },
        )
    }
}

@Composable
private fun BlocklistSettingsTopicList(
    state: LoadState<List<BlockedTopic>>,
    onRetry: () -> Unit,
    onClearAll: () -> Unit,
    onDelete: (BlockedTopic) -> Unit,
    listState: LazyListState,
    contentPadding: PaddingValues,
) {
    BlocklistItemList(
        state = state,
        onRetry = onRetry,
        description = AccountStrings.topicsSummary,
        emptyTitle = AccountStrings.topicsEmpty,
        emptyDescription = AccountStrings.topicsEmptyHint,
        clearTitle = { count -> AccountStrings.topicsClearTitle(count) },
        clearSummary = AccountStrings.topicsClearSummary,
        onClearAll = onClearAll,
        itemKey = { it.topicId },
        listState = listState,
        contentPadding = contentPadding,
        listTag = BlocklistSettingsTestTags.TOPIC_LIST,
        clearTag = BlocklistSettingsTestTags.TOPIC_CLEAR_BUTTON,
        clearDialogTag = BlocklistSettingsTestTags.CLEAR_DIALOG,
    ) { topic ->
        BlocklistItemRow(
            title = topic.topicName,
            description = AccountStrings.topicIdValue(topic.topicId),
            deleteDescription = AccountStrings.topicDeleteDescription(topic.topicName),
            onDelete = { onDelete(topic) },
            modifier = Modifier.testTag("blocklistSettings:topics:item:${topic.topicId}"),
            deleteTag = "blocklistSettings:topics:delete:${topic.topicId}",
        )
    }
}

fun keywordScopeDescription(scope: KeywordScope): String = when (scope) {
    KeywordScope.ALL -> AccountStrings.keywordScopeAllHint
    KeywordScope.QUESTION -> AccountStrings.keywordScopeQuestionHint
    KeywordScope.ANSWER -> AccountStrings.keywordScopeAnswerHint
}

/** 精确关键词与 NLP 管理共用字段来源语义，选择不会改变既有启用开关。 */
@Composable
fun KeywordScopeSettingItem(
    scope: KeywordScope,
    onSelect: (KeywordScope) -> Unit,
    enabled: Boolean,
    testTagPrefix: String,
) {
    SettingItem(
        title = { Text(AccountStrings.keywordScope) },
        description = { Text(keywordScopeDescription(scope)) },
        bottomAction = {
            AppSegmentedControl(
                options = listOf(AccountStrings.allContent, AccountStrings.question, AccountStrings.answer),
                selectedIndex = scope.ordinal,
                onSelect = { onSelect(KeywordScope.entries[it]) },
                enabled = enabled,
                testTags = KeywordScope.entries.map { "$testTagPrefix:${it.name}" },
            )
        },
    )
}

@Composable
fun <T> BlocklistItemList(
    state: LoadState<List<T>>,
    onRetry: () -> Unit,
    description: String,
    emptyTitle: String,
    emptyDescription: String? = null,
    clearTitle: (count: Int) -> String,
    clearSummary: String,
    onClearAll: () -> Unit,
    itemKey: (T) -> Any,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(),
    listTag: String? = null,
    clearTag: String? = null,
    clearDialogTag: String? = null,
    headerActions: @Composable RowScope.() -> Unit = {},
    leadingItems: LazyListScope.() -> Unit = {},
    itemContent: @Composable LazyItemScope.(T) -> Unit,
) {
    var showClearDialog by rememberSaveable { mutableStateOf(false) }
    val rows = state.readyValue.orEmpty()
    LazyColumn(
        modifier = modifier.fillMaxSize().then(listTag?.let { Modifier.testTag(it) } ?: Modifier),
        state = listState,
        contentPadding = contentPadding,
    ) {
        leadingItems()
        item {
            SettingsContent {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = AppDimens.PageHorizontalPadding, end = AppDimens.Space4),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = description,
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = AppDimens.Space8),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    headerActions()
                    AppTextButton(
                        text = AccountStrings.clear,
                        onClick = { showClearDialog = true },
                        modifier = clearTag?.let { Modifier.testTag(it) } ?: Modifier,
                        enabled = rows.isNotEmpty(),
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (rows.isEmpty()) {
            item {
                SettingsContent {
                    AppLoadStateContent(
                        state = state,
                        onRetry = onRetry,
                        isEmpty = { it.isEmpty() },
                        empty = { AppEmptyState(title = emptyTitle, description = emptyDescription) },
                    ) {}
                }
            }
        } else {
            items(rows, key = itemKey) { row ->
                val itemScope = this
                SettingsContent { itemContent(itemScope, row) }
            }
        }
    }
    if (showClearDialog) {
        AppDialog(
            onDismissRequest = { showClearDialog = false },
            title = clearTitle(rows.size),
            summary = clearSummary,
            confirmText = AccountStrings.clear,
            confirmEnabled = rows.isNotEmpty(),
            onConfirm = {
                if (showClearDialog && rows.isNotEmpty()) {
                    showClearDialog = false
                    onClearAll()
                }
            },
            dismissText = AccountStrings.cancel,
            destructive = true,
            modifier = clearDialogTag?.let { Modifier.testTag(it) } ?: Modifier,
        )
    }
}

/** 删除是独立点击目标；[deleteTag] 位于未合并语义树中，不能触发行导航。 */
@Composable
fun BlocklistItemRow(
    title: String,
    description: String,
    deleteDescription: String,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    titleMaxLines: Int = Int.MAX_VALUE,
    deleteTag: String? = null,
    icon: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    SettingItem(
        modifier = modifier,
        title = { Text(title, maxLines = titleMaxLines, overflow = TextOverflow.Ellipsis) },
        description = { Text(description) },
        icon = icon,
        onClick = onClick,
        endAction = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIconButton(
                    onClick = onDelete,
                    modifier = deleteTag?.let { Modifier.testTag(it) } ?: Modifier,
                ) {
                    AppIcon(
                        Icons.Default.Delete,
                        contentDescription = deleteDescription,
                        tint = LocalAppSkinTokens.current.error,
                    )
                }
                if (onClick != null) {
                    AppIcon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(AppDimens.SettingChevronSize),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}

@Composable
private fun BlockedPersonRow(
    category: String,
    noun: String,
    userId: String,
    userName: String,
    avatarUrl: String?,
    onDelete: () -> Unit,
    onClick: () -> Unit,
) {
    BlocklistItemRow(
        title = userName,
        description = AccountStrings.userIdValue(userId),
        deleteDescription = AccountStrings.blockedPersonDelete(noun, userName),
        onDelete = onDelete,
        modifier = Modifier.testTag("blocklistSettings:$category:item:$userId"),
        deleteTag = "blocklistSettings:$category:delete:$userId",
        icon = {
            AsyncImage(
                model = avatarUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(AppDimens.SettingRowIconSize)
                    .clip(CircleShape),
            )
        },
        onClick = onClick,
    )
}
