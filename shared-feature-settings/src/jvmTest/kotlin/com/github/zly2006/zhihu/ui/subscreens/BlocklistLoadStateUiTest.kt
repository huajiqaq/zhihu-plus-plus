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
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.room.execSQL
import androidx.room.useReaderConnection
import androidx.room.useWriterConnection
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.Navigator
import com.github.zly2006.zhihu.platform.desktopSettingsStore
import com.github.zly2006.zhihu.theme.ThemeManager
import com.github.zly2006.zhihu.theme.ThemeStyle
import com.github.zly2006.zhihu.theme.ZhihuTheme
import com.github.zly2006.zhihu.ui.BlocklistSettingsScreen
import com.github.zly2006.zhihu.ui.BlocklistSettingsTestTags
import com.github.zly2006.zhihu.ui.components.APP_LOAD_STATE_FAILED_TAG
import com.github.zly2006.zhihu.ui.components.APP_LOAD_STATE_LOADING_TAG
import com.github.zly2006.zhihu.viewmodel.filter.BlockedFeedRecord
import com.github.zly2006.zhihu.viewmodel.filter.BlockedKeyword
import com.github.zly2006.zhihu.viewmodel.filter.ContentFilterDatabase
import com.github.zly2006.zhihu.viewmodel.filter.ContentViewRecord
import com.github.zly2006.zhihu.viewmodel.filter.KeywordType
import com.github.zly2006.zhihu.viewmodel.filter.desktopContentFilterDatabaseFile
import com.github.zly2006.zhihu.viewmodel.filter.getContentFilterDatabase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 查询等待、失败不能显示为空列表；添加弹窗同帧重复确认只能写入一次。
 * 数据库单例须在独立 JVM 的临时 user.home 下创建，清表前必须核对实际数据库路径。
 */
