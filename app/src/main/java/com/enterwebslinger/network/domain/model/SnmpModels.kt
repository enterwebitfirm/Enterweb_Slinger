// === FILE: app/src/main/java/com/enterwebslinger/network/domain/model/SnmpModels.kt ===
package com.enterwebslinger.network.domain.model

enum class SnmpVersion(val wireValue: Int, val label: String) {
    V1(0, "SNMPv1"),
    V2C(1, "SNMPv2c"),
    V3(3, "SNMPv3")
}

enum class SnmpSecurityLevel(val label: String) {
    NO_AUTH_NO_PRIV("noAuthNoPriv"),
    AUTH_NO_PRIV("authNoPriv"),
    AUTH_PRIV("authPriv")
}

enum class SnmpAuthProtocol {
    NONE, MD5, SHA1, SHA256
}

enum class SnmpPrivProtocol {
    NONE, DES, AES128, AES256
}

data class SnmpTargetConfig(
    val host: String = "",
    val port: Int = 161,
    val version: SnmpVersion = SnmpVersion.V2C,
    val community: String = "public",
    val timeoutMs: Int = 2000,
    val retries: Int = 1,
    val securityName: String = "",
    val securityLevel: SnmpSecurityLevel = SnmpSecurityLevel.NO_AUTH_NO_PRIV,
    val authProtocol: SnmpAuthProtocol = SnmpAuthProtocol.NONE,
    val authPassword: String = "",
    val privProtocol: SnmpPrivProtocol = SnmpPrivProtocol.NONE,
    val privPassword: String = ""
)

data class SnmpVarBind(
    val oid: String,
    val syntax: String,
    val value: String
)

data class SnmpPduResult(
    val requestId: Int,
    val errorStatus: Int,
    val errorIndex: Int,
    val varBinds: List<SnmpVarBind>,
    val responseTimeMs: Long = 0L,
    val rawHex: String = ""
)

data class SnmpMibSummary(
    val sysDescr: String = "Unknown",
    val sysObjectId: String = "Unknown",
    val sysUpTime: String = "0:00:00.00",
    val sysContact: String = "N/A",
    val sysName: String = "Unknown",
    val sysLocation: String = "N/A",
    val cpuUtilization: Int? = null,
    val memoryUtilization: Int? = null,
    val interfaces: List<SnmpInterfaceEntry> = emptyList()
)

data class SnmpInterfaceEntry(
    val index: Int,
    val name: String,
    val type: String,
    val speedMbps: Long,
    val macAddress: String,
    val adminStatus: String,
    val operStatus: String,
    val inOctets: Long,
    val outOctets: Long,
    val inOctetsRateBps: Long = 0L,
    val outOctetsRateBps: Long = 0L,
    val inErrors: Long = 0L,
    val outErrors: Long = 0L
)

data class SnmpTrapRecord(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val sourceIp: String,
    val sourcePort: Int,
    val version: String,
    val trapOid: String,
    val genericTrap: Int? = null,
    val specificTrap: Int? = null,
    val uptimeString: String = "",
    val varBinds: List<SnmpVarBind> = emptyList(),
    val rawPayloadHex: String = ""
)
