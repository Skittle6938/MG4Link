package com.andrerinas.openheadunit.aap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbVideoFallbackTest {
    @Test
    fun `no picture ten seconds after the video setup over USB falls back`() {
        assertTrue(UsbVideoFallback.shouldFallBack(true, 1_000L, false, 11_000L))
    }

    @Test
    fun `before ten seconds it waits`() {
        assertFalse(UsbVideoFallback.shouldFallBack(true, 1_000L, false, 10_999L))
    }

    @Test
    fun `a picture that arrived, a wireless session or no video setup never fall back`() {
        assertFalse(UsbVideoFallback.shouldFallBack(true, 1_000L, true, 60_000L))
        assertFalse(UsbVideoFallback.shouldFallBack(false, 1_000L, false, 60_000L))
        assertFalse(UsbVideoFallback.shouldFallBack(true, 0L, false, 60_000L))
    }

    @Test
    fun `the device key leaves out the parts the phone does not give`() {
        assertEquals("Ulefone Armor Pad Pro", UsbVideoFallback.key("Ulefone", "Armor Pad Pro"))
        assertEquals("Pixel 9a", UsbVideoFallback.key(" ", "Pixel 9a"))
        assertEquals(null, UsbVideoFallback.key(null, ""))
    }
}
