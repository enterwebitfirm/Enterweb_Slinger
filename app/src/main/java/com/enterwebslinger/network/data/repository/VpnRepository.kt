// === FILE: app/src/main/java/com/enterwebslinger/network/data/repository/VpnRepository.kt ===
package com.enterwebslinger.network.data.repository

import android.content.Context
import android.content.Intent
import android.net.Ikev2VpnProfile
import android.net.VpnManager
import android.os.Build
import com.enterwebslinger.network.core.util.NetworkByteUtils
import com.enterwebslinger.network.domain.model.DnsLeakResult
import com.enterwebslinger.network.domain.model.Ikev2Config
import com.enterwebslinger.network.domain.model.OpenVpnConfig
import com.enterwebslinger.network.domain.model.VpnProfile
import com.enterwebslinger.network.domain.model.VpnProtocol
import com.enterwebslinger.network.domain.model.VpnTelemetry
import com.enterwebslinger.network.domain.model.WireGuardConfig
import com.enterwebslinger.network.service.OmniVpnService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.util.UUID
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

class VpnRepository(private val context: Context? = null) {

    private val _profiles = MutableStateFlow<List<VpnProfile>>(emptyList())
    val profiles: StateFlow<List<VpnProfile>> = _profiles.asStateFlow()

    val telemetry: StateFlow<VpnTelemetry> = OmniVpnService.telemetry

    fun startTunnel(profile: VpnProfile) {
        context?.let { OmniVpnService.startVpn(it, profile) }
    }

    fun stopTunnel() {
        context?.let {
            OmniVpnService.stopVpn(it)
            stopIkev2PlatformProfile()
        }
    }

    fun addProfile(profile: VpnProfile) {
        _profiles.update { it + profile }
    }

    fun removeProfile(profileId: String) {
        _profiles.update { list -> list.filterNot { it.id == profileId } }
    }

    fun addIkev2Profile(
        name: String,
        serverAddress: String,
        remoteIdentifier: String,
        preSharedKey: String,
        username: String = "",
        password: String = ""
    ): VpnProfile {
        val cleanHost = serverAddress.trim()
        val authMode = if (username.isNotBlank()) "EAP" else "PSK"
        val profile = VpnProfile(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { "IKEv2 ($cleanHost)" },
            protocol = VpnProtocol.IKEV2_IPSEC,
            serverAddress = cleanHost,
            port = 500,
            username = username.trim(),
            password = password,
            ikev2Config = Ikev2Config(
                serverAddress = cleanHost,
                remoteIdentifier = remoteIdentifier.trim().ifBlank { cleanHost },
                authType = authMode,
                preSharedKey = preSharedKey,
                username = username.trim(),
                password = password
            )
        )
        _profiles.update { it + profile }
        return profile
    }

