// === FILE: app/src/main/java/com/enterwebslinger/network/data/repository/EnterpriseIdentityRepository.kt ===
package com.enterwebslinger.network.data.repository

import com.enterwebslinger.network.core.util.NetworkByteUtils
import com.enterwebslinger.network.data.protocol.IdentityWireCodecs
import com.enterwebslinger.network.domain.model.LdapEntryRecord
import com.enterwebslinger.network.domain.model.LdapTestConfig
import com.enterwebslinger.network.domain.model.LdapTestResult
import com.enterwebslinger.network.domain.model.TacacsAuthConfig
import com.enterwebslinger.network.domain.model.TacacsAuthenMethod
import com.enterwebslinger.network.domain.model.TacacsFullResult
import com.enterwebslinger.network.domain.model.TacacsOperationMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Pure-Kotlin Enterprise Identity & Privilege Repository:
 * - RFC 8907 TACACS+ (TCP 49): Full 12-byte header + MD5 body obfuscation + Authentication (0x01) & Command Authorization (0x02)
 * - RFC 4511 LDAP / LDAPS (TCP 389 / 636): ASN.1 BER Simple Bind / UPN Bind + SearchRequest / SearchResultEntry / SearchResultDone
 */
class EnterpriseIdentityRepository {

    private val random = SecureRandom()

