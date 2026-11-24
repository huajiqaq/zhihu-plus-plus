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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSizeIn
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.theme.ThemeManager
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.TopAppBarDefaults
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.glass.GlassTopAppBar
import top.yukonga.miuix.kmp.glass.GlassTopAppBarDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.material.GlassDefaults
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar as MiuixSmallTopAppBar
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixNativeTopBar

@Composable
actual fun AppOverlayHost(content: @Composable () -> Unit) {
    /** 同树 popup 必须绘制在背景 blur 外；此宿主不能登记玻璃采样源，避免悬浮控件采样自身。 */
    MiuixScaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { content() }
}

@Composable
internal actual fun AppScaffoldSkinHost(
    modifier: Modifier,
    topBar: @Composable () -> Unit,
    bottomBar: @Composable () -> Unit,
    floatingActionButton: @Composable () -> Unit,
    snackbarHost: @Composable () -> Unit,
    containerColor: Color,
    contentWindowInsets: WindowInsets,
    content: @Composable (PaddingValues) -> Unit,
) {
    MiuixScaffold(
        modifier = modifier,
        topBar = topBar,
        bottomBar = bottomBar,
        floatingActionButton = floatingActionButton,
        snackbarHost = snackbarHost,
        containerColor = containerColor,
        contentWindowInsets = contentWindowInsets,
        content = content,
    )
}

@Composable
internal actual fun MiuixTopBar(
    title: @Composable () -> Unit,
    modifier: Modifier,
    navigationIcon: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    centered: Boolean,
    windowInsets: WindowInsets,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .appGlassBlur(
                shape = RectangleShape,
                tint = LocalAppSkinTokens.current.surfaceContainer.copy(alpha = 0.30f),
            ).background(appGlassContainerColor(LocalAppSkinTokens.current.surface))
            .windowInsetsPadding(windowInsets),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = AppDimens.MiuixTopBarHeight)
                .padding(start = TopAppBarDefaults.NavigationIconPadding, end = TopAppBarDefaults.ActionIconPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            navigationIcon()
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = AppDimens.TopBarTitleHorizontalPadding),
                contentAlignment = if (centered) Alignment.Center else Alignment.CenterStart,
            ) {
                ProvideTextStyle(LocalAppSkinTokens.current.title3, content = title)
            }
            actions()
        }
    }
}

internal actual class MiuixPageTitleBarScrollState internal constructor(
    internal val state: ScrollBehavior,
) {
    actual val nestedScrollConnection: NestedScrollConnection
        get() = state.nestedScrollConnection

    actual fun collapse() {
        state.state.heightOffset = state.state.heightOffsetLimit
    }
}

@Composable
internal actual fun rememberMiuixPageTitleBarScrollState(): MiuixPageTitleBarScrollState {
    val state = MiuixScrollBehavior()
    return remember(state) { MiuixPageTitleBarScrollState(state) }
}

