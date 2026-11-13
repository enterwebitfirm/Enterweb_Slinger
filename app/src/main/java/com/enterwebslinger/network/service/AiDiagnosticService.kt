// === FILE: app/src/main/java/com/enterwebslinger/network/service/AiDiagnosticService.kt ===
package com.enterwebslinger.network.service

import android.content.Context
import android.util.Log
import com.enterwebslinger.network.core.util.NetworkUtils
import com.enterwebslinger.network.data.repository.CoreProtocolsRepository
import com.enterwebslinger.network.data.repository.DiagnosticsRepository
import com.enterwebslinger.network.data.repository.FlowCollectorRepository
import com.enterwebslinger.network.data.repository.IotProtocolsRepository
import com.enterwebslinger.network.data.repository.LanScannerRepository
import com.enterwebslinger.network.data.repository.NetworkInfoRepository
import com.enterwebslinger.network.data.repository.NetworkTelemetryAggregator
import com.enterwebslinger.network.data.repository.NmapFingerprintEngine
import com.enterwebslinger.network.data.repository.OobHardwareRepository
import com.enterwebslinger.network.data.repository.SecurityRepository
import com.enterwebslinger.network.data.repository.SnmpRepository
import com.enterwebslinger.network.data.repository.TerminalAndNetOsRepository
import com.enterwebslinger.network.data.repository.VpnRepository
import com.enterwebslinger.network.data.repository.WebServicesRepository
import com.enterwebslinger.network.data.repository.WifiSpeedRepository
import com.enterwebslinger.network.domain.model.AiAuditFinding
import com.enterwebslinger.network.domain.model.AiAuditReport
import com.enterwebslinger.network.domain.model.DiscoveredDevice
import com.enterwebslinger.network.domain.model.HttpMethod
import com.enterwebslinger.network.domain.model.ScanProfile
import com.enterwebslinger.network.domain.model.SnmpTargetConfig
import com.enterwebslinger.network.domain.model.SnmpVersion
import com.enterwebslinger.network.domain.model.TracerouteHop
import com.enterwebslinger.network.domain.model.WifiScanEntry
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.FunctionDeclaration
import com.google.firebase.ai.type.FunctionResponsePart
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.json.JSONObject

private const val TAG = "AiDiagnosticService"

