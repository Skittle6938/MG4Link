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
    fun `a frame of the previous session is skipped whole`() {
        // Encrypted video: the two bytes after the length are the record's own (0x17 0x03).
        val h = header(3, 0x0b, 5000, 0x1703)
        assertFalse(HandshakeFrames.isTlsHandshake(h))
        assertEquals(4998, HandshakeFrames.bytesLeft(h))
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
