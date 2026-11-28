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

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.io.OutputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsRootExtension
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig
import org.jetbrains.kotlin.gradle.targets.js.webpack.WebpackDevtool

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.plugin.compose)
    alias(libs.plugins.compose)
    id("com.mikepenz.aboutlibraries.plugin")
    alias(libs.plugins.ktlint)
}

val webLicenseDirectory = layout.buildDirectory.dir("generated/aboutLibraries")
val prepareWebBridge =
    tasks.register<Sync>("prepareWebBridge") {
        from(rootProject.layout.projectDirectory.dir("misc/zhihu-web-bridge")) { include("*.user.js") }
        from(rootProject.layout.projectDirectory.file("LICENSE"))
        from(rootProject.layout.projectDirectory.dir("shared-domain/src/commonMain/resources")) {
            include("article_export_template.html", "footnotes.js")
        }
        into(layout.buildDirectory.dir("generated/web-bridge"))
    }

aboutLibraries {
    collect {
        all = true
    }
    export {
        outputFile = webLicenseDirectory.map { it.file("aboutlibraries.json") }
    }
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "zhihu.js"
                devServer =
                    (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                        port = 8080
                        open = false
                    }
            }
        }
        binaries.executable()
        aboutLibraries.collect.filterVariants =
            compilations.named("main").map {
                setOf(it.compileDependencyConfigurationName)
            }
    }
    sourceSets.wasmJsMain {
        resources.srcDir(prepareWebBridge)
        dependencies {
            implementation(projects.shared)
            implementation(libs.jb.compose.ui)
            implementation(libs.jb.compose.components.resources)
            // 不 import 到应用 bundle；只由录音流程按需加载本地 UMD 文件。
            implementation(npm("@ffmpeg/core", libs.versions.ffmpegWasm.get()))
        }
    }
}

/** 把 npm 与静态链接编解码器的原许可合并到应用实际展示的清单中。 */
abstract class MergeWebLicenses : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val gradleManifest: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val npmMetadata: ConfigurableFileCollection

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val codecLicenses: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val onnxLicenses: DirectoryProperty

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun merge() {
        val parser = JsonSlurper()
        @Suppress("UNCHECKED_CAST")
        val manifest = parser.parse(gradleManifest.get().asFile) as MutableMap<String, Any>
        @Suppress("UNCHECKED_CAST")
        val libraries = manifest.getValue("libraries") as MutableList<Map<String, Any>>
        @Suppress("UNCHECKED_CAST")
        val licenses = manifest.getValue("licenses") as MutableMap<String, Any>
        val codecDirectory = codecLicenses.get().asFile
        @Suppress("UNCHECKED_CAST")
        val sources = parser.parse(codecDirectory.resolve("sources.json")) as Map<String, Any>
        val components = sources.getValue("components") as List<*>
        components.forEach { entry ->
            @Suppress("UNCHECKED_CAST")
            val component = entry as Map<String, Any>
            val id = "ffmpegwasm-${component.getValue("id")}"
            val files = component.getValue("licenseFiles") as List<*>
            val content = files.joinToString("\n\n") { path ->
                val file = codecDirectory.resolve(path.toString()).canonicalFile
                require(file.toPath().startsWith(codecDirectory.canonicalFile.toPath())) { "Invalid license path: $path" }
                file.readText()
            }
            licenses[id] = mapOf("name" to component.getValue("license"), "url" to component.getValue("source"), "content" to content, "hash" to id)
            libraries.add(
                mapOf(
                    "uniqueId" to "native:$id",
                    "name" to component.getValue("name"),
                    "artifactVersion" to component.getValue("version"),
                    "website" to component.getValue("source"),
                    "licenses" to listOf(id),
                ),
            )
        }
        npmMetadata.files.filter { it.name == "package.json" }.forEach { file ->
            @Suppress("UNCHECKED_CAST")
            val pkg = parser.parse(file) as Map<String, Any>
            val name = pkg.getValue("name").toString()
            val id = "npm:$name"
            val content =
                if (name == "@ffmpeg/core") {
                    codecDirectory.resolve("ffmpeg/COPYING.GPLv2").readText() + "\n\n" + codecDirectory.resolve("NOTICE.txt").readText()
                } else if (name == "onnxruntime-web") {
                    onnxLicenses.get().file("LICENSE").asFile.readText() + "\n\n" + onnxLicenses.get().file("ThirdPartyNotices.txt").asFile.readText()
                } else {
                    file.parentFile.resolve("LICENSE").readText()
                }
            licenses[id] = mapOf("name" to pkg.getValue("license"), "content" to content, "hash" to id)
            libraries.removeAll { it["uniqueId"] == id }
            libraries.add(
                mapOf(
                    "uniqueId" to id,
                    "name" to name,
                    "artifactVersion" to pkg.getValue("version"),
                    "website" to (pkg["homepage"] ?: "https://www.npmjs.com/package/$name"),
                    "licenses" to listOf(id),
                ),
            )
        }
        outputFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(JsonOutput.toJson(manifest))
        }
    }
}

