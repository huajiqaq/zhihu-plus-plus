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

package com.github.zly2006.zhihu

import android.content.Context
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.zly2006.zhihu.navigation.Account
import com.github.zly2006.zhihu.navigation.Home
import com.github.zly2006.zhihu.test.MainActivityComposeRule
import com.github.zly2006.zhihu.test.resetAppPreferences
import com.github.zly2006.zhihu.test.setZhihuMainContent
import com.github.zly2006.zhihu.ui.HOME_CREATE_FAB_TAG
import com.github.zly2006.zhihu.ui.HOME_REFRESH_BUTTON_TAG
import com.github.zly2006.zhihu.ui.PREFERENCE_NAME
import com.github.zly2006.zhihu.ui.subscreens.BOTTOM_BAR_ITEMS_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.subscreens.START_DESTINATION_PREFERENCE_KEY
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Regression for https://github.com/zly2006/zhihu-plus-plus/issues/516 */
@RunWith(AndroidJUnit4::class)
class HomeCreateFabVisibilityInstrumentedTest {
    @get:Rule
    val composeRule: MainActivityComposeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        composeRule.resetAppPreferences()
        composeRule.activity
            .getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)
            .edit(commit = true) {
                putString(START_DESTINATION_PREFERENCE_KEY, Home.name)
                putStringSet(BOTTOM_BAR_ITEMS_PREFERENCE_KEY, linkedSetOf(Home.name, Account.name))
                putBoolean("duo3_nav_style", false)
                putBoolean("autoHideBottomBar", false)
                putBoolean("showRefreshFab", true)
            }
    }

    @Test
    fun createFabIsHiddenWhenPreferenceDisabled() {
        setCreateFabPreference(false)
        composeRule.setZhihuMainContent()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag(HOME_REFRESH_BUTTON_TAG).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(HOME_CREATE_FAB_TAG).assertDoesNotExist()
    }

    @Test
    fun createFabIsVisibleWhenPreferenceEnabled() {
        setCreateFabPreference(true)
        composeRule.setZhihuMainContent()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag(HOME_CREATE_FAB_TAG).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun setCreateFabPreference(enabled: Boolean) {
        composeRule.activity
            .getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)
            .edit(commit = true) {
                putBoolean("showCreateFab", enabled)
            }
    }
}
