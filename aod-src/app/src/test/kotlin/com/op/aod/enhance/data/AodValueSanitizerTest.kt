package com.op.aod.enhance.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AodValueSanitizerTest {
    @Test fun brightnessClamps() {
        assertEquals(0, AodValueSanitizer.sanitizeBrightness(-1))
        assertEquals(255, AodValueSanitizer.sanitizeBrightness(999))
    }
    @Test fun multiplierRejectsNonFinite() {
        assertEquals(1.6f, AodValueSanitizer.sanitizeRunningMultiplier(Float.NaN, 1.6f), 0.0f)
        assertEquals(2.0f, AodValueSanitizer.sanitizeRunningMultiplier(Float.POSITIVE_INFINITY, 2.0f), 0.0f)
    }
}
