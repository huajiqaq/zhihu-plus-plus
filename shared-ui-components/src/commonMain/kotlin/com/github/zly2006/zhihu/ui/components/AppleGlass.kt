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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.blur.BackdropEffectScope
import top.yukonga.miuix.kmp.blur.BackdropPipeline
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.runtimeShaderEffect

/** 同一页面采样上的胶囊折射材质。 */
@Composable
internal fun Modifier.appAppleGlassSurface(shape: Shape, containerColor: Color, isDark: Boolean): Modifier {
    val backdrop = LocalAppBackdrop.current
    val enabled = LocalAppGlassEnabled.current && backdrop != null && appGlassRenderingSupported()
    val shadow = dropShadow(shape, Shadow(radius = 8.dp, color = Color.Black, alpha = if (isDark) 0.22f else 0.1f))
    val edge = if (isDark) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.65f)
    if (!enabled) return shadow.background(containerColor, shape).border(0.5.dp, edge, shape)
    val pipeline = remember(shape, containerColor, isDark) { AppleGlassPipeline(shape, containerColor, isDark) }
    return shadow.drawBackdrop(checkNotNull(backdrop), pipeline).border(0.5.dp, edge, shape)
}

private data class AppleGlassPipeline(
    override val shape: Shape,
    val tint: Color,
    val dark: Boolean,
) : BackdropPipeline {
    override fun configureEffects(scope: BackdropEffectScope) = with(scope) {
        padding = maxOf(padding, 20.dp.toPx())
        blur(4.dp.toPx())
        // 自定义 shader 与模糊后的采样层使用相同的缩放及扩边坐标。
        val scale = downscaleFactor
        val width = size.width / scale
        val height = size.height / scale
        val origin = padding / scale
        val rim = 10.dp.toPx() / scale
        val bend = 12.dp.toPx() / scale
        runtimeShaderEffect("zhihu-apple-capsule", AppleCapsuleShader, "scene") {
            setFloatUniform("extent", width, height)
            setFloatUniform("origin", origin)
            setFloatUniform("rim", rim)
            setFloatUniform("bend", bend)
            setFloatUniform("tintRed", tint.red)
            setFloatUniform("tintGreen", tint.green)
            setFloatUniform("tintBlue", tint.blue)
            setFloatUniform("tintAmount", if (dark) 0.3f else 0.2f)
            setFloatUniform("edgeLight", if (dark) 0.1f else 0.16f)
        }
    }
}

private const val AppleCapsuleShader = """
    uniform shader scene;
    uniform float2 extent;
    uniform float origin;
    uniform float rim;
    uniform float bend;
    uniform float tintRed;
    uniform float tintGreen;
    uniform float tintBlue;
    uniform float tintAmount;
    uniform float edgeLight;

    half4 main(float2 coord) {
        float2 local = coord - float2(origin);
        float radius = max(min(extent.x, extent.y) * 0.5, 0.001);
        float2 centered = local - extent * 0.5;
        float axis = max(extent.x * 0.5 - radius, 0.0);
        float2 nearest = float2(clamp(centered.x, -axis, axis), 0.0);
        float2 radial = centered - nearest;
        float lengthToAxis = length(radial);
        float2 normal = radial / max(lengthToAxis, 0.001);
        float depth = max(radius - lengthToAxis, 0.0);
        float edge = clamp(1.0 - depth / max(rim, 0.001), 0.0, 1.0);
        float displacement = bend * edge * edge;
        float2 sampledCoord = clamp(coord - normal * displacement,
            float2(0.5), extent + float2(origin * 2.0 - 0.5));
        half4 sample = scene.eval(sampledCoord);
        half3 tint = half3(tintRed, tintGreen, tintBlue);
        half3 color = mix(sample.rgb, tint * sample.a, half(tintAmount));
        float light = dot(normal, float2(-0.6, -0.8));
        color += half(edge * edge * light * edgeLight) * sample.a;
        return half4(clamp(color, half3(0.0), half3(sample.a)), sample.a);
    }
"""