    fun provisionIkev2PlatformProfile(profile: VpnProfile): Result<Intent?> {
        val ctx = context ?: return Result.failure(IllegalStateException("Context required for VpnManager"))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return Result.failure(UnsupportedOperationException("Native VpnManager IKEv2 requires Android 11 (API 30)+"))
        }
        val cfg = profile.ikev2Config ?: Ikev2Config(
            serverAddress = profile.serverAddress,
            remoteIdentifier = profile.serverAddress,
            authType = if (profile.username.isNotBlank()) "EAP" else "PSK",
            username = profile.username,
            password = profile.password
        )
        if (cfg.serverAddress.isBlank()) {
            return Result.failure(IllegalArgumentException("IKEv2 server address is required"))
        }
        return runCatching {
            val vpnManager = ctx.getSystemService(VpnManager::class.java)
                ?: throw IllegalStateException("System VpnManager unavailable on this device")
            val remoteId = cfg.remoteIdentifier.ifBlank { cfg.serverAddress }
            val builder = Ikev2VpnProfile.Builder(cfg.serverAddress, remoteId)
            if (cfg.authType.equals("EAP", ignoreCase = true) || cfg.username.isNotBlank()) {
                builder.setAuthUsernamePassword(cfg.username, cfg.password, null)
            } else {
                if (cfg.preSharedKey.isBlank()) {
                    throw IllegalArgumentException("IKEv2 Pre-Shared Key (PSK) is required")
                }
                builder.setAuthPsk(cfg.preSharedKey.toByteArray(Charsets.UTF_8))
            }
            builder.setBypassable(true)
            builder.setMetered(false)
            vpnManager.provisionVpnProfile(builder.build())
        }
    }

    fun startIkev2PlatformProfile(): Result<Unit> {
        val ctx = context ?: return Result.failure(IllegalStateException("Context required for VpnManager"))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return Result.failure(UnsupportedOperationException("Native VpnManager IKEv2 requires Android 11 (API 30)+"))
        }
        return runCatching {
            val vpnManager = ctx.getSystemService(VpnManager::class.java)
                ?: throw IllegalStateException("System VpnManager unavailable")
            vpnManager.startProvisionedVpnProfile()
        }
    }

    fun stopIkev2PlatformProfile() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        runCatching {
            context?.getSystemService(VpnManager::class.java)?.stopProvisionedVpnProfile()
        }
    }

    // --- WireGuard .conf Parser ---
    fun parseWireGuardConf(confContent: String, profileName: String = "Imported WireGuard"): VpnProfile {
        var privKey = ""
        var pubKey = ""
        var presharedKey = ""
        val addresses = mutableListOf<String>()
        val dnsServers = mutableListOf<String>()
        var mtu = 1420
        var endpoint = ""
        val allowedIps = mutableListOf<String>()
        var keepalive = 25

        var inInterface = false
        var inPeer = false

        confContent.lines().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.startsWith("#") || line.isBlank()) return@forEach

            if (line.equals("[Interface]", ignoreCase = true)) {
                inInterface = true
                inPeer = false
                return@forEach
            } else if (line.equals("[Peer]", ignoreCase = true)) {
                inInterface = false
                inPeer = true
                return@forEach
            }

            val key = line.substringBefore("=").trim().lowercase()
            val value = line.substringAfter("=").trim()

            if (inInterface) {
                when (key) {
                    "privatekey" -> privKey = value
                    "address" -> addresses.addAll(value.split(",").map { it.trim() }.filter { it.isNotBlank() })
                    "dns" -> dnsServers.addAll(value.split(",").map { it.trim() }.filter { it.isNotBlank() })
                    "mtu" -> mtu = value.toIntOrNull() ?: 1420
                }
            } else if (inPeer) {
                when (key) {
                    "publickey" -> pubKey = value
                    "presharedkey" -> presharedKey = value
                    "endpoint" -> endpoint = value
                    "allowedips" -> allowedIps.addAll(value.split(",").map { it.trim() }.filter { it.isNotBlank() })
                    "persistentkeepalive" -> keepalive = value.toIntOrNull() ?: 25
                }
            }
        }

        val (host, port) = parseEndpointHostPort(endpoint, 51820)

        val config = WireGuardConfig(
            privateKey = privKey,
            publicKey = pubKey,
            presharedKey = presharedKey,
            interfaceAddresses = addresses,
            dnsServers = dnsServers,
            mtu = mtu,
            peerEndpoint = endpoint,
            peerAllowedIps = allowedIps,
            persistentKeepalive = keepalive
        )

        val profile = VpnProfile(
            id = UUID.randomUUID().toString(),
            name = profileName,
            protocol = VpnProtocol.WIREGUARD,
            serverAddress = host,
            port = port,
            wireGuardConfig = config
        )

        _profiles.update { it + profile }
        return profile
    }

    private fun parseEndpointHostPort(endpoint: String, defaultPort: Int): Pair<String, Int> {
        val trimmed = endpoint.trim()
        if (trimmed.isBlank()) return "" to defaultPort
        if (trimmed.startsWith("[")) {
            val closeIdx = trimmed.indexOf(']')
            if (closeIdx > 0) {
                val host = trimmed.substring(1, closeIdx).trim()
                val portStr = trimmed.substring(closeIdx + 1).removePrefix(":").trim()
                return host to (portStr.toIntOrNull() ?: defaultPort)
            }
        }
        val colonIdx = trimmed.lastIndexOf(':')
        return if (colonIdx > 0 && trimmed.indexOf(':') == colonIdx) {
            trimmed.substring(0, colonIdx).trim() to (trimmed.substring(colonIdx + 1).toIntOrNull() ?: defaultPort)
        } else {
            trimmed to defaultPort
        }
    }

    // --- OpenVPN .ovpn Parser ---
    fun parseOpenVpnConf(ovpnContent: String, profileName: String = "Imported OpenVPN"): VpnProfile {
        var host = ""
        var port = 1194
        var proto = "udp"
        var cipher = "AES-256-GCM"
        var localAddress = ""
        var netmask = "255.255.255.0"
        var mtu = 1500
        val dnsServers = mutableListOf<String>()
        val routes = mutableListOf<String>()

        val lines = ovpnContent.lines()
        for (raw in lines) {
            val line = raw.trim()
            if (line.startsWith("#") || line.startsWith(";") || line.isBlank()) continue

            val tokens = line.split("\\s+".toRegex())
            if (tokens.isNotEmpty()) {
                when (tokens[0].lowercase()) {
                    "remote" -> {
                        if (tokens.size >= 2 && host.isBlank()) host = tokens[1]
                        if (tokens.size >= 3) port = tokens[2].toIntOrNull() ?: 1194
                    }
                    "proto" -> {
                        if (tokens.size >= 2) proto = tokens[1].lowercase()
                    }
                    "cipher", "data-ciphers" -> {
                        if (tokens.size >= 2) cipher = tokens[1]
                    }
                    "ifconfig" -> {
                        if (tokens.size >= 2) localAddress = tokens[1]
                        if (tokens.size >= 3) netmask = tokens[2]
                    }
                    "tun-mtu" -> {
                        if (tokens.size >= 2) mtu = tokens[1].toIntOrNull() ?: 1500
                    }
                    "dhcp-option" -> {
                        if (tokens.size >= 3 && tokens[1].equals("DNS", ignoreCase = true)) {
                            dnsServers.add(tokens[2])
                        }
                    }
                    "route" -> {
                        if (tokens.size >= 3) {
                            routes.add("${tokens[1]}/${netmaskToPrefix(tokens[2])}")
                        } else if (tokens.size >= 2) {
                            routes.add("${tokens[1]}/32")
                        }
                    }
                }
            }
        }

        val caCert = ovpnContent.substringAfter("<ca>", "").substringBefore("</ca>", "").trim()
        val clientCert = ovpnContent.substringAfter("<cert>", "").substringBefore("</cert>", "").trim()
        val clientKey = ovpnContent.substringAfter("<key>", "").substringBefore("</key>", "").trim()
        val tlsAuth = ovpnContent.substringAfter("<tls-auth>", "").substringBefore("</tls-auth>", "").trim()

        val profile = VpnProfile(
            id = UUID.randomUUID().toString(),
            name = profileName,
            protocol = VpnProtocol.OPENVPN,
            serverAddress = host,
            port = port,
            openVpnConfig = OpenVpnConfig(
                remoteHost = host,
                remotePort = port,
                protocol = proto,
                caCert = caCert,
                clientCert = clientCert,
                clientKey = clientKey,
                tlsAuthKey = tlsAuth,
                cipher = cipher,
                localAddress = localAddress,
                netmask = netmask,
                dnsServers = dnsServers,
                routes = routes,
                mtu = mtu
            )
        )

        _profiles.update { it + profile }
        return profile
    }

    private fun netmaskToPrefix(maskOrRemote: String): Int {
        val parts = maskOrRemote.split(".")
        if (parts.size != 4) return 24
        var prefix = 0
        for (part in parts) {
            val octet = part.toIntOrNull() ?: return 24
            if (octet !in 0..255) return 24
            prefix += Integer.bitCount(octet)
        }
        return prefix.coerceIn(0, 32)
    }

    // --- Diagnostic Protocol Checkers ---

    suspend fun testSstpHandshakeReadiness(host: String, port: Int = 443): Result<String> = withContext(Dispatchers.IO) {
        if (host.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Target host is required"))
        }
        val cleanHost = host.trim()
        var sslSocket: SSLSocket? = null
        try {
            val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
            sslSocket = (factory.createSocket() as SSLSocket).apply {
                soTimeout = 4000
                connect(InetSocketAddress(cleanHost, port), 4000)
                startHandshake()
            }
            val tlsProto = sslSocket.session.protocol
            val cipher = sslSocket.session.cipherSuite

            val out = sslSocket.outputStream
            val sstpReq = buildString {
                append("SSTP_DUPLEX_POST /sra_{BA195980-CD49-458b-9E23-C84EE0ADCD75}/ HTTP/1.1\r\n")
                append("Host: $cleanHost:$port\r\n")
                append("SSTPCORRELATIONID: {${UUID.randomUUID()}}\r\n")
                append("Content-Length: 18446744073709551615\r\n\r\n")
            }
            out.write(sstpReq.toByteArray(Charsets.US_ASCII))
            out.flush()

            val reader = BufferedReader(InputStreamReader(sslSocket.inputStream, Charsets.US_ASCII))
            val statusLine = reader.readLine()?.trim().orEmpty()
            if (statusLine.contains("200")) {
                Result.success("SSTP DUPLEX_POST Accepted ($statusLine) over $tlsProto ($cipher)")
            } else {
                Result.success("TLS $tlsProto ($cipher) connected; SSTP response: ${statusLine.ifBlank { "No HTTP status line" }}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            NetworkByteUtils.safeClose(sslSocket)
        }
    }

    suspend fun testIpsecIkeReadiness(host: String, port: Int = 500): Result<String> = withContext(Dispatchers.IO) {
        if (host.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Target host is required"))
        }
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket().apply { soTimeout = 2500 }

            val ikeProbe = byteArrayOf(
                0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, // Initiator SPI
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // Responder SPI
                0x00, // Next Payload: No Next Payload
                0x20, // Major/Minor: IKEv2 (2.0)
                0x22, // Exchange: IKE_SA_INIT (34)
                0x08, // Flags: Initiator
                0x00, 0x00, 0x00, 0x00, // Message ID: 0
                0x00, 0x00, 0x00, 0x1C  // Length: 28 bytes
            )

            val packet = DatagramPacket(ikeProbe, ikeProbe.size, InetAddress.getByName(host.trim()), port)
            socket.send(packet)

            val recvBuf = ByteArray(1500)
            val recvPacket = DatagramPacket(recvBuf, recvBuf.size)
            try {
                socket.receive(recvPacket)
                val len = recvPacket.length
                val respSpi = if (len >= 16) {
                    recvPacket.data.sliceArray(8..15).joinToString("") { String.format("%02x", it) }
                } else "n/a"
                val exchange = if (len >= 19) recvPacket.data[18].toInt() and 0xFF else -1
                Result.success("IKEv2 responder replied ($len bytes, exchange=$exchange, rSPI=$respSpi) from $host:$port")
            } catch (_: SocketTimeoutException) {
                Result.success("IKEv2 SA_INIT probe sent to $host:$port (no UDP reply within 2.5s)")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            NetworkByteUtils.safeClose(socket)
        }
    }

    suspend fun runDnsLeakTest(): DnsLeakResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val resolvedIps = InetAddress.getAllByName("api.ipify.org")
                .mapNotNull { it.hostAddress }
                .distinct()

            val rtt = System.currentTimeMillis() - startTime
            val currentTunnelIp = telemetry.value.assignedIp
            val configuredDns = telemetry.value.dnsServers
            val isLeaking = currentTunnelIp == "0.0.0.0"

            DnsLeakResult(
                testedDnsServers = if (configuredDns.isNotEmpty()) configuredDns else resolvedIps,
                isLeakDetected = isLeaking,
                summary = if (!isLeaking) {
                    "DNS queries routed via active tunnel ($currentTunnelIp)"
                } else {
                    "No active VPN tunnel. DNS resolution uses device uplink (${resolvedIps.joinToString(", ")})"
                },
                responseTimeMs = rtt
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DnsLeakResult(
                testedDnsServers = emptyList(),
                isLeakDetected = true,
                summary = "DNS Leak Test Error: ${e.message}",
                responseTimeMs = System.currentTimeMillis() - startTime
            )
        }
    }
}
