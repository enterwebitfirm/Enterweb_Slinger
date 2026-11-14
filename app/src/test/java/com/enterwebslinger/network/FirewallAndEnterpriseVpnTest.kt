// === FILE: app/src/test/java/com/enterwebslinger/network/FirewallAndEnterpriseVpnTest.kt ===
package com.enterwebslinger.network

import com.enterwebslinger.network.data.protocol.FirewallParsers
import com.enterwebslinger.network.data.protocol.IkePacketCodec
import com.enterwebslinger.network.data.repository.EnterpriseVpnRepository
import com.enterwebslinger.network.data.repository.VpnRepository
import com.enterwebslinger.network.domain.model.TunnelEncapsulationType
import com.enterwebslinger.network.domain.model.VpnProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.ByteBuffer

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FirewallAndEnterpriseVpnTest {

    @Test
    fun testSophosXmlSshXfrmAndDropLogParsers() {
        val ipsecXml = """
            <Response>
              <IPSecConnection>
                <Name>HQ_Branch</Name>
                <GatewayAddress>203.0.113.10</GatewayAddress>
                <LocalSubnet>10.10.0.0/16</LocalSubnet>
                <RemoteSubnet>172.16.0.0/16</RemoteSubnet>
                <Status>Active</Status>
              </IPSecConnection>
            </Response>
        """.trimIndent()
        val xmlTunnels = FirewallParsers.parseSophosIpsecXml(ipsecXml)
        assertEquals(1, xmlTunnels.size)
        assertTrue(xmlTunnels[0].isUp)
        assertEquals("203.0.113.10", xmlTunnels[0].remoteGateway)

        val sshStatus = """
            HQ_Branch-1[4]: ESTABLISHED 2 hours ago, 198.51.100.1[198.51.100.1]...203.0.113.10[203.0.113.10]
            HQ_Branch-1{12}:  INSTALLED, TUNNEL, reqid 1, ESP in UDP SPIs: c12a34b1_i d98e76a2_o
            HQ_Branch-1{12}:   10.10.0.0/16 === 172.16.0.0/16
        """.trimIndent()
        val xfrmState = "src 198.51.100.1 dst 203.0.113.10\n\tproto esp spi 0xc12a34b1 reqid 1 mode tunnel"
        val sshTunnels = FirewallParsers.parseSophosSshIpsecAndXfrm(sshStatus, xfrmState)
        assertEquals(1, sshTunnels.size)
        assertEquals("ESTABLISHED", sshTunnels[0].phase1State)
        assertEquals("INSTALLED", sshTunnels[0].phase2State)
        assertEquals("10.10.0.0/16", sshTunnels[0].localSubnet)
        assertEquals("172.16.0.0/16", sshTunnels[0].remoteSubnet)

        val drppktRaw = "2026-04-01 12:00:01 in_interface=Port2 src_ip=192.0.2.50 dst_ip=10.10.5.5 proto=TCP dst_port=22 reason=\"Firewall Rule #0\""
        val drops = FirewallParsers.parseSophosDropPackets(drppktRaw)
        assertEquals(1, drops.size)
        assertEquals("192.0.2.50", drops[0].srcIp)
        assertEquals("TCP/22", drops[0].protocolPort)
    }

    @Test
    fun testFortiGateResourceSdwanIpsecAndPolicyZeroHitParsers() {
        val resJson = """{"results":{"cpu":{"current":34.5},"mem":{"current":58.2},"session":{"current":12400,"max":500000}}}"""
        val statusJson = """{"version":"v7.4.3","results":{"hostname":"FGT-Edge-01"}}"""
        val haJson = """{"results":[{"role":"master"},{"role":"slave"}]}"""
        val health = FirewallParsers.parseFortiGateResourceJson("FGT", resJson, statusJson, haJson)
        assertEquals("FGT-Edge-01", health.hostname)
        assertEquals(34.5f, health.cpuPercent, 0.1f)
        assertEquals(12400L, health.activeSessions)
        assertEquals("MASTER", health.haRole)

        val slaJson = """{"results":{"ISP_SLA":{"server":"1.1.1.1","members":{"1":{"interface":"wan1","gateway":"198.51.100.1","sla_targets_met":1,"latency":11.4,"jitter":1.2,"packet_loss":0.0,"status":"up"}}}}}"""
        val slas = FirewallParsers.parseFortiGateSdwanSlaJson(slaJson)
        assertEquals(1, slas.size)
        assertEquals("wan1", slas[0].interfaceName)
        assertEquals("UP", slas[0].status)

        val ipsecJson = """{"results":[{"name":"AWS-P1","rgwy":"52.10.20.30","proxyid":[{"p2name":"AWS-P2","status":"up","incoming_bytes":1024,"outgoing_bytes":2048,"proxy_src":[{"subnet":"10.0.0.0/16"}],"proxy_dst":[{"subnet":"172.31.0.0/16"}]}]}]}"""
        val tunnels = FirewallParsers.parseFortiGateIpsecJson(ipsecJson)
        assertEquals(1, tunnels.size)
        assertTrue(tunnels[0].isUp)
        assertEquals("10.0.0.0/16", tunnels[0].localSubnet)

        val monJson = """{"results":[{"policyid":10,"hit_count":0,"bytes":0,"action":"accept"},{"policyid":20,"hit_count":500,"bytes":102400,"action":"accept"}]}"""
        val cmdbJson = """{"results":[{"policyid":10,"name":"Legacy_Allow","srcintf":[{"name":"port1"}],"dstintf":[{"name":"wan1"}]},{"policyid":20,"name":"Corp_Web","srcintf":[{"name":"lan"}],"dstintf":[{"name":"wan1"}]}]}"""
        val hits = FirewallParsers.parseFortiGatePolicyHitsJson(monJson, cmdbJson)
        assertEquals(2, hits.size)
        assertTrue(hits[0].isZeroHit)
        assertEquals("Legacy_Allow", hits[0].name)
    }

    @Test
    fun testPanOsCiscoAsaAndPfSenseParsers() {
        val keyXml = """<response status="success"><result><key>LUFRPT123456</key></result></response>"""
        assertEquals("LUFRPT123456", FirewallParsers.parsePanOsKeygenXml(keyXml).getOrThrow())

        val secXml = """<response status="success"><result><entry name="Allow_HTTPS"><action>allow</action><from>trust</from><to>untrust</to><application>ssl</application></entry></result></response>"""
        val natXml = """<response status="success"><result><entry name="Outbound_PAT"><source-translation>dynamic-ip-and-port</source-translation></entry></result></response>"""
        val match = FirewallParsers.parsePanOsSecurityAndNatMatchXml(secXml, natXml)
        assertEquals("Allow_HTTPS", match.matchedRuleName)
        assertEquals("ALLOW", match.action)
        assertEquals("Outbound_PAT", match.natRuleMatched)

        val ptOut = """
            Phase: 1
            Type: ROUTE-LOOKUP
            Subtype: Resolve Egress Interface
            Result: ALLOW
            Additional Information:
            found next-hop 198.51.100.1 using egress ifc outside

            Phase: 2
            Type: ACCESS-LIST
            Subtype: log
            Result: DROP
            Config:
            access-group inside_access_in in interface inside
            access-list inside_access_in extended deny tcp any any eq 23

            Result:
            input-interface: inside
            input-status: up
            output-interface: outside
            output-status: up
            Action: drop
            Drop-reason: (acl-drop) Flow is denied by configured rule
        """.trimIndent()
        val pt = FirewallParsers.parseCiscoPacketTracerOutput(ptOut)
        assertEquals("DROP", pt.verdict)
        assertEquals(2, pt.phases.size)
        assertEquals("inside", pt.inputInterface)
        assertTrue(pt.dropReason.contains("acl-drop"))

        val pfGwOut = "WAN_DHCP 1.1.1.1 14.2ms 1.8ms 0.0% online"
        val gws = FirewallParsers.parsePfGatewayStatus(pfGwOut)
        assertEquals(1, gws.size)
        assertEquals(14.2, gws[0].latencyMs, 0.05)

        val carpOut = "vtnet0: flags=8943<UP>\n\tinet 10.0.0.1 netmask 0xffffff00 vhid 10\n\tcarp: MASTER vhid 10 advbase 1 advskew 0"
        val vips = FirewallParsers.parseCarpVipStatus(carpOut)
        assertEquals(1, vips.size)
        assertEquals("MASTER", vips[0].status)
        assertEquals("10", vips[0].vhid)
    }

    @Test
    fun testIkePacketCodecProbeAllowedIpsAndMss() {
        val iSpi = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val udp500 = IkePacketCodec.buildIkev2SaInitProbe(useNatT4500 = false, initiatorSpi = iSpi)
        assertEquals(0x20, udp500[17].toInt() and 0xFF) // IKEv2
        assertEquals(34, udp500[18].toInt() and 0xFF)   // IKE_SA_INIT

        val natT4500 = IkePacketCodec.buildIkev2SaInitProbe(useNatT4500 = true, initiatorSpi = iSpi)
        assertEquals(0, natT4500[0].toInt())
        assertEquals(0, natT4500[3].toInt())
        assertEquals(udp500.size + 4, natT4500.size)

        // Synthetic IKEv2 response containing Notify payload Type 14 (NO_PROPOSAL_CHOSEN)
        val notifyPayload = ByteBuffer.allocate(8).apply {
            put(0.toByte()) // Next payload = 0
            put(0.toByte())
            putShort(8.toShort())
            put(1.toByte()) // Protocol IKE
            put(0.toByte()) // SPI size
            putShort(14.toShort()) // NO_PROPOSAL_CHOSEN
        }.array()
        val respPkt = ByteBuffer.allocate(28 + notifyPayload.size).apply {
            put(iSpi)
            put(byteArrayOf(9, 9, 9, 9, 9, 9, 9, 9))
            put(41.toByte()) // Next Payload: Notify (41)
            put(0x20.toByte())
            put(34.toByte()) // IKE_SA_INIT
            put(0x20.toByte()) // Responder flag
            putInt(0)
            putInt(28 + notifyPayload.size)
            put(notifyPayload)
        }.array()

        val parsed = IkePacketCodec.parseIkev2Response(
            raw = respPkt,
            length = respPkt.size,
            host = "198.51.100.2",
            port = 500,
            useNatT4500 = false,
            expectedISpiHex = "0102030405060708",
            rttMs = 18L
        )
        assertTrue(parsed.peerResponded)
        assertTrue(parsed.noProposalChosenDetected)
        assertTrue(parsed.diagnosisSummary.contains("NO_PROPOSAL_CHOSEN"))

        val mss = IkePacketCodec.calculateTunnelOverheadAndMss(
            encapsulation = TunnelEncapsulationType.WIREGUARD,
            physicalMtu = 1500,
            isIpv6Outer = false,
            natTEnabled = false
        )
        assertEquals(60, mss.overheadBytes)
        assertEquals(1440, mss.effectiveTunnelMtu)
        assertEquals(1400, mss.recommendedTcpMss)

        val allowed = IkePacketCodec.calculateAllowedIps(
            includedCidrs = listOf("0.0.0.0/0"),
            excludedCidrs = listOf("192.168.0.0/16", "10.0.0.0/8")
        )
        assertFalse(allowed.resultCidrs.contains("192.168.0.0/16"))
        assertFalse(allowed.resultCidrs.contains("10.0.0.0/8"))
        assertTrue(allowed.resultCidrs.contains("128.0.0.0/2"))
        assertTrue(allowed.resultCidrs.contains("64.0.0.0/2"))
        assertTrue(allowed.resultCidrs.contains("11.0.0.0/8"))
    }

    @Test
    fun testWireGuardKeygenConfQrRoundTripAndOpenVpnTlsCrypt() {
        val vpnRepo = VpnRepository(null)
        val entRepo = EnterpriseVpnRepository(null, vpnRepo)

        val kp = entRepo.generateWireGuardKeyPair()
        assertEquals(44, kp.privateKeyBase64.length)
        assertEquals(44, kp.publicKeyBase64.length)
        assertEquals(44, kp.presharedKeyBase64.length)

        val conf = """
            [Interface]
            PrivateKey = ${kp.privateKeyBase64}
            Address = 10.77.0.2/32
            DNS = 1.1.1.1
            MTU = 1420

            [Peer]
            PublicKey = ${kp.publicKeyBase64}
            PresharedKey = ${kp.presharedKeyBase64}
            Endpoint = vpn.example.com:51820
            AllowedIPs = 0.0.0.0/0
            PersistentKeepalive = 15
        """.trimIndent()
        val parsedWg = vpnRepo.parseWireGuardConf(conf, "Unit-WG")
        assertEquals(VpnProtocol.WIREGUARD, parsedWg.protocol)
        assertEquals("vpn.example.com", parsedWg.serverAddress)
        assertEquals(51820, parsedWg.port)
        assertEquals(15, parsedWg.wireGuardConfig?.persistentKeepalive)
        assertEquals(kp.publicKeyBase64, parsedWg.wireGuardConfig?.derivedLocalPublicKey)

        val renderedConf = entRepo.buildWireGuardConfText(parsedWg.wireGuardConfig!!)
        val qrBitmap = entRepo.generateQrBitmap(renderedConf, 256).getOrThrow()
        val decodedQr = entRepo.decodeQrFromBitmap(qrBitmap).getOrThrow()
        assertNotNull(decodedQr)
        assertTrue("Expected '${kp.privateKeyBase64}' in rendered:\n$renderedConf\nDecoded:\n$decodedQr", decodedQr.contains(kp.privateKeyBase64))

        val ovpnText = """
            client
            dev tun
            proto tcp
            remote gateway.corp.net 443
            cipher AES-256-GCM
            auth SHA512
            <tls-crypt>
            -----BEGIN OpenVPN Static key V1-----
            0011223344556677
            -----END OpenVPN Static key V1-----
            </tls-crypt>
        """.trimIndent()
        val parsedOvpn = vpnRepo.parseOpenVpnConf(ovpnText, "Corp-OVPN")
        assertEquals(VpnProtocol.OPENVPN, parsedOvpn.protocol)
        assertEquals("SHA512", parsedOvpn.openVpnConfig?.authDigest)
        assertTrue(parsedOvpn.openVpnConfig?.tlsCryptKey?.contains("OpenVPN Static key V1") == true)
    }
}
