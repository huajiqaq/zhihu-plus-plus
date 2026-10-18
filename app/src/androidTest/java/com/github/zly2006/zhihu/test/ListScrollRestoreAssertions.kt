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

package com.github.zly2006.zhihu.test

import org.junit.Assert.assertTrue
import kotlin.math.abs

/** 首页和问题页共用同一套滚动恢复断言，避免两个页面各自放宽标准。 */
fun assertListOffsetKept(
    label: String,
    before: Float?,
    after: Float?,
    tolerancePx: Float = 8f,
) {
    assertTrue("$label must stay visible; before=$before after=$after", after != null)
    assertTrue(
        "$label must keep its pixel offset; before=$before after=$after",
        abs(after!! - before!!) < tolerancePx,
    )
}
