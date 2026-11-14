// === FILE: app/src/main/java/com/enterwebslinger/network/data/protocol/RadiusPacketCodec.kt ===
package com.enterwebslinger.network.data.protocol

import com.enterwebslinger.network.core.util.NetworkByteUtils
import com.enterwebslinger.network.domain.model.Radius8021xSummary
import com.enterwebslinger.network.domain.model.RadiusAcctStatusType
import com.enterwebslinger.network.domain.model.RadiusAccountingConfig
import com.enterwebslinger.network.domain.model.RadiusAttributeEntry
import com.enterwebslinger.network.domain.model.RadiusAuthProtocol
import com.enterwebslinger.network.domain.model.RadiusAuthRequestConfig
import com.enterwebslinger.network.domain.model.RadiusCoaConfig
import com.enterwebslinger.network.domain.model.RadiusDecodedVsa
import com.enterwebslinger.network.domain.model.RadiusPacketCode
import com.enterwebslinger.network.domain.model.RadiusResponseCode
import com.enterwebslinger.network.domain.model.RadiusVsaVendor
import com.enterwebslinger.network.domain.model.RadiusWorkbenchResult
import java.io.ByteArrayOutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Pure-Kotlin RFC 2865 / 2866 / 3579 / 5176 / 2548 Binary RADIUS Codec:
 * - PAP (MD5 XOR), CHAP (RFC 1994), MS-CHAPv2 (MD4 + SHA-1 + 3x DES-ECB, Vendor 311)
 * - Accounting-Request (Start, Stop, Interim-Update, On/Off + Gigawords)
 * - CoA-Request (43) & Disconnect-Request / PoD (40) + Error-Cause (101)
 * - Message-Authenticator (Attr 80 HMAC-MD5) generation & Blast-RADIUS response verification
 * - Multi-vendor VSA (Attr 26) & 802.1X dynamic VLAN decoder (MikroTik, Cisco, Sophos, Fortinet)
 */
object RadiusPacketCodec {

    private val secureRandom = SecureRandom()

    data class BuiltRadiusPacket(
        val code: RadiusPacketCode,
        val identifier: Int,
        val authenticator: ByteArray,
        val packetBytes: ByteArray
    )

    data class ParsedRadiusPacket(
        val packetCode: RadiusPacketCode,
        val identifier: Int,
        val length: Int,
        val authenticator: ByteArray,
        val responseAuthenticatorValid: Boolean,
        val messageAuthenticatorPresent: Boolean,
        val messageAuthenticatorValid: Boolean,
        val replyMessage: String,
        val userName: String,
        val sessionId: String,
        val errorCauseCode: Int?,
        val errorCauseText: String,
        val dot1xSummary: Radius8021xSummary,
        val attributes: List<RadiusAttributeEntry>,
        val decodedVsas: List<RadiusDecodedVsa>
    )

    fun buildAccessRequest(
        config: RadiusAuthRequestConfig,
        identifier: Int = secureRandom.nextInt(255) + 1,
        requestAuthenticator: ByteArray = ByteArray(16).apply { secureRandom.nextBytes(this) }
    ): BuiltRadiusPacket {
        require(requestAuthenticator.size == 16) { "Request Authenticator must be 16 bytes" }
        val attrs = ByteArrayOutputStream()

        writeStringAttr(attrs, 1, config.username.trim())

        when (config.protocol) {
            RadiusAuthProtocol.PAP -> {
                val encryptedPwd = encryptPapPassword(config.password, config.sharedSecret, requestAuthenticator)
                writeBytesAttr(attrs, 2, encryptedPwd)
            }
            RadiusAuthProtocol.CHAP -> {
                val chapId = secureRandom.nextInt(256)
                val challenge = ByteArray(16).apply { secureRandom.nextBytes(this) }
                val md5 = MessageDigest.getInstance("MD5").apply {
                    update(chapId.toByte())
                    update(config.password.toByteArray(Charsets.UTF_8))
                    update(challenge)
                }.digest()
                val chapPassword = ByteArray(17)
                chapPassword[0] = chapId.toByte()
                System.arraycopy(md5, 0, chapPassword, 1, 16)
                writeBytesAttr(attrs, 3, chapPassword)  // CHAP-Password
                writeBytesAttr(attrs, 60, challenge)    // CHAP-Challenge
            }
            RadiusAuthProtocol.MSCHAPV2 -> {
                val authChallenge = ByteArray(16).apply { secureRandom.nextBytes(this) }
                val peerChallenge = ByteArray(16).apply { secureRandom.nextBytes(this) }
                val ident = secureRandom.nextInt(256)
                val msChap2Response = buildMsChap2ResponsePayload(
                    ident = ident,
                    authChallenge = authChallenge,
                    peerChallenge = peerChallenge,
                    username = config.username.trim(),
                    password = config.password
                )
                // Microsoft Vendor ID = 311, Sub-Attr 11 = MS-CHAP-Challenge, Sub-Attr 25 = MS-CHAP2-Response
                writeVsaAttr(attrs, 311L, 11, authChallenge)
                writeVsaAttr(attrs, 311L, 25, msChap2Response)
            }
        }

        // Standard NAS Attributes
        writeIpAttr(attrs, 4, config.nasIpAddress.ifBlank { "127.0.0.1" })
        writeIntAttr(attrs, 5, config.nasPort.toLong())
        writeIntAttr(attrs, 6, config.serviceType.code.toLong())
        writeIntAttr(attrs, 7, config.framedProtocol.toLong())
        if (config.calledStationId.isNotBlank()) writeStringAttr(attrs, 30, config.calledStationId.trim())
        if (config.callingStationId.isNotBlank()) writeStringAttr(attrs, 31, config.callingStationId.trim())
        if (config.nasIdentifier.isNotBlank()) writeStringAttr(attrs, 32, config.nasIdentifier.trim())

        // Optional Dynamic 802.1X VLAN request hint
        if (config.customVlanId.isNotBlank()) {
            writeIntAttr(attrs, 64, 13L) // Tunnel-Type = VLAN
            writeIntAttr(attrs, 65, 6L)  // Tunnel-Medium-Type = 802
            writeStringAttr(attrs, 81, config.customVlanId.trim())
        }

        // Extra custom VSAs
        config.extraVsas.forEach { vsa ->
            val valBytes = when {
                vsa.intValue != null -> ByteBuffer.allocate(4).putInt(vsa.intValue.toInt()).array()
                vsa.ipv4Value.isNotBlank() -> parseIpv4Bytes(vsa.ipv4Value)
                else -> vsa.stringValue.toByteArray(Charsets.UTF_8)
            }
            writeVsaAttr(attrs, vsa.vendor.vendorId, vsa.vendorType, valBytes)
        }

        val messageAuthPos = if (config.includeMessageAuthenticator) {
            val pos = attrs.size()
            attrs.write(80)
            attrs.write(18)
            attrs.write(ByteArray(16))
            pos
        } else -1

        val attrBytes = attrs.toByteArray()
        val totalLen = 20 + attrBytes.size
        val pkt = ByteBuffer.allocate(totalLen).apply {
            put(RadiusPacketCode.ACCESS_REQUEST.code.toByte())
            put((identifier and 0xFF).toByte())
            putShort(totalLen.toShort())
            put(requestAuthenticator)
            put(attrBytes)
        }.array()

        if (messageAuthPos >= 0) {
            val hmac = computeHmacMd5(config.sharedSecret, pkt)
            System.arraycopy(hmac, 0, pkt, 20 + messageAuthPos + 2, 16)
        }

        return BuiltRadiusPacket(RadiusPacketCode.ACCESS_REQUEST, identifier and 0xFF, requestAuthenticator, pkt)
    }

