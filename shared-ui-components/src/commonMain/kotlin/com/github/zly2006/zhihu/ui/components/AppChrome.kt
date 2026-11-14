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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.TwoRowsTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.theme.ThemeManager
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import com.github.zly2006.zhihu.ui.components.AppIcon as Icon

/** 换肤保持宿主身份；玻璃采样只登记内容层。 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun AppScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    containerColor: Color = Color.Unspecified,
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    content: @Composable (PaddingValues) -> Unit,
) {
    val surface = when {
        containerColor != Color.Unspecified -> containerColor
        settingSkinIsMiuix() -> LocalAppSkinTokens.current.surface
        else -> MaterialTheme.colorScheme.background
    }
    val backdropSurface = if (surface.alpha == 0f) MaterialTheme.colorScheme.background else surface.copy(alpha = 1f)
    val backdrop = rememberLayerBackdrop {
        /** 不透明底色填满采样源，避免透明区域模糊后扩散成色块。 */
        drawRect(backdropSurface)
        drawContent()
    }
    CompositionLocalProvider(LocalAppBackdrop provides backdrop) {
        AppScaffoldSkinHost(
            modifier = modifier,
            topBar = topBar,
            bottomBar = bottomBar,
            floatingActionButton = floatingActionButton,
            snackbarHost = snackbarHost,
            containerColor = surface,
            contentWindowInsets = contentWindowInsets,
            content = { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .layerBackdrop(backdrop),
                ) {
                    content(padding)
                }
            },
        )
    }
}

