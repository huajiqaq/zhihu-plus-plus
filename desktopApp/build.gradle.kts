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

import buildlogic.desktopJavaPlatform
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.file.FileSystemOperations
import org.gradle.process.ExecOperations
import javax.inject.Inject
import org.gradle.jvm.tasks.Jar
import org.gradle.language.jvm.tasks.ProcessResources
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.Serializable
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.PosixFilePermissions

val appVersionName = property("app.versionName").toString()
val desktopPackageVersion = if (appVersionName.count { it == '.' } >= 2) appVersionName else "$appVersionName.0"

plugins {
    alias(libs.plugins.kotlin.jvm)
    id("zhihu.javafx")
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.plugin.compose)
    id("com.mikepenz.aboutlibraries.plugin")
    alias(libs.plugins.ktlint)
}

val desktopLicenseManifest = layout.buildDirectory.file("generated/aboutLibraries/aboutlibraries.json")

aboutLibraries {
    collect {
        all.set(true)
        // 许可证清单只包含安装包的实际运行依赖，不把 compileOnly 的平台类型算入。
        filterVariants.set(setOf("runtimeClasspath"))
    }
    export {
        outputFile.set(desktopLicenseManifest)
    }
}

val pdfFontResources = layout.buildDirectory.dir("generated/pdf-font-resources")
val preparePdfFonts =
    tasks.register<Exec>("preparePdfFonts") {
        val script = layout.projectDirectory.file("tools/prepare_pdf_fonts.py")
        val lock = layout.projectDirectory.file("pdf-fonts.lock.json")
        val cache = layout.buildDirectory.dir("pdf-font-tools")
        val python = providers.environmentVariable("ZHIHU_PYTHON").orElse("python")
        inputs.files(script, lock)
        inputs.property("pythonCommand", python)
        outputs.dir(pdfFontResources)
        localState.register(cache)
        commandLine(
            python.get(), "-I", script.asFile.absolutePath,
            "--lock", lock.asFile.absolutePath,
            "--cache", cache.get().asFile.absolutePath,
            "--output", pdfFontResources.get().asFile.absolutePath,
        )
    }

/** Maven 许可之外，原生编解码器和构建生成字体保留各自的原许可与来源。 */
abstract class MergeDesktopLicenses : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val gradleManifest: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val supplementalLicenses: ConfigurableFileCollection

    @get:Input
    abstract val platform: Property<String>

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
        for (directory in supplementalLicenses.files.sortedBy { it.name }) {
            @Suppress("UNCHECKED_CAST")
            val sources = parser.parse(directory.resolve("sources.json")) as Map<String, Any>
            for (entry in sources.getValue("components") as List<*>) {
                @Suppress("UNCHECKED_CAST")
                val component = entry as Map<String, Any>
                val platforms = component["platforms"] as? List<*>
                if (platforms != null && platform.get().substringBefore('-') !in platforms) continue
                val id = component.getValue("id").toString()
                val licenseId = "${directory.name}-$id"
                val content = (component.getValue("licenseFiles") as List<*>).joinToString("\n\n") { path ->
                    val file = directory.resolve(path.toString()).canonicalFile
                    require(file.toPath().startsWith(directory.canonicalFile.toPath())) { "Invalid license path: $path" }
                    file.readText()
                }
                licenses[licenseId] = mapOf("name" to component.getValue("license"), "url" to component.getValue("source"), "content" to content, "hash" to licenseId)
                libraries.removeAll { it["uniqueId"] == id }
                libraries.add(
                    mapOf(
                        "uniqueId" to id,
                        "name" to component.getValue("name"),
                        "artifactVersion" to component.getValue("version"),
                        "website" to component.getValue("source"),
                        "licenses" to listOf(licenseId),
                    ),
                )
            }
        }
        outputFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(JsonOutput.toJson(manifest))
        }
    }
}
val mergeDesktopLicenses =
    tasks.register<MergeDesktopLicenses>("mergeDesktopLicenses") {
        dependsOn(tasks.named("exportLibraryDefinitions"), preparePdfFonts)
        gradleManifest.set(desktopLicenseManifest)
        supplementalLicenses.from(layout.projectDirectory.dir("src/main/resources/META-INF/licenses/bytedeco"))
        supplementalLicenses.from(layout.projectDirectory.dir("src/main/resources/META-INF/licenses/onnxruntime"))
        supplementalLicenses.from(pdfFontResources.map { it.dir("META-INF/licenses/noto-sans-sc") })
        platform.set(desktopJavaPlatform())
        outputFile.set(layout.buildDirectory.file("generated/desktop-licenses/aboutlibraries.json"))
    }
