package com.op.aod.enhance.data

import android.content.Context
import com.highcapable.yukihookapi.hook.factory.prefs
import java.util.concurrent.atomic.AtomicReference

object AodConfigStore {
    const val PREFS_NAME = "aod_config"
    private val DEFAULT_CONFIG = AodUiConfig()
    private val cachedRef = AtomicReference<AodUiConfig?>()

    fun read(context: Context): AodUiConfig {
        val fresh = runCatching { readMap(context.prefs(PREFS_NAME).all()) }.getOrNull()
        if (fresh != null) { cachedRef.set(fresh); return fresh }
        return cachedRef.get() ?: DEFAULT_CONFIG
    }

    fun write(context: Context, cfg: AodUiConfig): Boolean {
        val safe = cfg.copy(
            initDark=AodValueSanitizer.sanitizeBrightness(cfg.initDark),
            initBright=AodValueSanitizer.sanitizeBrightness(cfg.initBright),
            runningMultiplier=AodValueSanitizer.sanitizeRunningMultiplier(cfg.runningMultiplier,AodConfigContract.DEFAULT_RUNNING_MULTIPLIER),
            aodDurationMode=cfg.aodDurationMode.coerceIn(AodConfigContract.DURATION_MODE_SYSTEM,AodConfigContract.DURATION_MODE_CUSTOM),
            aodDurationCustomMinutes=cfg.aodDurationCustomMinutes.coerceIn(AodConfigContract.MIN_AOD_DURATION_CUSTOM_MINUTES,AodConfigContract.MAX_AOD_DURATION_CUSTOM_MINUTES),
        )
        val success=runCatching {
            context.prefs(PREFS_NAME).edit {
                putInt(AodConfigContract.KEY_INIT_DARK,safe.initDark)
                putInt(AodConfigContract.KEY_INIT_BRIGHT,safe.initBright)
                putFloat(AodConfigContract.KEY_RUNNING_MULTIPLIER,safe.runningMultiplier)
                putBoolean(AodConfigContract.KEY_USE_SYSTEM_INIT_DARK,safe.useSystemInitDark)
                putBoolean(AodConfigContract.KEY_USE_SYSTEM_INIT_BRIGHT,safe.useSystemInitBright)
                putBoolean(AodConfigContract.KEY_USE_SYSTEM_RUNNING_MULTIPLIER,safe.useSystemRunningMultiplier)
                putBoolean(AodConfigContract.KEY_ENABLE_PANORAMIC,safe.enablePanoramic)
                putBoolean(AodConfigContract.KEY_ENABLE_SETTINGS_SUPPORT,safe.enableSettingsSupport)
                putBoolean(AodConfigContract.KEY_BLOCK_SINGLE_CLICK,safe.blockSingleClick)
                putBoolean(AodConfigContract.KEY_BLOCK_LOW_LIGHT_HIDE,safe.blockLowLightHide)
                putInt(AodConfigContract.KEY_AOD_DURATION_MODE,safe.aodDurationMode)
                putInt(AodConfigContract.KEY_AOD_DURATION_CUSTOM_MINUTES,safe.aodDurationCustomMinutes)
            }
            true
        }.getOrDefault(false)
        if(success) cachedRef.set(safe)
        return success
    }

    private fun readMap(all: Map<String,Any?>): AodUiConfig {
        fun int(k:String,d:Int)=(all[k] as? Number)?.toInt()?:d
        fun float(k:String,d:Float)=(all[k] as? Number)?.toFloat()?:d
        fun bool(k:String,d:Boolean)=all[k] as? Boolean?:d
        return AodUiConfig(
            initDark=AodValueSanitizer.sanitizeBrightness(int(AodConfigContract.KEY_INIT_DARK,AodConfigContract.DEFAULT_INIT_DARK)),
            initBright=AodValueSanitizer.sanitizeBrightness(int(AodConfigContract.KEY_INIT_BRIGHT,AodConfigContract.DEFAULT_INIT_BRIGHT)),
            runningMultiplier=AodValueSanitizer.sanitizeRunningMultiplier(float(AodConfigContract.KEY_RUNNING_MULTIPLIER,AodConfigContract.DEFAULT_RUNNING_MULTIPLIER),AodConfigContract.DEFAULT_RUNNING_MULTIPLIER),
            useSystemInitDark=bool(AodConfigContract.KEY_USE_SYSTEM_INIT_DARK,AodConfigContract.DEFAULT_USE_SYSTEM_INIT_DARK),
            useSystemInitBright=bool(AodConfigContract.KEY_USE_SYSTEM_INIT_BRIGHT,AodConfigContract.DEFAULT_USE_SYSTEM_INIT_BRIGHT),
            useSystemRunningMultiplier=bool(AodConfigContract.KEY_USE_SYSTEM_RUNNING_MULTIPLIER,AodConfigContract.DEFAULT_USE_SYSTEM_RUNNING_MULTIPLIER),
            enablePanoramic=bool(AodConfigContract.KEY_ENABLE_PANORAMIC,AodConfigContract.DEFAULT_ENABLE_PANORAMIC),
            enableSettingsSupport=bool(AodConfigContract.KEY_ENABLE_SETTINGS_SUPPORT,AodConfigContract.DEFAULT_ENABLE_SETTINGS_SUPPORT),
            blockSingleClick=bool(AodConfigContract.KEY_BLOCK_SINGLE_CLICK,AodConfigContract.DEFAULT_BLOCK_SINGLE_CLICK),
            blockLowLightHide=bool(AodConfigContract.KEY_BLOCK_LOW_LIGHT_HIDE,AodConfigContract.DEFAULT_BLOCK_LOW_LIGHT_HIDE),
            aodDurationMode=int(AodConfigContract.KEY_AOD_DURATION_MODE,AodConfigContract.DEFAULT_AOD_DURATION_MODE).coerceIn(AodConfigContract.DURATION_MODE_SYSTEM,AodConfigContract.DURATION_MODE_CUSTOM),
            aodDurationCustomMinutes=int(AodConfigContract.KEY_AOD_DURATION_CUSTOM_MINUTES,AodConfigContract.DEFAULT_AOD_DURATION_CUSTOM_MINUTES).coerceIn(AodConfigContract.MIN_AOD_DURATION_CUSTOM_MINUTES,AodConfigContract.MAX_AOD_DURATION_CUSTOM_MINUTES),
        )
    }
}
