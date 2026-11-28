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

package com.github.zly2006.zhihu.updater

import com.github.zly2006.zhihu.platform.SettingValue
import com.github.zly2006.zhihu.platform.SettingsStore
import com.github.zly2006.zhihu.ui.ReaderDisplayFailure
import com.github.zly2006.zhihu.ui.subscreens.SystemUpdateState
import com.github.zly2006.zhihu.ui.subscreens.checkSystemReleaseUpdate
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest

class ReleaseUpdateTest {
    @Test
    fun selectsOnlyThePublishedPlatformArchitectureAndVariant() {
        val full = asset("zhihu++-full.apk")
        val lite = asset("zhihu++-lite.apk")
        val windows = asset("zhihu++-desktop-windows-x64.msi")
        val mac = asset("zhihu++-desktop-macos-arm64.app.zip")
        val iosLite = asset("ZhihuPlusPlusLite-ios-lite-arm64-unsigned.ipa")
        val release = GithubRelease(assets = listOf(full, lite, windows, mac, iosLite))
        assertEquals(lite.browserDownloadUrl, release.extractAndroidDownloadInfo(true).browserDownloadUrl)
        assertSame(windows, release.assetFor("Windows", "amd64", nativeHost = false))
        assertSame(mac, release.assetFor("macOS", "aarch64", nativeHost = true))
        assertSame(iosLite, release.assetFor("iOS", "arm64", nativeHost = true, lite = true))
        assertNull(release.assetFor("Windows", "arm64", nativeHost = false))
        assertNull(release.assetFor("iOS", "arm64", nativeHost = true, lite = false))
        assertNull(release.assetFor("iOS", "x64", nativeHost = true, lite = true))
        assertNull(release.assetFor("Linux", "unknown", nativeHost = false))
        assertFailsWith<ReaderDisplayFailure> { GithubRelease(assets = listOf(full)).extractAndroidDownloadInfo(true) }
        assertNull(GithubRelease(assets = listOf(windows.copy(browserDownloadUrl = "http://github.com/untrusted"))).assetFor("Windows", "x64", nativeHost = false))
        assertNull(GithubRelease(assets = listOf(windows.copy(browserDownloadUrl = mac.browserDownloadUrl))).assetFor("Windows", "x64", nativeHost = false))
    }

    @Test
    fun macosNativeReleaseCannotUpdateTheJvmHost() {
        val mac = asset("zhihu++-desktop-macos-arm64.app.zip")
        val release = GithubRelease(assets = listOf(mac))
        assertSame(mac, release.assetFor("macOS", "arm64", nativeHost = true))
        assertNull(release.assetFor("macOS", "arm64", nativeHost = false))
        assertNull(release.assetFor("macOS", "x64", nativeHost = true))
        val page = "https://github.com/zly2006/zhihu-plus-plus/releases/tag/0.32.1"
        assertEquals(page, release.copy(htmlUrl = page).officialReleasePageUrl())
        assertEquals("https://github.com/zly2006/zhihu-plus-plus/releases", release.copy(htmlUrl = "$page/../../other").officialReleasePageUrl())
    }

    @Test
    fun androidRetainsOfficialAssetMetadataAndRejectsUntrustedDownloads() {
        val apk = asset("zhihu++-lite.apk").copy(
            browserDownloadUrl = "https://github.com/zly2006/zhihu-plus-plus/releases/download/0.32.1/zhihu%2B%2B-lite.apk",
            size = 4_257_850,
            digest = "sha256:a90d2303759305eeda09274a478cedad46cd77ce45317e22d350575f5668e87e",
            cnDownloadUrl = "https://pan.quark.cn/s/62fe006c377b",
        )
        val selected = GithubRelease(assets = listOf(apk)).extractAndroidDownloadInfo(true)
        assertSame(apk, selected.asset)
        assertEquals(apk.browserDownloadUrl, selected.browserDownloadUrl)
        assertEquals(apk.cnDownloadUrl, selected.cnDownloadUrl)
        assertEquals(false, selected.opensExternally)
        val legacy = apk.copy(size = 0, digest = null)
        assertSame(legacy, GithubRelease(assets = listOf(legacy)).extractAndroidDownloadInfo(true).asset)
        for (invalid in listOf(
            apk.copy(browserDownloadUrl = "https://example.invalid/zhihu%2B%2B-lite.apk"),
            apk.copy(browserDownloadUrl = apk.cnDownloadUrl!!),
            apk.copy(browserDownloadUrl = apk.browserDownloadUrl.replace("zhihu%2B%2B-lite", "zhihu%2B%2B-full")),
            apk.copy(browserDownloadUrl = apk.browserDownloadUrl.replace("/0.32.1/", "/../")),
            apk.copy(size = MAX_UPDATE_DOWNLOAD_BYTES + 1),
            apk.copy(size = -1),
            apk.copy(digest = "sha256:invalid"),
        )) {
            assertFailsWith<ReaderDisplayFailure> { GithubRelease(assets = listOf(invalid)).extractAndroidDownloadInfo(true) }
        }
        apk.verifySha256(apk.digest!!.removePrefix("sha256:"))
        assertFailsWith<IllegalStateException> { apk.verifySha256("0".repeat(64)) }
    }

