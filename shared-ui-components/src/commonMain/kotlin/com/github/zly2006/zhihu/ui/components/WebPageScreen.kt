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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import com.github.zly2006.zhihu.i18n.WidgetsStrings
import com.github.zly2006.zhihu.navigation.isWebPageUrl
import com.github.zly2006.zhihu.platform.PlatformBackHandler
import com.github.zly2006.zhihu.platform.rememberPlainTextClipboard
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.platform.rememberSystemUrlOpener
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.ui.rememberSetting
import io.ktor.http.Url

/** 浏览器拥有历史和当前地址；顶栏只读取原生视图的状态，不维护第二份历史。 */
internal abstract class WebPageBrowser(
    val initialUrl: String,
) {
    var currentUrl by mutableStateOf(initialUrl)
        protected set
    var title by mutableStateOf("")
        protected set
    var canGoBack by mutableStateOf(false)
        protected set
    var canGoForward by mutableStateOf(false)
        protected set
    var errorMessage by mutableStateOf<(() -> String)?>(null)
        protected set

    abstract fun goBack()

    abstract fun goForward()

    abstract fun reload()

    @Composable
    abstract fun Content(modifier: Modifier)
}

@Composable
internal expect fun rememberWebPageBrowser(initialUrl: String): WebPageBrowser?

@Composable
fun WebPageScreen(url: String, onClose: () -> Unit) = key(url) {
    val valid = remember(url) { isWebPageUrl(url) }
    val browser = if (valid) rememberWebPageBrowser(url) else null
    val address = browser?.currentUrl ?: url.takeIf { valid }
    val site = remember(address) {
        address?.let { Url(it) }?.run {
            if (port == protocol.defaultPort) "${protocol.name}://$host" else "${protocol.name}://$host:$port"
        }
    }
    val browserColor by rememberSetting(rememberSettingsStore(), "luotianyi_color", 0xFF66CCFF.toInt())
    val clipboard = rememberPlainTextClipboard()
    val openSystemUrl = rememberSystemUrlOpener()
    val back = if (browser != null && browser.canGoBack) browser::goBack else onClose
    PlatformBackHandler(enabled = rememberNavEntryOnTop() && browser?.canGoBack == true, onBack = back)

    AppScaffold(
        modifier = Modifier.testTag("web_page"),
        sampleContent = false,
        topBar = {
            AppCompactTopBar(
                title = browser?.title?.takeIf { it.isNotBlank() } ?: WidgetsStrings.webPage,
                subtitle = site,
                containerColor = readableToolbarColor(Color(browserColor)),
                onBack = back,
                backTag = "web_page_back",
                actions = {
                    if (address != null) {
                        if (browser != null && browser.canGoForward) {
                            WebPageAction(WidgetsStrings.webForward, Icons.AutoMirrored.Filled.ArrowForward, browser::goForward)
                        }
                        WebPageAction(WidgetsStrings.copyCurrentUrl, Icons.Default.ContentCopy) { clipboard(WidgetsStrings.webAddress, address) }
                        WebPageAction(WidgetsStrings.openInBrowser, Icons.Default.OpenInBrowser) { openSystemUrl(address) }
                        if (browser?.canGoBack == true) {
                            WebPageAction(WidgetsStrings.closeWebPage, Icons.Default.Close, onClose)
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            val error = if (!valid) WidgetsStrings.invalidWebUrl else browser?.errorMessage?.invoke()
            if (valid && browser == null) {
                AppEmptyState(
                    title = WidgetsStrings.externalWebPage,
                    description = url,
                    modifier = Modifier.fillMaxSize(),
                    action = { AppTextButton(WidgetsStrings.openInNewTab, onClick = { openSystemUrl(url) }) },
                )
            }
            if (error != null) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = AppDimens.Space16, vertical = AppDimens.Space8),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(error, modifier = Modifier.weight(1f))
                    if (browser != null) AppTextButton(WidgetsStrings.reload, onClick = browser::reload)
                }
            }
            browser?.Content(Modifier.fillMaxSize())
        }
    }
}

/** 与皮肤的固定前景色对比不足时，仅将所选色调淡到表面色上。 */
@Composable
private fun readableToolbarColor(color: Color): Color {
    val tokens = LocalAppSkinTokens.current
    val foreground = tokens.onSurface.luminance() + 0.05f
    val background = color.luminance() + 0.05f
    return if (maxOf(foreground, background) >= 4.5f * minOf(foreground, background)) color else lerp(tokens.surface, color, 0.2f)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WebPageAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        AppIconButton(onClick = onClick) { AppIcon(icon, label) }
    }
}
