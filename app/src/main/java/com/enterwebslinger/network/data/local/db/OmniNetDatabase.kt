// === FILE: app/src/main/java/com/enterwebslinger/network/data/local/db/OmniNetDatabase.kt ===
package com.enterwebslinger.network.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.enterwebslinger.network.data.local.db.dao.AaaServerProfileDao
import com.enterwebslinger.network.data.local.db.dao.AuditReportDao
import com.enterwebslinger.network.data.local.db.dao.DeviceDao
import com.enterwebslinger.network.data.local.db.dao.EndpointProfileDao
import com.enterwebslinger.network.data.local.db.dao.HostWatchDao
import com.enterwebslinger.network.data.local.db.dao.ScanHistoryDao
import com.enterwebslinger.network.data.local.db.dao.SyslogDao
import com.enterwebslinger.network.data.local.db.entities.AuditReportEntity
import com.enterwebslinger.network.data.local.db.entities.DiscoveredDeviceEntity
import com.enterwebslinger.network.data.local.db.entities.HostWatchLogEntity
import com.enterwebslinger.network.data.local.db.entities.SavedAaaServerEntity
import com.enterwebslinger.network.data.local.db.entities.SavedEndpointProfileEntity
import com.enterwebslinger.network.data.local.db.entities.ScanHistoryEntity
import com.enterwebslinger.network.data.local.db.entities.SyslogEventEntity

@Database(
    entities = [
        DiscoveredDeviceEntity::class,
        ScanHistoryEntity::class,
        HostWatchLogEntity::class,
        SyslogEventEntity::class,
        SavedEndpointProfileEntity::class,
        SavedAaaServerEntity::class,
        AuditReportEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class OmniNetDatabase : RoomDatabase() {
    abstract fun deviceDao(): DeviceDao
    abstract fun scanHistoryDao(): ScanHistoryDao
    abstract fun hostWatchDao(): HostWatchDao
    abstract fun syslogDao(): SyslogDao
    abstract fun endpointProfileDao(): EndpointProfileDao
    abstract fun aaaServerProfileDao(): AaaServerProfileDao
    abstract fun auditReportDao(): AuditReportDao

    companion object {
        @Volatile
        private var INSTANCE: OmniNetDatabase? = null

        fun getInstance(context: Context): OmniNetDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    OmniNetDatabase::class.java,
                    "omninet_production.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
