// === FILE: app/src/main/java/com/enterwebslinger/network/ui/screens/dashboard/DashboardScreen.kt ===
package com.enterwebslinger.network.ui.screens.dashboard

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.enterwebslinger.network.ui.components.AiAuditCard
import com.enterwebslinger.network.ui.components.ErrorBannerCard
import com.enterwebslinger.network.ui.components.RealtimeSignalChart
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
fun DashboardScreen(
    viewModel: DashboardViewModel,
    aiViewModel: AiAssistantViewModel,
    onOpenAiChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    val topology by viewModel.topology.collectAsStateWithLifecycle()
    val rssiHistory by viewModel.rssiHistory.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val auditState by aiViewModel.auditState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("dashboard_screen")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "ENTERWEB SLINGER",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = CyberCyan,
                        letterSpacing = 1.5.sp
                    )
                )
                Text(
                    text = "Network Health",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onOpenAiChat,
                    modifier = Modifier
                        .size(48.dp)
                        .background(CyberCyan.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                        .testTag("open_ai_chat_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "AI Assistant",
                        tint = CyberCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                val statusColor = if (topology.isConnected) StatusEmerald else StatusCrimson
                val statusText = if (topology.isConnected) "${topology.networkType} Online" else "Offline"
                Row(
                    modifier = Modifier
                        .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                        .border(1.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(statusColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = statusColor
                    )
                }
            }
        }

        if (isRefreshing) {
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .testTag("dashboard_refresh_progress"),
                color = CyberCyan,
                trackColor = CardBackgroundElevated
            )
        }

        val currentError = uiState as? UiState.Error
        if (currentError != null) {
            Spacer(modifier = Modifier.height(12.dp))
            ErrorBannerCard(
                message = currentError.message,
                onDismiss = { viewModel.dismissError() },
                onRetry = { viewModel.manualRefresh() },
                testTagPrefix = "dashboard_error_banner"
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        AiAuditCard(
            auditState = auditState,
            onRunAudit = { aiViewModel.runNetworkHealthAudit() }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("network_hero_card"),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(16.dp),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BorderStrokeColor))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(CyberCyan.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Wifi,
                                contentDescription = "Wi-Fi Icon",
                                tint = CyberCyan,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = topology.ssid,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "BSSID: ${topology.bssid}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                    }

                    Surface(
                        color = CardBackgroundElevated,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = topology.frequencyBand,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = CyberCyan,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MetricMiniBox(
                        title = "Signal (RSSI)",
                        value = "${topology.rssiDbm} dBm",
                        sub = "${topology.signalPercent}% Quality",
                        color = when {
                            topology.rssiDbm >= -65 -> StatusEmerald
                            topology.rssiDbm >= -80 -> StatusAmber
                            else -> StatusCrimson
                        }
                    )
                    MetricMiniBox(
                        title = "Link Speed",
                        value = "${topology.linkSpeedMbps} Mbps",
                        sub = "PHY Rate",
                        color = CyberCyan
                    )
                    MetricMiniBox(
                        title = "Gateway",
                        value = topology.defaultGateway,
                        sub = if (topology.subnetCidr.isNotBlank()) "Subnet /${topology.subnetCidr}" else "Route",
                        color = TextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        val gw = topology.defaultGateway.trim()
                        if (gw != "Unavailable" && gw.isNotBlank() && gw != "0.0.0.0") {
                            runCatching {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://$gw"))
                                context.startActivity(intent)
                            }.onFailure { err ->
                                Toast.makeText(
                                    context,
                                    "No browser available to open http://$gw (${err.message})",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } else {
                            Toast.makeText(context, "Default Gateway IP not detected", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("launch_router_admin_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyberCyan,
                        contentColor = Color(0xFF00363D)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Launch Router Admin (${topology.defaultGateway})",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Live Signal Strength (Rolling 60s)",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Text(
                text = "${topology.rssiDbm} dBm",
                style = MaterialTheme.typography.labelLarge.copy(color = CyberCyan)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        RealtimeSignalChart(dataPoints = rssiHistory)

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("topology_details_card"),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(16.dp),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BorderStrokeColor))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Network Topology Details",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    IconButton(
                        onClick = {
                            if (isRefreshing) viewModel.cancelRefresh() else viewModel.manualRefresh()
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("refresh_public_ip_button")
                    ) {
                        Icon(
                            imageVector = if (isRefreshing) Icons.Default.Close else Icons.Default.Refresh,
                            contentDescription = if (isRefreshing) "Cancel Refresh" else "Refresh Topology & Public IP",
                            tint = if (isRefreshing) StatusCrimson else CyberCyan
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                TopologyRow(label = "Public IP (WAN)", value = topology.publicIp, isMono = true, highlight = true)
                TopologyRow(label = "Local IPv4 (LAN)", value = topology.localIpv4, isMono = true)
                TopologyRow(label = "Local IPv6", value = topology.localIpv6, isMono = true)
                TopologyRow(
                    label = "Subnet Mask",
                    value = if (topology.subnetCidr.isNotBlank()) "${topology.subnetMask} (/${topology.subnetCidr})" else topology.subnetMask,
                    isMono = true
                )
                TopologyRow(label = "Default Gateway", value = topology.defaultGateway, isMono = true)
                TopologyRow(label = "DNS Servers", value = topology.dnsServers.joinToString(", ").ifBlank { "None" }, isMono = true)
                TopologyRow(label = "DHCP Lease Server", value = topology.dhcpLeaseServer, isMono = true)
                TopologyRow(label = "DHCP Lease Duration", value = "${topology.dhcpLeaseDurationSec}s (${topology.dhcpLeaseDurationSec / 3600}h)")
                TopologyRow(label = "Frequency", value = "${topology.frequencyMhz} MHz (${topology.frequencyBand})")
            }
        }
    }
}

@Composable
private fun MetricMiniBox(
    title: String,
    value: String,
    sub: String,
    color: Color
) {
    Box(
        modifier = Modifier
            .background(CardBackgroundElevated, RoundedCornerShape(10.dp))
            .padding(10.dp)
            .width(96.dp)
    ) {
        Column {
            Text(text = title, style = MaterialTheme.typography.labelSmall, color = TextMuted)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                ),
                color = color
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = sub, style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontSize = 9.sp)
        }
    }
}

@Composable
private fun TopologyRow(
    label: String,
    value: String,
    isMono: Boolean = false,
    highlight: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Text(
            text = value,
            style = if (isMono) MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace)
            else MaterialTheme.typography.bodySmall,
            color = if (highlight) CyberCyan else TextPrimary,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal
        )
    }
}
