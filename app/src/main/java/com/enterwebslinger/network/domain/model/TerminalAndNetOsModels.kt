// === FILE: app/src/main/java/com/enterwebslinger/network/domain/model/TerminalAndNetOsModels.kt ===
package com.enterwebslinger.network.domain.model

enum class TerminalProtocol(val displayName: String, val defaultPort: Int) {
    SSH("SSHv2", 22),
    TELNET("Telnet (NVT)", 23),
    NETCONF("NETCONF (SSH)", 830)
}

enum class VendorProfile(val displayName: String) {
    CISCO_IOS("Cisco IOS / IOS-XE"),
    CISCO_NXOS("Cisco NX-OS"),
    JUNIPER_JUNOS("Juniper JUNOS")
}

data class ParsedVersionInfo(
    val vendor: String,
    val osVersion: String,
    val model: String,
    val uptime: String,
    val serialNumber: String,
    val memory: String,
    val rawText: String
)

data class ParsedInterfaceBrief(
    val interfaceName: String,
    val ipAddress: String,
    val status: String,
    val protocol: String,
    val speed: String = "Auto",
    val duplex: String = "Auto"
)

data class ParsedRoute(
    val network: String,
    val prefixLength: Int,
    val protocol: String,
    val nextHop: String,
    val metric: String,
    val interfaceName: String
)

data class ParsedNeighbor(
    val deviceId: String,
    val localInterface: String,
    val remoteInterface: String,
    val platform: String,
    val capabilities: String
)

enum class DiffLineType {
    ADDED,
    REMOVED,
    UNCHANGED
}

data class DiffLine(
    val type: DiffLineType,
    val lineNumber: Int,
    val text: String
)

data class ConfigDiffResult(
    val addedCount: Int,
    val removedCount: Int,
    val lines: List<DiffLine>
)

data class NetconfRpcResult(
    val rpcXml: String,
    val responseXml: String,
    val isSuccess: Boolean,
    val executionTimeMs: Long,
    val errorMessage: String? = null
)