class AiDiagnosticService(
    private val context: Context? = null,
    private val telemetryAggregator: NetworkTelemetryAggregator,
    private val networkInfoRepository: NetworkInfoRepository,
    private val lanScannerRepository: LanScannerRepository,
    private val diagnosticsRepository: DiagnosticsRepository,
    private val securityRepository: SecurityRepository,
    private val wifiSpeedRepository: WifiSpeedRepository,
    private val snmpRepository: SnmpRepository? = null,
    private val flowCollectorRepository: FlowCollectorRepository? = null,
    private val oobHardwareRepository: OobHardwareRepository? = null,
    private val nmapFingerprintEngine: NmapFingerprintEngine? = null,
    private val terminalAndNetOsRepository: TerminalAndNetOsRepository? = null,
    private val iotProtocolsRepository: IotProtocolsRepository? = null,
    private val vpnRepository: VpnRepository? = null,
    private val webServicesRepository: WebServicesRepository? = null,
    private val coreProtocolsRepository: CoreProtocolsRepository? = null
) {
    private val systemInstructionText = """
        You are a Principal Wireless & Network Diagnostics Engineer. Analyze raw Android network telemetry accurately. Distinguish between Wi-Fi PHY/RF interference, local router bufferbloat, benign ISP ICMP deprioritization on intermediate traceroute hops, DNS resolution bottlenecks, and LAN security risks.
    """.trimIndent()

    private val findingSchema = Schema.obj(
        properties = mapOf(
            "title" to Schema.string(description = "Short title of the finding"),
            "technicalEvidence" to Schema.string(description = "Specific metrics or telemetry evidence (e.g. RSSI -84 dBm, bufferbloat +45ms)"),
            "plainEnglishExplanation" to Schema.string(description = "Clear user explanation of why this happens"),
            "recommendedFix" to Schema.string(description = "Actionable steps to resolve the issue")
        )
    )

    private val auditReportSchema = Schema.obj(
        properties = mapOf(
            "healthScore" to Schema.integer(description = "Overall health score from 0 to 100"),
            "primaryBottleneck" to Schema.enumeration(
                values = listOf(
                    "WIFI_SIGNAL_OR_CONGESTION",
                    "LOCAL_ROUTER_OR_LAN",
                    "ISP_WAN_OR_ROUTING",
                    "DNS_CONFIGURATION",
                    "HEALTHY"
                ),
                description = "Primary bottleneck category"
            ),
            "severity" to Schema.enumeration(
                values = listOf("OPTIMAL", "WARNING", "CRITICAL"),
                description = "Overall issue severity"
            ),
            "executiveSummary" to Schema.string(description = "Executive 1-2 sentence overview of network health"),
            "findings" to Schema.array(items = findingSchema, description = "Key diagnostic findings and fixes")
        )
    )

    private val auditConfig = generationConfig {
        responseMimeType = "application/json"
        responseSchema = auditReportSchema
    }

    private fun getAuditModel(): GenerativeModel? {
        return runCatching {
            context?.let { ctx ->
                if (FirebaseApp.getApps(ctx).isEmpty()) {
                    FirebaseApp.initializeApp(ctx)
                }
            }
            Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
                modelName = "gemini-3.8-flash",
                systemInstruction = content { text(systemInstructionText) },
                generationConfig = auditConfig
            )
        }.onFailure { Log.w(TAG, "AuditModel initialization unavailable", it) }.getOrNull()
    }

    private fun getGeneralModel(): GenerativeModel? {
        return runCatching {
            context?.let { ctx ->
                if (FirebaseApp.getApps(ctx).isEmpty()) {
                    FirebaseApp.initializeApp(ctx)
                }
            }
            Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
                modelName = "gemini-3.8-flash",
                systemInstruction = content { text(systemInstructionText) }
            )
        }.onFailure { Log.w(TAG, "GeneralModel initialization unavailable", it) }.getOrNull()
    }

    private val pingToolDeclaration = FunctionDeclaration(
        name = "runLivePingTest",
        description = "Runs a live ping test to a target host or IP and returns latency and packet loss stats.",
        parameters = mapOf(
            "targetHost" to Schema.string(description = "The destination host or IP to ping"),
            "protocol" to Schema.enumeration(listOf("ICMP", "TCP", "HTTP"), description = "The ping protocol")
        )
    )

    private val portScanToolDeclaration = FunctionDeclaration(
        name = "scanDeviceOpenPorts",
        description = "Scans a device on the LAN for open TCP ports using common presets.",
        parameters = mapOf(
            "ipAddress" to Schema.string(description = "Target local IP address"),
            "portPreset" to Schema.enumeration(listOf("COMMON", "WEB_ADMIN"), description = "Port scan preset")
        )
    )

    private val gatewayVsWanToolDeclaration = FunctionDeclaration(
        name = "measureGatewayVsWanLatency",
        description = "Compares round-trip latency to the local router default gateway vs external Internet WAN (1.1.1.1).",
        parameters = emptyMap()
    )

    private val wifiCongestionToolDeclaration = FunctionDeclaration(
        name = "getWifiChannelCongestionReport",
        description = "Analyzes neighboring APs and reports co-channel interference on the current Wi-Fi channel.",
        parameters = emptyMap()
    )

    private val querySnmpToolDeclaration = FunctionDeclaration(
        name = "querySnmpDeviceHealth",
        description = "Queries an SNMP agent for system MIB information, uptime, and CPU utilization.",
        parameters = mapOf(
            "ipAddress" to Schema.string(description = "IP address of the SNMP managed device"),
            "version" to Schema.string(description = "SNMP version: v1, v2c, or v3"),
            "communityOrUser" to Schema.string(description = "SNMP community string or v3 username")
        )
    )

    private val inspectTopFlowTalkersToolDeclaration = FunctionDeclaration(
        name = "inspectTopFlowTalkers",
        description = "Analyzes high-bandwidth NetFlow/IPFIX/sFlow conversations and top talkers on the network.",
        parameters = mapOf(
            "protocol" to Schema.string(description = "Protocol filter (e.g. TCP, UDP, ALL)"),
            "windowSeconds" to Schema.integer(description = "Analysis window in seconds")
        )
    )

    private val auditServerBmcRedfishToolDeclaration = FunctionDeclaration(
        name = "auditServerBmcRedfish",
        description = "Audits bare-metal server health, power state, and thermal sensors via DMTF Redfish HTTPS.",
        parameters = mapOf(
            "bmcIp" to Schema.string(description = "IP address of the BMC/iDRAC/iLO controller"),
            "user" to Schema.string(description = "BMC Redfish username"),
            "pass" to Schema.string(description = "BMC Redfish password")
        )
    )

    private val runNmapServiceFingerprintToolDeclaration = FunctionDeclaration(
        name = "runNmapServiceFingerprint",
        description = "Runs deep TCP service banner grabbing, TLS certificate analysis, and OS heuristics.",
        parameters = mapOf(
            "targetIp" to Schema.string(description = "Target host or IP address"),
            "profile" to Schema.enumeration(listOf("TOP_25", "TOP_100"), description = "Scan port profile")
        )
    )

    private val executeNetworkOsShowCommandToolDeclaration = FunctionDeclaration(
        name = "executeNetworkOsShowCommand",
        description = "Executes an operational show command on a network switch, router, or firewall via SSH.",
        parameters = mapOf(
            "host" to Schema.string(description = "Host IP or hostname"),
            "vendor" to Schema.string(description = "Device vendor: CISCO, JUNIPER, ARISTA, MIKROTIK, LINUX"),
            "command" to Schema.string(description = "Operational command (e.g. 'show version', 'show ip route')")
        )
    )

    private val testMqttOrCoapEndpointToolDeclaration = FunctionDeclaration(
        name = "testMqttOrCoapEndpoint",
        description = "Probes an IoT broker or CoAP server to verify reachability and discover resources.",
        parameters = mapOf(
            "uri" to Schema.string(description = "Target broker or server host and optional port"),
            "protocol" to Schema.enumeration(listOf("MQTT", "COAP"), description = "IoT Protocol to test")
        )
    )

    private val vpnAndDnsLeakToolDeclaration = FunctionDeclaration(
        name = "checkVpnAndDnsLeakStatus",
        description = "Inspects active VPN tunnel state, virtual IP, throughput counters, and runs a DNS leak test.",
        parameters = emptyMap()
    )

    private val probeHttpEndpointToolDeclaration = FunctionDeclaration(
        name = "probeHttpOrSoapEndpoint",
        description = "Performs an HTTP(S) GET timing waterfall check (DNS, TCP, TLS, TTFB, total ms) on a REST or SOAP endpoint URL.",
        parameters = mapOf(
            "url" to Schema.string(description = "Full HTTP/HTTPS URL to probe")
        )
    )

    private val dhcpOrTimeProbeToolDeclaration = FunctionDeclaration(
        name = "runDhcpOrTimeProbe",
        description = "Sends a live DHCP DISCOVER probe or SNTP time check on the local network.",
        parameters = mapOf(
            "mode" to Schema.enumeration(listOf("DHCP", "SNTP"), description = "Probe protocol"),
            "host" to Schema.string(description = "Optional NTP host when mode is SNTP (e.g. pool.ntp.org)")
        )
    )

    private fun getChatModelWithTools(): GenerativeModel? {
        return runCatching {
            context?.let { ctx ->
                if (FirebaseApp.getApps(ctx).isEmpty()) {
                    FirebaseApp.initializeApp(ctx)
                }
            }
            Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
                modelName = "gemini-3.8-flash",
                systemInstruction = content { text(systemInstructionText) },
                tools = listOf(
                    Tool.functionDeclarations(
                        listOf(
                            pingToolDeclaration,
                            portScanToolDeclaration,
                            gatewayVsWanToolDeclaration,
                            wifiCongestionToolDeclaration,
                            querySnmpToolDeclaration,
                            inspectTopFlowTalkersToolDeclaration,
                            auditServerBmcRedfishToolDeclaration,
                            runNmapServiceFingerprintToolDeclaration,
                            executeNetworkOsShowCommandToolDeclaration,
                            testMqttOrCoapEndpointToolDeclaration,
                            vpnAndDnsLeakToolDeclaration,
                            probeHttpEndpointToolDeclaration,
                            dhcpOrTimeProbeToolDeclaration
                        )
                    )
                )
            )
        }.onFailure { Log.w(TAG, "ChatModelWithTools initialization unavailable", it) }.getOrNull()
    }

    suspend fun runNetworkHealthAudit(): Result<AiAuditReport> = withContext(Dispatchers.IO) {
        try {
            val model = getAuditModel() ?: return@withContext Result.failure(
                IllegalStateException("Firebase AI / Gemini is initializing or pending configuration. Please check network connectivity.")
            )

            val snapshot = telemetryAggregator.buildSnapshot()
            val promptPayload = telemetryAggregator.snapshotToJsonPrompt(snapshot)
            val fullPrompt = "Perform a comprehensive health audit of this raw network telemetry:\n$promptPayload"

            val response = withTimeout(60_000L) {
                model.generateContent(fullPrompt)
            }

            val jsonString = response.text ?: throw IllegalStateException("Model returned empty audit text.")
            Log.d(TAG, "Raw Audit JSON: $jsonString")

            val json = JSONObject(jsonString)
            val score = json.optInt("healthScore", 0)
            val bottleneck = json.optString("primaryBottleneck", "UNKNOWN")
            val severity = json.optString("severity", "WARNING")
            val summary = json.optString("executiveSummary", "")

            val findingsList = mutableListOf<AiAuditFinding>()
            if (json.has("findings")) {
                val arr = json.getJSONArray("findings")
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    findingsList.add(
                        AiAuditFinding(
                            title = obj.optString("title", "Network Finding"),
                            technicalEvidence = obj.optString("technicalEvidence", ""),
                            plainEnglishExplanation = obj.optString("plainEnglishExplanation", ""),
                            recommendedFix = obj.optString("recommendedFix", "")
                        )
                    )
                }
            }

            Result.success(
                AiAuditReport(
                    healthScore = score,
                    primaryBottleneck = bottleneck,
                    severity = severity,
                    executiveSummary = summary,
                    findings = findingsList
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI Network Health Audit failed", e)
            Result.failure(e)
        }
    }

    suspend fun explainTraceroute(hops: List<TracerouteHop>): String = withContext(Dispatchers.IO) {
        try {
            val model = getGeneralModel() ?: return@withContext "Traceroute AI analysis unavailable: Firebase AI initializing or offline."
            val hopsSummary = hops.joinToString("\n") { hop ->
                "Hop ${hop.hopNumber}: ${hop.ip} (${hop.hostname}) - ${if (hop.isTimeout) "TIMED OUT" else "${hop.rttMs} ms"}"
            }
            val prompt = """
                Analyze this traceroute path and explain whether any high-latency middle hop with low/zero final-hop loss is benign control-plane ICMP rate-limiting or true network congestion:
                $hopsSummary
                
                Keep your answer concise (2-3 sentences), technical, and direct.
            """.trimIndent()

            val response = withTimeout(45_000L) {
                model.generateContent(prompt)
            }
            response.text ?: "Analysis unavailable."
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "Traceroute analysis unavailable: ${e.message}"
        }
    }

    suspend fun auditDeviceSecurity(device: DiscoveredDevice): String = withContext(Dispatchers.IO) {
        try {
            val model = getGeneralModel() ?: return@withContext "Device security audit unavailable: Firebase AI initializing or offline."
            val prompt = """
                Audit the security of this discovered LAN device:
                - IP: ${device.ip}
                - Hostname: ${device.hostname}
                - Vendor: ${device.vendor}
                - Open Ports: ${device.openPorts.joinToString(", ")}
                - Discovered Protocols / Services: ${device.services.joinToString(", ")}
                
                Flag any insecure protocols (e.g. SMB port 445, Telnet 23, cleartext HTTP on 80/8080) and provide 2 bullet points of remediation advice.
            """.trimIndent()

            val response = withTimeout(45_000L) {
                model.generateContent(prompt)
            }
            response.text ?: "Device audit unavailable."
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "Device audit failed: ${e.message}"
        }
    }

    suspend fun recommendWifiChannel(
        currentChannel: Int,
        currentBand: String,
        aps: List<WifiScanEntry>
    ): String = withContext(Dispatchers.IO) {
        try {
            val model = getGeneralModel() ?: return@withContext "Channel optimization advice unavailable: Firebase AI initializing or offline."
            val apSummary = aps.take(15).joinToString("\n") { ap ->
                "SSID: ${ap.ssid}, Ch: ${ap.channel}, Band: ${ap.band}, RSSI: ${ap.rssiDbm} dBm"
            }
            val prompt = """
                Current Wi-Fi Channel: $currentChannel on $currentBand.
                Surrounding Detected Access Points:
                $apSummary
                
                Recommend the cleanest, non-overlapping Wi-Fi channel (prioritizing 1, 6, 11 for 2.4 GHz or clean DFS/UNII channels for 5/6 GHz) to eliminate co-channel and adjacent-channel interference.
            """.trimIndent()

            val response = withTimeout(45_000L) {
                model.generateContent(prompt)
            }
            response.text ?: "Channel optimization advice unavailable."
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "Channel recommendation failed: ${e.message}"
        }
    }

    fun streamChatWithTools(
        userMessage: String,
        onToolExecuting: (toolName: String) -> Unit
    ): Flow<String> = flow {
        val model = getChatModelWithTools()
        if (model == null) {
            emit("AI Diagnostics Assistant is offline: Firebase AI is initializing or credentials are required. Please verify network connectivity.")
            return@flow
        }

        val initialContent = content("user") { text(userMessage) }
        val response = withTimeout(45_000L) {
            model.generateContent(initialContent)
        }

        val functionCalls = response.functionCalls
        if (functionCalls.isNotEmpty()) {
            val responseParts = mutableListOf<FunctionResponsePart>()
            for (call in functionCalls) {
                val toolName = call.name
                val toolArgs = call.args
                onToolExecuting(toolName)

                val stringArgs = toolArgs.mapValues { (_, v) ->
                    try {
                        v.jsonPrimitive.contentOrNull ?: v.toString()
                    } catch (_: Exception) {
                        v.toString()
                    }
                }

                val resultData = executeNetworkTool(toolName, stringArgs)
                val jsonResponse = buildJsonObject {
                    resultData.forEach { (k, v) ->
                        when (v) {
                            is Number -> put(k, JsonPrimitive(v))
                            is Boolean -> put(k, JsonPrimitive(v))
                            else -> put(k, JsonPrimitive(v.toString()))
                        }
                    }
                }
                responseParts.add(FunctionResponsePart(toolName, jsonResponse))
            }

            val functionResponsesTurn = content("function") {
                responseParts.forEach { part(it) }
            }

            val modelTurn = response.candidates.firstOrNull()?.content ?: content("model") {
                functionCalls.forEach { part(it) }
            }

            withTimeout(45_000L) {
                model.generateContentStream(
                    initialContent,
                    modelTurn,
                    functionResponsesTurn
                ).collect { chunk ->
                    val chunkText = chunk.text
                    if (!chunkText.isNullOrEmpty()) {
                        emit(chunkText)
                    }
                }
            }
        } else {
            emit(response.text.orEmpty())
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun executeNetworkTool(name: String, args: Map<String, String>): Map<String, Any> {
        return when (name) {
            "runLivePingTest" -> {
                val host = args["targetHost"]?.takeIf { it.isNotBlank() }
                    ?: return mapOf("error" to "targetHost argument is required")
                val protocol = args["protocol"] ?: "ICMP"
                val summary = diagnosticsRepository.startPingStream(host, protocol, count = 4).first()
                mapOf(
                    "host" to summary.host,
                    "avg_rtt_ms" to summary.avgRttMs,
                    "packet_loss_percent" to summary.packetLossPercent,
                    "jitter_ms" to summary.jitterMs
                )
            }
            "scanDeviceOpenPorts" -> {
                val ip = args["ipAddress"]?.takeIf { it.isNotBlank() }
                    ?: networkInfoRepository.topology.value.defaultGateway
                if (ip.isBlank() || ip == "Unavailable") {
                    return mapOf("error" to "No target IP or active default gateway available")
                }
                val ports = NetworkUtils.WEB_ADMIN_PORTS
                val open = mutableListOf<Int>()
                for (port in ports) {
                    try {
                        java.net.Socket().use { s ->
                            s.connect(java.net.InetSocketAddress(ip, port), 200)
                            open.add(port)
                        }
                    } catch (_: Exception) {}
                }
                mapOf("ip" to ip, "open_ports" to open)
            }
            "measureGatewayVsWanLatency" -> {
                val gw = networkInfoRepository.topology.value.defaultGateway
                if (gw.isBlank() || gw == "Unavailable") {
                    return mapOf("error" to "No active default gateway detected")
                }
                val gwSummary = diagnosticsRepository.startPingStream(gw, "TCP", count = 3).first()
                val wanSummary = diagnosticsRepository.startPingStream("1.1.1.1", "ICMP", count = 3).first()
                mapOf(
                    "gateway_ip" to gw,
                    "gateway_avg_ms" to gwSummary.avgRttMs,
                    "wan_avg_ms" to wanSummary.avgRttMs,
                    "difference_ms" to (wanSummary.avgRttMs - gwSummary.avgRttMs)
                )
            }
            "getWifiChannelCongestionReport" -> {
                val top = networkInfoRepository.topology.value
                val aps = wifiSpeedRepository.wifiScanList.value
                val coChannel = aps.count { it.channel == NetworkUtils.frequencyToChannel(top.frequencyMhz) }
                mapOf(
                    "current_ssid" to top.ssid,
                    "current_channel" to NetworkUtils.frequencyToChannel(top.frequencyMhz),
                    "co_channel_aps_count" to coChannel,
                    "total_surrounding_aps" to aps.size
                )
            }
            "querySnmpDeviceHealth" -> {
                val ip = args["ipAddress"]?.takeIf { it.isNotBlank() }
                    ?: return mapOf("error" to "ipAddress argument is required")
                val versionStr = args["version"] ?: "v2c"
                val community = args["communityOrUser"] ?: ""
                val snmpVer = if (versionStr.contains("1")) SnmpVersion.V1 else SnmpVersion.V2C
                val config = SnmpTargetConfig(host = ip, version = snmpVer, community = community)
                val mib = snmpRepository?.fetchMibSummary(config)?.getOrNull()
                if (mib != null) {
                    mapOf(
                        "ip" to ip,
                        "sysDescr" to mib.sysDescr,
                        "sysUpTime" to mib.sysUpTime,
                        "sysName" to mib.sysName,
                        "interfaces_count" to mib.interfaces.size,
                        "cpu_load_percent" to (mib.cpuUtilization ?: 0)
                    )
                } else {
                    mapOf("ip" to ip, "status" to "SNMP agent query timed out or device unreachable")
                }
            }
            "inspectTopFlowTalkers" -> {
                val protoFilter = args["protocol"] ?: "ALL"
                val talkers = flowCollectorRepository?.topTalkers?.value?.take(5)?.map { t ->
                    "${t.srcIp} -> ${t.dstIp} (${t.serviceOrPort}): ${t.totalBytes} bytes"
                } ?: emptyList()
                val stats = flowCollectorRepository?.stats?.value
                mapOf(
                    "filter" to protoFilter,
                    "total_flows" to (stats?.totalFlowsDecoded ?: 0L),
                    "total_bytes" to (stats?.totalBytesProcessed ?: 0L),
                    "top_talkers" to talkers
                )
            }
            "auditServerBmcRedfish" -> {
                val bmcIp = args["bmcIp"]?.takeIf { it.isNotBlank() }
                    ?: return mapOf("error" to "bmcIp argument is required")
                val user = args["user"] ?: ""
                val pass = args["pass"] ?: ""
                if (user.isBlank() || pass.isBlank()) {
                    return mapOf("bmc_ip" to bmcIp, "status" to "Redfish credentials (user and pass) are required")
                }
                val summary = oobHardwareRepository?.getRedfishSummary(host = bmcIp, port = 443, user = user, pass = pass)?.getOrNull()
                if (summary != null) {
                    mapOf(
                        "bmc_ip" to bmcIp,
                        "model" to summary.model,
                        "serial" to summary.serialNumber,
                        "power_state" to summary.powerState.name,
                        "bios_version" to summary.biosVersion,
                        "thermal_sensors_count" to summary.thermalSensors.size
                    )
                } else {
                    mapOf("bmc_ip" to bmcIp, "status" to "Redfish HTTPS endpoint unreachable or auth failed")
                }
            }
            "runNmapServiceFingerprint" -> {
                val ip = args["targetIp"]?.takeIf { it.isNotBlank() }
                    ?: return mapOf("error" to "targetIp argument is required")
                val profileName = args["profile"] ?: "TOP_25"
                val profile = if (profileName.contains("100") || profileName.contains("INTENSE")) ScanProfile.INTENSE_SCAN else ScanProfile.QUICK_SCAN
                val report = nmapFingerprintEngine?.auditHost(targetHost = ip, profile = profile)
                if (report != null) {
                    mapOf(
                        "target" to report.host,
                        "open_ports" to report.openPorts,
                        "detected_os" to (report.osEstimate?.predictedOs ?: "Unknown"),
                        "services_count" to report.banners.size
                    )
                } else {
                    mapOf("target" to ip, "status" to "Port scan completed with no open ports")
                }
            }
            "executeNetworkOsShowCommand" -> {
                val gw = networkInfoRepository.topology.value.defaultGateway.takeIf { it.isNotBlank() && it != "Unavailable" }
                val host = args["host"]?.takeIf { it.isNotBlank() } ?: gw
                    ?: return mapOf("error" to "host argument is required (no active gateway detected)")
                val vendor = args["vendor"] ?: "CISCO"
                val command = args["command"] ?: "show version"
                val output = terminalAndNetOsRepository?.executeSshCommand(host = host, port = 22, command = command)?.getOrNull()
                if (output != null) {
                    mapOf(
                        "host" to host,
                        "vendor" to vendor,
                        "command" to command,
                        "output" to output.take(500)
                    )
                } else {
                    mapOf("host" to host, "command" to command, "status" to "SSH connection failed or command timed out")
                }
            }
            "testMqttOrCoapEndpoint" -> {
                val uri = args["uri"]?.takeIf { it.isNotBlank() }
                    ?: return mapOf("error" to "uri argument is required")
                val protocol = args["protocol"] ?: "MQTT"
                if (protocol.equals("COAP", ignoreCase = true)) {
                    val host = uri.substringBefore(":")
                    val port = uri.substringAfter(":", "5683").toIntOrNull() ?: 5683
                    val coapRes = iotProtocolsRepository?.executeCoapRequest(host = host, port = port, uriPath = "/.well-known/core")
                    val discovered = iotProtocolsRepository?.coapDiscoveredResources?.value?.map { it.path }.orEmpty()
                    mapOf(
                        "protocol" to "CoAP",
                        "host" to host,
                        "port" to port,
                        "response_code" to (coapRes?.responseCode ?: "No response"),
                        "discovered_resources" to discovered
                    )
                } else {
                    val host = uri.substringBefore(":")
                    val port = uri.substringAfter(":", "1883").toIntOrNull() ?: 1883
                    var socketOpen = false
                    try {
                        java.net.Socket().use { s ->
                            s.connect(java.net.InetSocketAddress(host, port), 2000)
                            socketOpen = true
                        }
                    } catch (_: Exception) {}
                    mapOf(
                        "protocol" to "MQTT",
                        "host" to host,
                        "port" to port,
                        "broker_port_open" to socketOpen
                    )
                }
            }
            "checkVpnAndDnsLeakStatus" -> {
                val tel = vpnRepository?.telemetry?.value
                val leak = vpnRepository?.runDnsLeakTest()
                mapOf(
                    "vpn_status" to (tel?.status?.name ?: "DISCONNECTED"),
                    "assigned_ip" to (tel?.assignedIp ?: "0.0.0.0"),
                    "bytes_in" to (tel?.bytesIn ?: 0L),
                    "bytes_out" to (tel?.bytesOut ?: 0L),
                    "dns_leak_detected" to (leak?.isLeakDetected ?: true),
                    "dns_leak_summary" to (leak?.summary ?: "VPN repository unavailable")
                )
            }
            "probeHttpOrSoapEndpoint" -> {
                val url = args["url"]?.takeIf { it.isNotBlank() }
                    ?: return mapOf("error" to "url argument is required")
                val resp = webServicesRepository?.executeRestRequest(url = url, method = HttpMethod.GET)
                    ?: return mapOf("error" to "WebServicesRepository unavailable")
                mapOf(
                    "url" to url,
                    "status_code" to resp.statusCode,
                    "is_success" to resp.isSuccess,
                    "dns_ms" to resp.timing.dnsLookupMs,
                    "tcp_ms" to resp.timing.tcpHandshakeMs,
                    "tls_ms" to resp.timing.tlsHandshakeMs,
                    "ttfb_ms" to resp.timing.ttfbMs,
                    "total_ms" to resp.timing.totalDurationMs
                )
            }
            "runDhcpOrTimeProbe" -> {
                val mode = args["mode"] ?: "DHCP"
                val core = coreProtocolsRepository ?: return mapOf("error" to "CoreProtocolsRepository unavailable")
                if (mode.equals("DHCP", ignoreCase = true)) {
                    val offers = core.discoverDhcpServers(timeoutMs = 2500)
                    mapOf(
                        "protocol" to "DHCP",
                        "offers_count" to offers.size,
                        "diagnostic" to (core.lastDhcpDiagnostic.value ?: ""),
                        "offered_ips" to offers.map { it.offeredIp }
                    )
                } else {
                    val host = args["host"]?.takeIf { it.isNotBlank() } ?: "pool.ntp.org"
                    val ntp = core.querySntp(host)
                    if (!ntp.isSuccess) {
                        return mapOf("protocol" to "SNTP", "host" to host, "status" to "Error: ${ntp.errorMessage ?: "Query failed"}")
                    }
                    mapOf(
                        "protocol" to "SNTP",
                        "host" to host,
                        "offset_ms" to ntp.clockOffsetMs,
                        "round_trip_ms" to ntp.roundTripDelayMs,
                        "stratum" to ntp.stratum
                    )
                }
            }
            else -> mapOf("status" to "unknown tool")
        }
    }
}