/** 分组页底色；纯黑模式与 [appGroupedCardColor] 保持明度层级。 */
@Composable
fun appGroupedPageBackground(): Color {
    val dark = ThemeManager.isDarkTheme()
    return when {
        settingSkinIsMiuix() -> LocalAppSkinTokens.current.surface
        dark && ThemeManager.getAmoledTheme() -> MaterialTheme.colorScheme.background
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun appGroupedCardColor(): Color = if (!settingSkinIsMiuix() && ThemeManager.isDarkTheme() && ThemeManager.getAmoledTheme()) {
    MaterialTheme.colorScheme.surfaceContainerHighest
} else {
    ListItemDefaults.segmentedColors().containerColor
}

/** Miuix 原生顶栏仅支持字符串；复合标题按其尺寸 token 排版。 */
@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
fun AppTopBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    backTag: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    centered: Boolean = false,
    colors: TopAppBarColors? = null,
    windowInsets: WindowInsets = if (settingSkinIsMiuix()) {
        TopAppBarDefaults.windowInsets.union(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
    } else {
        TopAppBarDefaults.windowInsets
    },
) {
    val leadingIcon: @Composable () -> Unit = {
        onBack?.let { AppBackButton(onClick = it, modifier = backTag?.let { tag -> Modifier.testTag(tag) } ?: Modifier) }
    }

    if (settingSkinIsMiuix()) {
        MiuixTopBar(
            title = title,
            modifier = modifier,
            navigationIcon = leadingIcon,
            actions = actions,
            centered = centered,
            windowInsets = windowInsets,
        )
        return
    }

    val materialTitle: @Composable () -> Unit = {
        ProvideTextStyle(MaterialTheme.typography.titleLarge, content = title)
    }

    if (centered) {
        CenterAlignedTopAppBar(
            title = materialTitle,
            modifier = modifier,
            navigationIcon = leadingIcon,
            actions = actions,
            colors = colors ?: TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            windowInsets = windowInsets,
        )
        return
    }

    TopAppBar(
        title = materialTitle,
        modifier = modifier,
        navigationIcon = leadingIcon,
        actions = actions,
        colors = colors ?: TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        windowInsets = windowInsets,
    )
}

/** 多行标题仅在展开态完整显示，收起态单行。 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppPageTitleBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    titleTag: String? = null,
    scrollState: AppPageTitleBarScrollState? = null,
    onBack: (() -> Unit)? = null,
    backTag: String? = null,
    colors: TopAppBarColors? = null,
    multilineTitle: Boolean = false,
    onTitleClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val navigationIcon: @Composable () -> Unit = {
        onBack?.let { AppBackButton(onClick = it, modifier = backTag?.let { tag -> Modifier.testTag(tag) } ?: Modifier) }
    }
    if (settingSkinIsMiuix()) {
        MiuixPageTitleBar(
            title = title,
            modifier = modifier,
            subtitle = subtitle,
            titleTag = titleTag,
            scrollState = scrollState?.miuix,
            onBack = onBack,
            backTag = backTag,
            /** 顶栏使用调用方页面底色，避免分组页与顶栏断层。 */
            containerColor = colors?.containerColor ?: LocalAppSkinTokens.current.surfaceContainer,
            multilineTitle = multilineTitle,
            onTitleClick = onTitleClick,
            actions = actions,
        )
        return
    }
    val barModifier = modifier
        .appGlassBlur(
            shape = RectangleShape,
            tint = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.30f),
        ).background(appGlassContainerColor(colors?.containerColor ?: MaterialTheme.colorScheme.background))
    /** TopAppBar 自己的容器色也要走同一开关，否则不透明的 colors 会盖住模糊层。 */
    val pageBarColors = colors ?: TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.background,
        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    )
    val glassAwareColors = pageBarColors.copy(
        containerColor = appGlassContainerColor(pageBarColors.containerColor),
        scrolledContainerColor = appGlassContainerColor(pageBarColors.scrolledContainerColor),
    )
    if (multilineTitle) {
        TwoRowsTopAppBar(
            title = { expanded ->
                Text(
                    text = title,
                    modifier = (
                        if (expanded) {
                            Modifier
                                .padding(
                                    end = AppDimens.Space12,
                                    bottom = if (subtitle.isNullOrBlank()) AppDimens.Space28 else AppDimens.ElevationNone,
                                ).then(titleTag?.let { Modifier.testTag(it) } ?: Modifier)
                                .semantics { heading() }
                        } else {
                            Modifier
                        }
                    ).then(onTitleClick?.let { Modifier.clickable(onClick = it) } ?: Modifier),
                    style = if (expanded) MaterialTheme.typography.displaySmall else MaterialTheme.typography.titleLarge,
                    maxLines = if (expanded) Int.MAX_VALUE else 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            subtitle = subtitle?.takeIf { it.isNotBlank() }?.let { text ->
                { expanded ->
                    Text(
                        text,
                        modifier = if (expanded) Modifier.padding(end = AppDimens.Space12, bottom = AppDimens.Space28) else Modifier,
                        style = if (expanded) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodySmall,
                    )
                }
            },
            modifier = barModifier,
            navigationIcon = navigationIcon,
            actions = actions,
            collapsedHeight = TopAppBarDefaults.LargeAppBarCollapsedHeight,
            expandedHeight = if (subtitle.isNullOrBlank()) {
                TopAppBarDefaults.LargeFlexibleAppBarWithoutSubtitleExpandedHeight
            } else {
                TopAppBarDefaults.LargeFlexibleAppBarWithSubtitleExpandedHeight
            },
            colors = glassAwareColors,
            scrollBehavior = scrollState?.m3e,
        )
        return
    }
    LargeFlexibleTopAppBar(
        title = {
            Text(
                text = title,
                modifier = (titleTag?.let { Modifier.testTag(it) } ?: Modifier)
                    .then(onTitleClick?.let { Modifier.clickable(onClick = it) } ?: Modifier),
            )
        },
        subtitle = subtitle?.takeIf { it.isNotBlank() }?.let { text -> { Text(text) } },
        modifier = barModifier,
        navigationIcon = navigationIcon,
        actions = actions,
        colors = glassAwareColors,
        scrollBehavior = scrollState?.m3e,
    )
}

class AppPageTitleBarScrollState internal constructor(
    internal val miuix: MiuixPageTitleBarScrollState,
    internal val m3e: TopAppBarScrollBehavior,
) {
    fun collapse() {
        miuix.collapse()
        m3e.state.heightOffset = m3e.state.heightOffsetLimit
    }
}

@Composable
fun rememberAppPageTitleBarScrollState(): AppPageTitleBarScrollState {
    val miuix = rememberMiuixPageTitleBarScrollState()
    val m3e = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    return remember(miuix, m3e) { AppPageTitleBarScrollState(miuix, m3e) }
}

@Composable
fun Modifier.appPageTitleBarScroll(state: AppPageTitleBarScrollState): Modifier =
    nestedScroll(if (settingSkinIsMiuix()) state.miuix.nestedScrollConnection else state.m3e.nestedScrollConnection)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppCompactTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    titleTag: String? = null,
    onBack: (() -> Unit)? = null,
    backTag: String? = null,
    containerColor: Color? = null,
    centered: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val navigationIcon: @Composable () -> Unit = {
        onBack?.let { AppBackButton(onClick = it, modifier = backTag?.let { tag -> Modifier.testTag(tag) } ?: Modifier) }
    }
    if (settingSkinIsMiuix()) {
        MiuixCompactTopBar(
            title = title,
            modifier = modifier,
            subtitle = subtitle,
            titleTag = titleTag,
            onBack = onBack,
            backTag = backTag,
            containerColor = containerColor,
            actions = actions,
        )
        return
    }
    val barModifier = modifier
        .appGlassBlur(
            shape = RectangleShape,
            tint = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.30f),
        ).background(appGlassContainerColor(containerColor ?: MaterialTheme.colorScheme.background))
    val titleContent: @Composable () -> Unit = {
        Text(
            text = title,
            modifier = titleTag?.let { Modifier.testTag(it) } ?: Modifier,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    /** 没有副标题时不用带副标题的重载：空的副标题槽会让标题偏离垂直居中。 */
    when {
        !subtitle.isNullOrEmpty() -> TopAppBar(
            title = titleContent,
            subtitle = { Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            modifier = barModifier,
            navigationIcon = navigationIcon,
            actions = actions,
            titleHorizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        )

        centered -> CenterAlignedTopAppBar(
            title = titleContent,
            modifier = barModifier,
            navigationIcon = navigationIcon,
            actions = actions,
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
        )

        else -> TopAppBar(
            title = titleContent,
            modifier = barModifier,
            navigationIcon = navigationIcon,
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        )
    }
}

/** Miuix 0.9.4 无逐项 modifier；testTags 仅用于 Material，Miuix 按容器 tag 和文本定位。 */
@Composable
fun AppTabRow(
    titles: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    scrollable: Boolean = false,
    secondary: Boolean = false,
    testTags: List<String> = emptyList(),
    enabled: Boolean = true,
) {
    if (settingSkinIsMiuix()) {
        MiuixAppTabRow(
            titles = titles,
            selectedIndex = selectedIndex,
            onSelect = onSelect,
            modifier = modifier,
            scrollable = scrollable,
            secondary = secondary,
            enabled = enabled,
        )
        return
    }

    val tagFor: (Int) -> Modifier = { index ->
        testTags.getOrNull(index)?.let { Modifier.testTag(it) } ?: Modifier
    }

    if (scrollable) {
        val content: @Composable () -> Unit = {
            titles.forEachIndexed { index, title ->
                Tab(
                    selected = index == selectedIndex,
                    enabled = enabled,
                    onClick = { onSelect(index) },
                    text = { Text(title) },
                    modifier = tagFor(index),
                )
            }
        }
        if (secondary) {
            SecondaryScrollableTabRow(
                selectedTabIndex = selectedIndex,
                modifier = modifier,
                containerColor = MaterialTheme.colorScheme.background,
                edgePadding = AppDimens.PageHorizontalPadding,
                tabs = content,
            )
        } else {
            PrimaryScrollableTabRow(
                selectedTabIndex = selectedIndex,
                modifier = modifier,
                edgePadding = AppDimens.PageHorizontalPadding,
                tabs = content,
            )
        }
        return
    }

    if (secondary) {
        SecondaryTabRow(selectedTabIndex = selectedIndex, modifier = modifier) {
            titles.forEachIndexed { index, title ->
                Tab(
                    selected = index == selectedIndex,
                    enabled = enabled,
                    onClick = { onSelect(index) },
                    text = { Text(title) },
                    modifier = tagFor(index),
                )
            }
        }
        return
    }

    PrimaryTabRow(selectedTabIndex = selectedIndex, modifier = modifier) {
        titles.forEachIndexed { index, title ->
            Tab(
                selected = index == selectedIndex,
                enabled = enabled,
                onClick = { onSelect(index) },
                text = { Text(title) },
                modifier = tagFor(index),
            )
        }
    }
}

@Composable
internal expect fun MiuixAppTabRow(
    titles: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier,
    scrollable: Boolean,
    secondary: Boolean,
    enabled: Boolean,
)

@Composable
fun AppBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    contentDescription: String = "返回",
    colors: IconButtonColors? = null,
) {
    val image = icon ?: Icons.AutoMirrored.Filled.ArrowBack
    Box(modifier.size(AppDimens.TouchTarget), contentAlignment = Alignment.Center) {
        val buttonModifier = Modifier.size(AppDimens.IconButtonMinSize)
        if (settingSkinIsMiuix() && colors == null) {
            MiuixBackButton(onClick, buttonModifier, image, contentDescription)
        } else {
            AppIconButton(
                onClick = onClick,
                modifier = buttonModifier,
                colors = colors ?: IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                        .copy(alpha = 0.5f)
                        .compositeOver(MaterialTheme.colorScheme.surface),
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                Icon(imageVector = image, contentDescription = contentDescription)
            }
        }
    }
}
