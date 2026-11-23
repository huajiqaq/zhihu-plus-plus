# Android 句向量库

本模块构建 Android AAR，包含基于 Rust 的 HuggingFace tokenizer 和 ONNX 句向量推理能力，供 Zhihu++ Full 使用。代码来源为 Shubham Panchal 的 [Sentence-Embeddings-Android](https://github.com/shubham0204/Sentence-Embeddings-Android)；本文已按本项目构建配置翻译、更新，原包名与 JNI API 保持不变。

## 开发依赖

- 可用的 Android SDK、JDK 和 Gradle 环境，以模块构建脚本为准；
- Android NDK `29.0.14206865`，通过 SDK Manager 安装；
- [Rust 工具链](https://rustup.rs/)；
- cargo-ndk：`cargo install cargo-ndk`。

### Android Rust 目标

Gradle 的 `androidTargets` 当前包含 `aarch64-linux-android` 和 `x86_64-linux-android`，分别生成 `arm64-v8a` 与 `x86_64` 原生库。可用目标的安装命令如下；手动安装额外目标不会改变 Gradle 的 ABI 配置。

```bash
rustup target add aarch64-linux-android
rustup target add armv7-linux-androideabi
rustup target add i686-linux-android
rustup target add x86_64-linux-android
```

## 构建

在项目根目录执行：

```bash
# 在项目根目录执行
./gradlew :sentence_embeddings:assembleRelease
```

`preBuild` 依赖 `buildRustLib`，使用 cargo-ndk 编译 `rs-hf-tokenizer`，并将原生库放入本模块的 `src/main/jniLibs/`。AAR 输出位置：

```text
sentence_embeddings/build/outputs/aar/sentence_embeddings-release.aar
```

## 集成

在应用的 `build.gradle.kts` 中添加：

```kotlin
dependencies {
    implementation(project(":sentence_embeddings"))
}
```

## 故障排查

### 找不到 NDK

确认已安装构建脚本指定的版本；需要显式指定时设置 `ANDROID_NDK_HOME`：

```bash
export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/29.0.14206865
```

### 未安装 Rust 目标

按上面的目标安装命令补齐实际构建需要的目标。

### 找不到 cargo-ndk

```bash
cargo install cargo-ndk
```

## License

See LICENSE file in the project root.
