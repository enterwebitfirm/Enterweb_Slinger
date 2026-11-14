// === FILE: app/src/main/java/com/enterwebslinger/network/data/repository/AaaDiagnosticsRepository.kt ===
package com.enterwebslinger.network.data.repository

import com.enterwebslinger.network.core.util.NetworkByteUtils
import com.enterwebslinger.network.domain.model.RadiusAttributeEntry
import com.enterwebslinger.network.domain.model.RadiusResponseCode
import com.enterwebslinger.network.domain.model.RadiusTestResult
import com.enterwebslinger.network.domain.model.RadiusVsaAttribute
import com.enterwebslinger.network.domain.model.TacacsProbeResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Pure-Kotlin Enterprise AAA Protocol Tester:
 * 1) RFC 2865 RADIUS Access-Request builder & Access-Accept / Reject / Challenge + VSA (Type 26) parser
 * 2) TCP Port 49 TACACS+ v12.0 (0xC0) Authentication Header Probe
 */
class AaaDiagnosticsRepository {

    private val random = SecureRandom()

    suspend fun testRadiusAuth(
        serverIp: String,
        port: Int = 1812,
        sharedSecret: String,
        username: String,
        password: String,
        nasIp: String = "127.0.0.1"
    ): RadiusTestResult = withContext(Dispatchers.IO) {
        val cleanHost = serverIp.trim()
        if (cleanHost.isBlank() || sharedSecret.isBlank() || username.isBlank()) {
            return@withContext RadiusTestResult(
                serverIp = cleanHost,
                port = port,
                responseCode = RadiusResponseCode.TIMEOUT_OR_ERROR,
                errorMessage = "Server IP, Shared Secret, and Username are required."
            )
        }

        val authenticator = ByteArray(16).apply { random.nextBytes(this) }
        val packetId = random.nextInt(255) + 1
        val requestBytes = buildRadiusAccessRequest(
            identifier = packetId,
            requestAuthenticator = authenticator,
            sharedSecret = sharedSecret,
            username = username.trim(),
            password = password,
            nasIp = nasIp.trim().ifBlank { "127.0.0.1" }
        )

        var socket: DatagramSocket? = null
        val t0 = System.currentTimeMillis()
        try {
            val addr = InetAddress.getByName(cleanHost)
            socket = DatagramSocket().apply { soTimeout = 4500 }
            socket.send(DatagramPacket(requestBytes, requestBytes.size, addr, port))

            val rxBuf = ByteArray(4096)
            val rxPkt = DatagramPacket(rxBuf, rxBuf.size)
            socket.receive(rxPkt)
            val rtt = (System.currentTimeMillis() - t0).coerceAtLeast(1L)

            parseRadiusResponse(
                serverIp = cleanHost,
                port = port,
                raw = rxBuf,
                length = rxPkt.length,
                rttMs = rtt
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            RadiusTestResult(
                serverIp = cleanHost,
                port = port,
                responseCode = RadiusResponseCode.TIMEOUT_OR_ERROR,
                rttMs = (System.currentTimeMillis() - t0).coerceAtLeast(1L),
                isSuccess = false,
                errorMessage = e.message ?: "No RADIUS response received"
            )
        } finally {
            NetworkByteUtils.safeClose(socket)
        }
    }

    fun buildRadiusAccessRequest(
        identifier: Int,
        requestAuthenticator: ByteArray,
        sharedSecret: String,
        username: String,
        password: String,
        nasIp: String = "127.0.0.1"
    ): ByteArray {
        require(requestAuthenticator.size == 16) { "Request Authenticator must be 16 bytes" }

        val attrs = ByteArrayOutputStream()

        // Attribute 1: User-Name
        val userBytes = username.toByteArray(Charsets.UTF_8).take(253).toByteArray()
        attrs.write(1)
        attrs.write(2 + userBytes.size)
        attrs.write(userBytes)

        // Attribute 2: User-Password (RFC 2865 Section 5.2 MD5 XOR)
        val encryptedPwd = encryptRadiusUserPassword(password, sharedSecret, requestAuthenticator)
        attrs.write(2)
        attrs.write(2 + encryptedPwd.size)
        attrs.write(encryptedPwd)

        // Attribute 4: NAS-IP-Address
        val nasBytes = runCatching { InetAddress.getByName(nasIp).address }.getOrNull()
        if (nasBytes != null && nasBytes.size == 4) {
            attrs.write(4)
            attrs.write(6)
            attrs.write(nasBytes)
        }

        // Attribute 5: NAS-Port (0)
        attrs.write(5)
        attrs.write(6)
        attrs.write(ByteBuffer.allocate(4).putInt(0).array())

        // Attribute 6: Service-Type = Login (1)
        attrs.write(6)
        attrs.write(6)
        attrs.write(ByteBuffer.allocate(4).putInt(1).array())

        val attrBytes = attrs.toByteArray()
        val totalLen = 20 + attrBytes.size

        return ByteBuffer.allocate(totalLen).apply {
            put(1.toByte()) // Code = 1 (Access-Request)
            put((identifier and 0xFF).toByte())
            putShort(totalLen.toShort())
            put(requestAuthenticator)
            put(attrBytes)
        }.array()
    }

    /**
     * RFC 2865 Section 5.2 Password Hiding:
     * b1 = MD5(secret + RA), c1 = p1 XOR b1
     * bi = MD5(secret + c(i-1)), ci = pi XOR bi
     */
    fun encryptRadiusUserPassword(
        password: String,
        sharedSecret: String,
        requestAuthenticator: ByteArray
    ): ByteArray {
        val rawPwd = password.toByteArray(Charsets.UTF_8)
        val paddedLen = if (rawPwd.isEmpty()) 16 else ((rawPwd.size + 15) / 16) * 16
        val padded = ByteArray(paddedLen)
        System.arraycopy(rawPwd, 0, padded, 0, rawPwd.size)

        val secretBytes = sharedSecret.toByteArray(Charsets.UTF_8)
        val out = ByteArray(paddedLen)
        val md5 = MessageDigest.getInstance("MD5")

        var prevVector = requestAuthenticator
        var offset = 0
        while (offset < paddedLen) {
            md5.reset()
            md5.update(secretBytes)
            md5.update(prevVector)
            val digest = md5.digest()

            val cipherChunk = ByteArray(16)
            for (i in 0 until 16) {
                cipherChunk[i] = (padded[offset + i].toInt() xor digest[i].toInt()).toByte()
            }
            System.arraycopy(cipherChunk, 0, out, offset, 16)
            prevVector = cipherChunk
            offset += 16
        }
        return out
    }

    fun parseRadiusResponse(
        serverIp: String,
        port: Int,
        raw: ByteArray,
        length: Int,
        rttMs: Long = 0L
    ): RadiusTestResult {
        if (length < 20) {
            return RadiusTestResult(
                serverIp = serverIp,
                port = port,
                responseCode = RadiusResponseCode.TIMEOUT_OR_ERROR,
                rttMs = rttMs,
                errorMessage = "Malformed RADIUS packet (<20 bytes)"
            )
        }
        val codeByte = raw[0].toInt() and 0xFF
        val declaredLen = ((raw[2].toInt() and 0xFF) shl 8) or (raw[3].toInt() and 0xFF)
        val actualEnd = declaredLen.coerceIn(20, length)

        val responseCode = when (codeByte) {
            2 -> RadiusResponseCode.ACCESS_ACCEPT
            3 -> RadiusResponseCode.ACCESS_REJECT
            11 -> RadiusResponseCode.ACCESS_CHALLENGE
            5 -> RadiusResponseCode.ACCOUNTING_RESPONSE
            else -> RadiusResponseCode.TIMEOUT_OR_ERROR
        }

        val parsedAttrs = mutableListOf<RadiusAttributeEntry>()
        val vsas = mutableListOf<RadiusVsaAttribute>()
        var replyMsg = ""
        var stateHex = ""

        var offset = 20
        while (offset + 2 <= actualEnd) {
            val type = raw[offset].toInt() and 0xFF
            val attrLen = raw[offset + 1].toInt() and 0xFF
            if (attrLen < 2 || offset + attrLen > actualEnd) break
            val valBytes = raw.copyOfRange(offset + 2, offset + attrLen)

            when (type) {
                18 -> { // Reply-Message
                    val msg = String(valBytes, Charsets.UTF_8)
                    replyMsg = if (replyMsg.isBlank()) msg else "$replyMsg; $msg"
                    parsedAttrs.add(RadiusAttributeEntry(18, "Reply-Message", msg))
                }
                24 -> { // State
                    stateHex = NetworkByteUtils.bytesToHex(valBytes, "")
                    parsedAttrs.add(RadiusAttributeEntry(24, "State", "0x$stateHex"))
                }
                26 -> { // Vendor-Specific Attribute (VSA)
                    if (valBytes.size >= 6) {
                        val vendorId = ByteBuffer.wrap(valBytes, 0, 4).int.toLong() and 0xFFFFFFFFL
                        var vOffset = 4
                        while (vOffset + 2 <= valBytes.size) {
                            val vType = valBytes[vOffset].toInt() and 0xFF
                            val vLen = valBytes[vOffset + 1].toInt() and 0xFF
                            if (vLen < 2 || vOffset + vLen > valBytes.size) break
                            val vData = valBytes.copyOfRange(vOffset + 2, vOffset + vLen)
                            val ascii = vData.map { b ->
                                val c = b.toInt() and 0xFF
                                if (c in 32..126) c.toChar() else '.'
                            }.joinToString("")
                            vsas.add(
                                RadiusVsaAttribute(
                                    vendorId = vendorId,
                                    vendorType = vType,
                                    valueText = ascii,
                                    rawHex = NetworkByteUtils.bytesToHex(vData, " ")
                                )
                            )
                            parsedAttrs.add(RadiusAttributeEntry(26, "VSA(Vendor=$vendorId, Type=$vType)", ascii))
                            vOffset += vLen
                        }
                    }
                }
                else -> {
                    val name = radiusAttributeName(type)
                    val valueStr = if (valBytes.all { (it.toInt() and 0xFF) in 32..126 }) {
                        String(valBytes, Charsets.UTF_8)
                    } else if (valBytes.size == 4) {
                        "${valBytes[0].toInt() and 0xFF}.${valBytes[1].toInt() and 0xFF}.${valBytes[2].toInt() and 0xFF}.${valBytes[3].toInt() and 0xFF} (0x${NetworkByteUtils.bytesToHex(valBytes)})"
                    } else {
                        "0x${NetworkByteUtils.bytesToHex(valBytes)}"
                    }
                    parsedAttrs.add(RadiusAttributeEntry(type, name, valueStr))
                }
            }
            offset += attrLen
        }

        return RadiusTestResult(
            serverIp = serverIp,
            port = port,
            responseCode = responseCode,
            replyMessage = replyMsg,
            stateHex = stateHex,
            attributes = parsedAttrs,
            vsaAttributes = vsas,
            rttMs = rttMs,
            isSuccess = responseCode == RadiusResponseCode.ACCESS_ACCEPT || responseCode == RadiusResponseCode.ACCESS_CHALLENGE
        )
    }

    suspend fun probeTacacsPlus(
        serverIp: String,
        port: Int = 49
    ): TacacsProbeResult = withContext(Dispatchers.IO) {
        val cleanHost = serverIp.trim()
        if (cleanHost.isBlank()) {
            return@withContext TacacsProbeResult(
                serverIp = cleanHost,
                port = port,
                isReachable = false,
                statusMessage = "Target host or IP required."
            )
        }

        val sessionId = random.nextInt()
        val header = buildTacacsAuthHeader(sessionId)
        val t0 = System.currentTimeMillis()
        var socket: Socket? = null
        try {
            socket = Socket().apply {
                soTimeout = 3500
                connect(InetSocketAddress(cleanHost, port), 3500)
            }
            socket.outputStream.write(header)
            socket.outputStream.flush()

            val rx = ByteArray(64)
            val read = runCatching { socket.inputStream.read(rx) }.getOrDefault(-1)
            val rtt = (System.currentTimeMillis() - t0).coerceAtLeast(1L)

            if (read >= 12) {
                val version = rx[0].toInt() and 0xFF
                val validHdr = (version and 0xF0) == 0xC0
                val hex = NetworkByteUtils.bytesToHex(rx.copyOf(read.coerceAtMost(24)), " ")
                return@withContext TacacsProbeResult(
                    serverIp = cleanHost,
                    port = port,
                    isReachable = true,
                    versionByte = version,
                    protocolHeaderValid = validHdr,
                    responseHex = hex,
                    rttMs = rtt,
                    statusMessage = if (validHdr) {
                        "TACACS+ Daemon active (Version 0x${version.toString(16).uppercase()}, RTT ${rtt}ms)"
                    } else {
                        "TCP/$port reachable; non-standard TACACS+ reply header (0x${version.toString(16)})"
                    }
                )
            } else {
                return@withContext TacacsProbeResult(
                    serverIp = cleanHost,
                    port = port,
                    isReachable = true,
                    rttMs = rtt,
                    statusMessage = "TCP/$port accepted connection (daemon closed or awaited body within ${rtt}ms)"
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            TacacsProbeResult(
                serverIp = cleanHost,
                port = port,
                isReachable = false,
                rttMs = (System.currentTimeMillis() - t0).coerceAtLeast(1L),
                statusMessage = e.message ?: "TCP/$port connection refused/timeout"
            )
        } finally {
            NetworkByteUtils.safeClose(socket)
        }
    }

    /**
     * Builds a 12-byte RFC 8907 TACACS+ v12.0 Authentication START header (version = 0xC0, type = 0x01, seq = 1).
     */
    fun buildTacacsAuthHeader(sessionId: Int, bodyLength: Int = 8): ByteArray {
        val headerAndMinimalStart = ByteBuffer.allocate(12 + bodyLength).apply {
            put(0xC0.toByte()) // major=0xC, minor=0x0
            put(0x01.toByte()) // TAC_PLUS_AUTHEN
            put(0x01.toByte()) // seq_no = 1
            put(0x01.toByte()) // TAC_PLUS_UNENCRYPTED_FLAG for lightweight diagnostic probe
            putInt(sessionId)
            putInt(bodyLength)
            if (bodyLength == 8) {
                put(0x01.toByte()) // action = LOGIN
                put(0x01.toByte()) // priv_lvl = 1
                put(0x01.toByte()) // authen_type = ASCII
                put(0x01.toByte()) // authen_service = LOGIN
                put(0.toByte())    // user_len
                put(0.toByte())    // port_len
                put(0.toByte())    // rem_addr_len
                put(0.toByte())    // data_len
            }
        }
        return headerAndMinimalStart.array()
    }

    private fun radiusAttributeName(type: Int): String = when (type) {
        1 -> "User-Name"
        6 -> "Service-Type"
        7 -> "Framed-Protocol"
        8 -> "Framed-IP-Address"
        18 -> "Reply-Message"
        24 -> "State"
        25 -> "Class"
        26 -> "Vendor-Specific"
        27 -> "Session-Timeout"
        28 -> "Idle-Timeout"
        else -> "Attr-$type"
    }
}
