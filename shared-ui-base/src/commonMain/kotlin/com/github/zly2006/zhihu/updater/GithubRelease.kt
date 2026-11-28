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

import com.github.zly2006.zhihu.i18n.ReaderStrings
import com.github.zly2006.zhihu.i18n.SettingsStrings
import com.github.zly2006.zhihu.ui.ReaderDisplayFailure
import com.github.zly2006.zhihu.util.raiseForStatus
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.decodeURLPart
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val ZHIHU_PLUS_PLUS_GITHUB_LATEST_RELEASE_URL = "https://api.github.com/repos/zly2006/zhihu-plus-plus/releases/latest"
const val ZHIHU_PLUS_PLUS_REDEN_LATEST_RELEASE_URL = "https://redenmc.com/api/zhihu/releases/latest"
const val ZHIHU_PLUS_PLUS_GITHUB_NIGHTLY_RELEASE_URL = "https://api.github.com/repos/zly2006/zhihu-plus-plus/releases/tags/nightly"

@Serializable
data class GithubRelease(
    @SerialName("tag_name") val tagName: String = "",
    @SerialName("html_url") val htmlUrl: String? = null,
    val body: String? = null,
    val assets: List<GithubAsset> = emptyList(),
)

@Serializable
data class GithubAsset(
    val name: String,
    @SerialName("content_type") val contentType: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
    @SerialName("cn_download_url") val cnDownloadUrl: String? = null,
    /** 旧版发布元数据可能不提供长度，0 表示未知；下载仍受统一上限约束。 */
    val size: Long = 0,
    val digest: String? = null,
)

/** 与发布工作流的宿主和文件名逐字匹配；macOS 公开包属于 Native，不能更新 JVM 宿主。 */
internal fun releaseAssetName(platform: String, architecture: String, nativeHost: Boolean, lite: Boolean = false): String? {
    val arch = when (architecture.lowercase()) {
        "amd64", "x86_64", "x64" -> "x64"
        "aarch64", "arm64" -> "arm64"
        else -> return null
    }
    return when (platform) {
        "Windows" if !nativeHost -> "zhihu++-desktop-windows-$arch.msi"
        "Linux" if !nativeHost -> "zhihu++-desktop-linux-$arch.AppImage"
        "macOS" if nativeHost && arch == "arm64" -> "zhihu++-desktop-macos-arm64.app.zip"
        "iOS" if nativeHost && arch == "arm64" -> "ZhihuPlusPlus${if (lite) "Lite" else ""}-ios-${if (lite) "lite" else "full"}-arm64-unsigned.ipa"
        else -> null
    }
}

internal fun GithubRelease.assetFor(platform: String, architecture: String, nativeHost: Boolean, lite: Boolean = false): GithubAsset? {
    val name = releaseAssetName(platform, architecture, nativeHost, lite) ?: return null
    return assets.singleOrNull { it.name == name && it.size > 0 && it.hasValidDownloadMetadata() }
}

internal const val MAX_UPDATE_DOWNLOAD_BYTES = 2L * 1024 * 1024 * 1024

internal fun GithubAsset.isOfficialDownload(): Boolean = runCatching {
    val url = Url(browserDownloadUrl)
    val segments = url.encodedPath.split('/').map { it.decodeURLPart() }
    url.protocol.name == "https" && url.host == "github.com" && url.port == 443 &&
        url.user == null && url.password == null && url.fragment.isEmpty() &&
        segments.size == 7 && segments.take(5) == listOf("", "zly2006", "zhihu-plus-plus", "releases", "download") &&
        segments[5].isNotBlank() && segments[5] !in listOf(".", "..") && '/' !in segments[5] && '\\' !in segments[5] &&
        segments[6] == name && name.isNotBlank() && '/' !in name && '\\' !in name && name !in listOf(".", "..")
}.getOrDefault(false)

internal fun GithubAsset.hasValidDownloadMetadata(): Boolean =
    isOfficialDownload() && size in 0..MAX_UPDATE_DOWNLOAD_BYTES &&
        (digest == null || Regex("sha256:[0-9a-f]{64}").matches(digest))

