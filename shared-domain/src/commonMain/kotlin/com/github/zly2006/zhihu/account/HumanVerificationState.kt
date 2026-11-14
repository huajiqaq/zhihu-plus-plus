/*
 * Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.github.zly2006.zhihu.account

enum class HumanVerificationPhase { ACTIVE, PROMPT, VERIFYING, RESTART_REQUIRED, DISMISSED }

data class HumanVerificationState(
    val phase: HumanVerificationPhase = HumanVerificationPhase.ACTIVE,
    val url: String? = null,
    val candidateCookies: Map<String, String>? = null,
) {
    val blocksSessionChange: Boolean
        get() = phase != HumanVerificationPhase.ACTIVE && phase != HumanVerificationPhase.DISMISSED
}
