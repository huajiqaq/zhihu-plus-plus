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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.zly2006.zhihu.data.MOBILE_NOTIFICATION_MESSAGE_URL
import com.github.zly2006.zhihu.test.MainActivityComposeRule
import com.github.zly2006.zhihu.test.ZhihuMockApi
import com.github.zly2006.zhihu.test.resetAppPreferences
import com.github.zly2006.zhihu.test.setScreenContent
import com.github.zly2006.zhihu.ui.HOME_ACCOUNT_BADGE_TAG
import com.github.zly2006.zhihu.ui.HOME_ACCOUNT_BUTTON_TAG
import com.github.zly2006.zhihu.ui.HomeScreen
import com.github.zly2006.zhihu.ui.PREFERENCE_NAME
import io.ktor.http.HttpMethod
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.ceil
import kotlin.math.floor

@RunWith(AndroidJUnit4::class)
class HomeAccountBadgeInstrumentedTest {
    @get:Rule
    val composeRule: MainActivityComposeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        composeRule.resetAppPreferences(forceMock = true)
        ZhihuMockApi.mockJson(
            method = HttpMethod.Get,
            url = "$MOBILE_NOTIFICATION_MESSAGE_URL?limit=20",
            body =
                """
                {
                  "head": [{"detail_title": "评论转发@", "unread_count": 2}],
                  "column_head": [{"title": "邀请回答", "unread_count": 3}],
                  "data": [{"id": "messages", "unread_count": 4}],
                  "unread": {
                    "message": {"count": 9, "show_count": true}
                  }
                }
                """.trimIndent(),
        )
        composeRule.activity.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE).edit(commit = true) {
            putBoolean("showRefreshFab", false)
        }
        composeRule.setScreenContent {
            HomeScreen(
                scrollToTopTrigger = 0,
                innerPadding = PaddingValues(),
            )
        }
    }

    @Test
    fun unreadBadgeStaysInsideAccountEntry() {
        composeRule.waitUntil("等待未读数量请求", timeoutMillis = 5_000) {
            ZhihuMockApi.requestCount(HttpMethod.Get, "$MOBILE_NOTIFICATION_MESSAGE_URL?limit=20") > 0
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(HOME_ACCOUNT_BUTTON_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(HOME_ACCOUNT_BADGE_TAG, useUnmergedTree = true).assertIsDisplayed()

        val entryBounds = composeRule
            .onNodeWithTag(HOME_ACCOUNT_BUTTON_TAG)
            .fetchSemanticsNode()
            .boundsInRoot
        val badgeBounds = composeRule
            .onNodeWithTag(HOME_ACCOUNT_BADGE_TAG, useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
        assertTrue(
            "未读角标必须位于账号入口内：角标=$badgeBounds 入口=$entryBounds",
            badgeBounds.left >= entryBounds.left &&
                badgeBounds.top >= entryBounds.top &&
                badgeBounds.right <= entryBounds.right &&
                badgeBounds.bottom <= entryBounds.bottom,
        )
        val pixels = composeRule.onRoot().captureToImage().toPixelMap()
        val left = floor(badgeBounds.left).toInt().coerceIn(0, pixels.width - 1)
        val right = (ceil(badgeBounds.right).toInt() - 1).coerceIn(left, pixels.width - 1)
        val top = floor(badgeBounds.top).toInt().coerceIn(0, pixels.height - 1)
        val bottom = (ceil(badgeBounds.bottom).toInt() - 1).coerceIn(top, pixels.height - 1)
        val centerX = (left + right) / 2
        val centerY = (top + bottom) / 2
        val foreground = { x: Int, y: Int -> isBadgeForeground(pixels[x, y]) }
        assertTrue("角标填充必须可见", foreground(centerX, centerY))
        assertTrue("角标上边缘必须绘制", foreground(centerX, top))
        assertTrue("角标下边缘必须绘制", foreground(centerX, bottom))
        assertTrue("角标左边缘必须绘制", foreground(left, centerY))
        assertTrue("角标右边缘必须绘制", foreground(right, centerY))
        assertTrue("角标左上角必须保持圆角", !foreground(left, top))
        assertTrue("角标右上角必须保持圆角", !foreground(right, top))
    }

    @Test
    fun returningHomeRechecksTheServerUnreadTotal() {
        val url = "$MOBILE_NOTIFICATION_MESSAGE_URL?limit=20"
        val badge = composeRule.onAllNodesWithTag(HOME_ACCOUNT_BADGE_TAG, useUnmergedTree = true)
        composeRule.waitUntil(timeoutMillis = 5_000) { badge.fetchSemanticsNodes().isNotEmpty() }
        val before = ZhihuMockApi.requestCount(HttpMethod.Get, url)
        val scenario = composeRule.activityRule.scenario
        scenario.moveToState(Lifecycle.State.STARTED)
        try {
            ZhihuMockApi.mockJson(HttpMethod.Get, url, """{"head":[{"detail_title":"评论转发@","unread_count":2}],"unread":{"message":{"count":0}}}""")
        } finally {
            scenario.moveToState(Lifecycle.State.RESUMED)
        }
        composeRule.waitUntil("首页恢复后应按服务端总数清掉角标，而不是相加分类数", timeoutMillis = 5_000) {
            ZhihuMockApi.requestCount(HttpMethod.Get, url) == before + 1 && badge.fetchSemanticsNodes().isEmpty()
        }
    }

    private fun isBadgeForeground(color: Color): Boolean =
        color.alpha > 0.5f &&
            color.red > color.green + 0.12f &&
            color.red > color.blue + 0.12f
}
