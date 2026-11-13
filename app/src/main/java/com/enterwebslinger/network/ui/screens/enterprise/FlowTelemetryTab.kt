// === FILE: app/src/main/java/com/enterwebslinger/network/ui/screens/enterprise/FlowTelemetryTab.kt ===
package com.enterwebslinger.network.ui.screens.enterprise

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.enterwebslinger.network.domain.model.FlowRecord
import com.enterwebslinger.network.domain.model.TopTalkerEntry
import com.enterwebslinger.network.ui.theme.CardBackground
import com.enterwebslinger.network.ui.theme.CardBackgroundElevated
import com.enterwebslinger.network.ui.theme.CyberCyan
import com.enterwebslinger.network.ui.theme.StatusConnected
import com.enterwebslinger.network.ui.theme.StatusError
import com.enterwebslinger.network.ui.theme.StatusWarning
import com.enterwebslinger.network.ui.theme.TextMuted
import com.enterwebslinger.network.ui.theme.TextPrimary
import com.enterwebslinger.network.ui.theme.TextSecondary

@Composable
fun FlowTelemetryTab(viewModel: EnterpriseViewModel) {
    val stats by viewModel.flowStats.collectAsState()
    val topTalkers by viewModel.topTalkers.collectAsState()
    val protocolDist by viewModel.protocolDistribution.collectAsState()
    val dscpDist by viewModel.dscpDistribution.collectAsState()
    val recentFlows by viewModel.recentFlows.collectAsState()

    var netflowPortText by remember { mutableStateOf("2055") }
    var ipfixPortText by remember { mutableStateOf("4739") }
    var sflowPortText by remember { mutableStateOf("6343") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Collector Master Control Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("flow_control_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null, tint = if (stats.isRunning) StatusConnected else CyberCyan)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Flow Collector Engine", style = MaterialTheme.typography.titleMedium.copy(color = TextPrimary, fontWeight = FontWeight.Bold))
                                Text("UDP $netflowPortText (NetFlow), $ipfixPortText (IPFIX), $sflowPortText (sFlow)", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 10.sp))
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { viewModel.clearFlowStats() }, modifier = Modifier.size(48.dp)) {
                                Icon(imageVector = Icons.Default.Clear, contentDescription = "Reset Stats", tint = TextMuted)
                            }
                            Switch(
                                checked = stats.isRunning,
                                onCheckedChange = {
                                    val ports = listOf(
                                        (netflowPortText.toIntOrNull() ?: 2055).coerceAtLeast(1024),
                                        (ipfixPortText.toIntOrNull() ?: 4739).coerceAtLeast(1024),
                                        (sflowPortText.toIntOrNull() ?: 6343).coerceAtLeast(1024)
                                    ).distinct()
                                    viewModel.toggleFlowCollectors(ports)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF00363D),
                                    checkedTrackColor = CyberCyan
                                ),
                                modifier = Modifier.testTag("flow_collector_switch")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = netflowPortText,
                            onValueChange = { if (!stats.isRunning) netflowPortText = it },
                            enabled = !stats.isRunning,
                            label = { Text("NetFlow UDP") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyberCyan, unfocusedBorderColor = CardBackgroundElevated)
                        )
                        OutlinedTextField(
                            value = ipfixPortText,
                            onValueChange = { if (!stats.isRunning) ipfixPortText = it },
                            enabled = !stats.isRunning,
                            label = { Text("IPFIX UDP") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyberCyan, unfocusedBorderColor = CardBackgroundElevated)
                        )
                        OutlinedTextField(
                            value = sflowPortText,
                            onValueChange = { if (!stats.isRunning) sflowPortText = it },
                            enabled = !stats.isRunning,
                            label = { Text("sFlow UDP") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyberCyan, unfocusedBorderColor = CardBackgroundElevated)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 4-box Telemetry Matrix
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricBox(
                            title = "Throughput",
                            value = formatRate(stats.currentBytesPerSec),
                            subtitle = "${stats.currentPacketsPerSec} pkts/s",
                            tint = CyberCyan,
                            modifier = Modifier.weight(1f)
                        )
                        MetricBox(
                            title = "Flows Decoded",
                            value = stats.totalFlowsDecoded.toString(),
                            subtitle = "${stats.totalPacketsReceived} packets",
                            tint = StatusConnected,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricBox(
                            title = "Total Volume",
                            value = formatBytes(stats.totalBytesProcessed),
                            subtitle = "Cumulative",
                            tint = StatusWarning,
                            modifier = Modifier.weight(1f)
                        )
                        MetricBox(
                            title = "Active Templates",
                            value = stats.activeTemplatesCount.toString(),
                            subtitle = "v9 / IPFIX cache",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (!stats.errorMessage.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(StatusError.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                                .testTag("flow_error_banner")
                        ) {
                            Text(
                                text = stats.errorMessage ?: "",
                                color = StatusError,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        // 2. Top Talkers Analytics
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("top_talkers_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Top Talkers (Conversations)", style = MaterialTheme.typography.titleSmall.copy(color = TextPrimary, fontWeight = FontWeight.Bold))
                    Spacer(modifier = Modifier.height(10.dp))

                    if (topTalkers.isEmpty()) {
                        Text(
                            text = if (stats.isRunning) "Listening for flow packets from network routers..." else "Start collectors to aggregate conversation flows.",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, fontSize = 11.sp)
                        )
                    } else {
                        topTalkers.forEach { talker ->
                            TopTalkerRow(talker)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }

        // 3. Protocol & QoS DSCP Distribution
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).testTag("proto_dist_card")
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Protocols", style = MaterialTheme.typography.titleSmall.copy(color = TextPrimary, fontWeight = FontWeight.Bold))
                        Spacer(modifier = Modifier.height(8.dp))
                        if (protocolDist.isEmpty()) {
                            Text("No data yet", color = TextMuted, fontSize = 11.sp)
                        } else {
                            protocolDist.forEach { proto ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(proto.protocol, color = TextPrimary, fontSize = 11.sp)
                                    Text("%.1f%%".format(proto.percentage), color = CyberCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                LinearProgressIndicator(
                                    progress = { proto.percentage / 100f },
                                    modifier = Modifier.fillMaxWidth().height(4.dp).padding(vertical = 2.dp),
                                    color = CyberCyan,
                                    trackColor = CardBackgroundElevated
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).testTag("dscp_dist_card")
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("DSCP QoS", style = MaterialTheme.typography.titleSmall.copy(color = TextPrimary, fontWeight = FontWeight.Bold))
                        Spacer(modifier = Modifier.height(8.dp))
                        if (dscpDist.isEmpty()) {
                            Text("No data yet", color = TextMuted, fontSize = 11.sp)
                        } else {
                            dscpDist.forEach { dscp ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(dscp.dscpClass.take(12), color = TextPrimary, fontSize = 11.sp)
                                    Text("%.1f%%".format(dscp.percentage), color = StatusWarning, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                LinearProgressIndicator(
                                    progress = { dscp.percentage / 100f },
                                    modifier = Modifier.fillMaxWidth().height(4.dp).padding(vertical = 2.dp),
                                    color = StatusWarning,
                                    trackColor = CardBackgroundElevated
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                    }
                }
            }
        }

        // 4. Live 5-Tuple Flow Stream Log
        item {
            Text("Live Flow Records (Recent)", style = MaterialTheme.typography.titleSmall.copy(color = TextPrimary, fontWeight = FontWeight.Bold))
        }

        if (recentFlows.isEmpty()) {
            item {
                Text("No flow records decoded yet.", color = TextMuted, fontSize = 11.sp)
            }
        } else {
            items(recentFlows.take(25)) { flow ->
                FlowRecordItem(flow)
            }
        }
    }
}

@Composable
fun MetricBox(
    title: String,
    value: String,
    subtitle: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(CardBackgroundElevated, RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Column {
            Text(title, style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 10.sp))
            Text(value, style = MaterialTheme.typography.titleMedium.copy(color = tint, fontWeight = FontWeight.Bold))
            Text(subtitle, style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 9.sp))
        }
    }
}

@Composable
fun TopTalkerRow(talker: TopTalkerEntry) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(talker.conversation, color = TextPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
            Text(formatBytes(talker.totalBytes), color = CyberCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(2.dp))
        LinearProgressIndicator(
            progress = { talker.percentageOfTotal / 100f },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = CyberCyan,
            trackColor = CardBackgroundElevated
        )
    }
}

@Composable
fun FlowRecordItem(flow: FlowRecord) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBackgroundElevated, RoundedCornerShape(6.dp))
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(flow.protocolType.displayName, color = CyberCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(flow.protocolName, color = StatusConnected, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    if (flow.tcpFlags.isNotBlank()) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("[${flow.tcpFlags}]", color = TextMuted, fontSize = 9.sp)
                    }
                }
                Text("${flow.srcIp}:${flow.srcPort} -> ${flow.dstIp}:${flow.dstPort}", color = TextPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(formatBytes(flow.bytes), color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("${flow.packets} pkts | ${flow.dscpClass.take(8)}", color = TextSecondary, fontSize = 9.sp)
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1_000_000_000 -> "%.2f GB".format(bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> "%.2f MB".format(bytes / 1_000_000.0)
        bytes >= 1_000 -> "%.1f KB".format(bytes / 1_000.0)
        else -> "$bytes B"
    }
}

private fun formatRate(bps: Long): String {
    val bits = bps * 8
    return when {
        bits >= 1_000_000_000 -> "%.2f Gbps".format(bits / 1_000_000_000.0)
        bits >= 1_000_000 -> "%.2f Mbps".format(bits / 1_000_000.0)
        bits >= 1_000 -> "%.1f kbps".format(bits / 1_000.0)
        else -> "$bits bps"
    }
}
