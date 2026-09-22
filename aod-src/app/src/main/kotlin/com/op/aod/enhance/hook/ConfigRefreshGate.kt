package com.op.aod.enhance.hook

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

internal class ConfigRefreshGate(private val backoffNs: Long) {
    private val refreshing = AtomicBoolean(false)
    private val retryAfterNs = AtomicLong(0L)

    fun tryAcquire(nowNs: Long, force: Boolean): Boolean {
        if (!force && nowNs < retryAfterNs.get()) return false
        return refreshing.compareAndSet(false, true)
    }

    fun success() {
        retryAfterNs.set(0L)
        refreshing.set(false)
    }

    fun failure(nowNs: Long) {
        retryAfterNs.set(nowNs + backoffNs)
        refreshing.set(false)
    }
}
