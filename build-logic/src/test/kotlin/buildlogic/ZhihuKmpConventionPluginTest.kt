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
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
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

    @Test
    fun heavyJvmTestsUseOneGigabyteAndOneFork() {
        for (name in listOf("shared-domain", "shared-ui-base", "shared-feature-settings", "shared")) {
            val result = runner(null, "verifyTestIsolation", projectName = name).build()
            assertTrue(result.output.contains("TEST_HEAVY_HEAP=true"), result.output)
            assertTrue(result.output.contains("TEST_SINGLE_WORKER=true"), result.output)
        }
    }

    @Test
    fun appleTargetsMatchTheHostAndRetainStaticFrameworks() {
        val result = runner(null, "verifyAppleTargets", includeAppleTargets = true).build()
        if (System.getProperty("os.name").lowercase().contains("mac")) {
            assertTrue(result.output.contains("APPLE_TARGETS=iosArm64,iosSimulatorArm64,macosArm64"), result.output)
            assertTrue(result.output.contains("IOS_FRAMEWORKS=4"), result.output)
            assertTrue(result.output.contains("IOS_FRAMEWORK_CONTRACT=true"), result.output)
        } else {
            assertTrue(result.output.contains("APPLE_TARGETS=none"), result.output)
            assertTrue(result.output.contains("IOS_FRAMEWORKS=0"), result.output)
        }
    }

    @Test
    fun fontBootstrapPreservesJvmArgumentsAndFontPaths() {
        val directory = temporaryFolder.newFolder()
        val preparation = File("../gradle/prepare-upstream-sources.py").canonicalFile
        assertTrue(preparation.isFile, "找不到当前字体准备源码")
        val checker = directory.resolve("check-arguments.py")
        checker.writeText(
            """
            import ast, json, os, subprocess, sys
            from pathlib import Path

            source = Path(sys.argv[1])
            tree = ast.parse(source.read_text(encoding='utf-8'))
            function = next(node for node in tree.body if isinstance(node, ast.FunctionDef) and node.name == 'gradle_subprocess_command')
            namespace = {'os': os, 'subprocess': subprocess}
            exec(compile(ast.Module(body=[function], type_ignores=[]), str(source), 'exec'), namespace)
            directory = Path(__file__).parent / 'font arguments with spaces'
            directory.mkdir()
            receiver = directory / 'receive arguments.py'
            receiver.write_text("import json,sys\nsys.stdout.buffer.write(json.dumps(sys.argv[1:]).encode('ascii'))\n", encoding='utf-8')
            if os.name == 'nt':
                forwarder = directory / 'forward arguments.cmd'
                forwarder.write_text('@echo off\n"' + sys.executable + '" -I "' + str(receiver) + '" %*\n', encoding='utf-8')
                executable = [str(forwarder)]
            else:
                executable = [sys.executable, '-I', str(receiver)]
            for jvmargs in (
                '-Xmx2048m -XX:MaxMetaspaceSize=768m -Dfile.encoding=UTF-8',
                '-Xmx5120m -Dfile.encoding=UTF-8',
            ):
                expected = [
                    '--project-dir', str(directory),
                    '-Dorg.gradle.jvmargs=' + jvmargs,
                    '-Pkotlin.daemon.jvmargs=-Xmx2048m -XX:MaxMetaspaceSize=768m',
                    '--max-workers=2',
                    '-PtiqianLeteRegularLayoutFont=' + str(directory / 'layout font.otf'),
                    '-PtiqianStixRuntimeFont=' + str(directory / 'runtime font.otf'),
                ]
                command = namespace['gradle_subprocess_command'](executable + expected)
                result = subprocess.run(command, cwd=directory, capture_output=True, text=True, encoding='utf-8', check=True)
                received = json.loads(result.stdout)
                assert received == expected, (received, expected)
            print('PARAMETERS_PRESERVED=true')
            """.trimIndent(),
        )
        val python = if (System.getProperty("os.name").lowercase().contains("win")) "python" else "python3"
        val process = ProcessBuilder(python, "-I", checker.absolutePath, preparation.absolutePath)
            .directory(directory)
            .redirectErrorStream(true)
            .start()
        try {
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "合成参数转发检查超时")
            val output = process.inputStream.bufferedReader().use { it.readText() }
            assertEquals(0, process.exitValue(), output)
            assertTrue(output.contains("PARAMETERS_PRESERVED=true"), output)
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }

    private fun runner(
        explicitVariant: String?,
        vararg tasks: String,
        projectName: String = "variant-selection-test",
        includeAppleTargets: Boolean = false,
    ): GradleRunner {
        val directory = temporaryFolder.newFolder()
        directory.resolve("settings.gradle").writeText(
            """
            rootProject.name = '$projectName'
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
            ${if (includeAppleTargets) "buildlogic.ZhihuKmpConventionPluginKt.configureAppleTargets(kotlin, 'TestApple')" else ""}
            ['assembleFullDebug', 'assembleLiteDebug', 'compileAndroidMain'].each { name ->
                tasks.register(name)
            }
            tasks.register('verifyAppleTargets') {
                doLast {
                    def nativeTargets = kotlin.targets.findAll { it instanceof org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget }
                    def names = nativeTargets.collect { it.name }.sort().join(',')
                    def frameworks = nativeTargets.collectMany { target ->
                        target.binaries.findAll { it instanceof org.jetbrains.kotlin.gradle.plugin.mpp.Framework }
                    }
                    println('APPLE_TARGETS=' + (names ?: 'none'))
                    println('IOS_FRAMEWORKS=' + frameworks.size())
                    println('IOS_FRAMEWORK_CONTRACT=' + frameworks.every { it.baseName == 'TestApple' && it.isStatic() })
                }
            }
            tasks.register('verifyTestIsolation') {
                doLast {
                    def task = tasks.getByName('jvmTest')
                    def home = new File(task.systemProperties['user.home'].toString()).canonicalFile
                    def expected = new File(layout.buildDirectory.get().asFile, 'test-home/jvmTest').canonicalFile
                    println('TEST_HOME_ISOLATED=' + (home == expected))
                    println('TEST_PREFS_ISOLATED=' + (task.systemProperties['java.util.prefs.userRoot'] == new File(expected, 'prefs').absolutePath))
                    println('TEST_SINGLE_WORKER=' + (task.maxParallelForks == 1))
                    println('TEST_HEAVY_HEAP=' + (task.maxHeapSize == '1g'))
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
