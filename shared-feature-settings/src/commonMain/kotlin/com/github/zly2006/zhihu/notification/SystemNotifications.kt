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

package com.github.zly2006.zhihu.notification

import androidx.compose.runtime.Composable
import com.github.zly2006.zhihu.i18n.NotificationsStrings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class SystemNotificationStatus(
    val supported: Boolean = false,
    val signedIn: Boolean = false,
    val permissionGranted: Boolean = false,
    val appNotificationsEnabled: Boolean = false,
    val enabledTypes: Set<NotificationType> = emptySet(),
    val blockedChannels: Set<NotificationType> = emptySet(),
    private val errorText: (() -> String)? = null,
    /** 不具备后台检查仍可支持应用运行期间的系统通知。 */
    val backgroundPollingAvailable: Boolean = false,
    /** 平台不可用原因或投递限制，不能据此推断权限、后台能力或冷启动支持。 */
    private val availabilityText: (() -> String?)? = null,
) {
    val error: String? get() = errorText?.invoke()
    val availabilityDescription: String? get() = availabilityText?.invoke()
}

/** 系统订阅是本机授权，不从可备份的应用内通知偏好恢复。 */
interface SystemNotificationControl {
    val status: StateFlow<SystemNotificationStatus>

    /** 只能由用户操作调用；Android 在首次显式启用时才可请求通知权限。 */
    fun setTypeEnabled(type: NotificationType, enabled: Boolean)

    fun openSystemSettings()
}

@Composable
expect fun rememberSystemNotificationControl(): SystemNotificationControl

internal object UnsupportedSystemNotifications : SystemNotificationControl {
    override val status: StateFlow<SystemNotificationStatus> = MutableStateFlow(SystemNotificationStatus())

    override fun setTypeEnabled(type: NotificationType, enabled: Boolean): Nothing =
        throw SystemNotificationUnavailableException { NotificationsStrings.systemUnsupported }

    override fun openSystemSettings(): Nothing =
        throw SystemNotificationUnavailableException { NotificationsStrings.systemUnsupported }
}

/** 保留平台不可用原因，在当前语言下展示，而不重新申请通知租约。 */
internal class SystemNotificationUnavailableException(
    private val text: () -> String,
) : UnsupportedOperationException() {
    override val message: String get() = text()
}
