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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MarkUnreadChatAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import com.github.zly2006.zhihu.navigation.Navigator
import com.github.zly2006.zhihu.navigation.WriteAnswerChooser
import com.github.zly2006.zhihu.navigation.WriteArticle
import com.github.zly2006.zhihu.navigation.WritePin
import com.github.zly2006.zhihu.navigation.WriteQuestion
import com.github.zly2006.zhihu.ui.components.AppBottomSheet
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.SettingItem
import com.github.zly2006.zhihu.ui.components.AppIcon as Icon

@Composable
private fun HomeCreateAction(
    title: String,
    subtitle: String,
    icon: ImageVector,
    testTag: String,
    onClick: () -> Unit,
) {
    SettingItem(
        title = { Text(title) },
        description = { Text(subtitle) },
        icon = { Icon(icon, contentDescription = null) },
        modifier = Modifier
            .testTag(testTag),
        onClick = onClick,
        endAction = {},
    )
}

/** 「创作」底部菜单：提问题、写回答、写文章、发想法；选中后先收起菜单再跳转。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeCreateMenuSheet(
    navigator: Navigator,
    onDismiss: () -> Unit,
) {
    AppBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(HOME_CREATE_MENU_TAG),
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        ),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = AppDimens.Space24),
        ) {
            Text(
                text = "创作",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(
                    start = AppDimens.Space24,
                    end = AppDimens.Space24,
                    top = AppDimens.Space8,
                    bottom = AppDimens.Space8,
                ),
            )
            HomeCreateAction(
                title = "提问题",
                subtitle = "向知友提问，获取回答",
                icon = Icons.AutoMirrored.Default.HelpOutline,
                testTag = HOME_WRITE_QUESTION_BUTTON_TAG,
            ) {
                onDismiss()
                navigator.onNavigate(WriteQuestion)
            }
            HomeCreateAction(
                title = "写回答",
                subtitle = "找一个问题，分享你的见解",
                icon = Icons.Default.Edit,
                testTag = HOME_WRITE_ANSWER_BUTTON_TAG,
            ) {
                onDismiss()
                navigator.onNavigate(WriteAnswerChooser(defaultSelectedPage = 1))
            }
            HomeCreateAction(
                title = "写文章",
                subtitle = "发布一篇长文",
                icon = Icons.Default.Description,
                testTag = "home_write_article_button",
            ) {
                onDismiss()
                navigator.onNavigate(WriteArticle)
            }
            HomeCreateAction(
                title = "发想法",
                subtitle = "发布一条简短的想法",
                icon = Icons.Default.MarkUnreadChatAlt,
                testTag = HOME_WRITE_PIN_BUTTON_TAG,
            ) {
                onDismiss()
                navigator.onNavigate(WritePin())
            }
        }
    }
}
