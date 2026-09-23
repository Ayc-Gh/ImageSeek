package com.op.aod.enhance.hook

import android.content.Context
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.op.aod.enhance.hook.AodSettingsHook.hookAodAllDaySupportSettings
import com.op.aod.enhance.hook.AodTelemetry.hookAodTelemetry
import com.op.aod.enhance.hook.AodDurationHook.hookAodDurationLimit
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
            if (ctx != null) bindHostContext(ctx, "reflection-fallback")
            return _cachedContext
        }
    @Volatile private var _cachedContext: Context? = null
    private fun bindHostContext(context: Context, source: String) {
        val app = context.applicationContext ?: context
        val first = _cachedContext == null
        _cachedContext = app
        AodTelemetry.onHostContextAvailable(app)
        if (first) AodLog.i("HOST_CONTEXT", "bound source=$source package=${app.packageName}")
    }
    private fun fetchContext(): Context? = runCatching {
        Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null) as? Context
    }.getOrNull() ?: runCatching {
        Class.forName("android.app.AppGlobals").getMethod("getInitialApplication").invoke(null) as? Context
    }.getOrNull() ?: runCatching {
        val activityThread = Class.forName("android.app.ActivityThread").getMethod("currentActivityThread").invoke(null) ?: return@runCatching null
        activityThread.javaClass.getMethod("getSystemContext").invoke(activityThread) as? Context
    }.getOrNull()

    override fun onHook() {
        loadApp(name = SYSTEM_UI) {
            AodConfigReader.bindPrefs(prefs, SYSTEM_UI)
            onAppLifecycle(isOnFailureThrowToApp = false) {
                attachBaseContext { baseContext, _ -> bindHostContext(baseContext, "SystemUI.attachBaseContext") }
                onCreate { bindHostContext(this, "SystemUI.Application.onCreate") }
            }
            AodLog.i("PROCESS_ATTACH", "package=$SYSTEM_UI")
            hookWithLog("Brightness.Init") { hookInitBrightnessFix() }
            hookWithLog("Brightness.Running") { hookRunningBrightnessBoost() }
            hookWithLog("Telemetry.AodSession") { hookAodTelemetry() }
            hookWithLog("Duration.Limit") { hookAodDurationLimit() }
            hookWithLog("Panoramic.AllDay") { hookPanoramicAllDaySupport() }
            hookWithLog("SingleClick.Block") { hookSingleClickWakeUpBlock() }
            hookWithLog("LowLight.Block") { hookLowLightAodHide() }
        }
        loadApp(name = OPLUS_AOD) {
            AodConfigReader.bindPrefs(prefs, OPLUS_AOD)
            onAppLifecycle(isOnFailureThrowToApp = false) {
                attachBaseContext { baseContext, _ -> bindHostContext(baseContext, "OplusAod.attachBaseContext") }
                onCreate { bindHostContext(this, "OplusAod.Application.onCreate") }
            }
            AodLog.i("PROCESS_ATTACH", "package=$OPLUS_AOD")
            hookWithLog("AodSettings.AllDay") { hookAodAllDaySupportSettings() }
        }
    }
    private inline fun hookWithLog(name: String, block: () -> Unit) {
        AodLog.i("HOOK_REGISTER", "start name=$name")
        runCatching(block).onSuccess { AodLog.i("HOOK_REGISTER", "success name=$name") }.onFailure { AodLog.e("HOOK_REGISTER", "failed name=$name", it) }
    }
    private const val SYSTEM_UI = "com.android.systemui"
    private const val OPLUS_AOD = "com.oplus.aod"
}
