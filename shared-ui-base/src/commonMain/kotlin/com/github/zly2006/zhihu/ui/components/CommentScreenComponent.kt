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

package com.github.zly2006.zhihu.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.CommentHolder
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.Pin
import com.github.zly2006.zhihu.platform.exportTestTagsForUiAutomation
import com.github.zly2006.zhihu.platform.platformName
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.ui.CommentScreen
import com.github.zly2006.zhihu.ui.commentThreadKey
import com.github.zly2006.zhihu.ui.consumePendingCommentId
import com.github.zly2006.zhihu.viewmodel.CommentItem
import kotlinx.coroutines.flow.merge
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/** Android 的系统栏和 IME 由评论窗口布局共同避让，不能依赖底层 Activity 的 adjustResize。 */
internal expect val commentDialogProperties: DialogProperties

/** 独立评论窗口的菜单留在自身 Scaffold；移动端 IME 返回由独立编辑窗口处理。 */
@Composable
private fun CommentDialogSheet(
    onDismissRequest: () -> Unit,
    content: @Composable (MutableState<Boolean>) -> Unit,
) {
    val emojiPickerState = remember { mutableStateOf(false) }
    RegisterOverlayBlur()
    Dialog(
        onDismissRequest = {
            if (emojiPickerState.value) {
                emojiPickerState.value = false
            } else {
                onDismissRequest()
            }
        },
        properties = commentDialogProperties,
    ) {
        AppScaffold(
            modifier = if (platformName == "Android") Modifier.safeDrawingPadding() else Modifier,
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) {
            Box(
                modifier = Modifier
                    .exportTestTagsForUiAutomation()
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f)),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.08f)
                        .align(Alignment.TopCenter)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClickLabel = "关闭评论",
                            onClick = onDismissRequest,
                        ),
                )
                Box(
                    modifier = Modifier
                        .widthIn(max = AppDimens.ArticleReadingMaxWidth)
                        .fillMaxWidth()
                        .fillMaxHeight(0.92f),
                ) {
                    content(emojiPickerState)
                }
            }
        }
    }
}

