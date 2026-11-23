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

package com.github.zly2006.zhihu.ui.components

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
class ImageContextMenuPositionTest {
    @Test
    fun windowPressIgnoresOffsetPopupAnchorAndMirrorsAlignmentInRtl() {
        val provider = ImageContextMenuPositionProvider(Offset(520f, 230f), Rect(420f, 160f, 620f, 360f), 16, 16)
        val window = IntSize(900, 700)
        val menu = IntSize(220, 180)
        for (anchor in listOf(IntRect(400, 100, 650, 500), IntRect(0, 0, 900, 700))) {
            assertEquals(IntOffset(520, 246), provider.calculatePosition(anchor, window, LayoutDirection.Ltr, menu))
            assertEquals(IntOffset(300, 246), provider.calculatePosition(anchor, window, LayoutDirection.Rtl, menu))
        }
    }

    @Test
    fun bottomRightPressFlipsAboveAndClampsToWindowEdges() {
        val provider = ImageContextMenuPositionProvider(Offset(895f, 695f), null, 16, 16)
        assertEquals(
            IntOffset(664, 499),
            provider.calculatePosition(IntRect(400, 100, 650, 500), IntSize(900, 700), LayoutDirection.Ltr, IntSize(220, 180)),
        )
        val outside = ImageContextMenuPositionProvider(Offset(-20f, -10f), null, 16, 16)
        assertEquals(
            IntOffset(16, 16),
            outside.calculatePosition(IntRect.Zero, IntSize(900, 700), LayoutDirection.Rtl, IntSize(220, 180)),
        )
        assertEquals(
            IntOffset.Zero,
            provider.calculatePosition(IntRect.Zero, IntSize(120, 90), LayoutDirection.Ltr, IntSize(220, 180)),
        )
    }

    @Test
    fun missingPointerUsesLatestImageBoundsInsteadOfPopupParent() {
        val anchor = IntRect(0, 0, 900, 700)
        val window = IntSize(900, 700)
        val menu = IntSize(220, 180)
        for (top in listOf(160f, 100f)) {
            val provider = ImageContextMenuPositionProvider(null, Rect(420f, top, 620f, top + 200f), 8, 8)
            assertEquals(IntOffset(520, top.toInt() + 108), provider.calculatePosition(anchor, window, LayoutDirection.Ltr, menu))
        }
        val provider = ImageContextMenuPositionProvider(null, null, 8, 8)
        assertEquals(
            IntOffset(525, 308),
            provider.calculatePosition(IntRect(400, 100, 650, 500), window, LayoutDirection.Ltr, menu),
        )
    }
}
