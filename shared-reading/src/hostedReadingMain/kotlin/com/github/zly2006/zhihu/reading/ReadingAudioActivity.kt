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

import com.github.zly2006.zhihu.platform.AudioActivityCoordinator
import com.github.zly2006.zhihu.platform.AudioActivityInterruption
import com.github.zly2006.zhihu.platform.AudioActivityLease

/** 非 iOS 引擎在主线程借用应用音频所有者，录音中不能启动第二段朗读。 */
internal class ReadingAudioActivity(
    private val listener: () -> ReadingSpeechListener?,
    private val awaitSuspended: suspend () -> Unit = {},
) {
    private var lease: AudioActivityLease? = null
    fun acquire(): Boolean {
        lease?.takeIf { it.isValid }?.let { return it.isActive }
        lease = AudioActivityCoordinator.acquirePlayback(
            onInterrupted = { reason ->
                val temporary = reason == AudioActivityInterruption.Recording
                if (!temporary) lease = null
                listener()?.onInterrupted(temporary)
            },
            onResume = { listener()?.onInterruptionEnded() },
            awaitSuspended = awaitSuspended,
        )
        return lease?.isActive == true
    }
    fun release() { val previous = lease; lease = null; previous?.close() }
}
