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

import org.gradle.testfixtures.ProjectBuilder
import org.junit.Test

class VerifyThemeDependenciesTest {
    private fun verify(full: Boolean, vararg modules: String) {
        val task = ProjectBuilder
            .builder()
            .build()
            .tasks
            .create("verifyTheme", VerifyThemeDependencies::class.java)
        task.requireMiuixTheme.set(full)
        task.resolvedModules.set(modules.toList())
        task.verify()
    }

    @Test
    fun liteKeepsNavigationGlassAndTheirPlatformDependencies() {
        verify(
            false,
            "top.yukonga.miuix.kmp:miuix-nav-android:0.9.5-beta03",
            "top.yukonga.miuix.kmp:miuix-blur-android:0.9.5-beta03",
            "top.yukonga.miuix.kmp:miuix-squircle-android:0.9.5-beta03",
            "top.yukonga.miuix.kmp:miuix-material-android:0.9.5-beta03",
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
