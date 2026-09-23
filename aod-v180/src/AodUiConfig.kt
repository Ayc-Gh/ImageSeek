package com.op.aod.enhance.data

/** UI 侧配置镜像。 */
data class AodUiConfig(
    val initDark: Int = AodConfigContract.DEFAULT_INIT_DARK,
    val initBright: Int = AodConfigContract.DEFAULT_INIT_BRIGHT,
    val runningMultiplier: Float = AodConfigContract.DEFAULT_RUNNING_MULTIPLIER,
    val useSystemInitDark: Boolean = AodConfigContract.DEFAULT_USE_SYSTEM_INIT_DARK,
    val useSystemInitBright: Boolean = AodConfigContract.DEFAULT_USE_SYSTEM_INIT_BRIGHT,
    val useSystemRunningMultiplier: Boolean = AodConfigContract.DEFAULT_USE_SYSTEM_RUNNING_MULTIPLIER,
    val enablePanoramic: Boolean = AodConfigContract.DEFAULT_ENABLE_PANORAMIC,
    val enableSettingsSupport: Boolean = AodConfigContract.DEFAULT_ENABLE_SETTINGS_SUPPORT,
    val blockSingleClick: Boolean = AodConfigContract.DEFAULT_BLOCK_SINGLE_CLICK,
    val blockLowLightHide: Boolean = AodConfigContract.DEFAULT_BLOCK_LOW_LIGHT_HIDE,
    val aodDurationMode: Int = AodConfigContract.DEFAULT_AOD_DURATION_MODE,
    val aodDurationCustomMinutes: Int = AodConfigContract.DEFAULT_AOD_DURATION_CUSTOM_MINUTES,
)
