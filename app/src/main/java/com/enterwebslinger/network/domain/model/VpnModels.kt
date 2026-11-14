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

enum class Ikev2AuthType(val label: String) {
    PSK("Pre-Shared Key (PSK)"),
    EAP("EAP (MSCHAPv2 / RADIUS)"),
    RSA_CERT("RSA X.509 Certificate")
}

data class WireGuardConfig(
    val privateKey: String = "",
    val publicKey: String = "",
    val derivedLocalPublicKey: String = "",
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
    val tlsCryptKey: String = "",
    val cipher: String = "AES-256-GCM",
    val authDigest: String = "SHA256",
    val pkcs12Subject: String = "",
    val pkcs12Alias: String = "",
    val localAddress: String = "",
    val netmask: String = "255.255.255.0",
    val dnsServers: List<String> = emptyList(),
    val routes: List<String> = emptyList(),
    val mtu: Int = 1500
)

data class Ikev2Config(
    val serverAddress: String = "",
    val remoteIdentifier: String = "",
    val authType: String = "PSK", // PSK, EAP, RSA
    val preSharedKey: String = "",
    val username: String = "",
    val password: String = "",
    val pkcs12Alias: String = "",
    val userCertPem: String = "",
    val userKeyPem: String = "",
    val caCertPem: String = "",
    val certSubjectSummary: String = ""
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

data class WireGuardKeyPair(
    val privateKeyBase64: String,
    val publicKeyBase64: String,
    val presharedKeyBase64: String
)

data class AllowedIpsCalculation(
    val includedCidrs: List<String>,
    val excludedCidrs: List<String>,
    val resultCidrs: List<String>,
    val formattedCsv: String
)

enum class EnterpriseSslVpnVendor(val displayName: String, val defaultPort: Int) {
    CISCO_ANYCONNECT("Cisco AnyConnect / OpenConnect", 443),
    FORTICLIENT_SSLVPN("FortiClient SSL-VPN", 443),
    MICROSOFT_SSTP("Microsoft SSTP (DUPLEX_POST)", 443),
    L2TP_IPSEC("L2TP / IPsec (UDP 500 + 4500 + 1701)", 1701)
}

data class SslVpnProbeResult(
    val vendor: EnterpriseSslVpnVendor,
    val host: String,
    val port: Int,
    val isReachable: Boolean,
    val tlsVersion: String = "",
    val cipherSuite: String = "",
    val httpStatusOrCode: String = "",
    val portalBannerOrCstp: String = "",
    val dtlsOrNatTReady: Boolean = false,
    val authGroups: List<String> = emptyList(),
    val rttMs: Long = 0L,
    val errorMessage: String? = null
)

data class IkeProbeResult(
    val host: String,
    val port: Int,
    val useNatT4500: Boolean,
    val ikeVersionLabel: String,
    val peerResponded: Boolean,
    val initiatorSpiHex: String,
    val responderSpiHex: String,
    val exchangeType: Int = -1,
    val exchangeName: String = "",
    val notifyPayloads: List<String> = emptyList(),
    val noProposalChosenDetected: Boolean = false,
    val natTDetected: Boolean = false,
    val responseBytes: Int = 0,
    val rttMs: Long = 0L,
    val diagnosisSummary: String = ""
)

enum class TunnelEncapsulationType(val label: String) {
    IPSEC_ESP_TUNNEL_GCM("IPsec ESP Tunnel (AES-256-GCM)"),
    IPSEC_ESP_TUNNEL_CBC_SHA256("IPsec ESP Tunnel (AES-256-CBC + HMAC-SHA256)"),
    IPSEC_ESP_TRANSPORT_GCM("IPsec ESP Transport (AES-256-GCM)"),
    XFRM_INTERFACE("Linux / Sophos XFRM Interface"),
    GRE_OVER_IPSEC("GRE-over-IPsec Tunnel (CBC+SHA256)"),
    WIREGUARD("WireGuard (Noise_IKpsk2 ChaCha20Poly1305)"),
    OPENVPN_UDP_GCM("OpenVPN UDP (AES-256-GCM)"),
    OPENVPN_TCP_CBC("OpenVPN TCP (AES-256-CBC + SHA256)"),
    VXLAN_OVERLAY("VXLAN L2-over-UDP Overlay")
}

data class TunnelMssCalculation(
    val encapsulation: TunnelEncapsulationType,
    val physicalMtu: Int,
    val isIpv6Outer: Boolean,
    val natTEnabled: Boolean,
    val overheadBytes: Int,
    val effectiveTunnelMtu: Int,
    val recommendedTcpMss: Int,
    val routerCliSnippet: String
)

data class VpnAuditReport(
    val timestampMs: Long = System.currentTimeMillis(),
    val activeVpnStatus: VpnStatus = VpnStatus.DISCONNECTED,
    val assignedTunnelIp: String = "0.0.0.0",
    val dnsLeakResult: DnsLeakResult? = null,
    val ipv6AddressesOnDevice: List<String> = emptyList(),
    val ipv6RoutesDetected: List<String> = emptyList(),
    val ipv6LeakRisk: Boolean = false,
    val splitTunnelConflicts: List<String> = emptyList(),
    val activeRoutes: List<String> = emptyList(),
    val excludedPackageCount: Int = 0,
    val overallSummary: String = ""
)

data class InstalledAppEntry(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean
)
