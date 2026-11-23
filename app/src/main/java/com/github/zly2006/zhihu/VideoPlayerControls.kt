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

package com.github.zly2006.zhihu

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun BoxScope.VideoPlayerControls(
    visible: Boolean,
    isPlaying: Boolean,
    timeText: () -> String,
    playbackSpeed: Float,
    speedMenuExpanded: Boolean,
    onTogglePlayback: () -> Unit,
    onScreenshot: () -> Unit,
    onLock: () -> Unit,
    onDownload: () -> Unit,
    onShowSpeedMenu: () -> Unit,
    onDismissSpeedMenu: () -> Unit,
    onSpeedSelected: (Float) -> Unit,
    progress: @Composable () -> Unit,
) {
    AnimatedVisibility(visible = visible, enter = EnterTransition.None, exit = ExitTransition.None, modifier = Modifier.align(Alignment.BottomCenter)) {
        Surface(Modifier.fillMaxWidth(), color = Color.Black.copy(alpha = 0.55f)) {
            Column(
                Modifier
                    .navigationBarsPadding()
                    .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                progress()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onTogglePlayback, modifier = Modifier.size(36.dp)) {
                        Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, if (isPlaying) "暂停" else "播放", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Text(timeText(), color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                    IconButton(onClick = onScreenshot, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.CameraAlt, "截图", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onLock, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.LockOpen, "锁定", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onDownload, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Download, "下载", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Box {
                        Text(
                            "${playbackSpeed}x",
                            color = if (playbackSpeed != 1f) Color.White else Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .padding(8.dp)
                                .semantics {
                                    contentDescription = "播放速度"
                                    stateDescription = "${playbackSpeed}倍"
                                }.clickable(role = Role.Button, onClickLabel = "选择播放速度", onClick = onShowSpeedMenu),
                        )
                        DropdownMenu(expanded = speedMenuExpanded, onDismissRequest = onDismissSpeedMenu, modifier = Modifier.background(Color(0xFF2D2D2D), RoundedCornerShape(8.dp))) {
                            floatArrayOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { sp ->
                                val sel = sp == playbackSpeed
                                DropdownMenuItem(
                                    text = { Text("${sp}x", color = if (sel) Color(0xFFA78BFA) else Color.White.copy(alpha = 0.75f), fontSize = 13.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal) },
                                    onClick = { onSpeedSelected(sp) },
                                    modifier = Modifier.semantics { selected = sel },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VideoPlayerProgress(
    progressState: SliderState,
    currentPosition: Long,
    duration: Long,
    timeText: String,
    onValueChange: (Float) -> Unit,
) {
    Slider(
        state = progressState,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .height(20.dp)
            .semantics {
                contentDescription = "播放进度"
                stateDescription = timeText
            },
        track = {
            val frac = if (duration > 0) currentPosition.toFloat() / duration else 0f
            Canvas(Modifier.fillMaxWidth().height(8.dp)) {
                val y = size.height / 2f
                val sw = 2.dp.toPx()
                val pe = size.width * frac
                drawLine(Color.White.copy(alpha = 0.2f), Offset(pe, y), Offset(size.width, y), sw, cap = StrokeCap.Round)
                drawLine(Color.White, Offset(0f, y), Offset(pe, y), sw, cap = StrokeCap.Round)
            }
        },
    )
}
