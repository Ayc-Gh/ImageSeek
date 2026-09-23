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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.op.aod.enhance.data.AodConfigContract
import com.op.aod.enhance.data.AodConfigStore
import com.op.aod.enhance.data.AodUiConfig
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
import kotlin.math.roundToInt

class FeaturesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MiuixTheme {
                FeaturesScreen(
                    initial=AodConfigStore.read(this),
                    onSave={ cfg -> AodConfigStore.write(this,cfg) }
                )
            }
        }
    }
}

@Composable
private fun FeaturesScreen(initial:AodUiConfig,onSave:(AodUiConfig)->Unit){
    var enablePanoramic by remember{mutableStateOf(initial.enablePanoramic)}
    var enableSettingsSupport by remember{mutableStateOf(initial.enableSettingsSupport)}
    var blockSingleClick by remember{mutableStateOf(initial.blockSingleClick)}
    var blockLowLightHide by remember{mutableStateOf(initial.blockLowLightHide)}
    var durationMode by remember{mutableFloatStateOf(initial.aodDurationMode.toFloat())}
    var durationCustomMinutes by remember{mutableStateOf(initial.aodDurationCustomMinutes.toString())}
    val currentOnSave by rememberUpdatedState(onSave)
    val context=LocalContext.current
    fun update(transform:(AodUiConfig)->AodUiConfig){currentOnSave(transform(AodConfigStore.read(context)))}

    Scaffold(
        topBar={SmallTopAppBar(title="AOD功能设置",color=MiuixTheme.colorScheme.secondaryContainer)},
        containerColor=MiuixTheme.colorScheme.secondaryContainer,
    ){paddingValues:PaddingValues->
        LazyColumn(
            modifier=Modifier.fillMaxHeight().scrollEndHaptic().overScrollVertical().padding(horizontal=12.dp),
            contentPadding=paddingValues,overscrollEffect=null,
        ){
            item{
                Card(
                    modifier=Modifier.padding(top=12.dp).fillMaxWidth(),
                    colors=CardDefaults.defaultColors(color=MiuixTheme.colorScheme.background),
                ){
                    SwitchPreference(
                        title="系统界面-全天全景AOD支持",
                        summary="让系统界面解锁全天全景 AOD 相关能力；关闭后建议重启系统界面恢复原始能力状态",
                        checked=enablePanoramic,
                        onCheckedChange={enablePanoramic=it;update{b->b.copy(enablePanoramic=it)}},
                    )
                    SwitchPreference(
                        title="息屏-全天全景AOD开关",
                        summary="在息屏设置中显示全天全景 AOD 开关",
                        checked=enableSettingsSupport,
                        onCheckedChange={enableSettingsSupport=it;update{b->b.copy(enableSettingsSupport=it)}},
                    )
                    SwitchPreference(
                        title="AOD单击唤醒屏蔽",
                        summary="避免 AOD 单击误触导致唤醒；双击仍正常，修改后近实时生效",
                        checked=blockSingleClick,
                        onCheckedChange={blockSingleClick=it;update{b->b.copy(blockSingleClick=it)}},
                    )
                    SwitchPreference(
                        title="低光/特殊规则保持AOD显示",
                        summary="阻止极暗、夜间低光以及当前 ColorOS 的 4 小时特殊规则自动关闭 AOD",
                        checked=blockLowLightHide,
                        onCheckedChange={blockLowLightHide=it;update{b->b.copy(blockLowLightHide=it)}},
                    )

                    Text(
                        text="AOD 最长显示时长：${durationModeLabel(durationMode.roundToInt(),durationCustomMinutes.toIntOrNull())}",
                        modifier=Modifier.padding(horizontal=16.dp,vertical=8.dp),
                    )
                    Slider(
                        value=durationMode,
                        onValueChange={raw->
                            val mode=raw.roundToInt().coerceIn(AodConfigContract.DURATION_MODE_SYSTEM,AodConfigContract.DURATION_MODE_CUSTOM)
                            durationMode=mode.toFloat()
                            update{b->b.copy(aodDurationMode=mode)}
                        },
                        valueRange=AodConfigContract.DURATION_MODE_SYSTEM.toFloat()..AodConfigContract.DURATION_MODE_CUSTOM.toFloat(),
                        steps=7,
                        modifier=Modifier.padding(horizontal=16.dp).fillMaxWidth(),
                    )
                    Text(
                        text="系统默认 / 30秒 / 1分 / 5分 / 10分 / 30分 / 60分 / 始终 / 自定义",
                        modifier=Modifier.padding(horizontal=16.dp,vertical=4.dp),
                    )
                    if(durationMode.roundToInt()==AodConfigContract.DURATION_MODE_CUSTOM){
                        TextField(
                            value=durationCustomMinutes,
                            onValueChange={text->
                                durationCustomMinutes=text.filter{it.isDigit()}.take(4)
                                durationCustomMinutes.toIntOrNull()?.let{minutes->
                                    val safe=minutes.coerceIn(AodConfigContract.MIN_AOD_DURATION_CUSTOM_MINUTES,AodConfigContract.MAX_AOD_DURATION_CUSTOM_MINUTES)
                                    update{b->b.copy(aodDurationCustomMinutes=safe)}
                                }
                            },
                            label="自定义分钟数（1-1440）",
                            modifier=Modifier.padding(horizontal=16.dp,vertical=8.dp).fillMaxWidth(),
                            singleLine=true,
                        )
                    }
                    Text(
                        text="这是本次 AOD 的最长显示上限；来电、解锁、系统主动结束等仍会立即退出。选择“始终”只是不设置时长上限，低光保持由上方开关独立控制。",
                        modifier=Modifier.padding(horizontal=16.dp,vertical=8.dp),
                    )
                }
            }
            item{Spacer(modifier=Modifier.padding(bottom=16.dp))}
        }
    }
}

private fun durationModeLabel(mode:Int,customMinutes:Int?):String=when(mode){
    AodConfigContract.DURATION_MODE_SYSTEM->"使用系统默认"
    AodConfigContract.DURATION_MODE_30_SECONDS->"30 秒"
    AodConfigContract.DURATION_MODE_1_MINUTE->"1 分钟"
    AodConfigContract.DURATION_MODE_5_MINUTES->"5 分钟"
    AodConfigContract.DURATION_MODE_10_MINUTES->"10 分钟"
    AodConfigContract.DURATION_MODE_30_MINUTES->"30 分钟"
    AodConfigContract.DURATION_MODE_60_MINUTES->"60 分钟"
    AodConfigContract.DURATION_MODE_ALWAYS->"始终显示（模块不设上限）"
    AodConfigContract.DURATION_MODE_CUSTOM->"自定义 ${customMinutes?:AodConfigContract.DEFAULT_AOD_DURATION_CUSTOM_MINUTES} 分钟"
    else->"使用系统默认"
}