@Composable
internal actual fun MiuixPageTitleBar(
    title: String,
    modifier: Modifier,
    subtitle: String?,
    titleTag: String?,
    scrollState: MiuixPageTitleBarScrollState?,
    onBack: (() -> Unit)?,
    backTag: String?,
    containerColor: Color,
    multilineTitle: Boolean,
    onTitleClick: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit,
) {
    val useGlass = useMiuixGlassStyle()
    val state = scrollState?.state?.state
    val titleMeasure = if ((multilineTitle || useGlass) && state != null) {
        Modifier.layout { measurable, constraints ->
            /** 原生布局测量大标题时写入实际范围，但不会修正旧偏移。 */
            val previousLimit = state.heightOffsetLimit
            val bar = measurable.measure(constraints)
            if (state.heightOffsetLimit != previousLimit) {
                state.heightOffset = rebasedTitleBarOffset(state.heightOffset, previousLimit, state.heightOffsetLimit)
            }
            layout(bar.width, bar.height) { bar.placeRelative(0, 0) }
        }
    } else {
        Modifier
    }
    /** 发布玻璃顶栏不提供 titlePadding 或标题点击槽；复杂标题保留原生布局和交互。 */
    if (useGlass && !multilineTitle && onTitleClick == null) {
        GlassTopAppBar(
            title = title,
            largeTitle = title,
            subtitle = subtitle.orEmpty(),
            modifier = modifier.then(titleTag?.let { Modifier.testTag(it) } ?: Modifier).then(titleMeasure),
            backdrop = LocalAppBackdrop.current,
            style = GlassDefaults.style(ThemeManager.isDarkTheme()),
            progressiveBlur = true,
            bandBrush = GlassTopAppBarDefaults.bandBrush(containerColor),
            buttonSize = AppDimens.TouchTarget,
            scrollBehavior = scrollState?.state,
            navigationIcon = {
                onBack?.let {
                    MiuixBackButton(it, backTag?.let { tag -> Modifier.testTag(tag) } ?: Modifier, Icons.AutoMirrored.Filled.ArrowBack, "返回")
                }
            },
            actions = actions,
        )
        return
    }
    val density = LocalDensity.current
    var navigationWidth by remember { mutableIntStateOf(-1) }
    var actionsWidth by remember { mutableIntStateOf(-1) }
    val collapsedTitleVisible by remember(scrollState) {
        derivedStateOf { (scrollState?.state?.state?.collapsedFraction ?: 0f) * 3f >= 1f }
    }
    Box(modifier.then(if (onTitleClick == null) titleTag?.let { Modifier.testTag(it) } ?: Modifier else Modifier), propagateMinConstraints = true) {
        MiuixNativeTopBar(
            title = title,
            modifier = titleMeasure.appGlassBlur(
                shape = RectangleShape,
                tint = containerColor.copy(alpha = 0.30f),
            ),
            color = appGlassContainerColor(containerColor),
            titleColor = LocalAppSkinTokens.current.onSurface,
            largeTitle = title,
            largeTitleColor = LocalAppSkinTokens.current.onSurface,
            subtitle = subtitle.orEmpty(),
            subtitleColor = LocalAppSkinTokens.current.onSurfaceVariantSummary,
            scrollBehavior = scrollState?.state,
            titlePadding = if (multilineTitle) AppDimens.PageHorizontalPadding else TopAppBarDefaults.TitlePadding,
            navigationIcon = {
                Box(Modifier.onSizeChanged { navigationWidth = it.width }) {
                    onBack?.let {
                        if (useGlass) {
                            MiuixBackButton(it, backTag?.let { tag -> Modifier.testTag(tag) } ?: Modifier, Icons.AutoMirrored.Filled.ArrowBack, "返回")
                        } else {
                            AppBackButton(it, modifier = backTag?.let { Modifier.testTag(it) } ?: Modifier)
                        }
                    }
                }
            },
            actions = { Row(Modifier.onSizeChanged { actionsWidth = it.width }, content = actions) },
        )
        onTitleClick?.let { click ->
            /** 原生顶栏吞掉点击，标题层须避开已测量的按钮区域。 */
            Column(
                Modifier
                    .matchParentSize()
                    .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                    .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
                    .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top)),
            ) {
                Row(Modifier.fillMaxWidth().height(TopAppBarDefaults.CollapsedHeight)) {
                    Spacer(Modifier.width(with(density) { navigationWidth.coerceAtLeast(0).toDp() } + TopAppBarDefaults.NavigationIconPadding))
                    Box(
                        Modifier.weight(1f).fillMaxHeight().then(
                            if (collapsedTitleVisible && navigationWidth >= 0 && actionsWidth >= 0) {
                                (titleTag?.let { Modifier.testTag(it) } ?: Modifier)
                                    .semantics {
                                        heading()
                                        contentDescription = title
                                    }.clickable(onClick = click)
                            } else {
                                Modifier
                            },
                        ),
                    )
                    Spacer(Modifier.width(with(density) { actionsWidth.coerceAtLeast(0).toDp() } + TopAppBarDefaults.ActionIconPadding))
                }
                if (!collapsedTitleVisible) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .then(titleTag?.let { Modifier.testTag(it) } ?: Modifier)
                            .semantics {
                                heading()
                                contentDescription = title
                            }.clickable(onClick = click),
                    )
                }
            }
        }
    }
}

