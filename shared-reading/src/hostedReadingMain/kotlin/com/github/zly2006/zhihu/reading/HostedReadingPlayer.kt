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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.github.zly2006.zhihu.reading

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import com.github.zly2006.zhihu.account.rememberZhihuAccountStore
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.viewmodel.rememberPaginationEnvironment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

private val LocalReadingPlayer = staticCompositionLocalOf<ReadingPlayerController?> { null }

internal expect fun createReadingSpeechEngine(scope: CoroutineScope): ReadingSpeechEngine

@Composable
actual fun rememberReadingPlayerController(): ReadingPlayerController? = LocalReadingPlayer.current

@Composable
actual fun ReadingPlayerHost(content: @Composable () -> Unit) {
    val store = rememberZhihuAccountStore()
    val accounts by store.accountsState.collectAsState()
    val owner = remember(store, accounts) { store.client }
    val environment = rememberPaginationEnvironment(allowGuestAccess = true)
    val messages = rememberUserMessageSink()
    val scope = remember(owner, environment, messages) { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    val player = remember(owner, environment, messages) {
        if (isReadingPlayerSupported) ContinuousReadingPlayer(
            scope, ReadingContentResolver(environment), createReadingSpeechEngine(scope),
            showMessage = messages::showShortMessage,
        ) else null
    }
    DisposableEffect(player, scope) {
        onDispose {
            player?.close()
            scope.cancel()
        }
    }
    LaunchedEffect(player) { player?.initialize() }
    CompositionLocalProvider(LocalReadingPlayer provides player, content = content)
}
