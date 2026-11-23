package com.github.zly2006.zhihu.notification

import androidx.compose.runtime.Composable

@Composable
actual fun rememberSystemNotificationControl(): SystemNotificationControl = rememberRunningSystemNotificationControl()
