// === FILE: app/src/main/java/com/enterwebslinger/network/data/repository/CoreProtocolsRepository.kt ===
package com.enterwebslinger.network.data.repository

import com.enterwebslinger.network.domain.model.DhcpServerOffer
import com.enterwebslinger.network.domain.model.NtpResult
import com.enterwebslinger.network.domain.model.SmtpDiagResult
import com.enterwebslinger.network.domain.model.TodResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.apache.commons.net.ntp.NTPUDPClient
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

class CoreProtocolsRepository {

    private val _lastDhcpDiagnostic = MutableStateFlow<String?>(null)
    val lastDhcpDiagnostic: StateFlow<String?> = _lastDhcpDiagnostic.asStateFlow()

    companion object {
        const val SECONDS_1900_TO_1970 = 2208988800L
        val DHCP_MAGIC_COOKIE = byteArrayOf(0x63.toByte(), 0x82.toByte(), 0x53.toByte(), 0x63.toByte())
    }

    suspend fun discoverDhcpServers(
        authorizedGatewayIp: String? = null,
        timeoutMs: Int = 3500,
        useInform: Boolean = false
    ): List<DhcpServerOffer> = withContext(Dispatchers.IO) {
        val discoveredServers = mutableListOf<DhcpServerOffer>()
        var socket: DatagramSocket? = null
        val diagNotes = mutableListOf<String>()

        try {
            var boundToPort68 = true
            socket = try {
                DatagramSocket(68).apply { broadcast = true }
            } catch (_: Exception) {
                boundToPort68 = false
                diagNotes.add("UDP 68 restricted by Android OS; bound ephemeral source port (servers replying unicast/broadcast may require DHCPINFORM).")
                DatagramSocket().apply { broadcast = true }
            }
            socket.soTimeout = 1200

            val xid = SecureRandom().nextInt()
            val mac = ByteArray(6).also { SecureRandom().nextBytes(it) }
            mac[0] = ((mac[0].toInt() and 0xFC) or 0x02).toByte() // Locally administered unicast MAC

            val packetBuffer = ByteBuffer.allocate(548)
            packetBuffer.put(1.toByte())
            packetBuffer.put(1.toByte())
            packetBuffer.put(6.toByte())
            packetBuffer.put(0.toByte())
            packetBuffer.putInt(xid)
            packetBuffer.putShort(0.toShort())
            packetBuffer.putShort(0x8000.toShort())
            packetBuffer.putInt(0)
            packetBuffer.putInt(0)
            packetBuffer.putInt(0)
            packetBuffer.putInt(0)
            packetBuffer.put(mac)
            packetBuffer.put(ByteArray(10))
            packetBuffer.put(ByteArray(64))
            packetBuffer.put(ByteArray(128))
            packetBuffer.put(DHCP_MAGIC_COOKIE)

            packetBuffer.put(53.toByte())
            packetBuffer.put(1.toByte())
            packetBuffer.put((if (useInform) 8 else 1).toByte())

            packetBuffer.put(55.toByte())
            val requestedOptions = byteArrayOf(1, 3, 6, 15, 51, 54, 66, 150.toByte())
            packetBuffer.put(requestedOptions.size.toByte())
            packetBuffer.put(requestedOptions)

            packetBuffer.put(255.toByte())

            packetBuffer.flip()
            val discoverBytes = ByteArray(packetBuffer.remaining())
            packetBuffer.get(discoverBytes)

            val broadcastPacket = DatagramPacket(
                discoverBytes,
                discoverBytes.size,
                InetAddress.getByName("255.255.255.255"),
                67
            )

            val startTime = System.currentTimeMillis()
            socket.send(broadcastPacket)

            val recvBuffer = ByteArray(2048)
            val recvPacket = DatagramPacket(recvBuffer, recvBuffer.size)

            val deadline = startTime + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                try {
                    socket.receive(recvPacket)
                    val responseTime = System.currentTimeMillis() - startTime
                    val data = recvPacket.data
                    val length = recvPacket.length

                    if (length >= 240) {
                        val offer = parseDhcpPacket(data, length, recvPacket.address.hostAddress ?: "", authorizedGatewayIp, responseTime)
                        if (offer != null && discoveredServers.none { it.serverIp == offer.serverIp }) {
                            discoveredServers.add(offer)
                        }
                    }
                } catch (_: SocketTimeoutException) {
                }
            }

            if (discoveredServers.isEmpty()) {
                val msgType = if (useInform) "DHCPINFORM" else "DHCPDISCOVER"
                _lastDhcpDiagnostic.value = buildString {
                    append("No $msgType responses received within ${timeoutMs}ms.")
                    if (!boundToPort68 && diagNotes.isNotEmpty()) {
                        append(" ").append(diagNotes.first())
                    }
                }
            } else {
                _lastDhcpDiagnostic.value = "Discovered ${discoveredServers.size} DHCP server(s)."
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _lastDhcpDiagnostic.value = "DHCP scan error: ${e.message ?: "Socket failure"}"
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }

        discoveredServers
    }

