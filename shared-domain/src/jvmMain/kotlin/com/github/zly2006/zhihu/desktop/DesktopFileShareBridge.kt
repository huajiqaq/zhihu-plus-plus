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

package com.github.zly2006.zhihu.desktop

import com.github.zly2006.zhihu.account.ZhihuAccountStore
import com.github.zly2006.zhihu.i18n.DomainStrings
import com.github.zly2006.zhihu.i18n.WidgetsStrings
import com.github.zly2006.zhihu.viewmodel.ensureExportAccountActive
import java.awt.Window
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

/** 只从安装包资源加载官方系统分享桥，不从工作目录或 PATH 寻找可执行文件。 */
object DesktopFileShareBridge {
    private val gate = Mutex()
    private val loaded by lazy {
        val os = System.getProperty("os.name")
        check(os.startsWith("Windows", ignoreCase = true) || os.startsWith("Mac", ignoreCase = true)) {
            DomainStrings.desktopFileShareUnsupported
        }
        val name = System.mapLibraryName("ZhihuFileShare")
        val resource = checkNotNull(javaClass.getResourceAsStream("/native-share/$name")) {
            DomainStrings.desktopFileShareUnavailable
        }
        resource.use { input ->
            val directory = Files.createTempDirectory("zhihu-share-library-")
            directory.toFile().deleteOnExit()
            val library = directory.resolve(name)
            library.toFile().deleteOnExit()
            Files.copy(input, library)
            if (os.startsWith("Windows", ignoreCase = true)) {
                System.load(Path.of(System.getProperty("java.home"), "bin", "jawt.dll").toAbsolutePath().toString())
            }
            System.load(library.toAbsolutePath().toString())
        }
        true
    }

    /** true 只表示用户已选定系统接收方，不表示接收方完成发送；false 表示取消。 */
    private external fun shareContent(window: Window, payload: String, title: String, isFile: Boolean, cancelled: AtomicBoolean): Boolean

    suspend fun shareText(window: Window, text: String, title: String): Boolean {
        val os = System.getProperty("os.name")
        check(os.startsWith("Windows", ignoreCase = true) || os.startsWith("Mac", ignoreCase = true)) {
            WidgetsStrings.desktopTextShareUnsupported
        }
        require(text.isNotBlank()) { WidgetsStrings.systemShareFailed }
        check(gate.tryLock()) { WidgetsStrings.systemShareUnavailable }
        try {
            withContext(Dispatchers.IO) { check(loaded) }
            return deliver(window, text, title.ifBlank { WidgetsStrings.shareContent }, isFile = false)
        } finally {
            gate.unlock()
        }
    }

    suspend fun shareImage(store: ZhihuAccountStore, url: String, window: Window) {
        check(gate.tryLock()) { WidgetsStrings.systemShareUnavailable }
        try {
            withContext(Dispatchers.IO) { check(loaded) }
            val file = withContext(Dispatchers.IO) {
                val directory = desktopZhihuDataFile("share_images").toPath()
                Files.createDirectories(directory)
                // 接收方可能延迟读取；不在面板关闭或进程退出时立即删除共享文件。
                val expiredBefore = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
                Files.newDirectoryStream(directory, "share_*").use { entries ->
                    entries.forEach { entry ->
                        if (Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS) &&
                            Files.getLastModifiedTime(entry, LinkOption.NOFOLLOW_LINKS).toMillis() < expiredBefore
                        ) {
                            runCatching { Files.deleteIfExists(entry) }
                        }
                    }
                }
                store.downloadDesktopImage(url, directory.toFile(), "share")
            }
            // 一旦进入系统调用，结果未知时也保留文件，避免中断已选择的接收方读取。
            deliver(window, file.absolutePath, DomainStrings.shareImage, isFile = true)
        } finally {
            gate.unlock()
        }
    }

    private suspend fun deliver(window: Window, payload: String, title: String, isFile: Boolean): Boolean = coroutineScope {
        val cancelled = AtomicBoolean(false)
        val delivery = async(Dispatchers.IO) {
            ensureExportAccountActive()
            shareContent(window, payload, title, isFile, cancelled)
        }
        try {
            delivery.await()
        } finally {
            cancelled.set(true)
        }
    }
}
