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

package com.github.zly2006.zhihu.ui

import com.github.zly2006.zhihu.navigation.Account
import com.github.zly2006.zhihu.navigation.Daily
import com.github.zly2006.zhihu.navigation.Follow
import com.github.zly2006.zhihu.navigation.Home
import com.github.zly2006.zhihu.navigation.HotList
import com.github.zly2006.zhihu.navigation.MyCollections
import com.github.zly2006.zhihu.navigation.OnlineHistory
import com.github.zly2006.zhihu.platform.MapSettingsStore
import com.github.zly2006.zhihu.ui.subscreens.TIQIAN_MARKDOWN_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.subscreens.TIQIAN_MATH_FONT_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.subscreens.bottomBarItemOrderFromPreference
import com.github.zly2006.zhihu.ui.subscreens.defaultBottomBarSelectionKeys
import com.github.zly2006.zhihu.ui.subscreens.migrateLegacyReadingPreferences
import com.github.zly2006.zhihu.ui.subscreens.normalizeBottomBarItemOrder
import com.github.zly2006.zhihu.ui.subscreens.normalizeBottomBarSelection
import com.github.zly2006.zhihu.ui.subscreens.resolveValidStartDestinationKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ZhihuMainPreferencesTest {
    @Test
    fun legacyReadingPreferencesMigrateOnceWithoutOverwritingExplicitChoices() {
        val settings = MapSettingsStore().apply {
            putBoolean("duo3_tiqian_markdown", true)
            putString("duo3_tiqian_math_font", "stix")
        }
        migrateLegacyReadingPreferences(settings)
        assertTrue(settings.getBoolean(TIQIAN_MARKDOWN_PREFERENCE_KEY, false))
        assertEquals("stix", settings.getString(TIQIAN_MATH_FONT_PREFERENCE_KEY, ""))
        assertEquals(true, settings.allEntries()["duo3_tiqian_markdown"])
        assertEquals("stix", settings.allEntries()["duo3_tiqian_math_font"])

        settings.putBoolean(TIQIAN_MARKDOWN_PREFERENCE_KEY, false)
        settings.putString(TIQIAN_MATH_FONT_PREFERENCE_KEY, "lete")
        val explicitChoices = settings.allEntries()
        repeat(2) { migrateLegacyReadingPreferences(settings) }
        assertEquals(explicitChoices, settings.allEntries(), "正式 false/lete 及旧键都不能被再次迁移覆盖")

        val emptySettings = MapSettingsStore()
        migrateLegacyReadingPreferences(emptySettings)
        assertTrue(emptySettings.allEntries().isEmpty(), "没有旧偏好时不写默认值")
    }

    @Test
    fun defaultBottomBarSelectionUsesCompactMobileSet() {
        assertEquals(
            linkedSetOf(Home.name, Follow.name, Daily.name),
            defaultBottomBarSelectionKeys(),
        )
    }

    @Test
    fun desktopDefaultSelectionIncludesAllTopLevelDestinationsExceptAccount() {
        assertEquals(
            linkedSetOf(Home.name, Follow.name, HotList.name, Daily.name, OnlineHistory.name, MyCollections.name),
            defaultBottomBarSelectionKeys(maximumSelection = null),
        )
    }

    @Test
    fun normalizeBottomBarSelectionPreservesSelectionWithoutForcingAccount() {
        val selected = linkedSetOf(Home.name, Follow.name, HotList.name, Daily.name, OnlineHistory.name)
        val mobile = normalizeBottomBarSelection(selectedKeys = selected)
        assertEquals(linkedSetOf(Home.name, Follow.name, HotList.name, Daily.name), mobile)
        assertFalse(Account.name in mobile)
        assertEquals(Home.name, resolveValidStartDestinationKey(OnlineHistory.name, mobile.toList()))
        assertEquals(selected, normalizeBottomBarSelection(selectedKeys = selected, maximumSelection = null))
    }

    @Test
    fun normalizeBottomBarSelectionFillsToMinimumAndKeepsExplicitAccount() {
        val normalized = normalizeBottomBarSelection(
            selectedKeys = linkedSetOf(Home.name, Account.name),
            enforceMinimumSelection = true,
        )

        assertTrue(Home.name in normalized)
        assertTrue(Account.name in normalized)
        assertEquals(3, normalized.size)
    }

    @Test
    fun normalizeBottomBarSelectionKeepsAccountReachableWhenHomeIsAbsent() {
        val normalized = normalizeBottomBarSelection(
            selectedKeys = linkedSetOf(Follow.name, Daily.name, HotList.name),
        )

        assertTrue(Account.name in normalized)
        assertEquals(4, normalized.size)
    }

    @Test
    fun resolveValidStartDestinationFallsBackToFirstAvailableDestination() {
        assertEquals(
            Follow.name,
            resolveValidStartDestinationKey(
                preferredKey = HotList.name,
                availableKeysInOrder = listOf(Follow.name, Daily.name),
            ),
        )
    }

    @Test
    fun normalizeBottomBarSelectionAllowsCollectionsEntry() {
        val normalized = normalizeBottomBarSelection(
            selectedKeys = linkedSetOf(Home.name, HotList.name, MyCollections.name),
            enforceMinimumSelection = true,
        )

        assertTrue(MyCollections.name in normalized)
        assertEquals(3, normalized.size)
    }

    @Test
    fun normalizeBottomBarItemOrderKeepsPreferredOrderAndAppendsMissingSelectedItems() {
        val normalized = normalizeBottomBarItemOrder(
            preferredOrderKeys = listOf(HotList.name, MyCollections.name, HotList.name, "Unknown"),
            selectedKeys = linkedSetOf(Home.name, HotList.name, MyCollections.name, Daily.name),
        )

        assertEquals(
            listOf(HotList.name, MyCollections.name, Home.name, Daily.name),
            normalized,
        )
    }

    @Test
    fun bottomBarItemOrderFromPreferenceIgnoresUnselectedAndUnknownKeys() {
        val normalized = bottomBarItemOrderFromPreference(
            preferenceValue = "${MyCollections.name}, Unknown, ${HotList.name}, ${OnlineHistory.name}",
            selectedKeys = linkedSetOf(Home.name, HotList.name, MyCollections.name),
        )

        assertEquals(
            listOf(MyCollections.name, HotList.name, Home.name),
            normalized,
        )
    }
}
