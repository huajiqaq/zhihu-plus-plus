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

private const val MODEL_REVISION = "8acc1289891d75f6b665ad623359798b55f86adb"
private const val MODEL_BASE_URL = "https://huggingface.co/shibing624/text2vec-base-chinese/resolve/$MODEL_REVISION/onnx"

/** CPU 基础 ONNX 与同一版本的 BERT tokenizer；不使用 GPU 优化的 O4 或可变 main 引用。 */
val offlineNlpModelSpec = OfflineNlpModelSpec(
    id = "text2vec-base-chinese-fp32",
    revision = MODEL_REVISION,
    files = listOf(
        OfflineNlpModelFile(
            name = "model.onnx",
            url = "$MODEL_BASE_URL/model.onnx",
            expectedBytes = 406_953_148,
            sha256 = "716d380a65efde09842642540749bc0535b05f2c66737fea1106731b8b0d7ffb",
        ),
        OfflineNlpModelFile(
            name = "tokenizer.json",
            url = "$MODEL_BASE_URL/tokenizer.json",
            expectedBytes = 439_124,
            sha256 = "7dfbf1966ebf99d471c3796e9b457329d2b2182b817e144f1e904b957745c839",
        ),
    ),
)
