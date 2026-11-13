// === FILE: app/src/main/java/com/enterwebslinger/network/ui/screens/enterprise/DeepFingerprintTab.kt ===
package com.enterwebslinger.network.ui.screens.enterprise

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.enterwebslinger.network.domain.model.HostFingerprintReport
import com.enterwebslinger.network.domain.model.ScanProfile
import com.enterwebslinger.network.domain.model.ServiceBanner
import com.enterwebslinger.network.domain.model.TlsCertificateInfo
import com.enterwebslinger.network.ui.theme.CardBackground
import com.enterwebslinger.network.ui.theme.CardBackgroundElevated
import com.enterwebslinger.network.ui.theme.CyberCyan
import com.enterwebslinger.network.ui.theme.StatusConnected
import com.enterwebslinger.network.ui.theme.StatusWarning
import com.enterwebslinger.network.ui.theme.TextMuted
import com.enterwebslinger.network.ui.theme.TextPrimary
import com.enterwebslinger.network.ui.theme.TextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeepFingerprintTab(viewModel: EnterpriseViewModel) {
    val targetHost by viewModel.fingerprintTarget.collectAsState()
    val scanProfile by viewModel.scanProfile.collectAsState()
    val customPortsSpec by viewModel.customPortsSpec.collectAsState()
    val isScanning by viewModel.isAuditScanning.collectAsState()
    val progress by viewModel.auditProgress.collectAsState()
    val reports by viewModel.fingerprintReports.collectAsState()
    val topologyData by viewModel.topologyData.collectAsState()

    var selectedHostReport by remember { mutableStateOf<HostFingerprintReport?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Audit Target & Scan Profile Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("deep_audit_config_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Radar, contentDescription = null, tint = CyberCyan)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Deep Audit & OS Fingerprint",
                            style = MaterialTheme.typography.titleMedium.copy(color = TextPrimary, fontWeight = FontWeight.Bold)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = targetHost,
                        onValueChange = { viewModel.setFingerprintTarget(it) },
                        label = { Text("Target IP / Hostname") },
                        placeholder = { Text("e.g. 192.0.2.1") },
                        modifier = Modifier.fillMaxWidth().testTag("audit_target_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = CardBackgroundElevated
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text("Scan Profile:", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
                    Spacer(modifier = Modifier.height(4.dp))

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ScanProfile.entries.forEach { profile ->
                            FilterChip(
                                selected = scanProfile == profile,
                                onClick = { viewModel.setScanProfile(profile) },
                                label = { Text(profile.label, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CyberCyan,
                                    selectedLabelColor = Color(0xFF00363D)
                                )
                            )
                        }
                    }

                    if (scanProfile == ScanProfile.CUSTOM_RANGE) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = customPortsSpec,
                            onValueChange = { viewModel.setCustomPortsSpec(it) },
                            label = { Text("Custom Ports / Ranges") },
                            placeholder = { Text("e.g. 22,80,443,8000-8010") },
                            modifier = Modifier.fillMaxWidth().testTag("audit_custom_ports_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberCyan,
                                unfocusedBorderColor = CardBackgroundElevated
                            ),
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.startDeepAudit() },
                            modifier = Modifier.weight(1f).height(48.dp).testTag("start_deep_audit_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF00363D)),
                            enabled = !isScanning && targetHost.isNotBlank()
                        ) {
                            if (isScanning) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFF00363D), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Auditing (${progress.first}/${progress.second})...", fontWeight = FontWeight.Bold)
                            } else {
                                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Execute Deep Audit", fontWeight = FontWeight.Bold)
                            }
                        }
                        if (isScanning) {
                            OutlinedButton(
                                onClick = { viewModel.stopDeepAudit() },
                                modifier = Modifier.height(48.dp).testTag("stop_deep_audit_button")
                            ) {
                                Icon(imageVector = Icons.Default.Stop, contentDescription = "Stop Audit")
                            }
                        }
                    }

                    if (isScanning) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val pct = if (progress.second > 0) progress.first.toFloat() / progress.second.toFloat() else 0f
                        LinearProgressIndicator(
                            progress = { pct },
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            color = CyberCyan,
                            trackColor = CardBackgroundElevated
                        )
                    }
                }
            }
        }

        // 2. Interactive Network Radial Topology Map
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("topology_card")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Hub, contentDescription = null, tint = StatusConnected)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Network Topology Radial Map", style = MaterialTheme.typography.titleSmall.copy(color = TextPrimary, fontWeight = FontWeight.Bold))
                        }
                        Text("Tap node to inspect", color = TextMuted, fontSize = 10.sp)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val activeGraph = topologyData ?: if (reports.isNotEmpty() && targetHost.isNotBlank()) {
                        viewModel.nmapFingerprintEngine.buildTopologyGraph(targetHost.trim(), reports)
                    } else null

                    if (activeGraph == null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .background(CardBackgroundElevated, RoundedCornerShape(8.dp))
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Enter a target IP/hostname and execute Deep Audit to map live hosts and open services.",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        }
                    } else {
                        NetworkTopologyGraph(
                            graphData = activeGraph,
                            onNodeSelected = { node ->
                                selectedHostReport = reports.firstOrNull { it.host == node.ip }
                            }
                        )
                    }
                }
            }
        }

        // 3. Detailed Host Fingerprint Inspection
        val currentReport = selectedHostReport ?: reports.firstOrNull()
        if (currentReport != null) {
            item {
                Text(
                    text = "Audit Report: ${currentReport.host} ${if (currentReport.hostname.isNotBlank()) "(${currentReport.hostname})" else ""}",
                    style = MaterialTheme.typography.titleMedium.copy(color = TextPrimary, fontWeight = FontWeight.Bold)
                )
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("os_heuristic_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Fingerprint, contentDescription = null, tint = CyberCyan)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("OS Heuristic Fingerprint", style = MaterialTheme.typography.titleSmall.copy(color = TextPrimary, fontWeight = FontWeight.Bold))
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        val os = currentReport.osEstimate
                        if (os != null) {
                            Text(os.predictedOs, color = StatusConnected, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Confidence: ${os.confidence}% | TTL Estimate: ${os.initialTtlEstimate} | TCP Window: ${os.tcpWindowSize}", color = TextSecondary, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(os.rationale, color = TextMuted, fontSize = 11.sp)
                        } else {
                            Text("No OS heuristics available.", color = TextMuted, fontSize = 11.sp)
                        }
                    }
                }
            }

            if (currentReport.banners.isNotEmpty()) {
                item {
                    Text("Discovered Services & Banners (-sV)", style = MaterialTheme.typography.titleSmall.copy(color = TextPrimary, fontWeight = FontWeight.Bold))
                }
                items(currentReport.banners) { banner ->
                    ServiceBannerCard(banner)
                }
            }

            if (currentReport.tlsCertificates.isNotEmpty()) {
                item {
                    Text("TLS / SSL Handshake Certificates", style = MaterialTheme.typography.titleSmall.copy(color = TextPrimary, fontWeight = FontWeight.Bold))
                }
                items(currentReport.tlsCertificates) { cert ->
                    TlsCertCard(cert)
                }
            }
        }
    }
}

