// === FILE: app/src/test/java/com/enterwebslinger/network/IotAndApiProtocolsTest.kt ===
package com.enterwebslinger.network

import com.enterwebslinger.network.data.protocol.WireGuardCrypto
import com.enterwebslinger.network.data.repository.IotProtocolsRepository
import com.enterwebslinger.network.data.repository.VpnRepository
import com.enterwebslinger.network.data.repository.WebServicesRepository
import com.enterwebslinger.network.domain.model.CoapMethod
import com.enterwebslinger.network.domain.model.CoapResource
import com.enterwebslinger.network.domain.model.CoapType
import com.enterwebslinger.network.domain.model.SoapOperation
import com.enterwebslinger.network.domain.model.VpnProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Base64

class IotAndApiProtocolsTest {

    private lateinit var vpnRepository: VpnRepository
    private lateinit var webServicesRepository: WebServicesRepository
    private lateinit var iotProtocolsRepository: IotProtocolsRepository

    @Before
    fun setUp() {
        vpnRepository = VpnRepository()
        webServicesRepository = WebServicesRepository()
        iotProtocolsRepository = IotProtocolsRepository(CoroutineScope(Dispatchers.Default))
    }

    @Test
    fun testWireGuardCryptoRfc7748VectorAndHandshakeFrame() {
        fun hexToBytes(hex: String): ByteArray =
            hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

        // RFC 7748 Section 5.2 first official test vector
        val scalar = hexToBytes("a546e36bf0527c9d3b16154b82465edd62144c0ac1fc5a18506a2244ba449ac4")
        val uCoord = hexToBytes("e6db6867583030db3594c1a424b15f7c726624ec26b3353b10a903a6d0ab1c4c")
        val expectedShared = hexToBytes("c3da55379de9c6908e94ea4df28d084f32eccf03491c71f754b4075577a28552")

        val actualShared = WireGuardCrypto.x25519(scalar, uCoord)
        assertArrayEquals("RFC 7748 X25519 output mismatch", expectedShared, actualShared)

        // Keypair + BLAKE2s + Type 1 Handshake Initiation (148 bytes)
        val privA = WireGuardCrypto.generatePrivateKey()
        val pubA = WireGuardCrypto.publicKeyFromPrivate(privA)
        val privB = WireGuardCrypto.generatePrivateKey()
        val pubB = WireGuardCrypto.publicKeyFromPrivate(privB)
        assertEquals(32, pubA.size)
        assertEquals(32, pubB.size)

        val digest1 = WireGuardCrypto.blake2s("Enterweb-Slinger".toByteArray(Charsets.UTF_8))
        val digest2 = WireGuardCrypto.blake2s("Enterweb-Slinger".toByteArray(Charsets.UTF_8))
        assertEquals(32, digest1.size)
        assertArrayEquals(digest1, digest2)

        val packet = WireGuardCrypto.buildHandshakeInitiationPacket(
            localPrivateKeyBase64 = Base64.getEncoder().encodeToString(privA),
            peerPublicKeyBase64 = Base64.getEncoder().encodeToString(pubB),
            senderIndex = 0x11223344
        )
        assertNotNull(packet)
        assertEquals(148, packet!!.size)
        assertEquals(0x01.toByte(), packet[0])
    }

    @Test
    fun testWireGuardConfParserIpv4AndIpv6Endpoints() {
        val sampleWgConf = """
            [Interface]
            PrivateKey = aGVsbG93b3JsZHByaXZhdGVrZXlleGFtcGxlMTIzNDU2Nw==
            Address = 10.99.0.2/24, fd00::2/64
            DNS = 1.1.1.1, 1.0.0.1
            MTU = 1380

            [Peer]
            PublicKey = Y29ycG9yYXRlcHVibGlja2V5ZXhhbXBsZTEyMzQ1Njc4OQ==
            PresharedKey = dGVzdHByZXNoYXJlZGtleTEyMzQ1Njc4OTAxMjM0NQ==
            Endpoint = wg-gw.example.org:51820
            AllowedIPs = 0.0.0.0/0, ::/0
            PersistentKeepalive = 30
        """.trimIndent()

        val profile = vpnRepository.parseWireGuardConf(sampleWgConf, "Corp Gateway")

        assertEquals("Corp Gateway", profile.name)
        assertEquals(VpnProtocol.WIREGUARD, profile.protocol)
        assertEquals("wg-gw.example.org", profile.serverAddress)
        assertEquals(51820, profile.port)

        val config = profile.wireGuardConfig
        assertNotNull(config)
        assertEquals("aGVsbG93b3JsZHByaXZhdGVrZXlleGFtcGxlMTIzNDU2Nw==", config!!.privateKey)
        assertEquals("Y29ycG9yYXRlcHVibGlja2V5ZXhhbXBsZTEyMzQ1Njc4OQ==", config.publicKey)
        assertEquals(1380, config.mtu)
        assertEquals(30, config.persistentKeepalive)
        assertTrue(config.interfaceAddresses.contains("10.99.0.2/24"))
        assertTrue(config.dnsServers.contains("1.1.1.1"))

        val ipv6WgConf = """
            [Interface]
            PrivateKey = aGVsbG93b3JsZHByaXZhdGVrZXlleGFtcGxlMTIzNDU2Nw==
            Address = fd00::10/64

            [Peer]
            PublicKey = Y29ycG9yYXRlcHVibGlja2V5ZXhhbXBsZTEyMzQ1Njc4OQ==
            Endpoint = [fd00::1]:51820
        """.trimIndent()
        val ipv6Profile = vpnRepository.parseWireGuardConf(ipv6WgConf, "IPv6 Peer")
        assertEquals("fd00::1", ipv6Profile.serverAddress)
        assertEquals(51820, ipv6Profile.port)
    }

