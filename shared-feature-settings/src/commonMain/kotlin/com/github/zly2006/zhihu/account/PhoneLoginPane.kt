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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.DpSize
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.ui.components.AppButton
import com.github.zly2006.zhihu.ui.components.AppCheckbox
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppOutlinedButton
import com.github.zly2006.zhihu.ui.components.AppSpinner
import com.github.zly2006.zhihu.ui.components.AppTextField
import com.github.zly2006.zhihu.ui.components.settingSkinIsMiuix
import io.ktor.client.HttpClient
import kotlinx.coroutines.launch

@Composable
fun rememberLoginHttpClient(cookies: MutableMap<String, String>): HttpClient {
    val owner = rememberZhihuAccountStore()
    val accountClient = owner.client
    val httpClient = remember(owner, accountClient, cookies) {
        createAccountHttpClient(
            cookies,
            owner.session.userAgent,
            owner.engine,
            onFailure = { accountClient.reportHttpFailure(it, cookies) },
        )
    }
    DisposableEffect(httpClient) {
        onDispose(httpClient::close)
    }
    return httpClient
}

expect fun decodePhoneLoginCaptchaImage(content: String): ImageBitmap?

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhoneLoginPane(
    onLoginSuccess: (String) -> Unit,
    state: PhoneLoginState = rememberPhoneLoginState(),
) {
    val accountStore = rememberZhihuAccountStore()
    val uriHandler = LocalUriHandler.current

    /** 地区接口用独立访客 Cookie，避免网页 Cookie 混入移动端初始化。 */
    val countryHttpClient = rememberLoginHttpClient(remember { mutableMapOf<String, String>() })
    val scope = rememberCoroutineScope()
    val fieldShape = RoundedCornerShape(AppDimens.LoginFieldCornerRadius)
    var countryPickerOpen by remember(state) { mutableStateOf(false) }
    val verifyCaptcha: () -> Unit = {
        scope.launch { if (state.canVerifyCaptcha) state.verifyCaptchaAndRequestDigits() }
    }

    LaunchedEffect(state, countryPickerOpen) {
        if (countryPickerOpen && state.countryLoadError == null) state.loadCountries(countryHttpClient)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = AppDimens.Space8),
        verticalArrangement = Arrangement.spacedBy(AppDimens.Space8),
    ) {
        AppOutlinedButton(
            text = "国家或地区：${state.country.name} ${state.country.code} ▾",
            onClick = { countryPickerOpen = true },
            enabled = !state.isBusy,
            modifier = Modifier.fillMaxWidth().testTag("phone_login_country"),
        )
        PhoneLoginPanePhoneField(
            state = state,
            fieldShape = fieldShape,
        )

        if (state.captchaRequired) {
            CaptchaStep(
                state = state,
                fieldShape = fieldShape,
                onRefresh = { scope.launch { state.refreshCaptcha() } },
                onVerify = verifyCaptcha,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.Space12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppTextField(
                state = state.digits,
                inputTransformation = state.digitsTransformation,
                labelText = "短信验证码",
                enabled = !state.isBusy,
                shape = fieldShape,
                miuixCornerRadius = AppDimens.LoginFieldCornerRadius,
                miuixInsideMargin = DpSize(AppDimens.Space16, AppDimens.Space16),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("phone_login_digits"),
            )
            AppButton(
                onClick = { scope.launch { if (state.canSendDigits) state.requestDigits() } },
                enabled = state.canSendDigits,
                modifier = Modifier.testTag("phone_login_send_digits"),
            ) {
                if (state.isSendingDigits) {
                    AppSpinner(modifier = Modifier, size = AppDimens.InlineSpinnerSize)
                } else {
                    Text(
                        if (state.resendSeconds > 0) {
                            "${state.resendSeconds}s"
                        } else {
                            "发送验证码"
                        },
                    )
                }
            }
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .heightIn(min = AppDimens.TouchTarget)
                    .clip(RoundedCornerShape(AppDimens.MenuCornerRadius))
                    .toggleable(
                        value = state.agreementAccepted,
                        enabled = !state.isBusy,
                        role = Role.Checkbox,
                        onValueChange = { state.agreementAccepted = it },
                    ).testTag("phone_login_agreement"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppCheckbox(
                    checked = state.agreementAccepted,
                    onCheckedChange = null,
                    enabled = !state.isBusy,
                    modifier = Modifier.clearAndSetSemantics {},
                )
                Text(
                    text = "我已阅读并同意",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(end = AppDimens.Space4),
                )
            }
            AgreementLink(text = "《知乎协议》", onClick = { uriHandler.openUri(ZHIHU_TERMS_URL) })
            AgreementLink(text = "《个人信息保护指引》", onClick = { uriHandler.openUri(ZHIHU_PRIVACY_URL) })
        }

        AppButton(
            onClick = {
                scope.launch {
                    if (state.canSignIn) state.signIn(accountStore)?.let(onLoginSuccess)
                }
            },
            enabled = state.canSignIn,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("phone_login_submit"),
        ) {
            if (state.isLoggingIn) {
                AppSpinner(modifier = Modifier, size = AppDimens.InlineSpinnerSize)
            } else {
                Text("登录")
            }
        }

        state.errorMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .testTag("phone_login_error")
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }

        Text(
            text = "使用知乎官方登录协议；验证码形式由知乎风控实时决定。",
            style = if (settingSkinIsMiuix()) {
                LocalAppSkinTokens.current.footnote2
            } else {
                MaterialTheme.typography.bodySmall
            },
            color = if (settingSkinIsMiuix()) {
                LocalAppSkinTokens.current.onSurfaceVariantSummary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(top = AppDimens.Space4),
        )
    }

    if (countryPickerOpen) {
        PhoneLoginPaneCountryPicker(
            state = state,
            onDismiss = { countryPickerOpen = false },
            onRetry = { scope.launch { state.loadCountries(countryHttpClient) } },
            onSelectCountry = { country ->
                state.selectCountry(country)
                countryPickerOpen = false
            },
        )
    }
}
