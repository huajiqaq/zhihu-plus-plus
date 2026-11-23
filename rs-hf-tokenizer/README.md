# HuggingFace Tokenizers 的 Rust JNI 接口

[HuggingFace Tokenizers](https://github.com/huggingface/tokenizers) 使用 Rust 编写，可以作为 Rust crate 引入。本模块源自 Shubham Panchal 的 [Sentence-Embeddings-Android](https://github.com/shubham0204/Sentence-Embeddings-Android)，本文已翻译并按 Zhihu++ 构建入口更新；署名和许可保留在 `LICENSE`。

JNI 接口从 `tokenizer.json` 的字节创建 tokenizer，对输入文本分词，并将 `ids`、`attention_mask` 和 `token_type_ids` 编码为 JSON，通过 `jstring`（Java `String`）返回 Android 端解码。原包名参与 JNI 导出符号编码，不能只修改 Kotlin 包名。用完后调用现有 `close()` 释放 tokenizer。

## Android 构建

应用使用 [句向量模块的 Gradle 构建](../sentence_embeddings/README.md) 生成原生库，NDK 和实际打包 ABI 以 `sentence_embeddings/build.gradle.kts` 为准，不依赖仓库外的链接器配置。常见 Android Rust 目标如下：

```text
armv7-linux-androideabi
aarch64-linux-android
i686-linux-android
x86_64-linux-android
```

以下为 Rust 的独立交叉编译命令；执行前需在开发环境配置对应 NDK 链接器。命令只生成 Rust 产物，不负责复制到 Android AAR：

```bash
$> cargo build --release --target armv7-linux-androideabi
$> cargo build --release --target aarch64-linux-android
$> cargo build --release --target i686-linux-android
$> cargo build --release --target x86_64-linux-android
```
