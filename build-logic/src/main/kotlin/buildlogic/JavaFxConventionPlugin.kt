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

package buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import java.io.File

/** Compose 默认使用 daemon JDK 打包，JNI 与 JavaFX classifier 必须读取同一 JDK 的架构。 */
fun Project.desktopJavaPlatform(): Provider<String> {
    val jdkRelease = layout.file(providers.systemProperty("java.home").map { File(it, "release") })
    return providers.fileContents(jdkRelease).asText
        .map { release ->
            fun value(key: String): String = release.lineSequence()
                .firstOrNull { it.startsWith("$key=") }?.substringAfter('=')?.trim('"')
                ?: error("The selected JDK release metadata has no $key")
            val os = when (val name = value("OS_NAME").lowercase()) {
                "windows" -> "windows"
                "darwin", "mac os x", "macos" -> "macosx"
                "linux" -> "linux"
                else -> error("Unsupported desktop JDK OS: $name")
            }
            val arch = when (val name = value("OS_ARCH").lowercase()) {
                "amd64", "x86_64" -> "x86_64"
                "aarch64", "arm64" -> "arm64"
                else -> error("Unsupported desktop JDK architecture: $name")
            }
            require(os != "windows" || arch == "x86_64") {
                "The official JavaFX/FFmpeg Windows runtime requires an x86_64 JDK."
            }
            "$os-$arch"
        }
}

/** JavaFX 的平台 classifier 同时选择类与 JNI；共享模块只编译，桌面宿主提供运行时。 */
class JavaFxConventionPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val versions = project.extensions.getByType(VersionCatalogsExtension::class.java).named("libs")
        val platform = project.desktopJavaPlatform().map { platform ->
            when (platform) {
                "macosx-arm64" -> "mac-aarch64"
                "macosx-x86_64" -> "mac"
                "windows-x86_64" -> "win"
                "linux-arm64" -> "linux-aarch64"
                "linux-x86_64" -> "linux"
                else -> error("Unsupported JavaFX platform: $platform")
            }
        }
        val modules = versions.findBundle("javafx").get().get().map { module ->
            project.dependencies.variantOf(project.providers.provider { module }) { variant ->
                variant.classifier(platform.get())
            }
        }
        project.plugins.withId("org.jetbrains.kotlin.multiplatform") {
            project.extensions.configure(KotlinMultiplatformExtension::class.java) { kotlin ->
                kotlin.sourceSets.configureEach { sourceSet ->
                    val configuration = when (sourceSet.name) {
                        "jvmMain" -> sourceSet.compileOnlyConfigurationName
                        "jvmTest" -> sourceSet.implementationConfigurationName
                        else -> return@configureEach
                    }
                    modules.forEach { project.dependencies.add(configuration, it) }
                }
            }
        }
        project.plugins.withId("org.jetbrains.kotlin.jvm") {
            modules.forEach { project.dependencies.add("runtimeOnly", it) }
        }
    }
}
