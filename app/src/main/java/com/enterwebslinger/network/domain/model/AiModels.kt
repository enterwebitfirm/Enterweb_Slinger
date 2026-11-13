// === FILE: app/src/main/java/com/enterwebslinger/network/domain/model/AiModels.kt ===
package com.enterwebslinger.network.domain.model

data class NetworkDiagnosticSnapshot(
    val timestamp: Long = System.currentTimeMillis(),
    val wifiRfState: WifiRfState,
    val gatewayLatency: LatencyJitterState,
    val externalDnsLatency: LatencyJitterState,
    val tracerouteHops: List<TracerouteSnapshotHop>,
    val throughput: ThroughputState,
    val lanSecurity: LanSecuritySnapshot
)

data class WifiRfState(
    val ssid: String,
    val bssid: String,
    val band: String,
    val channelWidthMhz: Int,
    val rssiDbm: Int,
    val linkSpeedMbps: Int,
    val overlappingCoChannelApsCount: Int,
    val channel: Int
)

data class LatencyJitterState(
    val target: String,
    val protocol: String,
    val minMs: Float,
    val avgMs: Float,
    val maxMs: Float,
    val jitterMs: Float,
    val packetLossPercent: Float
)

data class TracerouteSnapshotHop(
    val hopNumber: Int,
    val ip: String,
    val hostname: String,
    val rttMs: Float,
    val rttDeltaMs: Float
)

data class ThroughputState(
    val downloadSpeedMbps: Float,
    val uploadSpeedMbps: Float,
    val unloadedPingMs: Float,
    val loadedPingMs: Float,
    val bufferbloatDeltaMs: Float
)

data class LanSecuritySnapshot(
    val totalDiscoveredDevicesCount: Int,
    val devicesWithCustomLabelsCount: Int,
    val exposedHighRiskPortsCount: Int,
    val exposedPortsSummary: List<String>
)

enum class BottleneckType {
    WIFI_SIGNAL_OR_CONGESTION,
    LOCAL_ROUTER_OR_LAN,
    ISP_WAN_OR_ROUTING,
    DNS_CONFIGURATION,
    HEALTHY
}

enum class AuditSeverity {
    OPTIMAL,
    WARNING,
    CRITICAL
}

data class AiAuditReport(
    val healthScore: Int = 100,
    val primaryBottleneck: String = "HEALTHY",
    val severity: String = "OPTIMAL",
    val executiveSummary: String = "",
    val findings: List<AiAuditFinding> = emptyList()
)

data class AiAuditFinding(
    val title: String,
    val technicalEvidence: String,
    val plainEnglishExplanation: String,
    val recommendedFix: String
)

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: String,
    val text: String,
    val isToolExecuting: Boolean = false,
    val toolName: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
