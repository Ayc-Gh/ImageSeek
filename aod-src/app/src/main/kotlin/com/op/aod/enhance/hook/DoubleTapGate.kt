package com.op.aod.enhance.hook

internal class DoubleTapGate(private val thresholdMs: Long = 350L) {
    private var lastBlockedAt = 0L

    fun shouldAllow(nowMs: Long): Boolean {
        val previous = lastBlockedAt
        if (previous != 0L && nowMs >= previous && nowMs - previous < thresholdMs) {
            lastBlockedAt = 0L
            return true
        }
        lastBlockedAt = nowMs
        return false
    }

    fun reset() { lastBlockedAt = 0L }
}
