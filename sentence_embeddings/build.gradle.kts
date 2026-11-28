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

plugins {
    alias(libs.plugins.android.library)
}

val envAndroidNDKPath = System.getenv("ANDROID_NDK_HOME")

android {
    namespace = "com.ml.shubham0204.sentence_embeddings"
    compileSdk {
        version = release(37) { minorApiLevel = 1 }
    }

    // JNI 构建显式固定 NDK 版本，避免本机默认工具链改变原生构件。
    ndkVersion = "29.0.14206865"
    envAndroidNDKPath?.let {
        ndkPath = it
    }

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.jvm.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.jvm.get())
    }
    kotlin {
        jvmToolchain(libs.versions.jvm.get().toInt())
    }

    androidResources {
        noCompress += "onnx"
    }

    sourceSets {
        getByName("main") {
            jniLibs.directories.add("src/main/jniLibs")
        }
    }
}

val rustProjectDir = file("../rs-hf-tokenizer")
val jniLibsDir = file("src/main/jniLibs")
val rustSrcDir = rustProjectDir.resolve("src")
val cargoToml = rustProjectDir.resolve("Cargo.toml")
val cargoLock = rustProjectDir.resolve("Cargo.lock")

val androidTargets = mapOf(
    "aarch64-linux-android" to "arm64-v8a",
    "x86_64-linux-android" to "x86_64",
)

tasks.register<Exec>("buildRustLib") {
    description = "Build Rust library for Android using cargo-ndk"
    group = "rust"

    workingDir = rustProjectDir

    inputs.dir(rustSrcDir)
    inputs.file(cargoToml)
    if (cargoLock.exists()) {
        inputs.file(cargoLock)
    }
    inputs.property("androidTargets", androidTargets.toString())
    inputs.property("ndkVersion", android.ndkVersion)

    outputs.files(androidTargets.values.map { jniLibsDir.resolve("$it/libhftokenizer.so") })

    val isWindows = System.getProperty("os.name").lowercase().contains("windows")

    if (isWindows) {
        val jniLibsPath = jniLibsDir.absolutePath.replace("\\", "/")
        val buildCommands = androidTargets.entries.joinToString(" && ") { (rustTarget, androidAbi) ->
            listOf(
                "echo Building for $androidAbi...",
                "cargo ndk --target $androidAbi --platform 24 build --release --locked",
                "(if not exist \"$jniLibsPath\\$androidAbi\" mkdir \"$jniLibsPath\\$androidAbi\")",
                "copy /Y target\\$rustTarget\\release\\libhftokenizer.so \"$jniLibsPath\\$androidAbi\\\""
            ).joinToString(" && ")
        }
        commandLine(
            "cmd", "/c",
            listOf(
                "echo Rust build starting...",
                buildCommands,
                "echo Rust build completed successfully!"
            ).joinToString(" && ")
        )
    } else {
        val jniLibsPath = jniLibsDir.absolutePath
        val buildCommands = androidTargets.entries.joinToString("\n") { (rustTarget, androidAbi) ->
            listOf(
                "echo \"Building for $androidAbi...\"",
                "cargo ndk --target $androidAbi --platform 24 build --release --locked",
                "mkdir -p \"$jniLibsPath/$androidAbi\"",
                "cp target/$rustTarget/release/libhftokenizer.so \"$jniLibsPath/$androidAbi/\""
            ).joinToString("\n")
        }
        val addTargetCommands = androidTargets.keys.joinToString("\n") {
            "rustup target add $it"
        }
        commandLine("sh", "-c", listOf(
            "set -e",
            "export PATH=\"\$HOME/.cargo/bin:\$PATH\"",
            "# Add Android targets if not already added",
            addTargetCommands,
            "",
            "# Build for each target",
            buildCommands,
            "",
            "echo \"Rust build completed successfully!\""
        ).joinToString("\n"))
    }
}

tasks.register<Delete>("cleanRust") {
    description = "Clean Rust build artifacts"
    group = "rust"
    delete(file("$rustProjectDir/target"))
    delete(jniLibsDir)
}

tasks.named("clean") {
    dependsOn("cleanRust")
}

tasks.matching { it.name.startsWith("preBuild") }.configureEach {
    dependsOn("buildRustLib")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.onnxruntime.android)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
