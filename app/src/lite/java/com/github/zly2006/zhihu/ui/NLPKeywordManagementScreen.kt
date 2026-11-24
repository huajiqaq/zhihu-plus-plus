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

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable

/** Lite 不提供 NLP 屏蔽，屏蔽列表也不显示这个标签（`isBlocklistNlpSupported = false`）；误调用时直接失败。 */
@Composable
fun NLPKeywordManagementScreen(
    @Suppress("UNUSED_PARAMETER") innerPadding: PaddingValues,
    @Suppress("UNUSED_PARAMETER") onNavigateBack: () -> Unit,
): Unit = error("Lite 版不支持 NLP 智能屏蔽设置")
