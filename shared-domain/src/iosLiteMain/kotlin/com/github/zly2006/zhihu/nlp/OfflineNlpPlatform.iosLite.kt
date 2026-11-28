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

package com.github.zly2006.zhihu.nlp

actual fun createOfflineNlpPlatform(): OfflineNlpPlatform = IosLiteOfflineNlpPlatform

private object IosLiteOfflineNlpPlatform : OfflineNlpPlatform {
    override val unsupportedReason = OfflineNlpFailure(OfflineNlpErrorCode.IOS_LITE)

    override fun normalizeNfd(text: String): String = throw OfflineNlpException(unsupportedReason)

    override fun unicodeCategory(codePoint: Int): OfflineNlpUnicodeCategory = throw OfflineNlpException(unsupportedReason)

    override suspend fun prepareModel(
        spec: OfflineNlpModelSpec,
        onProgress: (OfflineNlpModelState) -> Unit,
    ): OfflineNlpModelAssets = throw OfflineNlpException(unsupportedReason)

    override suspend fun openSession(
        assets: OfflineNlpModelAssets,
        onInvalidated: (failure: OfflineNlpFailure?) -> Unit,
    ): OfflineNlpSession = throw OfflineNlpException(unsupportedReason)

    override suspend fun deleteModel(spec: OfflineNlpModelSpec): Unit = throw OfflineNlpException(unsupportedReason)
}
