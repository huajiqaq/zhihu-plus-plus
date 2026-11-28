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

package com.github.zly2006.zhihu.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import com.github.zly2006.zhihu.editor.rememberImagePickerLauncher
import com.github.zly2006.zhihu.i18n.PlatformStrings
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.CoreImage.CIDetector
import platform.CoreImage.CIDetectorAccuracy
import platform.CoreImage.CIDetectorAccuracyHigh
import platform.CoreImage.CIDetectorTypeQRCode
import platform.CoreImage.CIImage
import platform.CoreImage.CIQRCodeFeature
import platform.Foundation.NSData
import platform.Foundation.dataWithBytes

actual val isAccountQrScanSupported: Boolean = true

/** 扫描只交付可审核的登录页面，不根据二维码自动确认登录或提交凭据。 */
@Composable
actual fun rememberAccountQrScanAction(): () -> Unit {
    val scope = rememberCoroutineScope()
    val messages = rememberUserMessageSink()
    val open = LocalAccountQrConfirmationOpener.current
    val picker = rememberImagePickerLauncher { picked ->
        scope.launch {
            try {
                val url = withContext(Dispatchers.Default) {
                    require(picked.bytes.isNotEmpty() && picked.bytes.size <= 32 * 1024 * 1024)
                    val data = picked.bytes.usePinned { NSData.dataWithBytes(it.addressOf(0), picked.bytes.size.toULong()) }
                    val image = CIImage.imageWithData(data) ?: return@withContext null
                    val detector = CIDetector.detectorOfType(CIDetectorTypeQRCode, null, mapOf(CIDetectorAccuracy to CIDetectorAccuracyHigh))
                    detector?.featuresInImage(image)?.filterIsInstance<CIQRCodeFeature>()
                        ?.mapNotNull { it.messageString }
                        ?.firstOrNull { it.length <= 8192 && it.startsWith("https://www.zhihu.com/account/scan/login/") }
                }
                if (url == null || open == null) messages.showShortMessage(PlatformStrings.scanQrFailed) else open(url)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                messages.showShortMessage(PlatformStrings.scanQrFailed)
            }
        }
    }
    return picker::launch
}
