package com.andrerinas.openheadunit.connection

import android.os.Build
import com.andrerinas.openheadunit.utils.AppLog
import java.io.File
import java.net.ServerSocket

/**
 * Network quirks of the MG4/SAIC firmware, detected on the car itself.
 *
 * The SAIC `arp_update.sh` script (section "Disable arp" + firewall):
 *  - sets `arp_ignore=8` on the SWI133 firmware: the car answers no ARP request, even on the hotspot
 *    (SWI132 sets 1, SWI68/SWI69 do not set it);
 *  - installs `iptables INPUT ... REJECT` after a whitelist of ports (TCP 6010, 8080, 30517, 30518...)
 *    on SWI132/SWI133; SWI68/SWI69 only drop a few specific ports.
 *
 * Rather than a table of software versions (the firmware exposes no readable "SWI" number),
 * the real rules are read: the TCP port announced to the phone is picked among the allowed ports.
 */
object CarNetworkQuirks {
    private const val TAG = "CarNetworkQuirks"

    /** Usual wireless Android Auto port, used when no restrictive firewall is detected. */
    const val STANDARD_PORT = 5288

    /** Port allowed by every known SAIC firmware; fallback when the script cannot be read. */
    const val SAIC_FALLBACK_PORT = 30518

    private val SCRIPT_PATHS = listOf("/system/bin/arp_update.sh", "/vendor/bin/arp_update.sh")

    /** Preference order among the allowed ports: the ones least likely to be taken already come first. */
    private val PREFERRED = listOf(30518, 30517, 8080, 6010)

    private val ACCEPT_TCP = Regex("""INPUT\s+-p\s+tcp\s+--dport\s+(\d+)\s+-j\s+ACCEPT""")
    private val DEFAULT_REJECT = Regex("""INPUT\s+-j\s+(REJECT|DROP)\b""")

    data class Firewall(val source: String, val rejectsByDefault: Boolean, val allowedTcp: List<Int>)

    /** Extracts the TCP whitelist and whether there is a default reject. Commented-out lines are ignored. */
    fun parseFirewall(text: String, source: String): Firewall? {
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toList()
        val allowed = lines.flatMap { ACCEPT_TCP.findAll(it).map { m -> m.groupValues[1].toInt() }.toList() }.distinct()
        val rejects = lines.any { DEFAULT_REJECT.containsMatchIn(it) }
        if (allowed.isEmpty() && !rejects) return null
        return Firewall(source, rejects, allowed)
    }

    /** Picks the port to announce. [isFree] tests whether a port can be bound; returns (port, reason). */
    fun pickPort(firewall: Firewall?, isSaic: Boolean, isFree: (Int) -> Boolean): Pair<Int, String> {
        if (firewall != null && firewall.rejectsByDefault && firewall.allowedTcp.isNotEmpty()) {
            val candidates = PREFERRED.filter { it in firewall.allowedTcp } +
                firewall.allowedTcp.filter { it !in PREFERRED }
            val chosen = candidates.firstOrNull { isFree(it) } ?: candidates.first()
            return chosen to "restrictive firewall read from ${firewall.source} (allowed TCP: ${firewall.allowedTcp.joinToString(",")})"
        }
        if (firewall != null) {
            return STANDARD_PORT to "firewall read from ${firewall.source} without a default reject"
        }
        if (isSaic) {
            return SAIC_FALLBACK_PORT to "SAIC device, firewall script unreadable: falling back to a port allowed on every known firmware"
        }
        return STANDARD_PORT to "no SAIC firewall detected: standard port"
    }

    @Volatile private var cachedPort: Int? = null
    @Volatile private var cachedWhy: String = ""
    @Volatile private var cachedArpNudge: Boolean? = null

    private fun isSaicDevice(): Boolean =
        Build.MODEL.equals("SAIC", ignoreCase = true) ||
            Build.DEVICE.contains("saic", ignoreCase = true) ||
            Build.MANUFACTURER.equals("AUTUS", ignoreCase = true)

    private fun readFirewall(): Firewall? {
        for (path in SCRIPT_PATHS) {
            try {
                val f = File(path)
                if (f.isFile && f.canRead()) {
                    val text = f.readText()
                    // The rules as this firmware has them, once (#30: R29 differs from the R43 we know).
                    firewallLines(text).takeIf { it.isNotEmpty() }?.let {
                        AppLog.i("$TAG: firewall rules in $path: " + it.joinToString(" | "))
                    }
                    parseFirewall(text, path)?.let { return it }
                }
            } catch (e: Exception) {
                AppLog.d("$TAG: cannot read $path (${e.javaClass.simpleName}: ${e.message})")
            }
        }
        return null
    }

    private fun canBind(port: Int): Boolean = try {
        ServerSocket().use { s ->
            s.reuseAddress = true
            s.bind(java.net.InetSocketAddress(port))
        }
        true
    } catch (e: Exception) {
        false
    }

