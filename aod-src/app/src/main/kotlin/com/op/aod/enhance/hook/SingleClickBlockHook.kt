package com.op.aod.enhance.hook

import android.os.SystemClock
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.toClass

/**
 * AOD 单击唤醒屏蔽 Hook。
 *
 * 所有 Hook 始终注册，避免 SystemUI 启动早期 Application Context 尚不可用时
 * 用默认配置错误决定“是否注册”。真正的开关判断放在回调内；AodConfigReader
 * 常态只读取内存快照，因此不会把 Binder IPC 放进触摸热路径。
 *
 * 路径 A：黑屏手势服务的 3 个 onClick 回调，各自维护独立 DoubleTapGate。
 * 路径 B：GestureDetector 已确认不是双击后的 onSingleTapConfirmed 回调。
 */
internal object SingleClickBlockHook {

    private const val DOUBLE_CLICK_LISTENER =
        "com.oplus.systemui.keyguard.gesture.OplusDoubleClickSleep\$OnDoubleClickListener"

    fun YukiBaseHooker.hookSingleClickWakeUpBlock() {
        val targets = arrayOf(
            "com.oplus.systemui.aod.scene.AodViewSingleClickWakeUpHolder\$AodSingleClickWakeUpCallback" to "NormalAod",
            "com.oplus.systemui.aod.scene.PanoramicAodSingleClickWakeUpController\$PanoramicAodSingleClickWakeUpCallback" to "PanoramicAod",
            "com.oplus.systemui.aod.display.OplusWakeUpController\$AodSingleClickWakeUpCallback" to "WakeUpController",
        )

        for ((cls, label) in targets) {
            registerClickHook(cls, label)
        }
        hookDoubleClickSleepSingleTap()
    }

    private fun YukiBaseHooker.hookDoubleClickSleepSingleTap() {
        runCatching {
            DOUBLE_CLICK_LISTENER
                .toClass(appClassLoader)
                .resolve()
                .firstMethod { name = "onSingleTapConfirmed" }
                .hook {
                    before {
                        if (!AodConfigReader.read(MainHook.hostAppContext).blockSingleClick) {
                            DebugFileLogger.d("SINGLE_CLICK", "view-touch single tap pass through because feature disabled")
                            return@before
                        }
                        result = false
                        DebugFileLogger.d("SINGLE_CLICK", "view-touch confirmed single tap blocked")
                    }
                }
        }.onSuccess {
            DebugFileLogger.i("HOOK_REGISTER", "single-click view-touch hook registered")
        }.onFailure {
            DebugFileLogger.w("HOOK_REGISTER", "single-click view-touch hook unavailable", it)
        }
    }

    private fun YukiBaseHooker.registerClickHook(targetClass: String, label: String) {
        val gate = DoubleTapGate()
        runCatching {
            targetClass
                .toClass(appClassLoader)
                .resolve()
                .firstMethod { name = "onClick" }
                .hook {
                    before {
                        val cfg = AodConfigReader.read(MainHook.hostAppContext)
                        if (!cfg.blockSingleClick) {
                            gate.reset()
                            DebugFileLogger.d("SINGLE_CLICK", "$label click pass through because feature disabled")
                            return@before
                        }

                        val now = SystemClock.elapsedRealtime()
                        if (gate.shouldAllow(now)) {
                            DebugFileLogger.d("SINGLE_CLICK", "$label allowed as double-click now=$now")
                            return@before
                        }

                        // onClick 返回 void/Unit；before 中设置 result=null 可跳过原方法。
                        result = null
                        DebugFileLogger.d("SINGLE_CLICK", "$label first/slow click blocked now=$now")
                    }
                }
        }.onSuccess {
            DebugFileLogger.i("HOOK_REGISTER", "single-click onClick hook registered label=$label class=$targetClass")
        }.onFailure {
            DebugFileLogger.w("HOOK_REGISTER", "single-click onClick hook unavailable label=$label class=$targetClass", it)
        }
    }
}
