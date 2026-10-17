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

package com.github.zly2006.zhihu.reading

/**
 * 朗读引擎每次 speak 时附带的参数。
 *
 * 部分第三方引擎（如 Multi TTS）不读取 TextToSpeech.setSpeechRate，
 * 只认每次 speak 调用里的 rate；因此速率必须同时出现在这里。
 */
internal const val READING_SPEECH_RATE_PARAM = "rate"
internal const val READING_SPEECH_VOLUME_PARAM = "volume"

internal fun readingSpeechParameters(speechRate: Float): Map<String, Float> = mapOf(
    READING_SPEECH_VOLUME_PARAM to 1f,
    READING_SPEECH_RATE_PARAM to speechRate,
)
