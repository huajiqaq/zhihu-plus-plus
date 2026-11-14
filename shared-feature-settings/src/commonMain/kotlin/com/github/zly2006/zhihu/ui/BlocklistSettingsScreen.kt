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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import com.github.zly2006.zhihu.filter.asListLoadState
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.Person
import com.github.zly2006.zhihu.platform.isBlocklistNlpSupported
import com.github.zly2006.zhihu.platform.platformName
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.ui.components.AppAlertDialog
import com.github.zly2006.zhihu.ui.components.AppButton
import com.github.zly2006.zhihu.ui.components.AppCheckbox
import com.github.zly2006.zhihu.ui.components.AppCompactTopBar
import com.github.zly2006.zhihu.ui.components.AppDialog
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppEmptyState
import com.github.zly2006.zhihu.ui.components.AppFloatingActionButton
import com.github.zly2006.zhihu.ui.components.AppIconButton
import com.github.zly2006.zhihu.ui.components.AppLoadStateContent
import com.github.zly2006.zhihu.ui.components.AppScaffold
import com.github.zly2006.zhihu.ui.components.AppTabRow
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.ui.components.AppTextField
import com.github.zly2006.zhihu.ui.components.SettingItem
import com.github.zly2006.zhihu.ui.components.SettingsContent
import com.github.zly2006.zhihu.util.Log
import com.github.zly2006.zhihu.viewmodel.LoadState
import com.github.zly2006.zhihu.viewmodel.filter.BlockedKeyword
import com.github.zly2006.zhihu.viewmodel.filter.BlockedQuestionAuthor
import com.github.zly2006.zhihu.viewmodel.filter.BlockedTopic
import com.github.zly2006.zhihu.viewmodel.filter.BlockedUser
import com.github.zly2006.zhihu.viewmodel.filter.KeywordType
import com.github.zly2006.zhihu.viewmodel.filter.getContentFilterDatabase
import com.github.zly2006.zhihu.viewmodel.readyValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import com.github.zly2006.zhihu.ui.components.AppIcon as Icon

const val BLOCKLIST_SETTINGS_BACK_BUTTON_TAG = "blocklist_settings_back_button"

object BlocklistSettingsTestTags {
    const val ROOT = "blocklistSettings:root"
    const val IMPORT_BUTTON = "blocklistSettings:import"
    const val EXPORT_BUTTON = "blocklistSettings:export"
    const val TAB_ROW = "blocklistSettings:tabs"
    const val FAB = "blocklistSettings:add"
    const val KEYWORD_LIST = "blocklistSettings:keywords:list"
    const val KEYWORD_CLEAR_BUTTON = "blocklistSettings:keywords:clear"
    const val USER_LIST = "blocklistSettings:users:list"
    const val USER_CLEAR_BUTTON = "blocklistSettings:users:clear"
    const val QUESTION_AUTHOR_LIST = "blocklistSettings:questionAuthors:list"
    const val QUESTION_AUTHOR_CLEAR_BUTTON = "blocklistSettings:questionAuthors:clear"
    const val TOPIC_LIST = "blocklistSettings:topics:list"
    const val TOPIC_CLEAR_BUTTON = "blocklistSettings:topics:clear"

    const val CLEAR_DIALOG = "blocklistSettings:clearDialog"
    const val KEYWORD_DIALOG_INPUT = "blocklistSettings:keywordDialog:input"
    const val KEYWORD_DIALOG_CASE_SENSITIVE = "blocklistSettings:keywordDialog:caseSensitive"
    const val KEYWORD_DIALOG_REGEX = "blocklistSettings:keywordDialog:regex"
    const val KEYWORD_DIALOG_CONFIRM = "blocklistSettings:keywordDialog:confirm"
    const val KEYWORD_DIALOG_DISMISS = "blocklistSettings:keywordDialog:dismiss"
    const val USER_DIALOG_ID_INPUT = "blocklistSettings:userDialog:userId"
    const val USER_DIALOG_NAME_INPUT = "blocklistSettings:userDialog:userName"
    const val USER_DIALOG_CONFIRM = "blocklistSettings:userDialog:confirm"
    const val USER_DIALOG_DISMISS = "blocklistSettings:userDialog:dismiss"
    const val TOPIC_DIALOG_ID_INPUT = "blocklistSettings:topicDialog:topicId"
    const val TOPIC_DIALOG_NAME_INPUT = "blocklistSettings:topicDialog:topicName"
    const val TOPIC_DIALOG_CONFIRM = "blocklistSettings:topicDialog:confirm"
    const val TOPIC_DIALOG_DISMISS = "blocklistSettings:topicDialog:dismiss"
}

