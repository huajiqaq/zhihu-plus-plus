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

@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.github.zly2006.zhihu.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.NavigationEventHandler
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventState
import com.github.zly2006.zhihu.navigation.AppNavEntry
import com.github.zly2006.zhihu.navigation.AppNavHost
import com.github.zly2006.zhihu.navigation.MainTabs
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.navigation.TopLevelDestination
import com.github.zly2006.zhihu.navigation.isListPaneDestination
import kotlin.js.toJsString
import kotlinx.browser.window
import kotlinx.coroutines.launch
import org.w3c.dom.events.Event

@Composable
internal fun WebBrowserNavigation(
    main: AppNavHost,
    tabs: ZhihuMainTabsState,
    onUnsupported: () -> Unit,
    content: @Composable (WebNavigationHistory) -> Unit,
) {
    val owner = rememberNavigationEventDispatcherOwner(parent = LocalNavigationEventDispatcherOwner.current)
    val dispatcher = owner.navigationEventDispatcher
    val history = remember(main, tabs, dispatcher) { WebNavigationHistory(main, tabs, onUnsupported) }
    DisposableEffect(history) { onDispose(history.attach(dispatcher)::close) }
    LaunchedEffect(history) {
        launch { dispatcher.history.collect { history.sync() } }
        snapshotFlow { history.position }.collect { history.sync() }
    }
    CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner, LocalMainNavigationBinding provides history::bind) {
        NavigationEventHandler(
            state = rememberNavigationEventState(WebForwardInfo),
            isBackEnabled = history.index > 0,
            onBackCompleted = history::back,
            isForwardEnabled = history.canGoForward,
            onForwardCompleted = { window.history.forward() },
        )
        content(history)
    }
}

/** 与普通返回处理器的 None 区分，根页即使可以前进也能观察到弹层开始或停止处理返回。 */
private object WebForwardInfo : NavigationEventInfo()

internal data class WebPosition(
    val main: List<AppNavEntry>,
    val detail: List<AppNavEntry>,
    val tabs: List<TopLevelDestination>,
    val owner: String?,
) {
    fun route(): WebRoute {
        val mainTop = main.lastOrNull()
        val child = detail.lastOrNull()?.takeIf { owner == (mainTop?.id ?: "root") }
        val page = (child ?: mainTop)?.destination ?: MainTabs
        val source = if (child != null) mainTop?.destination else main.dropLast(1).lastOrNull { it.destination.isListPaneDestination() }?.destination
        return WebRoute(page, tabs.lastOrNull(), source)
    }

    fun isPopFrom(old: WebPosition) = main == old.main.take(main.size) &&
        detail == old.detail.take(detail.size) &&
        tabs == old.tabs.take(tabs.size) &&
        (main.size < old.main.size || detail.size < old.detail.size || tabs.size < old.tabs.size)
}

