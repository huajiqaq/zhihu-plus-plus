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

@file:OptIn(kotlinx.cinterop.BetaInteropApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)

package com.github.zly2006.zhihu.notification

import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.serialization.json.Json
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSBundle
import platform.Foundation.NSCocoaErrorDomain
import platform.Foundation.NSData
import platform.Foundation.NSDataWritingAtomic
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileNoSuchFileError
import platform.Foundation.NSFilePosixPermissions
import platform.Foundation.NSFileReadNoSuchFileError
import platform.Foundation.NSFileType
import platform.Foundation.NSFileTypeDirectory
import platform.Foundation.NSFileTypeRegular
import platform.Foundation.NSNumber
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataWithBytes
import platform.Foundation.stringWithContentsOfURL
import platform.Foundation.writeToURL
import platform.posix.O_NOFOLLOW
import platform.posix.O_WRONLY
import platform.posix.close
import platform.posix.fsync
import platform.posix.open
import platform.posix.rename

/** 独立于可备份的偏好；暂存文件先设为私有且排除备份，再原子替换正式文件。 */
internal class AppleNotificationStorage {
    private val files = NSFileManager.defaultManager

    fun load(): DeviceNotificationState {
        val file = stateDirectory().URLByAppendingPathComponent("state.json")!!
        val attributes = attributes(file) ?: return DeviceNotificationState()
        check(attributes[NSFileType] == NSFileTypeRegular) { "本机通知存储不是普通文件" }
        check(files.setAttributes(mapOf(NSFilePosixPermissions to 384), file.path!!, null))
        val text = NSString.stringWithContentsOfURL(file, NSUTF8StringEncoding, null)
            ?: error("无法读取本机通知存储")
        return Json.decodeFromString(DeviceNotificationState.serializer(), text)
    }

    fun save(state: DeviceNotificationState) {
        val directory = stateDirectory()
        val file = directory.URLByAppendingPathComponent("state.json")!!
        val temporary = directory.URLByAppendingPathComponent("state.pending")!!
        attributes(temporary)?.let { check(it[NSFileType] == NSFileTypeRegular) }
        val bytes = Json.encodeToString(DeviceNotificationState.serializer(), state).encodeToByteArray()
        bytes.usePinned { pinned ->
            val data = NSData.dataWithBytes(pinned.addressOf(0), bytes.size.toULong())
            check(data.writeToURL(temporary, NSDataWritingAtomic, null)) { "无法写入本机通知存储" }
        }
        check(files.setAttributes(mapOf(NSFilePosixPermissions to 384), temporary.path!!, null))
        check(temporary.setResourceValue(NSNumber(bool = true), NSURLIsExcludedFromBackupKey, null))
        val descriptor = open(temporary.path!!, O_WRONLY or O_NOFOLLOW)
        check(descriptor >= 0) { "无法打开本机通知暂存文件" }
        try {
            check(fsync(descriptor) == 0) { "无法持久化本机通知存储" }
        } finally {
            close(descriptor)
        }
        check(rename(temporary.path!!, file.path!!) == 0) { "无法原子保存本机通知存储" }
    }

    private fun stateDirectory(): NSURL {
        val bundleId = checkNotNull(NSBundle.mainBundle.bundleIdentifier)
        check(bundleId.isNotBlank() && bundleId.all { it.isLetterOrDigit() || it == '.' || it == '-' })
        val support = files.URLForDirectory(NSApplicationSupportDirectory, NSUserDomainMask, null, true, null)
            ?: error("无法定位本机通知目录")
        val directory = support.URLByAppendingPathComponent("$bundleId.system-notifications", true)!!
        val existing = attributes(directory)
        if (existing == null) {
            check(files.createDirectoryAtURL(directory, false, mapOf(NSFilePosixPermissions to 448), null))
        } else {
            check(existing[NSFileType] == NSFileTypeDirectory) { "本机通知目录无效" }
        }
        check(files.setAttributes(mapOf(NSFilePosixPermissions to 448), directory.path!!, null))
        check(directory.setResourceValue(NSNumber(bool = true), NSURLIsExcludedFromBackupKey, null)) {
            "无法将本机通知存储排除备份"
        }
        return directory
    }

    /** 只有明确的文件不存在可视为空状态；权限、损坏和其他 I/O 错误必须关闭通知能力。 */
    private fun attributes(url: NSURL): Map<Any?, *>? = memScoped {
        val error = alloc<ObjCObjectVar<NSError?>>()
        error.value = null
        val attributes = files.attributesOfItemAtPath(url.path!!, error.ptr)
        if (attributes == null) {
            check(
                error.value?.domain == NSCocoaErrorDomain &&
                    error.value?.code in setOf(NSFileReadNoSuchFileError, NSFileNoSuchFileError),
            ) { "无法检查本机通知存储" }
        }
        attributes
    }
}