private enum class BlocklistTab(
    val label: String,
    val canAdd: Boolean,
) {
    Keywords("屏蔽关键词", true),
    Nlp("NLP智能屏蔽", false),
    Users("屏蔽用户", true),
    QuestionAuthors("屏蔽提问者", true),
    Topics("屏蔽主题", true),
}

@Composable
fun BlocklistSettingsScreen(
    nlpContent: @Composable (onNavigateBack: () -> Unit) -> Unit = {
        error("$platformName 暂不支持 NLP 智能屏蔽设置")
    },
) {
    val navigator = LocalNavigator.current
    val userMessages = rememberUserMessageSink()
    val exportRules = rememberBlocklistRuleExporter()
    val database = remember { getContentFilterDatabase() }
    val coroutineScope = rememberCoroutineScope()
    val nlpSupported = isBlocklistNlpSupported

    var reload by remember { mutableIntStateOf(0) }
    val retry: () -> Unit = { reload++ }
    val keywords by remember(reload) {
        database.blockedKeywordDao().observeKeywordsByType(KeywordType.EXACT_MATCH.name).asListLoadState()
    }.collectAsState(LoadState.Loading)
    val users by remember(reload) { database.blockedUserDao().observeAllUsers().asListLoadState() }
        .collectAsState(LoadState.Loading)
    val questionAuthors by remember(reload) { database.blockedQuestionAuthorDao().observeAllUsers().asListLoadState() }
        .collectAsState(LoadState.Loading)
    val topics by remember(reload) { database.blockedTopicDao().observeAllTopics().asListLoadState() }
        .collectAsState(LoadState.Loading)
    val nlpPhrases by remember(reload) {
        if (nlpSupported) {
            database.blockedKeywordDao().observeKeywordsByType(KeywordType.NLP_SEMANTIC.name).asListLoadState()
        } else {
            flowOf<LoadState<List<BlockedKeyword>>>(LoadState.Ready(emptyList()))
        }
    }.collectAsState(LoadState.Loading)

    val tabs = remember(nlpSupported) { BlocklistTab.entries.filter { it != BlocklistTab.Nlp || nlpSupported } }
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    val selectedPosition = selectedIndex.coerceIn(0, tabs.lastIndex)
    val selectedTab = tabs[selectedPosition]
    val tabTitles = tabs.map { tab ->
        val count = when (tab) {
            BlocklistTab.Keywords -> keywords.readyValue?.size
            BlocklistTab.Nlp -> nlpPhrases.readyValue?.size
            BlocklistTab.Users -> users.readyValue?.size
            BlocklistTab.QuestionAuthors -> questionAuthors.readyValue?.size
            BlocklistTab.Topics -> topics.readyValue?.size
        }
        if (count == null) tab.label else "${tab.label} $count"
    }

    fun launchAction(failurePrefix: String, action: suspend () -> Unit) {
        coroutineScope.launch {
            try {
                action()
                currentCoroutineContext().ensureActive()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("BlocklistSettingsScreen", failurePrefix, e)
                userMessages.showShortMessage("$failurePrefix：${e.message}")
            }
        }
    }

    var addingTab by rememberSaveable { mutableStateOf<BlocklistTab?>(null) }
    var submitting by remember { mutableStateOf(false) }

    // 确认按钮的禁用要等下一帧重组才生效，同一帧里的第二次点击只能靠这里拦住。
    fun submitRule(successMessage: String, insert: suspend () -> Unit) {
        if (submitting) return
        submitting = true
        val owner = addingTab
        coroutineScope.launch {
            try {
                insert()
                currentCoroutineContext().ensureActive()
                userMessages.showShortMessage(successMessage)
                if (addingTab == owner) addingTab = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("BlocklistSettingsScreen", "Failed to add blocklist rule", e)
                userMessages.showShortMessage("添加失败：${e.message}")
            } finally {
                submitting = false
            }
        }
    }

    val requestImport = rememberBlocklistRuleImporter(userMessages) { summary ->
        userMessages.showLongMessage("导入成功：$summary")
    }

    var fabHeightPx by remember { mutableIntStateOf(0) }
    val fabClearance = with(LocalDensity.current) { fabHeightPx.toDp() } + AppDimens.Space32
    val layoutDirection = LocalLayoutDirection.current
    val tabStateHolder = rememberSaveableStateHolder()

    AppScaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            AppCompactTopBar(
                title = "屏蔽列表",
                onBack = navigator.onNavigateBack,
                backTag = BLOCKLIST_SETTINGS_BACK_BUTTON_TAG,
                actions = {
                    AppIconButton(
                        onClick = { requestImport() },
                        modifier = Modifier.testTag(BlocklistSettingsTestTags.IMPORT_BUTTON),
                    ) {
                        Icon(Icons.Outlined.FileDownload, contentDescription = "导入规则")
                    }
                    AppIconButton(
                        onClick = { launchAction("导出失败") { userMessages.showLongMessage(exportRules()) } },
                        modifier = Modifier.testTag(BlocklistSettingsTestTags.EXPORT_BUTTON),
                    ) {
                        Icon(Icons.Outlined.FileUpload, contentDescription = "导出规则")
                    }
                },
            )
        },
        floatingActionButton = {
            if (selectedTab.canAdd) {
                AppFloatingActionButton(
                    modifier = Modifier
                        .testTag(BlocklistSettingsTestTags.FAB)
                        .onSizeChanged { fabHeightPx = it.height },
                    onClick = { if (!submitting) addingTab = selectedTab },
                ) {
                    Icon(Icons.Default.Add, contentDescription = "添加")
                }
            }
        },
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = scaffoldPadding.calculateStartPadding(layoutDirection),
                    top = scaffoldPadding.calculateTopPadding(),
                    end = scaffoldPadding.calculateEndPadding(layoutDirection),
                ).testTag(BlocklistSettingsTestTags.ROOT),
        ) {
            AppTabRow(
                titles = tabTitles,
                selectedIndex = selectedPosition,
                onSelect = { selectedIndex = it },
                modifier = Modifier.testTag(BlocklistSettingsTestTags.TAB_ROW),
                scrollable = true,
                testTags = tabs.indices.map { "blocklistSettings:tab:$it" },
            )
            val bottomPadding = scaffoldPadding.calculateBottomPadding()
            val listPadding = PaddingValues(bottom = bottomPadding + fabClearance)
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                // 每个标签各存一份列表滚动位置：切走再切回、旋转屏幕后都回到原处。
                tabStateHolder.SaveableStateProvider(selectedTab.name) {
                    val listState = rememberLazyListState()
                    when (selectedTab) {
                        BlocklistTab.Keywords -> BlocklistItemList(
                            state = keywords,
                            onRetry = retry,
                            description = "标题、摘要或正文包含这些词的内容不会出现在首页。",
                            emptyTitle = "暂无精确匹配关键词",
                            emptyDescription = "点击右下角的 + 按钮添加传统关键词屏蔽。",
                            clearTitle = { count -> "清空 $count 个屏蔽关键词？" },
                            clearSummary = "首页将不再按这些关键词屏蔽内容。清空后无法撤销，如需保留请先导出规则。",
                            onClearAll = {
                                launchAction("清空失败") {
                                    database.blockedKeywordDao().deleteKeywordsByType(KeywordType.EXACT_MATCH.name)
                                }
                            },
                            itemKey = { it.id },
                            listState = listState,
                            contentPadding = listPadding,
                            listTag = BlocklistSettingsTestTags.KEYWORD_LIST,
                            clearTag = BlocklistSettingsTestTags.KEYWORD_CLEAR_BUTTON,
                            clearDialogTag = BlocklistSettingsTestTags.CLEAR_DIALOG,
                        ) { keyword ->
                            BlocklistItemRow(
                                title = keyword.keyword,
                                description = listOfNotNull(
                                    "区分大小写".takeIf { keyword.caseSensitive },
                                    "正则表达式".takeIf { keyword.isRegex },
                                ).joinToString(" · ").ifEmpty { "精确匹配" },
                                deleteDescription = "删除屏蔽关键词“${keyword.keyword}”",
                                onDelete = {
                                    launchAction("删除失败") { database.blockedKeywordDao().deleteKeywordById(keyword.id) }
                                },
                                modifier = Modifier.testTag("blocklistSettings:keywords:item:${keyword.id}"),
                                deleteTag = "blocklistSettings:keywords:delete:${keyword.id}",
                            )
                        }

                        BlocklistTab.Nlp -> Box(Modifier.fillMaxSize().padding(bottom = bottomPadding)) {
                            nlpContent(navigator.onNavigateBack)
                        }

                        BlocklistTab.Users -> BlocklistItemList(
                            state = users,
                            onRetry = retry,
                            description = "这些用户的内容在首页、问题、搜索和评论区都会被隐藏。",
                            emptyTitle = "暂无屏蔽用户",
                            emptyDescription = "点击右下角的 + 按钮添加，也可以在内容卡片的更多菜单里屏蔽作者。",
                            clearTitle = { count -> "清空 $count 个屏蔽用户？" },
                            clearSummary = "这些用户的内容会重新显示。清空后无法撤销，如需保留请先导出规则。",
                            onClearAll = { launchAction("清空失败") { database.blockedUserDao().clearAllUsers() } },
                            itemKey = { it.userId },
                            listState = listState,
                            contentPadding = listPadding,
                            listTag = BlocklistSettingsTestTags.USER_LIST,
                            clearTag = BlocklistSettingsTestTags.USER_CLEAR_BUTTON,
                            clearDialogTag = BlocklistSettingsTestTags.CLEAR_DIALOG,
                        ) { user ->
                            BlockedPersonRow(
                                category = "users",
                                noun = "屏蔽用户",
                                userId = user.userId,
                                userName = user.userName,
                                avatarUrl = user.avatarUrl,
                                onDelete = {
                                    launchAction("删除失败") { database.blockedUserDao().deleteUserById(user.userId) }
                                },
                                onClick = {
                                    navigator.onNavigate(Person(id = user.userId, urlToken = user.urlToken ?: "", name = user.userName))
                                },
                            )
                        }

                        BlocklistTab.QuestionAuthors -> BlocklistItemList(
                            state = questionAuthors,
                            onRetry = retry,
                            description = "屏蔽这些用户提出的问题，不影响他们回答别人的问题。",
                            emptyTitle = "暂无屏蔽提问者",
                            emptyDescription = "点击右下角的 + 按钮添加。",
                            clearTitle = { count -> "清空 $count 个屏蔽提问者？" },
                            clearSummary = "这些用户提出的问题会重新出现在首页。清空后无法撤销，如需保留请先导出规则。",
                            onClearAll = { launchAction("清空失败") { database.blockedQuestionAuthorDao().clearAllUsers() } },
                            itemKey = { it.userId },
                            listState = listState,
                            contentPadding = listPadding,
                            listTag = BlocklistSettingsTestTags.QUESTION_AUTHOR_LIST,
                            clearTag = BlocklistSettingsTestTags.QUESTION_AUTHOR_CLEAR_BUTTON,
                            clearDialogTag = BlocklistSettingsTestTags.CLEAR_DIALOG,
                        ) { author ->
                            BlockedPersonRow(
                                category = "questionAuthors",
                                noun = "屏蔽提问者",
                                userId = author.userId,
                                userName = author.userName,
                                avatarUrl = author.avatarUrl,
                                onDelete = {
                                    launchAction("删除失败") { database.blockedQuestionAuthorDao().deleteUserById(author.userId) }
                                },
                                onClick = {
                                    navigator.onNavigate(Person(id = author.userId, urlToken = author.urlToken ?: "", name = author.userName))
                                },
                            )
                        }

                        BlocklistTab.Topics -> BlocklistItemList(
                            state = topics,
                            onRetry = retry,
                            description = "命中的屏蔽主题数达到阈值的内容不会出现在首页。",
                            emptyTitle = "暂无屏蔽主题",
                            emptyDescription = "点击右下角的 + 按钮添加要屏蔽的主题。",
                            clearTitle = { count -> "清空 $count 个屏蔽主题？" },
                            clearSummary = "首页将不再按这些主题屏蔽内容。清空后无法撤销，如需保留请先导出规则。",
                            onClearAll = { launchAction("清空失败") { database.blockedTopicDao().clearAllTopics() } },
                            itemKey = { it.topicId },
                            listState = listState,
                            contentPadding = listPadding,
                            listTag = BlocklistSettingsTestTags.TOPIC_LIST,
                            clearTag = BlocklistSettingsTestTags.TOPIC_CLEAR_BUTTON,
                            clearDialogTag = BlocklistSettingsTestTags.CLEAR_DIALOG,
                        ) { topic ->
                            BlocklistItemRow(
                                title = topic.topicName,
                                description = "ID: ${topic.topicId}",
                                deleteDescription = "删除屏蔽主题“${topic.topicName}”",
                                onDelete = {
                                    launchAction("删除失败") { database.blockedTopicDao().deleteTopicById(topic.topicId) }
                                },
                                modifier = Modifier.testTag("blocklistSettings:topics:item:${topic.topicId}"),
                                deleteTag = "blocklistSettings:topics:delete:${topic.topicId}",
                            )
                        }
                    }
                }
            }
        }
    }

    addingTab?.let { dialogTab ->
        key(dialogTab) {
            val dismiss: () -> Unit = { if (!submitting) addingTab = null }
            when (dialogTab) {
                BlocklistTab.Keywords -> AddKeywordDialog(
                    submitting = submitting,
                    onDismiss = dismiss,
                    onConfirm = { keyword, caseSensitive, isRegex ->
                        submitRule("已添加关键词") {
                            database.blockedKeywordDao().insertKeyword(
                                BlockedKeyword(
                                    keyword = keyword.trim(),
                                    keywordType = KeywordType.EXACT_MATCH.name,
                                    caseSensitive = caseSensitive,
                                    isRegex = isRegex,
                                ),
                            )
                        }
                    },
                )

                BlocklistTab.Users -> AddUserDialog(
                    submitting = submitting,
                    onDismiss = dismiss,
                    onConfirm = { userId, userName ->
                        submitRule("已添加用户") {
                            database.blockedUserDao().insertUser(BlockedUser(userId = userId, userName = userName))
                        }
                    },
                )

                BlocklistTab.QuestionAuthors -> AddUserDialog(
                    title = "添加屏蔽提问者",
                    hint = "添加后会屏蔽该用户提出的问题，不影响该用户回答别人问题的内容",
                    submitting = submitting,
                    onDismiss = dismiss,
                    onConfirm = { userId, userName ->
                        submitRule("已添加屏蔽提问者") {
                            database.blockedQuestionAuthorDao().insertUser(BlockedQuestionAuthor(userId = userId, userName = userName))
                        }
                    },
                )

                BlocklistTab.Topics -> AddTopicDialog(
                    submitting = submitting,
                    onDismiss = dismiss,
                    onConfirm = { topicId, topicName ->
                        submitRule("已添加主题") {
                            database.blockedTopicDao().insertTopic(BlockedTopic(topicId = topicId, topicName = topicName))
                        }
                    },
                )

                BlocklistTab.Nlp -> Unit
            }
        }
    }
}

