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

package com.github.zly2006.zhihu.nlp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal data class OfflineNlpTokens(
    val inputIds: LongArray,
    val attentionMask: LongArray,
    val tokenTypeIds: LongArray,
)

/**
 * 仅实现固定模型实际使用的 BertNormalizer / BertPreTokenizer / WordPiece / 单句模板。
 * Unicode 顺序遵循 Hugging Face tokenizers 0.22.2 的 normalizers/bert.rs 与 pre_tokenizers/bert.rs；
 * 先清理、中文分隔、NFD 去 Mn，最后按 code point 小写，不以字符串匹配替代模型 token。
 */
internal class OfflineBertTokenizer(
    tokenizerJson: String,
    private val platform: OfflineNlpPlatform,
    private val maxTokens: Int,
) {
    private val vocabulary: Map<String, Int>
    private val addedTokens: Map<String, Int>
    private val unknownId: Int
    private val clsId: Int
    private val sepId: Int
    private val subwordPrefix: String
    private val maxWordCharacters: Int
    private val cleanText: Boolean
    private val handleChineseCharacters: Boolean
    private val stripAccents: Boolean
    private val lowercase: Boolean

    init {
        require(maxTokens in 2..512) { offlineNlpError(OfflineNlpErrorCode.INVALID_TOKENIZER) }
        require(tokenizerJson.length <= 2_000_000) { offlineNlpError(OfflineNlpErrorCode.INVALID_TOKENIZER) }
        val root = Json.parseToJsonElement(tokenizerJson).jsonObject
        require(root.string("version") == "1.0") { offlineNlpError(OfflineNlpErrorCode.INVALID_TOKENIZER) }
        require(root["truncation"] == JsonNull && root["padding"] == JsonNull) {
            offlineNlpError(OfflineNlpErrorCode.INVALID_TOKENIZER)
        }
        val normalizer = root.getValue("normalizer").jsonObject
        require(normalizer.string("type") == "BertNormalizer") { offlineNlpError(OfflineNlpErrorCode.INVALID_TOKENIZER) }
        cleanText = normalizer.boolean("clean_text")
        handleChineseCharacters = normalizer.boolean("handle_chinese_chars")
        lowercase = normalizer.boolean("lowercase")
        stripAccents = normalizer["strip_accents"].let { value ->
            if (value == null || value == JsonNull) lowercase else value.jsonPrimitive.boolean
        }
        require(root.getValue("pre_tokenizer").jsonObject.string("type") == "BertPreTokenizer") {
            offlineNlpError(OfflineNlpErrorCode.INVALID_TOKENIZER)
        }
        val model = root.getValue("model").jsonObject
        require(model.string("type") == "WordPiece") { offlineNlpError(OfflineNlpErrorCode.INVALID_TOKENIZER) }
        vocabulary = model.getValue("vocab").jsonObject.mapValues { (_, value) -> value.jsonPrimitive.int }
        require(vocabulary.isNotEmpty() && vocabulary.values.all { it >= 0 }) { offlineNlpError(OfflineNlpErrorCode.INVALID_TOKENIZER) }
        require(vocabulary.values.toSet().size == vocabulary.size) { offlineNlpError(OfflineNlpErrorCode.INVALID_TOKENIZER) }
        unknownId = vocabulary.getValue(model.string("unk_token"))
        subwordPrefix = model.string("continuing_subword_prefix")
        maxWordCharacters = model.getValue("max_input_chars_per_word").jsonPrimitive.int
        require(subwordPrefix.isNotEmpty() && maxWordCharacters in 1..1000) { offlineNlpError(OfflineNlpErrorCode.INVALID_TOKENIZER) }
        addedTokens = buildMap {
            root.getValue("added_tokens").jsonArray.forEach { element ->
                val token = element.jsonObject
                require(
                    token.boolean("special") && !token.boolean("normalized") &&
                        !token.boolean("single_word") && !token.boolean("lstrip") && !token.boolean("rstrip"),
                ) { offlineNlpError(OfflineNlpErrorCode.INVALID_TOKENIZER) }
                val text = token.string("content")
                val id = token.getValue("id").jsonPrimitive.int
                require(text.isNotEmpty() && vocabulary[text] == id) { offlineNlpError(OfflineNlpErrorCode.INVALID_TOKENIZER) }
                require(put(text, id) == null) { offlineNlpError(OfflineNlpErrorCode.INVALID_TOKENIZER) }
            }
        }
        val postProcessor = root.getValue("post_processor").jsonObject
        require(postProcessor.string("type") == "TemplateProcessing") { offlineNlpError(OfflineNlpErrorCode.INVALID_TOKENIZER) }
        val template = postProcessor.getValue("single").jsonArray
        require(template.size == 3) { offlineNlpError(OfflineNlpErrorCode.INVALID_TOKENIZER) }
        fun specialId(index: Int): Int {
            val item = template[index].jsonObject
            require(item.size == 1 && "SpecialToken" in item) { offlineNlpError(OfflineNlpErrorCode.INVALID_TOKENIZER) }
            val token = item.getValue("SpecialToken").jsonObject
            require(token.getValue("type_id").jsonPrimitive.int == 0) { offlineNlpError(OfflineNlpErrorCode.INVALID_TOKENIZER) }
            val name = token.string("id")
            val definition = postProcessor.getValue("special_tokens").jsonObject.getValue(name).jsonObject
            val ids = definition.getValue("ids").jsonArray
            val tokens = definition.getValue("tokens").jsonArray
            require(ids.size == 1 && tokens.size == 1 && tokens.single().jsonPrimitive.content == name) {
                offlineNlpError(OfflineNlpErrorCode.INVALID_TOKENIZER)
            }
            val id = ids.single().jsonPrimitive.int
            require(addedTokens[name] == id) { offlineNlpError(OfflineNlpErrorCode.INVALID_TOKENIZER) }
            return id
        }
        clsId = specialId(0)
        sepId = specialId(2)
        val sequence = template[1].jsonObject
        require(sequence.size == 1 && "Sequence" in sequence) { offlineNlpError(OfflineNlpErrorCode.INVALID_TOKENIZER) }
        val sequenceSpec = sequence.getValue("Sequence").jsonObject
        require(sequenceSpec.string("id") == "A" && sequenceSpec.getValue("type_id").jsonPrimitive.int == 0) {
            offlineNlpError(OfflineNlpErrorCode.INVALID_TOKENIZER)
        }
    }

    fun encode(text: String): OfflineNlpTokens {
        val ids = ArrayList<Long>(maxTokens)
        ids.add(clsId.toLong())
        val bodyLimit = maxTokens - 1
        fun appendOrdinaryText(part: String) {
            if (ids.size >= bodyLimit) return
            for (word in preTokenize(normalize(part))) {
                for (id in wordPiece(word)) {
                    if (ids.size >= bodyLimit) return
                    ids.add(id.toLong())
                }
            }
        }
        var cursor = 0
        while (cursor < text.length && ids.size < bodyLimit) {
            var nextPosition = text.length
            var nextToken: String? = null
            for (special in addedTokens.keys) {
                val position = text.indexOf(special, cursor)
                if (position >= 0 &&
                    (position < nextPosition || (position == nextPosition && special.length > (nextToken?.length ?: 0)))
                ) {
                    nextPosition = position
                    nextToken = special
                }
            }
            appendOrdinaryText(text.substring(cursor, nextPosition))
            val special = nextToken ?: break
            if (ids.size < bodyLimit) ids.add(addedTokens.getValue(special).toLong())
            cursor = nextPosition + special.length
        }
        ids.add(sepId.toLong())
        return OfflineNlpTokens(ids.toLongArray(), LongArray(ids.size) { 1L }, LongArray(ids.size))
    }

    private fun normalize(text: String): String {
        val cleaned = buildString {
            for (character in text.codePointsAsStrings()) {
                val codePoint = character.codePointValue()
                if (cleanText &&
                    (codePoint == 0 || codePoint == 0xfffd ||
                        (codePoint != 9 && codePoint != 10 && codePoint != 13 &&
                            platform.unicodeCategory(codePoint) == OfflineNlpUnicodeCategory.CONTROL_OR_UNASSIGNED))
                ) {
                    continue
                }
                if (cleanText && isBertWhitespace(codePoint)) {
                    append(' ')
                } else if (handleChineseCharacters && isBertChineseCharacter(codePoint)) {
                    append(' ')
                    append(character)
                    append(' ')
                } else {
                    append(character)
                }
            }
        }
        val decomposed = if (stripAccents) platform.normalizeNfd(cleaned) else cleaned
        return buildString {
            for (character in decomposed.codePointsAsStrings()) {
                if (stripAccents && platform.unicodeCategory(character.codePointValue()) == OfflineNlpUnicodeCategory.NON_SPACING_MARK) continue
                // Rust NormalizedString.lowercase 按 scalar 转换，不采用整串的上下文敏感 final sigma。
                append(if (lowercase) character.lowercase() else character)
            }
        }
    }

    private fun preTokenize(text: String): List<String> {
        val result = mutableListOf<String>()
        val word = StringBuilder()
        fun finishWord() {
            if (word.isNotEmpty()) {
                result.add(word.toString())
                word.clear()
            }
        }
        for (character in text.codePointsAsStrings()) {
            val codePoint = character.codePointValue()
            when {
                isBertWhitespace(codePoint) -> finishWord()
                codePoint in 0x21..0x2f || codePoint in 0x3a..0x40 ||
                    codePoint in 0x5b..0x60 || codePoint in 0x7b..0x7e ||
                    platform.unicodeCategory(codePoint) == OfflineNlpUnicodeCategory.PUNCTUATION -> {
                    finishWord()
                    result.add(character)
                }
                else -> word.append(character)
            }
        }
        finishWord()
        return result
    }

    private fun wordPiece(word: String): List<Int> {
        val characters = word.codePointsAsStrings()
        if (characters.size > maxWordCharacters) return listOf(unknownId)
        val pieces = mutableListOf<Int>()
        var start = 0
        while (start < characters.size) {
            var end = characters.size
            var match: Int? = null
            while (end > start) {
                val candidate = buildString {
                    if (start != 0) append(subwordPrefix)
                    for (index in start until end) append(characters[index])
                }
                match = vocabulary[candidate]
                if (match != null) break
                end--
            }
            if (match == null) return listOf(unknownId)
            pieces.add(match)
            start = end
        }
        return pieces
    }
}

