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

import android.content.Context
import com.github.zly2006.zhihu.data.initializeAndroidApplicationContext
import kotlinx.io.files.Path

/** Application 持有唯一账号根对象，Activity、后台任务和图片加载借用同一实例。 */
interface AndroidZhihuAccountOwner {
    val zhihuAccountStore: ZhihuAccountStore
}

fun createAndroidAccountStore(context: Context): ZhihuAccountStore {
    val application = context.applicationContext
    initializeAndroidApplicationContext(application)
    return ZhihuAccountStore(
        repository = ZhihuAccountRepository(Path(application.filesDir.path, "account.json")),
    )
}

fun androidZhihuAccountStore(context: Context): ZhihuAccountStore {
    val application = context.applicationContext
    initializeAndroidApplicationContext(application)
    return (application as? AndroidZhihuAccountOwner)?.zhihuAccountStore
        ?: error("Android Application 必须持有账号 store")
}
