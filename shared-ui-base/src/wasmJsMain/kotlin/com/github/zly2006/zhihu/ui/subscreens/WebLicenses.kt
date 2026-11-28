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

@file:OptIn(ExperimentalWasmJsInterop::class)

package com.github.zly2006.zhihu.ui.subscreens

import androidx.compose.runtime.Composable
import com.github.zly2006.zhihu.i18n.ReaderStrings
import com.github.zly2006.zhihu.ui.ReaderDisplayFailure
import kotlinx.browser.window
import kotlinx.coroutines.await

@Composable
actual fun rememberLicenseManifestReader(): suspend () -> String = {
    val response = window.fetch("aboutlibraries.json").await()
    if (!response.ok) {
        throw ReaderDisplayFailure("$LICENSE_MANIFEST_MISSING_MESSAGE（HTTP ${response.status}）") {
            ReaderStrings.licenseHttpError(ReaderStrings.licenseManifestMissing, response.status)
        }
    }
    response.text().await().toString()
}
