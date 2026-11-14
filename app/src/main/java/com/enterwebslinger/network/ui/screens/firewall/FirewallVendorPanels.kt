// === FILE: app/src/main/java/com/enterwebslinger/network/ui/screens/firewall/FirewallVendorPanels.kt ===
package com.enterwebslinger.network.ui.screens.firewall

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enterwebslinger.network.domain.model.FirewallApplianceProfile
import com.enterwebslinger.network.domain.model.FirewallHealthSummary
import com.enterwebslinger.network.domain.model.FirewallHubSnapshot
import com.enterwebslinger.network.domain.model.FirewallIpsecTunnel
import com.enterwebslinger.network.domain.model.FirewallVendor
import com.enterwebslinger.network.domain.model.WanGatewaySlaEntry
import com.enterwebslinger.network.ui.components.ExplainResultSection
import com.enterwebslinger.network.ui.screens.ai.AiAssistantViewModel
import com.enterwebslinger.network.ui.theme.BorderStrokeColor
import com.enterwebslinger.network.ui.theme.CardBackground
import com.enterwebslinger.network.ui.theme.CardBackgroundElevated
import com.enterwebslinger.network.ui.theme.CyberCyan
import com.enterwebslinger.network.ui.theme.DarkBackground
import com.enterwebslinger.network.ui.theme.StatusAmber
import com.enterwebslinger.network.ui.theme.StatusCrimson
import com.enterwebslinger.network.ui.theme.StatusEmerald
import com.enterwebslinger.network.ui.theme.TextMuted
import com.enterwebslinger.network.ui.theme.TextPrimary
import com.enterwebslinger.network.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun FirewallOverviewPanel(
    appliance: FirewallApplianceProfile,
    snapshot: FirewallHubSnapshot?,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    aiViewModel: AiAssistantViewModel? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth().testTag("fw_overview_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(appliance.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                        Text(
                            "${appliance.vendor.displayName} • ${appliance.host}:${appliance.apiPort}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = CyberCyan
                        )
                    }
                    Button(
                        onClick = onRefresh,
                        enabled = !isLoading,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("fw_poll_now_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF00363D)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Poll Live", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                val health = snapshot?.health
                if (health != null) {
                    HealthMetricsBlock(health)
                    ExplainResultSection(
                        diagnosisKey = "fw_health_${appliance.id}",
                        contextTitle = "Firewall Health (${appliance.vendor.shortCode})",
                        rawResultText = "Appliance=${appliance.name} (${appliance.host}) CPU=${health.cpuPercent}% Mem=${health.memoryPercent}% Sessions=${health.activeSessions} HA=${health.haRole}/${health.haState} Gateways=${snapshot.wanGateways.joinToString { "${it.name}:${it.status}(${it.latencyMs}ms)" }}",
                        aiViewModel = aiViewModel,
                        buttonTestTag = "explain_fw_overview_btn"
                    )
                } else {
                    Text(
                        text = "Tap 'Poll Live' to query live CPU/RAM, session table, HA state, WAN SLA, and IPsec tunnels.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }

        if (snapshot?.wanGateways?.isNotEmpty() == true) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (appliance.vendor == FirewallVendor.FORTINET_FORTIGATE) "SD-WAN Health-Check SLAs" else "WAN Gateways & dpinger RTT",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                    snapshot.wanGateways.forEach { gw -> WanGatewayRow(gw) }
                }
            }
        }

        if (snapshot?.carpVips?.isNotEmpty() == true) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("CARP Virtual IPs (HA)", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                    snapshot.carpVips.forEach { vip ->
                        val color = if (vip.status == "MASTER") StatusEmerald else StatusAmber
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("VHID ${vip.vhid} (${vip.interfaceName}) ${vip.vipAddress}", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = TextPrimary)
                            Text(vip.status, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = color)
                        }
                    }
                }
            }
        }

        snapshot?.pfStateSummary?.let { pf ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("pf State Table, Unbound & DHCP", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                    Text(
                        "States: ${pf.totalStates} / ${pf.stateTableLimit} | Unbound DNS: ${pf.unboundStatus} | DHCP Leases: ${pf.dhcpLeaseCount}",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = CyberCyan
                    )
                    pf.recentLeases.take(6).forEach { lease ->
                        Text(lease, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun HealthMetricsBlock(health: FirewallHealthSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Host: ${health.hostname.ifBlank { health.applianceName }}", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
            Text("HA: ${health.haRole} (${health.haState})", style = MaterialTheme.typography.labelSmall, color = StatusEmerald)
        }
        if (health.firmwareVersion.isNotBlank() || health.uptimeText.isNotBlank()) {
            Text(
                "Firmware: ${health.firmwareVersion.ifBlank { "Detected" }}  ${if (health.uptimeText.isNotBlank()) "• Uptime: ${health.uptimeText}" else ""}",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = TextSecondary
            )
        }
        GaugeBar("CPU Load", health.cpuPercent, CyberCyan)
        health.mgmtCpuPercent?.let { GaugeBar("Mgmt Plane CPU", it, StatusAmber) }
        health.dataPlaneCpuPercent?.let { GaugeBar("Data Plane CPU", it, StatusEmerald) }
        GaugeBar("Memory Usage", health.memoryPercent, StatusEmerald)
        Text(
            text = "Active Sessions: ${health.activeSessions}${if (health.maxSessions > 0) " / ${health.maxSessions}" else ""}",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = TextPrimary
        )
    }
}

@Composable
private fun GaugeBar(label: String, percent: Float, color: Color) {
    val safePct = if (percent.isNaN() || percent.isInfinite()) 0f else percent.coerceIn(0f, 100f)
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Text(String.format(Locale.US, "%.1f%%", safePct), style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = TextPrimary)
        }
        Spacer(modifier = Modifier.height(3.dp))
        LinearProgressIndicator(
            progress = { (safePct / 100f).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = if (safePct > 85f) StatusCrimson else color,
            trackColor = CardBackgroundElevated
        )
    }
}

@Composable
private fun WanGatewayRow(gw: WanGatewaySlaEntry) {
    val statusColor = when {
        gw.status.contains("UP", true) || gw.status.contains("ONLINE", true) || gw.status.contains("ALIVE", true) -> StatusEmerald
        gw.status.contains("DOWN", true) || gw.status.contains("DEAD", true) -> StatusCrimson
        else -> StatusAmber
    }
    Surface(color = DarkBackground, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("${gw.name} (${gw.interfaceName})", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                Text(
                    "GW ${gw.gatewayIp.ifBlank { "--" }} • RTT ${String.format(Locale.US, "%.1f", gw.latencyMs)}ms • Jitter ${String.format(Locale.US, "%.1f", gw.jitterMs)}ms • Loss ${String.format(Locale.US, "%.1f", gw.packetLossPercent)}%",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = TextSecondary
                )
            }
            Text(gw.status, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = statusColor)
        }
    }
}

