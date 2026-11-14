// === FILE: app/src/main/java/com/enterwebslinger/network/ui/screens/vpn/VpnEnterpriseToolsTab.kt ===
package com.enterwebslinger.network.ui.screens.vpn

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enterwebslinger.network.domain.model.EnterpriseSslVpnVendor
import com.enterwebslinger.network.domain.model.IkeProbeResult
import com.enterwebslinger.network.domain.model.InstalledAppEntry
import com.enterwebslinger.network.domain.model.SslVpnProbeResult
import com.enterwebslinger.network.domain.model.TunnelEncapsulationType
import com.enterwebslinger.network.domain.model.TunnelMssCalculation
import com.enterwebslinger.network.domain.model.VpnAuditReport
import com.enterwebslinger.network.domain.model.VpnProfile
import com.enterwebslinger.network.ui.components.ExplainResultSection
import com.enterwebslinger.network.ui.screens.ai.AiAssistantViewModel
import com.enterwebslinger.network.ui.theme.CardBackground
import com.enterwebslinger.network.ui.theme.CardBackgroundElevated
import com.enterwebslinger.network.ui.theme.CyberCyan
import com.enterwebslinger.network.ui.theme.DarkBackground
import com.enterwebslinger.network.ui.theme.StatusAmber
import com.enterwebslinger.network.ui.theme.StatusConnected
import com.enterwebslinger.network.ui.theme.StatusError
import com.enterwebslinger.network.ui.theme.TextMuted
import com.enterwebslinger.network.ui.theme.TextPrimary
import com.enterwebslinger.network.ui.theme.TextSecondary

