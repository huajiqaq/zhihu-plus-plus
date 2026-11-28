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

package com.github.zly2006.zhihu.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.uikit.LocalUIViewController
import com.github.zly2006.zhihu.i18n.EditorStrings
import com.github.zly2006.zhihu.i18n.PlatformStrings
import com.github.zly2006.zhihu.platform.rememberFileImporter
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import platform.Foundation.NSOperationQueue
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIAlertAction
import platform.UIKit.UIAlertActionStyleCancel
import platform.UIKit.UIAlertActionStyleDefault
import platform.UIKit.UIAlertController
import platform.UIKit.UIAlertControllerStyleAlert
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTType
import platform.darwin.NSObject

/** PHPicker 不请求全图库访问；只读取用户明确选择的资产，Files 保留为独立入口。 */
@Composable
internal actual fun rememberNativeImagePicker(onPicked: (PickedImage) -> Unit): ImagePickerLauncher {
    val host = LocalUIViewController.current
    val currentOnPicked by rememberUpdatedState(onPicked)
    val messages = rememberUserMessageSink()
    val filePicker = rememberFileImporter(
        title = EditorStrings.chooseImage,
        mimeTypes = listOf("image/jpeg", "image/png", "image/gif", "image/webp"),
        extensions = listOf("jpg", "jpeg", "png", "gif", "webp"),
    ) { name, bytes -> currentOnPicked(PickedImage(bytes, guessMimeTypeFromFileName(name), name)) }
    val currentFilePicker by rememberUpdatedState(filePicker)
    val owner = remember(host) { IosPhotoPickerOwner() }
    val delegate = remember(owner) {
        object : NSObject(), PHPickerViewControllerDelegateProtocol {
            override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
                picker.dismissViewControllerAnimated(true, null)
                owner.controller = null
                val result = didFinishPicking.firstOrNull() as? PHPickerResult ?: return
                val provider = result.itemProvider
                val type = provider.registeredTypeIdentifiers.filterIsInstance<String>().firstOrNull {
                    UTType.typeWithIdentifier(it)?.preferredMIMEType in setOf("image/jpeg", "image/png", "image/gif", "image/webp")
                } ?: "public.image"
                provider.loadDataRepresentationForTypeIdentifier(type) { data, error ->
                    val mime = UTType.typeWithIdentifier(type)?.preferredMIMEType
                    val normalized = if (mime == null && data != null) UIImage(data = data)?.let(::UIImagePNGRepresentation) else data
                    val bytes = normalized?.takeIf { it.length in 1uL..(32uL * 1024uL * 1024uL) }
                        ?.let { value -> value.bytes?.reinterpret<ByteVar>()?.readBytes(value.length.toInt()) }
                    NSOperationQueue.mainQueue.addOperationWithBlock {
                        if (!owner.disposed) {
                            if (error != null || bytes == null) messages.showShortMessage(PlatformStrings.photoReadFailed)
                            else {
                                val extension = UTType.typeWithIdentifier(type)?.preferredFilenameExtension ?: "png"
                                currentOnPicked(PickedImage(bytes, mime ?: "image/png", "photo.$extension"))
                            }
                        }
                    }
                }
            }
        }
    }
    val cameraDelegate = remember(owner) {
        object : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {
            override fun imagePickerController(picker: UIImagePickerController, didFinishPickingMediaWithInfo: Map<Any?, *>) {
                val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
                picker.dismissViewControllerAnimated(true, null)
                owner.controller = null
                val data = image?.let(::UIImagePNGRepresentation)
                val bytes = data?.takeIf { it.length in 1uL..(32uL * 1024uL * 1024uL) }
                    ?.let { it.bytes?.reinterpret<ByteVar>()?.readBytes(it.length.toInt()) }
                if (!owner.disposed) {
                    if (bytes == null) messages.showShortMessage(PlatformStrings.photoReadFailed)
                    else currentOnPicked(PickedImage(bytes, "image/png", "camera.png"))
                }
            }

            override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
                picker.dismissViewControllerAnimated(true, null)
                owner.controller = null
            }
        }
    }
    DisposableEffect(owner) {
        onDispose {
            owner.disposed = true
            owner.controller?.dismissViewControllerAnimated(false, null)
            owner.controller = null
        }
    }
    return remember(host, owner, delegate, cameraDelegate) {
        ImagePickerLauncher {
            if (!owner.disposed && owner.controller == null) {
                var presenter = host
                while (presenter.presentedViewController != null) presenter = presenter.presentedViewController!!
                val chooser = UIAlertController.alertControllerWithTitle(PlatformStrings.chooseImageSource, null, UIAlertControllerStyleAlert)
                chooser.addAction(UIAlertAction.actionWithTitle(PlatformStrings.photoLibrary, UIAlertActionStyleDefault) {
                    owner.controller = null
                    val config = PHPickerConfiguration().apply {
                        selectionLimit = 1
                        filter = PHPickerFilter.imagesFilter
                    }
                    val picker = PHPickerViewController(config)
                    picker.delegate = delegate
                    owner.controller = picker
                    chooser.dismissViewControllerAnimated(true) {
                        if (!owner.disposed) presenter.presentViewController(picker, true, null)
                    }
                })
                chooser.addAction(UIAlertAction.actionWithTitle(PlatformStrings.files, UIAlertActionStyleDefault) {
                    owner.controller = null
                    chooser.dismissViewControllerAnimated(true) { if (!owner.disposed) currentFilePicker() }
                })
                if (UIImagePickerController.isSourceTypeAvailable(UIImagePickerControllerSourceTypeCamera)) {
                    chooser.addAction(UIAlertAction.actionWithTitle(PlatformStrings.camera, UIAlertActionStyleDefault) {
                        owner.controller = null
                        chooser.dismissViewControllerAnimated(true) {
                            val presentCamera = {
                                if (!owner.disposed) {
                                    val camera = UIImagePickerController().apply {
                                        sourceType = UIImagePickerControllerSourceTypeCamera
                                        setDelegate(cameraDelegate)
                                    }
                                    owner.controller = camera
                                    presenter.presentViewController(camera, true, null)
                                }
                            }
                            when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
                                AVAuthorizationStatusAuthorized -> presentCamera()
                                AVAuthorizationStatusNotDetermined -> AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
                                    NSOperationQueue.mainQueue.addOperationWithBlock {
                                        if (!owner.disposed) {
                                            if (granted) presentCamera() else messages.showShortMessage(PlatformStrings.cameraPermissionRequired)
                                        }
                                    }
                                }
                                else -> messages.showShortMessage(PlatformStrings.cameraPermissionRequired)
                            }
                        }
                    })
                }
                chooser.addAction(UIAlertAction.actionWithTitle(PlatformStrings.cancel, UIAlertActionStyleCancel) { owner.controller = null })
                owner.controller = chooser
                presenter.presentViewController(chooser, true, null)
            }
        }
    }
}

private class IosPhotoPickerOwner {
    var controller: UIViewController? = null
    var disposed = false
}
