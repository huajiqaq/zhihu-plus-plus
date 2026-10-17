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

import android.content.Context
import androidx.activity.BackEventCompat
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.core.content.edit
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.zly2006.zhihu.navigation.Account
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.Home
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.platform.PlatformBackHandler
import com.github.zly2006.zhihu.test.resetAppPreferences
import com.github.zly2006.zhihu.test.setZhihuMainContent
import com.github.zly2006.zhihu.theme.ThemeManager
import com.github.zly2006.zhihu.theme.ZhihuTheme
import com.github.zly2006.zhihu.ui.PREFERENCE_NAME
import com.github.zly2006.zhihu.ui.ZhihuMain
import com.github.zly2006.zhihu.ui.rememberAndroidZhihuMainPreferenceState
import com.github.zly2006.zhihu.ui.subscreens.APPEARANCE_SETTINGS_SCROLL_TAG
import com.github.zly2006.zhihu.ui.subscreens.COLLECTION_DIRECT_BROWSE_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.subscreens.START_DESTINATION_PREFERENCE_KEY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * Regression for https://github.com/zly2006/zhihu-plus-plus/issues/494:
 * 关闭“启用预测性返回”后，返回手势不应再驱动页面预览动画。
 *
 * Fixed by: 本地提交（待审阅，未开 PR）
 */
