// === FILE: app/src/main/java/com/enterwebslinger/network/data/protocol/IdentityWireCodecs.kt ===
package com.enterwebslinger.network.data.protocol

import com.enterwebslinger.network.domain.model.LdapEntryRecord
import com.enterwebslinger.network.domain.model.TacacsAuthenMethod
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * Pure-Kotlin Binary Codecs for Enterprise Identity & Authorization:
 * 1) RFC 8907 TACACS+ Header + MD5 Body Obfuscation + AUTHEN/START, AUTHEN/CONTINUE, AUTHOR/REQUEST
 * 2) RFC 4511 LDAPv3 ASN.1 BER Codec (BindRequest, BindResponse, SearchRequest, SearchResultEntry, SearchResultDone)
 */
object IdentityWireCodecs {

    // =========================================================================
    // RFC 8907 TACACS+ Wire Codec
    // =========================================================================

    const val TAC_PLUS_MAJOR_VER: Byte = 0xC0.toByte()
    const val TAC_PLUS_MINOR_DEFAULT: Byte = 0x00
    const val TAC_PLUS_MINOR_ONE: Byte = 0x01
    const val TAC_PLUS_AUTHEN: Byte = 0x01
    const val TAC_PLUS_AUTHOR: Byte = 0x02
    const val TAC_PLUS_ACCT: Byte = 0x03
    const val TAC_PLUS_UNENCRYPTED_FLAG: Byte = 0x01
    const val TAC_PLUS_ENCRYPTED_FLAG: Byte = 0x00

    data class TacacsParsedReply(
        val version: Int,
        val type: Int,
        val seqNo: Int,
        val flags: Int,
        val sessionId: Int,
        val status: Int,
        val serverMessage: String,
        val avPairs: List<String>,
        val obfuscated: Boolean
    )

    fun buildTacacsPacket(
        version: Byte,
        type: Byte,
        seqNo: Byte,
        sessionId: Int,
        plainBody: ByteArray,
        sharedSecret: String
    ): ByteArray {
        val obfuscated = sharedSecret.isNotEmpty()
        val flags = if (obfuscated) TAC_PLUS_ENCRYPTED_FLAG else TAC_PLUS_UNENCRYPTED_FLAG
        val bodyBytes = if (obfuscated) {
            obfuscateTacacsBody(sessionId, sharedSecret, version, seqNo, plainBody)
        } else {
            plainBody
        }

        return ByteBuffer.allocate(12 + bodyBytes.size).apply {
            put(version)
            put(type)
            put(seqNo)
            put(flags)
            putInt(sessionId)
            putInt(bodyBytes.size)
            put(bodyBytes)
        }.array()
    }

    fun obfuscateTacacsBody(
        sessionId: Int,
        sharedSecret: String,
        version: Byte,
        seqNo: Byte,
        data: ByteArray
    ): ByteArray {
        if (sharedSecret.isEmpty() || data.isEmpty()) return data.copyOf()
        val sessBytes = ByteBuffer.allocate(4).putInt(sessionId).array()
        val keyBytes = sharedSecret.toByteArray(Charsets.UTF_8)
        val out = ByteArray(data.size)

        var prevDigest: ByteArray? = null
        var offset = 0
        while (offset < data.size) {
            val md5 = MessageDigest.getInstance("MD5").apply {
                update(sessBytes)
                update(keyBytes)
                update(version)
                update(seqNo)
                if (prevDigest != null) update(prevDigest)
            }.digest()
            prevDigest = md5
            val chunk = minOf(16, data.size - offset)
            for (i in 0 until chunk) {
                out[offset + i] = (data[offset + i].toInt() xor md5[i].toInt()).toByte()
            }
            offset += chunk
        }
        return out
    }