tasks.named<ProcessResources>("processResources") {
    from(mergeDesktopLicenses.flatMap { it.outputFile })
    from(files(pdfFontResources).builtBy(preparePdfFonts))
    if (rootProject.extensions.extraProperties.has("zhihuUpstreamInputs")) {
        from(File(rootProject.extensions.extraProperties["zhihuUpstreamInputs"] as String, "assets"))
    }
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
    jvmToolchain(libs.versions.jvm.get().toInt())
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget(libs.versions.jvm.get())
    }
}

configurations.named("runtimeClasspath") {
    // FontBox 的 JCL 调用走桌面已有的 SLF4J API，不打包 Servlet、LogKit 等可选适配器。
    resolutionStrategy.dependencySubstitution {
        substitute(module("commons-logging:commons-logging")).using(module("org.slf4j:jcl-over-slf4j:2.0.20"))
    }
}

val desktopNativePlatform = desktopJavaPlatform()
val desktopJdkHome = providers.systemProperty("java.home")
val proguardProvidedLibraries = configurations.create("proguardProvidedLibraries") {
    isCanBeConsumed = false
    isTransitive = false
}
configurations.named("compileOnly") { extendsFrom(proguardProvidedLibraries) }

/** provided 注解只用于编译和 Proguard 符号解析，不进入安装包的运行时依赖。 */
abstract class GenerateProguardProvidedLibraries : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    abstract val libraries: ConfigurableFileCollection

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun generate() {
        outputFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(libraries.files.sortedBy { it.name }.joinToString("\n", postfix = "\n") { "-libraryjars '${it.invariantSeparatorsPath}'" })
        }
    }
}
val generateProguardProvidedLibraries =
    tasks.register<GenerateProguardProvidedLibraries>("generateProguardProvidedLibraries") {
        libraries.from(proguardProvidedLibraries)
        outputFile.set(layout.buildDirectory.file("generated/proguard/provided-libraries.pro"))
    }

dependencies {
    // JavaCPP 1.5.14 POM 的 provided 依赖，包含 ConsumerType 和 Export；不引入 Maven 工具。
    add(proguardProvidedLibraries.name, "org.osgi:osgi.annotation:8.1.0")
    implementation(projects.shared)
    implementation(project(":shared-feature-settings"))
    runtimeOnly(libs.dbus.java.transport.native.unixsocket)
    runtimeOnly(variantOf(libs.javacpp) { classifier(desktopNativePlatform.get()) }) { isTransitive = false }
    runtimeOnly(variantOf(libs.ffmpeg) { classifier(desktopNativePlatform.get()) }) { isTransitive = false }
    implementation(project(":shared-ui-foundation"))
    implementation(project(":shared-ui-components"))
    implementation(libs.jb.compose.ui)
    implementation(libs.jb.compose.components.resources)
}

