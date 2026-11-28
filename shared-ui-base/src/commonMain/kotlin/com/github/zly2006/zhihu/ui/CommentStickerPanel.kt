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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import com.github.zly2006.zhihu.editor.ImagePickerLauncher
import com.github.zly2006.zhihu.editor.ZhihuImageUploadSource
import com.github.zly2006.zhihu.editor.isImagePickerSupported
import com.github.zly2006.zhihu.editor.rememberImagePickerLauncher
import com.github.zly2006.zhihu.editor.uploadZhihuImage
import com.github.zly2006.zhihu.i18n.CommentsStrings
import com.github.zly2006.zhihu.platform.UserMessageSink
import com.github.zly2006.zhihu.ui.components.AppSpinner
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment
import com.github.zly2006.zhihu.viewmodel.comment.CommentSticker
import com.github.zly2006.zhihu.viewmodel.comment.CommentStickerGroup
import com.github.zly2006.zhihu.viewmodel.comment.StickerAlreadyCollectedException
import com.github.zly2006.zhihu.viewmodel.comment.collectCommentSticker
import com.github.zly2006.zhihu.viewmodel.comment.fetchCollectedStickers
import com.github.zly2006.zhihu.viewmodel.comment.fetchGroupStickers
import com.github.zly2006.zhihu.viewmodel.comment.fetchRecommendedStickers
import com.github.zly2006.zhihu.viewmodel.comment.fetchStickerGroups
import com.github.zly2006.zhihu.viewmodel.comment.removeCollectedCommentSticker
import com.github.zly2006.zhihu.viewmodel.comment.uploadCommentSticker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 表情面板里贴纸部分在一次重组内的状态与操作入口。
 * 每次重组新建实例；状态对象由 [rememberCommentStickerPanelState] 记住，读取发生在使用处。
 */
internal class CommentStickerPanelState(
    emojiEntryState: MutableState<EmojiPanelEntry>,
    collectedStickersState: State<List<CommentSticker>>,
    recommendedStickersState: State<List<CommentSticker>>,
    stickerGroupsState: State<List<CommentStickerGroup>>,
    groupStickersState: State<List<CommentSticker>>,
    emojiPanelLoadingState: State<Boolean>,
    isUploadingStickerState: State<Boolean>,
    /** 平台不支持选图时为 null。 */
    val launchStickerPicker: ImagePickerLauncher?,
    val collectSticker: (CommentSticker) -> Unit,
    val removeCollectedSticker: (CommentSticker) -> Unit,
) {
    var emojiEntry by emojiEntryState
    val collectedStickers by collectedStickersState
    val recommendedStickers by recommendedStickersState
    val stickerGroups by stickerGroupsState
    val groupStickers by groupStickersState
    val emojiPanelLoading by emojiPanelLoadingState
    val isUploadingSticker by isUploadingStickerState
}