private fun JsonObject.string(name: String): String = getValue(name).jsonPrimitive.content

private fun JsonObject.boolean(name: String): Boolean = getValue(name).jsonPrimitive.boolean

/** Kotlin 的 String 下标是 UTF-16；分词、长度限制与 Unicode 分类必须按 scalar。 */
private fun String.codePointsAsStrings(): List<String> = buildList {
    var index = 0
    while (index < this@codePointsAsStrings.length) {
        val first = this@codePointsAsStrings[index]
        val length = if (first.isHighSurrogate() && index + 1 < this@codePointsAsStrings.length && this@codePointsAsStrings[index + 1].isLowSurrogate()) 2 else 1
        add(this@codePointsAsStrings.substring(index, index + length))
        index += length
    }
}

private fun String.codePointValue(): Int =
    if (length == 2) 0x10000 + ((this[0].code - 0xd800) shl 10) + (this[1].code - 0xdc00) else this[0].code

private fun isBertWhitespace(codePoint: Int): Boolean =
    codePoint in 0x09..0x0d || codePoint == 0x20 || codePoint == 0x85 || codePoint == 0xa0 ||
        codePoint == 0x1680 || codePoint in 0x2000..0x200a || codePoint == 0x2028 || codePoint == 0x2029 ||
        codePoint == 0x202f || codePoint == 0x205f || codePoint == 0x3000

private fun isBertChineseCharacter(codePoint: Int): Boolean =
    codePoint in 0x4e00..0x9fff || codePoint in 0x3400..0x4dbf || codePoint in 0x20000..0x2a6df ||
        codePoint in 0x2a700..0x2b73f || codePoint in 0x2b740..0x2b81f || codePoint in 0x2b920..0x2ceaf ||
        codePoint in 0xf900..0xfaff || codePoint in 0x2f800..0x2fa1f
