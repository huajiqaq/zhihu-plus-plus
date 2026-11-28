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

@file:OptIn(kotlinx.cinterop.BetaInteropApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)

package com.github.zly2006.zhihu.notification

import androidx.compose.runtime.Composable
import com.github.zly2006.zhihu.account.ZhihuAccountStore
import com.github.zly2006.zhihu.i18n.NotificationsStrings
import com.github.zly2006.zhihu.navigation.NavDestination
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.Foundation.NSBundle
import platform.Foundation.NSThread
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecSuccess
import platform.Security.kSecRandomDefault
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusEphemeral
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationDefaultActionIdentifier
import platform.UserNotifications.UNNotificationPresentationOptionBanner
import platform.UserNotifications.UNNotificationPresentationOptionList
import platform.UserNotifications.UNNotificationPresentationOptionSound
import platform.UserNotifications.UNNotificationPresentationOptions
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationResponse
import platform.UserNotifications.UNNotificationSettingEnabled
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

private const val RECEIPT_PREFIX = "zhihu-local-v1-"

/** 必须在应用完成启动前于主线程调用；只安装长期持有的 delegate，不请求权限或启动网络任务。 */
fun installAppleSystemNotificationDelegate(): Boolean = AppleNotificationCenter.install()

/** 须先安装早期 delegate；复用进程内检查，退出或 iOS 挂起后不保证运行，不是 APNs。 */
@Composable
fun AppleSystemNotificationHost(
    accountStore: ZhihuAccountStore,
    onOpen: (NavDestination) -> Unit,
    content: @Composable () -> Unit,
) {
    RunningSystemNotificationHost(
        accounts = accountStore,
        adapter = AppleSystemNotificationAdapter,
        onOpen = {
            check(NSThread.isMainThread)
            onOpen(it)
        },
        content = content,
    )
}

internal expect fun openAppleNotificationSettings()

private object AppleSystemNotificationAdapter : RunningSystemNotificationAdapter {
    override val availabilityDescription: String get() = NotificationsStrings.appleAvailability
    private val storage = AppleNotificationStorage()

    override fun activate(onClick: (String) -> Unit): AutoCloseable? = AppleNotificationCenter.activate(onClick)

    override fun loadState(): DeviceNotificationState = storage.load()

    override fun saveState(state: DeviceNotificationState) = storage.save(state)

    override fun newReceipt(): String {
        val bytes = ByteArray(32)
        bytes.usePinned {
            check(SecRandomCopyBytes(kSecRandomDefault, bytes.size.toULong(), it.addressOf(0)) == errSecSuccess)
        }
        return RECEIPT_PREFIX + bytes.toHexString()
    }

    override suspend fun authorization(): RunningSystemNotificationAuthorization = withContext(Dispatchers.Main.immediate) {
        suspendCancellableCoroutine { continuation ->
            AppleNotificationCenter.requireCenter().getNotificationSettingsWithCompletionHandler { settings ->
                if (settings == null) {
                    continuation.resume(RunningSystemNotificationAuthorization(false, false, supported = false))
                } else {
                    val granted = settings.authorizationStatus in setOf(
                        UNAuthorizationStatusAuthorized,
                        UNAuthorizationStatusProvisional,
                        UNAuthorizationStatusEphemeral,
                    )
                    continuation.resume(
                        RunningSystemNotificationAuthorization(
                            permissionGranted = granted,
                            appNotificationsEnabled = granted &&
                                (
                                    settings.alertSetting == UNNotificationSettingEnabled ||
                                        settings.notificationCenterSetting == UNNotificationSettingEnabled ||
                                        settings.lockScreenSetting == UNNotificationSettingEnabled
                                ),
                        ),
                    )
                }
            }
        }
    }

    override suspend fun requestAuthorization(): Unit = withContext(Dispatchers.Main.immediate) {
        suspendCancellableCoroutine { continuation ->
            AppleNotificationCenter.requireCenter().requestAuthorizationWithOptions(
                UNAuthorizationOptionAlert or UNAuthorizationOptionSound,
            ) { _, error ->
                if (error == null) {
                    continuation.resume(Unit)
                } else {
                    continuation.resumeWithException(IllegalStateException(NotificationsStrings.applePermissionError(error.code)))
                }
            }
        }
    }

