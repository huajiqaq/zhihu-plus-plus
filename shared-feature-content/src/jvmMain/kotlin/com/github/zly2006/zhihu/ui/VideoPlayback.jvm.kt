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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.github.zly2006.zhihu.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import com.github.zly2006.zhihu.i18n.ContentStrings
import com.github.zly2006.zhihu.platform.AudioActivityCoordinator
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import javafx.geometry.Insets
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.Slider
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.StackPane
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.scene.media.MediaView
import javafx.util.Duration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch

@Composable
internal actual fun PlatformVideoPlayer(url: String, position: VideoPlaybackPosition, active: Boolean, onFailure: () -> Unit, modifier: Modifier) {
    val failure = rememberUpdatedState(onFailure)
    val currentActive = rememberUpdatedState(active)
    val scope = rememberCoroutineScope()
    val panelResult = remember(url) { runCatching { JFXPanel() } }
    val panel = panelResult.getOrNull()
    if (panel == null) { LaunchedEffect(url) { failure.value() }; return }
    val player = remember(url) { AtomicReference<MediaPlayer?>(null) }
    val disposed = remember(url) { AtomicBoolean(false) }
    val playLabel = ContentStrings.videoPlay
    val pauseLabel = ContentStrings.videoPause
    val muteLabel = ContentStrings.videoMute
    val unmuteLabel = ContentStrings.videoUnmute
    val rewindLabel = ContentStrings.videoRewind
    val forwardLabel = ContentStrings.videoForward
    DisposableEffect(url, panel) {
        var suspended = CompletableDeferred(Unit)
        val lease = AudioActivityCoordinator.acquirePlayback(
            onInterrupted = {
                position.playing = false
                val completion = CompletableDeferred<Unit>()
                suspended = completion
                Platform.runLater {
                    try { player.get()?.pause(); completion.complete(Unit) } catch (error: Exception) { completion.completeExceptionally(error) }
                }
            },
            awaitSuspended = { suspended.await() },
        )
        if (lease == null) failure.value()
        Platform.setImplicitExit(false)
        Platform.runLater {
            if (!disposed.get()) try {
                val media = Media(url)
                val playback = MediaPlayer(media)
                player.set(playback)
                fun fail() { scope.launch { if (!disposed.get()) failure.value() } }
                playback.setOnError { fail() }
                media.setOnError { fail() }
                val view = MediaView(playback).apply { isPreserveRatio = true }
                val surface = StackPane(view).apply { style = "-fx-background-color: black;" }
                view.fitWidthProperty().bind(surface.widthProperty())
                view.fitHeightProperty().bind(surface.heightProperty())
                val toggle = Button(playLabel).apply {
                    setOnAction {
                        position.playing = playback.status != MediaPlayer.Status.PLAYING
                        if (position.playing) playback.play() else playback.pause()
                    }
                }
                playback.statusProperty().addListener { _, _, status -> toggle.text = if (status == MediaPlayer.Status.PLAYING) pauseLabel else playLabel }
                val mute = Button(if (position.muted) unmuteLabel else muteLabel).apply {
                    setOnAction {
                        position.muted = !position.muted
                        playback.isMute = position.muted
                        text = if (position.muted) unmuteLabel else muteLabel
                    }
                }
                val progress = Slider(0.0, 1.0, position.seconds)
                val time = Label("0:00")
                var publishingTime = false
                progress.valueProperty().addListener { _, _, value ->
                    if (!publishingTime) { position.seconds = value.toDouble(); playback.seek(Duration.seconds(position.seconds)) }
                }
                playback.currentTimeProperty().addListener { _, _, value ->
                    val seconds = value.toSeconds()
                    if (seconds.isFinite()) {
                        position.seconds = seconds
                        time.text = "${seconds.toInt() / 60}:${(seconds.toInt() % 60).toString().padStart(2, '0')}"
                        if (!progress.isValueChanging) { publishingTime = true; progress.value = seconds; publishingTime = false }
                    }
                }
                val speed = ComboBox<Double>().apply {
                    items.addAll(0.5, 0.75, 1.0, 1.25, 1.5, 2.0)
                    value = position.rate
                    setOnAction { position.rate = value; playback.rate = value }
                }
                val back = Button(rewindLabel).apply { setOnAction { playback.seek(Duration.seconds((position.seconds - 10).coerceAtLeast(0.0))) } }
                val forward = Button(forwardLabel).apply { setOnAction { playback.seek(Duration.seconds((position.seconds + 10).coerceAtMost(progress.max))) } }
                val controls = HBox(8.0, toggle, back, forward, progress, time, speed, mute).apply { padding = Insets(8.0) }
                HBox.setHgrow(progress, Priority.ALWAYS)
                panel.scene = Scene(BorderPane().apply { center = surface; bottom = controls })
                playback.setOnReady {
                    progress.max = playback.totalDuration.toSeconds().takeIf { it.isFinite() && it > 0 } ?: 1.0
                    playback.isMute = position.muted
                    playback.rate = position.rate
                    playback.seek(Duration.seconds(position.seconds))
                    if (position.playing && currentActive.value) playback.play()
                }
                playback.setOnEndOfMedia { position.playing = false; playback.pause() }
            } catch (_: Exception) { scope.launch { if (!disposed.get()) failure.value() } }
        }
        onDispose {
            disposed.set(true)
            Platform.runLater { player.getAndSet(null)?.dispose(); panel.scene = null }
            lease?.close()
        }
    }
    LaunchedEffect(active) {
        Platform.runLater { player.get()?.let { if (active && position.playing) it.play() else it.pause() } }
    }
    SwingPanel(factory = { panel }, modifier = modifier)
}
