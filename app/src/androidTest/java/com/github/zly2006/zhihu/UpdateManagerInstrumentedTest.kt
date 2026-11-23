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

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.zly2006.zhihu.platform.androidSettingsStore
import com.github.zly2006.zhihu.updater.SemanticVersion
import com.github.zly2006.zhihu.updater.UpdateManager
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class UpdateManagerInstrumentedTest {
    @Test
    fun checksDoNotReplaceAnActiveDownloadOrItsLateCompletion() = runBlocking {
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val preferenceName = "update-owner-test-${UUID.randomUUID()}"
        val context = object : ContextWrapper(application) {
            override fun getApplicationContext(): Context = this

            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                application.getSharedPreferences(preferenceName, mode)
        }
        val settings = androidSettingsStore(context)
        settings.putString("githubToken", "synthetic-update-token")
        val requests = AtomicInteger()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val engine = MockEngine {
            requests.incrementAndGet()
            started.complete(Unit)
            release.await()
            respond(
                """{"tag_name":"v0.0.1","assets":[]}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val original = UpdateManager.updateState.value
        val update = UpdateManager.UpdateState.UpdateAvailable(
            version = SemanticVersion.fromString("999.0.0"),
            releaseNotes = null,
            downloadUrl = "https://example.invalid/update.apk",
            cnDownloadUrl = null,
        )
        try {
            for (state in listOf(UpdateManager.UpdateState.Downloading(update), UpdateManager.UpdateState.Downloaded(update))) {
                UpdateManager.updateState.value = state
                UpdateManager.autoCheckForUpdate(context, engine)
                UpdateManager.checkForUpdate(context, engine)
                assertSame(state, UpdateManager.updateState.value)
                assertEquals(0, requests.get())
            }
            UpdateManager.updateState.value = UpdateManager.UpdateState.NoUpdate
            val check = async { UpdateManager.checkForUpdate(context, engine) }
            try {
                withTimeout(5_000) { started.await() }
                val completed = UpdateManager.UpdateState.Downloaded(update)
                UpdateManager.updateState.value = completed
                release.complete(Unit)
                withTimeout(5_000) { check.await() }
                assertSame(completed, UpdateManager.updateState.value)
                assertEquals(1, requests.get())
            } finally {
                release.complete(Unit)
                check.cancel()
                check.join()
            }
        } finally {
            UpdateManager.updateState.value = original
            engine.close()
            context
                .getSharedPreferences(preferenceName, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()
        }
    }
}
