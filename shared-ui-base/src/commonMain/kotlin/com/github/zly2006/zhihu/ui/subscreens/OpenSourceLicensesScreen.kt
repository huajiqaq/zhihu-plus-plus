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

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import com.github.zly2006.zhihu.i18n.ReaderStrings
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.ui.ReaderDisplayFailure
import com.github.zly2006.zhihu.ui.components.AppAlertDialog
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppEmptyState
import com.github.zly2006.zhihu.ui.components.AppLoadStateContent
import com.github.zly2006.zhihu.ui.components.AppPageTitleBar
import com.github.zly2006.zhihu.ui.components.AppScaffold
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.ui.components.SettingItem
import com.github.zly2006.zhihu.ui.components.SettingItemGroup
import com.github.zly2006.zhihu.ui.components.SettingsContent
import com.github.zly2006.zhihu.ui.components.appGroupedPageBackground
import com.github.zly2006.zhihu.ui.components.appPageTitleBarScroll
import com.github.zly2006.zhihu.ui.components.rememberAppPageTitleBarScrollState
import com.github.zly2006.zhihu.viewmodel.LoadState
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

const val OPEN_SOURCE_LICENSES_BACK_BUTTON_TAG = "open_source_licenses_back_button"

internal const val LICENSE_MANIFEST_MISSING_MESSAGE = "没有找到构建时生成的许可证清单 aboutlibraries.json"

/** 构建清单缺失须报错，不能视为没有第三方许可证。 */
@Composable
expect fun rememberLicenseManifestReader(): suspend () -> String

/** 只展示当前产物的构建清单，平台和变体依赖由构建阶段收集。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenSourceLicensesScreen() {
    val navigator = LocalNavigator.current
    val uriHandler = LocalUriHandler.current
    val manifestReader = rememberLicenseManifestReader()
    val titleBarState = rememberAppPageTitleBarScrollState()
    var attempt by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<Result<Map<String, List<Library>>>?>(null) }
    var selected by remember { mutableStateOf<Library?>(null) }

    LaunchedEffect(manifestReader, attempt) {
        result = null
        result = try {
            val json = manifestReader()
            Result.success(
                withContext(Dispatchers.Default) {
                    /** aboutlibraries 解析失败也返回空列表，须区分损坏与真正空清单。 */
                    val declared = (Json.parseToJsonElement(json) as? JsonObject)?.get("libraries") as? JsonArray
                        ?: throw ReaderDisplayFailure("许可证清单格式不正确") { ReaderStrings.licenseManifestInvalid }
                    val libraries = Libs
                        .Builder()
                        .withJson(json)
                        .build()
                        .libraries
                    if (declared.isNotEmpty() && libraries.isEmpty()) {
                        throw ReaderDisplayFailure("许可证清单解析失败") { ReaderStrings.licenseManifestParseFailed }
                    }
                    libraries
                        .sortedBy { it.name.lowercase() }
                        .groupBy { library ->
                            library.name
                                .firstOrNull()
                                ?.uppercaseChar()
                                ?.takeIf { it in 'A'..'Z' }
                                ?.toString() ?: "#"
                        }
                },
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    AppScaffold(
        modifier = Modifier.fillMaxSize().appPageTitleBarScroll(titleBarState),
        containerColor = appGroupedPageBackground(),
        topBar = {
            AppPageTitleBar(
                title = ReaderStrings.openSourceLicenses,
                onBack = navigator.onNavigateBack,
                backTag = OPEN_SOURCE_LICENSES_BACK_BUTTON_TAG,
                scrollState = titleBarState,
                colors = TopAppBarDefaults.topAppBarColors().copy(
                    containerColor = appGroupedPageBackground(),
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            )
        },
    ) { innerPadding ->
        AppLoadStateContent(
            state = result?.fold(
                onSuccess = { LoadState.Ready(it) },
                onFailure = { error ->
                    LoadState.Failed(
                        if (error is ReaderDisplayFailure) error.displayMessage() else error.message ?: ReaderStrings.licenseManifestReadFailed,
                    )
                },
            ) ?: LoadState.Loading,
            onRetry = { attempt++ },
            modifier = Modifier.padding(innerPadding),
            failedTitle = ReaderStrings.licenseLoadFailed,
            isEmpty = { groups -> groups.isEmpty() },
            empty = { AppEmptyState(title = ReaderStrings.noLicenses, description = ReaderStrings.noLicensesDescription) },
        ) { groups ->
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = innerPadding) {
                item(key = "top") { Spacer(Modifier.height(AppDimens.Space16)) }
                groups.forEach { (initial, libraries) ->
                    item(key = "group:$initial") {
                        SettingsContent {
                            SettingItemGroup(title = initial) {
                                libraries.forEach { library ->
                                    LibraryLicenseRow(library, onOpen = { selected = library }, onOpenUri = uriHandler::openUri)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selected?.let { library ->
        val website = library.website ?: library.scm?.url
        AppAlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(library.name) },
            text = { Text(library.licenses.mapNotNull { it.licenseContent?.takeIf(String::isNotBlank) }.joinToString("\n\n")) },
            confirmButton = {
                if (website != null) {
                    AppTextButton(
                        text = ReaderStrings.openProjectHomepage,
                        onClick = {
                            selected = null
                            uriHandler.openUri(website)
                        },
                    )
                } else {
                    AppTextButton(text = ReaderStrings.close, onClick = { selected = null })
                }
            },
            dismissButton = if (website != null) {
                @Composable { AppTextButton(text = ReaderStrings.close, onClick = { selected = null }) }
            } else {
                null
            },
        )
    }
}

@Composable
private fun LibraryLicenseRow(
    library: Library,
    onOpen: () -> Unit,
    onOpenUri: (String) -> Unit,
) {
    val website = library.website ?: library.scm?.url
    val hasLicenseText = library.licenses.any { !it.licenseContent.isNullOrBlank() }
    val summary = listOfNotNull(
        library.artifactVersion?.takeIf(String::isNotBlank),
        library.licenses.joinToString(" / ") { it.spdxId ?: it.name }.takeIf(String::isNotBlank),
    ).joinToString(" · ")
    SettingItem(
        title = { Text(library.name) },
        description = if (summary.isEmpty()) {
            null
        } else {
            @Composable { Text(summary) }
        },
        onClick = when {
            hasLicenseText -> onOpen
            website != null -> {
                { onOpenUri(website) }
            }
            else -> null
        },
    )
}