@OptIn(ExperimentalTestApi::class)
class BlocklistLoadStateUiTest {
    @Test
    fun blockedFeedHistoryShowsLoadingBeforeTheFirstQueryReturns() = withFixture { database ->
        holding(database, readers = readerCount(database)) { release ->
            runDesktopComposeUiTest(width = 412, height = 892) {
                setScreen { BlockedFeedHistoryScreen() }
                onNodeWithTag(APP_LOAD_STATE_LOADING_TAG).assertExists()
                onNodeWithText("暂无屏蔽记录").assertDoesNotExist()
                release()
                waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("暂无屏蔽记录").fetchSemanticsNodes().isNotEmpty() }
            }
        }
    }

    @Test
    fun blockedFeedHistoryShowsTheReasonWhenLoadingFails() = withFixture { database ->
        withRenamedTable(database, BlockedFeedRecord.TABLE_NAME) {
            runDesktopComposeUiTest(width = 412, height = 892) {
                setScreen { BlockedFeedHistoryScreen() }
                waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag(APP_LOAD_STATE_FAILED_TAG).fetchSemanticsNodes().isNotEmpty() }
                onNode(hasText("no such table", substring = true)).assertExists()
                onNodeWithText("暂无屏蔽记录").assertDoesNotExist()
            }
        }
    }

    @Test
    fun blocklistShowsLoadingBeforeTheFirstQueryReturns() = withFixture { database ->
        holding(database, readers = readerCount(database)) { release ->
            runDesktopComposeUiTest(width = 412, height = 892) {
                setScreen { BlocklistSettingsScreen() }
                onNodeWithTag(APP_LOAD_STATE_LOADING_TAG).assertExists()
                onNodeWithText("暂无精确匹配关键词").assertDoesNotExist()
                release()
                waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("暂无精确匹配关键词").fetchSemanticsNodes().isNotEmpty() }
            }
        }
    }

    @Test
    fun blocklistShowsTheReasonWhenLoadingFails() = withFixture { database ->
        withRenamedTable(database, BlockedKeyword.TABLE_NAME) {
            runDesktopComposeUiTest(width = 412, height = 892) {
                setScreen { BlocklistSettingsScreen() }
                waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag(APP_LOAD_STATE_FAILED_TAG).fetchSemanticsNodes().isNotEmpty() }
                onNode(hasText("no such table", substring = true)).assertExists()
                onNodeWithText("暂无精确匹配关键词").assertDoesNotExist()
            }
        }
    }

    @Test
    fun filterOverviewShowsLoadingBeforeStatsReturn() = withFixture { database ->
        desktopSettingsStore().putBoolean("enableContentFilter", true)
        holding(database, readers = readerCount(database)) { release ->
            runDesktopComposeUiTest(width = 412, height = 892) {
                setScreen { ContentFilterSettingsScreen() }
                onNodeWithText("正在读取…").assertExists()
                onNodeWithText("已过滤 0 条重复内容").assertDoesNotExist()
                release()
                waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("已过滤 0 条重复内容").fetchSemanticsNodes().isNotEmpty() }
            }
        }
    }

    @Test
    fun filterOverviewShowsTheReasonWhenStatsFailToLoad() = withFixture { database ->
        desktopSettingsStore().putBoolean("enableContentFilter", true)
        withRenamedTable(database, ContentViewRecord.TABLE_NAME) {
            runDesktopComposeUiTest(width = 412, height = 892) {
                setScreen { ContentFilterSettingsScreen() }
                waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("过滤统计加载失败").fetchSemanticsNodes().isNotEmpty() }
                onNode(hasText("no such table", substring = true)).assertExists()
            }
        }
    }

    /** 同帧点击覆盖“提交中”禁用生效前的重复提交窗口。 */
    @Test
    fun addKeywordDialogSubmitsOnceWhenConfirmIsTappedTwice() = withFixture { database ->
        val dao = database.blockedKeywordDao()
        runDesktopComposeUiTest(width = 412, height = 892) {
            setScreen { BlocklistSettingsScreen() }
            onNodeWithTag(BlocklistSettingsTestTags.FAB).performClick()
            onNodeWithTag(BlocklistSettingsTestTags.KEYWORD_DIALOG_INPUT).performTextInput("重复提交")
            val confirm = onNodeWithTag(BlocklistSettingsTestTags.KEYWORD_DIALOG_CONFIRM)
                .fetchSemanticsNode()
                .config[SemanticsActions.OnClick]
                .action!!
            runOnUiThread {
                confirm()
                confirm()
            }
            waitUntil(timeoutMillis = 5_000) {
                onAllNodesWithTag(BlocklistSettingsTestTags.KEYWORD_DIALOG_CONFIRM).fetchSemanticsNodes().isEmpty()
            }
            /** 等待潜在第二次写入，避免异步延迟造成假通过。 */
            runCatching {
                waitUntil(timeoutMillis = 1_500) { runBlocking { dao.getAllKeywords().size } >= 2 }
            }
            assertEquals(listOf("重复提交"), runBlocking { dao.getAllKeywords().map { it.keyword } })
        }
    }

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

    @Test
    fun retryRestartsTheRealKeywordQueryAfterTheTableIsRestored() = withFixture { database ->
        val table = BlockedKeyword.TABLE_NAME
        val hidden = "${table}_hidden_for_retry"
        var restored = false
        runBlocking { database.useWriterConnection { it.execSQL("ALTER TABLE `$table` RENAME TO `$hidden`") } }
        try {
            runDesktopComposeUiTest(width = 412, height = 892) {
                setScreen { BlocklistSettingsScreen() }
                waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag(APP_LOAD_STATE_FAILED_TAG).fetchSemanticsNodes().isNotEmpty() }
                onNode(hasText("no such table", substring = true)).assertExists()
                runBlocking { database.useWriterConnection { it.execSQL("ALTER TABLE `$hidden` RENAME TO `$table`") } }
                restored = true
                onNodeWithText("重试").performClick()
                waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("暂无精确匹配关键词").fetchSemanticsNodes().isNotEmpty() }
                onNodeWithTag(APP_LOAD_STATE_FAILED_TAG).assertDoesNotExist()
            }
        } finally {
            if (!restored) runBlocking { database.useWriterConnection { it.execSQL("ALTER TABLE `$hidden` RENAME TO `$table`") } }
        }
    }

    private fun ComposeUiTest.setScreen(content: @Composable () -> Unit) = setContent {
        ZhihuTheme {
            CompositionLocalProvider(LocalNavigator provides Navigator({}, {})) { content() }
        }
    }

    private fun withFixture(body: (ContentFilterDatabase) -> Unit) {
        val originalUserHome = System.getProperty("user.home")
        val originalTheme = ThemeManager.snapshot()
        try {
            System.setProperty("user.home", fixtureHome.toAbsolutePath().toString())
            desktopSettingsStore().putString("uiThemeStyle", ThemeStyle.MATERIAL_EXPRESSIVE.name)
            ThemeManager.setThemeStyle(ThemeStyle.MATERIAL_EXPRESSIVE)
            val database = getContentFilterDatabase()
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
            ThemeManager.load(originalTheme)
            System.setProperty("user.home", originalUserHome)
        }
    }

    private fun isFixtureDatabase(database: ContentFilterDatabase): Boolean = runBlocking {
        val path = database.useReaderConnection { connection ->
            connection.usePrepared("SELECT file FROM pragma_database_list WHERE name = 'main'") { statement ->
                if (statement.step()) statement.getText(0) else ""
            }
        }
        path.isNotBlank() && java.io.File(path).canonicalFile == desktopContentFilterDatabaseFile().canonicalFile
    }

    private fun withRenamedTable(database: ContentFilterDatabase, table: String, body: () -> Unit) {
        val hidden = "${table}_hidden_by_test"
        runBlocking { database.useWriterConnection { connection -> connection.execSQL("ALTER TABLE `$table` RENAME TO `$hidden`") } }
        try {
            body()
        } finally {
            runBlocking { database.useWriterConnection { connection -> connection.execSQL("ALTER TABLE `$hidden` RENAME TO `$table`") } }
        }
    }

    /** 首次观察用写连接装触发器、后续直接读；占满读写连接才能稳定停在加载中。 */
    private fun holding(database: ContentFilterDatabase, readers: Int = 0, body: (release: () -> Unit) -> Unit) {
        val gate = CompletableDeferred<Unit>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val held = List(readers + 1) { index ->
            CompletableDeferred<Unit>().also { acquired ->
                scope.launch {
                    if (index == 0) {
                        database.useWriterConnection {
                            acquired.complete(Unit)
                            gate.await()
                        }
                    } else {
                        database.useReaderConnection {
                            acquired.complete(Unit)
                            gate.await()
                        }
                    }
                }
            }
        }
        try {
            runBlocking { withTimeout(5_000) { held.forEach { it.await() } } }
            body { gate.complete(Unit) }
        } finally {
            gate.complete(Unit)
            scope.cancel()
        }
    }

    /** Room 在 JVM 上的连接池：WAL 下 4 个读连接，其余日志模式 1 个，另有 1 个写连接。 */
    private fun readerCount(database: ContentFilterDatabase): Int = runBlocking {
        val mode = database.useReaderConnection { connection ->
            connection.usePrepared("PRAGMA journal_mode") { statement -> if (statement.step()) statement.getText(0) else "" }
        }
        if (mode.equals("wal", ignoreCase = true)) 4 else 1
    }

    private companion object {
        /** 数据库单例一直持有此目录，测试结束不能删除。 */
        val fixtureHome = Files.createTempDirectory("zhihu-blocklist-load-state-")
    }
}
