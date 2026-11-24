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

package com.github.zly2006.zhihu.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.zly2006.zhihu.platform.SettingsStore
import com.github.zly2006.zhihu.platform.isPageTurnSupported
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.ui.rememberObservedSetting
import com.github.zly2006.zhihu.ui.subscreens.DEFAULT_FAB_OPACITY
import com.github.zly2006.zhihu.ui.subscreens.DEFAULT_PAGE_TURN_PERCENT
import com.github.zly2006.zhihu.ui.subscreens.DEFAULT_SHOW_CONTENT_END_MARKER
import com.github.zly2006.zhihu.ui.subscreens.DEFAULT_SHOW_PAGE_TURN_GUIDE
import com.github.zly2006.zhihu.ui.subscreens.PREF_FAB_OPACITY
import com.github.zly2006.zhihu.ui.subscreens.PREF_PAGE_TURN_PERCENT
import com.github.zly2006.zhihu.ui.subscreens.PREF_SHOW_CONTENT_END_MARKER
import com.github.zly2006.zhihu.ui.subscreens.PREF_SHOW_PAGE_TURN_FAB
import com.github.zly2006.zhihu.ui.subscreens.PREF_SHOW_PAGE_TURN_GUIDE
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import com.github.zly2006.zhihu.ui.components.AppIcon as Icon

enum class PageTurnCommand {
    PageUp,
    PageDown,
    JumpToTop,
    JumpToBottom,
}

/** 每条翻页命令交给最近注册且有效的滚动区域。 */
class PageTurnDispatcher {
    private val targets = mutableListOf<PageTurnTargetRegistration>()

    var hasActiveTarget by mutableStateOf(false)
        private set

    internal fun registerTarget(): PageTurnTargetRegistration {
        val registration = PageTurnTargetRegistration(this)
        targets += registration
        hasActiveTarget = true
        return registration
    }

    fun dispatch(command: PageTurnCommand): Boolean =
        targets
            .lastOrNull()
            ?.commands
            ?.trySend(command)
            ?.isSuccess == true

    internal fun unregisterTarget(registration: PageTurnTargetRegistration) {
        targets.remove(registration)
        registration.commands.close()
        hasActiveTarget = targets.isNotEmpty()
    }
}

internal class PageTurnTargetRegistration(
    private val dispatcher: PageTurnDispatcher,
) {
    val commands = Channel<PageTurnCommand>(Channel.BUFFERED)
    private var closed = false

    fun close() {
        if (closed) return
        closed = true
        dispatcher.unregisterTarget(this)
    }
}

val LocalPageTurnDispatcher = staticCompositionLocalOf(::PageTurnDispatcher)

