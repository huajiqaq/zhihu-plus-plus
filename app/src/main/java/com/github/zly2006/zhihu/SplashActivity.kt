/*
 * Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 * Licensed under the GNU Affero General Public License, version 3 only.
 */

package com.github.zly2006.zhihu

import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withResumed
import com.github.zly2006.zhihu.theme.AndroidThemeSettings
import com.github.zly2006.zhihu.ui.components.AppSplashScreen
import com.github.zly2006.zhihu.util.enableEdgeToEdgeCompat
import kotlinx.coroutines.launch

class SplashActivity : ComponentActivity() {
    private var handingOff = false

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setOnExitAnimationListener { it.remove() }
        enableEdgeToEdgeCompat()
        super.onCreate(savedInstanceState)
        AndroidThemeSettings.initialize(this)
        setContent {
            Box(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
                AppSplashScreen {
                    lifecycleScope.launch {
                        lifecycle.withResumed {
                            if (!handingOff) {
                                handingOff = true
                                startActivity(
                                    Intent(this@SplashActivity, MainActivity::class.java)
                                        .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
                                    ActivityOptions.makeCustomAnimation(this@SplashActivity, 0, 0).toBundle(),
                                )
                                finish()
                            }
                        }
                    }
                }
            }
        }
    }
}
