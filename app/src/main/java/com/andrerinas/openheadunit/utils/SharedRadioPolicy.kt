package com.andrerinas.openheadunit.utils

/**
 * Experimental "keep the car's Wi-Fi": the car's Wi-Fi client stays on and the access point for
 * wireless Android Auto opens on the same channel, so the single radio never switches channels.
 *
 * The MG4's radio hops between channels when the client and the access point are on different
 * ones, and Android Auto stalls while it listens to the other network. On one shared channel the
 * two only share airtime. Its access point only takes channels 36 to 48, so the client's network
 * must be on one of them; otherwise the client goes off as usual.
 */
object SharedRadioPolicy {

    /**
     * The channel to open the access point on, or null when the client is switched off as usual:
     * option off, client off or not connected, or its network outside channels 36 to 48.
     */
    fun sharedChannel(optionOn: Boolean, clientOn: Boolean, clientConnected: Boolean, frequencyMhz: Int): Int? {
        if (!optionOn || !clientOn || !clientConnected) return null
        val channel = (frequencyMhz - 5000) / 5
        return channel.takeIf { frequencyMhz in 5170..5250 && it in WifiChannelPicker.CHANNELS }
    }

    /**
     * While sharing, a client that lost its network (the car drove away) keeps scanning for it,
     * and that starves the access point: it goes off then, for the rest of the session.
     */
    fun clientMustGoOff(optionOn: Boolean, clientOn: Boolean, clientConnected: Boolean, accessPointUp: Boolean): Boolean =
        optionOn && clientOn && !clientConnected && accessPointUp
}
