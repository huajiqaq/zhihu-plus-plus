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

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.delay

/**
 * 可滚动设置页；settingKey 命中后先收起顶栏再定位。
 * 折叠组需由页面展开目标项；LazyColumn 页面不使用此骨架。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsPage(
    title: String,
    onBack: (() -> Unit)?,
    backTag: String,
    modifier: Modifier = Modifier,
    highlightedKey: String = "",
    subtitle: String? = null,
    scrollTag: String? = null,
    scrollState: ScrollState = rememberScrollState(),
    pageTurnEnabled: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    overlays: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val titleBarState = rememberAppPageTitleBarScrollState()
    val pageTurnTarget = rememberPageTurnTarget(scrollState, enabled = pageTurnEnabled)
    val deepLink = remember(highlightedKey) { SettingsDeepLink(highlightedKey) }
    var scrolledTo by remember(highlightedKey) { mutableStateOf(false) }
    val target = deepLink.targetRequester
    LaunchedEffect(highlightedKey, target) {
        if (target != null && !scrolledTo) {
            scrolledTo = true
            delay(200)
            // 程序化滚动不经过 nestedScroll，标题栏要手动收起，否则目标行会停在大标题下面。
            titleBarState.collapse()
            target.bringIntoView()
        }
    }
    val background = appGroupedPageBackground()
    AppScaffold(
        modifier = modifier.fillMaxSize().appPageTitleBarScroll(titleBarState),
        containerColor = background,
        topBar = {
            AppPageTitleBar(
                title = title,
                subtitle = subtitle,
                onBack = onBack,
                backTag = backTag,
                scrollState = titleBarState,
                colors = TopAppBarDefaults.topAppBarColors().copy(
                    containerColor = background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                actions = actions,
            )
        },
    ) { innerPadding ->
        CompositionLocalProvider(LocalSettingsDeepLink provides deepLink) {
            // 顶栏留白放在滚动内容里：内容能滚到玻璃顶栏下面被采样模糊。
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .consumeWindowInsets(innerPadding)
                    .imePadding()
                    .pageTurnViewportWithGuide(pageTurnTarget)
                    .verticalScroll(scrollState)
                    .then(scrollTag?.let { Modifier.testTag(it) } ?: Modifier)
                    .padding(innerPadding)
                    .padding(vertical = AppDimens.Space16),
            ) {
                SettingsContent(content = content)
            }
            overlays()
        }
    }
}
