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
import androidx.compose.ui.uikit.LocalUIViewController
import com.github.zly2006.zhihu.i18n.DomainStrings
import com.github.zly2006.zhihu.viewmodel.ArticleExportPermission
import com.github.zly2006.zhihu.viewmodel.ArticleExportResult
import com.github.zly2006.zhihu.viewmodel.ensureExportAccountActive
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGImageRetain
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.Photos.PHAccessLevelAddOnly
import platform.Photos.PHAssetChangeRequest
import platform.Photos.PHAuthorizationStatusAuthorized
import platform.Photos.PHAuthorizationStatusLimited
import platform.Photos.PHAuthorizationStatusNotDetermined
import platform.Photos.PHPhotoLibrary
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationState.UIApplicationStateActive
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UIKit.endEditing
import platform.UIKit.popoverPresentationController
import platform.WebKit.WKSnapshotConfiguration
import platform.WebKit.WKWebView
import platform.darwin.NSObject

@Composable
fun NativeFileDeliveryHost(content: @Composable () -> Unit) {
    val controller = LocalUIViewController.current
    val delivery = remember(controller) { IosFileDelivery(controller) }
    DisposableEffect(delivery) { onDispose { delivery.dispose() } }
    CompositionLocalProvider(LocalNativeFileDelivery provides delivery, content = content)
}

private class IosFileDelivery(private val host: UIViewController) : NativeFileDelivery {
    private val presentation = Mutex()
    private var activeController: UIViewController? = null
    private var activeDelegate: NSObject? = null
    private var cancelPending: (() -> Unit)? = null
    private var disposed = false
    private val exportViews = mutableSetOf<WKWebView>()

    override suspend fun ensureImagePermission(): ArticleExportPermission = withContext(Dispatchers.Main) {
        check(!disposed) { DomainStrings.exportHostUnavailable }
        val status = PHPhotoLibrary.authorizationStatusForAccessLevel(PHAccessLevelAddOnly)
        val granted = if (status == PHAuthorizationStatusNotDetermined) {
            suspendCancellableCoroutine { continuation ->
                PHPhotoLibrary.requestAuthorizationForAccessLevel(PHAccessLevelAddOnly) { value ->
                    if (continuation.isActive) continuation.resume(value == PHAuthorizationStatusAuthorized || value == PHAuthorizationStatusLimited)
                }
            }
        } else {
            status == PHAuthorizationStatusAuthorized || status == PHAuthorizationStatusLimited
        }
        if (granted) ArticleExportPermission.Granted else ArticleExportPermission.Denied
    }

