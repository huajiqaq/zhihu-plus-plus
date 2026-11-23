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

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.zly2006.zhihu.navigation.Person
import com.github.zly2006.zhihu.test.MainActivityComposeRule
import com.github.zly2006.zhihu.test.RecordingNavigator
import com.github.zly2006.zhihu.test.performHorizontalSwipeCycle
import com.github.zly2006.zhihu.test.performVerticalSwipeCycle
import com.github.zly2006.zhihu.test.resetAppPreferences
import com.github.zly2006.zhihu.test.setScreenContent
import com.github.zly2006.zhihu.ui.BlocklistSettingsScreen
import com.github.zly2006.zhihu.ui.BlocklistSettingsTestTags
import com.github.zly2006.zhihu.viewmodel.filter.BlockedQuestionAuthor
import com.github.zly2006.zhihu.viewmodel.filter.getContentFilterDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BlocklistSettingsScreenInstrumentedTest {
    @get:Rule
    val composeRule: MainActivityComposeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        composeRule.resetAppPreferences()
        runBlocking {
            getContentFilterDatabase(composeRule.activity).blockedQuestionAuthorDao().apply {
                clearAllUsers()
                seededQuestionAuthors().forEach { insertUser(it) }
            }
        }
    }

    /**
     * 提问者屏蔽页须支持导航、持久化增删及确认对话框；行为依据：https://github.com/zly2006/zhihu-plus-plus/issues/403
     */
    @Test
    fun questionAuthorTabSupportsNavigationDeleteClearAndDialogPathsOffline() {
        /*
         * 预期行为：
         * 1. 切换到提问者标签后应显示预置列表，往返滑动后条目仍可见，添加悬浮按钮仍显示。
         * 2. 点击条目应携带预置的 id、urlToken 和显示名称，导航到提问者的 Person 页面。
         * 3. 单条删除、清空和添加操作应修改生产页面使用的真实持久化屏蔽列表。
         * 4. 添加对话框的取消与确认路径都应确定可测。
         */
        val dao = getContentFilterDatabase(composeRule.activity).blockedQuestionAuthorDao()
        val navigator = setScreen()

        composeRule.onNode(hasText("提问者", substring = true) and hasClickAction()).performClick()
        composeRule.onNodeWithTag(BlocklistSettingsTestTags.FAB).assertIsDisplayed()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("blocklistSettings:questionAuthors:item:offline-asker-1").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(BlocklistSettingsTestTags.QUESTION_AUTHOR_LIST).assertIsDisplayed()
        composeRule.onNodeWithTag(BlocklistSettingsTestTags.QUESTION_AUTHOR_LIST).performVerticalSwipeCycle()
        composeRule.onNodeWithTag(BlocklistSettingsTestTags.QUESTION_AUTHOR_LIST).performHorizontalSwipeCycle()
        navigator.reset()
        composeRule.onNodeWithTag("blocklistSettings:questionAuthors:item:offline-asker-1").performClick()
        assertEquals(
            listOf(
                Person(
                    id = "offline-asker-1",
                    urlToken = "offline-asker-token-1",
                    name = "离线提问者一",
                ),
            ),
            navigator.destinations,
        )

        // 删除图标嵌在可点击行中，须定位图标自身的语义节点，避免 Compose 将点击合并为父行的导航操作。
        composeRule
            .onNodeWithTag("blocklistSettings:questionAuthors:delete:offline-asker-1", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(5_000) { runBlocking { dao.getUserCount() == 1 } }
        composeRule.onNodeWithTag(BlocklistSettingsTestTags.QUESTION_AUTHOR_CLEAR_BUTTON).performClick()
        composeRule.waitForIdle()
        assertEquals("清空在确认前不应修改屏蔽列表", 1, runBlocking { dao.getUserCount() })
        composeRule
            .onNode(hasText("清空") and hasClickAction() and hasAnyAncestor(hasTestTag(BlocklistSettingsTestTags.CLEAR_DIALOG)))
            .performClick()
        composeRule.waitUntil(5_000) { runBlocking { dao.getUserCount() == 0 } }

        composeRule.onNodeWithTag(BlocklistSettingsTestTags.FAB).performClick()
        composeRule.onNodeWithTag(BlocklistSettingsTestTags.USER_DIALOG_DISMISS).performClick()
        assertEquals(0, runBlocking { dao.getUserCount() })

        composeRule.onNodeWithTag(BlocklistSettingsTestTags.FAB).performClick()
        composeRule.onNodeWithTag(BlocklistSettingsTestTags.USER_DIALOG_ID_INPUT).performTextInput("new-asker-id")
        composeRule.onNodeWithTag(BlocklistSettingsTestTags.USER_DIALOG_NAME_INPUT).performTextInput("新提问者")
        composeRule.onNodeWithTag(BlocklistSettingsTestTags.USER_DIALOG_CONFIRM).performClick()
        composeRule.waitUntil(5_000) { runBlocking { dao.getUserCount() == 1 } }
        assertEquals(
            listOf("new-asker-id" to "新提问者"),
            runBlocking { dao.getAllUsers().map { it.userId to it.userName } },
        )
    }

    private fun setScreen(): RecordingNavigator = composeRule.setScreenContent {
        BlocklistSettingsScreen()
    }

    private fun seededQuestionAuthors(): List<BlockedQuestionAuthor> = listOf(
        BlockedQuestionAuthor(
            userId = "offline-asker-1",
            userName = "离线提问者一",
            urlToken = "offline-asker-token-1",
        ),
        BlockedQuestionAuthor(
            userId = "offline-asker-2",
            userName = "离线提问者二",
            urlToken = "offline-asker-token-2",
        ),
    )
}
