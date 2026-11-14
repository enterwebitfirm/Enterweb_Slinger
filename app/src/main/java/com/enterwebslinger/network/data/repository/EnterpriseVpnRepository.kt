// === FILE: app/src/main/java/com/enterwebslinger/network/data/repository/EnterpriseVpnRepository.kt ===
package com.enterwebslinger.network.data.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.net.ConnectivityManager
import com.enterwebslinger.network.core.util.NetworkByteUtils
import com.enterwebslinger.network.data.protocol.IkePacketCodec
import com.enterwebslinger.network.data.protocol.WireGuardCrypto
import com.enterwebslinger.network.domain.model.AllowedIpsCalculation
import com.enterwebslinger.network.domain.model.EnterpriseSslVpnVendor
import com.enterwebslinger.network.domain.model.IkeProbeResult
import com.enterwebslinger.network.domain.model.InstalledAppEntry
import com.enterwebslinger.network.domain.model.SslVpnProbeResult
import com.enterwebslinger.network.domain.model.TunnelEncapsulationType
import com.enterwebslinger.network.domain.model.TunnelMssCalculation
import com.enterwebslinger.network.domain.model.VpnAuditReport
import com.enterwebslinger.network.domain.model.VpnProfile
import com.enterwebslinger.network.domain.model.VpnStatus
import com.enterwebslinger.network.domain.model.WireGuardConfig
import com.enterwebslinger.network.domain.model.WireGuardKeyPair
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStreamReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

data class ImportedPkcs12Bundle(
    val alias: String,
    val subjectDn: String,
    val issuerDn: String,
    val userCertPem: String,
    val userKeyPem: String,
    val caCertPem: String
)

/**
 * Pure-Kotlin / JVM Enterprise VPN Client & Site-to-Site IPsec Diagnostic Engine:
 * - WireGuard Curve25519 Keypair Generator, .conf Generator, ZXing QR Writer/Decoder, AllowedIPs Calculator
 * - OpenVPN & IKEv2 PKCS#12 (.p12/.pfx) X.509 Certificate Importer
 * - Cisco AnyConnect / FortiClient / SSTP / L2TP Handshake & Portal Probes
 * - IKEv2 SA_INIT (UDP 500 & NAT-T 4500) Probe & Notify Payload Analyzer
 * - Tunnel Encapsulation Overhead & TCP MSS Clamping Calculator
 * - Deep DNS/IPv6/Split-Tunnel Leak & Device Routing Table Auditor + Per-App Bypass List
 */
