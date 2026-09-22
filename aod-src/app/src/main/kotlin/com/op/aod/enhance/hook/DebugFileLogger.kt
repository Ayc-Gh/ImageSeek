package com.op.aod.enhance.hook

import android.app.Application
import android.os.Bundle
import android.os.Process
import android.util.Log
import com.op.aod.enhance.BuildConfig
import com.op.aod.enhance.data.AodConfigProvider
import com.op.aod.enhance.data.DebugLogContract
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

internal object DebugFileLogger {
    private data class Event(val time: Long, val level: Char, val area: String, val message: String, val pid: Int, val tid: Int, val thread: String)
    private const val TAG = "AOD_Enhance"
    private const val CAPACITY = 4096
    private const val MAX_BATCH = 256
    private const val COALESCE_MS = 150L
    private val queue = ArrayBlockingQueue<Event>(CAPACITY)
    private val started = AtomicBoolean(false)
    private val dropped = AtomicLong(0L)
    @Volatile private var lastPath: String? = null
    private val processName by lazy { runCatching { Application.getProcessName() }.getOrDefault("unknown") }

    fun d(area: String, message: String) = log('D', area, message, null)
    fun i(area: String, message: String) = log('I', area, message, null)
    fun w(area: String, message: String, t: Throwable? = null) = log('W', area, message, t)
    fun e(area: String, message: String, t: Throwable? = null) = log('E', area, message, t)

    private fun log(level: Char, area: String, message: String, t: Throwable?) {
        if (!BuildConfig.DEBUG) return
        val detail = buildString { append(message.replace('\n', ' ')); if (t != null) { append(" | "); append(t::class.java.simpleName); append(": "); append(t.message ?: "no-message") } }
        when (level) { 'E' -> Log.e(TAG, "[$area] $detail", t); 'W' -> Log.w(TAG, "[$area] $detail", t); 'I' -> Log.i(TAG, "[$area] $detail"); else -> Log.d(TAG, "[$area] $detail") }
        val ok = queue.offer(Event(System.currentTimeMillis(), level, area, detail, Process.myPid(), Process.myTid(), Thread.currentThread().name))
        if (!ok) dropped.incrementAndGet()
        startWorker()
    }

    private fun startWorker() {
        if (!started.compareAndSet(false, true)) return
        Thread({ workerLoop() }, "AOD-Enhance-FileLogger").apply { isDaemon = true; start() }
    }

    private fun workerLoop() {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        val batch = ArrayList<Event>(MAX_BATCH)
        while (true) {
            try {
                val ctx = MainHook.hostAppContext
                if (ctx == null) { Thread.sleep(250L); continue }
                val first = queue.poll(1, TimeUnit.SECONDS) ?: continue
                batch.clear(); batch.add(first); Thread.sleep(COALESCE_MS); queue.drainTo(batch, MAX_BATCH - 1)
                val lost = dropped.getAndSet(0L)
                val text = buildString {
                    if (lost > 0) append(fmt.format(Date())).append(" W pid=").append(Process.myPid()).append(" process=").append(processName).append(" [LOGGER] dropped=").append(lost).append(" because queue was full\n")
                    for (e in batch) append(fmt.format(Date(e.time))).append(' ').append(e.level).append(" pid=").append(e.pid).append(" tid=").append(e.tid).append(" process=").append(processName).append(" thread=").append(e.thread).append(" [").append(e.area).append("] ").append(e.message).append('\n')
                }
                val result = ctx.contentResolver.call(AodConfigProvider.CONTENT_URI, DebugLogContract.METHOD_APPEND_DEBUG_LOG, null, Bundle().apply { putString(DebugLogContract.EXTRA_BATCH, text) })
                val err = result?.getString(DebugLogContract.RESULT_ERROR)
                if (!err.isNullOrBlank()) Log.w(TAG, "file logger provider error: $err")
                val path = result?.getString(DebugLogContract.RESULT_PATH)
                if (!path.isNullOrBlank() && path != lastPath) { lastPath = path; Log.i(TAG, "Debug file log path: $path") }
            } catch (ie: InterruptedException) { return
            } catch (t: Throwable) { Log.w(TAG, "file logger worker failure: ${t.message}"); try { Thread.sleep(1000L) } catch (_: InterruptedException) { return } }
        }
    }
}
