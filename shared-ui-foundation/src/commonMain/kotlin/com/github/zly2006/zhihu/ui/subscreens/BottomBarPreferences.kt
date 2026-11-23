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

package com.github.zly2006.zhihu.ui.subscreens

import com.github.zly2006.zhihu.navigation.Account
import com.github.zly2006.zhihu.navigation.Daily
import com.github.zly2006.zhihu.navigation.Follow
import com.github.zly2006.zhihu.navigation.Home
import com.github.zly2006.zhihu.navigation.HotList
import com.github.zly2006.zhihu.navigation.MyCollections
import com.github.zly2006.zhihu.navigation.OnlineHistory
import com.github.zly2006.zhihu.navigation.TopLevelDestination

const val PREF_BLOCK_SPACING = "contentBlockSpacing"
const val START_DESTINATION_PREFERENCE_KEY = "startDestination"
const val BOTTOM_BAR_ITEMS_PREFERENCE_KEY = "bottom_bar_items"
const val BOTTOM_BAR_ITEM_ORDER_PREFERENCE_KEY = "bottom_bar_item_order"
const val COLLECTION_DIRECT_BROWSE_PREFERENCE_KEY = "collectionDirectBrowse"
const val LANDSCAPE_LIST_DETAIL_PREFERENCE_KEY = "landscapeListDetail"
private const val BOTTOM_BAR_ITEM_ORDER_SEPARATOR = ","

private val topLevelDestinationsInOrder: List<Pair<String, TopLevelDestination>> = listOf(
    Home.name to Home,
    Follow.name to Follow,
    HotList.name to HotList,
    Daily.name to Daily,
    OnlineHistory.name to OnlineHistory,
    MyCollections.name to MyCollections,
    Account.name to Account,
)

fun navDestinationFromName(name: String): TopLevelDestination = topLevelDestinationsInOrder
    .firstOrNull { it.first == name }
    ?.second
    ?: Home

fun resolveValidStartDestinationKey(
    preferredKey: String?,
    availableKeysInOrder: List<String>,
): String = when {
    !preferredKey.isNullOrEmpty() && preferredKey in availableKeysInOrder -> preferredKey
    availableKeysInOrder.isNotEmpty() -> availableKeysInOrder.first()
    else -> Home.name
}

fun defaultBottomBarSelectionKeys(
    maximumSelection: Int? = 5,
): Set<String> = if (maximumSelection == null) {
    linkedSetOf(Home.name, Follow.name, HotList.name, Daily.name, OnlineHistory.name, MyCollections.name)
} else {
    linkedSetOf(Home.name, Follow.name, Daily.name)
}

/** 移动端历史位于账号面板；桌面可将它保留为导航快捷入口。 */
fun availableBottomBarItemKeys(maximumSelection: Int?): List<String> = topLevelDestinationsInOrder
    .map { it.first }
    .filterNot { maximumSelection != null && it == OnlineHistory.name }

fun normalizeBottomBarSelection(
    selectedKeys: Collection<String>,
    enforceMinimumSelection: Boolean = false,
    maximumSelection: Int? = 5,
): Set<String> {
    val allowedKeys = availableBottomBarItemKeys(maximumSelection)
    val normalized = selectedKeys
        .filterTo(linkedSetOf()) { it in allowedKeys }
        .ifEmpty { defaultBottomBarSelectionKeys(maximumSelection).toMutableSet() }

    /** 主页头像是设置入口；没有主页时必须保留账号页出口。 */
    if (Home.name !in normalized && Account.name !in normalized) {
        normalized.add(Account.name)
    }

    while (maximumSelection != null && normalized.size > maximumSelection) {
        val removableKey = listOf(
            HotList.name,
            MyCollections.name,
            OnlineHistory.name,
            Daily.name,
            Follow.name,
            Home.name,
        ).firstOrNull { it in normalized } ?: break
        normalized.remove(removableKey)
    }

    if (enforceMinimumSelection) {
        listOf(Home.name, Follow.name, Daily.name, HotList.name, OnlineHistory.name, MyCollections.name).forEach { key ->
            if (normalized.size < 3) {
                normalized.add(key)
            }
        }
    }

    return normalized
}

fun normalizeBottomBarItemOrder(
    preferredOrderKeys: List<String>,
    selectedKeys: Set<String>,
): List<String> {
    val allowedKeys = topLevelDestinationsInOrder.map { it.first }.toSet()
    val orderedKeys = mutableListOf<String>()
    preferredOrderKeys.forEach { key ->
        if (key in allowedKeys && key in selectedKeys && key !in orderedKeys) {
            orderedKeys.add(key)
        }
    }
    topLevelDestinationsInOrder.forEach { (key, _) ->
        if (key in selectedKeys && key !in orderedKeys) {
            orderedKeys.add(key)
        }
    }
    return orderedKeys
}

fun bottomBarItemOrderPreferenceValue(keys: List<String>): String =
    keys.joinToString(BOTTOM_BAR_ITEM_ORDER_SEPARATOR)

fun bottomBarItemOrderFromPreference(
    preferenceValue: String?,
    selectedKeys: Set<String>,
): List<String> = normalizeBottomBarItemOrder(
    preferenceValue
        .orEmpty()
        .split(BOTTOM_BAR_ITEM_ORDER_SEPARATOR)
        .map { it.trim() }
        .filter { it.isNotEmpty() },
    selectedKeys,
)

fun shouldShowAccountHistoryShortcut(
    selectedKeys: Set<String>,
): Boolean = OnlineHistory.name !in selectedKeys
