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

/** 引擎回调和控制操作均在主调度器串行执行；取消的语句仍须携带原始 id。 */
internal interface ReadingSpeechListener {
    fun onReady()
    fun onInitializationFailed(message: String)
    fun onStarted(id: String)
    fun onFinished(id: String)
    fun onFailed(id: String, failure: ReadingSpeechFailure)
    fun onInterrupted(resumeAutomatically: Boolean)
    fun onInterruptionEnded()
    fun onCommand(command: ReadingMediaCommand)
}

internal enum class ReadingMediaCommand { Play, Pause, Previous, Next, Stop }

internal enum class ReadingSpeechFailureKind { Request, Synthesis, Engine }

internal data class ReadingSpeechFailure(
    val kind: ReadingSpeechFailureKind,
    val message: String,
)

/** 单段系统语音适配；队列、内容加载、重试及切篇只属于公共控制器。 */
internal interface ReadingSpeechEngine {
    var listener: ReadingSpeechListener?
    val label: String
    val availableLabels: List<String>

    fun initialize(languageTag: String, speed: Float)
    fun speak(text: String, id: String)

    /** true 表示系统保存了句中位置；false 表示控制器必须从当前分块重播。 */
    fun pause(): Boolean = false
    fun resume(): Boolean = false
    fun setSpeed(speed: Float)
    fun stop()
    fun close()

    fun acquireAudio(): Boolean = true
    fun releaseAudio() = Unit
    fun keepAwake(enabled: Boolean) = Unit
    fun publish(state: ReadingPlayerState) = Unit
}
