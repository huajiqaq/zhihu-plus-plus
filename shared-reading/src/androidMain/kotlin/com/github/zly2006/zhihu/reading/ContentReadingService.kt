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

package com.github.zly2006.zhihu.reading

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.IBinder
import android.os.PowerManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.widget.Toast
import androidx.compose.runtime.snapshotFlow
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.github.zly2006.zhihu.account.androidZhihuAccountStore
import com.github.zly2006.zhihu.i18n.AppLocale
import com.github.zly2006.zhihu.i18n.ReadingStrings
import com.github.zly2006.zhihu.viewmodel.SharedAndroidPaginationEnvironment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/** Android 系统宿主保留前台服务、焦点、唤醒锁及媒体通知，播放状态只由公共控制器维护。 */
class ContentReadingService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val accountStore by lazy { androidZhihuAccountStore(applicationContext) }
    private val environmentDelegate = lazy {
        SharedAndroidPaginationEnvironment(applicationContext, allowGuestAccess = true, accountStore = accountStore)
    }
    private val environment by environmentDelegate
    private val audioManager by lazy { getSystemService(AudioManager::class.java) }
    private val wakeLock by lazy {
        getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:content-reading").apply { setReferenceCounted(false) }
    }
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var audioFocusRequest: AudioFocusRequest
    private lateinit var player: ContinuousReadingPlayer
    private var destroyed = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        createMediaSession()
        createAudioFocusRequest()
        val engine = AndroidReadingSpeechEngine(
            this, serviceScope,
            acquireFocus = { audioManager.requestAudioFocus(audioFocusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED },
            abandonFocus = { runCatching { audioManager.abandonAudioFocusRequest(audioFocusRequest) }; Unit },
            wake = { enabled -> if (enabled) acquireWakeLock() else releaseWakeLock() },
        )
        player = ContinuousReadingPlayer(
            serviceScope, ReadingContentResolver(environment), engine,
            showMessage = { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() },
            onState = ::publishState,
            onStopped = {
                mediaSession.isActive = false
                if (!destroyed) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
            },
        )
        serviceScope.launch {
            snapshotFlow { AppLocale.current }.drop(1).collect {
                createNotificationChannel()
                if (player.state.value.hasSession) player.publish()
            }
        }
        serviceScope.launch {
            var owner = accountStore.client
            accountStore.accountsState.collect {
                val current = accountStore.client
                if (current !== owner) { owner = current; player.stop() }
            }
        }
        player.initialize()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val request = AndroidReadingPlayerBridge.consumeStartRequest()
                if (request != null) player.start(request) else if (!player.state.value.hasSession) stopSelfResult(startId)
            }
            ACTION_TOGGLE -> player.togglePlayPause()
            ACTION_PLAY -> player.play()
            ACTION_PAUSE -> player.pause()
            ACTION_PREVIOUS -> player.playPrevious()
            ACTION_NEXT -> player.playNext()
            ACTION_PLAY_AT -> player.playAt(intent.getIntExtra(EXTRA_INDEX, -1))
            ACTION_SET_PLAYBACK_SPEED -> player.setPlaybackSpeed(intent.getFloatExtra(EXTRA_PLAYBACK_SPEED, DEFAULT_READING_PLAYBACK_SPEED))
            ACTION_STOP -> player.stop()
        }
        if (intent?.action != ACTION_START && !player.state.value.hasSession) stopSelfResult(startId)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        destroyed = true
        player.close()
        releaseWakeLock()
        mediaSession.release()
        serviceScope.cancel()
        if (AndroidReadingPlayerBridge.state.value.hasSession) AndroidReadingPlayerBridge.publish(ReadingPlayerState())
        super.onDestroy()
    }

    private fun createMediaSession() {
        mediaSession = MediaSessionCompat(this, TAG).apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = player.play()
                override fun onPause() = player.pause()
                override fun onSkipToPrevious() = player.playPrevious()
                override fun onSkipToNext() = player.playNext()
                override fun onStop() = player.stop()
            })
        }
    }

    private fun createAudioFocusRequest() {
        val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { change ->
                serviceScope.launch {
                    if (destroyed) return@launch
                    when (change) {
                        AudioManager.AUDIOFOCUS_GAIN -> player.onInterruptionEnded()
                        AudioManager.AUDIOFOCUS_LOSS -> player.onInterrupted(false)
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> player.onInterrupted(true)
                    }
                }
            }.build()
    }

    private fun acquireWakeLock() { if (!wakeLock.isHeld) wakeLock.acquire() }
    private fun releaseWakeLock() { if (wakeLock.isHeld) wakeLock.release() }

    private fun publishState(state: ReadingPlayerState) {
        AndroidReadingPlayerBridge.publish(state)
        if (destroyed) return
        mediaSession.isActive = state.hasSession
        updateMediaSession(state)
        if (state.hasSession) ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(state), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
    }

    private fun updateMediaSession(state: ReadingPlayerState) {
        state.currentItem?.let { item ->
            mediaSession.setMetadata(
                MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, item.displayTitle)
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, item.author)
                    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, item.contentType.displayName)
                    .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, "${item.contentType.displayName} · ${state.currentIndex + 1} / ${state.queue.size}")
                    .build(),
            )
            mediaSession.setSessionActivity(contentPendingIntent(item))
        }
        val actions = PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_STOP or
            (if (state.canPlayPrevious) PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS else 0L) or
            (if (state.canPlayNext) PlaybackStateCompat.ACTION_SKIP_TO_NEXT else 0L)
        val status = when (state.status) {
            ReadingPlaybackStatus.Playing -> PlaybackStateCompat.STATE_PLAYING
            ReadingPlaybackStatus.Loading, ReadingPlaybackStatus.Initializing -> PlaybackStateCompat.STATE_BUFFERING
            ReadingPlaybackStatus.Paused -> PlaybackStateCompat.STATE_PAUSED
            ReadingPlaybackStatus.Error -> PlaybackStateCompat.STATE_ERROR
            ReadingPlaybackStatus.Idle -> PlaybackStateCompat.STATE_STOPPED
        }
        mediaSession.setPlaybackState(PlaybackStateCompat.Builder().setActions(actions)
            .setState(status, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, if (state.status == ReadingPlaybackStatus.Playing) state.playbackSpeed else 0f)
            .apply { state.errorMessage?.let { setErrorMessage(PlaybackStateCompat.ERROR_CODE_APP_ERROR, it) } }.build())
    }

    private fun buildNotification(state: ReadingPlayerState): Notification {
        val item = state.currentItem
        val playPause = if (state.isActivelyPlaying) {
            notificationAction(android.R.drawable.ic_media_pause, ReadingStrings.pause, ACTION_PAUSE)
        } else notificationAction(android.R.drawable.ic_media_play, ReadingStrings.resume, ACTION_PLAY)
        val actions = buildList {
            if (state.canPlayPrevious) add(notificationAction(android.R.drawable.ic_media_previous, ReadingStrings.previous, ACTION_PREVIOUS))
            add(playPause)
            if (state.canPlayNext) add(notificationAction(android.R.drawable.ic_media_next, ReadingStrings.next, ACTION_NEXT))
            add(notificationAction(android.R.drawable.ic_menu_close_clear_cancel, ReadingStrings.stop, ACTION_STOP))
        }
        val compactIndices = actions.indices.take(minOf(3, actions.lastIndex)).toIntArray()
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(item?.displayTitle ?: ReadingStrings.continuousReading)
            .setContentText(buildString {
                item?.author?.takeIf(String::isNotBlank)?.let { append(it); append(" · ") }
                item?.contentType?.displayName?.let(::append)
                if (state.hasSession) append(" · ${state.currentIndex + 1} / ${state.queue.size}")
            })
            .setContentIntent(item?.let(::contentPendingIntent))
            .setDeleteIntent(commandPendingIntent(ACTION_STOP, 40))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true).setOngoing(true)
            .also { builder -> actions.forEach(builder::addAction) }
            .setStyle(MediaStyle().setMediaSession(mediaSession.sessionToken)
                .setShowActionsInCompactView(*compactIndices).setShowCancelButton(true)
                .setCancelButtonIntent(commandPendingIntent(ACTION_STOP, 41))).build()
    }

    private fun notificationAction(icon: Int, title: String, action: String) = NotificationCompat.Action(icon, title, commandPendingIntent(action, action.hashCode()))
    private fun commandPendingIntent(action: String, requestCode: Int): PendingIntent = PendingIntent.getService(
        this, requestCode, commandIntent(this, action), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun contentPendingIntent(item: ReadingQueueItem): PendingIntent {
        val url = when (item.contentType) {
            ReadingContentType.Answer -> item.questionId?.let { "https://www.zhihu.com/question/$it/answer/${item.id}" } ?: "https://www.zhihu.com/answer/${item.id}"
            ReadingContentType.Article -> "https://zhuanlan.zhihu.com/p/${item.id}"
            ReadingContentType.Pin -> "https://www.zhihu.com/pin/${item.id}"
            ReadingContentType.Question -> "https://www.zhihu.com/question/${item.id}"
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            setClassName(packageName, MAIN_ACTIVITY_CLASS_NAME)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(READING_NOTIFICATION_INTENT_EXTRA, true)
        }
        return PendingIntent.getActivity(this, item.key.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun createNotificationChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(NOTIFICATION_CHANNEL_ID, ReadingStrings.channelReading, NotificationManager.IMPORTANCE_LOW).apply {
                description = ReadingStrings.channelDescription
                setShowBadge(false)
            },
        )
    }

    companion object {
        const val ACTION_START = "com.github.zly2006.zhihu.reading.START"
        const val ACTION_TOGGLE = "com.github.zly2006.zhihu.reading.TOGGLE"
        const val ACTION_PLAY = "com.github.zly2006.zhihu.reading.PLAY"
        const val ACTION_PAUSE = "com.github.zly2006.zhihu.reading.PAUSE"
        const val ACTION_PREVIOUS = "com.github.zly2006.zhihu.reading.PREVIOUS"
        const val ACTION_NEXT = "com.github.zly2006.zhihu.reading.NEXT"
        const val ACTION_PLAY_AT = "com.github.zly2006.zhihu.reading.PLAY_AT"
        const val ACTION_SET_PLAYBACK_SPEED = "com.github.zly2006.zhihu.reading.SET_PLAYBACK_SPEED"
        const val ACTION_STOP = "com.github.zly2006.zhihu.reading.STOP"
        const val EXTRA_INDEX = "index"
        const val EXTRA_PLAYBACK_SPEED = "playback_speed"
        private const val TAG = "ContentReadingService"
        private const val MAIN_ACTIVITY_CLASS_NAME = "com.github.zly2006.zhihu.MainActivity"
        const val READING_NOTIFICATION_INTENT_EXTRA = "com.github.zly2006.zhihu.reading.NOTIFICATION_CONTENT"
        private const val NOTIFICATION_CHANNEL_ID = "content_reading"
        private const val NOTIFICATION_ID = 2206
        fun commandIntent(context: Context, action: String): Intent = Intent(context, ContentReadingService::class.java).setAction(action)
    }
}