    fun buildAccountingRequest(
        config: RadiusAccountingConfig,
        identifier: Int = secureRandom.nextInt(255) + 1
    ): BuiltRadiusPacket {
        val attrs = ByteArrayOutputStream()
        writeIntAttr(attrs, 40, config.statusType.code.toLong()) // Acct-Status-Type
        writeStringAttr(attrs, 44, config.sessionId.ifBlank { "sess-${System.currentTimeMillis()}" })
        if (config.username.isNotBlank()) writeStringAttr(attrs, 1, config.username.trim())
        writeIpAttr(attrs, 4, config.nasIpAddress.ifBlank { "127.0.0.1" })
        if (config.nasIdentifier.isNotBlank()) writeStringAttr(attrs, 32, config.nasIdentifier.trim())
        if (config.framedIpAddress.isNotBlank()) writeIpAttr(attrs, 8, config.framedIpAddress.trim())
        writeIntAttr(attrs, 46, config.sessionTimeSec.coerceAtLeast(0L))
        writeIntAttr(attrs, 42, config.inputOctets.coerceAtLeast(0L))
        writeIntAttr(attrs, 43, config.outputOctets.coerceAtLeast(0L))
        if (config.inputGigawords > 0L) writeIntAttr(attrs, 52, config.inputGigawords)
        if (config.outputGigawords > 0L) writeIntAttr(attrs, 53, config.outputGigawords)
        if (config.statusType == RadiusAcctStatusType.STOP && config.terminateCause != null) {
            writeIntAttr(attrs, 49, config.terminateCause.toLong())
        }

        val attrBytes = attrs.toByteArray()
        val totalLen = 20 + attrBytes.size
        val pkt = ByteBuffer.allocate(totalLen).apply {
            put(RadiusPacketCode.ACCOUNTING_REQUEST.code.toByte())
            put((identifier and 0xFF).toByte())
            putShort(totalLen.toShort())
            put(ByteArray(16)) // Zeroed before MD5 hash
            put(attrBytes)
        }.array()

        // RFC 2866 Request Authenticator = MD5(Code + ID + Length + 16*0x00 + Attrs + Secret)
        val reqAuth = computeMd5WithSecret(pkt, config.sharedSecret)
        System.arraycopy(reqAuth, 0, pkt, 4, 16)

        return BuiltRadiusPacket(RadiusPacketCode.ACCOUNTING_REQUEST, identifier and 0xFF, reqAuth, pkt)
    }

