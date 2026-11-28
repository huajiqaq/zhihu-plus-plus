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

package com.github.zly2006.zhihu.account

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import com.github.zly2006.zhihu.i18n.AccountStrings
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.theme.LocalAppTheme
import com.github.zly2006.zhihu.theme.LocalAppThemeOverride
import com.github.zly2006.zhihu.theme.ThemeStyle
import com.github.zly2006.zhihu.ui.components.AppBackButton
import com.github.zly2006.zhihu.ui.components.AppButton
import com.github.zly2006.zhihu.ui.components.AppCheckbox
import com.github.zly2006.zhihu.ui.components.AppCompactTopBar
import com.github.zly2006.zhihu.ui.components.AppDialog
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppIcon
import com.github.zly2006.zhihu.ui.components.AppLinearProgress
import com.github.zly2006.zhihu.ui.components.AppScaffold
import com.github.zly2006.zhihu.ui.components.AppSegmentedControl
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.ui.components.SettingItem
import com.github.zly2006.zhihu.ui.components.SettingItemGroup
import com.github.zly2006.zhihu.ui.components.appGroupedPageBackground
import com.github.zly2006.zhihu.ui.components.settingSkinIsMiuix
import com.github.zly2006.zhihu.ui.rememberHomeIsDebuggable
import com.github.zly2006.zhihu.ui.rememberSetting

internal const val ZHIHU_TERMS_URL = "https://www.zhihu.com/term/zhihu-terms"
internal const val ZHIHU_PRIVACY_URL = "https://www.zhihu.com/term/privacy"
private const val ZHIHU_OFFICIAL_APP_URL = "https://www.zhihu.com/app/"
private const val LOGIN_BACK_TAG = "login_back"

enum class LoginMethod(
    private val labelText: () -> String,
    val tag: String,
) {
    Phone({ AccountStrings.loginPhone }, "login_mode_phone"),
    Qr({ AccountStrings.loginQr }, "login_mode_qr"),
    Web({ AccountStrings.loginWebFallback }, "login_mode_web"),
    ;

    val label: String get() = labelText()
}

expect val supportedLoginMethods: List<LoginMethod>

@Composable
expect fun QrLoginPane(onLoginSuccess: (String) -> Unit)

@Composable
expect fun WebLoginPane(onLoginSuccess: (String) -> Unit, onBrowserSessionVerified: () -> Unit)

