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

// 转场的组合方式（navDirectionalTransition 分派 push / pop / predictivePop，各自用
// navGraphicsTransition 描述图层）改编自 Miuix 示例 CrossActivityTransition.kt：
// https://github.com/compose-miuix-ui/miuix/blob/main/example/shared/src/commonMain/kotlin/navigation/CrossActivityTransition.kt
// Copyright 2026, compose-miuix-ui contributors，原示例以 Apache License 2.0 授权。
// 时长、位移与跟手曲线为本项目取值。

package com.github.zly2006.zhihu.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.nav.transition.NavMotion
import top.yukonga.miuix.kmp.nav.transition.NavRole
import top.yukonga.miuix.kmp.nav.transition.NavSettlePhase
import top.yukonga.miuix.kmp.nav.transition.NavSettleSpec
import top.yukonga.miuix.kmp.nav.transition.NavSwipeEdge
import top.yukonga.miuix.kmp.nav.transition.NavTransition
import top.yukonga.miuix.kmp.nav.transition.NavTransitionScope
import top.yukonga.miuix.kmp.nav.transition.navDirectionalTransition
import top.yukonga.miuix.kmp.nav.transition.navGraphicsTransition

private val PageDecelerate = Easing { progress ->
    val remaining = 1f - progress
    1f - remaining * remaining * remaining
}

private val PageMotion = NavMotion(programmatic = NavSettleSpec.Tween(240, LinearEasing))

private val PageOpenTransition = navGraphicsTransition(
    opaqueDepth = 1f,
    motion = PageMotion,
    scrim = { 0f },
) { scope ->
    if (scope.relativeDepth <= 0f) {
        val visible = PageDecelerate.transform((1f + scope.relativeDepth).coerceIn(0f, 1f))
        val direction = if (scope.layoutDirection == LayoutDirection.Rtl) -1f else 1f
        translationX = direction * (1f - visible) * with(scope.density) { 48.dp.toPx() }
        alpha = if (scope.role == NavRole.Incoming) visible else 1f
    }
}

private val PageCloseTransition = navGraphicsTransition(
    opaqueDepth = 1f,
    motion = PageMotion,
    scrim = { 0f },
) { scope ->
    if (scope.relativeDepth <= 0f) {
        val progress = (-scope.relativeDepth).coerceIn(0f, 1f)
        val direction = if (scope.layoutDirection == LayoutDirection.Rtl) -1f else 1f
        translationX = direction * PageDecelerate.transform(progress) * with(scope.density) { 48.dp.toPx() }
        // 先保持退场内容可辨认，再淡出，避免短动画首帧就露出完整的上一页。
        alpha = 1f - ((progress - 0.45f) / 0.55f).coerceIn(0f, 1f)
    }
}

private val BackDecelerate = CubicBezierEasing(0f, 0f, 0f, 1f)

/** 每个图层单独保存实际绘出的进度，不把系统原始进度当作释放位置。 */
private class BackLayerProgress {
    var visible = 0f
    var commitStart: Float? = null
}

/** 手势只变换当前页；取消归位，提交从释放位置继续移出，下层保留完整的原条目。 */
private object PredictivePageTransition : NavTransition {
    override val motion = NavMotion(
        commit = NavSettleSpec.Tween(340, CubicBezierEasing(0.23f, 1f, 0.32f, 1f)),
        cancel = NavSettleSpec.Tween(280, CubicBezierEasing(0.23f, 1f, 0.32f, 1f)),
    )

    override fun Modifier.transformEntry(scope: NavTransitionScope): Modifier = composed {
        val layer = remember { BackLayerProgress() }
        graphicsLayer {
            if (scope.relativeDepth <= 0f) {
                val gesture = scope.gesture
                val progress = (-scope.relativeDepth).coerceIn(0f, 1f)
                val committing = scope.settle?.phase == NavSettlePhase.Commit && gesture != null
                if (committing) {
                    if (layer.commitStart == null) layer.commitStart = progress
                } else {
                    layer.visible = progress
                    layer.commitStart = null
                }
                val follow = BackDecelerate.transform(layer.visible)
                val followDistance = with(scope.density) { 56.dp.toPx() }
                val direction = when (gesture?.swipeEdge) {
                    NavSwipeEdge.Left -> 1f
                    NavSwipeEdge.Right -> -1f
                    else -> if (scope.layoutDirection == LayoutDirection.Rtl) -1f else 1f
                }
                scaleX = 1f - 0.06f * follow
                scaleY = scaleX
                shape = RoundedCornerShape(24.dp * follow)
                clip = follow > 0f
                val from = followDistance * follow
                translationX = direction * if (committing) {
                    val start = checkNotNull(layer.commitStart)
                    val settled = if (start >= 1f) 1f else ((progress - start) / (1f - start)).coerceIn(0f, 1f)
                    from + (scope.layoutSize.width + followDistance - from) * settled
                } else {
                    from
                }
            }
        }
    }
}

/** 设置项“页面转场”取值 `aosp` 时使用的转场。 */
internal val SystemStyleNavTransition: NavTransition = navDirectionalTransition(
    push = PageOpenTransition,
    pop = PageCloseTransition,
    predictivePop = PredictivePageTransition,
)