/** 仅在所属页面活跃时注册目标，并在组合树协程作用域内消费命令。 */
@Composable
private fun PageTurnTargetRegistrationEffect(
    scrollState: ScrollableState,
    active: Boolean,
    onCommand: suspend (PageTurnTarget, PageTurnCommand) -> Unit,
): PageTurnTarget {
    val dispatcher = LocalPageTurnDispatcher.current
    val settings = rememberSettingsStore()
    val guideColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    val target = remember(dispatcher, guideColor) { PageTurnTarget(settings, guideColor) }
    val currentHandler by rememberUpdatedState<suspend (PageTurnCommand) -> Unit> { command -> onCommand(target, command) }
    var registration by remember(dispatcher) { mutableStateOf<PageTurnTargetRegistration?>(null) }

    DisposableEffect(settings, target) {
        val subscription = settings.observeKeyChanges { key ->
            when (key) {
                PREF_PAGE_TURN_PERCENT ->
                    target.pageTurnPercent = settings
                        .getInt(PREF_PAGE_TURN_PERCENT, DEFAULT_PAGE_TURN_PERCENT)
                        .coerceIn(50, 100)
                PREF_SHOW_PAGE_TURN_GUIDE ->
                    target.showGuide =
                        settings.getBoolean(PREF_SHOW_PAGE_TURN_GUIDE, DEFAULT_SHOW_PAGE_TURN_GUIDE).also { showGuide ->
                            if (!showGuide) target.lastPageTurnDirection = 0
                        }
            }
        }
        onDispose(subscription::close)
    }
    LaunchedEffect(target, scrollState) {
        snapshotFlow { scrollState.isScrollInProgress }.collect { scrolling ->
            if (scrolling && !target.pageTurnScrollInProgress) target.lastPageTurnDirection = 0
        }
    }
    DisposableEffect(dispatcher, active) {
        if (active) registration = dispatcher.registerTarget()
        onDispose {
            registration?.close()
            registration = null
        }
    }
    registration?.let { registeredTarget ->
        LaunchedEffect(registeredTarget) {
            for (command in registeredTarget.commands) currentHandler(command)
        }
    }
    return target
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DraggablePageTurnButtons(
    onPageUp: () -> Unit,
    onPageDown: () -> Unit,
    onLongPressUp: () -> Unit,
    onLongPressDown: () -> Unit,
    modifier: Modifier = Modifier,
    preferenceName: String = "fabPageTurn",
) {
    val settings = rememberSettingsStore()
    val buttonSize = 48.dp
    val gap = 8.dp
    val fabOpacityValue by rememberObservedSetting(settings, PREF_FAB_OPACITY) {
        getInt(PREF_FAB_OPACITY, DEFAULT_FAB_OPACITY)
    }
    val opacityFraction = fabOpacityValue.coerceIn(10, 100) / 100f
    val fabColor = FloatingActionButtonDefaults.containerColor.copy(alpha = opacityFraction)
    val iconTint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = opacityFraction)
    DraggableFloatingPlacement(
        modifier = modifier,
        preferenceName = preferenceName,
        initiallyOnLeft = true,
        animateSnap = false,
        defaultBottomGap = 80.dp,
    ) { draggableModifier ->
        Column(modifier = draggableModifier) {
            PageTurnButton(Icons.Default.KeyboardArrowUp, "上翻页", onPageUp, onLongPressUp, buttonSize, fabColor, iconTint)
            Spacer(modifier = Modifier.height(gap))
            PageTurnButton(
                Icons.Default.KeyboardArrowDown,
                "下翻页",
                onPageDown,
                onLongPressDown,
                buttonSize,
                fabColor,
                iconTint,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PageTurnButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    size: Dp,
    color: Color,
    iconTint: Color,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = appGlassContainerColor(color),
        shadowElevation = 4.dp,
        modifier = Modifier
            .appGlassBlur(shape = RoundedCornerShape(16.dp), tint = color)
            .size(size)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(icon, contentDescription = description, tint = iconTint)
        }
    }
}

@Composable
fun PageTurnFab(
    dispatcher: PageTurnDispatcher = LocalPageTurnDispatcher.current,
    modifier: Modifier = Modifier,
    preferenceName: String = "fabPageTurn",
) {
    val settings = rememberSettingsStore()
    val showFab by rememberObservedSetting(settings, PREF_SHOW_PAGE_TURN_FAB) {
        getBoolean(PREF_SHOW_PAGE_TURN_FAB, false)
    }
    if (!isPageTurnSupported || !showFab || !dispatcher.hasActiveTarget) return
    DraggablePageTurnButtons(
        onPageUp = { dispatcher.dispatch(PageTurnCommand.PageUp) },
        onPageDown = { dispatcher.dispatch(PageTurnCommand.PageDown) },
        onLongPressUp = { dispatcher.dispatch(PageTurnCommand.JumpToTop) },
        onLongPressDown = { dispatcher.dispatch(PageTurnCommand.JumpToBottom) },
        modifier = modifier,
        preferenceName = preferenceName,
    )
}

/** 末尾标记作为正文之后的独立布局项，避免覆盖最后一段内容。 */
@Composable
fun ContentEndMarker(modifier: Modifier = Modifier) {
    val settings = rememberSettingsStore()
    val visible by rememberObservedSetting(settings, PREF_SHOW_CONTENT_END_MARKER) {
        getBoolean(PREF_SHOW_CONTENT_END_MARKER, DEFAULT_SHOW_CONTENT_END_MARKER)
    }
    if (!isPageTurnSupported || !visible) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "— · —",
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            fontSize = 14.sp,
        )
    }
}

