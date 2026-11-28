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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.i18n.ContentStrings
import com.github.zly2006.zhihu.navigation.Person

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun VotersSheet(
    show: Boolean,
    title: String,
    voters: List<DataHolder.Author>,
    isLoading: Boolean,
    errorMessage: String?,
    canLoadMore: Boolean,
    onDismissRequest: () -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onNavigate: (Person) -> Unit,
) {
    if (!show) return
    AppBottomSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(
                    items = voters,
                    key = { it.id },
                ) { voter ->
                    VoterRow(
                        voter = voter,
                        onClick = {
                            onNavigate(
                                Person(
                                    id = voter.id,
                                    urlToken = voter.urlToken,
                                    name = voter.name,
                                ),
                            )
                        },
                    )
                }
                if (errorMessage != null) {
                    item("error") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = ContentStrings.loadError(errorMessage),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                            AppTextButton(onClick = onRetry) {
                                Text(ContentStrings.retry)
                            }
                        }
                    }
                } else if (voters.isEmpty() && isLoading) {
                    item("initial_loading") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            AppSpinner()
                        }
                    }
                } else if (voters.isEmpty()) {
                    item("empty") {
                        Text(
                            text = ContentStrings.noVoters,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                } else if (canLoadMore || isLoading) {
                    item("load_more") {
                        AppTextButton(
                            onClick = onLoadMore,
                            enabled = !isLoading,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (isLoading) {
                                AppSpinner(size = 18.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(if (isLoading) ContentStrings.loading else ContentStrings.loadMore)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VoterRow(
    voter: DataHolder.Author,
    onClick: () -> Unit,
) {
    val supportingContent: (@Composable () -> Unit)? = voter.headline.takeIf { it.isNotBlank() }?.let { headline ->
        {
            Text(
                text = headline,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    SettingItem(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
        onClick = onClick,
        endAction = {},
        iconShape = CircleShape,
        icon = {
            AsyncImage(
                model = voter.avatarUrl,
                contentDescription = ContentStrings.voterAvatar(voter.name),
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
            )
        },
        title = {
            Text(
                text = voter.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        description = supportingContent,
    )
}
