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

import com.github.zly2006.zhihu.i18n.ThemeStrings

const val ANSWER_DOUBLE_TAP_ACTION_PREFERENCE_KEY = "answerDoubleTapAction"

enum class AnswerDoubleTapAction(
    val preferenceValue: String,
    private val actionLabel: () -> String,
) {
    None(
        preferenceValue = "none",
        actionLabel = ThemeStrings::doubleTapNone,
    ),
    Ask(
        preferenceValue = "ask",
        actionLabel = ThemeStrings::doubleTapAsk,
    ),
    VoteUp(
        preferenceValue = "voteUp",
        actionLabel = ThemeStrings::doubleTapVoteUp,
    ),
    OpenComments(
        preferenceValue = "openComments",
        actionLabel = ThemeStrings::doubleTapComments,
    ),
    ToggleImmersive(
        preferenceValue = "toggleImmersive",
        actionLabel = ThemeStrings::doubleTapImmersive,
    ),
    JumpToTop(
        preferenceValue = "jumpToTop",
        actionLabel = ThemeStrings::doubleTapTop,
    ),
    JumpToBottom(
        preferenceValue = "jumpToBottom",
        actionLabel = ThemeStrings::doubleTapBottom,
    ),
    ;

    val label: String get() = actionLabel()

    companion object {
        fun fromPreference(value: String?): AnswerDoubleTapAction = entries.firstOrNull {
            it.preferenceValue == value
        } ?: Ask
    }
}
