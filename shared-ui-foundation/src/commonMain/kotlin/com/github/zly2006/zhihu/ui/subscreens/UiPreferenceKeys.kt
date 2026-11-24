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

package com.github.zly2006.zhihu.ui.subscreens

import com.github.zly2006.zhihu.platform.SettingsStore

const val TIQIAN_MARKDOWN_PREFERENCE_KEY = "tiqian_markdown"
const val TIQIAN_MATH_FONT_PREFERENCE_KEY = "tiqian_math_font"
const val APP_BLUR_PREFERENCE_KEY = "appBlurEnabled"
const val FEED_CARD_STYLE_PREFERENCE_KEY = "feedCardStyle"

const val FLOATING_BOTTOM_BAR_PREFERENCE_KEY = "floatingBottomBar"
const val FLOATING_BOTTOM_BAR_STYLE_PREFERENCE_KEY = "floatingBottomBarStyle"
const val PAGE_TRANSITION_PREFERENCE_KEY = "pageTransitionStyle"

/** 正式键优先；只迁入已有旧值，不写缺省值或删除旧键。 */
fun migrateLegacyReadingPreferences(settings: SettingsStore) {
    val keys = settings.allEntries().keys
    if (TIQIAN_MARKDOWN_PREFERENCE_KEY !in keys && "duo3_tiqian_markdown" in keys) {
        settings.putBoolean(TIQIAN_MARKDOWN_PREFERENCE_KEY, settings.getBoolean("duo3_tiqian_markdown", false))
    }
    if (TIQIAN_MATH_FONT_PREFERENCE_KEY !in keys && "duo3_tiqian_math_font" in keys) {
        settings.putString(TIQIAN_MATH_FONT_PREFERENCE_KEY, settings.getString("duo3_tiqian_math_font", "lete"))
    }
}
