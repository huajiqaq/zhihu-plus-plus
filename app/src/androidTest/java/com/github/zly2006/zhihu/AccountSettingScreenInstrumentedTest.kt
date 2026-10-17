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

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.zly2006.zhihu.navigation.Account
import com.github.zly2006.zhihu.test.MainActivityComposeRule
import com.github.zly2006.zhihu.test.resetAppPreferences
import com.github.zly2006.zhihu.test.setScreenContent
import com.github.zly2006.zhihu.ui.ACCOUNT_SETTINGS_APPEARANCE_TAG
import com.github.zly2006.zhihu.ui.AccountSettingScreen
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression for https://github.com/zly2006/zhihu-plus-plus/issues/259：
 * 账号面板作为主页头像弹层时，点击设置子页面应先关闭面板再导航，避免返回时闪回。
 */
@RunWith(AndroidJUnit4::class)
class AccountSettingScreenInstrumentedTest {
    @get:Rule
    val composeRule: MainActivityComposeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        composeRule.resetAppPreferences()
    }

    @Test
    fun subpageNavigationDismissesAccountSheetFirst() {
        var dismissed = false
        val navigator = composeRule.setScreenContent {
            AccountSettingScreen(
                innerPadding = PaddingValues(0.dp),
                onDismissRequest = { dismissed = true },
                refreshAccountProfileOnEnter = false,
                isActive = false,
            )
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag(ACCOUNT_SETTINGS_APPEARANCE_TAG).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(ACCOUNT_SETTINGS_APPEARANCE_TAG).performScrollTo().performClick()
        composeRule.waitForIdle()

        assertTrue("点击设置子页面应先关闭账号面板", dismissed)
        assertTrue(
            "点击设置子页面应导航到 AppearanceSettings，实际=${navigator.destinations}",
            navigator.destinations.any { it is Account.AppearanceSettings },
        )
    }
}