@Composable
fun EnterpriseSslAndIpsecProbesTab(
    isTesting: Boolean,
    sslProbeResult: SslVpnProbeResult?,
    ikeProbeResult: IkeProbeResult?,
    mssCalculation: TunnelMssCalculation?,
    onProbeSslVpn: (EnterpriseSslVpnVendor, String, Int, Boolean) -> Unit,
    onProbeIkev2: (String, Boolean) -> Unit,
    onCalculateMss: (TunnelEncapsulationType, Int, Boolean, Boolean) -> Unit,
    aiViewModel: AiAssistantViewModel? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 1. Enterprise SSL-VPN & L2TP Portal Probes
        var selectedVendor by remember { mutableStateOf(EnterpriseSslVpnVendor.CISCO_ANYCONNECT) }
        var sslHost by remember { mutableStateOf("") }
        var sslPort by remember { mutableStateOf(selectedVendor.defaultPort.toString()) }
        var allowSelfSigned by remember { mutableStateOf(true) }

        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = CardBackground), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enterprise SSL-VPN & L2TP Probes", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    EnterpriseSslVpnVendor.entries.forEach { v ->
                        FilterChip(
                            selected = selectedVendor == v,
                            onClick = {
                                selectedVendor = v
                                sslPort = v.defaultPort.toString()
                            },
                            label = { Text(v.name.replace("_", " "), fontSize = 10.sp) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProbeInput(sslHost, { sslHost = it }, "Portal Host / IP", Modifier.weight(1f).testTag("sslvpn_host_input"))
                    ProbeInput(sslPort, { sslPort = it }, "Port", Modifier.width(86.dp))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Allow Self-Signed Gateway Cert", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Switch(checked = allowSelfSigned, onCheckedChange = { allowSelfSigned = it })
                }
                Button(
                    onClick = { onProbeSslVpn(selectedVendor, sslHost, sslPort.toIntOrNull() ?: selectedVendor.defaultPort, allowSelfSigned) },
                    enabled = !isTesting && sslHost.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("probe_sslvpn_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF00363D))
                ) {
                    Text("Probe ${selectedVendor.name.substringBefore("_")} Handshake", fontWeight = FontWeight.Bold)
                }

                sslProbeResult?.let { res ->
                    val col = if (res.isReachable) StatusConnected else StatusError
                    Surface(color = col.copy(alpha = 0.14f), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("${res.vendor.displayName} • ${if (res.isReachable) "REACHABLE (${res.rttMs}ms)" else "FAILED"}", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = col)
                            if (res.tlsVersion.isNotBlank()) Text("${res.tlsVersion} / ${res.cipherSuite}", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = TextPrimary)
                            Text(res.httpStatusOrCode.ifBlank { res.errorMessage.orEmpty() }, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            if (res.authGroups.isNotEmpty()) Text("Auth Groups: ${res.authGroups.joinToString(", ")}", style = MaterialTheme.typography.labelSmall, color = CyberCyan)
                        }
                    }
                    ExplainResultSection(
                        diagnosisKey = "vpn_ssl_probe",
                        contextTitle = "SSL-VPN Probe (${res.vendor.displayName})",
                        rawResultText = "Host=${res.host}:${res.port} Reachable=${res.isReachable} TLS=${res.tlsVersion}/${res.cipherSuite} Status=${res.httpStatusOrCode} Groups=${res.authGroups} Err=${res.errorMessage}",
                        aiViewModel = aiViewModel,
                        buttonTestTag = "explain_sslvpn_btn"
                    )
                }
            }
        }

        // 2. Site-to-Site IKEv2 SA_INIT (UDP 500 & NAT-T 4500) Probe
        var ikeHost by remember { mutableStateOf("") }
        var useNatT by remember { mutableStateOf(false) }

        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = CardBackground), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Site-to-Site IKEv2 SA_INIT Probe (RFC 7296 / 3948)", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                ProbeInput(ikeHost, { ikeHost = it }, "Remote IPsec Peer IP / FQDN", Modifier.fillMaxWidth().testTag("ike_peer_input"))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Use NAT-T Encapsulation (UDP 4500)", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Switch(checked = useNatT, onCheckedChange = { useNatT = it })
                }
                Button(
                    onClick = { onProbeIkev2(ikeHost, useNatT) },
                    enabled = !isTesting && ikeHost.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("probe_ikev2_sa_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = CardBackgroundElevated, contentColor = CyberCyan)
                ) {
                    Text(if (useNatT) "Send IKE_SA_INIT on NAT-T UDP 4500" else "Send IKE_SA_INIT on UDP 500", fontWeight = FontWeight.Bold)
                }

                ikeProbeResult?.let { ike ->
                    val col = when {
                        !ike.peerResponded -> StatusError
                        ike.noProposalChosenDetected -> StatusAmber
                        else -> StatusConnected
                    }
                    Surface(color = col.copy(alpha = 0.14f), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("${ike.ikeVersionLabel} ${ike.exchangeName} (${ike.rttMs}ms)", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = col)
                            Text("SPIs: I=${ike.initiatorSpiHex} R=${ike.responderSpiHex}", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = TextPrimary)
                            Text(ike.diagnosisSummary, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                    }
                    ExplainResultSection(
                        diagnosisKey = "vpn_ike_probe",
                        contextTitle = "IKEv2 SA_INIT Handshake",
                        rawResultText = "Peer=${ike.host}:${ike.port} Responded=${ike.peerResponded} NoProposalChosen=${ike.noProposalChosenDetected} Exchange=${ike.exchangeName} InitSPI=${ike.initiatorSpiHex} RespSPI=${ike.responderSpiHex} Summary=${ike.diagnosisSummary}",
                        aiViewModel = aiViewModel,
                        buttonTestTag = "explain_ikev2_btn"
                    )
                }
            }
        }

        // 3. Tunnel Encapsulation Overhead & TCP MSS Clamping Calculator
        var selectedEncap by remember { mutableStateOf(TunnelEncapsulationType.IPSEC_ESP_TUNNEL_GCM) }
        var physMtuText by remember { mutableStateOf("1500") }
        var isIpv6Outer by remember { mutableStateOf(false) }
        var natTEnabled by remember { mutableStateOf(true) }

        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = CardBackground), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Calculate, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Tunnel Overhead & TCP MSS Clamping Calculator", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                }
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TunnelEncapsulationType.entries.forEach { enc ->
                        FilterChip(
                            selected = selectedEncap == enc,
                            onClick = {
                                selectedEncap = enc
                                onCalculateMss(enc, physMtuText.toIntOrNull() ?: 1500, isIpv6Outer, natTEnabled)
                            },
                            label = { Text(enc.name.replace("_", " "), fontSize = 10.sp) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProbeInput(physMtuText, { physMtuText = it }, "Physical MTU (1492/1500)", Modifier.weight(1f))
                    Button(
                        onClick = { onCalculateMss(selectedEncap, physMtuText.toIntOrNull() ?: 1500, isIpv6Outer, natTEnabled) },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("calc_mss_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF00363D))
                    ) { Text("Compute", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isIpv6Outer, onCheckedChange = { isIpv6Outer = it })
                        Text("IPv6 Outer", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = natTEnabled, onCheckedChange = { natTEnabled = it })
                        Text("NAT-T (+8B)", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                }
                mssCalculation?.let { calc ->
                    Surface(color = DarkBackground, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "Overhead: ${calc.overheadBytes} B  |  Tunnel MTU: ${calc.effectiveTunnelMtu}  |  TCP MSS: ${calc.recommendedTcpMss}",
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                                color = StatusConnected
                            )
                            Text(calc.routerCliSnippet, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = TextSecondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DeepLeakAndPerAppTab(
    isTesting: Boolean,
    auditReport: VpnAuditReport?,
    selectedProfile: VpnProfile?,
    installedApps: List<InstalledAppEntry>,
    onRunFullAudit: () -> Unit,
    onLoadApps: () -> Unit,
    onToggleExcludedApp: (String) -> Unit,
    aiViewModel: AiAssistantViewModel? = null
) {
    LaunchedEffect(Unit) { onLoadApps() }
    var appFilter by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = CardBackground), modifier = Modifier.fillMaxWidth().testTag("deep_audit_card")) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("DNS, IPv6 & Route Table Auditor", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                    }
                    Button(
                        onClick = onRunFullAudit,
                        enabled = !isTesting,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("run_full_audit_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF00363D))
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color(0xFF00363D))
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Audit All", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                auditReport?.let { rep ->
                    val dnsLeak = rep.dnsLeakResult?.isLeakDetected ?: false
                    val anyRisk = dnsLeak || rep.ipv6LeakRisk || rep.splitTunnelConflicts.isNotEmpty()
                    val bannerColor = if (anyRisk) StatusError else StatusConnected
                    Surface(color = bannerColor.copy(alpha = 0.14f), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (anyRisk) Icons.Default.LockOpen else Icons.Default.Lock, contentDescription = null, tint = bannerColor, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(rep.overallSummary, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                        }
                    }

                    if (rep.splitTunnelConflicts.isNotEmpty()) {
                        rep.splitTunnelConflicts.forEach { conflict ->
                            Text("! $conflict", style = MaterialTheme.typography.labelSmall, color = StatusAmber)
                        }
                    }

                    Text("Kernel & LinkProperties Routes (${rep.activeRoutes.size}):", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = CyberCyan)
                    rep.activeRoutes.take(12).forEach { r ->
                        Text(r, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = TextSecondary)
                    }

                    ExplainResultSection(
                        diagnosisKey = "vpn_leak_audit",
                        contextTitle = "VPN DNS / IPv6 & Route Audit",
                        rawResultText = "Summary=${rep.overallSummary} DnsLeak=$dnsLeak Ipv6Risk=${rep.ipv6LeakRisk} Conflicts=${rep.splitTunnelConflicts} Routes=${rep.activeRoutes.take(8)}",
                        aiViewModel = aiViewModel,
                        buttonTestTag = "explain_vpn_audit_btn"
                    )
                }
            }
        }

        // Per-App Split-Tunnel Exclusion (VpnService.Builder.addDisallowedApplication)
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = CardBackground), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Per-App VPN Exclusion (${selectedProfile?.splitTunnelExcludedApps?.size ?: 0} bypassed)",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
                ProbeInput(appFilter, { appFilter = it }, "Filter installed apps...", Modifier.fillMaxWidth().testTag("per_app_filter_input"))
                val excludedSet = selectedProfile?.splitTunnelExcludedApps?.toSet().orEmpty()
                val shownApps = installedApps.filter {
                    appFilter.isBlank() || it.appName.contains(appFilter, true) || it.packageName.contains(appFilter, true)
                }.take(25)
                shownApps.forEach { app ->
                    val checked = app.packageName in excludedSet
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(app.appName, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                            Text(app.packageName, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = TextMuted)
                        }
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { onToggleExcludedApp(app.packageName) },
                            modifier = Modifier.testTag("exclude_app_${app.packageName}")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProbeInput(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 11.sp) },
        singleLine = true,
        modifier = modifier,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = CyberCyan,
            unfocusedBorderColor = TextMuted,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary
        ),
        shape = RoundedCornerShape(8.dp)
    )
}