@Composable
fun <T> BlocklistItemList(
    state: LoadState<List<T>>,
    onRetry: () -> Unit,
    description: String,
    emptyTitle: String,
    emptyDescription: String,
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
                        text = "清空",
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
            confirmText = "清空",
            confirmEnabled = rows.isNotEmpty(),
            onConfirm = {
                if (showClearDialog && rows.isNotEmpty()) {
                    showClearDialog = false
                    onClearAll()
                }
            },
            dismissText = "取消",
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
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = deleteDescription,
                        tint = LocalAppSkinTokens.current.error,
                    )
                }
                if (onClick != null) {
                    Icon(
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
        description = "ID: $userId",
        deleteDescription = "删除$noun“$userName”",
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

@Composable
private fun AddKeywordDialog(
    submitting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, Boolean, Boolean) -> Unit,
) {
    var keyword by rememberSaveable { mutableStateOf("") }
    var caseSensitive by rememberSaveable { mutableStateOf(false) }
    var isRegex by rememberSaveable { mutableStateOf(false) }

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加屏蔽关键词") },
        text = {
            Column {
                AppTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    labelText = "关键词",
                    placeholder = "输入要屏蔽的关键词",
                    enabled = !submitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(BlocklistSettingsTestTags.KEYWORD_DIALOG_INPUT),
                )
                Spacer(modifier = Modifier.height(AppDimens.Space8))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(BlocklistSettingsTestTags.KEYWORD_DIALOG_CASE_SENSITIVE)
                        .toggleable(value = caseSensitive, enabled = !submitting, role = Role.Checkbox, onValueChange = { caseSensitive = it }),
                ) {
                    AppCheckbox(
                        modifier = Modifier.clearAndSetSemantics {},
                        checked = caseSensitive,
                        onCheckedChange = null,
                        enabled = !submitting,
                    )
                    Spacer(modifier = Modifier.width(AppDimens.Space8))
                    Text("区分大小写")
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(BlocklistSettingsTestTags.KEYWORD_DIALOG_REGEX)
                        .toggleable(value = isRegex, enabled = !submitting, role = Role.Checkbox, onValueChange = { isRegex = it }),
                ) {
                    AppCheckbox(
                        modifier = Modifier.clearAndSetSemantics {},
                        checked = isRegex,
                        onCheckedChange = null,
                        enabled = !submitting,
                    )
                    Spacer(modifier = Modifier.width(AppDimens.Space8))
                    Text("正则表达式")
                }
                if (isRegex) {
                    Text(
                        "提示：使用正则表达式可以实现更灵活的匹配，但语法错误会导致该关键词无效",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = AppDimens.Space8),
                    )
                }
            }
        },
        confirmButton = {
            AppButton(
                text = "添加",
                onClick = { if (keyword.isNotBlank()) onConfirm(keyword, caseSensitive, isRegex) },
                modifier = Modifier.testTag(BlocklistSettingsTestTags.KEYWORD_DIALOG_CONFIRM),
                enabled = keyword.isNotBlank() && !submitting,
            )
        },
        dismissButton = {
            AppTextButton(
                text = "取消",
                onClick = onDismiss,
                modifier = Modifier.testTag(BlocklistSettingsTestTags.KEYWORD_DIALOG_DISMISS),
                enabled = !submitting,
            )
        },
    )
}