/** 手机表单状态跨登录方式切换保留；onBack=null 表示根页面入口。 */
@Composable
fun LoginScreen(
    onLoginComplete: () -> Unit,
    onOpenTelemetrySettings: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val uriHandler = LocalUriHandler.current
    val settings = rememberSettingsStore()
    val phoneLoginState = if (LoginMethod.Phone in supportedLoginMethods) rememberPhoneLoginState() else null
    var noticesConfirmed by rememberSetting(settings, "loginNoticesConfirmed", false)
    var loggedInUsername by remember { mutableStateOf<String?>(null) }

    if (!noticesConfirmed) {
        LoginWelcomeStage(
            onBack = onBack,
            onOpenTerms = { uriHandler.openUri(ZHIHU_TERMS_URL) },
            onOpenTelemetrySettings = onOpenTelemetrySettings,
            onOpenOfficialApp = { uriHandler.openUri(ZHIHU_OFFICIAL_APP_URL) },
            onConfirm = { noticesConfirmed = true },
        )
        return
    }
    LoginMethodsStage(
        onBack = onBack,
        onContinueAsGuest = {
            settings.putBoolean("loginForRecommendation", false)
            onLoginComplete()
        },
        phoneLoginState = phoneLoginState,
        onLoginSuccess = { username -> loggedInUsername = username },
        onBrowserSessionVerified = onLoginComplete,
    ) {
        loggedInUsername?.let { username ->
            AppDialog(
                onDismissRequest = onLoginComplete,
                title = AccountStrings.loginSuccess,
                summary = AccountStrings.loginWelcome(username),
                confirmText = AccountStrings.confirm,
                onConfirm = onLoginComplete,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoginMethodsStage(
    onBack: (() -> Unit)?,
    onContinueAsGuest: () -> Unit,
    phoneLoginState: PhoneLoginState?,
    onLoginSuccess: (String) -> Unit,
    onBrowserSessionVerified: () -> Unit,
    overlays: @Composable () -> Unit,
) {
    val methods = supportedLoginMethods
    var selectedMethod by rememberSaveable(methods) { mutableStateOf(methods.first()) }
    val background = appGroupedPageBackground()
    AppScaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = background,
        topBar = {
            if (selectedMethod == LoginMethod.Web) {
                AppCompactTopBar(
                    title = AccountStrings.loginWeb,
                    onBack = onBack,
                    backTag = LOGIN_BACK_TAG,
                    containerColor = background,
                    actions = {
                        if (methods.size > 1) {
                            AppTextButton(
                                text = AccountStrings.loginOtherMethods,
                                onClick = { selectedMethod = methods.first { it != LoginMethod.Web } },
                                modifier = Modifier.testTag("login_method_return"),
                            )
                        } else {
                            AppTextButton(
                                text = AccountStrings.loginGuest,
                                onClick = onContinueAsGuest,
                                modifier = Modifier.testTag("login_guest"),
                            )
                        }
                    },
                )
            } else {
                AppCompactTopBar(
                    title = AccountStrings.loginTitle,
                    onBack = onBack,
                    backTag = LOGIN_BACK_TAG,
                    containerColor = background,
                    actions = {
                        AppTextButton(
                            text = AccountStrings.loginGuest,
                            onClick = onContinueAsGuest,
                            modifier = Modifier.testTag("login_guest"),
                        )
                    },
                )
            }
        },
    ) { innerPadding ->
        if (selectedMethod == LoginMethod.Web) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
                    .imePadding()
                    .testTag("login_web_content"),
            ) {
                WebLoginPane(onLoginSuccess, onBrowserSessionVerified)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
                    .imePadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = AppDimens.ContentMaxWidth)
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(
                            horizontal = AppDimens.PageHeaderM3eHorizontalPadding,
                            vertical = AppDimens.Space4,
                        ),
                    verticalArrangement = Arrangement.spacedBy(AppDimens.Space12),
                ) {
                    if (methods.size > 1) {
                        AppSegmentedControl(
                            options = methods.map(LoginMethod::label),
                            selectedIndex = methods.indexOf(selectedMethod),
                            onSelect = { selectedMethod = methods[it] },
                            modifier = Modifier.testTag("login_mode_selector"),
                            testTags = methods.map(LoginMethod::tag),
                            enabled = phoneLoginState?.isBusy != true,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        if (selectedMethod == LoginMethod.Phone) {
                            PhoneLoginPane(onLoginSuccess = onLoginSuccess, state = checkNotNull(phoneLoginState))
                        } else {
                            QrLoginPane(onLoginSuccess)
                        }
                    }
                }
            }
        }
        overlays()
    }
}

