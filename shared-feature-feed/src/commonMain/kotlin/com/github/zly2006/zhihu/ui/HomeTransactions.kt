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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.theme.ThemeManager
import com.github.zly2006.zhihu.ui.components.AppButton
import com.github.zly2006.zhihu.ui.components.AppCard
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.ui.components.AppIcon as Icon

internal data class HomeTransaction(
    val key: String,
    val title: String,
    val content: String?,
    val icon: ImageVector? = null,
    val acceptText: String?,
    val onAccept: (() -> Unit)?,
    val dismissText: String,
    val onDismiss: () -> Unit,
)

@Composable
internal fun HomeScreenTransactionsToggle(
    transactions: List<HomeTransaction>,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val skin = LocalAppSkinTokens.current
    AppCard(
        onClick = onClick,
        containerColor = if (ThemeManager.isDarkTheme()) MaterialTheme.colorScheme.surfaceContainerHighest else skin.surfaceContainer,
        contentColor = skin.onSurface,
        modifier = Modifier
            .padding(horizontal = AppDimens.PageHorizontalPadding, vertical = AppDimens.Space4)
            .fillMaxWidth()
            .testTag("home_transactions_toggle")
            .semantics { stateDescription = if (expanded) "已展开" else "已收起" },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = AppDimens.TouchTarget)
                .padding(horizontal = AppDimens.Space16, vertical = AppDimens.Space12),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.Space12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Notifications,
                contentDescription = null,
                tint = skin.primary,
                modifier = Modifier.size(AppDimens.IconSizeMedium),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AppDimens.Space4),
            ) {
                Text("通知与动态", style = skin.body2, fontWeight = FontWeight.SemiBold)
                if (!expanded) {
                    Text(
                        transactions.first().title,
                        style = skin.footnote2,
                        color = skin.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text("${transactions.size} 条", style = skin.footnote2, color = skin.onSurfaceVariantSummary)
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "收起" else "展开",
                tint = skin.onSurfaceVariantSummary,
                modifier = Modifier.size(AppDimens.IconSizeMedium),
            )
        }
    }
}

@Composable
internal fun HomeTransactionRow(transaction: HomeTransaction) {
    val skin = LocalAppSkinTokens.current
    val compact = transaction.key == NEW_UI_NOTICE_TAG
    AppCard(
        containerColor = if (ThemeManager.isDarkTheme()) MaterialTheme.colorScheme.surfaceContainerHighest else skin.surfaceContainer,
        contentColor = skin.onSurface,
        modifier = Modifier
            .padding(horizontal = AppDimens.PageHorizontalPadding, vertical = AppDimens.Space4)
            .fillMaxWidth()
            .testTag(transaction.key),
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(
                horizontal = AppDimens.Space16,
                vertical = if (compact) AppDimens.Space4 else AppDimens.Space16,
            ),
            maxItemsInEachRow = if (compact) 2 else 1,
            horizontalArrangement = if (compact) Arrangement.SpaceBetween else Arrangement.Start,
            verticalArrangement = Arrangement.spacedBy(if (compact) AppDimens.Space4 else AppDimens.Space12),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(if (compact) AppDimens.Space8 else AppDimens.Space12),
                verticalAlignment = if (compact) Alignment.CenterVertically else Alignment.Top,
            ) {
                transaction.icon?.let { icon ->
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = skin.primary,
                        modifier = Modifier.size(AppDimens.IconSizeMedium),
                    )
                }
                Text(
                    transaction.title,
                    style = skin.main,
                    fontWeight = FontWeight.SemiBold,
                    modifier = if (compact) Modifier else Modifier.weight(1f),
                )
            }
            transaction.content?.let { text ->
                Text(
                    text,
                    style = skin.body2,
                    color = skin.onSurfaceVariantSummary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            FlowRow(
                modifier = if (compact) Modifier else Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppDimens.Space8, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(AppDimens.Space4),
            ) {
                AppTextButton(
                    text = transaction.dismissText,
                    onClick = transaction.onDismiss,
                    contentColor = skin.onSurfaceVariantSummary,
                    modifier = Modifier.testTag("${transaction.key}_dismiss"),
                )
                val onAccept = transaction.onAccept
                transaction.acceptText?.takeIf { onAccept != null }?.let { text ->
                    AppButton(
                        text = text,
                        onClick = { onAccept?.invoke() },
                        modifier = Modifier.testTag("${transaction.key}_accept"),
                    )
                }
            }
        }
    }
}
