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

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    id("zhihu.kmp-conventions")
    alias(libs.plugins.kotlin.plugin.compose)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose)
}

kotlin {
    android { namespace = "com.github.zly2006.zhihu.uifoundation" }
    jvm()
    iosArm64()
    iosSimulatorArm64()
    macosArm64()
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
                }
            }
        androidFull.dependsOn(miuixThemeMain)
        jvmMain.get().dependsOn(miuixThemeMain)
        appleMain.get().dependsOn(miuixThemeMain)
        commonMain.dependencies {
            api(project(":shared-domain"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(libs.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(libs.materialkolor)
            implementation(libs.ksoup)
            implementation(libs.ktor.client.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
        }
        androidMain.dependencies { implementation(libs.androidx.activity.compose) }
        jvmMain.dependencies { implementation(compose.desktop.currentOs) }
        commonTest.dependencies { implementation(kotlin("test")) }
    }
}
