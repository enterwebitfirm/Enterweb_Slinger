// === FILE: app/src/main/java/com/enterwebslinger/network/data/repository/OobHardwareRepository.kt ===
package com.enterwebslinger.network.data.repository

import com.enterwebslinger.network.core.util.NetworkByteUtils
import com.enterwebslinger.network.domain.model.AsfPresenceResult
import com.enterwebslinger.network.domain.model.BootDevice
import com.enterwebslinger.network.domain.model.FanSensor
import com.enterwebslinger.network.domain.model.IpmiChassisStatus
import com.enterwebslinger.network.domain.model.PowerAction
import com.enterwebslinger.network.domain.model.PowerSupply
import com.enterwebslinger.network.domain.model.RedfishServerSummary
import com.enterwebslinger.network.domain.model.SelEntry
import com.enterwebslinger.network.domain.model.ServerPowerState
import com.enterwebslinger.network.domain.model.ThermalSensor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class OobHardwareRepository {

    // OkHttp client configured to trust self-signed BMC certificates
    private val unsafeHttpClient: OkHttpClient by lazy {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, trustAllCerts, SecureRandom())
        }

        OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private val defaultHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private fun clientFor(ignoreSslErrors: Boolean): OkHttpClient =
        if (ignoreSslErrors) unsafeHttpClient else defaultHttpClient

    private fun resolveRedfishUrl(rootOrigin: String, pathOrUrl: String): String {
        val cleaned = pathOrUrl.trim()
        return when {
            cleaned.startsWith("http://", ignoreCase = true) ||
                cleaned.startsWith("https://", ignoreCase = true) -> cleaned
            cleaned.startsWith("/") -> "$rootOrigin$cleaned"
            else -> "$rootOrigin/$cleaned"
        }
    }

    private fun fetchRedfishJson(
        client: OkHttpClient,
        url: String,
        credential: String
    ): JSONObject? {
        val req = Request.Builder()
            .url(url)
            .header("Authorization", credential)
            .header("Accept", "application/json")
            .build()
        return client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body?.string() ?: return null
            runCatching { JSONObject(body) }.getOrNull()
        }
    }

    // --- 1. DMTF Redfish Client (HTTPS /redfish/v1/) ---

    suspend fun getRedfishSummary(
        host: String,
        port: Int = 443,
        user: String,
        pass: String,
        systemId: String = "1",
        ignoreSslErrors: Boolean = true
    ): Result<RedfishServerSummary> = withContext(Dispatchers.IO) {
        if (host.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("BMC host address is required"))
        }
        val rootOrigin = "https://${host.trim()}:$port"
        val baseUrl = "$rootOrigin/redfish/v1"
        val credential = Credentials.basic(user, pass)
        val client = clientFor(ignoreSslErrors)

        try {
            val systemReq = Request.Builder()
                .url("$baseUrl/Systems/$systemId")
                .header("Authorization", credential)
                .header("Accept", "application/json")
                .build()

            val systemJson = client.newCall(systemReq).execute().use { systemResp ->
                if (!systemResp.isSuccessful) {
                    return@withContext Result.failure(Exception("Redfish HTTP error: ${systemResp.code} ${systemResp.message}"))
                }
                JSONObject(systemResp.body?.string() ?: "{}")
            }

            val name = systemJson.optString("Name", "Bare Metal Server")
            val manufacturer = systemJson.optString("Manufacturer", "Unknown")
            val model = systemJson.optString("Model", "Unknown")
            val serialNumber = systemJson.optString("SerialNumber", "Unknown")
            val biosVersion = systemJson.optString("BiosVersion", "Unknown")
            val powerStateStr = systemJson.optString("PowerState", "Unknown")
            val indicatorLed = systemJson.optString("IndicatorLED", "Off")

            val powerState = when (powerStateStr.lowercase()) {
                "on" -> ServerPowerState.ON
                "off" -> ServerPowerState.OFF
                "poweringon" -> ServerPowerState.POWERING_ON
                "poweringoff" -> ServerPowerState.POWERING_OFF
                "paused" -> ServerPowerState.PAUSED
                else -> ServerPowerState.UNKNOWN
            }

            // Dynamically resolve Chassis & ManagedBy @odata.id links when present (DMTF / iDRAC / iLO / Supermicro)
            val links = systemJson.optJSONObject("Links")
            var chassisPath = links?.optJSONArray("Chassis")?.optJSONObject(0)?.optString("@odata.id", "")
                ?.takeIf { it.isNotBlank() }
            if (chassisPath == null) {
                chassisPath = try {
                    fetchRedfishJson(client, "$baseUrl/Chassis", credential)
                        ?.optJSONArray("Members")
                        ?.optJSONObject(0)
                        ?.optString("@odata.id", "")
                        ?.takeIf { it.isNotBlank() }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
            }
            val effectiveChassisPath = chassisPath ?: "/redfish/v1/Chassis/1"
            val chassisJson = try {
                fetchRedfishJson(client, resolveRedfishUrl(rootOrigin, effectiveChassisPath), credential)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }

            val thermalPath = chassisJson?.optJSONObject("Thermal")?.optString("@odata.id", "")
                ?.takeIf { it.isNotBlank() } ?: "$effectiveChassisPath/Thermal"
            val powerPath = chassisJson?.optJSONObject("Power")?.optString("@odata.id", "")
                ?.takeIf { it.isNotBlank() } ?: "$effectiveChassisPath/Power"

            val managerPath = links?.optJSONArray("ManagedBy")?.optJSONObject(0)?.optString("@odata.id", "")
                ?.takeIf { it.isNotBlank() } ?: "/redfish/v1/Managers/1"

            // 2. Query Thermal Sensors
            val thermals = mutableListOf<ThermalSensor>()
            val fans = mutableListOf<FanSensor>()
            try {
                val tJson = fetchRedfishJson(client, resolveRedfishUrl(rootOrigin, thermalPath), credential)
                if (tJson != null) {
                    val tempsArr = tJson.optJSONArray("Temperatures")
                    if (tempsArr != null) {
                        for (i in 0 until tempsArr.length()) {
                            val tempObj = tempsArr.getJSONObject(i)
                            thermals.add(
                                ThermalSensor(
                                    name = tempObj.optString("Name", "Temp $i"),
                                    currentCelsius = tempObj.optDouble("ReadingCelsius", 0.0),
                                    warningCelsius = tempObj.optDouble("UpperThresholdNonCritical", 85.0),
                                    criticalCelsius = tempObj.optDouble("UpperThresholdCritical", 95.0),
                                    status = tempObj.optJSONObject("Status")?.optString("Health", "Unknown") ?: "Unknown"
                                )
                            )
                        }
                    }

                    val fansArr = tJson.optJSONArray("Fans")
                    if (fansArr != null) {
                        for (i in 0 until fansArr.length()) {
                            val fanObj = fansArr.getJSONObject(i)
                            fans.add(
                                FanSensor(
                                    name = fanObj.optString("Name", "Fan $i"),
                                    rpm = fanObj.optInt("Reading", 0),
                                    status = fanObj.optJSONObject("Status")?.optString("Health", "Unknown") ?: "Unknown"
                                )
                            )
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}

            // 3. Query Power Supplies
            val powerSupplies = mutableListOf<PowerSupply>()
            try {
                val pJson = fetchRedfishJson(client, resolveRedfishUrl(rootOrigin, powerPath), credential)
                if (pJson != null) {
                    val psArr = pJson.optJSONArray("PowerSupplies")
                    if (psArr != null) {
                        for (i in 0 until psArr.length()) {
                            val psObj = psArr.getJSONObject(i)
                            powerSupplies.add(
                                PowerSupply(
                                    name = psObj.optString("Name", "PSU $i"),
                                    powerCapacityWatts = psObj.optDouble("PowerCapacityWatts", 0.0),
                                    lastPowerOutputWatts = psObj.optDouble("LastPowerOutputWatts", 0.0),
                                    status = psObj.optJSONObject("Status")?.optString("Health", "Unknown") ?: "Unknown"
                                )
                            )
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}

            // 4. Query SEL Logs (discover LogServices -> SEL / System Event Log member when available)
            val selEntries = mutableListOf<SelEntry>()
            try {
                var selEntriesPath: String? = null
                val managerJson = fetchRedfishJson(client, resolveRedfishUrl(rootOrigin, managerPath), credential)
                val logServicesPath = managerJson?.optJSONObject("LogServices")?.optString("@odata.id", "")
                    ?.takeIf { it.isNotBlank() } ?: "$managerPath/LogServices"
                val logServicesJson = fetchRedfishJson(client, resolveRedfishUrl(rootOrigin, logServicesPath), credential)
                val lsMembers = logServicesJson?.optJSONArray("Members")
                if (lsMembers != null && lsMembers.length() > 0) {
                    var chosenServicePath = lsMembers.optJSONObject(0)?.optString("@odata.id", "") ?: ""
                    for (i in 0 until lsMembers.length()) {
                        val candidate = lsMembers.optJSONObject(i)?.optString("@odata.id", "") ?: ""
                        if (candidate.contains("SEL", ignoreCase = true) ||
                            candidate.contains("System", ignoreCase = true) ||
                            candidate.contains("Event", ignoreCase = true)
                        ) {
                            chosenServicePath = candidate
                            break
                        }
                    }
                    if (chosenServicePath.isNotBlank()) {
                        val lsObj = fetchRedfishJson(client, resolveRedfishUrl(rootOrigin, chosenServicePath), credential)
                        selEntriesPath = lsObj?.optJSONObject("Entries")?.optString("@odata.id", "")
                            ?.takeIf { it.isNotBlank() } ?: "$chosenServicePath/Entries"
                    }
                }

                val selJson = fetchRedfishJson(
                    client,
                    resolveRedfishUrl(rootOrigin, selEntriesPath ?: "$managerPath/LogServices/SEL/Entries"),
                    credential
                )
                val members = selJson?.optJSONArray("Members")
                if (members != null) {
                    for (i in 0 until members.length().coerceAtMost(20)) {
                        val e = members.getJSONObject(i)
                        selEntries.add(
                            SelEntry(
                                id = e.optString("Id", "$i"),
                                timestamp = e.optString("Created", ""),
                                severity = e.optString("Severity", "OK"),
                                message = e.optString("Message", ""),
                                sensorType = e.optString("SensorType", "System")
                            )
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}

            Result.success(
                RedfishServerSummary(
                    id = systemId,
                    bmcHost = host.trim(),
                    name = name,
                    manufacturer = manufacturer,
                    model = model,
                    serialNumber = serialNumber,
                    biosVersion = biosVersion,
                    powerState = powerState,
                    indicatorLed = indicatorLed,
                    thermalSensors = thermals,
                    fanSensors = fans,
                    powerSupplies = powerSupplies,
                    selEntries = selEntries
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setRedfishPowerState(
        host: String,
        port: Int = 443,
        user: String,
        pass: String,
        systemId: String = "1",
        action: PowerAction,
        ignoreSslErrors: Boolean = true
    ): Result<String> = withContext(Dispatchers.IO) {
        if (host.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("BMC host address is required"))
        }
        val url = "https://${host.trim()}:$port/redfish/v1/Systems/$systemId/Actions/ComputerSystem.Reset"
        val jsonPayload = JSONObject().apply {
            put("ResetType", action.redfishName)
        }.toString()

        try {
            val req = Request.Builder()
                .url(url)
                .header("Authorization", Credentials.basic(user, pass))
                .post(jsonPayload.toRequestBody("application/json".toMediaType()))
                .build()

            clientFor(ignoreSslErrors).newCall(req).execute().use { resp ->
                if (resp.isSuccessful || resp.code == 204 || resp.code == 202) {
                    Result.success("Power action '${action.redfishName}' executed successfully on $host")
                } else {
                    Result.failure(Exception("Redfish Reset error: ${resp.code} ${resp.message}"))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setRedfishOneTimeBoot(
        host: String,
        port: Int = 443,
        user: String,
        pass: String,
        systemId: String = "1",
        bootDevice: BootDevice,
        ignoreSslErrors: Boolean = true
    ): Result<String> = withContext(Dispatchers.IO) {
        if (host.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("BMC host address is required"))
        }
        val url = "https://${host.trim()}:$port/redfish/v1/Systems/$systemId"
        val jsonPayload = JSONObject().apply {
            val bootObj = JSONObject().apply {
                put("BootSourceOverrideTarget", bootDevice.redfishTarget)
                put("BootSourceOverrideEnabled", if (bootDevice == BootDevice.NONE) "Disabled" else "Once")
            }
            put("Boot", bootObj)
        }.toString()

        try {
            val req = Request.Builder()
                .url(url)
                .header("Authorization", Credentials.basic(user, pass))
                .patch(jsonPayload.toRequestBody("application/json".toMediaType()))
                .build()

            clientFor(ignoreSslErrors).newCall(req).execute().use { resp ->
                if (resp.isSuccessful || resp.code == 204) {
                    Result.success("One-time boot override set to '${bootDevice.redfishTarget}'")
                } else {
                    Result.failure(Exception("Redfish Boot Override error: ${resp.code} ${resp.message}"))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- 2. IPMI v2.0 / RMCP+ UDP Client (Port 623) ---

    suspend fun sendAsfPresencePing(host: String, port: Int = 623, timeoutMs: Int = 2000): AsfPresenceResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var socket: DatagramSocket? = null

        try {
            if (host.isBlank()) {
                return@withContext AsfPresenceResult(
                    isAlive = false,
                    ianaEnterpriseId = 0,
                    oemType = "Invalid Host",
                    responseTimeMs = 0L
                )
            }
            socket = DatagramSocket().apply { soTimeout = timeoutMs }

            val pingBuffer = byteArrayOf(
                0x06.toByte(), 0x00.toByte(), 0xFF.toByte(), 0x06.toByte(), // RMCP
                0x00.toByte(), 0x00.toByte(), 0x11.toByte(), 0xBE.toByte(), // ASF IANA = 4542
                0x80.toByte(), 0x01.toByte(), 0x00.toByte(), 0x00.toByte()  // MsgType Ping
            )

            val packet = DatagramPacket(pingBuffer, pingBuffer.size, InetAddress.getByName(host.trim()), port)
            socket.send(packet)

            val recvBuffer = ByteArray(512)
            val recvPacket = DatagramPacket(recvBuffer, recvBuffer.size)
            socket.receive(recvPacket)

            val rtt = System.currentTimeMillis() - startTime
            val len = recvPacket.length
            val data = recvPacket.data

            if (len >= 12 && data[0] == 0x06.toByte() && data[3] == 0x06.toByte()) {
                val msgType = data[8].toInt() and 0xFF
                if (msgType == 0x40 && len >= 16) {
                    val oemIana = ((data[12].toLong() and 0xFF) shl 24) or
                        ((data[13].toLong() and 0xFF) shl 16) or
                        ((data[14].toLong() and 0xFF) shl 8) or
                        (data[15].toLong() and 0xFF)

                    val oemName = when (oemIana) {
                        674L -> "Dell (iDRAC)"
                        11L -> "HP (iLO)"
                        10876L -> "Supermicro (IPMI)"
                        19046L -> "Lenovo (XClarity)"
                        4542L -> "Standard DMTF / ASF"
                        else -> "OEM Enterprise #$oemIana"
                    }

                    val hex = data.take(len).joinToString(" ") { String.format("%02X", it) }
                    return@withContext AsfPresenceResult(
                        isAlive = true,
                        ianaEnterpriseId = oemIana,
                        oemType = oemName,
                        responseTimeMs = rtt,
                        rawHex = hex
                    )
                }
            }

            AsfPresenceResult(
                isAlive = true,
                ianaEnterpriseId = 0,
                oemType = "Generic IPMI BMC",
                responseTimeMs = rtt
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            AsfPresenceResult(
                isAlive = false,
                ianaEnterpriseId = 0,
                oemType = "Unreachable",
                responseTimeMs = System.currentTimeMillis() - startTime
            )
        } finally {
            NetworkByteUtils.safeClose(socket)
        }
    }

    fun buildGetChannelAuthCapsPacket(): ByteArray {
        val buffer = ByteBuffer.allocate(32)
        buffer.put(0x06.toByte())
        buffer.put(0x00.toByte())
        buffer.put(0xFF.toByte())
        buffer.put(0x07.toByte())

        buffer.put(0x00.toByte())
        buffer.putInt(0)
        buffer.putInt(0)

        val ipmiMsg = byteArrayOf(
            0x20.toByte(),
            (0x06 shl 2).toByte(),
            0x00.toByte(),
            0x81.toByte(),
            0x00.toByte(),
            0x38.toByte(),
            0x0E.toByte(),
            0x04.toByte(),
            0x00.toByte()
        )

        ipmiMsg[2] = (-(ipmiMsg[0] + ipmiMsg[1])).toByte()
        var sum = 0
        for (i in 3 until 8) sum += ipmiMsg[i]
        ipmiMsg[8] = (-sum).toByte()

        buffer.put(ipmiMsg.size.toByte())
        buffer.put(ipmiMsg)

        buffer.flip()
        val result = ByteArray(buffer.remaining())
        buffer.get(result)
        return result
    }

    suspend fun queryIpmiChannelAuthCapabilities(
        host: String,
        port: Int = 623,
        timeoutMs: Int = 2000
    ): Result<String> = withContext(Dispatchers.IO) {
        if (host.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("BMC host address is required"))
        }
        var socket: DatagramSocket? = null
        try {
            val reqBytes = buildGetChannelAuthCapsPacket()
            socket = DatagramSocket().apply { soTimeout = timeoutMs }
            val address = InetAddress.getByName(host.trim())
            socket.send(DatagramPacket(reqBytes, reqBytes.size, address, port))

            val recvBuf = ByteArray(512)
            val recvPacket = DatagramPacket(recvBuf, recvBuf.size)
            socket.receive(recvPacket)
            parseChannelAuthCapsResponse(recvPacket.data, recvPacket.length)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            NetworkByteUtils.safeClose(socket)
        }
    }

    fun parseChannelAuthCapsResponse(data: ByteArray, length: Int = data.size): Result<String> {
        if (length < 24 || data.size < length) {
            return Result.failure(IllegalStateException("Truncated IPMI RMCP response ($length bytes)"))
        }
        if (data[0] != 0x06.toByte() || data[3] != 0x07.toByte()) {
            return Result.failure(IllegalStateException("Unexpected RMCP header (class 0x${String.format("%02X", data[3])})"))
        }
        val cc = data[20].toInt() and 0xFF
        if (cc != 0x00) {
            return Result.failure(IllegalStateException("IPMI GetChannelAuthCaps error code: 0x${String.format("%02X", cc)}"))
        }
        val channel = data[21].toInt() and 0x0F
        val authTypes = data[22].toInt() and 0xFF
        val authStatus = data[23].toInt() and 0xFF

        val supported = buildList {
            if ((authTypes and 0x01) != 0) add("None")
            if ((authTypes and 0x02) != 0) add("MD2")
            if ((authTypes and 0x04) != 0) add("MD5")
            if ((authTypes and 0x10) != 0) add("Password")
            if ((authTypes and 0x20) != 0) add("OEM")
            if ((authTypes and 0x80) != 0) add("RMCP+ (v2.0)")
        }.ifEmpty { listOf("None Reported") }

        val kgSet = (authStatus and 0x20) != 0
        val perMsgDisabled = (authStatus and 0x10) != 0
        val userDisabled = (authStatus and 0x08) != 0
        val nonNullUsers = (authStatus and 0x04) != 0
        val oemId = if (length >= 28) {
            (data[25].toInt() and 0xFF) or
                ((data[26].toInt() and 0xFF) shl 8) or
                ((data[27].toInt() and 0xFF) shl 16)
        } else 0

        return Result.success(
            "Channel $channel | Auth: ${supported.joinToString(", ")} | " +
                "PerMsg=${if (perMsgDisabled) "Off" else "On"}, " +
                "UserAuth=${if (userDisabled) "Off" else "On"}, " +
                "NonNullUsers=$nonNullUsers, KgKey=$kgSet, OEM IANA=$oemId"
        )
    }

    fun parseIpmiChassisStatus(statusByte: Byte): IpmiChassisStatus {
        val s = statusByte.toInt() and 0xFF
        val isPowerOn = (s and 0x01) != 0
        val overload = (s and 0x02) != 0
        val interlock = (s and 0x04) != 0
        val powerFault = (s and 0x08) != 0
        val driveFault = (s and 0x10) != 0
        val coolingFault = (s and 0x20) != 0

        val lastEvent = when ((s shr 5) and 0x03) {
            0 -> "AC Failed"
            1 -> "Power Overload"
            2 -> "Power Interlock"
            else -> "Normal AC restored"
        }

        return IpmiChassisStatus(
            isPowerOn = isPowerOn,
            powerOverload = overload,
            interlock = interlock,
            powerFault = powerFault,
            driveFault = driveFault,
            coolingFault = coolingFault,
            lastPowerEvent = lastEvent,
            rawStatusByte = statusByte
        )
    }
}
