package buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

abstract class VerifyThemeDependencies : DefaultTask() {
    @get:Input
    abstract val resolvedModules: ListProperty<String>

    @get:Input
    abstract val requireMiuixTheme: Property<Boolean>

    @TaskAction
    fun verify() {
        val artifacts = resolvedModules.get().filter { it.startsWith("top.yukonga.miuix.kmp:") }
        val platformSuffix = Regex("-(android|desktop|jvm|iosarm64|iossimulatorarm64|macosarm64)$")
        val modules = artifacts.map { it.substringAfter(':').substringBefore(':').replace(platformSuffix, "") }.toSet()
        check("miuix-nav" in modules) { "Variant must retain miuix-nav; resolved: $artifacts" }
        check("miuix-blur" in modules) { "Variant must retain the shared glass implementation; resolved: $artifacts" }
        val themeModules = setOf("miuix-core", "miuix-ui", "miuix-preference")
        if (requireMiuixTheme.get()) {
            val missing = themeModules - modules
            check(missing.isEmpty()) { "Full must contain the complete Miuix theme; missing: $missing; resolved: $artifacts" }
        } else {
            /** material 提供玻璃描边与着色器，不包含 Miuix 主题控件。 */
            val allowed = setOf("miuix-nav", "miuix-blur", "miuix-squircle", "miuix-material")
            val leaked = modules - allowed
            check(leaked.isEmpty()) { "Lite must not contain Miuix theme modules: $leaked; resolved: $artifacts" }
        }
    }
}
