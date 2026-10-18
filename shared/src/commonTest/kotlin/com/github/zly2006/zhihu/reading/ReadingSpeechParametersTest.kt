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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ReadingSpeechParametersTest {
    @Test
    fun doesNotOverrideFrameworkSpeechRate() {
        val parameters = readingSpeechParameters()

        assertEquals(1f, parameters[READING_SPEECH_VOLUME_PARAM])
        // 速率由 TextToSpeech.setSpeechRate() 以 Int 写入框架参数；
        // 这里再传 Float 会让 SynthesisRequest.getSpeechRate() 回退到 1x。
        assertFalse("rate" in parameters)
    }
}