/** 将 [ContentEndMarker] 作为独立列表项放在全部已加载内容之后。 */
fun LazyListScope.pageTurnContentEndMarker(key: Any = "page_turn_content_end_marker") {
    item(key = key) { ContentEndMarker() }
}

/** 翻页处理与实测视口共用目标，以视口高度计算单页距离。 */
@Stable
class PageTurnTarget internal constructor(
    settings: SettingsStore,
    internal val guideColor: Color,
) {
    internal var pageTurnPercent by mutableIntStateOf(
        settings.getInt(PREF_PAGE_TURN_PERCENT, DEFAULT_PAGE_TURN_PERCENT).coerceIn(50, 100),
    )
    internal var showGuide by mutableStateOf(settings.getBoolean(PREF_SHOW_PAGE_TURN_GUIDE, DEFAULT_SHOW_PAGE_TURN_GUIDE))
    internal var lastPageTurnDirection by mutableIntStateOf(0)
    internal var pageTurnScrollInProgress by mutableStateOf(false)
    internal val nestedScrollDispatcher = NestedScrollDispatcher()
    internal var viewportHeight = 0f
}

private val NoOpNestedScrollConnection = object : NestedScrollConnection {}

/**
 * 将实际滚动视口高度写入 [target]，按设置绘制最近一次翻页留下的重叠参考线；必须包裹真实滚动视口。
 * 同时挂载嵌套滚动分派节点，让翻页通过 [dispatchPageTurnScroll] 向外层连接发送合成滚动事件。
 */
fun Modifier.pageTurnViewportWithGuide(target: PageTurnTarget): Modifier =
    nestedScroll(NoOpNestedScrollConnection, target.nestedScrollDispatcher)
        .drawWithContent {
            drawContent()
            target.viewportHeight = size.height
            if (target.showGuide && target.lastPageTurnDirection != 0) {
                val overlapFraction = 1f - target.pageTurnPercent / 100f
                val y = if (target.lastPageTurnDirection > 0) {
                    overlapFraction * size.height
                } else {
                    target.pageTurnPercent / 100f * size.height
                }
                drawLine(
                    color = target.guideColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())),
                )
            }
        }

/**
 * 程序化翻页也向外层发送嵌套滚动事件，让自动隐藏栏和折叠工具栏与触摸滚动保持一致。
 * 使用 [NestedScrollSource.SideEffect]，只响应直接用户输入的连接（如回答切换）会忽略这些事件。
 *
 * 外层先在 `onPreScroll` 消费距离，剩余距离用于滚动正文；返回正文实际滚动量，未移动时不绘制参考线。
 * 以零速度结束，让祖先沿既有吸附行为归位，避免探测或重复消耗翻页距离。
 * 若提供标题栏，向下翻页被消费后先收起标题，避免随后吸附重新展开长标题。
 * [deltaPx] 沿用 [ScrollableState] 的符号约定（向内容末尾为正），嵌套滚动事件取相反符号以匹配手势。
 */
private suspend fun NestedScrollDispatcher.dispatchPageTurnScroll(
    scrollState: ScrollableState,
    deltaPx: Float,
    titleBarScrollState: AppPageTitleBarScrollState? = null,
): Float {
    val preConsumed = dispatchPreScroll(Offset(0f, -deltaPx), NestedScrollSource.SideEffect).y
    val childDelta = deltaPx + preConsumed
    val consumed = if (childDelta != 0f) scrollState.scrollBy(childDelta) else 0f
    dispatchPostScroll(
        Offset(0f, -consumed),
        Offset(0f, -(childDelta - consumed)),
        NestedScrollSource.SideEffect,
    )
    if (deltaPx > 0f && preConsumed < 0f) titleBarScrollState?.collapse()
    dispatchPostFling(Velocity.Zero, Velocity.Zero)
    return consumed
}

