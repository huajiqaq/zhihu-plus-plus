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

package com.github.zly2006.zhihu.ui.subscreens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.room.useReaderConnection
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.Navigator
import com.github.zly2006.zhihu.platform.desktopSettingsStore
import com.github.zly2006.zhihu.theme.ThemeStyle
import com.github.zly2006.zhihu.theme.ZhihuTheme
import com.github.zly2006.zhihu.ui.BlocklistSettingsScreen
import com.github.zly2006.zhihu.ui.BlocklistSettingsTestTags
import com.github.zly2006.zhihu.viewmodel.filter.BlockedKeyword
import com.github.zly2006.zhihu.viewmodel.filter.ContentFilterDatabase
import com.github.zly2006.zhihu.viewmodel.filter.KeywordType
import com.github.zly2006.zhihu.viewmodel.filter.RoomContentFilterDatabase
import com.github.zly2006.zhihu.viewmodel.filter.desktopContentFilterDatabase
import com.github.zly2006.zhihu.viewmodel.filter.desktopContentFilterDatabaseFile
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

/**
 * 清空需要确认，且不能删除其他类型的屏蔽规则。
 * 数据库单例须在独立 JVM 的临时 user.home 下创建，清表前必须核对实际数据库路径。
 */
@OptIn(ExperimentalTestApi::class)
class BlocklistLoadStateUiTest {
    @Test
    fun clearingKeywordsRequiresConfirmation() = withFixture { database ->
        val dao = database.blockedKeywordDao()
        runBlocking { dao.insertKeyword(BlockedKeyword(keyword = "fixture", keywordType = KeywordType.EXACT_MATCH.name)) }
        runDesktopComposeUiTest(width = 412, height = 892) {
            setScreen { BlocklistSettingsScreen() }
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("fixture").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag(BlocklistSettingsTestTags.KEYWORD_CLEAR_BUTTON).performClick()
            waitForIdle()
            assertEquals(1, runBlocking { dao.getAllKeywords().size }, "清空不应在确认前修改屏蔽规则")
            onNodeWithText("取消").performClick()
            waitForIdle()
            assertEquals(1, runBlocking { dao.getAllKeywords().size })
            runBlocking { dao.insertKeyword(BlockedKeyword(keyword = "semantic-fixture", keywordType = KeywordType.NLP_SEMANTIC.name)) }
            assertEquals(listOf("fixture", "semantic-fixture").sorted(), runBlocking { dao.getAllKeywords().map { it.keyword }.sorted() })
            onNodeWithTag(BlocklistSettingsTestTags.KEYWORD_CLEAR_BUTTON).performClick()
            val confirm = onNode(hasText("清空") and hasClickAction() and hasAnyAncestor(hasTestTag(BlocklistSettingsTestTags.CLEAR_DIALOG)))
                .fetchSemanticsNode()
                .config[SemanticsActions.OnClick]
                .action!!
            runOnUiThread {
                confirm()
                confirm()
            }
            waitUntil(timeoutMillis = 5_000) {
                runBlocking { dao.getAllKeywords().none { it.keywordType == KeywordType.EXACT_MATCH.name } }
            }
            assertEquals(listOf("semantic-fixture"), runBlocking { dao.getAllKeywords().map { it.keyword } })
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("暂无精确匹配关键词").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithText("暂无精确匹配关键词").assertExists()
        }
    }

    private fun ComposeUiTest.setScreen(content: @Composable () -> Unit) = setContent {
        ZhihuTheme {
            CompositionLocalProvider(LocalNavigator provides Navigator({}, {})) { content() }
        }
    }

    private fun withFixture(body: (ContentFilterDatabase) -> Unit) {
        val originalUserHome = System.getProperty("user.home")
        try {
            System.setProperty("user.home", fixtureHome.toAbsolutePath().toString())
            desktopSettingsStore().putString("uiThemeStyle", ThemeStyle.MATERIAL_EXPRESSIVE.name)
            val database = desktopContentFilterDatabase
            check(isFixtureDatabase(database)) { "桌面数据库单例不在测试目录下，该测试必须在独立 JVM worker 中执行" }
            runBlocking {
                database.blockedKeywordDao().deleteKeywordsByType(KeywordType.EXACT_MATCH.name)
                database.blockedKeywordDao().deleteKeywordsByType(KeywordType.NLP_SEMANTIC.name)
                database.blockedUserDao().clearAllUsers()
                database.blockedQuestionAuthorDao().clearAllUsers()
                database.blockedTopicDao().clearAllTopics()
                database.blockedFeedRecordDao().clearAll()
                database.contentFilterDao().clearAllRecords()
            }
            body(database)
        } finally {
            System.setProperty("user.home", originalUserHome)
        }
    }

    private fun isFixtureDatabase(database: RoomContentFilterDatabase): Boolean = runBlocking {
        val path = database.useReaderConnection { connection ->
            connection.usePrepared("SELECT file FROM pragma_database_list WHERE name = 'main'") { statement ->
                if (statement.step()) statement.getText(0) else ""
            }
        }
        path.isNotBlank() && java.io.File(path).canonicalFile == desktopContentFilterDatabaseFile().canonicalFile
    }

    private companion object {
        /** 数据库单例一直持有此目录，测试结束不能删除。 */
        val fixtureHome = Files.createTempDirectory("zhihu-blocklist-load-state-")
    }
}
