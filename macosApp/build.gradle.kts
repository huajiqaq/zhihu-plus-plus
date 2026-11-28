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
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.Sync

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
        binaries {
            all {
                freeCompilerArgs += "-Xoverride-konan-properties=ignoreXcodeVersionCheck=true"
                linkerOpts("-rpath", "@executable_path/../Frameworks")
                linkTaskProvider.configure { dependsOn(":shared-domain:prepareAppleOnnxRuntime") }
            }
            executable {
                baseName = "ZhihuPlusPlus"
                entryPoint = "com.github.zly2006.zhihu.macos.main"
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared"))
            implementation(project(":shared-domain"))
            implementation(project(":shared-ui-foundation"))
            implementation(project(":shared-ui-components"))
            implementation(libs.jb.compose.runtime)
            implementation(libs.material3)
            implementation(libs.jb.compose.ui)
        }
        commonTest.dependencies {
            implementation(project(":shared-core"))
            implementation(project(":shared-local-db"))
            implementation(project(":shared-feature-settings"))
            implementation(kotlin("test"))
        }
    }
}

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

listOf("Debug", "Release").forEach { buildType ->
    val buildTypeDirectory = buildType.lowercase()
    val appDirectory = layout.buildDirectory.dir("bin/macosArm64/${buildTypeDirectory}App/Zhihu++.app")

    val syncApp =
        tasks.register<Sync>("sync${buildType}MacosApp") {
            dependsOn(
                "link${buildType}ExecutableMacosArm64",
                prepareMacosLicenseManifest,
                ":shared:macosArm64AggregateResources",
                ":shared-domain:prepareAppleOnnxRuntime",
            )
            into(appDirectory)
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
            from(layout.buildDirectory.file("bin/macosArm64/${buildTypeDirectory}Executable/ZhihuPlusPlus.kexe")) {
                into("Contents/MacOS")
                rename("ZhihuPlusPlus\\.kexe", "ZhihuPlusPlus")
            }
            from(rootProject.file("misc/emoji_mapping.json")) {
                into("Contents/Resources/misc")
            }
            from(rootProject.file("misc/emojis")) {
                into("Contents/Resources/misc/emojis")
            }
            from(rootProject.file("iosApp")) {
                include("*.lproj/InfoPlist.strings")
                into("Contents/Resources")
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
    val validateAndSignApp =
        tasks.register<ValidateAndSignMacosApp>("validateAndSign${buildType}MacosApp") {
            dependsOn(syncApp)
            appBundle.set(appDirectory)
            composeResources.set(sharedMacosResources)
            requiredBundleFiles.set(
                listOf(
                    "Contents/Info.plist",
                    "Contents/MacOS/ZhihuPlusPlus",
                    "Contents/Resources/aboutlibraries.json",
                    "Contents/Resources/en.lproj/InfoPlist.strings",
                    "Contents/Resources/zh-Hans.lproj/InfoPlist.strings",
                    "Contents/Resources/misc/emoji_mapping.json",
                    "Contents/Resources/export/article_export_template.html",
                    "Contents/Resources/export/footnotes.js",
                    "Contents/Frameworks/libonnxruntime.1.dylib",
                    "Contents/Resources/licenses/onnxruntime/LICENSE",
                    "Contents/Resources/licenses/onnxruntime/ThirdPartyNotices.txt",
                ),
            )
        }

    if (buildType == "Debug") {
        tasks.named<Exec>("runDebugExecutableMacosArm64") {
            dependsOn(validateAndSignApp)
            executable =
                appDirectory
                    .get()
                    .file("Contents/MacOS/ZhihuPlusPlus")
                    .asFile
                    .absolutePath
        }
    }

    tasks.register("package${buildType}MacosApp") {
        dependsOn(validateAndSignApp)
    }
}
