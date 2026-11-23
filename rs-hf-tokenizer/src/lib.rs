/*
 * Copyright 2024 Shubham Panchal
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

use jni::objects::{JByteArray, JClass, JString};
use jni::sys::jlong;
use jni::JNIEnv;
use serde::Serialize;
use tokenizers::Tokenizer;

#[derive(Serialize)]
struct TokenizationResult {
    ids: Vec<u32>,
    attention_mask: Vec<u32>,
    token_type_ids: Vec<u32>,
}

#[no_mangle]
pub extern "C" fn Java_com_ml_shubham0204_sentence_1embeddings_HFTokenizer_createTokenizer<'a>(
    mut env: JNIEnv<'a>,
    _: JClass<'a>,
    tokenizer_bytes: JByteArray<'a>,
) -> jlong {
    let bytes = match env.convert_byte_array(&tokenizer_bytes) {
        Ok(bytes) => bytes,
        Err(error) => {
            let _ = env.throw_new("java/lang/IllegalArgumentException", error.to_string());
            return 0;
        }
    };
    match Tokenizer::from_bytes(&bytes) {
        Ok(tokenizer) => Box::into_raw(Box::new(tokenizer)) as jlong,
        Err(error) => {
            let _ = env.throw_new("java/lang/IllegalArgumentException", error.to_string());
            0
        }
    }
}

#[no_mangle]
pub extern "C" fn Java_com_ml_shubham0204_sentence_1embeddings_HFTokenizer_tokenize<'a>(
    mut env: JNIEnv<'a>,
    _: JClass<'a>,
    tokenizer_ptr: jlong,
    text: JString<'a>,
) -> JString<'a> {
    if tokenizer_ptr == 0 {
        let _ = env.throw_new("java/lang/IllegalStateException", "Tokenizer is closed");
        return JString::default();
    }
    // 句柄只指向创建时的 Tokenizer；Kotlin 同步访问并在关闭前清空句柄。
    let tokenizer = unsafe { &*(tokenizer_ptr as *const Tokenizer) };
    let text: String = match env.get_string(&text).map(|text| text.into()) {
        Ok(text) => text,
        Err(error) => {
            let _ = env.throw_new("java/lang/IllegalArgumentException", error.to_string());
            return JString::default();
        }
    };
    let encoding = match tokenizer.encode(text, true) {
        Ok(encoding) => encoding,
        Err(error) => {
            let _ = env.throw_new("java/lang/IllegalArgumentException", error.to_string());
            return JString::default();
        }
    };
    let result = TokenizationResult {
        ids: encoding.get_ids().to_vec(),
        attention_mask: encoding.get_attention_mask().to_vec(),
        token_type_ids: encoding.get_type_ids().to_vec(),
    };
    let json = match serde_json::to_string(&result) {
        Ok(json) => json,
        Err(error) => {
            let _ = env.throw_new("java/lang/IllegalStateException", error.to_string());
            return JString::default();
        }
    };
    match env.new_string(json) {
        Ok(text) => text,
        Err(error) => {
            let _ = env.throw_new("java/lang/IllegalStateException", error.to_string());
            JString::default()
        }
    }
}

#[no_mangle]
pub extern "C" fn Java_com_ml_shubham0204_sentence_1embeddings_HFTokenizer_deleteTokenizer(
    _: JNIEnv,
    _: JClass,
    tokenizer_ptr: jlong,
) {
    if tokenizer_ptr != 0 {
        unsafe { drop(Box::from_raw(tokenizer_ptr as *mut Tokenizer)) };
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    const TOKENIZER_JSON: &str = r#"{
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
    }"#;

    #[test]
    fn word_level_encoding_output_is_exact() {
        let tokenizer = Tokenizer::from_bytes(TOKENIZER_JSON.as_bytes()).unwrap();
        for (text, ids, attention_mask, token_type_ids) in [
            ("hello world", [2, 3, 1, 1], [1, 1, 0, 0], [0, 0, 1, 1]),
            ("你好 𠮷 unlisted", [4, 5, 0, 1], [1, 1, 1, 0], [0, 0, 0, 1]),
            ("", [1, 1, 1, 1], [0, 0, 0, 0], [1, 1, 1, 1]),
        ] {
            let encoding = tokenizer.encode(text, true).unwrap();
            let result = TokenizationResult {
                ids: encoding.get_ids().to_vec(),
                attention_mask: encoding.get_attention_mask().to_vec(),
                token_type_ids: encoding.get_type_ids().to_vec(),
            };
            let json = serde_json::to_string(&result).unwrap();
            assert_eq!(
                serde_json::from_str::<serde_json::Value>(&json).unwrap(),
                serde_json::json!({
                    "ids": ids,
                    "attention_mask": attention_mask,
                    "token_type_ids": token_type_ids,
                }),
                "{text:?}"
            );
        }
    }
}
