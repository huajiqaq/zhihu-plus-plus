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

import androidx.compose.animation.core.LinearEasing
import top.yukonga.miuix.kmp.nav.transition.NavMotion
import top.yukonga.miuix.kmp.nav.transition.NavSettleSpec
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import top.yukonga.miuix.kmp.nav.transition.NavTransition
import top.yukonga.miuix.kmp.nav.transition.NavTransitions

fun appNavTransition(preset: String = "miuix", enabled: Boolean = true): NavTransition = when {
    !enabled -> ImmediateNavTransition
    preset == "aosp" -> AospNavTransition
    else -> NavTransitions.MiuixDefault
}

fun appNavSwipeDismiss(isRtl: Boolean): NavSwipeDirection =
    if (isRtl) NavSwipeDirection.RightToLeft else NavSwipeDirection.LeftToRight

private object ImmediateNavTransition : NavTransition by NavTransitions.None {
    // None 只移除视觉变换仍保留默认 settle；关闭时连退场和取消也须即时结束。
    override val motion: NavMotion = NavMotion(
        commit = NavSettleSpec.Tween(0, LinearEasing),
        cancel = NavSettleSpec.Tween(0, LinearEasing),
        programmatic = NavSettleSpec.Tween(0, LinearEasing),
    )
}
