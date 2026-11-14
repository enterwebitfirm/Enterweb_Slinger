// === FILE: app/src/main/java/com/enterwebslinger/network/ui/screens/protoolkit/PmtudDnsAlpnTabs.kt ===
package com.enterwebslinger.network.ui.screens.protoolkit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enterwebslinger.network.ui.theme.BorderStrokeColor
import com.enterwebslinger.network.ui.theme.CardBackground
import com.enterwebslinger.network.ui.theme.CardBackgroundElevated
import com.enterwebslinger.network.ui.theme.CyberCyan
import com.enterwebslinger.network.ui.theme.StatusAmber
import com.enterwebslinger.network.ui.theme.StatusCrimson
import com.enterwebslinger.network.ui.theme.StatusEmerald
import com.enterwebslinger.network.ui.theme.TextMuted
import com.enterwebslinger.network.ui.theme.TextPrimary
import com.enterwebslinger.network.ui.theme.TextSecondary

@Composable
fun PmtudAuditorTab(viewModel: ProToolkitViewModel) {
    val result by viewModel.mtuResult.collectAsStateWithLifecycle()
    val liveSteps by viewModel.mtuLiveSteps.collectAsStateWithLifecycle()
    val isRunning by viewModel.isRunningMtu.collectAsStateWithLifecycle()
    var targetHost by remember { mutableStateOf("1.1.1.1") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, BorderStrokeColor)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Path MTU Discovery (PMTUD) & MSS Auditor",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                    Text(
                        text = "Binary search 576–1500 B via Don't-Fragment ICMP (-M do -s) with exact +28 IPv4 / +48 IPv6 header overhead.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    OutlinedTextField(
                        value = targetHost,
                        onValueChange = { targetHost = it },
                        label = { Text("Target Host / IP") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_pmtud_host"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = BorderStrokeColor,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { viewModel.runPathMtuDiscovery(targetHost) },
                            enabled = !isRunning && targetHost.isNotBlank(),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("btn_run_pmtud"),
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF00363D))
                        ) {
                            if (isRunning) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFF00363D))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Probing...", fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Discover Path MTU", fontWeight = FontWeight.Bold)
                            }
                        }

                        if (isRunning) {
                            OutlinedButton(
                                onClick = { viewModel.stopPathMtuDiscovery() },
                                modifier = Modifier.heightIn(min = 48.dp).testTag("btn_stop_pmtud")
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = "Stop PMTUD", tint = StatusCrimson)
                            }
                        }
                    }
                }
            }
        }

        result?.let { res ->
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    border = BorderStroke(1.dp, if (res.errorMessage == null) StatusEmerald else StatusCrimson),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (res.errorMessage != null) {
                            Text(res.errorMessage, style = MaterialTheme.typography.bodySmall, color = StatusCrimson)
                        } else {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Discovered Path MTU", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                                Text(
                                    text = "${res.pathMtuBytes} bytes",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                                    color = StatusEmerald
                                )
                            }
                            Text(
                                text = "IP: ${res.resolvedIp} (${if (res.isIpv6) "IPv6 +48B" else "IPv4 +28B"}) • Max ICMP Payload: ${res.maxPayloadBytes} B",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = CyberCyan
                            )
                        }

                        if (res.recommendations.isNotEmpty()) {
                            Spacer(modifier = Modifier.padding(top = 4.dp))
                            Text("Recommended Tunnel MTU & TCP MSS Clamping", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                            res.recommendations.forEach { rec ->
                                Surface(
                                    color = CardBackgroundElevated,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(rec.tunnelName, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                                            Text(
                                                "MTU ${rec.recommendedMtu} / MSS ${rec.recommendedTcpMss}",
                                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                                                color = CyberCyan
                                            )
                                        }
                                        Text(rec.notes, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        val stepsToShow = result?.steps ?: liveSteps
        if (stepsToShow.isNotEmpty()) {
            item {
                Text(
                    text = "Binary Search Trace (${stepsToShow.size} probes)",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
            }
            items(stepsToShow) { step ->
                Card(colors = CardDefaults.cardColors(containerColor = CardBackground), shape = RoundedCornerShape(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = if (step.succeeded) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = if (step.succeeded) StatusEmerald else StatusCrimson,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Wire ${step.totalWireSizeBytes} B (Payload ${step.payloadSizeBytes} B)",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                                    color = TextPrimary
                                )
                                Text(step.statusMessage, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            }
                        }
                        Text("${step.rttMs} ms", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = TextMuted)
                    }
                }
            }
        }
    }
}

@Composable
fun DnsAndHappyEyeballsTab(viewModel: ProToolkitViewModel) {
    val dnsResults by viewModel.dnsBenchmarks.collectAsStateWithLifecycle()
    val isRunningDns by viewModel.isRunningDns.collectAsStateWithLifecycle()
    val happyResult by viewModel.happyEyeballsResult.collectAsStateWithLifecycle()
    val isRunningAlpn by viewModel.isRunningAlpn.collectAsStateWithLifecycle()

    var dnsDomain by remember { mutableStateOf("cloudflare.com") }
    var tlsHost by remember { mutableStateOf("google.com") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // DNS & DNSSEC Benchmark
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, BorderStrokeColor)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Concurrent DNS & DNSSEC Validator Benchmark",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                    Text(
                        text = "Compares Active DHCP DNS vs Cloudflare (1.1.1.1), Google (8.8.8.8), Quad9 (9.9.9.9) & OpenDNS with EDNS0 DO/AD bit verification.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = dnsDomain,
                        onValueChange = { dnsDomain = it },
                        label = { Text("Domain to Resolve (DNSSEC Signed Recommended)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_dns_bench_domain"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = BorderStrokeColor,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    Button(
                        onClick = { viewModel.runDnsBenchmark(dnsDomain) },
                        enabled = !isRunningDns && dnsDomain.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("btn_run_dns_bench"),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF00363D))
                    ) {
                        if (isRunningDns) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFF00363D))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Benchmarking Resolvers...", fontWeight = FontWeight.Bold)
                        } else {
                            Text("Run DNS & DNSSEC Benchmark", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        items(dnsResults, key = { it.resolverIp }) { entry ->
            Card(colors = CardDefaults.cardColors(containerColor = CardBackground), shape = RoundedCornerShape(10.dp)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("${entry.resolverName} (${entry.resolverIp})", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                        if (entry.isSuccess) {
                            Text("avg ${entry.avgRttMs} ms (min ${entry.minRttMs} ms)", style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold), color = StatusEmerald)
                        } else {
                            Text("TIMEOUT", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = StatusCrimson)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = if (entry.dnssecAuthenticatedData) StatusEmerald else TextMuted,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (entry.dnssecAuthenticatedData) "DNSSEC AD=1 Verified" else "DNSSEC AD=0 (Unsigned or Non-Validating)",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (entry.dnssecAuthenticatedData) StatusEmerald else TextSecondary
                        )
                    }
                    if (entry.resolvedIps.isNotEmpty()) {
                        Text("A Records: ${entry.resolvedIps.joinToString(", ")}", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = CyberCyan)
                    }
                    entry.errorMessage?.let { err ->
                        Text(err, style = MaterialTheme.typography.labelSmall, color = StatusCrimson)
                    }
                }
            }
        }

        // Happy Eyeballs & ALPN h2/h3 Tester
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, BorderStrokeColor)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Happy Eyeballs (IPv6 vs IPv4) & ALPN h2/h3 Tester",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                    Text(
                        text = "Races AAAA (IPv6) vs A (IPv4) TCP handshake, negotiates TLS ALPN (h2 / http/1.1), and inspects Alt-Svc for HTTP/3 QUIC (h3).",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = tlsHost,
                        onValueChange = { tlsHost = it },
                        label = { Text("HTTPS Hostname (Port 443)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_alpn_host"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = BorderStrokeColor,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    Button(
                        onClick = { viewModel.runHappyEyeballsTest(tlsHost, 443) },
                        enabled = !isRunningAlpn && tlsHost.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("btn_run_alpn_test"),
                        colors = ButtonDefaults.buttonColors(containerColor = CardBackgroundElevated, contentColor = CyberCyan)
                    ) {
                        if (isRunningAlpn) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = CyberCyan)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Racing Dual-Stack + ALPN...", fontWeight = FontWeight.Bold)
                        } else {
                            Text("Race Happy Eyeballs & Inspect ALPN", fontWeight = FontWeight.Bold)
                        }
                    }

                    happyResult?.let { r ->
                        Surface(color = CardBackgroundElevated, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Winner: ${r.winnerFamily}", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = CyberCyan)
                                Text("IPv6 (${r.ipv6Address ?: "None"}): ${r.ipv6ConnectMs?.let { "$it ms" } ?: "N/A"}", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = TextPrimary)
                                Text("IPv4 (${r.ipv4Address ?: "None"}): ${r.ipv4ConnectMs?.let { "$it ms" } ?: "N/A"}", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = TextPrimary)
                                Text("TLS: ${r.tlsProtocol} • ALPN: ${r.alpnProtocol} • HTTP/3 Alt-Svc: ${if (r.supportsHttp3Quic) "Supported" else "Not advertised"}", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = if (r.supportsHttp2 || r.supportsHttp3Quic) StatusEmerald else StatusAmber)
                                if (r.altSvcHeader.isNotBlank()) {
                                    Text("Alt-Svc: ${r.altSvcHeader}", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = TextSecondary)
                                }
                                r.errorMessage?.let { err ->
                                    Text(err, style = MaterialTheme.typography.labelSmall, color = StatusCrimson)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