/** 浏览器状态只标记物理位置；账号切换或刷新后只能由公开地址重建，不取出上个账号的条目。 */
internal class WebNavigationHistory(
    private val main: AppNavHost,
    private val tabState: ZhihuMainTabsState,
    private val onUnsupported: () -> Unit,
) {
    private val input = DirectNavigationEventInput()
    private var detail by mutableStateOf<AppNavHost?>(null)
    private var pane: ZhihuMainPaneState? = null
    private val tabs: List<TopLevelDestination> get() = tabState.history
    private val mainEntries by derivedStateOf { main.pageEntries.drop(1).toList() }
    private val detailEntries by derivedStateOf {
        detail
            ?.pageEntries
            ?.drop(1)
            ?.toList()
            .orEmpty()
    }
    val position: WebPosition get() = WebPosition(mainEntries, detailEntries, tabs, pane?.detailOwnerEntryId?.takeIf { detailEntries.isNotEmpty() })

    private val visits = mutableMapOf<Int, WebPosition>()
    var index by mutableStateOf(0)
        private set
    private var pending: Int? = null
    private var guard = false
    private var dispatching = false
    private var usedFallback = false
    private var fallback: WebRoute? = null
    var canGoForward by mutableStateOf(false)
        private set

    fun bind(host: AppNavHost, paneState: ZhihuMainPaneState) {
        pane = paneState
        detail = host
        if (fallback == null) sync()
    }

    private val marker = "zhihu++/route/1:"

    private fun mark(i: Int) = "$marker$i".toJsString()

    private fun browserIndex() = window.history.state
        ?.toString()
        ?.takeIf { it.startsWith(marker) }
        ?.removePrefix(marker)
        ?.toIntOrNull()
        ?.takeIf { it >= 0 }

    private fun WebPosition.hash() = route().hash() ?: "#/local"

    private var attached = false

    fun attach(dispatcher: NavigationEventDispatcher): AutoCloseable {
        dispatcher.addInput(input)
        attached = true
        var popped: Pair<Int?, String>? = null
        val pop: (Event) -> Unit = {
            onLocation()
            popped = browserIndex() to window.location.hash
        }
        val hashChanged: (Event) -> Unit = {
            if (popped != (browserIndex() to window.location.hash)) onLocation()
            popped = null
        }
        window.addEventListener("popstate", pop)
        window.addEventListener("hashchange", hashChanged)
        sync()
        return AutoCloseable {
            attached = false
            window.removeEventListener("popstate", pop)
            window.removeEventListener("hashchange", hashChanged)
            dispatcher.removeInput(input)
        }
    }

    fun sync() {
        if (!attached || detail == null || !tabState.startupHandled || tabs.isEmpty() || dispatching || pending != null) return
        val next = position
        if (fallback == null) {
            start(next)
            return
        }
        val old = visits[index] ?: return
        if (old != next) {
            val previous = visits.keys.filter { it < index && visits[it] == next }.maxOrNull()
            if (previous != null || (index > 0 && next.isPopFrom(old))) {
                go(previous ?: (index - 1))
            } else {
                push(next)
            }
        } else if (index == 0) {
            if (input.hasEnabledBackHandlers && !guard) {
                guard = true
                if (canGoForward) go(1) else window.history.pushState(mark(1), "", old.hash())
            } else if (!input.hasEnabledBackHandlers && guard) {
                guard = false
                visits[1]?.let { window.history.replaceState(mark(1), "", it.hash()) }
                go(0)
            }
        }
    }

    private fun start(saved: WebPosition) {
        fallback = saved.route().takeIf { it.hash() != null } ?: WebRoute(tab = tabs.last())
        val hash = window.location.hash
        val existing = browserIndex()
        if (hash.isNotEmpty() && hash != saved.route().hash()) openHash(hash)
        val wanted = position
        if (existing != null) {
            index = existing
            rememberCurrent()
            return
        }
        /** 首次直达补足实际可退的父页；刷新已标记的条目绝不重复 push。 */
        val root = WebPosition(emptyList(), emptyList(), wanted.tabs.take(1), null)
        visits[0] = root
        window.history.replaceState(mark(0), "", root.hash())
        for (count in 2..wanted.tabs.size) push(root.copy(tabs = wanted.tabs.take(count)))
        for (count in 1..wanted.main.size) push(wanted.copy(main = wanted.main.take(count), detail = emptyList(), owner = null))
        for (count in 1..wanted.detail.size) push(wanted.copy(detail = wanted.detail.take(count)))
        if (index == 0) sync()
    }

    private fun openHash(hash: String) {
        val route = WebRoute.parse(hash) ?: checkNotNull(fallback).also { if (hash.isNotEmpty()) onUnsupported() }
        val tab = route.tab ?: fallback?.tab ?: tabs.first()
        if (tabState.pages.none { it.bottomDestination == tab }) onUnsupported()
        tabState.restoreHistory(listOf(tabs.first(), tab).distinct())
        detail?.popToRoot()
        pane?.detailOwnerEntryId = null
        main.popToRoot()
        route.source?.let(main::open)
        if (route.source != null && route.page is Question) {
            pane?.detailOwnerEntryId = main.topEntryId
            detail?.open(route.page)
        } else if (route.page != MainTabs) {
            main.open(route.page)
        }
    }

    private fun push(next: WebPosition) {
        visits.keys.removeAll { it > index }
        index++
        visits[index] = next
        if (guard) window.history.replaceState(mark(index), "", next.hash()) else window.history.pushState(mark(index), "", next.hash())
        guard = false
        canGoForward = false
    }

    private fun rememberCurrent() {
        val next = position
        visits[index] = next
        canGoForward = visits.containsKey(index + 1)
        if (window.location.hash != next.hash() || browserIndex() != index) window.history.replaceState(mark(index), "", next.hash())
    }

    private fun go(target: Int) {
        val at = browserIndex() ?: return
        if (target == at) return
        pending = target
        window.history.go(target - at)
    }

    fun back() {
        if (dispatching) {
            usedFallback = true
        } else if (pending == null && index > 0) {
            go(index - 1)
        }
    }

    private fun onLocation() {
        if (fallback == null) return
        val at = browserIndex()
        val acknowledgement = at != null && at == pending
        pending = null
        if (guard && acknowledgement && at == 1) {
            window.history.replaceState(mark(1), "", position.hash())
            return
        }
        if (at == index && !guard && window.location.hash == visits[index]?.hash()) return
        val before = position
        if (!acknowledgement && at != null && (at < index || (guard && at == 0)) && input.hasEnabledBackHandlers) {
            usedFallback = false
            dispatching = true
            try {
                input.backCompleted()
            } finally {
                dispatching = false
            }
            /** 弹层、iframe 等消耗了返回时不重放下层页面，也不截断它的前进分支。 */
            if (before == position && !usedFallback && at < index) {
                go(index)
                return
            }
        }
        guard = false
        if (at == null) visits.keys.removeAll { it > index }
        val target = at ?: (index + 1)
        if (at == 1 && index == 0 && at !in visits && position.hash() == window.location.hash) {
            go(0)
            return
        }
        val next = visits[target]
        index = target
        if (next == null) {
            if (at == null || window.location.hash != position.hash() || (!acknowledgement && before == position)) openHash(window.location.hash)
        } else if (position != next) {
            main.restorePages(next.main)
            detail?.restorePages(next.detail)
            pane?.detailOwnerEntryId = next.owner
            if (next.tabs != tabs) tabState.restoreHistory(next.tabs)
        }
        rememberCurrent()
    }

    private fun AppNavHost.restorePages(pages: List<AppNavEntry>) {
        val current = pageEntries.drop(1).toList()
        val common = current.zip(pages).takeWhile { (a, b) -> a.id == b.id }.size
        repeat(current.size - common) { back() }
        pages.drop(common).forEach { restore(it) }
    }
}
