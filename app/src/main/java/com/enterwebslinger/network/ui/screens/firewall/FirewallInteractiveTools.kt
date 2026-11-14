// === FILE: app/src/main/java/com/enterwebslinger/network/ui/screens/firewall/FirewallInteractiveTools.kt ===
package com.enterwebslinger.network.ui.screens.firewall

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enterwebslinger.network.domain.model.CiscoPacketTracerResult
import com.enterwebslinger.network.domain.model.FirewallApplianceProfile
import com.enterwebslinger.network.domain.model.FirewallBackupResult
import com.enterwebslinger.network.domain.model.FirewallDropLogEntry
import com.enterwebslinger.network.domain.model.FirewallHubSnapshot
import com.enterwebslinger.network.domain.model.FirewallVendor
import com.enterwebslinger.network.domain.model.PanOsPolicyMatchResult
import com.enterwebslinger.network.ui.components.ExplainResultSection
import com.enterwebslinger.network.ui.screens.ai.AiAssistantViewModel
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

@Composable
fun FirewallInteractivePolicyAndPacketPanel(
    appliance: FirewallApplianceProfile,
    snapshot: FirewallHubSnapshot?,
    panOsMatchResult: PanOsPolicyMatchResult?,
    packetTracerResult: CiscoPacketTracerResult?,
    isLoading: Boolean,
    onRunPanOsMatch: (fromZone: String, toZone: String, srcIp: String, dstIp: String, dstPort: Int, proto: Int, app: String) -> Unit,
    onRunCiscoPacketTracer: (inIfc: String, proto: String, srcIp: String, srcPort: Int, dstIp: String, dstPort: Int) -> Unit,
    onRestartPfService: (String) -> Unit,
    aiViewModel: AiAssistantViewModel? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (appliance.vendor) {
            FirewallVendor.PALO_ALTO_PANOS -> {
                PanOsPolicyTesterCard(panOsMatchResult, isLoading, onRunPanOsMatch)
                panOsMatchResult?.let { m ->
                    ExplainResultSection(
                        diagnosisKey = "panos_match_${appliance.id}",
                        contextTitle = "PAN-OS Policy Match",
                        rawResultText = "Rule=${m.matchedRuleName} Action=${m.action} NAT=${m.natRuleMatched} Trans=${m.translatedSrc}",
                        aiViewModel = aiViewModel,
                        buttonTestTag = "explain_panos_match_btn"
                    )
                }
                if (snapshot?.vpnUsers?.isNotEmpty() == true) {
                    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = CardBackground), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Active GlobalProtect Users (${snapshot.vpnUsers.size})", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                            snapshot.vpnUsers.forEach { u ->
                                Text(
                                    "${u.username} • VIP ${u.virtualIp} • Public ${u.publicIp} (${u.clientOsOrVersion})",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            FirewallVendor.CISCO_ASA_FTD -> {
                CiscoPacketTracerCard(packetTracerResult, isLoading, onRunCiscoPacketTracer)
                packetTracerResult?.let { pt ->
                    ExplainResultSection(
                        diagnosisKey = "cisco_pt_${appliance.id}",
                        contextTitle = "Cisco ASA Packet-Tracer",
                        rawResultText = "Verdict=${pt.verdict} (${pt.inputInterface}->${pt.outputInterface}) DropReason=${pt.dropReason} Phases=${pt.phases.joinToString { "P${it.phaseNumber}:${it.phaseType}=${it.result}" }}",
                        aiViewModel = aiViewModel,
                        buttonTestTag = "explain_cisco_pt_btn"
                    )
                }
            }

            FirewallVendor.FORTINET_FORTIGATE -> {
                val policies = snapshot?.policyHits.orEmpty()
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = CardBackground), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val zeroHits = policies.count { it.isZeroHit }
                        Text("Policy Hit-Count & Shadow Auditor", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                        Text(
                            "Total Policies: ${policies.size} | Zero-Hit Candidates: $zeroHits",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (zeroHits > 0) StatusAmber else StatusEmerald
                        )
                        policies.take(25).forEach { pol ->
                            Surface(color = DarkBackground, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                                Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("#${pol.policyId} ${pol.name}", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                                        Text("${pol.srcZoneOrIfc} -> ${pol.dstZoneOrIfc} (${pol.action})", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                    }
                                    Text(
                                        "${pol.hitCount} hits",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                                        color = if (pol.isZeroHit) StatusAmber else CyberCyan
                                    )
                                }
                            }
                        }
                    }
                }
            }

            FirewallVendor.PFSENSE_OPNSENSE -> {
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = CardBackground), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("FreeBSD Service Controls", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("unbound" to "Unbound DNS", "dpinger" to "dpinger GW", "ipsec" to "StrongSwan").forEach { (svc, label) ->
                                OutlinedButton(
                                    onClick = { onRestartPfService(svc) },
                                    enabled = !isLoading,
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                ) {
                                    Text("Restart\n$label", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            FirewallVendor.SOPHOS_XGS -> {
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = CardBackground), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Sophos SFOS Live Telemetry", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                        Text(
                            snapshot?.rawDiagnosticOutput?.ifBlank { "Poll Sophos dashboard or switch to Drops & Backup tab to run `drppkt`." }
                                ?: "Poll Sophos dashboard or switch to Drops & Backup tab to run `drppkt`.",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PanOsPolicyTesterCard(
    result: PanOsPolicyMatchResult?,
    isLoading: Boolean,
    onRunTest: (String, String, String, String, Int, Int, String) -> Unit
) {
    var fromZone by remember { mutableStateOf("trust") }
    var toZone by remember { mutableStateOf("untrust") }
    var srcIp by remember { mutableStateOf("10.10.10.50") }
    var dstIp by remember { mutableStateOf("1.1.1.1") }
    var dstPort by remember { mutableStateOf("443") }
    var app by remember { mutableStateOf("ssl") }

    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = CardBackground), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("PAN-OS Security & NAT Policy Match Tester", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniInput(fromZone, { fromZone = it }, "From Zone", Modifier.weight(1f))
                MiniInput(toZone, { toZone = it }, "To Zone", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniInput(srcIp, { srcIp = it }, "Source IP", Modifier.weight(1f))
                MiniInput(dstIp, { dstIp = it }, "Dest IP", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniInput(dstPort, { dstPort = it }, "Dst Port", Modifier.weight(1f))
                MiniInput(app, { app = it }, "App-ID", Modifier.weight(1f))
            }
            Button(
                onClick = { onRunTest(fromZone, toZone, srcIp, dstIp, dstPort.toIntOrNull() ?: 443, 6, app) },
                enabled = !isLoading && srcIp.isNotBlank() && dstIp.isNotBlank(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("panos_test_policy_btn"),
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF00363D))
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Run <test><security-policy-match>", fontWeight = FontWeight.Bold)
            }
            result?.let { m ->
                val verdictColor = if (m.action.contains("ALLOW", true)) StatusEmerald else StatusCrimson
                Surface(color = verdictColor.copy(alpha = 0.14f), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Rule: ${m.matchedRuleName} -> ${m.action}", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = verdictColor)
                        Text("NAT Rule: ${m.natRuleMatched} ${m.translatedSrc}", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = TextPrimary)
                    }
                }
            }
        }
    }
}

@Composable
private fun CiscoPacketTracerCard(
    result: CiscoPacketTracerResult?,
    isLoading: Boolean,
    onRunTrace: (inIfc: String, proto: String, srcIp: String, srcPort: Int, dstIp: String, dstPort: Int) -> Unit
) {
    var inIfc by remember { mutableStateOf("inside") }
    var proto by remember { mutableStateOf("tcp") }
    var srcIp by remember { mutableStateOf("10.1.1.10") }
    var srcPort by remember { mutableStateOf("51234") }
    var dstIp by remember { mutableStateOf("8.8.8.8") }
    var dstPort by remember { mutableStateOf("443") }

    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = CardBackground), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Cisco ASA Packet-Tracer Simulator", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniInput(inIfc, { inIfc = it }, "Input Ifc", Modifier.weight(1f))
                MiniInput(proto, { proto = it }, "Proto (tcp/udp)", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniInput(srcIp, { srcIp = it }, "Source IP", Modifier.weight(1f))
                MiniInput(srcPort, { srcPort = it }, "Src Port", Modifier.weight(0.6f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniInput(dstIp, { dstIp = it }, "Dest IP", Modifier.weight(1f))
                MiniInput(dstPort, { dstPort = it }, "Dst Port", Modifier.weight(0.6f))
            }
            Button(
                onClick = { onRunTrace(inIfc, proto, srcIp, srcPort.toIntOrNull() ?: 51234, dstIp, dstPort.toIntOrNull() ?: 443) },
                enabled = !isLoading && srcIp.isNotBlank() && dstIp.isNotBlank(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("cisco_pt_run_btn"),
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF00363D))
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Execute ASA packet-tracer", fontWeight = FontWeight.Bold)
            }

            result?.let { pt ->
                val isAllow = pt.verdict.contains("ALLOW", true)
                val vColor = if (isAllow) StatusEmerald else StatusCrimson
                Surface(color = vColor.copy(alpha = 0.14f), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Verdict: ${pt.verdict} (${pt.inputInterface} -> ${pt.outputInterface})", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = vColor)
                        if (pt.dropReason.isNotBlank()) {
                            Text("Drop Reason: ${pt.dropReason}", style = MaterialTheme.typography.labelSmall, color = StatusCrimson)
                        }
                    }
                }
                pt.phases.forEach { phase ->
                    val pColor = if (phase.result.contains("DROP", true)) StatusCrimson else StatusEmerald
                    Surface(color = DarkBackground, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, pColor.copy(alpha = 0.4f)), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Phase ${phase.phaseNumber}: ${phase.phaseType} (${phase.subtype})", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                                Text(phase.result, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = pColor)
                            }
                            if (phase.configLine.isNotBlank()) {
                                Text(phase.configLine, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = TextSecondary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FirewallDropLogsAndBackupPanel(
    appliance: FirewallApplianceProfile,
    dropLogs: List<FirewallDropLogEntry>,
    backupResult: FirewallBackupResult?,
    isLoading: Boolean,
    onFetchSophosDrops: (String) -> Unit,
    onTriggerSophosBackup: () -> Unit,
    onDownloadFortiBackup: () -> Unit,
    aiViewModel: AiAssistantViewModel? = null
) {
    val context = LocalContext.current
    var filterIp by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = CardBackground), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Configuration Backup & Vault Export", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                when (appliance.vendor) {
                    FirewallVendor.FORTINET_FORTIGATE -> {
                        Button(
                            onClick = onDownloadFortiBackup,
                            enabled = !isLoading,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("forti_backup_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF00363D))
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Download FortiGate Global .conf", fontWeight = FontWeight.Bold)
                        }
                    }
                    FirewallVendor.SOPHOS_XGS -> {
                        Button(
                            onClick = onTriggerSophosBackup,
                            enabled = !isLoading,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("sophos_backup_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF00363D))
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Trigger SFOS system_backup_now", fontWeight = FontWeight.Bold)
                        }
                    }
                    else -> {
                        Text("Use SSH Console / REST backup endpoints on ${appliance.vendor.shortCode} management host.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }

                backupResult?.let { bkp ->
                    Surface(color = CardBackgroundElevated, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(bkp.fileName, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = StatusEmerald)
                                Text("${bkp.fileSizeBytes} bytes saved", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            }
                            OutlinedButton(
                                onClick = {
                                    val uri = Uri.parse(bkp.shareUriString)
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Firewall Backup"))
                                },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Share", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        if (appliance.vendor == FirewallVendor.SOPHOS_XGS) {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = CardBackground), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Live Sophos drppkt Drop Log Inspector", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        MiniInput(filterIp, { filterIp = it }, "Host Filter IP (Optional)", Modifier.weight(1f))
                        Button(
                            onClick = { onFetchSophosDrops(filterIp) },
                            enabled = !isLoading,
                            modifier = Modifier.heightIn(min = 48.dp).testTag("sophos_drppkt_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF00363D))
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Capture", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                    dropLogs.forEach { entry ->
                        Surface(color = DarkBackground, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("${entry.srcIp} -> ${entry.dstIp} (${entry.protocolPort})", style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold), color = StatusCrimson)
                                Text("${entry.ruleOrReason} • if=${entry.inInterface} (${entry.timestamp})", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            }
                        }
                    }
                    if (dropLogs.isNotEmpty()) {
                        ExplainResultSection(
                            diagnosisKey = "sophos_drops_${appliance.id}",
                            contextTitle = "Sophos drppkt Drops",
                            rawResultText = dropLogs.take(10).joinToString("\n") { "${it.srcIp}->${it.dstIp} (${it.protocolPort}) reason=${it.ruleOrReason} if=${it.inInterface}" },
                            aiViewModel = aiViewModel,
                            buttonTestTag = "explain_sophos_drops_btn"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
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