    fun buildTacacsAuthenStartBody(
        authenMethod: TacacsAuthenMethod,
        privLevel: Int,
        username: String,
        password: String,
        portName: String = "tty0",
        remoteAddr: String = "127.0.0.1"
    ): ByteArray {
        val userB = username.toByteArray(Charsets.UTF_8).take(255).toByteArray()
        val portB = portName.toByteArray(Charsets.UTF_8).take(255).toByteArray()
        val remB = remoteAddr.toByteArray(Charsets.UTF_8).take(255).toByteArray()
        val dataB = if (authenMethod == TacacsAuthenMethod.PAP) {
            password.toByteArray(Charsets.UTF_8).take(255).toByteArray()
        } else {
            ByteArray(0)
        }
        val out = ByteArrayOutputStream()
        out.write(0x01) // action = TAC_PLUS_AUTHEN_LOGIN
        out.write(privLevel.coerceIn(0, 15))
        out.write(authenMethod.byteCode.toInt())
        out.write(0x01) // service = TAC_PLUS_AUTHEN_SVC_LOGIN
        out.write(userB.size)
        out.write(portB.size)
        out.write(remB.size)
        out.write(dataB.size)
        out.write(userB)
        out.write(portB)
        out.write(remB)
        out.write(dataB)
        return out.toByteArray()
    }

    fun buildTacacsAuthenContinueBody(userMessage: String): ByteArray {
        val msgB = userMessage.toByteArray(Charsets.UTF_8)
        val out = ByteArrayOutputStream()
        out.write((msgB.size ushr 8) and 0xFF)
        out.write(msgB.size and 0xFF)
        out.write(0) // data len hi
        out.write(0) // data len lo
        out.write(0) // flags
        out.write(msgB)
        return out.toByteArray()
    }

    fun buildTacacsAuthorRequestBody(
        authenMethod: TacacsAuthenMethod,
        privLevel: Int,
        username: String,
        command: String,
        commandArgs: String,
        portName: String = "tty0",
        remoteAddr: String = "127.0.0.1"
    ): ByteArray {
        val userB = username.toByteArray(Charsets.UTF_8).take(255).toByteArray()
        val portB = portName.toByteArray(Charsets.UTF_8).take(255).toByteArray()
        val remB = remoteAddr.toByteArray(Charsets.UTF_8).take(255).toByteArray()

        val args = mutableListOf(
            "service=shell".toByteArray(Charsets.UTF_8),
            "cmd=${command.trim().ifBlank { "show" }}".toByteArray(Charsets.UTF_8)
        )
        commandArgs.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.forEach { token ->
            args.add("cmd-arg=$token".toByteArray(Charsets.UTF_8))
        }

        val out = ByteArrayOutputStream()
        out.write(0x06) // authen_method = TAC_PLUS_AUTHEN_METH_TACACSPLUS
        out.write(privLevel.coerceIn(0, 15))
        out.write(authenMethod.byteCode.toInt())
        out.write(0x01) // authen_service = LOGIN
        out.write(userB.size)
        out.write(portB.size)
        out.write(remB.size)
        out.write(args.size.coerceAtMost(255))
        args.take(255).forEach { out.write(it.size.coerceAtMost(255)) }
        out.write(userB)
        out.write(portB)
        out.write(remB)
        args.take(255).forEach { out.write(it, 0, it.size.coerceAtMost(255)) }
        return out.toByteArray()
    }

    fun parseTacacsReply(
        header12: ByteArray,
        rawBody: ByteArray,
        sharedSecret: String
    ): TacacsParsedReply {
        val version = header12[0].toInt() and 0xFF
        val type = header12[1].toInt() and 0xFF
        val seqNo = header12[2].toInt() and 0xFF
        val flags = header12[3].toInt() and 0xFF
        val sessionId = ByteBuffer.wrap(header12, 4, 4).int

        val encryptedOnWire = (flags and 0x01) == 0 && sharedSecret.isNotEmpty()
        val plainBody = if (encryptedOnWire) {
            obfuscateTacacsBody(sessionId, sharedSecret, header12[0], header12[2], rawBody)
        } else {
            rawBody
        }

        if (type == TAC_PLUS_AUTHEN.toInt() && plainBody.size >= 6) {
            val status = plainBody[0].toInt() and 0xFF
            val msgLen = ((plainBody[2].toInt() and 0xFF) shl 8) or (plainBody[3].toInt() and 0xFF)
            val msgStart = 6
            val msg = if (msgLen > 0 && msgStart + msgLen <= plainBody.size) {
                String(plainBody, msgStart, msgLen, Charsets.UTF_8)
            } else ""
            return TacacsParsedReply(version, type, seqNo, flags, sessionId, status, msg, emptyList(), encryptedOnWire)
        }

        if (type == TAC_PLUS_AUTHOR.toInt() && plainBody.size >= 6) {
            val status = plainBody[0].toInt() and 0xFF
            val argCnt = plainBody[1].toInt() and 0xFF
            val msgLen = ((plainBody[2].toInt() and 0xFF) shl 8) or (plainBody[3].toInt() and 0xFF)
            val dataLen = ((plainBody[4].toInt() and 0xFF) shl 8) or (plainBody[5].toInt() and 0xFF)

            val argLensStart = 6
            val argLens = (0 until argCnt).mapNotNull { i ->
                if (argLensStart + i < plainBody.size) plainBody[argLensStart + i].toInt() and 0xFF else null
            }
            var pos = argLensStart + argCnt
            val msg = if (msgLen > 0 && pos + msgLen <= plainBody.size) {
                String(plainBody, pos, msgLen, Charsets.UTF_8)
            } else ""
            pos += msgLen + dataLen
            val avPairs = mutableListOf<String>()
            for (len in argLens) {
                if (pos + len <= plainBody.size) {
                    avPairs.add(String(plainBody, pos, len, Charsets.UTF_8))
                    pos += len
                }
            }
            return TacacsParsedReply(version, type, seqNo, flags, sessionId, status, msg, avPairs, encryptedOnWire)
        }

        return TacacsParsedReply(version, type, seqNo, flags, sessionId, -1, "", emptyList(), encryptedOnWire)
    }

