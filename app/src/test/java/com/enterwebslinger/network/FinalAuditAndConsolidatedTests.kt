// === FILE: app/src/test/java/com/enterwebslinger/network/FinalAuditAndConsolidatedTests.kt ===
package com.enterwebslinger.network

import com.enterwebslinger.network.core.util.NetworkByteUtils
import com.enterwebslinger.network.data.protocol.BgpAndVoipQosCodec
import com.enterwebslinger.network.data.protocol.RouterOsAndMndpCodec
import com.enterwebslinger.network.data.protocol.SwitchMibCodec
import com.enterwebslinger.network.domain.model.CollectorPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class FinalAuditAndConsolidatedTests {

    @Test
    fun testRouterOsSentenceEncoding() {
        val out = ByteArrayOutputStream()
        RouterOsAndMndpCodec.writeRouterOsSentence(out, listOf("/system/resource/print", "=proplist=uptime,version"))
        val bytes = out.toByteArray()
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun testSwitchMibParsingEmpty() {
        val lldp = SwitchMibCodec.parseLldpNeighbors(emptyList())
        assertTrue(lldp.isEmpty())

        val vlans = SwitchMibCodec.parseVlanTable(emptyList())
        assertTrue(vlans.isNotEmpty())
    }

    @Test
    fun testVoipQosMosCalculation() {
        val res = BgpAndVoipQosCodec.calculateVoipQos(
            targetHost = "10.0.0.1",
            port = 5060,
            dscpValueHex = "0xB8",
            sentCount = 100,
            receivedCount = 99,
            rttMs = 24f,
            jitterMs = 2.1f
        )
        assertNotNull(res)
        assertTrue(res.meanOpinionScoreMos >= 1.0f)
        assertTrue(res.meanOpinionScoreMos <= 4.5f)
        assertEquals("0xB8", res.dscpValueHex)
    }

    @Test
    fun testNetworkByteUtilsIpConversions() {
        val ipInt = 0xC0A80101L.toInt() // 192.168.1.1
        val ipStr = NetworkByteUtils.ipIntToStringBigEndian(ipInt)
        assertEquals("192.168.1.1", ipStr)

        val macHex = NetworkByteUtils.bytesToHex(byteArrayOf(0x00, 0x11, 0x22, 0x33, 0x44, 0x55), ":")
        assertEquals("00:11:22:33:44:55", macHex)
    }

    @Test
    fun testCollectorPreferencesDefaults() {
        val prefs = CollectorPreferences()
        assertEquals(1514, prefs.syslogUdpPort)
        assertEquals(1162, prefs.snmpTrapPort)
        assertEquals(2055, prefs.netflowPort)
    }
}