/** 无摘要的旧发布仍须校验长度上限及平台包身份；提供摘要时必须完全匹配。 */
internal fun GithubAsset.verifySha256(actualSha256: String) {
    digest?.let { check(it == "sha256:$actualSha256") { SettingsStrings.updateFileInvalid } }
}

internal fun GithubRelease.officialReleasePageUrl(): String = htmlUrl?.takeIf { value ->
    runCatching {
        val url = Url(value)
        val segments = url.encodedPath.split('/').map { it.decodeURLPart() }
        url.protocol.name == "https" && url.host == "github.com" && url.port == 443 &&
            url.user == null && url.password == null &&
            segments.size == 6 && segments.take(5) == listOf("", "zly2006", "zhihu-plus-plus", "releases", "tag") &&
            segments[5].isNotBlank() && segments[5] !in listOf(".", "..") && '/' !in segments[5] && '\\' !in segments[5]
    }.getOrDefault(false)
} ?: "https://github.com/zly2006/zhihu-plus-plus/releases"

fun extractGithubReleaseNotes(body: String): String = body
    .replace("\r\n", "\n")
    .substringAfter("## What's Changed\n")
    .substringBefore("\n**Full Changelog**:")
    .trimEnd('\n')

suspend fun fetchLatestZhihuRelease(
    client: HttpClient,
    githubToken: String?,
): GithubRelease = runCatching {
    client.get(ZHIHU_PLUS_PLUS_REDEN_LATEST_RELEASE_URL) {
        timeout { requestTimeoutMillis = 15_000 }
    }.raiseForStatus().body<GithubRelease>()
}.getOrElse { error ->
    if (error is CancellationException) throw error
    null
} ?: client
    .get(ZHIHU_PLUS_PLUS_GITHUB_LATEST_RELEASE_URL) {
        timeout { requestTimeoutMillis = 15_000 }
        githubToken?.let { token ->
            headers {
                append(HttpHeaders.Authorization, "Bearer $token")
            }
        }
    }.raiseForStatus()
    .body<GithubRelease>()

suspend fun fetchNightlyZhihuRelease(
    client: HttpClient,
    githubToken: String?,
): GithubRelease = client
    .get(ZHIHU_PLUS_PLUS_GITHUB_NIGHTLY_RELEASE_URL) {
        timeout { requestTimeoutMillis = 15_000 }
        githubToken?.let { token ->
            headers {
                append(HttpHeaders.Authorization, "Bearer $token")
            }
        }
    }.raiseForStatus()
    .body<GithubRelease>()

internal data class AndroidReleaseDownloadInfo(
    val browserDownloadUrl: String,
    val cnDownloadUrl: String? = null,
    val opensExternally: Boolean = false,
    val asset: GithubAsset? = null,
)

internal fun GithubRelease.extractAndroidDownloadInfo(isLiteVariant: Boolean): AndroidReleaseDownloadInfo {
    val variant = if (isLiteVariant) "lite" else "full"
    // CI 发布通用 ABI 包；缺少本变体时绝不能回退到另一 APK。
    val selectedAsset = assets.singleOrNull { it.name == "zhihu++-$variant.apk" }
    if (selectedAsset != null) {
        if (!selectedAsset.hasValidDownloadMetadata()) {
            throw ReaderDisplayFailure("发布资产下载信息无效") { SettingsStrings.updateFileInvalid }
        }
        return AndroidReleaseDownloadInfo(selectedAsset.browserDownloadUrl, selectedAsset.cnDownloadUrl, asset = selectedAsset)
    }
    val quarkUrl = Regex("""https://pan\.quark\.cn/s/[A-Za-z0-9_-]+(?:\?[A-Za-z0-9._~%!$&*+,;=:@/?-]+)?""")
        .find(body.orEmpty())
        ?.value
        ?: throw ReaderDisplayFailure("发布中未找到 APK 或夸克网盘下载链接") { ReaderStrings.releaseDownloadMissing }
    return AndroidReleaseDownloadInfo(browserDownloadUrl = quarkUrl, opensExternally = true)
}