    override suspend fun saveFile(filePath: String, image: Boolean): ArticleExportResult {
        if (image) return savePhoto(filePath)
        val lease = NativeDeliveryLease.copyOf(filePath)
        var handedOff = false
        try {
            return presentation.withLock {
                withContext(Dispatchers.Main) {
                    checkPresentationHost()
                    ensureExportAccountActive()
                    try {
                        suspendCancellableCoroutine { continuation ->
                            val picker = UIDocumentPickerViewController(forExportingURLs = listOf(NSURL.fileURLWithPath(lease.filePath)), asCopy = true)
                            val delegate = object : NSObject(), UIDocumentPickerDelegateProtocol {
                                override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
                                    lease.release()
                                    if (continuation.isActive) continuation.resume(
                                        if (didPickDocumentsAtURLs.isEmpty()) ArticleExportResult.Cancelled
                                        else ArticleExportResult.Completed(DomainStrings.fileSavedToFiles),
                                    )
                                }

                                override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
                                    lease.release()
                                    if (continuation.isActive) continuation.resume(ArticleExportResult.Cancelled)
                                }
                            }
                            activeDelegate = delegate
                            activeController = picker
                            picker.delegate = delegate
                            val cancel = {
                                picker.dismissViewControllerAnimated(false) {
                                    if (continuation.isActive) continuation.resume(ArticleExportResult.Cancelled)
                                }
                            }
                            cancelPending = cancel
                            continuation.invokeOnCancellation { NSOperationQueue.mainQueue.addOperationWithBlock(cancel) }
                            handedOff = true
                            host.presentViewController(picker, true, null)
                        }
                    } finally {
                        cancelPending = null
                        activeDelegate = null
                        activeController = null
                    }
                }
            }
        } finally {
            if (!handedOff) lease.release()
        }
    }

    private suspend fun savePhoto(filePath: String): ArticleExportResult {
        check(ensureImagePermission() == ArticleExportPermission.Granted) { DomainStrings.photosPermissionDenied }
        ensureExportAccountActive()
        // PhotoKit 没有撤销进行中写入的 API；等待其读取完源文件再允许任务清理。
        val result = withContext(NonCancellable) {
            ensureExportAccountActive()
            suspendCancellableCoroutine<ArticleExportResult> { continuation ->
                var requested = false
                PHPhotoLibrary.sharedPhotoLibrary().performChanges({
                    requested = PHAssetChangeRequest.creationRequestForAssetFromImageAtFileURL(NSURL.fileURLWithPath(filePath)) != null
                }) { success, error ->
                    if (success && requested) continuation.resume(ArticleExportResult.Completed(DomainStrings.imageSavedToPhotos))
                    else continuation.resumeWithException(IllegalStateException(error?.localizedDescription ?: DomainStrings.exportRenderFailed))
                }
            }
        }
        currentCoroutineContext().ensureActive()
        return result
    }

    override suspend fun shareFile(filePath: String): ArticleExportResult {
        val lease = NativeDeliveryLease.copyOf(filePath)
        return presentShare(listOf(NSURL.fileURLWithPath(lease.filePath)), filePath.substringAfterLast('/'), lease)
    }

    override suspend fun shareText(text: String, title: String): ArticleExportResult = presentShare(listOf(text), title, null)

    private suspend fun presentShare(items: List<Any>, title: String, lease: NativeDeliveryLease?): ArticleExportResult {
        var handedOff = false
        try {
            return presentation.withLock {
                withContext(Dispatchers.Main) {
                    checkPresentationHost()
                    ensureExportAccountActive()
                    try {
                        suspendCancellableCoroutine<ArticleExportResult> { continuation ->
                            val controller = UIActivityViewController(activityItems = items, applicationActivities = null)
                            controller.title = title
                            controller.popoverPresentationController?.let { popover ->
                                popover.sourceView = host.view
                                popover.sourceRect = host.view.bounds.useContents {
                                    CGRectMake(origin.x + size.width / 2, origin.y + size.height / 2, 1.0, 1.0)
                                }
                                popover.permittedArrowDirections = 0uL
                            }
                            activeController = controller
                            controller.completionWithItemsHandler = { _, completed, _, error ->
                                lease?.release()
                                if (continuation.isActive) {
                                    when {
                                        error != null -> continuation.resumeWithException(IllegalStateException(error.localizedDescription))
                                        completed -> continuation.resume(ArticleExportResult.Completed(DomainStrings.fileShared))
                                        else -> continuation.resume(ArticleExportResult.Cancelled)
                                    }
                                }
                            }
                            val cancel = {
                                controller.dismissViewControllerAnimated(false) {
                                    if (continuation.isActive) continuation.resume(ArticleExportResult.Cancelled)
                                }
                            }
                            cancelPending = cancel
                            continuation.invokeOnCancellation { NSOperationQueue.mainQueue.addOperationWithBlock(cancel) }
                            handedOff = true
                            host.presentViewController(controller, true, null)
                        }
                    } finally {
                        cancelPending = null
                        activeController = null
                    }
                }
            }
        } finally {
            if (!handedOff) lease?.release()
        }
    }

    private fun checkPresentationHost() {
        check(!disposed && UIApplication.sharedApplication.applicationState == UIApplicationStateActive &&
            host.view.window != null && !host.isBeingDismissed() && !host.isBeingPresented() && host.presentedViewController == null
        ) { DomainStrings.exportHostUnavailable }
        host.view.endEditing(true)
    }

    override fun attachExportView(webView: WKWebView): () -> Unit {
        check(!disposed && host.view.window != null) { DomainStrings.exportHostUnavailable }
        webView.userInteractionEnabled = false
        host.view.insertSubview(webView, atIndex = 0)
        exportViews.add(webView)
        return {
            webView.removeFromSuperview()
            exportViews.remove(webView)
        }
    }

    fun dispose() {
        disposed = true
        cancelPending?.invoke()
        exportViews.forEach { it.stopLoading(); it.removeFromSuperview() }
        exportViews.clear()
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
            val cgImage = image?.CGImage
            if (error != null || cgImage == null) {
                continuation.resumeWithException(IllegalStateException(error?.localizedDescription ?: DomainStrings.exportRenderFailed))
            } else {
                val owned = NativeExportSnapshot(checkNotNull(CGImageRetain(cgImage)))
                continuation.resume(owned, onCancellation = { _, value, _ -> value.close() })
            }
        }
    }
}
