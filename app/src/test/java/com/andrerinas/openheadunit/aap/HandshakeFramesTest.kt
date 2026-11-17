package com.andrerinas.openheadunit.aap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HandshakeFramesTest {
    private fun header(channel: Int, flags: Int, length: Int, type: Int) = byteArrayOf(
        channel.toByte(), flags.toByte(), (length shr 8).toByte(), length.toByte(), (type shr 8).toByte(), type.toByte()
    )

    @Test
    fun `TLS handshake frames from the phone are read`() {
        assertTrue(HandshakeFrames.isTlsHandshake(header(0, 3, 600, 3)))
    }

    @Test
    fun `a late version response is skipped whole`() {
        // As read on the car: 12 bytes on the wire, length 8 (type + major, minor, status).
        val h = header(0, 3, 8, 2)
        assertFalse(HandshakeFrames.isTlsHandshake(h))
        assertEquals(6, HandshakeFrames.bytesLeft(h))
    }

    @Test
    fun `a frame of the previous session is told apart`() {
        // As read on the car after a restart with the phone plugged in: encrypted video.
        val h = header(6, 0x0b, 8231, 0x1703)
        assertFalse(HandshakeFrames.isTlsHandshake(h))
        assertTrue(HandshakeFrames.isEncrypted(h))
        assertFalse(HandshakeFrames.isEncrypted(header(0, 3, 8, 2)))
    }

    @Test
    fun `a first fragment also carries its total size`() {
        assertEquals(16384 + 4 - 2, HandshakeFrames.bytesLeft(header(3, 0x09, 16384, 0)))
    }

    @Test
    fun `an encrypted control frame is not taken for a handshake one`() {
        assertFalse(HandshakeFrames.isTlsHandshake(header(0, 0x0b, 30, 3)))
    }

    @Test
    fun `a length too short for the type is refused`() {
        assertEquals(-1, HandshakeFrames.bytesLeft(header(0, 3, 1, 2)))
    }
}
