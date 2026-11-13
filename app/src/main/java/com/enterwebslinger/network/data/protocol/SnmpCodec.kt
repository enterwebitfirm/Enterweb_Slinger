// === FILE: app/src/main/java/com/enterwebslinger/network/data/protocol/SnmpCodec.kt ===
package com.enterwebslinger.network.data.protocol

import com.enterwebslinger.network.domain.model.SnmpAuthProtocol
import com.enterwebslinger.network.domain.model.SnmpPduResult
import com.enterwebslinger.network.domain.model.SnmpPrivProtocol
import com.enterwebslinger.network.domain.model.SnmpSecurityLevel
import com.enterwebslinger.network.domain.model.SnmpTargetConfig
import com.enterwebslinger.network.domain.model.SnmpTrapRecord
import com.enterwebslinger.network.domain.model.SnmpVarBind
import com.enterwebslinger.network.domain.model.SnmpVersion
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object SnmpCodec {

    const val TAG_INTEGER = 0x02
    const val TAG_OCTET_STRING = 0x04
    const val TAG_NULL = 0x05
    const val TAG_OBJECT_IDENTIFIER = 0x06
    const val TAG_SEQUENCE = 0x30

    const val TAG_IP_ADDRESS = 0x40
    const val TAG_COUNTER32 = 0x41
    const val TAG_GAUGE32 = 0x42
    const val TAG_TIMETICKS = 0x43
    const val TAG_COUNTER64 = 0x46

    const val TAG_GET_REQUEST = 0xA0
    const val TAG_GET_NEXT_REQUEST = 0xA1
    const val TAG_GET_RESPONSE = 0xA2
    const val TAG_SET_REQUEST = 0xA3
    const val TAG_TRAP_V1 = 0xA4
    const val TAG_TRAP_V2 = 0xA7
    const val TAG_REPORT = 0xA8

    private val secureRandom = SecureRandom()

    data class SnmpV3EngineParams(
        val engineId: ByteArray = byteArrayOf(),
        val engineBoots: Int = 0,
        val engineTime: Int = 0
    )

    fun buildGetRequest(
        community: String,
        requestId: Int,
        oids: List<String>,
        isGetNext: Boolean = false
    ): ByteArray = buildV1OrV2cGetRequest(
        versionWireValue = SnmpVersion.V2C.wireValue,
        community = community,
        requestId = requestId,
        oids = oids,
        isGetNext = isGetNext
    )

    fun buildV1OrV2cGetRequest(
        versionWireValue: Int,
        community: String,
        requestId: Int,
        oids: List<String>,
        isGetNext: Boolean = false
    ): ByteArray {
        val pdu = buildPdu(requestId, oids, isGetNext)
        val messagePayload = ByteArrayOutputStream()
        messagePayload.write(encodeInteger(versionWireValue))
        messagePayload.write(encodeOctetString(community))
        messagePayload.write(pdu)
        return encodeTlV(TAG_SEQUENCE, messagePayload.toByteArray())
    }

    fun buildV3DiscoveryPacket(requestId: Int, msgId: Int = requestId): ByteArray {
        val pdu = buildPdu(requestId, emptyList(), isGetNext = false)
        val scopedPduPayload = ByteArrayOutputStream().apply {
            write(encodeOctetStringBytes(byteArrayOf()))
            write(encodeOctetString(""))
            write(pdu)
        }.toByteArray()
        val scopedPdu = encodeTlV(TAG_SEQUENCE, scopedPduPayload)

        val usmParams = encodeUsmSecurityParameters(
            engineId = byteArrayOf(),
            engineBoots = 0,
            engineTime = 0,
            userName = "",
            authParams = byteArrayOf(),
            privParams = byteArrayOf()
        )
        val globalData = encodeGlobalData(msgId = msgId, msgFlags = 0x04)

        val msgPayload = ByteArrayOutputStream().apply {
            write(encodeInteger(SnmpVersion.V3.wireValue))
            write(globalData)
            write(encodeOctetStringBytes(usmParams))
            write(scopedPdu)
        }.toByteArray()

        return encodeTlV(TAG_SEQUENCE, msgPayload)
    }

    fun buildV3GetRequest(
        config: SnmpTargetConfig,
        requestId: Int,
        oids: List<String>,
        isGetNext: Boolean = false,
        engineParams: SnmpV3EngineParams = SnmpV3EngineParams(),
        msgId: Int = requestId
    ): ByteArray {
        val useAuth = config.securityLevel != SnmpSecurityLevel.NO_AUTH_NO_PRIV &&
            config.authProtocol != SnmpAuthProtocol.NONE
        val usePriv = config.securityLevel == SnmpSecurityLevel.AUTH_PRIV &&
            config.privProtocol != SnmpPrivProtocol.NONE &&
            useAuth

        var msgFlags = 0x04 // reportable
        if (useAuth) msgFlags = msgFlags or 0x01
        if (usePriv) msgFlags = msgFlags or 0x02

        val pdu = buildPdu(requestId, oids, isGetNext)
        val scopedPduPlain = encodeTlV(
            TAG_SEQUENCE,
            ByteArrayOutputStream().apply {
                write(encodeOctetStringBytes(engineParams.engineId))
                write(encodeOctetString(""))
                write(pdu)
            }.toByteArray()
        )

        var privParams = byteArrayOf()
        val msgData: ByteArray = if (usePriv) {
            val privKey = localizeKey(
                password = config.privPassword,
                engineId = engineParams.engineId,
                hashAlg = authHashAlgorithm(config.authProtocol)
            )
            val salt = ByteArray(8).also { secureRandom.nextBytes(it) }
            privParams = salt
            val encrypted = encryptScopedPdu(
                plainScopedPdu = scopedPduPlain,
                privProtocol = config.privProtocol,
                privKey = privKey,
                engineBoots = engineParams.engineBoots,
                engineTime = engineParams.engineTime,
                salt = salt
            )
            encodeOctetStringBytes(encrypted)
        } else {
            scopedPduPlain
        }

        val macLength = if (useAuth) authMacTruncateLength(config.authProtocol) else 0
        val authPlaceholder = ByteArray(macLength) { 0 }

        val usmParamsPlaceholder = encodeUsmSecurityParameters(
            engineId = engineParams.engineId,
            engineBoots = engineParams.engineBoots,
            engineTime = engineParams.engineTime,
            userName = config.securityName,
            authParams = authPlaceholder,
            privParams = privParams
        )
        val globalData = encodeGlobalData(msgId = msgId, msgFlags = msgFlags)

        val msgBody = ByteArrayOutputStream().apply {
            write(encodeInteger(SnmpVersion.V3.wireValue))
            write(globalData)
            write(encodeOctetStringBytes(usmParamsPlaceholder))
            write(msgData)
        }.toByteArray()

        val packet = encodeTlV(TAG_SEQUENCE, msgBody)

        if (useAuth && macLength > 0) {
            val authKey = localizeKey(
                password = config.authPassword,
                engineId = engineParams.engineId,
                hashAlg = authHashAlgorithm(config.authProtocol)
            )
            val mac = computeTruncatedHmac(packet, config.authProtocol, authKey, macLength)
            val placeholderOffset = findAuthPlaceholderOffset(packet, macLength)
            if (placeholderOffset >= 0) {
                System.arraycopy(mac, 0, packet, placeholderOffset, macLength)
            }
        }
        return packet
    }

    fun extractV3EngineParams(data: ByteArray): SnmpV3EngineParams? {
        return try {
            val buffer = ByteBuffer.wrap(data)
            val msgTag = readUnsignedByte(buffer)
            if (msgTag != TAG_SEQUENCE) return null
            val msgLen = readLength(buffer)
            val msgLimit = (buffer.position() + msgLen).coerceAtMost(buffer.limit())

            val verTlv = readTlv(buffer)
            if (decodeInt(verTlv.value) != SnmpVersion.V3.wireValue) return null
            if (buffer.position() >= msgLimit) return null

            readTlv(buffer) // globalData
            val secParamsOctets = readTlv(buffer)
            val secBuf = ByteBuffer.wrap(secParamsOctets.value)
            val secTag = readUnsignedByte(secBuf)
            if (secTag != TAG_SEQUENCE) return null
            readLength(secBuf)

            val engineIdTlv = readTlv(secBuf)
            val bootsTlv = readTlv(secBuf)
            val timeTlv = readTlv(secBuf)

            SnmpV3EngineParams(
                engineId = engineIdTlv.value,
                engineBoots = decodeInt(bootsTlv.value),
                engineTime = decodeInt(timeTlv.value)
            )
        } catch (_: Exception) {
            null
        }
    }

    fun parseSnmpResponse(
        data: ByteArray,
        responseTimeMs: Long = 0L,
        v3Config: SnmpTargetConfig? = null
    ): SnmpPduResult {
        val buffer = ByteBuffer.wrap(data)
        val msgTag = readUnsignedByte(buffer)
        if (msgTag != TAG_SEQUENCE) {
            throw IllegalArgumentException("Invalid SNMP message: expected SEQUENCE (0x30), got 0x${msgTag.toString(16)}")
        }
        val msgLen = readLength(buffer)
        val msgLimit = buffer.position() + msgLen
        if (msgLimit > buffer.limit()) {
            throw IllegalArgumentException("Truncated SNMP message length ($msgLen bytes)")
        }

        val verTlv = readTlv(buffer)
        val versionInt = decodeInt(verTlv.value)

        val pduBuffer: ByteBuffer
        if (versionInt == SnmpVersion.V3.wireValue) {
            val globalTlv = readTlv(buffer)
            val globalBuf = ByteBuffer.wrap(globalTlv.value)
            readTlv(globalBuf) // msgId
            readTlv(globalBuf) // maxSize
            val flagsTlv = readTlv(globalBuf)
            val flags = flagsTlv.value.firstOrNull()?.toInt()?.and(0xFF) ?: 0

            val secParamsOctets = readTlv(buffer)
            val secBuf = ByteBuffer.wrap(secParamsOctets.value)
            if (readUnsignedByte(secBuf) != TAG_SEQUENCE) {
                throw IllegalArgumentException("Invalid SNMPv3 security parameters")
            }
            readLength(secBuf)
            val engineIdTlv = readTlv(secBuf)
            val bootsTlv = readTlv(secBuf)
            val timeTlv = readTlv(secBuf)
            readTlv(secBuf) // userName
            val authParamsTlv = readTlv(secBuf)
            val privParamsTlv = readTlv(secBuf)

            val engineBoots = decodeInt(bootsTlv.value)
            val engineTime = decodeInt(timeTlv.value)

            val dataTlv = readTlv(buffer)
            val scopedBytes = if ((flags and 0x02) != 0) {
                if (v3Config == null || v3Config.privProtocol == SnmpPrivProtocol.NONE) {
                    throw IllegalArgumentException("Encrypted SNMPv3 response received without privacy credentials")
                }
                val privKey = localizeKey(
                    password = v3Config.privPassword,
                    engineId = engineIdTlv.value,
                    hashAlg = authHashAlgorithm(v3Config.authProtocol)
                )
                decryptScopedPdu(
                    cipherBytes = dataTlv.value,
                    privProtocol = v3Config.privProtocol,
                    privKey = privKey,
                    engineBoots = engineBoots,
                    engineTime = engineTime,
                    salt = privParamsTlv.value
                )
            } else {
                dataTlv.value
            }

            if ((flags and 0x01) != 0 && v3Config != null && v3Config.authProtocol != SnmpAuthProtocol.NONE && authParamsTlv.value.isNotEmpty()) {
                // Verify HMAC if authParams was supplied and non-empty
                val authLen = authParamsTlv.value.size
                val zeroed = data.copyOf()
                val offset = findBytesOffset(zeroed, authParamsTlv.value)
                if (offset >= 0) {
                    for (i in 0 until authLen) zeroed[offset + i] = 0
                    val authKey = localizeKey(
                        password = v3Config.authPassword,
                        engineId = engineIdTlv.value,
                        hashAlg = authHashAlgorithm(v3Config.authProtocol)
                    )
                    val expectedMac = computeTruncatedHmac(zeroed, v3Config.authProtocol, authKey, authLen)
                    if (!expectedMac.contentEquals(authParamsTlv.value)) {
                        throw IllegalArgumentException("SNMPv3 USM HMAC verification failed")
                    }
                }
            }

            val scopedWrap = if ((flags and 0x02) != 0) {
                scopedBytes
            } else {
                encodeTlV(dataTlv.tag, scopedBytes)
            }
            val sBuf = ByteBuffer.wrap(scopedWrap)
            val sTag = readUnsignedByte(sBuf)
            if (sTag != TAG_SEQUENCE) {
                throw IllegalArgumentException("Invalid SNMPv3 ScopedPDU")
            }
            readLength(sBuf)
            readTlv(sBuf) // contextEngineId
            readTlv(sBuf) // contextName
            pduBuffer = sBuf
        } else {
            readTlv(buffer) // community
            pduBuffer = buffer
        }

        val pduTag = readUnsignedByte(pduBuffer)
        val pduLength = readLength(pduBuffer)
        val pduLimit = (pduBuffer.position() + pduLength).coerceAtMost(pduBuffer.limit())

        val reqIdTlv = readTlv(pduBuffer)
        val requestId = decodeInt(reqIdTlv.value)

        val errStatusTlv = readTlv(pduBuffer)
        val errorStatus = decodeInt(errStatusTlv.value)

        val errIndexTlv = readTlv(pduBuffer)
        val errorIndex = decodeInt(errIndexTlv.value)

        val varBinds = parseVarBindSequence(pduBuffer, pduLimit)
        if (pduTag == TAG_REPORT && varBinds.isNotEmpty()) {
            val reportOid = varBinds.first().oid
            if (reportOid.startsWith("1.3.6.1.6.3.15.1.1")) {
                val usmMeaning = when {
                    reportOid.startsWith("1.3.6.1.6.3.15.1.1.1") -> "usmStatsUnsupportedSecLevels"
                    reportOid.startsWith("1.3.6.1.6.3.15.1.1.2") -> "usmStatsNotInTimeWindows"
                    reportOid.startsWith("1.3.6.1.6.3.15.1.1.3") -> "usmStatsUnknownUserNames"
                    reportOid.startsWith("1.3.6.1.6.3.15.1.1.4") -> "usmStatsUnknownEngineIDs"
                    reportOid.startsWith("1.3.6.1.6.3.15.1.1.5") -> "usmStatsWrongDigests"
                    reportOid.startsWith("1.3.6.1.6.3.15.1.1.6") -> "usmStatsDecryptionErrors"
                    else -> reportOid
                }
                throw IllegalStateException("SNMPv3 Report: $usmMeaning (${varBinds.first().value})")
            }
        }

        return SnmpPduResult(
            requestId = requestId,
            errorStatus = errorStatus,
            errorIndex = errorIndex,
            varBinds = varBinds,
            responseTimeMs = responseTimeMs,
            rawHex = data.take(32).joinToString(" ") { "%02X".format(it) }
        )
    }

    fun parseTrap(data: ByteArray, sourceIp: String, sourcePort: Int): SnmpTrapRecord {
        try {
            val buffer = ByteBuffer.wrap(data)
            val msgTag = readUnsignedByte(buffer)
            if (msgTag != TAG_SEQUENCE) throw IllegalArgumentException("Not a valid ASN.1 sequence")
            val msgLen = readLength(buffer)
            if (buffer.remaining() < msgLen) throw IllegalArgumentException("Truncated trap message")

            val verTlv = readTlv(buffer)
            val versionInt = decodeInt(verTlv.value)
            val versionStr = when (versionInt) {
                0 -> "SNMPv1"
                1 -> "SNMPv2c"
                3 -> "SNMPv3"
                else -> "SNMPv$versionInt"
            }

            val pduBuffer: ByteBuffer
            if (versionInt == 3) {
                val globalTlv = readTlv(buffer)
                val gBuf = ByteBuffer.wrap(globalTlv.value)
                readTlv(gBuf)
                readTlv(gBuf)
                val flagsTlv = readTlv(gBuf)
                val flags = flagsTlv.value.firstOrNull()?.toInt()?.and(0xFF) ?: 0
                readTlv(buffer) // msgSecurityParameters
                if ((flags and 0x02) != 0) {
                    return SnmpTrapRecord(
                        sourceIp = sourceIp,
                        sourcePort = sourcePort,
                        version = "SNMPv3 (Encrypted)",
                        trapOid = "Encrypted authPriv trap",
                        rawPayloadHex = data.take(32).joinToString(" ") { "%02X".format(it) }
                    )
                }
                val scopedTag = readUnsignedByte(buffer)
                if (scopedTag != TAG_SEQUENCE) throw IllegalArgumentException("Invalid v3 ScopedPDU")
                readLength(buffer)
                readTlv(buffer) // contextEngineId
                readTlv(buffer) // contextName
                pduBuffer = buffer
            } else {
                readTlv(buffer) // community
                pduBuffer = buffer
            }

            val pduTag = readUnsignedByte(pduBuffer)
            val pduLen = readLength(pduBuffer)
            val pduLimit = (pduBuffer.position() + pduLen).coerceAtMost(pduBuffer.limit())

            if (pduTag == TAG_TRAP_V1) {
                val enterpriseOidTlv = readTlv(pduBuffer)
                val enterpriseOid = decodeOid(enterpriseOidTlv.value)
                readTlv(pduBuffer) // agent-addr
                val genericTrapTlv = readTlv(pduBuffer)
                val genericTrap = decodeInt(genericTrapTlv.value)
                val specificTrapTlv = readTlv(pduBuffer)
                val specificTrap = decodeInt(specificTrapTlv.value)
                val timeTicksTlv = readTlv(pduBuffer)
                val timeTicks = decodeUnsignedInt(timeTicksTlv.value)

                val varBinds = parseVarBindSequence(pduBuffer, pduLimit)

                return SnmpTrapRecord(
                    sourceIp = sourceIp,
                    sourcePort = sourcePort,
                    version = versionStr,
                    trapOid = enterpriseOid,
                    genericTrap = genericTrap,
                    specificTrap = specificTrap,
                    uptimeString = formatTimeTicks(timeTicks),
                    varBinds = varBinds,
                    rawPayloadHex = data.take(24).joinToString(" ") { "%02X".format(it) }
                )
            } else {
                readTlv(pduBuffer) // request-id
                readTlv(pduBuffer) // error-status
                readTlv(pduBuffer) // error-index
                val varBinds = parseVarBindSequence(pduBuffer, pduLimit)

                val trapOid = varBinds.firstOrNull { it.oid.contains("1.3.6.1.6.3.1.1.4.1.0") }?.value
                    ?: "1.3.6.1.6.3.1.1.5.1"
                val uptimeStr = varBinds.firstOrNull { it.oid.contains("1.3.6.1.2.1.1.3.0") }?.value ?: ""

                return SnmpTrapRecord(
                    sourceIp = sourceIp,
                    sourcePort = sourcePort,
                    version = versionStr,
                    trapOid = trapOid,
                    uptimeString = uptimeStr,
                    varBinds = varBinds,
                    rawPayloadHex = data.take(24).joinToString(" ") { "%02X".format(it) }
                )
            }
        } catch (e: Exception) {
            return SnmpTrapRecord(
                sourceIp = sourceIp,
                sourcePort = sourcePort,
                version = "Raw Trap",
                trapOid = "Unknown (${e.message})",
                rawPayloadHex = data.take(32).joinToString(" ") { "%02X".format(it) }
            )
        }
    }

    private fun buildPdu(requestId: Int, oids: List<String>, isGetNext: Boolean): ByteArray {
        val pduTag = if (isGetNext) TAG_GET_NEXT_REQUEST else TAG_GET_REQUEST
        val varBindsStream = ByteArrayOutputStream()
        for (oid in oids) {
            val vbStream = ByteArrayOutputStream()
            vbStream.write(encodeOid(oid))
            vbStream.write(byteArrayOf(TAG_NULL.toByte(), 0x00))
            varBindsStream.write(encodeTlV(TAG_SEQUENCE, vbStream.toByteArray()))
        }
        val varBindsSequence = encodeTlV(TAG_SEQUENCE, varBindsStream.toByteArray())
        val pduPayload = ByteArrayOutputStream().apply {
            write(encodeInteger(requestId))
            write(encodeInteger(0))
            write(encodeInteger(0))
            write(varBindsSequence)
        }.toByteArray()
        return encodeTlV(pduTag, pduPayload)
    }

    private fun encodeGlobalData(msgId: Int, msgFlags: Int): ByteArray {
        val payload = ByteArrayOutputStream().apply {
            write(encodeInteger(msgId))
            write(encodeInteger(65507))
            write(encodeOctetStringBytes(byteArrayOf(msgFlags.toByte())))
            write(encodeInteger(3)) // USM
        }.toByteArray()
        return encodeTlV(TAG_SEQUENCE, payload)
    }

    private fun encodeUsmSecurityParameters(
        engineId: ByteArray,
        engineBoots: Int,
        engineTime: Int,
        userName: String,
        authParams: ByteArray,
        privParams: ByteArray
    ): ByteArray {
        val payload = ByteArrayOutputStream().apply {
            write(encodeOctetStringBytes(engineId))
            write(encodeInteger(engineBoots))
            write(encodeInteger(engineTime))
            write(encodeOctetString(userName))
            write(encodeOctetStringBytes(authParams))
            write(encodeOctetStringBytes(privParams))
        }.toByteArray()
        return encodeTlV(TAG_SEQUENCE, payload)
    }

    fun localizeKey(password: String, engineId: ByteArray, hashAlg: String): ByteArray {
        if (password.isEmpty()) return ByteArray(if (hashAlg == "SHA-256") 32 else if (hashAlg == "SHA-1") 20 else 16)
        val passBytes = password.toByteArray(Charsets.UTF_8)
        val md = MessageDigest.getInstance(hashAlg)
        val buf = ByteArray(64)
        var count = 0
        var passIndex = 0
        while (count < 1_048_576) {
            for (i in 0 until 64) {
                buf[i] = passBytes[passIndex % passBytes.size]
                passIndex++
            }
            md.update(buf, 0, 64)
            count += 64
        }
        val ku = md.digest()
        if (engineId.isEmpty()) return ku
        val localizedMd = MessageDigest.getInstance(hashAlg)
        localizedMd.update(ku)
        localizedMd.update(engineId)
        localizedMd.update(ku)
        return localizedMd.digest()
    }

    private fun authHashAlgorithm(protocol: SnmpAuthProtocol): String = when (protocol) {
        SnmpAuthProtocol.MD5 -> "MD5"
        SnmpAuthProtocol.SHA1 -> "SHA-1"
        SnmpAuthProtocol.SHA256 -> "SHA-256"
        SnmpAuthProtocol.NONE -> "SHA-1"
    }

    private fun authMacTruncateLength(protocol: SnmpAuthProtocol): Int = when (protocol) {
        SnmpAuthProtocol.MD5, SnmpAuthProtocol.SHA1 -> 12
        SnmpAuthProtocol.SHA256 -> 24
        SnmpAuthProtocol.NONE -> 0
    }

    private fun computeTruncatedHmac(
        message: ByteArray,
        protocol: SnmpAuthProtocol,
        localizedKey: ByteArray,
        truncateLen: Int
    ): ByteArray {
        val macAlg = when (protocol) {
            SnmpAuthProtocol.MD5 -> "HmacMD5"
            SnmpAuthProtocol.SHA1 -> "HmacSHA1"
            SnmpAuthProtocol.SHA256 -> "HmacSHA256"
            SnmpAuthProtocol.NONE -> return byteArrayOf()
        }
        val mac = Mac.getInstance(macAlg)
        mac.init(SecretKeySpec(localizedKey, macAlg))
        return mac.doFinal(message).copyOf(truncateLen)
    }

    private fun encryptScopedPdu(
        plainScopedPdu: ByteArray,
        privProtocol: SnmpPrivProtocol,
        privKey: ByteArray,
        engineBoots: Int,
        engineTime: Int,
        salt: ByteArray
    ): ByteArray {
        return when (privProtocol) {
            SnmpPrivProtocol.DES -> {
                val key = privKey.copyOf(8)
                val preIv = privKey.copyOfRange(8, 16)
                val iv = ByteArray(8) { idx -> (preIv[idx].toInt() xor salt[idx].toInt()).toByte() }
                val paddedLen = ((plainScopedPdu.size + 7) / 8) * 8
                val padded = plainScopedPdu.copyOf(paddedLen)
                val cipher = Cipher.getInstance("DES/CBC/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "DES"), IvParameterSpec(iv))
                cipher.doFinal(padded)
            }
            SnmpPrivProtocol.AES128, SnmpPrivProtocol.AES256 -> {
                val keyLen = if (privProtocol == SnmpPrivProtocol.AES256) 32 else 16
                val key = ensureKeyLength(privKey, keyLen)
                val iv = buildAesIv(engineBoots, engineTime, salt)
                val cipher = Cipher.getInstance("AES/CFB/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
                cipher.doFinal(plainScopedPdu)
            }
            SnmpPrivProtocol.NONE -> plainScopedPdu
        }
    }

    private fun decryptScopedPdu(
        cipherBytes: ByteArray,
        privProtocol: SnmpPrivProtocol,
        privKey: ByteArray,
        engineBoots: Int,
        engineTime: Int,
        salt: ByteArray
    ): ByteArray {
        return when (privProtocol) {
            SnmpPrivProtocol.DES -> {
                if (salt.size < 8) throw IllegalArgumentException("Invalid DES salt length")
                val key = privKey.copyOf(8)
                val preIv = privKey.copyOfRange(8, 16)
                val iv = ByteArray(8) { idx -> (preIv[idx].toInt() xor salt[idx].toInt()).toByte() }
                val cipher = Cipher.getInstance("DES/CBC/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "DES"), IvParameterSpec(iv))
                cipher.doFinal(cipherBytes)
            }
            SnmpPrivProtocol.AES128, SnmpPrivProtocol.AES256 -> {
                if (salt.size < 8) throw IllegalArgumentException("Invalid AES salt length")
                val keyLen = if (privProtocol == SnmpPrivProtocol.AES256) 32 else 16
                val key = ensureKeyLength(privKey, keyLen)
                val iv = buildAesIv(engineBoots, engineTime, salt)
                val cipher = Cipher.getInstance("AES/CFB/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
                cipher.doFinal(cipherBytes)
            }
            SnmpPrivProtocol.NONE -> cipherBytes
        }
    }

    private fun ensureKeyLength(privKey: ByteArray, targetLen: Int): ByteArray {
        if (privKey.size >= targetLen) return privKey.copyOf(targetLen)
        val expanded = ByteArrayOutputStream()
        var current = privKey
        expanded.write(current)
        val md = MessageDigest.getInstance("SHA-256")
        while (expanded.size() < targetLen) {
            current = md.digest(current)
            expanded.write(current)
        }
        return expanded.toByteArray().copyOf(targetLen)
    }

    private fun buildAesIv(engineBoots: Int, engineTime: Int, salt: ByteArray): ByteArray {
        return ByteBuffer.allocate(16).apply {
            putInt(engineBoots)
            putInt(engineTime)
            put(salt.copyOf(8))
        }.array()
    }

    private fun findAuthPlaceholderOffset(packet: ByteArray, macLength: Int): Int {
        if (macLength <= 0) return -1
        for (i in 0..(packet.size - macLength - 2)) {
            if ((packet[i].toInt() and 0xFF) == TAG_OCTET_STRING &&
                (packet[i + 1].toInt() and 0xFF) == macLength
            ) {
                var allZero = true
                for (j in 0 until macLength) {
                    if (packet[i + 2 + j].toInt() != 0) {
                        allZero = false
                        break
                    }
                }
                if (allZero) return i + 2
            }
        }
        return -1
    }

    private fun findBytesOffset(haystack: ByteArray, needle: ByteArray): Int {
        if (needle.isEmpty() || needle.size > haystack.size) return -1
        outer@ for (i in 0..(haystack.size - needle.size)) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }

    private fun parseVarBindSequence(buffer: ByteBuffer, limit: Int): List<SnmpVarBind> {
        val list = mutableListOf<SnmpVarBind>()
        val safeLimit = limit.coerceAtMost(buffer.limit())
        if (buffer.position() >= safeLimit || !buffer.hasRemaining()) return list

        val seqTag = readUnsignedByte(buffer)
        if (seqTag != TAG_SEQUENCE) return list
        val seqLen = readLength(buffer)
        val seqLimit = (buffer.position() + seqLen).coerceAtMost(safeLimit)

        while (buffer.position() < seqLimit && buffer.hasRemaining()) {
            val vbTag = readUnsignedByte(buffer)
            if (vbTag != TAG_SEQUENCE) break
            val vbLen = readLength(buffer)
            val vbLimit = (buffer.position() + vbLen).coerceAtMost(seqLimit)

            val oidTlv = readTlv(buffer)
            val oidStr = decodeOid(oidTlv.value)

            val valTlv = readTlv(buffer)
            val (syntax, valueStr) = decodeSnmpValue(valTlv.tag, valTlv.value)

            list.add(SnmpVarBind(oid = oidStr, syntax = syntax, value = valueStr))
            buffer.position(vbLimit)
        }
        return list
    }

    private fun encodeTlV(tag: Int, value: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(tag)
        encodeLength(out, value.size)
        out.write(value)
        return out.toByteArray()
    }

    private fun encodeLength(out: ByteArrayOutputStream, length: Int) {
        if (length < 128) {
            out.write(length)
        } else {
            val lenBytes = mutableListOf<Int>()
            var temp = length
            while (temp > 0) {
                lenBytes.add(0, temp and 0xFF)
                temp = temp ushr 8
            }
            out.write(0x80 or lenBytes.size)
            lenBytes.forEach { out.write(it) }
        }
    }

    private fun encodeInteger(value: Int): ByteArray {
        var temp = value
        val bytes = mutableListOf<Byte>()
        do {
            bytes.add(0, (temp and 0xFF).toByte())
            temp = temp shr 8
        } while ((temp != 0 && temp != -1) ||
            (temp == 0 && (bytes[0].toInt() and 0x80) != 0) ||
            (temp == -1 && (bytes[0].toInt() and 0x80) == 0)
        )
        return encodeTlV(TAG_INTEGER, bytes.toByteArray())
    }

    private fun encodeOctetString(str: String): ByteArray =
        encodeOctetStringBytes(str.toByteArray(Charsets.UTF_8))

    private fun encodeOctetStringBytes(bytes: ByteArray): ByteArray =
        encodeTlV(TAG_OCTET_STRING, bytes)

    private fun encodeOid(oidStr: String): ByteArray {
        val parts = oidStr.split(".").filter { it.isNotBlank() }.map { it.toLong() }
        if (parts.size < 2) throw IllegalArgumentException("OID must have at least 2 nodes")

        val out = ByteArrayOutputStream()
        val firstByte = (parts[0] * 40 + parts[1]).toInt()
        out.write(firstByte)

        for (i in 2 until parts.size) {
            var subId = parts[i]
            val subBytes = mutableListOf<Int>()
            subBytes.add((subId and 0x7F).toInt())
            subId = subId ushr 7
            while (subId > 0) {
                subBytes.add(0, ((subId and 0x7F) or 0x80).toInt())
                subId = subId ushr 7
            }
            subBytes.forEach { out.write(it) }
        }
        return encodeTlV(TAG_OBJECT_IDENTIFIER, out.toByteArray())
    }

    private data class Tlv(val tag: Int, val length: Int, val value: ByteArray)

    private fun readUnsignedByte(buffer: ByteBuffer): Int {
        if (!buffer.hasRemaining()) throw IllegalArgumentException("Unexpected end of ASN.1 BER stream")
        return buffer.get().toInt() and 0xFF
    }

    private fun readTlv(buffer: ByteBuffer): Tlv {
        val tag = readUnsignedByte(buffer)
        val length = readLength(buffer)
        if (length < 0 || buffer.remaining() < length) {
            throw IllegalArgumentException("ASN.1 TLV length $length exceeds remaining ${buffer.remaining()} bytes")
        }
        val value = ByteArray(length)
        buffer.get(value)
        return Tlv(tag, length, value)
    }

    private fun readLength(buffer: ByteBuffer): Int {
        val first = readUnsignedByte(buffer)
        if (first < 128) return first
        val numOctets = first and 0x7F
        if (numOctets !in 1..4) {
            throw IllegalArgumentException("Unsupported ASN.1 BER length octets: $numOctets")
        }
        if (buffer.remaining() < numOctets) {
            throw IllegalArgumentException("Truncated ASN.1 BER length")
        }
        var length = 0
        for (i in 0 until numOctets) {
            length = (length shl 8) or (buffer.get().toInt() and 0xFF)
        }
        if (length < 0) throw IllegalArgumentException("Negative ASN.1 BER length")
        return length
    }

    private fun decodeInt(bytes: ByteArray): Int {
        if (bytes.isEmpty()) return 0
        var result = if ((bytes[0].toInt() and 0x80) != 0) -1 else 0
        for (b in bytes) {
            result = (result shl 8) or (b.toInt() and 0xFF)
        }
        return result
    }

    private fun decodeUnsignedInt(bytes: ByteArray): Long {
        var result = 0L
        for (b in bytes) {
            result = (result shl 8) or (b.toLong() and 0xFFL)
        }
        return result
    }

    private fun decodeOid(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val oids = mutableListOf<Long>()
        val first = bytes[0].toInt() and 0xFF
        oids.add((first / 40).toLong())
        oids.add((first % 40).toLong())

        var current = 0L
        for (i in 1 until bytes.size) {
            val b = bytes[i].toInt() and 0xFF
            current = (current shl 7) or (b and 0x7F).toLong()
            if ((b and 0x80) == 0) {
                oids.add(current)
                current = 0L
            }
        }
        return oids.joinToString(".")
    }

    private fun decodeSnmpValue(tag: Int, bytes: ByteArray): Pair<String, String> {
        return when (tag) {
            TAG_INTEGER -> "INTEGER" to decodeInt(bytes).toString()
            TAG_OCTET_STRING -> {
                val isPrintable = bytes.all { it in 32..126 || it == 10.toByte() || it == 13.toByte() }
                if (isPrintable) {
                    "STRING" to String(bytes, Charsets.UTF_8)
                } else {
                    "HEX" to bytes.joinToString(":") { "%02X".format(it) }
                }
            }
            TAG_OBJECT_IDENTIFIER -> "OID" to decodeOid(bytes)
            TAG_IP_ADDRESS -> "IpAddress" to formatIp(bytes)
            TAG_COUNTER32 -> "Counter32" to decodeUnsignedInt(bytes).toString()
            TAG_GAUGE32 -> "Gauge32" to decodeUnsignedInt(bytes).toString()
            TAG_TIMETICKS -> "TimeTicks" to formatTimeTicks(decodeUnsignedInt(bytes))
            TAG_COUNTER64 -> "Counter64" to decodeUnsignedInt(bytes).toString()
            TAG_NULL -> "NULL" to ""
            0x80 -> "noSuchObject" to "noSuchObject"
            0x81 -> "noSuchInstance" to "noSuchInstance"
            0x82 -> "endOfMibView" to "endOfMibView"
            else -> "Tag(0x${tag.toString(16)})" to bytes.joinToString(" ") { "%02X".format(it) }
        }
    }

    private fun formatIp(bytes: ByteArray): String =
        bytes.take(4).joinToString(".") { (it.toInt() and 0xFF).toString() }

    private fun formatTimeTicks(hundredths: Long): String {
        val totalSec = hundredths / 100
        val days = totalSec / 86400
        val hours = (totalSec % 86400) / 3600
        val mins = (totalSec % 3600) / 60
        val secs = totalSec % 60
        val remainder = hundredths % 100
        return if (days > 0) {
            "${days}d ${hours}h ${mins}m ${secs}.${remainder}s"
        } else {
            "${hours}h ${mins}m ${secs}.${remainder}s"
        }
    }
}
