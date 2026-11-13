// === FILE: app/src/main/java/com/enterwebslinger/network/data/repository/NetworkTelemetryAggregator.kt ===
package com.enterwebslinger.network.data.repository

import com.enterwebslinger.network.core.util.NetworkUtils
import com.enterwebslinger.network.domain.model.LanSecuritySnapshot
import com.enterwebslinger.network.domain.model.LatencyJitterState
import com.enterwebslinger.network.domain.model.NetworkDiagnosticSnapshot
import com.enterwebslinger.network.domain.model.ThroughputState
import com.enterwebslinger.network.domain.model.TracerouteSnapshotHop
import com.enterwebslinger.network.domain.model.WifiRfState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.abs

class NetworkTelemetryAggregator(
    private val networkInfoRepository: NetworkInfoRepository,
    private val lanScannerRepository: LanScannerRepository,
    private val diagnosticsRepository: DiagnosticsRepository,
    private val wifiSpeedRepository: WifiSpeedRepository,
    private val snmpRepository: SnmpRepository? = null,
    private val flowCollectorRepository: FlowCollectorRepository? = null,
    private val syslogRepository: SyslogRepository? = null,
    private val vpnRepository: VpnRepository? = null,
    private val iotProtocolsRepository: IotProtocolsRepository? = null
) {
    suspend fun buildSnapshot(): NetworkDiagnosticSnapshot = withContext(Dispatchers.IO) {
        val topology = networkInfoRepository.topology.value
        val speed = wifiSpeedRepository.speedState.value
        val surroundingAps = wifiSpeedRepository.wifiScanList.value
        val devices = lanScannerRepository.devices.value

        val activeChannel = NetworkUtils.frequencyToChannel(topology.frequencyMhz)
        val coChannelCount = surroundingAps.count {
            it.channel == activeChannel && !it.isCurrentNetwork
        }
        val channelWidth = when (topology.frequencyBand) {
            "2.4 GHz" -> 20
            "5 GHz", "6 GHz" -> 80
            else -> 0
        }
        val wifiRf = WifiRfState(
            ssid = topology.ssid,
            bssid = topology.bssid,
            band = topology.frequencyBand,
            channelWidthMhz = channelWidth,
            rssiDbm = topology.rssiDbm,
            linkSpeedMbps = topology.linkSpeedMbps,
            overlappingCoChannelApsCount = coChannelCount,
            channel = activeChannel
        )

        val gw = topology.defaultGateway
        val gwLatency = if (gw.isNotBlank() && gw != "Unavailable") {
            probeTargetQuickly(gw, "Gateway")
        } else {
            LatencyJitterState("Gateway", "TCP/ICMP", 0f, 0f, 0f, 0f, 100f)
        }

        val dnsTarget = topology.dnsServers.firstOrNull { it.isNotBlank() }
        val wanLatency = if (!dnsTarget.isNullOrBlank()) {
            probeTargetQuickly(dnsTarget, "DNS ($dnsTarget)")
        } else {
            LatencyJitterState("DNS", "TCP/ICMP", 0f, 0f, 0f, 0f, 100f)
        }

        val hops = mutableListOf<TracerouteSnapshotHop>()
        if (gw.isNotBlank() && gw != "Unavailable") {
            hops.add(TracerouteSnapshotHop(1, gw, "local-gateway", gwLatency.avgMs, gwLatency.avgMs))
        }
        if (!dnsTarget.isNullOrBlank() && dnsTarget != gw) {
            hops.add(
                TracerouteSnapshotHop(
                    hopNumber = hops.size + 1,
                    ip = dnsTarget,
                    hostname = dnsTarget,
                    rttMs = wanLatency.avgMs,
                    rttDeltaMs = (wanLatency.avgMs - gwLatency.avgMs).coerceAtLeast(0f)
                )
            )
        }

        val throughput = ThroughputState(
            downloadSpeedMbps = speed.downloadSpeedMbps,
            uploadSpeedMbps = speed.uploadSpeedMbps,
            unloadedPingMs = speed.unloadedPingMs,
            loadedPingMs = speed.loadedPingMs,
            bufferbloatDeltaMs = speed.bufferbloatDeltaMs
        )

        val highRiskPorts = setOf(21, 22, 23, 80, 445, 3389, 8080)
        var exposedHighRiskCount = 0
        val exposedSummary = mutableListOf<String>()

        devices.forEach { dev ->
            val risky = dev.openPorts.filter { highRiskPorts.contains(it) }
            if (risky.isNotEmpty()) {
                exposedHighRiskCount += risky.size
                exposedSummary.add("${dev.ip} (${dev.hostname}): ports ${risky.joinToString(",")}")
            }
        }

        val lanSecurity = LanSecuritySnapshot(
            totalDiscoveredDevicesCount = devices.size,
            devicesWithCustomLabelsCount = devices.count { it.customLabel != null },
            exposedHighRiskPortsCount = exposedHighRiskCount,
            exposedPortsSummary = exposedSummary
        )

        NetworkDiagnosticSnapshot(
            wifiRfState = wifiRf,
            gatewayLatency = gwLatency,
            externalDnsLatency = wanLatency,
            tracerouteHops = hops,
            throughput = throughput,
            lanSecurity = lanSecurity
        )
    }

    private fun probeTargetQuickly(host: String, label: String): LatencyJitterState {
        val samples = mutableListOf<Float>()
        var successCount = 0
        for (i in 0 until 4) {
            val t0 = System.currentTimeMillis()
            var ok = false
            try {
                Socket().use { s ->
                    s.connect(InetSocketAddress(host, 53), 250)
                    ok = true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                try {
                    val addr = InetAddress.getByName(host)
                    ok = addr.isReachable(250)
                } catch (_: Exception) {}
            }
            val elapsed = (System.currentTimeMillis() - t0).toFloat()
            if (ok) {
                successCount++
                samples.add(elapsed.coerceAtLeast(1f))
            }
        }

        val min = samples.minOrNull() ?: 0f
        val max = samples.maxOrNull() ?: 0f
        val avg = if (samples.isNotEmpty()) samples.average().toFloat() else 0f
        val jitter = if (samples.isNotEmpty()) samples.map { abs(it - avg) }.average().toFloat() else 0f
        val loss = ((4 - successCount).toFloat() / 4f) * 100f

        return LatencyJitterState(
            target = label,
            protocol = "TCP/ICMP",
            minMs = min,
            avgMs = avg,
            maxMs = max,
            jitterMs = jitter,
            packetLossPercent = loss
        )
    }

    fun snapshotToJsonPrompt(snapshot: NetworkDiagnosticSnapshot): String {
        val snmpTrapCount = snmpRepository?.trapRecords?.value?.size ?: 0
        val snmpTrapActive = snmpRepository?.isTrapReceiverRunning?.value ?: false
        val flowStats = flowCollectorRepository?.stats?.value
        val syslogCount = syslogRepository?.messages?.value?.size ?: 0
        val vpnTel = vpnRepository?.telemetry?.value
        val mqttConnected = iotProtocolsRepository?.isMqttConnected?.value ?: false

        return buildString {
            appendLine("{")
            appendLine("  \"wifi_rf\": {")
            appendLine("    \"ssid\": \"${escapeJson(snapshot.wifiRfState.ssid)}\",")
            appendLine("    \"band\": \"${escapeJson(snapshot.wifiRfState.band)}\",")
            appendLine("    \"channel\": ${snapshot.wifiRfState.channel},")
            appendLine("    \"rssi_dbm\": ${snapshot.wifiRfState.rssiDbm},")
            appendLine("    \"link_speed_mbps\": ${snapshot.wifiRfState.linkSpeedMbps},")
            appendLine("    \"co_channel_aps\": ${snapshot.wifiRfState.overlappingCoChannelApsCount}")
            appendLine("  },")
            appendLine("  \"gateway_latency\": {")
            appendLine("    \"avg_ms\": ${snapshot.gatewayLatency.avgMs},")
            appendLine("    \"loss_percent\": ${snapshot.gatewayLatency.packetLossPercent}")
            appendLine("  },")
            appendLine("  \"wan_dns_latency\": {")
            appendLine("    \"avg_ms\": ${snapshot.externalDnsLatency.avgMs},")
            appendLine("    \"jitter_ms\": ${snapshot.externalDnsLatency.jitterMs},")
            appendLine("    \"loss_percent\": ${snapshot.externalDnsLatency.packetLossPercent}")
            appendLine("  },")
            appendLine("  \"throughput_and_bufferbloat\": {")
            appendLine("    \"download_mbps\": ${snapshot.throughput.downloadSpeedMbps},")
            appendLine("    \"upload_mbps\": ${snapshot.throughput.uploadSpeedMbps},")
            appendLine("    \"unloaded_ping_ms\": ${snapshot.throughput.unloadedPingMs},")
            appendLine("    \"loaded_ping_ms\": ${snapshot.throughput.loadedPingMs},")
            appendLine("    \"bufferbloat_delta_ms\": ${snapshot.throughput.bufferbloatDeltaMs}")
            appendLine("  },")
            appendLine("  \"lan_security\": {")
            appendLine("    \"devices_count\": ${snapshot.lanSecurity.totalDiscoveredDevicesCount},")
            appendLine("    \"exposed_high_risk_ports\": ${snapshot.lanSecurity.exposedHighRiskPortsCount},")
            appendLine("    \"exposed_details\": [${snapshot.lanSecurity.exposedPortsSummary.joinToString { "\"${escapeJson(it)}\"" }}]")
            appendLine("  },")
            appendLine("  \"enterprise_and_tunnel\": {")
            appendLine("    \"snmp_trap_listener_active\": $snmpTrapActive,")
            appendLine("    \"snmp_traps_received\": $snmpTrapCount,")
            appendLine("    \"flows_decoded\": ${flowStats?.totalFlowsDecoded ?: 0L},")
            appendLine("    \"syslog_messages_buffered\": $syslogCount,")
            appendLine("    \"vpn_status\": \"${vpnTel?.status?.name ?: "DISCONNECTED"}\",")
            appendLine("    \"vpn_assigned_ip\": \"${escapeJson(vpnTel?.assignedIp ?: "0.0.0.0")}\",")
            appendLine("    \"mqtt_broker_connected\": $mqttConnected")
            appendLine("  }")
            appendLine("}")
        }
    }

    private fun escapeJson(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")
}
