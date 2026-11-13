// === FILE: app/src/main/java/com/enterwebslinger/network/domain/model/FingerprintModels.kt ===
package com.enterwebslinger.network.domain.model

enum class ScanProfile(val label: String, val description: String) {
    QUICK_SCAN("Quick Scan", "Top 25 standard enterprise ports with fast timeout"),
    INTENSE_SCAN("Intense Scan (-T4 -A -v)", "Top 100 ports + banner grab + TLS cert + OS heuristics"),
    PING_SWEEP("Ping Sweep", "ICMP & TCP host discovery across the subnet"),
    CUSTOM_RANGE("Custom Range", "Targeted user-defined port range")
}

data class ServiceBanner(
    val port: Int,
    val serviceName: String,
    val banner: String,
    val version: String = "",
    val product: String = "",
    val extraInfo: String = ""
)

data class TlsCertificateInfo(
    val port: Int,
    val subjectDn: String,
    val issuerDn: String,
    val serialNumber: String,
    val validFrom: String,
    val validTo: String,
    val subjectAltNames: List<String> = emptyList(),
    val tlsVersion: String,
    val cipherSuite: String,
    val keyAlgorithm: String = ""
)

data class OsHeuristicResult(
    val predictedOs: String,
    val confidence: Int,
    val initialTtlEstimate: Int,
    val tcpWindowSize: Int,
    val rationale: String
)

data class HostFingerprintReport(
    val host: String,
    val hostname: String = "",
    val isAlive: Boolean = true,
    val latencyMs: Long = 0L,
    val openPorts: List<Int> = emptyList(),
    val banners: List<ServiceBanner> = emptyList(),
    val tlsCertificates: List<TlsCertificateInfo> = emptyList(),
    val osEstimate: OsHeuristicResult? = null,
    val scanDurationMs: Long = 0L
)

data class TopologyNode(
    val id: String,
    val label: String,
    val ip: String,
    val isGateway: Boolean = false,
    val isLocalDevice: Boolean = false,
    val openPortCount: Int = 0,
    val osFamily: String = "Unknown",
    val latencyMs: Long = 0L,
    val xRatio: Float = 0.5f,
    val yRatio: Float = 0.5f
)

data class TopologyLink(
    val sourceId: String,
    val targetId: String,
    val latencyMs: Long = 0L
)

data class TopologyGraphData(
    val nodes: List<TopologyNode> = emptyList(),
    val links: List<TopologyLink> = emptyList()
)