    fun buildCoaOrDisconnectRequest(
        config: RadiusCoaConfig,
        identifier: Int = secureRandom.nextInt(255) + 1
    ): BuiltRadiusPacket {
        val code = if (config.isDisconnect) RadiusPacketCode.DISCONNECT_REQUEST else RadiusPacketCode.COA_REQUEST
        val attrs = ByteArrayOutputStream()
        if (config.username.isNotBlank()) writeStringAttr(attrs, 1, config.username.trim())
        if (config.sessionId.isNotBlank()) writeStringAttr(attrs, 44, config.sessionId.trim())
        if (config.nasIpAddress.isNotBlank()) writeIpAttr(attrs, 4, config.nasIpAddress.trim())
        if (config.callingStationId.isNotBlank()) writeStringAttr(attrs, 31, config.callingStationId.trim())
        if (!config.isDisconnect) {
            if (config.filterIdOrRateLimit.isNotBlank()) writeStringAttr(attrs, 11, config.filterIdOrRateLimit.trim())
            config.sessionTimeoutSec?.let { writeIntAttr(attrs, 27, it) }
            if (config.vsaValue.isNotBlank()) {
                writeVsaAttr(attrs, config.vsaVendor.vendorId, config.vsaType, config.vsaValue.toByteArray(Charsets.UTF_8))
            }
        }

        val msgAuthPos = attrs.size()
        attrs.write(80)
        attrs.write(18)
        attrs.write(ByteArray(16))

        val attrBytes = attrs.toByteArray()
        val totalLen = 20 + attrBytes.size
        val pkt = ByteBuffer.allocate(totalLen).apply {
            put(code.code.toByte())
            put((identifier and 0xFF).toByte())
            putShort(totalLen.toShort())
            put(ByteArray(16))
            put(attrBytes)
        }.array()

        // First compute Message-Authenticator (with zeroed Request Authenticator placeholder per RFC 5176 + RFC 3579)
        val hmac = computeHmacMd5(config.sharedSecret, pkt)
        System.arraycopy(hmac, 0, pkt, 20 + msgAuthPos + 2, 16)

        // Then compute RFC 5176 Request Authenticator = MD5(Code + ID + Len + 16*0x00 + Attrs + Secret)
        val reqAuth = computeMd5WithSecret(pkt, config.sharedSecret)
        System.arraycopy(reqAuth, 0, pkt, 4, 16)

        return BuiltRadiusPacket(code, identifier and 0xFF, reqAuth, pkt)
    }

    fun buildCoaAckOrNakResponse(
        incomingPacket: ByteArray,
        incomingLen: Int,
        sharedSecret: String,
        isDisconnect: Boolean,
        accept: Boolean,
        errorCauseCode: Int? = null
    ): ByteArray {
        val id = if (incomingLen >= 2) incomingPacket[1].toInt() and 0xFF else 0
        val reqAuth = if (incomingLen >= 20) incomingPacket.copyOfRange(4, 20) else ByteArray(16)
        val replyCode = when {
            isDisconnect && accept -> RadiusPacketCode.DISCONNECT_ACK
            isDisconnect && !accept -> RadiusPacketCode.DISCONNECT_NAK
            !isDisconnect && accept -> RadiusPacketCode.COA_ACK
            else -> RadiusPacketCode.COA_NAK
        }

        val attrs = ByteArrayOutputStream()
        if (!accept && errorCauseCode != null) {
            writeIntAttr(attrs, 101, errorCauseCode.toLong())
        }
        val msgAuthPos = attrs.size()
        attrs.write(80)
        attrs.write(18)
        attrs.write(ByteArray(16))

        val attrBytes = attrs.toByteArray()
        val totalLen = 20 + attrBytes.size
        val pkt = ByteBuffer.allocate(totalLen).apply {
            put(replyCode.code.toByte())
            put(id.toByte())
            putShort(totalLen.toShort())
            put(reqAuth)
            put(attrBytes)
        }.array()

        val hmac = computeHmacMd5(sharedSecret, pkt)
        System.arraycopy(hmac, 0, pkt, 20 + msgAuthPos + 2, 16)

        // Response Authenticator = MD5(Code + ID + Len + ReqAuth + Attrs + Secret)
        val respAuth = computeMd5WithSecret(pkt, sharedSecret)
        System.arraycopy(respAuth, 0, pkt, 4, 16)
        return pkt
    }

