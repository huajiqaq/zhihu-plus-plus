/*
 * Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation (version 3 only).
 */

package buildlogic

import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertTrue

class ZhihuKmpConventionPluginTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun rejectsInvalidExplicitVariant() {
        for (variant in listOf("", "debug", "full ")) {
            val result = runner(variant, "compileAndroidMain").buildAndFail()
            assertTrue(result.output.contains("zhihuAndroidVariant"), result.output)
        }
    }

    @Test
    fun rejectsExplicitVariantThatConflictsWithRequestedTask() {
        for ((variant, task) in listOf("full" to "assembleLiteDebug", "lite" to "assembleFullDebug")) {
            val result = runner(variant, task).buildAndFail()
            assertTrue(result.output.contains("zhihuAndroidVariant"), result.output)
        }
    }

    @Test
    fun rejectsMixedVariantTasksEvenWithExplicitVariant() {
        for (variant in listOf("full", "lite")) {
            val result = runner(variant, "assembleFullDebug", "assembleLiteDebug").buildAndFail()
            assertTrue(result.output.contains("separate Gradle invocations"), result.output)
        }
    }

    @Test
    fun rejectsMixedVariantTasksWithoutExplicitVariant() {
        val result = runner(null, "assembleFullDebug", "assembleLiteDebug").buildAndFail()
        assertTrue(result.output.contains("separate Gradle invocations"), result.output)
    }

    @Test
    fun plainModuleCompilationDefaultsToLite() {
        val result = runner(null, "compileAndroidMain").build()
        assertTrue(result.output.contains("SELECTED_ANDROID_VARIANT=androidLite"), result.output)
    }

    @Test
    fun explicitFullSelectsFullForPlainModuleCompilation() {
        val result = runner("FULL", "compileAndroidMain").build()
        assertTrue(result.output.contains("SELECTED_ANDROID_VARIANT=androidFull"), result.output)
    }

    @Test
    fun matchingVariantTasksSelectTheirOwnSourceSet() {
        for ((variant, task) in listOf("full" to "assembleFullDebug", "lite" to "assembleLiteDebug")) {
            val result = runner(variant, task).build()
            val expected = if (variant == "full") "androidFull" else "androidLite"
            assertTrue(result.output.contains("SELECTED_ANDROID_VARIANT=$expected"), result.output)
        }
    }

    @Test
    fun jvmTestsUseAnIsolatedHomeAndOneDatabaseWorker() {
        val result = runner(null, "verifyTestIsolation").build()
        assertTrue(result.output.contains("TEST_HOME_ISOLATED=true"), result.output)
        assertTrue(result.output.contains("TEST_PREFS_ISOLATED=true"), result.output)
        assertTrue(result.output.contains("TEST_SINGLE_WORKER=true"), result.output)
    }

    private fun runner(explicitVariant: String?, vararg tasks: String): GradleRunner {
        val directory = temporaryFolder.newFolder()
        directory.resolve("settings.gradle").writeText(
            """
            rootProject.name = 'variant-selection-test'
            dependencyResolutionManagement {
                versionCatalogs {
                    libs { version('jvm', '21') }
                }
            }
            """.trimIndent(),
        )
        directory.resolve("build.gradle").writeText(
            """
            plugins {
                id 'org.jetbrains.kotlin.multiplatform'
                id 'zhihu.kmp-conventions'
            }
            kotlin {
                jvm()
                sourceSets {
                    def common = getByName('commonMain')
                    ['androidMain', 'androidFull', 'androidLite'].each { name ->
                        create(name).dependsOn(common)
                    }
                }
            }
            ['assembleFullDebug', 'assembleLiteDebug', 'compileAndroidMain'].each { name ->
                tasks.register(name)
            }
            tasks.register('verifyTestIsolation') {
                doLast {
                    def task = tasks.getByName('jvmTest')
                    def home = new File(task.systemProperties['user.home'].toString()).canonicalFile
                    def expected = new File(layout.buildDirectory.get().asFile, 'test-home/jvmTest').canonicalFile
                    println('TEST_HOME_ISOLATED=' + (home == expected))
                    println('TEST_PREFS_ISOLATED=' + (task.systemProperties['java.util.prefs.userRoot'] == new File(expected, 'prefs').absolutePath))
                    println('TEST_SINGLE_WORKER=' + (task.maxParallelForks == 1))
                }
            }
            tasks.register('verifyVariantSelection') {
                doLast {
                    def selected = kotlin.sourceSets.getByName('androidMain').dependsOn
                        .findAll { it.name == 'androidFull' || it.name == 'androidLite' }
                        .collect { it.name }.sort().join(',')
                    println('SELECTED_ANDROID_VARIANT=' + selected)
                }
            }
            """.trimIndent(),
        )
        val arguments = tasks.toList() + listOf("verifyVariantSelection", "--offline", "--no-configuration-cache", "--stacktrace") +
            listOfNotNull(explicitVariant?.let { "-PzhihuAndroidVariant=$it" })
        return GradleRunner
            .create()
            .withProjectDir(directory)
            .withPluginClasspath()
            .withArguments(arguments)
    }
}