val wasmNode = rootProject.extensions.getByType<WasmNodeJsRootExtension>()
val npmModules = wasmNode.rootPackageDirectory.map { it.dir("node_modules") }
val prepareWebNpmResources =
    tasks.register<Sync>("prepareWebNpmResources") {
        // 只依赖 package.json/npm 安装，不依赖 webpack/compileSync，以免 processResources 形成环。
        dependsOn(wasmNode.npmInstallTaskProvider)
        from(npmModules.map { it.dir("@ffmpeg/core/dist/umd") }) {
            include("ffmpeg-core.js", "ffmpeg-core.wasm")
            into("voice-codec")
        }
        from(npmModules.map { it.file("@ffmpeg/core/package.json") }) { into("voice-codec") }
        from(npmModules.map { it.dir("onnxruntime-web/dist") }) {
            include("ort.wasm.min.mjs", "ort-wasm-simd-threaded.mjs", "ort-wasm-simd-threaded.wasm")
            into("nlp/ort")
        }
        from(npmModules.map { it.file("onnxruntime-web/package.json") }) { into("nlp/ort") }
        from(npmModules.map { it.dir("@noble/hashes") }) {
            include("sha2.js", "_md.js", "_u64.js", "utils.js", "LICENSE", "package.json")
            into("nlp/hashes")
        }
        from(npmModules.map { it.dir("katex/dist") }) {
            include("katex.min.css", "fonts/**")
            into("export/katex")
        }
        from(npmModules.map { it.file("katex/LICENSE") }) { into("export/katex") }
        from(npmModules.map { it.file("html2canvas-pro/LICENSE") }) { into("export/html2canvas-pro") }
        from(npmModules.map { it.file("fflate/LICENSE") }) { into("export/fflate") }
        into(layout.buildDirectory.dir("generated/web-npm"))
        val modules = npmModules
        doFirst {
            listOf(
                "@ffmpeg/core/dist/umd/ffmpeg-core.js", "@ffmpeg/core/dist/umd/ffmpeg-core.wasm", "katex/dist/katex.min.css", "katex/LICENSE", "html2canvas-pro/LICENSE", "fflate/LICENSE",
                "onnxruntime-web/dist/ort.wasm.min.mjs", "onnxruntime-web/dist/ort-wasm-simd-threaded.mjs", "onnxruntime-web/dist/ort-wasm-simd-threaded.wasm",
                "@noble/hashes/sha2.js", "@noble/hashes/_md.js", "@noble/hashes/_u64.js", "@noble/hashes/utils.js", "@noble/hashes/LICENSE",
            )
                .forEach { path -> check(modules.get().file(path).asFile.isFile) { "Missing npm resource: $path" } }
            check(modules.get().dir("katex/dist/fonts").asFile.listFiles().orEmpty().any { it.extension == "woff2" }) { "Missing KaTeX fonts" }
        }
    }
val mergedWebLicenseDirectory = layout.buildDirectory.dir("generated/web-licenses")
val mergeWebLicenses =
    tasks.register<MergeWebLicenses>("mergeWebLicenses") {
        dependsOn(tasks.named("exportLibraryDefinitions"), wasmNode.npmInstallTaskProvider)
        gradleManifest.set(webLicenseDirectory.map { it.file("aboutlibraries.json") })
        npmMetadata.from(npmModules.map { directory ->
            listOf("@ffmpeg/core/package.json", "katex/package.json", "katex/LICENSE", "html2canvas-pro/package.json", "html2canvas-pro/LICENSE", "fflate/package.json", "fflate/LICENSE", "onnxruntime-web/package.json", "@noble/hashes/package.json", "@noble/hashes/LICENSE")
                .map { directory.file(it) }
        })
        codecLicenses.set(layout.projectDirectory.dir("src/wasmJsMain/resources/voice-codec/licenses"))
        onnxLicenses.set(layout.projectDirectory.dir("src/wasmJsMain/resources/nlp/ort"))
        outputFile.set(mergedWebLicenseDirectory.map { it.file("aboutlibraries.json") })
    }
kotlin.sourceSets.named("wasmJsMain") {
    resources.srcDir(prepareWebNpmResources)
    // 配置缓存只需要目录位置；生成依赖单独声明，不在序列化 CopySpec 时映射任务输出。
    resources.srcDir(files(mergedWebLicenseDirectory).builtBy(mergeWebLicenses))
}

tasks.named<Copy>("wasmJsProcessResources") {
    val versionName = providers.gradleProperty("app.versionName")
    val versionCode = providers.gradleProperty("app.versionCode")
    inputs.property("webVersionName", versionName)
    inputs.property("webVersionCode", versionCode)
    filteringCharset = "UTF-8"
    filesMatching("index.html") {
        val name = versionName.get()
        val code = versionCode.get()
        filter {
            it.replace("name=\"zhihu-version-name\" content=\"development\"", "name=\"zhihu-version-name\" content=\"$name\"")
                .replace("name=\"zhihu-version-code\" content=\"0\"", "name=\"zhihu-version-code\" content=\"$code\"")
        }
    }
}

