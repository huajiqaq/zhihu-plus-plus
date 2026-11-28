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

@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.github.zly2006.zhihu.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.github.zly2006.zhihu.i18n.DomainStrings
import com.github.zly2006.zhihu.viewmodel.ArticleExportPermission
import com.github.zly2006.zhihu.viewmodel.ArticleExportResult
import com.github.zly2006.zhihu.viewmodel.ensureExportAccountActive
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import platform.AppKit.NSModalResponseOK
import platform.AppKit.NSSavePanel
import platform.AppKit.NSSharingService
import platform.AppKit.NSSharingServiceDelegateProtocol
import platform.AppKit.NSSharingServicePicker
import platform.AppKit.NSSharingServicePickerDelegateProtocol
import platform.AppKit.NSWindow
import platform.AppKit.NSWindowBelow
import platform.CoreGraphics.CGImageRetain
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSError
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.NSMaxYEdge
import platform.WebKit.WKSnapshotConfiguration
import platform.WebKit.WKWebView
import platform.darwin.NSObject

@Composable
fun NativeFileDeliveryHost(window: NSWindow, content: @Composable () -> Unit) {
    val delivery = remember(window) { MacosFileDelivery(window) }
    DisposableEffect(delivery) { onDispose { delivery.dispose() } }
    CompositionLocalProvider(LocalNativeFileDelivery provides delivery, content = content)
}

private class MacosFileDelivery(private val window: NSWindow) : NativeFileDelivery {
    private val presentation = Mutex()
    private var disposed = false
    private var activePanel: NSSavePanel? = null
    private var activePicker: NSSharingServicePicker? = null
    private var activeDelegate: NSObject? = null
    private var cancelPending: (() -> Unit)? = null
    private val exportViews = mutableSetOf<WKWebView>()

    override suspend fun ensureImagePermission(): ArticleExportPermission = ArticleExportPermission.Granted

    override suspend fun saveFile(filePath: String, image: Boolean): ArticleExportResult = presentation.withLock {
        val destination = withContext(Dispatchers.Main) {
            checkHost()
            try {
                suspendCancellableCoroutine<NSURL?> { continuation ->
                    val panel = NSSavePanel.savePanel()
                    activePanel = panel
                    panel.nameFieldStringValue = filePath.substringAfterLast('/')
                    panel.canCreateDirectories = true
                    panel.extensionHidden = false
                    panel.beginSheetModalForWindow(window) { response ->
                        if (continuation.isActive) continuation.resume(if (response == NSModalResponseOK) panel.URL else null)
                    }
                    continuation.invokeOnCancellation {
                        NSOperationQueue.mainQueue.addOperationWithBlock { panel.cancel(null) }
                    }
                }
            } finally {
                activePanel = null
            }
        } ?: return@withLock ArticleExportResult.Cancelled
        val scoped = destination.startAccessingSecurityScopedResource()
        try {
            val target = checkNotNull(destination.path) { DomainStrings.exportHostUnavailable }
            withContext(Dispatchers.Default) { copyExportFileAtomically(filePath, target) }
            ArticleExportResult.Completed(if (image) DomainStrings.imageSavedTo(target) else DomainStrings.htmlSavedTo(target))
        } finally {
            if (scoped) destination.stopAccessingSecurityScopedResource()
        }
    }

    override suspend fun shareFile(filePath: String): ArticleExportResult {
        val lease = NativeDeliveryLease.copyOf(filePath)
        return presentShare(listOf(NSURL.fileURLWithPath(lease.filePath)), lease)
    }

    override suspend fun shareText(text: String, title: String): ArticleExportResult = presentShare(listOf(text), null)

