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

import java.io.File
import java.time.Instant
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Ad-hoc codesigning mutates the assembled application bundle")
abstract class ValidateAndSignMacosApp @Inject constructor(
    private val execOperations: ExecOperations,
) : DefaultTask() {
    @get:Internal
    abstract val appBundle: DirectoryProperty

    @get:InputDirectory
    abstract val composeResources: DirectoryProperty

    @get:Input
    abstract val requiredBundleFiles: ListProperty<String>

    @TaskAction
    fun validateAndSign() {
        val bundle = appBundle.get().asFile
        val sourceRoot = composeResources.get().asFile
        val packagedRoot = bundle.resolve("Contents/Resources/compose-resources")
        val resourceFiles = sourceRoot.walkTopDown().filter(File::isFile).toList()
        check(resourceFiles.isNotEmpty()) {
            "macOS Compose resources were not assembled"
        }
        val missingResources = resourceFiles.filterNot { resourceFile ->
            packagedRoot.resolve(resourceFile.relativeTo(sourceRoot)).isFile
        }
        check(missingResources.isEmpty()) {
            "macOS app is missing Compose resources: " +
                missingResources.joinToString { it.relativeTo(sourceRoot).invariantSeparatorsPath }
        }
        val missingRequiredFiles = requiredBundleFiles.get().filterNot { bundle.resolve(it).isFile }
        check(missingRequiredFiles.isEmpty()) {
            "macOS app is missing required files: ${missingRequiredFiles.joinToString()}"
        }
        val onnxRuntime = bundle.resolve("Contents/Frameworks/libonnxruntime.1.dylib")
        check(onnxRuntime.isFile) { "macOS app is missing ONNX Runtime" }
        logger.lifecycle("codesign adhoc-sign-runtime: clock={}, mtime={}", Instant.now(), Instant.ofEpochMilli(onnxRuntime.lastModified()))
        execOperations.exec { spec ->
            spec.commandLine("/usr/bin/codesign", "--verbose=4", "--force", "--sign", "-", "--timestamp=none", onnxRuntime.absolutePath)
        }.assertNormalExitValue()
        logger.lifecycle("codesign verify-runtime: clock={}, mtime={}", Instant.now(), Instant.ofEpochMilli(onnxRuntime.lastModified()))
        execOperations.exec { spec ->
            spec.commandLine("/usr/bin/codesign", "--verbose=4", "--verify", "--strict", onnxRuntime.absolutePath)
        }.assertNormalExitValue()
        logger.lifecycle("codesign adhoc-sign-bundle: clock={}", Instant.now())
        execOperations.exec { spec ->
            spec.commandLine(
                "/usr/bin/codesign",
                "--verbose=4",
                "--force",
                "--deep",
                "--sign",
                "-",
                "--timestamp=none",
                bundle.absolutePath,
            )
        }.assertNormalExitValue()
        logger.lifecycle("codesign verify-bundle: clock={}", Instant.now())
        execOperations.exec { spec ->
            spec.commandLine(
                "/usr/bin/codesign",
                "--verbose=4",
                "--verify",
                "--deep",
                "--strict",
                bundle.absolutePath,
            )
        }.assertNormalExitValue()
    }
}
