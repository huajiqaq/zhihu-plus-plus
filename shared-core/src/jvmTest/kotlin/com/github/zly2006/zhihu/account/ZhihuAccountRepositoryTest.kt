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

import com.github.zly2006.zhihu.data.ZhihuJson
import kotlinx.io.files.Path
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
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
    fun loadReturnsDefaultSessionWhenFileIsMissingOrBlank() {
        val repository = ZhihuAccountRepository(Path(file.toString()))

        val session = repository.loadAccounts().session

        assertFalse(session.login)
        assertEquals("", session.username)
        assertTrue(session.cookies.isEmpty())
        assertEquals(DEFAULT_ZHIHU_USER_AGENT, session.userAgent)
        assertNull(session.profile)
        Files.writeString(file, " \n\t")
        assertEquals(ZhihuAccounts(), repository.loadAccounts())
    }

    @Test
    fun saveAndLoadRoundTripsSession() {
        file = directory.resolve("nested/account.json")
        val repository = ZhihuAccountRepository(Path(file.toString()))
        val session = ZhihuAccountSession(
            login = true,
            username = "alice",
            cookies = mutableMapOf("z_c0" to "token", "d_c0" to "dc0"),
            profile = ZhihuAccountProfileSnapshot(
                id = "123",
                name = "alice",
                urlToken = "alice-token",
                userType = "people",
            ),
            self = buildJsonObject {
                put("id", "123")
                put("name", "alice")
            },
            mobileAccessToken = "mobile-access",
            mobileRefreshToken = "mobile-refresh",
            mobileTokenType = "bearer",
            mobileTokenExpiresAt = 123456789L,
        )
        val accounts = ZhihuAccounts("123", listOf(ZhihuSavedAccount("123", session)))

        repository.saveAccounts(accounts)

        assertEquals(accounts, repository.loadAccounts())
        assertEquals(session, repository.loadAccounts().session)
        assertFalse(Files.exists(file.resolveSibling("account.json.tmp")))
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
    fun clearDeletesStoredSessionAndPropagatesDeletionFailure() {
        val repository = ZhihuAccountRepository(Path(file.toString()))
        val session = ZhihuAccountSession(
            login = true,
            username = "alice",
            cookies = mutableMapOf("z_c0" to "token"),
        )
        repository.saveAccounts(ZhihuAccounts("alice", listOf(ZhihuSavedAccount("alice", session))))

        repository.clear()
        repository.clear()

        assertFalse(Files.exists(file))
        assertFalse(repository.loadAccounts().session.login)
        Files.createDirectory(file)
        val blocker = Files.writeString(file.resolve("blocker"), "keep")
        assertFailsWith<IOException> { repository.clear() }
        assertEquals("keep", Files.readString(blocker))
    }

    @Test
    fun legacySessionMigratesIntoOneSavedAccount() {
        val legacy = ZhihuAccountSession(
            login = true,
            username = "alice",
            profile = ZhihuAccountProfileSnapshot(id = "alice-id", name = "Alice"),
        )
        Files.writeString(file, ZhihuJson.json.encodeToString(legacy))
        val legacyBytes = Files.readAllBytes(file)
        val repository = ZhihuAccountRepository(Path(file.toString()))

        val accounts = repository.loadAccounts()

        assertEquals("alice-id", accounts.activeAccountId)
        assertEquals(listOf(legacy), accounts.accounts.map { it.session })
        assertContentEquals(legacyBytes, Files.readAllBytes(file))
        Files.writeString(file, ZhihuJson.json.encodeToString(legacy.copy(login = false)))
        assertEquals(ZhihuAccounts(), repository.loadAccounts())
    }

    @Test
    fun savedAccountsRoundTripWithoutLosingInactiveSession() {
        val repository = ZhihuAccountRepository(Path(file.toString()))
        val alice = ZhihuAccountSession(login = true, username = "alice")
        val bob = ZhihuAccountSession(login = true, username = "bob")
        val accounts = ZhihuAccounts(
            activeAccountId = "bob",
            accounts = listOf(ZhihuSavedAccount("alice", alice), ZhihuSavedAccount("bob", bob)),
        )
        repository.saveAccounts(accounts.copy(activeAccountId = "alice"))

        repository.saveAccounts(accounts)

        val restored = repository.loadAccounts()
        assertEquals(accounts, restored)
        assertEquals("bob", restored.activeAccountId)
        assertEquals(listOf("alice", "bob"), restored.accounts.map { it.id })
        assertEquals(bob, restored.session)
        assertFalse(Files.exists(directory.resolve("account.json.tmp")))
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
