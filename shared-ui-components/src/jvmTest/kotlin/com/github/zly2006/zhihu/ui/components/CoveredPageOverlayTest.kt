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

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.compose.LocalSavedStateRegistryOwner
import com.github.zly2006.zhihu.theme.ThemeManager
import com.github.zly2006.zhihu.theme.ThemeMode
import com.github.zly2006.zhihu.theme.ThemeStyle
import com.github.zly2006.zhihu.theme.ZhihuTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** 宿主仍 RESUMED 时，STARTED 条目表示被覆盖；宿主暂停本身不卸载弹窗。 */
@OptIn(ExperimentalTestApi::class)
class CoveredPageOverlayTest {
    @Test
    fun appDialogHidesWhileItsPageIsCoveredAndReturns() = forEachSkin { style ->
        assertHiddenWhileCovered(OverlayKind.Dialog, style)
    }

    @Test
    fun alertDialogAndSheetsHideWhileTheirPageIsCovered() = forEachSkin { style ->
        assertHiddenWhileCovered(OverlayKind.AlertDialog, style)
        assertHiddenWhileCovered(OverlayKind.BottomSheet, style)
        assertHiddenWhileCovered(OverlayKind.ShareSheet, style)
    }

    /** 切后台、权限框、选图器、桌面窗口失焦只让宿主暂停；卸载弹窗会丢掉里面的 ActivityResult 回调和协程。 */
    @Test
    fun hostPauseKeepsTheOverlayComposedUntilThePageIsCovered() = forEachSkin { style ->
        val host = TestHostOwner(Lifecycle.State.STARTED)
        val entry = TestEntryOwner(host.lifecycle)
        var bodyEntered = 0
        var bodyDisposed = 0
        runDesktopComposeUiTest(width = 412, height = 640) {
            setContent {
                PageHost(host, entry, AppOverlayBlurController()) {
                    AppAlertDialog(
                        onDismissRequest = {},
                        title = { Text(OVERLAY_TEXT) },
                        text = {
                            DisposableEffect(Unit) {
                                bodyEntered++
                                onDispose { bodyDisposed++ }
                            }
                            Text("正文")
                        },
                        confirmButton = { AppTextButton("确定", {}) },
                    )
                }
            }
            awaitOverlay(shown = true, style)

            val hostStates = listOf(Lifecycle.State.RESUMED, Lifecycle.State.STARTED, Lifecycle.State.CREATED, Lifecycle.State.RESUMED)
            for (state in hostStates) {
                runOnIdle { host.lifecycle.currentState = state }
                waitForIdle()
                runOnIdle {
                    assertEquals(1, bodyEntered, "$style 宿主切到 $state 后弹窗不应重新进入组合")
                    assertEquals(0, bodyDisposed, "$style 宿主切到 $state 后弹窗不应离开组合")
                }
            }

            runOnIdle {
                host.lifecycle.currentState = Lifecycle.State.STARTED
                entry.maxLifecycle = Lifecycle.State.STARTED
            }
            waitForIdle()
            runOnIdle { assertEquals(0, bodyDisposed, "$style 宿主暂停期间页面被盖住时还看不出来，弹窗应保持原样") }

            runOnIdle { host.lifecycle.currentState = Lifecycle.State.RESUMED }
            awaitOverlay(shown = false, style)
            runOnIdle { assertEquals(1, bodyDisposed, "$style 宿主恢复后发现页面已被盖住，弹窗应离开组合") }

            runOnIdle { entry.maxLifecycle = Lifecycle.State.RESUMED }
            awaitOverlay(shown = true, style)
            runOnIdle { assertEquals(2, bodyEntered, "$style 回到页面后弹窗应重新出现") }
        }
    }

