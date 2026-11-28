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

import buildlogic.AppendAndroidFullLicenses
import buildlogic.VerifyThemeDependencies
import com.mikepenz.aboutlibraries.plugin.AboutLibrariesTask
import kotlin.io.encoding.Base64
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.aboutlibraries)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.kotlin.plugin.compose)
    id("kotlin-parcelize")
    alias(libs.plugins.ktlint)
}

ktlint {
    android.set(true)
    outputToConsole.set(true)
    enableExperimentalRules.set(true)
    filter {
        exclude("**/generated/**")
        exclude("**/build/**")
    }
}

val signingKey = providers.environmentVariable("signingKey").orNull?.takeIf { it.isNotBlank() }

android {
    namespace = "com.github.zly2006.zhihu"
    compileSdk {
        version = release(libs.versions.compileSdk.get().toInt()) {
            minorApiLevel = libs.versions.compileSdkMinor.get().toInt()
        }
    }
    sourceSets.getByName("main").assets.directories.add(rootProject.file("shared-domain/src/commonMain/resources").path)
    if (rootProject.extensions.extraProperties.has("zhihuUpstreamInputs")) {
        val upstreamInputs = rootProject.extensions.extraProperties["zhihuUpstreamInputs"] as String
        val mainAssets = sourceSets.getByName("main").assets
        mainAssets.directories.add(File(upstreamInputs, "assets").path)
    }

    defaultConfig {
        applicationId = "com.github.zly2006.zhplus"
        minSdk = 27
        targetSdk = 35
        versionCode = property("app.versionCode").toString().toIntOrNull() ?: 1
        versionName = property("app.versionName").toString()

        testInstrumentationRunner = "com.github.zly2006.zhihu.ZhihuInstrumentedTestRunner"
    }

    flavorDimensions += "version"
    productFlavors {
        create("full") {
            dimension = "version"
            buildConfigField("boolean", "IS_LITE", "false")
        }
        create("lite") {
            dimension = "version"
            isDefault = true
            buildConfigField("boolean", "IS_LITE", "true")
            applicationIdSuffix = ".lite"
        }
    }

    androidResources {
        @Suppress("UnstableApiUsage")
        localeFilters += listOf("en", "zh")
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }

    signingConfigs {
        if (signingKey != null) {
            register("env") {
                storeFile =
                    file("zhihu.jks").apply {
                        writeBytes(Base64.decode(signingKey))
                    }
                storePassword = System.getenv("keyStorePassword")
                keyAlias = System.getenv("keyAlias")
                keyPassword = System.getenv("keyPassword")
            }
        }
    }

    buildTypes {
        val gitRevision =
            providers.exec {
                workingDir(rootProject.projectDir)
                commandLine("git", "--no-replace-objects", "rev-parse", "--short=7", "HEAD")
                setEnvironment(environment.filterKeys { !it.startsWith("GIT_") })
                isIgnoreExitValue = true
            }
        val gitHash =
            if (gitRevision.result.get().exitValue == 0) {
                gitRevision.standardOutput.asText
                    .get()
                    .trim()
            } else {
                "unknown"
            }
        debug {
            buildConfigField("String", "GIT_HASH", "\"$gitHash\"")
            manifestPlaceholders["zhihuBuildType"] = "debug"
            manifestPlaceholders["zhihuGitHash"] = gitHash
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("String", "GIT_HASH", "\"$gitHash\"")
            manifestPlaceholders["zhihuBuildType"] = "release"
            manifestPlaceholders["zhihuGitHash"] = gitHash
            if (signingKey != null) {
                signingConfig = signingConfigs["env"]
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.jvm.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.jvm.get())
    }
    kotlin {
        jvmToolchain(libs.versions.jvm.get().toInt())
    }
    buildFeatures {
        viewBinding = false
        buildConfig = true
        compose = true
    }

    packaging {
        resources {
            merges +=
                listOf(
                    "META-INF/LICENSE-MIT.txt",
                    "META-INF/LICENSE-UPSTREAM-MIT.txt",
                    "META-INF/LICENSE-APACHE-2.0.txt",
                    "META-INF/NOTICE.md",
                )
            excludes +=
                listOf(
                    "META-INF/DEPENDENCIES",
                    "META-INF/**/LICENSE",
                    "META-INF/**/LICENSE.txt",
                    "META-INF/proguard/*",
                    "**.kotlin_module",
                    "kotlin-tooling-metadata.json",
                    "DebugProbesKt.bin",
                )
        }
    }

    androidComponents {
        beforeVariants(selector().all()) { variantBuilder ->
            val flavorName = variantBuilder.flavorName
            if (variantBuilder.buildType == "release") {
                val minify =
                    when (flavorName) {
                        "lite" -> true
                        else -> false
                    }
                variantBuilder.isMinifyEnabled = minify
                variantBuilder.shrinkResources = minify
            }
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    if (name.contains("Debug")) compilerOptions.freeCompilerArgs.add("-Xdebug")
}

tasks.withType<Test>().configureEach {
    javaLauncher.set(
        javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(libs.versions.jvm.get().toInt()))
        },
    )
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":shared-core"))
    implementation(project(":shared-local-db"))
    implementation(project(":shared-domain"))
    implementation(project(":shared-ui-foundation"))
    implementation(project(":shared-ui-components"))
    implementation(project(":shared-ui-base"))
    implementation(project(":shared-reading"))
    implementation(project(":shared-feature-settings"))
    implementation(project(":shared-feature-content"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.ktor.client.core.jvm)
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.content.negotiation.jvm)
    implementation(libs.ktor.serialization.kotlinx.json)

    implementation(libs.markdown.renderer)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.ktor3.android)
    implementation(libs.coil.gif)

    implementation(libs.zxing.android.embedded)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.jetbrains.lifecycle.runtime.compose)
    implementation(libs.jetbrains.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.livedata.ktx)

    implementation(libs.androidx.webkit)
    implementation(libs.androidx.activity.compose)
    implementation(libs.jb.compose.runtime)
    implementation(libs.jb.compose.foundation)
    implementation(libs.material3)
    implementation(libs.jb.compose.ui)
    implementation(libs.jb.compose.ui.graphics)
    implementation(libs.jb.compose.animation)
    implementation(libs.jb.compose.animation.core)
    implementation(libs.jb.compose.components.resources.android)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.aboutlibraries.compose.m3)
    "fullImplementation"(project(":sentence_embeddings"))
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
    androidTestImplementation(project(":shared-feature-feed"))
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)

    "fullImplementation"(libs.hanlp)

    testImplementation(libs.junit4)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.ktor.client.cio)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.ktor.serialization.kotlinx.json)
    testImplementation(libs.kotlinx.coroutines.test)
    //noinspection GradleDependency
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.ktor.client.mock)
}