/** 对副本进行本地签名，不修改 CMake/Swift 输出或其它应用的文件。 */
abstract class SignMacosDesktopHelpers @Inject constructor(
    private val fileSystem: FileSystemOperations,
    private val execOperations: ExecOperations,
) : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val unsignedResources: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val unsignedShareLibrary: RegularFileProperty

    @get:OutputDirectory
    abstract val signedResources: DirectoryProperty

    @get:OutputDirectory
    abstract val signedShareLibrary: DirectoryProperty

    @TaskAction
    fun sign() {
        val resources = signedResources.get().asFile
        val share = signedShareLibrary.get().asFile
        fileSystem.sync {
            from(unsignedResources)
            into(resources)
            filePermissions { unix("rwxr-xr-x") }
        }
        fileSystem.sync {
            from(unsignedShareLibrary)
            into(share)
            filePermissions { unix("rwxr-xr-x") }
        }
        val binaries = listOf(
            resources.resolve("common/reading/zhihu-reading-host"),
            resources.resolve("common/native-notifications/libZhihuPortableNotifications.dylib"),
            share.resolve("libZhihuFileShare.dylib"),
        )
        binaries.forEach { binary ->
            check(binary.isFile) { "Missing native binary: $binary" }
            execOperations.exec { commandLine("/usr/bin/codesign", "--force", "--sign", "-", "--timestamp=none", binary.absolutePath) }
            execOperations.exec { commandLine("/usr/bin/codesign", "--verify", "--strict", binary.absolutePath) }
        }
    }
}

val prepareDesktopHelpers =
    tasks.register<Sync>("prepareDesktopHelpers") {
        filePermissions { unix("rwxr-xr-x") }
        into(layout.buildDirectory.dir("generated/app-resources"))
    }
