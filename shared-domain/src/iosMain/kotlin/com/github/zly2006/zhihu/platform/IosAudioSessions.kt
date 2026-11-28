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
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionAllowBluetoothHFP
import platform.AVFAudio.AVAudioSessionCategoryOptionDefaultToSpeaker
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionInterruptionNotification
import platform.AVFAudio.AVAudioSessionInterruptionOptionKey
import platform.AVFAudio.AVAudioSessionInterruptionOptionShouldResume
import platform.AVFAudio.AVAudioSessionInterruptionTypeBegan
import platform.AVFAudio.AVAudioSessionInterruptionTypeEnded
import platform.AVFAudio.AVAudioSessionInterruptionTypeKey
import platform.AVFAudio.AVAudioSessionMediaServicesWereResetNotification
import platform.AVFAudio.AVAudioSessionModeDefault
import platform.AVFAudio.AVAudioSessionRouteChangeNotification
import platform.AVFAudio.AVAudioSessionRouteChangeReasonKey
import platform.AVFAudio.AVAudioSessionRouteChangeReasonOldDeviceUnavailable
import platform.AVFAudio.AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation
import platform.AVFAudio.setActive
import platform.Foundation.NSNotification
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSThread
import platform.darwin.NSObjectProtocol

/** 音频会话由应用统一持有，录音期间暂停播放，不能由各引擎独立切换分类。 */
enum class IosAudioUse { Playback, Recording }

enum class IosAudioInterruption { Superseded, Recording, System, RouteChange, ServicesReset, ActivationFailed }

class IosAudioLease internal constructor(
    internal val use: IosAudioUse,
    internal val onInterrupted: (IosAudioInterruption) -> Unit,
    internal val onResume: () -> Unit,
    internal val awaitSuspended: suspend () -> Unit = {},
) : AutoCloseable {
    var isActive: Boolean = false
        internal set

    var isValid: Boolean = true
        internal set

    override fun close() = IosAudioSessions.release(this)
}

@OptIn(ExperimentalForeignApi::class)
object IosAudioSessions {
    private val session = AVAudioSession.sharedInstance()
    private val observers = mutableListOf<NSObjectProtocol>()
    private var current: IosAudioLease? = null
    private var pausedPlayback: IosAudioLease? = null
    private var systemInterrupted = false
    private var changing = false

    fun acquire(
        use: IosAudioUse,
        onInterrupted: (IosAudioInterruption) -> Unit,
        onResume: () -> Unit = {},
        awaitSuspended: suspend () -> Unit = {},
    ): IosAudioLease? {
        checkMainThread()
        require(use == IosAudioUse.Playback) { "Recording must await acquireRecording" }
        if (changing || systemInterrupted || current?.use == IosAudioUse.Recording) return null
        val next = IosAudioLease(use, onInterrupted, onResume, awaitSuspended)
        changing = true
        try {
            installObservers()
            val previous = current
            current = next
            if (previous != null) invalidate(previous, IosAudioInterruption.Superseded)
            if (!activate(next)) {
                next.isValid = false
                if (current === next) current = null
                return null
            }
            return next
        } finally {
            changing = false
            if (current == null) restorePlaybackOrDeactivate()
        }
    }

    /** 保留录音所有权但不开麦，直到旧播放器确认实际暂停；失败不切换音频分类。 */
    suspend fun acquireRecording(onInterrupted: (IosAudioInterruption) -> Unit): IosAudioLease? {
        checkMainThread()
        if (changing || systemInterrupted || current?.use == IosAudioUse.Recording) return null
        val previous = current
        val next = IosAudioLease(IosAudioUse.Recording, onInterrupted, {})
        changing = true
        try {
            installObservers()
            current = next
            if (previous?.isValid == true) {
                previous.isActive = false
                pausedPlayback = previous
                // 这里不能吞异常：未成功暂停就不能授予录音。
                previous.onInterrupted(IosAudioInterruption.Recording)
            }
        } catch (error: Throwable) {
            next.isValid = false
            if (current === next) current = null
            throw error
        } finally {
            changing = false
            if (current == null) restorePlaybackOrDeactivate()
        }
        try {
            previous?.awaitSuspended?.invoke()
            currentCoroutineContext().ensureActive()
            checkMainThread()
            if (current !== next || !next.isValid || systemInterrupted) {
                next.close()
                return null
            }
            if (!activate(next)) {
                next.close()
                return null
            }
            return next
        } catch (error: Throwable) {
            next.close()
            throw error
        }
    }

    internal fun release(lease: IosAudioLease) {
        checkMainThread()
        if (!lease.isValid) return
        lease.isValid = false
        lease.isActive = false
        if (pausedPlayback === lease) pausedPlayback = null
        if (current === lease) current = null
        if (!changing && current == null) restorePlaybackOrDeactivate()
    }

