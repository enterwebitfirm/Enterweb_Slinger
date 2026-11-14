// === FILE: app/src/test/java/com/enterwebslinger/network/FinalGapProtocolsTest.kt ===
package com.enterwebslinger.network

import com.enterwebslinger.network.data.protocol.PcapCaptureEngine
import com.enterwebslinger.network.data.repository.AaaDiagnosticsRepository
import com.enterwebslinger.network.data.repository.AdvancedProtocolRepository
import com.enterwebslinger.network.data.repository.LanSecurityAuditRepository
import com.enterwebslinger.network.data.repository.MtuDiscoveryRepository
import com.enterwebslinger.network.domain.model.WifiAuditSeverity
import com.enterwebslinger.network.domain.model.RadiusResponseCode
import com.enterwebslinger.network.domain.model.TrustedApBaseline
import com.enterwebslinger.network.domain.model.WifiSecurityFindingType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

class FinalGapProtocolsTest {

    @Test
    fun mtuRecommendations_computeExactTunnelMtuAndTcpMss() {
        val repo = MtuDiscoveryRepository()

        val ethernetRecs = repo.buildRecommendations(1500, isIpv6 = false)
        assertEquals(4, ethernetRecs.size)

        val pppoe = ethernetRecs.first { it.tunnelName.contains("PPPoE") }
        assertEquals(1492, pppoe.recommendedMtu)
        assertEquals(1452, pppoe.recommendedTcpMss)

        val wireguard = ethernetRecs.first { it.tunnelName.contains("WireGuard") }
        assertEquals(1420, wireguard.recommendedMtu)
        assertEquals(1380, wireguard.recommendedTcpMss)

        val ipsec = ethernetRecs.first { it.tunnelName.contains("IPsec") }
        assertEquals(1380, ipsec.recommendedMtu)
        assertEquals(1340, ipsec.recommendedTcpMss)

        val vxlan = ethernetRecs.first { it.tunnelName.contains("VXLAN") }
        assertEquals(1450, vxlan.recommendedMtu)
        assertEquals(1410, vxlan.recommendedTcpMss)

        // When underlying path is already PPPoE-capped at 1492 with IPv6 (60-byte TCP/IP overhead)
        val pppoeRecsV6 = repo.buildRecommendations(1492, isIpv6 = true)
        val wgV6 = pppoeRecsV6.first { it.tunnelName.contains("WireGuard") }
        assertEquals(1412, wgV6.recommendedMtu)
        assertEquals(1352, wgV6.recommendedTcpMss)
    }

