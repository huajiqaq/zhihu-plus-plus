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

package com.github.zly2006.zhihu.platform

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.uikit.LocalUIViewController
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.launch
import kotlinx.io.RawSource
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIPasteboard
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeData
import platform.darwin.NSObject

actual val nativeIsDesktop: Boolean = false

actual val platformName: String = "iOS"

actual val isAigcVoteSupported: Boolean = false

@Composable
@OptIn(ExperimentalForeignApi::class)
actual fun rememberExternalUrlOpener(): ExternalUrlOpener {
    val messages = rememberUserMessageSink()
    return remember(messages) {
        object : ExternalUrlOpener {
            override fun invoke(url: String) {
                NSOperationQueue.mainQueue.addOperationWithBlock {
                    val target = NSURL.URLWithString(url)
                    if (target == null || target.scheme.isNullOrBlank()) {
                        messages.showShortMessage("链接地址无效")
                    } else {
                        UIApplication.sharedApplication.openURL(target, options = emptyMap<Any?, Any>()) { opened ->
                            if (!opened) messages.showShortMessage("系统无法打开此链接")
                        }
                    }
                }
            }
        }
    }
}

internal actual fun copyNativePlainText(text: String) {
    UIPasteboard.generalPasteboard.string = text
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun nativeAccountFilePath(): String = "${nativeAppPrivateDirectoryPath()}/account.json"

@OptIn(ExperimentalForeignApi::class)
internal actual fun nativeAppPrivateDirectoryPath(): String {
    val urls = NSFileManager.defaultManager.URLsForDirectory(NSDocumentDirectory, NSUserDomainMask)
    return (urls.firstOrNull() as? NSURL)?.path ?: NSTemporaryDirectory()
}

internal actual fun nativeDownloadsDirectoryPath(): String = "${nativeAppPrivateDirectoryPath()}/Downloads"

@Composable
@OptIn(ExperimentalForeignApi::class)
internal actual fun rememberFilePicker(
    title: String,
    mimeTypes: List<String>,
    extensions: List<String>,
    onPicked: (() -> Pair<String, RawSource>) -> Unit,
): () -> Unit {
    val host = LocalUIViewController.current
    val currentOnPicked by rememberUpdatedState(onPicked)
    var activePicker by remember { mutableStateOf<UIDocumentPickerViewController?>(null) }
    val delegate = remember {
        object : NSObject(), UIDocumentPickerDelegateProtocol {
            override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
                activePicker = null
                val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL ?: return
                currentOnPicked {
                    val file = Path(checkNotNull(url.path))
                    val source = SystemFileSystem.source(file)
                    // 复制模式返回应用持有的临时文件，关闭时不会删除提供方的原文件。
                    (url.lastPathComponent ?: "所选文件") to object : RawSource by source {
                        override fun close() {
                            try {
                                source.close()
                            } finally {
                                runCatching { SystemFileSystem.delete(file, mustExist = false) }
                            }
                        }
                    }
                }
            }

            override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
                activePicker = null
            }
        }
    }
    DisposableEffect(host) {
        onDispose {
            activePicker?.delegate = null
            activePicker?.dismissViewControllerAnimated(false, completion = null)
            activePicker = null
        }
    }
    return {
        if (activePicker == null) {
            val types = extensions
                .mapNotNull { UTType.typeWithFilenameExtension(it) }
                .ifEmpty { mimeTypes.mapNotNull { UTType.typeWithMIMEType(it) } }
                .ifEmpty { listOf(UTTypeData) }
            val picker = UIDocumentPickerViewController(forOpeningContentTypes = types, asCopy = true)
            picker.title = title
            picker.allowsMultipleSelection = false
            picker.shouldShowFileExtensions = true
            picker.delegate = delegate
            activePicker = picker
            host.presentViewController(picker, animated = true, completion = null)
        }
    }
}

private val LocalIosUserMessages = staticCompositionLocalOf<UserMessageSink> { error("iOS 消息宿主未安装") }

@Composable
actual fun rememberUserMessageSink(): UserMessageSink = LocalIosUserMessages.current

/** 短消息随当前窗口排队和销毁；只有提示自身避让安全区，不改变页面布局。 */
@Composable
fun IosUserMessageHost(content: @Composable () -> Unit) {
    val state = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val messages = remember(state, scope) {
        object : UserMessageSink {
            override fun showShortMessage(message: String) {
                scope.launch { state.showSnackbar(message, duration = SnackbarDuration.Short) }
            }

            override fun showLongMessage(message: String) {
                scope.launch { state.showSnackbar(message, duration = SnackbarDuration.Long) }
            }
        }
    }
    CompositionLocalProvider(LocalIosUserMessages provides messages) {
        Box(Modifier.fillMaxSize()) {
            content()
            SnackbarHost(state, Modifier.align(Alignment.BottomCenter).safeDrawingPadding())
        }
    }
}

actual val platformBottomBarItemLimit: Int? = 5
