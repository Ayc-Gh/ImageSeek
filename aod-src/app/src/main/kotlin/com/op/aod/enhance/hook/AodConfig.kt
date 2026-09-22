package com.op.aod.enhance.hook

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import com.op.aod.enhance.data.AodConfigContract
import com.op.aod.enhance.data.AodValueSanitizer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal data class AodConfig(
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
)

/**
 * Hook 侧跨进程配置镜像。
 *
 * 设计目标：Hook 热路径只读内存，不做周期性 Binder IPC。
 * - 第一次拿到宿主 Context 时注册 ContentObserver
 * - 首次读取 Provider 后保存为 AtomicReference 快照
 * - UI 更新 Provider 后 notifyChange，Observer 负责刷新内存快照
 * - Provider 暂不可用时使用 5 秒退避，避免 SystemUI 启动期 IPC 风暴
 * - ConfigRefreshGate 保证同一时刻只有一个线程刷新
 */
internal object AodConfigReader {

    private val DEFAULT_CONFIG = AodConfig()
    private val uri: Uri = Uri.parse("content://com.op.aod.enhance.config/aod_config")

    private val cachedRef = AtomicReference<AodConfig?>(null)
    private val observerRef = AtomicReference<ContentObserver?>(null)
    private val observerRegistered = AtomicBoolean(false)
    private val refreshGate = ConfigRefreshGate(RETRY_BACKOFF_NS)

    fun read(context: Context?): AodConfig {
        if (context == null) {
            val value = cachedRef.get() ?: DEFAULT_CONFIG
            DebugFileLogger.d("CONFIG", "read context=null source=${if (cachedRef.get() != null) "cache" else "default"} $value")
            return value
        }

        val appContext = context.applicationContext ?: context
        val observing = ensureObserver(appContext)
        val cached = cachedRef.get()

        // Observer 未注册时保留低频轮询兜底；缓存为空时必须尝试首次读取。
        if (cached == null || !observing) {
            refresh(appContext, force = false)
        }
        val value = cachedRef.get() ?: DEFAULT_CONFIG
        DebugFileLogger.d(
            "CONFIG",
            "read source=${if (cachedRef.get() != null) "cache" else "default"} observer=$observing $value"
        )
        return value
    }

    private fun ensureObserver(context: Context): Boolean {
        if (observerRegistered.get()) return true
        if (!observerRegistered.compareAndSet(false, true)) return observerRegistered.get()

        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean, changedUri: Uri?) {
                DebugFileLogger.d("CONFIG", "observer onChange self=$selfChange uri=$changedUri")
                if (changedUri == null || changedUri == uri) {
                    refresh(context, force = true)
                }
            }
        }

        val registered = runCatching {
            context.contentResolver.registerContentObserver(uri, false, observer)
            observerRef.set(observer)
        }.isSuccess

        if (!registered) {
            observerRegistered.set(false)
            observerRef.set(null)
            DebugFileLogger.w("CONFIG", "ContentObserver registration failed")
        } else {
            DebugFileLogger.i("CONFIG", "ContentObserver registered: $uri")
        }
        return registered
    }

    private fun refresh(context: Context, force: Boolean) {
        val now = System.nanoTime()
        if (!refreshGate.tryAcquire(now, force)) {
            DebugFileLogger.d("CONFIG", "refresh skipped by gate force=$force")
            return
        }

        DebugFileLogger.d("CONFIG", "refresh start force=$force")
        val fresh = readFromProvider(context)
        if (fresh != null) {
            cachedRef.set(fresh)
            refreshGate.success()
            DebugFileLogger.i("CONFIG", "refresh success $fresh")
        } else {
            refreshGate.failure(now)
            DebugFileLogger.w("CONFIG", "refresh failed; retry backoff active")
        }
    }

    private fun readFromProvider(context: Context): AodConfig? {
        return runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (!c.moveToFirst()) return@use null
                val v = AodConfigContract.readRow(c)
                AodConfig(
                    initDark = AodValueSanitizer.sanitizeBrightness(v.initDark),
                    initBright = AodValueSanitizer.sanitizeBrightness(v.initBright),
                    runningMultiplier = AodValueSanitizer.sanitizeRunningMultiplier(
                        v.runningMultiplier,
                        AodConfigContract.DEFAULT_RUNNING_MULTIPLIER,
                    ),
                    useSystemInitDark = v.useSystemInitDark,
                    useSystemInitBright = v.useSystemInitBright,
                    useSystemRunningMultiplier = v.useSystemRunningMultiplier,
                    enablePanoramic = v.enablePanoramic,
                    enableSettingsSupport = v.enableSettingsSupport,
                    blockSingleClick = v.blockSingleClick,
                    blockLowLightHide = v.blockLowLightHide,
                )
            }
        }.onFailure {
            DebugFileLogger.w("CONFIG", "provider query failed", it)
        }.getOrNull()
    }

    private const val RETRY_BACKOFF_NS = 5_000_000_000L
}
