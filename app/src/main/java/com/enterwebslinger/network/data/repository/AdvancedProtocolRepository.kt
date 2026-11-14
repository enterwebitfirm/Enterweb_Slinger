// === FILE: app/src/main/java/com/enterwebslinger/network/data/repository/AdvancedProtocolRepository.kt ===
package com.enterwebslinger.network.data.repository

import com.enterwebslinger.network.domain.model.DnsBenchmarkEntry
import com.enterwebslinger.network.domain.model.HappyEyeballsResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * Pure-Kotlin Advanced Protocol Suite:
 * 1) Concurrent Multi-Resolver DNS Benchmark + DNSSEC DO/AD Validator (DHCP DNS, 1.1.1.1, 8.8.8.8, 9.9.9.9, 208.67.222.222)
 * 2) RFC 8305 Happy Eyeballs Dual-Stack Race + TLS ALPN (h2/http1.1) & Alt-Svc HTTP/3 (h3) Negotiator
 */
class AdvancedProtocolRepository(
    private val networkInfoRepository: NetworkInfoRepository?
) {
    private val random = SecureRandom()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    suspend fun runDnsAndDnssecBenchmark(domain: String = "cloudflare.com"): List<DnsBenchmarkEntry> =
        withContext(Dispatchers.IO) {
            val cleanDomain = domain.trim().ifBlank { "cloudflare.com" }
            val activeDns = networkInfoRepository?.topology?.value?.dnsServers?.firstOrNull {
                it.isNotBlank() && !it.startsWith("127.")
            }

            val resolvers = buildList {
                if (!activeDns.isNullOrBlank()) {
                    add("Active DHCP DNS" to activeDns)
                }
                add("Cloudflare" to "1.1.1.1")
                add("Google Public" to "8.8.8.8")
                add("Quad9 Security" to "9.9.9.9")
                add("OpenDNS" to "208.67.222.222")
            }.distinctBy { it.second }

            coroutineScope {
                resolvers.map { (label, ip) ->
                    async {
                        benchmarkSingleResolver(label, ip, cleanDomain)
                    }
                }.awaitAll().sortedWith(compareBy<DnsBenchmarkEntry> { !it.isSuccess }.thenBy { it.avgRttMs })
            }
        }

    private fun benchmarkSingleResolver(
        resolverName: String,
        resolverIp: String,
        domain: String
    ): DnsBenchmarkEntry {
        val rtts = mutableListOf<Long>()
        var lastAdFlag = false
        var resolvedIps = emptyList<String>()
        var lastErr: String? = null

        repeat(3) {
            val txId = random.nextInt(65535) + 1
            val wireQuery = buildDnsEdnsQueryPacket(domain = domain, transactionId = txId, requestDnssecDo = true)
            var sock: DatagramSocket? = null
            val t0 = System.currentTimeMillis()
            try {
                sock = DatagramSocket().apply { soTimeout = 2500 }
                val addr = InetAddress.getByName(resolverIp)
                sock.send(DatagramPacket(wireQuery, wireQuery.size, addr, 53))
                val rx = ByteArray(2048)
                val pkt = DatagramPacket(rx, rx.size)
                sock.receive(pkt)
                val rtt = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
                val parsed = parseDnsEdnsResponse(rx, pkt.length, txId)
                lastAdFlag = parsed.authenticatedData
                resolvedIps = parsed.resolvedIps
                rtts.add(rtt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastErr = e.message ?: "Query timeout"
            } finally {
                runCatching { sock?.close() }
            }
        }

        return if (rtts.isNotEmpty()) {
            DnsBenchmarkEntry(
                resolverName = resolverName,
                resolverIp = resolverIp,
                protocolType = "UDP/53 + EDNS0 DO",
                domainQueried = domain,
                minRttMs = rtts.minOrNull() ?: 0L,
                avgRttMs = (rtts.average()).toLong(),
                dnssecAuthenticatedData = lastAdFlag,
                resolvedIps = resolvedIps,
                isSuccess = true
            )
        } else {
            DnsBenchmarkEntry(
                resolverName = resolverName,
                resolverIp = resolverIp,
                protocolType = "UDP/53 + EDNS0 DO",
                domainQueried = domain,
                minRttMs = 0L,
                avgRttMs = 0L,
                dnssecAuthenticatedData = false,
                resolvedIps = emptyList(),
                isSuccess = false,
                errorMessage = lastErr ?: "Timeout"
            )
        }
    }

    /**
     * Builds RFC 1035 DNS Type A (1) query with RFC 6891 EDNS0 OPT pseudo-RR (Type 41) & DO bit (0x8000).
     */
    fun buildDnsEdnsQueryPacket(
        domain: String,
        transactionId: Int = 0x1234,
        requestDnssecDo: Boolean = true
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val hdr = ByteBuffer.allocate(12).apply {
            putShort(transactionId.toShort())
            putShort(0x0120.toShort()) // RD=1 (0x0100) + AD=1 check (0x0020)
            putShort(1.toShort())      // QDCOUNT = 1
            putShort(0.toShort())      // ANCOUNT
            putShort(0.toShort())      // NSCOUNT
            putShort(if (requestDnssecDo) 1.toShort() else 0.toShort()) // ARCOUNT = 1 (EDNS0 OPT)
        }
        out.write(hdr.array())

        for (label in domain.trim().trimEnd('.').split('.')) {
            val bytes = label.toByteArray(Charsets.UTF_8)
            if (bytes.isNotEmpty() && bytes.size <= 63) {
                out.write(bytes.size)
                out.write(bytes)
            }
        }
        out.write(0) // root terminator

        // QTYPE=A (1), QCLASS=IN (1)
        out.write(ByteBuffer.allocate(4).putShort(1).putShort(1).array())

        if (requestDnssecDo) {
            // EDNS0 OPT RR: root(0), type=41(0x0029), udpPayload=4096(0x1000), extRcode=0, version=0, flags=0x8000 (DO), rdlen=0
            out.write(0)
            out.write(ByteBuffer.allocate(10).apply {
                putShort(41.toShort())
                putShort(4096.toShort())
                putInt(0x00008000) // DO bit set
                putShort(0.toShort())
            }.array())
        }
        return out.toByteArray()
    }

    data class ParsedDnsWireResponse(
        val authenticatedData: Boolean,
        val rcode: Int,
        val resolvedIps: List<String>
    )

    fun parseDnsEdnsResponse(raw: ByteArray, length: Int, expectedTxId: Int? = null): ParsedDnsWireResponse {
        if (length < 12 || length > raw.size) {
            return ParsedDnsWireResponse(false, -1, emptyList())
        }
        val buf = ByteBuffer.wrap(raw, 0, length)
        val rxTxId = buf.short.toInt() and 0xFFFF
        if (expectedTxId != null && rxTxId != (expectedTxId and 0xFFFF)) {
            return ParsedDnsWireResponse(false, -1, emptyList())
        }
        val flags = buf.short.toInt() and 0xFFFF
        val adBit = (flags and 0x0020) != 0
        val rcode = flags and 0x000F
        val qdCount = buf.short.toInt() and 0xFFFF
        val anCount = buf.short.toInt() and 0xFFFF
        buf.short // nsCount
        buf.short // arCount

        var offset = 12
        repeat(qdCount) {
            offset = skipDnsName(raw, offset, length)
            offset += 4 // qtype + qclass
            if (offset > length) return ParsedDnsWireResponse(adBit, rcode, emptyList())
        }

        val ips = mutableListOf<String>()
        repeat(anCount) {
            if (offset >= length) return@repeat
            offset = skipDnsName(raw, offset, length)
            if (offset + 10 > length) return@repeat
            val rrType = ((raw[offset].toInt() and 0xFF) shl 8) or (raw[offset + 1].toInt() and 0xFF)
            val rdLength = ((raw[offset + 8].toInt() and 0xFF) shl 8) or (raw[offset + 9].toInt() and 0xFF)
            offset += 10
            if (offset + rdLength > length) return@repeat
            if (rrType == 1 && rdLength == 4) {
                ips.add("${raw[offset].toInt() and 0xFF}.${raw[offset + 1].toInt() and 0xFF}.${raw[offset + 2].toInt() and 0xFF}.${raw[offset + 3].toInt() and 0xFF}")
            } else if (rrType == 28 && rdLength == 16) {
                runCatching {
                    InetAddress.getByAddress(raw.copyOfRange(offset, offset + 16)).hostAddress?.let { ips.add(it) }
                }
            }
            offset += rdLength
        }

        return ParsedDnsWireResponse(adBit, rcode, ips)
    }

    private fun skipDnsName(raw: ByteArray, startOffset: Int, maxLen: Int): Int {
        var pos = startOffset
        var safety = 0
        while (pos < maxLen && safety++ < 64) {
            val len = raw[pos].toInt() and 0xFF
            if (len == 0) return pos + 1
            if ((len and 0xC0) == 0xC0) return pos + 2
            pos += len + 1
        }
        return maxLen
    }

    suspend fun testHappyEyeballsAndAlpn(host: String, port: Int = 443): HappyEyeballsResult =
        withContext(Dispatchers.IO) {
            val cleanHost = host.trim()
                .removePrefix("https://")
                .removePrefix("http://")
                .substringBefore("/")
                .substringBefore(":")
            if (cleanHost.isBlank()) {
                return@withContext HappyEyeballsResult(host = host, port = port, errorMessage = "Host is required.")
            }

            val allAddresses = try {
                InetAddress.getAllByName(cleanHost).toList()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return@withContext HappyEyeballsResult(
                    host = cleanHost,
                    port = port,
                    errorMessage = "DNS lookup failed: ${e.message}"
                )
            }

            val ipv4 = allAddresses.firstOrNull { it is Inet4Address }?.hostAddress
            val ipv6 = allAddresses.firstOrNull { it is Inet6Address }?.hostAddress

            val (v6Time, v4Time) = coroutineScope {
                val d6 = async { ipv6?.let { measureTcpConnect(it, port) } }
                val d4 = async { ipv4?.let { measureTcpConnect(it, port) } }
                d6.await() to d4.await()
            }

            val winner = when {
                v6Time != null && v4Time != null -> if (v6Time <= v4Time) "IPv6 (RFC 8305 Preferred)" else "IPv4"
                v6Time != null -> "IPv6 Only"
                v4Time != null -> "IPv4 Only"
                else -> "Unreachable"
            }

            var alpnProto = "Not negotiated"
            var tlsProto = "N/A"
            var cipher = "N/A"
            var altSvcHeader = ""
            var supportsH2 = false
            var supportsH3 = false
            var tlsErr: String? = null

            // Inspect ALPN (h2 vs http/1.1) with SSLSocket
            var sslSock: SSLSocket? = null
            try {
                val targetConnectIp = if (v6Time != null && (v4Time == null || v6Time <= v4Time)) ipv6 else ipv4
                val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
                sslSock = factory.createSocket() as SSLSocket
                sslSock.soTimeout = 4500
                val params = sslSock.sslParameters
                params.serverNames = listOf(SNIHostName(cleanHost))
                params.applicationProtocols = arrayOf("h2", "http/1.1")
                sslSock.sslParameters = params
                sslSock.connect(InetSocketAddress(targetConnectIp ?: cleanHost, port), 4500)
                sslSock.startHandshake()

                val negotiated = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    runCatching { sslSock.applicationProtocol }.getOrDefault("")
                } else {
                    ""
                }
                alpnProto = negotiated.ifBlank { "http/1.1 (fallback)" }
                supportsH2 = alpnProto.equals("h2", ignoreCase = true)
                tlsProto = sslSock.session?.protocol ?: "TLS"
                cipher = sslSock.session?.cipherSuite ?: "Unknown"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                tlsErr = e.message
            } finally {
                runCatching { sslSock?.close() }
            }

            // Inspect HTTP Alt-Svc header for HTTP/3 / QUIC (h3) advertisement
            runCatching {
                val req = Request.Builder()
                    .url("https://$cleanHost${if (port != 443) ":$port" else ""}/")
                    .head()
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    altSvcHeader = resp.header("alt-svc").orEmpty()
                    if (altSvcHeader.contains("h3", ignoreCase = true) ||
                        altSvcHeader.contains("quic", ignoreCase = true)
                    ) {
                        supportsH3 = true
                    }
                }
            }

            HappyEyeballsResult(
                host = cleanHost,
                port = port,
                ipv4Address = ipv4,
                ipv6Address = ipv6,
                ipv4ConnectMs = v4Time,
                ipv6ConnectMs = v6Time,
                winnerFamily = winner,
                alpnProtocol = alpnProto,
                tlsProtocol = tlsProto,
                cipherSuite = cipher,
                altSvcHeader = altSvcHeader,
                supportsHttp2 = supportsH2,
                supportsHttp3Quic = supportsH3,
                errorMessage = if (v4Time == null && v6Time == null) tlsErr ?: "TCP/$port unreachable on both IPv4 & IPv6" else null
            )
        }

    private fun measureTcpConnect(ip: String, port: Int): Long? {
        val t0 = System.currentTimeMillis()
        return try {
            Socket().use { sock ->
                sock.connect(InetSocketAddress(ip, port), 3000)
                (System.currentTimeMillis() - t0).coerceAtLeast(1L)
            }
        } catch (_: Exception) {
            null
        }
    }
}