    fun tacacsAuthenStatusLabel(status: Int): String = when (status) {
        0x01 -> "PASS (0x01)"
        0x02 -> "FAIL (0x02)"
        0x03 -> "GETDATA (0x03)"
        0x04 -> "GETUSER (0x04)"
        0x05 -> "GETPASS (0x05)"
        0x06 -> "RESTART (0x06)"
        0x07 -> "ERROR (0x07)"
        else -> "Status 0x${status.toString(16)}"
    }

    fun tacacsAuthorStatusLabel(status: Int): String = when (status) {
        0x01 -> "PASS_ADD (0x01)"
        0x02 -> "PASS_REPL (0x02)"
        0x10 -> "FAIL (0x10)"
        0x11 -> "ERROR (0x11)"
        0x21 -> "FOLLOW (0x21)"
        else -> "Status 0x${status.toString(16)}"
    }

    // =========================================================================
    // RFC 4511 LDAPv3 ASN.1 BER Codec
    // =========================================================================

    data class LdapBerResult(
        val messageId: Int,
        val protocolOpTag: Int,
        val resultCode: Int,
        val matchedDn: String,
        val diagnosticMessage: String,
        val entry: LdapEntryRecord? = null
    )

    fun buildLdapBindRequest(messageId: Int, bindDn: String, password: String): ByteArray {
        val versionTlv = encodeBerInteger(3) // LDAPv3
        val nameTlv = encodeBerOctetString(bindDn.toByteArray(Charsets.UTF_8))
        val simpleAuthTlv = encodeBerTlv(0x80, password.toByteArray(Charsets.UTF_8)) // Context [0] primitive
        val bindBody = ByteArrayOutputStream().apply {
            write(versionTlv)
            write(nameTlv)
            write(simpleAuthTlv)
        }.toByteArray()
        val bindOp = encodeBerTlv(0x60, bindBody) // [APPLICATION 0] BindRequest
        return encodeLdapMessage(messageId, bindOp)
    }

    fun buildLdapSearchRequest(
        messageId: Int,
        baseDn: String,
        filterText: String,
        attributes: List<String>
    ): ByteArray {
        val filterTlv = encodeLdapFilter(filterText.trim().ifBlank { "(objectClass=*)" })
        val attrSeqContent = ByteArrayOutputStream().apply {
            attributes.forEach { write(encodeBerOctetString(it.toByteArray(Charsets.UTF_8))) }
        }.toByteArray()

        val searchBody = ByteArrayOutputStream().apply {
            write(encodeBerOctetString(baseDn.toByteArray(Charsets.UTF_8)))
            write(encodeBerEnumerated(2)) // wholeSubtree (2)
            write(encodeBerEnumerated(0)) // neverDerefAliases (0)
            write(encodeBerInteger(25))   // sizeLimit = 25
            write(encodeBerInteger(5))    // timeLimit = 5s
            write(encodeBerTlv(0x01, byteArrayOf(0x00))) // typesOnly = FALSE
            write(filterTlv)
            write(encodeBerTlv(0x30, attrSeqContent))
        }.toByteArray()

        val searchOp = encodeBerTlv(0x63, searchBody) // [APPLICATION 3] SearchRequest
        return encodeLdapMessage(messageId, searchOp)
    }

