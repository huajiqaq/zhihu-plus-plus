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

package com.github.zly2006.zhihu.ui.subscreens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.sp
import com.github.zly2006.zhihu.markdown.PlatformTiqianMarkdown
import com.github.zly2006.zhihu.markdown.TiqianBrandTitle
import com.github.zly2006.zhihu.markdown.htmlToMdAst
import com.github.zly2006.zhihu.markdown.isTiqianMarkdownRendererAvailable
import com.github.zly2006.zhihu.navigation.Account
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.platform.isAnswerSwipeSupported
import com.github.zly2006.zhihu.platform.isPageTurnSupported
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.ui.ANSWER_DOUBLE_TAP_ACTION_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.ARTICLE_USE_WEBVIEW_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.AnswerDoubleTapAction
import com.github.zly2006.zhihu.ui.components.ANSWER_SWITCH_SENSITIVITY_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.components.AppDialog
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppSettingsPage
import com.github.zly2006.zhihu.ui.components.AppSlider
import com.github.zly2006.zhihu.ui.components.DEFAULT_ANSWER_SWITCH_SENSITIVITY
import com.github.zly2006.zhihu.ui.components.MAX_ANSWER_SWITCH_SENSITIVITY
import com.github.zly2006.zhihu.ui.components.MIN_ANSWER_SWITCH_SENSITIVITY
import com.github.zly2006.zhihu.ui.components.SettingItem
import com.github.zly2006.zhihu.ui.components.SettingItemGroup
import com.github.zly2006.zhihu.ui.components.SettingItemSelect
import com.github.zly2006.zhihu.ui.components.SettingItemWithSwitch
import com.github.zly2006.zhihu.ui.components.normalizedAnswerSwitchSensitivity
import com.github.zly2006.zhihu.ui.isLegacyWebViewSupported
import com.github.zly2006.zhihu.ui.rememberObservedSetting
import com.github.zly2006.zhihu.ui.rememberSetting
import com.hrm.markdown.renderer.Markdown
import com.hrm.markdown.renderer.MarkdownDefaults
import kotlin.math.abs
import kotlin.math.roundToInt

const val READING_SETTINGS_BACK_BUTTON_TAG = "reading_settings_back_button"

const val READING_LAYOUT_SETTINGS_SCROLL_TAG = "readingLayoutSettings.scroll"
const val READING_LAYOUT_SETTINGS_TITLE_TAG = "readingLayoutSettings.title"
const val READING_LAYOUT_SETTINGS_PREVIEW_TAG = "readingLayoutSettings.preview"
const val READING_LAYOUT_SETTINGS_PAGE_TURN_TAG = "readingLayoutSettings.pageTurn"
const val READING_LAYOUT_SETTINGS_WEBVIEW_CONFIRM_TAG = "readingLayoutSettings.webViewConfirm"

private const val ANSWER_SWITCH_MODE_PREFERENCE_KEY = "answerSwitchMode"
private const val WEBVIEW_CUSTOM_FONT_NAME_PREFERENCE_KEY = "webviewCustomFontName"
private const val WEBVIEW_HARDWARE_ACCELERATION_PREFERENCE_KEY = "webviewHardwareAcceleration"

val READING_LAYOUT_SETTINGS_KEYS: Set<String> = setOf(
    "fontScale", PREF_FONT_SIZE, PREF_LINE_HEIGHT, PREF_BLOCK_SPACING, "contentAndHome",
    "titleAutoHide", "autoHideArticleBottomBar", "pinAnswerDate", "buttonSkipAnswer", "autoHideSkipAnswerButton",
    ANSWER_SWITCH_MODE_PREFERENCE_KEY, ANSWER_SWITCH_SENSITIVITY_PREFERENCE_KEY, ANSWER_DOUBLE_TAP_ACTION_PREFERENCE_KEY,
    ARTICLE_USE_WEBVIEW_PREFERENCE_KEY, WEBVIEW_CUSTOM_FONT_NAME_PREFERENCE_KEY, WEBVIEW_HARDWARE_ACCELERATION_PREFERENCE_KEY,
    DUO3_TIQIAN_MARKDOWN_PREFERENCE_KEY, DUO3_TIQIAN_MATH_FONT_PREFERENCE_KEY,
)