    @Test
    fun testOpenVpnConfParserDirectives() {
        val sampleOvpn = """
            client
            dev tun
            proto udp
            remote ovpn-gw.example.org 1194
            cipher AES-256-GCM
            tun-mtu 1400
            ifconfig 10.8.0.6 10.8.0.5
            route 192.168.50.0 255.255.255.0
            dhcp-option DNS 9.9.9.9
            nobind

            <ca>
            -----BEGIN CERTIFICATE-----
            MIIDXTCCAkWgAwIBAgIJAL7n8V
            -----END CERTIFICATE-----
            </ca>

            <cert>
            -----BEGIN CERTIFICATE-----
            MIIClTCCAf6gAwIBAgIBAjANBg
            -----END CERTIFICATE-----
            </cert>
        """.trimIndent()

        val profile = vpnRepository.parseOpenVpnConf(sampleOvpn, "Branch OpenVPN")

        assertEquals("Branch OpenVPN", profile.name)
        assertEquals(VpnProtocol.OPENVPN, profile.protocol)
        assertEquals("ovpn-gw.example.org", profile.serverAddress)
        assertEquals(1194, profile.port)

        val config = profile.openVpnConfig
        assertNotNull(config)
        assertEquals("AES-256-GCM", config!!.cipher)
        assertEquals("udp", config.protocol)
        assertEquals(1400, config.mtu)
        assertEquals("10.8.0.6", config.localAddress)
        assertTrue(config.routes.contains("192.168.50.0/24"))
        assertTrue(config.dnsServers.contains("9.9.9.9"))
        assertTrue(config.caCert.contains("MIIDXTCCAkWgAwIBAgIJAL7n8V"))
        assertTrue(config.clientCert.contains("MIIClTCCAf6gAwIBAgIBAjANBg"))
    }

    @Test
    fun testMqttVariableByteIntegerEncoding() {
        fun encodeVarInt(length: Int): ByteArray {
            val out = ByteArrayOutputStream()
            var x = length
            do {
                var encodedByte = (x % 128).toByte()
                x /= 128
                if (x > 0) {
                    encodedByte = (encodedByte.toInt() or 0x80).toByte()
                }
                out.write(encodedByte.toInt())
            } while (x > 0)
            return out.toByteArray()
        }

        val b0 = encodeVarInt(0)
        assertEquals(1, b0.size)
        assertEquals(0x00.toByte(), b0[0])

        val b127 = encodeVarInt(127)
        assertEquals(1, b127.size)
        assertEquals(0x7F.toByte(), b127[0])

        val b128 = encodeVarInt(128)
        assertEquals(2, b128.size)
        assertEquals(0x80.toByte(), b128[0])
        assertEquals(0x01.toByte(), b128[1])

        val b16383 = encodeVarInt(16383)
        assertEquals(2, b16383.size)
        assertEquals(0xFF.toByte(), b16383[0])
        assertEquals(0x7F.toByte(), b16383[1])
    }

    @Test
    fun testCoapExtendedOptionEncodingAndResponseParsing() {
        val longSegment = "telemetry-sensor-measurements" // 29 bytes (> 12 -> uses len nibble 13)
        val req = iotProtocolsRepository.buildCoapRequestPacket(
            msgId = 0x1234,
            token = byteArrayOf(0xAA.toByte(), 0xBB.toByte()),
            method = CoapMethod.GET,
            uriPath = "/$longSegment",
            payload = "",
            type = CoapType.CON,
            observe = false
        )
        // Header (4) + Token (2) + Option byte (delta=11, len=13 -> 0xBD) + extLen (29 - 13 = 16)
        assertEquals(0xBD.toByte(), req[6])
        assertEquals(16.toByte(), req[7])

        // Response with Observe (#6) seq=42, Content-Format (#12) 50 (application/json), and payload
        val respPkt = ByteArrayOutputStream().apply {
            write(0x62) // Ver=1, ACK=2, TokenLen=2
            write(0x45) // 2.05 Content
            write(0x12); write(0x34)
            write(0xAA); write(0xBB)
            write(0x61); write(42) // Option 6 (Observe): delta=6, len=1, value=42
            write(0x61); write(50) // Option 12 (Content-Format): delta=6 (6+6=12), len=1, value=50
            write(0xFF)
            write("""{"temp":24.5}""".toByteArray(Charsets.UTF_8))
        }.toByteArray()

        val parsed = iotProtocolsRepository.parseCoapResponsePacket(
            inData = respPkt,
            inLen = respPkt.size,
            roundTripTimeMs = 15L,
            requestedUriPath = "/$longSegment"
        )
        assertTrue(parsed.isSuccess)
        assertEquals("AABB", parsed.tokenHex)
        assertEquals(42, parsed.observeSequence)
        assertEquals("application/json", parsed.contentType)
        assertEquals("""{"temp":24.5}""", parsed.payload)
    }

