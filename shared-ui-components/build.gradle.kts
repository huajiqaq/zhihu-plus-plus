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

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    id("zhihu.kmp-conventions")
    alias(libs.plugins.kotlin.plugin.compose)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose)
}

kotlin {
    android {
        namespace = "com.github.zly2006.zhihu.uicomponents"
        androidResources { enable = true }
    }
    jvm()
    configureAppleTargets()
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
        val miuixThemeMain =
            create("miuixThemeMain") {
                dependsOn(commonMain.get())
                kotlin.srcDir("src/miuixThemeMain/kotlin")
                dependencies {
                    implementation(libs.miuix.core)
                    implementation(libs.miuix.ui)
                    implementation(libs.miuix.preference)
                    implementation(libs.miuix.icons)
                    implementation(libs.miuix.glass)
                }
            }
        androidFull.dependsOn(miuixThemeMain)
        jvmMain.get().dependsOn(miuixThemeMain)
        findByName("appleMain")?.dependsOn(miuixThemeMain)
        commonMain.dependencies {
            api(project(":shared-ui-foundation"))
            api(project(":shared-reading"))
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
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.miuix.blur)
            implementation(libs.miuix.material)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.webkit)
            implementation(libs.telephoto.zoomable.image.coil3)
            implementation(libs.jsoup)
        }
        val osName = System.getProperty("os.name").lowercase()
        val osArch = System.getProperty("os.arch").lowercase()
        val fxClassifier =
            when {
                osName.contains("mac") && osArch in listOf("aarch64", "arm64") -> "mac-aarch64"
                osName.contains("mac") -> "mac"
                osName.contains("win") -> "win"
                osName.contains("linux") && osArch in listOf("aarch64", "arm64") -> "linux-aarch64"
                else -> "linux"
            }
        jvmMain.dependencies {
            listOf(
                libs.openjfx.base,
                libs.openjfx.graphics,
                libs.openjfx.controls,
                libs.openjfx.web,
                libs.openjfx.swing,
                libs.openjfx.media,
            ).forEach {
                compileOnly(project.dependencies.variantOf(it) { classifier(fxClassifier) })
            }
        }
        jvmTest.dependencies {
            listOf(
                libs.openjfx.base,
                libs.openjfx.graphics,
                libs.openjfx.controls,
                libs.openjfx.web,
                libs.openjfx.swing,
                libs.openjfx.media,
            ).forEach {
                implementation(project.dependencies.variantOf(it) { classifier(fxClassifier) })
            }
            implementation(libs.jb.compose.ui.test)
            implementation(libs.androidx.navigationevent.compose)
            implementation(libs.ktor.client.mock)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "zhihu.uicomponents.generated.resources"
}