    private suspend fun presentShare(items: List<Any>, lease: NativeDeliveryLease?): ArticleExportResult {
        var handedOff = false
        try {
            return presentation.withLock {
                withContext(Dispatchers.Main) {
                    checkHost()
                    ensureExportAccountActive()
                    val parent = checkNotNull(window.contentView)
                    try {
                        suspendCancellableCoroutine { continuation ->
                            val picker = NSSharingServicePicker(items)
                            val delegate = object : NSObject(), NSSharingServicePickerDelegateProtocol, NSSharingServiceDelegateProtocol {
                                @ObjCSignatureOverride
                                override fun sharingServicePicker(sharingServicePicker: NSSharingServicePicker, didChooseSharingService: NSSharingService?) {
                                    if (didChooseSharingService == null) {
                                        lease?.release()
                                        if (continuation.isActive) continuation.resume(ArticleExportResult.Cancelled)
                                    }
                                }

                                @ObjCSignatureOverride
                                override fun sharingServicePicker(
                                    sharingServicePicker: NSSharingServicePicker,
                                    delegateForSharingService: NSSharingService,
                                ): NSSharingServiceDelegateProtocol = this

                                override fun sharingService(sharingService: NSSharingService, didShareItems: List<*>) {
                                    lease?.release()
                                    if (continuation.isActive) continuation.resume(ArticleExportResult.Completed(DomainStrings.fileShared))
                                }

                                override fun sharingService(sharingService: NSSharingService, didFailToShareItems: List<*>, error: NSError) {
                                    lease?.release()
                                    if (continuation.isActive) continuation.resumeWithException(IllegalStateException(error.localizedDescription))
                                }
                            }
                            activePicker = picker
                            activeDelegate = delegate
                            picker.delegate = delegate
                            val cancel = {
                                picker.close()
                                if (continuation.isActive) continuation.resume(ArticleExportResult.Cancelled)
                            }
                            cancelPending = cancel
                            continuation.invokeOnCancellation { NSOperationQueue.mainQueue.addOperationWithBlock(cancel) }
                            handedOff = true
                            parent.bounds.useContents {
                                picker.showRelativeToRect(
                                    CGRectMake(origin.x + size.width / 2, origin.y + size.height / 2, 1.0, 1.0), parent, NSMaxYEdge,
                                )
                            }
                        }
                    } finally {
                        cancelPending = null
                        activePicker = null
                        activeDelegate = null
                    }
                }
            }
        } finally {
            if (!handedOff) lease?.release()
        }
    }

    private fun checkHost() {
        check(!disposed && window.isVisible() && window.contentView != null && window.attachedSheet == null) {
            DomainStrings.exportHostUnavailable
        }
    }

    override fun attachExportView(webView: WKWebView): () -> Unit {
        checkHost()
        checkNotNull(window.contentView).addSubview(webView, positioned = NSWindowBelow, relativeTo = null)
        exportViews.add(webView)
        return {
            webView.removeFromSuperview()
            exportViews.remove(webView)
        }
    }

    fun dispose() {
        disposed = true
        activePanel?.cancel(null)
        cancelPending?.invoke()
        exportViews.forEach { it.stopLoading(); it.removeFromSuperview() }
        exportViews.clear()
    }
}

/** 只在用户选定目标后写同目录临时文件，成功才原子替换，失败不破坏原文件。 */
private suspend fun copyExportFileAtomically(source: String, destination: String) {
    val temporary = "$destination.${NSUUID().UUIDString}.tmp"
    try {
        SystemFileSystem.source(Path(source)).buffered().use { input ->
            SystemFileSystem.sink(Path(temporary)).buffered().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.readAtMostTo(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
            }
        }
        ensureExportAccountActive()
        SystemFileSystem.atomicMove(Path(temporary), Path(destination))
    } finally {
        if (SystemFileSystem.exists(Path(temporary))) SystemFileSystem.delete(Path(temporary))
    }
}

internal actual fun resizeNativeExportView(webView: WKWebView, width: Int, height: Int) {
    webView.setFrame(CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()))
}

internal actual suspend fun snapshotNativeExportView(
    webView: WKWebView,
    width: Int,
    height: Int,
    outputWidth: Int,
): NativeExportSnapshot = suspendCancellableCoroutine { continuation ->
    val configuration = WKSnapshotConfiguration().apply {
        rect = CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble())
        snapshotWidth = NSNumber(int = outputWidth)
        afterScreenUpdates = true
    }
    webView.takeSnapshotWithConfiguration(configuration) { image, error ->
        if (continuation.isActive) {
            val cgImage = image?.CGImageForProposedRect(null, null, null)
            if (error != null || cgImage == null) {
                continuation.resumeWithException(IllegalStateException(error?.localizedDescription ?: DomainStrings.exportRenderFailed))
            } else {
                val owned = NativeExportSnapshot(checkNotNull(CGImageRetain(cgImage)))
                continuation.resume(owned, onCancellation = { _, value, _ -> value.close() })
            }
        }
    }
}
