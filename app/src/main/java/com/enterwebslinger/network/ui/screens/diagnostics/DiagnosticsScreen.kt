// === FILE: app/src/main/java/com/enterwebslinger/network/ui/screens/diagnostics/DiagnosticsScreen.kt ===
package com.enterwebslinger.network.ui.screens.diagnostics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enterwebslinger.network.domain.model.UiState
import com.enterwebslinger.network.ui.components.ErrorBannerCard
import com.enterwebslinger.network.ui.components.LatencyBarChart
import com.enterwebslinger.network.ui.screens.ai.AiAssistantViewModel
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
fun DiagnosticsScreen(
    viewModel: DiagnosticsViewModel,
    aiViewModel: AiAssistantViewModel,
    modifier: Modifier = Modifier
) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val tabs = listOf("Multi-Ping", "Traceroute", "DNS Lookup", "Whois (TCP 43)")

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("diagnostics_screen")
    ) {
        Text(
            text = "DIAGNOSTICS SUITE",
            style = MaterialTheme.typography.labelMedium.copy(
                color = CyberCyan,
                letterSpacing = 1.5.sp
            )
        )
        Text(
            text = "Network Diagnostics",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
        )

        Spacer(modifier = Modifier.height(12.dp))

        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            edgePadding = 4.dp,
            containerColor = CardBackground,
            contentColor = CyberCyan,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = CyberCyan
                )
            },
            divider = { Spacer(modifier = Modifier.height(1.dp).background(BorderStrokeColor)) },
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderStrokeColor, RoundedCornerShape(12.dp))
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { viewModel.setSelectedTab(index) },
                    text = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (selectedTab == index) CyberCyan else TextSecondary,
                            maxLines = 1
                        )
                    },
                    modifier = Modifier.testTag("diag_tab_$index")
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (selectedTab) {
            0 -> PingTab(viewModel)
            1 -> TracerouteTab(viewModel, aiViewModel)
            2 -> DnsTab(viewModel)
            3 -> WhoisTab(viewModel)
        }
    }
}