/** 为连续 [ScrollState] 创建翻页目标；到达首尾后的额外翻页命令可由边界回调导航到相邻内容。 */
@Composable
fun rememberPageTurnTarget(
    scrollState: ScrollState,
    enabled: Boolean,
    maxScrollValue: Int = scrollState.maxValue,
    onPageUpAtStart: (() -> Unit)? = null,
    onPageDownAtEnd: (() -> Unit)? = null,
    titleBarScrollState: AppPageTitleBarScrollState? = null,
): PageTurnTarget {
    val currentOnPageUpAtStart by rememberUpdatedState(onPageUpAtStart)
    val currentOnPageDownAtEnd by rememberUpdatedState(onPageDownAtEnd)
    val currentMaxScrollValue by rememberUpdatedState(maxScrollValue)
    return PageTurnTargetRegistrationEffect(scrollState, enabled && isPageTurnSupported) { target, command ->
        target.lastPageTurnDirection = command.scrollDirection.takeIf { target.showGuide } ?: 0
        target.pageTurnScrollInProgress = true
        try {
            when (command) {
                PageTurnCommand.JumpToTop -> scrollState.scrollTo(0)
                PageTurnCommand.JumpToBottom -> scrollState.scrollTo(currentMaxScrollValue)
                PageTurnCommand.PageUp,
                PageTurnCommand.PageDown,
                -> {
                    val reachedStart = command == PageTurnCommand.PageUp && scrollState.value <= 0
                    val reachedEnd = command == PageTurnCommand.PageDown &&
                        currentMaxScrollValue != Int.MAX_VALUE &&
                        scrollState.value >= currentMaxScrollValue
                    when {
                        reachedStart && currentOnPageUpAtStart != null -> currentOnPageUpAtStart?.invoke()
                        reachedEnd && currentOnPageDownAtEnd != null -> currentOnPageDownAtEnd?.invoke()
                        target.viewportHeight > 0f -> {
                            val scrolled = target.nestedScrollDispatcher.dispatchPageTurnScroll(
                                scrollState,
                                target.viewportHeight * target.pageTurnPercent / 100f * command.scrollDirection,
                                titleBarScrollState,
                            )
                            if (scrolled == 0f) target.lastPageTurnDirection = 0
                        }
                    }
                }
            }
        } finally {
            target.pageTurnScrollInProgress = false
        }
    }
}

/** 为惰性列表创建翻页目标，[listState] 仍由调用页面持有。 */
@Composable
fun rememberPageTurnTarget(
    listState: LazyListState,
    enabled: Boolean,
): PageTurnTarget =
    PageTurnTargetRegistrationEffect(listState, enabled && isPageTurnSupported) { target, command ->
        target.lastPageTurnDirection = command.scrollDirection.takeIf { target.showGuide } ?: 0
        target.pageTurnScrollInProgress = true
        try {
            when (command) {
                PageTurnCommand.JumpToTop -> listState.scrollToItem(0)
                PageTurnCommand.JumpToBottom -> {
                    val lastIndex = listState.layoutInfo.totalItemsCount - 1
                    if (lastIndex >= 0) listState.scrollToItem(lastIndex)
                }
                PageTurnCommand.PageUp,
                PageTurnCommand.PageDown,
                -> {
                    if (target.viewportHeight > 0f) {
                        val scrolled = target.nestedScrollDispatcher.dispatchPageTurnScroll(
                            listState,
                            target.viewportHeight * target.pageTurnPercent / 100f * command.scrollDirection,
                        )
                        if (scrolled == 0f) target.lastPageTurnDirection = 0
                    }
                }
            }
        } finally {
            target.pageTurnScrollInProgress = false
        }
    }

private val PageTurnCommand.scrollDirection: Int
    get() = when (this) {
        PageTurnCommand.PageUp -> -1
        PageTurnCommand.PageDown -> 1
        PageTurnCommand.JumpToTop,
        PageTurnCommand.JumpToBottom,
        -> 0
    }
