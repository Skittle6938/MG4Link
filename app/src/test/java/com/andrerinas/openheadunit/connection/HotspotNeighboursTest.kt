package com.andrerinas.openheadunit.connection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HotspotNeighboursTest {
    @Test
    fun `a phone that got an address shows as complete`() {
        assertEquals("192.168.33.24 (complete)",
            HotspotNeighbours.row("192.168.33.24    0x1         0x2         aa:bb:cc:dd:ee:ff     *        ap0", "ap0"))
    }

    @Test
    fun `an address asked for and never answered shows as incomplete`() {
        assertEquals("192.168.33.24 (incomplete)",
            HotspotNeighbours.row("192.168.33.24    0x1         0x0         00:00:00:00:00:00     *        ap0", "ap0"))
    }

    @Test
    fun `other interfaces are left out`() {
        assertNull(HotspotNeighbours.row("192.168.225.1    0x1         0x2         aa:bb:cc:dd:ee:ff     *        tbox0", "ap0"))
    }
}
