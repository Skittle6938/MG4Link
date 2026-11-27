package com.andrerinas.openheadunit.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Which channel the hotspot opens on before any scan. */
class WifiChannelChoiceTest {

    @Test fun `the channel shared with the car's Wi-Fi now comes first`() {
        assertEquals(44, WifiChannelPicker.choose(shared = 44, fixed = 36, keepCarWifi = true, lastShared = 40))
    }

    @Test fun `a channel set by hand comes next`() {
        assertEquals(40, WifiChannelPicker.choose(shared = null, fixed = 40, keepCarWifi = true, lastShared = 44))
    }

    @Test fun `with the option, the last shared channel is kept so the hotspot need not move`() {
        assertEquals(44, WifiChannelPicker.choose(shared = null, fixed = 0, keepCarWifi = true, lastShared = 44))
    }

    @Test fun `without the option, or with nothing shared yet, the least busy channel is picked`() {
        assertNull(WifiChannelPicker.choose(shared = null, fixed = 0, keepCarWifi = false, lastShared = 44))
        assertNull(WifiChannelPicker.choose(shared = null, fixed = 0, keepCarWifi = true, lastShared = 0))
    }
}
