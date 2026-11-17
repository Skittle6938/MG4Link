package com.andrerinas.openheadunit.aap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HandshakeFramesTest {
    private fun header(channel: Int, flags: Int, total: Int, type: Int) = byteArrayOf(
        channel.toByte(), flags.toByte(), (total shr 8).toByte(), total.toByte(), (type shr 8).toByte(), type.toByte()
    )

    @Test
    fun `a version response after the exchange is recognised`() {
        // As read on the car: 12 bytes on the wire, length 8 (type + major, minor, status).
        val h = header(0, 3, 8, 2)
        assertTrue(HandshakeFrames.isLateVersionResponse(h))
        assertEquals(6, HandshakeFrames.payloadLength(h))
    }

    @Test
    fun `TLS records are never taken for one`() {
        assertFalse(HandshakeFrames.isLateVersionResponse(header(0, 3, 600, 3)))
    }

    @Test
    fun `only on the control channel`() {
        assertFalse(HandshakeFrames.isLateVersionResponse(header(1, 3, 8, 2)))
    }

    @Test
    fun `a length too short for the type is refused`() {
        assertEquals(-1, HandshakeFrames.payloadLength(header(0, 3, 1, 2)))
    }
}
