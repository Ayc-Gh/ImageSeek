# Xposed 入口类
-keep class com.op.aod.enhance.HookEntryXposed { *; }

# YukiHookAPI 核心（反射调用需要保留）
-keep class com.highcapable.yukihookapi.** { *; }

# KavaRef 反射工具（方法查找需要保留）
-keep class com.highcapable.kavaref.** { *; }

# Miuix UI 组件库（Compose 需要保留）
-keep class top.yukonga.miuix.** { *; }

# 数据类（防止混淆导致 ContentProvider 列名不匹配）
-keep class com.op.aod.enhance.data.** { *; }

# Hook 侧配置类（反射和序列化需要）
-keep class com.op.aod.enhance.hook.AodConfig { *; }
-keep class com.op.aod.enhance.hook.AodConfigReader { *; }

# 抑制警告
-dontwarn com.highcapable.**
-dontwarn top.yukonga.miuix.**