private const val TYPOGRAPHY_PREVIEW_HTML =
    "<p>拖动下面的滑杆，这段文字会立即按新的字号、行高和段间距重新排版，与回答正文使用同一套渲染器。</p>" +
        "<p>第二段用来观察段间距：知乎 Zhihu 上的回答常常中英混排，例如 Kotlin Multiplatform 与 Compose 的讨论；" +
        "行高决定同一段里相邻两行的距离，段间距决定段与段之间的空白。</p>"

@Composable
fun ReadingSettingsScreen(
    setting: String? = null,
) {
    val settings = rememberSettingsStore()
    val userMessages = rememberUserMessageSink()
    val navigator = LocalNavigator.current

    var storedFontSize by rememberSetting(settings, PREF_FONT_SIZE, 100)
    val fontSize = snappedContentFontSize(storedFontSize)
    LaunchedEffect(storedFontSize) {
        if (storedFontSize != fontSize) storedFontSize = fontSize
    }
    var lineHeight by rememberSetting(settings, PREF_LINE_HEIGHT, 160)
    var blockSpacing by rememberSetting(settings, PREF_BLOCK_SPACING, 100)
    var tiqianMarkdown by rememberSetting(settings, DUO3_TIQIAN_MARKDOWN_PREFERENCE_KEY, false)
    var tiqianMathFont by rememberSetting(settings, DUO3_TIQIAN_MATH_FONT_PREFERENCE_KEY, "lete")
    var answerSwitchMode by rememberSetting(settings, ANSWER_SWITCH_MODE_PREFERENCE_KEY, "vertical")
    var articleUseWebview by rememberSetting(settings, ARTICLE_USE_WEBVIEW_PREFERENCE_KEY, false)
    val useWebView = isLegacyWebViewSupported && articleUseWebview
    var showWebViewConfirmation by remember(articleUseWebview) { mutableStateOf(false) }

    val highlightedKey = when {
        setting == PREF_FONT_SIZE -> "fontScale"
        setting in setOf(WEBVIEW_CUSTOM_FONT_NAME_PREFERENCE_KEY, WEBVIEW_HARDWARE_ACCELERATION_PREFERENCE_KEY) && !useWebView -> ARTICLE_USE_WEBVIEW_PREFERENCE_KEY
        setting == "autoHideSkipAnswerButton" && !settings.getBoolean("buttonSkipAnswer", true) -> "buttonSkipAnswer"
        setting in setOf(DUO3_TIQIAN_MARKDOWN_PREFERENCE_KEY, DUO3_TIQIAN_MATH_FONT_PREFERENCE_KEY, PREF_BLOCK_SPACING) && useWebView -> ARTICLE_USE_WEBVIEW_PREFERENCE_KEY
        setting == DUO3_TIQIAN_MATH_FONT_PREFERENCE_KEY && !tiqianMarkdown -> DUO3_TIQIAN_MARKDOWN_PREFERENCE_KEY
        setting == ANSWER_SWITCH_SENSITIVITY_PREFERENCE_KEY && answerSwitchMode == "off" -> ANSWER_SWITCH_MODE_PREFERENCE_KEY
        else -> setting.orEmpty()
    }

    AppSettingsPage(
        title = "阅读与排版",
        onBack = navigator.onNavigateBack,
        backTag = READING_SETTINGS_BACK_BUTTON_TAG,
        highlightedKey = highlightedKey,
        scrollTag = READING_LAYOUT_SETTINGS_SCROLL_TAG,
        overlays = {
            if (showWebViewConfirmation) {
                AppDialog(
                    modifier = Modifier.testTag(READING_LAYOUT_SETTINGS_WEBVIEW_CONFIRM_TAG),
                    title = "启用已弃用的 WebView 渲染？",
                    summary = "WebView 渲染已弃用，仅保留为兼容性回退，不再更新。开启后，正文查找定位与高亮、正文内划线喜欢与句子评论交互、段间距和提椠排版等 Compose 专属功能不可用。关闭后恢复 Compose 渲染。每次重新开启都需确认。",
                    confirmText = "仍然开启",
                    onConfirm = {
                        articleUseWebview = true
                        showWebViewConfirmation = false
                    },
                    dismissText = "取消",
                    onDismissRequest = { showWebViewConfirmation = false },
                )
            }
        },
    ) {
        SettingItemGroup(title = "排版预览") {
            SettingItem(
                modifier = Modifier.testTag(READING_LAYOUT_SETTINGS_PREVIEW_TAG),
                title = {
                    ReadingTypographyPreview(
                        fontSize = fontSize,
                        lineHeight = lineHeight,
                        blockSpacing = blockSpacing,
                        useTiqianRenderer = tiqianMarkdown,
                        mathFontFamilyId = tiqianMathFont.takeIf { it == "stix" },
                    )
                },
                description = if (useWebView) {
                    { Text("此处为 Compose 排版预览，不代表当前 WebView 正文的显示效果。") }
                } else {
                    null
                },
            )
        }

        SettingItemGroup(
            title = "排版",
            settingKey = "contentAndHome",
        ) {
            SettingItem(
                title = { Text("字号") },
                description = { Text("$fontSize%") },
                settingKey = "fontScale",
                bottomAction = {
                    AppSlider(
                        value = contentFontSizeLevels.indexOf(fontSize).toFloat(),
                        onValueChange = {
                            val level = contentFontSizeLevels[it.roundToInt().coerceIn(0, contentFontSizeLevels.lastIndex)]
                            if (level != storedFontSize) storedFontSize = level
                        },
                        valueRange = 0f..contentFontSizeLevels.lastIndex.toFloat(),
                        steps = contentFontSizeLevels.size - 2,
                        modifier = Modifier.fillMaxWidth().padding(top = AppDimens.Space8),
                    )
                },
            )
            SettingItem(
                title = { Text("行高") },
                description = { Text("$lineHeight%") },
                settingKey = PREF_LINE_HEIGHT,
                bottomAction = {
                    AppSlider(
                        value = lineHeight.toFloat(),
                        onValueChange = {
                            val height = ((it / 10).roundToInt() * 10).coerceIn(100, 300)
                            if (height != lineHeight) lineHeight = height
                        },
                        valueRange = 100f..300f,
                        steps = 19,
                        modifier = Modifier.fillMaxWidth().padding(top = AppDimens.Space8),
                    )
                },
            )
            SettingItem(
                title = { Text("段间距") },
                description = {
                    Text(if (useWebView) "$blockSpacing% · 当前 WebView 正文不支持段间距设置，关闭 WebView 后可用。" else "$blockSpacing%")
                },
                enabled = !useWebView,
                settingKey = PREF_BLOCK_SPACING,
                bottomAction = {
                    AppSlider(
                        value = blockSpacing.toFloat(),
                        enabled = !useWebView,
                        onValueChange = {
                            val spacing = ((it / 10).roundToInt() * 10).coerceIn(0, 300)
                            if (spacing != blockSpacing) blockSpacing = spacing
                        },
                        valueRange = 0f..300f,
                        steps = 29,
                        modifier = Modifier.fillMaxWidth().padding(top = AppDimens.Space8),
                    )
                },
            )
            if (isTiqianMarkdownRendererAvailable) {
                SettingItemWithSwitch(
                    modifier = Modifier.testTag(APPEARANCE_SETTINGS_TIQIAN_MARKDOWN_TAG),
                    title = { TiqianBrandTitle(prefix = "", suffix = "排版") },
                    description = {
                        Text(
                            if (useWebView) {
                                "当前 WebView 正文不支持提椠排版，关闭 WebView 后可用；已保存的选择会保留。"
                            } else {
                                "使用「提椠」段落书写器排版正文：段落两端对齐，改进中西混排间距、代码、表格、公式与脚注等样式，接近纸质书的排版效果。作用于文章、想法与问题详情。"
                            },
                        )
                    },
                    checked = tiqianMarkdown,
                    enabled = !useWebView,
                    onCheckedChange = { tiqianMarkdown = it },
                    settingKey = DUO3_TIQIAN_MARKDOWN_PREFERENCE_KEY,
                )
                AnimatedVisibility(visible = tiqianMarkdown) {
                    SettingItemWithSwitch(
                        title = { Text("正文：使用非衬线数学字体") },
                        description = {
                            Text(
                                if (useWebView) {
                                    "当前 WebView 正文不支持此数学字体设置，关闭 WebView 后可用；已保存的选择会保留。"
                                } else {
                                    "默认公式使用非衬线的 Lete Sans Math；关闭后改用衬线的 STIX Two Math。"
                                },
                            )
                        },
                        checked = tiqianMathFont == "lete",
                        enabled = !useWebView,
                        onCheckedChange = { tiqianMathFont = if (it) "lete" else "stix" },
                        settingKey = DUO3_TIQIAN_MATH_FONT_PREFERENCE_KEY,
                    )
                }
            }
        }

        SettingItemGroup(title = "回答页") {
            var titleAutoHide by rememberSetting(settings, "titleAutoHide", false)
            SettingItemWithSwitch(
                title = { Text("自动隐藏回答标题") },
                description = { Text("滚动时自动隐藏回答标题栏。") },
                checked = titleAutoHide,
                onCheckedChange = { titleAutoHide = it },
                settingKey = "titleAutoHide",
            )

            var autoHideArticleBottomBar by rememberSetting(settings, "autoHideArticleBottomBar", false)
            SettingItemWithSwitch(
                title = { Text("自动隐藏回答底部按钮") },
                description = { Text("手指向上滑动正文时隐藏悬浮操作栏，向下滑动时重新显示。") },
                checked = autoHideArticleBottomBar,
                onCheckedChange = { autoHideArticleBottomBar = it },
                settingKey = "autoHideArticleBottomBar",
            )

            var pinAnswerDate by rememberSetting(settings, "pinAnswerDate", false)
            SettingItemWithSwitch(
                title = { Text("置顶回答日期") },
                description = { Text("将回答的发布日期和编辑日期移动到内容最前面显示。") },
                checked = pinAnswerDate,
                onCheckedChange = { pinAnswerDate = it },
                settingKey = "pinAnswerDate",
            )

            var buttonSkipAnswer by rememberSetting(settings, "buttonSkipAnswer", true)
            SettingItemWithSwitch(
                title = { Text("显示跳转下一个回答按钮") },
                description = { Text("在回答页面显示可拖动的快速跳转按钮。") },
                checked = buttonSkipAnswer,
                onCheckedChange = { buttonSkipAnswer = it },
                settingKey = "buttonSkipAnswer",
            )

            var autoHideSkipAnswerButton by rememberSetting(settings, "autoHideSkipAnswerButton", true)
            AnimatedVisibility(visible = buttonSkipAnswer) {
                SettingItemWithSwitch(
                    title = { Text("滚动时自动隐藏跳转按钮") },
                    description = { Text("下拉正文时淡出「下一个回答」按钮，反向滚动时重新显示。") },
                    checked = autoHideSkipAnswerButton,
                    onCheckedChange = { autoHideSkipAnswerButton = it },
                    settingKey = "autoHideSkipAnswerButton",
                )
            }
        }
        SettingItemGroup(title = "回答切换") {
            if (isAnswerSwipeSupported) {
                val answerSwitchOptions = listOf(
                    "off" to "关闭",
                    "vertical" to "上下滑动",
                    "horizontal" to "左右滑动",
                )
                SettingItemSelect(
                    title = { Text("回答切换手势") },
                    description = { Text("在回答页面通过手势切换同一问题下的其他回答。") },
                    options = answerSwitchOptions.map { it.second },
                    selectedIndex = answerSwitchOptions.indexOfFirst { it.first == answerSwitchMode },
                    onSelect = { index ->
                        val (mode, label) = answerSwitchOptions[index]
                        answerSwitchMode = mode
                        userMessages.showShortMessage("已设置为：$label")
                    },
                    settingKey = ANSWER_SWITCH_MODE_PREFERENCE_KEY,
                )

                var storedSensitivity by rememberSetting(
                    settings,
                    ANSWER_SWITCH_SENSITIVITY_PREFERENCE_KEY,
                    DEFAULT_ANSWER_SWITCH_SENSITIVITY,
                )
                val sensitivity = normalizedAnswerSwitchSensitivity(storedSensitivity)
                AnimatedVisibility(visible = answerSwitchMode != "off") {
                    SettingItem(
                        title = { Text("回答切换灵敏度") },
                        description = {
                            Text("当前 ${(sensitivity * 10).roundToInt() / 10f}x，数值越高，滑动越短；同时作用于上下和左右切换。")
                        },
                        settingKey = ANSWER_SWITCH_SENSITIVITY_PREFERENCE_KEY,
                        bottomAction = {
                            AppSlider(
                                value = sensitivity,
                                onValueChange = {
                                    val next = (it * 10).roundToInt() / 10f
                                    if (next != storedSensitivity) storedSensitivity = next
                                },
                                valueRange = MIN_ANSWER_SWITCH_SENSITIVITY..MAX_ANSWER_SWITCH_SENSITIVITY,
                                steps = 24,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = AppDimens.Space8)
                                    .testTag(APPEARANCE_SETTINGS_ANSWER_SWITCH_SENSITIVITY_TAG),
                            )
                        },
                    )
                }
            }

            var doubleTapPreference by rememberSetting(
                settings,
                ANSWER_DOUBLE_TAP_ACTION_PREFERENCE_KEY,
                AnswerDoubleTapAction.Ask.preferenceValue,
            )
            val doubleTapAction = AnswerDoubleTapAction.fromPreference(doubleTapPreference)
            SettingItemSelect(
                title = { Text("双击回答动作") },
                description = { Text("双击回答正文时执行的动作。默认弹窗询问。") },
                options = AnswerDoubleTapAction.entries.map { it.label },
                selectedIndex = AnswerDoubleTapAction.entries.indexOf(doubleTapAction),
                onSelect = { index ->
                    val action = AnswerDoubleTapAction.entries[index]
                    doubleTapPreference = action.preferenceValue
                    userMessages.showShortMessage("已设置为：${action.label}")
                },
                modifier = Modifier.testTag(APPEARANCE_SETTINGS_ANSWER_DOUBLE_TAP_TAG),
                settingKey = ANSWER_DOUBLE_TAP_ACTION_PREFERENCE_KEY,
            )
        }

        if (isPageTurnSupported) {
            SettingItemGroup {
                val volumeKeyPageTurn by rememberSetting(settings, PREF_VOLUME_KEY_PAGE_TURN, false)
                val showPageTurnFab by rememberSetting(settings, PREF_SHOW_PAGE_TURN_FAB, false)
                SettingItem(
                    modifier = Modifier.testTag(READING_LAYOUT_SETTINGS_PAGE_TURN_TAG),
                    title = { Text("电纸书翻页") },
                    description = {
                        Text(
                            "音量键翻页已${if (volumeKeyPageTurn) "开启" else "关闭"} · 翻页按钮已${if (showPageTurnFab) "显示" else "隐藏"}" +
                                if (useWebView) "\nWebView 正文不支持电纸书翻页，其他支持翻页的页面不受影响。" else "",
                        )
                    },
                    onClick = { navigator.onNavigate(Account.PageTurnSettings()) },
                )
            }
        }

        if (isLegacyWebViewSupported) {
            SettingItemGroup(title = "WebView 渲染（已弃用）") {
                SettingItemWithSwitch(
                    modifier = Modifier.testTag(APPEARANCE_SETTINGS_USE_WEBVIEW_TAG),
                    title = { Text("使用 WebView 显示文章（已弃用）") },
                    description = {
                        Text(
                            if (useWebView) {
                                "当前已开启，仅作兼容性回退，不再更新。正文查找定位与高亮、正文内划线喜欢与句子评论交互等 Compose 专属功能不可用；关闭后恢复。"
                            } else {
                                "仅作兼容性回退，不再更新。开启后正文查找定位与高亮、正文内划线喜欢与句子评论交互等 Compose 专属功能不可用；每次开启都需确认。"
                            },
                        )
                    },
                    checked = articleUseWebview,
                    onCheckedChange = { checked ->
                        if (checked) {
                            showWebViewConfirmation = true
                        } else {
                            showWebViewConfirmation = false
                            articleUseWebview = false
                        }
                    },
                    settingKey = ARTICLE_USE_WEBVIEW_PREFERENCE_KEY,
                )

                var customFontName by rememberObservedSetting(settings, WEBVIEW_CUSTOM_FONT_NAME_PREFERENCE_KEY) {
                    getStringOrNull(WEBVIEW_CUSTOM_FONT_NAME_PREFERENCE_KEY)
                }
                AnimatedVisibility(visible = articleUseWebview && isWebViewCustomFontSupported) {
                    SettingItem(
                        modifier = Modifier.testTag(APPEARANCE_SETTINGS_WEBVIEW_FONT_TAG),
                        title = { Text("WebView 自定义字体") },
                        description = { Text(customFontName ?: "未设置") },
                        bottomAction = {
                            WebViewCustomFontSettings(
                                customFontName = customFontName,
                                onCustomFontNameChange = { name ->
                                    if (name == null) {
                                        settings.remove(WEBVIEW_CUSTOM_FONT_NAME_PREFERENCE_KEY)
                                    } else {
                                        settings.putString(WEBVIEW_CUSTOM_FONT_NAME_PREFERENCE_KEY, name)
                                    }
                                    customFontName = name
                                },
                            )
                        },
                    )
                }

                var hardwareAcceleration by rememberSetting(settings, WEBVIEW_HARDWARE_ACCELERATION_PREFERENCE_KEY, true)
                AnimatedVisibility(visible = articleUseWebview) {
                    SettingItemWithSwitch(
                        modifier = Modifier.testTag(APPEARANCE_SETTINGS_WEBVIEW_OPTIONS_TAG),
                        title = { Text("WebView 硬件加速") },
                        description = { Text("提高渲染性能，可能导致兼容性问题。") },
                        checked = hardwareAcceleration,
                        onCheckedChange = { hardwareAcceleration = it },
                        settingKey = WEBVIEW_HARDWARE_ACCELERATION_PREFERENCE_KEY,
                    )
                }
            }
        }
    }
}

