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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.github.zly2006.zhihu.platform.rememberExternalUrlOpener
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.theme.ThemeManager
import com.github.zly2006.zhihu.theme.ThemeStyle
import com.github.zly2006.zhihu.ui.components.AppButton
import com.github.zly2006.zhihu.ui.components.AppCheckbox
import com.github.zly2006.zhihu.ui.components.AppCompactTopBar
import com.github.zly2006.zhihu.ui.components.AppDialog
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppPageTitleBar
import com.github.zly2006.zhihu.ui.components.AppScaffold
import com.github.zly2006.zhihu.ui.components.AppSegmentedControl
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.ui.components.SettingItem
import com.github.zly2006.zhihu.ui.components.SettingItemGroup
import com.github.zly2006.zhihu.ui.components.appGroupedPageBackground
import com.github.zly2006.zhihu.ui.components.appPageTitleBarScroll
import com.github.zly2006.zhihu.ui.components.rememberAppPageTitleBarScrollState
import com.github.zly2006.zhihu.ui.components.settingSkinIsMiuix
import com.github.zly2006.zhihu.ui.rememberHomeIsDebuggable
import com.github.zly2006.zhihu.ui.components.AppIcon as Icon

internal const val ZHIHU_TERMS_URL = "https://www.zhihu.com/term/zhihu-terms"
internal const val ZHIHU_PRIVACY_URL = "https://www.zhihu.com/term/privacy"
private const val ZHIHU_OFFICIAL_APP_URL = "https://www.zhihu.com/app/"
private const val LOGIN_BACK_TAG = "login_back"

enum class LoginMethod(
    val label: String,
    val tag: String,
) {
    Phone("手机号登录", "login_mode_phone"),
    Qr("扫码登录", "login_mode_qr"),
    Web("备用网页", "login_mode_web"),
}

expect val supportedLoginMethods: List<LoginMethod>

@Composable
expect fun QrLoginPane(onLoginSuccess: (String) -> Unit)

@Composable
expect fun WebLoginPane(onLoginSuccess: (String) -> Unit)

