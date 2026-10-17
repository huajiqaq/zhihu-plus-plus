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

package com.github.zly2006.zhihu.markdown

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.github.zly2006.zhihu.theme.ZhihuTheme
import com.hrm.markdown.renderer.MarkdownImageData
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regression for https://github.com/zly2006/zhihu-plus-plus/issues/517 and
 * https://github.com/zly2006/zhihu-plus-plus/issues/721：
 * 正文图片应占满内容宽度，而不是只占 80%。
 */
@OptIn(ExperimentalTestApi::class)
class RenderImageWidthTest {
    @Test
    fun markdownImageFillsContentWidth() = runComposeUiTest {
        setContent {
            ZhihuTheme {
                Box(
                    modifier = Modifier
                        .width(400.dp)
                        .testTag("image_container"),
                ) {
                    RenderImage(
                        data = MarkdownImageData(
                            url = "https://example.invalid/test.png",
                            altText = "test-image",
                            width = 100,
                            height = 200,
                        ),
                        modifier = Modifier,
                    )
                }
            }
        }

        val containerWidth = onNodeWithTag("image_container").fetchSemanticsNode().boundsInRoot.width
        val imageWidth = onNodeWithContentDescription("test-image").fetchSemanticsNode().boundsInRoot.width
        assertEquals(containerWidth, imageWidth, 1f)
    }
}
