package com.andrerinas.openheadunit.connection

import org.junit.Assert.assertEquals
import org.junit.Test

class HotspotArpFixTest {
    @Test
    fun `a car that ignores ARP everywhere keeps it on the other interfaces and answers on the hotspot`() {
        // SWI132 R29: all=8, interfaces at 0 (the larger one applies).
        val values = mapOf("all" to 8, "default" to 8, "lo" to 0, "eth0" to 0, "tbox0" to 0, "ap0" to 0)
        assertEquals(
            listOf("eth0" to "8", "tbox0" to "8", "all" to "0", "ap0" to "1"),
            HotspotArpFix.plan(values, "ap0"),
        )
    }

    @Test
    fun `an interface already at least as strict is left alone`() {
        val values = mapOf("all" to 8, "eth0" to 8, "ap0" to 0)
        assertEquals(listOf("all" to "0", "ap0" to "1"), HotspotArpFix.plan(values, "ap0"))
    }

    @Test
    fun `a car that answers ARP only gets its hotspot value written back unchanged`() {
        // SWI69: all=0, the call only checks that netd takes it.
        assertEquals(listOf("ap0" to "0"), HotspotArpFix.plan(mapOf("all" to 0, "eth0" to 0, "ap0" to 0), "ap0"))
    }

    @Test
    fun `nothing without the values`() {
        assertEquals(emptyList<Pair<String, String>>(), HotspotArpFix.plan(mapOf("eth0" to 0), "ap0"))
    }
}
