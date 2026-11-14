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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Memory
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
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.platform.includesFullVariantLicenses
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
import com.github.zly2006.zhihu.ui.components.AppIcon as Icon

const val OPEN_SOURCE_LICENSES_BACK_BUTTON_TAG = "open_source_licenses_back_button"

internal const val LICENSE_MANIFEST_MISSING_MESSAGE = "没有找到构建时生成的许可证清单 aboutlibraries.json"

data class ManualLicenseEntry(
    val name: String,
    val license: String,
    val summary: String,
    val url: String,
    val icon: @Composable () -> Unit,
)

val fullVariantManualLibraries = listOf(
    ManualLicenseEntry(
        name = "Sentence-Embeddings-Android",
        license = "Apache-2.0",
        summary = "用于执行句子嵌入。",
        url = "https://github.com/shubham0204/Sentence-Embeddings-Android",
        icon = { Icon(Icons.Default.Memory, contentDescription = null) },
    ),
    ManualLicenseEntry(
        name = "huggingface/tokenizers 0.22.2",
        license = "Apache-2.0",
        summary = "huggingface tokenizer",
        url = "https://github.com/huggingface/tokenizers",
        icon = { Icon(Icons.Default.DataObject, contentDescription = null) },
    ),
    ManualLicenseEntry(
        name = "jni 0.21.1",
        license = "MIT/Apache-2.0",
        summary = "Rust JNI 绑定。",
        url = "https://github.com/jni-rs/jni-rs",
        icon = { Icon(Icons.Default.DataObject, contentDescription = null) },
    ),
    ManualLicenseEntry(
        name = "bytes 1.11.1",
        license = "MIT",
        summary = "Rust bytes 缓冲区库。",
        url = "https://github.com/tokio-rs/bytes",
        icon = { Icon(Icons.Default.DataObject, contentDescription = null) },
    ),
    ManualLicenseEntry(
        name = "serde 1.0.228",
        license = "MIT OR Apache-2.0",
        summary = "Rust 序列化框架。",
        url = "https://github.com/serde-rs/serde",
        icon = { Icon(Icons.Default.DataObject, contentDescription = null) },
    ),
    ManualLicenseEntry(
        name = "serde_json 1.0.149",
        license = "MIT OR Apache-2.0",
        summary = "Rust JSON 序列化实现。",
        url = "https://github.com/serde-rs/json",
        icon = { Icon(Icons.Default.DataObject, contentDescription = null) },
    ),
)

/** 构建清单缺失须报错，不能视为没有第三方许可证。 */
@Composable
expect fun rememberLicenseManifestReader(): suspend () -> String

/** 构建清单未收录的 Full 专属组件仍需列出许可证。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenSourceLicensesScreen() {
    val navigator = LocalNavigator.current
    val uriHandler = LocalUriHandler.current
    val manifestReader = rememberLicenseManifestReader()
    val manualLibraries = if (includesFullVariantLicenses) fullVariantManualLibraries else emptyList()
    val titleBarState = rememberAppPageTitleBarScrollState()
    var attempt by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<LoadState<Map<String, List<Library>>>>(LoadState.Loading) }
    var selected by remember { mutableStateOf<Library?>(null) }

    LaunchedEffect(manifestReader, attempt) {
        state = LoadState.Loading
        state = try {
            val json = manifestReader()
            LoadState.Ready(
                withContext(Dispatchers.Default) {
                    /** aboutlibraries 解析失败也返回空列表，须区分损坏与真正空清单。 */
                    val declared = (Json.parseToJsonElement(json) as? JsonObject)?.get("libraries") as? JsonArray
                        ?: error("许可证清单格式不正确")
                    val libraries = Libs
                        .Builder()
                        .withJson(json)
                        .build()
                        .libraries
                    check(declared.isEmpty() || libraries.isNotEmpty()) { "许可证清单解析失败" }
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
            LoadState.Failed(error.message ?: "许可证清单读取失败")
        }
    }

    AppScaffold(
        modifier = Modifier.fillMaxSize().appPageTitleBarScroll(titleBarState),
        containerColor = appGroupedPageBackground(),
        topBar = {
            AppPageTitleBar(
                title = "开源许可",
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
            state = state,
            onRetry = { attempt++ },
            modifier = Modifier.padding(innerPadding),
            failedTitle = "许可证清单加载失败",
            isEmpty = { groups -> groups.isEmpty() && manualLibraries.isEmpty() },
            empty = { AppEmptyState(title = "没有许可证条目", description = "这个构建的许可证清单里没有第三方组件。") },
        ) { groups ->
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = innerPadding) {
                item(key = "top") { Spacer(Modifier.height(AppDimens.Space16)) }
                if (manualLibraries.isNotEmpty()) {
                    item(key = "group:manual") {
                        SettingsContent {
                            SettingItemGroup(title = "Full 版本特有组件") {
                                manualLibraries.forEach { entry ->
                                    SettingItem(
                                        title = { Text(entry.name) },
                                        /** 长许可名独占一行，避免挤压摘要。 */
                                        description = {
                                            Column {
                                                Text(entry.license)
                                                Text(entry.summary)
                                            }
                                        },
                                        icon = entry.icon,
                                        onClick = { uriHandler.openUri(entry.url) },
                                    )
                                }
                            }
                        }
                    }
                }
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
                        text = "打开项目主页",
                        onClick = {
                            selected = null
                            uriHandler.openUri(website)
                        },
                    )
                } else {
                    AppTextButton(text = "关闭", onClick = { selected = null })
                }
            },
            dismissButton = if (website != null) {
                @Composable { AppTextButton(text = "关闭", onClick = { selected = null }) }
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
