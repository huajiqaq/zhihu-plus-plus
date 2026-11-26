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

package com.github.zly2006.zhihu.util

import kotlin.test.Test
import kotlin.test.assertEquals

class Sha1Test {
    @Test
    fun hmacMatchesRfc2202Vectors() {
        val vectors = listOf(
            Triple(
                ByteArray(20) { 0x0b },
                "Hi There".encodeToByteArray(),
                "b617318655057264e28bc0b6fb378c8ef146be00",
            ),
            Triple(
                "Jefe".encodeToByteArray(),
                "what do ya want for nothing?".encodeToByteArray(),
                "effcdf6ae5eb2fa2d27416d5f184df9c259a7c79",
            ),
            Triple(
                ByteArray(20) { 0xaa.toByte() },
                ByteArray(50) { 0xdd.toByte() },
                "125d7342b9ac11cd91a39af48aa17b4f63f175d3",
            ),
            Triple(
                ByteArray(80) { 0xaa.toByte() },
                "Test Using Larger Than Block-Size Key - Hash Key First".encodeToByteArray(),
                "aa4ae5e15272d00e95705637ce8a3b55ed402112",
            ),
            Triple(
                ByteArray(80) { 0xaa.toByte() },
                "Test Using Larger Than Block-Size Key and Larger Than One Block-Size Data".encodeToByteArray(),
                "e8e99d0f45237d786d6bbaa7965c7808bbff1a91",
            ),
        )
        for ((key, message, expected) in vectors) {
            assertEquals(expected, hmacSha1(key, message).toHexString(), "key length=${key.size}, message length=${message.size}")
        }
    }
}