@Composable
fun ServiceBannerCard(banner: ServiceBanner) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBackgroundElevated, RoundedCornerShape(8.dp))
            .border(1.dp, CardBackground, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Port ${banner.port}", color = StatusConnected, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(banner.serviceName, color = CyberCyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                if (banner.product.isNotBlank()) {
                    Text(banner.product.take(24), color = TextMuted, fontSize = 10.sp)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = banner.banner,
                color = TextPrimary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun TlsCertCard(cert: TlsCertificateInfo) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBackgroundElevated, RoundedCornerShape(8.dp))
            .border(1.dp, StatusConnected.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = StatusConnected, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("TLS Certificate (Port ${cert.port})", color = StatusConnected, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text("Subject: ${cert.subjectDn}", color = TextPrimary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            Text("Issuer: ${cert.issuerDn}", color = TextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            Text("Valid: ${cert.validFrom} to ${cert.validTo}", color = StatusWarning, fontSize = 10.sp)
            Text("Cipher: ${cert.tlsVersion} / ${cert.cipherSuite}", color = CyberCyan, fontSize = 10.sp)
            if (cert.subjectAltNames.isNotEmpty()) {
                Text("SANs: ${cert.subjectAltNames.take(4).joinToString(", ")}", color = TextMuted, fontSize = 9.sp)
            }
        }
    }
}
