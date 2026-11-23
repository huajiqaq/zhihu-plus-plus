package com.github.zly2006.zhihu.notification

internal actual fun openAppleNotificationSettings(): Unit =
    throw UnsupportedOperationException("macOS 未提供公开的通知设置跳转，请前往系统设置 → 通知 → Zhihu++")
