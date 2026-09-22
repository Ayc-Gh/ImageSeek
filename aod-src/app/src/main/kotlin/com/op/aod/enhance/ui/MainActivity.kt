package com.op.aod.enhance.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.op.aod.enhance.BuildConfig
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestDebugLogStorageAccessIfNeeded()
        setContent { MiuixTheme { MainScreen(
            onOpenBrightness = { startActivity(Intent(this, BrightnessActivity::class.java)) },
            onOpenFeatures = { startActivity(Intent(this, FeaturesActivity::class.java)) }
        ) } }
    }
    private fun requestDebugLogStorageAccessIfNeeded() {
        if (!BuildConfig.DEBUG || Environment.isExternalStorageManager()) return
        val specific = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))
        runCatching { startActivity(specific) }.onFailure { runCatching { startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) } }
    }
}

@Composable
private fun MainScreen(onOpenBrightness: () -> Unit, onOpenFeatures: () -> Unit) {
    Scaffold(topBar = { SmallTopAppBar(title = "ColorOS AOD 增强", color = MiuixTheme.colorScheme.secondaryContainer) },
        containerColor = MiuixTheme.colorScheme.secondaryContainer) { paddingValues: PaddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.background)) {
                ArrowPreference(title = "AOD亮度设置", summary = "调整初始亮度、系统默认模式与运行时倍率", onClick = onOpenBrightness)
                ArrowPreference(title = "AOD功能设置", summary = "系统界面、息屏设置与唤醒行为", onClick = onOpenFeatures)
            }
        }
    }
}
