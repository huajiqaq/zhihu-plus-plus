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
        // 先清除安装副本的旧 CMS 时间戳，再重新签名；不修改校验过的上游下载缓存。
        execOperations.exec { spec ->
            spec.commandLine("/usr/bin/codesign", "--remove-signature", onnxRuntime.absolutePath)
        }.assertNormalExitValue()
        execOperations.exec { spec ->
            spec.commandLine("/usr/bin/codesign", "--force", "--sign", "-", "--timestamp=none", onnxRuntime.absolutePath)
        }.assertNormalExitValue()
        execOperations.exec { spec ->
            spec.commandLine("/usr/bin/codesign", "--verify", "--strict", onnxRuntime.absolutePath)
        }.assertNormalExitValue()
        execOperations.exec { spec ->
            spec.commandLine(
                "/usr/bin/codesign",
                "--force",
                "--deep",
                "--sign",
                "-",
                "--timestamp=none",
                bundle.absolutePath,
            )
        }.assertNormalExitValue()
        execOperations.exec { spec ->
            spec.commandLine(
                "/usr/bin/codesign",
                "--verify",
                "--deep",
                "--strict",
                bundle.absolutePath,
            )
        }.assertNormalExitValue()
    }
}
