// === FILE: app/src/main/java/com/enterwebslinger/network/domain/model/ProToolkitModels.kt ===
package com.enterwebslinger.network.domain.model

// --- 1. Path MTU Discovery (PMTUD) ---

data class MtuProbeStep(
    val payloadSizeBytes: Int,
    val totalWireSizeBytes: Int,
    val succeeded: Boolean,
    val statusMessage: String,
    val rttMs: Long
)

data class MtuTunnelRecommendation(
    val tunnelName: String,
    val recommendedMtu: Int,
    val recommendedTcpMss: Int,
    val notes: String
)

data class MtuDiscoveryResult(
    val targetHost: String,
    val resolvedIp: String,
    val isIpv6: Boolean,
    val ipIcmpOverheadBytes: Int,
    val maxPayloadBytes: Int,
    val pathMtuBytes: Int,
    val minProbedBytes: Int = 576,
    val maxProbedBytes: Int = 1500,
    val steps: List<MtuProbeStep> = emptyList(),
    val recommendations: List<MtuTunnelRecommendation> = emptyList(),
    val errorMessage: String? = null
)

// --- 5. Local Loopback PCAP Packet Sniffer ---

data class CapturedPacketRecord(
    val id: Long,
    val timestampUs: Long,
    val capturedLength: Int,
    val originalLength: Int,
    val ipVersion: Int,
    val protocolName: String,
    val srcIp: String,
    val dstIp: String,
    val srcPort: Int,
    val dstPort: Int,
    val tcpFlags: String,
    val summary: String,
    val payloadPreviewAscii: String,
    val rawIpBytes: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CapturedPacketRecord) return false
        return id == other.id && timestampUs == other.timestampUs && rawIpBytes.contentEquals(other.rawIpBytes)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + timestampUs.hashCode()
        result = 31 * result + rawIpBytes.contentHashCode()
        return result
    }
}

data class PcapCaptureStats(
    val isCapturing: Boolean = false,
    val packetCount: Long = 0L,
    val totalBytes: Long = 0L,
    val tcpCount: Long = 0L,
    val udpCount: Long = 0L,
    val icmpCount: Long = 0L,
    val activeFilePath: String? = null,
    val errorMessage: String? = null
)

// --- 6. Enterprise RADIUS & TACACS+ AAA ---

enum class RadiusResponseCode(val code: Int, val label: String) {
    ACCESS_ACCEPT(2, "Access-Accept (Code 2)"),
    ACCESS_REJECT(3, "Access-Reject (Code 3)"),
    ACCESS_CHALLENGE(11, "Access-Challenge (Code 11)"),
    ACCOUNTING_RESPONSE(5, "Accounting-Response (Code 5)"),
    TIMEOUT_OR_ERROR(-1, "Timeout / Transport Error")
}

data class RadiusVsaAttribute(
    val vendorId: Long,
    val vendorType: Int,
    val valueText: String,
    val rawHex: String
)

data class RadiusAttributeEntry(
    val type: Int,
    val name: String,
    val valueText: String
)

data class RadiusTestResult(
    val serverIp: String,
    val port: Int,
    val responseCode: RadiusResponseCode,
    val replyMessage: String = "",
    val stateHex: String = "",
    val attributes: List<RadiusAttributeEntry> = emptyList(),
    val vsaAttributes: List<RadiusVsaAttribute> = emptyList(),
    val rttMs: Long = 0L,
    val isSuccess: Boolean = false,
    val errorMessage: String? = null
)

data class TacacsProbeResult(
    val serverIp: String,
    val port: Int,
    val isReachable: Boolean,
    val versionByte: Int? = null,
    val protocolHeaderValid: Boolean = false,
    val responseHex: String = "",
    val rttMs: Long = 0L,
    val statusMessage: String = ""
)

// --- 7. DNS Benchmark, DNSSEC & Happy Eyeballs / ALPN ---

data class DnsBenchmarkEntry(
    val resolverName: String,
    val resolverIp: String,
    val protocolType: String,
    val domainQueried: String,
    val minRttMs: Long,
    val avgRttMs: Long,
    val dnssecAuthenticatedData: Boolean,
    val resolvedIps: List<String>,
    val isSuccess: Boolean,
    val errorMessage: String? = null
)

data class HappyEyeballsResult(
    val host: String,
    val port: Int,
    val ipv4Address: String? = null,
    val ipv6Address: String? = null,
    val ipv4ConnectMs: Long? = null,
    val ipv6ConnectMs: Long? = null,
    val winnerFamily: String = "None",
    val alpnProtocol: String = "Not negotiated",
    val tlsProtocol: String = "N/A",
    val cipherSuite: String = "N/A",
    val altSvcHeader: String = "",
    val supportsHttp2: Boolean = false,
    val supportsHttp3Quic: Boolean = false,
    val errorMessage: String? = null
)

// --- 8. Background Network Audit Worker ---

data class AuditMonitorConfig(
    val enabled: Boolean = false,
    val intervalMinutes: Long = 15L,
    val trustedSsid: String = "",
    val trustedMacWhitelist: List<String> = emptyList(),
    val watchedHosts: List<String> = emptyList()
)

data class AuditRunSummary(
    val timestampMs: Long = 0L,
    val ssid: String = "",
    val arpCount: Int = 0,
    val unknownMacsFound: List<String> = emptyList(),
    val offlineHosts: List<String> = emptyList(),
    val gatewayReachable: Boolean = false,
    val statusMessage: String = "No scheduled audit executed yet"
)

// --- 9. Multi-Host SSH Batch Runner & TrafficStats ---

data class SshTargetHost(
    val id: String,
    val name: String,
    val host: String,
    val port: Int = 22,
    val username: String = "admin",
    val password: String = "",
    val vendorType: String = "Cisco IOS",
    val isSelected: Boolean = true
)

data class SshBatchResult(
    val targetName: String,
    val host: String,
    val output: String,
    val durationMs: Long,
    val diffLines: List<DiffLine> = emptyList(),
    val diffSummary: String = "",
    val isSuccess: Boolean,
    val errorMessage: String? = null
)

data class TrafficRateSample(
    val timestampMs: Long = System.currentTimeMillis(),
    val totalRxBps: Long = 0L,
    val totalTxBps: Long = 0L,
    val mobileRxBps: Long = 0L,
    val mobileTxBps: Long = 0L,
    val totalRxPps: Long = 0L,
    val totalTxPps: Long = 0L,
    val cumulativeRxBytes: Long = 0L,
    val cumulativeTxBytes: Long = 0L,
    val isSupported: Boolean = true
)