@Composable
private fun AddUserDialog(
    submitting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
    title: String = "添加屏蔽用户",
    hint: String = "提示：可以从用户主页的URL中获取用户ID",
) {
    var userId by rememberSaveable { mutableStateOf("") }
    var userName by rememberSaveable { mutableStateOf("") }

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                AppTextField(
                    value = userId,
                    onValueChange = { userId = it },
                    labelText = "用户ID",
                    placeholder = "输入用户ID",
                    enabled = !submitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(BlocklistSettingsTestTags.USER_DIALOG_ID_INPUT),
                )
                Spacer(modifier = Modifier.height(AppDimens.Space8))
                AppTextField(
                    value = userName,
                    onValueChange = { userName = it },
                    labelText = "用户名",
                    placeholder = "输入用户名（可选）",
                    enabled = !submitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(BlocklistSettingsTestTags.USER_DIALOG_NAME_INPUT),
                )
                Spacer(modifier = Modifier.height(AppDimens.Space8))
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            AppButton(
                text = "添加",
                onClick = { if (userId.isNotBlank()) onConfirm(userId, userName.ifBlank { userId }) },
                modifier = Modifier.testTag(BlocklistSettingsTestTags.USER_DIALOG_CONFIRM),
                enabled = userId.isNotBlank() && !submitting,
            )
        },
        dismissButton = {
            AppTextButton(
                text = "取消",
                onClick = onDismiss,
                modifier = Modifier.testTag(BlocklistSettingsTestTags.USER_DIALOG_DISMISS),
                enabled = !submitting,
            )
        },
    )
}

