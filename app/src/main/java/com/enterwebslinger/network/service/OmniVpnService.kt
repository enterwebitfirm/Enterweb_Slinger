// === FILE: app/src/main/java/com/enterwebslinger/network/service/OmniVpnService.kt ===
package com.enterwebslinger.network.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import com.enterwebslinger.network.core.util.NetworkByteUtils
import com.enterwebslinger.network.data.protocol.WireGuardCrypto
import com.enterwebslinger.network.domain.model.VpnProfile
import com.enterwebslinger.network.domain.model.VpnProtocol
import com.enterwebslinger.network.domain.model.VpnStatus
import com.enterwebslinger.network.domain.model.VpnTelemetry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

class OmniVpnService : VpnService() {

    companion object {
        const val ACTION_CONNECT = "com.enterwebslinger.network.vpn.CONNECT"
        const val ACTION_DISCONNECT = "com.enterwebslinger.network.vpn.DISCONNECT"
        const val ACTION_START_PCAP = "com.enterwebslinger.network.vpn.START_PCAP"
        const val ACTION_STOP_PCAP = "com.enterwebslinger.network.vpn.STOP_PCAP"
        const val NOTIFICATION_CHANNEL_ID = "enterweb_vpn_channel"
        const val NOTIFICATION_ID = 4040

        @Volatile
        var activeProfile: VpnProfile? = null

        private val _telemetry = MutableStateFlow(VpnTelemetry())
        val telemetry: StateFlow<VpnTelemetry> = _telemetry.asStateFlow()

        fun startVpn(context: Context, profile: VpnProfile) {
            activeProfile = profile
            val intent = Intent(context, OmniVpnService::class.java).apply {
                action = ACTION_CONNECT
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopVpn(context: Context) {
            val intent = Intent(context, OmniVpnService::class.java).apply {
                action = ACTION_DISCONNECT
            }
            context.startService(intent)
        }

        fun startLocalPcapCapture(context: Context) {
            val intent = Intent(context, OmniVpnService::class.java).apply {
                action = ACTION_START_PCAP
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopLocalPcapCapture(context: Context) {
            val intent = Intent(context, OmniVpnService::class.java).apply {
                action = ACTION_STOP_PCAP
            }
            context.startService(intent)
        }
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var tunnelJob: Job? = null
    private var protectedUdpSocket: DatagramSocket? = null
    private var protectedSslSocket: SSLSocket? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val pcapEngine by lazy {
        com.enterwebslinger.network.EnterwebSlingerApp.getContainer().pcapCaptureEngine
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY
        when (action) {
            ACTION_CONNECT -> {
                val profile = activeProfile
                if (profile != null) {
                    startForeground(NOTIFICATION_ID, buildNotification("Connecting to ${profile.name}..."))
                    connectTunnel(profile)
                }
            }
            ACTION_START_PCAP -> {
                startForeground(NOTIFICATION_ID, buildNotification("Local PCAP Packet Capture Active"))
                startLocalPcapLoop()
            }
            ACTION_STOP_PCAP -> {
                pcapEngine.stopCaptureSession()
                if (_telemetry.value.status != VpnStatus.CONNECTED) {
                    disconnectTunnel()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
            ACTION_DISCONNECT -> {
                pcapEngine.stopCaptureSession()
                disconnectTunnel()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun connectTunnel(profile: VpnProfile) {
        tunnelJob?.cancel()
        NetworkByteUtils.safeClose(protectedUdpSocket)
        NetworkByteUtils.safeClose(protectedSslSocket)
        protectedUdpSocket = null
        protectedSslSocket = null

        _telemetry.update {
            it.copy(
                status = VpnStatus.CONNECTING,
                activeProfileName = profile.name,
                errorMessage = null
            )
        }

        tunnelJob = serviceScope.launch {
            try {
                val builder = Builder().setSession(profile.name)
                var primaryAssignedIp = ""
                var effectiveMtu = 1420
                var dnsServers: List<String> = emptyList()
                var wgHandshakePacket: ByteArray? = null

                when (profile.protocol) {
                    VpnProtocol.WIREGUARD -> {
                        val wg = profile.wireGuardConfig
                        val rawAddresses = wg?.interfaceAddresses.orEmpty()
                        if (rawAddresses.isEmpty()) {
                            failTunnel("WireGuard profile '${profile.name}' has no [Interface] Address configured.")
                            return@launch
                        }
                        effectiveMtu = (wg?.mtu ?: 1420).coerceIn(1280, 1500)
                        dnsServers = wg?.dnsServers.orEmpty()

                        var addedAnyAddress = false
                        rawAddresses.forEach { cidr ->
                            parseCidr(cidr)?.let { (ip, prefix) ->
                                builder.addAddress(ip, prefix)
                                if (primaryAssignedIp.isBlank()) primaryAssignedIp = ip
                                addedAnyAddress = true
                            }
                        }
                        if (!addedAnyAddress) {
                            failTunnel("Invalid WireGuard interface Address format.")
                            return@launch
                        }

                        val allowed = wg?.peerAllowedIps.orEmpty()
                        if (allowed.isEmpty()) {
                            builder.addRoute("0.0.0.0", 0)
                        } else {
                            allowed.forEach { cidr ->
                                parseCidr(cidr)?.let { (routeIp, prefix) ->
                                    runCatching { builder.addRoute(routeIp, prefix) }
                                }
                            }
                        }

                        if (!wg?.publicKey.isNullOrBlank()) {
                            wgHandshakePacket = WireGuardCrypto.buildHandshakeInitiationPacket(
                                localPrivateKeyBase64 = wg?.privateKey.orEmpty(),
                                peerPublicKeyBase64 = wg?.publicKey.orEmpty()
                            )
                        }
                    }

                    VpnProtocol.OPENVPN -> {
                        val ovpn = profile.openVpnConfig
                        val localAddr = ovpn?.localAddress.orEmpty()
                        if (localAddr.isBlank()) {
                            failTunnel("OpenVPN static TUN requires an 'ifconfig <ip> <mask>' directive in the .ovpn profile.")
                            return@launch
                        }
                        val prefix = netmaskToPrefix(ovpn?.netmask.orEmpty())
                        builder.addAddress(localAddr.trim(), prefix)
                        primaryAssignedIp = localAddr.trim()
                        effectiveMtu = (ovpn?.mtu ?: 1500).coerceIn(1280, 1500)
                        dnsServers = ovpn?.dnsServers.orEmpty()
                        val routes = ovpn?.routes.orEmpty()
                        if (routes.isEmpty()) {
                            builder.addRoute("0.0.0.0", 0)
                        } else {
                            routes.forEach { cidr ->
                                parseCidr(cidr)?.let { (routeIp, rPrefix) ->
                                    runCatching { builder.addRoute(routeIp, rPrefix) }
                                }
                            }
                        }
                    }

                    VpnProtocol.SSTP_SSLVPN -> {
                        val sstpAddr = profile.sstpTunnelAddress.trim()
                        if (sstpAddr.isBlank()) {
                            failTunnel("SSTP TUN requires a local virtual interface CIDR (or use Diagnostics tab to test SSTP_DUPLEX_POST handshake).")
                            return@launch
                        }
                        val parsed = parseCidr(sstpAddr)
                        if (parsed == null) {
                            failTunnel("Invalid SSTP local interface CIDR: $sstpAddr")
                            return@launch
                        }
                        builder.addAddress(parsed.first, parsed.second)
                        builder.addRoute("0.0.0.0", 0)
                        primaryAssignedIp = parsed.first
                    }

                    VpnProtocol.IKEV2_IPSEC -> {
                        failTunnel("IKEv2 / IPsec profiles are managed via Android System VpnManager provisioning.")
                        return@launch
                    }
                }

                builder.setMtu(effectiveMtu)
                dnsServers.forEach { dns ->
                    runCatching { builder.addDnsServer(dns.trim()) }
                }
                profile.splitTunnelExcludedApps.forEach { pkg ->
                    runCatching { builder.addDisallowedApplication(pkg) }
                }

                // Open and protect transport socket BEFORE routing traffic via TUN
                var udpSock: DatagramSocket? = null
                var remoteEndpoint: InetSocketAddress? = null
                if (profile.serverAddress.isNotBlank() && profile.port > 0) {
                    if (profile.protocol == VpnProtocol.SSTP_SSLVPN) {
                        val sslFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
                        val ssl = (sslFactory.createSocket() as SSLSocket).apply {
                            soTimeout = 5000
                        }
                        protect(ssl)
                        ssl.connect(InetSocketAddress(profile.serverAddress, profile.port), 5000)
                        ssl.startHandshake()
                        val req = "SSTP_DUPLEX_POST /sra_{BA195980-CD49-458b-9E23-C84EE0ADCD75}/ HTTP/1.1\r\n" +
                            "Host: ${profile.serverAddress}:${profile.port}\r\n" +
                            "SSTPCORRELATIONID: {${UUID.randomUUID()}}\r\n" +
                            "Content-Length: 18446744073709551615\r\n\r\n"
                        ssl.outputStream.write(req.toByteArray(Charsets.US_ASCII))
                        ssl.outputStream.flush()
                        protectedSslSocket = ssl
                    } else if (profile.protocol == VpnProtocol.WIREGUARD ||
                        (profile.protocol == VpnProtocol.OPENVPN && !profile.openVpnConfig?.protocol.equals("tcp", true))
                    ) {
                        val addr = InetAddress.getByName(profile.serverAddress)
                        remoteEndpoint = InetSocketAddress(addr, profile.port)
                        val sock = DatagramSocket()
                        if (!protect(sock)) {
                            NetworkByteUtils.safeClose(sock)
                            failTunnel("Failed to protect underlying transport socket from TUN routing loop.")
                            return@launch
                        }
                        sock.soTimeout = 1000
                        sock.connect(remoteEndpoint)
                        udpSock = sock
                        protectedUdpSocket = sock

                        if (wgHandshakePacket != null) {
                            sock.send(DatagramPacket(wgHandshakePacket, wgHandshakePacket.size))
                        } else if (profile.protocol == VpnProtocol.OPENVPN) {
                            // P_CONTROL_HARD_RESET_CLIENT_V2 header (opcode 7 << 3 = 0x38 + empty 8-byte session)
                            val ovpnReset = ByteArray(14).apply { this[0] = 0x38 }
                            sock.send(DatagramPacket(ovpnReset, ovpnReset.size))
                        }
                    }
                }

                val pfd = builder.establish()
                if (pfd == null) {
                    NetworkByteUtils.safeClose(udpSock)
                    NetworkByteUtils.safeClose(protectedSslSocket)
                    failTunnel("Failed to establish TUN descriptor (check VPN user consent permission).")
                    return@launch
                }
                vpnInterface = pfd

                _telemetry.update {
                    it.copy(
                        status = VpnStatus.CONNECTED,
                        activeProfileName = profile.name,
                        assignedIp = primaryAssignedIp,
                        mtu = effectiveMtu,
                        dnsServers = dnsServers,
                        errorMessage = null
                    )
                }

                startForeground(NOTIFICATION_ID, buildNotification("Connected to ${profile.name} ($primaryAssignedIp)"))
                runTunnelDataPump(pfd, udpSock)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failTunnel(e.message ?: "Tunnel setup exception")
            }
        }
    }

    private fun failTunnel(message: String) {
        NetworkByteUtils.safeClose(protectedUdpSocket)
        NetworkByteUtils.safeClose(protectedSslSocket)
        NetworkByteUtils.safeClose(vpnInterface)
        protectedUdpSocket = null
        protectedSslSocket = null
        vpnInterface = null
        _telemetry.update {
            it.copy(
                status = VpnStatus.ERROR,
                assignedIp = "0.0.0.0",
                throughputInBps = 0L,
                throughputOutBps = 0L,
                errorMessage = message
            )
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun runTunnelDataPump(pfd: ParcelFileDescriptor, udpSocket: DatagramSocket?) {
        val startTime = System.currentTimeMillis()
        val totalBytesIn = AtomicLong(0L)
        val totalBytesOut = AtomicLong(0L)
        var lastBytesIn = 0L
        var lastBytesOut = 0L
        var lastTickTime = System.currentTimeMillis()

        val inStream = FileInputStream(pfd.fileDescriptor)
        val outStream = FileOutputStream(pfd.fileDescriptor)
        val txBuffer = ByteArray(32768)

        val monitorJob = serviceScope.launch {
            while (isActive) {
                delay(1000)
                val now = System.currentTimeMillis()
                val deltaSec = ((now - lastTickTime) / 1000.0).coerceAtLeast(0.5)

                val curIn = totalBytesIn.get()
                val curOut = totalBytesOut.get()
                val speedIn = ((curIn - lastBytesIn) / deltaSec).toLong().coerceAtLeast(0L)
                val speedOut = ((curOut - lastBytesOut) / deltaSec).toLong().coerceAtLeast(0L)

                lastBytesIn = curIn
                lastBytesOut = curOut
                lastTickTime = now

                _telemetry.update {
                    it.copy(
                        bytesIn = curIn,
                        bytesOut = curOut,
                        throughputInBps = speedIn,
                        throughputOutBps = speedOut,
                        durationSeconds = (now - startTime) / 1000
                    )
                }
            }
        }

        val rxJob = if (udpSocket != null) {
            serviceScope.launch {
                val rxBuffer = ByteArray(4096)
                val rxPacket = DatagramPacket(rxBuffer, rxBuffer.size)
                try {
                    while (isActive && !udpSocket.isClosed) {
                        try {
                            udpSocket.receive(rxPacket)
                            if (rxPacket.length > 0) {
                                totalBytesIn.addAndGet(rxPacket.length.toLong())
                                if (rxPacket.length > 20 && (rxBuffer[0].toInt() ushr 4) in listOf(4, 6)) {
                                    if (pcapEngine.stats.value.isCapturing) {
                                        pcapEngine.recordRawIpPacket(rxBuffer, rxPacket.length)
                                    }
                                    runCatching { outStream.write(rxBuffer, 0, rxPacket.length) }
                                }
                            }
                        } catch (_: SocketTimeoutException) {
                            // Periodic check for coroutine cancellation
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {}
            }
        } else null

        val wgKeepaliveSec = if (activeProfile?.protocol == VpnProtocol.WIREGUARD) {
            (activeProfile?.wireGuardConfig?.persistentKeepalive ?: 0).coerceIn(0, 120)
        } else 0
        val keepaliveJob = if (udpSocket != null && wgKeepaliveSec > 0) {
            serviceScope.launch {
                val keepalivePayload = ByteArray(32).apply { this[0] = 0x04 } // WireGuard Type 4 Transport Data empty keepalive
                try {
                    while (isActive && !udpSocket.isClosed) {
                        delay(wgKeepaliveSec * 1000L)
                        if (!udpSocket.isClosed) {
                            runCatching {
                                udpSocket.send(DatagramPacket(keepalivePayload, keepalivePayload.size))
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {}
            }
        } else null

        try {
            while (true) {
                val length = inStream.read(txBuffer)
                if (length > 0) {
                    totalBytesOut.addAndGet(length.toLong())
                    if (pcapEngine.stats.value.isCapturing) {
                        pcapEngine.recordRawIpPacket(txBuffer, length)
                    }
                    if (udpSocket != null && !udpSocket.isClosed && length <= 1500) {
                        runCatching {
                            udpSocket.send(DatagramPacket(txBuffer, length))
                        }
                    }
                } else if (length == -1) {
                    break
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        } finally {
            monitorJob.cancel()
            rxJob?.cancel()
            keepaliveJob?.cancel()
            NetworkByteUtils.safeClose(inStream)
            NetworkByteUtils.safeClose(outStream)
        }
    }

    private fun startLocalPcapLoop() {
        val sessionRes = pcapEngine.startCaptureSession()
        if (sessionRes.isFailure) {
            return
        }
        // If a VPN tunnel is already active, runTunnelDataPump automatically mirrors all packets to pcapEngine.
        if (_telemetry.value.status == VpnStatus.CONNECTED && vpnInterface != null) {
            return
        }

        tunnelJob?.cancel()
        tunnelJob = serviceScope.launch {
            var inStream: FileInputStream? = null
            var outStream: FileOutputStream? = null
            try {
                val builder = Builder()
                    .setSession("Enterweb PCAP Sniffer")
                    .addAddress("10.254.254.2", 30)
                    .addRoute("10.254.254.0", 30)
                    .addDnsServer("10.254.254.1")
                    .setMtu(1500)
                runCatching { builder.addDisallowedApplication(packageName) }

                val pfd = builder.establish() ?: run {
                    pcapEngine.stopCaptureSession()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@launch
                }
                vpnInterface = pfd

                val safeIn = FileInputStream(pfd.fileDescriptor)
                val safeOut = FileOutputStream(pfd.fileDescriptor)
                inStream = safeIn
                outStream = safeOut

                val buf = ByteArray(32768)
                val upstreamDns = InetAddress.getByName("1.1.1.1")

                while (isActive && pcapEngine.stats.value.isCapturing) {
                    val len = safeIn.read(buf)
                    if (len == -1) break
                    if (len < 20) continue

                    val version = (buf[0].toInt() ushr 4) and 0x0F
                    pcapEngine.recordRawIpPacket(buf, len)

                    if (version == 4) {
                        val ihl = (buf[0].toInt() and 0x0F) * 4
                        val proto = buf[9].toInt() and 0xFF
                        if (proto == 17 && len >= ihl + 8) {
                            val srcPort = ((buf[ihl].toInt() and 0xFF) shl 8) or (buf[ihl + 1].toInt() and 0xFF)
                            val dstPort = ((buf[ihl + 2].toInt() and 0xFF) shl 8) or (buf[ihl + 3].toInt() and 0xFF)
                            if (dstPort == 53) {
                                val dnsPayloadOffset = ihl + 8
                                val dnsPayloadLen = len - dnsPayloadOffset
                                if (dnsPayloadLen > 0) {
                                    val srcIpBytes = buf.copyOfRange(12, 16)
                                    val dstIpBytes = buf.copyOfRange(16, 20)
                                    val queryPayload = buf.copyOfRange(dnsPayloadOffset, len)
                                    serviceScope.launch {
                                        runCatching {
                                            DatagramSocket().use { qSock ->
                                                qSock.soTimeout = 2500
                                                if (!protect(qSock)) return@use
                                                qSock.send(DatagramPacket(queryPayload, queryPayload.size, upstreamDns, 53))
                                                val respBuf = ByteArray(2048)
                                                val respPkt = DatagramPacket(respBuf, respBuf.size)
                                                qSock.receive(respPkt)
                                                val ipUdpReply = buildIpv4UdpPacket(
                                                    srcIp = dstIpBytes,
                                                    dstIp = srcIpBytes,
                                                    srcPort = 53,
                                                    dstPort = srcPort,
                                                    payload = respBuf.copyOf(respPkt.length)
                                                )
                                                pcapEngine.recordRawIpPacket(ipUdpReply, ipUdpReply.size)
                                                synchronized(safeOut) {
                                                    safeOut.write(ipUdpReply)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            } finally {
                pcapEngine.stopCaptureSession()
                NetworkByteUtils.safeClose(inStream)
                NetworkByteUtils.safeClose(outStream)
                NetworkByteUtils.safeClose(vpnInterface)
                vpnInterface = null
            }
        }
    }

    private fun buildIpv4UdpPacket(
        srcIp: ByteArray,
        dstIp: ByteArray,
        srcPort: Int,
        dstPort: Int,
        payload: ByteArray
    ): ByteArray {
        val totalLen = 20 + 8 + payload.size
        val pkt = ByteArray(totalLen)
        pkt[0] = 0x45.toByte() // Version 4, IHL 5
        pkt[2] = ((totalLen ushr 8) and 0xFF).toByte()
        pkt[3] = (totalLen and 0xFF).toByte()
        pkt[8] = 64.toByte() // TTL
        pkt[9] = 17.toByte() // UDP
        System.arraycopy(srcIp, 0, pkt, 12, 4)
        System.arraycopy(dstIp, 0, pkt, 16, 4)

        val ipCsum = computeChecksum(pkt, 0, 20)
        pkt[10] = ((ipCsum ushr 8) and 0xFF).toByte()
        pkt[11] = (ipCsum and 0xFF).toByte()

        val udpLen = 8 + payload.size
        pkt[20] = ((srcPort ushr 8) and 0xFF).toByte()
        pkt[21] = (srcPort and 0xFF).toByte()
        pkt[22] = ((dstPort ushr 8) and 0xFF).toByte()
        pkt[23] = (dstPort and 0xFF).toByte()
        pkt[24] = ((udpLen ushr 8) and 0xFF).toByte()
        pkt[25] = (udpLen and 0xFF).toByte()
        System.arraycopy(payload, 0, pkt, 28, payload.size)
        // IPv4 UDP checksum 0 is valid per RFC 768 when optional
        return pkt
    }

    private fun computeChecksum(buf: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var i = offset
        while (i + 1 < offset + length) {
            val word = ((buf[i].toInt() and 0xFF) shl 8) or (buf[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        if (i < offset + length) {
            sum += (buf[i].toInt() and 0xFF) shl 8
        }
        while ((sum ushr 16) != 0) {
            sum = (sum and 0xFFFF) + (sum ushr 16)
        }
        return (sum.inv()) and 0xFFFF
    }

    private fun parseCidr(rawCidr: String): Pair<String, Int>? {
        val cleaned = rawCidr.trim()
        if (cleaned.isBlank()) return null
        val ip = cleaned.substringBefore("/").trim()
        if (ip.isBlank()) return null
        val isIpv6 = ip.contains(":")
        val maxPrefix = if (isIpv6) 128 else 32
        val defaultPrefix = if (isIpv6) 64 else 24
        val prefix = cleaned.substringAfter("/", defaultPrefix.toString())
            .toIntOrNull()?.coerceIn(0, maxPrefix) ?: defaultPrefix
        return ip to prefix
    }

    private fun netmaskToPrefix(mask: String): Int {
        val parts = mask.trim().split(".")
        if (parts.size != 4) return 24
        var prefix = 0
        for (part in parts) {
            val octet = part.toIntOrNull() ?: return 24
            if (octet !in 0..255) return 24
            prefix += Integer.bitCount(octet)
        }
        return prefix.coerceIn(1, 32)
    }

    private fun disconnectTunnel() {
        tunnelJob?.cancel()
        tunnelJob = null
        NetworkByteUtils.safeClose(protectedUdpSocket)
        NetworkByteUtils.safeClose(protectedSslSocket)
        NetworkByteUtils.safeClose(vpnInterface)
        protectedUdpSocket = null
        protectedSslSocket = null
        vpnInterface = null

        _telemetry.update {
            it.copy(
                status = VpnStatus.DISCONNECTED,
                assignedIp = "0.0.0.0",
                throughputInBps = 0L,
                throughputOutBps = 0L
            )
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Enterweb Slinger VPN",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active VPN tunnel state and throughput"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("Enterweb Slinger VPN")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Enterweb Slinger VPN")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setOngoing(true)
                .build()
        }
    }

    override fun onDestroy() {
        disconnectTunnel()
        serviceScope.cancel()
        super.onDestroy()
    }
}
