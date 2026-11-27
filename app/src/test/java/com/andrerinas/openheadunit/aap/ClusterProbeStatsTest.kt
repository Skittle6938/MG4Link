package com.andrerinas.openheadunit.aap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the cluster sink probe reports at the end of a session (#77). */
class ClusterProbeStatsTest {

    @Test
    fun `a sink the phone never opens says so`() {
        val s = ClusterProbeStats(1_000)
        assertEquals("declared, never opened", s.furthest)
        assertTrue(s.summary(61_000).startsWith("result=declared, never opened open=- setup=- start=-"))
    }

    @Test
    fun `each step the phone reaches moves the result on`() {
        val s = ClusterProbeStats(0)
        s.onOpen(100); assertEquals("opened, never set up", s.furthest)
        s.onSetup(200); assertEquals("set up, never started", s.furthest)
        s.onStart(300); assertEquals("started, no video", s.furthest)
        assertTrue(s.onFrame(codecConfig = true, size = 40, nowMs = 400))
        assertEquals("codec config only", s.furthest)
        assertFalse(s.onFrame(codecConfig = false, size = 1_000, nowMs = 500))
        assertEquals("streamed", s.furthest)
    }

    @Test
    fun `rate and bitrate come from the video span`() {
        val s = ClusterProbeStats(0)
        s.onOpen(10); s.onSetup(20); s.onStart(30)
        s.onFrame(codecConfig = true, size = 0, nowMs = 1_000)
        // 31 frames of 1250 bytes over 1 s after the codec config: 30 fps, 310 kbit/s.
        for (i in 0..30) s.onFrame(codecConfig = false, size = 1_250, nowMs = 1_000L + i * 1_000L / 30)
        val summary = s.summary(5_000)
        assertTrue(summary, summary.contains("frames=31"))
        assertTrue(summary, summary.contains("fps=30.0"))
        assertTrue(summary, summary.contains("kbps=310"))
        assertTrue(summary, summary.contains("firstVideo=+1000 ms"))
    }

    @Test
    fun `a first open time is kept when the phone reopens`() {
        val s = ClusterProbeStats(0)
        s.onOpen(100); s.onOpen(900)
        assertTrue(s.summary(1_000).contains("open=+100 ms"))
    }
}