    @Test
    fun comparesVersionsNumericallyAndIgnoresBuildMetadata() {
        assertTrue(SemanticVersion.fromString("1.10.0") > SemanticVersion.fromString("1.9.99"))
        assertTrue(SemanticVersion.fromString("v2.0.0-rc.10") > SemanticVersion.fromString("2.0.0-rc.2"))
        assertTrue(SemanticVersion.fromString("2.0.0") > SemanticVersion.fromString("2.0.0-rc.99"))
        assertEquals(0, SemanticVersion.fromString("1.2+old").compareTo("1.2.0+new"))
    }

    @Test
    fun checkingAndCancellationCannotReplaceAnActiveDownload() = runTest {
        for (cancel in listOf(false, true)) {
            val started = CompletableDeferred<Unit>()
            val releaseResponse = CompletableDeferred<Unit>()
            var requests = 0
            val engine = MockEngine {
                requests++
                started.complete(Unit)
                releaseResponse.await()
                respond("""{"tag_name":"1.0.0","assets":[]}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }
            val update = SystemUpdateState.UpdateAvailable("2.0.0", false, null, "", null, false)
            val state = MutableStateFlow<SystemUpdateState>(SystemUpdateState.Downloading(update))
            val settings = UpdateTestSettingsStore()
            suspend fun check() = checkSystemReleaseUpdate(engine, state, "1.0.0", settings, selectAsset = { null })
            try {
                val active = state.value
                check()
                assertSame(active, state.value)
                assertEquals(0, requests)
                state.value = SystemUpdateState.NoUpdate
                val checking = async { check() }
                started.await()
                val completed = SystemUpdateState.Downloaded(update)
                state.value = completed
                if (cancel) checking.cancelAndJoin() else {
                    releaseResponse.complete(Unit)
                    checking.await()
                }
                assertSame(completed, state.value)
                assertEquals(1, requests)
            } finally {
                releaseResponse.complete(Unit)
                engine.close()
            }
        }
    }

    @Test
    fun autoCheckHonorsSkippedVersionStableChannelAndInterval() = runTest {
        val settings = UpdateTestSettingsStore().apply {
            putString("skippedVersion", "2.0.0")
            putBoolean("checkNightlyUpdates", true)
        }
        var requests = 0
        val engine = MockEngine { request ->
            assertEquals(ZHIHU_PLUS_PLUS_REDEN_LATEST_RELEASE_URL, request.url.toString())
            requests++
            respond("""{"tag_name":"v2.0.0","assets":[]}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val state = MutableStateFlow<SystemUpdateState>(SystemUpdateState.NoUpdate)
        suspend fun check() = checkSystemReleaseUpdate(engine, state, "1.0.0", settings, auto = true, selectAsset = { error("Skipped update must not select or download an asset") })
        try {
            check()
            assertSame(SystemUpdateState.NoUpdate, state.value)
            assertEquals(1, requests)
            assertTrue(settings.getLong("lastUpdateCheck", 0) > 0)
            check()
            assertEquals(1, requests)
            settings.putLong("lastUpdateCheck", Clock.System.now().toEpochMilliseconds() - SYSTEM_UPDATE_AUTO_CHECK_INTERVAL_MILLIS - 1)
            check()
            assertEquals(2, requests)
            settings.putString("githubToken", "synthetic-update-token")
            check()
            assertEquals(3, requests)
            val update = SystemUpdateState.UpdateAvailable("3.0.0", false, null, "", null, false)
            val active = SystemUpdateState.Downloading(update)
            state.value = active
            val lastCheck = settings.getLong("lastUpdateCheck", 0)
            check()
            assertSame(active, state.value)
            assertEquals(lastCheck, settings.getLong("lastUpdateCheck", 0))
            assertEquals(3, requests)
        } finally {
            engine.close()
        }
    }

    @Test
    fun manualRecheckIgnoresSkipAndIntervalButHonorsNightlyPreference() = runTest {
        val settings = UpdateTestSettingsStore().apply {
            putString("skippedVersion", "2.0.0")
            putLong("lastUpdateCheck", Clock.System.now().toEpochMilliseconds())
        }
        val requested = mutableListOf<String>()
        val engine = MockEngine { request ->
            requested += request.url.toString()
            val tag = if (request.url.toString() == ZHIHU_PLUS_PLUS_GITHUB_NIGHTLY_RELEASE_URL) "nightly" else "2.0.0"
            respond("""{"tag_name":"$tag","assets":[]}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val state = MutableStateFlow<SystemUpdateState>(SystemUpdateState.NoUpdate)
        suspend fun check() = checkSystemReleaseUpdate(engine, state, "1.0.0", settings, selectAsset = { asset("zhihu++-desktop-windows-x64.msi") })
        try {
            check()
            val stable = assertIs<SystemUpdateState.UpdateAvailable>(state.value)
            assertEquals("2.0.0", stable.version)
            assertEquals(false, stable.isNightly)
            assertEquals(listOf(ZHIHU_PLUS_PLUS_REDEN_LATEST_RELEASE_URL), requested)
            assertEquals("2.0.0", settings.getStringOrNull("skippedVersion"))
            state.value = SystemUpdateState.NoUpdate
            settings.putBoolean("checkNightlyUpdates", true)
            check()
            assertTrue(assertIs<SystemUpdateState.UpdateAvailable>(state.value).isNightly)
            assertEquals(ZHIHU_PLUS_PLUS_GITHUB_NIGHTLY_RELEASE_URL, requested.last())
            assertEquals(3, requested.size)
        } finally {
            engine.close()
        }
    }

    @Test
    fun downloadRejectsTruncationAndHtmlBeforeBecomingReady() = runTest {
        for ((body, type, size) in listOf(Triple("x", "application/octet-stream", 2L), Triple("xx", "text/html", 2L))) {
            val engine = MockEngine { respond(body, headers = headersOf(HttpHeaders.ContentType, type)) }
            val client = HttpClient(engine)
            try {
                assertFailsWith<IllegalStateException> { downloadReleaseAsset(client, asset("zhihu++-desktop-windows-x64.msi").copy(size = size)) { _, _ -> } }
            } finally {
                client.close()
                engine.close()
            }
        }
    }

    @Test
    fun legacyAndroidDownloadsStillValidateResponseLengths() = runTest {
        val apk = asset("zhihu++-lite.apk").copy(size = 0)
        for (length in listOf(null, "3", "2", "4", "0", "invalid", (MAX_UPDATE_DOWNLOAD_BYTES + 1).toString())) {
            val engine = MockEngine {
                respond("apk", headers = if (length == null) headersOf(HttpHeaders.ContentType, "application/octet-stream") else headersOf(HttpHeaders.ContentLength, length))
            }
            val client = HttpClient(engine)
            var written = 0
            try {
                if (length == null || length == "3") {
                    downloadReleaseAsset(client, apk) { _, count -> written += count }
                    assertEquals(3, written)
                } else {
                    assertFailsWith<IllegalStateException> { downloadReleaseAsset(client, apk) { _, count -> written += count } }
                    if (length == "0" || length == "invalid" || length == (MAX_UPDATE_DOWNLOAD_BYTES + 1).toString()) assertEquals(0, written)
                }
            } finally {
                client.close()
                engine.close()
            }
        }
        var requests = 0
        val engine = MockEngine { requests++; respond("apk") }
        val client = HttpClient(engine)
        try {
            assertFailsWith<IllegalArgumentException> {
                downloadReleaseAsset(client, apk.copy(browserDownloadUrl = "https://example.invalid/zhihu++-lite.apk")) { _, _ -> }
            }
            assertEquals(0, requests)
        } finally {
            client.close()
            engine.close()
        }
    }

    private class UpdateTestSettingsStore : SettingsStore() {
        private val values = mutableMapOf<String, SettingValue>()
        override fun readValue(key: String): SettingValue? = values[key]
        override fun readEntries(): Map<String, SettingValue> = values.toMap()
        override fun commit(updates: Map<String, SettingValue?>) {
            updates.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
        }
        override fun observeKeyChanges(onChanged: (String) -> Unit): AutoCloseable = AutoCloseable { }
    }

    private fun asset(name: String) = GithubAsset(
        name = name,
        contentType = "application/octet-stream",
        browserDownloadUrl = "https://github.com/zly2006/zhihu-plus-plus/releases/download/v2.0.0/$name",
        size = 128,
    )
}