val androidFullLicenseManifest = layout.buildDirectory.file("generated/android-full-licenses/native.json")
val prepareAndroidFullLicenseManifest = tasks.register<Exec>("prepareAndroidFullLicenseManifest") {
    // Cargo 构建已按锁文件取得依赖；许可扫描只读缓存，不编译或另行访问网络。
    dependsOn(":sentence_embeddings:buildRustLib")
    val script = layout.projectDirectory.file("tools/android_full_licenses.py")
    val tokenizer = rootProject.layout.projectDirectory.dir("rs-hf-tokenizer")
    val sentenceLicense = rootProject.layout.projectDirectory.file("sentence_embeddings/LICENSE")
    val licenseSupplements = layout.projectDirectory.dir("third-party-licenses/android-full")
    val python = providers.environmentVariable("ZHIHU_PYTHON").orElse("python3")
    inputs.files(script, tokenizer.file("Cargo.toml"), tokenizer.file("Cargo.lock"), tokenizer.file("LICENSE"), sentenceLicense)
    inputs.dir(licenseSupplements)
    outputs.file(androidFullLicenseManifest)
    commandLine(
        python.get(), "-I", script.asFile.absolutePath,
        "--manifest", tokenizer.file("Cargo.toml").asFile.absolutePath,
        "--sentence-license", sentenceLicense.asFile.absolutePath,
        "--license-supplements", licenseSupplements.file("sources.json").asFile.absolutePath,
        "--target", "aarch64-linux-android",
        "--target", "x86_64-linux-android",
        "--output", androidFullLicenseManifest.get().asFile.absolutePath,
    )
}

androidComponents {
    onVariants(selector().all()) { variant ->
        val flavor = variant.productFlavors.single { it.first == "version" }.second
        val capitalizedName = variant.name.replaceFirstChar(Char::uppercaseChar)
        if (flavor == "full") {
            tasks.named<AboutLibrariesTask>("prepareLibraryDefinitions$capitalizedName") {
                dependsOn(prepareAndroidFullLicenseManifest)
                inputs.file(androidFullLicenseManifest)
                doLast(
                    AppendAndroidFullLicenses(
                        layout.buildDirectory.file("generated/aboutLibraries/${variant.name}/res/raw/aboutlibraries.json"),
                        androidFullLicenseManifest,
                    ),
                )
            }
        }
        val runtimeResolution = variant.runtimeConfiguration.incoming.resolutionResult
        val verify =
            tasks.register<VerifyThemeDependencies>("verify${capitalizedName}ThemeDependencies") {
                group = "verification"
                description = "Checks ${variant.name} resolved theme dependencies."
                resolvedModules.set(
                    providers.provider {
                        runtimeResolution.allComponents
                            .mapNotNull { component ->
                                (component.id as? ModuleComponentIdentifier)?.let {
                                    "${it.group}:${it.module}:${it.version}"
                                }
                            }.distinct()
                            .sorted()
                    },
                )
                requireMiuixTheme.set(flavor == "full")
            }
        variant.lifecycleTasks.registerPreBuild(verify)
        val aggregate = "verify${flavor.replaceFirstChar(Char::uppercaseChar)}ThemeDependencies"
        if (tasks.findByName(aggregate) == null) {
            tasks.register(aggregate) { group = "verification" }
        }
        tasks.named(aggregate) { dependsOn(verify) }
    }
}
