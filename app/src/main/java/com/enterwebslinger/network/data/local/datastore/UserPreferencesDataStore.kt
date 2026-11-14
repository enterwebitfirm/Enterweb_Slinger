// === FILE: app/src/main/java/com/enterwebslinger/network/data/local/datastore/UserPreferencesDataStore.kt ===
package com.enterwebslinger.network.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.enterwebslinger.network.domain.model.AuditMonitorConfig
import com.enterwebslinger.network.domain.model.AuditRunSummary
import com.enterwebslinger.network.domain.model.CustomDeviceNickname
import com.enterwebslinger.network.domain.model.SavedWolProfile
import com.enterwebslinger.network.domain.model.ScanHistoryEntry
import com.enterwebslinger.network.domain.model.TrustedApBaseline
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "enterweb_slinger_prefs")

class UserPreferencesDataStore(private val context: Context) {

    private object PreferenceKeys {
        val WOL_PROFILES = stringPreferencesKey("wol_profiles_json")
        val CUSTOM_NICKNAMES = stringPreferencesKey("custom_nicknames_json")
        val SCAN_HISTORY = stringPreferencesKey("scan_history_json")
        val TRUSTED_APS = stringPreferencesKey("trusted_ap_baselines_json")
        val AUDIT_CONFIG = stringPreferencesKey("audit_monitor_config_json")
        val LAST_AUDIT_SUMMARY = stringPreferencesKey("last_audit_summary_json")
        val COLLECTOR_PREFS = stringPreferencesKey("collector_prefs_json")
        val GEMINI_FALLBACK_KEY = stringPreferencesKey("gemini_fallback_api_key")
    }

    val collectorPreferencesFlow: Flow<com.enterwebslinger.network.domain.model.CollectorPreferences> = context.dataStore.data.map { prefs ->
        parseCollectorPrefs(prefs[PreferenceKeys.COLLECTOR_PREFS] ?: "{}")
    }

    suspend fun saveCollectorPreferences(config: com.enterwebslinger.network.domain.model.CollectorPreferences) {
        context.dataStore.edit { prefs ->
            val obj = JSONObject().apply {
                put("syslogUdpPort", config.syslogUdpPort)
                put("syslogTcpPort", config.syslogTcpPort)
                put("snmpTrapPort", config.snmpTrapPort)
                put("netflowPort", config.netflowPort)
                put("ipfixPort", config.ipfixPort)
                put("sflowPort", config.sflowPort)
                put("radiusCoaPort", config.radiusCoaPort)
                put("mndpPort", config.mndpPort)
                put("autoStartCollectors", config.autoStartCollectors)
            }
            prefs[PreferenceKeys.COLLECTOR_PREFS] = obj.toString()
        }
    }

