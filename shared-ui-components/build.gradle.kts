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

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    id("zhihu.kmp-conventions")
    id("zhihu.javafx")
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
        androidMain { configureAndroidVariant(miuixThemeMain) }
        wasmJsMain { dependsOn(miuixThemeMain) }
        jvmMain.get().dependsOn(miuixThemeMain)
        findByName("macosMain")?.dependsOn(miuixThemeMain)
        findByName("iosMain")?.configureIosVariant(miuixThemeMain)
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
        jvmTest.dependencies {
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

/** 编辑器各宿主使用同一份本地 Quill 资产，保留上游许可；不从 CDN 加载脚本。 */
val prepareRichEditorResources = tasks.register<Sync>("prepareRichEditorResources") {
    from(layout.projectDirectory.dir("src/commonMain/composeResources"))
    from(layout.projectDirectory.dir("src/androidMain/assets/rich-editor")) { into("files/rich-editor") }
    into(layout.buildDirectory.dir("generated/rich-editor-resources"))
}

compose.resources {
    customDirectory(
        sourceSetName = "commonMain",
        directoryProvider = prepareRichEditorResources.map { layout.buildDirectory.dir("generated/rich-editor-resources").get() },
    )
    publicResClass = true
    packageOfResClass = "zhihu.uicomponents.generated.resources"
}