/** 草稿、子目标和滚动状态跨关闭与导航保存，调用方不要条件性卸载此组件。 */
@Composable
fun CommentScreenComponent(
    showComments: Boolean,
    onDismiss: () -> Unit,
    content: NavDestination,
    isZhPlusAuthorContent: Boolean = false,
    commentSegments: Map<String, com.github.zly2006.zhihu.util.SegmentHighlightSpan> = emptyMap(),
    rootHeader: (@Composable () -> Unit)? = null,
) {
    val settings = rememberSettingsStore()
    val initialPendingCommentId = consumePendingCommentId(content)
    var pendingCommentId by remember(content) {
        mutableStateOf(initialPendingCommentId)
    }
    val entryLifecycle = LocalLifecycleOwner.current.lifecycle
    val hostLifecycle = LocalNavHostLifecycleOwner.current?.lifecycle
    var entryOnTop by remember(entryLifecycle, hostLifecycle) {
        mutableStateOf(
            hostLifecycle == null ||
                hostLifecycle.currentState != Lifecycle.State.RESUMED ||
                entryLifecycle.currentState == Lifecycle.State.RESUMED,
        )
    }
    var externalPickerActive by remember { mutableStateOf(false) }
    LaunchedEffect(entryLifecycle, hostLifecycle) {
        merge(entryLifecycle.currentStateFlow, hostLifecycle?.currentStateFlow ?: entryLifecycle.currentStateFlow).collect {
            if (entryLifecycle.currentState == Lifecycle.State.RESUMED) externalPickerActive = false
            /** 宿主暂停或选图保持组合；仅前台页面覆盖隐藏窗口，不执行 dismiss。 */
            if (hostLifecycle == null || hostLifecycle.currentState == Lifecycle.State.RESUMED) {
                entryOnTop = entryLifecycle.currentState == Lifecycle.State.RESUMED
            }
        }
    }
    val commentsWanted = showComments || pendingCommentId != null
    val commentsVisible = commentsWanted && (entryOnTop || (hostLifecycle == null && externalPickerActive))
    var authorCommentPolicyAcknowledged by remember {
        mutableStateOf(settings.getBoolean(ZH_PLUS_AUTHOR_COMMENT_POLICY_ACKNOWLEDGED_KEY, false))
    }
    val contentStateKey = content.commentThreadKey()
    var activeChildComment by rememberSaveable(contentStateKey, saver = activeChildCommentSaver) {
        mutableStateOf<CommentItem?>(null)
    }
    var pendingChildComment by remember(contentStateKey) {
        mutableStateOf<DataHolder.Comment?>(null)
    }
    var commentDrafts by rememberSaveable(contentStateKey) {
        mutableStateOf<Map<String, String>>(emptyMap())
    }
    val sentenceCommentsState = rememberSaveable(contentStateKey) { mutableStateOf(false) }
    var rootListResetToken by rememberSaveable(contentStateKey) { mutableIntStateOf(0) }
    val sentenceListState = rememberSaveable(contentStateKey, rootListResetToken, saver = LazyListState.Saver) {
        LazyListState()
    }
    val rootListState = rememberSaveable(
        contentStateKey,
        rootListResetToken,
        saver = LazyListState.Saver,
    ) {
        LazyListState()
    }
    val childTarget = activeChildComment?.clickTarget
    val childDraftKey = childTarget?.commentThreadKey()
    var childListResetToken by rememberSaveable(contentStateKey) { mutableIntStateOf(0) }
    val childListState = rememberSaveable(
        contentStateKey,
        childDraftKey,
        childListResetToken,
        saver = LazyListState.Saver,
    ) {
        LazyListState()
    }

    fun dismissRootComments() {
        pendingCommentId = null
        pendingChildComment = null
        activeChildComment = null
        rootListResetToken += 1
        childListResetToken += 1
        onDismiss()
    }

    fun dismissChildComments() {
        pendingChildComment = null
        activeChildComment = null
        childListResetToken += 1
    }

    fun updateCommentDraft(key: String, value: String) {
        commentDrafts = if (value.isEmpty()) {
            commentDrafts - key
        } else {
            commentDrafts + (key to value)
        }
    }

    val voicePlayer = rememberVoiceCommentPlayer()
    CompositionLocalProvider(LocalVoiceCommentPlayer provides voicePlayer) {
        if (commentsVisible) {
            CommentDialogSheet(onDismissRequest = ::dismissRootComments) { emojiPickerState ->
                CommentScreen(
                    content = { content },
                    onDismiss = ::dismissRootComments,
                    initialCommentId = pendingCommentId,
                    onChildCommentClick = { commentItem ->
                        if (commentItem.clickTarget != null) {
                            pendingChildComment = null
                            activeChildComment = commentItem
                        }
                    },
                    commentInput = commentDrafts[contentStateKey].orEmpty(),
                    onCommentInputChange = { updateCommentDraft(contentStateKey, it) },
                    listState = rootListState,
                    commentSegments = commentSegments,
                    sentenceCommentsState = sentenceCommentsState,
                    sentenceListState = sentenceListState,
                    pageTurnEnabled =
                        (content is Article || content is Pin) &&
                            activeChildComment == null &&
                            (authorCommentPolicyAcknowledged || !isZhPlusAuthorContent),
                    showPageTurnFab = content is Pin,
                    onInitialChildCommentResolved = { rootComment, childComment ->
                        pendingChildComment = childComment
                        activeChildComment = rootComment
                    },
                    rootHeader = rootHeader,
                    emojiPickerState = emojiPickerState,
                    onPickerLaunched = { externalPickerActive = true },
                )
            }
        }

        if (commentsVisible && activeChildComment != null && childTarget != null && childDraftKey != null) {
            CommentDialogSheet(onDismissRequest = ::dismissChildComments) { emojiPickerState ->
                CommentScreen(
                    content = { childTarget },
                    onDismiss = ::dismissChildComments,
                    activeCommentItem = activeChildComment,
                    onChildCommentClick = { },
                    commentInput = commentDrafts[childDraftKey].orEmpty(),
                    onCommentInputChange = { updateCommentDraft(childDraftKey, it) },
                    listState = childListState,
                    commentSegments = commentSegments,
                    initialComment = pendingChildComment,
                    pageTurnEnabled = childTarget.article is Article || childTarget.article is Pin,
                    showPageTurnFab = childTarget.article is Pin,
                    emojiPickerState = emojiPickerState,
                    onPickerLaunched = { externalPickerActive = true },
                )
            }
        }
    }

    if (commentsVisible && isZhPlusAuthorContent && !authorCommentPolicyAcknowledged) {
        AppAlertDialog(
            modifier = Modifier.testTag(ZH_PLUS_AUTHOR_COMMENT_POLICY_DIALOG_TAG),
            onDismissRequest = {},
            title = { Text("评论区使用须知") },
            text = {
                Column {
                    Text(
                        "请勿通过知乎提交任何 Bug 反馈或功能建议。所有反馈仅在 GitHub Issues 处理；" +
                            "不要发送到任何交流群，也不要发送到知乎++作者的回答或想法评论区。",
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "评论区只可发布与当前回答或想法相关的内容。无关内容将被无视，严重时会被直接拉黑。",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            confirmButton = {
                AppTextButton(
                    modifier = Modifier.testTag(ZH_PLUS_AUTHOR_COMMENT_POLICY_CONFIRM_TAG),
                    onClick = {
                        settings.putBoolean(ZH_PLUS_AUTHOR_COMMENT_POLICY_ACKNOWLEDGED_KEY, true)
                        authorCommentPolicyAcknowledged = true
                    },
                ) {
                    Text("我已知晓并确认")
                }
            },
        )
    }
}

const val ZH_PLUS_AUTHOR_COMMENT_POLICY_ACKNOWLEDGED_KEY = "zhPlusAuthorCommentPolicyAcknowledged"
const val ZH_PLUS_AUTHOR_COMMENT_POLICY_DIALOG_TAG = "zh_plus_author_comment_policy_dialog"
const val ZH_PLUS_AUTHOR_COMMENT_POLICY_CONFIRM_TAG = "zh_plus_author_comment_policy_confirm"

private val activeChildCommentSaver = Saver<MutableState<CommentItem?>, List<String>>(
    save = { state ->
        val commentItem = state.value
        val target = commentItem?.clickTarget
        if (commentItem == null || target == null) {
            emptyList()
        } else {
            listOf(
                ZhihuJson.json.encodeToString(commentItem.item),
                ZhihuJson.json.encodeToString(target),
            )
        }
    },
    restore = { saved ->
        mutableStateOf(
            if (saved.size != 2) {
                null
            } else {
                runCatching {
                    CommentItem(
                        item = ZhihuJson.json.decodeFromString<DataHolder.Comment>(saved[0]),
                        clickTarget = ZhihuJson.json.decodeFromString<CommentHolder>(saved[1]),
                    )
                }.getOrNull()
            },
        )
    },
)
