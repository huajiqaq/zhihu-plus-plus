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

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.zly2006.zhihu.MainActivity
import com.github.zly2006.zhihu.navigation.AppNavHost
import com.github.zly2006.zhihu.platform.androidUserMessageSink
import com.github.zly2006.zhihu.viewmodel.ArticleViewModel

@Composable
fun AndroidZhihuMain(navHost: AppNavHost) {
    val activity = LocalActivity.current as MainActivity
    val isTablet = LocalConfiguration.current.smallestScreenWidthDp >= 600
    ZhihuMain(
        navHost = navHost,
        mainTabNavigationTarget = activity.mainTabNavigationTarget,
        navigate = { route -> activity.navigate(route) },
        navigateContent = { route, targetHost -> activity.navigate(route, targetHost) },
        enableLandscapeListDetail = true,
        isListDetailCapable = isTablet,
        setCurrentMainTabOpenFrom = activity::setCurrentMainTabOpenFrom,
        consumeMainTabNavigationTarget = activity::consumeMainTabNavigationTarget,
        preferenceState = rememberZhihuMainPreferenceState(),
        isDarkTheme = com.github.zly2006.zhihu.theme.ThemeManager.isDarkTheme,
        articleContent = { article ->
            val entryLifecycle = LocalLifecycleOwner.current
            val viewModel: ArticleViewModel = viewModel {
                ArticleViewModel(article, activity.httpClient, androidUserMessageSink(activity)) { onPause ->
                    entryLifecycle.lifecycle.addObserver(object : DefaultLifecycleObserver {
                        override fun onPause(owner: LifecycleOwner) {
                            onPause()
                        }
                    })
                }
            }
            ArticleScreen(article, viewModel)
        },
        sentenceSimilarityContent = {
            SentenceSimilarityTestScreen()
        },
        blocklistSettingsNlpContent = { onNavigateBack ->
            NLPKeywordManagementScreen(
                innerPadding = PaddingValues(),
                onNavigateBack = onNavigateBack,
            )
        },
    )
}
