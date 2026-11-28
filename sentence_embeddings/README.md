# Android 句向量库

本模块构建 Android AAR，包含基于 Rust 的 HuggingFace tokenizer 和 ONNX 句向量推理能力，供 Zhihu++ Full 使用。代码来源为 Shubham Panchal 的 [Sentence-Embeddings-Android](https://github.com/shubham0204/Sentence-Embeddings-Android)，保留原包名与 JNI API。

## 开发依赖

- 可用的 Android SDK、JDK 和 Gradle 环境，以 [模块构建脚本](build.gradle.kts) 为准；
- Android NDK，版本以构建脚本的 `ndkVersion` 为准，通过 SDK Manager 安装；
- [Rust 工具链](https://rustup.rs/)；
- cargo-ndk：`cargo install cargo-ndk`。

### Android Rust 目标

Gradle 的 `androidTargets` 包含 `aarch64-linux-android` 和 `x86_64-linux-android`，分别生成 `arm64-v8a` 与 `x86_64` 原生库。安装对应目标：

```bash
rustup target add aarch64-linux-android x86_64-linux-android
```

## 构建

在项目根目录执行：

```bash
./gradlew :sentence_embeddings:assembleRelease
```

`preBuild` 依赖 `buildRustLib`，使用 cargo-ndk 编译 `rs-hf-tokenizer`，并将原生库放入本模块的 `src/main/jniLibs/`。AAR 输出位置：

```text
sentence_embeddings/build/outputs/aar/sentence_embeddings-release.aar
```

## 故障排查

找不到 NDK 时，确认已安装构建脚本指定的版本；需要显式指定时，将 `ANDROID_NDK_HOME` 设为该 NDK 的安装目录。缺少 Rust 目标或 cargo-ndk 时，按上面的开发依赖补齐。

## 许可

本模块的来源署名与 Apache-2.0 许可见 [LICENSE](LICENSE)。
