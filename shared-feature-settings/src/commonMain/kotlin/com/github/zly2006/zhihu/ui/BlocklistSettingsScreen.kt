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

import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import com.github.zly2006.zhihu.filter.asListLoadState
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.Person
import com.github.zly2006.zhihu.platform.isBlocklistNlpSupported
import com.github.zly2006.zhihu.platform.platformName
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.util.Log
import com.github.zly2006.zhihu.viewmodel.LoadState
import com.github.zly2006.zhihu.viewmodel.filter.BlockedKeyword
import com.github.zly2006.zhihu.viewmodel.filter.BlockedQuestionAuthor
import com.github.zly2006.zhihu.viewmodel.filter.BlockedTopic
import com.github.zly2006.zhihu.viewmodel.filter.BlockedUser
import com.github.zly2006.zhihu.viewmodel.filter.KeywordScope
import com.github.zly2006.zhihu.viewmodel.filter.KeywordType
import com.github.zly2006.zhihu.viewmodel.filter.getContentFilterDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

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

    var keywordScope by rememberSaveable { mutableStateOf(KeywordScope.ALL) }
    var reload by remember { mutableIntStateOf(0) }
    val retry: () -> Unit = { reload++ }
    val keywords by remember(reload, keywordScope) {
        database.blockedKeywordDao().observeKeywordsByType(KeywordType.EXACT_MATCH.name, keywordScope.name).asListLoadState()
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
    val tabTitles = blocklistTabTitles(tabs, keywords, nlpPhrases, users, questionAuthors, topics)

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

    BlocklistSettingsLayout(
        selectedTab = selectedTab,
        tabs = tabs,
        tabTitles = tabTitles,
        selectedPosition = selectedPosition,
        fabClearance = fabClearance,
        layoutDirection = layoutDirection,
        onBack = navigator.onNavigateBack,
        onImport = { requestImport() },
        onExport = { launchAction("导出失败") { userMessages.showLongMessage(exportRules()) } },
        onFabHeightChange = { fabHeightPx = it },
        onAdd = { if (!submitting) addingTab = selectedTab },
        onSelect = { selectedIndex = it },
    ) { bottomPadding, listPadding ->
        // 每个标签各存一份列表滚动位置：切走再切回、旋转屏幕后都回到原处。
        tabStateHolder.SaveableStateProvider(if (selectedTab == BlocklistTab.Keywords) "${selectedTab.name}:${keywordScope.name}" else selectedTab.name) {
            val listState = rememberLazyListState()
            BlocklistSettingsLists(
                selectedTab = selectedTab,
                keywords = keywords,
                users = users,
                questionAuthors = questionAuthors,
                topics = topics,
                keywordScope = keywordScope,
                submitting = submitting,
                retry = retry,
                listState = listState,
                listPadding = listPadding,
                bottomPadding = bottomPadding,
                nlpContent = nlpContent,
                onBack = navigator.onNavigateBack,
                onScopeSelect = { keywordScope = it },
                onClearKeywords = {
                    val scopeToClear = keywordScope.name
                    launchAction("清空失败") {
                        database.blockedKeywordDao().deleteKeywordsByType(KeywordType.EXACT_MATCH.name, scopeToClear)
                    }
                },
                onDeleteKeyword = { keyword ->
                    launchAction("删除失败") {
                        database.blockedKeywordDao().deleteKeywordById(keyword.id, KeywordType.EXACT_MATCH.name, keywordScope.name)
                    }
                },
                onClearUsers = { launchAction("清空失败") { database.blockedUserDao().clearAllUsers() } },
                onDeleteUser = { user ->
                    launchAction("删除失败") { database.blockedUserDao().deleteUserById(user.userId) }
                },
                onClickUser = { user ->
                    navigator.onNavigate(Person(id = user.userId, urlToken = user.urlToken ?: "", name = user.userName))
                },
                onClearQuestionAuthors = { launchAction("清空失败") { database.blockedQuestionAuthorDao().clearAllUsers() } },
                onDeleteQuestionAuthor = { author ->
                    launchAction("删除失败") { database.blockedQuestionAuthorDao().deleteUserById(author.userId) }
                },
                onClickQuestionAuthor = { author ->
                    navigator.onNavigate(Person(id = author.userId, urlToken = author.urlToken ?: "", name = author.userName))
                },
                onClearTopics = { launchAction("清空失败") { database.blockedTopicDao().clearAllTopics() } },
                onDeleteTopic = { topic ->
                    launchAction("删除失败") { database.blockedTopicDao().deleteTopicById(topic.topicId) }
                },
            )
        }
    }

    addingTab?.let { dialogTab ->
        key(dialogTab) {
            val dismiss: () -> Unit = { if (!submitting) addingTab = null }
            BlocklistSettingsAddDialog(
                dialogTab = dialogTab,
                submitting = submitting,
                dismiss = dismiss,
                onConfirmKeyword = { keyword, caseSensitive, isRegex ->
                    submitRule("已添加关键词") {
                        database.blockedKeywordDao().insertKeyword(
                            BlockedKeyword(
                                keyword = keyword.trim(),
                                keywordType = KeywordType.EXACT_MATCH.name,
                                scope = keywordScope.name,
                                caseSensitive = caseSensitive,
                                isRegex = isRegex,
                            ),
                        )
                    }
                },
                onConfirmUser = { userId, userName ->
                    submitRule("已添加用户") {
                        database.blockedUserDao().insertUser(BlockedUser(userId = userId, userName = userName))
                    }
                },
                onConfirmQuestionAuthor = { userId, userName ->
                    submitRule("已添加屏蔽提问者") {
                        database.blockedQuestionAuthorDao().insertUser(BlockedQuestionAuthor(userId = userId, userName = userName))
                    }
                },
                onConfirmTopic = { topicId, topicName ->
                    submitRule("已添加主题") {
                        database.blockedTopicDao().insertTopic(BlockedTopic(topicId = topicId, topicName = topicName))
                    }
                },
            )
        }
    }
}
