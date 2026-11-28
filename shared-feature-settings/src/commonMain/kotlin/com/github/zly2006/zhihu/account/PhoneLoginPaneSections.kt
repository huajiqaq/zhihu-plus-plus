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

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.DpSize
import com.github.zly2006.zhihu.i18n.AccountStrings
import com.github.zly2006.zhihu.ui.components.AppAlertDialog
import com.github.zly2006.zhihu.ui.components.AppButton
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppOutlinedButton
import com.github.zly2006.zhihu.ui.components.AppSpinner
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.ui.components.AppTextField

@Composable
internal fun PhoneLoginPanePhoneField(
    state: PhoneLoginState,
    fieldShape: RoundedCornerShape,
) {
    AppTextField(
        state = state.phoneNumber,
        inputTransformation = state.phoneNumberTransformation,
        labelText = AccountStrings.phoneNumber,
        enabled = !state.isBusy,
        shape = fieldShape,
        miuixCornerRadius = AppDimens.LoginFieldCornerRadius,
        miuixInsideMargin = DpSize(AppDimens.Space16, AppDimens.Space16),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Phone,
            imeAction = ImeAction.Next,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("phone_login_phone"),
    )
    val isMainland = state.country.code
        .replace(" ", "")
        .replace("-", "") == "+86"
    if (state.phoneNumber.text.isNotEmpty() &&
        !state.isPhoneNumberValid &&
        (!isMainland || state.phoneNumber.text.length >= MAINLAND_PHONE_NUMBER_LENGTH)
    ) {
        Text(
            text = if (isMainland) INVALID_PHONE_NUMBER_MESSAGE else INVALID_INTERNATIONAL_PHONE_NUMBER_MESSAGE,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .padding(horizontal = AppDimens.Space16)
                .testTag("phone_login_phone_error")
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Composable
internal fun PhoneLoginPaneCountryPicker(
    state: PhoneLoginState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onSelectCountry: (PhoneLoginCountry) -> Unit,
) {
    val countryQuery = remember(state) { TextFieldState() }
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(AccountStrings.countryPickerTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppDimens.Space8)) {
                when {
                    state.isLoadingCountries || (state.countries == null && state.countryLoadError == null) -> {
                        AppSpinner(size = AppDimens.InlineSpinnerSize)
                        Text(AccountStrings.countriesLoading)
                    }

                    state.countryLoadError != null -> {
                        Text(state.countryLoadError.orEmpty(), color = MaterialTheme.colorScheme.error)
                        AppTextButton(
                            text = AccountStrings.retry,
                            onClick = onRetry,
                            modifier = Modifier.testTag("phone_login_country_retry"),
                        )
                    }

                    else -> {
                        AppTextField(
                            state = countryQuery,
                            labelText = AccountStrings.countrySearch,
                            modifier = Modifier.fillMaxWidth().testTag("phone_login_country_search"),
                        )
                        val query = countryQuery.text.toString().trim()
                        val filtered = state.countries.orEmpty().filter {
                            it.displayName.contains(query, ignoreCase = true) ||
                                it.abbr.contains(query, ignoreCase = true) ||
                                it.code.contains(query, ignoreCase = true)
                        }
                        if (filtered.isEmpty()) {
                            Text(AccountStrings.countriesEmpty)
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = AppDimens.Space240)) {
                                items(filtered) { country ->
                                    AppTextButton(
                                        onClick = {
                                            onSelectCountry(country)
                                        },
                                        modifier = Modifier.fillMaxWidth().testTag("phone_login_country_${country.abbr}"),
                                    ) {
                                        Text(
                                            text = "${country.displayName} ${country.code}${if (country == state.country) " ✓" else ""}",
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { AppTextButton(text = AccountStrings.close, onClick = onDismiss) },
    )
}

@Composable
internal fun CaptchaStep(
    state: PhoneLoginState,
    fieldShape: RoundedCornerShape,
    onRefresh: () -> Unit,
    onVerify: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppDimens.Space8)) {
        val imageBase64 = state.captchaImageBase64
        val captchaBitmap = remember(imageBase64) {
            imageBase64?.let(::decodePhoneLoginCaptchaImage)
        }
        if (captchaBitmap != null) {
            Image(
                bitmap = captchaBitmap,
                contentDescription = AccountStrings.captcha,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(AppDimens.LargeInputHeight)
                    .testTag("phone_login_captcha_image"),
            )
        } else {
            CaptchaUnavailable(
                reason = if (imageBase64 == null) {
                    AccountStrings.captchaMissing
                } else {
                    AccountStrings.captchaInvalidImage
                },
                retryEnabled = state.canRefreshCaptcha,
                onRetry = onRefresh,
            )
        }
        AppTextField(
            state = state.captchaInput,
            labelText = AccountStrings.captcha,
            enabled = !state.isBusy,
            shape = fieldShape,
            miuixCornerRadius = AppDimens.LoginFieldCornerRadius,
            miuixInsideMargin = DpSize(AppDimens.Space16, AppDimens.Space16),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            onKeyboardAction = { if (state.canVerifyCaptcha) onVerify() },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("phone_login_captcha_input"),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.Space8),
        ) {
            AppOutlinedButton(
                onClick = onRefresh,
                enabled = state.canRefreshCaptcha,
                modifier = Modifier
                    .weight(1f)
                    .testTag("phone_login_refresh_captcha"),
            ) {
                Text(AccountStrings.captchaChange)
            }
            AppButton(
                text = AccountStrings.captchaVerifySend,
                onClick = onVerify,
                modifier = Modifier
                    .weight(1f)
                    .testTag("phone_login_verify_captcha"),
                enabled = state.canVerifyCaptcha,
            )
        }
    }
}

@Composable
private fun CaptchaUnavailable(
    reason: String,
    retryEnabled: Boolean,
    onRetry: () -> Unit,
) {
    val shape = RoundedCornerShape(AppDimens.LoginFieldCornerRadius)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AppDimens.LargeInputHeight)
            .border(AppDimens.DividerThickness, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(horizontal = AppDimens.Space16, vertical = AppDimens.Space8)
            .testTag("phone_login_captcha_unavailable"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = reason,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        AppTextButton(
            text = AccountStrings.fetchAgain,
            onClick = onRetry,
            enabled = retryEnabled,
        )
    }
}

@Composable
internal fun AgreementLink(
    text: String,
    onClick: () -> Unit,
) {
    AppTextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = AppDimens.Space4, vertical = AppDimens.Space8),
    ) {
        Text(text = text, style = MaterialTheme.typography.bodySmall)
    }
}
