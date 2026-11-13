// === FILE: app/src/main/java/com/enterwebslinger/network/data/repository/LibreNmsRepository.kt ===
package com.enterwebslinger.network.data.repository

import com.enterwebslinger.network.domain.model.LibreNmsAlert
import com.enterwebslinger.network.domain.model.LibreNmsDevice
import com.enterwebslinger.network.domain.model.LibreNmsHealthSensor
import com.enterwebslinger.network.domain.model.LibreNmsServerConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class LibreNmsRepository {

    private fun buildOkHttpClient(ignoreSsl: Boolean): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)

        if (ignoreSsl) {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustAllCerts, SecureRandom())
            builder.sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            builder.hostnameVerifier { _, _ -> true }
        }
        return builder.build()
    }

    suspend fun testConnection(config: LibreNmsServerConfig): Result<String> {
        if (config.serverUrl.isBlank()) return Result.failure(IllegalArgumentException("LibreNMS Server URL is required"))
        return withContext(Dispatchers.IO) {
            try {
                val client = buildOkHttpClient(config.ignoreSslErrors)
                val cleanUrl = config.serverUrl.trimEnd('/')
                val request = Request.Builder()
                    .url("$cleanUrl/api/v0/system")
                    .header("X-Auth-Token", config.apiToken)
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val json = JSONObject(body)
                        val version = json.optString("version", "LibreNMS Connected")
                        Result.success("Connection Successful: $version")
                    } else {
                        Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getDevices(config: LibreNmsServerConfig): Result<List<LibreNmsDevice>> {
        if (config.serverUrl.isBlank()) return Result.failure(IllegalArgumentException("LibreNMS Server URL is required"))
        return withContext(Dispatchers.IO) {
            try {
                val client = buildOkHttpClient(config.ignoreSslErrors)
                val cleanUrl = config.serverUrl.trimEnd('/')
                val request = Request.Builder()
                    .url("$cleanUrl/api/v0/devices")
                    .header("X-Auth-Token", config.apiToken)
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: "{}"
                        val json = JSONObject(body)
                        val devicesArray = json.optJSONArray("devices") ?: JSONArray()
                        val list = mutableListOf<LibreNmsDevice>()

                        for (i in 0 until devicesArray.length()) {
                            val d = devicesArray.getJSONObject(i)
                            list.add(
                                LibreNmsDevice(
                                    deviceId = d.optInt("device_id", i),
                                    hostname = d.optString("hostname", "Device-$i"),
                                    ip = d.optString("ip", ""),
                                    sysName = d.optString("sysName", ""),
                                    os = d.optString("os", "generic"),
                                    hardware = d.optString("hardware", ""),
                                    version = d.optString("version", ""),
                                    status = d.optInt("status", 0) == 1,
                                    uptimeSeconds = d.optLong("uptime", 0L),
                                    location = d.optString("location", ""),
                                    pingLatencyMs = d.optDouble("ping_time", 0.0).toFloat()
                                )
                            )
                        }
                        Result.success(list)
                    } else {
                        Result.failure(Exception("HTTP ${response.code} fetching devices"))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getAlerts(config: LibreNmsServerConfig): Result<List<LibreNmsAlert>> {
        if (config.serverUrl.isBlank()) return Result.failure(IllegalArgumentException("LibreNMS Server URL is required"))
        return withContext(Dispatchers.IO) {
            try {
                val client = buildOkHttpClient(config.ignoreSslErrors)
                val cleanUrl = config.serverUrl.trimEnd('/')
                val request = Request.Builder()
                    .url("$cleanUrl/api/v0/alerts")
                    .header("X-Auth-Token", config.apiToken)
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: "{}"
                        val json = JSONObject(body)
                        val alertsArray = json.optJSONArray("alerts") ?: JSONArray()
                        val list = mutableListOf<LibreNmsAlert>()

                        for (i in 0 until alertsArray.length()) {
                            val a = alertsArray.getJSONObject(i)
                            list.add(
                                LibreNmsAlert(
                                    alertId = a.optInt("id", i),
                                    deviceId = a.optInt("device_id", 0),
                                    deviceHostname = a.optString("hostname", "Host-${a.optInt("device_id", 0)}"),
                                    ruleName = a.optString("rule", ""),
                                    severity = a.optString("severity", "warning"),
                                    state = a.optInt("state", 1),
                                    timestamp = a.optString("time_logged", ""),
                                    isAcknowledged = a.optInt("state", 1) == 2,
                                    ackMessage = a.optString("note", "")
                                )
                            )
                        }
                        Result.success(list)
                    } else {
                        Result.failure(Exception("HTTP ${response.code} fetching alerts"))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun acknowledgeAlert(config: LibreNmsServerConfig, alertId: Int, note: String = "Acknowledged via Enterweb Slinger NOC"): Result<Boolean> {
        if (config.serverUrl.isBlank()) return Result.failure(IllegalArgumentException("LibreNMS Server URL is required"))
        return withContext(Dispatchers.IO) {
            try {
                val client = buildOkHttpClient(config.ignoreSslErrors)
                val cleanUrl = config.serverUrl.trimEnd('/')
                val payload = JSONObject().apply {
                    put("state", "ack")
                    put("note", note)
                }
                val requestBody = payload.toString().toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url("$cleanUrl/api/v0/alerts/$alertId")
                    .header("X-Auth-Token", config.apiToken)
                    .put(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        Result.success(true)
                    } else {
                        Result.failure(Exception("HTTP ${response.code}: ${response.message} acknowledging alert #$alertId"))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getDeviceSensors(config: LibreNmsServerConfig, deviceId: Int): Result<List<LibreNmsHealthSensor>> {
        if (config.serverUrl.isBlank()) return Result.failure(IllegalArgumentException("LibreNMS Server URL is required"))
        return withContext(Dispatchers.IO) {
            try {
                val client = buildOkHttpClient(config.ignoreSslErrors)
                val cleanUrl = config.serverUrl.trimEnd('/')
                val request = Request.Builder()
                    .url("$cleanUrl/api/v0/devices/$deviceId/health")
                    .header("X-Auth-Token", config.apiToken)
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: "{}"
                        val json = JSONObject(body)
                        val sensorsArray = json.optJSONArray("health") ?: JSONArray()
                        val list = mutableListOf<LibreNmsHealthSensor>()

                        for (i in 0 until sensorsArray.length()) {
                            val s = sensorsArray.getJSONObject(i)
                            val sClass = s.optString("sensor_class", "temperature")
                            val unit = when (sClass) {
                                "temperature" -> "°C"
                                "voltage" -> "V"
                                "fanspeed" -> "RPM"
                                "power" -> "W"
                                "dBm" -> "dBm"
                                else -> ""
                            }
                            list.add(
                                LibreNmsHealthSensor(
                                    sensorId = s.optInt("sensor_id", i),
                                    deviceId = deviceId,
                                    sensorClass = sClass,
                                    sensorDescr = s.optString("sensor_descr", "Sensor $i"),
                                    currentValue = s.optDouble("sensor_current", 0.0),
                                    unit = unit,
                                    limitHigh = s.optDouble("sensor_limit", 0.0).takeIf { it > 0.0 },
                                    status = s.optString("sensor_status", "ok")
                                )
                            )
                        }
                        Result.success(list)
                    } else {
                        Result.failure(Exception("HTTP ${response.code} fetching sensors"))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    fun getPortGraphUrl(config: LibreNmsServerConfig, deviceId: Int, portId: Int): String {
        val cleanUrl = config.serverUrl.trimEnd('/')
        return "$cleanUrl/api/v0/devices/$deviceId/ports/$portId/port_bits.png"
    }
}
