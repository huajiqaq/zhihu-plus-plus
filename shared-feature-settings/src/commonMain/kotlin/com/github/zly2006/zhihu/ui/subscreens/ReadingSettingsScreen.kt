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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.sp
import com.github.zly2006.zhihu.i18n.SettingsStrings
import com.github.zly2006.zhihu.markdown.LocalMarkdownWidthManagedByHost
import com.github.zly2006.zhihu.markdown.PlatformTiqianMarkdown
import com.github.zly2006.zhihu.markdown.TiqianBrandTitle
import com.github.zly2006.zhihu.markdown.htmlToMdAst
import com.github.zly2006.zhihu.markdown.isTiqianMarkdownRendererAvailable
import com.github.zly2006.zhihu.navigation.Account
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.platform.defaultAnswerSwitchMode
import com.github.zly2006.zhihu.platform.isAnswerSwipeSupported
import com.github.zly2006.zhihu.platform.isPageTurnSupported
import com.github.zly2006.zhihu.platform.platformName
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

private const val DEFAULT_FONT_SIZE_PERCENT = 100
private const val DEFAULT_LINE_HEIGHT_PERCENT = 160
private const val DEFAULT_BLOCK_SPACING_PERCENT = 100
private const val DEFAULT_TIQIAN_MARKDOWN = false
private const val TIQIAN_SANS_MATH_FONT = "lete"

val READING_LAYOUT_SETTINGS_KEYS: Set<String> = setOf(
    "fontScale",
    PREF_FONT_SIZE,
    PREF_LINE_HEIGHT,
    PREF_BLOCK_SPACING,
    "contentAndHome",
    "titleAutoHide",
    "autoHideArticleBottomBar",
    "pinAnswerDate",
    "buttonSkipAnswer",
    "autoHideSkipAnswerButton",
    ANSWER_SWITCH_MODE_PREFERENCE_KEY,
    ANSWER_SWITCH_SENSITIVITY_PREFERENCE_KEY,
    ANSWER_DOUBLE_TAP_ACTION_PREFERENCE_KEY,
    ARTICLE_USE_WEBVIEW_PREFERENCE_KEY,
    WEBVIEW_CUSTOM_FONT_NAME_PREFERENCE_KEY,
    WEBVIEW_HARDWARE_ACCELERATION_PREFERENCE_KEY,
    TIQIAN_MARKDOWN_PREFERENCE_KEY,
    TIQIAN_MATH_FONT_PREFERENCE_KEY,
)

