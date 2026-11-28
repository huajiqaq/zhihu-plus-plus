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

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.github.zly2006.zhihu.i18n.AccountStrings
import com.github.zly2006.zhihu.platform.platformName
import kotlin.io.encoding.Base64
import org.jetbrains.skia.Image

actual val isLoginRiskControlSupported: Boolean = false

actual val phoneLoginDeviceInfo: ZhihuPhoneLoginDeviceInfo
    get() = ZhihuPhoneLoginDeviceInfo(
        timezoneOffsetSeconds = 0,
        appInstallTimeMillis = 0,
        notificationEnabled = false,
        bluetoothAvailable = false,
        phoneBrand = platformName,
        phoneModel = platformName,
        androidRelease = "12",
        cpuType = platformName,
        cpuCount = 1,
        cpuUsage = "0.0",
        totalMemoryMegabytes = 0,
        freeMemoryMegabytes = 0,
        totalStorageMegabytes = 0,
        freeStorageMegabytes = 0,
    )

actual fun decodePhoneLoginCaptchaImage(content: String) = runCatching {
    val encoded = content.substringAfter("base64,", content)
    Image.makeFromEncoded(Base64.Default.decode(encoded)).toComposeImageBitmap()
}.getOrNull()

@Composable
actual fun QrLoginPane(onLoginSuccess: (String) -> Unit) = AccountQrLoginPane(onLoginSuccess)

@Composable
actual fun WebLoginPane(onLoginSuccess: (String) -> Unit, onBrowserSessionVerified: () -> Unit): Unit =
    error(AccountStrings.webLoginUnsupported(platformName))

internal actual fun readLoginRiskControlCookies(url: String): Map<String, String> =
    error(AccountStrings.verificationPlatformUnsupported(platformName))

@Composable
actual fun LoginRiskControlPane(
    url: String,
    cookies: Map<String, String>,
    onCookiesChanged: (Map<String, String>) -> Unit,
    onVerificationComplete: () -> Unit,
): Unit = error(AccountStrings.verificationPlatformUnsupported(platformName))
