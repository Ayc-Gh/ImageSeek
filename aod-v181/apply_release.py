from pathlib import Path

root = Path("/tmp/aod-src/app")
hook = root / "src/main/kotlin/com/op/aod/enhance/hook"

# Remove debug-only telemetry registration and host callbacks.
p = hook / "MainHook.kt"
s = p.read_text()
s = s.replace("import com.op.aod.enhance.hook.AodTelemetry.hookAodTelemetry\n", "")
s = s.replace("        AodTelemetry.onHostContextAvailable(app)\n", "")
s = s.replace('            hookWithLog("Telemetry.AodSession") { hookAodTelemetry() }\n', "")
p.write_text(s)

# Remove brightness-to-telemetry bridge calls.
p = hook / "BrightnessHook.kt"
s = p.read_text()
s = "\n".join(line for line in s.splitlines() if "AodTelemetry.onBrightnessEvent(" not in line) + "\n"
p.write_text(s)

# Replace debug logger with a release-safe logger: DEBUG/INFO only in debug builds;
# WARN/ERROR remain available in release builds for fault diagnosis.
(hook / "AodLog.kt").write_text("""package com.op.aod.enhance.hook

import android.util.Log
import com.op.aod.enhance.BuildConfig

/** Lightweight logcat logger. Release builds suppress verbose/debug lifecycle logs. */
internal object AodLog {
    private const val TAG = "AOD_Enhance"
    private const val MAX_MESSAGE_CHARS = 6000

    fun d(event: String, message: String) {
        if (BuildConfig.DEBUG) write(Log.DEBUG, event, message, null)
    }

    fun i(event: String, message: String) {
        if (BuildConfig.DEBUG) write(Log.INFO, event, message, null)
    }

    fun w(event: String, message: String, error: Throwable? = null) =
        write(Log.WARN, event, message, error)

    fun e(event: String, message: String, error: Throwable? = null) =
        write(Log.ERROR, event, message, error)

    private fun write(priority: Int, event: String, message: String, error: Throwable?) {
        val out = buildString {
            append(message.take(MAX_MESSAGE_CHARS))
            if (error != null) {
                append(" | ").append(error.javaClass.name)
                error.message?.let { append(": ").append(it.take(1200)) }
                val stack = error.stackTraceToString().take(MAX_MESSAGE_CHARS)
                if (stack.isNotBlank()) append(" | stack=").append(stack.replace('\\n', ' '))
            }
        }
        Log.println(priority, TAG, "$event: $out")
    }
}
""")

# Telemetry is not shipped in the release build.
(hook / "AodTelemetry.kt").unlink(missing_ok=True)

# Formal release version.
p = root / "build.gradle"
s = p.read_text()
s = s.replace("versionCode = 22", "versionCode = 23")
s = s.replace("versionName = '1.8.0-debug'", "versionName = '1.8.1'")
p.write_text(s)
