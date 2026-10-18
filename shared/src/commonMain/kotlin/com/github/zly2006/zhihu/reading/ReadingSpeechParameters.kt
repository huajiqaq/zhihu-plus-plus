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
 * 这里不能放 `"rate"`：`TextToSpeech.setSpeechRate()` 已经把速率以 **Int**
 * （`rate * 100`）写进框架内部的 speak 参数；调用方再传同名 Float 会在
 * `TextToSpeech.getParams()` 的 `putAll` 中覆盖它，随后引擎读取
 * `SynthesisRequest.getSpeechRate()` 时 `Bundle.getInt()` 类型不匹配，
 * 回退到系统默认值（1x）。因此速率只走 `setSpeechRate()`，这里仅传音量。
 */
internal const val READING_SPEECH_VOLUME_PARAM = "volume"

internal fun readingSpeechParameters(): Map<String, Float> = mapOf(
    READING_SPEECH_VOLUME_PARAM to 1f,
)
