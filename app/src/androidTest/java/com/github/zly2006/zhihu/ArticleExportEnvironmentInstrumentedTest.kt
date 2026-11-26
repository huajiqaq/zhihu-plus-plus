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
import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewAssetLoader
import com.github.zly2006.zhihu.account.ZhihuAccountRepository
import com.github.zly2006.zhihu.account.ZhihuAccountStore
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.util.buildArticleExportHtml
import com.github.zly2006.zhihu.viewmodel.AndroidPreparedExportWebView
import com.github.zly2006.zhihu.viewmodel.SharedAndroidPaginationEnvironment
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class ArticleExportEnvironmentInstrumentedTest {
    @Test
    fun webContentCanReadOnlyTheSelectedCustomFont() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "synthetic-web-files-${java.util.UUID.randomUUID()}").apply { check(mkdirs()) }
        val privateFiles = File(directory, "files").apply { check(mkdirs()) }
        val fontBytes = byteArrayOf(0, 1, 2, 3)
        val privateBytes = byteArrayOf(7, 8, 9)
        val font = File(privateFiles, "custom_font").apply { writeBytes(fontBytes) }
        val privateFile = File(privateFiles, "synthetic-private.txt").apply { writeBytes(privateBytes) }
        val outside = File(directory, "synthetic-outside.txt").apply { writeBytes(privateBytes) }
        val fixtureContext = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this

            override fun getFilesDir(): File = privateFiles
        }
        try {
            val handler = Class
                .forName("com.github.zly2006.zhihu.ui.components.UserFilesPathHandler")
                .getDeclaredConstructor(Context::class.java)
                .apply { isAccessible = true }
                .newInstance(fixtureContext) as WebViewAssetLoader.PathHandler
            val response = handler.handle("custom_font")
            assertNotNull(response)
            assertEquals(200, response!!.statusCode)
            assertArrayEquals(fontBytes, response.data.use { it.readBytes() })
            for (path in listOf("synthetic-private.txt", "../synthetic-outside.txt", "custom_font/../synthetic-private.txt", "")) {
                assertNull(handler.handle(path))
            }
            check(font.delete())
            android.system.Os.symlink(outside.absolutePath, font.absolutePath)
            assertNull(handler.handle("custom_font"))
            check(font.delete())
            assertArrayEquals(privateBytes, privateFile.readBytes())
            assertArrayEquals(privateBytes, outside.readBytes())
        } finally {
            check(directory.deleteRecursively())
        }
    }

    /**
     * 导出的长图应包含首段和末段正文。
     * 用不同颜色的标记验证正文确已绘制，避免仅凭非空标题判断导出成功。
     */
    @Test
    fun articleImageRendererUsesStableContentHeightBeforeFullLayout() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "synthetic-export-${java.util.UUID.randomUUID()}").apply { check(mkdirs()) }
        val store = ZhihuAccountStore(
            ZhihuAccountRepository(kotlinx.io.files.Path(directory.resolve("account.json").path)),
            MockEngine { error("合成导出不应发起网络请求") },
        )
        try {
            val environment = SharedAndroidPaginationEnvironment(context, allowGuestAccess = true, accountStore = store)
            val renderer = environment.articleImageExportRenderer()
            val prepared = renderer.prepareExportWebView(
                htmlContent = buildArticleExportHtml(
                    loadAssetText = environment::loadExportAssetText,
                    content = sampleLongAnswerContent(),
                    includeAppAttribution = true,
                    extraSectionsHtml = "",
                ),
                timeoutMs = 15_000,
            ) as AndroidPreparedExportWebView
            val bitmap = renderer.captureExportBitmap(prepared) as Bitmap
            try {
                assertTrue(prepared.contentHeightPx < 60_000)
                assertEquals(prepared.expectedBitmapWidthPx(context), bitmap.width)
                assertEquals(prepared.expectedBitmapHeightPx(context), bitmap.height)
                assertTrue(
                    "导出图上半部分应包含首段正文的青色标记",
                    bitmap.hasMarkerPixels(red = 0, green = 255, blue = 255, startY = 0, endY = bitmap.height / 2),
                )
                assertTrue(
                    "导出图下半部分应包含末段正文的品红标记，不能只画出标题或前几段",
                    bitmap.hasMarkerPixels(red = 255, green = 0, blue = 255, startY = bitmap.height / 2, endY = bitmap.height),
                )
                ByteArrayOutputStream().use { output ->
                    assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 70, output))
                    assertTrue(output.size() > 0)
                }
            } finally {
                bitmap.recycle()
                renderer.destroyExportWebView(prepared)
            }
        } finally {
            store.close()
            directory.deleteRecursively()
        }
    }

    private fun AndroidPreparedExportWebView.expectedBitmapWidthPx(context: Context): Int =
        (viewportWidthPx * context.exportScale()).roundToInt().coerceAtLeast(1)

    private fun AndroidPreparedExportWebView.expectedBitmapHeightPx(context: Context): Int =
        (contentHeightPx * context.exportScale()).roundToInt().coerceAtLeast(1)

    private fun Context.exportScale(): Float =
        ARTICLE_EXPORT_DPI_FOR_TEST / resources.displayMetrics.densityDpi
            .coerceAtLeast(1)
            .toFloat()

    private fun Bitmap.hasMarkerPixels(
        red: Int,
        green: Int,
        blue: Int,
        startY: Int,
        endY: Int,
    ): Boolean {
        val row = IntArray(width)
        var markerPixelCount = 0
        for (y in startY until endY) {
            getPixels(row, 0, width, 0, y, width, 1)
            for (pixel in row) {
                if (
                    kotlin.math.abs((pixel shr 16 and 0xff) - red) <= 16 &&
                    kotlin.math.abs((pixel shr 8 and 0xff) - green) <= 16 &&
                    kotlin.math.abs((pixel and 0xff) - blue) <= 16
                ) {
                    markerPixelCount++
                    if (markerPixelCount >= 100) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private companion object {
        const val ARTICLE_EXPORT_DPI_FOR_TEST = 200f
    }

    private fun sampleLongAnswerContent(): DataHolder.Answer = DataHolder.Answer(
        id = 3309625617L,
        answerType = "normal",
        author = DataHolder.Author(
            avatarUrl = "",
            gender = 0,
            headline = "导出测试答主简介",
            id = "answer-export-author",
            isAdvertiser = false,
            isOrg = false,
            name = "导出测试答主",
            type = "people",
            url = "https://www.zhihu.com/people/answer-export-author",
            urlToken = "answer-export-author",
            userType = "people",
        ),
        canComment = DataHolder.CanComment(status = true, reason = ""),
        commentCount = 4175,
        content = (1..24).joinToString("") { index ->
            val marker = when (index) {
                1 -> " style=\"background-color:#00ffff\""
                24 -> " style=\"background-color:#ff00ff\""
                else -> ""
            }
            "<p data-pid=\"p$index\"$marker>第 $index 段用于验证 WebView 导出高度稳定性的正文。</p>"
        },
        excerpt = "导出测试回答摘要",
        type = "answer",
        createdTime = 1_710_000_000L,
        updatedTime = 1_710_000_600L,
        url = "https://www.zhihu.com/question/631983014/answer/3309625617",
        thanksCount = 0,
        voteupCount = 12,
        question = DataHolder.AnswerModelQuestion(
            created = 1_710_000_000L,
            id = 631983014L,
            questionType = "normal",
            title = "导出高度稳定性测试问题",
            type = "question",
            updatedTime = 1_710_000_600L,
            url = "https://www.zhihu.com/question/631983014",
        ),
    )
}
