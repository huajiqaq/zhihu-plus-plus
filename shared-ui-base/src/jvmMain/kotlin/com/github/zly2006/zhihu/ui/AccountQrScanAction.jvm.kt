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

package com.github.zly2006.zhihu.ui

import androidx.compose.runtime.Composable
import com.github.zly2006.zhihu.i18n.PlatformStrings
import com.github.zly2006.zhihu.platform.rememberFileImporter
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.imageio.ImageIO

actual val isAccountQrScanSupported: Boolean = true

@Composable
actual fun rememberAccountQrScanAction(): () -> Unit {
    val messages = rememberUserMessageSink()
    val open = LocalAccountQrConfirmationOpener.current
    return rememberFileImporter(
        title = PlatformStrings.scanQrImage,
        mimeTypes = listOf("image/png", "image/jpeg"),
        extensions = listOf("png", "jpg", "jpeg"),
        maxBytes = 32 * 1024 * 1024,
    ) { _, bytes ->
        val result = withContext(Dispatchers.IO) {
            runCatching {
                ImageIO.createImageInputStream(bytes.inputStream()).use { input ->
                    val readers = ImageIO.getImageReaders(input)
                    check(readers.hasNext())
                    val reader = readers.next()
                    try {
                        reader.input = input
                        val width = reader.getWidth(0)
                        val height = reader.getHeight(0)
                        require(width > 0 && height > 0 && width.toLong() * height <= 32_000_000L)
                        val image = reader.read(0)
                        val pixels = image.getRGB(0, 0, width, height, null, 0, width)
                        MultiFormatReader().decode(
                            BinaryBitmap(HybridBinarizer(RGBLuminanceSource(width, height, pixels))),
                            mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE), DecodeHintType.TRY_HARDER to true),
                        ).text
                    } finally { reader.dispose() }
                }
            }.getOrNull()
        }
        if (open != null && result != null && result.length <= 8192 && result.startsWith("https://www.zhihu.com/account/scan/login/")) open(result)
        else messages.showShortMessage(PlatformStrings.scanQrFailed)
    }
}
