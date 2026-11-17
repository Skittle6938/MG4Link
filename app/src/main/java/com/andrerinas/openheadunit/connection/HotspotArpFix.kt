package com.andrerinas.openheadunit.connection

import com.andrerinas.openheadunit.utils.AppLog
import java.io.File

/**
 * Makes the car answer ARP on its hotspot again, where its firmware set arp_ignore=8 for every
 * interface (SWI132 R29, SWI133): the phone then cannot find the car's hardware address and never
 * reaches MG4Link (#30). /proc/sys is root's, but netd (root) sets these values for the system
 * user through INetd.setProcSysNet, the call Android's own Wi-Fi uses.
 *
 * Linux applies the larger of conf/all and conf/<interface>, so: every other interface first gets
 * the value of "all" (they keep the protection SAIC wanted), then "all" goes to 0 and the hotspot to
 * 1 (answer only for its own address). The firmware's script sets it all back at the next start.
 * On cars that already answer, the hotspot's own value is written back unchanged, which only
 * checks that netd takes the call.
 */
object HotspotArpFix {
    private const val TAG = "HotspotArpFix"
    private const val IPV4 = 4
    private const val CONF = 1
    private const val HOTSPOT_VALUE = "1"
    private const val CONF_DIR = "/proc/sys/net/ipv4/conf"

    @Volatile private var doneFor: String? = null

    /** The writes for [hotspot], from the current values: (interface, value), in order. */
    fun plan(values: Map<String, Int>, hotspot: String): List<Pair<String, String>> {
        val all = values["all"] ?: return emptyList()
        val hotspotNow = values[hotspot] ?: return emptyList()
        if (maxOf(all, hotspotNow) < 3) return listOf(hotspot to hotspotNow.toString())
        val keep = values.filterKeys { it != "all" && it != "default" && it != hotspot && it != "lo" }
            .filter { (_, v) -> v < all }
            .map { (iface, _) -> iface to all.toString() }
        return keep + listOf("all" to "0", hotspot to HOTSPOT_VALUE)
    }

    @Synchronized
    fun apply(hotspot: String) {
        if (doneFor == hotspot) return
        doneFor = hotspot
        val values = readValues()
        val writes = plan(values, hotspot)
        if (writes.isEmpty()) {
            AppLog.i("$TAG: arp_ignore unreadable for $hotspot ($values), nothing done")
            return
        }
        val netd = netd() ?: run {
            AppLog.w("$TAG: netd not reachable, the car still answers no ARP on $hotspot")
            return
        }
        for ((iface, value) in writes) {
            val ok = runCatching {
                netd.javaClass.getMethod(
                    "setProcSysNet", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                    String::class.java, String::class.java, String::class.java,
                ).invoke(netd, IPV4, CONF, iface, "arp_ignore", value)
            }
            if (ok.isFailure) {
                AppLog.w("$TAG: netd refused arp_ignore=$value on $iface: ${ok.exceptionOrNull()?.let { it.cause ?: it }}")
                return
            }
        }
        AppLog.i("$TAG: arp_ignore before $values, after ${readValues()}")
    }

    private fun readValues(): Map<String, Int> = runCatching {
        File(CONF_DIR).listFiles().orEmpty().mapNotNull { dir ->
            runCatching { File(dir, "arp_ignore").readText().trim().toInt() }.getOrNull()?.let { dir.name to it }
        }.toMap()
    }.getOrElse { emptyMap() }

    private fun netd(): Any? = runCatching {
        val binder = Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java)
            .invoke(null, "netd") as android.os.IBinder
        Class.forName("android.net.INetd\$Stub").getMethod("asInterface", android.os.IBinder::class.java)
            .invoke(null, binder)
    }.getOrNull()
}
