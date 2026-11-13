// === FILE: app/src/main/java/com/enterwebslinger/network/data/repository/CiscoCollaborationRepository.kt ===
package com.enterwebslinger.network.data.repository

import com.enterwebslinger.network.domain.model.CallQualityMetric
import com.enterwebslinger.network.domain.model.EndpointRegistrationStatus
import com.enterwebslinger.network.domain.model.PhoneEndpoint
import com.enterwebslinger.network.domain.model.SipTrunk
import com.enterwebslinger.network.domain.model.VoiceGatewayDsp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.xml.sax.InputSource
import java.io.StringReader
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import javax.xml.parsers.DocumentBuilderFactory

class CiscoCollaborationRepository {

    private val httpClient: OkHttpClient by lazy {
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
            .readTimeout(12, TimeUnit.SECONDS)
            .build()
    }

    suspend fun queryCucmEndpoints(
        host: String,
        port: Int = 8443,
        user: String,
        pass: String
    ): Result<List<PhoneEndpoint>> = withContext(Dispatchers.IO) {
        if (host.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("CUCM host is required"))
        }
        val url = "https://${host.trim()}:$port/cucm-uds/users"
        try {
            val req = Request.Builder()
                .url(url)
                .header("Authorization", Credentials.basic(user, pass))
                .header("Accept", "application/json")
                .get()
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(Exception("CUCM UDS HTTP ${resp.code}: ${resp.message}"))
                }

                val json = JSONObject(resp.body?.string() ?: "{}")
                val users = json.optJSONObject("users")?.optJSONArray("user") ?: JSONArray()
                val endpoints = mutableListOf<PhoneEndpoint>()

                for (i in 0 until users.length()) {
                    val u = users.getJSONObject(i)
                    val userName = u.optString("userName", "")
                    val dn = u.optString("telephoneNumber", u.optString("primaryExtension", ""))
                    val deviceName = u.optString("deviceName", u.optString("id", userName))
                    val ip = u.optString("ipAddress", "")
                    val model = u.optString("model", "Cisco Endpoint")
                    val load = u.optString("activeLoadId", "")
                    val statusStr = u.optString("status", "").uppercase()
                    val regStatus = when (statusStr) {
                        "REGISTERED" -> EndpointRegistrationStatus.REGISTERED
                        "UNREGISTERED" -> EndpointRegistrationStatus.UNREGISTERED
                        "REJECTED" -> EndpointRegistrationStatus.REJECTED
                        "PARTIALLY_REGISTERED" -> EndpointRegistrationStatus.PARTIALLY_REGISTERED
                        else -> EndpointRegistrationStatus.UNKNOWN
                    }
                    endpoints.add(
                        PhoneEndpoint(
                            name = deviceName.ifBlank { userName },
                            model = model,
                            directoryNumber = dn,
                            status = regStatus,
                            ipAddress = ip,
                            activeLoadId = load,
                            primaryUser = userName
                        )
                    )
                }
                Result.success(endpoints)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun querySipTrunks(
        host: String,
        port: Int = 8443,
        user: String,
        pass: String
    ): Result<List<SipTrunk>> = withContext(Dispatchers.IO) {
        if (host.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("CUCM host is required"))
        }
        val axlSoap = """
            <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:ns="http://www.cisco.com/AXL/API/12.5">
               <soapenv:Header/>
               <soapenv:Body>
                  <ns:listSipTrunk>
                     <searchCriteria><name>%</name></searchCriteria>
                     <returnedTags>
                        <name/><description/><runFlag/>
                     </returnedTags>
                  </ns:listSipTrunk>
               </soapenv:Body>
            </soapenv:Envelope>
        """.trimIndent()

