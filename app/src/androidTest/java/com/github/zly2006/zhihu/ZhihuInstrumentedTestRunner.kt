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

import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.test.runner.AndroidJUnitRunner
import com.github.zly2006.zhihu.test.InstrumentedTestEnvironment

class ZhihuInstrumentedTestApplication : ZhihuApplication() {
    val environment = InstrumentedTestEnvironment()
    override val zhihuAccountStore get() = environment.accountStore
}

class ZhihuInstrumentedTestRunner : AndroidJUnitRunner() {
    private lateinit var application: ZhihuInstrumentedTestApplication
    private var testArguments: Bundle? = null

    override fun newApplication(cl: ClassLoader, className: String, context: Context): Application =
        super.newApplication(cl, ZhihuInstrumentedTestApplication::class.java.name, context).also {
            application = it as ZhihuInstrumentedTestApplication
        }

    override fun onCreate(arguments: Bundle) {
        if (!arguments.containsKey(TEST_TIMEOUT_ARGUMENT)) {
            arguments.putString(TEST_TIMEOUT_ARGUMENT, DEFAULT_TEST_TIMEOUT_MILLIS.toString())
        }

        val timingListenerName = InstrumentedTestTimingListener::class.java.name
        val listenerNames = arguments
            .getString(TEST_LISTENER_ARGUMENT)
            .orEmpty()
            .split(',')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toMutableSet()
        listenerNames += timingListenerName
        arguments.putString(TEST_LISTENER_ARGUMENT, listenerNames.joinToString(","))

        testArguments = Bundle(arguments)
        super.onCreate(arguments)
    }

    override fun callApplicationOnCreate(app: Application) {
        // 先建立测试 Application 自己的账号根对象，后台入口不能先打开生产存储。
        application.environment.configureFromArguments(testArguments)
        application.environment.reseed(app)
        super.callApplicationOnCreate(app)
    }

    override fun finish(resultCode: Int, results: Bundle?) {
        try {
            if (::application.isInitialized) application.environment.close()
        } catch (error: Exception) {
            Log.e("ZHPP_TEST", "关闭设备测试账号根对象失败", error)
        } finally {
            super.finish(resultCode, results)
        }
    }

    private companion object {
        const val TEST_TIMEOUT_ARGUMENT = "timeout_msec"
        const val TEST_LISTENER_ARGUMENT = "listener"
        const val DEFAULT_TEST_TIMEOUT_MILLIS = 30_000L
    }
}
