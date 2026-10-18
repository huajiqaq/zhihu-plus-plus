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

package com.github.zly2006.zhihu.ui

internal val PLAIN_URL_REGEX =
    Regex("""https?://[A-Za-z0-9\-._~:/?#\[\]@!$&'()*+,;=%]+""")

private val TRAILING_URL_PUNCTUATION = charArrayOf(
    '。',
    '，',
    '、',
    '；',
    '：',
    '！',
    '？',
    ')',
    '）',
    ']',
    '】',
    ',',
    '.',
    ';',
    ':',
    '!',
    '?',
)

internal data class PlainUrlMatch(
    val url: String,
    val range: IntRange,
)

/**
 * 找出评论文本里的裸链接。中文标点常紧跟在 URL 后面，
 * 不能把它们算进链接，否则点击会打开错误地址。
 */
internal fun findPlainUrls(text: String): List<PlainUrlMatch> =
    PLAIN_URL_REGEX
        .findAll(text)
        .mapNotNull { match ->
            val url = match.value.trimEnd(*TRAILING_URL_PUNCTUATION)
            url.takeIf(String::isNotEmpty)?.let { PlainUrlMatch(it, match.range) }
        }.toList()
