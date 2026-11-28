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

package com.github.zly2006.zhihu.viewmodel

/** 一次性加载三态；失败与成功空值不能混同。 */
sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>

    class Failed(
        private val text: () -> String,
    ) : LoadState<Nothing> {
        constructor(message: String) : this({ message })

        val message: String get() = text()
    }

    data class Ready<T>(
        val value: T,
    ) : LoadState<T>
}

val <T> LoadState<T>.readyValue: T?
    get() = (this as? LoadState.Ready<T>)?.value