val desktopAppResources = objects.directoryProperty().convention(prepareDesktopHelpers.flatMap { it.destinationDirectory })
val desktopHostOs = providers.systemProperty("os.name").get()
if (desktopHostOs.startsWith("Windows", ignoreCase = true) || desktopHostOs.startsWith("Mac", ignoreCase = true)) {
    val windows = desktopHostOs.startsWith("Windows", ignoreCase = true)
    val nativeSource = layout.projectDirectory.dir("native-share")
    val nativeBuild = layout.buildDirectory.dir(desktopNativePlatform.map { "native-share/$it" })
    val nativeOutput = nativeBuild.map { it.dir("out") }
    val nativeFileName = if (windows) "ZhihuFileShare.dll" else "libZhihuFileShare.dylib"
    val cmake = providers.environmentVariable("CMAKE_COMMAND").orElse("cmake")
    // 切换 JDK 后用 --fresh 清除 FindJNI 缓存，避免继续链接旧 JDK 的头文件和 AWT。
    val jdkHome = desktopJdkHome
    val configureDesktopFileShare =
        tasks.register<Exec>("configureDesktopFileShare") {
            inputs.file(nativeSource.file("CMakeLists.txt"))
            inputs.property("jdkHome", jdkHome)
            inputs.property("platform", desktopNativePlatform)
            outputs.file(nativeBuild.map { it.file("CMakeCache.txt") })
            environment("JAVA_HOME", jdkHome.get())
            commandLine(
                cmake.get(), "--fresh", "-S", nativeSource.asFile.absolutePath, "-B", nativeBuild.get().asFile.absolutePath,
                "-DCMAKE_BUILD_TYPE=Release", "-DCMAKE_RUNTIME_OUTPUT_DIRECTORY_RELEASE=${nativeOutput.get().asFile.absolutePath}",
                "-DCMAKE_LIBRARY_OUTPUT_DIRECTORY_RELEASE=${nativeOutput.get().asFile.absolutePath}",
                "-DCMAKE_RUNTIME_OUTPUT_DIRECTORY=${nativeOutput.get().asFile.absolutePath}",
                "-DCMAKE_LIBRARY_OUTPUT_DIRECTORY=${nativeOutput.get().asFile.absolutePath}",
            )
            if (windows) {
                args("-A", "x64", "-DCMAKE_MSVC_RUNTIME_LIBRARY=MultiThreaded")
            } else {
                args("-DCMAKE_OSX_ARCHITECTURES=${if (desktopNativePlatform.get().endsWith("arm64")) "arm64" else "x86_64"}", "-DCMAKE_OSX_DEPLOYMENT_TARGET=12.0")
            }
        }
    val buildDesktopFileShare =
        tasks.register<Exec>("buildDesktopFileShare") {
            dependsOn(configureDesktopFileShare)
            inputs.dir(nativeSource)
            inputs.file(nativeBuild.map { it.file("CMakeCache.txt") })
            outputs.file(nativeOutput.map { it.file(nativeFileName) })
            environment("JAVA_HOME", jdkHome.get())
            commandLine(cmake.get(), "--build", nativeBuild.get().asFile.absolutePath, "--config", "Release", "--target", "ZhihuFileShare")
            doLast { outputs.files.forEach { file -> check(file.isFile) { "Missing file-share library: $file" } } }
        }
    if (windows) {
        tasks.named<ProcessResources>("processResources") {
            dependsOn(buildDesktopFileShare)
            from(nativeOutput.map { it.file(nativeFileName) }) { into("native-share") }
        }
    } else {
        val signMacosDesktopHelpers =
            tasks.register<SignMacosDesktopHelpers>("signMacosDesktopHelpers") {
                dependsOn(buildDesktopFileShare)
                unsignedResources.set(prepareDesktopHelpers.flatMap { it.destinationDirectory })
                unsignedShareLibrary.set(nativeOutput.map { it.file(nativeFileName) })
                signedResources.set(layout.buildDirectory.dir("generated/signed-app-resources"))
                signedShareLibrary.set(layout.buildDirectory.dir("generated/signed-native-share"))
            }
        desktopAppResources.set(signMacosDesktopHelpers.flatMap { it.signedResources })
        tasks.named<ProcessResources>("processResources") {
            from(signMacosDesktopHelpers.flatMap { it.signedShareLibrary }) { into("native-share") }
        }
    }
    val notificationsSource = layout.projectDirectory.dir("native-notifications-portable")
    val notificationsBuild = layout.buildDirectory.dir(desktopNativePlatform.map { "native-notifications/$it" })
    val notificationsOutput = notificationsBuild.map { it.dir("out") }
    val notificationsFileNames =
        if (windows) listOf("ZhihuPortableNotifications.dll", "ZhihuNotificationActivator.exe") else listOf("libZhihuPortableNotifications.dylib")
    val configureDesktopNotifications =
        tasks.register<Exec>("configureDesktopNotifications") {
            inputs.file(notificationsSource.file("CMakeLists.txt"))
            inputs.property("jdkHome", jdkHome)
            inputs.property("platform", desktopNativePlatform)
            outputs.file(notificationsBuild.map { it.file("CMakeCache.txt") })
            environment("JAVA_HOME", jdkHome.get())
            commandLine(
                cmake.get(), "--fresh", "-S", notificationsSource.asFile.absolutePath, "-B", notificationsBuild.get().asFile.absolutePath,
                "-DCMAKE_BUILD_TYPE=Release", "-DCMAKE_RUNTIME_OUTPUT_DIRECTORY_RELEASE=${notificationsOutput.get().asFile.absolutePath}",
                "-DCMAKE_LIBRARY_OUTPUT_DIRECTORY_RELEASE=${notificationsOutput.get().asFile.absolutePath}",
                "-DCMAKE_RUNTIME_OUTPUT_DIRECTORY=${notificationsOutput.get().asFile.absolutePath}",
                "-DCMAKE_LIBRARY_OUTPUT_DIRECTORY=${notificationsOutput.get().asFile.absolutePath}",
            )
            if (windows) {
                args("-A", "x64")
            } else {
                args("-DCMAKE_OSX_ARCHITECTURES=${if (desktopNativePlatform.get().endsWith("arm64")) "arm64" else "x86_64"}", "-DCMAKE_OSX_DEPLOYMENT_TARGET=12.0")
            }
        }
    val buildDesktopNotifications =
        tasks.register<Exec>("buildDesktopNotifications") {
            dependsOn(configureDesktopNotifications)
            inputs.dir(notificationsSource)
            inputs.file(notificationsBuild.map { it.file("CMakeCache.txt") })
            outputs.files(notificationsFileNames.map { name -> notificationsOutput.map { it.file(name) } })
            environment("JAVA_HOME", jdkHome.get())
            commandLine(cmake.get(), "--build", notificationsBuild.get().asFile.absolutePath, "--config", "Release", "--target", "ZhihuPortableNotifications")
            if (windows) args("ZhihuNotificationActivator")
            doLast { outputs.files.forEach { file -> check(file.isFile) { "Missing notification binary: $file" } } }
        }
    prepareDesktopHelpers.configure {
        dependsOn(buildDesktopNotifications)
        from(notificationsOutput) {
            include(notificationsFileNames)
            into("common/native-notifications")
        }
    }
}

