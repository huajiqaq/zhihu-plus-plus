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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fleeksoft.ksoup.Ksoup
import com.github.zly2006.zhihu.account.rememberZhihuAccountStore
import com.github.zly2006.zhihu.data.Feed
import com.github.zly2006.zhihu.data.MobileNotificationTimelineItem
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.data.ZhihuPaging
import com.github.zly2006.zhihu.i18n.ContentStrings
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.navigation.Search
import com.github.zly2006.zhihu.navigation.WriteAnswer
import com.github.zly2006.zhihu.navigation.WriteAnswerChooser
import com.github.zly2006.zhihu.navigation.resolveContent
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.ui.components.AppButton
import com.github.zly2006.zhihu.ui.components.AppCard
import com.github.zly2006.zhihu.ui.components.AppCompactTopBar
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppEmptyState
import com.github.zly2006.zhihu.ui.components.AppPullToRefreshBox
import com.github.zly2006.zhihu.ui.components.AppScaffold
import com.github.zly2006.zhihu.ui.components.AppSpinner
import com.github.zly2006.zhihu.ui.components.AppTabRow
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.ui.components.PaginatedList
import com.github.zly2006.zhihu.ui.components.settingSkinIsMiuix
import com.github.zly2006.zhihu.viewmodel.ZhihuApiEnvironment
import com.github.zly2006.zhihu.viewmodel.rememberPaginationEnvironment
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

const val WRITE_ANSWER_CHOOSER_TAG = "write_answer_chooser"
const val WRITE_ANSWER_CHOOSER_BACK_TAG = "write_answer_chooser_back"
const val WRITE_ANSWER_CHOOSER_SEARCH_TAG = "write_answer_chooser_search"
const val WRITE_ANSWER_CHOOSER_RETRY_TAG = "write_answer_chooser_retry"

internal enum class WriteAnswerChoiceTab(
    private val titleText: () -> String,
    val endpoint: String,
    val tag: String,
) {
    Invite({ ContentStrings.invitations }, "https://api.zhihu.com/notifications/v3/timeline/entry/invite", "write_answer_tab_invite"),
    Recommended({ ContentStrings.recommended }, "https://api.zhihu.com/creators/question_route/notification_related/recommended", "write_answer_tab_recommended"),
    Hot({ ContentStrings.popular }, "https://api.zhihu.com/creators/question_route/notification_related/hot", "write_answer_tab_hot"),
    ;

    val initialUrl: String
        get() = when (this) {
            Invite -> "$endpoint?invite_with_time_slice=1&limit=20"
            else -> "$endpoint?offset=0"
        }

    val title: String get() = titleText()
}

internal data class WriteAnswerChoice(
    val questionId: Long,
    val title: String,
    val summary: String,
    val questionDetail: String = "",
    val hasAnswer: Boolean = false,
    val existingAnswer: Article? = null,
    val sourceQuestion: Feed.QuestionTarget? = null,
) {
    init {
        require(questionId > 0 && title.isNotBlank())
    }

    val displaySummary: String get() = sourceQuestion?.displayDetails ?: summary

    val answerDestination: NavDestination?
        get() = existingAnswer ?: if (hasAnswer) null else WriteAnswer(questionId, title, questionDetail)
}

internal data class WriteAnswerChoicesPage(
    val items: List<WriteAnswerChoice>,
    val paging: ZhihuPaging,
)

/** 两种真实列表的入站解码都经过 ZhihuJson；不使用通知 ViewModel，避免它的 readall 写入。 */
internal fun decodeWriteAnswerChoices(
    raw: JsonObject,
    tab: WriteAnswerChoiceTab,
): WriteAnswerChoicesPage {
    val data = raw["data"] as? JsonArray ?: error(ContentStrings.questionListMissingData)
    val items = data
        .mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            if (tab == WriteAnswerChoiceTab.Invite) {
                val notification = runCatching { ZhihuJson.decodeJson<MobileNotificationTimelineItem>(item) }.getOrNull()
                    ?: return@mapNotNull null
                val target = notification.target ?: return@mapNotNull null
                val id = target.id.toLongOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
                if (target.type != "question" || target.title.isBlank() || notification.content?.isDeleted == true || notification.targetSource?.isDeleted == true) {
                    return@mapNotNull null
                }
                val existingAnswer = (target.myAnswerUrl.takeIf(String::isNotBlank)?.let(::resolveContent) as? Article)
                    ?.takeIf { it.type == ArticleType.Answer && it.id > 0 }
                WriteAnswerChoice(
                    questionId = id,
                    title = target.title,
                    summary = notification.targetSource?.subText.orEmpty(),
                    hasAnswer = target.hasAnswer || target.myAnswerUrl.isNotBlank(),
                    existingAnswer = existingAnswer,
                )
            } else {
                val questionJson = item["question"] as? JsonObject ?: return@mapNotNull null
                val question = runCatching { ZhihuJson.decodeJson<Feed.QuestionTarget>(questionJson) }.getOrNull()
                    ?: return@mapNotNull null
                if (question.id <= 0 || question.type != "question" || question.title.isBlank()) return@mapNotNull null
                WriteAnswerChoice(
                    questionId = question.id,
                    title = question.title,
                    summary = question.detailsText,
                    questionDetail = question.detail,
                    sourceQuestion = question,
                )
            }
        }.distinctBy { it.questionId }
    if (items.isEmpty() && data.any { ((it as? JsonObject)?.get("type") as? JsonPrimitive)?.contentOrNull != "empty" }) {
        error(ContentStrings.questionListDecodeError(data.size))
    }
    val paging = raw["paging"]?.let { ZhihuJson.decodeJson<ZhihuPaging>(it) }
        ?: error(ContentStrings.questionListMissingPaging)
    return WriteAnswerChoicesPage(items, paging)
}