/** 手机表单状态跨登录方式切换保留；onBack=null 表示根页面入口。 */
@Composable
fun LoginScreen(
    onLoginComplete: () -> Unit,
    onOpenTelemetrySettings: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val openExternalUrl = rememberExternalUrlOpener()
    val settings = rememberSettingsStore()
    val phoneLoginState = rememberPhoneLoginState()
    var showLogin by rememberSaveable { mutableStateOf(false) }
    var loggedInUsername by remember { mutableStateOf<String?>(null) }

    if (!showLogin) {
        LoginWelcomeStage(
            onBack = onBack,
            onOpenTerms = { openExternalUrl(ZHIHU_TERMS_URL) },
            onOpenTelemetrySettings = onOpenTelemetrySettings,
            onOpenOfficialApp = { openExternalUrl(ZHIHU_OFFICIAL_APP_URL) },
            onConfirm = { showLogin = true },
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
    ) {
        loggedInUsername?.let { username ->
            AppDialog(
                onDismissRequest = onLoginComplete,
                title = "登录成功",
                summary = "欢迎回来，$username",
                confirmText = "确定",
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
    phoneLoginState: PhoneLoginState,
    onLoginSuccess: (String) -> Unit,
    overlays: @Composable () -> Unit,
) {
    val methods = supportedLoginMethods
    var selectedMethod by rememberSaveable(methods) { mutableStateOf(methods.first()) }
    val titleBarState = rememberAppPageTitleBarScrollState()
    val background = appGroupedPageBackground()
    AppScaffold(
        modifier = Modifier.fillMaxSize().appPageTitleBarScroll(titleBarState),
        containerColor = background,
        topBar = {
            AppPageTitleBar(
                title = "知乎++登录",
                subtitle = "登录后同步收藏、关注与阅读记录",
                onBack = onBack,
                backTag = LOGIN_BACK_TAG,
                scrollState = titleBarState,
                colors = TopAppBarDefaults.topAppBarColors().copy(
                    containerColor = background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                actions = {
                    AppTextButton(
                        text = "先逛逛",
                        onClick = onContinueAsGuest,
                        modifier = Modifier.testTag("login_guest"),
                    )
                },
            )
        },
    ) { innerPadding ->
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
                        enabled = !phoneLoginState.isBusy,
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    when (selectedMethod) {
                        LoginMethod.Phone -> PhoneLoginPane(onLoginSuccess = onLoginSuccess, state = phoneLoginState)
                        LoginMethod.Qr -> QrLoginPane(onLoginSuccess)
                        LoginMethod.Web -> WebLoginPane(onLoginSuccess)
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
    val isDebuggable = rememberHomeIsDebuggable()
    val background = appGroupedPageBackground()

    AppScaffold(
        containerColor = background,
        topBar = {
            if (onBack != null) {
                AppCompactTopBar(
                    title = "",
                    onBack = onBack,
                    backTag = LOGIN_BACK_TAG,
                    containerColor = background,
                )
            }
        },
    ) { innerPadding ->
        /** 顶栏留白随内容滚动，供玻璃顶栏采样。 */
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
                Column(modifier = Modifier.padding(horizontal = AppDimens.PageHeaderM3eHorizontalPadding)) {
                    Spacer(Modifier.height(if (onBack == null) AppDimens.LoginHeroTopSpacing else AppDimens.Space16))
                    Text(
                        text = "开始之前",
                        modifier = if (isDebuggable) {
                            Modifier.pointerInput(Unit) {
                                detectTapGestures(
                                    onLongPress = {
                                        ThemeManager.setThemeStyle(
                                            if (ThemeManager.currentStyle == ThemeStyle.MIUIX) {
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
                    Spacer(Modifier.height(AppDimens.Space10))
                    Text(
                        text = "三件需要你确认的事。",
                        style = if (miuix) {
                            LocalAppSkinTokens.current.main
                        } else {
                            MaterialTheme.typography.titleMedium
                        },
                        color = accentColor(),
                    )
                    Spacer(Modifier.height(AppDimens.Space12))
                    Text(
                        text = "知乎++ 由开源社区维护，与知乎官方无隶属关系。请阅读并确认以下说明。",
                        style = if (miuix) {
                            LocalAppSkinTokens.current.footnote2
                        } else {
                            MaterialTheme.typography.bodyLarge
                        },
                        color = secondaryTextColor(),
                    )
                    Spacer(Modifier.height(AppDimens.LoginNoticeBodyTopSpacing))
                }

                SettingItemGroup {
                    loginNotices.forEachIndexed { index, notice ->
                        LoginNoticeRow(
                            index = index,
                            notice = notice,
                            expanded = expandedNoticeIndex == index,
                            onToggle = { onToggleNotice(index) },
                            onInlineAction = when (index) {
                                1 -> onOpenTerms
                                2 -> onOpenTelemetrySettings
                                else -> null
                            },
                        )
                    }
                }

                Column(modifier = Modifier.padding(horizontal = AppDimens.PageHeaderM3eHorizontalPadding)) {
                    AnimatedVisibility(
                        visible = !allNoticesRead,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        Text(
                            text = "逐项展开阅读后才能同意（已读 $readNoticeCount/${loginNotices.size}）",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = AppDimens.Space8),
                            style = if (miuix) {
                                LocalAppSkinTokens.current.footnote2
                            } else {
                                MaterialTheme.typography.labelMedium
                            },
                            color = secondaryTextColor(),
                        )
                    }

                    AnimatedVisibility(
                        visible = allNoticesRead,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(AppDimens.MenuCornerRadius))
                                .toggleable(value = agreed, role = Role.Checkbox, onValueChange = { agreed = it })
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
                                text = "我已阅读并同意以上说明",
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
                        modifier = Modifier.align(Alignment.CenterHorizontally),
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
                stateDescription = if (expanded) "已展开" else "已收起"
                if (expanded) {
                    collapse(action = toggle)
                } else {
                    expand(action = toggle)
                }
            }.animateContentSize(),
        title = { Text(notice.title) },
        description = { Text(notice.summary) },
        onClick = onToggle,
        endAction = {
            Icon(
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
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .widthIn(max = AppDimens.NoticeMaxWidth)
            .fillMaxWidth()
            .padding(top = AppDimens.Space8, bottom = AppDimens.Space4),
        verticalArrangement = Arrangement.spacedBy(AppDimens.Space4),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppButton(
            onClick = onConfirm,
            enabled = agreed,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = AppDimens.LoginMethodHeight)
                .testTag("login_notice_confirm"),
        ) {
            Text(
                text = "同意并继续",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        AppTextButton(
            text = "下载官方 App",
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
    val annotated = remember(message, linkStyle) {
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

private val loginNotices = listOf(
    LoginNotice(
        title = "关于本应用",
        summary = "由开源社区维护，与知乎官方无隶属关系。",
        message = "我清楚，本应用由开源社区开发和维护，不由知乎官方开发并运营，也不受到知乎官方的承认或支持，使用本应用的一切后果由我本人承担。我可以在 $ZHIHU_OFFICIAL_APP_URL 下载官方应用。",
    ),
    LoginNotice(
        title = "使用约定",
        summary = "遵守知乎协议，内容仅用于个人浏览与备份。",
        message = "在使用本应用的过程中，我承诺遵守知乎使用协议 $ZHIHU_TERMS_URL 。我保证在使用过程中不侵犯知乎及其他作者的著作权，使用本应用产生的一切输出仅用于个人浏览和备份，不会进行传播等其他影响作者著作权的行为。",
        inlineActionText = "查看协议",
    ),
    LoginNotice(
        title = "隐私与遥测",
        summary = "匿名使用统计可随时在设置中关闭。",
        message = "我知晓，本应用可能会收集部分匿名化的使用信息来确定使用人数，我可以在设置中随时关闭此项遥测。",
        inlineActionText = "查看设置",
    ),
)