    fun buildLdapUnbindRequest(messageId: Int): ByteArray {
        return encodeLdapMessage(messageId, encodeBerTlv(0x42, ByteArray(0)))
    }

    fun encodeLdapFilter(raw: String): ByteArray {
        val trimmed = raw.trim().removePrefix("(").removeSuffix(")").trim()
        if (trimmed.contains('=')) {
            val idx = trimmed.indexOf('=')
            val attr = trimmed.substring(0, idx).trim()
            val value = trimmed.substring(idx + 1).trim()
            return if (value == "*") {
                // Present filter [7] primitive OCTET STRING
                encodeBerTlv(0x87, attr.toByteArray(Charsets.UTF_8))
            } else {
                // EqualityMatch [3] CONSTRUCTED SEQUENCE { attrDesc, assertionValue }
                val eqBody = ByteArrayOutputStream().apply {
                    write(encodeBerOctetString(attr.toByteArray(Charsets.UTF_8)))
                    write(encodeBerOctetString(value.toByteArray(Charsets.UTF_8)))
                }.toByteArray()
                encodeBerTlv(0xA3, eqBody)
            }
        }
        return encodeBerTlv(0x87, "objectClass".toByteArray(Charsets.UTF_8))
    }

    fun parseLdapMessage(raw: ByteArray): LdapBerResult {
        var idx = 0
        fun readLen(buf: ByteArray, pos: Int): Pair<Int, Int> {
            val first = buf[pos].toInt() and 0xFF
            if (first < 0x80) return first to (pos + 1)
            val numBytes = first and 0x7F
            var len = 0
            for (i in 1..numBytes) {
                len = (len shl 8) or (buf[pos + i].toInt() and 0xFF)
            }
            return len to (pos + 1 + numBytes)
        }

        if (raw.isEmpty() || (raw[idx].toInt() and 0xFF) != 0x30) {
            return LdapBerResult(-1, -1, -1, "", "Invalid LDAP SEQUENCE")
        }
        idx++
        val (msgLen, contentStart) = readLen(raw, idx)
        val msgEnd = (contentStart + msgLen).coerceAtMost(raw.size)
        idx = contentStart

        // MessageID INTEGER (0x02)
        var messageId = 0
        if (idx < msgEnd && (raw[idx].toInt() and 0xFF) == 0x02) {
            idx++
            val (idLen, idStart) = readLen(raw, idx)
            for (i in 0 until idLen) {
                messageId = (messageId shl 8) or (raw[idStart + i].toInt() and 0xFF)
            }
            idx = idStart + idLen
        }

        if (idx >= msgEnd) return LdapBerResult(messageId, -1, -1, "", "Empty LDAP message")
        val opTag = raw[idx].toInt() and 0xFF
        idx++
        val (opLen, opStart) = readLen(raw, idx)
        val opEnd = (opStart + opLen).coerceAtMost(msgEnd)
        idx = opStart

        return when (opTag) {
            0x61, 0x65 -> { // BindResponse or SearchResultDone
                var resCode = -1
                var matchedDn = ""
                var diag = ""
                if (idx < opEnd && (raw[idx].toInt() and 0xFF) == 0x0A) {
                    idx++
                    val (cLen, cStart) = readLen(raw, idx)
                    resCode = if (cLen >= 1) raw[cStart].toInt() and 0xFF else 0
                    idx = cStart + cLen
                }
                if (idx < opEnd && (raw[idx].toInt() and 0xFF) == 0x04) {
                    idx++
                    val (mLen, mStart) = readLen(raw, idx)
                    matchedDn = String(raw, mStart, mLen.coerceAtMost(opEnd - mStart), Charsets.UTF_8)
                    idx = mStart + mLen
                }
                if (idx < opEnd && (raw[idx].toInt() and 0xFF) == 0x04) {
                    idx++
                    val (dLen, dStart) = readLen(raw, idx)
                    diag = String(raw, dStart, dLen.coerceAtMost(opEnd - dStart), Charsets.UTF_8)
                }
                LdapBerResult(messageId, opTag, resCode, matchedDn, diag)
            }
            0x64 -> { // SearchResultEntry
                var objectDn = ""
                val attrsMap = linkedMapOf<String, List<String>>()
                if (idx < opEnd && (raw[idx].toInt() and 0xFF) == 0x04) {
                    idx++
                    val (dnLen, dnStart) = readLen(raw, idx)
                    objectDn = String(raw, dnStart, dnLen.coerceAtMost(opEnd - dnStart), Charsets.UTF_8)
                    idx = dnStart + dnLen
                }
                if (idx < opEnd && (raw[idx].toInt() and 0xFF) == 0x30) {
                    idx++
                    val (attrListLen, attrListStart) = readLen(raw, idx)
                    val attrListEnd = (attrListStart + attrListLen).coerceAtMost(opEnd)
                    var aPos = attrListStart
                    while (aPos < attrListEnd && (raw[aPos].toInt() and 0xFF) == 0x30) {
                        aPos++
                        val (paLen, paStart) = readLen(raw, aPos)
                        val paEnd = (paStart + paLen).coerceAtMost(attrListEnd)
                        var p = paStart
                        var attrName = ""
                        if (p < paEnd && (raw[p].toInt() and 0xFF) == 0x04) {
                            p++
                            val (nLen, nStart) = readLen(raw, p)
                            attrName = String(raw, nStart, nLen.coerceAtMost(paEnd - nStart), Charsets.UTF_8)
                            p = nStart + nLen
                        }
                        val values = mutableListOf<String>()
                        if (p < paEnd && (raw[p].toInt() and 0xFF) == 0x31) {
                            p++
                            val (setLen, setStart) = readLen(raw, p)
                            val setEnd = (setStart + setLen).coerceAtMost(paEnd)
                            var vp = setStart
                            while (vp < setEnd && (raw[vp].toInt() and 0xFF) == 0x04) {
                                vp++
                                val (vLen, vStart) = readLen(raw, vp)
                                values.add(String(raw, vStart, vLen.coerceAtMost(setEnd - vStart), Charsets.UTF_8))
                                vp = vStart + vLen
                            }
                        }
                        if (attrName.isNotBlank()) attrsMap[attrName] = values
                        aPos = paEnd
                    }
                }
                LdapBerResult(messageId, opTag, 0, objectDn, "", LdapEntryRecord(objectDn, attrsMap))
            }
            else -> LdapBerResult(messageId, opTag, -1, "", "LDAP OpTag 0x${opTag.toString(16)}")
        }
    }

