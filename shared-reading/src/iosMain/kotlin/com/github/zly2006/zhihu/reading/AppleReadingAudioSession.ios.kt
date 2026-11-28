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

@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.github.zly2006.zhihu.reading

import com.github.zly2006.zhihu.platform.IosAudioInterruption
import com.github.zly2006.zhihu.platform.IosAudioLease
import com.github.zly2006.zhihu.platform.IosAudioSessions
import com.github.zly2006.zhihu.platform.IosAudioUse
import platform.AVFAudio.AVSpeechSynthesizer
import platform.AVFAudio.AVAudioSessionMediaServicesWereResetNotification
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.darwin.NSObjectProtocol

internal actual fun appleReadingAudioSession(
    listener: () -> ReadingSpeechListener?,
    onServicesReset: () -> Unit,
    awaitSuspended: suspend () -> Unit,
): AppleReadingAudioSession = object : AppleReadingAudioSession {
    private var lease: IosAudioLease? = null
    // 暂停会释放 lease，但引擎仍存活，reset 必须覆盖这段时间。
    private var resetObserver: NSObjectProtocol? = NSNotificationCenter.defaultCenter.addObserverForName(
        AVAudioSessionMediaServicesWereResetNotification, null, NSOperationQueue.mainQueue,
    ) {
        release()
        onServicesReset()
        listener()?.onInterrupted(false)
    }

    override fun acquire(): Boolean {
        lease?.takeIf { it.isValid }?.let { return it.isActive }
        lease = IosAudioSessions.acquire(
            IosAudioUse.Playback,
            onInterrupted = { reason ->
                val temporary = reason == IosAudioInterruption.Recording || reason == IosAudioInterruption.System
                if (!temporary) lease = null
                listener()?.onInterrupted(temporary)
            },
            onResume = { listener()?.onInterruptionEnded() },
            awaitSuspended = awaitSuspended,
        )
        return lease?.isActive == true
    }

    override fun release() { val previous = lease; lease = null; previous?.close() }
    override fun configure(synthesizer: AVSpeechSynthesizer) { synthesizer.usesApplicationAudioSession = true }
    override fun close() {
        release()
        resetObserver?.let(NSNotificationCenter.defaultCenter::removeObserver)
        resetObserver = null
    }
}
