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

import buildlogic.configureAppleTargets
import buildlogic.zhihuIosVariant
import com.mikepenz.aboutlibraries.plugin.AboutLibrariesTask
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    id("zhihu.kmp-conventions")
    id("zhihu.javafx")
    alias(libs.plugins.kotlin.plugin.compose)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose)
    id("com.mikepenz.aboutlibraries.plugin")
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

/** 内容过滤数据库在测试类结束时关闭，测试类不复用同一 JVM。 */
tasks.withType<Test>().matching { it.name == "jvmTest" }.configureEach {
    forkEvery = 1
}

kotlin {
    android {
        namespace = "com.github.zly2006.zhihu.shared"
        minSdk = 27

        androidResources {
            enable = false
        }
    }
    jvm {
    }

    configureAppleTargets("Shared")

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared-local-db"))
            api(project(":shared-core"))
            api(project(":shared-domain"))
            implementation(project(":shared-ui-foundation"))
            implementation(project(":shared-ui-components"))
            api(project(":shared-ui-base"))
            implementation(project(":shared-reading"))
            implementation(project(":shared-feature-settings"))
            implementation(project(":shared-feature-content"))
            implementation(project(":shared-feature-feed"))
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
            implementation(libs.androidx.navigationevent.compose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.markdown.renderer)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.io.core)
            implementation(libs.kotlinx.serialization.json)
        }
        wasmJsMain.dependencies {
            implementation(libs.coil.network.ktor3)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
        androidMain {
            dependencies {
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
        }
        jvmMain.dependencies {
            implementation(libs.jb.ui.backhandler.desktop)
            implementation(libs.navigationevent)
            implementation(libs.coil.network.ktor3)
            implementation(libs.sqlite.bundled)
            implementation(libs.zxing.core)
            implementation(libs.ktor.client.cio)
            implementation(libs.kotlinx.coroutines.swing)
        }
        findByName("macosMain")?.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        jvmTest.dependencies {
            implementation(libs.jb.compose.ui.test)
            implementation(libs.jsoup)
        }
    }
}

aboutLibraries { collect { all.set(true) } }
val iosLicenseVariant = zhihuIosVariant
kotlin.targets.withType<KotlinNativeTarget>().configureEach {
    val targetName = name
    val suffix = targetName.replaceFirstChar { it.uppercase() }
    val variant = if (targetName.startsWith("ios")) iosLicenseVariant else "full"
    val rawManifest = layout.buildDirectory.file("generated/aboutLibraries/$variant/$targetName/dependencies.json")
    val exportDependencies = tasks.register<AboutLibrariesTask>("exportAppleLicenses$suffix") {
        this.variant.set(compilations.getByName("main").compileDependencyConfigurationName)
        configureOutputFile(rawManifest)
        configure()
    }
    tasks.register<Exec>("prepare${suffix}LicenseManifest") {
        dependsOn(exportDependencies)
        val script = layout.projectDirectory.file("tools/merge_apple_licenses.py")
        val output = layout.buildDirectory.file("generated/apple-licenses/$variant/$targetName/aboutlibraries.json")
        val python = providers.environmentVariable("ZHIHU_PYTHON").orElse("python3")
        inputs.files(script, rawManifest)
        inputs.property("licenseVariant", variant)
        outputs.file(output)
        outputs.dir(output.map { it.asFile.parentFile.resolve("licenses") })
        commandLine(python.get(), "-I", script.asFile.absolutePath, "--input", rawManifest.get().asFile.absolutePath, "--output", output.get().asFile.absolutePath)
        if (variant == "full") {
            val notices = project(":shared-domain").layout.buildDirectory.dir("generated/onnxruntime-apple/licenses")
            dependsOn(":shared-domain:prepareAppleOnnxRuntime")
            inputs.dir(notices)
            args("--ort-licenses", notices.get().asFile.absolutePath)
        }
    }
}