    fun ldapResultLabel(code: Int): String = when (code) {
        0 -> "Success (0)"
        1 -> "Operations Error (1)"
        2 -> "Protocol Error (2)"
        7 -> "Auth Method Not Supported (7)"
        8 -> "Stronger Auth Required (8)"
        32 -> "No Such Object (32)"
        34 -> "Invalid DN Syntax (34)"
        49 -> "Invalid Credentials (49)"
        50 -> "Insufficient Access Rights (50)"
        else -> "LDAP Code $code"
    }

    private fun encodeLdapMessage(messageId: Int, opTlv: ByteArray): ByteArray {
        val msgBody = ByteArrayOutputStream().apply {
            write(encodeBerInteger(messageId))
            write(opTlv)
        }.toByteArray()
        return encodeBerTlv(0x30, msgBody)
    }

    private fun encodeBerInteger(value: Int): ByteArray {
        val bytes = when {
            value in 0..127 -> byteArrayOf(value.toByte())
            value in 128..32767 -> byteArrayOf((value shr 8).toByte(), value.toByte())
            else -> ByteBuffer.allocate(4).putInt(value).array()
        }
        return encodeBerTlv(0x02, bytes)
    }

    private fun encodeBerEnumerated(value: Int): ByteArray = encodeBerTlv(0x0A, byteArrayOf(value.toByte()))

    private fun encodeBerOctetString(bytes: ByteArray): ByteArray = encodeBerTlv(0x04, bytes)

    fun encodeBerTlv(tag: Int, content: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(tag and 0xFF)
        val len = content.size
        when {
            len < 128 -> out.write(len)
            len <= 0xFF -> {
                out.write(0x81)
                out.write(len)
            }
            else -> {
                out.write(0x82)
                out.write((len shr 8) and 0xFF)
                out.write(len and 0xFF)
            }
        }
        out.write(content)
        return out.toByteArray()
    }
}
