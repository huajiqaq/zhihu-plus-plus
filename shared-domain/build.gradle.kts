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
        val jvmAndAndroidMain by creating {
            dependsOn(commonMain.get())
        }
        androidMain.get().dependsOn(jvmAndAndroidMain)
        jvmMain.get().dependsOn(jvmAndAndroidMain)
        val androidFull =
            create("androidFull") {
                dependsOn(commonMain.get())
                dependencies {
                }
            }
        val androidLite =
            create("androidLite") {
                dependsOn(commonMain.get())
            }
        commonMain.dependencies {
            api(project(":shared-local-db"))
            api(project(":shared-core"))
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

        jvmMain.dependencies {
            implementation(libs.jb.ui.backhandler.desktop)
            implementation(libs.navigationevent)
            implementation(libs.coil.network.ktor3)
            implementation(libs.sqlite.bundled)
            implementation(libs.zxing.core)
            implementation(libs.ktor.client.cio)
            implementation(libs.kotlinx.coroutines.swing)
            // JavaFX 运行时由桌面宿主提供，共享模块只需编译类。
            javafxModules.forEach { compileOnly(project.dependencies.variantOf(it) { classifier(fxClassifier) }) }
        }
        findByName("appleMain")?.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        jvmTest.dependencies {
            javafxModules.forEach { implementation(project.dependencies.variantOf(it) { classifier(fxClassifier) }) }
            implementation(libs.jb.compose.ui.test)
            implementation(libs.jsoup)
        }
    }
}

tasks.named<ProcessResources>("jvmProcessResources") {
    from(rootProject.file("app/src/main/assets/article_export_template.html"))
}
