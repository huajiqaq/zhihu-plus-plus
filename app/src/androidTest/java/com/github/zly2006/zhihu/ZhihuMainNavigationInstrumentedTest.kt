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

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.zly2006.zhihu.account.LocalZhihuAccountStore
import com.github.zly2006.zhihu.account.androidZhihuAccountStore
import com.github.zly2006.zhihu.navigation.Account
import com.github.zly2006.zhihu.navigation.AppNavHost
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.Daily
import com.github.zly2006.zhihu.navigation.Follow
import com.github.zly2006.zhihu.navigation.Home
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.MainTabs
import com.github.zly2006.zhihu.navigation.OnlineHistory
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.navigation.Video
import com.github.zly2006.zhihu.navigation.WriteQuestion
import com.github.zly2006.zhihu.navigation.rememberAppNavHost
import com.github.zly2006.zhihu.test.MainActivityComposeRule
import com.github.zly2006.zhihu.test.resetAppPreferences
import com.github.zly2006.zhihu.theme.ZhihuTheme
import com.github.zly2006.zhihu.ui.PREFERENCE_NAME
import com.github.zly2006.zhihu.ui.QUESTION_SCREEN_LIST_TAG
import com.github.zly2006.zhihu.ui.WRITE_QUESTION_BACK_BUTTON_TAG
import com.github.zly2006.zhihu.ui.WRITE_QUESTION_TITLE_TAG
import com.github.zly2006.zhihu.ui.ZhihuMain
import com.github.zly2006.zhihu.ui.rememberZhihuMainTabsState
import com.github.zly2006.zhihu.ui.subscreens.BOTTOM_BAR_ITEMS_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.subscreens.COLLECTION_DIRECT_BROWSE_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.subscreens.START_DESTINATION_PREFERENCE_KEY
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.github.zly2006.zhihu.ui.components.AppAlertDialog as AlertDialog

@RunWith(AndroidJUnit4::class)
class ZhihuMainNavigationInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val deterministicBottomBarItems = linkedSetOf(
        Home.name,
        Follow.name,
        Daily.name,
        OnlineHistory.name,
        Account.name,
    )

    @Before
    fun resetPreferences() {
        composeRule.resetAppPreferences()
    }

    /**
     * 页面隐藏时独立窗口必须同步隐藏，返回后恢复窗口，避免评论窗口遮住跳转目标。
     */
    @Test
    fun hiddenDetailDismissesWindowAndRestoresItOnBack() {
        val width = mutableStateOf(1000.dp)
        composeRule.launchZhihuMainWithFakeArticle(width = { width.value })
        composeRule.runOnIdle { composeRule.activity.navigate(Article(type = ArticleType.Answer, id = 318L)) }
        composeRule.runOnIdle { width.value = 400.dp }
        composeRule.onNodeWithTag("article_dialog").performClick()
        composeRule.onNodeWithText("从弹窗打开问题").performClick()
        composeRule.onNodeWithText("从弹窗打开问题").assertDoesNotExist()
        composeRule.onNodeWithTag(QUESTION_SCREEN_LIST_TAG).assertIsDisplayed()
        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.onNodeWithText("从弹窗打开问题").assertIsDisplayed()

        composeRule.runOnIdle {
            width.value = 1000.dp
            composeRule.activity.navigate(WriteQuestion)
        }
        composeRule.onNodeWithTag(WRITE_QUESTION_TITLE_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("从弹窗打开问题").assertDoesNotExist()
        composeRule.onNodeWithTag(WRITE_QUESTION_BACK_BUTTON_TAG).performClick()
        composeRule.onNodeWithText("从弹窗打开问题").assertIsDisplayed()
    }

    private fun MainActivityComposeRule.launchZhihuMainWithFakeArticle(
        width: (() -> Dp)? = null,
        onVideo: ((AppNavHost) -> Unit)? = null,
        onQuestionHost: ((AppNavHost) -> Unit)? = null,
        height: () -> Dp = { 600.dp },
    ) {
        activity.getSharedPreferences(PREFERENCE_NAME, android.content.Context.MODE_PRIVATE).edit(commit = true) {
            putString(START_DESTINATION_PREFERENCE_KEY, Home.name)
            putStringSet(BOTTOM_BAR_ITEMS_PREFERENCE_KEY, deterministicBottomBarItems)
            putBoolean("bottomBarTapScrollToTop", false)
            putBoolean("autoHideBottomBar", false)
            putBoolean(COLLECTION_DIRECT_BROWSE_PREFERENCE_KEY, false)
        }
        activity.setContent { }
        waitForIdle()
        activity.setContent {
            CompositionLocalProvider(LocalZhihuAccountStore provides androidZhihuAccountStore(activity)) {
            ZhihuTheme {
                val navHost = rememberAppNavHost(MainTabs)
                val tabsState = rememberZhihuMainTabsState()
                activity.tabsState = tabsState
                LaunchedEffect(navHost) {
                    activity.navHost = navHost
                }
                ZhihuMain(
                    modifier = (width?.let { Modifier.wrapContentSize(Alignment.TopStart, unbounded = true).requiredSize(it(), height()) } ?: Modifier)
                        .testTag("zhihu_main_window"),
                    navHost = navHost,
                    tabsState = tabsState,
                    navigate = { destination ->
                        if (destination is Video && onVideo != null) onVideo(navHost) else activity.navigate(destination)
                    },
                    navigateContent = { destination, controller ->
                        if (destination is Question) onQuestionHost?.invoke(controller)
                        if (destination is Video && onVideo != null) onVideo(controller) else activity.navigate(destination, controller)
                    },
                    enableLandscapeListDetail = true,
                    articleContent = { _ ->
                        val navigator = LocalNavigator.current
                        var expanded by rememberSaveable { mutableStateOf(false) }
                        var dialogVisible by rememberSaveable { mutableStateOf(false) }
                        if (dialogVisible) {
                            AlertDialog(
                                onDismissRequest = { dialogVisible = false },
                                text = { Text("详情窗口") },
                                confirmButton = {
                                    Button(onClick = { navigator.onNavigate(Question(318L, "测试问题")) }) {
                                        Text("从弹窗打开问题")
                                    }
                                },
                            )
                        }
                        Column(Modifier.fillMaxSize().testTag("article_content")) {
                            Button(onClick = { dialogVisible = true }, modifier = Modifier.testTag("article_dialog")) {
                                Text("打开窗口")
                            }
                            Button(onClick = { expanded = true }, modifier = Modifier.testTag("article_state")) {
                                Text(if (expanded) "状态已保留" else "更改状态")
                            }
                            Button(onClick = { navigator.onNavigate(Video(1L)) }, modifier = Modifier.testTag("article_video_link")) {
                                Text("播放视频")
                            }
                            Button(
                                onClick = {
                                    navigator.onNavigate(Question(318L, "测试问题"))
                                },
                                modifier = Modifier.testTag("article_question_link"),
                            ) {
                                Text("打开问题")
                            }
                        }
                    },
                )
            }
            }
        }
        waitForIdle()
    }
}
