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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.navigation.Pin
import com.github.zly2006.zhihu.ui.components.AppCard
import com.github.zly2006.zhihu.ui.components.AppOutlinedButton

@Composable
internal fun PinPollCard(
    poll: DataHolder.Pin.Poll,
    votingOptionId: String?,
    errorMessage: String?,
    onPollVote: (pollId: String, optionId: String) -> Unit,
) {
    val acceptsVote = poll.acceptsVote()
    val showsResult = poll.isVoted || !acceptsVote
    val pollVoterCount = poll.memberCount.takeIf { it > 0 } ?: poll.votingCount
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(PIN_SCREEN_POLL_CARD_TAG),
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = poll.title.ifBlank { "想法投票" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = poll.statusText(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            poll.options.forEach { option ->
                if (showsResult) {
                    PinPollResultRow(
                        option = option,
                        totalVoterCount = pollVoterCount,
                    )
                } else {
                    AppOutlinedButton(
                        onClick = { onPollVote(poll.id, option.id) },
                        enabled = votingOptionId == null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(pinScreenPollOptionTag(option.id)),
                    ) {
                        Text(
                            text = option.title,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        if (votingOptionId == option.id) {
                            Text("提交中...")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (errorMessage != null) {
                Text(
                    text = "投票失败: $errorMessage",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun PinPollResultRow(
    option: DataHolder.Pin.PollOption,
    totalVoterCount: Int,
) {
    val voteFraction = if (totalVoterCount > 0) {
        (option.votingCount.toFloat() / totalVoterCount).coerceIn(0f, 1f)
    } else {
        0f
    }
    val rowShape = RoundedCornerShape(12.dp)
    val indicatorColor = if (option.isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(pinScreenPollOptionTag(option.id)),
        shape = rowShape,
        color = if (option.isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    if (voteFraction > 0f) {
                        drawRoundRect(
                            color = indicatorColor,
                            size = Size(
                                width = size.width * voteFraction,
                                height = size.height,
                            ),
                            cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
                        )
                    }
                }.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = option.title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (option.isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.weight(1f),
            )
            if (option.isSelected) {
                Text(
                    text = "已选",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = "${option.votingCount} 票",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
