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

import buildlogic.configureAndroidVariant
import buildlogic.configureAppleTargets
import buildlogic.configureIosVariant
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

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
    android { namespace = "com.github.zly2006.zhihu.feature.content" }
    jvm()
    configureAppleTargets()
    applyDefaultHierarchyTemplate()
    sourceSets {
        val markdownEditorMain = create("markdownEditorMain") { dependsOn(commonMain.get()) }
        if (targets.withType<KotlinNativeTarget>().isNotEmpty()) {
            nativeMain { dependsOn(markdownEditorMain) }
        }
        val miuixThemeMain =
            create("miuixThemeMain") {
                dependsOn(commonMain.get())
                kotlin.srcDir("src/miuixThemeMain/kotlin")
                dependencies {
                    implementation(libs.miuix.core)
                    implementation(libs.miuix.ui)
                }
            }
        androidMain { configureAndroidVariant(miuixThemeMain) }
        wasmJsMain {
            dependsOn(miuixThemeMain)
            dependsOn(markdownEditorMain)
        }
        jvmMain.get().dependsOn(miuixThemeMain)
        findByName("macosMain")?.dependsOn(miuixThemeMain)
        findByName("iosMain")?.configureIosVariant(miuixThemeMain)
        commonMain.dependencies {
            implementation(project(":shared-ui-base"))
            implementation(libs.jb.compose.runtime)
            implementation(libs.jb.compose.foundation)
            implementation(libs.material3)
            implementation(libs.jb.material.icons.extended)
            implementation(libs.jb.compose.ui)
            implementation(libs.jb.compose.components.resources)
            implementation(libs.coil.compose)
            implementation(libs.jetbrains.lifecycle.runtime.compose)
            implementation(libs.jetbrains.lifecycle.viewmodel.compose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.io.core)
            implementation(libs.ktor.client.core)
            implementation(libs.ksoup)
        }
        jvmTest.dependencies { implementation(libs.jb.compose.ui.test) }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
    }
}
