// === FILE: app/src/main/java/com/enterwebslinger/network/data/local/db/entities/Entities.kt ===
package com.enterwebslinger.network.data.local.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "discovered_devices")
data class DiscoveredDeviceEntity(
    @PrimaryKey
    val macAddress: String,
    val ipAddress: String,
    val hostname: String = "",
    val customNickname: String = "",
    val vendor: String = "Unknown Vendor",
    val isRandomizedMac: Boolean = false,
    val openPortsJson: String = "[]",
    val servicesJson: String = "[]",
    val osFingerprint: String = "Unknown",
    val lastSeenTimestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "scan_history")
data class ScanHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val scanType: String,
    val target: String,
    val deviceCount: Int = 0,
    val durationMs: Long = 0,
    val summary: String = ""
)

@Entity(tableName = "host_watch_logs")
data class HostWatchLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val host: String,
    val ipAddress: String = "",
    val isUp: Boolean,
    val latencyMs: Double = -1.0,
    val httpStatus: Int? = null,
    val errorMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "syslog_events")
data class SyslogEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val facility: String,
    val severity: String,
    val sourceHost: String,
    val appName: String = "",
    val message: String
)

@Entity(tableName = "saved_endpoint_profiles")
data class SavedEndpointProfileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val profileType: String,
    val name: String,
    val host: String,
    val port: Int,
    val username: String = "",
    val credentialVaultKey: String = "",
    val extraConfigJson: String = "{}",
    val lastUsedTimestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "saved_aaa_servers")
data class SavedAaaServerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val kind: String,
    val host: String,
    val port: Int,
    val username: String = "",
    val nasIp: String = "127.0.0.1",
    val nasIdentifier: String = "EnterwebSlinger",
    val baseDn: String = "",
    val useTls: Boolean = false,
    val allowSelfSignedTls: Boolean = true,
    val credentialVaultKey: String = "",
    val lastUsedTimestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "audit_reports")
data class AuditReportEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val targetLabel: String,
    val category: String,
    val healthScore: Int = 0,
    val severity: String = "INFO",
    val summary: String,
    val reportJson: String = "{}"
)