    /** Automatically chosen wireless TCP port (computed once, then cached for the lifetime of the process). */
    fun autoWirelessPort(): Int {
        cachedPort?.let { return it }
        synchronized(this) {
            cachedPort?.let { return it }
            val (port, why) = pickPort(readFirewall(), isSaicDevice(), ::canBind)
            cachedWhy = why
            cachedPort = port
            AppLog.i("$TAG: automatic wireless port = $port ($why).")
            return port
        }
    }

    /** Why the automatic choice was made (for diagnostics). */
    fun autoPortReason(): String {
        autoWirelessPort()
        return cachedWhy
    }

    /**
     * The ARP wake-up is only useful when the kernel does not answer ARP requests (arp_ignore >= 3);
     * when the value is unreadable it is enabled on SAIC devices (harmless).
     */
    fun needsArpNudge(): Boolean {
        cachedArpNudge?.let { return it }
        val value = listOf("all", "default").mapNotNull { scope ->
            try {
                File("/proc/sys/net/ipv4/conf/$scope/arp_ignore").readText().trim().toIntOrNull()
            } catch (e: Exception) {
                null
            }
        }.maxOrNull()
        val needed = if (value != null) value >= 3 else isSaicDevice()
        cachedArpNudge = needed
        AppLog.i("$TAG: arp_ignore=${value ?: "unreadable"} -> ARP wake-up ${if (needed) "needed" else "not needed"}.")
        return needed
    }

    /** The iptables INPUT rules and the arp settings of the script, comments left out. */
    fun firewallLines(text: String): List<String> =
        text.lineSequence().map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .filter { (it.contains("iptables") && it.contains("INPUT")) || it.contains("arp_ignore") || it.contains("arp_announce") }
            .toList()

    /**
     * The car's kernel counters that tell whether the phone's packets reach it: ICMP "unreachable"
     * sent (a rejected packet), TCP connection attempts received and their resets. Read before and
     * after the wait for the phone: unchanged, nothing came; up, it came and was refused (#30).
     */
    fun refusalCounters(): String = runCatching {
        val lines = File("/proc/net/snmp").readLines()
        fun field(proto: String, name: String): String? {
            val pair = lines.filter { it.startsWith("$proto:") }
            if (pair.size < 2) return null
            val names = pair[0].split(Regex("\\s+"))
            val values = pair[1].split(Regex("\\s+"))
            val i = names.indexOf(name)
            return if (i in values.indices) values[i] else null
        }
        "icmpOutDestUnreachs=${field("Icmp", "OutDestUnreachs")} tcpPassiveOpens=${field("Tcp", "PassiveOpens")} " +
            "tcpOutRsts=${field("Tcp", "OutRsts")} tcpInSegs=${field("Tcp", "InSegs")}"
    }.getOrElse { "counters unreadable" }

    /**
     * Devices the car knows on the hotspot: the kernel's ARP table for [iface] (IP and whether
     * the entry is complete), and the hotspot's own arp_ignore. When the phone says it joined but
     * never connects (#30, SWI132), this tells whether it got an address from the car at all.
     */
    fun hotspotNeighbours(iface: String = "ap0"): String {
        val arpIgnore = runCatching { File("/proc/sys/net/ipv4/conf/$iface/arp_ignore").readText().trim() }.getOrNull()
        val rows = runCatching {
            File("/proc/net/arp").readLines().drop(1).mapNotNull { HotspotNeighbours.row(it, iface) }
        }.getOrNull()
        return "$iface arp_ignore=${arpIgnore ?: "unreadable"}, " + refusalCounters() + ", neighbours=" +
            (rows?.let { if (it.isEmpty()) "none" else it.joinToString("; ") } ?: "unreadable")
    }

    /** Addresses on the hotspot the car has a complete ARP entry for (the phones that got one). */
    fun hotspotPhones(subnetPrefix: String): List<String> = runCatching {
        File("/proc/net/arp").readLines().drop(1).mapNotNull { line ->
            val cols = line.trim().split(Regex("\\s+"))
            val complete = cols.size >= 3 && ((cols[2].removePrefix("0x").toIntOrNull(16) ?: 0) and 0x2) != 0
            cols.firstOrNull()?.takeIf { complete && it.startsWith(subnetPrefix) }
        }
    }.getOrElse { emptyList() }
}

/** One line of /proc/net/arp for a given interface, as "IP (complete|incomplete)". */
object HotspotNeighbours {
    fun row(line: String, iface: String): String? {
        val cols = line.trim().split(Regex("\\s+"))
        if (cols.size < 6 || cols[5] != iface) return null
        // Flags 0x2 = complete (the MAC is known); 0x0 = asked, never answered.
        val complete = (cols[2].removePrefix("0x").toIntOrNull(16) ?: 0) and 0x2 != 0
        return "${cols[0]} (${if (complete) "complete" else "incomplete"})"
    }
}
