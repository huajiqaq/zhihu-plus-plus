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

package com.ml.shubham0204.sentence_embeddings;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertThrows;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class HFTokenizerInstrumentedTest {
    private static final String TOKENIZER_JSON = """
        {
            "version": "1.0",
            "truncation": null,
            "padding": {
                "strategy": {"Fixed": 4},
                "direction": "Right",
                "pad_to_multiple_of": null,
                "pad_id": 1,
                "pad_type_id": 1,
                "pad_token": "[PAD]"
            },
            "added_tokens": [],
            "normalizer": null,
            "pre_tokenizer": {"type": "WhitespaceSplit"},
            "post_processor": null,
            "decoder": null,
            "model": {
                "type": "WordLevel",
                "vocab": {"[UNK]": 0, "[PAD]": 1, "hello": 2, "world": 3, "你好": 4, "𠮷": 5},
                "unk_token": "[UNK]"
            }
        }
        """;

    @Test
    public void createsTokenizerAndReturnsExactEncoding() {
        HFTokenizer tokenizer = new HFTokenizer(TOKENIZER_JSON.getBytes(StandardCharsets.UTF_8));
        try {
            HFTokenizer.Result result = tokenizer.tokenize("hello world");
            assertArrayEquals(new long[] {2, 3, 1, 1}, result.getIds());
            assertArrayEquals(new long[] {1, 1, 0, 0}, result.getAttentionMask());
            assertArrayEquals(new long[] {0, 0, 1, 1}, result.getTokenTypeIds());

            result = tokenizer.tokenize("你好 𠮷 unlisted");
            assertArrayEquals(new long[] {4, 5, 0, 1}, result.getIds());
            assertArrayEquals(new long[] {1, 1, 1, 0}, result.getAttentionMask());
            assertArrayEquals(new long[] {0, 0, 0, 1}, result.getTokenTypeIds());

            result = tokenizer.tokenize("");
            assertArrayEquals(new long[] {1, 1, 1, 1}, result.getIds());
            assertArrayEquals(new long[] {0, 0, 0, 0}, result.getAttentionMask());
            assertArrayEquals(new long[] {1, 1, 1, 1}, result.getTokenTypeIds());
        } finally {
            tokenizer.close();
        }
    }

    @Test
    public void rejectsInvalidTokenizerBytes() {
        for (byte[] bytes : new byte[][] {
            "not JSON".getBytes(StandardCharsets.UTF_8),
            "{}".getBytes(StandardCharsets.UTF_8),
        }) {
            assertThrows(IllegalArgumentException.class, () -> {
                HFTokenizer tokenizer = new HFTokenizer(bytes);
                tokenizer.close();
            });
        }
        assertThrows(NullPointerException.class, () -> new HFTokenizer(null));
    }

    @Test
    public void encodingErrorLeavesTokenizerUsable() {
        String json = TOKENIZER_JSON.replace("\"unk_token\": \"[UNK]\"", "\"unk_token\": \"[MISSING]\"");
        HFTokenizer tokenizer = new HFTokenizer(json.getBytes(StandardCharsets.UTF_8));
        try {
            assertThrows(IllegalArgumentException.class, () -> tokenizer.tokenize("unlisted"));
            HFTokenizer.Result result = tokenizer.tokenize("hello");
            assertArrayEquals(new long[] {2, 1, 1, 1}, result.getIds());
            assertArrayEquals(new long[] {1, 0, 0, 0}, result.getAttentionMask());
            assertArrayEquals(new long[] {0, 1, 1, 1}, result.getTokenTypeIds());
        } finally {
            tokenizer.close();
        }
    }

    @Test
    public void rejectsNullTextAndClosedUseWithIdempotentClose() {
        HFTokenizer tokenizer = new HFTokenizer(TOKENIZER_JSON.getBytes(StandardCharsets.UTF_8));
        try {
            assertThrows(NullPointerException.class, () -> tokenizer.tokenize(null));
            assertArrayEquals(new long[] {2, 1, 1, 1}, tokenizer.tokenize("hello").getIds());
            tokenizer.close();
            tokenizer.close();
            assertThrows(IllegalStateException.class, () -> tokenizer.tokenize("hello"));
        } finally {
            tokenizer.close();
        }
    }
}
