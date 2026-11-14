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
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget

@Suppress("UnstableApiUsage")
class ZhihuKmpConventionPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val jvmVersion = project.extensions
            .getByType(VersionCatalogsExtension::class.java)
            .named("libs")
            .findVersion("jvm")
            .get()
            .requiredVersion

        project.configurations.configureEach { configuration ->
            configuration.resolutionStrategy.cacheChangingModulesFor(24, "hours")
        }

        project.plugins.withId("org.jetbrains.kotlin.multiplatform") {
            project.extensions.configure(KotlinMultiplatformExtension::class.java) { kmp ->
                kmp.jvmToolchain(jvmVersion.toInt())
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
                    task.systemProperty("user.home", home.absolutePath)
                    task.systemProperty("java.util.prefs.userRoot", home.resolve("prefs").absolutePath)
                    task.systemProperty("java.util.prefs.systemRoot", home.resolve("system-prefs").absolutePath)
                    task.maxParallelForks = 1
                }
                project.plugins.withId("com.android.kotlin.multiplatform.library") {
                    kmp.targets.withType(KotlinMultiplatformAndroidLibraryTarget::class.java).configureEach { target ->
                        target.compileSdk = 37
                        target.minSdk = 27
                        target.compilerOptions.jvmTarget.set(JvmTarget.fromTarget(jvmVersion))
                    }
                }
            }
        }

        project.afterEvaluate {
            val kmp = project.extensions.findByType(KotlinMultiplatformExtension::class.java) ?: return@afterEvaluate
            val androidMain = kmp.sourceSets.findByName("androidMain") ?: return@afterEvaluate
            val androidFull = kmp.sourceSets.findByName("androidFull") ?: return@afterEvaluate
            val androidLite = kmp.sourceSets.findByName("androidLite") ?: return@afterEvaluate
            // 显式属性用于无 flavor 任务/配置缓存；任务名仍独立校验，不能被属性覆盖。
            val explicitVariant = project.providers
                .gradleProperty("zhihuAndroidVariant")
                .orNull
                ?.lowercase()
            require(explicitVariant == null || explicitVariant == "full" || explicitVariant == "lite") {
                "zhihuAndroidVariant must be full or lite"
            }
            val variantTaskPattern = Regex("^(?:assemble|bundle|install|test|connected|compile|verify)(Full|Lite)")
            val requestedVariants = project.gradle.startParameter.taskNames
                .mapNotNull { taskName ->
                    variantTaskPattern
                        .find(taskName.substringAfterLast(':'))
                        ?.groupValues
                        ?.get(1)
                        ?.lowercase()
                }.toSet()
            require(requestedVariants.size <= 1) {
                "Full and Lite Android tasks require separate Gradle invocations"
            }
            val requestedVariant = requestedVariants.singleOrNull()
            require(explicitVariant == null || requestedVariant == null || explicitVariant == requestedVariant) {
                "zhihuAndroidVariant=$explicitVariant conflicts with requested $requestedVariant Android tasks"
            }
            when (explicitVariant ?: requestedVariant) {
                "full" -> androidMain.dependsOn(androidFull)
                // 无 variant 请求（直接跑 `:模块:compileAndroidMain`、IDE sync、plain assemble）
                // 默认挂 lite：androidFull/androidLite 里的 expect/actual 缺了任何一个都会编译失败。
                else -> androidMain.dependsOn(androidLite)
            }
        }
    }
}
