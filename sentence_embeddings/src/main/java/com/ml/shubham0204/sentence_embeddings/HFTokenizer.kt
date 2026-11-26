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

// JNI 导出符号编码了原包名；改名会断开已发布的原生绑定。
@file:Suppress("ktlint:standard:package-name")

package com.ml.shubham0204.sentence_embeddings

import org.json.JSONObject

class HFTokenizer(
    tokenizerBytes: ByteArray,
) {
    data class Result(
        val ids: LongArray = longArrayOf(),
        val attentionMask: LongArray = longArrayOf(),
        val tokenTypeIds: LongArray = longArrayOf(),
    )

    private var tokenizerPtr: Long = createTokenizer(tokenizerBytes).also {
        check(it != 0L) { "Tokenizer initialization failed" }
    }

    @Synchronized
    fun tokenize(text: String): Result {
        check(tokenizerPtr != 0L) { "Tokenizer is closed" }
        val output = tokenize(tokenizerPtr, text)
        val jsonObject = JSONObject(output)
        val idsArray = jsonObject.getJSONArray("ids")
        val ids = LongArray(idsArray.length()) { (idsArray.get(it) as Int).toLong() }
        val attentionMaskArray = jsonObject.getJSONArray("attention_mask")
        val attentionMask = LongArray(attentionMaskArray.length()) { (attentionMaskArray.get(it) as Int).toLong() }
        val tokenTypeIdsArray = jsonObject.getJSONArray("token_type_ids")
        val tokenTypeIds = LongArray(tokenTypeIdsArray.length()) { (tokenTypeIdsArray.get(it) as Int).toLong() }
        return Result(ids, attentionMask, tokenTypeIds)
    }

    @Synchronized
    fun close() {
        val pointer = tokenizerPtr
        tokenizerPtr = 0L
        if (pointer != 0L) deleteTokenizer(pointer)
    }

    private external fun createTokenizer(tokenizerBytes: ByteArray): Long

    private external fun tokenize(
        tokenizerPtr: Long,
        text: String,
    ): String

    private external fun deleteTokenizer(tokenizerPtr: Long)

    companion object {
        init {
            try {
                System.loadLibrary("hftokenizer")
            } catch (e: UnsatisfiedLinkError) {
                e.printStackTrace()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
