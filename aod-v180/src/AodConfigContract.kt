package com.op.aod.enhance.data

import android.database.Cursor
import kotlin.concurrent.Volatile

object AodConfigContract {
    const val KEY_INIT_DARK = "init_brightness_dark"
    const val KEY_INIT_BRIGHT = "init_brightness_bright"
    const val KEY_RUNNING_MULTIPLIER = "running_brightness_multiplier"
    const val KEY_USE_SYSTEM_INIT_DARK = "use_system_init_dark"
    const val KEY_USE_SYSTEM_INIT_BRIGHT = "use_system_init_bright"
    const val KEY_USE_SYSTEM_RUNNING_MULTIPLIER = "use_system_running_multiplier"
    const val KEY_ENABLE_PANORAMIC = "enable_panoramic"
    const val KEY_ENABLE_SETTINGS_SUPPORT = "enable_settings_support"
    const val KEY_BLOCK_SINGLE_CLICK = "block_single_click"
    const val KEY_BLOCK_LOW_LIGHT_HIDE = "block_low_light_hide"
    const val KEY_AOD_DURATION_MODE = "aod_duration_mode"
    const val KEY_AOD_DURATION_CUSTOM_MINUTES = "aod_duration_custom_minutes"

    const val DEFAULT_INIT_DARK = 80
    const val DEFAULT_INIT_BRIGHT = 160
    const val DEFAULT_RUNNING_MULTIPLIER = 1.6f
    const val DEFAULT_USE_SYSTEM_INIT_DARK = false
    const val DEFAULT_USE_SYSTEM_INIT_BRIGHT = false
    const val DEFAULT_USE_SYSTEM_RUNNING_MULTIPLIER = false
    const val DEFAULT_ENABLE_PANORAMIC = true
    const val DEFAULT_ENABLE_SETTINGS_SUPPORT = true
    const val DEFAULT_BLOCK_SINGLE_CLICK = true
    const val DEFAULT_BLOCK_LOW_LIGHT_HIDE = true

    const val DURATION_MODE_SYSTEM = 0
    const val DURATION_MODE_30_SECONDS = 1
    const val DURATION_MODE_1_MINUTE = 2
    const val DURATION_MODE_5_MINUTES = 3
    const val DURATION_MODE_10_MINUTES = 4
    const val DURATION_MODE_30_MINUTES = 5
    const val DURATION_MODE_60_MINUTES = 6
    const val DURATION_MODE_ALWAYS = 7
    const val DURATION_MODE_CUSTOM = 8
    const val DEFAULT_AOD_DURATION_MODE = DURATION_MODE_SYSTEM
    const val DEFAULT_AOD_DURATION_CUSTOM_MINUTES = 5
    const val MIN_AOD_DURATION_CUSTOM_MINUTES = 1
    const val MAX_AOD_DURATION_CUSTOM_MINUTES = 1440

    @Volatile private var columnIndices: IntArray? = null
    private fun getCachedColumnIndices(c: Cursor): IntArray {
        columnIndices?.let { return it }
        synchronized(this) {
            columnIndices?.let { return it }
            val indices = intArrayOf(
                c.getColumnIndexOrThrow(KEY_INIT_DARK), c.getColumnIndexOrThrow(KEY_INIT_BRIGHT),
                c.getColumnIndexOrThrow(KEY_RUNNING_MULTIPLIER), c.getColumnIndexOrThrow(KEY_USE_SYSTEM_INIT_DARK),
                c.getColumnIndexOrThrow(KEY_USE_SYSTEM_INIT_BRIGHT), c.getColumnIndexOrThrow(KEY_USE_SYSTEM_RUNNING_MULTIPLIER),
                c.getColumnIndexOrThrow(KEY_ENABLE_PANORAMIC), c.getColumnIndexOrThrow(KEY_ENABLE_SETTINGS_SUPPORT),
                c.getColumnIndexOrThrow(KEY_BLOCK_SINGLE_CLICK), c.getColumnIndexOrThrow(KEY_BLOCK_LOW_LIGHT_HIDE),
            )
            columnIndices = indices
            return indices
        }
    }
    fun readRow(c: Cursor): ConfigValues {
        val i=getCachedColumnIndices(c)
        return ConfigValues(c.getInt(i[0]),c.getInt(i[1]),c.getFloat(i[2]),c.getInt(i[3])==1,c.getInt(i[4])==1,c.getInt(i[5])==1,c.getInt(i[6])==1,c.getInt(i[7])==1,c.getInt(i[8])==1,c.getInt(i[9])==1)
    }
    data class ConfigValues(val initDark:Int,val initBright:Int,val runningMultiplier:Float,val useSystemInitDark:Boolean,val useSystemInitBright:Boolean,val useSystemRunningMultiplier:Boolean,val enablePanoramic:Boolean,val enableSettingsSupport:Boolean,val blockSingleClick:Boolean,val blockLowLightHide:Boolean)
}
