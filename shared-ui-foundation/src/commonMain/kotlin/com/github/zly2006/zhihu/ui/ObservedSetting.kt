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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.github.zly2006.zhihu.platform.SettingsStore

@Composable
fun <T> rememberObservedSetting(
    settings: SettingsStore,
    key: String,
    read: SettingsStore.() -> T,
): MutableState<T> {
    val state = remember(settings, key) { mutableStateOf(settings.read()) }
    DisposableEffect(settings, key, state) {
        val subscription = settings.observeKeyChanges { changedKey ->
            if (changedKey == key) state.value = settings.read()
        }
        onDispose(subscription::close)
    }
    return state
}

/** 即时写回并观察外部修改；需要确认后保存的输入应使用本地状态。 */
@Composable
fun rememberSetting(settings: SettingsStore, key: String, defaultValue: Boolean): MutableState<Boolean> =
    rememberSettingBinding(settings, key, { getBoolean(key, defaultValue) }, { putBoolean(key, it) })

@Composable
fun rememberSetting(settings: SettingsStore, key: String, defaultValue: Int): MutableState<Int> =
    rememberSettingBinding(settings, key, { getInt(key, defaultValue) }, { putInt(key, it) })

@Composable
fun rememberSetting(settings: SettingsStore, key: String, defaultValue: Float): MutableState<Float> =
    rememberSettingBinding(settings, key, { getFloat(key, defaultValue) }, { putFloat(key, it) })

@Composable
fun rememberSetting(settings: SettingsStore, key: String, defaultValue: String): MutableState<String> =
    rememberSettingBinding(settings, key, { getString(key, defaultValue) }, { putString(key, it) })

@Composable
private fun <T> rememberSettingBinding(
    settings: SettingsStore,
    key: String,
    read: SettingsStore.() -> T,
    write: SettingsStore.(T) -> Unit,
): MutableState<T> {
    val observed = rememberObservedSetting(settings, key, read)
    return remember(settings, key, observed) { WriteThroughSetting(observed) { value -> settings.write(value) } }
}

private class WriteThroughSetting<T>(
    private val observed: MutableState<T>,
    private val persist: (T) -> Unit,
) : MutableState<T> {
    override var value: T
        get() = observed.value
        set(newValue) {
            observed.value = newValue
            persist(newValue)
        }

    override fun component1(): T = value

    override fun component2(): (T) -> Unit = { value = it }
}
