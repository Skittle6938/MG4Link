package com.andrerinas.openheadunit.aap

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceNavigationTest {

    @Test fun `a label is said in the car's language`() {
        assertEquals("Navigue vers Ionity Mâcon Est", VoiceNavigation.phraseFor("fr", "Ionity Mâcon Est", null, null))
        assertEquals("Navigate to Ionity Mâcon Est", VoiceNavigation.phraseFor("ja", "Ionity Mâcon Est", null, null))
        assertEquals("Navigiere zu Lyon", VoiceNavigation.phraseFor("de", " Lyon ", 1.0, 2.0))
    }

    @Test fun `coordinates are used when there is no label, with a dot decimal separator`() {
        assertEquals("Navigue vers 45.76400, 4.83570", VoiceNavigation.phraseFor("fr", null, 45.764, 4.8357))
    }

    @Test fun `nothing to say without a label or valid coordinates`() {
        assertNull(VoiceNavigation.phraseFor("fr", "  ", null, null))
        assertNull(VoiceNavigation.phraseFor("fr", null, 120.0, 4.0))
    }

    @Test fun `silence pads the sentence on both sides`() {
        val speech = byteArrayOf(1, 2, 3, 4)
        val out = VoiceNavigation.withSilence(speech, 16_000, 10, 20)
        assertEquals(320 + 4 + 640, out.size)
        assertArrayEquals(speech, out.copyOfRange(320, 324))
    }

    @Test fun `a wav file is read and resampled to the mic rate`() {
        // 4 stereo frames at 32 kHz: left 100, right 300 -> mono 200.
        val data = ByteArray(16)
        for (i in 0 until 4) {
            data[i * 4] = 100; data[i * 4 + 2] = 44; data[i * 4 + 3] = 1 // 300 = 0x012C
        }
        val pcm = VoiceNavigation.Wav.parse(wav(32_000, 2, data))!!
        assertEquals(32_000, pcm.rate)
        assertEquals(2, pcm.channels)
        val mono = VoiceNavigation.Wav.toMono16(pcm, 16_000)
        assertEquals(4, mono.size) // 2 frames
        assertEquals(200, (mono[0].toInt() and 0xff) or (mono[1].toInt() shl 8))
    }

    @Test fun `anything that is not 16-bit pcm wav is refused`() {
        assertNull(VoiceNavigation.Wav.parse(ByteArray(10)))
        assertNull(VoiceNavigation.Wav.parse(wav(16_000, 1, ByteArray(4), bits = 8)))
    }

    private fun wav(rate: Int, channels: Int, data: ByteArray, bits: Int = 16): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        fun s(x: String) = out.write(x.toByteArray(Charsets.US_ASCII))
        fun i32(v: Int) { for (k in 0 until 4) out.write((v shr (8 * k)) and 0xff) }
        fun i16(v: Int) { out.write(v and 0xff); out.write((v shr 8) and 0xff) }
        s("RIFF"); i32(36 + data.size); s("WAVE")
        s("fmt "); i32(16); i16(1); i16(channels); i32(rate); i32(rate * channels * bits / 8); i16(channels * bits / 8); i16(bits)
        s("data"); i32(data.size); out.write(data)
        return out.toByteArray()
    }
}
