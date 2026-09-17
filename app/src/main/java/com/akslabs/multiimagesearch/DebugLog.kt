package com.akslabs.multiimagesearch

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Process
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal object DebugLog {
    private const val TAG = "ImageSeekDebug"
    private const val LOG_RELATIVE_PATH = "Download/ImageSeek/logs"
    private const val ASSET_RELATIVE_PATH = "Download/ImageSeek/logs/assets"

    private val lock = Any()
    private val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)
    private val filenameTimestamp = SimpleDateFormat("yyyyMMdd-HHmmss.SSS", Locale.US)

    @Volatile private var enabled = false
    private var appContext: Context? = null
    private var writer: BufferedWriter? = null
    private var logUri: Uri? = null
    private var logDisplayName: String? = null
    private var fallbackLogFile: File? = null
    private var previousExceptionHandler: Thread.UncaughtExceptionHandler? = null

    fun start(context: Context) {
        val app = context.applicationContext
        val debuggable = app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (!debuggable) return

        synchronized(lock) {
            if (enabled && writer != null) return
            enabled = true
            appContext = app

            val now = Date()
            val name = "ImageSeek-debug-${filenameTimestamp.format(now)}-pid${Process.myPid()}.log"
            logDisplayName = name

            runCatching {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, LOG_RELATIVE_PATH)
                }
                val uri = app.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("MediaStore insert returned null")
                val stream = app.contentResolver.openOutputStream(uri, "w")
                    ?: error("MediaStore openOutputStream returned null")
                logUri = uri
                writer = BufferedWriter(OutputStreamWriter(stream, StandardCharsets.UTF_8), 32 * 1024)
            }.onFailure { mediaStoreFailure ->
                runCatching {
                    val base = app.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: app.filesDir
                    val dir = File(base, "ImageSeek/logs").apply { mkdirs() }
                    val file = File(dir, name)
                    fallbackLogFile = file
                    writer = file.outputStream().bufferedWriter(StandardCharsets.UTF_8, 32 * 1024)
                }.onFailure {
                    Log.e(TAG, "Unable to create debug log", mediaStoreFailure)
                    Log.e(TAG, "Fallback log creation also failed", it)
                }
            }

            previousExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                exception("CRASH", throwable, "Uncaught exception on thread=${thread.name}/${thread.id}")
                runCatching { writer?.flush() }
                previousExceptionHandler?.uncaughtException(thread, throwable)
            }

            i("LOGGER", "================ ImageSeek detailed debug session ================")
            i("LOGGER", "logName=$name")
            i("LOGGER", "logUri=${logUri ?: "null"}")
            i("LOGGER", "fallbackFile=${fallbackLogFile?.absolutePath ?: "null"}")
            i("LOGGER", "publicRelativePath=$LOG_RELATIVE_PATH")
            i("DEVICE", "manufacturer=${Build.MANUFACTURER}")
            i("DEVICE", "brand=${Build.BRAND}")
            i("DEVICE", "model=${Build.MODEL}")
            i("DEVICE", "device=${Build.DEVICE}")
            i("DEVICE", "product=${Build.PRODUCT}")
            i("DEVICE", "hardware=${Build.HARDWARE}")
            i("DEVICE", "fingerprint=${Build.FINGERPRINT}")
            i("DEVICE", "androidRelease=${Build.VERSION.RELEASE} sdk=${Build.VERSION.SDK_INT} securityPatch=${Build.VERSION.SECURITY_PATCH}")
            i("DEVICE", "abis=${Build.SUPPORTED_ABIS.joinToString()}")
            i("PROCESS", "pid=${Process.myPid()} uid=${Process.myUid()} package=${app.packageName}")
        }
    }

    fun isEnabled(): Boolean = enabled

    fun location(): String = synchronized(lock) {
        when {
            logUri != null -> "$LOG_RELATIVE_PATH/${logDisplayName ?: "unknown.log"} | uri=$logUri"
            fallbackLogFile != null -> fallbackLogFile!!.absolutePath
            else -> "日志文件未创建"
        }
    }

    fun d(tag: String, message: String) = write("DEBUG", tag, message, Log.DEBUG)
    fun i(tag: String, message: String) = write("INFO", tag, message, Log.INFO)
    fun w(tag: String, message: String) = write("WARN", tag, message, Log.WARN)
    fun e(tag: String, message: String) = write("ERROR", tag, message, Log.ERROR)

    fun exception(tag: String, throwable: Throwable, message: String = "") {
        val prefix = if (message.isBlank()) "" else "$message\n"
        write("ERROR", tag, prefix + throwable.stackTraceToString(), Log.ERROR)
    }

    fun intent(intent: Intent?, label: String) {
        if (!enabled) return
        if (intent == null) {
            w("INTENT", "$label: null")
            return
        }
        val extrasText = runCatching {
            intent.extras?.keySet()?.sorted()?.joinToString(prefix = "{", postfix = "}") { key ->
                val value = intent.extras?.get(key)
                "$key=${value?.let { "${it::class.java.name}:$it" } ?: "null"}"
            } ?: "{}"
        }.getOrElse { "<extras read failed: ${it.stackTraceToString()}>" }
        val clipText = runCatching {
            val clip = intent.clipData ?: return@runCatching "null"
            buildString {
                append("description=").append(clip.description).append(" items=").append(clip.itemCount)
                for (index in 0 until clip.itemCount) {
                    val item = clip.getItemAt(index)
                    append("\n  [$index] uri=").append(item.uri)
                    append(" text=").append(item.text)
                    append(" htmlText=").append(item.htmlText)
                    append(" intent=").append(item.intent)
                }
            }
        }.getOrElse { "<clipData read failed: ${it.stackTraceToString()}>" }
        i(
            "INTENT",
            "$label action=${intent.action} type=${intent.type} data=${intent.data} flags=0x${intent.flags.toString(16)} categories=${intent.categories} component=${intent.component} package=${intent.`package`} selector=${intent.selector}\nextras=$extrasText\nclipData=$clipText"
        )
    }

    fun uriMetadata(context: Context, uri: Uri, label: String) {
        if (!enabled) return
        val metadata = runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use "cursorEmpty"
                buildString {
                    append("columns=")
                    cursor.columnNames.forEachIndexed { index, column ->
                        if (index > 0) append(" | ")
                        val value = runCatching { cursor.getString(index) }.getOrElse { "<${it.javaClass.simpleName}>" }
                        append(column).append('=').append(value)
                    }
                }
            } ?: "queryReturnedNull"
        }.getOrElse { "queryFailed=${it.stackTraceToString()}" }
        i("URI", "$label uri=$uri scheme=${uri.scheme} authority=${uri.authority} path=${uri.path} query=${uri.query} fragment=${uri.fragment} mime=${context.contentResolver.getType(uri)} metadata=$metadata")
    }

    fun captureSource(context: Context, uri: Uri, label: String): String? {
        if (!enabled) return null
        return runCatching {
            val resolver = context.contentResolver
            var displayName: String? = null
            var declaredSize: Long? = null
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) displayName = cursor.getString(nameIndex)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) declaredSize = cursor.getLong(sizeIndex)
                }
            }
            val mime = resolver.getType(uri) ?: "application/octet-stream"
            val extension = displayName?.substringAfterLast('.', "bin")?.takeIf { it.length in 1..10 } ?: "bin"
            val assetName = "${filenameTimestamp.format(Date())}-${sanitize(label)}-source.$extension"
            val target = createDownloadTarget(context, assetName, mime, ASSET_RELATIVE_PATH)
            var copied = 0L
            resolver.openInputStream(uri)?.use { input ->
                target.output.use { output -> copied = input.copyTo(output, 128 * 1024) }
            } ?: error("openInputStream returned null")
            val result = "name=$assetName location=${target.location} bytes=$copied declaredSize=$declaredSize mime=$mime sourceUri=$uri"
            i("CAPTURE", result)
            result
        }.onFailure { exception("CAPTURE", it, "captureSource failed label=$label uri=$uri") }.getOrNull()
    }

    fun captureBytes(context: Context, label: String, extension: String, mime: String, bytes: ByteArray): String? {
        if (!enabled) return null
        return runCatching {
            val assetName = "${filenameTimestamp.format(Date())}-${sanitize(label)}.$extension"
            val target = createDownloadTarget(context, assetName, mime, ASSET_RELATIVE_PATH)
            target.output.use { it.write(bytes) }
            val sha = sha256(bytes)
            val result = "name=$assetName location=${target.location} bytes=${bytes.size} sha256=$sha mime=$mime"
            i("CAPTURE", result)
            result
        }.onFailure { exception("CAPTURE", it, "captureBytes failed label=$label") }.getOrNull()
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private data class DownloadTarget(val output: OutputStream, val location: String)

    private fun createDownloadTarget(context: Context, name: String, mime: String, relativePath: String): DownloadTarget {
        return runCatching {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("MediaStore insert returned null")
            val stream = context.contentResolver.openOutputStream(uri, "w")
                ?: error("MediaStore openOutputStream returned null")
            DownloadTarget(stream, "$relativePath/$name | uri=$uri")
        }.getOrElse {
            val base = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
            val dir = File(base, "ImageSeek/logs/assets").apply { mkdirs() }
            val file = File(dir, name)
            DownloadTarget(FileOutputStream(file), file.absolutePath)
        }
    }

    private fun sanitize(value: String): String = value.replace(Regex("[^A-Za-z0-9._-]+"), "_").take(80)

    private fun write(level: String, tag: String, message: String, logcatPriority: Int) {
        if (!enabled) return
        val line = synchronized(lock) {
            val now = timestamp.format(Date())
            val thread = Thread.currentThread()
            val formatted = "$now [$level] [${thread.name}:${thread.id}] [$tag] $message"
            runCatching {
                writer?.apply {
                    write(formatted)
                    newLine()
                    flush()
                }
            }.onFailure { Log.e(TAG, "File log write failed", it) }
            formatted
        }
        Log.println(logcatPriority, TAG, line)
    }
}
