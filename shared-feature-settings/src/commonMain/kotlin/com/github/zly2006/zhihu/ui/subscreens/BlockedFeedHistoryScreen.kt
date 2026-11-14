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

package com.github.zly2006.zhihu.ui.subscreens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.github.zly2006.zhihu.data.decodeFeedNavDestinationOrNull
import com.github.zly2006.zhihu.filter.asListLoadState
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.ui.BlocklistItemList
import com.github.zly2006.zhihu.ui.BlocklistItemRow
import com.github.zly2006.zhihu.ui.components.AppCompactTopBar
import com.github.zly2006.zhihu.ui.components.AppScaffold
import com.github.zly2006.zhihu.ui.components.pageTurnViewportWithGuide
import com.github.zly2006.zhihu.ui.components.rememberPageTurnTarget
import com.github.zly2006.zhihu.util.Log
import com.github.zly2006.zhihu.util.twoDigitString
import com.github.zly2006.zhihu.viewmodel.LoadState
import com.github.zly2006.zhihu.viewmodel.filter.BlockedFeedRecord
import com.github.zly2006.zhihu.viewmodel.filter.getContentFilterDatabase
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

const val BLOCKED_FEED_HISTORY_BACK_BUTTON_TAG = "blocked_feed_history_back_button"
const val BLOCKED_FEED_HISTORY_CLEAR_TAG = "blocked_feed_history_clear"

/** 屏蔽原因用于解释内容为何消失，新增过滤规则应留下用户可理解的原因。 */
@Composable
fun BlockedFeedHistoryScreen() {
    val navigator = LocalNavigator.current
    val coroutineScope = rememberCoroutineScope()
    val userMessages = rememberUserMessageSink()
    val dao = remember { getContentFilterDatabase().blockedFeedRecordDao() }
    var reload by remember { mutableIntStateOf(0) }
    val records by remember(reload) { dao.observeAll().asListLoadState() }.collectAsState(LoadState.Loading)
    val listState = rememberLazyListState()
    val pageTurnTarget = rememberPageTurnTarget(listState = listState, enabled = true)

    fun launchAction(failurePrefix: String, action: suspend () -> Unit) {
        coroutineScope.launch {
            try {
                action()
                currentCoroutineContext().ensureActive()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("BlockedFeedHistoryScreen", failurePrefix, e)
                userMessages.showShortMessage("$failurePrefix：${e.message}")
            }
        }
    }

    AppScaffold(
        topBar = {
            AppCompactTopBar(
                title = "屏蔽记录",
                onBack = navigator.onNavigateBack,
                backTag = BLOCKED_FEED_HISTORY_BACK_BUTTON_TAG,
            )
        },
    ) { innerPadding ->
        BlocklistItemList(
            state = records,
            onRetry = { reload++ },
            description = "首页因广告、已读、关键词、作者或主题规则过滤掉的内容，保留最近 ${BlockedFeedRecord.MAX_RECORDS} 条。",
            emptyTitle = "暂无屏蔽记录",
            emptyDescription = "首页过滤掉的内容会记录在这里。",
            clearTitle = { count -> "清空 $count 条屏蔽记录？" },
            clearSummary = "只删除这些记录，不影响屏蔽规则；之后过滤的内容仍会继续记录。清空后无法撤销。",
            onClearAll = { launchAction("清空失败") { dao.clearAll() } },
            itemKey = { it.id },
            modifier = Modifier.fillMaxSize().pageTurnViewportWithGuide(pageTurnTarget),
            listState = listState,
            contentPadding = innerPadding,
            listTag = "blocked_feed_history_list",
            clearTag = BLOCKED_FEED_HISTORY_CLEAR_TAG,
        ) { record ->
            BlockedFeedRecordRow(
                record = record,
                onDelete = { launchAction("删除失败") { dao.deleteById(record.id) } },
            )
        }
    }
}

@Composable
private fun BlockedFeedRecordRow(
    record: BlockedFeedRecord,
    onDelete: () -> Unit,
) {
    val navigator = LocalNavigator.current
    val destination = remember(record.navDestinationJson) { record.navDestinationJson?.let(::decodeFeedNavDestinationOrNull) }
    val meta = listOfNotNull(
        record.authorName?.takeIf { it.isNotBlank() },
        record.blockedReason,
        formatBlockedTime(record.blockedTime),
    ).joinToString(" · ")
    BlocklistItemRow(
        title = record.title.ifBlank { "（无标题）" },
        description = if (destination == null) "$meta\n原内容已不可用" else meta,
        deleteDescription = "删除这条屏蔽记录",
        onDelete = onDelete,
        modifier = Modifier.testTag("blocked_feed_history_item_${record.id}"),
        titleMaxLines = 2,
        deleteTag = "blocked_feed_history_delete_${record.id}",
        onClick = destination?.let { target -> { navigator.onNavigate(target) } },
    )
}

private fun formatBlockedTime(timestampMillis: Long): String {
    val dateTime = Instant
        .fromEpochMilliseconds(timestampMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    return "${(dateTime.month.ordinal + 1).twoDigitString()}-" +
        "${dateTime.day.twoDigitString()} " +
        "${dateTime.hour.twoDigitString()}:" +
        dateTime.minute.twoDigitString()
}
