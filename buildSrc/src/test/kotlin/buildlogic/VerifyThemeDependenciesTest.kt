package buildlogic

import org.gradle.testfixtures.ProjectBuilder
import org.junit.Test

class VerifyThemeDependenciesTest {
    private fun verify(full: Boolean, vararg modules: String) {
        val task = ProjectBuilder.builder().build().tasks.create("verifyTheme", VerifyThemeDependencies::class.java)
        task.requireMiuixTheme.set(full)
        task.resolvedModules.set(modules.toList())
        task.verify()
    }

    @Test
    fun liteKeepsNavigationGlassAndTheirPlatformDependencies() {
        verify(
            false,
            "top.yukonga.miuix.kmp:miuix-nav-android:0.9.4",
            "top.yukonga.miuix.kmp:miuix-blur-android:0.9.4",
            "top.yukonga.miuix.kmp:miuix-squircle-android:0.9.4",
            "top.yukonga.miuix.kmp:miuix-shader-android:0.9.4",
            "unrelated:miuix-ui:1.0",
        )
    }

    @Test
    fun fullKeepsTheWholeTheme() {
        verify(
            true,
            "top.yukonga.miuix.kmp:miuix-nav-android:0.9.4",
            "top.yukonga.miuix.kmp:miuix-blur-android:0.9.4",
            "top.yukonga.miuix.kmp:miuix-core-android:0.9.4",
            "top.yukonga.miuix.kmp:miuix-ui-android:0.9.4",
            "top.yukonga.miuix.kmp:miuix-preference-android:0.9.4",
        )
    }

    @Test(expected = IllegalStateException::class)
    fun liteRejectsARealThemeLeak() {
        verify(false, "top.yukonga.miuix.kmp:miuix-nav-android:0.9.4", "top.yukonga.miuix.kmp:miuix-blur-android:0.9.4", "top.yukonga.miuix.kmp:miuix-icons-android:0.9.4")
    }

    @Test(expected = IllegalStateException::class)
    fun fullRejectsAnIncompleteTheme() {
        verify(true, "top.yukonga.miuix.kmp:miuix-nav-android:0.9.4", "top.yukonga.miuix.kmp:miuix-blur-android:0.9.4", "top.yukonga.miuix.kmp:miuix-ui-android:0.9.4")
    }

    @Test(expected = IllegalStateException::class)
    fun anotherGroupCannotSupplyNavigation() {
        verify(false, "unrelated:miuix-nav:0.9.4", "top.yukonga.miuix.kmp:miuix-blur-android:0.9.4")
    }
}
