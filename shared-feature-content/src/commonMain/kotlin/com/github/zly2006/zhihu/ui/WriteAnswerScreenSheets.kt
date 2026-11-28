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

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.zly2006.zhihu.i18n.ContentStrings
import com.github.zly2006.zhihu.ui.components.AppBottomSheet
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.SettingItemWithSwitch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WriteAnswerSettingsSheet(
    sheetState: SheetState,
    tocEnabled: Boolean,
    onDismissRequest: () -> Unit,
    onTocEnabledChange: (Boolean) -> Unit,
) {
    AppBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Text(
            text = ContentStrings.answerSettings,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = AppDimens.PageHorizontalPadding, vertical = 8.dp),
        )
        Text(
            text = ContentStrings.tocModesHelp,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = AppDimens.PageHorizontalPadding),
        )
        Spacer(Modifier.height(16.dp))
        SettingItemWithSwitch(
            title = { Text(ContentStrings.generateToc) },
            description = {
                Text(ContentStrings.tocHelp)
            },
            checked = tocEnabled,
            onCheckedChange = onTocEnabledChange,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Spacer(Modifier.height(20.dp))
    }
}
