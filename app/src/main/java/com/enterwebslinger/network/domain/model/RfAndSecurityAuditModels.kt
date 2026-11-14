// === FILE: app/src/main/java/com/enterwebslinger/network/domain/model/RfAndSecurityAuditModels.kt ===
package com.enterwebslinger.network.domain.model

// --- Floorplan Signal Heatmap & BLE Scanner ---

data class FloorplanPin(
    val id: String,
    val xRatio: Float,
    val yRatio: Float,
    val ssid: String,
    val bssid: String,
    val rssiDbm: Int,
    val linkSpeedMbps: Int,
    val frequencyMhz: Int,
    val timestampMs: Long = System.currentTimeMillis()
)

data class BleDeviceResult(
    val address: String,
    val name: String,
    val rssiDbm: Int,
    val txPowerDbm: Int?,
    val estimatedDistanceMeters: Double,
    val serviceUuids: List<String>,
    val isConnectable: Boolean,
    val manufacturerDataHex: String = "",
    val lastSeenMs: Long = System.currentTimeMillis()
)

// --- Cellular 5G NR / LTE RF Telemetry ---

enum class CellRadioType(val label: String) {
    NR_5G("5G NR"),
    LTE_4G("4G LTE"),
    WCDMA_3G("3G WCDMA"),
    GSM_2G("2G GSM"),
    UNKNOWN("No Cellular Radio")
}

data class CellularNeighborEntry(
    val radioType: CellRadioType,
    val pciOrPsc: Int,
    val arfcnOrEarfcn: Int,
    val bandLabel: String,
    val rsrpOrRscpDbm: Int,
    val rsrqOrEcNoDb: Int?,
    val isRegistered: Boolean
)

data class CellularTelemetryState(
    val operatorName: String = "Unavailable",
    val mcc: String = "--",
    val mnc: String = "--",
    val networkTypeLabel: String = "Unknown",
    val radioType: CellRadioType = CellRadioType.UNKNOWN,
    val voiceServiceState: String = "Unknown",
    val cellIdentity: Long = -1L,
    val pci: Int = -1,
    val tacOrLac: Int = -1,
    val arfcn: Int = -1,
    val bandLabel: String = "--",
    val rsrpDbm: Int? = null,
    val rsrqDb: Int? = null,
    val sinrDb: Int? = null,
    val timingAdvance: Int? = null,
    val neighbors: List<CellularNeighborEntry> = emptyList(),
    val lastUpdatedMs: Long = 0L,
    val errorMessage: String? = null
)

// --- ONVIF / IoT Camera Discovery & Evil Twin Auditor ---

data class OnvifCameraDevice(
    val ip: String,
    val hostName: String,
    val endpointUrl: String,
    val hardwareModel: String,
    val scopes: List<String>,
    val openPorts: List<Int>,
    val rtspUrls: List<String>,
    val discoveryMethod: String,
    val responseTimeMs: Long
)

enum class WifiSecurityFindingType(val label: String) {
    DUPLICATE_SSID_UNKNOWN_BSSID("Duplicate SSID / Unknown BSSID"),
    SECURITY_DOWNGRADE("Security Downgrade Detected"),
    HIDDEN_SSID_OVERLAPPING_CHANNEL("Hidden SSID on Overlapping Channel"),
    OPEN_UNENCRYPTED_AP("Open Unencrypted AP")
}

enum class WifiAuditSeverity(val label: String) {
    HIGH("HIGH"),
    MEDIUM("MEDIUM"),
    INFO("INFO")
}

data class TrustedApBaseline(
    val ssid: String,
    val bssid: String,
    val expectedSecurity: String
)

data class EvilTwinFinding(
    val ssid: String,
    val bssid: String,
    val securityCapabilities: String,
    val channel: Int,
    val band: String,
    val rssiDbm: Int,
    val severity: WifiAuditSeverity,
    val findingType: WifiSecurityFindingType,
    val reason: String
)