    suspend fun testTacacsPlus(config: TacacsAuthConfig): TacacsFullResult =
        withContext(Dispatchers.IO) {
            val cleanHost = config.serverIp.trim()
            if (cleanHost.isBlank() || config.username.isBlank()) {
                return@withContext TacacsFullResult(
                    serverIp = cleanHost,
                    port = config.port,
                    mode = config.mode,
                    isReachable = false,
                    isAuthorizedOrPassed = false,
                    errorMessage = "TACACS+ Host and Username are required."
                )
            }

            val sessionId = random.nextInt()
            val sessionHex = String.format("%08X", sessionId)
            val version = IdentityWireCodecs.TAC_PLUS_MAJOR_VER // 0xC0 (v12.0)
            var socket: Socket? = null
            val t0 = System.currentTimeMillis()

            try {
                socket = Socket().apply {
                    connect(InetSocketAddress(cleanHost, config.port.coerceIn(1, 65535)), 4500)
                    soTimeout = 4500
                    tcpNoDelay = true
                }
                val outStream = socket.getOutputStream()
                val inStream = socket.getInputStream()

                if (config.mode == TacacsOperationMode.AUTHENTICATION) {
                    val startBody = IdentityWireCodecs.buildTacacsAuthenStartBody(
                        authenMethod = config.authenMethod,
                        privLevel = config.privilegeLevel,
                        username = config.username.trim(),
                        password = config.password,
                        remoteAddr = config.remoteAddress.ifBlank { "127.0.0.1" }
                    )
                    val minorVer = if (config.authenMethod == TacacsAuthenMethod.PAP) {
                        (IdentityWireCodecs.TAC_PLUS_MAJOR_VER.toInt() or IdentityWireCodecs.TAC_PLUS_MINOR_ONE.toInt()).toByte()
                    } else version

                    val reqPkt = IdentityWireCodecs.buildTacacsPacket(
                        version = minorVer,
                        type = IdentityWireCodecs.TAC_PLUS_AUTHEN,
                        seqNo = 0x01,
                        sessionId = sessionId,
                        plainBody = startBody,
                        sharedSecret = config.sharedSecret
                    )
                    outStream.write(reqPkt)
                    outStream.flush()

                    var (hdr, body) = readTacacsFrame(inStream)
                    var parsed = IdentityWireCodecs.parseTacacsReply(hdr, body, config.sharedSecret)

                    // Handle interactive ASCII GETPASS (0x05) or GETDATA (0x03) challenge
                    if (parsed.status == 0x05 || parsed.status == 0x03) {
                        val contBody = IdentityWireCodecs.buildTacacsAuthenContinueBody(config.password)
                        val contPkt = IdentityWireCodecs.buildTacacsPacket(
                            version = minorVer,
                            type = IdentityWireCodecs.TAC_PLUS_AUTHEN,
                            seqNo = 0x03,
                            sessionId = sessionId,
                            plainBody = contBody,
                            sharedSecret = config.sharedSecret
                        )
                        outStream.write(contPkt)
                        outStream.flush()
                        val second = readTacacsFrame(inStream)
                        hdr = second.first
                        body = second.second
                        parsed = IdentityWireCodecs.parseTacacsReply(hdr, body, config.sharedSecret)
                    }

                    val rtt = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
                    TacacsFullResult(
                        serverIp = cleanHost,
                        port = config.port,
                        mode = config.mode,
                        isReachable = true,
                        isAuthorizedOrPassed = parsed.status == 0x01,
                        versionByte = parsed.version,
                        flagsByte = parsed.flags,
                        sessionIdHex = sessionHex,
                        obfuscatedBody = parsed.obfuscated,
                        statusCode = parsed.status,
                        statusLabel = IdentityWireCodecs.tacacsAuthenStatusLabel(parsed.status),
                        serverMessage = parsed.serverMessage,
                        rttMs = rtt,
                        rawResponseHex = NetworkByteUtils.bytesToHex(hdr + body, " ")
                    )
                } else {
                    val authBody = IdentityWireCodecs.buildTacacsAuthorRequestBody(
                        authenMethod = config.authenMethod,
                        privLevel = config.privilegeLevel,
                        username = config.username.trim(),
                        command = config.commandToAuthorize,
                        commandArgs = config.commandArgs,
                        remoteAddr = config.remoteAddress.ifBlank { "127.0.0.1" }
                    )
                    val reqPkt = IdentityWireCodecs.buildTacacsPacket(
                        version = version,
                        type = IdentityWireCodecs.TAC_PLUS_AUTHOR,
                        seqNo = 0x01,
                        sessionId = sessionId,
                        plainBody = authBody,
                        sharedSecret = config.sharedSecret
                    )
                    outStream.write(reqPkt)
                    outStream.flush()

                    val (hdr, body) = readTacacsFrame(inStream)
                    val parsed = IdentityWireCodecs.parseTacacsReply(hdr, body, config.sharedSecret)
                    val rtt = (System.currentTimeMillis() - t0).coerceAtLeast(1L)

                    TacacsFullResult(
                        serverIp = cleanHost,
                        port = config.port,
                        mode = config.mode,
                        isReachable = true,
                        isAuthorizedOrPassed = parsed.status == 0x01 || parsed.status == 0x02,
                        versionByte = parsed.version,
                        flagsByte = parsed.flags,
                        sessionIdHex = sessionHex,
                        obfuscatedBody = parsed.obfuscated,
                        statusCode = parsed.status,
                        statusLabel = IdentityWireCodecs.tacacsAuthorStatusLabel(parsed.status),
                        serverMessage = parsed.serverMessage,
                        returnedAvPairs = parsed.avPairs,
                        rttMs = rtt,
                        rawResponseHex = NetworkByteUtils.bytesToHex(hdr + body, " ")
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                TacacsFullResult(
                    serverIp = cleanHost,
                    port = config.port,
                    mode = config.mode,
                    isReachable = false,
                    isAuthorizedOrPassed = false,
                    sessionIdHex = sessionHex,
                    rttMs = (System.currentTimeMillis() - t0).coerceAtLeast(1L),
                    errorMessage = e.localizedMessage ?: "TACACS+ connection failed"
                )
            } finally {
                NetworkByteUtils.safeClose(socket)
            }
        }

    suspend fun testLdapBindAndSearch(config: LdapTestConfig): LdapTestResult =
        withContext(Dispatchers.IO) {
            val cleanHost = config.host.trim()
            if (cleanHost.isBlank() || config.bindDnOrUpn.isBlank()) {
                return@withContext LdapTestResult(
                    host = cleanHost,
                    port = config.port,
                    useLdaps = config.useLdaps,
                    isBindSuccess = false,
                    errorMessage = "LDAP Host and Bind DN / UPN (e.g. user@domain.local) are required."
                )
            }

            var socket: Socket? = null
            val t0 = System.currentTimeMillis()
            try {
                socket = openLdapSocket(cleanHost, config.port.coerceIn(1, 65535), config.useLdaps, config.allowSelfSignedTls)
                val tlsProtocol = (socket as? SSLSocket)?.session?.protocol ?: "Plaintext TCP"
                val out = socket.getOutputStream()
                val input = socket.getInputStream()

                // 1. BindRequest (MsgID = 1)
                val bindBytes = IdentityWireCodecs.buildLdapBindRequest(
                    messageId = 1,
                    bindDn = config.bindDnOrUpn.trim(),
                    password = config.bindPassword
                )
                out.write(bindBytes)
                out.flush()

                val bindFrame = readSingleBerMessage(input)
                val bindParsed = IdentityWireCodecs.parseLdapMessage(bindFrame)
                val bindOk = bindParsed.resultCode == 0

                val entries = mutableListOf<LdapEntryRecord>()
                var searchCode: Int? = null
                var searchDiag = ""

                // 2. SearchRequest (MsgID = 2) if Bind succeeded and BaseDN was given
                if (bindOk && config.baseDn.isNotBlank()) {
                    val searchBytes = IdentityWireCodecs.buildLdapSearchRequest(
                        messageId = 2,
                        baseDn = config.baseDn.trim(),
                        filterText = config.searchFilter,
                        attributes = config.requestedAttributes
                    )
                    out.write(searchBytes)
                    out.flush()

                    while (true) {
                        val frame = readSingleBerMessage(input)
                        val msg = IdentityWireCodecs.parseLdapMessage(frame)
                        if (msg.protocolOpTag == 0x64 && msg.entry != null) {
                            entries.add(msg.entry)
                        } else if (msg.protocolOpTag == 0x65) {
                            searchCode = msg.resultCode
                            searchDiag = msg.diagnosticMessage
                            break
                        } else {
                            break
                        }
                    }
                }

                // Polite UnbindRequest (MsgID = 3)
                runCatching {
                    out.write(IdentityWireCodecs.buildLdapUnbindRequest(3))
                    out.flush()
                }

                val rtt = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
                LdapTestResult(
                    host = cleanHost,
                    port = config.port,
                    useLdaps = config.useLdaps,
                    isBindSuccess = bindOk,
                    bindResultCode = bindParsed.resultCode,
                    bindResultLabel = IdentityWireCodecs.ldapResultLabel(bindParsed.resultCode),
                    bindDiagnosticMessage = bindParsed.diagnosticMessage,
                    searchResultCode = searchCode,
                    searchDiagnosticMessage = searchDiag,
                    tlsProtocol = tlsProtocol,
                    entries = entries,
                    rttMs = rtt
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LdapTestResult(
                    host = cleanHost,
                    port = config.port,
                    useLdaps = config.useLdaps,
                    isBindSuccess = false,
                    rttMs = (System.currentTimeMillis() - t0).coerceAtLeast(1L),
                    errorMessage = e.localizedMessage ?: "LDAP connection failed"
                )
            } finally {
                NetworkByteUtils.safeClose(socket)
            }
        }

    private fun readTacacsFrame(inStream: InputStream): Pair<ByteArray, ByteArray> {
        val header = NetworkByteUtils.readExact(inStream, 12)
        val bodyLen = ByteBuffer.wrap(header, 8, 4).int.coerceIn(0, 65536)
        val body = if (bodyLen > 0) NetworkByteUtils.readExact(inStream, bodyLen) else ByteArray(0)
        return header to body
    }

    private fun openLdapSocket(
        host: String,
        port: Int,
        useLdaps: Boolean,
        allowSelfSignedTls: Boolean
    ): Socket {
        if (!useLdaps) {
            return Socket().apply {
                connect(InetSocketAddress(host, port), 5000)
                soTimeout = 5000
                tcpNoDelay = true
            }
        }
        val sslFactory = if (allowSelfSignedTls) {
            val trustAll = arrayOf<TrustManager>(
                object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                }
            )
            SSLContext.getInstance("TLS").apply { init(null, trustAll, SecureRandom()) }.socketFactory
        } else {
            javax.net.ssl.SSLSocketFactory.getDefault() as javax.net.ssl.SSLSocketFactory
        }

        val plain = Socket().apply {
            connect(InetSocketAddress(host, port), 5000)
            soTimeout = 5000
            tcpNoDelay = true
        }
        val sslSock = sslFactory.createSocket(plain, host, port, true) as SSLSocket
        sslSock.soTimeout = 5000
        sslSock.startHandshake()
        return sslSock
    }

    private fun readSingleBerMessage(input: InputStream): ByteArray {
        val tag = input.read()
        if (tag == -1) throw java.io.EOFException("LDAP peer closed connection")
        val lenFirst = input.read()
        if (lenFirst == -1) throw java.io.EOFException("EOF reading BER length")

        val out = ByteArrayOutputStream()
        out.write(tag)
        out.write(lenFirst)

        val contentLen = if (lenFirst < 0x80) {
            lenFirst
        } else {
            val lenBytesCount = lenFirst and 0x7F
            var lenAcc = 0
            repeat(lenBytesCount) {
                val b = input.read()
                if (b == -1) throw java.io.EOFException("EOF reading long BER length")
                out.write(b)
                lenAcc = (lenAcc shl 8) or (b and 0xFF)
            }
            lenAcc
        }.coerceIn(0, 262144)

        if (contentLen > 0) {
            out.write(NetworkByteUtils.readExact(input, contentLen))
        }
        return out.toByteArray()
    }
}
