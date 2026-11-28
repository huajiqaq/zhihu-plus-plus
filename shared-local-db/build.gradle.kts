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
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinWasmJsTargetDsl
import org.jlleitschuh.gradle.ktlint.tasks.BaseKtLintCheckTask

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    id("zhihu.kmp-conventions")
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktlint)
}

ktlint {
    android.set(true)
    outputToConsole.set(true)
    enableExperimentalRules.set(true)
}

/** KSP 源目录带生成任务依赖；格式检查只读取手写源码，避免触发 Native 编译。 */
afterEvaluate {
    tasks.withType<BaseKtLintCheckTask>().configureEach {
        if (name.contains("SourceSet")) {
            val sourceSet = name.substringAfter("Over").substringBefore("SourceSet").replaceFirstChar(Char::lowercase)
            setSource(fileTree("src/$sourceSet/kotlin"))
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

kotlin {
    android {
        namespace = "com.github.zly2006.zhihu.shared.localdb"
        minSdk = 27
    }
    jvm {
    }

    targets.named<KotlinWasmJsTargetDsl>("wasmJs") {
        browser {
            testTask { useKarma { useChromeHeadless() } }
        }
    }
    configureAppleTargets("SharedLocalDb")
    applyDefaultHierarchyTemplate()

    sourceSets {
        val nonWebMain =
            create("nonWebMain") {
                dependsOn(commonMain.get())
                dependencies { api(libs.room.runtime) }
            }
        androidMain.get().dependsOn(nonWebMain)
        jvmMain.get().dependsOn(nonWebMain)
        findByName("nativeMain")?.dependsOn(nonWebMain)
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        wasmJsTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmMain.dependencies {
            implementation(libs.sqlite.bundled)
        }
        findByName("nativeMain")?.dependencies {
            implementation(libs.sqlite.bundled)
        }
    }
}

dependencies {
    add("kspAndroid", libs.room.compiler)
    add("kspJvm", libs.room.compiler)
    kotlin.targets.withType<KotlinNativeTarget>().forEach { target ->
        add("ksp${target.name.replaceFirstChar(Char::uppercaseChar)}", libs.room.compiler)
    }
}
