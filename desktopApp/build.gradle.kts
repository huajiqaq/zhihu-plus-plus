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

import org.gradle.jvm.tasks.Jar
import org.gradle.language.jvm.tasks.ProcessResources
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val appVersionName = property("app.versionName").toString()
val desktopPackageVersion = if (appVersionName.count { it == '.' } >= 2) appVersionName else "$appVersionName.0"

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.plugin.compose)
    id("com.mikepenz.aboutlibraries.plugin")
    alias(libs.plugins.ktlint)
}

val desktopLicenseManifest = layout.buildDirectory.file("generated/aboutLibraries/aboutlibraries.json")

aboutLibraries {
    collect {
        all.set(true)
        // 许可证清单只包含安装包的实际运行依赖，不把 compileOnly 的平台类型算入。
        filterVariants.set(setOf("runtimeClasspath"))
    }
    export {
        outputFile.set(desktopLicenseManifest)
    }
}

tasks.named<ProcessResources>("processResources") {
    dependsOn(tasks.named("exportLibraryDefinitions"))
    from(desktopLicenseManifest)
    if (rootProject.extensions.extraProperties.has("zhihuUpstreamInputs")) {
        from(File(rootProject.extensions.extraProperties["zhihuUpstreamInputs"] as String, "assets"))
    }
}

ktlint {
    outputToConsole.set(true)
    enableExperimentalRules.set(true)
    filter {
        exclude("**/generated/**")
        exclude("**/build/**")
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

configurations.named("runtimeClasspath") {
    // FontBox 的 JCL 调用走桌面已有的 SLF4J API，不打包 Servlet、LogKit 等可选适配器。
    resolutionStrategy.dependencySubstitution {
        substitute(module("commons-logging:commons-logging")).using(module("org.slf4j:jcl-over-slf4j:2.0.19"))
    }
}

dependencies {
    implementation(projects.shared)
    implementation(project(":shared-feature-settings"))
    runtimeOnly(libs.dbus.java.transport.native.unixsocket)
    implementation(project(":shared-ui-foundation"))
    implementation(project(":shared-ui-components"))
    implementation(libs.jb.compose.ui)
    // Gradle 不解析 JavaFX POM 的平台 classifier；显式指定才能取得类和 JNI。
    // 桌面内嵌 WebView 使用 JavaFX；共享模块编译类型，宿主通过 runtimeOnly 提供类与 JNI。
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
        listOf(libs.openjfx.base, libs.openjfx.graphics, libs.openjfx.controls, libs.openjfx.web, libs.openjfx.swing, libs.openjfx.media)
    javafxModules.forEach { module ->
        runtimeOnly(variantOf(module) { classifier(fxClassifier) })
    }
}

fun currentDesktopNativeExcludes(): List<String> {
    val osName = System.getProperty("os.name").lowercase()
    val osArch = System.getProperty("os.arch").lowercase()
    val isArm64 = osArch == "aarch64" || osArch == "arm64"
    val sqliteTarget =
        when {
            osName.contains("mac") && isArm64 -> "osx_arm64"
            osName.contains("mac") -> "osx_x64"
            osName.contains("win") -> "windows_x64"
            osName.contains("linux") && isArm64 -> "linux_arm64"
            else -> "linux_x64"
        }
    val skikoTarget =
        when {
            osName.contains("mac") && isArm64 -> "macos-arm64"
            osName.contains("mac") -> "macos-x64"
            osName.contains("win") -> "windows-x64"
            osName.contains("linux") && isArm64 -> "linux-arm64"
            else -> "linux-x64"
        }
    val sqliteNatives =
        listOf(
            "natives/linux_arm64/**",
            "natives/linux_x64/**",
            "natives/osx_arm64/**",
            "natives/osx_x64/**",
            "natives/windows_x64/**",
        )
    val skikoNatives =
        listOf(
            "libskiko-linux-arm64.so",
            "libskiko-linux-x64.so",
            "libskiko-macos-arm64.dylib",
            "libskiko-macos-x64.dylib",
            "skiko-windows-x64.dll",
        )

    return sqliteNatives.filterNot { it == "natives/$sqliteTarget/**" } +
        skikoNatives.filterNot { it.contains(skikoTarget) }
}

tasks.withType<Jar>().configureEach {
    if (name == "packageReleaseUberJarForCurrentOS") {
        exclude(currentDesktopNativeExcludes())
    }
}

compose.desktop {
    application {
        mainClass = "com.github.zly2006.zhihu.desktop.MainKt"

        buildTypes.release.proguard {
            isEnabled.set(true)
            optimize.set(false)
            configurationFiles.from(project.file("proguard-release.pro"))
        }

        nativeDistributions {
            // 保留 JavaFX WebView 的 JRE 字体后备，不依赖用户系统是否安装相同字体。
            stripJreFonts = false
            // AppImage 由 CI 封装 jpackage app-image；macOS 使用 macosApp 的 Native 构建。
            targetFormats(TargetFormat.AppImage, TargetFormat.Msi)
            packageName = "Zhihu++"
            packageVersion = desktopPackageVersion
            description = "Free and ad-free third-party Zhihu client"
            vendor = "zly2006"

            // jlink 需保留 WebView JS/JDBC/HTTP/中文字符集所用模块；checkRuntime 不检查缺失模块。
            modules(
                "java.management",
                "java.naming",
                "java.net.http",
                "java.prefs",
                "java.security.jgss",
                "java.sql",
                "java.xml",
                "jdk.charsets",
                "jdk.jsobject",
                "jdk.net",
                "jdk.security.auth",
                "jdk.unsupported",
                "jdk.zipfs",
            )

            linux {
                iconFile.set(project.file("src/main/resources/desktop-icon.png"))
            }
            windows {
                iconFile.set(project.file("desktop-icon.ico"))
                menu = true
                menuGroup = "Zhihu++"
                shortcut = true
                upgradeUuid = "84FED2C6-8F40-4FDA-B84A-616602D7A888"
            }
        }
    }
}
