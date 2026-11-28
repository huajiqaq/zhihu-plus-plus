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

package com.github.zly2006.zhihu.test

import android.content.Context
import android.os.Bundle
import com.github.zly2006.zhihu.ZhihuInstrumentedTestApplication
import com.github.zly2006.zhihu.account.ZhihuAccountProfileSnapshot
import com.github.zly2006.zhihu.account.ZhihuAccountRepository
import com.github.zly2006.zhihu.account.ZhihuAccountSession
import com.github.zly2006.zhihu.account.ZhihuAccountStore
import com.github.zly2006.zhihu.data.Person
import com.github.zly2006.zhihu.data.ZHIHU_ANDROID_USER_AGENT
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.i18n.APP_LANGUAGE_KEY
import com.github.zly2006.zhihu.ui.PREFERENCE_NAME
import java.io.File
import java.util.UUID
import kotlinx.io.files.Path
import kotlinx.serialization.json.encodeToJsonElement

val Context.instrumentedTestEnvironment: InstrumentedTestEnvironment
    get() = (applicationContext as ZhihuInstrumentedTestApplication).environment

class InstrumentedTestEnvironment {
    companion object {
        const val DATA_MODE_ARG = "zhpp_data_mode"
    }

    enum class DataMode {
        MOCK,
        REAL,
    }

    private var currentDataMode: DataMode = DataMode.MOCK
    var installedDataMode: DataMode? = null
        private set
    private var currentAccountStore: ZhihuAccountStore? = null
    private var mockDirectory: File? = null

    val accountStore: ZhihuAccountStore
        get() = checkNotNull(currentAccountStore) { "设备测试账号根对象尚未初始化" }

    fun configureFromArguments(arguments: Bundle?) {
        currentDataMode = when (arguments?.getString(DATA_MODE_ARG)?.trim()?.lowercase()) {
            "real" -> DataMode.REAL
            else -> DataMode.MOCK
        }
    }

    fun isMockMode(): Boolean = currentDataMode == DataMode.MOCK

    fun reseed(context: Context, forceMock: Boolean = false) {
        val dataMode = if (forceMock) DataMode.MOCK else currentDataMode
        if (installedDataMode != dataMode || currentAccountStore == null) {
            // 模拟账号根对象不能读取或覆盖手工恢复的真实 account.json。
            val directory = if (dataMode == DataMode.MOCK) {
                File(context.cacheDir, "zhpp-instrument-account-${UUID.randomUUID()}").also {
                    check(it.mkdir()) { "创建设备测试的隔离账号目录失败" }
                }
            } else {
                null
            }
            val engine = if (dataMode == DataMode.MOCK) ZhihuMockApi.mockEngine() else null
            val store = try {
                ZhihuAccountStore(
                    ZhihuAccountRepository(Path((directory ?: context.filesDir).path, "account.json")),
                    engine,
                )
            } catch (error: Throwable) {
                runCatching { directory?.deleteRecursively() }.exceptionOrNull()?.let { if (it !== error) error.addSuppressed(it) }
                throw error
            }
            val previousStore = currentAccountStore
            val previousDirectory = mockDirectory
            currentAccountStore = store
            installedDataMode = dataMode
            mockDirectory = directory
            previousStore?.close()
            previousDirectory?.let { check(it.deleteRecursively()) { "删除设备测试账号目录失败" } }
        }

        context
            .getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .putString(APP_LANGUAGE_KEY, "zh")
            .putBoolean("allowTelemetry", false)
            .putLong("last_main_launch_timestamp", System.currentTimeMillis())
            .commit()

        ZhihuMockApi.reset(enabled = dataMode == DataMode.MOCK)

        when (dataMode) {
            DataMode.MOCK -> {
                val self = Person(
                    id = "android-test-user-id",
                    url = "https://www.zhihu.com/people/android-test-user",
                    userType = "people",
                    urlToken = "android-test-user",
                    name = "AndroidTestUser",
                    headline = "androidTest seeded login state",
                    avatarUrl = "",
                )
                accountStore.save(
                    ZhihuAccountSession(
                        login = true,
                        username = "AndroidTestUser",
                        cookies = mutableMapOf(
                            "z_c0" to "android-test-zc0",
                            "d_c0" to "android-test-dc0",
                            "_xsrf" to "android-test-xsrf",
                        ),
                        userAgent = ZHIHU_ANDROID_USER_AGENT,
                        profile = ZhihuAccountProfileSnapshot(
                            id = self.id,
                            name = self.name,
                            urlToken = self.urlToken,
                            userType = self.userType,
                            avatarUrl = self.avatarUrl,
                        ),
                        self = ZhihuJson.json.encodeToJsonElement(self),
                    ),
                )
            }

            DataMode.REAL -> check(accountStore.session.login) {
                "真实数据模式要求预先提供当前多账号结构且已登录的账号文件"
            }
        }
    }

    fun close() {
        currentAccountStore?.close()
        currentAccountStore = null
        installedDataMode = null
        ZhihuMockApi.reset(enabled = false)
        mockDirectory?.let { check(it.deleteRecursively()) { "删除设备测试账号目录失败" } }
        mockDirectory = null
    }
}
