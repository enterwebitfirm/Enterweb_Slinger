// === FILE: app/src/main/java/com/enterwebslinger/network/domain/model/ConsolidatedEngineeringModels.kt ===
package com.enterwebslinger.network.domain.model

data class CollectorPreferences(
    val syslogUdpPort: Int = 1514,
    val syslogTcpPort: Int = 6514,
    val snmpTrapPort: Int = 1162,
    val netflowPort: Int = 2055,
    val ipfixPort: Int = 4739,
    val sflowPort: Int = 6343,
    val radiusCoaPort: Int = 3799,
    val mndpPort: Int = 5678,
    val autoStartCollectors: Boolean = false
)

data class CollectorServiceStatus(
    val isRunning: Boolean = false,
    val activeCollectors: List<String> = emptyList(),
    val totalEventsProcessed: Long = 0L,
    val errorMessage: String? = null
)

data class MikroTikSystemResource(
    val uptime: String,
    val version: String,
    val cpuLoadPercent: Int,
    val freeMemoryBytes: Long,
    val totalMemoryBytes: Long,
    val freeHddBytes: Long,
    val totalHddBytes: Long,
    val architectureName: String,
    val boardName: String
)

data class MikroTikInterfaceEntry(
    val id: String,
    val name: String,
    val type: String,
    val running: Boolean,
    val disabled: Boolean,
    val rxBytes: Long,
    val txBytes: Long
)

data class MikroTikBgpSession(
    val peerName: String,
    val remoteAddress: String,
    val remoteAs: Long,
    val state: String,
    val uptime: String,
    val prefixCount: Long
)

data class MndpNeighborDevice(
    val identity: String,
    val ipAddress: String,
    val macAddress: String,
    val platform: String,
    val version: String,
    val board: String,
    val uptimeSeconds: Long
)

data class SwitchAuditSnapshot(
    val host: String,
    val lldpNeighbors: List<LldpCdpNeighborEntry> = emptyList(),
    val cdpNeighbors: List<LldpCdpNeighborEntry> = emptyList(),
    val vlanTable: List<SwitchVlanEntry> = emptyList(),
    val poePorts: List<SwitchPoePortEntry> = emptyList(),
    val isSuccess: Boolean = true,
    val errorMessage: String? = null
)

data class LldpCdpNeighborEntry(
    val protocol: String, // "LLDP" or "CDP"
    val localInterface: String,
    val neighborDeviceName: String,
    val neighborInterfaceId: String,
    val neighborIp: String,
    val capabilities: String
)

data class SwitchVlanEntry(
    val vlanId: Int,
    val vlanName: String,
    val status: String,
    val memberPorts: List<String>
)

data class SwitchPoePortEntry(
    val portIndex: Int,
    val portName: String,
    val powerState: String,
    val consumedWatts: Float,
    val voltage: Float,
    val currentMa: Float
)

data class BgpLookingGlassResult(
    val prefixOrAsn: String,
    val originAsn: String,
    val announcedPrefix: String,
    val rpkiValidity: String, // "VALID", "INVALID", "NOT_FOUND"
    val asPath: List<String>,
    val peerCount: Int,
    val rawJson: String,
    val isSuccess: Boolean
)

data class RouteValidationReport(
    val targetHost: String,
    val resolvedIp: String,
    val localGateway: String,
    val publicWanIp: String,
    val mplsTtlHopCount: Int,
    val snatEgressMatch: Boolean,
    val summary: String
)

data class VoipQosComparisonResult(
    val targetHost: String,
    val port: Int,
    val dscpValueHex: String, // "0xB8" (EF) vs "0x00" (BE)
    val packetsSent: Int,
    val packetsReceived: Int,
    val jitterMs: Float,
    val packetLossPercent: Float,
    val meanOpinionScoreMos: Float, // 1.0 to 4.5
    val rFactor: Float, // 0 to 100
    val audioQualityGrade: String
)

data class AuditReportRecord(
    val id: Long = 0L,
    val timestampMs: Long = System.currentTimeMillis(),
    val ssid: String,
    val healthScore: Int,
    val severity: String,
    val summary: String,
    val jsonReport: String
)
