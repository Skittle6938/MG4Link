package com.andrerinas.openheadunit.aap

/**
 * Reading the plain frames of the handshake. A frame starts with channel, flags, a two-byte
 * length (which counts the two type bytes) and the two-byte message type.
 */
internal object HandshakeFrames {
    private const val VERSION_RESPONSE = 2

    /**
     * A version response arriving once the version exchange is over: the phone answered both the
     * version request and its repeat. TLS records come as type 3 (SSL handshake), never as 2.
     */
    fun isLateVersionResponse(header: ByteArray): Boolean =
        header.size >= 6 && header[0].toInt() == 0 &&
            (((header[4].toInt() and 0xFF) shl 8) or (header[5].toInt() and 0xFF)) == VERSION_RESPONSE

    /** Bytes after the type, or -1 when the length cannot even hold the type. */
    fun payloadLength(header: ByteArray): Int {
        val total = ((header[2].toInt() and 0xFF) shl 8) or (header[3].toInt() and 0xFF)
        return if (total < 2) -1 else total - 2
    }
}