    private fun parseDhcpPacket(
        data: ByteArray,
        length: Int,
        senderIp: String,
        authorizedGatewayIp: String?,
        responseTimeMs: Long
    ): DhcpServerOffer? {
        val buffer = ByteBuffer.wrap(data, 0, length)
        val op = buffer.get()
        if (op != 2.toByte()) return null

        buffer.position(16)
        val yiaddrBytes = ByteArray(4)
        buffer.get(yiaddrBytes)
        val offeredIp = InetAddress.getByAddress(yiaddrBytes).hostAddress ?: "0.0.0.0"

        buffer.position(236)
        val cookie = ByteArray(4)
        buffer.get(cookie)
        if (!cookie.contentEquals(DHCP_MAGIC_COOKIE)) return null

        var subnetMask = ""
        val routers = mutableListOf<String>()
        val dnsServers = mutableListOf<String>()
        var domainName = ""
        var leaseTime = 0L
        var serverId = senderIp
        var tftpServer = "N/A"
        var voipTftpServer = "N/A"

        while (buffer.hasRemaining()) {
            val optType = buffer.get().toInt() and 0xFF
            if (optType == 255) break
            if (optType == 0) continue

            if (!buffer.hasRemaining()) break
            val optLen = buffer.get().toInt() and 0xFF
            if (buffer.remaining() < optLen) break

            val optBytes = ByteArray(optLen)
            buffer.get(optBytes)

            when (optType) {
                1 -> if (optLen >= 4) subnetMask = InetAddress.getByAddress(optBytes.take(4).toByteArray()).hostAddress ?: subnetMask
                3 -> for (i in 0 until optLen step 4) {
                    if (i + 4 <= optLen) {
                        InetAddress.getByAddress(optBytes.sliceArray(i until i + 4)).hostAddress?.let { routers.add(it) }
                    }
                }
                6 -> for (i in 0 until optLen step 4) {
                    if (i + 4 <= optLen) {
                        InetAddress.getByAddress(optBytes.sliceArray(i until i + 4)).hostAddress?.let { dnsServers.add(it) }
                    }
                }
                15 -> domainName = String(optBytes, Charsets.US_ASCII).trimEnd(0.toChar())
                51 -> if (optLen >= 4) leaseTime = ByteBuffer.wrap(optBytes).int.toLong() and 0xFFFFFFFFL
                54 -> if (optLen >= 4) serverId = InetAddress.getByAddress(optBytes.take(4).toByteArray()).hostAddress ?: senderIp
                66 -> tftpServer = String(optBytes, Charsets.US_ASCII).trimEnd(0.toChar())
                150 -> if (optLen >= 4) voipTftpServer = InetAddress.getByAddress(optBytes.take(4).toByteArray()).hostAddress ?: "N/A"
            }
        }

        val effectiveServerIp = serverId.ifBlank { senderIp }
        val isRogue = !authorizedGatewayIp.isNullOrBlank() &&
            effectiveServerIp != authorizedGatewayIp &&
            !routers.contains(authorizedGatewayIp)

        return DhcpServerOffer(
            serverIp = effectiveServerIp,
            offeredIp = offeredIp,
            subnetMask = subnetMask,
            routers = routers,
            dnsServers = dnsServers,
            domainName = domainName,
            leaseTimeSeconds = leaseTime,
            tftpServer = tftpServer,
            voipTftpServer = voipTftpServer,
            isRogue = isRogue,
            responseTimeMs = responseTimeMs
        )
    }

