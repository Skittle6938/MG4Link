package com.andrerinas.openheadunit.aap

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicAudioRecoveryTest {
    @Test
    fun `playing with the sink set up and no audio started recovers`() {
        assertTrue(MusicAudioRecovery.shouldRecover(1_000L, 2_000L, 0L, true, false))
    }

    @Test
    fun `audio already started, paused music or a second time does nothing`() {
        assertFalse(MusicAudioRecovery.shouldRecover(1_000L, 2_000L, 3_000L, true, false))
        assertFalse(MusicAudioRecovery.shouldRecover(1_000L, 2_000L, 0L, false, false))
        assertFalse(MusicAudioRecovery.shouldRecover(1_000L, 2_000L, 0L, true, true))
    }

    @Test
    fun `no media sink this session (music over Bluetooth) does nothing`() {
        assertFalse(MusicAudioRecovery.shouldRecover(1_000L, 500L, 0L, true, false))
        assertFalse(MusicAudioRecovery.shouldRecover(0L, 2_000L, 0L, true, false))
    }
}
