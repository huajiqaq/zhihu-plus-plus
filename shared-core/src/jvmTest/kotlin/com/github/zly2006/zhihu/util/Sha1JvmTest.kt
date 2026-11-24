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

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class Sha1JvmTest {
    @Test
    fun sha1MatchesMessageDigestAcrossPaddingBoundaries() {
        val digest = MessageDigest.getInstance("SHA-1")
        for (length in 0..130) {
            val input = ByteArray(length) { (it * 31 + 7).toByte() }
            assertContentEquals(digest.digest(input), sha1Digest(input), "input length=$length")
        }
    }

    @Test
    fun hmacMatchesMacAcrossKeyAndMessageLengths() {
        val mac = Mac.getInstance("HmacSHA1")
        for (keyLength in listOf(1, 20, 63, 64, 65, 100)) {
            val key = ByteArray(keyLength) { (it * 31 + 7).toByte() }
            mac.init(SecretKeySpec(key, "HmacSHA1"))
            for (messageLength in listOf(0, 1, 55, 56, 64, 119, 130)) {
                val message = ByteArray(messageLength) { (it * 17 + 3).toByte() }
                assertContentEquals(
                    mac.doFinal(message),
                    hmacSha1(key, message),
                    "key length=$keyLength, message length=$messageLength",
                )
            }
        }
    }

    @Test
    fun commonHmacHexMatchesJvmHmacSha1Hex() {
        val cases = listOf(
            "synthetic-key" to "synthetic-message",
            "another-synthetic-key" to "",
            "synthetic-key-".repeat(6) to "synthetic-message".repeat(6),
        )
        for ((key, message) in cases) {
            assertEquals(
                hmacSha1Hex(key, message),
                hmacSha1(key.encodeToByteArray(), message.encodeToByteArray()).toHexString(),
                "key length=${key.length}, message length=${message.length}",
            )
        }
    }
}
