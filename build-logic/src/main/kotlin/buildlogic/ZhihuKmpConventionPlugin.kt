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

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest

/** iOS 默认完整构建，独立于 Android 的变体选择。 */
val Project.zhihuIosVariant: String
    get() {
        val variant = providers.gradleProperty("zhihuIosVariant").orElse("full").get().lowercase()
        require(variant == "full" || variant == "lite") { "zhihuIosVariant must be full or lite" }
        return variant
    }

/** Apple 编译与资源任务只在 Mac 注册；framework 名称沿用各模块的公开契约。 */
fun KotlinMultiplatformExtension.configureAppleTargets(frameworkBaseName: String? = null) {
    if (!System.getProperty("os.name").lowercase().contains("mac")) return
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        if (frameworkBaseName != null) {
            target.binaries.framework {
                baseName = frameworkBaseName
                isStatic = true
            }
        }
    }
    macosArm64()
}

/** Android 每次只编译一种变体，无 flavor 的任务和 IDE 同步默认使用 Lite。 */
fun KotlinSourceSet.configureAndroidVariant(miuixThemeMain: KotlinSourceSet? = null) {
    val explicitVariant = project.providers.gradleProperty("zhihuAndroidVariant").orNull?.lowercase()
    require(explicitVariant == null || explicitVariant == "full" || explicitVariant == "lite") {
        "zhihuAndroidVariant must be full or lite"
    }
    val variantTaskPattern = Regex("^(?:assemble|bundle|install|test|connected|compile|verify)(Full|Lite)")
    val requestedVariants = project.gradle.startParameter.taskNames
        .mapNotNull { taskName ->
            variantTaskPattern.find(taskName.substringAfterLast(':'))?.groupValues?.get(1)?.lowercase()
        }.toSet()
    require(requestedVariants.size <= 1) {
        "Full and Lite Android tasks require separate Gradle invocations"
    }
    val requestedVariant = requestedVariants.singleOrNull()
    require(explicitVariant == null || requestedVariant == null || explicitVariant == requestedVariant) {
        "zhihuAndroidVariant=$explicitVariant conflicts with requested $requestedVariant Android tasks"
    }
    val full = (explicitVariant ?: requestedVariant) == "full"
    kotlin.srcDir(if (full) "src/androidFull/kotlin" else "src/androidLite/kotlin")
    if (miuixThemeMain != null) {
        if (full) dependsOn(miuixThemeMain) else kotlin.srcDir("src/liteThemeMain/kotlin")
    }
}

/** iOS 每次只编译一种变体；Lite 与 Android Lite 共用不含 Miuix 的主题实现。 */
fun KotlinSourceSet.configureIosVariant(miuixThemeMain: KotlinSourceSet? = null) {
    val full = project.zhihuIosVariant == "full"
    kotlin.srcDir(if (full) "src/iosFullMain/kotlin" else "src/iosLiteMain/kotlin")
    if (miuixThemeMain != null) {
        if (full) dependsOn(miuixThemeMain) else kotlin.srcDir("src/liteThemeMain/kotlin")
    }
}

@OptIn(ExperimentalWasmDsl::class)
@Suppress("UnstableApiUsage")
class ZhihuKmpConventionPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val versions = project.extensions.getByType(VersionCatalogsExtension::class.java).named("libs")
        val jvmVersion = versions.findVersion("jvm").get().requiredVersion
        val compileSdk = versions.findVersion("compileSdk").get().requiredVersion.toInt()
        val compileSdkMinor = versions.findVersion("compileSdkMinor").get().requiredVersion.toInt()

        project.plugins.withId("org.jetbrains.kotlin.multiplatform") {
            project.extensions.configure(KotlinMultiplatformExtension::class.java) { kmp ->
                kmp.jvmToolchain(jvmVersion.toInt())
                kmp.wasmJs { browser() }
                kmp.compilerOptions.freeCompilerArgs.add("-Xexpect-actual-classes")
                kmp.targets.withType(KotlinJvmTarget::class.java) { target ->
                    target.compilerOptions.jvmTarget.set(JvmTarget.fromTarget(jvmVersion))
                }
                project.tasks.withType(Test::class.java).configureEach { task ->
                    // 测试的默认账户和数据库不得落到真实用户目录。
                    val home = project.layout.buildDirectory
                        .dir("test-home/${task.name}")
                        .get()
                        .asFile
                    // 既有界面回归固定中文；语言切换用例显式选择目标语言。
                    task.systemProperty("user.language", "zh")
                    task.systemProperty("user.country", "CN")
                    task.systemProperty("user.home", home.absolutePath)
                    task.systemProperty("java.util.prefs.userRoot", home.resolve("prefs").absolutePath)
                    task.systemProperty("java.util.prefs.systemRoot", home.resolve("system-prefs").absolutePath)
                    task.maxParallelForks = 1
                    if (project.name in setOf("shared-domain", "shared-ui-base", "shared-feature-settings", "shared")) {
                        task.maxHeapSize = "1g"
                    }
                }
                if (project.name in setOf("shared-ui-base", "shared-feature-settings")) {
                    val resourcePath = "misc/emoji_mapping.json"
                    val emojiMapping = project.rootProject.file(resourcePath)
                    project.tasks.withType(KotlinNativeTest::class.java).matching { it.name == "macosArm64Test" }.configureEach { test ->
                        test.inputs.file(emojiMapping)
                        // 未打包的 test.kexe 以自身所在目录作为 NSBundle 的资源根。
                        test.doFirst { task ->
                            val resourceRoot = (task as KotlinNativeTest).executable.parentFile
                            emojiMapping.copyTo(resourceRoot.resolve(resourcePath), overwrite = true)
                        }
                    }
                }
                project.plugins.withId("com.android.kotlin.multiplatform.library") {
                    kmp.targets.withType(KotlinMultiplatformAndroidLibraryTarget::class.java).configureEach { target ->
                        target.compileSdk {
                            version = release(compileSdk) { minorApiLevel = compileSdkMinor }
                        }
                        target.minSdk = 27
                        target.compilerOptions.jvmTarget.set(JvmTarget.fromTarget(jvmVersion))
                        target.withHostTest {}
                    }
                }
            }
        }
    }
}