if (desktopHostOs.startsWith("Windows", ignoreCase = true) || desktopHostOs.startsWith("Mac", ignoreCase = true) || desktopHostOs.startsWith("Linux", ignoreCase = true)) {
    val readingHost = project(":shared-reading").layout.buildDirectory.file(
        when {
            desktopHostOs.startsWith("Windows", ignoreCase = true) -> "reading-host/windows/out/zhihu-reading-host.exe"
            desktopHostOs.startsWith("Mac", ignoreCase = true) -> "reading-host/macos/zhihu-reading-host"
            else -> "reading-host/linux/out/zhihu-reading-host"
        },
    )
    prepareDesktopHelpers.configure {
        dependsOn(":shared-reading:buildReadingHost")
        from(readingHost) { into("common/reading") }
        doFirst { check(readingHost.get().asFile.isFile) { "Missing reading helper: ${readingHost.get().asFile}" } }
    }
    tasks.named<ProcessResources>("processResources") {
        // jar 也编译系统朗读；CI 不必运行应用即可检查宿主 API。
        dependsOn(prepareDesktopHelpers)
    }
    compose.desktop.application.nativeDistributions.appResourcesRootDir.set(desktopAppResources)
    tasks.withType<JavaExec>().configureEach {
        if (name == "run" || name == "runRelease") {
            inputs.dir(desktopAppResources)
            val helperName = readingHost.get().asFile.name
            systemProperty("zhihu.reading.helper", desktopAppResources.get().file("common/reading/$helperName").asFile.absolutePath)
        }
    }
}

/** Compose 的 jpackage 输入复制不保留执行位，在最终 app-image 上恢复自产 helper 的权限。 */
class NormalizePackagedReadingPermissions(private val macos: Boolean) : Action<Task>, Serializable {
    override fun execute(task: Task) {
        val packaging = task as AbstractJPackageTask
        val imageName = packaging.packageName.get() + if (macos) ".app" else ""
        val resourcesPath = if (macos) "Contents/app/resources" else "lib/app/resources"
        val helper = packaging.destinationDir.get().asFile.resolve("$imageName/$resourcesPath/reading/zhihu-reading-host").toPath()
        check(Files.isRegularFile(helper, LinkOption.NOFOLLOW_LINKS)) { "Missing packaged reading helper: $helper" }
        val permissions = PosixFilePermissions.fromString("rwxr-xr-x")
        Files.setPosixFilePermissions(helper, permissions)
        check(Files.getPosixFilePermissions(helper) == permissions && Files.isExecutable(helper)) { "Reading helper is not executable after packaging: $helper" }
    }
}
if (desktopHostOs.startsWith("Linux", ignoreCase = true) || desktopHostOs.startsWith("Mac", ignoreCase = true)) {
    val macos = desktopHostOs.startsWith("Mac", ignoreCase = true)
    tasks.withType<AbstractJPackageTask>().configureEach {
        if (targetFormat == TargetFormat.AppImage) {
            doLast(NormalizePackagedReadingPermissions(macos))
        }
    }
}

compose.resources {
    packageOfResClass = "com.github.zly2006.zhihu.desktop.resources"
}

