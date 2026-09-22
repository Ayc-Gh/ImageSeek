package com.op.aod.enhance.hook

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GateTest {
    @Test fun refreshGateSingleFlightAndBackoff() {
        val g = ConfigRefreshGate(5_000L)
        assertTrue(g.tryAcquire(100L, false))
        assertFalse(g.tryAcquire(100L, false))
        g.failure(100L)
        assertFalse(g.tryAcquire(1_000L, false))
        assertTrue(g.tryAcquire(1_000L, true))
    }
    @Test fun doubleTapUsesMonotonicInput() {
        val g = DoubleTapGate(350L)
        assertFalse(g.shouldAllow(1000L))
        assertTrue(g.shouldAllow(1200L))
        assertFalse(g.shouldAllow(900L))
    }
}