    fun parsePacket(
        raw: ByteArray,
        length: Int,
        sharedSecret: String? = null,
        expectedRequestAuthenticator: ByteArray? = null,
        isAccountingOrCoaRequest: Boolean = false
    ): ParsedRadiusPacket {
        if (length < 20) {
            return ParsedRadiusPacket(
                packetCode = RadiusPacketCode.UNKNOWN,
                identifier = 0,
                length = length,
                authenticator = ByteArray(16),
                responseAuthenticatorValid = false,
                messageAuthenticatorPresent = false,
                messageAuthenticatorValid = false,
                replyMessage = "",
                userName = "",
                sessionId = "",
                errorCauseCode = null,
                errorCauseText = "",
                dot1xSummary = Radius8021xSummary(),
                attributes = emptyList(),
                decodedVsas = emptyList()
            )
        }

        val codeByte = raw[0].toInt() and 0xFF
        val idByte = raw[1].toInt() and 0xFF
        val declaredLen = (((raw[2].toInt() and 0xFF) shl 8) or (raw[3].toInt() and 0xFF)).coerceAtMost(length)
        val pktAuth = raw.copyOfRange(4, 20)
        val packetCode = RadiusPacketCode.entries.firstOrNull { it.code == codeByte } ?: RadiusPacketCode.UNKNOWN

        val standardAttrs = mutableListOf<RadiusAttributeEntry>()
        val vsas = mutableListOf<RadiusDecodedVsa>()
        var replyMsg = ""
        var userName = ""
        var sessionId = ""
        var errorCauseCode: Int? = null
        var vlanId = ""
        var tunnelType = ""
        var tunnelMed = ""
        var filterId = ""
        var sessionTimeout: Long? = null
        var framedPool = ""
        var mkRateLimit = ""
        var mkGroup = ""
        var fortiGroup = ""
        val ciscoAvPairs = mutableListOf<String>()

        var msgAuthOffset = -1
        var msgAuthReceived: ByteArray? = null

        var offset = 20
        while (offset + 2 <= declaredLen) {
            val attrType = raw[offset].toInt() and 0xFF
            val attrLen = raw[offset + 1].toInt() and 0xFF
            if (attrLen < 2 || offset + attrLen > declaredLen) break

            val valStart = offset + 2
            val valLen = attrLen - 2
            val valBytes = raw.copyOfRange(valStart, valStart + valLen)

            when (attrType) {
                1 -> {
                    userName = String(valBytes, Charsets.UTF_8)
                    standardAttrs.add(RadiusAttributeEntry(attrType, "User-Name (1)", userName))
                }
                4 -> standardAttrs.add(RadiusAttributeEntry(attrType, "NAS-IP-Address (4)", formatIpv4(valBytes)))
                5 -> standardAttrs.add(RadiusAttributeEntry(attrType, "NAS-Port (5)", readUint32(valBytes).toString()))
                6 -> standardAttrs.add(RadiusAttributeEntry(attrType, "Service-Type (6)", readUint32(valBytes).toString()))
                7 -> standardAttrs.add(RadiusAttributeEntry(attrType, "Framed-Protocol (7)", readUint32(valBytes).toString()))
                8 -> standardAttrs.add(RadiusAttributeEntry(attrType, "Framed-IP-Address (8)", formatIpv4(valBytes)))
                11 -> {
                    filterId = String(valBytes, Charsets.UTF_8)
                    standardAttrs.add(RadiusAttributeEntry(attrType, "Filter-Id (11)", filterId))
                }
                18 -> {
                    val txt = String(valBytes, Charsets.UTF_8)
                    replyMsg = if (replyMsg.isBlank()) txt else "$replyMsg | $txt"
                    standardAttrs.add(RadiusAttributeEntry(attrType, "Reply-Message (18)", txt))
                }
                26 -> decodeVsaBlock(valBytes, vsas)?.let { summaryUpdate ->
                    if (summaryUpdate.mikrotikRate.isNotEmpty()) mkRateLimit = summaryUpdate.mikrotikRate
                    if (summaryUpdate.mikrotikGroup.isNotEmpty()) mkGroup = summaryUpdate.mikrotikGroup
                    if (summaryUpdate.fortinetGroup.isNotEmpty()) fortiGroup = summaryUpdate.fortinetGroup
                    ciscoAvPairs.addAll(summaryUpdate.ciscoAvs)
                }
                27 -> {
                    val sec = readUint32(valBytes)
                    sessionTimeout = sec
                    standardAttrs.add(RadiusAttributeEntry(attrType, "Session-Timeout (27)", "${sec}s"))
                }
                30 -> standardAttrs.add(RadiusAttributeEntry(attrType, "Called-Station-Id (30)", String(valBytes, Charsets.UTF_8)))
                31 -> standardAttrs.add(RadiusAttributeEntry(attrType, "Calling-Station-Id (31)", String(valBytes, Charsets.UTF_8)))
                32 -> standardAttrs.add(RadiusAttributeEntry(attrType, "NAS-Identifier (32)", String(valBytes, Charsets.UTF_8)))
                40 -> standardAttrs.add(RadiusAttributeEntry(attrType, "Acct-Status-Type (40)", acctStatusName(readUint32(valBytes).toInt())))
                44 -> {
                    sessionId = String(valBytes, Charsets.UTF_8)
                    standardAttrs.add(RadiusAttributeEntry(attrType, "Acct-Session-Id (44)", sessionId))
                }
                64 -> {
                    val v = readUint32(valBytes) and 0x00FFFFFFL
                    tunnelType = if (v == 13L) "VLAN (13)" else v.toString()
                    standardAttrs.add(RadiusAttributeEntry(attrType, "Tunnel-Type (64)", tunnelType))
                }
                65 -> {
                    val v = readUint32(valBytes) and 0x00FFFFFFL
                    tunnelMed = if (v == 6L) "802 / Ethernet (6)" else v.toString()
                    standardAttrs.add(RadiusAttributeEntry(attrType, "Tunnel-Medium-Type (65)", tunnelMed))
                }
                80 -> {
                    if (valLen == 16) {
                        msgAuthOffset = valStart
                        msgAuthReceived = valBytes
                    }
                    standardAttrs.add(RadiusAttributeEntry(attrType, "Message-Authenticator (80)", NetworkByteUtils.bytesToHex(valBytes)))
                }
                81 -> {
                    vlanId = String(valBytes, Charsets.UTF_8).trim()
                    standardAttrs.add(RadiusAttributeEntry(attrType, "Tunnel-Private-Group-Id (81)", vlanId))
                }
                88 -> {
                    framedPool = String(valBytes, Charsets.UTF_8)
                    standardAttrs.add(RadiusAttributeEntry(attrType, "Framed-Pool (88)", framedPool))
                }
                101 -> {
                    val code = readUint32(valBytes).toInt()
                    errorCauseCode = code
                    standardAttrs.add(RadiusAttributeEntry(attrType, "Error-Cause (101)", "$code (${errorCauseDescription(code)})"))
                }
                else -> {
                    val readable = String(valBytes, Charsets.UTF_8)
                    val display = if (readable.isNotEmpty() && readable.all { it.code in 32..126 }) readable else NetworkByteUtils.bytesToHex(valBytes, " ")
                    standardAttrs.add(RadiusAttributeEntry(attrType, "Attr-$attrType", display))
                }
            }
            offset += attrLen
        }

        val respAuthValid = if (!sharedSecret.isNullOrEmpty()) {
            val copy = raw.copyOf(declaredLen)
            if (isAccountingOrCoaRequest) {
                // Zero out authenticator for Request Authenticator check (RFC 2866 / 5176)
                for (i in 4 until 20) copy[i] = 0
                val expected = computeMd5WithSecret(copy, sharedSecret)
                expected.contentEquals(pktAuth)
            } else if (expectedRequestAuthenticator != null && expectedRequestAuthenticator.size == 16) {
                System.arraycopy(expectedRequestAuthenticator, 0, copy, 4, 16)
                val expected = computeMd5WithSecret(copy, sharedSecret)
                expected.contentEquals(pktAuth)
            } else false
        } else false

        val msgAuthValid = if (msgAuthOffset >= 0 && msgAuthReceived != null && !sharedSecret.isNullOrEmpty()) {
            val copy = raw.copyOf(declaredLen)
            if (!isAccountingOrCoaRequest && expectedRequestAuthenticator != null && expectedRequestAuthenticator.size == 16) {
                System.arraycopy(expectedRequestAuthenticator, 0, copy, 4, 16)
            } else if (isAccountingOrCoaRequest) {
                for (i in 4 until 20) copy[i] = 0
            }
            for (i in 0 until 16) copy[msgAuthOffset + i] = 0
            val computed = computeHmacMd5(sharedSecret, copy)
            computed.contentEquals(msgAuthReceived)
        } else false

        return ParsedRadiusPacket(
            packetCode = packetCode,
            identifier = idByte,
            length = declaredLen,
            authenticator = pktAuth,
            responseAuthenticatorValid = respAuthValid,
            messageAuthenticatorPresent = msgAuthOffset >= 0,
            messageAuthenticatorValid = msgAuthValid,
            replyMessage = replyMsg,
            userName = userName,
            sessionId = sessionId,
            errorCauseCode = errorCauseCode,
            errorCauseText = errorCauseCode?.let { errorCauseDescription(it) }.orEmpty(),
            dot1xSummary = Radius8021xSummary(
                vlanId = vlanId,
                tunnelType = tunnelType,
                tunnelMediumType = tunnelMed,
                filterId = filterId,
                sessionTimeoutSec = sessionTimeout,
                framedPool = framedPool,
                mikrotikRateLimit = mkRateLimit,
                mikrotikGroup = mkGroup,
                fortinetGroup = fortiGroup,
                ciscoAvPairs = ciscoAvPairs
            ),
            attributes = standardAttrs,
            decodedVsas = vsas
        )
    }