fun currentDesktopNativeExcludes(platform: String): List<String> {
    val osName = platform.substringBefore('-')
    val isArm64 = platform.endsWith("-arm64")
    val sqliteTarget =
        when {
            osName.contains("mac") && isArm64 -> "osx_arm64"
            osName.contains("mac") -> "osx_x64"
            osName.contains("win") -> "windows_x64"
            osName.contains("linux") && isArm64 -> "linux_arm64"
            else -> "linux_x64"
        }
    val skikoTarget =
        when {
            osName.contains("mac") && isArm64 -> "macos-arm64"
            osName.contains("mac") -> "macos-x64"
            osName.contains("win") -> "windows-x64"
            osName.contains("linux") && isArm64 -> "linux-arm64"
            else -> "linux-x64"
        }
    val sqliteNatives =
        listOf(
            "natives/linux_arm64/**",
            "natives/linux_x64/**",
            "natives/osx_arm64/**",
            "natives/osx_x64/**",
            "natives/windows_x64/**",
        )
    val skikoNatives =
        listOf(
            "libskiko-linux-arm64.so",
            "libskiko-linux-x64.so",
            "libskiko-macos-arm64.dylib",
            "libskiko-macos-x64.dylib",
            "skiko-windows-x64.dll",
        )

    return sqliteNatives.filterNot { it == "natives/$sqliteTarget/**" } +
        skikoNatives.filterNot { it.contains(skikoTarget) }
}

tasks.withType<Jar>().configureEach {
    if (name == "packageReleaseUberJarForCurrentOS") {
        exclude(currentDesktopNativeExcludes(desktopNativePlatform.get()))
    }
}

compose.desktop {
    application {
        mainClass = "com.github.zly2006.zhihu.desktop.MainKt"
        jvmArgs("--enable-native-access=ALL-UNNAMED")

        buildTypes.release.proguard {
            isEnabled.set(true)
            optimize.set(false)
            configurationFiles.from(project.file("proguard-release.pro"), generateProguardProvidedLibraries.flatMap { it.outputFile })
        }

        nativeDistributions {
            // 保留 JavaFX WebView 的 JRE 字体后备，不依赖用户系统是否安装相同字体。
            stripJreFonts = false
            // AppImage 由 CI 封装 jpackage app-image；macOS 使用 macosApp 的 Native 构建。
            targetFormats(TargetFormat.AppImage, TargetFormat.Msi)
            packageName = "Zhihu++"
            packageVersion = desktopPackageVersion
            description = "Free and ad-free third-party Zhihu client"
            vendor = "zly2006"

            // jlink 需保留 WebView JS/JDBC/HTTP/中文字符集所用模块；checkRuntime 不检查缺失模块。
            modules(
                "java.management",
                "java.naming",
                "java.net.http",
                "java.prefs",
                "java.security.jgss",
                "java.sql",
                "java.xml",
                "jdk.charsets",
                "jdk.jsobject",
                "jdk.net",
                "jdk.security.auth",
                "jdk.unsupported",
                "jdk.xml.dom",
                "jdk.zipfs",
            )

            macOS {
                // JVM ONNX Runtime 1.23.2 的 Mach-O 最低为 13.4，高于 JDK/JavaFX 的 11。
                minimumSystemVersion = "13.4"
                val buildVersion = providers.gradleProperty("app.versionCode").get().toInt().also { require(it > 0) }
                // jpackage 要求正首段版本；仅 Mac 以构建号打包，末尾 plist key 保留应用展示版本。
                packageVersion = "$buildVersion.0.0"
                packageBuildVersion = buildVersion.toString()
                infoPlist {
                    extraKeysRawXml = """
                        <key>CFBundleShortVersionString</key><string>$appVersionName</string>
                        <key>NSMicrophoneUsageDescription</key><string>录制语音输入并转换为文字。</string>
                    """.trimIndent()
                }
            }
            linux {
                iconFile.set(project.file("src/main/composeResources/drawable/desktop_icon.png"))
            }
            windows {
                iconFile.set(project.file("desktop-icon.ico"))
                menu = true
                menuGroup = "Zhihu++"
                shortcut = true
                upgradeUuid = "84FED2C6-8F40-4FDA-B84A-616602D7A888"
            }
        }
    }
}
