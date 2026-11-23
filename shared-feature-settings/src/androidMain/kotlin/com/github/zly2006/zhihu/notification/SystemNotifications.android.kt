package com.github.zly2006.zhihu.notification

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.github.zly2006.zhihu.account.androidZhihuAccountStore

@Composable
actual fun rememberSystemNotificationControl(): SystemNotificationControl {
    val context = LocalContext.current
    val runtime = remember(context) { androidSystemNotifications(context) }
    val accountClient = androidZhihuAccountStore(context).client
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        runtime.refresh()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { runtime.refresh() }
    return remember(context, runtime, permission, accountClient) {
        object : SystemNotificationControl {
            override val status = runtime.status

            override fun setTypeEnabled(type: NotificationType, enabled: Boolean) {
                if (runtime.setTypeEnabled(type, enabled, accountClient) &&
                    enabled &&
                    Build.VERSION.SDK_INT >= 33 &&
                    !runtime.status.value.permissionGranted
                ) {
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            override fun openSystemSettings() {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }
}
