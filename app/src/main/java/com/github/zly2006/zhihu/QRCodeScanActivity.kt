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

import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import com.github.zly2006.zhihu.QRCodeScanActivity.Companion.LOGIN_PREFIX
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.theme.ZhihuTheme
import com.github.zly2006.zhihu.ui.components.AppAlertDialog
import com.github.zly2006.zhihu.ui.components.AppButton
import com.github.zly2006.zhihu.ui.components.AppCompactTopBar
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppOverlayHost
import com.github.zly2006.zhihu.ui.components.AppScaffold
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.util.clipboardManager
import com.github.zly2006.zhihu.util.enableEdgeToEdgeCompat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions

class QRCodeScanActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdgeCompat()

        setContent {
            ZhihuTheme {
                /** Miuix 结果弹窗使用同树 OverlayDialog，独立 Activity 也必须提供宿主。 */
                AppOverlayHost {
                    QRCodeScanScreen(
                        onBack = { finish() },
                        onScanResult = { result ->
                            if (result.startsWith(LOGIN_PREFIX)) {
                                val resultIntent = Intent().apply {
                                    putExtra(EXTRA_SCAN_RESULT, result)
                                }
                                setResult(RESULT_OK, resultIntent)
                            }
                            finish()
                        },
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_SCAN_RESULT = "scan_result"
        const val LOGIN_PREFIX = "https://www.zhihu.com/account/scan/login/"
    }
}

@Composable
private fun QRCodeScanScreen(
    onBack: () -> Unit,
    onScanResult: (String) -> Unit,
) {
    var showResultDialog by remember { mutableStateOf(false) }
    var scanResult by remember { mutableStateOf("") }

    val context = LocalContext.current
    val userMessages = rememberUserMessageSink()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (!isGranted) {
            userMessages.showShortMessage("需要相机权限才能扫描二维码")
        }
    }

    val scanLauncher = rememberLauncherForActivityResult(
        contract = ScanContract(),
    ) { result: ScanIntentResult ->
        if (result.contents != null) {
            if (result.contents.startsWith(LOGIN_PREFIX)) {
                onScanResult(result.contents)
            } else {
                scanResult = result.contents
                showResultDialog = true
            }
        }
    }

    AppScaffold(
        topBar = {
            AppCompactTopBar(
                title = "扫码登录",
                onBack = onBack,
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = AppDimens.PageHeaderM3eHorizontalPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(AppDimens.ScanHeroSize)
                    .clip(RoundedCornerShape(AppDimens.ScanHeroCorner))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = "扫码登录",
                    modifier = Modifier.size(AppDimens.ScanIconSize),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            Spacer(modifier = Modifier.height(AppDimens.Space28))

            Text(
                text = "扫描电脑端登录二维码",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(AppDimens.Space10))

            Text(
                text = "在电脑浏览器中打开知乎网站，使用此功能扫描登录二维码",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(AppDimens.Space32))

            AppButton(
                onClick = {
                    if (hasCameraPermission) {
                        val options = ScanOptions().apply {
                            setPrompt("将登录二维码对准扫描框")
                            setBeepEnabled(false)
                            setDesiredBarcodeFormats(
                                ScanOptions.QR_CODE,
                            )
                            setCaptureActivity(CaptureActivity::class.java)
                        }
                        scanLauncher.launch(options)
                    } else {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
                modifier = Modifier.fillMaxWidth(0.8f),
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    modifier = Modifier.size(AppDimens.Space20),
                )
                Spacer(modifier = Modifier.width(AppDimens.Space8))
                Text(
                    text = if (hasCameraPermission) "开始扫描" else "授权相机权限",
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            Spacer(modifier = Modifier.height(AppDimens.Space16))

            Text(
                text = "二维码只用于知乎登录，不会上传到第三方",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }

    if (showResultDialog) {
        QRResultDialog(
            result = scanResult,
            onDismiss = { showResultDialog = false },
            onCopy = { text ->
                val clipData = ClipData.newPlainText("QR扫描结果", text)
                context.clipboardManager.setPrimaryClip(clipData)
                userMessages.showShortMessage("已复制到剪贴板")
            },
            onConfirm = { text ->
                onScanResult(text)
            },
        )
    }
}

@Composable
private fun QRResultDialog(
    result: String,
    onDismiss: () -> Unit,
    onCopy: (String) -> Unit,
    onConfirm: (String) -> Unit,
) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("扫描结果")
        },
        text = {
            Column {
                Text("扫描到的内容：")
                Spacer(modifier = Modifier.height(AppDimens.Space8))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Text(
                        text = result,
                        modifier = Modifier.padding(AppDimens.Space12),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            AppTextButton(
                text = "确定",
                onClick = { onConfirm(result) },
            )
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.Space8)) {
                AppTextButton(
                    text = "取消",
                    onClick = onDismiss,
                )
                AppTextButton(
                    text = "复制",
                    onClick = { onCopy(result) },
                )
            }
        },
    )
}
