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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.theme.ThemeManager
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import zhihu.uicomponents.generated.resources.Res
import zhihu.uicomponents.generated.resources.splash_accent
import zhihu.uicomponents.generated.resources.splash_cat
import kotlin.math.PI
import kotlin.math.sin

const val APP_STARTUP_SPLASH_TAG = "app_startup_splash"

@Composable
fun AppStartupSplash(content: @Composable () -> Unit) {
    var finished by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        if (finished) {
            content()
        } else {
            AppSplashScreen(onFinished = { finished = true })
        }
    }
}

@Composable
fun AppSplashScreen(onFinished: () -> Unit) {
    var completed by rememberSaveable { mutableStateOf(false) }
    var splashDrawn by remember { mutableStateOf(false) }
    val entrance = remember { Animatable(if (completed) 1f else 0f) }
    val finish by rememberUpdatedState(onFinished)
    val settings = rememberSettingsStore()
    LaunchedEffect(settings) { ThemeManager.loadFromSettings(settings) }
    val darkTheme = ThemeManager.isDarkTheme()
    val background = if (darkTheme) {
        if (ThemeManager.getAmoledTheme()) Color.Black else Color(0xFF121212)
    } else {
        Color(0xFFFAFAFA)
    }
    val brandColor = Color(0xFF3996D1)
    val textColor = if (darkTheme) Color(0xFFEAF0F7) else Color(0xFF202B3A)

    LaunchedEffect(splashDrawn) {
        if (splashDrawn) {
            if (!completed) {
                entrance.animateTo(1f, tween(600, easing = LinearEasing))
                delay(140)
                completed = true
            }
            finish()
        }
    }

    Box(Modifier.fillMaxSize().background(background)) {
        Box(
            Modifier
                .fillMaxSize()
                .testTag(APP_STARTUP_SPLASH_TAG)
                .semantics { paneTitle = "启动页" }
                .drawWithContent {
                    drawContent()
                    if (!splashDrawn) splashDrawn = true
                }.pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent().changes.forEach { it.consume() }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2, size.height / 2 - 34.dp.toPx())
                val radius = (130 + 90 * entrance.value).dp.toPx()
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            brandColor.copy(alpha = (if (darkTheme) 0.22f else 0.12f)),
                            Color.Transparent,
                        ),
                        center = center,
                        radius = radius,
                    ),
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(192.dp)) {
                    Image(
                        painterResource(Res.drawable.splash_cat),
                        contentDescription = null,
                        modifier = Modifier.matchParentSize().graphicsLayer {
                            val progress = FastOutSlowInEasing.transform((entrance.value / 0.78f).coerceIn(0f, 1f))
                            val scale = 0.78f + 0.22f * progress + 0.10f * sin(PI * progress).toFloat()
                            alpha = (entrance.value / 0.18f).coerceIn(0f, 1f)
                            translationY = (1f - progress) * 28.dp.toPx()
                            rotationZ = -5f * (1f - progress)
                            scaleX = scale * 1.6f
                            scaleY = scaleX
                        },
                    )
                    Image(
                        painterResource(Res.drawable.splash_accent),
                        contentDescription = null,
                        modifier = Modifier.matchParentSize().graphicsLayer {
                            val progress = FastOutSlowInEasing.transform(((entrance.value - 0.16f) / 0.68f).coerceIn(0f, 1f))
                            alpha = progress
                            translationX = (1f - progress) * 12.dp.toPx()
                            translationY = (1f - progress) * 14.dp.toPx()
                            rotationZ = -42f * (1f - progress)
                            scaleX = (0.45f + 0.55f * progress) * 1.6f
                            scaleY = scaleX
                        },
                    )
                }
                Spacer(Modifier.height(8.dp))
                BasicText(
                    "知乎++",
                    style = TextStyle(color = textColor, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp),
                    modifier = Modifier.graphicsLayer {
                        val progress = FastOutSlowInEasing.transform(((entrance.value - 0.22f) / 0.52f).coerceIn(0f, 1f))
                        alpha = progress
                        translationY = (1f - progress) * 8.dp.toPx()
                    },
                )
                Spacer(Modifier.height(10.dp))
                BasicText(
                    "让阅读回归内容",
                    style = TextStyle(color = textColor.copy(alpha = 0.55f), fontSize = 14.sp, letterSpacing = 2.sp),
                    modifier = Modifier.graphicsLayer {
                        alpha = ((entrance.value - 0.38f) / 0.44f).coerceIn(0f, 1f)
                    },
                )
            }
        }
    }
}
