package com.andrerinas.openheadunit.connection

import android.content.Context
import android.provider.Settings
import com.andrerinas.openheadunit.utils.AppLog
import java.net.Inet6Address
import java.net.NetworkInterface

/**
 * Wireless Android Auto over the hotspot's IPv6 link-local address (#30). Some SAIC firmwares
 * (SWI132 R29, SWI133) set arp_ignore=8, so the car answers no ARP request and the phone may never
 * find it over IPv4: its connection to 192.168.33.1 then fails with EHOSTUNREACH. arp_ignore and
 * the firewall script only cover IPv4; over IPv6 the phone finds the car with neighbour discovery,
 * which they leave alone, so the phone can be given the car's fe80:: address instead.
 *
 * Settings.Global "mg4link_wireless_ipv6": 1 always (for tests), 0 never. Unset: only on cars that
 * answer no ARP, and there IPv4 and IPv6 take turns after each attempt the phone gives up on, until
 * one connects; that one is remembered and used first from then on.
 */
object HotspotIpv6 {
    private const val TAG = "HotspotIpv6"
    const val SETTING = "mg4link_wireless_ipv6"
    private const val HOTSPOT_IFACE = "ap0"
    private const val PREFS = "hotspot_ipv6"
    private const val KEY_WORKED = "worked"

    /** The phone's Wi-Fi interface, for the scope of the link-local address it is given. */
    const val PHONE_IFACE = "wlan0"

    /** The family the next attempt uses, in automatic mode. */
    @Volatile private var nextIsIpv6: Boolean? = null

    private fun setting(context: Context): Int =
        runCatching { Settings.Global.getInt(context.contentResolver, SETTING, -1) }.getOrDefault(-1)

    /** Whether the IPv6 listener is opened at all. */
    fun enabled(context: Context): Boolean = when (setting(context)) {
        1 -> true
        0 -> false
        else -> CarNetworkQuirks.needsArpNudge()
    }

    /** Whether this attempt gives the phone the IPv6 address. */
    private fun useNow(context: Context): Boolean = when (setting(context)) {
        1 -> true
        0 -> false
        else -> CarNetworkQuirks.needsArpNudge() &&
            (nextIsIpv6 ?: prefs(context).getBoolean(KEY_WORKED, false).also { nextIsIpv6 = it })
    }

    /** The phone gave up on this attempt: in automatic mode, the next one uses the other family. */
    fun attemptFailed(context: Context) {
        if (setting(context) != -1 || !CarNetworkQuirks.needsArpNudge()) return
        val now = nextIsIpv6 ?: prefs(context).getBoolean(KEY_WORKED, false)
        nextIsIpv6 = !now
        AppLog.i("$TAG: the phone did not connect over ${family(now)}, the next attempt uses ${family(!now)}")
    }

    /** A phone connected: remember the family it used. */
    fun connected(context: Context, overIpv6: Boolean) {
        nextIsIpv6 = overIpv6
        if (!CarNetworkQuirks.needsArpNudge()) return
        if (prefs(context).getBoolean(KEY_WORKED, false) != overIpv6) {
            prefs(context).edit().putBoolean(KEY_WORKED, overIpv6).apply()
            AppLog.i("$TAG: the phone connected over ${family(overIpv6)}, used first from now on")
        }
    }

    private fun family(ipv6: Boolean) = if (ipv6) "IPv6" else "IPv4"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The hotspot's own link-local address, scoped to its interface (null when it has none). */
    fun listenAddress(iface: String = HOTSPOT_IFACE): Inet6Address? = runCatching {
        NetworkInterface.getByName(iface)?.inetAddresses?.toList()
            ?.filterIsInstance<Inet6Address>()?.firstOrNull { it.isLinkLocalAddress }
    }.getOrNull()

    /** What the phone is told to connect to: [ipv4], or the car's fe80:: address. */
    fun advertisedHost(context: Context, ipv4: String): String {
        if (!useNow(context)) return ipv4
        val addr = listenAddress()
        if (addr == null) {
            AppLog.w("$TAG: the hotspot has no IPv6 link-local address, the phone is given $ipv4")
            return ipv4
        }
        return forPhone(addr.hostAddress ?: return ipv4)
    }

    /** fe80::1%ap0 seen from the car becomes fe80::1%wlan0 for the phone. */
    fun forPhone(hostAddress: String): String = hostAddress.substringBefore('%') + "%" + PHONE_IFACE
}
