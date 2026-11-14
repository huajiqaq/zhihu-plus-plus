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
import com.github.zly2006.zhihu.navigation.Person
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.platform.MapSettingsStore
import kotlinx.io.files.Path
import kotlinx.serialization.json.Json
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse

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

    @Test
    fun resolvedPeopleKeepOneHistoryEntryAndCanBeRemovedByTheirOriginalLink() {
        val directory = Files.createTempDirectory("local-history-person-identity-")
        try {
            val file = directory.resolve("history.json")
            val link = Person(Person.EMPTY_ID, "alice")
            val resolved = Person("a".repeat(32), "alice")
            val storage = LocalHistoryStorage(Path(file.toString()), MapSettingsStore())
            storage.add(link)
            storage.add(resolved)
            assertEquals(1, storage.history.size, "资料加载后的ID不能让同一个人物在历史里重复")
            assertEquals(resolved.id, (storage.history.single() as Person).id)
            storage.remove(setOf(link))
            assertEquals(0, storage.history.size)
            Files.writeString(file, Json.encodeToString(arrayOf<NavDestination>(link, resolved)))
            assertEquals(1, storage.history.size, "已有旧数组也应按同一身份读回")
            storage.remove(setOf(link))
            assertEquals(0, storage.history.size)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun recordingToggleBlocksEveryStorageOwnerAndReopenKeepsOrder() {
        val directory = Files.createTempDirectory("local-history-recording-")
        try {
            val settings = MapSettingsStore()
            val path = Path(directory.resolve("history.json").toString())
            val storage = LocalHistoryStorage(path, settings)
            val navigationWriter = LocalHistoryStorage(path, settings)
            storage.add(Question(1, "保留记录"))
            storage.setRecordingEnabled(false)
            navigationWriter.add(Question(2, "关闭期间"))
            storage.add(Article("关闭期间", ArticleType.Answer, 3))
            assertEquals(1, storage.history.size)
            assertEquals(Question(1, "保留记录"), storage.history.single())
            storage.setRecordingEnabled(true)
            navigationWriter.add(Question(4, "重新记录"))
            assertContentEquals(arrayOf<NavDestination>(Question(4, "重新记录"), Question(1, "保留记录")), storage.history)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun unreadableHistoryIsNotOverwrittenAndBoundRemainsOneThousand() {
        val directory = Files.createTempDirectory("local-history-write-safety-")
        try {
            val file = directory.resolve("history.json")
            val initial = Array<NavDestination>(1000) { Question(it.toLong(), "问题 $it") }
            Files.writeString(file, Json.encodeToString(initial))
            val storage = LocalHistoryStorage(Path(file.toString()), MapSettingsStore())
            storage.add(Question(1001, "最新"))
            assertEquals(1000, storage.history.size)
            assertFalse(storage.history.any { it == Question(0) })
            assertEquals(Question(1001), storage.history.first())
            Files.writeString(file, "invalid legacy bytes")
            assertFails { storage.add(Question(9)) }
            assertEquals("invalid legacy bytes", Files.readString(file))
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
