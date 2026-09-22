package com.op.aod.enhance.data

object AodValueSanitizer {
    fun sanitizeBrightness(value: Int): Int = value.coerceIn(0, 255)
    fun sanitizeRunningMultiplier(value: Float, fallback: Float = AodConfigContract.DEFAULT_RUNNING_MULTIPLIER): Float =
        if (value.isFinite()) value.coerceIn(1.0f, 2.0f) else fallback.coerceIn(1.0f, 2.0f)
}