internal class WriteAnswerChoicesViewModel(
    val tab: WriteAnswerChoiceTab,
) : ViewModel() {
    val items = mutableStateListOf<WriteAnswerChoice>()
    var isLoading by mutableStateOf(false)
        private set
    var hasLoaded by mutableStateOf(false)
        private set
    var isEnd by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    private var nextUrl: String? = null
    private var loadJob: Job? = null
    private var generation = 0
    val isRefreshing: Boolean
        get() = isLoading && hasLoaded && nextUrl == null

    fun loadMore(environment: ZhihuApiEnvironment) {
        if (isLoading || isEnd || errorMessage != null) return
        val requestGeneration = generation
        val url = nextUrl ?: tab.initialUrl
        val replacesItems = nextUrl == null
        isLoading = true
        loadJob = viewModelScope.launch {
            try {
                val raw = environment.fetchJson(url, "") ?: error(ContentStrings.questionListEmptyResponse)
                val page = decodeWriteAnswerChoices(raw, tab)
                // 分页只接受当前标签的官方 HTTPS endpoint，且不能原地循环或泄漏凭据到外站。
                val next = if (page.paging.isEnd) {
                    null
                } else {
                    val parsed = Url(page.paging.next)
                    check(
                        parsed.protocol.name == "https" &&
                            parsed.host == "api.zhihu.com" &&
                            parsed.port == 443 &&
                            parsed.encodedPath == Url(tab.endpoint).encodedPath &&
                            parsed.user.isNullOrEmpty() &&
                            parsed.password.isNullOrEmpty() &&
                            page.paging.next != url,
                    ) { ContentStrings.invalidPagingUrl }
                    page.paging.next
                }
                if (requestGeneration != generation) return@launch
                if (replacesItems) items.clear()
                val existingIds = items.mapTo(mutableSetOf()) { it.questionId }
                items.addAll(page.items.filter { existingIds.add(it.questionId) })
                nextUrl = next
                isEnd = page.paging.isEnd
                hasLoaded = true
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (requestGeneration == generation) {
                    errorMessage = error.message?.takeIf(String::isNotBlank) ?: ContentStrings.questionLoadFailedRetry
                }
            } finally {
                if (requestGeneration == generation) isLoading = false
            }
        }
    }

    fun refresh(environment: ZhihuApiEnvironment) {
        generation++
        loadJob?.cancel()
        isLoading = false
        nextUrl = null
        isEnd = false
        errorMessage = null
        loadMore(environment)
    }

    fun retry(environment: ZhihuApiEnvironment) {
        errorMessage = null
        loadMore(environment)
    }
}