    @Test
    fun pcapEngine_buildsValidLibpcapHeadersAndParsesIpv4TcpSyn() {
        val engine = PcapCaptureEngine(null)

        val globalHdr = engine.buildLibpcapGlobalHeader()
        assertEquals(24, globalHdr.size)
        // Little-endian 0xA1B2C3D4 => bytes D4 C3 B2 A1
        assertEquals(0xD4.toByte(), globalHdr[0])
        assertEquals(0xC3.toByte(), globalHdr[1])
        assertEquals(0xB2.toByte(), globalHdr[2])
        assertEquals(0xA1.toByte(), globalHdr[3])
        val parsedGlobal = ByteBuffer.wrap(globalHdr).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(PcapCaptureEngine.PCAP_MAGIC_LE, parsedGlobal.int)
        assertEquals(2.toShort(), parsedGlobal.short)
        assertEquals(4.toShort(), parsedGlobal.short)
        parsedGlobal.int // thiszone
        parsedGlobal.int // sigfigs
        assertEquals(65535, parsedGlobal.int)
        assertEquals(PcapCaptureEngine.LINKTYPE_RAW, parsedGlobal.int)

        val pktHdr = engine.buildLibpcapPacketHeader(1700000000, 123456, 40, 40)
        assertEquals(16, pktHdr.size)
        val parsedPktHdr = ByteBuffer.wrap(pktHdr).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(1700000000, parsedPktHdr.int)
        assertEquals(123456, parsedPktHdr.int)
        assertEquals(40, parsedPktHdr.int)
        assertEquals(40, parsedPktHdr.int)

        // Construct a synthetic 40-byte IPv4 + TCP SYN packet (10.0.0.2:51500 -> 1.1.1.1:443)
        val pkt = ByteArray(40)
        pkt[0] = 0x45.toByte() // IPv4, IHL=5 (20 bytes)
        pkt[2] = 0x00
        pkt[3] = 40.toByte()
        pkt[9] = 6.toByte() // TCP
        // Src IP 10.0.0.2
        pkt[12] = 10; pkt[13] = 0; pkt[14] = 0; pkt[15] = 2
        // Dst IP 1.1.1.1
        pkt[16] = 1; pkt[17] = 1; pkt[18] = 1; pkt[19] = 1
        // TCP src port 51500 (0xC92C)
        pkt[20] = 0xC9.toByte(); pkt[21] = 0x2C.toByte()
        // TCP dst port 443 (0x01BB)
        pkt[22] = 0x01.toByte(); pkt[23] = 0xBB.toByte()
        // Data offset = 5 (20 bytes => 0x50), SYN flag (0x02)
        pkt[32] = 0x50.toByte()
        pkt[33] = 0x02.toByte()

        val record = engine.parseIpPacket(pkt, pkt.size, id = 7L, timestampUs = 999_000L)
        assertNotNull(record)
        record!!
        assertEquals(4, record.ipVersion)
        assertEquals("TCP", record.protocolName)
        assertEquals("10.0.0.2", record.srcIp)
        assertEquals("1.1.1.1", record.dstIp)
        assertEquals(51500, record.srcPort)
        assertEquals(443, record.dstPort)
        assertEquals("SYN", record.tcpFlags)
        assertTrue(record.summary.contains("10.0.0.2:51500 -> 1.1.1.1:443"))
    }

    @Test
    fun aaaDiagnostics_buildsRadiusRequestEncryptsPasswordAndParsesVsaAndTacacs() {
        val repo = AaaDiagnosticsRepository()
        val ra = ByteArray(16) { idx -> (idx + 1).toByte() }
        val secret = "testing123"

        // Password > 16 chars forces 2 MD5 XOR blocks (32 bytes)
        val longPassword = "super-secret-network-pass"
        val enc = repo.encryptRadiusUserPassword(longPassword, secret, ra)
        assertEquals(32, enc.size)

        // Verify deterministic RFC 2865 block 1 decryption round-trip
        val md5 = MessageDigest.getInstance("MD5")
        md5.update(secret.toByteArray(Charsets.UTF_8))
        md5.update(ra)
        val b1 = md5.digest()
        val dec1 = ByteArray(16) { i -> (enc[i].toInt() xor b1[i].toInt()).toByte() }
        assertEquals(longPassword.substring(0, 16), String(dec1, Charsets.UTF_8))

        // Build Access-Request packet
        val req = repo.buildRadiusAccessRequest(
            identifier = 42,
            requestAuthenticator = ra,
            sharedSecret = secret,
            username = "netadmin",
            password = longPassword
        )
        assertEquals(1, req[0].toInt() and 0xFF) // Access-Request
        assertEquals(42, req[1].toInt() and 0xFF)
        val declaredLen = ((req[2].toInt() and 0xFF) shl 8) or (req[3].toInt() and 0xFF)
        assertEquals(req.size, declaredLen)

        // Build synthetic Access-Accept with Reply-Message (Type 18) and Cisco VSA (Type 26, Vendor 9)
        val attrs = ByteArrayOutputStream()
        val replyText = "Welcome NOC".toByteArray(Charsets.UTF_8)
        attrs.write(18)
        attrs.write(2 + replyText.size)
        attrs.write(replyText)

        val vsaVal = "shell:priv-lvl=15".toByteArray(Charsets.UTF_8)
        val vsaSubLen = 2 + vsaVal.size
        attrs.write(26)
        attrs.write(2 + 4 + vsaSubLen)
        attrs.write(ByteBuffer.allocate(4).putInt(9).array()) // Cisco Vendor ID = 9
        attrs.write(1) // Cisco-AVPair
        attrs.write(vsaSubLen)
        attrs.write(vsaVal)

        val attrBytes = attrs.toByteArray()
        val respPacket = ByteBuffer.allocate(20 + attrBytes.size).apply {
            put(2.toByte()) // Access-Accept
            put(42.toByte())
            putShort((20 + attrBytes.size).toShort())
            put(ra)
            put(attrBytes)
        }.array()

        val parsed = repo.parseRadiusResponse("10.10.10.5", 1812, respPacket, respPacket.size, rttMs = 14L)
        assertEquals(RadiusResponseCode.ACCESS_ACCEPT, parsed.responseCode)
        assertTrue(parsed.isSuccess)
        assertEquals("Welcome NOC", parsed.replyMessage)
        assertEquals(1, parsed.vsaAttributes.size)
        assertEquals(9L, parsed.vsaAttributes[0].vendorId)
        assertEquals("shell:priv-lvl=15", parsed.vsaAttributes[0].valueText)

        // Verify TACACS+ v12.0 header (version 0xC0, type 0x01, seq 0x01)
        val tacacsHdr = repo.buildTacacsAuthHeader(sessionId = 0x11223344)
        assertEquals(20, tacacsHdr.size)
        assertEquals(0xC0, tacacsHdr[0].toInt() and 0xFF)
        assertEquals(0x01, tacacsHdr[1].toInt() and 0xFF)
        assertEquals(0x01, tacacsHdr[2].toInt() and 0xFF)
    }

