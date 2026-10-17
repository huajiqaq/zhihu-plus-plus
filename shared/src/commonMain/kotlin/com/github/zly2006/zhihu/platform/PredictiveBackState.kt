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

package com.github.zly2006.zhihu.platform

import androidx.compose.runtime.compositionLocalOf

/**
 * 当前宿主是否允许预测性返回进度驱动页面和弹层动画。
 *
 * 关闭时，导航层回退处理器会消费系统返回并执行普通 popBackStack；底部弹层等
 * [PlatformPredictiveBackHandler] 使用方也会退化为普通返回，不再跟随手势预览。
 * 默认开启，未提供该值的宿主（例如独立预览、桌面端）保持原有行为。
 */
internal val LocalPredictiveBackEnabled = compositionLocalOf { true }
