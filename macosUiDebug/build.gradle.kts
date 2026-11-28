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

import buildlogic.ValidateAndSignMacosApp
import com.mikepenz.aboutlibraries.plugin.AboutLibrariesTask
import org.apache.tools.ant.filters.ReplaceTokens
import org.gradle.api.tasks.Sync
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType.DEBUG

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.plugin.compose)
    alias(libs.plugins.compose)
    id("com.mikepenz.aboutlibraries.plugin")
    alias(libs.plugins.ktlint)
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
    macosArm64 {
        val onnxRuntimeLibrary = project(":shared-domain").layout.buildDirectory.file("generated/onnxruntime-apple/macosArm64/libonnxruntime.1.dylib")
        binaries {
            all {
                freeCompilerArgs += "-Xoverride-konan-properties=ignoreXcodeVersionCheck=true"
                // 内联参数值，避免 konanc 将独立的 @executable_path token 当作 argfile。
                freeCompilerArgs += listOf("-linker-option=-rpath", "-linker-option=@executable_path/../Frameworks")
                linkerOpts(onnxRuntimeLibrary.get().asFile.absolutePath)
                linkTaskProvider.configure {
                    dependsOn(":shared-domain:prepareAppleOnnxRuntime")
                    inputs.file(onnxRuntimeLibrary)
                }
            }
            executable(listOf(DEBUG)) {
                baseName = "ZhihuPlusPlusUiDebug"
                entryPoint = "com.github.zly2006.zhihu.macos.debug.main"
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared"))
            implementation(project(":shared-local-db"))
            implementation(project(":shared-domain"))
            implementation(project(":shared-ui-foundation"))
            implementation(project(":shared-ui-components"))
            implementation(project(":shared-feature-settings"))
            implementation(libs.jb.compose.runtime)
            implementation(libs.jb.compose.ui)
            implementation(libs.jb.compose.ui.test)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}

val debugBundle = layout.buildDirectory.dir("bin/macosArm64/debugApp/ZhihuPlusPlusUiDebug.app")
val appVersionName = providers.gradleProperty("app.versionName").get()
val appVersionCode = providers.gradleProperty("app.versionCode").get()
val sharedMacosResources =
    project(":shared").layout.buildDirectory.dir("kotlin-multiplatform-resources/aggregated-resources/macosArm64")
val appleOnnxRuntime = project(":shared-domain").layout.buildDirectory.dir("generated/onnxruntime-apple")
aboutLibraries { collect { all.set(true) } }
val rawMacosLicenses = layout.buildDirectory.file("generated/aboutLibraries/dependencies.json")
val macosLicenses = layout.buildDirectory.file("generated/aboutLibraries/aboutlibraries.json")
val exportMacosLicenses = tasks.register<AboutLibrariesTask>("exportMacosLicenses") {
    variant.set(kotlin.targets.getByName("macosArm64").compilations.getByName("main").compileDependencyConfigurationName)
    configureOutputFile(rawMacosLicenses)
    configure()
}
val prepareMacosLicenseManifest = tasks.register<Exec>("prepareMacosLicenseManifest") {
    dependsOn(exportMacosLicenses, ":shared-domain:prepareAppleOnnxRuntime")
    val script = rootProject.layout.projectDirectory.file("shared/tools/merge_apple_licenses.py")
    val notices = appleOnnxRuntime.map { it.dir("licenses") }
    inputs.files(script, rawMacosLicenses)
    inputs.dir(notices)
    outputs.file(macosLicenses)
    commandLine(providers.environmentVariable("ZHIHU_PYTHON").orElse("python3").get(), "-I", script.asFile.absolutePath,
        "--input", rawMacosLicenses.get().asFile.absolutePath, "--output", macosLicenses.get().asFile.absolutePath,
        "--ort-licenses", notices.get().asFile.absolutePath)
}

val syncDebugApp =
    tasks.register<Sync>("syncDebugMacosUiDebug") {
        dependsOn(
            "linkDebugExecutableMacosArm64",
            prepareMacosLicenseManifest,
            ":shared:macosArm64AggregateResources",
            ":shared-domain:prepareAppleOnnxRuntime",
        )
        into(debugBundle)
        from("src/macosMain/resources/Info.plist") {
            into("Contents")
            filter(
                ReplaceTokens::class,
                "tokens" to
                    mapOf(
                        "APP_VERSION_NAME" to appVersionName,
                        "APP_VERSION_CODE" to appVersionCode,
                    ),
            )
        }
        from(layout.buildDirectory.file("bin/macosArm64/debugExecutable/ZhihuPlusPlusUiDebug.kexe")) {
            into("Contents/MacOS")
            rename("ZhihuPlusPlusUiDebug\\.kexe", "ZhihuPlusPlusUiDebug")
        }
        from(rootProject.file("misc/emoji_mapping.json")) {
            into("Contents/Resources/misc")
        }
        from(rootProject.file("misc/emojis")) {
            into("Contents/Resources/misc/emojis")
        }
        from(rootProject.file("desktopApp/src/main/composeResources/drawable/desktop_icon.png")) {
            into("Contents/Resources")
            rename { "desktop-icon.png" }
        }
        from(macosLicenses) {
            into("Contents/Resources")
        }
        from(rootProject.file("shared-domain/src/commonMain/resources")) {
            include("article_export_template.html", "footnotes.js")
            into("Contents/Resources/export")
        }
        from(sharedMacosResources) {
            into("Contents/Resources/compose-resources")
        }
        from(appleOnnxRuntime.map { it.file("macosArm64/libonnxruntime.1.dylib") }) {
            into("Contents/Frameworks")
            filePermissions { unix("rwxr-xr-x") }
        }
        from(appleOnnxRuntime.map { it.dir("licenses") }) { into("Contents/Resources/licenses/onnxruntime") }
    }

val validateAndSignDebugApp =
    tasks.register<ValidateAndSignMacosApp>("validateAndSignDebugMacosUiDebug") {
        dependsOn(syncDebugApp)
        appBundle.set(debugBundle)
        composeResources.set(sharedMacosResources)
        requiredBundleFiles.set(
            listOf(
                "Contents/Info.plist",
                "Contents/MacOS/ZhihuPlusPlusUiDebug",
                "Contents/Resources/aboutlibraries.json",
                "Contents/Resources/misc/emoji_mapping.json",
                "Contents/Resources/export/article_export_template.html",
                "Contents/Resources/export/footnotes.js",
                "Contents/Frameworks/libonnxruntime.1.dylib",
                "Contents/Resources/licenses/onnxruntime/LICENSE",
                "Contents/Resources/licenses/onnxruntime/ThirdPartyNotices.txt",
            ),
        )
    }

tasks.register("packageDebugMacosUiDebug") {
    dependsOn(validateAndSignDebugApp)
}

tasks.register<Exec>("verifyMacosReleaseIsolation") {
    dependsOn(":macosApp:linkReleaseExecutableMacosArm64")
    val releaseBinary =
        project(":macosApp")
            .layout.buildDirectory
            .file("bin/macosArm64/releaseExecutable/ZhihuPlusPlus.kexe")
            .get()
            .asFile
            .absolutePath
    inputs.file(releaseBinary)
    commandLine(
        "/bin/bash",
        "-euo",
        "pipefail",
        "-c",
        """
        test -f "${'$'}1"
        contents="${'$'}(/usr/bin/strings "${'$'}1")"
        if /usr/bin/grep -E 'ZHPP_BACKGROUND_UI_DEBUG_V1|--smoke-test|MacosAppSmokeTest|native-unhandled-exception-(smoke-)?test' <<< "${'$'}contents" > /dev/null; then
          echo "macOS release binary contains debug or smoke-test controls" >&2
          exit 1
        fi
        """.trimIndent(),
        "verifyMacosReleaseIsolation",
        releaseBinary,
    )
}