    @Test
    fun dnsEdnsPacket_buildsOptDoBitAndParsesAdAndIpv4Answer() {
        val repo = AdvancedProtocolRepository(null)
        val query = repo.buildDnsEdnsQueryPacket("cloudflare.com", transactionId = 0x4A2B, requestDnssecDo = true)
        assertTrue(query.size > 28)
        // ARCOUNT = 1 at bytes 10..11
        assertEquals(0x00, query[10].toInt() and 0xFF)
        assertEquals(0x01, query[11].toInt() and 0xFF)
        // Verify EDNS0 OPT RR tail contains Type 41 (0x00, 0x29) and DO bit (0x80, 0x00)
        val tail = query.takeLast(11).map { it.toInt() and 0xFF }
        assertEquals(0x00, tail[0]) // root
        assertEquals(0x00, tail[1]); assertEquals(0x29, tail[2]) // OPT Type 41
        assertEquals(0x80, tail[7]); assertEquals(0x00, tail[8]) // DO flag = 0x8000

        // Construct synthetic DNS response with AD=1 (0x81A0), QDCOUNT=1, ANCOUNT=1, A=104.16.132.229
        val out = ByteArrayOutputStream()
        out.write(ByteBuffer.allocate(12).apply {
            putShort(0x4A2B.toShort())
            putShort(0x81A0.toShort()) // QR=1, RD=1, RA=1, AD=1 (0x0020)
            putShort(1.toShort())
            putShort(1.toShort())
            putShort(0.toShort())
            putShort(0.toShort())
        }.array())
        // Question: cloudflare.com A IN
        out.write(10); out.write("cloudflare".toByteArray())
        out.write(3); out.write("com".toByteArray())
        out.write(0)
        out.write(byteArrayOf(0, 1, 0, 1))
        // Answer pointer 0xC00C, Type A (1), Class IN (1), TTL 300, RDLEN 4, IP 104.16.132.229
        out.write(byteArrayOf(0xC0.toByte(), 0x0C))
        out.write(byteArrayOf(0, 1, 0, 1, 0, 0, 1, 44, 0, 4))
        out.write(byteArrayOf(104, 16, 132.toByte(), 229.toByte()))

        val rawResp = out.toByteArray()
        val parsed = repo.parseDnsEdnsResponse(rawResp, rawResp.size, expectedTxId = 0x4A2B)
        assertTrue(parsed.authenticatedData)
        assertEquals(0, parsed.rcode)
        assertEquals(listOf("104.16.132.229"), parsed.resolvedIps)
    }

