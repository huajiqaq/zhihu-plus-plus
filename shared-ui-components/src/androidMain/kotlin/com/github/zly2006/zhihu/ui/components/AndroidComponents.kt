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

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Color.BLACK
import android.graphics.Color.TRANSPARENT
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentDialog
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.PopupProperties
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.doOnPreDraw
import com.github.zly2006.zhihu.i18n.AppLocale
import com.github.zly2006.zhihu.i18n.WidgetsStrings
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.nlp.KeywordAnalyzerCore
import com.github.zly2006.zhihu.nlp.KeywordWithWeight
import com.github.zly2006.zhihu.platform.androidUserMessageSink
import com.github.zly2006.zhihu.platform.rememberSystemUrlOpener
import com.github.zly2006.zhihu.theme.ZhihuTheme
import com.github.zly2006.zhihu.ui.AndroidArticleNavigationHandoff
import com.github.zly2006.zhihu.util.clipboardManager
import com.github.zly2006.zhihu.viewmodel.filter.androidKeywordWeightExtractor
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState

/** 手柄是独立的窗口：保留菜单的键盘焦点，但让窗外触摸直接交给手柄或正文，不能在按下时清空选区。 */
internal actual val hrmSelectionToolbarPopupProperties = PopupProperties(
    flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
    dismissOnClickOutside = false,
)

class OpenImageDialog(
    context: Context,
    urls: List<String>,
    initialIndex: Int = 0,
) : ComponentDialog(context) {
    constructor(
        context: Context,
        url: String,
    ) : this(context, listOf(url), 0)

    private val imageUrls = urls
        .filter { it.isNotBlank() && !it.startsWith("data") }
        .distinct()
        .ifEmpty { listOf("") }
    private val initialPage = initialIndex.coerceIn(0, imageUrls.lastIndex)
    private var systemBarsHidden = false
    private var dismissPosted = false

    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setCanceledOnTouchOutside(true)
        setContentView(
            /** 使用 Dialog 的主题 Context，避免主题的系统栏副作用触及宿主 Activity。 */
            ComposeView(this.context).apply {
                setContent {
                    ZhihuTheme {
                        OpenImagePreviewContent(
                            urls = imageUrls,
                            initialIndex = initialPage,
                            onDismiss = { dismiss() },
                            onOpenInBrowser = rememberSystemUrlOpener()::invoke,
                        ) { imageUrl, onClick, onLongClick, onPageSwipeEnabledChange ->
                            val imageState = rememberZoomableImageState(rememberZoomableState())
                            LaunchedEffect(imageState) {
                                snapshotFlow { imageState.zoomableState.zoomFraction }
                                    .collect { zoomFraction ->
                                        onPageSwipeEnabledChange((zoomFraction ?: 0f) <= 0.01f)
                                    }
                            }
                            ZoomableAsyncImage(
                                model = imageUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                state = imageState,
                                onClick = { onClick() },
                                onLongClick = onLongClick,
                            )
                        }
                    }
                }
            },
        )
    }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.apply {
            setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setBackgroundDrawable(BLACK.toDrawable())
            WindowCompat.setDecorFitsSystemWindows(this, false)
            /** 先完整铺满窗口再隐藏系统栏，避免宿主页在预览尚未遮挡时重排。 */
            setWindowAnimations(0)
            statusBarColor = TRANSPARENT
            navigationBarColor = TRANSPARENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                isStatusBarContrastEnforced = false
                isNavigationBarContrastEnforced = false
            }
            WindowCompat.getInsetsController(this, decorView).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
            decorView.doOnPreDraw {
                decorView.postOnAnimation { hideSystemBars() }
            }
        }
    }

    private fun hideSystemBars() {
        if (systemBarsHidden || !isShowing) return
        val window = window ?: return
        WindowCompat.getInsetsController(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
        systemBarsHidden = true
    }

    override fun dismiss() {
        if (dismissPosted) return
        val window = window
        if (!systemBarsHidden || window == null) {
            super.dismiss()
            return
        }
        /** 先在仍不透明的预览后恢复宿主布局，再移除窗口。 */
        WindowCompat.getInsetsController(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
        systemBarsHidden = false
        dismissPosted = true
        window.decorView.postDelayed({
            if (isShowing) super.dismiss()
        }, 64L)
    }
}

fun richEditorUiLabels(): Map<String, String> = mapOf(
    "lang" to AppLocale.languageTag,
    "undo" to WidgetsStrings.editorUndo,
    "redo" to WidgetsStrings.editorRedo,
    "headingLevel" to WidgetsStrings.editorHeadingLevel,
    "paragraph" to WidgetsStrings.editorParagraph,
    "heading1" to WidgetsStrings.editorHeadingOne,
    "heading2" to WidgetsStrings.editorHeadingTwo,
    "heading3" to WidgetsStrings.editorHeadingThree,
    "heading4" to WidgetsStrings.editorHeadingFour,
    "bold" to WidgetsStrings.editorBold,
    "italic" to WidgetsStrings.editorItalic,
    "underline" to WidgetsStrings.editorUnderline,
    "ordered" to WidgetsStrings.editorOrderedList,
    "bullet" to WidgetsStrings.editorBulletList,
    "blockquote" to WidgetsStrings.editorQuote,
    "code" to WidgetsStrings.editorInlineCode,
    "code-block" to WidgetsStrings.editorCodeBlock,
    "link" to WidgetsStrings.editorInsertLink,
    "clean" to WidgetsStrings.editorClearFormatting,
    "visitLink" to WidgetsStrings.editorVisitLink,
    "enterLink" to WidgetsStrings.editorEnterLink,
    "editLink" to WidgetsStrings.editorEditLink,
    "saveLink" to WidgetsStrings.editorSaveLink,
    "removeLink" to WidgetsStrings.editorRemoveLink,
)

actual suspend fun extractFeedKeywords(
    title: String,
    excerpt: String?,
): List<KeywordWithWeight> = KeywordAnalyzerCore.extractFromFeedWithWeight(
    title = title,
    excerpt = excerpt,
    content = null,
    topN = 10,
    extractor = androidKeywordWeightExtractor,
)

@Composable
actual fun rememberShareActionExecutor(): ShareActionExecutor {
    val context = LocalContext.current
    return remember(context) {
        object : ShareActionExecutor {
            override fun invoke(action: ShareAction, content: NavDestination, shareText: String) {
                if (action == ShareAction.CopyLink) {
                    AndroidArticleNavigationHandoff.markClipboardDestination(content)
                    context.clipboardManager.setPrimaryClip(ClipData.newPlainText(WidgetsStrings.link, shareText))
                    androidUserMessageSink(context).showShortMessage(WidgetsStrings.linkCopied)
                } else {
                    val shareIntent = Intent().apply {
                        this.action = Intent.ACTION_SEND
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareText)
                        if (action == ShareAction.DirectShare) {
                            putExtra(Intent.EXTRA_TITLE, getShareTitle(content))
                        }
                    }
                    context.startActivity(
                        Intent.createChooser(shareIntent, WidgetsStrings.shareTo).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
        }
    }
}