    val geminiFallbackKeyFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[PreferenceKeys.GEMINI_FALLBACK_KEY] ?: ""
    }

    suspend fun saveGeminiFallbackKey(apiKey: String) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.GEMINI_FALLBACK_KEY] = apiKey.trim()
        }
    }

    private fun parseCollectorPrefs(jsonStr: String): com.enterwebslinger.network.domain.model.CollectorPreferences {
        return try {
            val o = JSONObject(jsonStr)
            com.enterwebslinger.network.domain.model.CollectorPreferences(
                syslogUdpPort = o.optInt("syslogUdpPort", 1514),
                syslogTcpPort = o.optInt("syslogTcpPort", 6514),
                snmpTrapPort = o.optInt("snmpTrapPort", 1162),
                netflowPort = o.optInt("netflowPort", 2055),
                ipfixPort = o.optInt("ipfixPort", 4739),
                sflowPort = o.optInt("sflowPort", 6343),
                radiusCoaPort = o.optInt("radiusCoaPort", 3799),
                mndpPort = o.optInt("mndpPort", 5678),
                autoStartCollectors = o.optBoolean("autoStartCollectors", false)
            )
        } catch (_: Exception) {
            com.enterwebslinger.network.domain.model.CollectorPreferences()
        }
    }

    val wolProfilesFlow: Flow<List<SavedWolProfile>> = context.dataStore.data.map { prefs ->
        val jsonStr = prefs[PreferenceKeys.WOL_PROFILES] ?: "[]"
        parseWolProfiles(jsonStr)
    }

    suspend fun saveWolProfile(profile: SavedWolProfile) {
        context.dataStore.edit { prefs ->
            val existing = parseWolProfiles(prefs[PreferenceKeys.WOL_PROFILES] ?: "[]").toMutableList()
            existing.removeAll { it.id == profile.id || it.macAddress.equals(profile.macAddress, ignoreCase = true) }
            existing.add(0, profile)
            prefs[PreferenceKeys.WOL_PROFILES] = serializeWolProfiles(existing)
        }
    }

    suspend fun deleteWolProfile(id: String) {
        context.dataStore.edit { prefs ->
            val existing = parseWolProfiles(prefs[PreferenceKeys.WOL_PROFILES] ?: "[]").filter { it.id != id }
            prefs[PreferenceKeys.WOL_PROFILES] = serializeWolProfiles(existing)
        }
    }

    val customNicknamesFlow: Flow<Map<String, CustomDeviceNickname>> = context.dataStore.data.map { prefs ->
        val jsonStr = prefs[PreferenceKeys.CUSTOM_NICKNAMES] ?: "{}"
        parseCustomNicknames(jsonStr)
    }

    suspend fun setDeviceNickname(identifier: String, nickname: String, type: String = "Generic") {
        context.dataStore.edit { prefs ->
            val existing = parseCustomNicknames(prefs[PreferenceKeys.CUSTOM_NICKNAMES] ?: "{}").toMutableMap()
            existing[identifier] = CustomDeviceNickname(identifier, nickname, type)
            prefs[PreferenceKeys.CUSTOM_NICKNAMES] = serializeCustomNicknames(existing)
        }
    }

    val scanHistoryFlow: Flow<List<ScanHistoryEntry>> = context.dataStore.data.map { prefs ->
        val jsonStr = prefs[PreferenceKeys.SCAN_HISTORY] ?: "[]"
        parseScanHistory(jsonStr)
    }

    suspend fun appendScanHistory(entry: ScanHistoryEntry) {
        context.dataStore.edit { prefs ->
            val existing = parseScanHistory(prefs[PreferenceKeys.SCAN_HISTORY] ?: "[]").toMutableList()
            existing.add(0, entry)
            val trimmed = existing.take(20)
            prefs[PreferenceKeys.SCAN_HISTORY] = serializeScanHistory(trimmed)
        }
    }

    val trustedApBaselinesFlow: Flow<List<TrustedApBaseline>> = context.dataStore.data.map { prefs ->
        parseTrustedAps(prefs[PreferenceKeys.TRUSTED_APS] ?: "[]")
    }

    suspend fun saveTrustedApBaseline(baseline: TrustedApBaseline) {
        context.dataStore.edit { prefs ->
            val existing = parseTrustedAps(prefs[PreferenceKeys.TRUSTED_APS] ?: "[]").toMutableList()
            existing.removeAll { it.bssid.equals(baseline.bssid, ignoreCase = true) }
            existing.add(0, baseline)
            prefs[PreferenceKeys.TRUSTED_APS] = serializeTrustedAps(existing.take(30))
        }
    }

    val auditMonitorConfigFlow: Flow<AuditMonitorConfig> = context.dataStore.data.map { prefs ->
        parseAuditConfig(prefs[PreferenceKeys.AUDIT_CONFIG] ?: "{}")
    }

    suspend fun saveAuditMonitorConfig(config: AuditMonitorConfig) {
        context.dataStore.edit { prefs ->
            val obj = JSONObject().apply {
                put("enabled", config.enabled)
                put("intervalMinutes", config.intervalMinutes)
                put("trustedSsid", config.trustedSsid)
                put("trustedMacWhitelist", JSONArray(config.trustedMacWhitelist))
                put("watchedHosts", JSONArray(config.watchedHosts))
            }
            prefs[PreferenceKeys.AUDIT_CONFIG] = obj.toString()
        }
    }

    val lastAuditSummaryFlow: Flow<AuditRunSummary> = context.dataStore.data.map { prefs ->
        parseAuditSummary(prefs[PreferenceKeys.LAST_AUDIT_SUMMARY] ?: "{}")
    }

    suspend fun saveAuditSummary(summary: AuditRunSummary) {
        context.dataStore.edit { prefs ->
            val obj = JSONObject().apply {
                put("timestampMs", summary.timestampMs)
                put("ssid", summary.ssid)
                put("arpCount", summary.arpCount)
                put("unknownMacsFound", JSONArray(summary.unknownMacsFound))
                put("offlineHosts", JSONArray(summary.offlineHosts))
                put("gatewayReachable", summary.gatewayReachable)
                put("statusMessage", summary.statusMessage)
            }
            prefs[PreferenceKeys.LAST_AUDIT_SUMMARY] = obj.toString()
        }
    }

    private fun parseTrustedAps(jsonStr: String): List<TrustedApBaseline> {
        return try {
            val arr = JSONArray(jsonStr)
            List(arr.length()) { idx ->
                val o = arr.getJSONObject(idx)
                TrustedApBaseline(
                    ssid = o.optString("ssid", ""),
                    bssid = o.optString("bssid", ""),
                    expectedSecurity = o.optString("expectedSecurity", "WPA2")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun serializeTrustedAps(list: List<TrustedApBaseline>): String {
        val arr = JSONArray()
        list.forEach { ap ->
            arr.put(JSONObject().apply {
                put("ssid", ap.ssid)
                put("bssid", ap.bssid)
                put("expectedSecurity", ap.expectedSecurity)
            })
        }
        return arr.toString()
    }

    private fun parseAuditConfig(jsonStr: String): AuditMonitorConfig {
        return try {
            val obj = JSONObject(jsonStr)
            val macArr = obj.optJSONArray("trustedMacWhitelist")
            val hostArr = obj.optJSONArray("watchedHosts")
            AuditMonitorConfig(
                enabled = obj.optBoolean("enabled", false),
                intervalMinutes = obj.optLong("intervalMinutes", 15L),
                trustedSsid = obj.optString("trustedSsid", ""),
                trustedMacWhitelist = macArr?.let { a -> List(a.length()) { a.optString(it) }.filter { it.isNotBlank() } } ?: emptyList(),
                watchedHosts = hostArr?.let { a -> List(a.length()) { a.optString(it) }.filter { it.isNotBlank() } } ?: emptyList()
            )
        } catch (_: Exception) {
            AuditMonitorConfig()
        }
    }

    private fun parseAuditSummary(jsonStr: String): AuditRunSummary {
        return try {
            val obj = JSONObject(jsonStr)
            val unknownArr = obj.optJSONArray("unknownMacsFound")
            val offlineArr = obj.optJSONArray("offlineHosts")
            AuditRunSummary(
                timestampMs = obj.optLong("timestampMs", 0L),
                ssid = obj.optString("ssid", ""),
                arpCount = obj.optInt("arpCount", 0),
                unknownMacsFound = unknownArr?.let { a -> List(a.length()) { a.optString(it) } } ?: emptyList(),
                offlineHosts = offlineArr?.let { a -> List(a.length()) { a.optString(it) } } ?: emptyList(),
                gatewayReachable = obj.optBoolean("gatewayReachable", false),
                statusMessage = obj.optString("statusMessage", "No scheduled audit executed yet")
            )
        } catch (_: Exception) {
            AuditRunSummary()
        }
    }

    private fun parseWolProfiles(jsonStr: String): List<SavedWolProfile> {
        return try {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<SavedWolProfile>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    SavedWolProfile(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        macAddress = obj.getString("macAddress"),
                        ipAddress = obj.optString("ipAddress", "")
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun serializeWolProfiles(list: List<SavedWolProfile>): String {
        val arr = JSONArray()
        for (item in list) {
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("name", item.name)
            obj.put("macAddress", item.macAddress)
            obj.put("ipAddress", item.ipAddress)
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun parseCustomNicknames(jsonStr: String): Map<String, CustomDeviceNickname> {
        return try {
            val obj = JSONObject(jsonStr)
            val map = mutableMapOf<String, CustomDeviceNickname>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val item = obj.getJSONObject(key)
                map[key] = CustomDeviceNickname(
                    identifier = item.getString("identifier"),
                    customName = item.getString("customName"),
                    customType = item.optString("customType", "Generic")
                )
            }
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun serializeCustomNicknames(map: Map<String, CustomDeviceNickname>): String {
        val root = JSONObject()
        for ((k, v) in map) {
            val item = JSONObject()
            item.put("identifier", v.identifier)
            item.put("customName", v.customName)
            item.put("customType", v.customType)
            root.put(k, item)
        }
        return root.toString()
    }

    private fun parseScanHistory(jsonStr: String): List<ScanHistoryEntry> {
        return try {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<ScanHistoryEntry>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    ScanHistoryEntry(
                        timestamp = obj.getLong("timestamp"),
                        ssid = obj.getString("ssid"),
                        deviceCount = obj.getInt("deviceCount"),
                        summary = obj.optString("summary", "")
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun serializeScanHistory(list: List<ScanHistoryEntry>): String {
        val arr = JSONArray()
        for (item in list) {
            val obj = JSONObject()
            obj.put("timestamp", item.timestamp)
            obj.put("ssid", item.ssid)
            obj.put("deviceCount", item.deviceCount)
            obj.put("summary", item.summary)
            arr.put(obj)
        }
        return arr.toString()
    }
}
