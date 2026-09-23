package com.op.aod.enhance.hook

import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.toClass
import com.op.aod.enhance.data.AodConfigContract
import java.util.concurrent.atomic.AtomicLong

internal object AodDurationHook {
    private const val DOZE_SERVICE = "com.android.systemui.doze.DozeService"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val sessionToken = AtomicLong(0L)
    @Volatile private var pending: Runnable? = null

    fun YukiBaseHooker.hookAodDurationLimit() {
        val clazz = DOZE_SERVICE.toClass(appClassLoader).resolve()
        runCatching {
            clazz.firstMethod { name = "onDreamingStarted" }.hook { after { schedule(instance<Any>()) } }
        }.onSuccess { AodLog.i("DURATION_HOOK", "onDreamingStarted registered") }
            .onFailure { AodLog.e("DURATION_HOOK", "onDreamingStarted unavailable", it) }
        runCatching {
            clazz.firstMethod { name = "onDreamingStopped" }.hook { before { cancel("DozeService.onDreamingStopped") } }
        }.onSuccess { AodLog.i("DURATION_HOOK", "onDreamingStopped registered") }
            .onFailure { AodLog.e("DURATION_HOOK", "onDreamingStopped unavailable", it) }
    }

    private fun schedule(dozeService: Any) {
        cancel("new-session")
        val cfg = AodConfigReader.read(MainHook.hostAppContext)
        val limitMs = durationLimitMs(cfg)
        val modeName = durationModeName(cfg.aodDurationMode)
        if (limitMs == null) {
            AodLog.i("AOD_DURATION", "mode=$modeName action=no-module-limit")
            return
        }
        val token = sessionToken.incrementAndGet()
        val startedAt = SystemClock.elapsedRealtime()
        val task = Runnable {
            if (sessionToken.get() != token) return@Runnable
            val interactive = runCatching { MainHook.hostAppContext?.getSystemService(PowerManager::class.java)?.isInteractive }.getOrNull()
            if (interactive == true) {
                AodLog.i("AOD_DURATION_CANCEL", "token=$token reason=device-interactive elapsedMs=${SystemClock.elapsedRealtime() - startedAt}")
                pending = null
                return@Runnable
            }
            val result = runCatching { dozeService.javaClass.getMethod("finish").invoke(dozeService) }
            if (result.isSuccess) AodLog.i("AOD_DURATION_EXPIRED", "token=$token mode=$modeName limitMs=$limitMs elapsedMs=${SystemClock.elapsedRealtime() - startedAt} action=DreamService.finish")
            else AodLog.e("AOD_DURATION_EXPIRED", "token=$token mode=$modeName finish failed", result.exceptionOrNull())
            pending = null
        }
        pending = task
        mainHandler.postDelayed(task, limitMs)
        AodLog.i("AOD_DURATION_START", "token=$token mode=$modeName limitMs=$limitMs customMin=${cfg.aodDurationCustomMinutes}")
    }
    private fun cancel(reason: String) {
        pending?.let { mainHandler.removeCallbacks(it) }
        pending = null
        val token = sessionToken.incrementAndGet()
        AodLog.d("AOD_DURATION_CANCEL", "token=$token reason=$reason")
    }
    private fun durationLimitMs(cfg: AodConfig): Long? = when (cfg.aodDurationMode) {
        AodConfigContract.DURATION_MODE_SYSTEM, AodConfigContract.DURATION_MODE_ALWAYS -> null
        AodConfigContract.DURATION_MODE_30_SECONDS -> 30_000L
        AodConfigContract.DURATION_MODE_1_MINUTE -> 60_000L
        AodConfigContract.DURATION_MODE_5_MINUTES -> 5 * 60_000L
        AodConfigContract.DURATION_MODE_10_MINUTES -> 10 * 60_000L
        AodConfigContract.DURATION_MODE_30_MINUTES -> 30 * 60_000L
        AodConfigContract.DURATION_MODE_60_MINUTES -> 60 * 60_000L
        AodConfigContract.DURATION_MODE_CUSTOM -> cfg.aodDurationCustomMinutes.coerceIn(AodConfigContract.MIN_AOD_DURATION_CUSTOM_MINUTES, AodConfigContract.MAX_AOD_DURATION_CUSTOM_MINUTES).toLong() * 60_000L
        else -> null
    }
    private fun durationModeName(mode: Int): String = when (mode) {
        0 -> "system"; 1 -> "30s"; 2 -> "1m"; 3 -> "5m"; 4 -> "10m"; 5 -> "30m"; 6 -> "60m"; 7 -> "always"; 8 -> "custom"; else -> "system"
    }
}
