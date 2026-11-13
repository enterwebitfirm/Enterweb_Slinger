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
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var tunnelJob: Job? = null
    private var protectedUdpSocket: DatagramSocket? = null
    private var protectedSslSocket: SSLSocket? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
            ACTION_DISCONNECT -> {
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

        try {
            while (true) {
                val length = inStream.read(txBuffer)
                if (length > 0) {
                    totalBytesOut.addAndGet(length.toLong())
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
            NetworkByteUtils.safeClose(inStream)
            NetworkByteUtils.safeClose(outStream)
        }
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
