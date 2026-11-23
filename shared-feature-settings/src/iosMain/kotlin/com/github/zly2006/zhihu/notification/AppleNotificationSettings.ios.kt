@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.github.zly2006.zhihu.notification

import platform.Foundation.NSThread
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString

internal actual fun openAppleNotificationSettings() {
    check(NSThread.isMainThread)
    val application = UIApplication.sharedApplication
    val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: error("系统设置入口不可用")
    check(application.canOpenURL(url)) { "无法打开应用的系统设置" }
    application.openURL(url, emptyMap<Any?, Any>(), null)
}
