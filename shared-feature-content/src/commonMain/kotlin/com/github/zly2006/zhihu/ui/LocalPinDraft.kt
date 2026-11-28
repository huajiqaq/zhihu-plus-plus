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

import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.editor.UploadedZhihuImage
import kotlinx.serialization.Required
import kotlinx.serialization.Serializable

@Serializable
internal data class LocalPinDraft(
    @Required
    val schema: Int = 2,
    val title: String = "",
    val content: String = "",
    val images: List<UploadedZhihuImage> = emptyList(),
    val titleSelectionStart: Int = 0,
    val titleSelectionEnd: Int = 0,
    val contentSelectionStart: Int = 0,
    val contentSelectionEnd: Int = 0,
)

/** 仅恢复当前格式；失败时调用方保留存储原值。 */
internal fun readLocalPinDraft(raw: String): LocalPinDraft? = runCatching {
    val draft = ZhihuJson.json.decodeFromString<LocalPinDraft>(raw)
    if (draft.schema != 2) return null
    draft.copy(
        titleSelectionStart = draft.titleSelectionStart.coerceIn(0, draft.title.length),
        titleSelectionEnd = draft.titleSelectionEnd.coerceIn(0, draft.title.length),
        contentSelectionStart = draft.contentSelectionStart.coerceIn(0, draft.content.length),
        contentSelectionEnd = draft.contentSelectionEnd.coerceIn(0, draft.content.length),
    )
}.getOrNull()
