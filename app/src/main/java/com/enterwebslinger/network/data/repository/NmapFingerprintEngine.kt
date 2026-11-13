// === FILE: app/src/main/java/com/enterwebslinger/network/data/repository/NmapFingerprintEngine.kt ===
package com.enterwebslinger.network.data.repository

import com.enterwebslinger.network.domain.model.HostFingerprintReport
import com.enterwebslinger.network.domain.model.OsHeuristicResult
import com.enterwebslinger.network.domain.model.ScanProfile
import com.enterwebslinger.network.domain.model.ServiceBanner
import com.enterwebslinger.network.domain.model.TlsCertificateInfo
import com.enterwebslinger.network.domain.model.TopologyGraphData
import com.enterwebslinger.network.domain.model.TopologyLink
import com.enterwebslinger.network.domain.model.TopologyNode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Locale
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.math.cos
import kotlin.math.sin

class NmapFingerprintEngine {

    val TOP_25_PORTS = listOf(
        21, 22, 23, 25, 53, 80, 110, 135, 139, 143,
        443, 445, 993, 995, 1433, 1521, 3306, 3389,
        5432, 5900, 6379, 8080, 8443, 8888, 9200
    )

    val TOP_100_PORTS = listOf(
        20, 21, 22, 23, 25, 53, 67, 68, 69, 80,
        110, 111, 119, 123, 135, 137, 138, 139, 143, 161,
        162, 179, 199, 389, 443, 445, 465, 500, 514, 515,
        520, 587, 631, 636, 873, 902, 993, 995, 1025, 1080,
        1194, 1433, 1434, 1521, 1723, 1883, 2049, 2181, 2222, 2375,
        3000, 3128, 3306, 3389, 4369, 5000, 5060, 5222, 5353, 5432,
        5672, 5900, 5985, 6000, 6379, 6443, 6667, 7001, 8000, 8008,
        8080, 8081, 8086, 8088, 8181, 8443, 8888, 9000, 9042, 9090,
        9092, 9100, 9200, 9300, 9418, 9999, 10000, 11211, 27017, 50051
    )