private fun snappedContentFontSize(stored: Int): Int {
    val bounded = stored.coerceIn(contentFontSizeLevels.first(), contentFontSizeLevels.last())
    return contentFontSizeLevels.minBy { abs(it - bounded) }
}

/** 样张字号换算须与正文渲染一致。 */
@Composable
private fun ReadingTypographyPreview(
    fontSize: Int,
    lineHeight: Int,
    blockSpacing: Int,
    useTiqianRenderer: Boolean,
    mathFontFamilyId: String?,
) {
    val document = remember { htmlToMdAst(TYPOGRAPHY_PREVIEW_HTML) }
    if (useTiqianRenderer && isTiqianMarkdownRendererAvailable) {
        Box(Modifier.fillMaxWidth()) {
            PlatformTiqianMarkdown(
                document = document,
                sourceMarkdown = null,
                segmentCommentSnapshot = null,
                imageUrls = emptyList(),
                scrollState = rememberScrollState(),
                selectable = false,
                enableScroll = false,
                fontSizeScale = fontSize / 100f,
                lineHeightFromFontSize = lineHeight / 100f,
                blockSpacingScale = blockSpacing / 100f,
                mathFontFamilyId = mathFontFamilyId,
                onLinkClick = {},
                header = null,
                footer = null,
            )
        }
    } else {
        val defaultTypography = MarkdownDefaults.typography()
        val defaultLayout = MarkdownDefaults.layout()
        val scaledFontSize = 16.sp * fontSize / 100
        Markdown(
            document = document,
            modifier = Modifier.fillMaxWidth(),
            colors = MarkdownDefaults.material3Colors(),
            typography = defaultTypography.copy(
                text = defaultTypography.text.copy(
                    fontSize = scaledFontSize,
                    lineHeight = scaledFontSize * lineHeight / 100,
                ),
            ),
            layout = defaultLayout.copy(blockSpacing = defaultLayout.blockSpacing * (blockSpacing / 100f)),
            enableScroll = false,
            enableSelection = false,
        )
    }
}
