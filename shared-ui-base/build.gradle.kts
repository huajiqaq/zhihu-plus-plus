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

@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class, org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

import buildlogic.configureAppleTargets
import groovy.json.JsonOutput
import org.gradle.language.jvm.tasks.ProcessResources
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

val miscResources = rootProject.layout.projectDirectory.dir("misc")
val prepareWasmEmojiDrawables =
    tasks.register<Sync>("prepareWasmEmojiDrawables") {
        from(miscResources.dir("emojis")) {
            include("*.png")
            into("drawable")
        }
        into(layout.buildDirectory.dir("generated/wasmEmojiResources"))
    }
val generateWasmUiMetadata =
    tasks.register<Sync>("generateWasmUiMetadata") {
        val mapping = providers.fileContents(miscResources.file("emoji_mapping.json")).asText
        val version = providers.gradleProperty("app.versionName")
        inputs.property("emojiMapping", mapping)
        inputs.property("appVersion", version)
        filteringCharset = "UTF-8"
        from(layout.projectDirectory.file("src/wasmJsMain/templates/WasmUiMetadata.kt.template")) {
            eachFile {
                name = name.removeSuffix(".template")
                expand(
                    "emojiMapping" to JsonOutput.toJson(mapping.get()).replace("$", "\\$"),
                    "appVersion" to JsonOutput.toJson(version.get()).replace("$", "\\$"),
                )
            }
        }
        into(layout.buildDirectory.dir("generated/wasmUiMetadata"))
    }

kotlin {
    android {
        namespace = "com.github.zly2006.zhihu.uibase"
        minSdk = 27

        androidResources {
            enable = false
        }
    }
    jvm {
    }

    configureAppleTargets("SharedUiBase")

    applyDefaultHierarchyTemplate()

    sourceSets {
        val nativeAndWebMain = create("nativeAndWebMain") { dependsOn(commonMain.get()) }
        wasmJsMain {
            dependsOn(nativeAndWebMain)
            generatedKotlin.srcDir(generateWasmUiMetadata)
        }
        if (targets.withType<KotlinNativeTarget>().isNotEmpty()) {
            nativeMain { dependsOn(nativeAndWebMain) }
        }
        commonMain.dependencies {
            api(project(":shared-ui-components"))
            api(project(":shared-editor"))
            implementation(libs.jb.compose.runtime)
            implementation(libs.jb.compose.foundation)
            implementation(libs.material3)
            implementation(libs.jb.material.icons.extended)
            implementation(libs.jb.compose.ui)
            implementation(libs.jb.compose.components.resources)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.core)
            implementation(libs.jetbrains.lifecycle.runtime.compose)
            implementation(libs.jetbrains.lifecycle.viewmodel.compose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ksoup)
            implementation(libs.markdown.renderer)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.io.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.aboutlibraries.compose.m3)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
        androidMain {
            kotlin.srcDir("src/tiqianMarkdownMain/kotlin")
            dependencies {
                implementation(libs.tiqian.markdown.compose)
                implementation(libs.tiqian.math.font.stix)
                implementation(libs.androidx.activity.compose)
                implementation(libs.androidx.core.ktx)
                implementation(libs.androidx.lifecycle.livedata.ktx)
                implementation(libs.androidx.media)
                implementation(libs.androidx.webkit)
                implementation(libs.zxing.core)
                implementation(libs.coil.gif)
                implementation(libs.coil.network.ktor3.android)
                implementation(libs.ktor.client.android)
                implementation(libs.jsoup)
            }
        }
        jvmMain {
            kotlin.srcDir("src/tiqianMarkdownMain/kotlin")
        }
        jvmMain.dependencies {
            implementation(libs.jb.ui.backhandler.desktop)
            implementation(libs.navigationevent)
            implementation(libs.tiqian.markdown.compose)
            implementation(libs.tiqian.math.font.stix)
            implementation(libs.coil.network.ktor3)
            implementation(libs.sqlite.bundled)
            implementation(libs.zxing.core)
            implementation(libs.ktor.client.cio)
            implementation(libs.kotlinx.coroutines.swing)
            // 录音只使用 FFmpeg；JavaCV 的默认传递依赖还包含 OpenCV 和各相机 SDK。
            implementation("org.bytedeco:javacv:${libs.versions.javacv.get()}") { isTransitive = false }
            implementation("org.bytedeco:ffmpeg:${libs.versions.ffmpeg.get()}") { isTransitive = false }
            implementation("org.bytedeco:javacpp:${libs.versions.javacpp.get()}") { isTransitive = false }
        }
        findByName("macosMain")?.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        jvmTest.dependencies {
            implementation(libs.jb.compose.ui.test)
            implementation(libs.androidx.navigationevent.compose)
            implementation(libs.jsoup)
        }
    }
}

compose.resources {
    customDirectory(kotlin.run { sourceSets.wasmJsMain.name }, prepareWasmEmojiDrawables.flatMap { it.destinationDirectory })
}

// 打包应用不依赖源码目录，所需资源必须进入类路径。
tasks.named<ProcessResources>("jvmProcessResources") {
    val appVersionName = providers.gradleProperty("app.versionName")
    inputs.property("appVersionName", appVersionName)
    filesMatching("zhihu-version.txt") {
        expand("appVersionName" to appVersionName.get())
    }
    from(rootProject.file("misc")) {
        include("emoji_mapping.json", "emojis/*.png")
        into("misc")
    }
}