    fun toWorkbenchResult(
        serverIp: String,
        port: Int,
        requestCode: RadiusPacketCode,
        protocolUsed: String,
        rttMs: Long,
        reqBytes: ByteArray,
        respBytes: ByteArray,
        parsed: ParsedRadiusPacket,
        errorMessage: String? = null
    ): RadiusWorkbenchResult {
        val mappedResponseCode = when (parsed.packetCode) {
            RadiusPacketCode.ACCESS_ACCEPT, RadiusPacketCode.COA_ACK, RadiusPacketCode.DISCONNECT_ACK -> RadiusResponseCode.ACCESS_ACCEPT
            RadiusPacketCode.ACCESS_REJECT, RadiusPacketCode.COA_NAK, RadiusPacketCode.DISCONNECT_NAK -> RadiusResponseCode.ACCESS_REJECT
            RadiusPacketCode.ACCESS_CHALLENGE -> RadiusResponseCode.ACCESS_CHALLENGE
            RadiusPacketCode.ACCOUNTING_RESPONSE -> RadiusResponseCode.ACCOUNTING_RESPONSE
            else -> RadiusResponseCode.TIMEOUT_OR_ERROR
        }
        val isPositive = parsed.packetCode in listOf(
            RadiusPacketCode.ACCESS_ACCEPT,
            RadiusPacketCode.ACCOUNTING_RESPONSE,
            RadiusPacketCode.COA_ACK,
            RadiusPacketCode.DISCONNECT_ACK
        )
        return RadiusWorkbenchResult(
            serverIp = serverIp,
            port = port,
            requestCode = requestCode,
            responsePacketCode = parsed.packetCode,
            responseCode = mappedResponseCode,
            protocolUsed = protocolUsed,
            rttMs = rttMs,
            isSuccess = isPositive && errorMessage == null,
            responseAuthenticatorValid = parsed.responseAuthenticatorValid,
            messageAuthenticatorPresent = parsed.messageAuthenticatorPresent,
            messageAuthenticatorValid = parsed.messageAuthenticatorValid,
            replyMessage = parsed.replyMessage,
            errorCauseCode = parsed.errorCauseCode,
            errorCauseText = parsed.errorCauseText,
            dot1xSummary = parsed.dot1xSummary,
            attributes = parsed.attributes,
            decodedVsas = parsed.decodedVsas,
            rawRequestHex = NetworkByteUtils.bytesToHex(reqBytes, " "),
            rawResponseHex = NetworkByteUtils.bytesToHex(respBytes, " "),
            errorMessage = errorMessage
        )
    }

    // --- VSA Decoder ---

    private data class VsaSummaryHint(
        val mikrotikRate: String = "",
        val mikrotikGroup: String = "",
        val fortinetGroup: String = "",
        val ciscoAvs: List<String> = emptyList()
    )

