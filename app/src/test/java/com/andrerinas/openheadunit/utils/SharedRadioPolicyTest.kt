package com.andrerinas.openheadunit.utils

import com.andrerinas.openheadunit.utils.SharedRadioPolicy.Client
import com.andrerinas.openheadunit.utils.SharedRadioPolicy.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    private fun step(
        previous: Client, now: Client, searchingForMs: Long = 0, clientChannel: Int? = null,
        apChannel: Int = 36, restarted: Boolean = false, optionOn: Boolean = true, apUp: Boolean = true,
    ) = SharedRadioPolicy.duringSession(optionOn, apUp, previous, now, searchingForMs, clientChannel, apChannel, restarted)

    @Test fun `a client that lost its network goes off at once`() {
        assertEquals(Step.CLIENT_OFF, step(Client.CONNECTED, Client.SEARCHING))
    }

    @Test fun `a client switched on during the drive gets 30 s to find a network`() {
        assertEquals(Step.WAIT, step(Client.OFF, Client.SEARCHING))
        assertEquals(Step.WAIT, step(Client.SEARCHING, Client.SEARCHING, searchingForMs = 29_000))
        assertEquals(Step.CLIENT_OFF, step(Client.SEARCHING, Client.SEARCHING, searchingForMs = 30_000))
    }

    @Test fun `joined on the hotspot's channel it stays, on another one the hotspot moves once`() {
        assertEquals(Step.KEEP, step(Client.SEARCHING, Client.CONNECTED, clientChannel = 36, apChannel = 36))
        assertEquals(Step.RESTART_ACCESS_POINT, step(Client.SEARCHING, Client.CONNECTED, clientChannel = 44, apChannel = 36))
        assertEquals(Step.CLIENT_OFF, step(Client.SEARCHING, Client.CONNECTED, clientChannel = 44, apChannel = 36, restarted = true))
    }

    @Test fun `joined outside channels 36 to 48 it goes off`() {
        assertEquals(Step.CLIENT_OFF, step(Client.SEARCHING, Client.CONNECTED, clientChannel = null))
    }

    @Test fun `nothing without the option, the hotspot, or the client`() {
        assertEquals(Step.NOTHING, step(Client.OFF, Client.SEARCHING, optionOn = false))
        assertEquals(Step.NOTHING, step(Client.OFF, Client.SEARCHING, apUp = false))
        assertEquals(Step.NOTHING, step(Client.CONNECTED, Client.OFF))
    }
}
