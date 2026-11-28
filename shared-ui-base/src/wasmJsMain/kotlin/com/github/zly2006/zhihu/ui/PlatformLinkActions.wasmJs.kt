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
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.github.zly2006.zhihu.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.github.zly2006.zhihu.i18n.PlatformStrings
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

@Composable
internal actual fun rememberPlatformClipboardReader(): suspend () -> String? = remember {
    {
        suspendCancellableCoroutine { continuation ->
            readClipboardText { success, text ->
                if (continuation.isActive) {
                    if (success) continuation.resume(text)
                    else continuation.resumeWithException(IllegalStateException(PlatformStrings.clipboardLinkUnavailable))
                }
            }
        }
    }
}

@JsFun(
    """(done) => {
        if (!navigator.userActivation?.isActive || typeof navigator.clipboard?.readText !== 'function') {
            done(false, ''); return;
        }
        try {
            navigator.clipboard.readText().then(text => {
                if (typeof text === 'string' && text.length <= 8192) done(true, text);
                else done(false, '');
            }, () => done(false, ''));
        } catch { done(false, ''); }
    }""",
)
private external fun readClipboardText(done: (Boolean, String) -> Unit)
