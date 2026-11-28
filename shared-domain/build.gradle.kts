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

import buildlogic.configureAndroidVariant
import buildlogic.configureIosVariant
import buildlogic.configureAppleTargets
import buildlogic.zhihuIosVariant
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    id("zhihu.kmp-conventions")
    id("zhihu.javafx")
    alias(libs.plugins.kotlin.plugin.compose)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose)
    alias(libs.plugins.ktlint)
}

ktlint {
    android.set(true)
    outputToConsole.set(true)
    enableExperimentalRules.set(true)
    filter {
        exclude("**/generated/**")
        exclude("**/build/**")
        exclude("build/generated/**")
        exclude("**/build/generated/ksp/**")
        exclude("**/ksp/**")
        exclude { it.file.absolutePath.contains("/build/generated/") }
    }
}

kotlin {
    android {
        namespace = "com.github.zly2006.zhihu.domain"
        minSdk = 27

        androidResources {
            enable = false
        }
    }
    jvm {
    }

    configureAppleTargets("SharedDomain")

    applyDefaultHierarchyTemplate()

    sourceSets {
        findByName("appleMain")?.let { appleMain ->
            val appleFullMain = create("appleFullMain") { dependsOn(appleMain) }
            getByName("macosMain").dependsOn(appleFullMain)
            getByName("iosMain").configureIosVariant()
            if (project.zhihuIosVariant == "full") getByName("iosMain").dependsOn(appleFullMain)
        }
        val jvmAndAndroidMain = create("jvmAndAndroidMain") { dependsOn(commonMain.get()) }
        androidMain.get().dependsOn(jvmAndAndroidMain)
        jvmMain.get().dependsOn(jvmAndAndroidMain)
        commonMain.dependencies {
            api(project(":shared-local-db"))
            api(project(":shared-core"))
            implementation(libs.jb.compose.runtime)
            implementation(libs.jb.compose.foundation)
            implementation(libs.material3)
            implementation(libs.jb.compose.ui)
            implementation(libs.jb.compose.components.resources)
            implementation(libs.jetbrains.lifecycle.runtime.compose)
            implementation(libs.jetbrains.lifecycle.viewmodel.compose)
            implementation(libs.androidx.navigationevent.compose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ksoup)
            api(libs.markdown.renderer)
            implementation(libs.markdown.parser)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.io.core)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
        androidMain {
            configureAndroidVariant()
            dependencies {
                implementation(libs.androidx.activity.compose)
                implementation(libs.androidx.core.ktx)
                implementation(libs.androidx.lifecycle.livedata.ktx)
                implementation(libs.androidx.media)
                implementation(libs.androidx.webkit)
                implementation(libs.zxing.core)
                implementation(libs.ktor.client.android)
                implementation(libs.jsoup)
            }
        }
        jvmMain.dependencies {
            implementation(libs.openhtmltopdf.pdfbox)
            implementation("io.github.openhtmltopdf:openhtmltopdf-svg-support:${libs.versions.openhtmltopdf.get()}") {
                // JDK 提供 JAXP；旧标准包会遮蔽 ACCESS_EXTERNAL_*，SVG DOM 的 xml-apis-ext 仍保留。
                exclude(group = "xml-apis", module = "xml-apis")
            }
            implementation(libs.onnxruntime.jvm)
            implementation(libs.hanlp)
            implementation(libs.jsoup)
            implementation(libs.sqlite.bundled)
            implementation(libs.zxing.core)
            implementation(libs.ktor.client.cio)
            implementation(libs.kotlinx.coroutines.swing)
        }
        findByName("appleMain")?.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        wasmJsMain.dependencies {
            implementation(npm("html2canvas-pro", "2.6.5"))
            implementation(npm("katex", "0.16.22"))
            implementation(npm("fflate", "0.8.3"))
            implementation(npm("onnxruntime-web", libs.versions.onnxRuntimeWeb.get()))
            implementation(npm("@noble/hashes", libs.versions.nobleHashes.get()))
        }
        jvmTest.dependencies {
            implementation(libs.jb.compose.ui.test)
            implementation(libs.jsoup)
        }
    }
}

if (providers.systemProperty("os.name").get().startsWith("Mac", ignoreCase = true)) {
    val appleRuntime = layout.buildDirectory.dir("generated/onnxruntime-apple")
    val prepareAppleOnnxRuntime =
        tasks.register<Exec>("prepareAppleOnnxRuntime") {
            val script = layout.projectDirectory.file("tools/prepare_apple_onnxruntime.py")
            val lock = layout.projectDirectory.file("onnxruntime-apple.lock.json")
            val cache = layout.buildDirectory.dir("onnxruntime-apple-downloads")
            val python = providers.environmentVariable("ZHIHU_PYTHON").orElse("python3")
            inputs.files(script, lock)
            inputs.property("pythonCommand", python)
            outputs.dir(appleRuntime)
            localState.register(cache)
            commandLine(python.get(), "-I", script.asFile.absolutePath, "--lock", lock.asFile.absolutePath, "--cache", cache.get().asFile.absolutePath, "--output", appleRuntime.get().asFile.absolutePath)
        }
    val iosFull = zhihuIosVariant == "full"
    kotlin.targets.withType<KotlinNativeTarget>().configureEach {
        val targetName = name
        if (targetName.startsWith("ios") && !iosFull) return@configureEach
        val interop = compilations.getByName("main").cinterops.create("onnxruntime") {
            definitionFile.set(layout.projectDirectory.file("src/nativeInterop/cinterop/onnxruntime.def"))
            includeDirs(layout.projectDirectory.dir("src/nativeInterop/cinterop"), appleRuntime.map { it.dir("include") })
            when (targetName) {
                "macosArm64" -> linkerOpts(appleRuntime.get().file("macosArm64/libonnxruntime.1.dylib").asFile.absolutePath)
                // Shared.framework 为静态库，ORT 及系统依赖只由最终 Xcode Full 宿主链接一次。
                "iosArm64", "iosSimulatorArm64" -> Unit
                else -> error("Unsupported Apple ONNX Runtime target: $targetName")
            }
        }
        tasks.named(interop.interopProcessingTaskName) {
            dependsOn(prepareAppleOnnxRuntime)
            inputs.dir(appleRuntime)
        }
        if (targetName == "macosArm64") {
            binaries.all {
                linkerOpts("-rpath", "@executable_path/../Frameworks", "-rpath", appleRuntime.get().dir("macosArm64").asFile.absolutePath)
            }
        }
    }
}
