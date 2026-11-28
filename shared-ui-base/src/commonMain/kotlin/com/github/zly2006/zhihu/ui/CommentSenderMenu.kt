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

package com.github.zly2006.zhihu.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.github.zly2006.zhihu.account.CommentSender
import com.github.zly2006.zhihu.account.ZhihuAccountProfileSnapshot
import com.github.zly2006.zhihu.account.ZhihuAccountStore
import com.github.zly2006.zhihu.i18n.CommentsStrings
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppDropdownMenu
import com.github.zly2006.zhihu.ui.components.AppDropdownMenuItem
import com.github.zly2006.zhihu.ui.components.AppIcon
import kotlinx.coroutines.CancellationException

@Composable
internal fun CommentSenderMenu(store: ZhihuAccountStore, selected: CommentSender?, enabled: Boolean, onSelect: (CommentSender?) -> Unit) {
    val accounts by store.accountsState.collectAsState()
    if (!accounts.session.login) return
    val owner = store.client
    val local = accounts.accounts.mapNotNull { account ->
        account.session.profile
            ?.takeIf { it.id.isNotBlank() && it.name.isNotBlank() }
            ?.let { CommentSender(account.id, it) }
    }
    val sources = local.map { it.sourceId to it.profile.id }
    val remote = remember(owner, sources) { mutableStateMapOf<String, List<CommentSender>>() }
    val failed = remember(owner, sources) { mutableStateMapOf<String, Boolean>() }
    var attempt by remember { mutableIntStateOf(0) }
    var expanded by remember(owner) { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    LaunchedEffect(expanded, owner, sources, attempt) {
        if (!expanded) return@LaunchedEffect
        loading = true
        try {
            for ((source, _) in sources) {
                if (source in remote || source in failed) continue
                try {
                    remote[source] = store.commentIdentities.list(owner, source)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    failed[source] = true
                }
            }
        } finally {
            loading = false
        }
    }
    val shown = selected?.profile ?: accounts.session.profile
    Box {
        Row(
            Modifier
                .sizeIn(minWidth = AppDimens.TouchTarget, minHeight = AppDimens.TouchTarget)
                .clickable(enabled = enabled, role = Role.Button) { expanded = true }
                .semantics { contentDescription = CommentsStrings.senderDescription(shown?.name.orEmpty()) }
                .testTag("comment_account_selector"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SenderAvatar(shown)
            AppIcon(Icons.Default.ArrowDropDown, null, Modifier.size(18.dp))
        }
        AppDropdownMenu(expanded, { expanded = false }, Modifier.testTag("comment_account_menu"), renderInRootScaffold = false) {
            Text(CommentsStrings.chooseSender, Modifier.padding(AppDimens.Space12).semantics { heading() }, style = LocalAppSkinTokens.current.footnote2)
            for (option in (local + sources.flatMap { remote[it.first].orEmpty() }).distinctBy { it.profile.id }) {
                AppDropdownMenuItem(
                    text = { Text(option.profile.name) },
                    leadingIcon = { SenderAvatar(option.profile) },
                    trailingIcon = { if (shown?.id == option.profile.id) AppIcon(Icons.Default.Check, CommentsStrings.currentSender) },
                    modifier = Modifier.testTag("comment_account_${option.profile.id}"),
                    onClick = {
                        expanded = false
                        onSelect(option.takeUnless { it.profile.id == accounts.session.profile?.id })
                    },
                )
            }
            if (loading) Text(CommentsStrings.loadingLinkedAccounts, Modifier.padding(AppDimens.Space12))
            if (!loading && failed.isNotEmpty()) {
                AppDropdownMenuItem(
                    text = { Text(CommentsStrings.linkedAccountsLoadFailed) },
                    onClick = {
                        failed.clear()
                        attempt++
                    },
                    modifier = Modifier.testTag("comment_account_retry"),
                )
            }
        }
    }
}

@Composable
private fun SenderAvatar(profile: ZhihuAccountProfileSnapshot?) {
    val modifier = Modifier.size(24.dp).clip(CircleShape)
    if (profile?.avatarUrl.isNullOrBlank()) {
        AppIcon(Icons.Default.Person, null, modifier)
    } else {
        AsyncImage(profile.avatarUrl, null, modifier, contentScale = ContentScale.Crop)
    }
}
