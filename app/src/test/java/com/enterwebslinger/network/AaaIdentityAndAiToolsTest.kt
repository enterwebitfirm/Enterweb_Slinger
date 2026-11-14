// === FILE: app/src/test/java/com/enterwebslinger/network/AaaIdentityAndAiToolsTest.kt ===
package com.enterwebslinger.network

import androidx.test.core.app.ApplicationProvider
import com.enterwebslinger.network.core.util.NetworkByteUtils
import com.enterwebslinger.network.data.protocol.IdentityWireCodecs
import com.enterwebslinger.network.data.protocol.RadiusPacketCodec
import com.enterwebslinger.network.domain.model.RadiusAcctStatusType
import com.enterwebslinger.network.domain.model.RadiusAccountingConfig
import com.enterwebslinger.network.domain.model.RadiusAuthProtocol
import com.enterwebslinger.network.domain.model.RadiusAuthRequestConfig
import com.enterwebslinger.network.domain.model.RadiusCoaConfig
import com.enterwebslinger.network.domain.model.RadiusPacketCode
import com.enterwebslinger.network.domain.model.RadiusVsaInput
import com.enterwebslinger.network.domain.model.RadiusVsaVendor
import com.enterwebslinger.network.domain.model.TacacsAuthenMethod
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AaaIdentityAndAiToolsTest {

    @Test
    fun radiusCodec_buildsPapChapMsChapV2_withMessageAuthenticatorAndVsa8021x() {
        val secret = "super-radius-secret"

        // Verify RFC 1320 MD4 test vectors for MS-CHAPv2 NT-Hash
        assertEquals("31D6CFE0D16AE931B73C59D7E0C089C0", NetworkByteUtils.bytesToHex(RadiusPacketCodec.md4Digest(ByteArray(0))))
        assertEquals("A448017AAF21D8525FC10AE87AA6729D", NetworkByteUtils.bytesToHex(RadiusPacketCodec.md4Digest("abc".toByteArray())))

        // 1. PAP + Message-Authenticator + 802.1X VLAN + 4 Vendor VSAs
        val papConfig = RadiusAuthRequestConfig(
            serverIp = "10.0.0.10",
            port = 1812,
            sharedSecret = secret,
            username = "noc_admin",
            password = "MyVeryLongRadiusPassword123!",
            protocol = RadiusAuthProtocol.PAP,
            callingStationId = "AA:BB:CC:DD:EE:FF",
            calledStationId = "00:11:22:33:44:55:CorpWiFi",
            nasIdentifier = "Core-WLC",
            includeMessageAuthenticator = true,
            customVlanId = "120",
            extraVsas = listOf(
                RadiusVsaInput(RadiusVsaVendor.MIKROTIK, 8, stringValue = "50M/100M"),
                RadiusVsaInput(RadiusVsaVendor.CISCO, 1, stringValue = "shell:priv-lvl=15"),
                RadiusVsaInput(RadiusVsaVendor.SOPHOS, 2, stringValue = "IT-Admins"),
                RadiusVsaInput(RadiusVsaVendor.FORTINET, 1, stringValue = "VPN-NOC")
            )
        )
        val builtPap = RadiusPacketCodec.buildAccessRequest(papConfig, identifier = 77)
        assertEquals(RadiusPacketCode.ACCESS_REQUEST.code, builtPap.packetBytes[0].toInt() and 0xFF)
        assertEquals(77, builtPap.packetBytes[1].toInt() and 0xFF)

        val parsedPapReq = RadiusPacketCodec.parsePacket(
            raw = builtPap.packetBytes,
            length = builtPap.packetBytes.size,
            sharedSecret = secret,
            expectedRequestAuthenticator = builtPap.authenticator,
            isAccountingOrCoaRequest = false
        )
        assertTrue(parsedPapReq.messageAuthenticatorPresent)
        assertTrue(parsedPapReq.messageAuthenticatorValid)
        assertEquals("120", parsedPapReq.dot1xSummary.vlanId)
        assertEquals("50M/100M", parsedPapReq.dot1xSummary.mikrotikRateLimit)
        assertEquals("VPN-NOC", parsedPapReq.dot1xSummary.fortinetGroup)
        assertEquals(listOf("shell:priv-lvl=15"), parsedPapReq.dot1xSummary.ciscoAvPairs)
        assertEquals(4, parsedPapReq.decodedVsas.size)

        // 2. CHAP
        val chapBuilt = RadiusPacketCodec.buildAccessRequest(
            papConfig.copy(protocol = RadiusAuthProtocol.CHAP, extraVsas = emptyList(), customVlanId = ""),
            identifier = 88
        )
        val parsedChap = RadiusPacketCodec.parsePacket(chapBuilt.packetBytes, chapBuilt.packetBytes.size, secret, chapBuilt.authenticator)
        assertTrue(parsedChap.attributes.any { it.type == 3 })  // CHAP-Password
        assertTrue(parsedChap.attributes.any { it.type == 60 }) // CHAP-Challenge

        // 3. MS-CHAPv2 (Vendor 311 sub-attrs 11 & 25 -> 50B response)
        val msChapBuilt = RadiusPacketCodec.buildAccessRequest(
            papConfig.copy(protocol = RadiusAuthProtocol.MSCHAPV2, extraVsas = emptyList(), customVlanId = ""),
            identifier = 99
        )
        val parsedMsChap = RadiusPacketCodec.parsePacket(msChapBuilt.packetBytes, msChapBuilt.packetBytes.size, secret, msChapBuilt.authenticator)
        assertTrue(parsedMsChap.decodedVsas.any { it.vendorId == 311L && it.vendorType == 11 })
        assertTrue(parsedMsChap.decodedVsas.any { it.vendorId == 311L && it.vendorType == 25 })
    }

    @Test
    fun radiusCodec_buildsAccountingAndCoaDisconnectPacketsAndAcks() {
        val secret = "coa-acct-secret"

        // Accounting Stop w/ Gigawords
        val acctBuilt = RadiusPacketCodec.buildAccountingRequest(
            RadiusAccountingConfig(
                serverIp = "10.0.0.11",
                sharedSecret = secret,
                username = "pppoe-user1",
                statusType = RadiusAcctStatusType.STOP,
                sessionId = "sess-991",
                sessionTimeSec = 7200,
                inputOctets = 500_000,
                outputOctets = 900_000,
                inputGigawords = 2,
                outputGigawords = 5,
                terminateCause = 1
            ),
            identifier = 12
        )
        val parsedAcct = RadiusPacketCodec.parsePacket(
            raw = acctBuilt.packetBytes,
            length = acctBuilt.packetBytes.size,
            sharedSecret = secret,
            isAccountingOrCoaRequest = true
        )
        assertEquals(RadiusPacketCode.ACCOUNTING_REQUEST, parsedAcct.packetCode)
        assertTrue(parsedAcct.responseAuthenticatorValid)
        assertEquals("sess-991", parsedAcct.sessionId)

        // CoA / Disconnect-Request -> CoA Listener Auto-ACK & Error-Cause NAK
        val coaReq = RadiusPacketCodec.buildCoaOrDisconnectRequest(
            RadiusCoaConfig(
                targetIp = "10.0.0.2",
                sharedSecret = secret,
                isDisconnect = true,
                username = "guest42",
                sessionId = "sess-42"
            ),
            identifier = 33
        )
        val parsedCoaReq = RadiusPacketCodec.parsePacket(coaReq.packetBytes, coaReq.packetBytes.size, secret, isAccountingOrCoaRequest = true)
        assertEquals(RadiusPacketCode.DISCONNECT_REQUEST, parsedCoaReq.packetCode)
        assertTrue(parsedCoaReq.responseAuthenticatorValid)
        assertTrue(parsedCoaReq.messageAuthenticatorValid)

        val nakBytes = RadiusPacketCodec.buildCoaAckOrNakResponse(
            incomingPacket = coaReq.packetBytes,
            incomingLen = coaReq.packetBytes.size,
            sharedSecret = secret,
            isDisconnect = true,
            accept = false,
            errorCauseCode = 503
        )
        val parsedNak = RadiusPacketCodec.parsePacket(
            raw = nakBytes,
            length = nakBytes.size,
            sharedSecret = secret,
            expectedRequestAuthenticator = coaReq.authenticator
        )
        assertEquals(RadiusPacketCode.DISCONNECT_NAK, parsedNak.packetCode)
        assertTrue(parsedNak.responseAuthenticatorValid)
        assertTrue(parsedNak.messageAuthenticatorValid)
        assertEquals(503, parsedNak.errorCauseCode)
        assertEquals("Session Context Not Found", parsedNak.errorCauseText)
    }

    @Test
    fun tacacsCodec_obfuscatesAndRoundTripsAuthorization() {
        val secret = "tacacs-shared-key"
        val sessionId = 0x12345678
        val authorBody = IdentityWireCodecs.buildTacacsAuthorRequestBody(
            authenMethod = TacacsAuthenMethod.PAP,
            privLevel = 15,
            username = "neteng",
            command = "show",
            commandArgs = "running-config"
        )
        val pkt = IdentityWireCodecs.buildTacacsPacket(
            version = IdentityWireCodecs.TAC_PLUS_MAJOR_VER,
            type = IdentityWireCodecs.TAC_PLUS_AUTHOR,
            seqNo = 0x01,
            sessionId = sessionId,
            plainBody = authorBody,
            sharedSecret = secret
        )
        assertEquals(0xC0, pkt[0].toInt() and 0xFF)
        assertEquals(0x02, pkt[1].toInt() and 0xFF)
        assertEquals(0x00, pkt[3].toInt() and 0xFF) // Encrypted flag

        val encryptedBody = pkt.copyOfRange(12, pkt.size)
        val decryptedBody = IdentityWireCodecs.obfuscateTacacsBody(
            sessionId = sessionId,
            sharedSecret = secret,
            version = IdentityWireCodecs.TAC_PLUS_MAJOR_VER,
            seqNo = 0x01,
            data = encryptedBody
        )
        assertArrayEquals(authorBody, decryptedBody)

        // Synthetic TACACS+ AUTHOR PASS_ADD (0x01) reply w/ 1 AV-pair
        val av = "priv-lvl=15".toByteArray(Charsets.UTF_8)
        val replyPlain = ByteBuffer.allocate(6 + 1 + av.size).apply {
            put(0x01) // PASS_ADD
            put(1)    // arg_cnt
            putShort(0)
            putShort(0)
            put(av.size.toByte())
            put(av)
        }.array()
        val replyPkt = IdentityWireCodecs.buildTacacsPacket(
            version = IdentityWireCodecs.TAC_PLUS_MAJOR_VER,
            type = IdentityWireCodecs.TAC_PLUS_AUTHOR,
            seqNo = 0x02,
            sessionId = sessionId,
            plainBody = replyPlain,
            sharedSecret = secret
        )
        val parsed = IdentityWireCodecs.parseTacacsReply(
            header12 = replyPkt.copyOfRange(0, 12),
            rawBody = replyPkt.copyOfRange(12, replyPkt.size),
            sharedSecret = secret
        )
        assertEquals(0x01, parsed.status)
        assertTrue(parsed.obfuscated)
        assertEquals(listOf("priv-lvl=15"), parsed.avPairs)
    }

    @Test
    fun ldapCodec_encodesBindAndDecodesSearchResultEntry() {
        val bindPkt = IdentityWireCodecs.buildLdapBindRequest(1, "admin@corp.local", "AdPass123!")
        assertEquals(0x30, bindPkt[0].toInt() and 0xFF)

        val searchPkt = IdentityWireCodecs.buildLdapSearchRequest(
            messageId = 2,
            baseDn = "DC=corp,DC=local",
            filterText = "(sAMAccountName=jdoe)",
            attributes = listOf("cn", "memberOf")
        )
        assertEquals(0x30, searchPkt[0].toInt() and 0xFF)

        // Build synthetic LDAP SearchResultEntry (0x64) with dn + memberOf SET
        val memberAttr = ByteArrayOutputStream().apply {
            write(IdentityWireCodecs.encodeBerTlv(0x04, "memberOf".toByteArray()))
            val grp1 = IdentityWireCodecs.encodeBerTlv(0x04, "CN=NetAdmins,DC=corp,DC=local".toByteArray())
            write(IdentityWireCodecs.encodeBerTlv(0x31, grp1))
        }.toByteArray()
        val attrSeq = IdentityWireCodecs.encodeBerTlv(0x30, IdentityWireCodecs.encodeBerTlv(0x30, memberAttr))
        val entryBody = ByteArrayOutputStream().apply {
            write(IdentityWireCodecs.encodeBerTlv(0x04, "CN=Jane Doe,OU=NOC,DC=corp,DC=local".toByteArray()))
            write(attrSeq)
        }.toByteArray()
        val msgBody = ByteArrayOutputStream().apply {
            write(IdentityWireCodecs.encodeBerTlv(0x02, byteArrayOf(2)))
            write(IdentityWireCodecs.encodeBerTlv(0x64, entryBody))
        }.toByteArray()
        val rawMsg = IdentityWireCodecs.encodeBerTlv(0x30, msgBody)

        val parsed = IdentityWireCodecs.parseLdapMessage(rawMsg)
        assertEquals(2, parsed.messageId)
        assertEquals(0x64, parsed.protocolOpTag)
        assertNotNull(parsed.entry)
        assertEquals("CN=Jane Doe,OU=NOC,DC=corp,DC=local", parsed.entry?.dn)
        assertEquals(listOf("CN=NetAdmins,DC=corp,DC=local"), parsed.entry?.attributes?.get("memberOf"))
    }

    @Test
    fun aiDiagnosticService_registersFirewallVpnAndRadiusTools() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val container = com.enterwebslinger.network.core.di.AppContainer(ctx)
        assertEquals(22, container.aiDiagnosticService.allToolDeclarations.size)
        val names = container.aiDiagnosticService.registeredToolNames
        assertTrue(names.contains("checkFirewallHealthAndTunnels"))
        assertTrue(names.contains("analyzeIpsecTunnelDropLog"))
        assertTrue(names.contains("simulateFirewallPolicyMatch"))
        assertTrue(names.contains("runRadiusAuthAndVsaTest"))

        val dropAnalysis = container.aiDiagnosticService.executeNetworkTool(
            "analyzeIpsecTunnelDropLog",
            mapOf(
                "rawLogText" to "IKEv2 peer returned NO_PROPOSAL_CHOSEN and INVALID_SPI during Phase-2 SA rekey",
                "localVendor" to "Sophos XGS",
                "remoteVendor" to "FortiGate"
            )
        )
        @Suppress("UNCHECKED_CAST")
        val issues = dropAnalysis["detected_issues"] as List<String>
        assertTrue(issues.any { it.contains("NO_PROPOSAL_CHOSEN") })
        assertTrue(issues.any { it.contains("INVALID_SPI") })
    }
}
