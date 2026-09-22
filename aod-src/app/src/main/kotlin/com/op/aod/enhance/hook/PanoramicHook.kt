package com.op.aod.enhance.hook

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.toClass

internal object PanoramicHook {
    private val FIELD_NAMES = listOf("isSupportPanoramicAllDay","isSupportPanoramicAllDayByPanelFeature","isSupportPanoramicByPanelFeature","isSupportPanoramic")
    private const val CONTROLLER = "com.oplus.systemui.aod.display.SmoothTransitionController"

    fun YukiBaseHooker.hookPanoramicAllDaySupport() {
        val clazz = runCatching { CONTROLLER.toClass(appClassLoader).resolve() }
            .onFailure { DebugFileLogger.w("HOOK_REGISTER", "panoramic controller unavailable", it) }
            .getOrNull() ?: return
        DebugFileLogger.i("HOOK_REGISTER", "panoramic controller resolved")

        fun apply(instance: Any) {
            val cfg = AodConfigReader.read(MainHook.hostAppContext)
            if (!cfg.enablePanoramic) {
                DebugFileLogger.d("PANORAMIC", "feature disabled; leaving system fields unchanged")
                return
            }
            val realClass = instance::class.java
            var applied = 0
            for (name in FIELD_NAMES) {
                runCatching {
                    realClass.getDeclaredField(name).apply { isAccessible = true }.setBoolean(instance, true)
                    applied++
                }.onFailure { DebugFileLogger.d("PANORAMIC", "field unavailable name=" + name) }
            }
            DebugFileLogger.d("PANORAMIC", "applied fields=" + applied)
        }

        runCatching { clazz.firstMethod { name = "initSmoothTransitionState" } }.getOrNull()?.hook { after { apply(instance<Any>()) } }
        runCatching { clazz.firstMethod { name = "setPanoramicSupportedByRemote" } }.getOrNull()?.hook { after { apply(instance<Any>()) } }
    }
}
