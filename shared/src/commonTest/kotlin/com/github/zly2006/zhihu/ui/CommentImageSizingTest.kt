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

package com.github.zly2006.zhihu.ui

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class CommentImageSizingTest {
    @Test
    fun missingDimensionsFallBackTo120Square() {
        assertEquals(120.dp to 120.dp, commentImageDisplaySize(0, 0))
    }

    @Test
    fun smallImagesAreClampedToMinimum() {
        assertEquals(120.dp to 120.dp, commentImageDisplaySize(50, 50))
    }

    @Test
    fun portraitImagesAreClampedTo200Square() {
        assertEquals(200.dp to 200.dp, commentImageDisplaySize(1080, 1920))
    }

    @Test
    fun landscapeImagesUseClampedRatio() {
        val (width, height) = commentImageDisplaySize(1920, 1080)
        assertEquals(355.55f, width.value, 0.1f)
        assertEquals(112.5f, height.value, 0.1f)
    }
}