val productionWebpack =
    tasks.named<KotlinWebpack>("wasmJsBrowserProductionWebpack") {
        devtool = WebpackDevtool.HIDDEN_SOURCE_MAP
        webpackConfigApplier { outputFileName = "zhihu.[contenthash].js" }
    }
tasks.named<Sync>("wasmJsBrowserDistribution") {
    val productionBundleName =
        productionWebpack.flatMap { it.outputDirectory }.map { directory ->
            directory.asFile.listFiles().orEmpty().single { it.name.matches(Regex("zhihu\\.[a-f0-9]+\\.js")) }.name
        }
    val productionBuildId = productionBundleName.map { it.removePrefix("zhihu.").removeSuffix(".js") }
    val versionName = providers.gradleProperty("app.versionName")
    val versionCode = providers.gradleProperty("app.versionCode").map(String::toInt)
    val resourceBase = versionName.zip(versionCode) { name, code ->
        require(name.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) && code > 0) { "Invalid published Web version" }
        "app-resources/$code-$name/"
    }
    inputs.property("webBuildId", productionBuildId)
    inputs.property("webVersionName", versionName)
    inputs.property("webVersionCode", versionCode)
    // 调试映射留在 kotlin-webpack / compileSync 中，不随静态站点发布；完整许可证仍分发。
    exclude("**/*.map")
    filteringCharset = "UTF-8"
    from(layout.projectDirectory.file("src/wasmJsMain/productionResources/web-version.json"))
    filesMatching("index.html") {
        val bundleName = productionBundleName.get()
        val buildId = productionBuildId.get()
        val name = versionName.get()
        val code = versionCode.get()
        val base = resourceBase.get()
        filter {
            it.replace("name=\"zhihu-app-entry\" content=\"zhihu.js\"", "name=\"zhihu-app-entry\" content=\"$bundleName\"")
                .replace("name=\"zhihu-build-id\" content=\"development\"", "name=\"zhihu-build-id\" content=\"$buildId\"")
                .replace("name=\"zhihu-cache-mode\" content=\"debug\"", "name=\"zhihu-cache-mode\" content=\"production\"")
                .replace("name=\"zhihu-version-name\" content=\"development\"", "name=\"zhihu-version-name\" content=\"$name\"")
                .replace("name=\"zhihu-version-code\" content=\"0\"", "name=\"zhihu-version-code\" content=\"$code\"")
                .replace("name=\"zhihu-resource-base\" content=\"./\"", "name=\"zhihu-resource-base\" content=\"$base\"")
        }
    }
    doLast {
        val root = destinationDir
        val base = resourceBase.get()
        val target = root.resolve(base)
        val stableFiles = setOf(
            "index.html", "zhihu-app-bootstrap.js", "zhihu-app-cache.js", "zhihu-notifications-sw.js",
            "manifest.webmanifest", "icon-192.png", "icon-512.png", "LICENSE",
        )
        // Enumerate after Sync, not webpack compilation: this also covers copied resources and dynamic chunks.
        val payload = root.walkTopDown().filter { it.isFile && it.relativeTo(root).invariantSeparatorsPath != "web-version.json" }
            .map { it to it.relativeTo(root).invariantSeparatorsPath }.sortedBy { it.second }.toList()
        check(payload.none { (_, path) -> path.startsWith("app-resources/") || path.endsWith(".onnx") || path.endsWith(".onnx_data") }) {
            "Stale version directory or NLP model in Web app distribution"
        }
        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            DigestInputStream(file.inputStream(), digest).use { it.copyTo(OutputStream.nullOutputStream()) }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
        val assets = payload.map { (file, path) ->
            val destination = target.resolve(path)
            destination.parentFile.mkdirs()
            file.copyTo(destination, overwrite = true)
            if (path !in stableFiles && !path.endsWith(".user.js")) check(file.delete()) { "Cannot relocate Web resource: $path" }
            mapOf(
                "path" to path,
                "bytes" to destination.length(),
                "sha256" to sha256(destination),
                "group" to if (path.startsWith("voice-codec/") || path.startsWith("nlp/") || path.startsWith("export/")) "lazy" else "core",
            )
        }
        val manifest = target.resolve("app-assets.json")
        manifest.writeText(JsonOutput.toJson(mapOf(
            "schema" to 1, "versionName" to versionName.get(), "versionCode" to versionCode.get(),
            "buildId" to productionBuildId.get(), "base" to base, "entry" to productionBundleName.get(), "assets" to assets,
        )))
        val pointer = root.resolve("web-version.json")
        pointer.writeText(pointer.readText()
            .replace("@WEB_VERSION_NAME@", versionName.get())
            .replace("@WEB_VERSION_CODE@", versionCode.get().toString())
            .replace("@WEB_BUILD_ID@", productionBuildId.get())
            .replace("@WEB_RESOURCE_BASE@", base)
            .replace("@WEB_MANIFEST_SHA256@", sha256(manifest)))
        check(pointer.length() <= 2048) { "Web version pointer must remain small" }
    }
}