/** 贴纸数据随评论页存活：打开表情面板时加载分组，切换分组时按需加载并缓存。 */
@Composable
internal fun rememberCommentStickerPanelState(
    emojiPickerState: State<Boolean>,
    isSendingState: State<Boolean>,
    paginationEnvironment: PaginationEnvironment,
    userMessages: UserMessageSink,
    coroutineScope: CoroutineScope,
): CommentStickerPanelState {
    val showEmojiPicker by emojiPickerState
    val isSending by isSendingState
    val emojiEntryState = remember { mutableStateOf<EmojiPanelEntry>(EmojiPanelEntry.Default) }
    var emojiEntry by emojiEntryState
    val collectedStickersState = remember { mutableStateOf<List<CommentSticker>>(emptyList()) }
    var collectedStickers by collectedStickersState
    val recommendedStickersState = remember { mutableStateOf<List<CommentSticker>>(emptyList()) }
    var recommendedStickers by recommendedStickersState
    val stickerGroupsState = remember { mutableStateOf<List<CommentStickerGroup>>(emptyList()) }
    var stickerGroups by stickerGroupsState
    val groupStickersState = remember { mutableStateOf<List<CommentSticker>>(emptyList()) }
    var groupStickers by groupStickersState
    val groupStickerCache = remember { mutableStateMapOf<String, List<CommentSticker>>() }
    var favoritesLoaded by remember { mutableStateOf(false) }
    val emojiPanelLoadingState = remember { mutableStateOf(false) }
    var emojiPanelLoading by emojiPanelLoadingState
    var stickerGroupsLoading by remember { mutableStateOf(false) }
    val isUploadingStickerState = remember { mutableStateOf(false) }
    var isUploadingSticker by isUploadingStickerState

    val launchStickerPicker = if (isImagePickerSupported) {
        rememberImagePickerLauncher { picked ->
            if (isUploadingSticker || isSending) return@rememberImagePickerLauncher
            isUploadingSticker = true
            coroutineScope.launch {
                try {
                    val uploaded = uploadZhihuImage(
                        paginationEnvironment,
                        picked.bytes,
                        picked.mimeType,
                        picked.fileName,
                        ZhihuImageUploadSource.Zim,
                    )
                    val token = uploaded.hash
                        ?: throw IllegalStateException(CommentsStrings.imageTokenMissing)
                    val uploadedStickers = uploadCommentSticker(paginationEnvironment, token)
                    collectedStickers = (uploadedStickers + collectedStickers).distinctBy { it.id }
                    emojiEntry = EmojiPanelEntry.Favorites
                    userMessages.showShortMessage(CommentsStrings.stickerUploaded)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: StickerAlreadyCollectedException) {
                    try {
                        collectedStickers = fetchCollectedStickers(paginationEnvironment).stickers
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        userMessages.showShortMessage(CommentsStrings.refreshStickersFailed)
                    }
                    emojiEntry = EmojiPanelEntry.Favorites
                    userMessages.showShortMessage(CommentsStrings.stickerAlreadySaved)
                } catch (e: Exception) {
                    userMessages.showShortMessage(CommentsStrings.stickerUploadFailed(e.message.orEmpty()))
                } finally {
                    isUploadingSticker = false
                }
            }
        }
    } else {
        null
    }

    LaunchedEffect(showEmojiPicker) {
        if (!showEmojiPicker || stickerGroups.isNotEmpty() || stickerGroupsLoading) {
            return@LaunchedEffect
        }
        stickerGroupsLoading = true
        try {
            stickerGroups = fetchStickerGroups(paginationEnvironment)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            userMessages.showShortMessage(CommentsStrings.stickerGroupsFailed)
        } finally {
            stickerGroupsLoading = false
        }
    }

    LaunchedEffect(emojiEntry, showEmojiPicker) {
        if (!showEmojiPicker) return@LaunchedEffect
        when (val entry = emojiEntry) {
            EmojiPanelEntry.Default -> Unit

            EmojiPanelEntry.Favorites -> if (!favoritesLoaded && !emojiPanelLoading) {
                emojiPanelLoading = true
                try {
                    collectedStickers = fetchCollectedStickers(paginationEnvironment).stickers
                    recommendedStickers = fetchRecommendedStickers(paginationEnvironment).stickers
                    favoritesLoaded = true
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    userMessages.showShortMessage(CommentsStrings.savedStickersFailed)
                } finally {
                    emojiPanelLoading = false
                }
            }

            is EmojiPanelEntry.Group -> {
                val cached = groupStickerCache[entry.group.id]
                if (cached != null) {
                    groupStickers = cached
                } else if (!emojiPanelLoading) {
                    emojiPanelLoading = true
                    try {
                        val stickers = fetchGroupStickers(paginationEnvironment, entry.group.id)
                        groupStickers = stickers
                        if (stickers.isNotEmpty()) {
                            groupStickerCache[entry.group.id] = stickers
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        groupStickers = emptyList()
                        userMessages.showShortMessage(CommentsStrings.stickersLoadFailed)
                    } finally {
                        emojiPanelLoading = false
                    }
                }
            }
        }
    }

    fun removeCollectedSticker(sticker: CommentSticker) {
        coroutineScope.launch {
            try {
                removeCollectedCommentSticker(
                    paginationEnvironment,
                    sticker.id,
                )
                collectedStickers = collectedStickers - sticker
                userMessages.showShortMessage(CommentsStrings.stickerUnsaved)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                userMessages.showShortMessage(CommentsStrings.unsaveStickerFailed(e.message.orEmpty()))
            }
        }
    }

    fun collectSticker(sticker: CommentSticker) {
        coroutineScope.launch {
            try {
                collectCommentSticker(paginationEnvironment, sticker.id)
                recommendedStickers = recommendedStickers.filterNot { it.id == sticker.id }
                collectedStickers = (
                    listOf(sticker.copy(collected = true)) +
                        collectedStickers
                ).distinctBy { it.id }
                userMessages.showShortMessage(CommentsStrings.stickerSaved)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                userMessages.showShortMessage(CommentsStrings.saveStickerFailed(e.message.orEmpty()))
            }
        }
    }

    return CommentStickerPanelState(
        emojiEntryState = emojiEntryState,
        collectedStickersState = collectedStickersState,
        recommendedStickersState = recommendedStickersState,
        stickerGroupsState = stickerGroupsState,
        groupStickersState = groupStickersState,
        emojiPanelLoadingState = emojiPanelLoadingState,
        isUploadingStickerState = isUploadingStickerState,
        launchStickerPicker = launchStickerPicker,
        collectSticker = ::collectSticker,
        removeCollectedSticker = ::removeCollectedSticker,
    )
}

/** 表情面板内容：默认表情、收藏和各贴纸分组。 */
@Composable
internal fun CommentScreenEmojiPanel(
    panel: CommentStickerPanelState,
    commentEmojis: List<CommentEmoji>,
    emojiInlineContent: Map<String, InlineTextContent>,
    onEmojiClick: (String) -> Unit,
    onStickerClick: (CommentSticker) -> Unit,
    /** 系统选图窗口启动前通知 owner，避免窗口失焦卸载注册与上传协程。 */
    onPickerLaunched: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .testTag(COMMENT_EMOJI_PICKER_TAG),
    ) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item(key = "default") {
                EmojiPanelChip(
                    selected = panel.emojiEntry == EmojiPanelEntry.Default,
                    label = CommentsStrings.defaultEmojis,
                    onClick = { panel.emojiEntry = EmojiPanelEntry.Default },
                    testTag = COMMENT_EMOJI_TAB_TAG_PREFIX + "default",
                )
            }
            item(key = "favorites") {
                EmojiPanelChip(
                    selected = panel.emojiEntry == EmojiPanelEntry.Favorites,
                    label = CommentsStrings.saved,
                    icon = Icons.Filled.Favorite,
                    onClick = { panel.emojiEntry = EmojiPanelEntry.Favorites },
                    testTag = COMMENT_EMOJI_TAB_TAG_PREFIX + "favorites",
                )
            }
            items(
                items = panel.stickerGroups.filter { !it.isVip && it.type != "emoji" },
                key = { "group_${it.id}" },
            ) { group ->
                EmojiPanelChip(
                    selected = (panel.emojiEntry as? EmojiPanelEntry.Group)
                        ?.group
                        ?.id == group.id,
                    label = group.title.ifBlank { CommentsStrings.sticker },
                    onClick = { panel.emojiEntry = EmojiPanelEntry.Group(group) },
                    testTag = COMMENT_EMOJI_TAB_TAG_PREFIX + "group_${group.id}",
                )
            }
            items(
                items = panel.stickerGroups.filter { it.isVip },
                key = { "vip_${it.id}" },
            ) { group ->
                EmojiPanelChip(
                    selected = (panel.emojiEntry as? EmojiPanelEntry.Group)
                        ?.group
                        ?.id == group.id,
                    label = group.title.ifBlank { CommentsStrings.memberStickers },
                    isVip = true,
                    onClick = { panel.emojiEntry = EmojiPanelEntry.Group(group) },
                    testTag = COMMENT_EMOJI_TAB_TAG_PREFIX + "vip_${group.id}",
                )
            }
        }

        if (panel.emojiPanelLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                AppSpinner(size = 24.dp)
            }
        } else {
            when (val entry = panel.emojiEntry) {
                EmojiPanelEntry.Default -> DefaultCommentEmojiGrid(
                    emojis = commentEmojis,
                    emojiInlineContent = emojiInlineContent,
                    onEmojiClick = { emoji ->
                        onEmojiClick(emoji.placeholder)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )

                EmojiPanelEntry.Favorites -> FavoritesStickerPanel(
                    collected = panel.collectedStickers,
                    recommended = panel.recommendedStickers,
                    isUploading = panel.isUploadingSticker,
                    onUpload = panel.launchStickerPicker?.let { launcher ->
                        {
                            onPickerLaunched()
                            launcher.launch()
                        }
                    },
                    onCollectedClick = { sticker ->
                        onStickerClick(sticker)
                    },
                    onCollectedLongClick = panel.removeCollectedSticker,
                    onRecommendedClick = { sticker ->
                        onStickerClick(sticker)
                    },
                    onRecommendedLongClick = panel.collectSticker,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
                is EmojiPanelEntry.Group -> StickerGrid(
                    stickers = panel.groupStickers,
                    showTitles = true,
                    onClick = { sticker ->
                        onStickerClick(sticker)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }
        }
    }
}
