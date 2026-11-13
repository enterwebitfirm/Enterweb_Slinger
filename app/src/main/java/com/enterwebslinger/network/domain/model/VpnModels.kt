// === FILE: app/src/main/java/com/enterwebslinger/network/domain/model/VpnModels.kt ===
package com.enterwebslinger.network.domain.model

enum class VpnProtocol(val displayName: String, val defaultPort: Int) {
    WIREGUARD("WireGuard (UDP)", 51820),
    OPENVPN("OpenVPN (SSL/TLS)", 1194),
    IKEV2_IPSEC("IKEv2 / IPsec", 500),
    SSTP_SSLVPN("SSTP (MS SSL-VPN)", 443)
}

enum class VpnStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    ERROR
}

data class WireGuardConfig(
    val privateKey: String = "",
    val publicKey: String = "",
    val presharedKey: String = "",
    val interfaceAddresses: List<String> = emptyList(),
    val dnsServers: List<String> = emptyList(),
    val mtu: Int = 1420,
    val peerEndpoint: String = "",
    val peerAllowedIps: List<String> = emptyList(),
    val persistentKeepalive: Int = 25
)

data class OpenVpnConfig(
    val remoteHost: String = "",
    val remotePort: Int = 1194,
    val protocol: String = "udp",
    val caCert: String = "",
    val clientCert: String = "",
    val clientKey: String = "",
    val tlsAuthKey: String = "",
    val cipher: String = "AES-256-GCM",
    val localAddress: String = "",
    val netmask: String = "255.255.255.0",
    val dnsServers: List<String> = emptyList(),
    val routes: List<String> = emptyList(),
    val mtu: Int = 1500
)

data class Ikev2Config(
    val serverAddress: String = "",
    val remoteIdentifier: String = "",
    val authType: String = "PSK",
    val preSharedKey: String = "",
    val username: String = "",
    val password: String = ""
)

data class VpnProfile(
    val id: String,
    val name: String,
    val protocol: VpnProtocol,
    val serverAddress: String,
    val port: Int,
    val username: String = "",
    val password: String = "",
    val wireGuardConfig: WireGuardConfig? = null,
    val openVpnConfig: OpenVpnConfig? = null,
    val ikev2Config: Ikev2Config? = null,
    val splitTunnelExcludedApps: List<String> = emptyList(),
    val isDefault: Boolean = false,
    val sstpTunnelAddress: String = ""
)

data class VpnTelemetry(
    val status: VpnStatus = VpnStatus.DISCONNECTED,
    val activeProfileName: String = "",
    val assignedIp: String = "0.0.0.0",
    val mtu: Int = 1420,
    val dnsServers: List<String> = emptyList(),
    val bytesIn: Long = 0L,
    val bytesOut: Long = 0L,
    val throughputInBps: Long = 0L,
    val throughputOutBps: Long = 0L,
    val durationSeconds: Long = 0L,
    val errorMessage: String? = null
) {
    val formattedThroughputIn: String
        get() {
            val kb = throughputInBps / 1024.0
            val mb = kb / 1024.0
            return when {
                mb >= 1.0 -> String.format("%.2f MB/s", mb)
                kb >= 1.0 -> String.format("%.1f KB/s", kb)
                else -> "$throughputInBps B/s"
            }
        }

    val formattedThroughputOut: String
        get() {
            val kb = throughputOutBps / 1024.0
            val mb = kb / 1024.0
            return when {
                mb >= 1.0 -> String.format("%.2f MB/s", mb)
                kb >= 1.0 -> String.format("%.1f KB/s", kb)
                else -> "$throughputOutBps B/s"
            }
        }
}

data class DnsLeakResult(
    val testedDnsServers: List<String>,
    val isLeakDetected: Boolean,
    val summary: String,
    val responseTimeMs: Long
)
