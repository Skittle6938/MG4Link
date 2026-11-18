package com.andrerinas.openheadunit.connection

import org.junit.Assert.assertEquals
import org.junit.Test

class HotspotIpv6Test {
    @Test
    fun `the car scope is replaced by the phone Wi-Fi interface`() {
        assertEquals("fe80::a1b2:c3ff:fed4:e5f6%wlan0", HotspotIpv6.forPhone("fe80::a1b2:c3ff:fed4:e5f6%ap0"))
    }

    @Test
    fun `an address without scope gets the phone one`() {
        assertEquals("fe80::1%wlan0", HotspotIpv6.forPhone("fe80::1"))
    }
}