@Composable
private fun AddTopicDialog(
    submitting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var topicId by rememberSaveable { mutableStateOf("") }
    var topicName by rememberSaveable { mutableStateOf("") }

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加屏蔽主题") },
        text = {
            Column {
                Text(
                    "输入要屏蔽的主题ID和名称。主题ID可以从知乎网页版主题链接中获取。",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(modifier = Modifier.height(AppDimens.Space16))
                AppTextField(
                    value = topicId,
                    onValueChange = { topicId = it },
                    labelText = "主题ID",
                    placeholder = "例如: 19550517",
                    singleLine = true,
                    enabled = !submitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(BlocklistSettingsTestTags.TOPIC_DIALOG_ID_INPUT),
                )
                Spacer(modifier = Modifier.height(AppDimens.Space8))
                AppTextField(
                    value = topicName,
                    onValueChange = { topicName = it },
                    labelText = "主题名称",
                    placeholder = "例如: 娱乐",
                    singleLine = true,
                    enabled = !submitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(BlocklistSettingsTestTags.TOPIC_DIALOG_NAME_INPUT),
                )
            }
        },
        confirmButton = {
            AppButton(
                text = "添加",
                onClick = { if (topicId.isNotBlank()) onConfirm(topicId, topicName.ifBlank { topicId }) },
                modifier = Modifier.testTag(BlocklistSettingsTestTags.TOPIC_DIALOG_CONFIRM),
                enabled = topicId.isNotBlank() && !submitting,
            )
        },
        dismissButton = {
            AppTextButton(
                text = "取消",
                onClick = onDismiss,
                modifier = Modifier.testTag(BlocklistSettingsTestTags.TOPIC_DIALOG_DISMISS),
                enabled = !submitting,
            )
        },
    )
}
