// === FILE: app/src/main/java/com/enterwebslinger/network/domain/model/FlowModels.kt ===
package com.enterwebslinger.network.domain.model

enum class FlowProtocolType(val displayName: String, val defaultPort: Int) {
    NETFLOW_V5("NetFlow v5", 2055),
    NETFLOW_V9("NetFlow v9", 2055),
    IPFIX("IPFIX (v10)", 4739),
    SFLOW_V5("sFlow v5", 6343)
}

data class FlowRecord(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val protocolType: FlowProtocolType,
    val srcIp: String,
    val dstIp: String,
    val srcPort: Int,
    val dstPort: Int,
    val ipProtocol: Int,
    val protocolName: String,
    val bytes: Long,
    val packets: Long,
    val tcpFlags: String = "",
    val dscp: Int = 0,
    val dscpClass: String = "CS0 (Best Effort)",
    val srcAs: Int = 0,
    val dstAs: Int = 0,
    val inputIfIndex: Int = 0,
    val outputIfIndex: Int = 0
)

data class TopTalkerEntry(
    val conversation: String,
    val srcIp: String,
    val dstIp: String,
    val serviceOrPort: String,
    val totalBytes: Long,
    val totalPackets: Long,
    val percentageOfTotal: Float = 0f
)

data class ProtocolDistribution(
    val protocol: String,
    val byteCount: Long,
    val packetCount: Long,
    val percentage: Float
)

data class DscpDistribution(
    val dscpClass: String,
    val packets: Long,
    val percentage: Float
)

data class FlowCollectorStats(
    val isRunning: Boolean = false,
    val boundPorts: List<Int> = emptyList(),
    val totalPacketsReceived: Long = 0L,
    val totalFlowsDecoded: Long = 0L,
    val totalBytesProcessed: Long = 0L,
    val currentBytesPerSec: Long = 0L,
    val currentPacketsPerSec: Long = 0L,
    val droppedPackets: Long = 0L,
    val activeTemplatesCount: Int = 0,
    val errorMessage: String? = null
)
