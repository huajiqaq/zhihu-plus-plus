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
import org.gradle.language.jvm.tasks.ProcessResources
import org.jlleitschuh.gradle.ktlint.tasks.GenerateReportsTask
import org.jlleitschuh.gradle.ktlint.tasks.KtLintFormatTask

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
        exclude("build/generated/**")
        exclude("**/build/generated/ksp/**")
        exclude("**/ksp/**")
        exclude { it.file.absolutePath.contains("/build/generated/") }
    }
}

tasks.withType<KtLintFormatTask>().configureEach {
    exclude("**/generated/**")
    exclude("build/generated/**")
    exclude("**/build/generated/ksp/**")
    exclude("**/ksp/**")
    exclude { it.file.absolutePath.contains("/build/generated/") }
}

tasks
    .withType<GenerateReportsTask>()
    .matching { it.name in setOf("ktlintAndroidMainSourceSetFormat", "ktlintJvmMainSourceSetFormat") }
    .configureEach {
        enabled = false
    }

mapOf(
    "runKtlintFormatOverAndroidMainSourceSet" to
        listOf("src/androidMain/kotlin", "src/tiqianMarkdownMain/kotlin"),
    "runKtlintFormatOverAndroidFullSourceSet" to
        listOf("src/androidFull/kotlin"),
    "runKtlintFormatOverJvmMainSourceSet" to listOf("src/jvmMain/kotlin"),
    "runKtlintFormatOverCommonMainSourceSet" to listOf("src/commonMain/kotlin"),
    "runKtlintFormatOverJvmTestSourceSet" to listOf("src/jvmTest/kotlin"),
).forEach { (taskName, sourcePaths) ->
    tasks.withType<KtLintFormatTask>().matching { it.name == taskName }.configureEach {
        setSource(
            sourcePaths.map { sourcePath ->
                fileTree(sourcePath) {
                    include("**/*.kt")
                }
            },
        )
    }
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
        val androidFull =
            create("androidFull") {
                dependsOn(commonMain.get())
            }
        val androidLite =
            create("androidLite") {
                dependsOn(commonMain.get())
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
        // Gradle 不解析 JavaFX POM 的平台 classifier；显式指定才能取得类和 JNI。
        val osName = System.getProperty("os.name").lowercase()
        val osArch = System.getProperty("os.arch").lowercase()
        val fxClassifier =
            when {
                osName.contains("mac") && (osArch == "aarch64" || osArch == "arm64") -> "mac-aarch64"
                osName.contains("mac") -> "mac"
                osName.contains("win") -> "win"
                osName.contains("linux") && (osArch == "aarch64" || osArch == "arm64") -> "linux-aarch64"
                else -> "linux"
            }
        val javafxModules =
            listOf(
                libs.openjfx.base,
                libs.openjfx.graphics,
                libs.openjfx.controls,
                libs.openjfx.web,
                libs.openjfx.swing,
                libs.openjfx.media,
            )

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
            // JavaFX 运行时由桌面宿主提供，共享模块只需编译类。
            javafxModules.forEach { compileOnly(project.dependencies.variantOf(it) { classifier(fxClassifier) }) }
        }
        findByName("macosMain")?.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        jvmTest.dependencies {
            javafxModules.forEach { implementation(project.dependencies.variantOf(it) { classifier(fxClassifier) }) }
            implementation(libs.jb.compose.ui.test)
            implementation(libs.androidx.navigationevent.compose)
            implementation(libs.jsoup)
        }
    }
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
