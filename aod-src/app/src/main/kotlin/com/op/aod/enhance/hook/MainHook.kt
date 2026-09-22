package com.op.aod.enhance.hook

import android.content.Context
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.op.aod.enhance.hook.AodSettingsHook.hookAodAllDaySupportSettings
import com.op.aod.enhance.hook.BrightnessHook.hookInitBrightnessFix
import com.op.aod.enhance.hook.BrightnessHook.hookRunningBrightnessBoost
import com.op.aod.enhance.hook.LowLightHideHook.hookLowLightAodHide
import com.op.aod.enhance.hook.PanoramicHook.hookPanoramicAllDaySupport
import com.op.aod.enhance.hook.SingleClickBlockHook.hookSingleClickWakeUpBlock

object MainHook : YukiBaseHooker() {
    val hostAppContext: Context?
        get() {
            _cachedContext?.let { return it }
            val ctx = fetchContext()
            if (ctx != null) _cachedContext = ctx
            return ctx
        }
    @Volatile private var _cachedContext: Context? = null

    private fun fetchContext(): Context? =
        runCatching { Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null) as? Context }.getOrNull()
            ?: runCatching { Class.forName("android.app.AppGlobals").getMethod("getInitialApplication").invoke(null) as? Context }.getOrNull()

    override fun onHook() {
        loadApp(name = SYSTEM_UI) {
            DebugFileLogger.i("LIFECYCLE", "loaded SystemUI host")
            register("init brightness") { hookInitBrightnessFix() }
            register("running brightness") { hookRunningBrightnessBoost() }
            register("panoramic") { hookPanoramicAllDaySupport() }
            register("single click") { hookSingleClickWakeUpBlock() }
            register("low light") { hookLowLightAodHide() }
        }
        loadApp(name = OPLUS_AOD) {
            DebugFileLogger.i("LIFECYCLE", "loaded Oplus AOD host")
            register("AOD settings") { hookAodAllDaySupportSettings() }
        }
    }

    private inline fun register(name: String, block: () -> Unit) {
        runCatching(block)
            .onSuccess { DebugFileLogger.i("HOOK_REGISTER", name + " registration completed") }
            .onFailure { DebugFileLogger.e("HOOK_REGISTER", name + " registration failed", it) }
    }

    private const val SYSTEM_UI = "com.android.systemui"
    private const val OPLUS_AOD = "com.oplus.aod"
}
