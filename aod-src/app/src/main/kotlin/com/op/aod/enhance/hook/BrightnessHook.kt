package com.op.aod.enhance.hook

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.toClass
import com.op.aod.enhance.data.AodConfigContract
import com.op.aod.enhance.data.AodValueSanitizer
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

internal object BrightnessHook {
    private val pendingInitBrightness = AtomicReference<Int?>(null)

    fun YukiBaseHooker.hookInitBrightnessFix() {
        OPLUS_DOZE_SERVICE_EX_IMPL.toClass(appClassLoader).resolve()
            .firstMethod { name = "setBrightnessBeforeDozing"; emptyParameters() }
            .hook {
                after {
                    val originalResult = result<Int>() ?: return@after
                    if (originalResult == -1) {
                        DebugFileLogger.d("BRIGHTNESS", "init callback original=-1 sentinel; pass through")
                        return@after
                    }
                    val cfg = AodConfigReader.read(MainHook.hostAppContext)
                    val isDark = originalResult < INIT_DARK_THRESHOLD
                    val useSystem = if (isDark) cfg.useSystemInitDark else cfg.useSystemInitBright
                    val target = if (useSystem) originalResult else
                        AodValueSanitizer.sanitizeBrightness(if (isDark) cfg.initDark else cfg.initBright)
                    result = target
                    pendingInitBrightness.set(target)
                    val mode = if (useSystem) "system" else "custom"
                    DebugFileLogger.d("BRIGHTNESS", "init ambient=" + (if (isDark) "dark" else "bright") + " mode=" + mode + " original=" + originalResult + " target=" + target)
                }
            }
    }

    fun YukiBaseHooker.hookRunningBrightnessBoost() {
        val main = runCatching { hookDozeServiceBrightnessBoost() }
        if (main.isSuccess) {
            DebugFileLogger.i("HOOK_REGISTER", "brightness main DozeService hook registered")
            return
        }
        DebugFileLogger.w("HOOK_REGISTER", "brightness main hook unavailable; enabling fallbacks", main.exceptionOrNull())
        runCatching { hookOplusDozeServiceBrightnessBoost() }
            .onSuccess { DebugFileLogger.i("HOOK_REGISTER", "brightness Oplus fallback registered") }
            .onFailure { DebugFileLogger.w("HOOK_REGISTER", "brightness Oplus fallback failed", it) }
        runCatching { hookFallbackBrightnessBoost() }
            .onFailure { DebugFileLogger.w("HOOK_REGISTER", "brightness named fallback failed", it) }
    }

    private fun YukiBaseHooker.hookDozeServiceBrightnessBoost() {
        DOZE_SERVICE.toClass(appClassLoader).resolve()
            .firstMethod { name = "setDozeScreenBrightness"; parameters(Int::class) }
            .hook {
                before {
                    val original = args(0).any() as? Int ?: return@before
                    if (consumePendingInit(original)) {
                        DebugFileLogger.d("BRIGHTNESS", "skip pending init value=" + original + " path=DozeService")
                        return@before
                    }
                    applyBoostToArg(original) { args(0).set(it) }
                }
            }
    }

    private fun YukiBaseHooker.hookOplusDozeServiceBrightnessBoost() {
        OPLUS_DOZE_SERVICE_EX_IMPL.toClass(appClassLoader).resolve()
            .firstMethod { name = "setDozeScreenBrightness"; parameters(Int::class) }
            .hook {
                before {
                    val original = args(0).any() as? Int ?: return@before
                    if (consumePendingInit(original)) {
                        DebugFileLogger.d("BRIGHTNESS", "skip pending init value=" + original + " path=OplusFallback")
                        return@before
                    }
                    applyBoostToArg(original) { args(0).set(it) }
                }
            }
    }

    private fun YukiBaseHooker.hookFallbackBrightnessBoost() {
        for (methodName in FALLBACK_METHOD_NAMES) {
            val hooked = runCatching {
                OPLUS_DOZE_SERVICE_EX_IMPL.toClass(appClassLoader).resolve()
                    .firstMethod { name = methodName; parameters(Int::class) }
                    .hook {
                        before {
                            val original = args(0).any() as? Int ?: return@before
                            applyBoostToArg(original) { args(0).set(it) }
                        }
                    }
            }.isSuccess
            if (hooked) {
                DebugFileLogger.i("HOOK_REGISTER", "brightness named fallback registered method=" + methodName)
                return
            }
            DebugFileLogger.d("HOOK_REGISTER", "brightness named fallback unavailable method=" + methodName)
        }
    }

    private fun applyBoostToArg(original: Int, setter: (Int) -> Unit) {
        if (original < 0) {
            DebugFileLogger.d("BRIGHTNESS", "running sentinel=" + original + " pass through")
            return
        }
        val cfg = AodConfigReader.read(MainHook.hostAppContext)
        if (cfg.useSystemRunningMultiplier) {
            DebugFileLogger.d("BRIGHTNESS", "running mode=system original=" + original + " pass through")
            return
        }
        val multiplier = AodValueSanitizer.sanitizeRunningMultiplier(cfg.runningMultiplier, AodConfigContract.DEFAULT_RUNNING_MULTIPLIER)
        if (multiplier == 1.0f) {
            DebugFileLogger.d("BRIGHTNESS", "running original=" + original + " multiplier=1.0 pass through")
            return
        }
        val target = AodValueSanitizer.sanitizeBrightness((original * multiplier).roundToInt())
        setter(target)
        DebugFileLogger.d("BRIGHTNESS", "running mode=custom original=" + original + " target=" + target + " multiplier=" + multiplier)
    }

    private fun consumePendingInit(original: Int): Boolean {
        val pending = pendingInitBrightness.getAndSet(null) ?: return false
        val matched = original == pending
        DebugFileLogger.d("BRIGHTNESS", "consume pending init pending=" + pending + " original=" + original + " matched=" + matched)
        return matched
    }

    private const val OPLUS_DOZE_SERVICE_EX_IMPL = "com.oplus.systemui.aod.OplusDozeServiceExImpl"
    private const val DOZE_SERVICE = "com.android.systemui.doze.DozeService"
    private const val INIT_DARK_THRESHOLD = 40
    private val FALLBACK_METHOD_NAMES = arrayOf("setBrightnessForFallbackStrategy", "setBrightness4FallbackStrategy")
}
