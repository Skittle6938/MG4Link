package com.andrerinas.openheadunit.connection

import org.junit.Assert.assertEquals
import org.junit.Test

class FirewallLinesTest {
    @Test
    fun `the INPUT rules and arp settings are kept, comments and the rest left out`() {
        val script = """
            #!/system/bin/sh
            # Disable arp
            echo 8 > /proc/sys/net/ipv4/conf/all/arp_ignore
            iptables -A INPUT -p tcp --dport 30518 -j ACCEPT
            # iptables -A INPUT -p tcp --dport 5555 -j ACCEPT
            iptables -A OUTPUT -j ACCEPT
            iptables -A INPUT -j REJECT
        """.trimIndent()
        assertEquals(
            listOf(
                "echo 8 > /proc/sys/net/ipv4/conf/all/arp_ignore",
                "iptables -A INPUT -p tcp --dport 30518 -j ACCEPT",
                "iptables -A INPUT -j REJECT",
            ),
            CarNetworkQuirks.firewallLines(script),
        )
    }
}
