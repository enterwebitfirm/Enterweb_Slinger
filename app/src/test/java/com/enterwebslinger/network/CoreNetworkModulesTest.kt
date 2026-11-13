// === FILE: app/src/test/java/com/enterwebslinger/network/CoreNetworkModulesTest.kt ===
package com.enterwebslinger.network

import com.enterwebslinger.network.core.util.NetworkByteUtils
import com.enterwebslinger.network.core.util.NetworkUtils
import com.enterwebslinger.network.domain.model.ChannelCongestionInfo
import com.enterwebslinger.network.domain.model.HostWatcherTarget
import com.enterwebslinger.network.domain.model.WhoisLookupResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreNetworkModulesTest {

    @Test
    fun buildMagicPacket_produces102BytesWithSyncAndMacRepetitions() {
        val packet = NetworkUtils.buildMagicPacket("AA:BB:CC:DD:EE:FF")
        assertEquals(102, packet.size)
        for (i in 0 until 6) {
            assertEquals(0xFF.toByte(), packet[i])
        }
        val expectedMac = byteArrayOf(
            0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(),
            0xDD.toByte(), 0xEE.toByte(), 0xFF.toByte()
        )
        for (rep in 0 until 16) {
            val base = 6 + rep * 6
            for (j in 0 until 6) {
                assertEquals(expectedMac[j], packet[base + j])
            }
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun buildMagicPacket_rejectsMalformedMac() {
        NetworkUtils.buildMagicPacket("AA:BB:CC")
    }

    @Test
    fun networkByteUtils_roundTripsIpAndComputesBroadcast() {
        val ipStr = "10.0.2.15"
        val ipLong = NetworkByteUtils.stringToIpLong(ipStr)
        assertEquals(ipStr, NetworkByteUtils.ipLongToString(ipLong))

        val mask24 = (0xFFFFFFFFL shl (32 - 24)) and 0xFFFFFFFFL
        val broadcast = (ipLong and mask24) or (mask24.inv() and 0xFFFFFFFFL)
        assertEquals("10.0.2.255", NetworkByteUtils.ipLongToString(broadcast))
    }

    @Test
    fun frequencyConversion_maps24And5And6GhzCorrectly() {
        assertEquals(1, NetworkUtils.frequencyToChannel(2412))
        assertEquals(6, NetworkUtils.frequencyToChannel(2437))
        assertEquals(11, NetworkUtils.frequencyToChannel(2462))
        assertEquals("2.4 GHz", NetworkUtils.frequencyToBand(2437))

        assertEquals(36, NetworkUtils.frequencyToChannel(5180))
        assertEquals(149, NetworkUtils.frequencyToChannel(5745))
        assertEquals("5 GHz", NetworkUtils.frequencyToBand(5745))

        assertEquals("6 GHz", NetworkUtils.frequencyToBand(5955))
    }

    @Test
    fun hostWatcherTarget_computesUptimePercentageAccurately() {
        val target = HostWatcherTarget(
            id = "gw",
            host = "192.168.1.1",
            label = "Gateway",
            totalChecks = 20,
            upChecks = 19
        )
        assertEquals(95.0f, target.uptimePercent, 0.01f)
    }

    @Test
    fun whoisAndCongestionModels_preserveStructuredFields() {
        val whois = WhoisLookupResult(
            query = "example.com",
            serverUsed = "whois.verisign-grs.com",
            referralChain = listOf("whois.iana.org", "whois.verisign-grs.com"),
            registrar = "RESERVED-Internet Assigned Numbers Authority",
            nameServers = listOf("A.IANA-SERVERS.NET", "B.IANA-SERVERS.NET")
        )
        assertEquals(2, whois.referralChain.size)
        assertEquals(2, whois.nameServers.size)

        val congestion = ChannelCongestionInfo(
            channel = 11,
            band = "2.4 GHz",
            apCount = 0,
            congestionPercent = 0,
            strongestRssiDbm = -100,
            isRecommended = true
        )
        assertTrue(congestion.isRecommended)
        assertFalse(congestion.congestionPercent > 0)
    }
}
