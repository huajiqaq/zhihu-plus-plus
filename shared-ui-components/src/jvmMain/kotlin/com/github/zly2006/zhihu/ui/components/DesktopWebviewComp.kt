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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import com.github.zly2006.zhihu.account.ZHIHU_DESKTOP_USER_AGENT
import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javafx.scene.web.WebEngine

@Composable
fun DesktopWebviewComp(
    url: String?,
    modifier: Modifier = Modifier,
    userAgent: String = ZHIHU_DESKTOP_USER_AGENT,
    initialCookies: Map<String, String> = emptyMap(),
    onPageFinished: (url: String?) -> Unit = {},
    onCookiesChanged: (Map<String, String>) -> Unit = {},
    onNavigation: (String) -> Unit = {},
    onDocumentReady: (WebEngine, (() -> Unit) -> Unit) -> Unit = { _, _ -> },
) {
    val pageFinished = rememberUpdatedState(onPageFinished)
    val cookiesChanged = rememberUpdatedState(onCookiesChanged)
    val navigation = rememberUpdatedState(onNavigation)
    val documentReady = rememberUpdatedState(onDocumentReady)
    val panel = remember { JFXPanel() }
    val sessionRef = remember(panel) { AtomicReference<DesktopWebviewSession?>() }
    val disposed = remember(panel) { AtomicBoolean(false) }
    DisposableEffect(panel) {
        /** JavaFX 工具包退出后无法重新打开登录窗口。 */
        Platform.setImplicitExit(false)
        onDispose {
            disposed.set(true)
            Platform.runLater {
                sessionRef.getAndSet(null)?.close()
                panel.scene = null
            }
        }
    }
    SwingPanel(
        modifier = modifier,
        factory = { panel.also { it.enableInputMethods(false) } },
        update = { jfxPanel ->
            Platform.runLater {
                if (disposed.get()) return@runLater
                val session = sessionRef.get()
                    ?: DesktopWebviewSession(
                        onPageFinished = { pageFinished.value(it) },
                        onCookiesChanged = { cookiesChanged.value(it) },
                        onNavigation = { navigation.value(it) },
                        onDocumentReady = { engine, onJavaScriptMessage ->
                            if (!disposed.get()) documentReady.value(engine, onJavaScriptMessage)
                        },
                    ).also {
                        sessionRef.set(it)
                        jfxPanel.scene = Scene(it.view)
                    }
                session.load(url, userAgent, initialCookies)
            }
        },
    )
}