    private fun assertHiddenWhileCovered(kind: OverlayKind, style: ThemeStyle) {
        val host = TestHostOwner(Lifecycle.State.RESUMED)
        val entry = TestEntryOwner(host.lifecycle)
        val blur = AppOverlayBlurController()
        val overlayText = if (kind == OverlayKind.ShareSheet) "分享" else OVERLAY_TEXT
        var dismissCalls = 0
        runDesktopComposeUiTest(width = 412, height = 640) {
            setContent {
                PageHost(host, entry, blur) {
                    OverlayUnderTest(kind, onDismissRequest = { dismissCalls++ })
                }
            }
            awaitOverlay(shown = true, "$style $kind", overlayText)
            runOnIdle { assertEquals(1, blur.openedOverlayCount, "$style $kind 显示时应模糊背景") }

            runOnIdle { entry.maxLifecycle = Lifecycle.State.STARTED }
            awaitOverlay(shown = false, "$style $kind 页面被盖住", overlayText)
            runOnIdle { assertEquals(0, blur.openedOverlayCount, "$style $kind 隐藏后新页面不应继续被模糊") }

            runOnIdle { entry.maxLifecycle = Lifecycle.State.RESUMED }
            awaitOverlay(shown = true, "$style $kind 回到页面", overlayText)
            runOnIdle {
                assertEquals(1, blur.openedOverlayCount, "$style $kind 重新出现后应恢复模糊")
                assertEquals(0, dismissCalls, "$style $kind 隐藏不是关闭，不应调用 onDismissRequest")
            }
        }
    }

    private fun ComposeUiTest.awaitOverlay(shown: Boolean, label: Any, text: String = OVERLAY_TEXT) {
        waitUntil("$label：弹窗应${if (shown) "显示" else "隐藏"}", timeoutMillis = 5_000) {
            if (shown) {
                runCatching { onNodeWithText(text, useUnmergedTree = true).assertIsDisplayed() }.isSuccess
            } else {
                onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
            }
        }
    }

    private fun forEachSkin(body: (ThemeStyle) -> Unit) {
        val original = ThemeManager.snapshot()
        try {
            for (style in ThemeStyle.entries) {
                ThemeManager.load(original.copy(themeStyle = style, themeMode = ThemeMode.LIGHT))
                body(style)
            }
        } finally {
            ThemeManager.load(original)
        }
    }
}

private const val OVERLAY_TEXT = "被盖住时隐藏的弹窗"

private enum class OverlayKind { Dialog, AlertDialog, BottomSheet, ShareSheet }

/** 与 ZhihuMain 相同的层级：宿主在根部提供生命周期并登记为 [LocalNavHostLifecycleOwner]，导航条目替换 LocalLifecycleOwner。 */
@Composable
private fun PageHost(
    host: TestHostOwner,
    entry: TestEntryOwner,
    blur: AppOverlayBlurController,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalLifecycleOwner provides host,
        LocalSavedStateRegistryOwner provides host,
        LocalNavHostLifecycleOwner provides host,
        LocalAppOverlayBlurController provides blur,
    ) {
        ZhihuTheme {
            val dispatcherOwner = LocalNavigationEventDispatcherOwner.current
                ?: rememberNavigationEventDispatcherOwner(parent = null)
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides dispatcherOwner) {
                AppScaffold {
                    CompositionLocalProvider(LocalLifecycleOwner provides entry, content = content)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OverlayUnderTest(kind: OverlayKind, onDismissRequest: () -> Unit) {
    when (kind) {
        OverlayKind.Dialog -> AppDialog(
            onDismissRequest = onDismissRequest,
            confirmText = "确定",
            onConfirm = {},
            title = OVERLAY_TEXT,
            dismissText = "取消",
        )

        OverlayKind.AlertDialog -> AppAlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text(OVERLAY_TEXT) },
            confirmButton = { AppTextButton("确定", {}) },
        )

        OverlayKind.BottomSheet -> AppBottomSheet(onDismissRequest = onDismissRequest) {
            Text(OVERLAY_TEXT)
        }

        OverlayKind.ShareSheet -> ShareDialogContent(
            showDialog = true,
            onDismissRequest = onDismissRequest,
            onShareClick = {},
            onCopyClick = {},
            onSettingsClick = {},
        )
    }
}

private class TestHostOwner(
    initial: Lifecycle.State,
) : SavedStateRegistryOwner {
    override val lifecycle: LifecycleRegistry = LifecycleRegistry.createUnsafe(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    init {
        savedStateController.performRestore(null)
        lifecycle.currentState = initial
    }
}

/** 导航条目：跟随宿主，但不超过 [maxLifecycle]；页面被盖住时上限是 STARTED。 */
private class TestEntryOwner(
    private val host: Lifecycle,
) : LifecycleOwner {
    override val lifecycle: LifecycleRegistry = LifecycleRegistry.createUnsafe(this)

    var maxLifecycle: Lifecycle.State = Lifecycle.State.RESUMED
        set(value) {
            field = value
            sync()
        }

    init {
        host.addObserver(LifecycleEventObserver { _, _ -> sync() })
    }

    private fun sync() {
        lifecycle.currentState = minOf(host.currentState, maxLifecycle)
    }
}