    private fun decodeVsaBlock(valBytes: ByteArray, out: MutableList<RadiusDecodedVsa>): VsaSummaryHint? {
        if (valBytes.size < 6) return null
        val vendorId = ByteBuffer.wrap(valBytes, 0, 4).int.toLong() and 0xFFFFFFFFL
        val vendorName = RadiusVsaVendor.entries.firstOrNull { it.vendorId == vendorId }?.label ?: "Vendor ($vendorId)"
        var mkRate = ""
        var mkGroup = ""
        var fortiGrp = ""
        val ciscoList = mutableListOf<String>()

        var subPos = 4
        while (subPos + 2 <= valBytes.size) {
            val subType = valBytes[subPos].toInt() and 0xFF
            val subLen = valBytes[subPos + 1].toInt() and 0xFF
            if (subLen < 2 || subPos + subLen > valBytes.size) break
            val subBytes = valBytes.copyOfRange(subPos + 2, subPos + subLen)
            val attrName = vsaAttributeName(vendorId, subType)
            val decoded = decodeVsaValue(vendorId, subType, subBytes)
            out.add(
                RadiusDecodedVsa(
                    vendorId = vendorId,
                    vendorName = vendorName,
                    vendorType = subType,
                    attributeName = attrName,
                    decodedValue = decoded,
                    rawHex = NetworkByteUtils.bytesToHex(subBytes, " ")
                )
            )
            if (vendorId == 14988L && subType == 8) mkRate = decoded
            if (vendorId == 14988L && subType == 3) mkGroup = decoded
            if (vendorId == 12356L && subType == 1) fortiGrp = decoded
            if (vendorId == 9L && subType == 1) ciscoList.add(decoded)
            subPos += subLen
        }
        return VsaSummaryHint(mkRate, mkGroup, fortiGrp, ciscoList)
    }

    private fun vsaAttributeName(vendorId: Long, subType: Int): String = when (vendorId) {
        14988L -> when (subType) {
            1 -> "Mikrotik-Recv-Limit"
            2 -> "Mikrotik-Xmit-Limit"
            3 -> "Mikrotik-Group"
            8 -> "Mikrotik-Rate-Limit"
            19 -> "Mikrotik-Address-List"
            else -> "Mikrotik-Attr-$subType"
        }
        9L -> when (subType) {
            1 -> "Cisco-AVPair"
            else -> "Cisco-Attr-$subType"
        }
        2604L -> when (subType) {
            1 -> "Sophos-User-Name"
            2 -> "Sophos-Group-Name"
            else -> "Sophos-Attr-$subType"
        }
        12356L -> when (subType) {
            1 -> "Fortinet-Group-Name"
            2 -> "Fortinet-Client-IP-Address"
            3 -> "Fortinet-Vdom-Name"
            6 -> "Fortinet-Access-Profile"
            else -> "Fortinet-Attr-$subType"
        }
        311L -> when (subType) {
            2 -> "MS-CHAP-Error"
            11 -> "MS-CHAP-Challenge"
            25 -> "MS-CHAP2-Response"
            26 -> "MS-CHAP2-Success"
            else -> "Microsoft-Attr-$subType"
        }
        else -> "VSA-$vendorId-$subType"
    }

    private fun decodeVsaValue(vendorId: Long, subType: Int, bytes: ByteArray): String {
        if ((vendorId == 14988L && (subType == 1 || subType == 2)) && bytes.size == 4) {
            return readUint32(bytes).toString()
        }
        if (vendorId == 12356L && subType == 2 && bytes.size == 4) {
            return formatIpv4(bytes)
        }
        val utf8 = String(bytes, Charsets.UTF_8)
        return if (utf8.isNotEmpty() && utf8.all { it.code in 32..126 }) utf8 else NetworkByteUtils.bytesToHex(bytes, " ")
    }

    // --- Crypto Primitives (MD5 XOR, HMAC-MD5, MS-CHAPv2 MD4+SHA1+DES) ---

    fun encryptPapPassword(password: String, sharedSecret: String, authenticator: ByteArray): ByteArray {
        val rawBytes = password.toByteArray(Charsets.UTF_8)
        val paddedLen = ((rawBytes.size.coerceAtLeast(1) + 15) / 16) * 16
        val padded = ByteArray(paddedLen)
        System.arraycopy(rawBytes, 0, padded, 0, rawBytes.size)

        val secretBytes = sharedSecret.toByteArray(Charsets.UTF_8)
        val output = ByteArray(paddedLen)
        var prevCipherBlock = authenticator

        for (block in 0 until paddedLen / 16) {
            val md5 = MessageDigest.getInstance("MD5").apply {
                update(secretBytes)
                update(prevCipherBlock)
            }.digest()
            val base = block * 16
            for (i in 0 until 16) {
                output[base + i] = (padded[base + i].toInt() xor md5[i].toInt()).toByte()
            }
            prevCipherBlock = output.copyOfRange(base, base + 16)
        }
        return output
    }

