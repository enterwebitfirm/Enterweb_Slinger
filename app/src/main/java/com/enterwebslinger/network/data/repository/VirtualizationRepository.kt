// === FILE: app/src/main/java/com/enterwebslinger/network/data/repository/VirtualizationRepository.kt ===
package com.enterwebslinger.network.data.repository

import com.enterwebslinger.network.domain.model.HypervisorHost
import com.enterwebslinger.network.domain.model.HypervisorType
import com.enterwebslinger.network.domain.model.VmItem
import com.enterwebslinger.network.domain.model.VmPowerState
import com.enterwebslinger.network.domain.model.WinRmCommandResult
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
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class VirtualizationRepository {

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
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val defaultHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val httpClient: OkHttpClient get() = unsafeHttpClient

    private fun clientFor(ignoreSslErrors: Boolean): OkHttpClient =
        if (ignoreSslErrors) unsafeHttpClient else defaultHttpClient

    // --- 1. VMware vSphere & vCenter Server (REST API) ---

    suspend fun getVcenterVms(
        host: String,
        port: Int = 443,
        user: String,
        pass: String
    ): Result<List<VmItem>> = withContext(Dispatchers.IO) {
        if (host.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("vCenter host is required"))
        }
        val baseUrl = "https://${host.trim()}:$port/api"
        try {
            val sessionReq = Request.Builder()
                .url("$baseUrl/session")
                .header("Authorization", Credentials.basic(user, pass))
                .post("".toRequestBody("application/json".toMediaType()))
                .build()

            val sessionToken = httpClient.newCall(sessionReq).execute().use { sessionResp ->
                if (!sessionResp.isSuccessful) {
                    return@withContext Result.failure(Exception("vCenter Login Failed: HTTP ${sessionResp.code} ${sessionResp.message}"))
                }
                sessionResp.body?.string()?.replace("\"", "")?.trim() ?: ""
            }

            val vmReq = Request.Builder()
                .url("$baseUrl/vcenter/vm")
                .header("vmware-api-session-id", sessionToken)
                .get()
                .build()

            val vmsJson = httpClient.newCall(vmReq).execute().use { vmResp ->
                if (!vmResp.isSuccessful) {
                    return@withContext Result.failure(Exception("Failed to fetch VMs: HTTP ${vmResp.code}"))
                }
                JSONArray(vmResp.body?.string() ?: "[]")
            }

            val vms = mutableListOf<VmItem>()
            for (i in 0 until vmsJson.length()) {
                val vm = vmsJson.getJSONObject(i)
                val powerStr = vm.optString("power_state", "UNKNOWN")
                val powerState = when (powerStr) {
                    "POWERED_ON" -> VmPowerState.POWERED_ON
                    "POWERED_OFF" -> VmPowerState.POWERED_OFF
                    "SUSPENDED" -> VmPowerState.SUSPENDED
                    else -> VmPowerState.UNKNOWN
                }

                vms.add(
                    VmItem(
                        id = vm.optString("vm", "vm-$i"),
                        name = vm.optString("name", "VM-$i"),
                        hypervisorType = HypervisorType.VMWARE_VCENTER_REST,
                        powerState = powerState,
                        cpuCount = vm.optInt("cpu_count", 0),
                        memoryMb = vm.optLong("memory_size_MiB", 0L),
                        guestOs = vm.optString("guest_OS", ""),
                        ipAddress = vm.optString("ip_address", "")
                    )
                )
            }

            Result.success(vms)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getVcenterHosts(
        host: String,
        port: Int = 443,
        user: String,
        pass: String
    ): Result<List<HypervisorHost>> = withContext(Dispatchers.IO) {
        if (host.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("vCenter host is required"))
        }
        val baseUrl = "https://${host.trim()}:$port/api"
        try {
            val sessionReq = Request.Builder()
                .url("$baseUrl/session")
                .header("Authorization", Credentials.basic(user, pass))
                .post("".toRequestBody("application/json".toMediaType()))
                .build()

            val sessionToken = httpClient.newCall(sessionReq).execute().use { sessionResp ->
                if (!sessionResp.isSuccessful) {
                    return@withContext Result.failure(Exception("vCenter Login Failed: HTTP ${sessionResp.code}"))
                }
                sessionResp.body?.string()?.replace("\"", "")?.trim() ?: ""
            }

            val hostReq = Request.Builder()
                .url("$baseUrl/vcenter/host")
                .header("vmware-api-session-id", sessionToken)
                .get()
                .build()

            val hostsJson = httpClient.newCall(hostReq).execute().use { hostResp ->
                if (!hostResp.isSuccessful) {
                    return@withContext Result.failure(Exception("Failed to fetch hosts: HTTP ${hostResp.code}"))
                }
                JSONArray(hostResp.body?.string() ?: "[]")
            }

            val hosts = mutableListOf<HypervisorHost>()
            for (i in 0 until hostsJson.length()) {
                val h = hostsJson.getJSONObject(i)
                hosts.add(
                    HypervisorHost(
                        id = h.optString("host", "host-$i"),
                        name = h.optString("name", "esxi-$i"),
                        ipAddress = h.optString("name", host),
                        cpuUsageMhz = h.optLong("cpu_usage_mhz", 0L),
                        totalCpuMhz = h.optLong("total_cpu_mhz", 0L),
                        ramUsageBytes = h.optLong("ram_usage_bytes", 0L),
                        totalRamBytes = h.optLong("total_ram_bytes", 0L),
                        status = h.optString("connection_state", "UNKNOWN"),
                        model = h.optString("model", "VMware ESXi Host")
                    )
                )
            }

            Result.success(hosts)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun controlVcenterVmPower(
        host: String,
        port: Int = 443,
        user: String,
        pass: String,
        vmId: String,
        action: String
    ): Result<String> = withContext(Dispatchers.IO) {
        if (host.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("vCenter host is required"))
        }
        val baseUrl = "https://${host.trim()}:$port/api"
        try {
            val sessionReq = Request.Builder()
                .url("$baseUrl/session")
                .header("Authorization", Credentials.basic(user, pass))
                .post("".toRequestBody("application/json".toMediaType()))
                .build()

            val sessionToken = httpClient.newCall(sessionReq).execute().use { sessionResp ->
                if (!sessionResp.isSuccessful) {
                    return@withContext Result.failure(Exception("vCenter Login Failed: HTTP ${sessionResp.code}"))
                }
                sessionResp.body?.string()?.replace("\"", "")?.trim() ?: ""
            }

            val actionReq = Request.Builder()
                .url("$baseUrl/vcenter/vm/$vmId/power?action=$action")
                .header("vmware-api-session-id", sessionToken)
                .post("".toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(actionReq).execute().use { actionResp ->
                if (actionResp.isSuccessful || actionResp.code == 204) {
                    Result.success("VM '$vmId' power action '$action' executed successfully")
                } else {
                    Result.failure(Exception("Power action failed: HTTP ${actionResp.code}"))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- 2. VMware ESXi Web Services SDK (SOAP Envelope XML /sdk) ---

    suspend fun queryEsxiSoapServiceContent(
        host: String,
        port: Int = 443,
        ignoreSslErrors: Boolean = true
    ): Result<String> = withContext(Dispatchers.IO) {
        if (host.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("ESXi host is required"))
        }
        val url = "https://${host.trim()}:$port/sdk"
        val soapXml = """
            <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:urn="urn:vim25">
               <soapenv:Header/>
               <soapenv:Body>
                  <urn:RetrieveServiceContent>
                     <urn:_this type="ServiceInstance">ServiceInstance</urn:_this>
                  </urn:RetrieveServiceContent>
               </soapenv:Body>
            </soapenv:Envelope>
        """.trimIndent()

        try {
            val req = Request.Builder()
                .url(url)
                .header("SOAPAction", "urn:vim25/RetrieveServiceContent")
                .post(soapXml.toRequestBody("text/xml; charset=utf-8".toMediaType()))
                .build()

            clientFor(ignoreSslErrors).newCall(req).execute().use { resp ->
                val responseBody = resp.body?.string() ?: ""
                if (responseBody.contains("<faultstring>", ignoreCase = true)) {
                    val fault = responseBody.substringAfter("<faultstring>").substringBefore("</faultstring>").trim()
                    return@withContext Result.failure(Exception("ESXi SOAP Fault: $fault"))
                }
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(Exception("ESXi SOAP HTTP ${resp.code}: ${resp.message}"))
                }
                if (responseBody.contains("<about>")) {
                    val osName = responseBody.substringAfter("<fullName>").substringBefore("</fullName>")
                    val apiVersion = responseBody.substringAfter("<apiVersion>").substringBefore("</apiVersion>")
                    Result.success("ESXi SOAP Endpoint Verified: $osName (API v$apiVersion)")
                } else {
                    Result.success("ESXi SOAP HTTP ${resp.code}: ${responseBody.take(150)}")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- 3. Red Hat RHEV / oVirt Engine REST API ---

    suspend fun getOvirtVms(
        host: String,
        port: Int = 443,
        user: String,
        pass: String
    ): Result<List<VmItem>> = withContext(Dispatchers.IO) {
        if (host.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("oVirt host is required"))
        }
        val url = "https://${host.trim()}:$port/ovirt-engine/api/vms"
        try {
            val req = Request.Builder()
                .url(url)
                .header("Authorization", Credentials.basic(user, pass))
                .header("Accept", "application/json")
                .get()
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(Exception("RHEV / oVirt Query Failed: HTTP ${resp.code}"))
                }

                val json = JSONObject(resp.body?.string() ?: "{}")
                val vmsJson = json.optJSONArray("vm") ?: JSONArray()
                val list = mutableListOf<VmItem>()

                for (i in 0 until vmsJson.length()) {
                    val v = vmsJson.getJSONObject(i)
                    val status = v.optString("status", "unknown").lowercase()
                    val pState = when (status) {
                        "up" -> VmPowerState.POWERED_ON
                        "down" -> VmPowerState.POWERED_OFF
                        "suspended" -> VmPowerState.SUSPENDED
                        else -> VmPowerState.UNKNOWN
                    }
                    val reportedDevices = v.optJSONObject("reported_devices")?.optJSONArray("reported_device")
                    val firstIp = reportedDevices?.optJSONObject(0)
                        ?.optJSONObject("ips")?.optJSONArray("ip")?.optJSONObject(0)?.optString("address", "") ?: ""

                    list.add(
                        VmItem(
                            id = v.optString("id", "$i"),
                            name = v.optString("name", "oVirt-VM-$i"),
                            hypervisorType = HypervisorType.RH_OVIRT,
                            powerState = pState,
                            cpuCount = v.optJSONObject("cpu")?.optJSONObject("topology")?.optInt("cores", 0) ?: 0,
                            memoryMb = (v.optLong("memory", 0L) / 1048576L),
                            guestOs = v.optJSONObject("os")?.optString("type", "") ?: "",
                            ipAddress = firstIp
                        )
                    )
                }

                Result.success(list)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- 4. Microsoft Hyper-V & Windows Server via WinRM (WS-Management SOAP) ---

    suspend fun executeWinRmQuery(
        host: String,
        port: Int = 5985,
        user: String,
        pass: String,
        wqlQuery: String = "SELECT Name, ElementName, EnabledState FROM Msvm_ComputerSystem",
        useTls: Boolean = (port == 5986),
        ignoreSslErrors: Boolean = true
    ): Result<WinRmCommandResult> = withContext(Dispatchers.IO) {
        if (host.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Hyper-V / WinRM host is required"))
        }
        val startTime = System.currentTimeMillis()
        val scheme = if (useTls) "https" else "http"
        val url = "$scheme://${host.trim()}:$port/wsman"
        val messageId = "uuid:${UUID.randomUUID()}"

        val wsmanXml = """
            <s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope"
                        xmlns:wsa="http://schemas.xmlsoap.org/ws/2004/08/addressing"
                        xmlns:wsman="http://schemas.dmtf.org/wbem/wsman/1/wsman.xsd"
                        xmlns:wsen="http://schemas.xmlsoap.org/ws/2004/09/enumeration">
               <s:Header>
                  <wsa:To>$url</wsa:To>
                  <wsa:Action>http://schemas.xmlsoap.org/ws/2004/09/enumeration/Enumerate</wsa:Action>
                  <wsa:MessageID>$messageId</wsa:MessageID>
                  <wsa:ReplyTo>
                     <wsa:Address>http://schemas.xmlsoap.org/ws/2004/08/addressing/role/anonymous</wsa:Address>
                  </wsa:ReplyTo>
                  <wsman:ResourceURI>http://schemas.microsoft.com/wbem/wsman/1/wmi/root/virtualization/v2/*</wsman:ResourceURI>
               </s:Header>
               <s:Body>
                  <wsen:Enumerate>
                     <wsman:OptimizeEnumeration/>
                     <wsman:MaxElements>100</wsman:MaxElements>
                     <wsman:Filter Dialect="http://schemas.microsoft.com/wbem/wsman/1/WQL">$wqlQuery</wsman:Filter>
                  </wsen:Enumerate>
               </s:Body>
            </s:Envelope>
        """.trimIndent()

        try {
            val req = Request.Builder()
                .url(url)
                .header("Authorization", Credentials.basic(user, pass))
                .header("Content-Type", "application/soap+xml;charset=UTF-8")
                .post(wsmanXml.toRequestBody("application/soap+xml;charset=UTF-8".toMediaType()))
                .build()

            clientFor(ignoreSslErrors).newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: ""
                val duration = System.currentTimeMillis() - startTime

                if (resp.isSuccessful) {
                    Result.success(
                        WinRmCommandResult(
                            stdout = body,
                            stderr = "",
                            exitCode = 0,
                            executionTimeMs = duration,
                            isSuccess = true
                        )
                    )
                } else {
                    Result.success(
                        WinRmCommandResult(
                            stdout = "",
                            stderr = "WinRM Error HTTP ${resp.code}: ${resp.message}\n$body",
                            exitCode = resp.code,
                            executionTimeMs = duration,
                            isSuccess = false
                        )
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- 5. Oracle VirtualBox WebService (vboxwebsrv SOAP) ---

    suspend fun queryVirtualBoxWebservice(
        host: String,
        port: Int = 18083,
        user: String = "",
        pass: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        if (host.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("VirtualBox host is required"))
        }
        val url = "http://${host.trim()}:$port/"
        val logonXml = """
            <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:vbox="http://www.virtualbox.org/">
               <soapenv:Header/>
               <soapenv:Body>
                  <vbox:IWebsessionManager_logon>
                     <username>$user</username>
                     <password>$pass</password>
                  </vbox:IWebsessionManager_logon>
               </soapenv:Body>
            </soapenv:Envelope>
        """.trimIndent()

        try {
            val req = Request.Builder()
                .url(url)
                .header("SOAPAction", "\"\"")
                .post(logonXml.toRequestBody("text/xml; charset=utf-8".toMediaType()))
                .build()

            httpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: ""
                if (body.contains("<faultstring>", ignoreCase = true)) {
                    val fault = body.substringAfter("<faultstring>").substringBefore("</faultstring>").trim()
                    return@withContext Result.failure(Exception("vboxwebsrv SOAP Fault: $fault"))
                }
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(Exception("vboxwebsrv HTTP ${resp.code}: ${resp.message}"))
                }
                if (body.contains("<returnval>")) {
                    val sessionToken = body.substringAfter("<returnval>").substringBefore("</returnval>")
                    Result.success("VirtualBox WebService Connected: Session Handle '$sessionToken'")
                } else {
                    Result.success("vboxwebsrv response HTTP ${resp.code}: ${body.take(160)}")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