@Composable
private fun PingTab(viewModel: DiagnosticsViewModel) {
    val host by viewModel.pingHost.collectAsStateWithLifecycle()
    val protocol by viewModel.pingProtocol.collectAsStateWithLifecycle()
    val summary by viewModel.pingSummary.collectAsStateWithLifecycle()
    val pingUiState by viewModel.pingUiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = host,
                onValueChange = { viewModel.setPingHost(it) },
                label = { Text("Target Host / IP") },
                placeholder = { Text("e.g. 1.1.1.1 or hostname") },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("ping_host_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = CardBackground,
                    unfocusedContainerColor = CardBackground,
                    focusedBorderColor = CyberCyan,
                    unfocusedBorderColor = BorderStrokeColor
                ),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.width(6.dp))

            if (summary.results.isNotEmpty()) {
                IconButton(
                    onClick = { viewModel.exportPingReport(context, formatAsJson = false) },
                    modifier = Modifier.size(48.dp).testTag("share_ping_report_button")
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = "Share Ping Report", tint = CyberCyan)
                }
            }

            Button(
                onClick = {
                    if (summary.isRunning) viewModel.stopPing() else viewModel.startPing()
                },
                enabled = summary.isRunning || host.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (summary.isRunning) StatusCrimson else CyberCyan,
                    contentColor = if (summary.isRunning) Color.White else Color(0xFF00363D)
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .height(56.dp)
                    .testTag("ping_action_button")
            ) {
                Icon(
                    imageVector = if (summary.isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (summary.isRunning) "Stop" else "Ping")
            }
        }

        if (summary.isRunning) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = CyberCyan,
                trackColor = CardBackgroundElevated
            )
        }

        val pingErr = pingUiState as? UiState.Error
        if (pingErr != null) {
            Spacer(modifier = Modifier.height(10.dp))
            ErrorBannerCard(
                message = pingErr.message,
                onDismiss = { viewModel.dismissPingError() },
                onRetry = { viewModel.startPing() },
                testTagPrefix = "ping_error_banner"
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("ICMP", "TCP", "HTTP").forEach { proto ->
                FilterChip(
                    selected = protocol == proto,
                    onClick = { viewModel.setPingProtocol(proto) },
                    label = { Text(proto, style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CyberCyan.copy(alpha = 0.2f),
                        selectedLabelColor = CyberCyan
                    ),
                    modifier = Modifier.testTag("protocol_chip_$proto")
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(12.dp),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BorderStrokeColor))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MiniStat("Packets", "${summary.packetsReceived}/${summary.packetsSent}")
                    MiniStat("Loss", String.format("%.0f%%", summary.packetLossPercent), if (summary.packetLossPercent > 0) StatusCrimson else StatusEmerald)
                    MiniStat("Min", String.format("%.1f ms", summary.minRttMs), CyberCyan)
                    MiniStat("Avg", String.format("%.1f ms", summary.avgRttMs), StatusEmerald)
                    MiniStat("Max", String.format("%.1f ms", summary.maxRttMs), StatusAmber)
                    MiniStat("Jitter", String.format("%.1f ms", summary.jitterMs), TextSecondary)
                }

                Spacer(modifier = Modifier.height(12.dp))

                val rtts = summary.results.map { it.rttMs }
                LatencyBarChart(rtts = rtts)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("ping_log_list"),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(summary.results.reversed()) { res ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CardBackgroundElevated, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(if (res.isSuccess) StatusEmerald else StatusCrimson, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "#${res.seq} to ${res.ip}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = TextPrimary
                        )
                    }

                    if (res.isSuccess) {
                        val fallbackTag = res.errorMessage?.let { " ($it)" } ?: ""
                        Text(
                            text = "${String.format("%.1f", res.rttMs)} ms ttl=${res.ttl}$fallbackTag",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                color = CyberCyan
                            )
                        )
                    } else {
                        Text(
                            text = res.errorMessage ?: "Timeout",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                color = StatusCrimson
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TracerouteTab(
    viewModel: DiagnosticsViewModel,
    aiViewModel: AiAssistantViewModel
) {
    val target by viewModel.tracerouteTarget.collectAsStateWithLifecycle()
    val isTracing by viewModel.isTracing.collectAsStateWithLifecycle()
    val hops by viewModel.tracerouteHops.collectAsStateWithLifecycle()
    val tracerouteUiState by viewModel.tracerouteUiState.collectAsStateWithLifecycle()
    val microDiagnosisMap by aiViewModel.microDiagnosisMap.collectAsStateWithLifecycle()
    val microDiagnosisLoading by aiViewModel.microDiagnosisLoading.collectAsStateWithLifecycle()

    val isAnalyzing = microDiagnosisLoading.contains("traceroute")
    val traceAnalysis = microDiagnosisMap["traceroute"]

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = target,
                onValueChange = { viewModel.setTracerouteTarget(it) },
                label = { Text("Trace Destination") },
                placeholder = { Text("e.g. 8.8.8.8 or hostname") },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("traceroute_target_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = CardBackground,
                    unfocusedContainerColor = CardBackground,
                    focusedBorderColor = CyberCyan,
                    unfocusedBorderColor = BorderStrokeColor
                ),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Button(
                onClick = {
                    if (isTracing) viewModel.stopTraceroute() else viewModel.startTraceroute()
                },
                enabled = isTracing || target.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isTracing) StatusCrimson else CyberCyan,
                    contentColor = if (isTracing) Color.White else Color(0xFF00363D)
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .height(56.dp)
                    .testTag("traceroute_action_button")
            ) {
                Icon(
                    imageVector = if (isTracing) Icons.Default.Stop else Icons.Default.AltRoute,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (isTracing) "Stop" else "Trace")
            }
        }

        if (isTracing) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = CyberCyan,
                trackColor = CardBackgroundElevated
            )
        }

        val traceErr = tracerouteUiState as? UiState.Error
        if (traceErr != null) {
            Spacer(modifier = Modifier.height(10.dp))
            ErrorBannerCard(
                message = traceErr.message,
                onDismiss = { viewModel.dismissTracerouteError() },
                onRetry = { viewModel.startTraceroute() },
                testTagPrefix = "traceroute_error_banner"
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (hops.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Hop Timeline (${hops.size} hops)",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                )

                OutlinedButton(
                    onClick = { aiViewModel.explainTraceroute(hops) },
                    enabled = !isAnalyzing,
                    modifier = Modifier.height(48.dp).testTag("analyze_traceroute_ai_button"),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberCyan),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan)
                ) {
                    if (isAnalyzing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = CyberCyan,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Analyzing...", fontSize = 11.sp)
                    } else {
                        Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp), tint = CyberCyan)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "AI Path Diagnosis", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            if (traceAnalysis != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardBackgroundElevated),
                    shape = RoundedCornerShape(12.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CyberCyan.copy(alpha = 0.4f)))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Path Analysis",
                                style = MaterialTheme.typography.labelLarge.copy(color = CyberCyan, fontWeight = FontWeight.Bold)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = traceAnalysis,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        if (hops.isEmpty() && !isTracing) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Enter a destination IP or hostname to run visual hop-by-hop traceroute.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("traceroute_hops_list"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(hops) { hop ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                        shape = RoundedCornerShape(10.dp),
                        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BorderStrokeColor))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(CyberCyan.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${hop.hopNumber}",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = CyberCyan,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (hop.isTimeout) "* * * Request timed out" else hop.ip,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (hop.isTimeout) StatusAmber else TextPrimary
                                    )
                                )
                                if (!hop.isTimeout && hop.hostname.isNotBlank()) {
                                    Text(
                                        text = hop.hostname,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                            }

                            if (!hop.isTimeout) {
                                Text(
                                    text = String.format("%.1f ms", hop.rttMs),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        color = StatusEmerald
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DnsTab(viewModel: DiagnosticsViewModel) {
    val domain by viewModel.dnsDomain.collectAsStateWithLifecycle()
    val recordType by viewModel.dnsRecordType.collectAsStateWithLifecycle()
    val isResolving by viewModel.isResolvingDns.collectAsStateWithLifecycle()
    val results by viewModel.dnsResults.collectAsStateWithLifecycle()
    val dnsUiState by viewModel.dnsUiState.collectAsStateWithLifecycle()

    val recordTypes = listOf("A", "AAAA", "CNAME", "MX", "TXT", "NS", "SOA", "PTR")

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = domain,
                onValueChange = { viewModel.setDnsDomain(it) },
                label = { Text(if (recordType == "PTR") "IP Address or ARPA" else "Domain Name") },
                placeholder = { Text(if (recordType == "PTR") "e.g. 8.8.8.8" else "e.g. example.com") },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("dns_domain_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = CardBackground,
                    unfocusedContainerColor = CardBackground,
                    focusedBorderColor = CyberCyan,
                    unfocusedBorderColor = BorderStrokeColor
                ),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Button(
                onClick = {
                    if (isResolving) viewModel.cancelDnsLookup() else viewModel.resolveDns()
                },
                enabled = isResolving || domain.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isResolving) StatusCrimson else CyberCyan,
                    contentColor = if (isResolving) Color.White else Color(0xFF00363D)
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .height(56.dp)
                    .testTag("dns_resolve_button")
            ) {
                Icon(
                    imageVector = if (isResolving) Icons.Default.Stop else Icons.Default.Search,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (isResolving) "Cancel" else "Resolve")
            }
        }

        if (isResolving) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = CyberCyan,
                trackColor = CardBackgroundElevated
            )
        }

        val dnsErr = dnsUiState as? UiState.Error
        if (dnsErr != null) {
            Spacer(modifier = Modifier.height(10.dp))
            ErrorBannerCard(
                message = dnsErr.message,
                onDismiss = { viewModel.dismissDnsError() },
                onRetry = { viewModel.resolveDns() },
                testTagPrefix = "dns_error_banner"
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            recordTypes.forEach { type ->
                FilterChip(
                    selected = recordType == type,
                    onClick = { viewModel.setDnsRecordType(type) },
                    label = { Text(type, style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CyberCyan.copy(alpha = 0.2f),
                        selectedLabelColor = CyberCyan
                    ),
                    modifier = Modifier.testTag("dns_type_chip_$type")
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (results.isEmpty() && !isResolving) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Query DNS over HTTPS (DoH) for A, AAAA, MX, NS, CNAME, TXT, SOA, and reverse PTR records.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("dns_results_list"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(results) { rec ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                        shape = RoundedCornerShape(10.dp),
                        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BorderStrokeColor))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = rec.type,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = CyberCyan
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        color = if (rec.rcode.startsWith("NOERROR")) StatusEmerald.copy(alpha = 0.15f) else StatusAmber.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = rec.rcode,
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = if (rec.rcode.startsWith("NOERROR")) StatusEmerald else StatusAmber,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "TTL: ${rec.ttl}s",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = rec.data,
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                                color = TextPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WhoisTab(viewModel: DiagnosticsViewModel) {
    val query by viewModel.whoisQuery.collectAsStateWithLifecycle()
    val isQuerying by viewModel.isQueryingWhois.collectAsStateWithLifecycle()
    val result by viewModel.whoisResult.collectAsStateWithLifecycle()
    val whoisUiState by viewModel.whoisUiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { viewModel.setWhoisQuery(it) },
                label = { Text("Domain, IP, or ASN") },
                placeholder = { Text("e.g. cloudflare.com or 8.8.8.8") },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("whois_query_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = CardBackground,
                    unfocusedContainerColor = CardBackground,
                    focusedBorderColor = CyberCyan,
                    unfocusedBorderColor = BorderStrokeColor
                ),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Button(
                onClick = {
                    if (isQuerying) viewModel.cancelWhoisLookup() else viewModel.runWhoisLookup()
                },
                enabled = isQuerying || query.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isQuerying) StatusCrimson else CyberCyan,
                    contentColor = if (isQuerying) Color.White else Color(0xFF00363D)
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .height(56.dp)
                    .testTag("whois_action_button")
            ) {
                Icon(
                    imageVector = if (isQuerying) Icons.Default.Stop else Icons.Default.Public,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (isQuerying) "Cancel" else "Query")
            }
        }

        if (isQuerying) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = CyberCyan,
                trackColor = CardBackgroundElevated
            )
        }

        val whoisErr = whoisUiState as? UiState.Error
        if (whoisErr != null) {
            Spacer(modifier = Modifier.height(10.dp))
            ErrorBannerCard(
                message = whoisErr.message,
                onDismiss = { viewModel.dismissWhoisError() },
                onRetry = { viewModel.runWhoisLookup() },
                testTagPrefix = "whois_error_banner"
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        val current = result
        if (current == null && !isQuerying) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Runs raw TCP port 43 WHOIS queries starting at whois.iana.org and chasing authoritative referrals.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            }
        } else if (current != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .testTag("whois_result_panel"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    shape = RoundedCornerShape(12.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BorderStrokeColor))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Registry Summary",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = CyberCyan)
                        )
                        if (current.referralChain.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                current.referralChain.forEach { hopServer ->
                                    Surface(
                                        color = CardBackgroundElevated,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = hopServer,
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                            color = if (hopServer == current.serverUsed) StatusEmerald else TextSecondary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                        if (current.registrar.isNotBlank()) WhoisField("Registrar", current.registrar)
                        if (current.asnInfo.isNotBlank()) WhoisField("ASN / NetName", current.asnInfo)
                        if (current.creationDate.isNotBlank()) WhoisField("Created", current.creationDate)
                        if (current.expiryDate.isNotBlank()) WhoisField("Expires", current.expiryDate)
                        if (current.nameServers.isNotEmpty()) {
                            WhoisField("Name Servers", current.nameServers.joinToString(", "))
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardBackgroundElevated),
                    shape = RoundedCornerShape(12.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BorderStrokeColor))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Raw WHOIS Response (${current.serverUsed}:43)",
                            style = MaterialTheme.typography.labelMedium.copy(color = CyberCyan, fontWeight = FontWeight.SemiBold)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = current.rawOutput.ifBlank { "Empty response" },
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            ),
                            color = TextPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WhoisField(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = TextPrimary
        )
    }
}

@Composable
private fun MiniStat(label: String, value: String, valueColor: Color = TextPrimary) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            ),
            color = valueColor
        )
    }
}
