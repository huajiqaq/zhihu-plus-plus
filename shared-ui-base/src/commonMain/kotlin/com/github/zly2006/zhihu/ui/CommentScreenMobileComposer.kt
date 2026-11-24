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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import com.github.zly2006.zhihu.platform.PlatformBackHandler
import com.github.zly2006.zhihu.platform.UserMessageSink
import com.github.zly2006.zhihu.platform.isSoftwareKeyboardVisible
import com.github.zly2006.zhihu.platform.platformName
import com.github.zly2006.zhihu.ui.components.AppBottomSheet
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppIconButton
import com.github.zly2006.zhihu.ui.components.VoiceCommentRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.github.zly2006.zhihu.ui.components.AppIcon as Icon

/** 移动端的评论入口条和编辑底部面板；录音期间面板不能关闭。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CommentScreenMobileComposer(
    editorTitle: String,
    composerOpen: Boolean,
    onOpen: () -> Unit,
    /** 面板关闭后的收尾：停止试听、收起表情面板、清除输入焦点标记。 */
    onClose: () -> Unit,
    showEmojiPicker: Boolean,
    onHideEmojiPicker: () -> Unit,
    voiceRecorder: VoiceCommentRecorder,
    commentInputFocusRequester: FocusRequester,
    commentInputBarColor: Color,
    titleStyle: TextStyle,
    userMessages: UserMessageSink,
    coroutineScope: CoroutineScope,
    editor: @Composable () -> Unit,
) {
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(AppDimens.InputCornerRadius),
        color = commentInputBarColor,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.PageHorizontalPadding, vertical = AppDimens.Space8)
            .heightIn(min = AppDimens.TouchTarget)
            .testTag(COMMENT_COMPOSER_ENTRY_TAG),
    ) {
        Row(
            Modifier.padding(horizontal = AppDimens.Space16, vertical = AppDimens.Space12),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.Space12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Outlined.Comment, contentDescription = null, modifier = Modifier.size(AppDimens.IconSizeMedium))
            Text("发布评论", style = MaterialTheme.typography.bodyLarge)
        }
    }
    if (composerOpen) {
        val recording by rememberUpdatedState(voiceRecorder.isRecording)
        val sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
            confirmValueChange = { it != SheetValue.Hidden || !recording },
        )
        AppBottomSheet(
            onDismissRequest = onClose,
            modifier = Modifier.testTag(COMMENT_COMPOSER_SHEET_TAG).semantics { paneTitle = editorTitle },
            sheetState = sheetState,
            sheetGesturesEnabled = !recording,
            properties = ModalBottomSheetProperties(shouldDismissOnBackPress = platformName != "Android"),
            containerColor = commentInputBarColor,
        ) {
            val keyboard = LocalSoftwareKeyboardController.current
            val focus = LocalFocusManager.current
            val keyboardVisible = isSoftwareKeyboardVisible()

            fun closeSheet() {
                if (recording) {
                    userMessages.showShortMessage("请先停止录音")
                } else {
                    focus.clearFocus(force = true)
                    keyboard?.hide()
                    coroutineScope.launch {
                        sheetState.hide()
                        if (!sheetState.isVisible) onClose()
                    }
                }
            }
            if (platformName == "Android") {
                PlatformBackHandler(enabled = true) {
                    when {
                        keyboardVisible -> {
                            focus.clearFocus(force = true)
                            keyboard?.hide()
                        }
                        showEmojiPicker -> onHideEmojiPicker()
                        else -> closeSheet()
                    }
                }
            }
            LaunchedEffect(sheetState) {
                snapshotFlow { sheetState.currentValue }.first { it == SheetValue.Expanded }
                commentInputFocusRequester.requestFocus()
                keyboard?.show()
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = AppDimens.PageHorizontalPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(editorTitle, modifier = Modifier.weight(1f), style = titleStyle)
                AppIconButton(onClick = ::closeSheet) {
                    Icon(Icons.Filled.Close, contentDescription = "关闭编辑面板")
                }
            }
            Column(Modifier.verticalScroll(rememberScrollState())) { editor() }
        }
    }
}
