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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.github.zly2006.zhihu.i18n.ContentStrings
import com.github.zly2006.zhihu.markdown.RenderMarkdown
import com.github.zly2006.zhihu.markdown.RenderMarkdownText
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.ui.ZhihuHtmlWebViewContent
import com.github.zly2006.zhihu.ui.isLegacyWebViewSupported
import com.github.zly2006.zhihu.ui.questionSelectionWorkaround
import com.github.zly2006.zhihu.ui.rememberObservedSetting
import com.github.zly2006.zhihu.ui.subscreens.TIQIAN_MARKDOWN_PREFERENCE_KEY

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WriteContentPreviewSheet(
    sheetState: SheetState,
    useWebView: Boolean,
    isLoading: Boolean,
    html: String?,
    markdown: String?,
    onDismissRequest: () -> Unit,
) {
    val settings = rememberSettingsStore()
    val useTiqianMarkdown by rememberObservedSetting(settings, TIQIAN_MARKDOWN_PREFERENCE_KEY) {
        getBoolean(TIQIAN_MARKDOWN_PREFERENCE_KEY, false)
    }
    val skin = LocalAppSkinTokens.current
    AppBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = Modifier.fillMaxHeight().testTag("write_content_preview"),
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppDimens.Space16)
                    .testTag("write_content_preview_header"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = ContentStrings.contentPreview,
                    style = skin.title4,
                    color = skin.onSurface,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                Text(
                    text = if (useWebView) {
                        "WebView"
                    } else if (html != null) {
                        ContentStrings.richText
                    } else {
                        "Markdown"
                    },
                    style = skin.footnote2,
                    color = skin.onSurfaceVariantSummary,
                )
                AppIconButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.size(AppDimens.TouchTarget).testTag("write_content_preview_close"),
                ) {
                    AppIcon(Icons.Filled.Close, contentDescription = ContentStrings.closePreview)
                }
            }
            Spacer(Modifier.height(AppDimens.Space8))
            Box(Modifier.fillMaxWidth().weight(1f).testTag("write_content_preview_body")) {
                when {
                    isLoading -> {
                        AppSpinner(modifier = Modifier.align(Alignment.Center))
                    }
                    useWebView && html != null && isLegacyWebViewSupported -> {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            ZhihuHtmlWebViewContent(html = html)
                        }
                    }
                    html != null -> {
                        RenderMarkdown(
                            html = html,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = AppDimens.Space16, vertical = AppDimens.Space8)
                                .questionSelectionWorkaround(),
                            selectable = true,
                            enableScroll = true,
                            useTiqianRenderer = useTiqianMarkdown,
                        )
                    }
                    markdown != null && !useWebView -> {
                        RenderMarkdownText(
                            markdown = markdown,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = AppDimens.Space16, vertical = AppDimens.Space8)
                                .questionSelectionWorkaround(),
                            selectable = true,
                            enableScroll = true,
                            useTiqianRenderer = useTiqianMarkdown,
                        )
                    }
                    else -> {
                        Text(
                            text = ContentStrings.noPreview,
                            style = skin.main,
                            color = skin.onSurfaceVariantSummary,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
            }
            Spacer(Modifier.height(AppDimens.Space20))
        }
    }
}
