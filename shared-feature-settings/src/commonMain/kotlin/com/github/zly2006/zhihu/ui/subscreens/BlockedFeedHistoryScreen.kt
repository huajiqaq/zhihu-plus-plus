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
import com.github.zly2006.zhihu.i18n.SettingsStrings
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.ui.BlocklistItemList
import com.github.zly2006.zhihu.ui.BlocklistItemRow
import com.github.zly2006.zhihu.ui.components.AppCompactTopBar
import com.github.zly2006.zhihu.ui.components.AppScaffold
import com.github.zly2006.zhihu.ui.components.pageTurnViewportWithGuide
import com.github.zly2006.zhihu.ui.components.rememberPageTurnTarget
import com.github.zly2006.zhihu.util.AppLog
import com.github.zly2006.zhihu.util.twoDigitString
import com.github.zly2006.zhihu.viewmodel.LoadState
import com.github.zly2006.zhihu.viewmodel.filter.BlockedFeedRecord
import com.github.zly2006.zhihu.viewmodel.filter.displayBlockedReason
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
                AppLog.e("BlockedFeedHistoryScreen", failurePrefix, e)
                userMessages.showShortMessage(SettingsStrings.failureWithReason(failurePrefix, e.message))
            }
        }
    }

    AppScaffold(
        topBar = {
            AppCompactTopBar(
                title = SettingsStrings.blockedHistory,
                onBack = navigator.onNavigateBack,
                backTag = BLOCKED_FEED_HISTORY_BACK_BUTTON_TAG,
            )
        },
    ) { innerPadding ->
        BlocklistItemList(
            state = records,
            onRetry = { reload++ },
            description = SettingsStrings.blockedHistoryScope(BlockedFeedRecord.MAX_RECORDS),
            emptyTitle = SettingsStrings.noBlockedHistory,
            clearTitle = { count -> SettingsStrings.clearBlockedCount(count) },
            clearSummary = SettingsStrings.clearBlockedHistoryWarning,
            onClearAll = { launchAction(SettingsStrings.clearFailed) { dao.clearAll() } },
            itemKey = { it.id },
            modifier = Modifier.fillMaxSize().pageTurnViewportWithGuide(pageTurnTarget),
            listState = listState,
            contentPadding = innerPadding,
            listTag = "blocked_feed_history_list",
            clearTag = BLOCKED_FEED_HISTORY_CLEAR_TAG,
        ) { record ->
            BlockedFeedRecordRow(
                record = record,
                onDelete = { launchAction(SettingsStrings.deleteFailed) { dao.deleteById(record.id) } },
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
        record.displayBlockedReason,
        formatBlockedTime(record.blockedTime),
    ).joinToString(" · ")
    BlocklistItemRow(
        title = record.title.ifBlank { SettingsStrings.untitled },
        description = if (destination == null) SettingsStrings.originalContentUnavailable(meta) else meta,
        deleteDescription = SettingsStrings.deleteBlockedRecord,
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
