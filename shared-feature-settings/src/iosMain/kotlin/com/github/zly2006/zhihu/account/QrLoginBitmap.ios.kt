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

package com.github.zly2006.zhihu.account

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.github.zly2006.zhihu.i18n.AccountStrings
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import org.jetbrains.skia.Image
import platform.CoreGraphics.CGAffineTransformMakeScale
import platform.CoreGraphics.CGImageRelease
import platform.CoreImage.CIContext
import platform.CoreImage.CIFilter
import platform.CoreImage.filterWithName
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Foundation.setValue
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation
import platform.darwin.NSObject

actual val supportedLoginMethods: List<LoginMethod> = listOf(
    LoginMethod.Phone,
    LoginMethod.Qr,
    LoginMethod.Web,
)

actual fun generateQrLoginBitmap(content: String): ImageBitmap {
    val message = NSString.create(string = content).dataUsingEncoding(NSUTF8StringEncoding)
        ?: throw LoginMessageException { AccountStrings.qrContentEncodingFailed }
    val filter = CIFilter.filterWithName("CIQRCodeGenerator")
        ?: throw LoginMessageException { AccountStrings.qrImageGenerationFailed }
    (filter as NSObject).setValue(message, forKey = "inputMessage")
    (filter as NSObject).setValue("M", forKey = "inputCorrectionLevel")
    val output = filter.outputImage?.imageByApplyingTransform(CGAffineTransformMakeScale(10.0, 10.0))
        ?: throw LoginMessageException { AccountStrings.qrImageGenerationFailed }
    val image = CIContext.contextWithOptions(null).createCGImage(output, fromRect = output.extent)
        ?: throw LoginMessageException { AccountStrings.qrImageConversionFailed }
    try {
        val data = UIImagePNGRepresentation(UIImage.imageWithCGImage(image))
            ?: throw LoginMessageException { AccountStrings.qrImageEncodingFailed }
        val bytes = data.bytes?.reinterpret<ByteVar>()?.readBytes(data.length.toInt())
            ?: throw LoginMessageException { AccountStrings.qrImageDataEmpty }
        return Image.makeFromEncoded(bytes).toComposeImageBitmap()
    } finally {
        CGImageRelease(image)
    }
}
