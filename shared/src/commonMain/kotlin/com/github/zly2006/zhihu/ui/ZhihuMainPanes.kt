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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.rememberLifecycleOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import com.github.zly2006.zhihu.navigation.AppNavHost
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.EmptyDetail
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.Navigator
import com.github.zly2006.zhihu.navigation.accountSettings
import com.github.zly2006.zhihu.navigation.contentDetailEntries
import com.github.zly2006.zhihu.navigation.detailNotificationEntries
import com.github.zly2006.zhihu.navigation.isDetailPaneDestination
import com.github.zly2006.zhihu.reading.ReadingPlayerState
import com.github.zly2006.zhihu.reading.ReadingQueueItem
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.ui.components.CompactReadingPlayerButton
import com.github.zly2006.zhihu.ui.components.ReadingPlayerBar
import com.github.zly2006.zhihu.ui.components.ReadingQueueSheet
import com.github.zly2006.zhihu.ui.components.settingSkinIsMiuix
import top.yukonga.miuix.kmp.nav.core.NavCornerClipMode
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import top.yukonga.miuix.kmp.nav.transition.NavTransition
import com.github.zly2006.zhihu.navigation.AppNavDisplay as NavDisplay

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun ZhihuMainDetailPane(
    visible: Boolean,
    isDualPaneReading: Boolean,
    modifier: Modifier,
    detailHost: AppNavHost,
    navigator: Navigator,
    selectedContentDestination: NavDestination?,
    readingPlayerOverlayPadding: Dp,
    readingPlayerOverlayOffsetState: ReadingPlayerOverlayOffsetState,
    navTransition: NavTransition,
    navEffects: NavDisplayEffects,
    swipeBack: NavSwipeDirection,
    articleContent: @Composable (Article) -> Unit,
    reloadBottomBarPreferences: () -> Unit,
    blocklistSettingsNlpContent: @Composable (onNavigateBack: () -> Unit) -> Unit,
) {
    val lifecycleOwner = rememberLifecycleOwner(
        maxLifecycle = if (visible) Lifecycle.State.RESUMED else Lifecycle.State.CREATED,
    )
    val dispatcherOwner = rememberNavigationEventDispatcherOwner(
        enabled = visible,
        parent = LocalNavigationEventDispatcherOwner.current,
    )
    val paneModifier = modifier.retainVisibleConstraints(visible)
    CompositionLocalProvider(
        LocalLifecycleOwner provides lifecycleOwner,
        LocalNavigationEventDispatcherOwner provides dispatcherOwner,
        LocalArticleNavHost provides detailHost,
        LocalNavigator provides navigator,
        LocalSelectedContentDestination provides selectedContentDestination,
        LocalReadingPlayerOverlayPadding provides readingPlayerOverlayPadding,
        LocalReadingPlayerOverlayOffsetState provides readingPlayerOverlayOffsetState,
    ) {
        NavDisplay(
            navHost = detailHost,
            modifier = paneModifier.clipToBounds().testTag("detail_pane"),
            onBack = detailHost::onBack,
            transition = navTransition,
            effects = navEffects,
            entryWrapper = { entry, content ->
                CompositionLocalProvider(
                    LocalIsDualPaneReading provides (isDualPaneReading && entry.destination.isDetailPaneDestination()),
                    content = content,
                )
            },
        ) {
            entry<EmptyDetail>(swipeDismiss = NavSwipeDirection.None) { EmptyDetailPane() }
            contentDetailEntries(swipeBack, articleContent)
            accountSettings(reloadBottomBarPreferences, blocklistSettingsNlpContent, swipeBack)
            detailNotificationEntries(swipeBack)
        }
    }
}

@Composable
internal fun ZhihuMainReadingPlayerOverlay(
    modifier: Modifier,
    isReadingPlayerExpanded: Boolean,
    readingPlayerState: ReadingPlayerState,
    onPrevious: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onStop: () -> Unit,
    onOpenQueue: () -> Unit,
    onPlaybackSpeedChange: (Float) -> Unit,
    onBackgroundInteraction: () -> Unit,
    onHeightChange: (Int) -> Unit,
    readingPlayerOverlayOffsetState: ReadingPlayerOverlayOffsetState,
    onExpand: () -> Unit,
) {
    AnimatedVisibility(
        modifier = modifier,
        visible = isReadingPlayerExpanded,
        enter = fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.92f),
        exit = fadeOut(tween(160)) + scaleOut(tween(160), targetScale = 0.92f),
    ) {
        ReadingPlayerBar(
            state = readingPlayerState,
            onPrevious = onPrevious,
            onTogglePlayPause = onTogglePlayPause,
            onNext = onNext,
            onStop = onStop,
            onOpenQueue = onOpenQueue,
            onPlaybackSpeedChange = onPlaybackSpeedChange,
            onBackgroundInteraction = onBackgroundInteraction,
            modifier = Modifier
                .onSizeChanged { onHeightChange(it.height) }
                .graphicsLayer {
                    translationY = readingPlayerOverlayOffsetState.verticalOffsetPx
                },
        )
    }

    AnimatedVisibility(
        visible = readingPlayerState.hasSession && !isReadingPlayerExpanded,
        enter = fadeIn(tween(220)),
        exit = fadeOut(tween(160)),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            CompactReadingPlayerButton(
                state = readingPlayerState,
                onExpand = onExpand,
            )
        }
    }
}

@Composable
internal fun ZhihuMainReadingQueue(
    visible: Boolean,
    readingPlayerState: ReadingPlayerState,
    onDismissRequest: () -> Unit,
    onItemClick: (index: Int, item: ReadingQueueItem) -> Unit,
    onOpenSettings: () -> Unit,
) {
    if (visible && readingPlayerState.hasSession) {
        ReadingQueueSheet(
            state = readingPlayerState,
            onDismissRequest = onDismissRequest,
            onItemClick = onItemClick,
            onOpenSettings = onOpenSettings,
        )
    }
}

/** 每次触摸手势结束（手指全部抬起）后回调一次；只观察不消费，页面照常响应点击和滚动。 */
internal fun Modifier.afterEachGesture(onGestureEnd: suspend () -> Unit): Modifier = pointerInput(Unit) {
    while (true) {
        awaitPointerEventScope {
            awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Initial,
            )
            while (
                awaitPointerEvent(PointerEventPass.Final)
                    .changes
                    .any { it.pressed }
            ) {
                /** 等手势完成后再重组，避免取消同一次背景点击或滚动。 */
            }
        }
        onGestureEnd()
    }
}

@Composable
internal fun navDisplayEffects(aospStyle: Boolean): NavDisplayEffects =
    if (aospStyle) {
        NavDisplayEffects(
            enableCornerClip = false,
            dimAmount = 0.2f,
            backdropColor = LocalAppSkinTokens.current.background,
        )
    } else if (settingSkinIsMiuix()) {
        NavDisplayEffects(
            cornerClipRadius = rememberNavSystemCornerRadius(),
            cornerClipMode = NavCornerClipMode.Leading,
            dimAmount = 0.35f,
            backdropColor = LocalAppSkinTokens.current.background,
        )
    } else {
        NavDisplayEffects(
            enableCornerClip = false,
            dimAmount = 0f,
            backdropColor = MaterialTheme.colorScheme.background,
        )
    }
