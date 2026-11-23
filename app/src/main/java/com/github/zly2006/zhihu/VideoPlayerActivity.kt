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

import android.content.res.Configuration
import android.media.MediaPlayer
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.github.zly2006.zhihu.platform.androidSettingsStore
import com.github.zly2006.zhihu.util.enableEdgeToEdgeCompat

class VideoPlayerActivity : ComponentActivity() {
    private var player: MediaPlayer? = null
    private var videoId: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val videoUrl = intent.getStringExtra("video_url") ?: run {
            finish()
            return
        }
        videoId = intent.getLongExtra("video_id", 0)
        val settingsStore = androidSettingsStore(this)
        val savedPosition = if (videoId != 0L) {
            settingsStore.getLong("video_progress_$videoId", 0)
        } else {
            0
        }

        enableEdgeToEdgeCompat()
        setContent {
            val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
            val isFastForwardingState = remember { mutableStateOf(false) }

            LaunchedEffect(isLandscape) {
                val ctrl = WindowInsetsControllerCompat(window, window.decorView)
                if (isLandscape) {
                    ctrl.hide(WindowInsetsCompat.Type.systemBars())
                    ctrl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                } else {
                    ctrl.show(WindowInsetsCompat.Type.systemBars())
                }
            }

            Box(Modifier.fillMaxSize().background(Color.Black)) {
                VideoPlayerView(
                    videoUrl = videoUrl,
                    savedPosition = savedPosition,
                    isFastForwardingState = isFastForwardingState,
                    isLandscape = isLandscape,
                    onBack = { finish() },
                    onPlayerReady = { p -> player = p },
                )

                AnimatedVisibility(
                    visible = isFastForwardingState.value,
                    enter = fadeIn(tween(150)),
                    exit = fadeOut(tween(300)),
                    modifier = Modifier.align(Alignment.TopCenter),
                ) {
                    Surface(
                        modifier = Modifier.padding(top = 56.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.35f),
                    ) {
                        Text(
                            "2x",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        )
                    }
                }
            }

            DisposableEffect(Unit) { onDispose { saveCurrentProgress() } }
        }
    }

    override fun onPause() {
        super.onPause()
        saveCurrentProgress()
    }

    override fun onDestroy() {
        super.onDestroy()
        saveCurrentProgress()
        player?.release()
        player = null
    }

    private fun saveCurrentProgress() {
        val p = player ?: return
        if (videoId == 0L) return
        try {
            val pos = p.currentPosition.toLong()
            val d = p.duration.toLong()
            if (pos > 1000 && d > 0) {
                val store = androidSettingsStore(this)
                if (d - pos > 3000) {
                    store.putLong("video_progress_$videoId", pos)
                } else {
                    store.remove("video_progress_$videoId")
                }
            }
        } catch (_: IllegalStateException) {
        }
    }
}