        try {
            val req = Request.Builder()
                .url("https://${host.trim()}:$port/axl/")
                .header("Authorization", Credentials.basic(user, pass))
                .header("SOAPAction", "CUCM:DB ver=12.5 listSipTrunk")
                .post(axlSoap.toRequestBody("text/xml; charset=utf-8".toMediaType()))
                .build()

            httpClient.newCall(req).execute().use { resp ->
                val xml = resp.body?.string() ?: ""
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(Exception("CUCM AXL SIP Trunk query failed: HTTP ${resp.code} ${resp.message}"))
                }

                val trunks = mutableListOf<SipTrunk>()
                val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
                    .newDocumentBuilder()
                    .parse(InputSource(StringReader(xml)))
                val nodes = doc.getElementsByTagName("sipTrunk")
                for (i in 0 until nodes.length) {
                    val el = nodes.item(i) as? org.w3c.dom.Element ?: continue
                    val name = el.getElementsByTagName("name").item(0)?.textContent?.trim() ?: continue
                    val desc = el.getElementsByTagName("description").item(0)?.textContent?.trim() ?: ""
                    val runFlag = el.getElementsByTagName("runFlag").item(0)?.textContent?.trim() ?: "Unknown"
                    trunks.add(
                        SipTrunk(
                            name = name,
                            destinationAddress = host.trim(),
                            port = 5060,
                            status = runFlag,
                            serviceState = desc.ifBlank { runFlag },
                            activeCallCount = 0
                        )
                    )
                }
                Result.success(trunks)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun queryCallQualityMos(
        host: String,
        port: Int = 8443,
        user: String,
        pass: String
    ): Result<List<CallQualityMetric>> = withContext(Dispatchers.IO) {
        if (host.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("CUCM host is required"))
        }
        try {
            val req = Request.Builder()
                .url("https://${host.trim()}:$port/ast/ASTisApi")
                .header("Authorization", Credentials.basic(user, pass))
                .header("Accept", "application/json")
                .get()
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(Exception("CUCM Serviceability call quality query failed: HTTP ${resp.code} ${resp.message}"))
                }
                val bodyStr = resp.body?.string() ?: "{}"
                val json = JSONObject(bodyStr)
                val callsArr = json.optJSONArray("calls") ?: JSONArray()
                val metrics = mutableListOf<CallQualityMetric>()
                for (i in 0 until callsArr.length()) {
                    val c = callsArr.getJSONObject(i)
                    metrics.add(
                        CallQualityMetric(
                            callId = c.optString("callId", "$i"),
                            caller = c.optString("caller", ""),
                            callee = c.optString("callee", ""),
                            mosScore = c.optDouble("mosScore", 0.0),
                            jitterMs = c.optDouble("jitterMs", 0.0),
                            latencyMs = c.optDouble("latencyMs", 0.0),
                            packetLossPercent = c.optDouble("packetLossPercent", 0.0),
                            codec = c.optString("codec", "")
                        )
                    )
                }
                Result.success(metrics)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun queryVoiceGatewayDsps(
        host: String,
        port: Int = 8443,
        user: String = "",
        pass: String = ""
    ): Result<List<VoiceGatewayDsp>> = withContext(Dispatchers.IO) {
        if (host.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("CUCM / Voice Gateway host is required"))
        }
        val perfmonSoap = """
            <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:soap="http://schemas.cisco.com/ast/soap">
               <soapenv:Header/>
               <soapenv:Body>
                  <soap:perfmonCollectCounterData>
                     <soap:Host>${host.trim()}</soap:Host>
                     <soap:Object>Cisco Media Streaming App</soap:Object>
                  </soap:perfmonCollectCounterData>
               </soapenv:Body>
            </soapenv:Envelope>
        """.trimIndent()

        try {
            val reqBuilder = Request.Builder()
                .url("https://${host.trim()}:$port/perfmonservice2/services/PerfmonService")
                .header("SOAPAction", "perfmonCollectCounterData")
                .post(perfmonSoap.toRequestBody("text/xml; charset=utf-8".toMediaType()))
            if (user.isNotBlank()) {
                reqBuilder.header("Authorization", Credentials.basic(user, pass))
            }

            httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                val xml = resp.body?.string() ?: ""
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(Exception("Voice Gateway Perfmon query failed: HTTP ${resp.code} ${resp.message}"))
                }

                val dsps = mutableListOf<VoiceGatewayDsp>()
                val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
                    .newDocumentBuilder()
                    .parse(InputSource(StringReader(xml)))
                val items = doc.getElementsByTagName("CounterInfo")
                for (i in 0 until items.length) {
                    val el = items.item(i) as? org.w3c.dom.Element ?: continue
                    val name = el.getElementsByTagName("Name").item(0)?.textContent?.trim() ?: continue
                    val value = el.getElementsByTagName("Value").item(0)?.textContent?.trim()?.toIntOrNull() ?: 0
                    dsps.add(
                        VoiceGatewayDsp(
                            gatewayName = name,
                            activeChannels = value,
                            totalChannels = value,
                            activeCodec = "",
                            utilizationPercent = 0f
                        )
                    )
                }
                Result.success(dsps)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
