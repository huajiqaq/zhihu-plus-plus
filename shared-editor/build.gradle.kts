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
    android { namespace = "com.github.zly2006.zhihu.sharededitor" }
    jvm()
    configureAppleTargets()
    applyDefaultHierarchyTemplate()
    sourceSets {
        val directUploadMain = create("directUploadMain") { dependsOn(commonMain.get()) }
        androidMain.get().dependsOn(directUploadMain)
        jvmMain.get().dependsOn(directUploadMain)
        findByName("nativeMain")?.dependsOn(directUploadMain)
        commonMain.dependencies {
            api(project(":shared-domain"))
            implementation(libs.jb.compose.runtime)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.ktor.client.core)
            implementation(libs.ksoup)
            implementation(libs.markdown.parser)
        }
        findByName("iosMain")?.dependencies { implementation(libs.jb.compose.ui) }
        androidMain.dependencies {
            implementation(libs.jb.compose.ui)
            implementation(libs.androidx.activity.compose)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmTest.dependencies {
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.io.core)
        }
    }
}
