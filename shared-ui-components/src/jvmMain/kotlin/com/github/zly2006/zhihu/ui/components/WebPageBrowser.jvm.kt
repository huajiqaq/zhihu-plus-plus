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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import com.github.zly2006.zhihu.i18n.WidgetsStrings
import com.github.zly2006.zhihu.navigation.isWebPageUrl
import java.util.concurrent.atomic.AtomicBoolean
import javafx.application.Platform
import javafx.beans.InvalidationListener
import javafx.concurrent.Worker
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.web.WebEngine
import javafx.scene.web.WebView

@Composable
internal actual fun rememberWebPageBrowser(initialUrl: String): WebPageBrowser? = remember(initialUrl) { DesktopWebPageBrowser(initialUrl) }

private class DesktopWebPageBrowser(
    initialUrl: String,
) : WebPageBrowser(initialUrl) {
    @Volatile
    private var engine: WebEngine? = null
    private var attempt by mutableIntStateOf(0)

    override fun goBack() = Platform.runLater { engine?.history?.let { if (it.currentIndex > 0) it.go(-1) } }

    override fun goForward() = Platform.runLater { engine?.history?.let { if (it.currentIndex < it.entries.lastIndex) it.go(1) } }

    override fun reload() {
        if (engine == null) {
            attempt++
        } else {
            Platform.runLater { engine?.let { if (isWebPageUrl(it.location)) it.reload() else it.load(currentUrl) } }
        }
    }

    private fun createView(): WebView {
        val view = WebView()
        val web = view.engine
        val history = web.history
        val historyChanged = InvalidationListener {
            if (engine === web) {
                canGoBack = history.currentIndex > 0
                canGoForward = history.currentIndex < history.entries.size - 1
            }
        }
        history.currentIndexProperty().addListener(historyChanged)
        history.entries.addListener(historyChanged)
        web.setCreatePopupHandler { web }
        web.titleProperty().addListener { _, _, value -> if (engine === web) title = value.orEmpty() }
        web.locationProperty().addListener { _, _, location ->
            if (engine !== web) return@addListener
            if (isWebPageUrl(location)) {
                currentUrl = location
                errorMessage = null
            } else {
                errorMessage = WidgetsStrings::notHttpAddress
                web.loadWorker.cancel()
            }
        }
        web.loadWorker.stateProperty().addListener { _, _, state ->
            if (engine !== web) return@addListener
            when (state) {
                Worker.State.RUNNING -> if (isWebPageUrl(web.location)) errorMessage = null else web.loadWorker.cancel()
                Worker.State.FAILED -> {
                    val detail = web.loadWorker.exception?.localizedMessage.orEmpty()
                    errorMessage = { WidgetsStrings.webLoadFailed(detail) }
                }
                else -> Unit
            }
        }
        engine = web
        web.load(initialUrl)
        return view
    }

    private fun releaseEngine() {
        val previous = engine
        engine = null
        previous?.load(null)
    }

    @Composable
    override fun Content(modifier: Modifier) {
        val panel = remember(attempt) { runCatching { JFXPanel() } }
        val target = panel.getOrNull()
        if (target == null) {
            LaunchedEffect(attempt) {
                val detail = panel.exceptionOrNull()?.message.orEmpty()
                errorMessage = { WidgetsStrings.webEngineFailed(detail) }
            }
            return
        }
        DisposableEffect(target) {
            val disposed = AtomicBoolean(false)
            Platform.setImplicitExit(false)
            Platform.runLater {
                if (disposed.get()) return@runLater
                runCatching { target.scene = Scene(createView()) }.onFailure {
                    releaseEngine()
                    val detail = it.localizedMessage.orEmpty()
                    errorMessage = { WidgetsStrings.webEngineFailed(detail) }
                }
            }
            onDispose {
                disposed.set(true)
                Platform.runLater {
                    releaseEngine()
                    target.scene = null
                }
            }
        }
        SwingPanel(factory = { target }, modifier = modifier)
    }
}
