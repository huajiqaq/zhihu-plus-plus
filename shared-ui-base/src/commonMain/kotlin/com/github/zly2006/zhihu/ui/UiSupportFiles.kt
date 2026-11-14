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

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import com.fleeksoft.ksoup.Ksoup
import com.github.zly2006.zhihu.account.rememberZhihuAccountStore
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.markdown.RenderMarkdown
import com.github.zly2006.zhihu.navigation.Account
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.Daily
import com.github.zly2006.zhihu.navigation.Follow
import com.github.zly2006.zhihu.navigation.Home
import com.github.zly2006.zhihu.navigation.HotList
import com.github.zly2006.zhihu.navigation.MyCollections
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.OnlineHistory
import com.github.zly2006.zhihu.navigation.Pin
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.navigation.TopLevelDestination
import com.github.zly2006.zhihu.platform.UserMessageSink
import com.github.zly2006.zhihu.platform.platformBottomBarItemLimit
import com.github.zly2006.zhihu.platform.rememberFileImporter
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.ui.subscreens.BOTTOM_BAR_ITEMS_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.subscreens.BOTTOM_BAR_ITEM_ORDER_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.subscreens.COLLECTION_DIRECT_BROWSE_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.subscreens.DUO3_TIQIAN_MARKDOWN_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.subscreens.LANDSCAPE_LIST_DETAIL_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.subscreens.START_DESTINATION_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.subscreens.bottomBarItemOrderFromPreference
import com.github.zly2006.zhihu.ui.subscreens.defaultBottomBarSelectionKeys
import com.github.zly2006.zhihu.ui.subscreens.navDestinationFromName
import com.github.zly2006.zhihu.ui.subscreens.normalizeBottomBarSelection
import com.github.zly2006.zhihu.ui.subscreens.resolveValidStartDestinationKey
import com.github.zly2006.zhihu.viewmodel.ZhihuApiEnvironment
import com.github.zly2006.zhihu.viewmodel.filter.getContentFilterDatabase
import com.github.zly2006.zhihu.viewmodel.filter.importBlocklistBackupFromJsonText
import com.github.zly2006.zhihu.viewmodel.getOrFetchContentDetail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive

@Composable
expect fun rememberNotificationEnvironment(
    settingsStore: com.github.zly2006.zhihu.notification.NotificationSettingsStore,
): com.github.zly2006.zhihu.viewmodel.NotificationEnvironment

data class PinLikeResult(
    val isLiked: Boolean,
    val likeCount: Int,
)

suspend fun fetchPinLinkCardPreview(
    linkCard: DataHolder.Pin.ContentLinkCard,
    env: ZhihuApiEnvironment,
): PinLinkCardPreview? {
    val destination = resolveLinkCardDestination(linkCard) ?: return null
    return when (destination) {
        is Article -> {
            when (val detail = env.getOrFetchContentDetail(destination)) {
                is DataHolder.Article -> PinLinkCardPreview(
                    title = compactTitle(detail.title),
                    preview = compactPreview(detail.excerpt.ifBlank { detail.content }),
                )
                is DataHolder.Answer -> PinLinkCardPreview(
                    title = compactTitle(detail.question.title),
                    preview = compactPreview(detail.excerpt.ifBlank { detail.content }),
                )
                else -> null
            }
        }
        is Question -> {
            (env.getOrFetchContentDetail(destination) as? DataHolder.Question)?.let { detail ->
                PinLinkCardPreview(
                    title = compactTitle(detail.title),
                    preview = compactPreview(detail.detail),
                )
            }
        }
        is Pin -> {
            (env.getOrFetchContentDetail(destination) as? DataHolder.Pin)?.let { detail ->
                PinLinkCardPreview(
                    title = "${detail.author.name} 的想法",
                    preview = compactPreview(detail.contentHtml),
                )
            }
        }
        else -> null
    }
}

fun JsonObject?.booleanCompat(vararg keys: String): Boolean {
    if (this == null) return false
    return keys.firstNotNullOfOrNull { key ->
        get(key)?.jsonPrimitive?.booleanOrNull
    } ?: false
}

/** 与文章、问题共用渲染设置，避免用户的 WebView/Markdown 选择仅对部分正文生效。 */
@Composable
fun PinHtmlContent(html: String) {
    val settings = rememberSettingsStore()
    if (settings.getBoolean(ARTICLE_USE_WEBVIEW_PREFERENCE_KEY, false) &&
        isLegacyWebViewSupported
    ) {
        ZhihuHtmlWebViewContent(html)
    } else {
        Spacer(Modifier.height(10.dp))
        RenderMarkdown(
            html = html,
            modifier = Modifier.questionSelectionWorkaround(),
            selectable = true,
            enableScroll = false,
            useTiqianRenderer = settings.getBoolean(DUO3_TIQIAN_MARKDOWN_PREFERENCE_KEY, false),
        )
    }
}

expect val isLegacyWebViewSupported: Boolean

@Composable
expect fun ZhihuHtmlWebViewContent(html: String)