    fun computeHmacMd5(secret: String, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacMD5")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacMD5"))
        return mac.doFinal(data)
    }

    fun computeMd5WithSecret(packet: ByteArray, secret: String): ByteArray {
        return MessageDigest.getInstance("MD5").apply {
            update(packet)
            update(secret.toByteArray(Charsets.UTF_8))
        }.digest()
    }

    /**
     * RFC 2759 Section 8 / RFC 2548 MS-CHAP2-Response (50 bytes):
     * [1B Ident][1B Flags=0][16B PeerChallenge][8B Reserved=0][24B NT-Response]
     */
    fun buildMsChap2ResponsePayload(
        ident: Int,
        authChallenge: ByteArray,
        peerChallenge: ByteArray,
        username: String,
        password: String
    ): ByteArray {
        val challenge8 = msChap2ChallengeHash(peerChallenge, authChallenge, username)
        val ntHash = ntPasswordHash(password)
        val ntResponse = challengeResponse(challenge8, ntHash)

        return ByteArray(50).apply {
            this[0] = (ident and 0xFF).toByte()
            this[1] = 0x00
            System.arraycopy(peerChallenge, 0, this, 2, 16)
            // bytes 18..25 reserved 0x00
            System.arraycopy(ntResponse, 0, this, 26, 24)
        }
    }

    fun msChap2ChallengeHash(peerChallenge: ByteArray, authChallenge: ByteArray, username: String): ByteArray {
        val cleanUser = username.substringAfterLast('\\').substringBefore('@')
        val sha1 = MessageDigest.getInstance("SHA-1").apply {
            update(peerChallenge)
            update(authChallenge)
            update(cleanUser.toByteArray(Charsets.UTF_8))
        }.digest()
        return sha1.copyOfRange(0, 8)
    }

    fun ntPasswordHash(password: String): ByteArray {
        val utf16Le = password.toByteArray(Charsets.UTF_16LE)
        return md4Digest(utf16Le)
    }

    private fun challengeResponse(challenge8: ByteArray, hash16: ByteArray): ByteArray {
        val zpadded = ByteArray(21)
        System.arraycopy(hash16, 0, zpadded, 0, 16)
        val out = ByteArray(24)
        System.arraycopy(desEncrypt7ByteKey(zpadded.copyOfRange(0, 7), challenge8), 0, out, 0, 8)
        System.arraycopy(desEncrypt7ByteKey(zpadded.copyOfRange(7, 14), challenge8), 0, out, 8, 8)
        System.arraycopy(desEncrypt7ByteKey(zpadded.copyOfRange(14, 21), challenge8), 0, out, 16, 8)
        return out
    }

    private fun desEncrypt7ByteKey(key7: ByteArray, block8: ByteArray): ByteArray {
        val key8 = ByteArray(8)
        key8[0] = (key7[0].toInt() and 0xFE).toByte()
        key8[1] = (((key7[0].toInt() shl 7) or ((key7[1].toInt() and 0xFF) ushr 1)) and 0xFE).toByte()
        key8[2] = (((key7[1].toInt() shl 6) or ((key7[2].toInt() and 0xFF) ushr 2)) and 0xFE).toByte()
        key8[3] = (((key7[2].toInt() shl 5) or ((key7[3].toInt() and 0xFF) ushr 3)) and 0xFE).toByte()
        key8[4] = (((key7[3].toInt() shl 4) or ((key7[4].toInt() and 0xFF) ushr 4)) and 0xFE).toByte()
        key8[5] = (((key7[4].toInt() shl 3) or ((key7[5].toInt() and 0xFF) ushr 5)) and 0xFE).toByte()
        key8[6] = (((key7[5].toInt() shl 2) or ((key7[6].toInt() and 0xFF) ushr 6)) and 0xFE).toByte()
        key8[7] = ((key7[6].toInt() shl 1) and 0xFE).toByte()

        // Odd parity bit per byte
        for (i in 0 until 8) {
            val bits = Integer.bitCount(key8[i].toInt() and 0xFE)
            if (bits % 2 == 0) key8[i] = (key8[i].toInt() or 0x01).toByte()
        }

        val cipher = Cipher.getInstance("DES/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key8, "DES"))
        return cipher.doFinal(block8)
    }

    /**
     * Pure-Kotlin RFC 1320 MD4 128-bit digest for MS-CHAPv2 NT-Password-Hash.
     */
    fun md4Digest(input: ByteArray): ByteArray {
        val bitLen = input.size.toLong() * 8L
        val padZeros = (56 - (input.size + 1) % 64 + 64) % 64
        val padded = ByteArray(input.size + 1 + padZeros + 8)
        System.arraycopy(input, 0, padded, 0, input.size)
        padded[input.size] = 0x80.toByte()
        for (i in 0 until 8) {
            padded[padded.size - 8 + i] = ((bitLen ushr (i * 8)) and 0xFFL).toByte()
        }

        var a = 0x67452301
        var b = 0xEFCDAB89.toInt()
        var c = 0x98BADCFE.toInt()
        var d = 0x10325476
        val x = IntArray(16)

        fun rotL(v: Int, s: Int) = (v shl s) or (v ushr (32 - s))
        fun f(x: Int, y: Int, z: Int) = (x and y) or (x.inv() and z)
        fun g(x: Int, y: Int, z: Int) = (x and y) or (x and z) or (y and z)
        fun h(x: Int, y: Int, z: Int) = x xor y xor z

        for (offset in padded.indices step 64) {
            for (i in 0 until 16) {
                val p = offset + i * 4
                x[i] = (padded[p].toInt() and 0xFF) or
                    ((padded[p + 1].toInt() and 0xFF) shl 8) or
                    ((padded[p + 2].toInt() and 0xFF) shl 16) or
                    ((padded[p + 3].toInt() and 0xFF) shl 24)
            }
            val aa = a; val bb = b; val cc = c; val dd = d

            val s1 = intArrayOf(3, 7, 11, 19)
            for (i in 0 until 16) {
                val r = s1[i % 4]
                when (i % 4) {
                    0 -> a = rotL(a + f(b, c, d) + x[i], r)
                    1 -> d = rotL(d + f(a, b, c) + x[i], r)
                    2 -> c = rotL(c + f(d, a, b) + x[i], r)
                    else -> b = rotL(b + f(c, d, a) + x[i], r)
                }
            }

            val s2 = intArrayOf(3, 5, 9, 13)
            val idx2 = intArrayOf(0, 4, 8, 12, 1, 5, 9, 13, 2, 6, 10, 14, 3, 7, 11, 15)
            for (i in 0 until 16) {
                val r = s2[i % 4]
                val k = idx2[i]
                when (i % 4) {
                    0 -> a = rotL(a + g(b, c, d) + x[k] + 0x5A827999, r)
                    1 -> d = rotL(d + g(a, b, c) + x[k] + 0x5A827999, r)
                    2 -> c = rotL(c + g(d, a, b) + x[k] + 0x5A827999, r)
                    else -> b = rotL(b + g(c, d, a) + x[k] + 0x5A827999, r)
                }
            }

            val s3 = intArrayOf(3, 9, 11, 15)
            val idx3 = intArrayOf(0, 8, 4, 12, 2, 10, 6, 14, 1, 9, 5, 13, 3, 11, 7, 15)
            for (i in 0 until 16) {
                val r = s3[i % 4]
                val k = idx3[i]
                when (i % 4) {
                    0 -> a = rotL(a + h(b, c, d) + x[k] + 0x6ED9EBA1, r)
                    1 -> d = rotL(d + h(a, b, c) + x[k] + 0x6ED9EBA1, r)
                    2 -> c = rotL(c + h(d, a, b) + x[k] + 0x6ED9EBA1, r)
                    else -> b = rotL(b + h(c, d, a) + x[k] + 0x6ED9EBA1, r)
                }
            }
            a += aa; b += bb; c += cc; d += dd
        }

        val out = ByteArray(16)
        intArrayOf(a, b, c, d).forEachIndexed { i, w ->
            out[i * 4] = (w and 0xFF).toByte()
            out[i * 4 + 1] = ((w ushr 8) and 0xFF).toByte()
            out[i * 4 + 2] = ((w ushr 16) and 0xFF).toByte()
            out[i * 4 + 3] = ((w ushr 24) and 0xFF).toByte()
        }
        return out
    }

    // --- TLV Writers & Helpers ---

    private fun writeStringAttr(out: ByteArrayOutputStream, type: Int, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8).take(253).toByteArray()
        writeBytesAttr(out, type, bytes)
    }

    private fun writeIntAttr(out: ByteArrayOutputStream, type: Int, value: Long) {
        writeBytesAttr(out, type, ByteBuffer.allocate(4).putInt(value.toInt()).array())
    }

    private fun writeIpAttr(out: ByteArrayOutputStream, type: Int, ip: String) {
        writeBytesAttr(out, type, parseIpv4Bytes(ip))
    }

    private fun writeBytesAttr(out: ByteArrayOutputStream, type: Int, bytes: ByteArray) {
        val len = bytes.size.coerceAtMost(253)
        out.write(type and 0xFF)
        out.write(2 + len)
        out.write(bytes, 0, len)
    }

    private fun writeVsaAttr(out: ByteArrayOutputStream, vendorId: Long, vendorType: Int, valueBytes: ByteArray) {
        val capped = valueBytes.take(245).toByteArray()
        val subLen = 2 + capped.size
        out.write(26)
        out.write(2 + 4 + subLen)
        out.write(ByteBuffer.allocate(4).putInt(vendorId.toInt()).array())
        out.write(vendorType and 0xFF)
        out.write(subLen)
        out.write(capped)
    }

    private fun parseIpv4Bytes(ip: String): ByteArray = try {
        val addr = InetAddress.getByName(ip)
        if (addr is Inet4Address) addr.address else byteArrayOf(127, 0, 0, 1)
    } catch (_: Exception) {
        byteArrayOf(127, 0, 0, 1)
    }

    private fun formatIpv4(bytes: ByteArray): String = if (bytes.size == 4) {
        "${bytes[0].toInt() and 0xFF}.${bytes[1].toInt() and 0xFF}.${bytes[2].toInt() and 0xFF}.${bytes[3].toInt() and 0xFF}"
    } else NetworkByteUtils.bytesToHex(bytes)

    private fun readUint32(bytes: ByteArray): Long {
        if (bytes.size < 4) return 0L
        return ((bytes[0].toLong() and 0xFF) shl 24) or
            ((bytes[1].toLong() and 0xFF) shl 16) or
            ((bytes[2].toLong() and 0xFF) shl 8) or
            (bytes[3].toLong() and 0xFF)
    }

    private fun acctStatusName(code: Int): String = when (code) {
        1 -> "Start (1)"
        2 -> "Stop (2)"
        3 -> "Interim-Update (3)"
        7 -> "Accounting-On (7)"
        8 -> "Accounting-Off (8)"
        else -> code.toString()
    }

    fun errorCauseDescription(code: Int): String = when (code) {
        201 -> "Residual Session Context Removed"
        202 -> "Invalid EAP Packet"
        401 -> "Unsupported Attribute"
        402 -> "Missing Attribute"
        403 -> "NAS Identification Mismatch"
        404 -> "Invalid Request"
        405 -> "Unsupported Service"
        406 -> "Unsupported Extension"
        407 -> "Invalid Attribute Value"
        501 -> "Administratively Prohibited"
        502 -> "Request Not Routable (Proxy)"
        503 -> "Session Context Not Found"
        504 -> "Session Context Not Removable"
        505 -> "Other Proxy Processing Error"
        506 -> "Resources Unavailable"
        507 -> "Request Initiated"
        else -> "RFC 5176 Code $code"
    }
}
