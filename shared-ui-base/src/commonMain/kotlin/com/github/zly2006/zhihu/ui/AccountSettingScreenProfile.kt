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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import coil3.compose.AsyncImage
import com.github.zly2006.zhihu.i18n.ReaderStrings
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.theme.LocalAppTheme
import com.github.zly2006.zhihu.ui.components.AppBadgedBox
import com.github.zly2006.zhihu.ui.components.AppCard
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppFilledTonalIconButton
import com.github.zly2006.zhihu.ui.components.AppIcon
import com.github.zly2006.zhihu.ui.components.AppSectionTitle
import com.github.zly2006.zhihu.ui.components.AppSpinner
import com.github.zly2006.zhihu.ui.components.appGroupedCardColor
import com.github.zly2006.zhihu.ui.components.settingSkinIsMiuix

internal enum class ProfileRefresh { Idle, Silent, Retrying, Failed }

/** 资料卡与快捷入口的底色，与下方设置分组的卡片一致。 */
@Composable
private fun accountCardColor(): Color = if (settingSkinIsMiuix()) {
    /** Miuix 暗色下 surfaceContainer 与页面同色，与设置分组一样抬高一级。 */
    if (LocalAppTheme.current.isDarkTheme) MaterialTheme.colorScheme.surfaceContainerHighest else LocalAppSkinTokens.current.surfaceContainer
} else {
    appGroupedCardColor()
}