    @Test
    fun lanSecurityAudit_parsesOnvifWsDiscoveryXmlAndDetectsEvilTwins() {
        val repo = LanSecurityAuditRepository(null, null, null)

        val wsXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <e:Envelope xmlns:e="http://www.w3.org/2003/05/soap-envelope" xmlns:d="http://schemas.xmlsoap.org/ws/2005/04/discovery">
              <e:Body>
                <d:ProbeMatches>
                  <d:ProbeMatch>
                    <d:Scopes>onvif://www.onvif.org/type/Network_Video_Transmitter onvif://www.onvif.org/hardware/DS-2CD2143G2-I</d:Scopes>
                    <d:XAddrs>http://192.168.1.64:80/onvif/device_service</d:XAddrs>
                  </d:ProbeMatch>
                </d:ProbeMatches>
              </e:Body>
            </e:Envelope>
        """.trimIndent()

        val cam = repo.parseWsDiscoveryXml("192.168.1.64", wsXml, elapsedMs = 24L)
        assertEquals("192.168.1.64", cam.ip)
        assertEquals("DS-2CD2143G2-I", cam.hardwareModel)
        assertEquals("http://192.168.1.64:80/onvif/device_service", cam.endpointUrl)
        assertEquals(2, cam.scopes.size)

        val baselines = listOf(
            TrustedApBaseline(
                ssid = "CorpWiFi",
                bssid = "AA:BB:CC:11:22:33",
                expectedSecurity = "WPA3"
            )
        )
        val scans = listOf(
            // Legit AP (no alert expected)
            LanSecurityAuditRepository.ScannedApSnapshot(
                ssid = "CorpWiFi",
                bssid = "AA:BB:CC:11:22:33",
                capabilities = "[WPA2-PSK+SAE-CCMP][ESS]",
                channel = 36,
                band = "5 GHz",
                rssiDbm = -52
            ),
            // Evil Twin with unknown BSSID + downgraded OPEN security
            LanSecurityAuditRepository.ScannedApSnapshot(
                ssid = "CorpWiFi",
                bssid = "DE:AD:BE:EF:00:99",
                capabilities = "[ESS]",
                channel = 36,
                band = "5 GHz",
                rssiDbm = -41
            ),
            // Hidden AP sharing channel 36
            LanSecurityAuditRepository.ScannedApSnapshot(
                ssid = "",
                bssid = "DE:AD:BE:EF:00:88",
                capabilities = "[WPA2-PSK-CCMP][ESS]",
                channel = 36,
                band = "5 GHz",
                rssiDbm = -58
            )
        )

        val findings = repo.analyzeWifiScanForEvilTwins(
            scanResults = scans,
            trustedBaselines = baselines,
            activeSsid = "CorpWiFi",
            activeBssid = "AA:BB:CC:11:22:33",
            activeChannel = 36
        )

        assertTrue(findings.any { it.findingType == WifiSecurityFindingType.DUPLICATE_SSID_UNKNOWN_BSSID && it.severity == WifiAuditSeverity.HIGH })
        assertTrue(findings.any { it.findingType == WifiSecurityFindingType.SECURITY_DOWNGRADE && it.severity == WifiAuditSeverity.HIGH })
        assertTrue(findings.any { it.findingType == WifiSecurityFindingType.HIDDEN_SSID_OVERLAPPING_CHANNEL && it.channel == 36 })
        assertFalse(findings.any { it.bssid == "AA:BB:CC:11:22:33" })
    }
}
