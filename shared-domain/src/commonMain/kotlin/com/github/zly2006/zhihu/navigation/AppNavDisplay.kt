/*
 * Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 * Licensed under the GNU Affero General Public License, version 3 only.
 */

package com.github.zly2006.zhihu.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.nav.core.LocalNavTransitionScope
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import top.yukonga.miuix.kmp.nav.transition.NavTransition
import top.yukonga.miuix.kmp.nav.transition.NavTransitionScope
import top.yukonga.miuix.kmp.nav.transition.NavTransitions
import kotlin.reflect.KClass

class AppNavEntryBuilder {
    @PublishedApi
    internal class Definition(
        val transition: NavTransition?,
        val swipeDismiss: NavSwipeDirection?,
        val content: @Composable (NavDestination) -> Unit,
    )

    @PublishedApi
    internal val definitions = mutableMapOf<KClass<*>, Definition>()

    inline fun <reified T : NavDestination> entry(
        transition: NavTransition? = null,
        swipeDismiss: NavSwipeDirection? = null,
        noinline content: @Composable (T) -> Unit,
    ) {
        definitions[T::class] = Definition(transition, swipeDismiss) { destination -> content(destination as T) }
    }

    @Composable
    internal fun Content(
        entry: AppNavEntry,
        wrapper: @Composable (AppNavEntry, @Composable () -> Unit) -> Unit,
    ) {
        wrapper(entry) {
            checkNotNull(definitions[entry.destination::class]) { "未注册页面 ${entry.destination::class.simpleName}" }
                .content(entry.destination)
        }
    }
}

/** contentKey 隔离访问状态，业务目标的 equals/hashCode 保持不变。 */
@Composable
fun AppNavDisplay(
    navHost: AppNavHost,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = navHost::onBack,
    transition: NavTransition = NavTransitions.MiuixDefault,
    effects: NavDisplayEffects = NavDisplayEffects(),
    entryWrapper: @Composable (AppNavEntry, @Composable () -> Unit) -> Unit = { _, content -> content() },
    entryTransition: ((AppNavEntry, NavTransition) -> NavTransition)? = null,
    retainedEntryId: String? = null,
    content: AppNavEntryBuilder.() -> Unit,
) {
    val definitions = remember(content) { AppNavEntryBuilder().apply(content) }
    val current = navHost.top?.let { definitions.definitions[it::class] }
    val reading = definitions.definitions[Article::class]
    val searching = definitions.definitions[Search::class]

    /** miuix-nav 0.9.4 不跨 display 共享保存槽；绑定原 scope 只改布局，避免展宽丢状态：https://github.com/zly2006/zhihu-plus-plus/pull/777 */
    val scopedEntries = remember { mutableStateMapOf<NavTransitionScope, AppNavEntry>() }
    val scopedContent: @Composable (AppNavEntry) -> Unit = { entry ->
        val scope = LocalNavTransitionScope.current
        if (entryTransition != null) {
            DisposableEffect(scope, entry) {
                scopedEntries[scope] = entry
                onDispose { scopedEntries.remove(scope) }
            }
        }
        definitions.Content(entry, entryWrapper)
    }

    @Composable
    fun scoped(base: NavTransition): NavTransition = if (entryTransition == null) {
        base
    } else {
        remember(base, entryTransition, retainedEntryId) {
            object : NavTransition by base {
                override val opaqueDepth: Float
                    get() = if (retainedEntryId == null) {
                        base.opaqueDepth
                    } else {
                        maxOf(
                            base.opaqueDepth,
                            scopedEntries.entries
                                .firstOrNull { it.value.id == retainedEntryId }
                                ?.key
                                ?.relativeDepth
                                ?: (navHost.pageEntries.count() - navHost.pageEntries.indexOfFirst { it.id == retainedEntryId } - 1).coerceAtLeast(1).toFloat(),
                        )
                    }

                override fun Modifier.transformEntry(scope: NavTransitionScope): Modifier {
                    /** 尺寸变化或退场重建 scope 后，同一层仍须读取原条目的实时深度。 */
                    val entry = scopedEntries[scope] ?: scopedEntries.entries
                        .singleOrNull { it.key.relativeDepth == scope.relativeDepth }
                        ?.value
                    val applied = if (entry == null) base else entryTransition(entry, base)
                    return with(applied) { transformEntry(scope) }
                }
            }
        }
    }
    val hostTransition = scoped(transition)
    val readingTransition = scoped(reading?.transition ?: transition)
    val searchingTransition = scoped(searching?.transition ?: transition)
    NavDisplay(
        backStack = navHost.entries,
        modifier = modifier,
        onBack = onBack,
        transition = hostTransition,
        effects = effects,
    ) {
        entry<AppNavEntry.Root>(contentKey = { it.id }, swipeDismiss = NavSwipeDirection.None) { scopedContent(it) }
        entry<AppNavEntry.Page>(contentKey = { it.id }, swipeDismiss = current?.swipeDismiss) { scopedContent(it) }
        entry<AppNavEntry.Reading>(contentKey = { it.id }, transition = readingTransition, swipeDismiss = reading?.swipeDismiss) { scopedContent(it) }
        entry<AppNavEntry.Searching>(contentKey = { it.id }, transition = searchingTransition, swipeDismiss = searching?.swipeDismiss) { scopedContent(it) }
    }
}
