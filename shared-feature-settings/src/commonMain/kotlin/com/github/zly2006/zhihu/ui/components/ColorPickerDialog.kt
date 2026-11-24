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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun ColorPickerDialog(
    title: String = "选择颜色",
    initialColor: Color,
    presetColors: List<Color> = listOf(
        Color(0xFF2196F3),
        Color(0xFF4CAF50),
        Color(0xFFF44336),
        Color(0xFFFF9800),
        Color(0xFF9C27B0),
        Color(0xFF607D8B),
    ),
    onDismiss: () -> Unit,
    onColorSelected: (Color) -> Unit,
) {
    val red = rememberSliderState(value = initialColor.red * 255f, trackRange = 0f..255f)
    val green = rememberSliderState(value = initialColor.green * 255f, trackRange = 0f..255f)
    val blue = rememberSliderState(value = initialColor.blue * 255f, trackRange = 0f..255f)

    fun selectedColor() = Color(red.value / 255f, green.value / 255f, blue.value / 255f)

    val currentColor = selectedColor()
    val density = LocalDensity.current
    val windowHeightPx = LocalWindowInfo.current.containerSize.height
    val contentMaxHeight = with(density) { (windowHeightPx * 0.45f).toDp() }.coerceIn(200.dp, 420.dp)

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = contentMaxHeight)
                    .verticalScroll(rememberScrollState()),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(currentColor)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
                )

                Spacer(modifier = Modifier.height(16.dp))

                ColorSlider("红色", red)

                Spacer(modifier = Modifier.height(8.dp))

                ColorSlider("绿色", green)

                Spacer(modifier = Modifier.height(8.dp))

                ColorSlider("蓝色", blue)

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "RGB: (${red.value.roundToInt()}, ${green.value.roundToInt()}, ${blue.value.roundToInt()}) #${currentColor.toArgb().toUInt().toString(16).padStart(3, '0').takeLast(6).uppercase()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text("预设颜色", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    presetColors.forEach { presetColor ->
                        PresetColor(presetColor, selected = currentColor.toArgb() == presetColor.toArgb()) {
                            red.value = it.red * 255f
                            green.value = it.green * 255f
                            blue.value = it.blue * 255f
                        }
                    }
                }
            }
        },
        confirmButton = {
            AppTextButton(
                text = "确定",
                onClick = { onColorSelected(selectedColor()) },
            )
        },
        dismissButton = {
            AppTextButton(
                text = "取消",
                onClick = onDismiss,
            )
        },
    )
}

@Composable
private fun ColorSlider(
    label: String,
    state: SliderState,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            label,
            modifier = Modifier.width(50.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
        AppSlider(
            state = state,
            onValueChange = { state.value = it },
            modifier = Modifier.weight(1f).semantics {
                contentDescription = label
                stateDescription = state.value.roundToInt().toString()
            },
        )
        Text(
            state.value.roundToInt().toString(),
            modifier = Modifier.width(40.dp),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun PresetColor(
    color: Color,
    selected: Boolean,
    onSelect: (Color) -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .semantics {
                contentDescription = "预设颜色 #${color.toArgb().toUInt().toString(16).padStart(6, '0').takeLast(6).uppercase()}"
                this.selected = selected
            }.clickable { onSelect(color) },
    )
}
