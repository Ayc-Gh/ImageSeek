package com.op.aod.enhance.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.op.aod.enhance.data.AodConfigContract
import com.op.aod.enhance.data.AodConfigStore
import com.op.aod.enhance.data.AodUiConfig
import com.op.aod.enhance.data.AodValueSanitizer
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

class BrightnessActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MiuixTheme { BrightnessScreen(AodConfigStore.read(contentResolver)) { AodConfigStore.write(contentResolver, it) } } }
    }
}

@Composable
private fun BrightnessScreen(initial: AodUiConfig, onSave: (AodUiConfig) -> Unit) {
    var dark by remember { mutableFloatStateOf(initial.initDark.toFloat()) }
    var bright by remember { mutableFloatStateOf(initial.initBright.toFloat()) }
    var multiplier by remember { mutableFloatStateOf(initial.runningMultiplier) }
    var systemDark by remember { mutableStateOf(initial.useSystemInitDark) }
    var systemBright by remember { mutableStateOf(initial.useSystemInitBright) }
    var systemMultiplier by remember { mutableStateOf(initial.useSystemRunningMultiplier) }
    val resolver = LocalContext.current.contentResolver

    fun save(transform: (AodUiConfig) -> AodUiConfig) = onSave(transform(AodConfigStore.read(resolver)))

    Scaffold(topBar = { SmallTopAppBar(title = "AOD亮度设置") }) { padding: PaddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxHeight().scrollEndHaptic().overScrollVertical().padding(horizontal = 12.dp),
            contentPadding = padding,
            overscrollEffect = null
        ) {
            item {
                Card(modifier = Modifier.padding(top = 12.dp).fillMaxWidth(), colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.background)) {
                    SwitchPreference(
                        title = "暗光初始亮度使用系统默认",
                        summary = "开启后原样保留 ColorOS 当次计算的暗光初始亮度；自定义数值仍会保留",
                        checked = systemDark,
                        onCheckedChange = { v -> systemDark = v; save { it.copy(useSystemInitDark = v) } }
                    )
                    Text("熄屏前暗光环境初始 AOD 亮度：" + dark.toInt(), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    Slider(value = dark, onValueChange = { v ->
                        dark = v.coerceIn(0f,255f)
                        save { it.copy(initDark = dark.toInt()) }
                    }, valueRange = 0f..255f, steps = 254, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                    TextField(value = dark.toInt().toString(), onValueChange = { text ->
                        text.toIntOrNull()?.let { n -> dark = AodValueSanitizer.sanitizeBrightness(n).toFloat(); save { it.copy(initDark = dark.toInt()) } }
                    }, label = "自定义暗光初始亮度", modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), singleLine = true)

                    SwitchPreference(
                        title = "亮光初始亮度使用系统默认",
                        summary = "开启后原样保留 ColorOS 当次计算的亮光初始亮度；自定义数值仍会保留",
                        checked = systemBright,
                        onCheckedChange = { v -> systemBright = v; save { it.copy(useSystemInitBright = v) } }
                    )
                    Text("熄屏前亮光环境初始 AOD 亮度：" + bright.toInt(), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    Slider(value = bright, onValueChange = { v ->
                        bright = v.coerceIn(0f,255f)
                        save { it.copy(initBright = bright.toInt()) }
                    }, valueRange = 0f..255f, steps = 254, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                    TextField(value = bright.toInt().toString(), onValueChange = { text ->
                        text.toIntOrNull()?.let { n -> bright = AodValueSanitizer.sanitizeBrightness(n).toFloat(); save { it.copy(initBright = bright.toInt()) } }
                    }, label = "自定义亮光初始亮度", modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), singleLine = true)

                    SwitchPreference(
                        title = "自动亮度倍率使用系统默认",
                        summary = "开启后完全跳过模块倍率补偿，保留 ColorOS 原生 AOD 自动亮度策略",
                        checked = systemMultiplier,
                        onCheckedChange = { v -> systemMultiplier = v; save { it.copy(useSystemRunningMultiplier = v) } }
                    )
                    Text("熄屏时 AOD 自动亮度倍率：" + multiplier, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    Slider(value = multiplier, onValueChange = { v ->
                        multiplier = ((v * 10).toInt().coerceIn(10,20) / 10f)
                        save { it.copy(runningMultiplier = multiplier) }
                    }, valueRange = 1f..2f, steps = 9, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                    TextField(value = multiplier.toString(), onValueChange = { text ->
                        text.toFloatOrNull()?.takeIf { it.isFinite() }?.let { n ->
                            multiplier = AodValueSanitizer.sanitizeRunningMultiplier(n, AodConfigContract.DEFAULT_RUNNING_MULTIPLIER)
                            save { it.copy(runningMultiplier = multiplier) }
                        }
                    }, label = "自定义自动亮度倍率", modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), singleLine = true)
                }
            }
            item { Spacer(modifier = Modifier.padding(bottom = 16.dp)) }
        }
    }
}