@Composable
fun WriteAnswerChooserScreen(args: WriteAnswerChooser) {
    val navigator = LocalNavigator.current
    val environment = rememberPaginationEnvironment(allowGuestAccess = false)
    val accountClient = rememberZhihuAccountStore().client
    val tabs = WriteAnswerChoiceTab.entries
    val models = tabs.map { tab ->
        viewModel(key = "answer_choices_${accountClient.hashCode()}_${tab.name}") { WriteAnswerChoicesViewModel(tab) }
    }
    val scrollStates = tabs.map { tab -> key(accountClient, tab) { rememberLazyListState() } }
    var selectedIndex by rememberSaveable(args.defaultSelectedPage) {
        mutableIntStateOf((args.defaultSelectedPage.takeIf { it in 1..3 } ?: 2) - 1)
    }
    val model = models[selectedIndex]
    val overlayPadding = LocalReadingPlayerOverlayPadding.current
    LaunchedEffect(model, environment) {
        if (!model.hasLoaded) model.loadMore(environment)
    }

    AppScaffold(
        modifier = Modifier.fillMaxSize().testTag(WRITE_ANSWER_CHOOSER_TAG),
        topBar = {
            val textMeasurer = rememberTextMeasurer()
            val titleStyle = if (settingSkinIsMiuix()) LocalAppSkinTokens.current.title3 else MaterialTheme.typography.titleLarge
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val requiredWidth = with(LocalDensity.current) {
                    (
                        textMeasurer.measure(ContentStrings.answerQuestions, style = titleStyle, softWrap = false).size.width +
                            textMeasurer.measure(ContentStrings.searchQuestions, style = if (settingSkinIsMiuix()) LocalAppSkinTokens.current.main else MaterialTheme.typography.labelLarge, softWrap = false).size.width
                    ).toDp()
                } + AppDimens.TouchTarget + AppDimens.PageHorizontalPadding * 2 + AppDimens.Space24 * 2
                val separateAction = maxWidth < requiredWidth
                Column {
                    AppCompactTopBar(
                        title = ContentStrings.answerQuestions,
                        onBack = navigator.onNavigateBack,
                        backTag = WRITE_ANSWER_CHOOSER_BACK_TAG,
                        actions = {
                            if (!separateAction) {
                                AppTextButton(
                                    text = ContentStrings.searchQuestions,
                                    onClick = { navigator.onNavigate(Search()) },
                                    modifier = Modifier.testTag(WRITE_ANSWER_CHOOSER_SEARCH_TAG),
                                )
                            }
                        },
                    )
                    if (separateAction) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = AppDimens.PageHorizontalPadding), horizontalArrangement = Arrangement.End) {
                            AppTextButton(
                                text = ContentStrings.searchQuestions,
                                onClick = { navigator.onNavigate(Search()) },
                                modifier = Modifier.testTag(WRITE_ANSWER_CHOOSER_SEARCH_TAG),
                            )
                        }
                    }
                    AppTabRow(
                        titles = tabs.map { it.title },
                        selectedIndex = selectedIndex,
                        onSelect = { selectedIndex = it },
                        testTags = tabs.map { it.tag },
                    )
                }
            }
        },
    ) { padding ->
        key(accountClient, model.tab) {
            AppPullToRefreshBox(
                isRefreshing = model.isRefreshing,
                onRefresh = { model.refresh(environment) },
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                if (model.items.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(bottom = overlayPadding), contentAlignment = Alignment.Center) {
                        when {
                            model.isLoading || (!model.hasLoaded && model.errorMessage == null) -> AppSpinner(Modifier.testTag("write_answer_chooser_loading"))
                            model.errorMessage != null -> AppEmptyState(
                                title = ContentStrings.questionLoadFailed,
                                description = model.errorMessage,
                                action = {
                                    AppButton(
                                        text = ContentStrings.retry,
                                        onClick = { model.retry(environment) },
                                        modifier = Modifier.testTag(WRITE_ANSWER_CHOOSER_RETRY_TAG),
                                    )
                                },
                            )
                            else -> AppEmptyState(
                                title = if (model.tab == WriteAnswerChoiceTab.Invite) ContentStrings.noAnswerInvitations else ContentStrings.noQuestionsToAnswer,
                                description = ContentStrings.answerChooserEmptyHelp,
                                action = {
                                    AppTextButton(
                                        text = ContentStrings.reload,
                                        onClick = { model.refresh(environment) },
                                        modifier = Modifier.testTag(WRITE_ANSWER_CHOOSER_RETRY_TAG),
                                    )
                                },
                            )
                        }
                    }
                } else {
                    PaginatedList(
                        items = model.items,
                        listState = scrollStates[selectedIndex],
                        onLoadMore = { model.loadMore(environment) },
                        isEnd = { model.isEnd },
                        isLoading = { model.isLoading },
                        loadError = { model.errorMessage },
                        onRetry = { model.retry(environment) },
                        modifier = Modifier.fillMaxSize().testTag("write_answer_list_${model.tab.name.lowercase()}"),
                        contentPadding = PaddingValues(bottom = overlayPadding + AppDimens.Space16),
                        key = { it.questionId },
                    ) { choice ->
                        AppCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = AppDimens.Space16, vertical = AppDimens.Space4),
                        ) {
                            Column(
                                modifier = Modifier.padding(AppDimens.Space16),
                                verticalArrangement = Arrangement.spacedBy(AppDimens.Space8),
                            ) {
                                Text(
                                    text = choice.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("write_answer_question:${choice.questionId}")
                                        .clickable { navigator.onNavigate(Question(choice.questionId, choice.title)) },
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = Ksoup.parse(choice.displaySummary).text(),
                                        modifier = Modifier.weight(1f).padding(end = AppDimens.Space8),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    val destination = choice.answerDestination
                                    AppButton(
                                        text = if (choice.hasAnswer || choice.existingAnswer != null) ContentStrings.viewAnswer else ContentStrings.writeAnswer,
                                        enabled = destination != null,
                                        onClick = { destination?.let(navigator.onNavigate) },
                                        modifier = Modifier.testTag("write_answer_action:${choice.questionId}"),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
