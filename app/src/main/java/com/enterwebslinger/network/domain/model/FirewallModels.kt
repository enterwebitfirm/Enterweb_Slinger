// === FILE: app/src/main/java/com/enterwebslinger/network/domain/model/FirewallModels.kt ===
package com.enterwebslinger.network.domain.model

enum class FirewallVendor(
    val displayName: String,
    val shortCode: String,
    val defaultApiPort: Int,
    val defaultSshPort: Int,
    val managementHint: String
) {
    SOPHOS_XGS(
        displayName = "Sophos XG / XGS (SFOS)",
        shortCode = "Sophos",
        defaultApiPort = 4444,
        defaultSshPort = 22,
        managementHint = "XML API (/webconsole/APIController) + SFOS SSH CLI"
    ),
    FORTINET_FORTIGATE(
        displayName = "Fortinet FortiGate (7.x/8.x)",
        shortCode = "FortiGate",
        defaultApiPort = 443,
        defaultSshPort = 22,
        managementHint = "REST API (/api/v2/monitor/*) with Bearer API Token"
    ),
    PALO_ALTO_PANOS(
        displayName = "Palo Alto PAN-OS / Panorama",
        shortCode = "PAN-OS",
        defaultApiPort = 443,
        defaultSshPort = 22,
        managementHint = "XML API (/api/?type=keygen & op) + SSH CLI"
    ),
    CISCO_ASA_FTD(
        displayName = "Cisco ASA / FTD (FMC)",
        shortCode = "Cisco ASA",
        defaultApiPort = 443,
        defaultSshPort = 22,
        managementHint = "SSH CLI (packet-tracer / failover) + ASA/FMC REST API"
    ),
    PFSENSE_OPNSENSE(
        displayName = "pfSense / OPNsense",
        shortCode = "pf/OPN",
        defaultApiPort = 443,
        defaultSshPort = 22,
        managementHint = "REST API (/api/v1 & /api/diagnostics) + FreeBSD SSH CLI"
    )
}

data class FirewallApplianceProfile(
    val id: Long = 0L,
    val vendor: FirewallVendor,
    val name: String,
    val host: String,
    val apiPort: Int = vendor.defaultApiPort,
    val sshPort: Int = vendor.defaultSshPort,
    val username: String = "",
    val allowSelfSignedTls: Boolean = true,
    val extraContext: String = "", // VDOM / vsys / security context
    val passwordVaultKey: String = "",
    val apiTokenVaultKey: String = "",
    val hasStoredPassword: Boolean = false,
    val hasStoredToken: Boolean = false,
    val lastUsedTimestamp: Long = System.currentTimeMillis()
)

data class FirewallHealthSummary(
    val vendor: FirewallVendor,
    val applianceName: String,
    val hostname: String = "",
    val firmwareVersion: String = "",
    val uptimeText: String = "",
    val cpuPercent: Float = 0f,
    val mgmtCpuPercent: Float? = null,
    val dataPlaneCpuPercent: Float? = null,
    val memoryPercent: Float = 0f,
    val activeSessions: Long = 0L,
    val maxSessions: Long = 0L,
    val haRole: String = "Standalone",
    val haState: String = "Unknown",
    val lastUpdatedMs: Long = System.currentTimeMillis(),
    val statusNote: String = ""
)

data class FirewallIpsecTunnel(
    val id: String,
    val name: String,
    val remoteGateway: String,
    val localSubnet: String = "",
    val remoteSubnet: String = "",
    val phase1State: String = "",
    val phase2State: String = "",
    val isUp: Boolean = false,
    val bytesIn: Long = 0L,
    val bytesOut: Long = 0L,
    val spiOrSelector: String = "",
    val detailNote: String = ""
)

data class WanGatewaySlaEntry(
    val name: String,
    val interfaceName: String,
    val gatewayIp: String,
    val role: String, // Active, Backup, Master, Standby
    val latencyMs: Double,
    val jitterMs: Double,
    val packetLossPercent: Double,
    val status: String
)

data class FirewallDropLogEntry(
    val timestamp: String,
    val ruleOrReason: String,
    val srcIp: String,
    val dstIp: String,
    val protocolPort: String,
    val inInterface: String,
    val rawLine: String
)

data class FirewallPolicyHitEntry(
    val policyId: String,
    val name: String,
    val srcZoneOrIfc: String,
    val dstZoneOrIfc: String,
    val action: String,
    val hitCount: Long,
    val bytesTransferred: Long,
    val isZeroHit: Boolean = hitCount == 0L
)

data class GlobalProtectUserEntry(
    val username: String,
    val virtualIp: String,
    val publicIp: String,
    val clientOsOrVersion: String,
    val loginDuration: String
)

data class PanOsPolicyMatchResult(
    val matchedRuleName: String,
    val action: String,
    val sourceZone: String,
    val destZone: String,
    val application: String,
    val natRuleMatched: String = "",
    val translatedSrc: String = "",
    val translatedDst: String = "",
    val rawXmlResponse: String = ""
)

data class CiscoPacketTracerPhase(
    val phaseNumber: Int,
    val phaseType: String,
    val subtype: String,
    val configLine: String,
    val result: String,
    val extraInfo: String
)

data class CiscoPacketTracerResult(
    val verdict: String,
    val dropReason: String,
    val inputInterface: String,
    val outputInterface: String,
    val phases: List<CiscoPacketTracerPhase>,
    val rawOutput: String
)

data class CarpVipEntry(
    val vhid: String,
    val interfaceName: String,
    val vipAddress: String,
    val status: String,
    val advskew: String = ""
)

data class PfStateSummary(
    val totalStates: Long = 0L,
    val stateTableLimit: Long = 0L,
    val topProtocolFlows: List<String> = emptyList(),
    val dhcpLeaseCount: Int = 0,
    val unboundStatus: String = "Unknown",
    val recentLeases: List<String> = emptyList()
)

data class FirewallBackupResult(
    val vendor: FirewallVendor,
    val fileName: String,
    val fileSizeBytes: Long,
    val savedPath: String,
    val shareUriString: String,
    val summary: String
)

data class FirewallHubSnapshot(
    val health: FirewallHealthSummary? = null,
    val ipsecTunnels: List<FirewallIpsecTunnel> = emptyList(),
    val wanGateways: List<WanGatewaySlaEntry> = emptyList(),
    val dropLogs: List<FirewallDropLogEntry> = emptyList(),
    val policyHits: List<FirewallPolicyHitEntry> = emptyList(),
    val vpnUsers: List<GlobalProtectUserEntry> = emptyList(),
    val carpVips: List<CarpVipEntry> = emptyList(),
    val pfStateSummary: PfStateSummary? = null,
    val anyConnectCount: Int = 0,
    val asaConnCount: Long = 0L,
    val asaXlateCount: Long = 0L,
    val rawDiagnosticOutput: String = ""
)
