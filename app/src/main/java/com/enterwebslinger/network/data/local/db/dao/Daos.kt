// === FILE: app/src/main/java/com/enterwebslinger/network/data/local/db/dao/Daos.kt ===
package com.enterwebslinger.network.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.enterwebslinger.network.data.local.db.entities.DiscoveredDeviceEntity
import com.enterwebslinger.network.data.local.db.entities.HostWatchLogEntity
import com.enterwebslinger.network.data.local.db.entities.SavedEndpointProfileEntity
import com.enterwebslinger.network.data.local.db.entities.ScanHistoryEntity
import com.enterwebslinger.network.data.local.db.entities.SyslogEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {
    @Query("SELECT * FROM discovered_devices ORDER BY lastSeenTimestamp DESC")
    fun getAllDevicesFlow(): Flow<List<DiscoveredDeviceEntity>>

    @Query("SELECT * FROM discovered_devices WHERE macAddress = :mac LIMIT 1")
    suspend fun getDeviceByMac(mac: String): DiscoveredDeviceEntity?

    @Query("SELECT * FROM discovered_devices WHERE ipAddress = :ip LIMIT 1")
    suspend fun getDeviceByIp(ip: String): DiscoveredDeviceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateDevice(device: DiscoveredDeviceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateDevices(devices: List<DiscoveredDeviceEntity>)

    @Query("UPDATE discovered_devices SET customNickname = :nickname WHERE macAddress = :mac")
    suspend fun updateNickname(mac: String, nickname: String)

    @Delete
    suspend fun deleteDevice(device: DiscoveredDeviceEntity)

    @Query("DELETE FROM discovered_devices")
    suspend fun clearAllDevices()
}

@Dao
interface ScanHistoryDao {
    @Query("SELECT * FROM scan_history ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentScansFlow(limit: Int = 100): Flow<List<ScanHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScan(scan: ScanHistoryEntity): Long

    @Query("DELETE FROM scan_history")
    suspend fun clearHistory()
}

@Dao
interface HostWatchDao {
    @Query("SELECT * FROM host_watch_logs WHERE host = :host ORDER BY timestamp DESC LIMIT :limit")
    fun getLogsForHostFlow(host: String, limit: Int = 100): Flow<List<HostWatchLogEntity>>

    @Query("SELECT * FROM host_watch_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getAllLogsFlow(limit: Int = 200): Flow<List<HostWatchLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: HostWatchLogEntity): Long

    @Query("DELETE FROM host_watch_logs WHERE timestamp < :beforeTimestamp")
    suspend fun pruneLogsOlderThan(beforeTimestamp: Long)

    @Query("DELETE FROM host_watch_logs")
    suspend fun clearAllLogs()
}

@Dao
interface SyslogDao {
    @Query("SELECT * FROM syslog_events ORDER BY timestamp DESC LIMIT :limit")
    fun getEventsFlow(limit: Int = 500): Flow<List<SyslogEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: SyslogEventEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<SyslogEventEntity>)

    @Query("DELETE FROM syslog_events WHERE id NOT IN (SELECT id FROM syslog_events ORDER BY timestamp DESC LIMIT :keepCount)")
    suspend fun pruneOldEvents(keepCount: Int = 5000)

    @Query("DELETE FROM syslog_events")
    suspend fun clearAllEvents()
}

@Dao
interface EndpointProfileDao {
    @Query("SELECT * FROM saved_endpoint_profiles ORDER BY lastUsedTimestamp DESC")
    fun getAllProfilesFlow(): Flow<List<SavedEndpointProfileEntity>>

    @Query("SELECT * FROM saved_endpoint_profiles WHERE profileType = :type ORDER BY lastUsedTimestamp DESC")
    fun getProfilesByTypeFlow(type: String): Flow<List<SavedEndpointProfileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: SavedEndpointProfileEntity): Long

    @Update
    suspend fun updateProfile(profile: SavedEndpointProfileEntity)

    @Delete
    suspend fun deleteProfile(profile: SavedEndpointProfileEntity)

    @Query("DELETE FROM saved_endpoint_profiles WHERE id = :id")
    suspend fun deleteProfileById(id: Long)
}