@RunWith(AndroidJUnit4::class)
class PredictiveBackPreferenceInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun resetPreferences() {
        composeRule.resetAppPreferences()
    }

    @Test
    fun predictiveBackDisabledKeepsPageStillDuringBackGesture() {
        openAppearanceSettings(predictiveBackEnabled = false)
        val before = settingsPaneBounds()

        dispatchPredictiveBack(progress = 0.6f)
        settlePredictiveBack()

        val during = settingsPaneBounds()
        assertEquals(
            "关闭预测性返回后页面不应跟随手势横移",
            before.left,
            during.left,
            1f,
        )
        assertTrue("预测手势进行中设置页仍应可见", settingsVisible())

        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.dispatchOnBackCancelled() }
        composeRule.waitForIdle()
        assertTrue("取消手势后设置页应保持可见", settingsVisible())

        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitUntil(5_000) { !settingsVisible() }
    }

    @Test
    fun predictiveBackEnabledKeepsPreviewAnimation() {
        openAppearanceSettings(predictiveBackEnabled = true)
        val before = settingsPaneBounds()

        dispatchPredictiveBack(progress = 0.6f)
        composeRule.waitUntil(3_000) { settingsPaneBounds().left > before.left + 20f }
        val during = settingsPaneBounds()
        assertTrue(
            "开启预测性返回时页面应跟随手势横移，before=${before.left}, during=${during.left}",
            during.left > before.left + 20f,
        )

        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.dispatchOnBackCancelled() }
        composeRule.waitUntil(3_000) { abs(settingsPaneBounds().left - before.left) < 2f }
        assertTrue(settingsVisible())
    }

    @Test
    fun turningPreferenceOffTakesEffectWithoutRestart() {
        openAppearanceSettings(predictiveBackEnabled = true)
        composeRule
            .onNodeWithTag(APPEARANCE_SETTINGS_SCROLL_TAG)
            .performScrollToNode(hasText("启用预测性返回"))
        composeRule.onNodeWithText("启用预测性返回").performClick()
        composeRule.waitForIdle()

        val stored = composeRule.activity
            .getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)
            .getBoolean("enable_predictive_back", true)
        assertFalse("点击开关后偏好应写入关闭", stored)

        val before = settingsPaneBounds()
        dispatchPredictiveBack(progress = 0.6f)
        settlePredictiveBack()
        assertEquals(
            "运行中关闭预测性返回后不应再有页面预览位移",
            before.left,
            settingsPaneBounds().left,
            1f,
        )
    }

    @Test
    fun screenBackHandlerKeepsPriorityWhenPredictiveBackDisabled() {
        composeRule.activity
            .getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)
            .edit(commit = true) {
                putBoolean("enable_predictive_back", false)
                putString(START_DESTINATION_PREFERENCE_KEY, Home.name)
                putBoolean("duo3_home_account", false)
                putBoolean("bottomBarTapScrollToTop", false)
                putBoolean("autoHideBottomBar", false)
                putBoolean(COLLECTION_DIRECT_BROWSE_PREFERENCE_KEY, false)
            }
        composeRule.activity.setContent { }
        composeRule.waitForIdle()
        composeRule.activity.setContent {
            ZhihuTheme {
                val navController = rememberNavController()
                LaunchedEffect(navController) {
                    composeRule.activity.navController = navController
                }
                ZhihuMain(
                    navController = navController,
                    mainTabNavigationTarget = composeRule.activity.mainTabNavigationTarget,
                    navigate = composeRule.activity::navigate,
                    navigateContent = composeRule.activity::navigateIn,
                    enableLandscapeListDetail = true,
                    setCurrentMainTabOpenFrom = composeRule.activity::setCurrentMainTabOpenFrom,
                    consumeMainTabNavigationTarget = composeRule.activity::consumeMainTabNavigationTarget,
                    preferenceState = rememberAndroidZhihuMainPreferenceState(),
                    isDarkTheme = ThemeManager.isDarkTheme,
                    enablePredictiveBack = false,
                    articleContent = { _, _ ->
                        val navigator = LocalNavigator.current
                        var overlayOpen by rememberSaveable { mutableStateOf(false) }
                        // 屏幕内的返回处理器晚于导航层回退处理器注册，必须继续优先消费返回。
                        PlatformBackHandler(enabled = overlayOpen) { overlayOpen = false }
                        Column(Modifier.fillMaxSize()) {
                            if (overlayOpen) {
                                Text("screen overlay", Modifier.testTag("screen_overlay"))
                            }
                            Button(
                                onClick = { overlayOpen = true },
                                modifier = Modifier.testTag("open_screen_overlay"),
                            ) {
                                Text("open")
                            }
                            Button(
                                onClick = { navigator.onNavigate(Question(318L, "测试问题")) },
                                modifier = Modifier.testTag("article_question_link"),
                            ) {
                                Text("question")
                            }
                        }
                    },
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { composeRule.activity.navigate(Article(type = ArticleType.Answer, id = 318L)) }
        composeRule.waitUntil(10_000) { nodeExists("article_question_link") }

        composeRule.onNodeWithTag("open_screen_overlay").performClick()
        composeRule.waitUntil(5_000) { nodeExists("screen_overlay") }
        val before = composeRule.onNodeWithTag("article_question_link").fetchSemanticsNode().boundsInRoot

        dispatchPredictiveBack(progress = 0.6f)
        settlePredictiveBack()
        assertEquals(
            "预测性返回关闭时，文章页也不应跟随手势横移",
            before.left,
            composeRule
                .onNodeWithTag("article_question_link")
                .fetchSemanticsNode()
                .boundsInRoot.left,
            1f,
        )

        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitUntil(5_000) { !nodeExists("screen_overlay") }
        assertTrue("关闭屏幕内浮层不应退出文章页", nodeExists("article_question_link"))

        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitUntil(5_000) { !nodeExists("article_question_link") }
    }

    private fun nodeExists(tag: String): Boolean = composeRule
        .onAllNodesWithTag(tag)
        .fetchSemanticsNodes()
        .isNotEmpty()

    private fun openAppearanceSettings(predictiveBackEnabled: Boolean) {
        composeRule.activity
            .getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)
            .edit(commit = true) {
                putBoolean("enable_predictive_back", predictiveBackEnabled)
            }
        composeRule.setZhihuMainContent()
        composeRule.runOnIdle { composeRule.activity.navigate(Account.AppearanceSettings()) }
        composeRule.waitUntil(10_000) { settingsVisible() }
        composeRule.waitForIdle()
    }

    private fun settingsVisible(): Boolean = composeRule
        .onAllNodesWithTag(APPEARANCE_SETTINGS_SCROLL_TAG)
        .fetchSemanticsNodes()
        .isNotEmpty()

    private fun settingsPaneBounds(): Rect = composeRule
        .onNodeWithTag(APPEARANCE_SETTINGS_SCROLL_TAG)
        .fetchSemanticsNode()
        .boundsInRoot

    private fun dispatchPredictiveBack(progress: Float) {
        composeRule.runOnIdle {
            with(composeRule.activity.onBackPressedDispatcher) {
                dispatchOnBackStarted(
                    BackEventCompat(0f, 600f, 0f, BackEventCompat.EDGE_LEFT),
                )
                dispatchOnBackProgressed(
                    BackEventCompat(600f, 600f, progress, BackEventCompat.EDGE_LEFT),
                )
            }
        }
    }

    private fun settlePredictiveBack() {
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()
        Thread.sleep(300)
        composeRule.waitForIdle()
    }
}
