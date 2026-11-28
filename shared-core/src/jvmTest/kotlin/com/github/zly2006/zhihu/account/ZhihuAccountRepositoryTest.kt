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

package com.github.zly2006.zhihu.account

import java.io.IOException
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlinx.io.files.Path
import kotlinx.serialization.json.Json
import java.nio.file.Path as JavaPath

class ZhihuAccountRepositoryTest {
    private lateinit var directory: JavaPath
    private lateinit var file: JavaPath

    @BeforeTest
    fun createAccountDirectory() {
        directory = Files.createTempDirectory("account-repository-contract-")
        file = directory.resolve("account.json")
    }

    @AfterTest
    fun deleteAccountDirectory() {
        check(directory.toFile().deleteRecursively())
    }

    @Test
    fun loadRejectsInvalidAndLegacyFilesWithoutChangingBytes() {
        val repository = ZhihuAccountRepository(Path(file.toString()))
        assertEquals(ZhihuAccounts(), repository.loadAccounts())
        val invalidFiles = listOf(
            "",
            "not json",
            "{}",
            """{"login":true,"username":"legacy","cookies":{"z_c0":"synthetic-private"}}""",
            """{"accounts":[null]}""",
            """{"accounts":"synthetic-private"}""",
            """{"accounts":[],"version":2}""",
            """{"accounts":[],"activeAccountId":"missing"}""",
            """{"accounts":[{"id":"duplicate","session":{}},{"id":"duplicate","session":{}}]}""",
        )
        for (text in invalidFiles) {
            Files.writeString(file, text)
            val originalBytes = Files.readAllBytes(file)
            val failure = assertFailsWith<IllegalStateException> { repository.loadAccounts() }
            assertEquals(null, failure.cause, "解析异常不能泄漏账号文件片段")
            assertContentEquals(originalBytes, Files.readAllBytes(file))
            assertFalse(Files.exists(directory.resolve("account.json.tmp")))
        }
        Files.delete(file)
        val self = Json.parseToJsonElement("""{"id":"self-only","name":"Self Only"}""")
        val session = ZhihuAccountSession(login = true, username = "alice", self = self)
        val accounts = ZhihuAccounts("alice", listOf(ZhihuSavedAccount("alice", session)), mapOf("d_c0" to "synthetic-guest"))
        repository.saveAccounts(accounts)
        assertEquals(accounts, repository.loadAccounts(), "保留当前 self 字段，但不能从它补写 profile")
        assertEquals(null, repository.loadAccounts().session.profile)
        Files.delete(file)
        Files.createDirectory(file)
        Files.writeString(file.resolve("blocker"), "keep")
        assertFailsWith<IOException> { repository.loadAccounts() }
    }

    @Test
    fun failedAtomicSavePreservesOriginalBytesAndCleansItsTemporaryFile() {
        val repository = ZhihuAccountRepository(Path(file.toString()))
        val alice = ZhihuAccountSession(login = true, username = "alice")
        val accounts = ZhihuAccounts("alice", listOf(ZhihuSavedAccount("alice", alice)))
        repository.saveAccounts(accounts)
        val persistedBytes = Files.readAllBytes(file)
        val temporary = Files.createDirectory(directory.resolve("account.json.tmp"))
        val blocker = Files.writeString(temporary.resolve("blocker"), "keep")

        assertFailsWith<IOException> { repository.saveAccounts(accounts.copy(activeAccountId = null)) }
        assertContentEquals(persistedBytes, Files.readAllBytes(file))
        assertEquals(accounts, repository.loadAccounts())
        assertEquals("keep", Files.readString(blocker))
        Files.delete(blocker)
        Files.delete(temporary)
        Files.delete(file)
        Files.createDirectory(file)
        val preserved = Files.write(file.resolve("preserved.json"), persistedBytes)

        assertFailsWith<IOException> { repository.saveAccounts(accounts) }
        assertContentEquals(persistedBytes, Files.readAllBytes(preserved))
        assertFalse(Files.exists(temporary))
    }
}
