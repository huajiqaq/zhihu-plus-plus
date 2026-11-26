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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.Dp
import com.github.zly2006.zhihu.theme.LocalMiuixStyle
import com.github.zly2006.zhihu.theme.MiuixStyle
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.material.GlassEdgeStrokes
import top.yukonga.miuix.kmp.material.edgeStroke
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported

val LocalAppBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/** 仅由移到根 Scaffold 绘制的弹层读取。 */
internal val LocalAppPopupBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }
val LocalAppGlassEnabled = compositionLocalOf { false }

fun appGlassRenderingSupported(): Boolean = isRuntimeShaderSupported()

@Composable
internal fun useMiuixGlassStyle(): Boolean =
    settingSkinIsMiuix() &&
        LocalMiuixStyle.current == MiuixStyle.HYPEROS_4 &&
        LocalAppGlassEnabled.current &&
        appGlassRenderingSupported()

@Composable
fun Modifier.appGlassBlur(
    shape: Shape,
    tint: Color,
    radius: Dp = AppDimens.GlassBlurRadius,
): Modifier {
    val backdrop = LocalAppBackdrop.current ?: return this
    if (!LocalAppGlassEnabled.current || !isRuntimeShaderSupported()) return this
    return textureBlur(
        backdrop = backdrop,
        shape = shape,
        blurRadius = radius.value,
        colors = BlurColors(blendColors = listOf(BlendColorEntry(color = tint))),
    )
}

@Composable
fun Modifier.appFloatingGlassSurface(
    shape: Shape,
    fallbackContainer: Color,
    tint: Color,
    borderColor: Color,
    isDark: Boolean,
): Modifier {
    val backdrop = LocalAppBackdrop.current
    val glassEnabled = LocalAppGlassEnabled.current && backdrop != null && isRuntimeShaderSupported()
    if (glassEnabled && useMiuixGlassStyle() && shape is RoundedCornerShape) {
        return miuixFloatingGlassSurface(shape, isDark)
    }
    val blend = remember(tint) { BlurColors(blendColors = listOf(BlendColorEntry(tint))) }
    return dropShadow(
        shape = shape,
        shadow = Shadow(
            radius = AppDimens.Space12,
            color = Color.Black,
            alpha = if (isDark) 0.32f else 0.14f,
        ),
    ).then(
        if (glassEnabled) {
            Modifier
                .textureBlur(
                    backdrop = backdrop,
                    shape = shape,
                    blurRadius = AppDimens.GlassBarBlurRadius.value,
                    noiseCoefficient = 0f,
                    colors = blend,
                ).edgeStroke(shape, GlassEdgeStrokes.forTheme(isDark), alpha = 0.8f)
        } else {
            Modifier
        },
    ).background(if (glassEnabled) Color.Transparent else fallbackContainer, shape)
        .then(if (glassEnabled) Modifier else Modifier.border(AppDimens.GlassFallbackBorderWidth, borderColor, shape))
}

@Composable
internal expect fun Modifier.miuixFloatingGlassSurface(
    shape: RoundedCornerShape,
    isDark: Boolean,
): Modifier

@Composable
fun appGlassContainerColor(color: Color): Color {
    if (LocalAppBackdrop.current == null || !LocalAppGlassEnabled.current) return color
    return if (isRuntimeShaderSupported()) Color.Transparent else color
}
