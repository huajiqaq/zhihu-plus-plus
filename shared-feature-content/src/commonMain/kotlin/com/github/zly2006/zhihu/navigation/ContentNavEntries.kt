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

package com.github.zly2006.zhihu.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import com.github.zly2006.zhihu.ui.CollectionContentScreen
import com.github.zly2006.zhihu.ui.CollectionScreen
import com.github.zly2006.zhihu.ui.PeopleScreen
import com.github.zly2006.zhihu.ui.PinScreen
import com.github.zly2006.zhihu.ui.QuestionScreen
import com.github.zly2006.zhihu.ui.TopicScreen
import com.github.zly2006.zhihu.ui.WriteAnswerChooserScreen
import com.github.zly2006.zhihu.ui.WriteAnswerScreen
import com.github.zly2006.zhihu.ui.WriteArticleScreen
import com.github.zly2006.zhihu.ui.WritePinScreen
import com.github.zly2006.zhihu.ui.WriteQuestionScreen
import com.github.zly2006.zhihu.ui.components.WebPageScreen
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection

fun AppNavEntryBuilder.contentMainEntries(
    swipeBack: NavSwipeDirection,
    articleContent: @Composable (Article) -> Unit,
) {
    webPageEntry()
    entry<Question>(swipeDismiss = swipeBack) { question ->
        QuestionScreen(question)
    }
    entry<Topic>(swipeDismiss = swipeBack) { topic ->
        TopicScreen(topic)
    }
    entry<WriteAnswerChooser>(swipeDismiss = swipeBack) { args ->
        WriteAnswerChooserScreen(args)
    }
    entry<WriteAnswer>(swipeDismiss = swipeBack) { args ->
        WriteAnswerScreen(args)
    }
    entry<WritePin>(swipeDismiss = swipeBack) { args ->
        WritePinScreen(args)
    }
    entry<WriteQuestion>(swipeDismiss = swipeBack) {
        WriteQuestionScreen(WriteQuestion)
    }
    entry<WriteArticle>(swipeDismiss = swipeBack) {
        WriteArticleScreen(WriteArticle)
    }
    entry<Article>(swipeDismiss = swipeBack) { article -> articleContent(article) }
}

fun AppNavEntryBuilder.contentBrowseEntries(
    innerPadding: PaddingValues,
    swipeBack: NavSwipeDirection,
) {
    entry<Collections>(swipeDismiss = swipeBack) { data ->
        CollectionScreen(
            urlToken = data.userToken,
            contentPadding = innerPadding,
        )
    }
    entry<CollectionContent>(swipeDismiss = swipeBack) { content ->
        CollectionContentScreen(content.collectionId)
    }
    entry<Person>(swipeDismiss = swipeBack) { person ->
        PeopleScreen(person)
    }
    entry<Pin>(swipeDismiss = swipeBack) { pin ->
        PinScreen(pin)
    }
}

fun AppNavEntryBuilder.contentDetailEntries(
    swipeBack: NavSwipeDirection,
    articleContent: @Composable (Article) -> Unit,
) {
    webPageEntry()
    entry<Article>(swipeDismiss = swipeBack) { article -> articleContent(article) }
    entry<Pin>(swipeDismiss = swipeBack) { pin -> PinScreen(pin) }
    entry<Question>(swipeDismiss = swipeBack) { question -> QuestionScreen(question) }
}

private fun AppNavEntryBuilder.webPageEntry() {
    entry<WebPage>(swipeDismiss = NavSwipeDirection.None) { page ->
        WebPageScreen(page.url, onClose = LocalNavigator.current.onNavigateBack)
    }
}
