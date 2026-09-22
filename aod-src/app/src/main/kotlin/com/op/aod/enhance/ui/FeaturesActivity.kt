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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.op.aod.enhance.data.AodConfigStore
import com.op.aod.enhance.data.AodUiConfig
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

class FeaturesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MiuixTheme { FeaturesScreen(AodConfigStore.read(contentResolver)) { AodConfigStore.write(contentResolver, it) } } }
    }
}

@Composable
private fun FeaturesScreen(initial: AodUiConfig, onSave: (AodUiConfig) -> Unit) {
    var panoramic by remember { mutableStateOf(initial.enablePanoramic) }
    var settings by remember { mutableStateOf(initial.enableSettingsSupport) }
    var single by remember { mutableStateOf(initial.blockSingleClick) }
    var low by remember { mutableStateOf(initial.blockLowLightHide) }
    val resolver = LocalContext.current.contentResolver
    fun save(transform: (AodUiConfig) -> AodUiConfig) = onSave(transform(AodConfigStore.read(resolver)))

    Scaffold(topBar = { SmallTopAppBar(title = "AOD功能设置", color = MiuixTheme.colorScheme.secondaryContainer) },
        containerColor = MiuixTheme.colorScheme.secondaryContainer) { padding: PaddingValues ->
        LazyColumn(modifier = Modifier.fillMaxHeight().scrollEndHaptic().overScrollVertical().padding(horizontal=12.dp), contentPadding=padding, overscrollEffect=null) {
            item {
                Card(modifier = Modifier.padding(top=12.dp).fillMaxWidth(), colors=CardDefaults.defaultColors(color=MiuixTheme.colorScheme.background)) {
                    SwitchPreference(title="系统界面-全天全景AOD支持", summary="让系统界面解锁全天全景 AOD 相关能力", checked=panoramic,
                        onCheckedChange={ v -> panoramic=v; save { it.copy(enablePanoramic=v) } })
                    SwitchPreference(title="息屏-全天全景AOD开关", summary="在息屏设置中显示全天全景 AOD 开关", checked=settings,
                        onCheckedChange={ v -> settings=v; save { it.copy(enableSettingsSupport=v) } })
                    SwitchPreference(title="AOD单击唤醒屏蔽", summary="避免 AOD 单击误触导致唤醒；双击仍正常", checked=single,
                        onCheckedChange={ v -> single=v; save { it.copy(blockSingleClick=v) } })
                    SwitchPreference(title="低光/特殊规则保持AOD显示", summary="阻止极暗、夜间低光以及当前 ColorOS 的 4 小时特殊规则自动关闭 AOD", checked=low,
                        onCheckedChange={ v -> low=v; save { it.copy(blockLowLightHide=v) } })
                }
            }
            item { Spacer(modifier=Modifier.padding(bottom=16.dp)) }
        }
    }
}
