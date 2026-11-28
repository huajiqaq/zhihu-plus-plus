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

@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

import buildlogic.configureAppleTargets
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.gradle.api.tasks.Exec

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    id("zhihu.kmp-conventions")
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
    }
}

kotlin {
    android { namespace = "com.github.zly2006.zhihu.sharedreading" }
    jvm()
    configureAppleTargets()
    applyDefaultHierarchyTemplate()
    sourceSets {
        val hostedReadingMain = create("hostedReadingMain") { dependsOn(commonMain.get()) }
        jvmMain { dependsOn(hostedReadingMain) }
        wasmJsMain { dependsOn(hostedReadingMain) }
        if (targets.withType<KotlinNativeTarget>().isNotEmpty()) {
            nativeMain { dependsOn(hostedReadingMain) }
        }
        commonMain.dependencies {
            api(project(":shared-domain"))
            implementation(libs.jb.compose.runtime)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.ktor.client.core)
            implementation(libs.ksoup)
        }
        androidMain.dependencies {
            implementation(libs.jb.compose.ui)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.media)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

// 系统 helper 只由匹配宿主的官方工具链构建；其它 OS 不注册伪成功任务。
val readingHostOs = System.getProperty("os.name").lowercase()
if (readingHostOs.startsWith("windows")) {
    val nativeBuild = layout.buildDirectory.dir("reading-host/windows")
    val configureReadingHost = tasks.register<Exec>("configureReadingHost") {
        inputs.dir("native/windows")
        outputs.file(nativeBuild.map { it.file("CMakeCache.txt") })
        commandLine("cmake", "-S", layout.projectDirectory.dir("native/windows").asFile.absolutePath,
            "-B", nativeBuild.get().asFile.absolutePath, "-A", "x64")
    }
    tasks.register<Exec>("buildReadingHost") {
        dependsOn(configureReadingHost)
        inputs.dir("native/windows")
        outputs.file(nativeBuild.map { it.file("out/zhihu-reading-host.exe") })
        commandLine("cmake", "--build", nativeBuild.get().asFile.absolutePath, "--config", "Release")
    }
} else if (readingHostOs.contains("mac")) {
    val output = layout.buildDirectory.file("reading-host/macos/zhihu-reading-host")
    tasks.register<Exec>("buildReadingHost") {
        inputs.file("native/macos/ReadingHost.swift")
        outputs.file(output)
        doFirst { output.get().asFile.parentFile.mkdirs() }
        commandLine("xcrun", "swiftc", "-O", "-target",
            (if (System.getProperty("os.arch") in setOf("aarch64", "arm64")) "arm64" else "x86_64") + "-apple-macosx12.0",
            "-framework", "AppKit", "-framework", "AVFoundation", "-framework", "MediaPlayer",
            layout.projectDirectory.file("native/macos/ReadingHost.swift").asFile.absolutePath,
            "-o", output.get().asFile.absolutePath)
    }
} else if (readingHostOs.startsWith("linux")) {
    val nativeBuild = layout.buildDirectory.dir("reading-host/linux")
    val configureReadingHost = tasks.register<Exec>("configureReadingHost") {
        inputs.dir("native/linux")
        outputs.file(nativeBuild.map { it.file("CMakeCache.txt") })
        commandLine("cmake", "-S", layout.projectDirectory.dir("native/linux").asFile.absolutePath,
            "-B", nativeBuild.get().asFile.absolutePath, "-DCMAKE_BUILD_TYPE=Release")
    }
    tasks.register<Exec>("buildReadingHost") {
        dependsOn(configureReadingHost)
        inputs.dir("native/linux")
        outputs.file(nativeBuild.map { it.file("out/zhihu-reading-host") })
        commandLine("cmake", "--build", nativeBuild.get().asFile.absolutePath, "--config", "Release")
    }
}