class EnterpriseVpnRepository(
    private val context: Context?,
    private val vpnRepository: VpnRepository
) {
    private val random = SecureRandom()

    // -------------------------------------------------------------------------
    // WireGuard Keygen, Conf Builder, QR Writer/Decoder & AllowedIPs Calculator
    // -------------------------------------------------------------------------

    fun generateWireGuardKeyPair(): WireGuardKeyPair {
        val priv = WireGuardCrypto.generatePrivateKey(random)
        val pub = WireGuardCrypto.publicKeyFromPrivate(priv)
        val psk = ByteArray(32).apply { random.nextBytes(this) }
        val enc = Base64.getEncoder()
        return WireGuardKeyPair(
            privateKeyBase64 = enc.encodeToString(priv),
            publicKeyBase64 = enc.encodeToString(pub),
            presharedKeyBase64 = enc.encodeToString(psk)
        )
    }

    fun buildWireGuardConfText(config: WireGuardConfig): String {
        return buildString {
            appendLine("[Interface]")
            appendLine("PrivateKey = ${config.privateKey}")
            if (config.interfaceAddresses.isNotEmpty()) {
                appendLine("Address = ${config.interfaceAddresses.joinToString(", ")}")
            }
            if (config.dnsServers.isNotEmpty()) {
                appendLine("DNS = ${config.dnsServers.joinToString(", ")}")
            }
            appendLine("MTU = ${config.mtu.coerceIn(1280, 1500)}")
            appendLine()
            appendLine("[Peer]")
            if (config.publicKey.isNotBlank()) {
                appendLine("PublicKey = ${config.publicKey}")
            }
            if (config.presharedKey.isNotBlank()) {
                appendLine("PresharedKey = ${config.presharedKey}")
            }
            if (config.peerEndpoint.isNotBlank()) {
                appendLine("Endpoint = ${config.peerEndpoint}")
            }
            val allowed = config.peerAllowedIps.ifEmpty { listOf("0.0.0.0/0") }
            appendLine("AllowedIPs = ${allowed.joinToString(", ")}")
            if (config.persistentKeepalive > 0) {
                appendLine("PersistentKeepalive = ${config.persistentKeepalive}")
            }
        }.trim()
    }

    fun generateQrBitmap(content: String, sizePx: Int = 512): Result<Bitmap> = runCatching {
        require(content.isNotBlank()) { "QR content cannot be empty" }
        val hints = mapOf(EncodeHintType.CHARACTER_SET to "UTF-8")
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val w = matrix.width
        val h = matrix.height
        val pixels = IntArray(w * h) { idx ->
            val x = idx % w
            val y = idx / w
            if (matrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, w, 0, 0, w, h)
        }
    }

    fun decodeQrFromBitmap(bitmap: Bitmap): Result<String> = runCatching {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val source = RGBLuminanceSource(w, h, pixels)
        val hints = mapOf(
            DecodeHintType.TRY_HARDER to true,
            DecodeHintType.CHARACTER_SET to "UTF-8"
        )
        val reader = MultiFormatReader()
        runCatching {
            reader.decode(BinaryBitmap(HybridBinarizer(source)), hints).text
        }.getOrElse {
            reader.decode(BinaryBitmap(GlobalHistogramBinarizer(source)), hints).text
        }
    }

    fun calculateAllowedIps(includedCsv: String, excludedCsv: String): AllowedIpsCalculation {
        val inc = includedCsv.split(",", ";", "\n").map { it.trim() }.filter { it.isNotBlank() }
        val exc = excludedCsv.split(",", ";", "\n").map { it.trim() }.filter { it.isNotBlank() }
        return IkePacketCodec.calculateAllowedIps(inc, exc)
    }

    fun tuneWireGuardProfile(
        profile: VpnProfile,
        keepaliveSeconds: Int,
        allowedIpsCsv: String? = null
    ): VpnProfile {
        val wg = profile.wireGuardConfig ?: return profile
        val updatedAllowed = if (!allowedIpsCsv.isNullOrBlank()) {
            allowedIpsCsv.split(",").map { it.trim() }.filter { it.isNotBlank() }
        } else {
            wg.peerAllowedIps
        }
        val updated = profile.copy(
            wireGuardConfig = wg.copy(
                persistentKeepalive = keepaliveSeconds.coerceIn(0, 120),
                peerAllowedIps = updatedAllowed
            )
        )
        vpnRepository.updateProfile(updated)
        return updated
    }

    // -------------------------------------------------------------------------
    // PKCS#12 (.p12 / .pfx) X.509 Importer (Pure JVM)
    // -------------------------------------------------------------------------

    fun importPkcs12Certificate(p12Bytes: ByteArray, password: String): Result<ImportedPkcs12Bundle> = runCatching {
        val ks = KeyStore.getInstance("PKCS12")
        val pwdChars = password.toCharArray()
        ks.load(ByteArrayInputStream(p12Bytes), pwdChars)

        val aliases = ks.aliases()
        var chosenAlias = ""
        while (aliases.hasMoreElements()) {
            val a = aliases.nextElement()
            if (ks.isKeyEntry(a)) {
                chosenAlias = a
                break
            }
        }
        require(chosenAlias.isNotBlank()) { "No private key entry found inside PKCS#12 archive" }

        val privateKey = ks.getKey(chosenAlias, pwdChars)
            ?: throw IllegalStateException("Unable to unlock private key for alias '$chosenAlias'")
        val chain = ks.getCertificateChain(chosenAlias)
        val userCert = (chain?.firstOrNull() ?: ks.getCertificate(chosenAlias)) as? X509Certificate
            ?: throw IllegalStateException("Missing X.509 certificate in PKCS#12 archive")
        val caCert = chain?.drop(1)?.lastOrNull() as? X509Certificate

        ImportedPkcs12Bundle(
            alias = chosenAlias,
            subjectDn = userCert.subjectX500Principal.name,
            issuerDn = userCert.issuerX500Principal.name,
            userCertPem = encodePem("CERTIFICATE", userCert.encoded),
            userKeyPem = encodePem("PRIVATE KEY", privateKey.encoded),
            caCertPem = caCert?.let { encodePem("CERTIFICATE", it.encoded) }.orEmpty()
        )
    }

    private fun encodePem(type: String, derBytes: ByteArray): String {
        val b64 = Base64.getMimeEncoder(64, "\n".toByteArray(Charsets.US_ASCII)).encodeToString(derBytes)
        return "-----BEGIN $type-----\n$b64\n-----END $type-----"
    }

    // -------------------------------------------------------------------------
    // Enterprise SSL-VPN & L2TP Handshake Probes
    // -------------------------------------------------------------------------

    suspend fun probeEnterpriseSslVpn(
        vendor: EnterpriseSslVpnVendor,
        host: String,
        port: Int = vendor.defaultPort,
        allowSelfSigned: Boolean = true
    ): SslVpnProbeResult = withContext(Dispatchers.IO) {
        val cleanHost = host.trim()
        if (cleanHost.isBlank()) {
            return@withContext SslVpnProbeResult(
                vendor = vendor,
                host = cleanHost,
                port = port,
                isReachable = false,
                errorMessage = "Target portal host or IP is required."
            )
        }

        if (vendor == EnterpriseSslVpnVendor.L2TP_IPSEC) {
            return@withContext probeL2tpIpsecGateway(cleanHost, port)
        }

        val t0 = System.currentTimeMillis()
        var sslSocket: SSLSocket? = null
        try {
            val factory = buildSocketFactory(allowSelfSigned)
            sslSocket = (factory.createSocket() as SSLSocket).apply {
                soTimeout = 4500
                runCatching {
                    val params = sslParameters
                    params.serverNames = listOf(SNIHostName(cleanHost))
                    sslParameters = params
                }
                connect(InetSocketAddress(cleanHost, port), 4500)
                startHandshake()
            }
            val tlsVer = sslSocket.session?.protocol ?: "TLS"
            val cipher = sslSocket.session?.cipherSuite ?: "Unknown"

            val requestPayload = when (vendor) {
                EnterpriseSslVpnVendor.CISCO_ANYCONNECT -> {
                    val xmlBody = """<?xml version="1.0" encoding="UTF-8"?>
<config-auth client="vpn" type="init" aggregate-auth-version="2">
  <version who="vpn">5.0</version>
  <device-id>android</device-id>
  <group-access>https://$cleanHost:$port</group-access>
</config-auth>"""
                    "POST / HTTP/1.1\r\n" +
                        "Host: $cleanHost:$port\r\n" +
                        "User-Agent: AnyConnect Android 5.0\r\n" +
                        "X-Aggregate-Auth: 1\r\n" +
                        "X-Transcend-Version: 1\r\n" +
                        "Content-Type: application/x-www-form-urlencoded\r\n" +
                        "Content-Length: ${xmlBody.toByteArray(Charsets.UTF_8).size}\r\n" +
                        "Connection: close\r\n\r\n$xmlBody"
                }
                EnterpriseSslVpnVendor.FORTICLIENT_SSLVPN -> {
                    "GET /remote/info HTTP/1.1\r\n" +
                        "Host: $cleanHost:$port\r\n" +
                        "User-Agent: FortiSSLVPNClient/7.2\r\n" +
                        "Connection: close\r\n\r\n"
                }
                EnterpriseSslVpnVendor.MICROSOFT_SSTP -> {
                    "SSTP_DUPLEX_POST /sra_{BA195980-CD49-458b-9E23-C84EE0ADCD75}/ HTTP/1.1\r\n" +
                        "Host: $cleanHost:$port\r\n" +
                        "SSTPCORRELATIONID: {${UUID.randomUUID()}}\r\n" +
                        "Content-Length: 18446744073709551615\r\n\r\n"
                }
                EnterpriseSslVpnVendor.L2TP_IPSEC -> ""
            }

            sslSocket.outputStream.write(requestPayload.toByteArray(Charsets.US_ASCII))
            sslSocket.outputStream.flush()

            val reader = BufferedReader(InputStreamReader(sslSocket.inputStream, Charsets.UTF_8))
            val statusLine = reader.readLine()?.trim().orEmpty()
            val headers = mutableListOf<String>()
            var line: String?
            while (reader.readLine().also { line = it } != null && headers.size < 32) {
                val h = line?.trim().orEmpty()
                if (h.isEmpty()) break
                headers.add(h)
            }
            val bodySb = StringBuilder()
            if (vendor != EnterpriseSslVpnVendor.MICROSOFT_SSTP) {
                val cBuf = CharArray(2048)
                val readChars = runCatching { reader.read(cBuf) }.getOrDefault(0)
                if (readChars > 0) bodySb.append(cBuf, 0, readChars)
            }
            val bodyText = bodySb.toString()
            val rtt = (System.currentTimeMillis() - t0).coerceAtLeast(1L)

            val authGroups = Regex("""<option[^>]*>([^<]+)</option>""", RegexOption.IGNORE_CASE)
                .findAll(bodyText).map { it.groupValues[1].trim() }.distinct().toList()
            val cstpHdr = headers.firstOrNull { it.contains("CSTP", true) || it.contains("DTLS", true) }
                ?: headers.firstOrNull { it.startsWith("Server:", true) || it.startsWith("Set-Cookie:", true) }
                ?: ""
            val dtlsReady = headers.any { it.contains("DTLS", true) } ||
                bodyText.contains("dtls", true) ||
                statusLine.contains("200")

            SslVpnProbeResult(
                vendor = vendor,
                host = cleanHost,
                port = port,
                isReachable = true,
                tlsVersion = tlsVer,
                cipherSuite = cipher,
                httpStatusOrCode = statusLine.ifBlank { "TLS Handshake OK" },
                portalBannerOrCstp = cstpHdr.ifBlank { bodyText.lineSequence().firstOrNull()?.take(100).orEmpty() },
                dtlsOrNatTReady = dtlsReady,
                authGroups = authGroups,
                rttMs = rtt
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SslVpnProbeResult(
                vendor = vendor,
                host = cleanHost,
                port = port,
                isReachable = false,
                rttMs = (System.currentTimeMillis() - t0).coerceAtLeast(1L),
                errorMessage = e.message ?: "TLS/SSL-VPN handshake failed"
            )
        } finally {
            NetworkByteUtils.safeClose(sslSocket)
        }
    }

    private fun probeL2tpIpsecGateway(host: String, port: Int): SslVpnProbeResult {
        val t0 = System.currentTimeMillis()
        var sock: DatagramSocket? = null
        return try {
            val addr = InetAddress.getByName(host)
            sock = DatagramSocket().apply { soTimeout = 2200 }
            // L2TP RFC 2661 SCCRQ (Start-Control-Connection-Request) minimal control header:
            // Flags=0xC802 (Type=Control, Length bit, Seq bit, Version=2), Len=12, TunnelID=0, SessionID=0, Ns=0, Nr=0
            val sccrq = byteArrayOf(
                0xC8.toByte(), 0x02, 0x00, 0x0C,
                0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00
            )
            sock.send(DatagramPacket(sccrq, sccrq.size, addr, port))
            val rx = ByteArray(512)
            val pkt = DatagramPacket(rx, rx.size)
            var banner = "Sent L2TP SCCRQ to $host:$port"
            var replied = false
            try {
                sock.receive(pkt)
                replied = true
                banner = "L2TP peer responded (${pkt.length} B): ${NetworkByteUtils.bytesToHex(rx.copyOf(pkt.length.coerceAtMost(16)), " ")}"
            } catch (_: SocketTimeoutException) {
                banner = "No unauthenticated L2TP UDP/$port reply (expected if IPsec transport encryption enforces IKE/ESP first)"
            }
            val ikeProbe = IkePacketCodec.buildIkev2SaInitProbe(useNatT4500 = true)
            sock.send(DatagramPacket(ikeProbe, ikeProbe.size, addr, 4500))
            var natTReplied = false
            try {
                sock.receive(pkt)
                natTReplied = true
            } catch (_: SocketTimeoutException) {}

            SslVpnProbeResult(
                vendor = EnterpriseSslVpnVendor.L2TP_IPSEC,
                host = host,
                port = port,
                isReachable = replied || natTReplied,
                httpStatusOrCode = if (natTReplied) "IKE NAT-T 4500 Active" else if (replied) "L2TP UDP/1701 Active" else "Timed out",
                portalBannerOrCstp = banner,
                dtlsOrNatTReady = natTReplied,
                rttMs = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SslVpnProbeResult(
                vendor = EnterpriseSslVpnVendor.L2TP_IPSEC,
                host = host,
                port = port,
                isReachable = false,
                rttMs = (System.currentTimeMillis() - t0).coerceAtLeast(1L),
                errorMessage = e.message
            )
        } finally {
            NetworkByteUtils.safeClose(sock)
        }
    }

    private fun buildSocketFactory(allowSelfSigned: Boolean): SSLSocketFactory {
        if (!allowSelfSigned) return SSLSocketFactory.getDefault() as SSLSocketFactory
        val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        })
        return SSLContext.getInstance("TLS").apply {
            init(null, trustAll, SecureRandom())
        }.socketFactory
    }

    // -------------------------------------------------------------------------
    // Site-to-Site IKEv2 SA_INIT Probe & Tunnel Overhead/MSS Calculator
    // -------------------------------------------------------------------------

    suspend fun probeIkev2Peer(host: String, useNatT4500: Boolean): IkeProbeResult =
        withContext(Dispatchers.IO) {
            val cleanHost = host.trim()
            val port = if (useNatT4500) 4500 else 500
            if (cleanHost.isBlank()) {
                return@withContext IkeProbeResult(
                    host = cleanHost,
                    port = port,
                    useNatT4500 = useNatT4500,
                    ikeVersionLabel = "N/A",
                    peerResponded = false,
                    initiatorSpiHex = "",
                    responderSpiHex = "",
                    diagnosisSummary = "Remote VPN peer IP or hostname is required."
                )
            }

            val iSpi = ByteArray(8).apply { random.nextBytes(this) }
            val iSpiHex = NetworkByteUtils.bytesToHex(iSpi, "")
            val probeBytes = IkePacketCodec.buildIkev2SaInitProbe(useNatT4500 = useNatT4500, initiatorSpi = iSpi)

            var sock: DatagramSocket? = null
            val t0 = System.currentTimeMillis()
            try {
                val addr = InetAddress.getByName(cleanHost)
                sock = DatagramSocket().apply { soTimeout = 3200 }
                sock.send(DatagramPacket(probeBytes, probeBytes.size, addr, port))

                val rx = ByteArray(2048)
                val pkt = DatagramPacket(rx, rx.size)
                sock.receive(pkt)
                val rtt = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
                IkePacketCodec.parseIkev2Response(
                    raw = pkt.data,
                    length = pkt.length,
                    host = cleanHost,
                    port = port,
                    useNatT4500 = useNatT4500,
                    expectedISpiHex = iSpiHex,
                    rttMs = rtt
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: SocketTimeoutException) {
                IkeProbeResult(
                    host = cleanHost,
                    port = port,
                    useNatT4500 = useNatT4500,
                    ikeVersionLabel = "IKEv2",
                    peerResponded = false,
                    initiatorSpiHex = iSpiHex,
                    responderSpiHex = "0000000000000000",
                    rttMs = (System.currentTimeMillis() - t0).coerceAtLeast(1L),
                    diagnosisSummary = "No IKE_SA_INIT reply from $cleanHost:$port (check firewall UDP $port, IPSec enabled, or peer reachability)."
                )
            } catch (e: Exception) {
                IkeProbeResult(
                    host = cleanHost,
                    port = port,
                    useNatT4500 = useNatT4500,
                    ikeVersionLabel = "IKEv2",
                    peerResponded = false,
                    initiatorSpiHex = iSpiHex,
                    responderSpiHex = "",
                    rttMs = (System.currentTimeMillis() - t0).coerceAtLeast(1L),
                    diagnosisSummary = "IKE probe failed: ${e.message}"
                )
            } finally {
                NetworkByteUtils.safeClose(sock)
            }
        }

    fun calculateTunnelOverheadAndMss(
        encapsulation: TunnelEncapsulationType,
        physicalMtu: Int = 1500,
        isIpv6Outer: Boolean = false,
        natTEnabled: Boolean = true
    ): TunnelMssCalculation = IkePacketCodec.calculateTunnelOverheadAndMss(
        encapsulation = encapsulation,
        physicalMtu = physicalMtu,
        isIpv6Outer = isIpv6Outer,
        natTEnabled = natTEnabled
    )

    // -------------------------------------------------------------------------
    // VPN Leak, Routing Table & Per-App Exclusion Auditor
    // -------------------------------------------------------------------------

    suspend fun runFullVpnLeakAndRouteAudit(selectedProfile: VpnProfile?): VpnAuditReport =
        withContext(Dispatchers.IO) {
            val dnsResult = vpnRepository.runDnsLeakTest()
            val tel = vpnRepository.telemetry.value

            // Enumerate non-loopback global IPv6 addresses on device interfaces
            val v6Addrs = mutableListOf<String>()
            runCatching {
                val ifaces = NetworkInterface.getNetworkInterfaces()
                while (ifaces.hasMoreElements()) {
                    val nif = ifaces.nextElement()
                    if (!nif.isUp || nif.isLoopback) continue
                    val addrs = nif.inetAddresses
                    while (addrs.hasMoreElements()) {
                        val addr = addrs.nextElement()
                        if (addr is Inet6Address && !addr.isLinkLocalAddress && !addr.isLoopbackAddress) {
                            v6Addrs.add("${nif.name}: ${addr.hostAddress?.substringBefore("%")}")
                        }
                    }
                }
            }

            // Read Android ConnectivityManager LinkProperties routes
            val routes = mutableListOf<String>()
            val cm = context?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (cm != null) {
                val activeNet = cm.activeNetwork
                val lp = cm.getLinkProperties(activeNet)
                lp?.routes?.forEach { r ->
                    routes.add("${r.destination} via ${r.gateway?.hostAddress ?: "direct"} (${r.`interface` ?: "if"})")
                }
            }

            // Read kernel `ip route show table all` with `/proc/net/route` fallback
            val cmdRoutes = readKernelRoutes()
            routes.addAll(cmdRoutes.filter { it !in routes })

            val ipv6Routes = routes.filter { it.contains(":") }
            val tunnelIsIpv4Only = tel.status == VpnStatus.CONNECTED && !tel.assignedIp.contains(":")
            val ipv6LeakRisk = tunnelIsIpv4Only && (v6Addrs.isNotEmpty() || ipv6Routes.any { it.startsWith("::/0") })

            val conflicts = mutableListOf<String>()
            val wgAllowed = selectedProfile?.wireGuardConfig?.peerAllowedIps.orEmpty()
            val ovpnRoutes = selectedProfile?.openVpnConfig?.routes.orEmpty()
            val allProfileRoutes = wgAllowed + ovpnRoutes
            if (allProfileRoutes.any { it.startsWith("192.168.") || it.startsWith("10.") }) {
                routes.filter { it.startsWith("192.168.") || it.startsWith("10.") }.forEach { localRoute ->
                    val prefix = localRoute.substringBefore(" ")
                    if (allProfileRoutes.any { it == prefix }) {
                        conflicts.add("Split-tunnel overlap on $prefix with active local interface route.")
                    }
                }
            }
            if (ipv6LeakRisk) {
                conflicts.add("IPv6 Leak Risk: Global IPv6 address/default route active outside IPv4 VPN tunnel.")
            }

            val summary = buildString {
                append(if (dnsResult.isLeakDetected) "DNS Leak/No Tunnel" else "DNS Contained")
                append(" | IPv6 Risk: ${if (ipv6LeakRisk) "HIGH (Global v6 outside tunnel)" else "Low/None"}")
                append(" | Routes: ${routes.size} | Split-Conflicts: ${conflicts.size}")
            }

            VpnAuditReport(
                activeVpnStatus = tel.status,
                assignedTunnelIp = tel.assignedIp,
                dnsLeakResult = dnsResult,
                ipv6AddressesOnDevice = v6Addrs,
                ipv6RoutesDetected = ipv6Routes,
                ipv6LeakRisk = ipv6LeakRisk,
                splitTunnelConflicts = conflicts,
                activeRoutes = routes.take(40),
                excludedPackageCount = selectedProfile?.splitTunnelExcludedApps?.size ?: 0,
                overallSummary = summary
            )
        }

    private fun readKernelRoutes(): List<String> {
        val outLines = mutableListOf<String>()
        var proc: Process? = null
        try {
            proc = ProcessBuilder("ip", "route", "show", "table", "all")
                .redirectErrorStream(true)
                .start()
            BufferedReader(InputStreamReader(proc.inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null && outLines.size < 35) {
                    val t = line?.trim().orEmpty()
                    if (t.isNotBlank() && !t.startsWith("Cannot", true) && !t.contains("Permission denied", true)) {
                        outLines.add(t)
                    }
                }
            }
            proc.waitFor(2, TimeUnit.SECONDS)
        } catch (_: Exception) {
        } finally {
            NetworkByteUtils.destroyProcess(proc)
        }

        if (outLines.isEmpty()) {
            runCatching {
                val procRoute = File("/proc/net/route")
                if (procRoute.exists() && procRoute.canRead()) {
                    procRoute.readLines().drop(1).forEach { line ->
                        val cols = line.trim().split(Regex("""\s+"""))
                        if (cols.size >= 8) {
                            val iface = cols[0]
                            val dest = hexLittleEndianToIp(cols[1])
                            val gw = hexLittleEndianToIp(cols[2])
                            val mask = hexLittleEndianToIp(cols[7])
                            outLines.add("$dest/$mask via $gw dev $iface")
                        }
                    }
                }
            }
        }
        return outLines
    }

    private fun hexLittleEndianToIp(hex: String): String {
        val v = hex.toLongOrNull(16) ?: return "0.0.0.0"
        val b0 = v and 0xFF
        val b1 = (v ushr 8) and 0xFF
        val b2 = (v ushr 16) and 0xFF
        val b3 = (v ushr 24) and 0xFF
        return "$b0.$b1.$b2.$b3"
    }

    suspend fun listInstalledApps(): List<InstalledAppEntry> = withContext(Dispatchers.IO) {
        val pm = context?.packageManager ?: return@withContext emptyList()
        runCatching {
            pm.getInstalledApplications(0)
                .filter { it.packageName != context.packageName }
                .map { app ->
                    val isSys = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    InstalledAppEntry(
                        packageName = app.packageName,
                        appName = pm.getApplicationLabel(app).toString(),
                        isSystemApp = isSys
                    )
                }
                .sortedWith(compareBy<InstalledAppEntry> { it.isSystemApp }.thenBy { it.appName.lowercase() })
        }.getOrDefault(emptyList())
    }
}
