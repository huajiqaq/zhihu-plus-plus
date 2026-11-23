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

import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    // 与消费模块共用 KGP，避免约定插件的 Kotlin 扩展类型不一致。
    alias(libs.plugins.kotlin.jvm)
    `java-gradle-plugin`
}

repositories {
    google()
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(libs.versions.jvm.get().toInt()))
    }
}

tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = JavaVersion.VERSION_21.toString()
    targetCompatibility = JavaVersion.VERSION_21.toString()
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget(libs.versions.jvm.get())
    }
}

dependencies {
    // 约定插件按类型配置 KotlinMultiplatformExtension；版本必须与
    // 消费模块的 KGP 一致，保证扩展类型同一。
    implementation(libs.kotlin.gradle.plugin)
    compileOnly("com.android.tools.build:gradle-api:${libs.versions.agp.get()}")
    testImplementation(gradleTestKit())
    testImplementation(kotlin("test-junit"))
}

gradlePlugin {
    plugins {
        create("zhihuKmpConventions") {
            id = "zhihu.kmp-conventions"
            implementationClass = "buildlogic.ZhihuKmpConventionPlugin"
        }
    }
}