@Composable
private fun LoginWelcomeStage(
    onBack: (() -> Unit)?,
    onOpenTerms: () -> Unit,
    onOpenTelemetrySettings: () -> Unit,
    onOpenOfficialApp: () -> Unit,
    onConfirm: () -> Unit,
) {
    var expandedNoticeIndex by rememberSaveable { mutableIntStateOf(-1) }
    var readNoticesMask by rememberSaveable { mutableIntStateOf(0) }
    var agreed by rememberSaveable { mutableStateOf(false) }
    val loginNotices = loginNotices()
    val allNoticesRead = readNoticesMask == (1 shl loginNotices.size) - 1
    val readNoticeCount = loginNotices.indices.count { readNoticesMask and (1 shl it) != 0 }
    val onToggleNotice: (Int) -> Unit = { index ->
        /** 已读标记不随收起回退，避免强迫用户同时展开全部说明。 */
        if (expandedNoticeIndex != index) {
            readNoticesMask = readNoticesMask or (1 shl index)
        }
        expandedNoticeIndex = if (expandedNoticeIndex == index) -1 else index
    }
    val miuix = settingSkinIsMiuix()
    val groupInset = if (miuix) AppDimens.SettingGroupHorizontalMarginMiuix else AppDimens.SettingGroupHeaderHorizontalPadding
    val isDebuggable = rememberHomeIsDebuggable()
    val theme by rememberUpdatedState(LocalAppTheme.current)
    val overrideStyle by rememberUpdatedState(LocalAppThemeOverride.current)
    val background = appGroupedPageBackground()

    AppScaffold(containerColor = background) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = AppDimens.ContentMaxWidth)
                    .fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(horizontal = groupInset)) {
                    if (onBack != null) {
                        Spacer(Modifier.height(AppDimens.Space8))
                        AppBackButton(onClick = onBack, modifier = Modifier.testTag(LOGIN_BACK_TAG))
                    }
                    Spacer(Modifier.height(if (onBack == null) AppDimens.LoginHeroTopSpacing else AppDimens.Space16))
                    Text(
                        text = AccountStrings.loginBeforeStart,
                        modifier = if (isDebuggable) {
                            Modifier.pointerInput(Unit) {
                                detectTapGestures(
                                    onLongPress = {
                                        overrideStyle(
                                            if (theme.style == ThemeStyle.MIUIX) {
                                                ThemeStyle.MATERIAL_EXPRESSIVE
                                            } else {
                                                ThemeStyle.MIUIX
                                            },
                                        )
                                    },
                                )
                            }
                        } else {
                            Modifier
                        },
                        style = if (miuix) {
                            LocalAppSkinTokens.current.headline2.copy(fontWeight = FontWeight.Bold)
                        } else {
                            MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold)
                        },
                        color = primaryTextColor(),
                    )
                    Spacer(Modifier.height(AppDimens.Space8))
                    Text(
                        text = AccountStrings.loginNoticeIntro,
                        style = if (miuix) {
                            LocalAppSkinTokens.current.footnote2
                        } else {
                            MaterialTheme.typography.bodyLarge
                        },
                        color = secondaryTextColor(),
                    )
                    Spacer(Modifier.height(AppDimens.Space16))
                    Text(
                        text = AccountStrings.loginNoticeProgress(readNoticeCount, loginNotices.size),
                        style = if (miuix) LocalAppSkinTokens.current.footnote2 else MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = accentColor(),
                    )
                    Spacer(Modifier.height(AppDimens.Space4))
                    AppLinearProgress(progress = readNoticeCount.toFloat() / loginNotices.size, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(AppDimens.Space12))
                }

                SettingItemGroup {
                    loginNotices.forEachIndexed { index, notice ->
                        LoginNoticeRow(
                            index = index,
                            notice = notice,
                            expanded = expandedNoticeIndex == index,
                            read = readNoticesMask and (1 shl index) != 0,
                            onToggle = { onToggleNotice(index) },
                            onInlineAction = when (index) {
                                1 -> onOpenTerms
                                2 -> onOpenTelemetrySettings
                                else -> null
                            },
                        )
                    }
                }

                Column(modifier = Modifier.padding(horizontal = groupInset)) {
                    AnimatedVisibility(
                        visible = allNoticesRead,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RectangleShape)
                                .toggleable(value = agreed, role = Role.Checkbox, onValueChange = { agreed = it })
                                .heightIn(min = AppDimens.TouchTarget)
                                .padding(vertical = AppDimens.Space8)
                                .testTag("login_notice_agreement"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppCheckbox(
                                checked = agreed,
                                onCheckedChange = null,
                            )
                            Spacer(Modifier.width(AppDimens.Space10))
                            Text(
                                text = AccountStrings.loginAgreeNotices,
                                style = if (miuix) {
                                    LocalAppSkinTokens.current.footnote2
                                } else {
                                    MaterialTheme.typography.bodyMedium
                                },
                                color = primaryTextColor(),
                            )
                        }
                    }

                    WelcomeActions(
                        agreed = agreed && allNoticesRead,
                        onConfirm = onConfirm,
                        onOpenOfficialApp = onOpenOfficialApp,
                    )
                    Spacer(Modifier.height(AppDimens.Space12))
                }
            }
        }
    }
}

@Composable
private fun LoginNoticeRow(
    index: Int,
    notice: LoginNotice,
    expanded: Boolean,
    read: Boolean,
    onToggle: () -> Unit,
    onInlineAction: (() -> Unit)?,
) {
    val miuix = settingSkinIsMiuix()
    val toggle: () -> Boolean = {
        onToggle()
        true
    }
    val body: @Composable () -> Unit = {
        /** Miuix 原生行已为底部内容留出 8dp 间距。 */
        Column(modifier = Modifier.padding(top = if (miuix) AppDimens.ElevationNone else AppDimens.Space10)) {
            LinkifiedNotice(
                message = notice.message,
                color = secondaryTextColor(),
            )
            if (onInlineAction != null && notice.inlineActionText != null) {
                AppTextButton(
                    text = notice.inlineActionText,
                    onClick = onInlineAction,
                    modifier = Modifier.align(Alignment.End),
                )
            }
        }
    }
    val animatedBody: @Composable () -> Unit = {
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) { body() }
    }
    SettingItem(
        modifier = Modifier
            .testTag("login_notice_step_${index + 1}")
            .semantics {
                stateDescription = if (expanded) AccountStrings.expanded else AccountStrings.collapsed
                if (expanded) {
                    collapse(action = toggle)
                } else {
                    expand(action = toggle)
                }
            }.animateContentSize(),
        title = { Text(notice.title) },
        description = { Text(notice.summary) },
        icon = {
            if (read) {
                AppIcon(Icons.Filled.Check, contentDescription = AccountStrings.read, tint = accentColor())
            } else {
                Text("${index + 1}", color = accentColor(), fontWeight = FontWeight.SemiBold)
            }
        },
        onClick = onToggle,
        shape = RectangleShape,
        endAction = {
            AppIcon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = secondaryTextColor(),
            )
        },
        /** Miuix 收起时省略底部内容以免留空；M3E 保持内容宿主以执行展开动画。 */
        bottomAction = when {
            !miuix -> animatedBody
            expanded -> body
            else -> null
        },
    )
}

