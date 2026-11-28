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

package com.github.zly2006.zhihu.ui.article

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterCenterFocus
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.GetApp
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.github.zly2006.zhihu.account.rememberZhihuAccountStore
import com.github.zly2006.zhihu.i18n.ContentStrings
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.WriteAnswer
import com.github.zly2006.zhihu.platform.platformName
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.platform.rememberWebViewUrlOpener
import com.github.zly2006.zhihu.reading.ReadingContentType
import com.github.zly2006.zhihu.reading.ReadingQueueItem
import com.github.zly2006.zhihu.reading.ReadingQueueSourceRegistry
import com.github.zly2006.zhihu.reading.ReadingStartRequest
import com.github.zly2006.zhihu.reading.hasReadableFields
import com.github.zly2006.zhihu.reading.loadReadingPlaybackSpeed
import com.github.zly2006.zhihu.reading.loadReadingPreferences
import com.github.zly2006.zhihu.reading.rememberReadingPlayerController
import com.github.zly2006.zhihu.ui.TtsState
import com.github.zly2006.zhihu.ui.articleActionText
import com.github.zly2006.zhihu.ui.components.AppAlertDialog
import com.github.zly2006.zhihu.ui.components.AppIcon
import com.github.zly2006.zhihu.ui.components.AppSpinner
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.ui.components.ShareAction
import com.github.zly2006.zhihu.ui.components.rememberShareActionExecutor
import com.github.zly2006.zhihu.ui.rememberArticleBrowserOpener
import com.github.zly2006.zhihu.ui.rememberArticleSpeechToggler
import com.github.zly2006.zhihu.ui.rememberArticleTtsState
import com.github.zly2006.zhihu.ui.reportUrl
import com.github.zly2006.zhihu.util.AppLog
import com.github.zly2006.zhihu.viewmodel.ArticleViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleActionsMenu(
    article: Article,
    viewModel: ArticleViewModel,
    answerQueueFallbackProvider: (suspend (limit: Int) -> List<Article>)? = null,
    showMenu: Boolean,
    onDismissRequest: () -> Unit,
    onSummaryRequest: () -> Unit,
    onAigcFlagRequest: () -> Unit,
    onExportRequest: () -> Unit,
    onSetImmersiveDoubleTap: () -> Unit = {},
) {
    val ttsState = rememberArticleTtsState()
    val toggleSpeech = rememberArticleSpeechToggler()
    val readingPlayer = rememberReadingPlayerController()
    val readingSettings = rememberSettingsStore()
    val openArticleInBrowser = rememberArticleBrowserOpener()
    val openWebUrl = rememberWebViewUrlOpener()
    val executeShareAction = rememberShareActionExecutor()
    val coroutineScope = rememberCoroutineScope()
    var showReportConfirm by remember { mutableStateOf(false) }
    val readingItem = ReadingQueueItem(
        contentType = when (article.type) {
            ArticleType.Answer -> ReadingContentType.Answer
            ArticleType.Article -> ReadingContentType.Article
        },
        id = article.id,
        title = viewModel.title,
        author = viewModel.authorName,
        questionId = viewModel.questionId.takeIf { it > 0 },
        bodyHtml = viewModel.content.takeIf(String::isNotBlank),
        publishedAt = viewModel.createdAt,
        updatedAt = viewModel.updatedAt,
        voteUpCount = viewModel.voteUpCount,
        commentCount = viewModel.commentCount,
    )
    val readingPreferences = loadReadingPreferences(readingSettings)
    val readingPlaybackSpeed = loadReadingPlaybackSpeed(readingSettings)
    val hasReadingSession = readingPlayer?.state?.value?.hasSession == true
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )

    @Composable
    fun MenuActionButton(
        icon: @Composable () -> Unit,
        text: String,
        enabled: Boolean = true,
        backgroundColor: Color = MaterialTheme.colorScheme.surfaceBright,
        contentColor: Color = MaterialTheme.colorScheme.onSurface,
        onClick: () -> Unit,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { onClick() },
            shape = RoundedCornerShape(16.dp),
            color = if (enabled) backgroundColor else backgroundColor.copy(alpha = 0.5f),
        ) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.size(24.dp)) {
                    icon()
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (enabled) contentColor else contentColor.copy(alpha = 0.38f),
                )
            }
        }
    }

    @Composable
    fun MenuActionButton(
        icon: ImageVector,
        text: String,
        enabled: Boolean = true,
        backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
        onClick: () -> Unit,
    ) {
        MenuActionButton(
            icon = {
                AppIcon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                )
            },
            text = text,
            enabled = enabled,
            backgroundColor = backgroundColor,
            contentColor = contentColor,
            onClick = onClick,
        )
    }

    @Composable
    fun Content() {
        val navigator = LocalNavigator.current
        val accounts by rememberZhihuAccountStore().accountsState.collectAsState()
        if (
            article.type == ArticleType.Answer &&
            viewModel.questionId > 0 &&
            accounts.session.login &&
            viewModel.authorId.isNotBlank() &&
            accounts.session.profile?.id == viewModel.authorId
        ) {
            MenuActionButton(
                icon = Icons.Filled.Edit,
                text = ContentStrings.editAnswer,
                onClick = {
                    onDismissRequest()
                    navigator.onNavigate(WriteAnswer(questionId = viewModel.questionId, questionTitle = viewModel.title))
                },
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        MenuActionButton(
            icon = {
                if (hasReadingSession) {
                    AppIcon(
                        Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    when (ttsState) {
                        TtsState.Initializing, TtsState.Uninitialized -> AppSpinner(modifier = Modifier.height(24.dp))

                        else -> AppIcon(
                            if (readingPlayer == null && ttsState.isSpeaking) {
                                Icons.AutoMirrored.Filled.VolumeOff
                            } else {
                                Icons.AutoMirrored.Filled.VolumeUp
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            text = if (readingPlayer != null) {
                if (hasReadingSession) ContentStrings.stopReading else ContentStrings.startContinuousReading
            } else if (ttsState.isSpeaking) {
                ContentStrings.stopReading
            } else {
                ContentStrings.startReading
            },
            enabled = if (readingPlayer != null) {
                hasReadingSession || readingItem.hasReadableFields(readingPreferences)
            } else {
                ttsState !in listOf(TtsState.Error, TtsState.Uninitialized, TtsState.Initializing)
            },
            onClick = {
                onDismissRequest()
                if (readingPlayer != null) {
                    if (hasReadingSession) {
                        readingPlayer.stop()
                    } else {
                        coroutineScope.launch {
                            val useQuestionAnswerOrder = article.type == ArticleType.Answer &&
                                article.readingQueueSourceId?.startsWith("home:") == true
                            val originQueue = ReadingQueueSourceRegistry.queueStartingAt(
                                current = readingItem,
                                sourceId = article.readingQueueSourceId.takeUnless { useQuestionAnswerOrder },
                                limit = readingPreferences.queueLimit,
                            )
                            val queue = if (
                                article.type == ArticleType.Answer &&
                                (useQuestionAnswerOrder || originQueue.size < readingPreferences.queueLimit) &&
                                readingPreferences.queueLimit > 1
                            ) {
                                val fallbackAfterCurrent = try {
                                    val fallbackArticles = answerQueueFallbackProvider
                                        ?.invoke(readingPreferences.queueLimit - 1)
                                        ?: viewModel.answerNextIds.map { answerId ->
                                            Article(
                                                type = ArticleType.Answer,
                                                id = answerId,
                                                title = viewModel.title,
                                            )
                                        }
                                    fallbackArticles.map { fallback ->
                                        ReadingQueueItem(
                                            contentType = ReadingContentType.Answer,
                                            id = fallback.id,
                                            title = fallback.title
                                                .takeUnless { it == "loading..." }
                                                .orEmpty()
                                                .ifBlank { viewModel.title },
                                            author = fallback.authorName
                                                .takeUnless { it == "loading..." }
                                                .orEmpty(),
                                            questionId = viewModel.questionId.takeIf { it > 0 },
                                        )
                                    }
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (error: Exception) {
                                    AppLog.w("ArticleActionsMenu", "Failed to load the remaining reading queue", error)
                                    emptyList()
                                }
                                ReadingQueueSourceRegistry.queueStartingAt(
                                    current = readingItem,
                                    sourceId = article.readingQueueSourceId.takeUnless { useQuestionAnswerOrder },
                                    limit = readingPreferences.queueLimit,
                                    fallbackAfterCurrent = fallbackAfterCurrent,
                                )
                            } else {
                                originQueue
                            }
                            readingPlayer.start(
                                ReadingStartRequest(
                                    queue = queue,
                                    preferences = readingPreferences,
                                    sourceId = article.readingQueueSourceId,
                                    playbackSpeed = readingPlaybackSpeed,
                                ),
                            )
                        }
                    }
                } else {
                    toggleSpeech(viewModel.title, viewModel.content)
                }
            },
        )

        Spacer(modifier = Modifier.height(12.dp))
        MenuActionButton(
            icon = Icons.Filled.Share,
            text = ContentStrings.share,
            onClick = {
                onDismissRequest()
                executeShareAction(
                    ShareAction.Share,
                    article,
                    articleActionText(article, viewModel.questionId, viewModel.title, viewModel.authorName),
                )
            },
        )

        Spacer(modifier = Modifier.height(12.dp))
        MenuActionButton(
            icon = Icons.Filled.Summarize,
            text = ContentStrings.summarizeContent,
            onClick = {
                onDismissRequest()
                onSummaryRequest()
            },
        )

        Spacer(modifier = Modifier.height(12.dp))
        MenuActionButton(
            icon = Icons.Filled.Flag,
            text = ContentStrings.flagAiContent,
            onClick = {
                onDismissRequest()
                onAigcFlagRequest()
            },
        )

        Spacer(modifier = Modifier.height(12.dp))
        MenuActionButton(
            icon = Icons.Filled.Report,
            text = ContentStrings.report,
            onClick = {
                onDismissRequest()
                showReportConfirm = true
            },
        )

        Spacer(modifier = Modifier.height(12.dp))
        MenuActionButton(
            icon = Icons.Filled.ContentCopy,
            text = ContentStrings.copyLink,
            onClick = {
                onDismissRequest()
                executeShareAction(
                    ShareAction.CopyLink,
                    article,
                    articleActionText(article, viewModel.questionId, viewModel.title, viewModel.authorName),
                )
            },
        )

        Spacer(modifier = Modifier.height(12.dp))
        MenuActionButton(
            icon = Icons.Filled.FilterCenterFocus,
            text = ContentStrings.enterImmersive,
            onClick = {
                onDismissRequest()
                onSetImmersiveDoubleTap()
            },
        )

        Spacer(modifier = Modifier.height(12.dp))
        MenuActionButton(
            icon = Icons.Filled.GetApp,
            text = ContentStrings.exportArticleFormats,
            onClick = {
                onDismissRequest()
                onExportRequest()
            },
        )

        Spacer(modifier = Modifier.height(12.dp))
        MenuActionButton(
            icon = Icons.Filled.Share,
            text = ContentStrings.shareMarkdownBody,
            onClick = {
                onDismissRequest()
                executeShareAction(ShareAction.DirectShare, article, viewModel.convertToMarkdown())
            },
        )

        Spacer(modifier = Modifier.height(12.dp))
        MenuActionButton(
            icon = Icons.Outlined.DesktopWindows,
            text = if (platformName == "Android") ContentStrings.openOnComputer else ContentStrings.openInBrowser,
            onClick = {
                coroutineScope.launch {
                    openArticleInBrowser(article)
                    onDismissRequest()
                }
            },
        )
        Spacer(modifier = Modifier.height(16.dp))
    }

    ArticleActionsMenuHost(
        showMenu = showMenu,
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        content = { Content() },
    )

    if (showReportConfirm) {
        AppAlertDialog(
            onDismissRequest = { showReportConfirm = false },
            title = { Text(ContentStrings.report) },
            text = { Text(ContentStrings.reportHelp) },
            confirmButton = {
                AppTextButton(ContentStrings.openReportPage, {
                    showReportConfirm = false
                    openWebUrl(reportUrl(article.id.toString(), if (article.type == ArticleType.Article) "article" else "answer"))
                })
            },
            dismissButton = { AppTextButton(ContentStrings.cancel, { showReportConfirm = false }) },
        )
    }
}
