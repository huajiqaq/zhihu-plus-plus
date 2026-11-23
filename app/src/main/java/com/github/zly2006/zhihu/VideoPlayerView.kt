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

package com.github.zly2006.zhihu

import android.content.ContentValues
import android.graphics.SurfaceTexture
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Surface
import android.view.TextureView
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import kotlinx.coroutines.delay
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PortraitOverlay(toolbarColor: Color, onBack: () -> Unit) {
    val c = if (toolbarColor.luminance() > 0.5f) Color.Black else Color.White
    TopAppBar(
        title = { Text("视频播放", color = c) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = c) } },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = toolbarColor, navigationIconContentColor = c, titleContentColor = c),
        modifier = Modifier.statusBarsPadding().windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)),
    )
}

@Composable
private fun LandscapeOverlay(onBack: () -> Unit) {
    Surface(onClick = onBack, modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp).size(40.dp), shape = CircleShape, color = Color.Black.copy(alpha = 0.5f)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Color.White, modifier = Modifier.size(24.dp))
        }
    }
}

private fun formatTime(ms: Long): String {
    val total = ms / 1000
    return "%d:%02d".format(total / 60, total % 60)
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun VideoPlayerView(
    videoUrl: String,
    savedPosition: Long,
    isFastForwardingState: MutableState<Boolean>,
    isLandscape: Boolean,
    onBack: () -> Unit,
    onPlayerReady: (MediaPlayer) -> Unit,
) {
    val isPlayingState = remember { mutableStateOf(true) }
    var videoEnded by remember { mutableStateOf(false) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var controlsVisible by remember { mutableStateOf(false) }
    var videoHasFocus by remember { mutableStateOf(false) }
    var speedMenuExpanded by remember { mutableStateOf(false) }
    var screenLocked by remember { mutableStateOf(false) }
    var lockHintVisible by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    var dragSeekPreview by remember { mutableLongStateOf(0L) }
    var videoAspectRatio by remember { mutableFloatStateOf(16f / 9f) }
    val ctx = LocalContext.current
    val accessibilityManager = remember(ctx) {
        ctx.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    }
    var touchExplorationEnabled by remember(accessibilityManager) {
        mutableStateOf(accessibilityManager.isTouchExplorationEnabled)
    }
    DisposableEffect(accessibilityManager) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener {
            touchExplorationEnabled = it
        }
        accessibilityManager.addTouchExplorationStateChangeListener(listener)
        onDispose { accessibilityManager.removeTouchExplorationStateChangeListener(listener) }
    }

    fun togglePlayback(): Boolean {
        val p = mediaPlayer ?: return false
        return try {
            if (p.isPlaying) {
                p.pause()
            } else {
                if (videoEnded) {
                    p.seekTo(0)
                    videoEnded = false
                }
                p.start()
            }
            isPlayingState.value = p.isPlaying
            controlsVisible = true
            true
        } catch (_: IllegalStateException) {
            false
        }
    }

    fun safeSetSpeed(speed: Float) {
        val p = mediaPlayer ?: return
        try {
            val wasPlaying = p.isPlaying
            p.playbackParams = PlaybackParams().setSpeed(speed)
            /** 设置 playbackParams 会启动已暂停的播放器。 */
            if (!wasPlaying && p.isPlaying) p.pause()
            isPlayingState.value = p.isPlaying
        } catch (_: Exception) {
        }
    }

    fun safeSeekTo(posMs: Long) {
        try {
            val dur = duration.coerceAtLeast(0)
            if (dur > 0) {
                val target = posMs.coerceIn(0, dur)
                mediaPlayer?.seekTo(target, MediaPlayer.SEEK_CLOSEST)
            }
        } catch (_: Exception) {
        }
    }

    fun safeGetMediaTime(fallback: Long, value: MediaPlayer.() -> Int): Long = try {
        mediaPlayer?.value()?.toLong()?.coerceAtLeast(0) ?: 0
    } catch (_: Exception) {
        fallback
    }

    LaunchedEffect(controlsVisible, isPlayingState.value) {
        while (controlsVisible || isPlayingState.value) {
            currentPosition = safeGetMediaTime(currentPosition) { this.currentPosition }
            duration = safeGetMediaTime(duration) { this.duration }
            delay(200)
        }
    }
    LaunchedEffect(isPlayingState.value, screenLocked, touchExplorationEnabled, videoHasFocus, speedMenuExpanded) {
        if (!screenLocked && (!isPlayingState.value || touchExplorationEnabled || videoHasFocus || speedMenuExpanded)) {
            controlsVisible = true
        }
    }
    LaunchedEffect(controlsVisible, isPlayingState.value, touchExplorationEnabled, videoHasFocus, speedMenuExpanded) {
        if (controlsVisible && isPlayingState.value && !touchExplorationEnabled && !videoHasFocus && !speedMenuExpanded) {
            delay(3000)
            controlsVisible = false
        }
    }
    LaunchedEffect(lockHintVisible) {
        if (lockHintVisible) {
            delay(3000)
            lockHintVisible = false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onFocusChanged { videoHasFocus = it.hasFocus }
            .focusGroup(),
    ) {
        Box(Modifier.align(Alignment.Center).aspectRatio(videoAspectRatio)) {
            AndroidView(
                factory = { viewCtx ->
                    TextureView(viewCtx).apply {
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                                val existing = mediaPlayer
                                if (existing != null) {
                                    val output = Surface(surface)
                                    try {
                                        existing.setSurface(output)
                                    } finally {
                                        output.release()
                                    }
                                    return
                                }
                                val player = MediaPlayer().apply {
                                    setDataSource(viewCtx, videoUrl.toUri())
                                    val output = Surface(surface)
                                    try {
                                        setSurface(output)
                                    } finally {
                                        output.release()
                                    }
                                    setOnPreparedListener {
                                        if (savedPosition > 0) seekTo(savedPosition.toInt())
                                        start()
                                        isPlayingState.value = true
                                    }
                                    setOnVideoSizeChangedListener { _, w, h ->
                                        if (w > 0 && h > 0) videoAspectRatio = w.toFloat() / h.toFloat()
                                    }
                                    setOnCompletionListener {
                                        videoEnded = true
                                        isPlayingState.value = false
                                    }
                                    setOnErrorListener { _, _, _ -> true }
                                    prepareAsync()
                                }
                                mediaPlayer = player
                                onPlayerReady(player)
                            }

                            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}

                            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                                mediaPlayer?.setSurface(null)
                                return true
                            }

                            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        val screenW = LocalResources.current.displayMetrics.widthPixels
            .toFloat()
        val viewCfg = LocalViewConfiguration.current
        var fingerDown by remember { mutableStateOf(false) }

        LaunchedEffect(fingerDown) {
            if (!fingerDown) return@LaunchedEffect
            delay(viewCfg.longPressTimeoutMillis + 150L)
            if (fingerDown && !screenLocked) {
                safeSetSpeed(2f)
                isFastForwardingState.value = true
            }
        }
        LaunchedEffect(fingerDown, isFastForwardingState.value) {
            if (!fingerDown && isFastForwardingState.value) {
                safeSetSpeed(playbackSpeed)
                isFastForwardingState.value = false
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = "视频播放区域"
                    stateDescription = if (screenLocked) {
                        "已锁定"
                    } else if (isPlayingState.value) {
                        "正在播放"
                    } else {
                        "已暂停"
                    }
                    onClick(label = "显示播放控件") {
                        if (screenLocked) lockHintVisible = true else controlsVisible = true
                        true
                    }
                    customActions = if (screenLocked) {
                        listOf(
                            CustomAccessibilityAction("解锁") {
                                screenLocked = false
                                controlsVisible = true
                                lockHintVisible = false
                                true
                            },
                        )
                    } else {
                        listOf(CustomAccessibilityAction(if (isPlayingState.value) "暂停" else "播放") { togglePlayback() })
                    }
                }.onKeyEvent { event ->
                    if (event.key != Key.Enter && event.key != Key.Spacebar) {
                        false
                    } else {
                        if (event.type == KeyEventType.KeyUp) {
                            if (screenLocked) {
                                screenLocked = false
                                controlsVisible = true
                                lockHintVisible = false
                            } else {
                                togglePlayback()
                            }
                        }
                        true
                    }
                }.focusable()
                .pointerInput(screenLocked) {
                    var lastTapTime = 0L
                    var tapCount = 0
                    awaitPointerEventScope {
                        while (true) {
                            var down: PointerInputChange? = null
                            while (down == null) {
                                val e = awaitPointerEvent(PointerEventPass.Main)
                                down = e.changes.firstOrNull { it.pressed }
                            }
                            val downPos = down.position
                            var totalDragX = 0f
                            var isDragging = false
                            if (!screenLocked) fingerDown = true
                            var done = false
                            while (!done) {
                                val ev = awaitPointerEvent(PointerEventPass.Main)
                                val ch = ev.changes.firstOrNull() ?: break
                                if (ch.changedToUp()) {
                                    done = true
                                    fingerDown = false
                                    if (screenLocked) {
                                        lockHintVisible = true
                                    } else if (isDragging) {
                                        val ms = (totalDragX / screenW * 15 * 1000).toLong()
                                        val mp = mediaPlayer
                                        if (mp != null) {
                                            try {
                                                val dur = mp.duration.coerceAtLeast(0)
                                                val pos = mp.currentPosition.coerceAtLeast(0)
                                                if (dur > 0) {
                                                    val target = (pos.toLong() + ms).coerceIn(0L, dur.toLong())
                                                    mp.seekTo(target, MediaPlayer.SEEK_CLOSEST)
                                                    currentPosition = mp.currentPosition.coerceAtLeast(0).toLong()
                                                }
                                            } catch (_: Exception) {
                                            }
                                        }
                                        dragSeekPreview = 0
                                        controlsVisible = true
                                    } else {
                                        val now = System.currentTimeMillis()
                                        if (now - lastTapTime < 300) tapCount++ else tapCount = 1
                                        lastTapTime = now
                                        if (tapCount >= 2) {
                                            togglePlayback()
                                            tapCount = 0
                                        } else {
                                            controlsVisible = !controlsVisible || touchExplorationEnabled || videoHasFocus || speedMenuExpanded
                                        }
                                    }
                                } else if (!ch.pressed) {
                                    done = true
                                    fingerDown = false
                                } else {
                                    if (!isDragging && !isFastForwardingState.value && !screenLocked) {
                                        val dx = ch.position.x - downPos.x
                                        totalDragX = dx
                                        if (abs(dx) > viewConfiguration.touchSlop) {
                                            isDragging = true
                                            fingerDown = false
                                            ch.consume()
                                        }
                                    }
                                    if (isDragging) {
                                        ch.consume()
                                        totalDragX = ch.position.x - downPos.x
                                        dragSeekPreview = (totalDragX / screenW * 15 * 1000).toLong()
                                    }
                                }
                            }
                        }
                    }
                },
        )

        AnimatedVisibility(visible = controlsVisible, enter = EnterTransition.None, exit = ExitTransition.None, modifier = Modifier.align(Alignment.TopStart)) {
            if (isLandscape) {
                LandscapeOverlay(onBack = onBack)
            } else {
                PortraitOverlay(Color(0xFF1A1A2E).copy(alpha = 0.85f), onBack = onBack)
            }
        }

        VideoPlayerControls(
            visible = controlsVisible,
            isPlaying = isPlayingState.value,
            timeText = { "${formatTime(currentPosition)} / ${formatTime(duration)}" },
            playbackSpeed = playbackSpeed,
            speedMenuExpanded = speedMenuExpanded,
            onTogglePlayback = { togglePlayback() },
            onScreenshot = {
                val url = videoUrl
                val pos = currentPosition
                Thread {
                    try {
                        val r = MediaMetadataRetriever()
                        r.setDataSource(url, HashMap<String, String>())
                        val bmp = r.getFrameAtTime(pos * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        r.release()
                        if (bmp == null) {
                            Handler(Looper.getMainLooper()).post { Toast.makeText(ctx, "截图失败", Toast.LENGTH_SHORT).show() }
                            return@Thread
                        }
                        val v = ContentValues().apply {
                            put(MediaStore.Images.Media.DISPLAY_NAME, "zhihu_${System.currentTimeMillis()}.jpg")
                            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Zhihu")
                        }
                        val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v)
                        if (uri != null) {
                            ctx.contentResolver.openOutputStream(uri)?.use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, it) }
                            Handler(Looper.getMainLooper()).post { Toast.makeText(ctx, "截图已保存", Toast.LENGTH_SHORT).show() }
                        }
                    } catch (e: Exception) {
                        Handler(Looper.getMainLooper()).post { Toast.makeText(ctx, "截图失败: ${e.message}", Toast.LENGTH_SHORT).show() }
                    }
                }.start()
            },
            onLock = {
                screenLocked = true
                controlsVisible = false
                lockHintVisible = true
            },
            onDownload = {
                val dm = ctx.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                dm.enqueue(
                    android.app.DownloadManager.Request(videoUrl.toUri()).apply {
                        setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "zhihu_video_${System.currentTimeMillis()}.mp4")
                        setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    },
                )
                Toast.makeText(ctx, "开始下载", Toast.LENGTH_SHORT).show()
            },
            onShowSpeedMenu = { speedMenuExpanded = true },
            onDismissSpeedMenu = { speedMenuExpanded = false },
            onSpeedSelected = { sp ->
                playbackSpeed = sp
                safeSetSpeed(sp)
                speedMenuExpanded = false
            },
            progress = {
                val progressState = rememberSliderState()
                progressState.value = if (duration > 0) currentPosition.toFloat() / duration else 0f
                VideoPlayerProgress(
                    progressState = progressState,
                    currentPosition = currentPosition,
                    duration = duration,
                    timeText = "${formatTime(currentPosition)} / ${formatTime(duration)}",
                    onValueChange = {
                        val pos = (it * duration).toLong()
                        safeSeekTo(pos)
                        currentPosition = safeGetMediaTime(currentPosition) { this.currentPosition }
                    },
                )
            },
        )

        AnimatedVisibility(visible = screenLocked && lockHintVisible, enter = EnterTransition.None, exit = ExitTransition.None, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)) {
            Surface(onClick = {
                screenLocked = false
                controlsVisible = true
                lockHintVisible = false
            }, shape = CircleShape, color = Color.Black.copy(alpha = 0.45f), modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Lock, "解锁", tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp)) }
            }
        }

        if (dragSeekPreview != 0L && duration > 0) {
            Surface(Modifier.align(Alignment.Center).padding(bottom = 80.dp), shape = RoundedCornerShape(8.dp), color = Color.Black.copy(alpha = 0.6f)) {
                Text("${if (dragSeekPreview > 0) "+" else ""}${dragSeekPreview / 1000}s  ${formatTime((currentPosition + dragSeekPreview).coerceIn(0, duration))}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
        }
    }
}
