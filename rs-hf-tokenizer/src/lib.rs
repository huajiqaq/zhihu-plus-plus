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