/** 身份卡整体打开个人主页，扫码、退出和资料刷新重试保持独立操作。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AccountProfileCard(
    data: AccountSettingsAccountState,
    refresh: ProfileRefresh,
    onRetryRefresh: () -> Unit,
    onOpenProfile: () -> Unit,
    onScan: (() -> Unit)?,
    onLogout: () -> Unit,
) {
    val miuix = settingSkinIsMiuix()
    val onSurface = if (miuix) LocalAppSkinTokens.current.onSurface else MaterialTheme.colorScheme.onSurface
    val textMeasurer = rememberTextMeasurer()
    val nameStyle = if (miuix) LocalAppSkinTokens.current.title2 else MaterialTheme.typography.titleLarge
    val minimumNameWidth = with(LocalDensity.current) {
        textMeasurer
            .measure(data.username.take(4), style = nameStyle, softWrap = false)
            .size.width
            .toDp()
    }
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (miuix) AppDimens.SettingGroupHorizontalMarginMiuix else AppDimens.SettingGroupHorizontalMargin,
                end = if (miuix) AppDimens.SettingGroupHorizontalMarginMiuix else AppDimens.SettingGroupHorizontalMargin,
                bottom = AppDimens.SettingGroupBottomMargin,
            ).testTag(ACCOUNT_SETTINGS_PROFILE_HEADER_TAG),
        containerColor = accountCardColor(),
        contentColor = onSurface,
    ) {
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onOpenProfile)
                .padding(
                    horizontal = AppDimens.SettingRowHorizontalPadding,
                    vertical = AppDimens.Space12,
                ),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.Space12),
            verticalArrangement = Arrangement.spacedBy(AppDimens.Space8),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = data.avatarUrl,
                contentDescription = ReaderStrings.avatar,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(AppDimens.ProfileHeroAvatarHeight)
                    .background(
                        if (miuix) {
                            LocalAppSkinTokens.current.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        },
                        CircleShape,
                    ).clip(CircleShape),
            )
            Column(Modifier.widthIn(min = minimumNameWidth).weight(1f)) {
                Text(
                    text = data.username,
                    style = nameStyle,
                    color = onSurface,
                    modifier = Modifier.testTag(ACCOUNT_SETTINGS_PROFILE_NAME_TAG),
                )
                Spacer(Modifier.height(AppDimens.Space2))
                Text(
                    text = ReaderStrings.viewProfile,
                    style = if (miuix) LocalAppSkinTokens.current.footnote1 else MaterialTheme.typography.bodyMedium,
                    color = if (miuix) {
                        LocalAppSkinTokens.current.onSurfaceVariantSummary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onScan != null) {
                    AppFilledTonalIconButton(
                        onClick = onScan,
                        modifier = Modifier
                            .size(AppDimens.TouchTarget)
                            .testTag(ACCOUNT_SETTINGS_SCAN_TAG),
                    ) {
                        AppIcon(Icons.Default.QrCodeScanner, contentDescription = ReaderStrings.scanToSignIn)
                    }
                    Spacer(Modifier.width(AppDimens.Space4))
                }
                AppFilledTonalIconButton(
                    onClick = onLogout,
                    modifier = Modifier.size(AppDimens.TouchTarget),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    AppIcon(
                        Icons.AutoMirrored.Filled.Logout,
                        contentDescription = ReaderStrings.signOut,
                        modifier = Modifier.size(AppDimens.IconSizeMedium),
                    )
                }
            }
        }
        if (refresh == ProfileRefresh.Failed || refresh == ProfileRefresh.Retrying) {
            ProfileRefreshStatus(retrying = refresh == ProfileRefresh.Retrying, onRetry = onRetryRefresh)
        }
    }
}

@Composable
private fun ProfileRefreshStatus(
    retrying: Boolean,
    onRetry: () -> Unit,
) {
    val color = if (retrying) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AppDimens.TouchTarget)
            .clickable(enabled = !retrying, role = Role.Button, onClick = onRetry)
            .padding(horizontal = AppDimens.SettingRowHorizontalPadding, vertical = AppDimens.Space8)
            .testTag(ACCOUNT_SETTINGS_PROFILE_RETRY_TAG),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.Space8),
    ) {
        if (retrying) {
            AppSpinner(size = AppDimens.InlineSpinnerSize)
        } else {
            AppIcon(
                Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(AppDimens.IconSizeSmall),
            )
        }
        Text(
            text = if (retrying) ReaderStrings.profileRefreshing else ReaderStrings.profileRefreshFailed,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
        )
    }
}

internal data class AccountQuickAccessAction(
    val label: String,
    val icon: ImageVector,
    val testTag: String,
    val badgeCount: Int = 0,
    val onClick: () -> Unit,
)

/**
 * 快捷访问优先同排；完整标签放不下时按当前字体重排为两列或单列，不裁切。
 * 入口数量仍随历史快捷入口是否显示变化。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AccountQuickAccessRow(
    actions: List<AccountQuickAccessAction>,
    modifier: Modifier = Modifier,
) {
    if (actions.isEmpty()) return
    val miuix = settingSkinIsMiuix()
    val contentColor = if (miuix) LocalAppSkinTokens.current.onSurface else MaterialTheme.colorScheme.onSurface
    val containerColor = accountCardColor()
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = if (miuix) LocalAppSkinTokens.current.footnote2 else MaterialTheme.typography.labelLarge
    val minimumActionWidth = with(LocalDensity.current) {
        actions.maxOf { textMeasurer.measure(it.label, style = labelStyle, softWrap = false).size.width }.toDp() + AppDimens.Space4 * 2
    }

    Column(
        modifier
            .fillMaxWidth()
            .padding(bottom = AppDimens.SettingGroupBottomMargin),
    ) {
        AppSectionTitle(
            text = ReaderStrings.quickAccess,
            modifier = Modifier.padding(
                start = if (settingSkinIsMiuix()) {
                    AppDimens.SettingGroupHorizontalMarginMiuix + AppDimens.SettingRowHorizontalPadding - AppDimens.Space4
                } else {
                    AppDimens.SettingGroupHeaderHorizontalPadding + AppDimens.Space4
                },
                top = AppDimens.Space8,
                end = AppDimens.SettingGroupHeaderHorizontalPadding + AppDimens.Space4,
            ),
        )
        BoxWithConstraints(
            Modifier.fillMaxWidth().padding(horizontal = if (miuix) AppDimens.SettingGroupHorizontalMarginMiuix else AppDimens.SettingGroupHorizontalMargin),
        ) {
            val columns = when {
                maxWidth >= minimumActionWidth * actions.size + AppDimens.AccountQuickAccessSpacing * (actions.size - 1) -> actions.size
                maxWidth >= minimumActionWidth * 2 + AppDimens.AccountQuickAccessSpacing -> 2
                else -> 1
            }
            val actionWidth = (maxWidth - AppDimens.AccountQuickAccessSpacing * (columns - 1)) / columns
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppDimens.AccountQuickAccessSpacing, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(AppDimens.AccountQuickAccessSpacing),
                maxItemsInEachRow = columns,
            ) {
                actions.forEach { action ->
                    AppCard(
                        onClick = action.onClick,
                        modifier = Modifier
                            .width(actionWidth)
                            .testTag(action.testTag),
                        containerColor = containerColor,
                        contentColor = contentColor,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = AppDimens.AccountQuickAccessHeight)
                                .padding(horizontal = AppDimens.Space4, vertical = AppDimens.Space12),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            AppBadgedBox(count = action.badgeCount) {
                                AppIcon(
                                    imageVector = action.icon,
                                    contentDescription = null,
                                    tint = contentColor,
                                    modifier = Modifier.size(AppDimens.IconSizeMedium).testTag("${action.testTag}:icon"),
                                )
                            }
                            Spacer(Modifier.height(AppDimens.Space6))
                            Text(
                                text = action.label,
                                modifier = Modifier.fillMaxWidth(),
                                style = if (miuix) LocalAppSkinTokens.current.footnote2 else MaterialTheme.typography.labelLarge,
                                color = contentColor,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}