@Composable
fun consumePendingCommentId(content: NavDestination): String? = remember(content) {
    CommentNavigationHandoff.consumeCommentId(content)
}

@Composable
expect fun ArticleWebViewContent(
    article: Article,
    html: String,
    title: String,
    scrollState: ScrollState,
    rememberedScrollY: Int,
    rememberedScrollYSync: Boolean,
    onRememberedScrollYSyncChange: (Boolean) -> Unit,
    onImageLoadFailed: () -> Unit,
    onDoubleTap: () -> Unit,
)

/** 过滤部分设备文本选择菜单中的非预期系统项。 */
expect fun Modifier.articleMarkdownSelectionWorkaround(): Modifier

/** 平台不支持 WebView 时仍需保留正文渲染，使用 Compose Markdown。 */
@Composable
fun QuestionDetailContent(
    questionId: Long,
    html: String,
) {
    val settings = rememberSettingsStore()
    if (settings.getBoolean(ARTICLE_USE_WEBVIEW_PREFERENCE_KEY, false) &&
        isLegacyWebViewSupported
    ) {
        QuestionDetailWebViewContent(
            questionId = questionId,
            html = html,
        )
    } else {
        RenderMarkdown(
            html = html,
            modifier = Modifier.questionSelectionWorkaround(),
            selectable = true,
            enableScroll = false,
            useTiqianRenderer = settings.getBoolean(DUO3_TIQIAN_MARKDOWN_PREFERENCE_KEY, false),
        )
    }
}

@Composable
expect fun QuestionDetailWebViewContent(
    questionId: Long,
    html: String,
)

@Composable
expect fun rememberArticleTtsState(): TtsState

@Composable
expect fun rememberArticleSpeechToggler(): (String, String) -> Unit

@Composable
expect fun rememberArticleBrowserOpener(): (Article) -> Unit

fun articleActionText(
    article: Article,
    questionId: Long,
    title: String,
    authorName: String,
): String =
    when (article.type) {
        ArticleType.Answer -> {
            "https://www.zhihu.com/question/$questionId/answer/${article.id}\n【$title - $authorName 的回答】"
        }
        ArticleType.Article -> {
            "https://zhuanlan.zhihu.com/p/${article.id}\n【$title - $authorName 的文章】"
        }
    }

fun articleWebUrl(article: Article): String =
    when (article.type) {
        ArticleType.Answer -> "https://www.zhihu.com/answer/${article.id}"
        ArticleType.Article -> "https://zhuanlan.zhihu.com/p/${article.id}"
    }

fun reportUrl(id: String, type: String): String =
    "https://www.zhihu.com/report?id=$id&type=$type&source=android"

fun articleSpeechText(
    title: String,
    content: String,
    maxContentLength: Int = 50_000,
): String =
    buildString {
        append(title)
        append("。")
        if (content.isNotEmpty()) {
            val contentToProcess =
                if (content.length > maxContentLength) {
                    content.substring(0, maxContentLength) + "..."
                } else {
                    content
                }
            append(Ksoup.parse(contentToProcess).text())
        }
    }

enum class TtsState(
    val isSpeaking: Boolean = false,
) {
    Uninitialized,
    Initializing,
    Ready,
    Error,
    LoadingText,
    Speaking(true),
    Paused,
    SwitchingChunk(true),
}

/** [ZhihuMain] 整体读取设置快照，避免应用更新到一半的导航设置。 */
data class ZhihuMainPreferenceSnapshot(
    val duo3HomeAccount: Boolean,
    val tapToScrollToTopEnabled: Boolean,
    val autoHideBottomBar: Boolean,
    val collectionDirectBrowseEnabled: Boolean,
    val landscapeListDetailEnabled: Boolean,
    val selectedBottomBarItemKeys: List<String>,
    val startDestination: TopLevelDestination,
)

/** 设置页退出时 [reload] 原地更新主壳，避免重建 NavHost 并丢失已加载 tab、返回栈和滚动位置。 */
class ZhihuMainPreferenceState(
    private val readSnapshot: () -> ZhihuMainPreferenceSnapshot,
) {
    private var snapshot by mutableStateOf(readSnapshot())

    val duo3HomeAccount: Boolean get() = snapshot.duo3HomeAccount
    val tapToScrollToTopEnabled: Boolean get() = snapshot.tapToScrollToTopEnabled
    val autoHideBottomBar: Boolean get() = snapshot.autoHideBottomBar
    val collectionDirectBrowseEnabled: Boolean get() = snapshot.collectionDirectBrowseEnabled
    val landscapeListDetailEnabled: Boolean get() = snapshot.landscapeListDetailEnabled
    val selectedBottomBarItemKeys: List<String> get() = snapshot.selectedBottomBarItemKeys
    val startDestination: TopLevelDestination get() = snapshot.startDestination

    fun reload() {
        snapshot = readSnapshot()
    }
}