    override fun publish(
        accountId: String,
        event: SystemNotificationEvent,
        receipt: String,
        onComplete: (Throwable?) -> Unit,
    ) {
        require(isAppleNotificationReceipt(receipt))
        val content = UNMutableNotificationContent().apply {
            setTitle(NotificationsStrings.appName)
            setBody(NotificationsStrings.appleNewMessage)
            setSound(UNNotificationSound.defaultSound)
        }
        val request = UNNotificationRequest.requestWithIdentifier(receipt, content, null)
        AppleNotificationCenter.requireCenter().addNotificationRequest(request) { error ->
            /** completion 只表示系统接受请求，不能证明已经展示或送达。 */
            dispatch_async(dispatch_get_main_queue()) {
                onComplete(error?.let { IllegalStateException(NotificationsStrings.appleDeliveryError(it.code)) })
            }
        }
    }

    override fun withdraw(accountId: String, eventKey: String, receipt: String) {
        require(isAppleNotificationReceipt(receipt))
        AppleNotificationCenter.requireCenter().apply {
            removePendingNotificationRequestsWithIdentifiers(listOf(receipt))
            removeDeliveredNotificationsWithIdentifiers(listOf(receipt))
        }
    }

    override fun openSystemSettings() = openAppleNotificationSettings()
}

private fun isAppleNotificationReceipt(receipt: String): Boolean =
    receipt.startsWith(RECEIPT_PREFIX) &&
        receipt.length == RECEIPT_PREFIX.length + 64 &&
        receipt.drop(RECEIPT_PREFIX.length).all { it in '0'..'9' || it in 'a'..'f' }

/** UNUserNotificationCenter 弱持有 delegate；进程级对象保留它以及 UI 尚未就绪的冷启动点击。 */
private object AppleNotificationCenter {
    private val delegate = AppleNotificationDelegate()
    private var center: UNUserNotificationCenter? = null
    private var onClick: ((String) -> Unit)? = null
    private val pendingClicks = linkedSetOf<String>()

    fun install(): Boolean {
        if (!NSThread.isMainThread) return false
        val bundle = NSBundle.mainBundle
        if (!bundle.bundlePath.endsWith(".app", ignoreCase = true) ||
            bundle.bundleIdentifier.isNullOrBlank() ||
            bundle.objectForInfoDictionaryKey("CFBundlePackageType") != "APPL"
        ) {
            return false
        }
        val candidate = center ?: UNUserNotificationCenter.currentNotificationCenter()
        if (candidate.delegate != null && candidate.delegate !== delegate) return false
        candidate.delegate = delegate
        center = candidate
        return true
    }

    fun requireCenter(): UNUserNotificationCenter {
        check(NSThread.isMainThread)
        return checkNotNull(center?.takeIf { it.delegate === delegate }) { NotificationsStrings.appleUnavailable }
    }

    fun activate(listener: (String) -> Unit): AutoCloseable? {
        check(NSThread.isMainThread)
        if (center?.delegate !== delegate || onClick != null) return null
        onClick = listener
        /** activate 返回后公共运行时才准备好会话和本机回执。 */
        dispatch_async(dispatch_get_main_queue()) { drainClicks() }
        return AutoCloseable {
            check(NSThread.isMainThread)
            if (onClick === listener) onClick = null
        }
    }

    fun receive(receipt: String) {
        check(NSThread.isMainThread)
        pendingClicks += receipt
        drainClicks()
    }

    private fun drainClicks() {
        val listener = onClick ?: return
        val clicks = pendingClicks.toList()
        pendingClicks.clear()
        clicks.forEach(listener)
    }
}

private class AppleNotificationDelegate :
    NSObject(),
    UNUserNotificationCenterDelegateProtocol {
    @ObjCSignatureOverride
    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        willPresentNotification: UNNotification,
        withCompletionHandler: (UNNotificationPresentationOptions) -> Unit,
    ) {
        val request = willPresentNotification.request
        withCompletionHandler(
            if (request.trigger == null && isAppleNotificationReceipt(request.identifier)) {
                UNNotificationPresentationOptionBanner or UNNotificationPresentationOptionList or UNNotificationPresentationOptionSound
            } else {
                0uL
            },
        )
    }

    @ObjCSignatureOverride
    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        didReceiveNotificationResponse: UNNotificationResponse,
        withCompletionHandler: () -> Unit,
    ) {
        try {
            val request = didReceiveNotificationResponse.notification.request
            if (didReceiveNotificationResponse.actionIdentifier == UNNotificationDefaultActionIdentifier &&
                request.trigger == null &&
                isAppleNotificationReceipt(request.identifier)
            ) {
                dispatch_async(dispatch_get_main_queue()) { AppleNotificationCenter.receive(request.identifier) }
            }
        } finally {
            withCompletionHandler()
        }
    }
}
