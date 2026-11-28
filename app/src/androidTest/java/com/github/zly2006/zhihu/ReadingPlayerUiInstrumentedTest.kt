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
import android.content.Intent
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.zly2006.zhihu.navigation.Account
import com.github.zly2006.zhihu.navigation.AppNavHost
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.Home
import com.github.zly2006.zhihu.reading.AndroidReadingPlayerBridge
import com.github.zly2006.zhihu.reading.ContentReadingService
import com.github.zly2006.zhihu.reading.ReadingContentType
import com.github.zly2006.zhihu.reading.ReadingPlaybackStatus
import com.github.zly2006.zhihu.reading.ReadingPlayerState
import com.github.zly2006.zhihu.reading.ReadingQueueItem
import com.github.zly2006.zhihu.test.MainActivityComposeRule
import com.github.zly2006.zhihu.test.resetAppPreferences
import com.github.zly2006.zhihu.test.setZhihuMainContent
import com.github.zly2006.zhihu.ui.PREFERENCE_NAME
import com.github.zly2006.zhihu.ui.components.READING_PLAYER_BAR_TAG
import com.github.zly2006.zhihu.ui.subscreens.BOTTOM_BAR_ITEMS_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.subscreens.START_DESTINATION_PREFERENCE_KEY
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReadingPlayerUiInstrumentedTest {
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
                putBoolean("autoHideBottomBar", false)
                putBoolean("showRefreshFab", true)
            }
        AndroidReadingPlayerBridge.publish(PLAYER_STATE)
    }

    @After
    fun tearDown() {
        composeRule.activity.stopService(Intent(composeRule.activity, ContentReadingService::class.java))
        AndroidReadingPlayerBridge.publish(ReadingPlayerState())
    }

    @Test
    fun changingThePlayingItemReplacesTheVisibleReadingDetail() {
        lateinit var navHost: AppNavHost
        composeRule.setZhihuMainContent { navHost = it }
        composeRule.runOnIdle {
            navHost.open(Article(type = ArticleType.Answer, id = PLAYER_STATE.queue.first().id))
        }
        composeRule.waitForTag(READING_PLAYER_BAR_TAG)

        AndroidReadingPlayerBridge.publish(PLAYER_STATE.copy(currentIndex = 1))

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.activity.history.history
                .firstOrNull() == Article(type = ArticleType.Answer, id = PLAYER_STATE.queue[1].id)
        }
        assertEquals(
            Article(type = ArticleType.Answer, id = PLAYER_STATE.queue[1].id),
            composeRule.activity.history.history
                .first(),
        )
    }

    private fun MainActivityComposeRule.waitForTag(tag: String) {
        waitUntil(timeoutMillis = 5_000) {
            onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        val PLAYER_STATE = ReadingPlayerState(
            status = ReadingPlaybackStatus.Playing,
            queue = listOf(
                ReadingQueueItem(
                    contentType = ReadingContentType.Answer,
                    id = 1,
                    title = "第一条离线回答",
                    author = "作者一",
                ),
                ReadingQueueItem(
                    contentType = ReadingContentType.Answer,
                    id = 2,
                    title = "第二条离线回答",
                    author = "作者二",
                ),
            ),
            currentIndex = 0,
        )
    }
}
