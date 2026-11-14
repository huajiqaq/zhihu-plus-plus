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

import groovy.json.JsonSlurper

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

buildCache {
    local {
        isEnabled = true
        directory = File(rootDir, ".gradle/build-cache")
    }
}
val patchProperty = providers.gradleProperty("zhihuUpstreamPatches").orNull
require(patchProperty == null || patchProperty == "true" || patchProperty == "false") {
    "zhihuUpstreamPatches must be true or false"
}
val packagingRequested =
    gradle.startParameter.taskNames.any { task ->
        Regex("^(assemble|bundle|package|createDistributable|install|build)")
            .containsMatchIn(task.substringAfterLast(':'))
    }
require(!packagingRequested || patchProperty != "false") { "打包必须使用已审查的上游补丁" }
val useUpstreamPatches = packagingRequested || patchProperty == "true"
val upstreamRecipe = JsonSlurper().parse(file("gradle/upstream-sources.lock.json")) as Map<*, *>
val sourcePreparation =
    providers.exec {
        isIgnoreExitValue = true
        workingDir(rootDir)
        val windows = System.getProperty("os.name").lowercase().contains("windows")
        commandLine(if (windows) "python" else "python3", "-I", file("gradle/prepare-upstream-sources.py").absolutePath)
        if (useUpstreamPatches) {
            args("--package")
            val gradleHome = requireNotNull(gradle.gradleHomeDir) { "打包资源准备需要当前 Gradle 目录" }
            args("--gradle", gradleHome.resolve("bin/${if (windows) "gradle.bat" else "gradle"}").absolutePath)
        }
        if (gradle.startParameter.isOffline) args("--offline")
    }
check(sourcePreparation.result.get().exitValue == 0) {
    "上游构建输入准备失败：\n${sourcePreparation.standardError.asText.get()}"
}
val upstreamInputs = file(sourcePreparation.standardOutput.asText.get().trim())
check(upstreamInputs.canonicalFile.toPath().startsWith(file(".gradle/upstream-inputs").canonicalFile.toPath())) {
    "上游输入必须来自本项目隔离缓存"
}
gradle.beforeProject {
    if (path == ":") extensions.extraProperties.set("zhihuUpstreamInputs", upstreamInputs.absolutePath)
}
if (useUpstreamPatches) {
    for ((sourceName, metadata) in upstreamRecipe["sources"] as Map<*, *>) {
        val source = metadata as Map<*, *>
        includeBuild(upstreamInputs.resolve("sources/$sourceName")) {
            name = "upstream-$sourceName"
            dependencySubstitution {
                for ((coordinate, destination) in source["modules"] as Map<*, *>) {
                    substitute(module(coordinate as String)).using(project(destination as String))
                }
            }
        }
    }
}
val verifiedPublicCoordinates =
    (upstreamRecipe["artifacts"] as Map<*, *>).keys
        .asSequence()
        .map { relative ->
            val parts = (relative as String).split('/')
            Triple(parts.dropLast(3).joinToString("."), parts[parts.lastIndex - 2], parts[parts.lastIndex - 1])
        }.toSet()

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        exclusiveContent {
            forRepository {
                maven {
                    name = "verifiedPublicInputs"
                    url = upstreamInputs.resolve("maven").toURI()
                }
            }
            filter {
                verifiedPublicCoordinates.forEach { (group, module, version) -> includeVersion(group, module, version) }
            }
        }
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        if (!useUpstreamPatches) {
            maven("https://central.sonatype.com/repository/maven-snapshots/") {
                content { includeGroup("org.tiqian") }
                mavenContent { snapshotsOnly() }
            }
        }
        mavenCentral {
            if (useUpstreamPatches) {
                content {
                    excludeGroup("org.tiqian")
                    excludeModuleByRegex("io\\.github\\.huarangmeng", "markdown-(parser|runtime|renderer)(-.*)?")
                }
            }
        }
        maven("https://www.jitpack.io") {
            if (useUpstreamPatches) {
                content {
                    excludeGroup("org.tiqian")
                    excludeModuleByRegex("io\\.github\\.huarangmeng", "markdown-(parser|runtime|renderer)(-.*)?")
                }
            }
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "Zhihu"
include(":app")
include(":desktopApp")
if (System.getProperty("os.name").lowercase().contains("mac")) {
    include(":macosApp")
    include(":macosUiDebug")
}
include(":sentence_embeddings")
include(":shared")
include(":shared-core")
include(":shared-domain")
include(":shared-ui-foundation")
include(":shared-ui-components")
include(":shared-ui-base")
include(":shared-feature-feed")
include(":shared-feature-settings")
include(":shared-feature-content")
include(":shared-local-db")
