// === FILE: app/src/main/java/com/enterwebslinger/network/data/local/datastore/UserPreferencesDataStore.kt ===
package com.enterwebslinger.network.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.enterwebslinger.network.domain.model.CustomDeviceNickname
import com.enterwebslinger.network.domain.model.SavedWolProfile
import com.enterwebslinger.network.domain.model.ScanHistoryEntry
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
