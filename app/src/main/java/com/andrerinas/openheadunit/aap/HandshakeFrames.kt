package com.andrerinas.openheadunit.aap

/**
 * Reading the frames that arrive during the TLS handshake. A frame starts with channel, flags
 * and a two-byte length; a first fragment that is not also the last adds the four-byte total
 * size. In a plain frame the length counts the two type bytes that follow.
 */
internal object HandshakeFrames {
    private const val SSL_HANDSHAKE = 3
    private const val FLAG_FIRST = 0x01
    private const val FLAG_LAST = 0x02
    private const val FLAG_ENCRYPTED = 0x08

    /** A plain TLS handshake frame on the control channel: the only kind the TLS phase reads. */
    fun isTlsHandshake(header: ByteArray): Boolean =
        header[0].toInt() == 0 && (header[1].toInt() and FLAG_ENCRYPTED) == 0 && u16(header, 4) == SSL_HANDSHAKE

    /** A frame of an established session: from a previous one when it comes during the handshake. */
    fun isEncrypted(header: ByteArray): Boolean = (header[1].toInt() and FLAG_ENCRYPTED) != 0

    /** Bytes of the frame still to read once its first six are in, or -1 for a length that cannot be. */
    fun bytesLeft(header: ByteArray): Int {
        val flags = header[1].toInt() and 0xFF
        val totalSize = if (flags and (FLAG_FIRST or FLAG_LAST) == FLAG_FIRST) 4 else 0
        val left = u16(header, 2) + totalSize - 2
        return if (left < 0) -1 else left
    }

    fun describe(header: ByteArray): String =
        "ch=${header[0].toInt() and 0xFF} flags=0x${Integer.toHexString(header[1].toInt() and 0xFF)} " +
            "len=${u16(header, 2)} type=${u16(header, 4)}"

    private fun u16(b: ByteArray, at: Int) = ((b[at].toInt() and 0xFF) shl 8) or (b[at + 1].toInt() and 0xFF)
}
