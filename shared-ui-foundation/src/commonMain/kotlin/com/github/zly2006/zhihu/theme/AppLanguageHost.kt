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

package com.github.zly2006.zhihu.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.github.zly2006.zhihu.i18n.APP_LANGUAGE_KEY
import com.github.zly2006.zhihu.i18n.AppLocale
import com.github.zly2006.zhihu.platform.SettingsStore
import com.github.zly2006.zhihu.platform.applySavedAppLanguage

/** 更新文案状态而不替换下游组合身份，语言切换不重建导航或编辑器。 */
@Composable
internal fun AppLanguageHost(settings: SettingsStore?, content: @Composable () -> Unit) {
    if (settings != null) {
        remember(settings) { applySavedAppLanguage(settings) }
        DisposableEffect(settings) {
            val changes = settings.observeKeyChanges { if (it == APP_LANGUAGE_KEY) applySavedAppLanguage(settings) }
            applySavedAppLanguage(settings)
            onDispose(changes::close)
        }
    }
    PlatformLanguageContent(AppLocale.languageTag, content)
}

@Composable
internal expect fun PlatformLanguageContent(languageTag: String, content: @Composable () -> Unit)
