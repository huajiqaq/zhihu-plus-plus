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

package com.github.zly2006.zhihu

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.ComponentName
import android.os.Bundle
import android.os.Process
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

internal class FatalErrorHandler(
    private val application: Application,
) : Thread.UncaughtExceptionHandler,
    Application.ActivityLifecycleCallbacks {
    private val activities = CopyOnWriteArraySet<Activity>()
    private val handling = AtomicBoolean()

    override fun uncaughtException(thread: Thread, error: Throwable) {
        if (!handling.compareAndSet(false, true)) return
        try {
            replaceActivitiesWithFatalError(application, error, activities)
        } finally {
            try {
                Process.killProcess(Process.myPid())
            } finally {
                exitProcess(10)
            }
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        activities.add(activity)
    }

    override fun onActivityDestroyed(activity: Activity) {
        activities.remove(activity)
    }

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivityResumed(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}

/** 先投递错误页以保留前台启动资格；外部任务只结束嵌入其中的本应用 Activity。 */
internal fun replaceActivitiesWithFatalError(application: Application, error: Throwable, activities: Iterable<Activity>) {
    val previousTasks = runCatching {
        application.getSystemService(ActivityManager::class.java)?.appTasks.orEmpty()
    }.getOrDefault(emptyList())
    val errorPage = ComponentName(application, FatalErrorActivity::class.java)
    try {
        application.startActivity(FatalErrorActivity.createIntent(application, error))
    } finally {
        previousTasks.forEach { task ->
            runCatching {
                val info = task.taskInfo ?: return@runCatching
                val base = info.baseIntent.component
                val root = info.baseActivity ?: base ?: return@runCatching
                if (root.packageName == application.packageName &&
                    root != errorPage &&
                    (base == null || base.packageName == application.packageName && base != errorPage)
                ) {
                    task.finishAndRemoveTask()
                }
            }
        }
        activities.forEach { activity -> runCatching { activity.finish() } }
    }
}