    @Test
    fun testCoapCoreLinkFormatParsing() {
        val corePayload = "</sensors/temp>;rt=\"temperature-c\";if=\"sensor\",</sensors/light>;rt=\"light-lux\";obs"

        val resources = mutableListOf<CoapResource>()
        corePayload.split(",").forEach { entry ->
            val link = entry.trim()
            if (link.startsWith("<") && link.contains(">")) {
                val uri = link.substringAfter("<").substringBefore(">")
                val isObs = link.contains(";obs")
                var title = ""
                if (link.contains("title=\"")) {
                    title = link.substringAfter("title=\"").substringBefore("\"")
                }
                resources.add(
                    CoapResource(
                        path = uri,
                        resourceType = "core.sensor",
                        interfaceDescription = "sensor",
                        title = title,
                        isObservable = isObs
                    )
                )
            }
        }

        assertEquals(2, resources.size)
        assertEquals("/sensors/temp", resources[0].path)
        assertFalse(resources[0].isObservable)
        assertEquals("/sensors/light", resources[1].path)
        assertTrue(resources[1].isObservable)
    }

    @Test
    fun testSoapEnvelopeGenerationXPathAndFaultExtraction() {
        val op = SoapOperation(
            name = "GetServerHealth",
            soapAction = "http://example.org/GetServerHealth",
            targetNamespace = "http://example.org/",
            inputMessage = "GetServerHealthSoapIn",
            outputMessage = "GetServerHealthSoapOut",
            envelopeTemplateXml = ""
        )

        val paramValues = mapOf("ServerId" to "SRV-4042", "IncludeSensors" to "true")
        val envelope = webServicesRepository.generateSoapEnvelopeSkeleton(op, paramValues, isSoap12 = false)

        assertTrue(envelope.contains("<soapenv:Envelope"))
        assertTrue(envelope.contains("GetServerHealth"))
        assertTrue(envelope.contains("<ServerId>SRV-4042</ServerId>"))
        assertTrue(envelope.contains("<IncludeSensors>true</IncludeSensors>"))

        val sampleXml = """
            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
              <soap:Body>
                <GetServerHealthResponse xmlns="http://example.org/">
                  <HealthStatus>CRITICAL</HealthStatus>
                  <CpuLoad>94</CpuLoad>
                </GetServerHealthResponse>
              </soap:Body>
            </soap:Envelope>
        """.trimIndent()

        assertNull(webServicesRepository.extractSoapFault(sampleXml))

        val statusMatches = webServicesRepository.evaluateXPath(sampleXml, "//HealthStatus/text()")
        assertEquals(1, statusMatches.size)
        assertEquals("CRITICAL", statusMatches[0])

        val cpuMatches = webServicesRepository.evaluateXPath(sampleXml, "//CpuLoad/text()")
        assertEquals(1, cpuMatches.size)
        assertEquals("94", cpuMatches[0])

        val soap11FaultXml = """
            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
              <soap:Body>
                <soap:Fault>
                  <faultcode>soap:Client</faultcode>
                  <faultstring>Invalid Authentication Token</faultstring>
                </soap:Fault>
              </soap:Body>
            </soap:Envelope>
        """.trimIndent()
        val fault11 = webServicesRepository.extractSoapFault(soap11FaultXml)
        assertNotNull(fault11)
        assertTrue(fault11!!.contains("Invalid Authentication Token"))

        val soap12FaultXml = """
            <env:Envelope xmlns:env="http://www.w3.org/2003/05/soap-envelope">
              <env:Body>
                <env:Fault>
                  <env:Code><env:Value>env:Sender</env:Value></env:Code>
                  <env:Reason><env:Text xml:lang="en">Schema Validation Error</env:Text></env:Reason>
                </env:Fault>
              </env:Body>
            </env:Envelope>
        """.trimIndent()
        val fault12 = webServicesRepository.extractSoapFault(soap12FaultXml)
        assertNotNull(fault12)
        assertTrue(fault12!!.contains("Schema Validation Error"))
    }
}
