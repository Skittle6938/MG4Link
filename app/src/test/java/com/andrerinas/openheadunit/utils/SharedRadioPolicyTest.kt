package com.andrerinas.openheadunit.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Experimental "keep the car's Wi-Fi": the hotspot shares the client's channel. */
class SharedRadioPolicyTest {

    @Test fun `a client on channels 36 to 48 is kept, the hotspot takes its channel`() {
        assertEquals(36, SharedRadioPolicy.sharedChannel(true, true, true, 5180))
        assertEquals(44, SharedRadioPolicy.sharedChannel(true, true, true, 5220))
        assertEquals(48, SharedRadioPolicy.sharedChannel(true, true, true, 5240))
    }

    @Test fun `other channels and 2 point 4 GHz switch the client off as usual`() {
        assertNull(SharedRadioPolicy.sharedChannel(true, true, true, 5745)) // channel 149
        assertNull(SharedRadioPolicy.sharedChannel(true, true, true, 5260)) // channel 52, radar check
        assertNull(SharedRadioPolicy.sharedChannel(true, true, true, 2437)) // 2.4 GHz
    }

    @Test fun `only with the option on and a connected client`() {
        assertNull(SharedRadioPolicy.sharedChannel(false, true, true, 5180))
        assertNull(SharedRadioPolicy.sharedChannel(true, false, true, 5180))
        assertNull(SharedRadioPolicy.sharedChannel(true, true, false, 5180))
    }

    @Test fun `a client that lost its network goes off while the hotspot is up`() {
        assertTrue(SharedRadioPolicy.clientMustGoOff(optionOn = true, clientOn = true, clientConnected = false, accessPointUp = true))
        assertFalse(SharedRadioPolicy.clientMustGoOff(optionOn = true, clientOn = true, clientConnected = true, accessPointUp = true))
        assertFalse(SharedRadioPolicy.clientMustGoOff(optionOn = false, clientOn = true, clientConnected = false, accessPointUp = true))
        assertFalse(SharedRadioPolicy.clientMustGoOff(optionOn = true, clientOn = true, clientConnected = false, accessPointUp = false))
    }
}
