// === FILE: app/src/main/java/com/enterwebslinger/network/domain/model/NetworkModels.kt ===
package com.enterwebslinger.network.domain.model

typealias NetworkTopologyState = NetworkTopology

data class NetworkTopology(
    val isConnected: Boolean = false,
    val networkType: String = "None",
    val publicIp: String = "Unavailable",
    val localIpv4: String = "Unavailable",
    val localIpv6: String = "Unavailable",
    val subnetMask: String = "Unavailable",
    val subnetCidr: String = "",
    val defaultGateway: String = "Unavailable",
    val dnsServers: List<String> = emptyList(),
    val dhcpLeaseServer: String = "Unavailable",
    val dhcpLeaseDurationSec: Int = 0,
    val ssid: String = "Unknown SSID",
    val bssid: String = "--:--:--:--:--:--",
    val frequencyBand: String = "Unknown",
    val frequencyMhz: Int = 0,
    val linkSpeedMbps: Int = 0,
    val rssiDbm: Int = -100,
    val signalPercent: Int = 0
)

data class RssiDataPoint(
    val timestamp: Long,
    val rssiDbm: Int,
    val linkSpeedMbps: Int
)

data class DiscoveredDevice(
    val ip: String,
    val mac: String = "Unavailable",
    val hostname: String = "Unknown",
    val vendor: String = "Generic Device",
    val responseTimeMs: Long = 0L,
    val isAlive: Boolean = true,
    val services: List<String> = emptyList(),
    val openPorts: List<Int> = emptyList(),
    val customLabel: String? = null,
    val deviceType: String = "Generic",
    val locationUrl: String? = null,
    val serverHeader: String? = null
)

data class PingResult(
    val seq: Int,
    val host: String,
    val ip: String,
    val rttMs: Float,
    val isSuccess: Boolean,
    val ttl: Int = 64,
    val errorMessage: String? = null
)

data class PingSummary(
    val host: String = "",
    val ip: String = "",
    val protocol: String = "ICMP",
    val packetsSent: Int = 0,
    val packetsReceived: Int = 0,
    val packetLossPercent: Float = 0f,
    val minRttMs: Float = 0f,
    val avgRttMs: Float = 0f,
    val maxRttMs: Float = 0f,
    val jitterMs: Float = 0f,
    val isRunning: Boolean = false,
    val results: List<PingResult> = emptyList()
)

data class TracerouteHop(
    val hopNumber: Int,
    val ip: String,
    val hostname: String = "",
    val rttMs: Float = 0f,
    val isCompleted: Boolean = false,
    val isTimeout: Boolean = false
)

data class DnsRecord(
    val type: String,
    val name: String,
    val data: String,
    val ttl: Int,
    val rcode: String = "NOERROR"
)

data class PortScanResult(
    val port: Int,
    val serviceName: String,
    val isOpen: Boolean,
    val isHttp: Boolean = false,
    val banner: String? = null
)

data class HostWatcherTarget(
    val id: String,
    val host: String,
    val label: String,
    val checkIntervalSec: Int = 10,
    val isUp: Boolean = true,
    val lastLatencyMs: Long = 0L,
    val totalChecks: Int = 0,
    val upChecks: Int = 0,
    val lastCheckedTimestamp: Long = 0L,
    val recentHistory: List<Boolean> = emptyList()
) {
    val uptimePercent: Float
        get() = if (totalChecks > 0) (upChecks.toFloat() / totalChecks * 100f) else 0f
}

enum class SpeedPhase {
    IDLE, PING, DOWNLOAD, UPLOAD, COMPLETE, ERROR
}

data class SpeedTestState(
    val phase: SpeedPhase = SpeedPhase.IDLE,
    val progress: Float = 0f,
    val currentRateMbps: Float = 0f,
    val downloadSpeedMbps: Float = 0f,
    val uploadSpeedMbps: Float = 0f,
    val unloadedPingMs: Float = 0f,
    val loadedPingMs: Float = 0f,
    val bufferbloatDeltaMs: Float = 0f,
    val statusText: String = "Ready to Test",
    val liveRateHistory: List<Float> = emptyList()
)

data class WifiScanEntry(
    val ssid: String,
    val bssid: String,
    val rssiDbm: Int,
    val frequencyMhz: Int,
    val band: String,
    val channel: Int,
    val channelWidthMhz: Int,
    val security: String,
    val isCurrentNetwork: Boolean = false
)

data class SavedWolProfile(
    val id: String,
    val name: String,
    val macAddress: String,
    val ipAddress: String = ""
)

data class CustomDeviceNickname(
    val identifier: String,
    val customName: String,
    val customType: String = "Generic"
)

data class ScanHistoryEntry(
    val timestamp: Long,
    val ssid: String,
    val deviceCount: Int,
    val summary: String
)

data class WhoisLookupResult(
    val query: String,
    val serverUsed: String,
    val referralChain: List<String> = emptyList(),
    val registrar: String = "",
    val creationDate: String = "",
    val expiryDate: String = "",
    val nameServers: List<String> = emptyList(),
    val asnInfo: String = "",
    val rawOutput: String = ""
)

data class ChannelCongestionInfo(
    val channel: Int,
    val band: String,
    val apCount: Int,
    val congestionPercent: Int,
    val strongestRssiDbm: Int,
    val isRecommended: Boolean = false
)

