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

package com.github.zly2006.zhihu.viewmodel

import com.github.zly2006.zhihu.data.AigcVoteVoter
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.platform.UserMessageSink
import com.github.zly2006.zhihu.platform.isAigcVoteSupported
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ArticleViewModelAigcUnsupportedTest {
    @Test
    fun desktopRejectsSyncAndSubmitButStatusStillShowsVoterName() {
        assertFalse(isAigcVoteSupported)
        val messages = mutableListOf<String>()
        val model = ArticleViewModel(
            Article(type = ArticleType.Answer, id = 601L),
            httpClient = null,
            userMessages = object : UserMessageSink {
                override fun showShortMessage(message: String) {
                    messages += message
                }
            },
        ).apply { content = "<p>Synthetic content</p>" }
        val environment = object : AigcVoteEnvironment {
            override fun isAigcVoteEnabled() = true

            override fun aigcVoteVoter() = AigcVoteVoter("fixture-voter", "合成投票人")

            override fun aigcVoteHttpClient(): Nothing = error("Unsupported platform must not obtain an HTTP client")
        }

        model.loadAigcFlagStatus(environment)
        assertEquals("合成投票人", model.aigcVoterName)
        assertFalse(model.aigcVoteAvailable)
        assertFalse(model.aigcVoteLoading)
        assertNull(model.aigcVoteError)

        model.syncAigcReadEventIfEligible(environment)
        assertFalse(model.aigcVoteAvailable)
        assertNull(model.aigcVoteError)
        assertEquals(0, model.aigcVoteCredit)
        assertEquals(0, model.aigcVoteProgress)
        assertEquals(5, model.aigcVoteCap)

        model.submitAigcFlag(environment)
        assertFalse(model.aigcVoteAvailable)
        assertFalse(model.aigcVoteLoading)
        assertFalse(model.aigcFlagged)
        assertEquals(0, model.aigcSupportVoterCount)
        assertEquals("未配置 AIGC 投票服务", model.aigcVoteError)
        assertEquals(listOf("未配置 AIGC 投票服务"), messages)
    }
}
