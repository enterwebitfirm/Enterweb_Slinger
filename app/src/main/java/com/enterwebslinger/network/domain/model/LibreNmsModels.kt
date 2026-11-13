// === FILE: app/src/main/java/com/enterwebslinger/network/domain/model/LibreNmsModels.kt ===
package com.enterwebslinger.network.domain.model

data class LibreNmsServerConfig(
    val serverUrl: String = "",
    val apiToken: String = "",
    val ignoreSslErrors: Boolean = true
)

data class LibreNmsDevice(
    val deviceId: Int,
    val hostname: String,
    val ip: String,
    val sysName: String = "",
    val os: String = "generic",
    val hardware: String = "",
    val version: String = "",
    val status: Boolean = true,
    val uptimeSeconds: Long = 0L,
    val location: String = "",
    val pingLatencyMs: Float = 0f,
    val iconUrl: String = ""
)

data class LibreNmsAlert(
    val alertId: Int,
    val deviceId: Int,
    val deviceHostname: String,
    val ruleName: String,
    val severity: String,
    val state: Int,
    val timestamp: String,
    val isAcknowledged: Boolean = false,
    val ackMessage: String = ""
)

data class LibreNmsPort(
    val portId: Int,
    val deviceId: Int,
    val ifName: String,
    val ifDescr: String,
    val ifAlias: String,
    val ifSpeed: Long,
    val ifOperStatus: String,
    val inRateBps: Long = 0L,
    val outRateBps: Long = 0L,
    val graphUrl: String = ""
)

data class LibreNmsHealthSensor(
    val sensorId: Int,
    val deviceId: Int,
    val sensorClass: String,
    val sensorDescr: String,
    val currentValue: Double,
    val unit: String,
    val limitHigh: Double? = null,
    val limitLow: Double? = null,
    val status: String = "ok"
)
