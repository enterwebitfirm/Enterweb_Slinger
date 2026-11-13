// === FILE: app/src/main/java/com/enterwebslinger/network/domain/model/CoreProtocolsModels.kt ===
package com.enterwebslinger.network.domain.model

data class DhcpServerOffer(
    val serverIp: String,
    val offeredIp: String,
    val subnetMask: String,
    val routers: List<String>,
    val dnsServers: List<String>,
    val domainName: String,
    val leaseTimeSeconds: Long,
    val tftpServer: String,
    val voipTftpServer: String,
    val isRogue: Boolean,
    val responseTimeMs: Long
)

data class TodResult(
    val serverHost: String,
    val port: Int,
    val raw32BitSeconds: Long,
    val epochSecondsUtc: Long,
    val formattedUtcTime: String,
    val clockOffsetMs: Long,
    val latencyMs: Long,
    val isSuccess: Boolean,
    val errorMessage: String? = null
)

data class NtpResult(
    val serverHost: String,
    val stratum: Int,
    val leapIndicator: Int,
    val referenceId: String,
    val roundTripDelayMs: Double,
    val clockOffsetMs: Double,
    val transmitTimeFormatted: String,
    val isSuccess: Boolean,
    val errorMessage: String? = null
)

data class SmtpDiagResult(
    val host: String,
    val port: Int,
    val banner: String,
    val ehloKeywords: List<String>,
    val startTlsSupported: Boolean,
    val isTlsActive: Boolean,
    val openRelayVulnerable: Boolean,
    val transcriptLogs: List<String>,
    val transactionLatencyMs: Long,
    val isSuccess: Boolean,
    val errorMessage: String? = null
)
