package com.op.aod.enhance.data

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Environment
import android.os.Process
import com.op.aod.enhance.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 跨进程配置 Provider。
 *
 * exported=true 是设计要求：SystemUI 与 com.oplus.aod 需要跨 UID 读取配置。
 * 安全边界放在 Provider 内部：
 * - 读取：模块自身、system UID、SystemUI、com.oplus.aod
 * - 写入：仅模块自身 UID
 */
class AodConfigProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.op.aod.enhance.config"
        private const val PATH_CONFIG = "aod_config"
        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/$PATH_CONFIG")

        private const val PREFS_NAME = "aod_config"

        private val matcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, PATH_CONFIG, 1)
        }

        private val COLUMNS = arrayOf(
            AodConfigContract.KEY_INIT_DARK,
            AodConfigContract.KEY_INIT_BRIGHT,
            AodConfigContract.KEY_RUNNING_MULTIPLIER,
            AodConfigContract.KEY_USE_SYSTEM_INIT_DARK,
            AodConfigContract.KEY_USE_SYSTEM_INIT_BRIGHT,
            AodConfigContract.KEY_USE_SYSTEM_RUNNING_MULTIPLIER,
            AodConfigContract.KEY_ENABLE_PANORAMIC,
            AodConfigContract.KEY_ENABLE_SETTINGS_SUPPORT,
            AodConfigContract.KEY_BLOCK_SINGLE_CLICK,
            AodConfigContract.KEY_BLOCK_LOW_LIGHT_HIDE,
        )

        private val ALLOWED_READER_PACKAGES = setOf(
            "com.android.systemui",
            "com.oplus.aod",
        )

        private const val DEBUG_DIR_NAME = "AOD_Enhance/log"
        private const val CURRENT_LOG_NAME = "aod-debug-current.log"
        private const val MAX_LOG_BYTES = 4L * 1024L * 1024L
        private const val MAX_ROTATED_LOGS = 7
        private val debugLogLock = Any()
    }

    override fun onCreate(): Boolean = true

    private fun prefs() = context?.getSharedPreferences(PREFS_NAME, 0)

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        if (matcher.match(uri) != 1) return null
        enforceReadAccess()
        val p = prefs() ?: return null

        val initDark = AodValueSanitizer.sanitizeBrightness(
            p.getInt(AodConfigContract.KEY_INIT_DARK, AodConfigContract.DEFAULT_INIT_DARK)
        )
        val initBright = AodValueSanitizer.sanitizeBrightness(
            p.getInt(AodConfigContract.KEY_INIT_BRIGHT, AodConfigContract.DEFAULT_INIT_BRIGHT)
        )
        val multiplier = AodValueSanitizer.sanitizeRunningMultiplier(
            p.getFloat(
                AodConfigContract.KEY_RUNNING_MULTIPLIER,
                AodConfigContract.DEFAULT_RUNNING_MULTIPLIER
            ),
            AodConfigContract.DEFAULT_RUNNING_MULTIPLIER,
        )

        return MatrixCursor(COLUMNS).apply {
            addRow(arrayOf<Any>(
                initDark,
                initBright,
                multiplier,
                if (p.getBoolean(AodConfigContract.KEY_USE_SYSTEM_INIT_DARK, AodConfigContract.DEFAULT_USE_SYSTEM_INIT_DARK)) 1 else 0,
                if (p.getBoolean(AodConfigContract.KEY_USE_SYSTEM_INIT_BRIGHT, AodConfigContract.DEFAULT_USE_SYSTEM_INIT_BRIGHT)) 1 else 0,
                if (p.getBoolean(AodConfigContract.KEY_USE_SYSTEM_RUNNING_MULTIPLIER, AodConfigContract.DEFAULT_USE_SYSTEM_RUNNING_MULTIPLIER)) 1 else 0,
                if (p.getBoolean(AodConfigContract.KEY_ENABLE_PANORAMIC, AodConfigContract.DEFAULT_ENABLE_PANORAMIC)) 1 else 0,
                if (p.getBoolean(AodConfigContract.KEY_ENABLE_SETTINGS_SUPPORT, AodConfigContract.DEFAULT_ENABLE_SETTINGS_SUPPORT)) 1 else 0,
                if (p.getBoolean(AodConfigContract.KEY_BLOCK_SINGLE_CLICK, AodConfigContract.DEFAULT_BLOCK_SINGLE_CLICK)) 1 else 0,
                if (p.getBoolean(AodConfigContract.KEY_BLOCK_LOW_LIGHT_HIDE, AodConfigContract.DEFAULT_BLOCK_LOW_LIGHT_HIDE)) 1 else 0,
            ))
        }
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        if (matcher.match(uri) != 1) return 0
        enforceWriteAccess()
        val p = prefs() ?: return 0
        val input = values ?: return 0
        val e = p.edit()
        var changed = false

        if (input.containsKey(AodConfigContract.KEY_INIT_DARK)) {
            val raw = safeInt(input, AodConfigContract.KEY_INIT_DARK, AodConfigContract.DEFAULT_INIT_DARK)
            e.putInt(AodConfigContract.KEY_INIT_DARK, AodValueSanitizer.sanitizeBrightness(raw))
            changed = true
        }
        if (input.containsKey(AodConfigContract.KEY_INIT_BRIGHT)) {
            val raw = safeInt(input, AodConfigContract.KEY_INIT_BRIGHT, AodConfigContract.DEFAULT_INIT_BRIGHT)
            e.putInt(AodConfigContract.KEY_INIT_BRIGHT, AodValueSanitizer.sanitizeBrightness(raw))
            changed = true
        }
        if (input.containsKey(AodConfigContract.KEY_RUNNING_MULTIPLIER)) {
            val raw = safeFloat(
                input,
                AodConfigContract.KEY_RUNNING_MULTIPLIER,
                AodConfigContract.DEFAULT_RUNNING_MULTIPLIER,
            )
            e.putFloat(
                AodConfigContract.KEY_RUNNING_MULTIPLIER,
                AodValueSanitizer.sanitizeRunningMultiplier(
                    raw,
                    AodConfigContract.DEFAULT_RUNNING_MULTIPLIER,
                )
            )
            changed = true
        }
        if (input.containsKey(AodConfigContract.KEY_USE_SYSTEM_INIT_DARK)) {
            e.putBoolean(
                AodConfigContract.KEY_USE_SYSTEM_INIT_DARK,
                safeBoolean(input, AodConfigContract.KEY_USE_SYSTEM_INIT_DARK, AodConfigContract.DEFAULT_USE_SYSTEM_INIT_DARK)
            )
            changed = true
        }
        if (input.containsKey(AodConfigContract.KEY_USE_SYSTEM_INIT_BRIGHT)) {
            e.putBoolean(
                AodConfigContract.KEY_USE_SYSTEM_INIT_BRIGHT,
                safeBoolean(input, AodConfigContract.KEY_USE_SYSTEM_INIT_BRIGHT, AodConfigContract.DEFAULT_USE_SYSTEM_INIT_BRIGHT)
            )
            changed = true
        }
        if (input.containsKey(AodConfigContract.KEY_USE_SYSTEM_RUNNING_MULTIPLIER)) {
            e.putBoolean(
                AodConfigContract.KEY_USE_SYSTEM_RUNNING_MULTIPLIER,
                safeBoolean(input, AodConfigContract.KEY_USE_SYSTEM_RUNNING_MULTIPLIER, AodConfigContract.DEFAULT_USE_SYSTEM_RUNNING_MULTIPLIER)
            )
            changed = true
        }
        if (input.containsKey(AodConfigContract.KEY_ENABLE_PANORAMIC)) {
            e.putBoolean(
                AodConfigContract.KEY_ENABLE_PANORAMIC,
                safeBoolean(input, AodConfigContract.KEY_ENABLE_PANORAMIC, AodConfigContract.DEFAULT_ENABLE_PANORAMIC)
            )
            changed = true
        }
        if (input.containsKey(AodConfigContract.KEY_ENABLE_SETTINGS_SUPPORT)) {
            e.putBoolean(
                AodConfigContract.KEY_ENABLE_SETTINGS_SUPPORT,
                safeBoolean(input, AodConfigContract.KEY_ENABLE_SETTINGS_SUPPORT, AodConfigContract.DEFAULT_ENABLE_SETTINGS_SUPPORT)
            )
            changed = true
        }
        if (input.containsKey(AodConfigContract.KEY_BLOCK_SINGLE_CLICK)) {
            e.putBoolean(
                AodConfigContract.KEY_BLOCK_SINGLE_CLICK,
                safeBoolean(input, AodConfigContract.KEY_BLOCK_SINGLE_CLICK, AodConfigContract.DEFAULT_BLOCK_SINGLE_CLICK)
            )
            changed = true
        }
        if (input.containsKey(AodConfigContract.KEY_BLOCK_LOW_LIGHT_HIDE)) {
            e.putBoolean(
                AodConfigContract.KEY_BLOCK_LOW_LIGHT_HIDE,
                safeBoolean(input, AodConfigContract.KEY_BLOCK_LOW_LIGHT_HIDE, AodConfigContract.DEFAULT_BLOCK_LOW_LIGHT_HIDE)
            )
            changed = true
        }

        if (!changed) return 0
        if (!e.commit()) return 0

        context?.contentResolver?.notifyChange(CONTENT_URI, null)
        return 1
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method != DebugLogContract.METHOD_APPEND_DEBUG_LOG) {
            return super.call(method, arg, extras)
        }
        enforceDebugLogAccess()
        if (!BuildConfig.DEBUG) {
            return Bundle().apply {
                putString(DebugLogContract.RESULT_ERROR, "file logging is disabled in non-debug builds")
            }
        }

        val batch = extras?.getString(DebugLogContract.EXTRA_BATCH).orEmpty()
        if (batch.isEmpty()) return Bundle.EMPTY
        val bytes = batch.toByteArray(Charsets.UTF_8)
        if (bytes.size > DebugLogContract.MAX_BATCH_BYTES) {
            return Bundle().apply {
                putString(DebugLogContract.RESULT_ERROR, "batch exceeds ${DebugLogContract.MAX_BATCH_BYTES} bytes")
            }
        }

        return appendDebugLog(bytes)
    }

    private fun appendDebugLog(bytes: ByteArray): Bundle {
        val ctx = context ?: return Bundle().apply {
            putString(DebugLogContract.RESULT_ERROR, "provider context unavailable")
        }

        val fixedDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            DEBUG_DIR_NAME,
        )
        val hasExternalAccess = Environment.isExternalStorageManager()
        val fallbackDir = ctx.getExternalFilesDir("debug-log") ?: File(ctx.filesDir, "debug-log")
        val targetDir = if (hasExternalAccess) fixedDir else fallbackDir

        return runCatching {
            synchronized(debugLogLock) {
                if (!targetDir.exists() && !targetDir.mkdirs()) {
                    error("cannot create log directory: ${targetDir.absolutePath}")
                }

                if (hasExternalAccess) {
                    migrateFallbackLogs(fallbackDir, fixedDir)
                }

                val current = File(targetDir, CURRENT_LOG_NAME)
                if (current.exists() && current.length() + bytes.size > MAX_LOG_BYTES) {
                    rotateCurrentLog(current, targetDir)
                }

                FileOutputStream(current, true).use { out ->
                    out.write(bytes)
                    if (bytes.isNotEmpty() && bytes.last() != 10.toByte()) out.write(10)
                    out.flush()
                }
                pruneRotatedLogs(targetDir)

                Bundle().apply {
                    putString(DebugLogContract.RESULT_PATH, current.absolutePath)
                    putBoolean(DebugLogContract.RESULT_EXTERNAL, hasExternalAccess)
                }
            }
        }.getOrElse { t ->
            Bundle().apply {
                putString(DebugLogContract.RESULT_PATH, targetDir.absolutePath)
                putBoolean(DebugLogContract.RESULT_EXTERNAL, hasExternalAccess)
                putString(DebugLogContract.RESULT_ERROR, t.message ?: t::class.java.simpleName)
            }
        }
    }

    private fun rotateCurrentLog(current: File, dir: File) {
        if (!current.exists()) return
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date())
        val rotated = File(dir, "aod-debug-$stamp.log")
        if (!current.renameTo(rotated)) {
            current.copyTo(rotated, overwrite = true)
            current.delete()
        }
    }

    private fun pruneRotatedLogs(dir: File) {
        val rotated = dir.listFiles { file ->
            file.isFile && file.name.startsWith("aod-debug-") && file.name.endsWith(".log")
        }.orEmpty().sortedByDescending { it.lastModified() }
        rotated.drop(MAX_ROTATED_LOGS).forEach { runCatching { it.delete() } }
    }

    private fun migrateFallbackLogs(fallbackDir: File, fixedDir: File) {
        if (!fallbackDir.exists() || fallbackDir.absolutePath == fixedDir.absolutePath) return
        if (!fixedDir.exists() && !fixedDir.mkdirs()) return

        fallbackDir.listFiles { file -> file.isFile && file.name.endsWith(".log") }
            .orEmpty()
            .forEach { source ->
                val stamp = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date(source.lastModified()))
                val target = File(fixedDir, "aod-debug-recovered-$stamp.log")
                runCatching {
                    source.copyTo(target, overwrite = false)
                    source.delete()
                }
            }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun getType(uri: Uri): String? =
        if (matcher.match(uri) == 1) "vnd.android.cursor.item/vnd.$AUTHORITY.$PATH_CONFIG" else null

    private fun enforceReadAccess() {
        val uid = Binder.getCallingUid()
        if (uid == Process.myUid() || uid == Process.SYSTEM_UID) return
        val packages = context?.packageManager?.getPackagesForUid(uid).orEmpty()
        if (packages.any(ALLOWED_READER_PACKAGES::contains)) return
        throw SecurityException("Caller uid=$uid is not allowed to read AOD Enhance configuration")
    }

    private fun enforceWriteAccess() {
        val uid = Binder.getCallingUid()
        if (uid != Process.myUid()) {
            throw SecurityException("Caller uid=$uid is not allowed to modify AOD Enhance configuration")
        }
    }

    private fun enforceDebugLogAccess() {
        val uid = Binder.getCallingUid()
        if (uid == Process.myUid() || uid == Process.SYSTEM_UID) return
        val packages = context?.packageManager?.getPackagesForUid(uid).orEmpty()
        if (packages.any(ALLOWED_READER_PACKAGES::contains)) return
        throw SecurityException("Caller uid=$uid is not allowed to append AOD Enhance debug logs")
    }

    private fun safeInt(values: ContentValues, key: String, fallback: Int): Int =
        runCatching { values.getAsInteger(key) }.getOrNull() ?: fallback

    private fun safeFloat(values: ContentValues, key: String, fallback: Float): Float =
        runCatching { values.getAsFloat(key) }.getOrNull() ?: fallback

    private fun safeBoolean(values: ContentValues, key: String, fallback: Boolean): Boolean =
        runCatching { values.getAsBoolean(key) }.getOrNull() ?: fallback
}