@Composable
fun FirewallIpsecPanel(
    appliance: FirewallApplianceProfile,
    snapshot: FirewallHubSnapshot?,
    isLoading: Boolean,
    onBounceSophosTunnel: (String) -> Unit,
    onControlFortiTunnel: (p1: String, p2: String, bringUp: Boolean) -> Unit,
    aiViewModel: AiAssistantViewModel? = null
) {
    val tunnels = snapshot?.ipsecTunnels.orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (tunnels.isEmpty()) {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = CardBackground), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("IPsec & XFRM Site-to-Site Tunnels", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("No IPsec tunnels loaded yet. Tap Poll Live on the top bar or Overview tab.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
        } else {
            ExplainResultSection(
                diagnosisKey = "fw_ipsec_${appliance.id}",
                contextTitle = "IPsec Tunnels (${appliance.vendor.shortCode})",
                rawResultText = tunnels.joinToString("\n") { "${it.name}: peer=${it.remoteGateway} p1=${it.phase1State} p2=${it.phase2State} selector=${it.spiOrSelector} rx=${it.bytesIn} tx=${it.bytesOut}" },
                aiViewModel = aiViewModel,
                buttonTestTag = "explain_fw_ipsec_btn"
            )
            tunnels.forEach { tunnel ->
                IpsecTunnelCard(
                    vendor = appliance.vendor,
                    tunnel = tunnel,
                    isLoading = isLoading,
                    onBounceSophos = { onBounceSophosTunnel(tunnel.name) },
                    onFortiUpDown = { up ->
                        val parts = tunnel.id.split("::")
                        val p1 = parts.getOrNull(0) ?: tunnel.name
                        val p2 = parts.getOrNull(1) ?: tunnel.name
                        onControlFortiTunnel(p1, p2, up)
                    }
                )
            }
        }
    }
}

@Composable
private fun IpsecTunnelCard(
    vendor: FirewallVendor,
    tunnel: FirewallIpsecTunnel,
    isLoading: Boolean,
    onBounceSophos: () -> Unit,
    onFortiUpDown: (Boolean) -> Unit
) {
    val stateColor = if (tunnel.isUp) StatusEmerald else StatusCrimson
    Card(
        modifier = Modifier.fillMaxWidth().testTag("fw_ipsec_${tunnel.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        border = BorderStroke(1.dp, if (tunnel.isUp) StatusEmerald.copy(alpha = 0.4f) else BorderStrokeColor)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(tunnel.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                Surface(color = stateColor.copy(alpha = 0.16f), shape = RoundedCornerShape(6.dp)) {
                    Text(
                        text = "${tunnel.phase1State} / ${tunnel.phase2State}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = stateColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Text(
                "Peer: ${tunnel.remoteGateway} | Local: ${tunnel.localSubnet.ifBlank { "--" }} <-> Remote: ${tunnel.remoteSubnet.ifBlank { "--" }}",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = TextSecondary
            )
            if (tunnel.spiOrSelector.isNotBlank() || tunnel.bytesIn > 0 || tunnel.bytesOut > 0) {
                Text(
                    "${tunnel.spiOrSelector} • Rx ${tunnel.bytesIn} B / Tx ${tunnel.bytesOut} B",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = TextMuted
                )
            }
            if (vendor == FirewallVendor.SOPHOS_XGS) {
                OutlinedButton(
                    onClick = onBounceSophos,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("sophos_bounce_${tunnel.id}")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Bounce / Re-initiate IPsec Tunnel")
                }
            } else if (vendor == FirewallVendor.FORTINET_FORTIGATE) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onFortiUpDown(true) },
                        enabled = !isLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = StatusEmerald, contentColor = Color(0xFF002114)),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Bring UP", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { onFortiUpDown(false) },
                        enabled = !isLoading,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp), tint = StatusCrimson)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Bring DOWN", fontSize = 12.sp, color = StatusCrimson)
                    }
                }
            }
        }
    }
}
