// === FILE: app/src/main/java/com/enterwebslinger/network/service/AiDiagnosticService.kt ===
package com.enterwebslinger.network.service

import android.content.Context
import android.util.Log
import com.enterwebslinger.network.core.util.NetworkUtils
import com.enterwebslinger.network.data.protocol.PcapCaptureEngine
import com.enterwebslinger.network.data.repository.AaaDiagnosticsRepository
import com.enterwebslinger.network.data.repository.AdvancedProtocolRepository
import com.enterwebslinger.network.data.repository.BatchAutomationRepository
import com.enterwebslinger.network.data.repository.CellularTelemetryRepository
import com.enterwebslinger.network.data.repository.CoreProtocolsRepository
import com.enterwebslinger.network.data.repository.DiagnosticsRepository
import com.enterwebslinger.network.data.repository.EnterpriseIdentityRepository
import com.enterwebslinger.network.data.repository.EnterpriseVpnRepository
import com.enterwebslinger.network.data.repository.FirewallManagementRepository
import com.enterwebslinger.network.data.repository.FlowCollectorRepository
import com.enterwebslinger.network.data.repository.IotProtocolsRepository
import com.enterwebslinger.network.data.repository.LanScannerRepository
import com.enterwebslinger.network.data.repository.LanSecurityAuditRepository
import com.enterwebslinger.network.data.repository.MtuDiscoveryRepository
import com.enterwebslinger.network.data.repository.NetworkInfoRepository
import com.enterwebslinger.network.data.repository.NetworkTelemetryAggregator
import com.enterwebslinger.network.data.repository.NmapFingerprintEngine
import com.enterwebslinger.network.data.repository.OobHardwareRepository
import com.enterwebslinger.network.data.repository.RadiusAndAaaRepository
import com.enterwebslinger.network.data.repository.SecurityRepository
import com.enterwebslinger.network.data.repository.SnmpRepository
import com.enterwebslinger.network.data.repository.TerminalAndNetOsRepository
import com.enterwebslinger.network.data.repository.VpnRepository
import com.enterwebslinger.network.data.repository.WebServicesRepository
import com.enterwebslinger.network.data.repository.WifiSpeedRepository
import com.enterwebslinger.network.domain.model.AiAuditFinding
import com.enterwebslinger.network.domain.model.AiAuditReport
import com.enterwebslinger.network.domain.model.DiscoveredDevice
import com.enterwebslinger.network.domain.model.FirewallVendor
import com.enterwebslinger.network.domain.model.HttpMethod
import com.enterwebslinger.network.domain.model.RadiusAuthProtocol
import com.enterwebslinger.network.domain.model.RadiusAuthRequestConfig
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
    private val coreProtocolsRepository: CoreProtocolsRepository? = null,
    private val mtuDiscoveryRepository: MtuDiscoveryRepository? = null,
    private val advancedProtocolRepository: AdvancedProtocolRepository? = null,
    private val lanSecurityAuditRepository: LanSecurityAuditRepository? = null,
    private val cellularTelemetryRepository: CellularTelemetryRepository? = null,
    private val aaaDiagnosticsRepository: AaaDiagnosticsRepository? = null,
    private val pcapCaptureEngine: PcapCaptureEngine? = null,
    private val batchAutomationRepository: BatchAutomationRepository? = null,
    private val firewallManagementRepository: FirewallManagementRepository? = null,
    private val enterpriseVpnRepository: EnterpriseVpnRepository? = null,
    private val radiusAndAaaRepository: RadiusAndAaaRepository? = null,
    private val enterpriseIdentityRepository: EnterpriseIdentityRepository? = null
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

    private val pathMtuToolDeclaration = FunctionDeclaration(
        name = "runPathMtuDiscovery",
        description = "Discovers exact end-to-end Path MTU between 576 and 1500 bytes and recommends WireGuard/PPPoE/IPsec/VXLAN MSS clamping values.",
        parameters = mapOf(
            "targetHost" to Schema.string(description = "Destination host or IP address to probe with DF flag")
        )
    )

    private val dnsAndAlpnBenchmarkToolDeclaration = FunctionDeclaration(
        name = "runDnsAndHttp3Benchmark",
        description = "Benchmarks DNS resolvers with DNSSEC AD check and tests Happy Eyeballs IPv6/IPv4 + TLS ALPN h2/h3 support on a host.",
        parameters = mapOf(
            "domain" to Schema.string(description = "Domain or HTTPS hostname (e.g. cloudflare.com)")
        )
    )

    private val onvifAndEvilTwinToolDeclaration = FunctionDeclaration(
        name = "scanOnvifOrEvilTwin",
        description = "Discovers ONVIF/RTSP security cameras via WS-Discovery multicast or audits Wi-Fi scan results for Evil Twin / Rogue AP downgrades.",
        parameters = mapOf(
            "auditType" to Schema.enumeration(listOf("ONVIF", "EVIL_TWIN"), description = "Security audit type")
        )
    )

    private val cellularTelemetryToolDeclaration = FunctionDeclaration(
        name = "inspectCellularSignal",
        description = "Reads live 5G NR / 4G LTE cellular RF telemetry (Operator, NCI/CI, PCI, TAC, ARFCN, RSRP, RSRQ, SINR, neighbors).",
        parameters = emptyMap()
    )

    private val trafficAndPcapSummaryToolDeclaration = FunctionDeclaration(
        name = "getTrafficAndPcapSummary",
        description = "Returns live TrafficStats RX/TX rates and current Loopback PCAP sniffer capture counts.",
        parameters = emptyMap()
    )

    private val checkFirewallHealthToolDeclaration = FunctionDeclaration(
        name = "checkFirewallHealthAndTunnels",
        description = "Queries a saved firewall appliance (Sophos XG/XGS, Fortinet FortiGate, Palo Alto PAN-OS, Cisco ASA/FTD, or pfSense/OPNsense) for CPU/RAM health, HA state, WAN SLA, and IPsec/XFRM tunnel status.",
        parameters = mapOf(
            "vendor" to Schema.string(description = "Firewall vendor (e.g., SOPHOS_XGS, FORTINET_FORTIGATE, PALO_ALTO_PANOS, CISCO_ASA_FTD, PFSENSE_OPNSENSE)"),
            "host" to Schema.string(description = "Appliance IP or hostname")
        )
    )

    private val analyzeIpsecDropLogToolDeclaration = FunctionDeclaration(
        name = "analyzeIpsecTunnelDropLog",
        description = "Analyzes raw IPsec / StrongSwan / XFRM / IKEv2 / FortiGate / ASA / Sophos tunnel drop logs for Phase 1/2 mismatches, Invalid SPI, DPD timeouts, or NAT-T issues.",
        parameters = mapOf(
            "rawLogText" to Schema.string(description = "Raw IKE/IPsec/XFRM diagnostic log excerpt"),
            "localVendor" to Schema.string(description = "Local firewall/VPN vendor"),
            "remoteVendor" to Schema.string(description = "Remote peer vendor")
        )
    )

    private val simulateFirewallPolicyMatchToolDeclaration = FunctionDeclaration(
        name = "simulateFirewallPolicyMatch",
        description = "Simulates a firewall security policy & NAT match (PAN-OS security-policy-match, Cisco ASA packet-tracer, or FortiGate rule lookup) for a 5-tuple flow.",
        parameters = mapOf(
            "vendor" to Schema.string(description = "Firewall vendor (PALO_ALTO_PANOS, CISCO_ASA_FTD, FORTINET_FORTIGATE)"),
            "host" to Schema.string(description = "Firewall management IP or hostname"),
            "srcIp" to Schema.string(description = "Source IPv4 address"),
            "dstIp" to Schema.string(description = "Destination IPv4 address"),
            "dstPort" to Schema.integer(description = "Destination TCP/UDP port"),
            "protocol" to Schema.string(description = "Protocol tcp or udp")
        )
    )

    private val runRadiusAuthVsaToolDeclaration = FunctionDeclaration(
        name = "runRadiusAuthAndVsaTest",
        description = "Executes a live RADIUS RFC 2865 Access-Request (PAP/CHAP/MS-CHAPv2 with Message-Authenticator) using vaulted server secret and decodes returned VSAs & 802.1X VLAN tags.",
        parameters = mapOf(
            "serverIp" to Schema.string(description = "RADIUS server IPv4 or hostname"),
            "port" to Schema.integer(description = "RADIUS UDP port (default 1812)"),
            "authProtocol" to Schema.string(description = "PAP, CHAP, or MSCHAPV2"),
            "username" to Schema.string(description = "Username to test")
        )
    )

    val allToolDeclarations: List<FunctionDeclaration> = listOf(
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
        dhcpOrTimeProbeToolDeclaration,
        pathMtuToolDeclaration,
        dnsAndAlpnBenchmarkToolDeclaration,
        onvifAndEvilTwinToolDeclaration,
        cellularTelemetryToolDeclaration,
        trafficAndPcapSummaryToolDeclaration,
        checkFirewallHealthToolDeclaration,
        analyzeIpsecDropLogToolDeclaration,
        simulateFirewallPolicyMatchToolDeclaration,
        runRadiusAuthVsaToolDeclaration
    )

    val registeredToolNames: Set<String> = setOf(
        "runLivePingTest",
        "scanDeviceOpenPorts",
        "measureGatewayVsWanLatency",
        "getWifiChannelCongestionReport",
        "querySnmpDeviceHealth",
        "inspectTopFlowTalkers",
        "auditServerBmcRedfish",
        "runNmapServiceFingerprint",
        "executeNetworkOsShowCommand",
        "testMqttOrCoapEndpoint",
        "checkVpnAndDnsLeakStatus",
        "probeHttpOrSoapEndpoint",
        "runDhcpOrTimeProbe",
        "runPathMtuDiscovery",
        "runDnsAndHttp3Benchmark",
        "scanOnvifOrEvilTwin",
        "inspectCellularSignal",
        "getTrafficAndPcapSummary",
        "checkFirewallHealthAndTunnels",
        "analyzeIpsecTunnelDropLog",
        "simulateFirewallPolicyMatch",
        "runRadiusAuthAndVsaTest"
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
                tools = listOf(Tool.functionDeclarations(allToolDeclarations))
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

    suspend fun explainTechnicalOutput(
        contextTitle: String,
        rawTelemetry: String
    ): String = withContext(Dispatchers.IO) {
        try {
            val model = getGeneralModel()
                ?: return@withContext "AI explanation unavailable: Firebase AI initializing or offline."
            val prompt = """
                As a Principal Network Security & AAA Engineer, explain the following $contextTitle telemetry clearly and concisely (3-4 bullet points max):
                - Highlight root causes (e.g., Phase 1/2 IKE mismatch, NAT-T UDP 4500 drop, Firewall policy shadow, Blast-RADIUS Message-Authenticator failure, MS-CHAPv2 rejection, or LDAP Invalid Credentials 49).
                - Provide the exact corrective CLI or GUI configuration step.
                
                Raw Result:
                ${rawTelemetry.take(2500)}
            """.trimIndent()
            val response = withTimeout(45_000L) {
                model.generateContent(prompt)
            }
            response.text ?: "Explanation unavailable."
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "AI explanation unavailable: ${e.message}"
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

    internal suspend fun executeNetworkTool(name: String, args: Map<String, String>): Map<String, Any> {
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
            "runPathMtuDiscovery" -> {
                val host = args["targetHost"]?.takeIf { it.isNotBlank() } ?: "1.1.1.1"
                val mtuRepo = mtuDiscoveryRepository ?: return mapOf("error" to "MtuDiscoveryRepository unavailable")
                val res = mtuRepo.discoverPathMtu(host)
                mapOf(
                    "target" to res.targetHost,
                    "resolved_ip" to res.resolvedIp,
                    "path_mtu_bytes" to res.pathMtuBytes,
                    "max_icmp_payload" to res.maxPayloadBytes,
                    "overhead_bytes" to res.ipIcmpOverheadBytes,
                    "error" to (res.errorMessage ?: "none")
                )
            }
            "runDnsAndHttp3Benchmark" -> {
                val domain = args["domain"]?.takeIf { it.isNotBlank() } ?: "cloudflare.com"
                val adv = advancedProtocolRepository ?: return mapOf("error" to "AdvancedProtocolRepository unavailable")
                val dnsList = adv.runDnsAndDnssecBenchmark(domain)
                val he = adv.testHappyEyeballsAndAlpn(domain, 443)
                mapOf(
                    "domain" to domain,
                    "fastest_dns" to (dnsList.firstOrNull { it.isSuccess }?.let { "${it.resolverName} (${it.avgRttMs}ms, AD=${it.dnssecAuthenticatedData})" } ?: "none"),
                    "happy_eyeballs_winner" to he.winnerFamily,
                    "alpn_protocol" to he.alpnProtocol,
                    "supports_http3_quic" to he.supportsHttp3Quic
                )
            }
            "scanOnvifOrEvilTwin" -> {
                val audit = lanSecurityAuditRepository ?: return mapOf("error" to "LanSecurityAuditRepository unavailable")
                val type = args["auditType"] ?: "EVIL_TWIN"
                if (type.equals("ONVIF", true)) {
                    val cams = audit.discoverOnvifCameras()
                    mapOf("audit_type" to "ONVIF", "cameras_found" to cams.size, "ips" to cams.map { it.ip })
                } else {
                    val findings = audit.auditEvilTwinAndWifiSecurity().getOrDefault(emptyList())
                    mapOf(
                        "audit_type" to "EVIL_TWIN",
                        "findings_count" to findings.size,
                        "highlights" to findings.take(5).map { "${it.severity.label}: ${it.ssid} (${it.reason})" }
                    )
                }
            }
            "inspectCellularSignal" -> {
                val cellRepo = cellularTelemetryRepository ?: return mapOf("error" to "CellularTelemetryRepository unavailable")
                val st = cellRepo.inspectCellularTelemetry()
                mapOf(
                    "operator" to st.operatorName,
                    "radio_type" to st.radioType.label,
                    "band" to st.bandLabel,
                    "rsrp_dbm" to (st.rsrpDbm ?: 0),
                    "rsrq_db" to (st.rsrqDb ?: 0),
                    "sinr_db" to (st.sinrDb ?: 0),
                    "neighbors_count" to st.neighbors.size,
                    "error" to (st.errorMessage ?: "none")
                )
            }
            "getTrafficAndPcapSummary" -> {
                val sample = batchAutomationRepository?.latestTrafficSample?.value
                val pcap = pcapCaptureEngine?.stats?.value
                mapOf(
                    "rx_bps" to (sample?.totalRxBps ?: 0L),
                    "tx_bps" to (sample?.totalTxBps ?: 0L),
                    "pcap_capturing" to (pcap?.isCapturing ?: false),
                    "pcap_packets" to (pcap?.packetCount ?: 0L),
                    "pcap_bytes" to (pcap?.totalBytes ?: 0L)
                )
            }
            "checkFirewallHealthAndTunnels" -> {
                val host = args["host"].orEmpty().trim()
                val vendorArg = args["vendor"].orEmpty().trim()
                val fwRepo = firewallManagementRepository
                    ?: return mapOf("error" to "FirewallManagementRepository unavailable")
                val appliances = fwRepo.savedAppliancesFlow.first()
                val matched = appliances.firstOrNull {
                    (host.isBlank() || it.host.equals(host, ignoreCase = true) || it.name.contains(host, true)) &&
                        (vendorArg.isBlank() || it.vendor.name.contains(vendorArg, true) || it.vendor.shortCode.contains(vendorArg, true))
                } ?: appliances.firstOrNull { host.isNotBlank() && it.host.equals(host, ignoreCase = true) }

                if (matched == null) {
                    return mapOf(
                        "status" to "No saved firewall appliance found matching '$host' ($vendorArg). Add the appliance in Firewall Hub first so credentials stay sealed in AndroidKeyStore."
                    )
                }
                val snap = fwRepo.fetchApplianceDashboard(matched).getOrElse { err ->
                    return mapOf("host" to matched.host, "vendor" to matched.vendor.shortCode, "error" to (err.localizedMessage ?: "Poll failed"))
                }
                mapOf(
                    "appliance" to matched.name,
                    "vendor" to matched.vendor.displayName,
                    "host" to matched.host,
                    "cpu_percent" to (snap.health?.cpuPercent ?: 0f),
                    "memory_percent" to (snap.health?.memoryPercent ?: 0f),
                    "active_sessions" to (snap.health?.activeSessions ?: 0L),
                    "ha_state" to "${snap.health?.haRole ?: "--"} (${snap.health?.haState ?: "--"})",
                    "tunnels_total" to snap.ipsecTunnels.size,
                    "tunnels_up" to snap.ipsecTunnels.count { it.isUp },
                    "wan_gateways" to snap.wanGateways.joinToString("; ") { "${it.name}:${it.status}(${it.latencyMs}ms)" }
                )
            }
            "analyzeIpsecTunnelDropLog" -> {
                val raw = args["rawLogText"].orEmpty()
                val localVendor = args["localVendor"] ?: "LocalPeer"
                val remoteVendor = args["remoteVendor"] ?: "RemotePeer"
                val findings = mutableListOf<String>()
                if (raw.contains("NO_PROPOSAL_CHOSEN", ignoreCase = true) || raw.contains("proposal mismatch", true)) {
                    findings.add("Phase 1/2 Crypto Mismatch (NO_PROPOSAL_CHOSEN): Verify identical AES-GCM/SHA-256/DH-Group (e.g., DH14/19/20) between $localVendor and $remoteVendor.")
                }
                if (raw.contains("INVALID_SPI", ignoreCase = true) || raw.contains("no SA found", true)) {
                    findings.add("Desynchronized SPI (INVALID_SPI): One peer flushed its Child SA while the other kept transmitting; bounce Phase-2 SA or enable DPD.")
                }
                if (raw.contains("DPD", ignoreCase = true) || raw.contains("retransmit", true) || raw.contains("timeout", true)) {
                    findings.add("Dead Peer Detection / UDP 500 or 4500 Reachability Drop: Check upstream ISP firewalling UDP 500/4500 or asymmetrical NAT-T traversal.")
                }
                if (raw.contains("TS_UNACCEPTABLE", ignoreCase = true) || raw.contains("traffic selector", true)) {
                    findings.add("Traffic Selector Mismatch (TS_UNACCEPTABLE): Verify local/remote proxy ID subnets match identically (or switch to route-based VTI 0.0.0.0/0).")
                }
                if (raw.contains("AUTH_FAILED", ignoreCase = true) || raw.contains("PSK", true)) {
                    findings.add("Authentication Failure: Check Pre-Shared Key / X.509 IKE ID (@fqdn vs IPv4) on both $localVendor and $remoteVendor.")
                }
                if (findings.isEmpty()) {
                    findings.add("No signature drop token matched directly; check IKEv2 port 500/4500 reachability and ESP Protocol 50 filtering.")
                }
                mapOf(
                    "local_vendor" to localVendor,
                    "remote_vendor" to remoteVendor,
                    "detected_issues" to findings,
                    "log_length_chars" to raw.length
                )
            }
            "simulateFirewallPolicyMatch" -> {
                val host = args["host"].orEmpty().trim()
                val vendorArg = args["vendor"].orEmpty().trim()
                val srcIp = args["srcIp"] ?: "10.0.0.10"
                val dstIp = args["dstIp"] ?: "1.1.1.1"
                val dstPort = args["dstPort"]?.toIntOrNull() ?: 443
                val proto = args["protocol"]?.lowercase() ?: "tcp"
                val fwRepo = firewallManagementRepository
                    ?: return mapOf("error" to "FirewallManagementRepository unavailable")
                val appliances = fwRepo.savedAppliancesFlow.first()
                val matched = appliances.firstOrNull {
                    (host.isBlank() || it.host.equals(host, ignoreCase = true)) &&
                        (vendorArg.isBlank() || it.vendor.name.contains(vendorArg, true) || it.vendor.shortCode.contains(vendorArg, true))
                } ?: return mapOf("status" to "No saved firewall appliance found matching '$host' ($vendorArg). Save appliance in Firewall Hub first.")

                when (matched.vendor) {
                    FirewallVendor.PALO_ALTO_PANOS -> {
                        val protoNum = if (proto == "udp") 17 else 6
                        val res = fwRepo.testPanOsSecurityAndNatMatch(matched, "trust", "untrust", srcIp, dstIp, dstPort, protoNum, "ssl")
                            .getOrElse { return mapOf("error" to (it.localizedMessage ?: "PAN-OS query failed")) }
                        mapOf("vendor" to "PAN-OS", "rule" to res.matchedRuleName, "action" to res.action, "nat" to res.natRuleMatched)
                    }
                    FirewallVendor.CISCO_ASA_FTD -> {
                        val res = fwRepo.runCiscoPacketTracer(matched, "inside", proto, srcIp, 52000, dstIp, dstPort)
                            .getOrElse { return mapOf("error" to (it.localizedMessage ?: "Cisco packet-tracer failed")) }
                        mapOf("vendor" to "Cisco ASA", "verdict" to res.verdict, "drop_reason" to res.dropReason, "phases" to res.phases.size)
                    }
                    else -> {
                        val snap = fwRepo.fetchApplianceDashboard(matched).getOrNull()
                        mapOf("vendor" to matched.vendor.shortCode, "policy_count" to (snap?.policyHits?.size ?: 0), "note" to "Policy hit-table fetched for ${matched.host}")
                    }
                }
            }
            "runRadiusAuthAndVsaTest" -> {
                val serverIp = args["serverIp"]?.takeIf { it.isNotBlank() }
                    ?: return mapOf("error" to "serverIp is required")
                val port = args["port"]?.toIntOrNull() ?: 1812
                val proto = when (args["authProtocol"]?.uppercase()) {
                    "CHAP" -> RadiusAuthProtocol.CHAP
                    "MSCHAPV2", "MS-CHAPV2" -> RadiusAuthProtocol.MSCHAPV2
                    else -> RadiusAuthProtocol.PAP
                }
                val username = args["username"]?.takeIf { it.isNotBlank() } ?: "testuser"
                val radiusRepo = radiusAndAaaRepository
                    ?: return mapOf("error" to "RadiusAndAaaRepository unavailable")
                val secret = radiusRepo.findSavedSecretForHost(serverIp)
                    ?: return mapOf("status" to "No saved RADIUS profile/secret found for $serverIp in AAA Workbench Vault. Save a RADIUS server profile first.")
                val res = radiusRepo.sendAuthenticationTest(
                    RadiusAuthRequestConfig(
                        serverIp = serverIp,
                        port = port,
                        sharedSecret = secret,
                        username = username,
                        password = "",
                        protocol = proto
                    )
                )
                mapOf(
                    "server_ip" to res.serverIp,
                    "port" to res.port,
                    "protocol" to res.protocolUsed,
                    "response_code" to res.responsePacketCode.label,
                    "rtt_ms" to res.rttMs,
                    "message_auth_valid" to res.messageAuthenticatorValid,
                    "vlan_8021x" to res.dot1xSummary.vlanId,
                    "vsas_decoded" to res.decodedVsas.map { "${it.attributeName}=${it.decodedValue}" },
                    "error" to (res.errorMessage ?: "none")
                )
            }
            else -> mapOf("status" to "unknown tool")
        }
    }
}
