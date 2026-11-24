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

package com.github.zly2006.zhihu

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.github.zly2006.zhihu.platform.androidSettingsStore
import com.github.zly2006.zhihu.test.resetAppPreferences
import com.github.zly2006.zhihu.test.setScreenContent
import com.github.zly2006.zhihu.ui.components.AppBottomBar
import com.github.zly2006.zhihu.ui.components.AppScaffold
import com.github.zly2006.zhihu.ui.components.BottomBarItem
import com.github.zly2006.zhihu.ui.components.LocalAppGlassEnabled
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 33)
class AppleBottomBarInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun glassSamplesThePageAndPreservesSelectionWhenDisabled() {
        compose.resetAppPreferences(forceMock = true)
        androidSettingsStore(compose.activity).putString("floatingBottomBarStyle", "apple")
        val glass = mutableStateOf(true)
        val reverse = mutableStateOf(false)
        val selected = mutableStateOf(0)
        val clicks = mutableListOf<Int>()
        var margin = 0f
        compose.setScreenContent {
            margin = with(LocalDensity.current) { 8.dp.toPx() }
            CompositionLocalProvider(LocalAppGlassEnabled provides glass.value) {
                AppScaffold(
                    bottomBar = {
                        AppBottomBar(
                            items = listOf("主页", "关注", "日报").mapIndexed { index, label ->
                                BottomBarItem(label, Icons.Default.Home, "apple_device_$index", selected.value == index) {
                                    clicks += index
                                    selected.value = index
                                }
                            },
                            modifier = Modifier.testTag("apple_device_bar"),
                            floating = true,
                        )
                    },
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val stripe = size.width / 12f
                        for (index in 0 until 12) {
                            val red = (index % 2 == 0) != reverse.value
                            drawRect(if (red) Color.Red else Color.Blue, Offset(index * stripe, 0f), Size(stripe, size.height))
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("apple_device_1").performClick().assertIsSelected()
        compose.onNodeWithTag("apple_device_1").performClick()
        assertEquals(listOf(1, 1), clicks)
        val item = compose.onNodeWithTag("apple_device_0").fetchSemanticsNode().boundsInRoot
        val rowTop = item.top + margin
        val rowBottom = item.bottom - margin
        val bandLeft = item.left + margin
        val bandRight = item.left + margin * 2f

        fun sampleBand(): List<Color> {
            val image = compose.onRoot().captureToImage().toPixelMap()
            return buildList {
                for (y in ceil(rowTop).toInt() until floor(rowBottom).toInt()) {
                    for (x in ceil(bandLeft).toInt() until floor(bandRight).toInt()) add(image[x, y])
                }
            }
        }
        val before = sampleBand()
        assertTrue(before.any { abs(it.red - it.blue) > 0.25f })
        compose.runOnIdle { reverse.value = true }
        val changed = sampleBand()
        assertEquals(before.size, changed.size)
        assertTrue(before.zip(changed).count { (a, b) -> abs(a.red - b.red) + abs(a.blue - b.blue) > 0.2f } > before.size / 4)
        compose.runOnIdle { glass.value = false }
        val solid = sampleBand()
        compose.runOnIdle { reverse.value = false }
        val solidAfter = sampleBand()
        assertEquals("The disabled material must not sample the page", solid, solidAfter)
        compose.onNodeWithTag("apple_device_1").assertIsSelected()
        compose.onNodeWithTag("apple_device_2").performClick().assertIsSelected()
        assertEquals(listOf(1, 1, 2), clicks)

        val root = compose.onRoot()
        val sourceBounds = compose.onNodeWithTag("apple_device_2").fetchSemanticsNode().boundsInRoot
        val targetBounds = compose.onNodeWithTag("apple_device_1").fetchSemanticsNode().boundsInRoot
        val start = sourceBounds.center
        val target = targetBounds.center
        val probe = target + (start - target) * 0.35f

        fun capsulePixel() = root.captureToImage().toPixelMap()[probe.x.toInt(), probe.y.toInt()]
        val empty = capsulePixel()
        root.performTouchInput {
            down(start)
            moveTo((start + target) / 2f)
        }
        assertTrue("半程胶囊应覆盖下一项靠近手指的一侧", capsulePixel() != empty)
        compose.onNodeWithTag("apple_device_2").assertIsSelected()
        assertEquals(listOf(1, 1, 2), clicks)
        root.performTouchInput { cancel() }
        assertEquals(empty, capsulePixel())
        root.performTouchInput {
            down(start)
            moveTo(target)
            up()
        }
        compose.onNodeWithTag("apple_device_1").assertIsSelected()
        assertEquals(listOf(1, 1, 2, 1), clicks)
    }
}
