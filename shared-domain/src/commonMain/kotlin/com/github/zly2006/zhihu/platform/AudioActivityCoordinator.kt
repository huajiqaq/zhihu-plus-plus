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

package com.github.zly2006.zhihu.platform

import com.github.zly2006.zhihu.util.AppLog
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

enum class AudioActivityInterruption { Recording, Superseded }

class AudioActivityLease internal constructor(
    internal val recording: Boolean,
    internal val onInterrupted: (AudioActivityInterruption) -> Unit,
    internal val onResume: () -> Unit,
    internal val awaitSuspended: suspend () -> Unit = {},
) : AutoCloseable {
    var isActive = false
        internal set

    var isValid = true
        internal set

    override fun close() = AudioActivityCoordinator.release(this)
}

/** 主线程内协调应用自己的播放器和麦克风；iOS 使用统一系统音频会话，不重复获取此租约。 */
object AudioActivityCoordinator {
    private var current: AudioActivityLease? = null
    private var pausedPlayback: AudioActivityLease? = null
    private var changing = false

    fun acquirePlayback(
        onInterrupted: (AudioActivityInterruption) -> Unit,
        onResume: () -> Unit = {},
        awaitSuspended: suspend () -> Unit = {},
    ): AudioActivityLease? = acquire(recording = false, onInterrupted, onResume, awaitSuspended)

    suspend fun acquireRecording(
        onInterrupted: (AudioActivityInterruption) -> Unit,
    ): AudioActivityLease? {
        val previous = current
        val lease = acquire(recording = true, onInterrupted, {}) ?: return null
        lease.isActive = false
        try {
            previous?.awaitSuspended?.invoke()
            currentCoroutineContext().ensureActive()
            if (!lease.isValid || current !== lease) return null
            lease.isActive = true
            return lease
        } catch (error: Throwable) {
            lease.close()
            throw error
        }
    }

    private fun acquire(
        recording: Boolean,
        onInterrupted: (AudioActivityInterruption) -> Unit,
        onResume: () -> Unit,
        awaitSuspended: suspend () -> Unit = {},
    ): AudioActivityLease? {
        if (changing || current?.recording == true) return null
        changing = true
        val previous = current
        val lease = AudioActivityLease(recording, onInterrupted, onResume, awaitSuspended)
        try {
            current = lease
            if (previous != null) {
                previous.isActive = false
                if (recording) {
                    pausedPlayback = previous
                } else {
                    previous.isValid = false
                }
                previous.onInterrupted(if (recording) AudioActivityInterruption.Recording else AudioActivityInterruption.Superseded)
            }
            lease.isActive = true
            return lease
        } catch (error: Exception) {
            lease.isValid = false
            current = null
            previous?.isValid = false
            pausedPlayback = null
            AppLog.e("AudioActivity", "Could not interrupt the current audio activity", error)
            return null
        } finally {
            changing = false
        }
    }

    internal fun release(lease: AudioActivityLease) {
        if (!lease.isValid) return
        lease.isValid = false
        lease.isActive = false
        if (pausedPlayback === lease) pausedPlayback = null
        if (current !== lease) return
        current = null
        if (changing) return
        val previous = pausedPlayback
        pausedPlayback = null
        if (previous?.isValid != true) return
        current = previous
        previous.isActive = true
        try {
            previous.onResume()
        } catch (error: Exception) {
            previous.close()
            AppLog.e("AudioActivity", "Could not resume playback after recording", error)
        }
    }
}