@Composable
internal actual fun MiuixCompactTopBar(
    title: String,
    modifier: Modifier,
    subtitle: String?,
    titleTag: String?,
    onBack: (() -> Unit)?,
    backTag: String?,
    containerColor: Color?,
    actions: @Composable RowScope.() -> Unit,
) {
    if (useMiuixGlassStyle()) {
        GlassTopAppBar(
            title = title,
            subtitle = subtitle.orEmpty(),
            expandable = false,
            modifier = modifier.then(titleTag?.let { Modifier.testTag(it) } ?: Modifier),
            backdrop = LocalAppBackdrop.current,
            style = GlassDefaults.style(ThemeManager.isDarkTheme()),
            progressiveBlur = true,
            bandBrush = GlassTopAppBarDefaults.bandBrush(containerColor ?: LocalAppSkinTokens.current.surface),
            buttonSize = AppDimens.TouchTarget,
            navigationIcon = {
                onBack?.let {
                    MiuixBackButton(it, backTag?.let { tag -> Modifier.testTag(tag) } ?: Modifier, Icons.AutoMirrored.Filled.ArrowBack, "返回")
                }
            },
            actions = actions,
        )
        return
    }
    MiuixSmallTopAppBar(
        title = title,
        navigationIcon = {
            onBack?.let { AppBackButton(it, modifier = backTag?.let { tag -> Modifier.testTag(tag) } ?: Modifier) }
        },
        subtitle = subtitle.orEmpty(),
        modifier = modifier
            .then(titleTag?.let { Modifier.testTag(it) } ?: Modifier)
            .appGlassBlur(
                shape = RectangleShape,
                tint = (containerColor ?: LocalAppSkinTokens.current.surfaceContainer).copy(alpha = 0.30f),
            ),
        color = appGlassContainerColor(containerColor ?: LocalAppSkinTokens.current.surface),
        actions = actions,
    )
}

@Composable
internal actual fun MiuixBackButton(
    onClick: () -> Unit,
    modifier: Modifier,
    image: ImageVector,
    contentDescription: String,
) {
    val tokens = LocalAppSkinTokens.current
    val isDefaultBack = image.name.substringAfterLast('.') == "ArrowBack"
    val mirrorChevron = isDefaultBack && image.autoMirror && LocalLayoutDirection.current == LayoutDirection.Rtl
    if (useMiuixGlassStyle()) {
        GlassIconButton(
            onClick = onClick,
            modifier = modifier
                .requiredSizeIn(minWidth = AppDimens.TouchTarget, minHeight = AppDimens.TouchTarget)
                .semantics(mergeDescendants = true) { role = Role.Button },
            backdrop = LocalAppBackdrop.current,
            style = GlassDefaults.style(ThemeManager.isDarkTheme()),
            size = AppDimens.TouchTarget,
        ) {
            AppIcon(
                imageVector = if (isDefaultBack) MiuixIcons.ChevronBackward else image,
                contentDescription = contentDescription,
                modifier = if (mirrorChevron) Modifier.graphicsLayer { scaleX = -1f } else Modifier,
                tint = tokens.onSurface,
            )
        }
        return
    }
    MiuixIconButton(
        onClick = onClick,
        modifier = modifier,
        backgroundColor = tokens.secondaryVariant.copy(alpha = 0.5f).compositeOver(tokens.surface),
        cornerRadius = AppDimens.IconButtonMinSize / 2,
        minHeight = AppDimens.IconButtonMinSize,
        minWidth = AppDimens.IconButtonMinSize,
    ) {
        AppIcon(
            imageVector = if (isDefaultBack) MiuixIcons.ChevronBackward else image,
            contentDescription = contentDescription,
            modifier = if (mirrorChevron) Modifier.graphicsLayer { scaleX = -1f } else Modifier,
            tint = tokens.onSurface,
        )
    }
}
