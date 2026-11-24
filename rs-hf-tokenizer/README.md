# HuggingFace Tokenizers 的 Rust JNI 接口

[HuggingFace Tokenizers](https://github.com/huggingface/tokenizers) 使用 Rust 编写，可以作为 Rust crate 引入。本模块源自 Shubham Panchal 的 [Sentence-Embeddings-Android](https://github.com/shubham0204/Sentence-Embeddings-Android)，构建说明按 Zhihu++ 配置整理；署名和许可见 [LICENSE](LICENSE)。

JNI 接口从 `tokenizer.json` 的字节创建 tokenizer，对输入文本分词，并将 `ids`、`attention_mask` 和 `token_type_ids` 编码为 JSON，通过 `jstring`（Java `String`）返回 Android 端解码。原包名参与 JNI 导出符号编码，不能只修改 Kotlin 包名。用完后调用现有 `close()` 释放 tokenizer。

## Android 构建

应用使用 [句向量模块的 Gradle 构建](../sentence_embeddings/README.md) 生成原生库，NDK 和实际打包 ABI 以 [模块构建脚本](../sentence_embeddings/build.gradle.kts) 为准，不依赖仓库外的链接器配置。

单独交叉编译时，先安装对应 Rust 目标并在开发环境配置 NDK 链接器，然后在仓库根目录执行。以下命令只生成当前 ABI 的 Rust 产物，不负责复制到 Android AAR：

```bash
cargo build --manifest-path rs-hf-tokenizer/Cargo.toml --release --target aarch64-linux-android
cargo build --manifest-path rs-hf-tokenizer/Cargo.toml --release --target x86_64-linux-android
```