@Composable
fun rememberZhihuMainPreferenceState(): ZhihuMainPreferenceState {
    val settings = rememberSettingsStore()
    val allBottomBarItemKeys = remember {
        listOf(Home.name, Follow.name, HotList.name, Daily.name, OnlineHistory.name, MyCollections.name, Account.name)
    }
    return remember(settings) {
        ZhihuMainPreferenceState {
            val duo3HomeAccount = settings.getBoolean("duo3_home_account", false)
            val selectedKeys = normalizeBottomBarSelection(
                settings.getStringSet(
                    BOTTOM_BAR_ITEMS_PREFERENCE_KEY,
                    defaultBottomBarSelectionKeys(duo3HomeAccount, platformBottomBarItemLimit),
                ),
                duo3HomeAccount,
                enforceMinimumSelection = true,
                maximumSelection = platformBottomBarItemLimit,
            )
            val orderedSelectedKeys = bottomBarItemOrderFromPreference(
                settings.getStringOrNull(BOTTOM_BAR_ITEM_ORDER_PREFERENCE_KEY),
                selectedKeys,
            )
            ZhihuMainPreferenceSnapshot(
                duo3HomeAccount = duo3HomeAccount,
                tapToScrollToTopEnabled = settings.getBoolean("bottomBarTapScrollToTop", true),
                autoHideBottomBar = settings.getBoolean("autoHideBottomBar", false),
                collectionDirectBrowseEnabled = settings.getBoolean(COLLECTION_DIRECT_BROWSE_PREFERENCE_KEY, false),
                landscapeListDetailEnabled = settings.getBoolean(LANDSCAPE_LIST_DETAIL_PREFERENCE_KEY, true),
                selectedBottomBarItemKeys = orderedSelectedKeys,
                startDestination = navDestinationFromName(
                    resolveValidStartDestinationKey(
                        settings.getString(START_DESTINATION_PREFERENCE_KEY, Home.name),
                        orderedSelectedKeys.ifEmpty { allBottomBarItemKeys.filter { it in selectedKeys } },
                    ),
                ),
            )
        }
    }
}

data class AccountSettingsAccountState(
    val login: Boolean = false,
    val hasRequiredCookie: Boolean = true,
    val username: String = "",
    val avatarUrl: String? = null,
    val id: String = "",
    val urlToken: String? = null,
)

@Composable
fun rememberAccountSettingsAccountState(): State<AccountSettingsAccountState> {
    val accountStore = rememberZhihuAccountStore()
    val accounts = accountStore.accountsState.collectAsState()
    return remember(accounts) {
        derivedStateOf {
            val session = accounts.value.session
            AccountSettingsAccountState(
                login = session.login,
                hasRequiredCookie = session.cookies["d_c0"]
                    .isNullOrBlank()
                    .not(),
                username = session.username,
                avatarUrl = session.profile?.avatarUrl,
                id = session.profile
                    ?.id
                    .orEmpty(),
                urlToken = session.profile?.urlToken,
            )
        }
    }
}

@Composable
expect fun rememberAppVersionInfo(): String

const val PEOPLE_PROFILE_INCLUDE_PATH =
    "allow_message,is_followed,is_following,is_org,is_blocking,badge_v2,answer_count,follower_count,following_count,articles_count,question_count,pins_count"

@Composable
expect fun rememberHomeIsDebuggable(): Boolean

@Composable
expect fun rememberCommentEmojiInlineContent(emojiKeys: Set<String>): Map<String, InlineTextContent>

data class CommentEmoji(
    val placeholder: String,
    val inlineKey: String,
)

@Composable
expect fun rememberCommentEmojis(): List<CommentEmoji>

expect fun commentEmojiInlineKey(placeholder: String): String?

/** 输入框不支持 InlineTextContent，需要表情位图。 */
@Composable
expect fun rememberCommentEmojiBitmaps(inlineKeys: Set<String>): Map<String, ImageBitmap>

expect fun Modifier.commentSelectionWorkaround(): Modifier

@Composable
fun rememberBlocklistRuleImporter(
    userMessages: UserMessageSink,
    onImported: (String) -> Unit,
): () -> Unit {
    val database = remember { getContentFilterDatabase() }
    return rememberFileImporter(
        title = "导入屏蔽规则",
        mimeTypes = listOf("application/json", "text/plain", "*/*"),
        extensions = listOf("json", "txt"),
        userMessages = userMessages,
    ) { _, bytes ->
        val summary = withContext(Dispatchers.Default) {
            importBlocklistBackupFromJsonText(
                keywordDao = database.blockedKeywordDao(),
                userDao = database.blockedUserDao(),
                questionAuthorDao = database.blockedQuestionAuthorDao(),
                topicDao = database.blockedTopicDao(),
                text = bytes.decodeToString(),
            )
        }
        onImported(summary)
    }
}

@Composable
expect fun rememberBlocklistRuleExporter(): suspend () -> String

/** 仅 Android 支持状态栏沉浸控制；Desktop/iOS 不执行此效果。 */
@Composable
expect fun ArticleImmersiveModeEffect(immersive: Boolean)

/** 离开文章后恢复 Android 状态栏，避免沉浸状态留在其他页面。 */
@Composable
expect fun LeaveImmersiveModeCleanup()

expect fun Modifier.questionSelectionWorkaround(): Modifier
