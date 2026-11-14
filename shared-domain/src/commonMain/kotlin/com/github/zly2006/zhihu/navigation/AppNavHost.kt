/*
 * Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation (version 3 only).
 */

package com.github.zly2006.zhihu.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack
import kotlin.random.Random

/** 每次访问独立持有 ViewModel 与保存状态，不复用内容的 equals。 */
@Serializable
sealed interface AppNavEntry : NavKey {
    val id: String
    val destination: NavDestination

    @Serializable
    data class Page(
        override val id: String,
        override val destination: NavDestination,
    ) : AppNavEntry

    /** 库按 key 类型选择转场，阅读与搜索须保留各自的配置。 */
    @Serializable
    data class Reading(
        override val id: String,
        override val destination: Article,
    ) : AppNavEntry

    @Serializable
    data class Searching(
        override val id: String,
        override val destination: Search,
    ) : AppNavEntry

    @Serializable
    data class Root(
        override val id: String,
        override val destination: NavDestination,
    ) : AppNavEntry
}

/** 仅主壳与详情栏根唯一；普通页面每次打开都有独立 ID。 */
class AppNavHost internal constructor(
    internal val entries: NavBackStack,
) {
    val backStack: List<NavDestination>
        get() = entries.map { (it as AppNavEntry).destination }

    val pageEntries: Sequence<AppNavEntry>
        get() = entries.asSequence().map { it as AppNavEntry }

    val top: NavDestination?
        get() = (entries.lastOrNull() as? AppNavEntry)?.destination

    val topEntryId: String?
        get() = (entries.lastOrNull() as? AppNavEntry)?.id

    fun contains(route: NavDestination): Boolean = entries.any { (it as AppNavEntry).destination == route }

    fun containsEntry(id: String): Boolean = entries.any { (it as AppNavEntry).id == id }

    fun open(route: NavDestination) {
        if (route == MainTabs || route == EmptyDetail) {
            if (backStack.firstOrNull() == route) popToRoot()
            return
        }
        val id = Random.nextLong().toString(16) + Random.nextLong().toString(16)
        entries.add(
            when (route) {
                is Article -> AppNavEntry.Reading(id, route)
                is Search -> AppNavEntry.Searching(id, route)
                else -> AppNavEntry.Page(id, route)
            },
        )
    }

    fun back(): Boolean {
        if (entries.size <= 1) return false
        entries.removeAt(entries.lastIndex)
        return true
    }

    fun popToRoot() {
        while (entries.size > 1) entries.removeAt(entries.lastIndex)
    }

    fun onBack() {
        back()
    }
}

@Composable
fun rememberAppNavHost(startDestination: NavDestination): AppNavHost = key("page-instance-stack-v1") {
    val stack = rememberNavBackStack<AppNavEntry>(AppNavEntry.Root("root", startDestination))
    remember(stack) { AppNavHost(stack) }
}
