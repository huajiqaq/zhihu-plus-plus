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

import kotlinx.io.files.Path
import java.io.IOException
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ZhihuAccountRepositoryTest {
    private lateinit var directory: java.nio.file.Path
    private lateinit var file: java.nio.file.Path

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
    fun loadFallsBackToDefaultSessionForInvalidJsonWithoutHidingReadFailure() {
        val repository = ZhihuAccountRepository(Path(file.toString()))
        Files.writeString(file, "not json")
        val invalidBytes = Files.readAllBytes(file)

        val session = repository.loadAccounts().session

        assertFalse(session.login)
        assertTrue(session.cookies.isEmpty())
        assertContentEquals(invalidBytes, Files.readAllBytes(file))
        Files.writeString(file, """{"accounts":[null]}""")
        assertEquals(ZhihuAccounts(), repository.loadAccounts())
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
