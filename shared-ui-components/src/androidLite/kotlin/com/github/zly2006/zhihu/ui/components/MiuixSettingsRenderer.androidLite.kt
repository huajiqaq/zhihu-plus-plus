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

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape

@Composable
internal actual fun MiuixSettingSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier,
    enabled: Boolean,
): Unit = error("Miuix settings switches are not supported by the lite variant")

internal actual object MiuixSettingsRenderer {
    @Composable
    actual fun group(
        modifier: Modifier,
        title: String?,
        header: (@Composable () -> Unit)?,
        footer: (@Composable () -> Unit)?,
        content: @Composable () -> Unit,
    ): Unit = unsupported()

    @Composable
    actual fun row(
        modifier: Modifier,
        title: @Composable () -> Unit,
        description: (@Composable () -> Unit)?,
        icon: (@Composable () -> Unit)?,
        onClick: (() -> Unit)?,
        endAction: (@Composable () -> Unit)?,
        bottomAction: (@Composable () -> Unit)?,
        rowModifier: Modifier,
        enabled: Boolean,
        iconShape: Shape,
    ): Unit = unsupported()

    @Composable
    actual fun select(
        modifier: Modifier,
        title: @Composable () -> Unit,
        description: (@Composable () -> Unit)?,
        icon: (@Composable () -> Unit)?,
        options: List<String>,
        selectedIndex: Int,
        summaries: List<String?>?,
        enabled: Boolean,
        fieldModifier: Modifier,
        settingKey: String?,
        highlightedKey: String,
        onPositioned: ((Int) -> Unit)?,
        bringIntoViewRequester: BringIntoViewRequester?,
        expanded: Boolean,
        onExpandedChange: (Boolean) -> Unit,
        onSelect: (Int) -> Unit,
    ): Unit = unsupported()

    private fun unsupported(): Nothing = error("Miuix settings are not supported by the lite variant")
}