@Composable
fun ReadingSettingsScreen(
    setting: String? = null,
) {
    val settings = rememberSettingsStore()
    val userMessages = rememberUserMessageSink()
    val navigator = LocalNavigator.current

    var storedFontSize by rememberSetting(settings, PREF_FONT_SIZE, DEFAULT_FONT_SIZE_PERCENT)
    val fontSize = snappedContentFontSize(storedFontSize)
    LaunchedEffect(storedFontSize) {
        if (storedFontSize != fontSize) storedFontSize = fontSize
    }
    var lineHeight by rememberSetting(settings, PREF_LINE_HEIGHT, DEFAULT_LINE_HEIGHT_PERCENT)
    var blockSpacing by rememberSetting(settings, PREF_BLOCK_SPACING, DEFAULT_BLOCK_SPACING_PERCENT)
    var tiqianMarkdown by rememberSetting(settings, TIQIAN_MARKDOWN_PREFERENCE_KEY, DEFAULT_TIQIAN_MARKDOWN)
    var tiqianMathFont by rememberSetting(settings, TIQIAN_MATH_FONT_PREFERENCE_KEY, TIQIAN_SANS_MATH_FONT)
    var answerSwitchMode by rememberSetting(settings, ANSWER_SWITCH_MODE_PREFERENCE_KEY, defaultAnswerSwitchMode)
    var articleUseWebview by rememberSetting(settings, ARTICLE_USE_WEBVIEW_PREFERENCE_KEY, false)
    val useWebView = isLegacyWebViewSupported && articleUseWebview
    var showWebViewConfirmation by remember(articleUseWebview) { mutableStateOf(false) }

    val highlightedKey = when {
        setting == PREF_FONT_SIZE -> "fontScale"
        setting in setOf(WEBVIEW_CUSTOM_FONT_NAME_PREFERENCE_KEY, WEBVIEW_HARDWARE_ACCELERATION_PREFERENCE_KEY) && !useWebView -> ARTICLE_USE_WEBVIEW_PREFERENCE_KEY
        setting == "autoHideSkipAnswerButton" && !settings.getBoolean("buttonSkipAnswer", true) -> "buttonSkipAnswer"
        setting in setOf(TIQIAN_MARKDOWN_PREFERENCE_KEY, TIQIAN_MATH_FONT_PREFERENCE_KEY, PREF_BLOCK_SPACING) && useWebView -> ARTICLE_USE_WEBVIEW_PREFERENCE_KEY
        setting == TIQIAN_MATH_FONT_PREFERENCE_KEY && !tiqianMarkdown -> TIQIAN_MARKDOWN_PREFERENCE_KEY
        setting == ANSWER_SWITCH_SENSITIVITY_PREFERENCE_KEY && answerSwitchMode == "off" -> ANSWER_SWITCH_MODE_PREFERENCE_KEY
        else -> setting.orEmpty()
    }

    AppSettingsPage(
        title = SettingsStrings.readingLayout,
        onBack = navigator.onNavigateBack,
        backTag = READING_SETTINGS_BACK_BUTTON_TAG,
        highlightedKey = highlightedKey,
        scrollTag = READING_LAYOUT_SETTINGS_SCROLL_TAG,
        overlays = {
            if (showWebViewConfirmation) {
                AppDialog(
                    modifier = Modifier.testTag(READING_LAYOUT_SETTINGS_WEBVIEW_CONFIRM_TAG),
                    title = SettingsStrings.enableLegacyWebview,
                    summary = SettingsStrings.legacyWebviewConfirmation,
                    confirmText = SettingsStrings.enableAnyway,
                    onConfirm = {
                        articleUseWebview = true
                        showWebViewConfirmation = false
                    },
                    dismissText = SettingsStrings.cancel,
                    onDismissRequest = { showWebViewConfirmation = false },
                )
            }
        },
    ) {
        SettingItemGroup(title = SettingsStrings.typographyPreview) {
            SettingItem(
                modifier = Modifier.testTag(READING_LAYOUT_SETTINGS_PREVIEW_TAG),
                title = {
                    CompositionLocalProvider(LocalMarkdownWidthManagedByHost provides true) {
                        ReadingTypographyPreview(
                            fontSize = fontSize,
                            lineHeight = lineHeight,
                            blockSpacing = blockSpacing,
                            useTiqianRenderer = tiqianMarkdown,
                            mathFontFamilyId = tiqianMathFont.takeIf { it == "stix" },
                        )
                    }
                },
                description = if (useWebView) {
                    { Text(SettingsStrings.composePreviewNote) }
                } else {
                    null
                },
            )
        }

        SettingItemGroup(
            title = SettingsStrings.typography,
            settingKey = "contentAndHome",
        ) {
            SettingItem(
                title = { Text(SettingsStrings.fontSize) },
                description = { Text("$fontSize%") },
                settingKey = "fontScale",
                bottomAction = {
                    val index = contentFontSizeLevels.indexOf(fontSize).toFloat()
                    val slider = rememberSliderState(
                        value = index,
                        steps = contentFontSizeLevels.size - 2,
                        trackRange = 0f..contentFontSizeLevels.lastIndex.toFloat(),
                    )
                    slider.value = index
                    AppSlider(
                        state = slider,
                        onValueChange = {
                            val level = contentFontSizeLevels[it.roundToInt().coerceIn(0, contentFontSizeLevels.lastIndex)]
                            if (level != storedFontSize) storedFontSize = level
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = AppDimens.Space8),
                    )
                },
            )
            SettingItem(
                title = { Text(SettingsStrings.lineHeight) },
                description = { Text("$lineHeight%") },
                settingKey = PREF_LINE_HEIGHT,
                bottomAction = {
                    val slider = rememberSliderState(value = lineHeight.toFloat(), steps = 19, trackRange = 100f..300f)
                    slider.value = lineHeight.toFloat()
                    AppSlider(
                        state = slider,
                        onValueChange = {
                            val height = ((it / 10).roundToInt() * 10).coerceIn(100, 300)
                            if (height != lineHeight) lineHeight = height
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = AppDimens.Space8),
                    )
                },
            )
            SettingItem(
                title = { Text(SettingsStrings.paragraphSpacing) },
                description = {
                    Text(if (useWebView) SettingsStrings.webviewSpacingUnavailable(blockSpacing) else "$blockSpacing%")
                },
                enabled = !useWebView,
                settingKey = PREF_BLOCK_SPACING,
                bottomAction = {
                    val slider = rememberSliderState(value = blockSpacing.toFloat(), steps = 29, trackRange = 0f..300f)
                    slider.value = blockSpacing.toFloat()
                    AppSlider(
                        state = slider,
                        enabled = !useWebView,
                        onValueChange = {
                            val spacing = ((it / 10).roundToInt() * 10).coerceIn(0, 300)
                            if (spacing != blockSpacing) blockSpacing = spacing
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = AppDimens.Space8),
                    )
                },
            )
            if (isTiqianMarkdownRendererAvailable) {
                SettingItemWithSwitch(
                    modifier = Modifier.testTag(APPEARANCE_SETTINGS_TIQIAN_MARKDOWN_TAG),
                    title = { TiqianBrandTitle(prefix = "", suffix = SettingsStrings.typography) },
                    description = {
                        Text(
                            if (useWebView) {
                                SettingsStrings.disableWebviewFirst
                            } else {
                                SettingsStrings.tiqianDescription
                            },
                        )
                    },
                    checked = tiqianMarkdown,
                    enabled = !useWebView,
                    onCheckedChange = { tiqianMarkdown = it },
                    settingKey = TIQIAN_MARKDOWN_PREFERENCE_KEY,
                )
                AnimatedVisibility(visible = tiqianMarkdown) {
                    SettingItemWithSwitch(
                        title = { Text(SettingsStrings.sansMathFont) },
                        description = {
                            Text(
                                if (useWebView) {
                                    SettingsStrings.disableWebviewFirst
                                } else {
                                    SettingsStrings.sansMathFontDescription
                                },
                            )
                        },
                        checked = tiqianMathFont == TIQIAN_SANS_MATH_FONT,
                        enabled = !useWebView,
                        onCheckedChange = { tiqianMathFont = if (it) TIQIAN_SANS_MATH_FONT else "stix" },
                        settingKey = TIQIAN_MATH_FONT_PREFERENCE_KEY,
                    )
                }
            }
            SettingItem(
                title = { Text(SettingsStrings.restoreDefaults) },
                description = {
                    Text(
                        if (isTiqianMarkdownRendererAvailable) {
                            SettingsStrings.restoreTypographyFull
                        } else {
                            SettingsStrings.restoreTypographyBasic
                        },
                    )
                },
                endAction = { Icon(Icons.Default.RestartAlt, contentDescription = null) },
                onClick = {
                    storedFontSize = DEFAULT_FONT_SIZE_PERCENT
                    lineHeight = DEFAULT_LINE_HEIGHT_PERCENT
                    blockSpacing = DEFAULT_BLOCK_SPACING_PERCENT
                    if (isTiqianMarkdownRendererAvailable) {
                        tiqianMarkdown = DEFAULT_TIQIAN_MARKDOWN
                        tiqianMathFont = TIQIAN_SANS_MATH_FONT
                    }
                    userMessages.showShortMessage(SettingsStrings.typographyRestored)
                },
            )
        }

        SettingItemGroup(title = SettingsStrings.answerPage) {
            var titleAutoHide by rememberSetting(settings, "titleAutoHide", false)
            SettingItemWithSwitch(
                title = { Text(SettingsStrings.autoHideAnswerTitle) },
                description = { Text(SettingsStrings.autoHideAnswerTitleDescription) },
                checked = titleAutoHide,
                onCheckedChange = { titleAutoHide = it },
                settingKey = "titleAutoHide",
            )

            var autoHideArticleBottomBar by rememberSetting(settings, "autoHideArticleBottomBar", true)
            SettingItemWithSwitch(
                title = { Text(SettingsStrings.autoHideAnswerToolbar) },
                description = { Text(SettingsStrings.autoHideAnswerToolbarDescription) },
                checked = autoHideArticleBottomBar,
                onCheckedChange = { autoHideArticleBottomBar = it },
                settingKey = "autoHideArticleBottomBar",
            )

            var pinAnswerDate by rememberSetting(settings, "pinAnswerDate", false)
            SettingItemWithSwitch(
                title = { Text(SettingsStrings.pinAnswerDate) },
                description = { Text(SettingsStrings.pinAnswerDateDescription) },
                checked = pinAnswerDate,
                onCheckedChange = { pinAnswerDate = it },
                settingKey = "pinAnswerDate",
            )

            var buttonSkipAnswer by rememberSetting(settings, "buttonSkipAnswer", true)
            SettingItemWithSwitch(
                title = { Text(SettingsStrings.nextAnswerButton) },
                description = { Text(SettingsStrings.nextAnswerButtonDescription) },
                checked = buttonSkipAnswer,
                onCheckedChange = { buttonSkipAnswer = it },
                settingKey = "buttonSkipAnswer",
            )

            var autoHideSkipAnswerButton by rememberSetting(settings, "autoHideSkipAnswerButton", true)
            AnimatedVisibility(visible = buttonSkipAnswer) {
                SettingItemWithSwitch(
                    title = { Text(SettingsStrings.autoHideNextAnswer) },
                    description = { Text(SettingsStrings.autoHideNextAnswerDescription) },
                    checked = autoHideSkipAnswerButton,
                    onCheckedChange = { autoHideSkipAnswerButton = it },
                    settingKey = "autoHideSkipAnswerButton",
                )
            }
        }
        SettingItemGroup(title = SettingsStrings.answerSwitching) {
            if (isAnswerSwipeSupported) {
                val answerSwitchOptions = listOf(
                    "off" to SettingsStrings.off,
                    "vertical" to SettingsStrings.swipeVertical,
                    "horizontal" to SettingsStrings.swipeHorizontal,
                )
                SettingItemSelect(
                    title = { Text(SettingsStrings.answerSwitchGesture) },
                    description = { Text(SettingsStrings.answerSwitchGestureDescription) },
                    options = answerSwitchOptions.map { it.second },
                    selectedIndex = answerSwitchOptions.indexOfFirst { it.first == answerSwitchMode },
                    onSelect = { index ->
                        val (mode, label) = answerSwitchOptions[index]
                        answerSwitchMode = mode
                        userMessages.showShortMessage(SettingsStrings.settingChanged(label))
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
                        title = { Text(SettingsStrings.answerSwitchSensitivity) },
                        description = {
                            Text(SettingsStrings.answerSwitchSensitivityDescription((sensitivity * 10).roundToInt() / 10f))
                        },
                        settingKey = ANSWER_SWITCH_SENSITIVITY_PREFERENCE_KEY,
                        bottomAction = {
                            val slider = rememberSliderState(
                                value = sensitivity,
                                steps = 24,
                                trackRange = MIN_ANSWER_SWITCH_SENSITIVITY..MAX_ANSWER_SWITCH_SENSITIVITY,
                            )
                            slider.value = sensitivity
                            AppSlider(
                                state = slider,
                                onValueChange = {
                                    val next = (it * 10).roundToInt() / 10f
                                    if (next != storedSensitivity) storedSensitivity = next
                                },
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
                title = { Text(SettingsStrings.answerDoubleTap) },
                description = { Text(SettingsStrings.answerDoubleTapDescription) },
                options = AnswerDoubleTapAction.entries.map { it.label },
                selectedIndex = AnswerDoubleTapAction.entries.indexOf(doubleTapAction),
                onSelect = { index ->
                    val action = AnswerDoubleTapAction.entries[index]
                    doubleTapPreference = action.preferenceValue
                    userMessages.showShortMessage(SettingsStrings.answerDoubleTapChanged(action.label))
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
                    title = { Text(if (platformName != "Android") SettingsStrings.pageTurning else SettingsStrings.einkPageTurning) },
                    description = {
                        Text(
                            (if (platformName == "Android") {
                                SettingsStrings.pageTurnSummary(
                                    if (volumeKeyPageTurn) SettingsStrings.enabled else SettingsStrings.off,
                                    if (showPageTurnFab) SettingsStrings.show else SettingsStrings.hide,
                                )
                            } else {
                                SettingsStrings.pageTurnButtonsSummary(if (showPageTurnFab) SettingsStrings.show else SettingsStrings.hide)
                            }) + if (useWebView) SettingsStrings.webviewPageTurnNote else "",
                        )
                    },
                    onClick = { navigator.onNavigate(Account.PageTurnSettings()) },
                )
            }
        }

        if (isLegacyWebViewSupported) {
            SettingItemGroup(title = SettingsStrings.legacyWebviewSection) {
                SettingItemWithSwitch(
                    modifier = Modifier.testTag(APPEARANCE_SETTINGS_USE_WEBVIEW_TAG),
                    title = { Text(SettingsStrings.legacyWebview) },
                    description = {
                        Text(
                            if (useWebView) {
                                SettingsStrings.legacyWebviewEnabled
                            } else {
                                SettingsStrings.legacyWebviewDisabled
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
                        title = { Text(SettingsStrings.webviewCustomFont) },
                        description = { Text(customFontName ?: SettingsStrings.notSet) },
                        settingKey = WEBVIEW_CUSTOM_FONT_NAME_PREFERENCE_KEY,
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
                        title = { Text(SettingsStrings.webviewHardwareAcceleration) },
                        description = { Text(SettingsStrings.webviewHardwareAccelerationDescription) },
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
    val previewHtml = SettingsStrings.typographyPreviewFirst + SettingsStrings.typographyPreviewSecond + SettingsStrings.typographyPreviewThird
    val document = remember(previewHtml) { htmlToMdAst(previewHtml) }
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
