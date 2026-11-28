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
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import com.github.zly2006.zhihu.i18n.AccountStrings
import com.github.zly2006.zhihu.ui.components.AppCompactTopBar
import com.github.zly2006.zhihu.ui.components.AppFloatingActionButton
import com.github.zly2006.zhihu.ui.components.AppIcon
import com.github.zly2006.zhihu.ui.components.AppIconButton
import com.github.zly2006.zhihu.ui.components.AppScaffold
import com.github.zly2006.zhihu.ui.components.AppTabRow
import com.github.zly2006.zhihu.viewmodel.LoadState
import com.github.zly2006.zhihu.viewmodel.filter.BlockedKeyword
import com.github.zly2006.zhihu.viewmodel.filter.BlockedQuestionAuthor
import com.github.zly2006.zhihu.viewmodel.filter.BlockedTopic
import com.github.zly2006.zhihu.viewmodel.filter.BlockedUser
import com.github.zly2006.zhihu.viewmodel.readyValue

internal enum class BlocklistTab(
    private val labelText: () -> String,
    val canAdd: Boolean,
) {
    Keywords({ AccountStrings.blockedKeywords }, true),
    Nlp({ AccountStrings.nlpBlocking }, false),
    Users({ AccountStrings.blockedUsers }, true),
    QuestionAuthors({ AccountStrings.blockedQuestionAuthors }, true),
    Topics({ AccountStrings.blockedTopics }, true),
    ;

    val label: String get() = labelText()
}

internal fun blocklistTabTitles(
    tabs: List<BlocklistTab>,
    keywords: LoadState<List<BlockedKeyword>>,
    nlpPhrases: LoadState<List<BlockedKeyword>>,
    users: LoadState<List<BlockedUser>>,
    questionAuthors: LoadState<List<BlockedQuestionAuthor>>,
    topics: LoadState<List<BlockedTopic>>,
): List<String> = tabs.map { tab ->
    val count = when (tab) {
        BlocklistTab.Keywords -> keywords.readyValue?.size
        BlocklistTab.Nlp -> nlpPhrases.readyValue?.size
        BlocklistTab.Users -> users.readyValue?.size
        BlocklistTab.QuestionAuthors -> questionAuthors.readyValue?.size
        BlocklistTab.Topics -> topics.readyValue?.size
    }
    if (count == null) tab.label else "${tab.label} $count"
}

@Composable
internal fun BlocklistSettingsLayout(
    selectedTab: BlocklistTab,
    tabs: List<BlocklistTab>,
    tabTitles: List<String>,
    selectedPosition: Int,
    fabClearance: Dp,
    layoutDirection: LayoutDirection,
    onBack: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onFabHeightChange: (Int) -> Unit,
    onAdd: () -> Unit,
    onSelect: (Int) -> Unit,
    content: @Composable (Dp, PaddingValues) -> Unit,
) {
    AppScaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            BlocklistSettingsTopBar(
                onBack = onBack,
                onImport = onImport,
                onExport = onExport,
            )
        },
        floatingActionButton = {
            if (selectedTab.canAdd) {
                AppFloatingActionButton(
                    modifier = Modifier
                        .testTag(BlocklistSettingsTestTags.FAB)
                        .onSizeChanged { onFabHeightChange(it.height) },
                    onClick = onAdd,
                ) {
                    AppIcon(Icons.Default.Add, contentDescription = AccountStrings.add)
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
                onSelect = onSelect,
                modifier = Modifier.testTag(BlocklistSettingsTestTags.TAB_ROW),
                scrollable = true,
                testTags = tabs.indices.map { "blocklistSettings:tab:$it" },
            )
            val bottomPadding = scaffoldPadding.calculateBottomPadding()
            val listPadding = PaddingValues(bottom = bottomPadding + fabClearance)
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                content(bottomPadding, listPadding)
            }
        }
    }
}

@Composable
private fun BlocklistSettingsTopBar(
    onBack: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
) {
    AppCompactTopBar(
        title = AccountStrings.blocklistTitle,
        onBack = onBack,
        backTag = BLOCKLIST_SETTINGS_BACK_BUTTON_TAG,
        actions = {
            AppIconButton(
                onClick = onImport,
                modifier = Modifier.testTag(BlocklistSettingsTestTags.IMPORT_BUTTON),
            ) {
                AppIcon(Icons.Outlined.FileDownload, contentDescription = AccountStrings.importRules)
            }
            AppIconButton(
                onClick = onExport,
                modifier = Modifier.testTag(BlocklistSettingsTestTags.EXPORT_BUTTON),
            ) {
                AppIcon(Icons.Outlined.FileUpload, contentDescription = AccountStrings.exportRules)
            }
        },
    )
}