    private fun activate(lease: IosAudioLease): Boolean {
        val recording = lease.use == IosAudioUse.Recording
        val options = if (recording) {
            AVAudioSessionCategoryOptionDefaultToSpeaker or AVAudioSessionCategoryOptionAllowBluetoothHFP
        } else {
            0uL
        }
        val category = if (recording) AVAudioSessionCategoryPlayAndRecord else AVAudioSessionCategoryPlayback
        val activated = session.setCategory(category, AVAudioSessionModeDefault, options, null) &&
            session.setActive(true, error = null)
        lease.isActive = activated
        return activated
    }

    private fun restorePlaybackOrDeactivate() {
        val previous = pausedPlayback
        pausedPlayback = null
        if (previous?.isValid == true) {
            current = previous
            if (systemInterrupted) return
            if (activate(previous)) {
                notifyResumed(previous)
                return
            }
            current = null
            invalidate(previous, IosAudioInterruption.ActivationFailed)
        }
        deactivate()
        removeObserversIfUnused()
    }

    private fun installObservers() {
        if (observers.isNotEmpty()) return
        val center = NSNotificationCenter.defaultCenter
        observers += center.addObserverForName(AVAudioSessionInterruptionNotification, session, NSOperationQueue.mainQueue) {
            if (it != null) handleInterruption(it)
        }
        observers += center.addObserverForName(AVAudioSessionRouteChangeNotification, session, NSOperationQueue.mainQueue) {
            val reason = (it?.userInfo?.get(AVAudioSessionRouteChangeReasonKey) as? NSNumber)?.unsignedLongLongValue
            if (reason == AVAudioSessionRouteChangeReasonOldDeviceUnavailable) {
                stopAll(IosAudioInterruption.RouteChange)
            }
        }
        observers += center.addObserverForName(AVAudioSessionMediaServicesWereResetNotification, session, NSOperationQueue.mainQueue) {
            stopAll(IosAudioInterruption.ServicesReset)
        }
    }

    private fun handleInterruption(notification: NSNotification) {
        val type = (notification.userInfo?.get(AVAudioSessionInterruptionTypeKey) as? NSNumber)?.unsignedLongLongValue ?: return
        when (type) {
            AVAudioSessionInterruptionTypeBegan -> {
                systemInterrupted = true
                val lease = current ?: return
                lease.isActive = false
                notifyInterrupted(lease, IosAudioInterruption.System)
            }
            AVAudioSessionInterruptionTypeEnded -> {
                systemInterrupted = false
                val options = (notification.userInfo?.get(AVAudioSessionInterruptionOptionKey) as? NSNumber)?.unsignedLongLongValue ?: 0uL
                val lease = current ?: return
                if (lease.use == IosAudioUse.Recording || !lease.isValid) return
                if (options and AVAudioSessionInterruptionOptionShouldResume == 0uL) {
                    lease.close()
                    return
                }
                if (activate(lease)) {
                    notifyResumed(lease)
                } else {
                    current = null
                    invalidate(lease, IosAudioInterruption.ActivationFailed)
                    removeObserversIfUnused()
                }
            }
        }
    }

    private fun stopAll(reason: IosAudioInterruption) {
        checkMainThread()
        val leases = listOfNotNull(current, pausedPlayback)
        current = null
        pausedPlayback = null
        systemInterrupted = false
        changing = true
        try {
            leases.forEach { invalidate(it, reason) }
        } finally {
            changing = false
            deactivate()
            removeObserversIfUnused()
        }
    }

    private fun invalidate(lease: IosAudioLease, reason: IosAudioInterruption) {
        lease.isValid = false
        lease.isActive = false
        notifyInterrupted(lease, reason)
    }

    private fun notifyInterrupted(lease: IosAudioLease, reason: IosAudioInterruption) {
        runCatching { lease.onInterrupted(reason) }.onFailure {
            AppLog.e("IosAudioSessions", "Audio interruption handler failed", it)
        }
    }

    private fun notifyResumed(lease: IosAudioLease) {
        runCatching { lease.onResume() }.onFailure {
            lease.close()
            AppLog.e("IosAudioSessions", "Audio resumption handler failed", it)
        }
    }

    private fun deactivate() {
        if (!systemInterrupted && !session.setActive(false, AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation, null)) {
            AppLog.w("IosAudioSessions", "Audio session deactivation failed")
        }
    }

    private fun removeObserversIfUnused() {
        if (current != null || pausedPlayback != null) return
        observers.forEach(NSNotificationCenter.defaultCenter::removeObserver)
        observers.clear()
        systemInterrupted = false
    }

    private fun checkMainThread() {
        check(NSThread.isMainThread) { "Audio session ownership must be changed on the main thread" }
    }
}