@Composable
private fun WelcomeActions(
    agreed: Boolean,
    onConfirm: () -> Unit,
    onOpenOfficialApp: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = AppDimens.Space8, bottom = AppDimens.Space4),
        verticalArrangement = Arrangement.spacedBy(AppDimens.Space4),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppButton(
            text = AccountStrings.loginAgreeContinue,
            onClick = onConfirm,
            enabled = agreed,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = AppDimens.LoginMethodHeight)
                .testTag("login_notice_confirm"),
        )
        AppTextButton(
            text = AccountStrings.loginOfficialApp,
            onClick = onOpenOfficialApp,
            modifier = Modifier.testTag("login_notice_secondary_action"),
        )
    }
}

@Composable
private fun LinkifiedNotice(
    message: String,
    color: Color,
) {
    val uriHandler = LocalUriHandler.current
    val linkStyle = SpanStyle(
        color = accentColor(),
        textDecoration = TextDecoration.Underline,
    )
    val annotated = remember(message, linkStyle, uriHandler) {
        buildAnnotatedString {
            var cursor = 0
            URL_REGEX.findAll(message).forEach { match ->
                if (match.range.first > cursor) {
                    append(message.substring(cursor, match.range.first))
                }
                val url = match.value
                withLink(
                    LinkAnnotation.Url(
                        url = url,
                        styles = TextLinkStyles(style = linkStyle),
                    ) {
                        uriHandler.openUri(url)
                    },
                ) { append(url) }
                cursor = match.range.last + 1
            }
            if (cursor < message.length) {
                append(message.substring(cursor))
            }
        }
    }
    Text(
        text = annotated,
        style = if (settingSkinIsMiuix()) {
            LocalAppSkinTokens.current.footnote2
        } else {
            MaterialTheme.typography.bodyMedium
        },
        color = color,
    )
}

@Composable
private fun accentColor(): Color = if (settingSkinIsMiuix()) {
    LocalAppSkinTokens.current.primary
} else {
    MaterialTheme.colorScheme.primary
}

@Composable
private fun primaryTextColor(): Color = if (settingSkinIsMiuix()) {
    LocalAppSkinTokens.current.onSurface
} else {
    MaterialTheme.colorScheme.onBackground
}

@Composable
private fun secondaryTextColor(): Color = if (settingSkinIsMiuix()) {
    LocalAppSkinTokens.current.onSurfaceVariantSummary
} else {
    MaterialTheme.colorScheme.onSurfaceVariant
}

private data class LoginNotice(
    val title: String,
    val summary: String,
    val message: String,
    val inlineActionText: String? = null,
)

private val URL_REGEX = Regex("""https?://[A-Za-z0-9./_%?=&:#~+-]+""")

private fun loginNotices(): List<LoginNotice> = listOf(
    LoginNotice(
        title = AccountStrings.loginAboutTitle,
        summary = AccountStrings.loginAboutSummary,
        message = AccountStrings.loginAboutNotice(ZHIHU_OFFICIAL_APP_URL),
    ),
    LoginNotice(
        title = AccountStrings.loginTermsTitle,
        summary = AccountStrings.loginTermsSummary,
        message = AccountStrings.loginTermsNotice(ZHIHU_TERMS_URL),
        inlineActionText = AccountStrings.loginViewTerms,
    ),
    LoginNotice(
        title = AccountStrings.loginPrivacyTitle,
        summary = AccountStrings.loginPrivacySummary,
        message = AccountStrings.loginPrivacyNotice,
        inlineActionText = AccountStrings.loginViewSettings,
    ),
)