    suspend fun queryTimeOfDay(
        host: String,
        port: Int = 37,
        timeoutMs: Int = 3000,
        useUdp: Boolean = false
    ): TodResult = withContext(Dispatchers.IO) {
        if (host.isBlank()) {
            return@withContext TodResult(host, port, 0, 0, "", 0, 0, false, "Target host is required")
        }
        val startTime = System.currentTimeMillis()
        try {
            val buffer = ByteArray(4)
            var bytesRead = 0

            if (useUdp) {
                DatagramSocket().use { dgram ->
                    dgram.soTimeout = timeoutMs
                    val addr = InetAddress.getByName(host)
                    val trigger = byteArrayOf(0x0A)
                    dgram.send(DatagramPacket(trigger, trigger.size, addr, port))
                    val respPacket = DatagramPacket(ByteArray(16), 16)
                    dgram.receive(respPacket)
                    bytesRead = respPacket.length.coerceAtMost(4)
                    System.arraycopy(respPacket.data, 0, buffer, 0, bytesRead)
                }
            } else {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), timeoutMs)
                    socket.soTimeout = timeoutMs

                    val isStream = socket.getInputStream()
                    while (bytesRead < 4) {
                        val read = isStream.read(buffer, bytesRead, 4 - bytesRead)
                        if (read == -1) break
                        bytesRead += read
                    }
                }
            }

            val latency = System.currentTimeMillis() - startTime
            if (bytesRead < 4) {
                return@withContext TodResult(
                    serverHost = host,
                    port = port,
                    raw32BitSeconds = 0,
                    epochSecondsUtc = 0,
                    formattedUtcTime = "Incomplete response",
                    clockOffsetMs = 0,
                    latencyMs = latency,
                    isSuccess = false,
                    errorMessage = "Server returned less than 4 bytes (RFC 868 expects 32-bit uint)"
                )
            }

            val rawSeconds = ByteBuffer.wrap(buffer).int.toLong() and 0xFFFFFFFFL
            val utcEpochSeconds = rawSeconds - SECONDS_1900_TO_1970
            val utcEpochMillis = utcEpochSeconds * 1000L

            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val formatted = sdf.format(Date(utcEpochMillis))
            val offset = System.currentTimeMillis() - utcEpochMillis

            TodResult(
                serverHost = host,
                port = port,
                raw32BitSeconds = rawSeconds,
                epochSecondsUtc = utcEpochSeconds,
                formattedUtcTime = formatted,
                clockOffsetMs = offset,
                latencyMs = latency,
                isSuccess = true
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            TodResult(
                serverHost = host,
                port = port,
                raw32BitSeconds = 0,
                epochSecondsUtc = 0,
                formattedUtcTime = "Error",
                clockOffsetMs = 0,
                latencyMs = System.currentTimeMillis() - startTime,
                isSuccess = false,
                errorMessage = e.message ?: "Failed to connect to ToD server on ${if (useUdp) "UDP" else "TCP"} port $port"
            )
        }
    }

    suspend fun querySntp(host: String, timeoutMs: Int = 3000): NtpResult = withContext(Dispatchers.IO) {
        if (host.isBlank()) {
            return@withContext NtpResult(host, 0, 0, "N/A", 0.0, 0.0, "", false, "NTP host is required")
        }
        val client = NTPUDPClient()
        client.defaultTimeout = timeoutMs
        try {
            client.open()
            val address = InetAddress.getByName(host)
            val info = client.getTime(address)
            info.computeDetails()

            val msg = info.message
            val stratum = msg.stratum
            val leap = msg.leapIndicator
            val refId = String.format("%08X", msg.referenceId)
            val delay = (info.delay ?: 0L).toDouble()
            val offset = (info.offset ?: 0L).toDouble()

            val transmitTime = msg.transmitTimeStamp?.date ?: Date()
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS 'UTC'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }

            NtpResult(
                serverHost = host,
                stratum = stratum,
                leapIndicator = leap,
                referenceId = refId,
                roundTripDelayMs = delay,
                clockOffsetMs = offset,
                transmitTimeFormatted = sdf.format(transmitTime),
                isSuccess = true
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            NtpResult(
                serverHost = host,
                stratum = 0,
                leapIndicator = 0,
                referenceId = "N/A",
                roundTripDelayMs = 0.0,
                clockOffsetMs = 0.0,
                transmitTimeFormatted = "Error",
                isSuccess = false,
                errorMessage = e.message ?: "SNTP query failed"
            )
        } finally {
            client.close()
        }
    }

    suspend fun diagnoseSmtp(
        host: String,
        port: Int = 25,
        testOpenRelay: Boolean = true,
        heloHost: String = "enterwebslinger.local",
        timeoutMs: Int = 5000
    ): SmtpDiagResult = withContext(Dispatchers.IO) {
        if (host.isBlank()) {
            return@withContext SmtpDiagResult(host, port, "", emptyList(), false, false, false, emptyList(), 0, false, "SMTP host is required")
        }
        val logs = mutableListOf<String>()
        val startTime = System.currentTimeMillis()
        var socket: Socket? = null

        var banner = "Unknown"
        val ehloKeywords = mutableListOf<String>()
        var startTlsSupported = false
        var isTlsActive = false
        var openRelayVulnerable = false

        try {
            logs.add(">> Connecting to $host:$port (TCP)...")
            socket = if (port == 465) {
                val sslFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
                sslFactory.createSocket().apply {
                    connect(InetSocketAddress(host, port), timeoutMs)
                    soTimeout = timeoutMs
                    isTlsActive = true
                }
            } else {
                Socket().apply {
                    connect(InetSocketAddress(host, port), timeoutMs)
                    soTimeout = timeoutMs
                }
            }

            var reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.US_ASCII))
            var writer = PrintWriter(socket.getOutputStream(), true)

            val bannerLine = reader.readLine() ?: ""
            banner = bannerLine
            logs.add("<< $bannerLine")

            if (!bannerLine.startsWith("220")) {
                return@withContext SmtpDiagResult(
                    host = host,
                    port = port,
                    banner = banner,
                    ehloKeywords = emptyList(),
                    startTlsSupported = false,
                    isTlsActive = isTlsActive,
                    openRelayVulnerable = false,
                    transcriptLogs = logs,
                    transactionLatencyMs = System.currentTimeMillis() - startTime,
                    isSuccess = false,
                    errorMessage = "Invalid SMTP Banner: $bannerLine"
                )
            }

            logs.add(">> EHLO $heloHost")
            writer.println("EHLO $heloHost")

            var ehloLine: String?
            while (reader.readLine().also { ehloLine = it } != null) {
                logs.add("<< $ehloLine")
                val clean = ehloLine!!.trim()
                if (clean.length > 4) {
                    val keyword = clean.substring(4).trim()
                    ehloKeywords.add(keyword)
                    if (keyword.equals("STARTTLS", ignoreCase = true)) {
                        startTlsSupported = true
                    }
                }
                if (clean.startsWith("250 ")) break
            }

            if (startTlsSupported && port != 465) {
                logs.add(">> STARTTLS")
                writer.println("STARTTLS")
                val tlsResponse = reader.readLine() ?: ""
                logs.add("<< $tlsResponse")

                if (tlsResponse.startsWith("220")) {
                    val sslFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
                    val sslSocket = sslFactory.createSocket(socket, host, port, true) as SSLSocket
                    sslSocket.startHandshake()
                    isTlsActive = true
                    logs.add("[TLS Handshake Completed: ${sslSocket.session.cipherSuite} / ${sslSocket.session.protocol}]")

                    reader = BufferedReader(InputStreamReader(sslSocket.getInputStream(), Charsets.US_ASCII))
                    writer = PrintWriter(sslSocket.getOutputStream(), true)

                    logs.add(">> EHLO $heloHost (TLS)")
                    writer.println("EHLO $heloHost")
                    while (reader.readLine().also { ehloLine = it } != null) {
                        logs.add("<< $ehloLine")
                        if (ehloLine!!.startsWith("250 ")) break
                    }
                }
            }

            if (testOpenRelay) {
                logs.add(">> MAIL FROM:<probe@enterwebslinger.invalid>")
                writer.println("MAIL FROM:<probe@enterwebslinger.invalid>")
                val mailFromResp = reader.readLine() ?: ""
                logs.add("<< $mailFromResp")

                if (mailFromResp.startsWith("250")) {
                    logs.add(">> RCPT TO:<relay-check@test-external-domain.invalid>")
                    writer.println("RCPT TO:<relay-check@test-external-domain.invalid>")
                    val rcptResp = reader.readLine() ?: ""
                    logs.add("<< $rcptResp")

                    if (rcptResp.startsWith("250")) {
                        openRelayVulnerable = true
                        logs.add("[WARNING: Server accepted external recipient without authentication]")
                    } else {
                        logs.add("[PASSED: Server rejected unauthenticated relay: $rcptResp]")
                    }

                    writer.println("RSET")
                    val rsetResp = reader.readLine() ?: ""
                    logs.add("<< $rsetResp")
                }
            }

            writer.println("QUIT")
            val quitResp = reader.readLine() ?: ""
            logs.add("<< $quitResp")

            SmtpDiagResult(
                host = host,
                port = port,
                banner = banner,
                ehloKeywords = ehloKeywords,
                startTlsSupported = startTlsSupported,
                isTlsActive = isTlsActive,
                openRelayVulnerable = openRelayVulnerable,
                transcriptLogs = logs,
                transactionLatencyMs = System.currentTimeMillis() - startTime,
                isSuccess = true
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logs.add("[ERROR: ${e.message}]")
            SmtpDiagResult(
                host = host,
                port = port,
                banner = banner,
                ehloKeywords = ehloKeywords,
                startTlsSupported = startTlsSupported,
                isTlsActive = isTlsActive,
                openRelayVulnerable = openRelayVulnerable,
                transcriptLogs = logs,
                transactionLatencyMs = System.currentTimeMillis() - startTime,
                isSuccess = false,
                errorMessage = e.message ?: "SMTP diagnostic error"
            )
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }
}
