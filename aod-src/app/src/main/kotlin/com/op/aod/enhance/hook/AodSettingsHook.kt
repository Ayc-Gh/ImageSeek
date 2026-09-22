package com.op.aod.enhance.hook

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.toClass

internal object AodSettingsHook {
    fun YukiBaseHooker.hookAodAllDaySupportSettings() {
        SETTINGS_UTILS.toClass(appClassLoader).resolve()
            .firstMethod { name = "getKeyAodAllDaySupportSettings" }
            .hook {
                after {
                    val cfg = AodConfigReader.read(MainHook.hostAppContext)
                    val value = if (cfg.enableSettingsSupport) 1 else 0
                    result = value
                    DebugFileLogger.d("AOD_SETTINGS", "getKeyAodAllDaySupportSettings value=" + value + " enable=" + cfg.enableSettingsSupport)
                }
            }
    }
    private const val SETTINGS_UTILS = "com.oplus.aod.util.SettingsUtils"
}