    suspend fun auditHost(
        targetHost: String,
        profile: ScanProfile,
        customPorts: List<Int> = emptyList(),
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): HostFingerprintReport {
        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            var isAlive = false
            var latencyMs = 0L

            try {
                val pingStart = System.currentTimeMillis()
                val addr = InetAddress.getByName(targetHost)
                isAlive = addr.isReachable(800)
                if (!isAlive) {
                    Socket().use { s ->
                        s.connect(InetSocketAddress(targetHost, 80), 400)
                        isAlive = true
                    }
                }
                latencyMs = (System.currentTimeMillis() - pingStart).coerceAtLeast(1L)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}

            val portsToScan = when (profile) {
                ScanProfile.QUICK_SCAN -> TOP_25_PORTS
                ScanProfile.INTENSE_SCAN -> TOP_100_PORTS
                ScanProfile.PING_SWEEP -> listOf(80, 443, 22)
                ScanProfile.CUSTOM_RANGE -> if (customPorts.isNotEmpty()) customPorts else TOP_25_PORTS
            }

            val openPorts = mutableListOf<Int>()
            val banners = mutableListOf<ServiceBanner>()
            val tlsCerts = mutableListOf<TlsCertificateInfo>()

            val semaphore = Semaphore(20)
            coroutineScope {
                val tasks = portsToScan.mapIndexed { index, port ->
                    async {
                        semaphore.withPermit {
                            val isOpen = probePort(targetHost, port, timeoutMs = 600)
                            if (isOpen) {
                                synchronized(openPorts) { openPorts.add(port) }
                                isAlive = true

                                val banner = grabServiceBanner(targetHost, port)
                                if (banner != null) {
                                    synchronized(banners) { banners.add(banner) }
                                }

                                if (port in setOf(443, 465, 636, 990, 993, 995, 5061, 5986, 6443, 8081, 8443, 8883, 10000)) {
                                    val cert = inspectTlsCertificate(targetHost, port)
                                    if (cert != null) {
                                        synchronized(tlsCerts) { tlsCerts.add(cert) }
                                    }
                                }
                            }
                            onProgress(index + 1, portsToScan.size)
                        }
                    }
                }
                tasks.awaitAll()
            }

            openPorts.sort()

            val osResult = inferOs(openPorts, banners)
            val duration = System.currentTimeMillis() - startTime

            HostFingerprintReport(
                host = targetHost,
                hostname = try { InetAddress.getByName(targetHost).canonicalHostName } catch (_: Exception) { "" },
                isAlive = isAlive || openPorts.isNotEmpty(),
                latencyMs = latencyMs,
                openPorts = openPorts,
                banners = banners.sortedBy { it.port },
                tlsCertificates = tlsCerts,
                osEstimate = osResult,
                scanDurationMs = duration
            )
        }
    }

    fun parseCustomPorts(spec: String, maxPorts: Int = 1024): List<Int> {
        val out = LinkedHashSet<Int>()
        spec.split(",").forEach { token ->
            val trimmed = token.trim()
            if (trimmed.isEmpty()) return@forEach
            if (trimmed.contains("-")) {
                val parts = trimmed.split("-", limit = 2)
                val start = parts[0].trim().toIntOrNull()?.coerceIn(1, 65535)
                val end = parts[1].trim().toIntOrNull()?.coerceIn(1, 65535)
                if (start != null && end != null) {
                    val lo = minOf(start, end)
                    val hi = maxOf(start, end)
                    for (p in lo..hi) {
                        out.add(p)
                        if (out.size >= maxPorts) return out.toList()
                    }
                }
            } else {
                trimmed.toIntOrNull()?.takeIf { it in 1..65535 }?.let {
                    out.add(it)
                    if (out.size >= maxPorts) return out.toList()
                }
            }
        }
        return out.toList()
    }

    private fun probePort(host: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            Socket().use { s ->
                s.connect(InetSocketAddress(host, port), timeoutMs)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun grabServiceBanner(host: String, port: Int): ServiceBanner? {
        return try {
            Socket().use { socket ->
                socket.soTimeout = 1200
                socket.connect(InetSocketAddress(host, port), 800)

                when (port) {
                    22 -> {
                        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                        val banner = reader.readLine() ?: ""
                        ServiceBanner(
                            port = port,
                            serviceName = "SSH",
                            banner = banner,
                            product = if (banner.contains("OpenSSH")) "OpenSSH" else "SSH",
                            version = banner.substringAfter("OpenSSH_", "").substringBefore(" ")
                        )
                    }
                    21 -> {
                        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                        val banner = reader.readLine() ?: ""
                        ServiceBanner(port = port, serviceName = "FTP", banner = banner, product = "FTP Server")
                    }
                    25, 587 -> {
                        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                        val banner = reader.readLine() ?: ""
                        ServiceBanner(port = port, serviceName = "SMTP", banner = banner, product = "Mail Server (ESMTP)")
                    }
                    80, 8080, 8000 -> {
                        val writer = OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8)
                        writer.write("HEAD / HTTP/1.1\r\nHost: $host\r\nConnection: close\r\nUser-Agent: EnterwebSlinger/2.0\r\n\r\n")
                        writer.flush()

                        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                        var line = reader.readLine()
                        var serverHeader = ""
                        while (line != null && line.isNotBlank()) {
                            if (line.startsWith("Server:", ignoreCase = true)) {
                                serverHeader = line.substringAfter(":").trim()
                            }
                            line = reader.readLine()
                        }
                        ServiceBanner(
                            port = port,
                            serviceName = "HTTP",
                            banner = if (serverHeader.isNotBlank()) "Server: $serverHeader" else "HTTP/1.1 200 OK",
                            product = serverHeader
                        )
                    }
                    6379 -> {
                        val writer = OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8)
                        writer.write("PING\r\n")
                        writer.flush()
                        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                        val resp = reader.readLine() ?: ""
                        ServiceBanner(port = port, serviceName = "Redis", banner = resp, product = "Redis In-Memory Store")
                    }
                    else -> ServiceBanner(
                        port = port,
                        serviceName = getCommonServiceName(port),
                        banner = "Port $port OPEN",
                        product = getCommonServiceName(port)
                    )
                }
            }
        } catch (_: Exception) {
            ServiceBanner(
                port = port,
                serviceName = getCommonServiceName(port),
                banner = "Port $port OPEN",
                product = getCommonServiceName(port)
            )
        }
    }

    private fun inspectTlsCertificate(host: String, port: Int): TlsCertificateInfo? {
        return try {
            val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustAll, SecureRandom())

            (sslContext.socketFactory.createSocket() as SSLSocket).use { sslSocket ->
                sslSocket.soTimeout = 1500
                sslSocket.connect(InetSocketAddress(host, port), 1000)
                sslSocket.startHandshake()

                val session = sslSocket.session
                val certs = session.peerCertificates
                val x509 = certs.firstOrNull() as? X509Certificate

                if (x509 != null) {
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    val sans = try {
                        x509.subjectAlternativeNames?.mapNotNull { it[1]?.toString() } ?: emptyList()
                    } catch (_: Exception) {
                        emptyList()
                    }

                    TlsCertificateInfo(
                        port = port,
                        subjectDn = x509.subjectX500Principal.name,
                        issuerDn = x509.issuerX500Principal.name,
                        serialNumber = x509.serialNumber.toString(16).uppercase(),
                        validFrom = dateFormat.format(x509.notBefore),
                        validTo = dateFormat.format(x509.notAfter),
                        subjectAltNames = sans,
                        tlsVersion = session.protocol,
                        cipherSuite = session.cipherSuite,
                        keyAlgorithm = x509.publicKey.algorithm
                    )
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun inferOs(openPorts: List<Int>, banners: List<ServiceBanner>): OsHeuristicResult {
        var predictedOs = "Linux / Embedded Network Device"
        var confidence = 75
        var ttl = 64
        var winSize = 29200
        val reasons = mutableListOf<String>()

        val bannerTexts = banners.joinToString(" ") { "${it.banner} ${it.product}" }

        if (bannerTexts.contains("Ubuntu", ignoreCase = true) || bannerTexts.contains("Debian", ignoreCase = true)) {
            predictedOs = "Linux (Ubuntu / Debian)"
            confidence = 94
            reasons.add("SSH banner identifies Linux distribution package")
            ttl = 64
        } else if (openPorts.contains(135) || openPorts.contains(445) || openPorts.contains(3389)) {
            predictedOs = "Microsoft Windows"
            confidence = 90
            reasons.add("SMB (445), MSRPC (135), or RDP (3389) open ports match Windows signature")
            ttl = 128
            winSize = 64240
        } else if (openPorts.contains(22) && openPorts.contains(80) && !openPorts.contains(445)) {
            predictedOs = "Linux / Embedded Appliance"
            confidence = 82
            reasons.add("OpenSSH and HTTP management footprint")
            ttl = 64
            winSize = 14600
        } else if (openPorts.contains(161) || openPorts.contains(179)) {
            predictedOs = "Cisco IOS / Junos Network OS"
            confidence = 85
            reasons.add("SNMP (161) and BGP (179) management profile detected")
            ttl = 255
            winSize = 4128
        } else {
            reasons.add("General IP stack behavior and open port footprint")
        }

        return OsHeuristicResult(
            predictedOs = predictedOs,
            confidence = confidence,
            initialTtlEstimate = ttl,
            tcpWindowSize = winSize,
            rationale = reasons.joinToString("; ")
        )
    }

    private fun getCommonServiceName(port: Int): String = when (port) {
        21 -> "FTP"
        22 -> "SSH"
        23 -> "Telnet"
        25 -> "SMTP"
        53 -> "DNS"
        80 -> "HTTP"
        110 -> "POP3"
        135 -> "MSRPC"
        139 -> "NetBIOS"
        143 -> "IMAP"
        161 -> "SNMP"
        389 -> "LDAP"
        443 -> "HTTPS"
        445 -> "SMB"
        993 -> "IMAPS"
        995 -> "POP3S"
        1433 -> "MS-SQL"
        1521 -> "Oracle-DB"
        3306 -> "MySQL"
        3389 -> "RDP"
        5432 -> "PostgreSQL"
        5900 -> "VNC"
        6379 -> "Redis"
        8080 -> "HTTP-Proxy"
        8443 -> "HTTPS-Alt"
        else -> "Service-$port"
    }

    fun buildTopologyGraph(
        gatewayIp: String,
        discoveredHosts: List<HostFingerprintReport>,
        localIp: String = ""
    ): TopologyGraphData {
        if (discoveredHosts.isEmpty() && (gatewayIp.isBlank() || gatewayIp == "Unavailable")) {
            return TopologyGraphData()
        }

        val nodes = mutableListOf<TopologyNode>()
        val links = mutableListOf<TopologyLink>()

        val hasGateway = gatewayIp.isNotBlank() && gatewayIp != "Unavailable"
        val gwPortCount = discoveredHosts.firstOrNull { it.host == gatewayIp }?.openPorts?.size ?: 0
        val hubId = if (hasGateway) "GW-$gatewayIp" else "LOCAL-${localIp.ifBlank { "node" }}"
        val hubLabel = if (hasGateway) "Gateway ($gatewayIp)" else "Local Subnet"

        val gwNode = TopologyNode(
            id = hubId,
            label = hubLabel,
            ip = gatewayIp,
            isGateway = hasGateway,
            isLocalDevice = !hasGateway,
            openPortCount = gwPortCount,
            osFamily = if (hasGateway) "Router / Gateway" else "Local",
            latencyMs = 0L,
            xRatio = 0.5f,
            yRatio = 0.5f
        )
        nodes.add(gwNode)

        val peerHosts = discoveredHosts.filter { it.host != gatewayIp }
        val count = peerHosts.size.coerceAtLeast(1)
        val radius = 0.35f

        peerHosts.forEachIndexed { i, host ->
            val angle = (2.0 * Math.PI * i / count) - (Math.PI / 2.0)
            val x = (0.5f + (radius * cos(angle))).toFloat()
            val y = (0.5f + (radius * sin(angle))).toFloat()

            val isLocal = localIp.isNotBlank() && host.host == localIp
            val node = TopologyNode(
                id = host.host,
                label = if (host.hostname.isNotBlank()) host.hostname else host.host,
                ip = host.host,
                isGateway = false,
                isLocalDevice = isLocal,
                openPortCount = host.openPorts.size,
                osFamily = host.osEstimate?.predictedOs?.substringBefore(" ") ?: "Host",
                latencyMs = host.latencyMs,
                xRatio = x,
                yRatio = y
            )
            nodes.add(node)
            links.add(
                TopologyLink(
                    sourceId = gwNode.id,
                    targetId = node.id,
                    latencyMs = host.latencyMs
                )
            )
        }

        return TopologyGraphData(nodes = nodes, links = links)
    }
}
