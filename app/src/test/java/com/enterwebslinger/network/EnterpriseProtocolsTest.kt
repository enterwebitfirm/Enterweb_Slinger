// === FILE: app/src/test/java/com/enterwebslinger/network/EnterpriseProtocolsTest.kt ===
package com.enterwebslinger.network

import com.enterwebslinger.network.data.protocol.FlowParser
import com.enterwebslinger.network.data.protocol.SnmpCodec
import com.enterwebslinger.network.data.protocol.SyslogParser
import com.enterwebslinger.network.data.repository.GnmiRepository
import com.enterwebslinger.network.data.repository.NmapFingerprintEngine
import com.enterwebslinger.network.data.repository.TerminalAndNetOsRepository
import com.enterwebslinger.network.domain.model.FlowProtocolType
import com.enterwebslinger.network.domain.model.HostFingerprintReport
import com.enterwebslinger.network.domain.model.SyslogFacility
import com.enterwebslinger.network.domain.model.SyslogSeverity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class EnterpriseProtocolsTest {

    @Test
    fun testSnmpGetRequestEncoding() {
        val oids = listOf("1.3.6.1.2.1.1.1.0", "1.3.6.1.2.1.1.3.0")
        val requestBytes = SnmpCodec.buildGetRequest("public", 42, oids, isGetNext = false)

        assertTrue("SNMP request bytes should not be empty", requestBytes.isNotEmpty())
        assertEquals("Root tag must be ASN.1 SEQUENCE (0x30)", 0x30, requestBytes[0].toInt() and 0xFF)
    }

    @Test
    fun testSnmpV3UsmKeyLocalizationAndTruncatedBerBounds() {
        val engineId = byteArrayOf(0x80.toByte(), 0x00, 0x13, 0x70, 0x01, 0x0A, 0x00, 0x00, 0x01)
        val sha1Key = SnmpCodec.localizeKey("maplesyrup", engineId, "SHA-1")
        val sha1KeyAgain = SnmpCodec.localizeKey("maplesyrup", engineId, "SHA-1")
        assertEquals("SHA-1 localized key must be 20 bytes", 20, sha1Key.size)
        assertArrayEquals("Key localization must be deterministic", sha1Key, sha1KeyAgain)

        val sha256Key = SnmpCodec.localizeKey("maplesyrup", engineId, "SHA-256")
        assertEquals("SHA-256 localized key must be 32 bytes", 32, sha256Key.size)

        // Truncated ASN.1 BER response should throw cleanly instead of crashing with BufferUnderflow
        val truncated = byteArrayOf(0x30, 0x50, 0x02, 0x01, 0x01)
        var threwIllegalArg = false
        try {
            SnmpCodec.parseSnmpResponse(truncated)
        } catch (_: IllegalArgumentException) {
            threwIllegalArg = true
        }
        assertTrue("Truncated BER response must raise IllegalArgumentException", threwIllegalArg)
    }

    @Test
    fun testSyslogRfc3164Parsing() {
        val raw = "<34>Oct 11 22:14:15 mymachine su[123]: 'su root' failed for operator on /dev/pts/8"
        val msg = SyslogParser.parse(raw, "10.0.0.1", 514)

        assertEquals(34, msg.pri)
        assertEquals(SyslogFacility.AUTH, msg.facility)
        assertEquals(SyslogSeverity.CRITICAL, msg.severity)
        assertEquals("mymachine", msg.hostname)
        assertEquals("su", msg.appName)
        assertEquals("123", msg.procId)
        assertTrue(msg.messageContent.contains("'su root' failed"))
    }

    @Test
    fun testSyslogRfc5424Parsing() {
        val raw = """<165>1 2026-11-02T12:00:00.000Z edge-router bgpd 4092 BGP_EVT [noc@32473 peer="10.0.0.2"] Peer up"""
        val msg = SyslogParser.parse(raw, "10.0.0.2", 1514)

        assertEquals(165, msg.pri)
        assertEquals(SyslogFacility.LOCAL4, msg.facility)
        assertEquals(SyslogSeverity.NOTICE, msg.severity)
        assertEquals("edge-router", msg.hostname)
        assertEquals("bgpd", msg.appName)
        assertEquals("4092", msg.procId)
        assertEquals("BGP_EVT", msg.msgId)
        assertEquals("10.0.0.2", msg.structuredData["peer"])
        assertEquals("Peer up", msg.messageContent)
    }

    @Test
    fun testNetFlowV5Parsing() {
        val parser = FlowParser()
        val buffer = ByteBuffer.allocate(72)
        buffer.putShort(5.toShort())
        buffer.putShort(1.toShort())
        buffer.putInt(100000)
        buffer.putInt(1700000000)
        buffer.putInt(0)
        buffer.putInt(1)
        buffer.put(0.toByte())
        buffer.put(0.toByte())
        buffer.putShort(0.toShort())

        buffer.put(byteArrayOf(192.toByte(), 168.toByte(), 1.toByte(), 50.toByte()))
        buffer.put(byteArrayOf(142.toByte(), 250.toByte(), 190.toByte(), 46.toByte()))
        buffer.put(byteArrayOf(192.toByte(), 168.toByte(), 1.toByte(), 1.toByte()))
        buffer.putShort(1.toShort())
        buffer.putShort(2.toShort())
        buffer.putInt(12)
        buffer.putInt(15420)
        buffer.putInt(90000)
        buffer.putInt(99000)
        buffer.putShort(54321.toShort())
        buffer.putShort(443.toShort())
        buffer.put(0.toByte())
        buffer.put(0x12.toByte())
        buffer.put(6.toByte())
        buffer.put((46 shl 2).toByte())
        buffer.putShort(0.toShort())
        buffer.putShort(15169.toShort())
        buffer.put(24.toByte())
        buffer.put(24.toByte())
        buffer.putShort(0.toShort())

        val records = parser.parsePacket(buffer.array(), 72)
        assertEquals(1, records.size)
        val flow = records[0]
        assertEquals(FlowProtocolType.NETFLOW_V5, flow.protocolType)
        assertEquals("192.168.1.50", flow.srcIp)
        assertEquals("142.250.190.46", flow.dstIp)
        assertEquals(54321, flow.srcPort)
        assertEquals(443, flow.dstPort)
        assertEquals("TCP", flow.protocolName)
        assertEquals(15420L, flow.bytes)
        assertEquals(12L, flow.packets)
        assertEquals(46, flow.dscp)
        assertTrue(flow.dscpClass.contains("EF"))
    }

    @Test
    fun testFlowParserMalformedNetFlowV9AndSflowV5Bounds() {
        val parser = FlowParser()

        // Truncated NetFlow v9 header asserting FlowSet length > remaining bytes
        val shortV9 = ByteBuffer.allocate(24).apply {
            putShort(9)
            putShort(1)
            putInt(1000)
            putInt(1700000000)
            putInt(1)
            putInt(100)
            putShort(0)    // template flowset id
            putShort(120)  // bogus length exceeding packet size
        }.array()
        assertTrue(parser.parsePacket(shortV9, shortV9.size).isEmpty())

        // Short/malformed sFlow v5 packet declaring 999 samples
        val shortSflow = ByteBuffer.allocate(32).apply {
            putInt(5)      // sFlow version 5
            putInt(1)      // IPv4 agent
            putInt(0x0A000001)
            putInt(1)      // subAgentId
            putInt(1)      // seq
            putInt(500)    // uptime
            putInt(999)    // inflated sampleCount
            putInt(0)      // partial
        }.array()
        assertTrue(parser.parsePacket(shortSflow, shortSflow.size).isEmpty())
    }

    @Test
    fun testGnmiPathProtobufEncodingWithKeySelector() {
        val repo = GnmiRepository()
        val encoded = repo.encodeGnmiPath("/interfaces/interface[name=eth0]/state/counters")
        val ascii = String(encoded, Charsets.UTF_8)

        assertTrue("Encoded protobuf path should start with field 1 Path.elem tag (0x0A)", encoded[0] == 0x0A.toByte())
        assertTrue(ascii.contains("interfaces"))
        assertTrue(ascii.contains("interface"))
        assertTrue(ascii.contains("name"))
        assertTrue(ascii.contains("eth0"))
        assertTrue(ascii.contains("counters"))
    }

    @Test
    fun testNmapRadialTopologyAndCustomPorts() {
        val engine = NmapFingerprintEngine()
        val hosts = listOf(
            HostFingerprintReport(host = "192.168.1.10", openPorts = listOf(22, 80)),
            HostFingerprintReport(host = "192.168.1.20", openPorts = listOf(445, 3389))
        )
        val graph = engine.buildTopologyGraph("192.168.1.1", hosts, "192.168.1.100")

        assertEquals(3, graph.nodes.size)
        assertEquals(2, graph.links.size)
        val gwNode = graph.nodes.first { it.isGateway }
        assertEquals(0.5f, gwNode.xRatio, 0.01f)
        assertEquals(0.5f, gwNode.yRatio, 0.01f)

        val customPorts = engine.parseCustomPorts("22, 80-82, 443, 99999")
        assertEquals(listOf(22, 80, 81, 82, 443), customPorts)
        assertFalse(customPorts.contains(99999))
    }

    @Test
    fun testTodEpochMath() {
        val unixEpochSeconds = 1700000000L
        val todRawSeconds = unixEpochSeconds + 2208988800L
        val convertedUnix = todRawSeconds - 2208988800L
        assertEquals(unixEpochSeconds, convertedUnix)
    }

    @Test
    fun testCiscoShowVersionParser() {
        val repo = TerminalAndNetOsRepository(CoroutineScope(Dispatchers.Default))
        val sample = """
            Cisco IOS Software, C2960 Software (C2960-LANBASEK9-M), Version 15.0(2)SE4, RELEASE SOFTWARE (fc1)
            cisco WS-C2960-24TT-L (PowerPC405) processor (revision B0) with 65536K bytes of memory.
            Processor board ID FOC1429Z0GH
            Router uptime is 4 weeks, 2 days, 18 hours, 32 minutes
        """.trimIndent()

        val parsed = repo.parseShowVersion(sample)
        assertEquals("Cisco Systems", parsed.vendor)
        assertEquals("15.0(2)SE4", parsed.osVersion)
        assertEquals("WS-C2960-24TT-L", parsed.model)
        assertEquals("FOC1429Z0GH", parsed.serialNumber)
        assertEquals("4 weeks, 2 days, 18 hours, 32 minutes", parsed.uptime)
        assertEquals("65536K", parsed.memory)
    }

    @Test
    fun testCiscoShowIpInterfaceBriefParser() {
        val repo = TerminalAndNetOsRepository(CoroutineScope(Dispatchers.Default))
        val sample = """
            Interface              IP-Address      OK? Method Status                Protocol
            FastEthernet0/1        192.168.1.10    YES manual up                    up      
            GigabitEthernet0/1     10.0.0.1        YES manual up                    up      
            GigabitEthernet0/2     unassigned      YES unset  administratively down down    
        """.trimIndent()

        val interfaces = repo.parseIpInterfaceBrief(sample)
        assertEquals(3, interfaces.size)
        assertEquals("FastEthernet0/1", interfaces[0].interfaceName)
        assertEquals("192.168.1.10", interfaces[0].ipAddress)
        assertEquals("up", interfaces[0].status)
        assertEquals("up", interfaces[0].protocol)

        assertEquals("GigabitEthernet0/2", interfaces[2].interfaceName)
        assertEquals("administratively down", interfaces[2].status)
    }

    @Test
    fun testJunosRoutesAndBgpSummaryParsers() {
        val repo = TerminalAndNetOsRepository(CoroutineScope(Dispatchers.Default))

        val routeSample = """
            inet.0: 12 destinations, 12 routes (12 active, 0 holddown, 0 hidden)
            + = Active Route, - = Last Active, * = Both

            0.0.0.0/0          *[Static/5] 14w2d 06:11:20
                                >  to 10.10.0.1 via ge-0/0/0.0
            10.20.30.0/24      *[BGP/170] 3d 04:00:00, localpref 100
                                >  to 172.16.1.2 via xe-0/0/1.0
        """.trimIndent()
        val routes = repo.parseJunosRoutes(routeSample)
        assertEquals(2, routes.size)
        assertEquals("0.0.0.0", routes[0].network)
        assertEquals(0, routes[0].prefixLength)
        assertEquals("Static", routes[0].protocol)
        assertEquals("10.10.0.1", routes[0].nextHop)
        assertEquals("ge-0/0/0.0", routes[0].interfaceName)
        assertEquals("BGP", routes[1].protocol)
        assertEquals("170", routes[1].metric)

        val bgpSample = """
            Groups: 1 Peers: 2 Down peers: 0
            Peer                     AS      InPkt     OutPkt    OutQ   Flaps Last Up/Dwn State|#Active/Received/Accepted/Damped...
            172.16.1.2            65100      12045      12088       0       0     3d 4:00 Establ
            198.51.100.2          65200          0          0       0       2       12:30 Active
        """.trimIndent()
        val peers = repo.parseJunosBgpSummary(bgpSample)
        assertEquals(2, peers.size)
        assertEquals("172.16.1.2", peers[0].deviceId)
        assertEquals("AS 65100", peers[0].localInterface)
        assertEquals("Establ", peers[0].capabilities)
        assertEquals("Active", peers[1].capabilities)
    }

    @Test
    fun testConfigDiffGeneration() {
        val repo = TerminalAndNetOsRepository(CoroutineScope(Dispatchers.Default))
        val running = """
            hostname Router1
            interface Gig0/0
             ip address 192.168.1.1 255.255.255.0
            ip access-list extended BLOCK-TELNET
             deny tcp any any eq 23
        """.trimIndent()

        val startup = """
            hostname Router1
            interface Gig0/0
             ip address 192.168.1.1 255.255.255.0
        """.trimIndent()

        val diff = repo.generateConfigDiff(running, startup)
        assertTrue("Should detect added access-list lines", diff.addedCount >= 2)
        assertEquals(0, diff.removedCount)
    }
}
