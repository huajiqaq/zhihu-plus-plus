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

/*
 * 转场的组合方式（navDirectionalTransition 分派 push / pop / predictivePop，各自用
 * navGraphicsTransition 描述图层）改编自 Miuix 示例 CrossActivityTransition.kt：
 * https://github.com/compose-miuix-ui/miuix/blob/main/example/shared/src/commonMain/kotlin/navigation/CrossActivityTransition.kt
 * Copyright 2026, compose-miuix-ui contributors，原示例以 Apache License 2.0 授权。
 * 时长、位移与跟手曲线为本项目取值。
 */

package com.github.zly2006.zhihu.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.nav.transition.NavMotion
import top.yukonga.miuix.kmp.nav.transition.NavRole
import top.yukonga.miuix.kmp.nav.transition.NavSettlePhase
import top.yukonga.miuix.kmp.nav.transition.NavSettleSpec
import top.yukonga.miuix.kmp.nav.transition.NavSwipeEdge
import top.yukonga.miuix.kmp.nav.transition.NavTransition
import top.yukonga.miuix.kmp.nav.transition.navDirectionalTransition
import top.yukonga.miuix.kmp.nav.transition.navGraphicsTransition

private val PageMotion = NavMotion(
    programmatic = NavSettleSpec.Tween(
        150,
        Easing { progress ->
            val remaining = 1f - progress
            1f - remaining * remaining * remaining
        },
    ),
)

private val PageTransition = navGraphicsTransition(
    opaqueDepth = 1f,
    motion = PageMotion,
    scrim = { 0f },
) { scope ->
    if (scope.relativeDepth <= 0f) {
        val visible = (1f + scope.relativeDepth).coerceIn(0f, 1f)
        translationX = (1f - visible) * with(scope.density) { 48.dp.toPx() }
        alpha = if (scope.role == NavRole.Incoming || scope.role == NavRole.Outgoing) visible else 1f
    }
}

private val BackDecelerate = CubicBezierEasing(0f, 0f, 0f, 1f)

/** 系统返回先短距离跟手，提交后才把当前页移出；下层始终是完整的原条目。 */
private val PredictivePageTransition = navGraphicsTransition(
    opaqueDepth = 1f,
    motion = NavMotion(
        commit = NavSettleSpec.Tween(340, CubicBezierEasing(0.23f, 1f, 0.32f, 1f)),
        cancel = NavSettleSpec.Tween(280, CubicBezierEasing(0.23f, 1f, 0.32f, 1f)),
    ),
    scrim = { it.relativeDepth.coerceIn(0f, 1f) },
) { scope ->
    if (scope.relativeDepth <= 0f) {
        val gesture = scope.gesture
        val progress = (-scope.relativeDepth).coerceIn(0f, 1f)
        val followDistance = with(scope.density) { 56.dp.toPx() }
        val direction = if (gesture?.swipeEdge == NavSwipeEdge.Right) -1f else 1f
        translationX = direction * if (scope.settle?.phase == NavSettlePhase.Commit && gesture != null) {
            val released = gesture.progress.coerceIn(0f, 1f)
            val from = followDistance * BackDecelerate.transform(released)
            val settled = if (released >= 1f) 1f else ((progress - released) / (1f - released)).coerceIn(0f, 1f)
            from + (scope.layoutSize.width + followDistance - from) * settled
        } else {
            followDistance * BackDecelerate.transform(progress)
        }
    }
}

/** 设置项“页面转场”取值 `aosp` 时使用的转场。 */
internal val SystemStyleNavTransition: NavTransition = navDirectionalTransition(
    push = PageTransition,
    pop = PageTransition,
    predictivePop = PredictivePageTransition,
)
