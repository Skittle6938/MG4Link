package com.andrerinas.openheadunit.aap

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbLinkResetPolicyTest {
    @Test
    fun `the first failure resets the link`() {
        assertTrue(UsbLinkResetPolicy.mayReset(nowMs = 5_000, lastResetMs = 0))
    }

    @Test
    fun `a failure right after a reset does not reset again`() {
        assertFalse(UsbLinkResetPolicy.mayReset(nowMs = 100_000, lastResetMs = 90_000))
    }

    @Test
    fun `a minute later it may`() {
        assertTrue(UsbLinkResetPolicy.mayReset(nowMs = 160_000, lastResetMs = 100_000))
    }
}
