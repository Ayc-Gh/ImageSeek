package com.op.aod.enhance

import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.factory.encase
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import com.op.aod.enhance.hook.DebugFileLogger
import com.op.aod.enhance.hook.MainHook

@InjectYukiHookWithXposed(sourcePath = "src/main", modulePackageName = "com.op.aod.enhance", entryClassName = "HookEntryXposed")
object HookEntry : IYukiHookXposedInit {
    override fun onInit() {
        YukiHookAPI.configs {
            debugLog {
                tag = "AOD_Enhance"
                isEnable = BuildConfig.DEBUG
                isRecord = false
                elements(TAG, PRIORITY)
            }
            isDebug = BuildConfig.DEBUG
        }
        DebugFileLogger.i("LIFECYCLE", "HookEntry initialized debug=" + BuildConfig.DEBUG)
    }
    override fun onHook() = encase(MainHook)
}
