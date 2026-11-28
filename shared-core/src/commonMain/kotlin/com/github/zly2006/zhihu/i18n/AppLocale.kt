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

package com.github.zly2006.zhihu.i18n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

const val APP_LANGUAGE_KEY = "appLanguage"

enum class AppLanguage(val tag: String) {
    Chinese("zh-CN"),
    English("en"),
}

/** 仅应用文案使用该状态，不更改进程、浏览器或请求的全局语言。 */
object AppLocale {
    private var preference = "system"
    var current: AppLanguage by mutableStateOf(resolveAppLanguage(preference, systemPreferredLanguages()))
        private set
    val languageTag: String get() = current.tag

    fun select(value: String) {
        preference = value.takeIf { it in setOf("system", "zh", "en") } ?: "system"
        refreshSystemLanguage()
    }

    fun refreshSystemLanguage() {
        current = resolveAppLanguage(preference, systemPreferredLanguages())
    }
}

internal fun resolveAppLanguage(preference: String, systemLanguages: List<String>): AppLanguage {
    when (preference) {
        "zh" -> return AppLanguage.Chinese
        "en" -> return AppLanguage.English
    }
    return systemLanguages.firstNotNullOfOrNull { tag ->
        when (tag.substringBefore('-').substringBefore('_').lowercase()) {
            "zh" -> AppLanguage.Chinese
            "en" -> AppLanguage.English
            else -> null
        }
    } ?: AppLanguage.English
}

internal expect fun systemPreferredLanguages(): List<String>
