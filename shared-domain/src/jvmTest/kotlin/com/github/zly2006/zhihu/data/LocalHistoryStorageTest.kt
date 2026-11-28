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

package com.github.zly2006.zhihu.data

import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.platform.MapSettingsStore
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.io.files.Path
import kotlinx.serialization.json.Json

class LocalHistoryStorageTest {
    @Test
    fun legacyArrayDeletionAndTwoWritersKeepLatestContent() {
        val directory = Files.createTempDirectory("local-history-contract-")
        try {
            val file = directory.resolve("history.json")
            val first = Article("旧标题", ArticleType.Answer, 1)
            val second = Article("文章", ArticleType.Article, 2)
            Files.writeString(file, Json.encodeToString(arrayOf<NavDestination>(first, second)))
            val settings = MapSettingsStore()
            val writer = LocalHistoryStorage(Path(file.toString()), settings)
            val otherPage = LocalHistoryStorage(Path(file.toString()), settings)
            assertContentEquals(arrayOf<NavDestination>(second, first), writer.history)
            writer.remove(setOf(first))
            otherPage.add(Question(3, "新问题"))
            assertContentEquals(arrayOf<NavDestination>(Question(3, "新问题"), second), writer.history)
            assertFalse(writer.history.any { it == first })
            writer.add(first.copy(title = "新标题", readingQueueSourceId = "do-not-persist"))
            assertEquals("新标题", (otherPage.history.first() as Article).title)
            assertEquals(null, (otherPage.history.first() as Article).readingQueueSourceId)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
