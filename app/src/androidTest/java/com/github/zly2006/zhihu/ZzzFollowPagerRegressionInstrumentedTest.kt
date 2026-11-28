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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.zly2006.zhihu.test.MainActivityComposeRule
import com.github.zly2006.zhihu.test.resetAppPreferences
import com.github.zly2006.zhihu.test.setScreenContent
import com.github.zly2006.zhihu.ui.FollowScreen
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// 这些分页手势之后启动其他 Activity 测试时，Android 模拟器的输入通道可能超时。
// 保持本类在 AndroidJUnitRunner 按名称排序的末尾，让设备测试进程在本类结束后立即退出。
@RunWith(AndroidJUnit4::class)
class ZzzFollowPagerRegressionInstrumentedTest {
    @get:Rule
    val composeRule: MainActivityComposeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        composeRule.resetAppPreferences(forceMock = true)
    }

    /**
     * 边界反向手势后父分页必须落在整页。
     */
    @Test
    fun boundaryReverseGesture_settlesParentToWholePage() {
        val outerPagerState = AtomicReference<PagerState>()
        composeRule.setScreenContent {
            val state = rememberPagerState(initialPage = 1, pageCount = { 3 })
            SideEffect { outerPagerState.set(state) }
            HorizontalPager(
                state = state,
                modifier = Modifier.fillMaxSize(),
                pageNestedScrollConnection = object : NestedScrollConnection {},
            ) { page ->
                if (page == 1) {
                    FollowScreen(
                        scrollToTopTrigger = 0,
                        innerPadding = PaddingValues(),
                        parentPagerState = state,
                    )
                } else {
                    Box(Modifier.fillMaxSize())
                }
            }
        }

        repeat(10) { iteration ->
            composeRule.onNodeWithTag("follow_screen_tab_2").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("follow_screen_tab_2").assertIsSelected()

            injectReverseBoundaryGesture()
            composeRule.mainClock.advanceTimeBy(1_000)
            composeRule.waitForIdle()

            composeRule.runOnIdle {
                val state = outerPagerState.get()
                val failureContext = "iteration=$iteration isScrollInProgress=${state.isScrollInProgress}"
                assertEquals(failureContext, 1, state.currentPage)
                assertEquals(failureContext, 0f, state.currentPageOffsetFraction, 0.001f)
            }
        }

        composeRule.onRoot().performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            assertEquals(2, outerPagerState.get().currentPage)
            assertEquals(0f, outerPagerState.get().currentPageOffsetFraction, 0.001f)
        }
    }

    private fun injectReverseBoundaryGesture() {
        composeRule.onRoot().performTouchInput {
            fun point(xFraction: Float) = Offset(width * xFraction, height * 0.55f)

            fun moveToFraction(xFraction: Float) {
                advanceEventTime(55)
                moveTo(point(xFraction))
            }

            down(point(0.72f))
            moveToFraction(0.58f)
            moveToFraction(0.40f)
            moveToFraction(0.22f)
            moveToFraction(0.26f)
            moveToFraction(0.28f)
            advanceEventTime(55)
            up()
        }
    }
}
