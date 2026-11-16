package com.andrerinas.openheadunit.aap

import com.andrerinas.openheadunit.aap.protocol.proto.MediaPlayback
import com.google.protobuf.ByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AapMediaPlaybackTest {

    @Test
    fun `a middle fragment that starts like a status message still belongs to the metadata (#28)`() {
        var got: MediaPlayback.MediaMetaData? = null
        var status: MediaPlayback.MediaPlaybackStatus? = null
        val playback = AapMediaPlayback({ got = it }, { status = it })

        val art = ByteArray(40_000) { (it * 7).toByte() }
        val bytes = MediaPlayback.MediaMetaData.newBuilder()
            .setSong("Song").setArtist("Artist").setAlbumArt(ByteString.copyFrom(art)).build().toByteArray()
        // Split in three; the middle piece is made to begin with 0x80 0x01, the status message type.
        val a = 16_000
        val b = 32_000
        bytes[a] = 0x80.toByte(); bytes[a + 1] = 0x01
        art[a - (bytes.size - art.size)] = 0x80.toByte()
        val first = byteArrayOf(0x80.toByte(), 0x03) + bytes.copyOfRange(0, a)
        val middle = bytes.copyOfRange(a, b)
        val last = bytes.copyOfRange(b, bytes.size)

        playback.process(AapMessage(9, 0x09, 32771, 2, first.size, first))
        playback.process(AapMessage(9, 0x08, 32769, 0, middle.size, middle))
        playback.process(AapMessage(9, 0x0A, 0, 0, last.size, last))

        assertEquals("Song", got?.song)
        assertEquals(bytes.size, got!!.toByteArray().size)
        assertNull(status)
    }
}
