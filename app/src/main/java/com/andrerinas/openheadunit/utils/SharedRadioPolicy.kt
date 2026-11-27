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

    /** How long a client switched on during a session may look for its network. */
    const val SEARCH_MS = 30_000L

    /**
     * The channel to open the access point on, or null when the client is switched off as usual:
     * option off, client off or not connected, or its network outside channels 36 to 48.
     */
    fun sharedChannel(optionOn: Boolean, clientOn: Boolean, clientConnected: Boolean, frequencyMhz: Int): Int? {
        if (!optionOn || !clientOn || !clientConnected) return null
        return channelOf(frequencyMhz)
    }

    /** The access point channel (36 to 48) a 5 GHz frequency is on, else null. */
    fun channelOf(frequencyMhz: Int): Int? {
        val channel = (frequencyMhz - 5000) / 5
        return channel.takeIf { frequencyMhz in 5170..5250 && it in WifiChannelPicker.CHANNELS }
    }

    /** A saved network of the car: its quoted SSID as WifiConfiguration holds it, and its id. */
    data class Saved(val quotedSsid: String, val networkId: Int)

    /** A network the last scan saw. */
    data class Seen(val ssid: String, val frequencyMhz: Int, val level: Int)

    /**
     * A saved network in range that the hotspot can share (channels 36 to 48), strongest first,
     * other than [currentNetworkId]: a box often has one name for 2.4 GHz and another for 5 GHz,
     * and the car may join the 2.4 GHz one by itself. Null when there is none.
     */
    fun shareableNetwork(saved: List<Saved>, seen: List<Seen>, currentNetworkId: Int): Int? = seen
        .filter { channelOf(it.frequencyMhz) != null }
        .sortedByDescending { it.level }
        .firstNotNullOfOrNull { scan ->
            saved.firstOrNull { it.quotedSsid == "\"" + scan.ssid + "\"" && it.networkId != currentNetworkId }
        }?.networkId

    enum class Client { OFF, SEARCHING, CONNECTED }

    enum class Step { NOTHING, WAIT, KEEP, RESTART_ACCESS_POINT, CLIENT_OFF }

    /**
     * What to do with the car's Wi-Fi client during a wireless session, checked every few seconds.
     *
     * - A client that was connected and lost its network (the car drove away) keeps scanning for
     *   it, which starves the access point: it goes off at once.
     * - A client switched on during the session (by the driver) gets [SEARCH_MS] to join a known
     *   network; Android Auto stutters while it scans. Joined on the access point's channel, it
     *   stays; on another channel from 36 to 48, the access point is restarted on it once (Android
     *   Auto reconnects, about 15 s); anywhere else, or after [SEARCH_MS], it goes off again.
     */
    fun duringSession(
        optionOn: Boolean,
        accessPointUp: Boolean,
        previous: Client,
        now: Client,
        searchingForMs: Long,
        clientChannel: Int?,
        accessPointChannel: Int,
        restartedAlready: Boolean,
    ): Step = when {
        !optionOn || !accessPointUp || now == Client.OFF -> Step.NOTHING
        now == Client.SEARCHING ->
            if (previous == Client.CONNECTED || searchingForMs >= SEARCH_MS) Step.CLIENT_OFF else Step.WAIT
        clientChannel == null -> Step.CLIENT_OFF
        clientChannel == accessPointChannel -> Step.KEEP
        restartedAlready -> Step.CLIENT_OFF
        else -> Step.RESTART_ACCESS_POINT
    }
}
